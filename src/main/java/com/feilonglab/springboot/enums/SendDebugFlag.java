package com.feilonglab.springboot.enums;

import com.feilonglab.springboot.util.MessageUtils;

/**
 * メール 发送调试标志枚举。
 */
public enum SendDebugFlag {

    /** 表示关闭（不会输出错误信息） */
    SEND_SUCCESS("0", "表示关闭（不会输出错误信息）"),

    /** 表示开启（会输出错误信息） */
    SEND_FAILURE("1", "表示开启（会输出错误信息）");

    /** 标志值 */
    private final String value;
    /** 描述文本 */
    private final String text;

    /** 构造函数 */
    SendDebugFlag(String value, String text) {
        this.value = value;
        this.text = text;
    }

    /**
     * 获取标志值。
     * 
     * @return 标志值
     */
    public String getValue() {
        return value;
    }

    /**
     * 获取描述文本。
     * 
     * @return 描述文本
     */
    public String getText() {
        return text;
    }

    /**
     * 根据字符串标志值获取对应的调试标志枚举。
     *
     * @param value 标志值（"0" 或 "1"）
     * @return 调试标志枚举实例
     * @throws IllegalArgumentException 如果传入未知的调试标志值
     */
    public static SendDebugFlag fromValue(String value) {
        for (SendDebugFlag flag : SendDebugFlag.values()) {
            if (flag.getValue().equals(value)) {
                return flag;
            }
        }
        throw new IllegalArgumentException(MessageUtils.getMessage("smtp.debug.flag.unknown", value));
    }
}
