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
 * 邮件信息实体类 (Seasar Doma 实体)。
 * 映射数据库中的 mail_info 表。
 */
@Entity(naming = NamingType.SNAKE_LOWER_CASE)
@Table(name = "mail_info")
public class MailInfo {

    /** 邮件唯一ID，自增主键 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long mailId;

    /** 邮件发送状态：0(未发送)、1(发送成功)、9(发送失败) */
    private Integer status;

    /** 重试次数 */
    private Integer retryCount;

    /** 发送成功时间 */
    private LocalDateTime sentAt;

    /** 收件人显示姓名 */
    private String toName;

    /** 收件人电子邮箱地址 */
    private String toEmail;

    /** 邮件主题 */
    private String subject;

    /** 邮件正文内容（支持 HTML） */
    private String content;

    /** 乐观锁版本号，每次更新时递增，防止并发冲突 */
    @Version
    private Integer version;

    /**
     * 默认构造函数。
     */
    public MailInfo() {
    }

    /**
     * 获取邮件唯一ID。
     *
     * @return 邮件ID
     */
    public Long getMailId() {
        return mailId;
    }

    /**
     * 设置邮件唯一ID。
     *
     * @param mailId 邮件ID
     */
    public void setMailId(Long mailId) {
        this.mailId = mailId;
    }

    /**
     * 获取邮件发送状态：0(未发送)、1(发送成功)、9(发送失败)。
     *
     * @return 邮件状态值
     */
    public Integer getStatus() {
        return status;
    }

    /**
     * 设置邮件发送状态：0(未发送)、1(发送成功)、9(发送失败)。
     *
     * @param status 邮件状态值
     */
    public void setStatus(Integer status) {
        this.status = status;
    }

    /**
     * 获取重试次数。
     *
     * @return 重试计数
     */
    public Integer getRetryCount() {
        return retryCount;
    }

    /**
     * 设置重试次数。
     *
     * @param retryCount 重试计数
     */
    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    /**
     * 获取发送成功时间。
     *
     * @return 发送成功时刻的时间对象
     */
    public LocalDateTime getSentAt() {
        return sentAt;
    }

    /**
     * 设置发送成功时间。
     *
     * @param sentAt 发送成功时刻的时间对象
     */
    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }

    /**
     * 获取收件人显示姓名。
     *
     * @return 收件人姓名
     */
    public String getToName() {
        return toName;
    }

    /**
     * 设置收件人显示姓名。
     *
     * @param toName 收件人姓名
     */
    public void setToName(String toName) {
        this.toName = toName;
    }

    /**
     * 获取收件人电子邮箱地址。
     *
     * @return 收件人邮箱地址
     */
    public String getToEmail() {
        return toEmail;
    }

    /**
     * 设置收件人电子邮箱地址。
     *
     * @param toEmail 收件人邮箱地址
     */
    public void setToEmail(String toEmail) {
        this.toEmail = toEmail;
    }

    /**
     * 获取邮件主题。
     *
     * @return 邮件主题内容
     */
    public String getSubject() {
        return subject;
    }

    /**
     * 设置邮件主题。
     *
     * @param subject 邮件主题内容
     */
    public void setSubject(String subject) {
        this.subject = subject;
    }

    /**
     * 获取邮件正文内容（HTML格式）。
     *
     * @return 邮件正文HTML内容
     */
    public String getContent() {
        return content;
    }

    /**
     * 设置邮件正文内容（HTML格式）。
     *
     * @param content 邮件正文HTML内容
     */
    public void setContent(String content) {
        this.content = content;
    }

    /**
     * 获取乐观锁版本号。
     *
     * @return 当前版本号
     */
    public Integer getVersion() {
        return version;
    }

    /**
     * 设置乐观锁版本号。
     *
     * @param version 最新版本号
     */
    public void setVersion(Integer version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "MailInfo{" +
                "mailId=" + mailId +
                ", status=" + status +
                ", retryCount=" + retryCount +
                ", sentAt=" + sentAt +
                ", toName='" + toName + '\'' +
                ", toEmail='" + toEmail + '\'' +
                ", subject='" + subject + '\'' +
                ", version=" + version +
                '}';
    }
}
