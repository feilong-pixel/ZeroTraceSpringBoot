package com.feilonglab.springboot.model.entity;

import java.time.LocalDateTime;
import org.seasar.doma.Entity;
import org.seasar.doma.Table;
import org.seasar.doma.Id;
import org.seasar.doma.GeneratedValue;
import org.seasar.doma.GenerationType;
import org.seasar.doma.Version;
import org.seasar.doma.jdbc.entity.NamingType;

/**
 * 邮件发送日志实体类 (Seasar Doma 实体)。
 * 映射数据库中的 mail_send_log 表。
 */
@Entity(naming = NamingType.SNAKE_LOWER_CASE)
@Table(name = "mail_send_log")
public class MailSendLog {

    /** 日志唯一ID，自增主键 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long logId;

    /** 关联的邮件信息ID */
    private Long mailId;

    /** 处理/发送时间 */
    private LocalDateTime sentAt;

    /** 处理/发送状态：1(发送成功)、9(发送失败) */
    private Integer status;

    /** 发送重试轮次序号（第几次尝试发送） */
    private Integer attempt;

    /** 发送失败的异常堆栈或错误描述原因 */
    private String errorReason;

    /** 乐观锁版本号 */
    @Version
    private Integer version;

    /**
     * 默认构造函数。
     */
    public MailSendLog() {
    }

    /**
     * 获取日志唯一ID。
     *
     * @return 日志ID
     */
    public Long getLogId() {
        return logId;
    }

    /**
     * 设置日志唯一ID。
     *
     * @param logId 日志ID
     */
    public void setLogId(Long logId) {
        this.logId = logId;
    }

    /**
     * 获取关联的邮件信息ID。
     *
     * @return 邮件ID
     */
    public Long getMailId() {
        return mailId;
    }

    /**
     * 设置关联的邮件信息ID。
     *
     * @param mailId 邮件ID
     */
    public void setMailId(Long mailId) {
        this.mailId = mailId;
    }

    /**
     * 获取处理/发送时间。
     *
     * @return 发送时刻时间对象
     */
    public LocalDateTime getSentAt() {
        return sentAt;
    }

    /**
     * 设置处理/发送时间。
     *
     * @param sentAt 发送时刻时间对象
     */
    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }

    /**
     * 获取处理/发送状态：1(发送成功)、9(发送失败)。
     *
     * @return 发送状态码
     */
    public Integer getStatus() {
        return status;
    }

    /**
     * 设置处理/发送状态：1(发送成功)、9(发送失败)。
     *
     * @param status 发送状态码
     */
    public void setStatus(Integer status) {
        this.status = status;
    }

    /**
     * 获取发送重试轮次序号（第几次尝试发送）。
     *
     * @return 尝试次数
     */
    public Integer getAttempt() {
        return attempt;
    }

    /**
     * 设置发送重试轮次序号（第几次尝试发送）。
     *
     * @param attempt 尝试次数
     */
    public void setAttempt(Integer attempt) {
        this.attempt = attempt;
    }

    /**
     * 获取发送失败的异常堆栈或错误描述原因。
     *
     * @return 错误描述原因
     */
    public String getErrorReason() {
        return errorReason;
    }

    /**
     * 设置发送失败的异常堆栈或错误描述原因。
     *
     * @param errorReason 错误描述原因
     */
    public void setErrorReason(String errorReason) {
        this.errorReason = errorReason;
    }

    /**
     * 获取乐观锁版本号。
     *
     * @return 乐观锁版本号
     */
    public Integer getVersion() {
        return version;
    }

    /**
     * 设置乐观锁版本号。
     *
     * @param version 乐观锁版本号
     */
    public void setVersion(Integer version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "MailSendLog{" +
                "logId=" + logId +
                ", mailId=" + mailId +
                ", sentAt=" + sentAt +
                ", status=" + status +
                ", attempt=" + attempt +
                ", version=" + version +
                '}';
    }
}
