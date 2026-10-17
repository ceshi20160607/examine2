package com.unique.unexamine.moduleconfig.manage.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 模块标识并发创建与发布时，锁定所属系统行的专用查询。 */
@Mapper
public interface ModuleIdentifierLockRepository {

    /** 在当前事务内锁住系统行，避免两个租户同时通过相同模块标识的检查。 */
    @Select("SELECT id FROM un_sys_system WHERE id = #{systemId} FOR UPDATE")
    Long lockSystem(@Param("systemId") Long systemId);
}
