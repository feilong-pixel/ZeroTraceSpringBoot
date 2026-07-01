package com.feilonglab.springboot.doma;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Doma 动态 SQL 示例集成测试。
 * 验证 dynamic SQL 的 Raw 与 Formatted SQL 拼接是否正确。
 */
@SpringBootTest
public class DomaDynamicSqlControllerTest {

    @Autowired
    private DomaDynamicSqlDemoService demoService;

    @Test
    public void testSearchMail() {
        DomaDynamicSqlDemoService.SearchResult result = demoService.search(0, "Test");
        assertNotNull(result);
        
        // 验证原始 SQL
        assertTrue(result.rawSql().contains("AND status = ?"));
        assertTrue(result.rawSql().contains("AND subject LIKE ?"));
        
        // 验证格式化（参数替换后）的 SQL
        assertTrue(result.formattedSql().contains("AND status = 0"));
        assertTrue(result.formattedSql().contains("AND subject LIKE '%Test%'"));
    }

    @Test
    public void testSearchIn() {
        DomaDynamicSqlDemoService.SearchResult result = demoService.searchIn(List.of(1L, 2L));
        assertNotNull(result);
        
        // 验证 IN 子句生成
        assertTrue(result.rawSql().contains("mail_id IN"));
        assertTrue(result.formattedSql().contains("1"));
        assertTrue(result.formattedSql().contains("2"));
    }

    @Test
    public void testSearchOptional() {
        DomaDynamicSqlDemoService.SearchResult result = demoService.searchWithDate(java.time.LocalDate.of(2026, 6, 1).atStartOfDay());
        assertNotNull(result);
        
        // 验证可选日期拼接
        assertTrue(result.rawSql().contains("AND sent_at >= ?"));
        assertTrue(result.formattedSql().contains("AND sent_at >= '2026-06-01"));
    }
}
