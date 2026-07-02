# ZeroTraceSpringBoot (Spring Boot 并发与异步 技术方案示例)

[English](README_en.md) | [设计与需求说明书](docs/design.md)

本项目是一个基于 **Spring Boot** 的可运行技术示例项目。项目以**邮件发送（SMTP）**作为经典切入口，具体展示和对比了四种不同的并发与异步技术方案（**Spring Batch 批处理、Embedded ActiveMQ Artemis 异步队列、ThreadPool 线程池并发、Spring Task Scheduler 定时补偿**），并展示了在轻量级 **SQLite** 文件数据库环境下如何进行精细的事务控制与连接管理。

---

## 💡 核心设计与技术点

本项目以邮件发送（SMTP）为切入口，旨在展示各种并发和异步处理模式的实际技术实现与配置细节。通过在相同业务场景下实现不同的技术架构，本项目具体示例了以下技术点：

1. **高并发与溢出保护设计**：
   - 对比**消息队列削峰**（使用消息代理做可靠性持久化）与**内存线程池**（使用 `ThreadPoolTaskExecutor` 配合拒绝策略）在应对瞬时突发流量时的容错与限流表现。
2. **SQLite 并发下的短事务控制 (Mitigating SQLITE_BUSY)**：
   - 示例如何将批处理或定时补偿的邮件读取、连接 SMTP 发送、修改状态及写日志，通过 `Propagation.REQUIRES_NEW` 划分为**单封邮件独立短事务**，从而快速释放 SQLite 文件级排他锁，防止并发任务因长时间持有数据库写锁而产生 `SQLITE_BUSY` 锁死异常。
3. **三种不同的配置文件加载策略**：
   - 在同一个项目中，对比展示了 Java 经典 Properties 读取、JDK `ResourceBundle` 资源绑定、以及 Spring `@Value` 声明式注入三种读取配置的方式，展示不同解耦层级的实现细节。
4. **多维度定时任务控制**：
   - 包含通过 Spring Batch 示例的不随 Web 容器生命周期管理的命令行触发（外部拉起式作业），以及在 Web 容器生命周期内基于 Cron 表达式自动执行的内置补偿轮询机制。

---

## 🛠️ 五大核心示例场景

为了清晰了解每种方案的内部逻辑，下表总结了本项目的五大示例场景及核心代码入口：

| 示例场景 | 核心业务流程与设计 | 关键配置与业务入口 | 示例/测试方法 |
| :--- | :--- | :--- | :--- |
| **1. Spring Batch 批处理计划任务** | `Reader` 从 SQLite 提取未发邮件，`Processor` 组装并单封发送，`Writer` 以独立短事务更新状态并记录发送日志。适用于大数据量离线批处理。 | `JobConfig`: [MailBatchConfig.java](src/main/java/com/feilonglab/springboot/batch/sendmail/MailBatchConfig.java)<br>`Runner`: [BatchCommandLineRunner.java](src/main/java/com/feilonglab/springboot/batch/sendmail/BatchCommandLineRunner.java) | 通过命令行运行带 `--run-batch-job` 参数的 jar 包触发（非 Web 容器模式）。 |
| **2. ActiveMQ Artemis 异步队列** | 接收 API 请求后，前置持久化邮件到 SQLite，并将 `mail_id` 投递至嵌入式消息队列。JMS 监听器异步消费并调用 SMTP 客户端发送。如遇异常则增加重试计数并标记为失败，由场景 4 进行重试。 | `JMS Config`: [MqConfig.java](src/main/java/com/feilonglab/springboot/web/mq/sendmail/MqConfig.java)<br>`Consumer`: [MqSendMailService.java](src/main/java/com/feilonglab/springboot/web/mq/sendmail/MqSendMailService.java) | 发送 POST 请求到异步队列接口，观察嵌入式 ActiveMQ 消息队列的消费与重试行为。 |
| **3. ThreadPool 线程池并发与溢出** | 自定义核心线程 2，最大线程 5，队列 10 的线程池，配合 `@Async` 异步执行。当并发流量撑满队列触发拒绝策略（`AbortPolicy`）时，系统捕获异常并将被拒绝邮件在数据库直接置为 `FAILED(9)`，保证系统不会因内存积压崩溃。 | `Pool Config`: [ThreadPoolConfig.java](src/main/java/com/feilonglab/springboot/web/threadpool/sendmail/ThreadPoolConfig.java)<br>`Async Service`: [ThreadPoolSendMailService.java](src/main/java/com/feilonglab/springboot/web/threadpool/sendmail/ThreadPoolSendMailService.java) | 发送批量发送请求（大于 15 封邮件）至并发接口，观察部分邮件进入队列异步发送，剩余溢出邮件触发拒绝策略并被数据库捕获标记失败。 |
| **4. Task Scheduler 定时补偿与轮询** | 每隔指定时间（默认工作日 8:00~23:00 内每 10 分钟）轮询数据库捞取未发送或重试次数 `<3` 的失败邮件，复用单一连接批量发出，确保网络异常或宕机重启后，邮件最终送达。 | `Schedule Config`: [SchedulerConfig.java](src/main/java/com/feilonglab/springboot/web/scheduler/sendmail/SchedulerConfig.java)<br>`Scheduler Service`: [SchedulerSendMailService.java](src/main/java/com/feilonglab/springboot/web/scheduler/sendmail/SchedulerSendMailService.java) | 使用 `GET /scheduler/status` 查询定时器配置与积压状态；使用 `POST /scheduler/trigger` 手动触发一次补偿扫描。 |
| **5. SQLite 多事务锁控制** | 示例为规避 SQLite 文件级独占锁造成的 `SQLITE_BUSY` 异常，将所有批处理与定时发送任务以单封邮件为粒度，在独立短事务中进行状态更新和日志记录。 | `Transaction Control`: [MailInfoDao.java](src/main/java/com/feilonglab/springboot/model/dao/MailInfoDao.java) （基于 Doma 2 的 DAO 接口设计） | 在高并发线程池或定时任务发送过程中，数据库能够保持高稳定性，不发生写冲突死锁。 |

---

## ⚙️ 三种配置加载模式示例

为了展示不同生命周期和环境下的开发方式，项目在 `SmtpClient` 的配置读取上示例了三种不同的读取习惯：

### 1. Java 经典 Properties 加载 (脱离容器的普通 POJO)
- **示例类**：[basic/SmtpClient.java](src/main/java/com/feilonglab/smtp/basic/SmtpClient.java)
- **示例代码**：
  ```java
  Properties props = new Properties();
  try (InputStream in = SmtpClient.class.getResourceAsStream("/mail.properties")) {
      props.load(in);
  }
  ```
- **技术点**：完全不依赖 Spring IOC 容器，利用 ClassLoader 流式读取 classpath 下的配置文件。适合用于可移植性极高、不与任何框架绑定的底层工具包中。

### 2. JDK ResourceBundle 资源绑定 (适用于国际化与经典 Java 绑定)
- **示例类**：[mq/SmtpClient.java](src/main/java/com/feilonglab/smtp/mq/SmtpClient.java)
- **示例代码**：
  ```java
  // 在构造器中直接绑定 mail.properties
  ResourceBundle bundle = ResourceBundle.getBundle("mail");
  String host = bundle.getString("mail.smtp.host");
  ```
- **技术点**：利用 Java 核心库提供的资源绑定器，通常用于 i18n 资源处理，也可作为轻量级的配置文件备份加载方案，不需要管理资源文件的流闭合。

### 3. Spring 声明式注解注入 (标准的 Spring 容器管理组件)
- **示例类**：[threadpool/SmtpClient.java](src/main/java/com/feilonglab/smtp/threadpool/SmtpClient.java) / [scheduler/SmtpClient.java](src/main/java/com/feilonglab/smtp/scheduler/SmtpClient.java)
- **示例代码**：
  ```java
  @Component
  @PropertySource("classpath:mail.properties")
  public class SmtpClient {
      @Value("${mail.smtp.host}")
      private String host;
      // ...
  }
  ```
- **技术点**：利用 Spring Framework 的 `@Value` 和 `@PropertySource` 进行属性动态解析与注入。能够方便地配合 `@Scope("prototype")` 动态控制组件的生命周期与重加载。

---

## 📊 核心示例 API 参考

示例 API 主要用于触发特定的并发和异步逻辑，可用如下 HTTP 请求进行调用：

### 1. 并发发送与溢出测试 (场景 3)
- **请求方法**：`POST /threadpool/sendmail`
- **请求内容** (JSON 数组)：
  ```json
  [
    {
      "toName": "示例用户A",
      "toEmail": "usera@example.com",
      "subject": "并发测试 - 01",
      "content": "这是一封用于并发测试的邮件体。"
    },
    {
      "toName": "示例用户B",
      "toEmail": "userb@example.com",
      "subject": "并发测试 - 02",
      "content": "测试线程池队列溢出和状态落盘。"
    }
  ]
  ```
- **示例要点**：当同时传入的数组长度大于 15 时（核心线程 2 + 最大 5 + 队列 10），系统会对其余邮件触发拒签，并直接记录失败日志。

### 2. 状态轮询监控 (场景 4)
- **请求方法**：`GET /scheduler/status`
- **返回结果** (示例)：
  ```json
  {
    "success": true,
    "enabled": true,
    "cron": "0 */10 8-23 * * MON-FRI",
    "pendingOrFailedCount": 3
  }
  ```

### 3. 手动触发定时轮询补偿 (场景 4)
- **请求方法**：`POST /scheduler/trigger`
- **响应**：返回本次捞取并成功投递/重试的邮件数量。

---

## 🌐 双语前端交互与 SQL 实时监控控制台

除了后端 API，项目还在内置的 Web 控制中心提供了丰富的可视化交互示例：

1. **主控制面板 (`/`)**：
   - 实时监控当前的 SMTP 邮件服务器配置项（通过 [mail.properties](src/main/resources/mail.properties) 可配置本地模拟服务器或真实 SMTP 代理）。
   - 提供即时发送富文本 HTML 邮件的交互界面。
   - 顶部提供快捷导航，可直达各项高级示例子系统。
   - 支持通过顶部的 `中文 | English` 链接动态切换多语言界面（基于 Spring 的 LocaleResolver 与 `message.properties` 资源文件）。

2. **Thymeleaf 标签与 CRUD 示例 (`/demo/thymeleaf`)**：
   - 集合展示了 Thymeleaf 的核心标签在实际项目中的使用方式，包括文本渲染（`th:text`/`th:placeholder`）、条件逻辑（`th:if`/`th:block`）、列表遍历（`th:each`）以及表单绑定（`th:object`/`th:field`）。
   - 提供对 SQLite 邮件表记录的实时数据浏览与 CRUD 操作示例。

3. **Doma 2 动态 SQL & 日志实时监控控制台 (`/demo/doma`)**：
   - **动态 SQL 模板展示**：展示 Doma 2 如何利用标准的 SQL 注释（如 `/*%if */`、`/*%for */`）拼装动态 SQL。由于采用注释语法，SQL 模板文件在 DataGrip、DBeaver 等客户端中可独立作为有效 SQL 执行。
   - **实时 SQL 解析日志终端**：在页面左侧调整查询参数并执行，右侧 of Doma Core 日志终端会实时高亮输出框架在底层生成的实际 SQL 和绑定参数，并在下方实时呈现数据库返回的数据，方便观察 Doma 2 的 SQL 生成逻辑与效率。

---

## 🚀 启动与运行示例

### 1. 执行测试与编译
在工程根目录下使用 Maven Wrapper 进行测试和构建：
```bash
# Windows
.\mvnw.cmd clean compile test

# Linux/Mac
./mvnw clean compile test
```

### 2. 运行 Web 控制中心
```bash
# 启动 Web 容器并运行服务
mvnw spring-boot:run
```
启动后访问：`http://localhost:8080` 进入控制台。

### 3. 以纯命令行模式运行 Spring Batch 批处理 (场景 1)
```bash
# 以非 Web 容器模式单独拉起 Batch 任务
mvnw spring-boot:run -Dspring-boot.run.arguments="--run-batch-job"
```

### 4. 邮件发送模拟配置
为方便在本地示例发送失败、网络异常和重试补偿，可在 [mail.properties](src/main/resources/mail.properties) 中调整以下控制项：
- `mail.smtp.host` 和 `mail.smtp.port`：设置 SMTP 服务器参数。
- `mail.debug.flag.smtp`：设置为 `true` 会开启 SMTP 交互的底层 JavaMail 日志。
- `mail.debug.flag.send`：设置为 `FAIL_ALL` 或 `FAIL_RANDOM` 可主动模拟邮件投递失败，便于在本地直接观测消息队列重试机制和定时补偿任务的运行效果。
