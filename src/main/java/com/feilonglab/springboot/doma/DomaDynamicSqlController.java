package com.feilonglab.springboot.doma;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Doma 动态 SQL 示例 Controller。
 * 
 * 提供独立 Demo 的 API 接口及前端测试页面。
 */
@Controller
@RequestMapping("/demo/doma")
public class DomaDynamicSqlController {

    @Autowired
    private DomaDynamicSqlDemoService demoService;

    /**
     * 渲染 Doma 动态
     * 
     * SQL 交互式网页界面。
     */
    @GetMapping
    public String index() {
        return "doma_demo";
    }

    /**
     * 示例 1：if条件与参数绑定。
     * 
     * GET /demo/doma/mail?status=0&keyword=test
     */
    @GetMapping("/mail")
    public ResponseEntity<?> searchMail(@RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword, @RequestParam(required = false) String format,
            @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String acceptHeader) {

        DomaDynamicSqlDemoService.SearchResult result = demoService.search(status, keyword);
        return buildResponse("Doma Dynamic SQL Demo: If Conditional & Parameter Binding", result, format, acceptHeader);
    }

    /**
     * 示例 2：for循环与 IN 查询。
     * 
     * GET /demo/doma/in?ids=1,2,3
     */
    @GetMapping("/in")
    public ResponseEntity<?> searchIn(@RequestParam(required = false) List<Long> ids,
            @RequestParam(required = false) String format,
            @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String acceptHeader) {

        // 默认传递 List.of(1L, 2L, 3L) 演示经典场景
        List<Long> queryIds = (ids == null || ids.isEmpty()) ? List.of(1L, 2L, 3L) : ids;
        DomaDynamicSqlDemoService.SearchResult result = demoService.searchIn(queryIds);
        return buildResponse("Doma Dynamic SQL Demo: For Loop & IN Clause", result, format, acceptHeader);
    }

    /**
     * 示例 3：Optional风格日期范围过滤。
     * 
     * GET /demo/doma/optional?fromDate=2026-06-01
     */
    @GetMapping("/optional")
    public ResponseEntity<?> searchOptional(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) String format,
            @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String acceptHeader) {

        LocalDateTime fromDateTime = fromDate != null ? fromDate.atStartOfDay() : null;
        DomaDynamicSqlDemoService.SearchResult result = demoService.searchWithDate(fromDateTime);
        return buildResponse("Doma Dynamic SQL Demo: Optional Date Filter", result, format, acceptHeader);
    }

    /**
     * 根据请求的格式和 Accept 头部，构建适当的响应。
     * 
     * @param title
     * @param result
     * @param format
     * @param acceptHeader
     * @return
     */
    private ResponseEntity<?> buildResponse(String title, DomaDynamicSqlDemoService.SearchResult result, String format,
            String acceptHeader) {

        // 根据请求参数或 Accept 头部判断是否返回 JSON 格式
        boolean wantJson = "json".equalsIgnoreCase(format)
                || (acceptHeader != null && acceptHeader.contains(MediaType.APPLICATION_JSON_VALUE));

        // 如果请求 JSON，则返回结构化的 JSON 响应
        if (wantJson) {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result);
        }

        // 默认返回纯文本控制台排版
        String consoleText = formatConsoleOutput(title, result);
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(consoleText);
    }

    /**
     * 格式化输出结果为控制台风格文本，便于在浏览器中查看。
     * 
     * @param title 
     * @param result 
     * @return
     */
    private String formatConsoleOutput(String title, DomaDynamicSqlDemoService.SearchResult result) {
        StringBuilder sb = new StringBuilder();
        // 输出标题和分隔线
        sb.append("========================================================================\n");
        sb.append("  ").append(title).append("\n");
        sb.append("========================================================================\n\n");

        sb.append("======== Generated SQL (Raw with parameter placeholders) ========\n\n");
        sb.append(result.rawSql().trim()).append("\n\n");
        sb.append("=================================================================\n\n");

        sb.append("======== Generated SQL (Formatted with bind parameters) ========\n\n");
        sb.append(result.formattedSql().trim()).append("\n\n");
        sb.append("=================================================================\n\n");

        sb.append("======== Database Query Results ========\n");
        if (result.results().isEmpty()) {
            sb.append("(No rows returned from the database matching the criteria)\n");
        } else {
            for (var record : result.results()) {
                sb.append(" -> ").append(record.toString()).append("\n");
            }
        }
        sb.append("========================================\n\n");

        sb.append("Note: These SQL statements are compiled from comment-based Doma template files.\n");
        sb.append("      Thus, the .sql files remain fully syntax-compliant and readable in regular SQL editors.\n");
        return sb.toString();
    }
}
