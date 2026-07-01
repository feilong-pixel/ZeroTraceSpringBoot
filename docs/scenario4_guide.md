# 场景 4 入门指南：使用 Spring Task Scheduler 定时轮询（补偿重试机制）

本指南旨在向您详细解释和说明 **场景 4：使用 Spring Task Scheduler 定时轮询（补偿重试机制）** 的设计、架构及执行逻辑，帮助您快速理解本工程中定时补偿与重试的核心逻辑。

---

## 📖 1. 什么是定时补偿重试机制？

在分布式或高并发邮件系统中，单次发送任务可能会因为以下原因中断或遗漏：
- 网络瞬时闪断，导致与外部 SMTP 邮件服务器通信失败。
- 系统突然断电、崩溃或 JVM 重启，导致内存队列中的任务丢失。
- 数据库写入成功，但在推送到消息队列（RabbitMQ）或线程池排队时发生崩溃。

**场景 4** 扮演了“**后台安全卫士**”的角色。它不依赖外部即时触发，而是通过定时扫描数据库来兜底：
1. 定期捞取数据库中因上述意外遗漏的邮件（状态为 0，即未发送）或历史投递失败的邮件（状态为 9）。
2. 在保证不超限的前提下（最多重试 3 次），重新发起投递。
3. 它是保证所有邮件“最终能够送达”的核心补偿机制。

---

## 🏗️ 2. 定时计划配置 (Cron)

在 [SchedulerConfig.java](../src/main/java/com/feilonglab/springboot/web/scheduler/sendmail/SchedulerConfig.java) 中添加了 `@EnableScheduling`，从而激活了定时任务。

在 [SchedulerSendMailService.java](../src/main/java/com/feilonglab/springboot/web/scheduler/sendmail/SchedulerSendMailService.java) 中，我们声明了基于 Cron 表达式的定时任务：

```java
@Scheduled(cron = "${mail.scheduler.cron:0 */10 8-23 * * MON-FRI}")
```

### 1) 默认 Cron 表达式详解：
- **`0`**：第 0 秒触发。
- **`*/10`**：每隔 10 分钟触发一次。
- **`8-23`**：在每天的 **8:00 到 23:00**（包含 23 点整）时间段内触发。
- **`* *`**：任意日期、任意月份。
- **`MON-FRI`**：**周一到周五**（工作日）触发。

这个配置完全满足“**仅在工作日的 8:00 ~ 23:00 之间，每隔 10 分钟执行一次扫描**”的业务要求，避免在深夜或周末非工作时间打扰用户。

### 2) 灵活的可定制性 (测试友好)
Cron 表达式通过配置项占位符 `${mail.scheduler.cron:...}` 加载。在本地开发和测试期间，可以在 `application.properties` 中将其覆盖为更快速的频率，以便观察日志（例如每 5 秒扫描一次）：
```properties
mail.scheduler.cron=*/5 * * * * *
```

---

## 🔄 3. 详细执行流程与源码分析

### 1) 捞取数据与 SMTP 连接复用
1. 当定时器触发时，首先调用 `mailInfoDao.selectUnsentOrFailed(3)`。其中参数 `3` 限制了**最大重试次数**，只有重试次数少于 3 次的未发或失败邮件才会被拉取。
2. 在拉取到待发送邮件列表后，服务通过 `smtpClientFactory.getObject()` 打开一个 SMTP 客户端连接，并在循环发送中复用该连接，避免每发一封邮件都重复建立 TCP 连接的性能开销。

### 2) 独立短事务隔离落盘
为防止在批量更新数据库时锁死 SQLite 文件，发送成功后调用 `saveSuccess`，发送失败后调用 `saveFailure`。这两个更新落盘方法都配置了独立事务：
- `@Transactional(propagation = Propagation.REQUIRES_NEW)`
- 这确保了每封邮件的状态保存与流水日志插入均在自己独立的短事务中完成，快速提交并释放锁，避免了 `SQLITE_BUSY` 异常，同时保证了单封邮件的错误不会影响其他邮件的继续发送。

---

## 📂 4. 状态监控与手动干预接口

为方便测试和管理，[SchedulerSendMailController.java](../src/main/java/com/feilonglab/springboot/web/scheduler/sendmail/SchedulerSendMailController.java) 提供了两个 RESTful Web API：

### 1) 查询定时任务状态 (`GET /scheduler/status`)
- **功能**：返回定时调度器当前是否启用、绑定的 Cron 表达式规则，以及当前积压在数据库中亟待发送和重试的邮件总数。
- **响应示例**：
  ```json
  {
    "success": true,
    "enabled": true,
    "cron": "0 */10 8-23 * * MON-FRI",
    "pendingOrFailedCount": 2
  }
  ```

### 2) 手动触发重试扫描 (`POST /scheduler/trigger`)
- **功能**：立即执行一次重试与补偿任务，不需要等待下一次 Cron 触发点，非常适合管理员应急干预或在测试时立即验证发送效果。
