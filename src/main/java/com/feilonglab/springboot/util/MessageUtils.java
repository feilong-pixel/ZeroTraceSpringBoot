package com.feilonglab.springboot.util;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

/**
 * 统一的国际化消息资源工具类。
 * 提供全局静态的 getMessage 方法，避免在各个类中重复编写相同的国际化逻辑。
 */
@Component
public class MessageUtils {

    private static MessageSource messageSource;

    /**
     * 构造函数，由 Spring 容器自动注入 MessageSource 并保存到静态域中。
     *
     * @param messageSource Spring MessageSource
     */
    public MessageUtils(MessageSource messageSource) {
        MessageUtils.messageSource = messageSource;
    }

    /**
     * 全局获取国际化消息方法。
     * <p>
     * 1. 优先使用 Spring 容器的 {@link MessageSource} 进行国际化解析。<br/>
     * 2. 如果 Spring 容器尚未加载或解析失败，自动降级为读取类路径下的 ResourceBundle，确保原生 POJO 类亦能正常使用。
     * </p>
     *
     * @param key 资源文件中的 Key
     * @param args 参数占位符填充值
     * @return 格式化后的本地化字符串，若找不到 key 则直接返回 key 自身
     */
    public static String getMessage(String key, Object... args) {
        if (messageSource != null) {
            try {
                return messageSource.getMessage(key, args, Locale.CHINA);
            } catch (Exception e) {
                // 解析失败时降级使用 ResourceBundle 兜底
            }
        }
        try {
            ResourceBundle bundle = ResourceBundle.getBundle("message", Locale.CHINA);
            String pattern = bundle.getString(key);
            return MessageFormat.format(pattern, args);
        } catch (Exception e) {
            return key;
        }
    }
}
