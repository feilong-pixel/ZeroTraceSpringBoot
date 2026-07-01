# ZeroTraceSpringBoot: Spring Boot Concurrency & Asynchronous Task Learning Demo

[中文](README.md) 

This project is a runnable learning demo and sample based on **Spring Boot**. It provides an intuitive comparison and usage showcase for **Spring Batch Processing**, **Embedded ActiveMQ Artemis Asynchronous Queues**, **ThreadPool Concurrency**, and **Spring Task Scheduler Cron-based compensation**. It also demonstrates how transaction and connection management are handled in a lightweight **SQLite** database environment.

---

## 🚀 Core Tech Stack

- **Framework Core**: Spring Boot 3.x, Spring MVC, Spring AOP
- **Asynchronous & Concurrency Management**:
  - **Spring Batch**: Demonstrates batch processing workflows.
  - **Spring JMS (ActiveMQ Artemis)**: Demonstrates asynchronous message queuing, load shaving, and retries for delivery.
  - **Spring TaskExecutor**: Custom thread pool demonstrating local concurrent sending and overflow rejection handling.
  - **Spring Task Scheduler**: Cron-based scheduling to trigger scanning and retries within specified time windows.
- **Persistence Layer**: **Doma 2** (Type-safe Java SQL framework) ensures clean separation between SQL files and Java code.
- **Database**: **SQLite**, configured with **WAL (Write-Ahead Logging)** mode and busy timeout wait duration, providing solid support for concurrent write operations.

---

## 🛠️ Five Core Demo Scenarios

### 1. Scenario 1: Spring Batch Scheduled Task
- **Process**: Reads pending emails via `Reader`, processes/formats them via `Processor`, and writes status updates and transaction logs using short transactions via `Writer`.
- **Trigger**: Can be triggered externally via command-line arguments (independent of the Web container's lifecycle), perfect for routine offline batch processing jobs.

### 2. Scenario 2: ActiveMQ Artemis Asynchronous Queue & Retry Compensation
- **Process**: When an API receives a send request, it first saves the email to SQLite and pushes the `mail_id` to the ActiveMQ Artemis queue. An asynchronous JMS listener consumes the message and invokes the SMTP client to deliver it.
- **Retry**: In case of network errors or SMTP failures, the exception is caught, retry counts are incremented, and status changes to `FAILED` (9) while writing logs. These are subsequently scanned and retried by Scenario 4.

### 3. Scenario 3: Spring ThreadPool Concurrency & Overflow Protection
- **Process**: Demonstrates concurrent sending using `@Async("mailThreadPoolExecutor")` powered by a custom thread pool configured with Core Threads: 2, Max Threads: 5, and Queue Capacity: 10.
- **Flow Control**: If high-volume requests exceed capacity, an overflow occurs and throws `RejectedExecutionException`. The application catches this, marks all overflowed emails as `FAILED` (9) in the database with log entries, and immediately returns a 500 error response to the client.

### 4. Scenario 4: Spring Task Scheduler Scanning & Retry Compensation
- **Process**: Configured using `@Scheduled(cron = "${mail.scheduler.cron:0 */10 8-23 * * MON-FRI}")`, which automatically runs **every 10 minutes from 8:00 AM to 11:00 PM on weekdays**.
- **Retry Limit**: Scans the database for emails with `status = 0` (unsent) or `status = 9` (failed, and retry count < 3). It reuses a single SMTP client connection to send them in batches, ensuring no unsent emails are lost even if the server restarts.
- **Interfaces**: Provides a manual trigger API and status checking endpoint.

### 5. Scenario 5: SQLite Multi-Transaction Isolation Control
- **Process**: SQLite database files throw `SQLITE_BUSY` when a single large transaction holds locks on multiple modified rows.
- **Control**: In this project, when handling multiple emails, the reading, SMTP interaction, status updating, and log saving for each email are encapsulated within their own short transaction (`Propagation.REQUIRES_NEW`), releasing locks immediately to ensure SQLite safety.

---

## ⚙️ Three Implementations of SmtpClient Configuration Loading

To demonstrate different coding practices across Java SE and Spring environments, the project demonstrates three ways to load properties from `mail.properties`:

1. **Classic Java Properties Loading (For non-Spring POJOs)**
   - **Class**: [basic/SmtpClient.java](src/main/java/com/feilonglab/smtp/basic/SmtpClient.java)
   - **Mechanism**: Employs JDK native `java.util.Properties` loading the classpath file via ClassLoader streams:
     ```java
     Properties props = new Properties();
     try (InputStream in = SmtpClient.class.getResourceAsStream("/mail.properties")) {
         props.load(in);
     }
     ```
   - **Pros**: 100% independent of Spring, runnable in standard Java SE apps, highly portable.

2. **ResourceBundle Binding (For classic Java Resource Binding)**
   - **Class**: [mq/SmtpClient.java](src/main/java/com/feilonglab/smtp/mq/SmtpClient.java)
   - **Mechanism**: Employs `java.util.ResourceBundle.getBundle("mail")` inside class constructors as a fallback mechanism.
   - **Pros**: Standard Java resource bundling mechanism, commonly used for localization, runs fine without Spring bean contexts.

3. **Spring Declarative Annotation Injection (For Spring Managed Components)**
   - **Class**: [threadpool/SmtpClient.java](src/main/java/com/feilonglab/smtp/threadpool/SmtpClient.java), [scheduler/SmtpClient.java](src/main/java/com/feilonglab/smtp/scheduler/SmtpClient.java)
   - **Mechanism**: Configures `@Component` and `@PropertySource("classpath:mail.properties")` on the class, binding fields using `@Value("${mail.smtp.host}")` during Bean initialization.
   - **Pros**: Idiomatic Spring configuration pattern, fits cleanly with Prototype Scope beans.

---

## 📂 Core API Reference

### 1. ThreadPool Concurrency Send Test (Scenario 3)
- **Endpoint**: `POST /threadpool/sendmail`
- **Body** (JSON Array):
  ```json
  [
    {
      "toName": "John Doe",
      "toEmail": "john.doe@example.com",
      "subject": "Concurrency Test Mail 1",
      "content": "Hello World!"
    },
    {
      "toName": "Jane Doe",
      "toEmail": "jane.doe@example.com",
      "subject": "Concurrency Test Mail 2",
      "content": "Hello Code!"
    }
  ]
  ```

### 2. Task Scheduler Status (Scenario 4)
- **Endpoint**: `GET /scheduler/status`
- **Response** (Example):
  ```json
  {
    "success": true,
    "enabled": true,
    "cron": "0 */10 8-23 * * MON-FRI",
    "pendingOrFailedCount": 3
  }
  ```

### 3. Manually Trigger Task Scheduler (Scenario 4)
- **Endpoint**: `POST /scheduler/trigger`
- **Description**: Triggers a manual round of email sending for unsent or failed emails (under retry limits) and returns the count.

---

## 🌐 Web UI & Demos

In addition to the backend demonstration scenarios, the project contains an interactive, bilingual front-end console:

1. **Dynamic Language (i18n) Switching**:
   - The entire Web UI (Main Mail Center, Thymeleaf Demo, and Doma SQL Interactive Demo) supports dynamic `中文 | English` switching.
   - Driven by Spring Boot `LocaleResolver` session resolution and the `message.properties` / `message_zh.properties` bundles.

2. **ZeroTrace Main Center (`/`)**:
   - Displays SMTP configurations (host, port, username, sender alias) and provides a form to send rich-text/HTML emails.
   - Includes **Feature Navigation** links at the top-right of the main pane to navigate to feature demo pages.

3. **Thymeleaf Core Demo (`/demo/thymeleaf`)**:
   - **Thymeleaf Features**: Showcases text variable parsing (`th:text`/`th:placeholder`), conditional checks (`th:if`/`th:block`), iteration (`th:each`), object bindings (`th:object`/`th:field`), and URL dynamic parameters (`th:href`).
   - **Interactive CRUD**: Allows developers to view, add, edit, and delete email mock records in SQLite directly from the UI.

4. **Doma 2 Dynamic SQL Interactive Console (`/demo/doma`)**:
   - **Comment-based SQL Templates**: Showcases Doma 2's signature feature. Templates are standard SQL comments (e.g., `/*%if */`, `/*%for */`, date filtering) allowing SQL files to remain executable by database clients (DBeaver, DataGrip).
   - **Interactive Console**: Enter parameters on the left and click "Execute Query & Print SQL". The **Doma Core SQL Logger Console** on the right prints colorized, formatted SQL queries and bound parameters, and renders matching database records below.

---

## ⚙️ Quick Start & Run

### 1. Compile and Run Integration Tests
Execute Maven Wrapper to clean, compile, and run unit tests:
```bash
# Windows PowerShell/Command Prompt
.\mvnw.cmd clean compile test

# Linux / Mac
./mvnw clean compile test
```

### 2. Run Web Service
Start the application:
```bash
# Windows PowerShell/Command Prompt
.\mvnw.cmd spring-boot:run

# Linux / Mac / Git Bash
./mvnw spring-boot:run
```
Open your browser and navigate to: `http://localhost:8080`.

### 3. Local Mail Simulation Configuration
Modify `src/main/resources/mail.properties` to set custom SMTP parameters or toggle simulated network/SMTP failures to observe error handling and retry logic.
