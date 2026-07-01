package com.feilonglab.springboot.thymeleaf;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

import com.feilonglab.springboot.model.entity.MailInfo;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Thymeleaf 极简入门示例单元与集成测试。
 * 验证控制层接口返回的视图模版路径、Model 数据绑定以及 CRUD 的逻辑流转。
 */
@SpringBootTest
public class ThymeleafDemoControllerTest {

    @Autowired
    private ThymeleafDemoController controller;

    @Autowired
    private ThymeleafDemoService service;

    @Test
    public void testListEndpoint() {
        Model model = new ConcurrentModel();
        // 模拟请求 /demo/thymeleaf?login=true
        String viewName = controller.list(model, true);
        
        // 验证返回的 Thymeleaf 模板路径 (对应 templates/demo/thymeleaf/list.html)
        assertEquals("demo/thymeleaf/list", viewName);
        
        // 验证 Model 属性装配
        assertTrue(model.containsAttribute("message"));
        assertTrue(model.containsAttribute("mailList"));
        assertEquals(true, model.getAttribute("login"));
        
        List<?> mailList = (List<?>) model.getAttribute("mailList");
        assertNotNull(mailList);
        assertFalse(mailList.isEmpty());
    }

    @Test
    public void testCreateFormEndpoint() {
        Model model = new ConcurrentModel();
        String viewName = controller.showCreateForm(model);
        
        // 验证模版路径
        assertEquals("demo/thymeleaf/form", viewName);
        
        // 验证绑定的新空对象
        assertTrue(model.containsAttribute("mail"));
        MailInfo boundMail = (MailInfo) model.getAttribute("mail");
        assertNotNull(boundMail);
        assertNull(boundMail.getMailId());
    }

    @Test
    public void testSaveAndRedirectFlow() {
        // 创建测试数据
        MailInfo testMail = new MailInfo();
        testMail.setToName("测试用户");
        testMail.setToEmail("test-user@example.com");
        testMail.setSubject("Thymeleaf测试主题");
        testMail.setContent("<p>测试正文</p>");
        testMail.setStatus(0);

        // 调用保存
        String redirectView = controller.saveMail(testMail);
        
        // 验证保存后进行了重定向
        assertEquals("redirect:/demo/thymeleaf", redirectView);

        // 验证服务层中已添加该数据
        List<MailInfo> currentList = service.getMailList();
        boolean hasSavedMail = currentList.stream()
                .anyMatch(m -> "Thymeleaf测试主题".equals(m.getSubject()));
        assertTrue(hasSavedMail);
    }
}
