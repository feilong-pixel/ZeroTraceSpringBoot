package com.feilonglab.springboot.doma;

import java.time.LocalDateTime;
import java.util.List;
import org.seasar.doma.jdbc.Sql;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.feilonglab.springboot.model.entity.MailInfo;

/**
 * Doma 动态 SQL 示例 Service。
 * 调用 DAO 层执行查询，并从 DomaSqlInterceptorLogger 获取拦截的最终生成 SQL。
 */
@Service
public class DomaDynamicSqlDemoService {

    /** Doma 动态 SQL 示例 DAO */
    @Autowired
    private DomaDynamicSqlDemoDao demoDao;

    /**
     * 演示包含 if 条件的动态 SQL 查询。
     *
     * @param status  状态代码
     * @param keyword 关键词
     * @return 包含生成的 SQL 与结果集的 SearchResult
     */
    public SearchResult search(Integer status, String keyword) {
        DomaSqlInterceptorLogger.clear();
        List<MailInfo> results = demoDao.search(status, keyword);
        Sql<?> sql = DomaSqlInterceptorLogger.getLastSql();
        return new SearchResult(
                sql != null ? sql.getRawSql() : "",
                sql != null ? sql.getFormattedSql() : "",
                results
        );
    }

    /**
     * 演示包含 for 循环 (IN 子句) 的动态 SQL 查询。
     *
     * @param ids ID列表
     * @return 包含生成的 SQL 与结果集的 SearchResult
     */
    public SearchResult searchIn(List<Long> ids) {
        DomaSqlInterceptorLogger.clear();
        List<MailInfo> results = demoDao.searchIn(ids);
        Sql<?> sql = DomaSqlInterceptorLogger.getLastSql();
        return new SearchResult(
                sql != null ? sql.getRawSql() : "",
                sql != null ? sql.getFormattedSql() : "",
                results
        );
    }

    /**
     * 演示 Optional 风格的日期范围动态 SQL 查询。
     *
     * @param fromDate 开始日期时间
     * @return 包含生成的 SQL 与结果集的 SearchResult
     */
    public SearchResult searchWithDate(LocalDateTime fromDate) {
        DomaSqlInterceptorLogger.clear();
        List<MailInfo> results = demoDao.searchWithDate(fromDate);
        Sql<?> sql = DomaSqlInterceptorLogger.getLastSql();
        return new SearchResult(
                sql != null ? sql.getRawSql() : "",
                sql != null ? sql.getFormattedSql() : "",
                results
        );
    }

    /**
     * 查询结果包装记录类。
     *
     * @param rawSql       Doma 生成的带参数占位符（?）的原始 SQL
     * @param formattedSql Doma 生成的参数替换后的格式化 SQL
     * @param results      数据查询结果集
     */
    public static record SearchResult(String rawSql, String formattedSql, List<MailInfo> results) {}
}
