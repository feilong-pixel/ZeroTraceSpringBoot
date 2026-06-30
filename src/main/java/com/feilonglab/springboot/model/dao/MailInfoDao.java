package com.feilonglab.springboot.model.dao;

import java.util.List;
import org.seasar.doma.Dao;
import org.seasar.doma.Insert;
import org.seasar.doma.Update;
import org.seasar.doma.Delete;
import org.seasar.doma.Select;
import org.seasar.doma.boot.ConfigAutowireable;
import org.springframework.stereotype.Component;
import com.feilonglab.springboot.model.entity.MailInfo;

/**
 * 邮件信息数据访问对象 (Seasar Doma DAO)。
 * 使用 ConfigAutowireable 注解与 Spring Boot 集成。
 */
@Dao
@ConfigAutowireable
@Component // 注册为 Spring Bean，以便 Autowire
public interface MailInfoDao {

    /**
     * 根据主键查询邮件信息。
     * 需要对应的 SQL 模板文件: META-INF/com/feilonglab/springboot/model/dao/MailInfoDao/selectById.sql
     */
    @Select
    MailInfo selectById(Long mailId);

    /**
     * 查询状态为未发送（0）或发送失败且未达到最大重试次数的邮件列表。
     * 需要对应的 SQL 模板文件: META-INF/com/feilonglab/springboot/model/dao/MailInfoDao/selectUnsentOrFailed.sql
     */
    @Select
    List<MailInfo> selectUnsentOrFailed(int maxRetry);

    /**
     * 插入单条邮件记录（自动生成 SQL）。
     */
    @Insert
    int insert(MailInfo mailInfo);

    /**
     * 更新单条邮件记录（自动生成 SQL，包含乐观锁校验）。
     */
    @Update
    int update(MailInfo mailInfo);

    /**
     * 删除单条邮件记录（自动生成 SQL）。
     */
    @Delete
    int delete(MailInfo mailInfo);
}
