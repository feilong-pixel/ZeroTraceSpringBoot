# ZeroTraceSpringBoot 邮件发送演示系统设计与需求说明书

本工程是一个基于 Spring Boot 的示例项目，旨在演示 **Spring Batch (批处理)**、**Spring AMQP (消息队列)**、**Spring Async (线程池)**、**Spring Task Scheduler (计划任务)** 以及 **SQLite 数据库** 的集成与应用。

项目核心业务场景为：**可靠性邮件发送系统**。通过多种模式（队列、线程池、批处理、定时轮询）演示如何实现高可靠、防丢失的邮件发送机制，并展示不同架构下的技术差异。

---

## 一、 系统架构与关键技术对比

### 1. 消息队列 (Spring AMQP) vs 线程池 (Spring Executor)
本系统的一大设计目标是对比**消息队列**与**内存线程池**在处理高并发、突发流量时的容错与可靠性表现：

| 特性维度 | 消息队列 (Spring AMQP / RabbitMQ) | 线程池 (Spring TaskExecutor) |
| :--- | :--- | :--- |
| **内存溢出时的消息持久性** | **高可靠**：即使队列溢出或服务宕机，AMQP 代理（如 RabbitMQ）支持消息持久化，消息不会丢失。 | **易丢失**：若内存队列（如 `LinkedBlockingQueue`）溢出或应用宕机，内存中的待处理邮件任务将全部丢失。 |
| **高并发限流/削峰** | 极佳。消费者根据自身消费能力拉取或推送消息，天然起到削峰填谷的作用。 | 较好。通过调整线程池的核心线程数、最大线程数和阻塞队列大小进行限流。 |
| **错误恢复与重试机制** | **结合数据库实现 (ZeroTrace)**：当队列消费失败或发送超时，将异常邮件记录写入 SQLite 数据库，由后台定时任务进行补偿和重新发送，实现零丢失 (ZeroTrace) 的高可靠投递。 | **数据库补偿**：由于内存任务易丢失，系统必须将所有邮件在发送前持久化至数据库。当线程池任务执行失败或发生内存丢失时，通过轮询数据库重新提交任务。 |

### 2. Spring 计划任务 (Scheduled Tasks) 的配置方式
系统演示了以下几种计划任务的调用与配置方式：
*   **固定间隔 (Fixed Rate)**：`@Scheduled(fixedRate = 5000)`，以**任务开始时间**为基准，每隔指定毫秒数执行一次。
*   **固定延迟 (Fixed Delay)**：`@Scheduled(fixedDelay = 5000)`，以**前一个任务完成时间**为基准，延迟指定毫秒数执行下一次任务。
*   **Cron 表达式**：`@Scheduled(cron = "0 0/5 * * * ?")`，支持在指定的时间、日期或按复杂周期执行。
*   **命令行触发**：结合 Spring Batch，演示如何通过外部定时任务（如 Linux crontab 或 Windows Task Scheduler）通过命令行拉起 Spring 应用程序并执行批处理作业。

---

## 二、 数据库设计

系统选用轻量级 **SQLite** 作为持久化数据库，为支持**多事务并发**，设计上采用**单事务独立处理单封邮件**的策略，避免因长时间占用锁导致 `SQLITE_BUSY` 数据库锁死。

### 1. 邮件信息表 (`mail_info`)
存储邮件的基本元数据以及当前的发送状态。

| 字段名 (Field) | 类型 (Type) | 约束/默认值 | 描述 (Description) |
| :--- | :--- | :--- | :--- |
| `mail_id` | `INTEGER` | `PRIMARY KEY AUTOINCREMENT` | 邮件唯一标识 ID |
| `status` | `INTEGER` | `DEFAULT 0` | 邮件发送状态（`0`：未发送；`1`：发送成功；`9`：发送失败） |
| `retry_count`| `INTEGER` | `DEFAULT 0` | 尝试发送次数（最大数值：3） |
| `sent_at` | `DATETIME` | `NULL` | 成功发送的日期与时间 |
| `to_name` | `VARCHAR` | `NOT NULL` | 收件人显示姓名 |
| `to_email` | `VARCHAR` | `NOT NULL` | 收件人电子邮箱地址 |
| `subject` | `VARCHAR` | `NOT NULL` | 邮件标题 |
| `content` | `TEXT` | `NOT NULL` | 邮件内容 (支持 HTML 格式) |
| `version` | `INTEGER` | `DEFAULT 1` | 乐观锁版本号，用于控制多线程并发修改 |

### 2. 邮件发送日志表 (`mail_send_log`)
记录邮件的每次发送详情，用于追踪发送轨迹与排查失败原因。

| 字段名 (Field) | 类型 (Type) | 约束/默认值 | 描述 (Description) |
| :--- | :--- | :--- | :--- |
| `log_id` | `INTEGER` | `PRIMARY KEY AUTOINCREMENT` | 日志唯一标识 ID |
| `mail_id` | `INTEGER` | `NOT NULL` | 关联的邮件 ID |
| `sent_at` | `DATETIME` | `NOT NULL` | 本次发送时间 |
| `status` | `INTEGER` | `NOT NULL` | 发送状态（`1`：发送成功；`9`：发送失败） |
| `attempt` | `INTEGER` | `NOT NULL` | 本次是第几次尝试发送 |
| `error_reason`| `TEXT` | `NULL` | 发送失败时的具体异常/错误原因 |
| `version` | `INTEGER` | `DEFAULT 1` | 乐观锁版本号 |

### 3. DDL 创表语句 (SQLite 兼容)
```sql
-- 邮件信息表
CREATE TABLE IF NOT EXISTS mail_info (
    mail_id INTEGER PRIMARY KEY AUTOINCREMENT,
    status INTEGER DEFAULT 0,
    retry_count INTEGER DEFAULT 0,
    sent_at DATETIME,
    to_name VARCHAR(100) NOT NULL,
    to_email VARCHAR(255) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    version INTEGER DEFAULT 1
);

-- 邮件发送日志表
CREATE TABLE IF NOT EXISTS mail_send_log (
    log_id INTEGER PRIMARY KEY AUTOINCREMENT,
    mail_id INTEGER NOT NULL,
    sent_at DATETIME NOT NULL,
    status INTEGER NOT NULL,
    attempt INTEGER NOT NULL,
    error_reason TEXT,
    version INTEGER DEFAULT 1,
    FOREIGN KEY(mail_id) REFERENCES mail_info(mail_id)
);

-- 创建索引以加速未发送和失败重试邮件的轮询
CREATE INDEX IF NOT EXISTS idx_mail_info_status ON mail_info(status, retry_count);
```

---

## 三、 五大核心场景与演示需求

### 场景 1：使用 Spring Batch 批处理计划任务
*   **需求目标**：展示如何通过命令行方式触发 Spring Batch 批处理程序。
*   **具体流程**：
    1.  提供一个标准的 Spring Batch 作业，包含 `Reader` (从 SQLite 读取未发送邮件)、`Processor` (调用邮件发送客户端并记录结果，单封发送) 和 `Writer` (单封事务更新 `mail_info` 及写入 `mail_send_log`)。
    2.  支持从外部命令行（非 Web 容器生命周期内）传入特定 Job 参数并调用该批处理。
    3.  适用于每天/每小时的例行、高通量邮件发送业务。

### 场景 2：使用 Spring 消息队列 (AMQP / RabbitMQ) 与 ZeroTrace 可靠重试
*   **需求目标**：演示基于 AMQP 消息队列发送邮件，并结合数据库设计实现防丢失的 **ZeroTrace** 重试效果。
*   **具体流程**：
    1.  当系统收到发送邮件的 API 请求时，先将邮件写入 `mail_info` 数据库，并将 `mail_id` 作为消息发布至 RabbitMQ 队列。
    2.  监听器消费消息，读取 `mail_info` 并通过 `SmtpClient` 发送邮件。
    3.  若发送成功，更新状态为 `1` 并记录成功日志。
    4.  若发送失败或队列溢出导致消费异常，则捕获该异常，递增 `retry_count`，记录失败日志并将状态标为 `9`。由后续定时任务（场景 4）负责从数据库捞起失败记录重新发送。

### 场景 3：使用 Spring 线程池 (ThreadPoolTaskExecutor) 并发发送
*   **需求目标**：展示如何利用本地线程池提高邮件发送的吞吐量。
*   **具体流程**：
    1.  配置一个 Spring `ThreadPoolTaskExecutor` 作为专属邮件发送线程池。
    2.  当接收到多封邮件发送指令时，使用 `@Async` 注解将发送动作委托给线程池中的子线程并发处理。
    3.  对比线程池阻塞队列溢出时与消息队列在系统资源占用及数据持久化上的差异。

### 场景 4：使用 Spring Task Scheduler 定时轮询（补偿重试机制）
*   **需求目标**：展示如何基于 Spring 定时任务实现对未发送/失败邮件的定期补偿轮询。
*   **具体流程**：
    1.  定义一个轮询器（类似于后台守护进程），使用 Spring `@Scheduled` 设置指定时间间隔（例如每 1 分钟）循环调用。
    2.  每次执行时，扫描 `mail_info` 中 `status = 0` (未发送) 或 `status = 9` (发送失败，且 `retry_count < 3`) 的记录。
    3.  将扫描到的邮件批量加载并提交发送，以保证所有因断电、崩溃、网络超时等原因遗漏的邮件得以最终送达。

### 场景 5：SQLite 数据库多事务隔离控制
*   **需求目标**：展示在 SQLite 文件级锁限制下，如何实现安全的并发事务控制。
*   **具体流程**：
    1.  多线程或多进程并发操作同一 SQLite 文件时，避免在单个大事务中合并处理所有邮件，否则会引发 `SQLITE_BUSY` 异常。
    2.  **设计要求**：采用**单个独立事务处理一封邮件**的粒度。每封邮件在读取、执行发送、修改状态及写入日志的过程都必须归属于它自己独立的短事务，确保快速释放文件锁，最大化并发能力。
