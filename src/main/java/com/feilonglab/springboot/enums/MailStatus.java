package com.feilonglab.springboot.enums;

import com.feilonglab.springboot.util.MessageUtils;

/**
 * 邮件发送状态枚举。
 */
public enum MailStatus {

    /** 未发送 */
    PENDING(0, "未发送"),
    
    /** 发送成功 */
    SUCCESS(1, "发送成功"),
    
    /** 发送失败 */
    FAILED(9, "发送失败");

    /** 状态码值 */
    private final int value;
    /** 状态描述 */
    private final String text;

    /** 构造函数 */
    MailStatus(int value, String text) {
        this.value = value;
        this.text = text;
    }

    /**
     * 获取状态码值。
     * @return 状态码值
     */
    public int getValue() {
        return value;
    }

    /**
     * 获取状态描述。
     * @return 状态描述
     */
    public String getText() {
        return text;
    }

    /**
     * 根据整数值获取对应的状态枚举。
     *
     * @param value 状态码值
     * @return 状态枚举实例
     * @throws IllegalArgumentException 如果传入未知的状态值
     */
    public static MailStatus fromValue(int value) {
        for (MailStatus status : MailStatus.values()) {
            if (status.getValue() == value) {
                return status;
            }
        }
        throw new IllegalArgumentException(MessageUtils.getMessage("mail.status.unknown", value));
    }
}
