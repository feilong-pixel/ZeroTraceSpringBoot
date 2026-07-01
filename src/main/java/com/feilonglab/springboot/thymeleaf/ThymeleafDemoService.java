package com.feilonglab.springboot.thymeleaf;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Service;
import com.feilonglab.springboot.model.entity.MailInfo;

/**
 * Thymeleaf 极简入门示例服务层。
 * 在内存中模拟邮件数据的增删改查，避免依赖真实数据库，使演示更为聚焦。
 */
@Service
public class ThymeleafDemoService {

    private final ConcurrentHashMap<Long, MailInfo> mailStorage = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(100);

    public ThymeleafDemoService() {
        // 初始化一些 mock 邮件数据
        MailInfo mail1 = new MailInfo();
        mail1.setMailId(1L);
        mail1.setStatus(0); // 未发送
        mail1.setToName("张三");
        mail1.setToEmail("zhangsan@example.com");
        mail1.setSubject("Thymeleaf 极简入门指南");
        mail1.setContent("<p>欢迎学习 Thymeleaf！这是一个非常直观且易于维护的 HTML5 模板引擎。</p>");
        mail1.setRetryCount(0);
        mailStorage.put(mail1.getMailId(), mail1);

        MailInfo mail2 = new MailInfo();
        mail2.setMailId(2L);
        mail2.setStatus(1); // 发送成功
        mail2.setToName("李四");
        mail2.setToEmail("lisi@example.com");
        mail2.setSubject("Spring Boot 与 Thymeleaf 深度整合");
        mail2.setContent("<p>本邮件为演示数据，已成功发送。</p>");
        mail2.setRetryCount(1);
        mailStorage.put(mail2.getMailId(), mail2);
    }

    /**
     * 获取所有邮件列表。
     */
    public List<MailInfo> getMailList() {
        return new ArrayList<>(mailStorage.values());
    }

    /**
     * 根据 ID 获取邮件。
     */
    public MailInfo getMailById(Long id) {
        return mailStorage.get(id);
    }

    /**
     * 保存或更新邮件。
     */
    public void save(MailInfo mail) {
        if (mail.getMailId() == null) {
            mail.setMailId(idGenerator.incrementAndGet());
        }
        if (mail.getStatus() == null) {
            mail.setStatus(0);
        }
        if (mail.getRetryCount() == null) {
            mail.setRetryCount(0);
        }
        mailStorage.put(mail.getMailId(), mail);
    }

    /**
     * 根据 ID 删除邮件。
     */
    public void delete(Long id) {
        mailStorage.remove(id);
    }
}
