package com.feilonglab.springboot.batch.sendmail;

import java.time.LocalDateTime;
import com.feilonglab.springboot.model.entity.MailInfo;

/**
 * 邮件发送处理结果类，供 Spring Batch Reader/Processor/Writer 阶段传递状态与日志信息使用。
 */
public class MailProcessingResult {
    
    /** 发送的邮件数据实体信息 */
    private MailInfo mailInfo;
    
    /** 发送动作是否成功（true: 成功, false: 失败） */
    private boolean success;
    
    /** 错误异常的文本描述信息 */
    private String errorReason;
    
    /** 邮件处理开始的时间戳 */
    private LocalDateTime processedAt;
    
    /** 本次处理的尝试次数 */
    private int attempt;

    /**
     * 默认无参构造函数。
     */
    public MailProcessingResult() {
    }

    /**
     * 全参构造函数，方便快速创建结果对象。
     *
     * @param mailInfo 邮件数据实体
     * @param success 是否发送成功
     * @param errorReason 错误描述原因
     * @param processedAt 处理发生的时间
     * @param attempt 本次发送属于第几次重试
     */
    public MailProcessingResult(MailInfo mailInfo, boolean success, String errorReason, LocalDateTime processedAt, int attempt) {
        this.mailInfo = mailInfo;
        this.success = success;
        this.errorReason = errorReason;
        this.processedAt = processedAt;
        this.attempt = attempt;
    }

    /**
     * 获取发送的邮件数据实体信息。
     *
     * @return 邮件实体信息
     */
    public MailInfo getMailInfo() {
        return mailInfo;
    }

    /**
     * 设置发送的邮件数据实体信息。
     *
     * @param mailInfo 邮件实体信息
     */
    public void setMailInfo(MailInfo mailInfo) {
        this.mailInfo = mailInfo;
    }

    /**
     * 获取发送动作是否成功。
     *
     * @return true表示成功，false表示失败
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * 设置发送动作是否成功。
     *
     * @param success 是否发送成功
     */
    public void setSuccess(boolean success) {
        this.success = success;
    }

    /**
     * 获取错误异常的文本描述信息。
     *
     * @return 错误描述原因
     */
    public String getErrorReason() {
        return errorReason;
    }

    /**
     * 设置错误异常的文本描述信息。
     *
     * @param errorReason 错误描述原因
     */
    public void setErrorReason(String errorReason) {
        this.errorReason = errorReason;
    }

    /**
     * 获取邮件处理开始的时间戳。
     *
     * @return 处理时间
     */
    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    /**
     * 设置邮件处理开始的时间戳。
     *
     * @param processedAt 处理时间
     */
    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }

    /**
     * 获取本次处理的尝试次数。
     *
     * @return 重试计数
     */
    public int getAttempt() {
        return attempt;
    }

    /**
     * 设置本次处理的尝试次数。
     *
     * @param attempt 重试计数
     */
    public void setAttempt(int attempt) {
        this.attempt = attempt;
    }

    @Override
    public String toString() {
        return "MailProcessingResult{" +
                "mailId=" + (mailInfo != null ? mailInfo.getMailId() : null) +
                ", success=" + success +
                ", attempt=" + attempt +
                ", errorReason='" + errorReason + '\'' +
                '}';
    }
}
