package com.feilonglab.springboot.web.config;

import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;

/**
 * Web MVC Configuration for Internationalization (i18n).
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    // 从配置文件中获取默认语言环境
    @Value("${app.locale:en}")
    private String defaultLocaleStr;

    /**
     * Session Locale Resolver <br/>
     * 作用：管理 Session 中的语言环境 <br/>
     * 默认语言：从配置文件中获取
     * 
     * @return
     */
    @Bean
    public LocaleResolver localeResolver() {
        SessionLocaleResolver resolver = new SessionLocaleResolver();
        Locale defaultLocale = Locale.ENGLISH;
        if (defaultLocaleStr != null && !defaultLocaleStr.isEmpty()) {
            try {
                defaultLocale = StringUtils.parseLocaleString(defaultLocaleStr);
            } catch (Exception e) {
                // Keep default
            }
        }
        resolver.setDefaultLocale(defaultLocale);
        return resolver;
    }

    /**
     * 语言环境切换拦截器 <br/>
     * 作用：拦截请求，改变语言环境 <br/>
     * 
     * @return
     */
    @Bean
    public LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
        interceptor.setParamName("lang"); // Parameter in URL to change language, e.g., ?lang=zh_CN
        return interceptor;
    }

    /**
     * 添加拦截器 <br/>
     * 
     * 作用：注册拦截器 <br/>
     * 
     * @param registry 拦截器注册表
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(localeChangeInterceptor());
    }
}
