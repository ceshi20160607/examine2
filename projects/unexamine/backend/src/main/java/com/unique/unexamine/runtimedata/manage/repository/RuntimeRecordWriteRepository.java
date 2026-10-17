package com.unique.unexamine.runtimedata.manage.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/** 业务记录动作中需要数据库时间或行锁的专用 MyBatis 查询。 */
@Mapper
public interface RuntimeRecordWriteRepository {

    /** 转化完成时间采用数据库连接的业务时区，和记录创建时间保持一致。 */
    @Select("SELECT CURRENT_TIMESTAMP(3)")
    LocalDateTime currentDatabaseTime();

    /** 锁定来源记录版本，避免转化与编辑或审批恢复交错写入。 */
    @Select("SELECT version FROM un_biz_record WHERE id=#{recordId} AND system_id=#{systemId} "
            + "AND tenant_id=#{tenantId} FOR UPDATE")
    List<Integer> lockSourceVersions(@Param("recordId") Long recordId, @Param("systemId") Long systemId,
                                     @Param("tenantId") Long tenantId);
}
