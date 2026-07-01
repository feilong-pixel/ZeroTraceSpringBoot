package com.feilonglab.springboot.doma;

import java.time.LocalDateTime;
import java.util.List;
import org.seasar.doma.Dao;
import org.seasar.doma.Select;
import org.seasar.doma.boot.ConfigAutowireable;
import org.springframework.stereotype.Component;
import com.feilonglab.springboot.model.entity.MailInfo;

/**
 * Doma 动态 SQL 示例 DAO。
 * 使用 ConfigAutowireable 注解与 Spring Boot 集成。
 */
@Dao
@ConfigAutowireable
@Component
public interface DomaDynamicSqlDemoDao {

    /**
     * 演示 /*%if *\/ 动态 SQL 及参数绑定。
     */
    @Select
    List<MailInfo> search(Integer status, String keyword);

    /**
     * 演示 /*%for *\/ 动态 IN 查询。
     */
    @Select
    List<MailInfo> searchIn(List<Long> ids);

    /**
     * 演示 Optional 样式的日期范围过滤。
     */
    @Select
    List<MailInfo> searchWithDate(LocalDateTime fromDate);
}
