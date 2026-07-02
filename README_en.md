# ZeroTraceSpringBoot: Spring Boot Concurrency & Asynchronous Demo

[中文](README.md) | [Design & Architecture Document](docs/design.md)

ZeroTraceSpringBoot is a runnable Spring Boot project designed to demonstrate various concurrency and asynchronous processing patterns. Using **mail sending (SMTP)** as a classic entry point, the project showcases and compares four distinct approaches: **Spring Batch processing, Embedded ActiveMQ Artemis message queueing, ThreadPool task execution, and Spring Task Scheduler compensation**. It also demonstrates fine-grained transaction management and connection handling within a lightweight **SQLite** file-based database.

---

## 💡 Core Design Patterns & Technical Insights

This project uses SMTP mail sending as a classic entry point to showcase and contrast different technical implementations of concurrency and asynchronous processing. By implementing different architectural approaches under the same business scenario, the project highlights the following technical aspects:

1. **High Concurrency & Rate Limiting**:
   - Compares **message queue load shaving** (using persistent message brokers for guaranteed delivery) against **in-memory thread pools** (using `ThreadPoolTaskExecutor` with custom rejection policies) to showcase stability under traffic surges.
2. **Short-Transaction Control in SQLite (Mitigating SQLITE_BUSY)**:
   - Shows how to break down bulk email dispatching, SMTP handshakes, status updates, and logging into isolated, single-record transactions using `Propagation.REQUIRES_NEW`. This strategy releases SQLite file-level locks immediately, preventing write lock contention and `SQLITE_BUSY` errors.
3. **Property Configuration Loading Strategies**:
   - Compares three classic configuration loading techniques: native Java properties loading, JDK `ResourceBundle` property mapping, and Spring `@Value` declarative annotation injection. This serves as a reference for selecting the appropriate level of framework coupling.
4. **Multi-Dimensional Task Scheduling**:
   - Showcases both offline CLI-triggered batch jobs (run independently of the Web container's lifecycle via Spring Batch) and built-in cron-based background worker scanning (for automated retry/consistency reconciliation).

---

## 🛠️ Five Core Scenarios

The table below outlines the core scenarios implemented in this project, along with their key configuration classes and files:

| Scenario | Architectural Approach & Flow | Key Configuration & Entry Point | Testing / Verification |
| :--- | :--- | :--- | :--- |
| **1. Spring Batch Processing** | Reads unsent mail from SQLite in chunks, dispatches them, and writes logs and status updates using isolated short transactions. Ideal for high-volume offline batch jobs. | `JobConfig`: [MailBatchConfig.java](src/main/java/com/feilonglab/springboot/batch/sendmail/MailBatchConfig.java)<br>`Runner`: [BatchCommandLineRunner.java](src/main/java/com/feilonglab/springboot/batch/sendmail/BatchCommandLineRunner.java) | Run the compiled JAR via the command line with `--run-batch-job` to launch without booting the Web container. |
| **2. ActiveMQ Artemis Async Queue** | Persists email metadata to SQLite on API request, then pushes the `mail_id` to an embedded JMS queue. A JMS listener consumes the queue asynchronously to dispatch the mail. | `JMS Config`: [MqConfig.java](src/main/java/com/feilonglab/springboot/web/mq/sendmail/MqConfig.java)<br>`Consumer`: [MqSendMailService.java](src/main/java/com/feilonglab/springboot/web/mq/sendmail/MqSendMailService.java) | POST requests to the queue endpoint to observe asynchronous delivery, queue consumption, and automatic failed-message persistence. |
| **3. ThreadPool Concurrency & Overflow** | Dispatches emails concurrently using `@Async` backed by a custom thread pool (Core: 2, Max: 5, Queue: 10). Excess traffic triggers `AbortPolicy`, which is caught to persist the overflow as failed records. | `Pool Config`: [ThreadPoolConfig.java](src/main/java/com/feilonglab/springboot/web/threadpool/sendmail/ThreadPoolConfig.java)<br>`Async Service`: [ThreadPoolSendMailService.java](src/main/java/com/feilonglab/springboot/web/threadpool/sendmail/ThreadPoolSendMailService.java) | Send a batch request of >15 emails to trigger the rejection policy, observing how overflowed items are safely handled and recorded. |
| **4. Task Scheduler Compensation** | Periodically scans SQLite for unsent or failed emails (retry count < 3) and batch-sends them using a single SMTP connection. Ensures eventual consistency and recovery from server restarts. | `Schedule Config`: [SchedulerConfig.java](src/main/java/com/feilonglab/springboot/web/scheduler/sendmail/SchedulerConfig.java)<br>`Scheduler Service`: [SchedulerSendMailService.java](src/main/java/com/feilonglab/springboot/web/scheduler/sendmail/SchedulerSendMailService.java) | Query `GET /scheduler/status` for cron and queue status, or `POST /scheduler/trigger` to manually trigger an immediate compensation run. |
| **5. SQLite Multi-Transaction Control** | Demonstrates transaction isolation using Doma 2. Encapsulates each email's database updates into an independent transaction to keep SQLite file-locks short and prevent concurrency deadlock. | `Transaction Control`: [MailInfoDao.java](src/main/java/com/feilonglab/springboot/model/dao/MailInfoDao.java) (Type-safe DAO generated by Doma 2) | Observe database stability and write performance during concurrent thread-pool and scheduler execution. |

---

## ⚙️ Property Loading Implementations

To illustrate configuration patterns across both standard Java SE and Spring environments, the project implements three distinct ways to load properties in `SmtpClient`:

### 1. Classic Java Properties (Framework-Independent)
- **Class**: [basic/SmtpClient.java](src/main/java/com/feilonglab/smtp/basic/SmtpClient.java)
- **Implementation**:
  ```java
  Properties props = new Properties();
  try (InputStream in = SmtpClient.class.getResourceAsStream("/mail.properties")) {
      props.load(in);
  }
  ```
- **Use Case**: Completely decoupled from Spring. Useful for standalone utilities or core libraries where framework dependencies are undesirable.

### 2. JDK ResourceBundle (Standard Java Internationalization)
- **Class**: [mq/SmtpClient.java](src/main/java/com/feilonglab/smtp/mq/SmtpClient.java)
- **Implementation**:
  ```java
  ResourceBundle bundle = ResourceBundle.getBundle("mail");
  String host = bundle.getString("mail.smtp.host");
  ```
- **Use Case**: Utilizes standard Java localization APIs. Offers a cleaner approach for properties loading without manual stream management.

### 3. Spring Declarative Injection (Framework-Managed)
- **Class**: [threadpool/SmtpClient.java](src/main/java/com/feilonglab/smtp/threadpool/SmtpClient.java) / [scheduler/SmtpClient.java](src/main/java/com/feilonglab/smtp/scheduler/SmtpClient.java)
- **Implementation**:
  ```java
  @Component
  @PropertySource("classpath:mail.properties")
  public class SmtpClient {
      @Value("${mail.smtp.host}")
      private String host;
      // ...
  }
  ```
- **Use Case**: Idiomatic Spring configuration pattern. Simplifies dependency injection and works seamlessly with `@Scope("prototype")` for dynamic lifecycle management.

---

## 📊 REST API Reference

The following HTTP endpoints allow you to trigger and observe the concurrency and queuing behaviors:

### 1. Concurrent Mail Dispatch & Rejection Test (Scenario 3)
- **Method**: `POST /threadpool/sendmail`
- **Request Body** (JSON Array):
  ```json
  [
    {
      "toName": "Demo User A",
      "toEmail": "usera@example.com",
      "subject": "ThreadPool Test - 01",
      "content": "Testing concurrency behavior."
    },
    {
      "toName": "Demo User B",
      "toEmail": "userb@example.com",
      "subject": "ThreadPool Test - 02",
      "content": "Testing database logging under stress."
    }
  ]
  ```
- **Test Detail**: When sending more than 15 emails simultaneously (Core: 2 + Max: 5 + Queue: 10), the remaining emails will be rejected by the executor, and immediately recorded as `FAILED` in the SQLite database.

### 2. Check Scheduler Status (Scenario 4)
- **Method**: `GET /scheduler/status`
- **Response** (Example):
  ```json
  {
    "success": true,
    "enabled": true,
    "cron": "0 */10 8-23 * * MON-FRI",
    "pendingOrFailedCount": 3
  }
  ```

### 3. Manually Trigger Compensation (Scenario 4)
- **Method**: `POST /scheduler/trigger`
- **Response**: Returns the count of pending/failed emails retrieved and sent during this manual run.

---

## 🌐 Web UI & Live SQL Log Console

In addition to REST endpoints, the application provides an interactive front-end dashboard:

1. **Dashboard Home (`/`)**:
   - Displays real-time SMTP configurations (set in [mail.properties](src/main/resources/mail.properties)).
   - Includes a rich-text/HTML email composer.
   - Provides quick links to sub-modules.
   - Supports instant language switching (`中文 | English`) via Spring Boot `LocaleResolver` and `message.properties` bundles.

2. **Thymeleaf Features & CRUD Demo (`/demo/thymeleaf`)**:
   - Shows core Thymeleaf tags in action: text replacement (`th:text`/`th:placeholder`), conditionals (`th:if`/`th:block`), iterations (`th:each`), and form bindings (`th:object`/`th:field`).
   - Offers real-time CRUD operations against the SQLite database records.

3. **Doma 2 Dynamic SQL Console (`/demo/doma`)**:
   - **SQL Templates**: Showcases Doma 2's comment-based dynamic SQL (`/*%if */`, `/*%for */`). These templates are valid standard SQL, meaning they can be opened and run directly in database clients like DataGrip or DBeaver.
   - **Real-Time SQL Log Terminal**: Adjust query parameters in the left pane and execute. The right panel (Doma Core SQL Logger Console) displays live, colorized SQL generation and parameter binding logs, with the retrieved records displayed below.

---

## 🚀 Getting Started

### 1. Build and Run Tests
Clean, compile, and run tests using the Maven Wrapper:
```bash
# Windows
.\mvnw.cmd clean compile test

# Linux/Mac
./mvnw clean compile test
```

### 2. Launch the Web Server
```bash
# Launch the application
mvnw spring-boot:run
```
Access the dashboard at `http://localhost:8080`.

### 3. Run Offline Batch Job (Scenario 1)
```bash
# Launch in non-Web mode to trigger the batch processing job
mvnw spring-boot:run -Dspring-boot.run.arguments="--run-batch-job"
```

### 4. Simulating Failures & SMTP Logs
To inspect error handling and retry mechanisms locally, modify [mail.properties](src/main/resources/mail.properties):
- `mail.smtp.host` and `mail.smtp.port`: Set up mock or real SMTP configurations.
- `mail.debug.flag.smtp`: Set to `true` to enable verbose SMTP debugging logs.
- `mail.debug.flag.send`: Set to `FAIL_ALL` or `FAIL_RANDOM` to simulate network and mail server errors. This lets you observe message queue retries and task scheduler compensation in action.
