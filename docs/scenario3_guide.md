# 场景 3 入门指南：使用 Spring 线程池 (ThreadPoolTaskExecutor) 并发发送

本指南旨在向您详细解释和说明 **场景 3：使用 Spring 线程池 (ThreadPoolTaskExecutor) 并发发送** 的设计、架构及执行逻辑，帮助您快速理解本工程中本地多线程并发与拒绝限流机制。

---

## 📖 1. 什么是本地线程池并发投递与溢出保护？

本地多线程是提升系统任务处理吞吐量最直接的方式。
**场景 3** 演示了如何利用 Spring 的 `ThreadPoolTaskExecutor` 和 `@Async` 异步注解，将单线程的邮件发送工作分配给本地的多个后台子线程并发处理：
1. **并发执行**：Web 接收端接收到一批（例如 10 封以上）邮件发送请求，批量开启子线程同时调用 SMTP 客户端发送邮件，极大地缩短了总处理时间。
2. **限流保护与溢出处理**：内存资源是有限的。如果请求量过大，无限堆积任务会导致内存溢出（OOM）。为此，必须为线程池设置合理的参数（核心数、最大数、阻塞队列）和**拒绝策略**。
3. **与消息队列的区别**：
   - 消息队列（如 RabbitMQ）在消息过多时，会安全地在外部中间件中持久化，消费者可以按自身节奏消费。
   - 本地线程池在任务积压超过阈值时，会直接抛出拒绝异常。系统必须捕获这一异常，将拒绝的任务在数据库中标记为失败（`status` = 9），防止内存任务丢失，后续依赖场景四（定时器）进行最终补偿。

---

## 🏗️ 2. 线程池配置与核心参数

在 [ThreadPoolConfig.java](../src/main/java/com/feilonglab/springboot/web/threadpool/sendmail/ThreadPoolConfig.java) 中，我们显式配置了一个名为 `mailThreadPoolExecutor` 的线程池：

- **核心线程数 (Core Pool Size) = 2**：平时保持活跃的线程数量。
- **最大线程数 (Max Pool Size) = 5**：当核心线程都在忙，且队列也满了时，线程池能创建的最大并发线程数。
- **队列容量 (Queue Capacity) = 10**：用来存放等待执行任务的阻塞队列（`LinkedBlockingQueue`）大小。
- **拒绝策略 (Rejected Execution Handler) = AbortPolicy**：一旦最大线程数 (5) 和队列容量 (10) 都占满时，第 16 个任务提交将立即抛出 `RejectedExecutionException`。
  *(注：为了便于开发和测试时模拟溢出拒绝行为，我们故意将这些参数配置得很小。)*

---

## 🔄 3. 详细执行流程与源码分析

整个发送流程在 [ThreadPoolSendMailController.java](../src/main/java/com/feilonglab/springboot/web/threadpool/sendmail/ThreadPoolSendMailController.java) 中驱动：

```mermaid
sequenceDiagram
    autonumber
    actor User as 客户端
    participant Controller as ThreadPoolSendMailController
    participant Service as ThreadPoolSendMailService
    database DB as SQLite (mail_info)
    participant Pool as mailThreadPoolExecutor (2/5/10)

    User->>Controller: POST /threadpool/sendmail (List<MailRequest>)
    activate Controller
    loop 循环每封邮件
        Controller->>Service: persistMail(request)
        Service->>DB: 插入数据 (status=0, REQUIRES_NEW)
    end

    loop 循环向线程池提交
        Controller->>Service: sendMailAsync(mailId)
        alt 线程池未满 (<= 15)
            Service->>Pool: 提交任务 (@Async)
            Controller->>Controller: 累计成功提交数
        else 线程池已满 (> 15)
            Service->>Pool: 提交失败 (抛出 RejectedExecutionException)
            Controller->>Service: saveFailure(mailId)
            Service->>DB: 更新状态为 9并记录日志 (REQUIRES_NEW)
            Controller->>Controller: 累计拒绝数
        end
    end
    
    Controller-->>User: HTTP 500 或 200 (返回成功与拒绝计数)
    deactivate Controller

    note over Service, Pool: 子线程在后台异步执行并发发送...
```

### 1) 数据前置持久化 (`persistMail`)
在向线程池提交任何异步任务前，控制器必须先调用 `threadPoolSendMailService.persistMail` 将邮件持久化写入 SQLite。
- 该方法被注解为 `@Transactional(propagation = Propagation.REQUIRES_NEW)`，以**独立新事务**运行。
- 这样能保证即使该邮件随后在提交给线程池时被**限流拒绝**，它也已经在数据库中有了对应的记录，使得后续可以通过定时器捞起重试，数据不会凭空消失。

### 2) 异步方法派发与拦截 (`sendMailAsync`)
控制器遍历已持久化的邮件 ID，调用 `threadPoolSendMailService.sendMailAsync(mailId)`：
- 该方法声明了 `@Async("mailThreadPoolExecutor")`。
- Spring AOP 会在运行时拦截这个调用，把具体的发送任务打包扔给线程池 `mailThreadPoolExecutor` 排队执行。

### 3) 限流拒绝异常处理
如果发送列表过大（例如 20 封邮件）：
1. 前 2 封邮件由核心线程立即执行；
2. 随后 10 封邮件进入阻塞队列（共 12 封）；
3. 接着 3 封邮件促使创建新线程直到达到最大线程数 5（共 15 封）；
4. 第 16 封邮件由于池和队列均满，提交时抛出 `RejectedExecutionException`；
5. 控制器捕获该拒绝异常，记录警告日志，并立即调用 `saveFailure(mailId, ...)` 将该邮件状态置为 `FAILED` (9)；
6. 最终，控制器向客户端返回 500 状态码，并输出类似下面的结果：
   ```json
   {
     "success": false,
     "message": "线程池队列已溢出，部分任务已被拒绝投递，后续将由定时器补偿重试。",
     "submittedCount": 15,
     "rejectedCount": 5
   }
   ```

### 4) 子线程独立事务状态更新
与场景 2 类似，后台线程并发执行 SmtpClient 发送，并在结束后调用 `saveSuccess` 或 `saveFailure` 方法。所有数据库更新必须使用 `REQUIRES_NEW` 独立短事务，确保并发时能快速释放 SQLite 悲观文件锁，规避 `SQLITE_BUSY` 并发死锁。
