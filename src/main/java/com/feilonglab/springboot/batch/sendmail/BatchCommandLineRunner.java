package com.feilonglab.springboot.batch.sendmail;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.feilonglab.springboot.util.MessageUtils;

/**
 * 命令行启动批处理任务的 Runner。
 * 支持在命令行通过参数启动指定的 Spring Batch 作业。
 * 例如：--run-batch-job=sendMailJob
 */
@Component
public class BatchCommandLineRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(BatchCommandLineRunner.class);

    /** Spring Batch 作业操作员 */
    private final JobOperator jobOperator;

    /** 存储系统中所有已注册 Job 实例的 Map，键为 Job 的 Bean 名称 */
    private final Map<String, Job> jobMap;

    /**
     * 构造函数，由 Spring 容器自动注入所需的 JobOperator 以及所有已注册的 Job Bean。
     *
     * @param jobOperator 作业操作员实例
     * @param jobMap 包含所有作业 Bean 的 Map 映射
     */
    public BatchCommandLineRunner(JobOperator jobOperator, Map<String, Job> jobMap) {
        this.jobOperator = jobOperator;
        this.jobMap = jobMap;
    }

    /**
     * 命令行运行入口方法。
     * 解析传入的命令行参数（--run-batch-job 或 --run-batch-job=jobName），
     * 如果匹配则拉起对应的批处理 Job 进行同步执行，并在执行结束后退出 JVM。
     *
     * @param args 命令行输入参数数组
     * @throws Exception 执行过程中发生的异常
     */
    @Override
    public void run(String... args) throws Exception {
        String targetJobName = null;
        boolean shouldRun = false;

        // 解析命令行参数
        for (String arg : args) {
            if ("--run-batch-job".equals(arg)) {
                shouldRun = true;
                targetJobName = "sendMailJob"; // 默认执行发送邮件作业
            } else if (arg != null && arg.startsWith("--run-batch-job=")) {
                shouldRun = true;
                targetJobName = arg.substring("--run-batch-job=".length());
            }
        }

        if (!shouldRun) {
            logger.info(MessageUtils.getMessage("runner.no.arg"));
            return;
        }

        if (targetJobName == null || targetJobName.trim().isEmpty()) {
            logger.error(MessageUtils.getMessage("runner.job.name.empty", jobMap.keySet()));
            System.exit(1);
            return;
        }

        // 从 Map 中获取对应的作业实例
        Job job = jobMap.get(targetJobName);
        if (job == null) {
            logger.error(MessageUtils.getMessage("runner.job.not.found", targetJobName, jobMap.keySet()));
            System.exit(1);
            return;
        }

        logger.info(MessageUtils.getMessage("runner.job.start", targetJobName));

        try {
            // 使用当前系统时间戳作为 Job 参数，确保每次运行都是全新的 JobInstance
            JobParameters params = new JobParametersBuilder()
                    .addLong("time", System.currentTimeMillis())
                    .toJobParameters();

            JobExecution execution = jobOperator.run(job, params);
            logger.info(MessageUtils.getMessage("runner.job.complete", targetJobName, execution.getStatus()));

            if (execution.getStatus().isUnsuccessful()) {
                logger.error(MessageUtils.getMessage("runner.job.failed", targetJobName, execution.getAllFailureExceptions()));
                System.exit(1);
            } else {
                logger.info(MessageUtils.getMessage("runner.job.success", targetJobName));
                System.exit(0);
            }
        } catch (Exception e) {
            logger.error(MessageUtils.getMessage("runner.job.uncaught.error", targetJobName), e);
            System.exit(1);
        }
    }
}
