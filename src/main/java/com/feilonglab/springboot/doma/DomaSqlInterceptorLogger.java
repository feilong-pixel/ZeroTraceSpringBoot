package com.feilonglab.springboot.doma;

import org.seasar.doma.jdbc.Sql;
import org.seasar.doma.jdbc.UtilLoggingJdbcLogger;
import org.springframework.stereotype.Component;

/**
 * 拦截并保存 Doma 最终生成的 SQL，便于在 Demo 中进行展示。
 * 继承自默认的 UtilLoggingJdbcLogger，仅覆写 logSql 以保存 SQL 信息。
 */
@Component
public class DomaSqlInterceptorLogger extends UtilLoggingJdbcLogger {

    private static final ThreadLocal<Sql<?>> lastSql = new ThreadLocal<>();

    /**
     * 获取当前线程最后一次执行 of SQL 信息。
     *
     * @return Doma 的 Sql 封装对象，包含原始 SQL、格式化 SQL 及参数列表
     */
    public static Sql<?> getLastSql() {
        return lastSql.get();
    }

    /**
     * 清理 ThreadLocal 中的 SQL 信息，防止内存泄漏。
     */
    public static void clear() {
        lastSql.remove();
    }

    @Override
    public void logSql(String callerClassName, String callerMethodName, Sql<?> sql) {
        lastSql.set(sql);
        super.logSql(callerClassName, callerMethodName, sql);
    }
}
