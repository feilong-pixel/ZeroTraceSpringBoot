package com.feilonglab.springboot.thymeleaf;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.feilonglab.springboot.model.entity.MailInfo;

/**
 * Thymeleaf 极简入门示例控制层。
 * 覆盖 Thymeleaf 的核心知识点，包括普通变量渲染、列表循环、条件渲染、表单绑定、超链接及重定向跳转。
 */
@Controller
@RequestMapping("/demo/thymeleaf")
public class ThymeleafDemoController {

    @Autowired
    private ThymeleafDemoService demoService;

    /**
     * 1. 展示邮件列表页面。
     * 展示知识点：
     *   - 显示普通数据 (th:text)
     *   - 遍历 List (th:each)
     *   - 条件显示 (th:if)
     *   - 块级条件渲染 (th:block th:if)
     *   - 超链接路径表达式 (th:href="@{...}")
     * 
     * 路由：GET /demo/thymeleaf
     * 返回值 "demo/thymeleaf/list" 映射到 resources/templates/demo/thymeleaf/list.html 模板。
     */
    @GetMapping
    public String list(Model model, @RequestParam(required = false, defaultValue = "true") Boolean login) {
        // 知识点 1：渲染普通文本变量到页面
        model.addAttribute("message", "Hello Thymeleaf! 欢迎来到 Spring Boot + Thymeleaf 极简入门示例。");

        // 知识点 2：将列表注入 Model，在前端利用 th:each 循环遍历
        model.addAttribute("mailList", demoService.getMailList());

        // 知识点 3：注入当前登录状态，用于演示非侵入式的 th:block 语法
        model.addAttribute("login", login);

        return "demo/thymeleaf/list";
    }

    /**
     * 2. 展示新增邮件表单页面。
     * 展示知识点：
     *   - 表单对象绑定准备 (Model 绑定新对象)
     * 
     * 路由：GET /demo/thymeleaf/new
     */
    @GetMapping("/new")
    public String showCreateForm(Model model) {
        // 知识点 4：初始化一个空对象并命名为 "mail"，用于表单中的 th:object 绑定
        model.addAttribute("mail", new MailInfo());
        model.addAttribute("title", "创建新邮件 (Create Mail)");
        return "demo/thymeleaf/form";
    }

    /**
     * 3. 展示编辑邮件表单页面。
     * 展示知识点：
     *   - 路径变量读取与对象绑定 (Model 绑定已有对象)
     * 
     * 路由：GET /demo/thymeleaf/edit/{id}
     */
    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable Long id, Model model) {
        MailInfo mail = demoService.getMailById(id);
        if (mail == null) {
            return "redirect:/demo/thymeleaf";
        }
        model.addAttribute("mail", mail);
        model.addAttribute("title", "编辑邮件 (Edit Mail)");
        return "demo/thymeleaf/form";
    }

    /**
     * 4. 保存/提交邮件数据表单。
     * 展示知识点：
     *   - 接收 ModelAttribute 表单提交数据
     *   - 重定向页面跳转 (redirect:)
     * 
     * 路由：POST /demo/thymeleaf/save
     */
    @PostMapping("/save")
    public String saveMail(@ModelAttribute("mail") MailInfo mail) {
        demoService.save(mail);
        // 知识点 5：利用 "redirect:" 前缀告诉 Spring MVC 进行客户端重定向，跳转回列表页
        return "redirect:/demo/thymeleaf";
    }

    /**
     * 5. 删除单封邮件记录。
     * 路由：GET /demo/thymeleaf/delete/{id}
     */
    @GetMapping("/delete/{id}")
    public String deleteMail(@PathVariable Long id) {
        demoService.delete(id);
        return "redirect:/demo/thymeleaf";
    }
}
