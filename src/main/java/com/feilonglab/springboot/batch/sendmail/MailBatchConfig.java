package com.feilonglab.springboot.batch.sendmail;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import org.springframework.context.annotation.Lazy;

import com.feilonglab.smtp.basic.SmtpClient;
import com.feilonglab.springboot.enums.MailStatus;
import com.feilonglab.springboot.model.dao.MailInfoDao;
import com.feilonglab.springboot.model.dao.MailSendLogDao;
import com.feilonglab.springboot.model.entity.MailInfo;
import com.feilonglab.springboot.model.entity.MailSendLog;
import com.feilonglab.springboot.util.MessageUtils;


/**
 * Spring Batch 批处理任务配置类。
 * 定义了发送邮件作业 (sendMailJob) 及对应步骤 (sendMailStep)。
 * 采用 Chunk(1) 模式，使得每封邮件的处理与写入均在独立的短事务中进行。
 */
@Configuration
public class MailBatchConfig {

    /**
     * 待发送邮件读取器 Bean。
     * 使用 @StepScope 保证每次步骤执行时都会从数据库重新查询。
     * 采用自定义 ItemReader 以通过 Doma MailInfoDao 获取数据，去除了原生的 SQL 硬编码。
     *
     * @param mailInfoDao 邮件数据访问接口
     * @return 邮件实体信息读取器
     */
    @Bean
    @StepScope
    public ItemReader<MailInfo> mailItemReader(@Lazy MailInfoDao mailInfoDao) {
        // 查询所有未发送或发送失败的邮件，最多重试 3 次
        List<MailInfo> unsentMails = mailInfoDao.selectUnsentOrFailed(3);

        // 返回一个自定义的 ItemReader 实现，按顺序读取待发送邮件列表
        return new ItemReader<MailInfo>() {
            private int index = 0;

            @Override
            public MailInfo read() {
                if (index < unsentMails.size()) {
                    return unsentMails.get(index++);
                }
                return null;
            }
        };
    }

    /**
     * 邮件发送处理器 Bean。
     * 针对每一封待发送邮件，调用 SMTP 客户端执行具体投递操作。
     * 若投递成功或失败，动态记录相关属性并封装成 MailProcessingResult 返回。
     *
     * @return 邮件处理器实例
     */
    @Bean
    public ItemProcessor<MailInfo, MailProcessingResult> mailItemProcessor() {
        // 返回一个自定义的 ItemProcessor 实现，处理每一封邮件的发送逻辑
        return mailInfo -> {
            int attempt = mailInfo.getRetryCount() + 1;
            LocalDateTime processedAt = LocalDateTime.now();
            
            try (SmtpClient client = new SmtpClient()) {
                client.open();
                // 调用邮件发送客户端进行投递，演示模式下会将发送详情输出至日志
                client.sendMail(mailInfo.getToName(), mailInfo.getToEmail(), mailInfo.getSubject(), mailInfo.getContent());
                
                // 投递成功，更新邮件状态为成功并记录发送时间
                mailInfo.setStatus(MailStatus.SUCCESS.getValue()); // 发送成功
                mailInfo.setRetryCount(attempt);
                mailInfo.setSentAt(processedAt);
                return new MailProcessingResult(mailInfo, true, null, processedAt, attempt);
            } catch (Exception e) {

                // 投递失败，更新邮件状态为失败并记录发送时间
                mailInfo.setStatus(MailStatus.FAILED.getValue()); // 发送失败
                mailInfo.setRetryCount(attempt);
                mailInfo.setSentAt(null);
                
                // 捕获异常并封装错误原因，限制长度以防止日志过长
                String errorReason = e.getMessage();
                if (errorReason != null && errorReason.length() > 500) {
                    errorReason = errorReason.substring(0, 500); // 截断过长的异常错误描述
                }

                // 返回处理结果，包含邮件信息、发送状态、错误原因、处理时间和尝试次数
                return new MailProcessingResult(mailInfo, false, errorReason, processedAt, attempt);
            }
        };
    }

    /**
     * 邮件发送写出器 Bean。
     * 在每一批次（Chunk）发送动作结束后触发调用。
     * 利用 Doma 更新 mail_info 数据记录，并向 mail_send_log 表记录单次投递流水。
     *
     * @param mailInfoDao 邮件数据访问接口
     * @param mailSendLogDao 邮件发送日志数据访问接口
     * @return 邮件写出器实例
     */
    @Bean
    public ItemWriter<MailProcessingResult> mailItemWriter(@Lazy MailInfoDao mailInfoDao, @Lazy MailSendLogDao mailSendLogDao) {
        return chunk -> {
            for (MailProcessingResult result : chunk) {
                MailInfo mail = result.getMailInfo();
                
                // 使用 Doma Dao 更新邮件状态（自动更新 optimistic lock version）
                int updated = mailInfoDao.update(mail);
                if (updated == 0) {
                    String errorMsg = MessageUtils.getMessage("batch.db.optimistic.lock", 
                            mail.getMailId(), mail.getVersion());
                    throw new org.springframework.dao.OptimisticLockingFailureException(errorMsg);
                }

                // 使用 Doma Dao 插入发送历史日志
                MailSendLog sendLog = new MailSendLog();
                sendLog.setMailId(mail.getMailId());
                sendLog.setSentAt(result.getProcessedAt());
                sendLog.setStatus(result.isSuccess() ? MailStatus.SUCCESS.getValue() : MailStatus.FAILED.getValue());
                sendLog.setAttempt(result.getAttempt());
                sendLog.setErrorReason(result.getErrorReason());
                
                // 插入发送日志
                mailSendLogDao.insert(sendLog);
            }
        };
    }

    /**
     * 配置邮件发送步骤 (Step)。
     * 采用 Chunk(1) 模式以保证单封隔离执行与更新。
     *
     * @param jobRepository 批处理元数据存储库
     * @param transactionManager 事务管理器
     * @param reader 读取器
     * @param processor 处理器
     * @param writer 写出器
     * @return 批处理 Step 实例
     */
    @Bean
    public Step sendMailStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                             ItemReader<MailInfo> reader, ItemProcessor<MailInfo, MailProcessingResult> processor,
                             ItemWriter<MailProcessingResult> writer) {
        // 使用 StepBuilder 构建发送邮件步骤，指定读取器、处理器和写出器
        return new StepBuilder("sendMailStep", jobRepository)
                .<MailInfo, MailProcessingResult>chunk(1)
                .transactionManager(transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .build();
    }

    /**
     * 配置整个邮件发送作业 (Job)。
     *
     * @param jobRepository 批处理元数据存储库
     * @param sendMailStep 执行步骤
     * @return 批处理 Job 实例
     */
    @Bean
    public Job sendMailJob(JobRepository jobRepository, Step sendMailStep) {
        // 使用 JobBuilder 构建发送邮件作业，指定执行步骤
        return new JobBuilder("sendMailJob", jobRepository)
                .start(sendMailStep)
                .build();
    }
}
