# ZeroTraceSpringBoot Spring Boot 并发与异步任务演示示例

[English](README_en.md) 

本工程是一个基于 **Spring Boot** 的可运行学习演示示例。通过该项目，您可以直观地了解并对比 **Spring Batch 批处理**、**Embedded ActiveMQ Artemis 异步队列**、**ThreadPool 线程池并发** 以及 **Spring Task Scheduler 定时补偿** 等多种并发和异步任务处理模式，展示在轻量级 **SQLite 数据库** 环境下如何进行事务与连接管理。

---

## 🚀 核心技术栈

- **框架核心**：Spring Boot 3.x, Spring MVC, Spring AOP
- **异步与并发管理**：
  - **Spring Batch**：实现批处理任务的流程演示
  - **Spring JMS (ActiveMQ Artemis)**：实现嵌入式异步消息削峰和重试投递逻辑演示
  - **Spring TaskExecutor**：使用自定义线程池演示本地的并发发送与拒绝策略处理
  - **Spring Task Scheduler**：基于 Cron 表达式在指定时间窗口内执行定时重试扫描
- **持久层**：**Doma 2** (类型安全的 Java SQL 框架)，保证 SQL 语句与 Java 代码清晰隔离
- **数据库**：**SQLite**，并启用了 **WAL (Write-Ahead Logging)** 模式与并发繁忙等待时长配置，以更好地支持并发写入操作

---

## 🛠️ 五大核心演示场景

### 1. 场景一：Spring Batch 批处理计划任务
- **流程**：利用 `Reader` 读取待发送邮件，`Processor` 统一组装发送，`Writer` 对每封邮件以独立的短事务更新状态并记录发送日志。
- **触发**：支持从外部命令行（非 Web 容器生命周期内）传入特定 Job 参数触发执行，适合例行批处理作业演示。

### 2. 场景二：ActiveMQ Artemis 异步队列与重试补偿
- **流程**：当 API 接收到发送请求，首先将邮件写入 SQLite，并将 `mail_id` 推送至 ActiveMQ Artemis。监听器消费消息时调用 SMTP 客户端发送。
- **重试**：如遇发送失败或网络异常，程序将捕获异常并增加重试计数，状态变更为 `FAILED` (9)，并记录发送日志，后续由场景四进行后台重试。

### 3. 场景三：Spring 线程池并发投递与溢出保护
- **流程**：自定义核心线程 2，最大线程 5，队列 10 的 `ThreadPoolTaskExecutor` 发送线程池。使用 `@Async("mailThreadPoolExecutor")` 演示并发发送。
- **限流**：若瞬时并发量过大导致队列溢出，程序会抛出 `RejectedExecutionException`。程序捕获该异常后，将未进队列的邮件在数据库中直接标记为 `FAILED` (9) 并记录日志，并对客户端快速返回 500 告知溢出状态。

### 4. 场景四：Spring Task Scheduler 定时轮询与补偿
- **流程**：通过 `@Scheduled(cron = "${mail.scheduler.cron:0 */10 8-23 * * MON-FRI}")` 配置规则，默认在**工作日的 8:00 ~ 23:00 时间段内每 10 分钟**自动扫描一次数据库。
- **重试限制**：捞取 `status = 0` (未发送) 或 `status = 9` (发送失败，且重试次数 `< 3`) 的邮件，复用单一的 SMTP 客户端连接批量发出，确保即使服务异常重启后也能自动将所有遗漏邮件送达。
- **接口**：提供手动触发重试与定时状态监控接口。

### 5. 场景五：SQLite 多事务隔离控制
- **流程**：SQLite 文件锁在单个大事务持有多封邮件修改时会报 `SQLITE_BUSY`。
- **控制**：本项目中，所有场景在处理多封邮件时，每一封邮件的读取、SMTP 交互、状态更新与日志写入均独立属于它自己的短事务（`Propagation.REQUIRES_NEW`），快速释放锁，以实现并发时的文件锁安全操作。

---

## ⚙️ SmtpClient 属性配置加载的三种实现方式

为了演示 Java 经典环境与 Spring 环境下的不同开发习惯，本工程在各场景的 `SmtpClient` 中演示了三种不同的 Properties 属性配置文件读取方式：

1. **方式一：Java 经典 Properties 加载（适用于非 Spring POJO）**
   - **代表类**：[basic/SmtpClient.java](src/main/java/com/feilonglab/smtp/basic/SmtpClient.java)
   - **原理**：使用 JDK 原生的 `java.util.Properties`，通过当前类的 ClassLoader 以流（Stream）的方式加载类路径下的文件：
     ```java
     Properties props = new Properties();
     try (InputStream in = SmtpClient.class.getResourceAsStream("/mail.properties")) {
         props.load(in);
     }
     ```
   - **特点**：完全独立于 Spring 容器，可在任何普通的 Java SE 应用程序中运行，移植性高。

2. **方式二：ResourceBundle 绑定读取（适用于经典 Java 国际化与资源绑定）**
   - **代表类**：[mq/SmtpClient.java](src/main/java/com/feilonglab/smtp/mq/SmtpClient.java)
   - **原理**：利用 `java.util.ResourceBundle.getBundle("mail")` 在无参构造函数中拉取类路径下的属性映射，作为非容器环境下的备份手段。
   - **特点**：经典的 Java 资源读取方式，通常用于国际化资源处理，可在不依赖 Spring 容器的普通 Java 环境下依然能够良好运行。

3. **方式三：Spring 声明式注解注入（适用于 Spring 容器管理组件）**
   - **代表类**：[threadpool/SmtpClient.java](src/main/java/com/feilonglab/smtp/threadpool/SmtpClient.java)、[scheduler/SmtpClient.java](src/main/java/com/feilonglab/smtp/scheduler/SmtpClient.java)
   - **原理**：在类上添加 `@Component` 和 `@PropertySource("classpath:mail.properties")`，使属性合并进 Spring `Environment` 中，字段使用 `@Value("${mail.smtp.host}")` 声明式地让容器在初始化 Bean 时自动注入。
   - **特点**：Spring 体系下标准的属性管理模式，能够天然融入 Spring Configuration 并配合 Prototype Scope 动态管理生命周期。

---

## 📂 核心 API 接口一览

### 1. 线程池并发发送测试 (场景三)
- **接口**：`POST /threadpool/sendmail`
- **Body** (JSON 数组)：
  ```json
  [
    {
      "toName": "张三",
      "toEmail": "zhangsan@example.com",
      "subject": "并发测试邮件 1",
      "content": "Hello World!"
    },
    {
      "toName": "李四",
      "toEmail": "lisi@example.com",
      "subject": "并发测试邮件 2",
      "content": "Hello Code!"
    }
  ]
  ```

### 2. 计划任务运行状态 (场景四)
- **接口**：`GET /scheduler/status`
- **返回结果** (示例)：
  ```json
  {
    "success": true,
    "enabled": true,
    "cron": "0 */10 8-23 * * MON-FRI",
    "pendingOrFailedCount": 3
  }
  ```

### 3. 手动触发计划任务轮询 (场景四)
- **接口**：`POST /scheduler/trigger`
- **功能**：立即对数据库内待发/重试次数未超限的邮件执行一次投递，并返回投递数。

---

## 🌐 国际化与交互演示页面 (Web UI & Demos)

本项目不仅提供了丰富的后台并发模式演示，还包含一个精心设计的多语言交互式 Web 前端控制台，让开发者能直观体验各种模式的运行效果：

1. **多语言 (i18n) 动态切换**：
   - 所有的用户界面（主页邮件发送中心、Thymeleaf 极简示例页面、Doma SQL 交互示例页面）均完全双语化。
   - 支持通过顶部的 `中文 | English` 链接切换语言（基于 Spring Boot `LocaleResolver` 和多语言资源文件 `message.properties` / `message_zh.properties` 进行解析）。

2. **首页控制中心 (ZeroTrace Main Center - `/`)**：
   - 提供直观的 SMTP 配置项监控（主机地址、端口、账户名、发件人别名等），并支持直接发送单封富文本/HTML 邮件。
   - 右侧主区域的右上方包含**功能导航 (Feature Navigation)** 快捷链接，作为各个演示场景的统一控制枢纽。

3. **Thymeleaf 极简入门示例 (`/demo/thymeleaf`)**：
   - **Thymeleaf 标签演示**：涵盖 `th:text`/`th:placeholder` 文本及占位符显示、`th:if`/`th:block` 条件渲染与控制、`th:each` 数据循环遍历、`th:object`/`th:field` 表单模型绑定，以及 `th:href` 动态路由及传参绑定。
   - **交互式 CRUD**：可在页面上对 SQLite 模拟邮件数据库中的记录进行列表查看、添加新邮件、修改状态和删除等操作。

4. **Doma 2 动态 SQL 交互控制台 (`/demo/doma`)**：
   - **SQL 注释模板展示**：直观演示 Doma 2 所提倡的基于标准 SQL 注释的动态模板拼装（如 `/*%if */`、`/*%for */` 及可选日期过滤等）。由于语法基于原生 SQL 注释，这些 SQL 模板文件仍可被 DataGrip / DBeaver 等数据库工具直接读取和执行。
   - **交互式 SQL 控制台**：开发者可在左侧参数面板输入条件并点击“执行查询 & 打印 SQL”，右侧的**终端日志控制台 (Doma Core SQL Logger Console)** 会高亮打印 Doma 在底层实际拼装出的 SQL 语句与参数绑定明细，并在下方渲染物理 SQLite 表中检索出的真实记录。

---

## ⚙️ 快速上手与运行

### 1. 编译并运行集成测试
在工程根目录下执行 Maven Wrapper 进行清理与编译测试：
```bash
# Windows 环境
.\mvnw.cmd clean compile test

# Linux/Mac 环境
./mvnw clean compile test
```

### 2. 运行 Web 服务
启动 Web 容器：
```bash
# Windows 环境
.\mvnw.cmd spring-boot:run

# Linux/Mac / Windows PowerShell 均支持
mvnw spring-boot:run
```
访问本地默认端口：`http://localhost:8080`。

### 3. 本地邮件模拟配置
您可以编辑 `src/main/resources/mail.properties` 中的 SMTP 服务器地址及模拟失败标志来进行各种网络条件和投递失败的演示。
