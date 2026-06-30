package com.feilonglab.springboot.model.dao;

import java.util.List;
import org.seasar.doma.Dao;
import org.seasar.doma.Insert;
import org.seasar.doma.Update;
import org.seasar.doma.Delete;
import org.seasar.doma.Select;
import org.seasar.doma.boot.ConfigAutowireable;
import org.springframework.stereotype.Component;
import com.feilonglab.springboot.model.entity.MailSendLog;

/**
 * 邮件发送日志数据访问对象 (Seasar Doma DAO)。
 * 使用 ConfigAutowireable 注解与 Spring Boot 集成。
 */
@Dao
@ConfigAutowireable
@Component // 注册为 Spring Bean，以便 Autowire
public interface MailSendLogDao {

    /**
     * 根据日志 ID 查询单条日志记录。
     * 需要对应的 SQL 模板文件: META-INF/com/feilonglab/springboot/model/dao/MailSendLogDao/selectById.sql
     */
    @Select
    MailSendLog selectById(Long logId);

    /**
     * 根据邮件 ID 查询发送日志列表。
     * 需要对应的 SQL 模板文件: META-INF/com/feilonglab/springboot/model/dao/MailSendLogDao/selectByMailId.sql
     */
    @Select
    List<MailSendLog> selectByMailId(Long mailId);

    /**
     * 插入单条发送日志（自动生成 SQL）。
     */
    @Insert
    int insert(MailSendLog mailSendLog);

    /**
     * 更新单条发送日志（自动生成 SQL，包含乐观锁校验）。
     */
    @Update
    int update(MailSendLog mailSendLog);

    /**
     * 删除单条发送日志（自动生成 SQL）。
     */
    @Delete
    int delete(MailSendLog mailSendLog);
}
