# 场景 2 入门指南：使用 Spring 消息队列 (AMQP / RabbitMQ) 与重试补偿

本指南旨在向您详细解释和说明 **场景 2：使用 Spring 消息队列 (AMQP / RabbitMQ) 与重试补偿** 的设计、架构及执行逻辑，帮助您快速理解本工程中消息队列模式下的邮件发送逻辑。

---

## 📖 1. 什么是消息队列异步邮件投递与重试？

在企业级应用中，直接在 Web 请求中同步发送邮件会极大地阻塞用户线程（因为与外部 SMTP 服务器建立 TCP 连接和握手可能需要数秒时间）。
**场景 2** 演示了如何将同步发送转为**异步队列处理**：
1. Web 接口接收请求后，仅将邮件元数据保存至本地 SQLite 数据库，并向 **RabbitMQ** 推送一条只含有“邮件 ID”的消息，随即立刻向用户返回“已提交”的成功响应。
2. 消费者线程在后台从队列中拉取邮件 ID，加载数据并通过 SMTP 客户端完成实际的邮件发送。
3. **重试补偿设计**：如果消费者发送邮件时网络闪断或服务器超时导致失败，系统不会丢弃该邮件，而是更新其重试次数并标记为失败状态（`status` = 9）。在后台，定时器（场景 4）会扫描这些记录并在允许的时间段内发起重试。这样即便系统或队列出现瞬时崩溃，邮件记录也能做到防丢失，实现可靠重试。

---

## 🏗️ 2. 队列配置与核心组件

在 [MqConfig.java](../src/main/java/com/feilonglab/springboot/web/mq/sendmail/MqConfig.java) 中，我们定义了 RabbitMQ 相关的基础 Bean：

- **邮件队列 (`mailQueue`)**：声明了一个名为 `mail.queue` 的持久化队列，支持在配置文件中设置队列的最大长度（`x-max-length`）与溢出策略（`x-overflow`）。
- **主题交换机 (`mailExchange`)**：声明了一个名为 `mail.exchange` 的 Topic 交换机。
- **绑定规则 (`mailBinding`)**：将队列通过路由键 `mail.routing.key` 绑定到交换机。
- **并发消费者配置 (`rabbitListenerContainerFactory`)**：配置了监听器容器的并发消费者线程数（最小 1，最大 5）。

---

## 🔄 3. 详细执行流程与源码分析

整个发送流程在 [MqSendMailService.java](../src/main/java/com/feilonglab/springboot/web/mq/sendmail/MqSendMailService.java) 中实现：

```mermaid
sequenceDiagram
    autonumber
    actor User as 客户端
    participant Controller as MqSendMailController
    participant Service as MqSendMailService
    database DB as "SQLite (mail_info)"
    participant MQ as "RabbitMQ (mail.queue)"

    User->>Controller: POST /mq/sendmail (MailRequest)
    Controller->>Service: sendMailAsync(request)
    activate Service
    Service->>DB: 插入数据 (status=0, retry_count=0)
    Service->>MQ: 发送消息 (payload = mailId)
    Service-->>Controller: 返回 mailId
    deactivate Service
    Controller-->>User: HTTP 200 (邮件已推入队列)

    note over Service, MQ: 消费者异步提取消息...
    MQ->>Service: @RabbitListener 触发 consumeMailMessage(mailId)
    activate Service
    Service->>DB: selectById(mailId)
    Service->>Service: 校验状态 (若已发送成功则跳过)
    Service->>Service: 调用 SmtpClient 发送邮件
    alt 发送成功
        Service->>DB: saveSuccess() (新事务，status=1, sent_at=当前时间)
    else 发送失败
        Service->>DB: saveFailure() (新事务，status=9, error_reason=报错异常)
    end
    deactivate Service
```

### 1) 接收与推入阶段 (`sendMailAsync`)
当用户调用 `POST /mq/sendmail` 接口：
1. 事务方法 `sendMailAsync` 将请求数据封装为 `MailInfo`，设置状态为 `PENDING` (0)，重试次数为 `0`，并执行 `mailInfoDao.insert(mailInfo)` 写入数据库。
2. 随后，使用 `rabbitTemplate.convertAndSend` 将新生成的 `mailId` 推送到 RabbitMQ 队列中。

### 2) 异步消费与投递阶段 (`consumeMailMessage`)
1. 方法带有 `@RabbitListener(queues = "${mail.queue:mail.queue}")` 注解，只要队列有消息，就会被唤醒执行。
2. 消费者根据 `mailId` 从 SQLite 中检索出完整的邮件数据。
3. 检查状态：如果该邮件之前已发送成功，则直接忽略，防止因消息重复投递导致用户重复收到多封相同邮件（幂等性设计）。
4. 通过 SMTP 客户端向目标邮箱进行投递。

### 3) 隔离事务落盘与状态更新 (`saveSuccess` 与 `saveFailure`)
- 投递成功：通过 `self.saveSuccess(mail)` 启动一个具有 `Propagation.REQUIRES_NEW` 隔离级别的**独立新事务**，将邮件状态修改为 `SUCCESS` (1)，累计重试计数，设置当前发送时间，并写入一条成功流水日志到 `mail_send_log`。
- 投递失败：通过 `self.saveFailure(mail, errorReason)` 同样在**独立新事务**中将状态置为 `FAILED` (9)，重试计数加一，日志中保存最多 500 字符的错误堆栈原因。
- **为什么使用新事务？**
  如果直接在监听方法内提交大事务，一旦数据库锁冲突或某步抛出未捕获异常，会导致整条消息的消费事务回滚。通过拆分成短事务落盘，能迅速释放 SQLite 数据库锁，最大限度保护数据的完整性。
