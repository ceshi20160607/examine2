package com.unique.unexamine.sys.module.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.unique.unexamine.sys.module.base.entity.UnModuleSequence;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * <p>
 * 模块自动编号并发序列 Mapper 接口
 * </p>
 *
 * @author Template Base
 * @since 2026-10-23
 */
@Mapper
public interface UnModuleSequenceMapper extends BaseMapper<UnModuleSequence> {

    /** 原子创建或递增序列行，避免并发新增记录取到相同编号。 */
    @Insert("INSERT INTO un_module_sequence(module_id,tenant_id,rule_code,period_key,current_value,update_time,deleted_time) "
            + "VALUES(#{moduleId},#{tenantId},#{ruleCode},#{periodKey},#{initialValue},CURRENT_TIMESTAMP(3),NULL) "
            + "ON DUPLICATE KEY UPDATE current_value=IF(deleted_time IS NULL,current_value+1,VALUES(current_value)), "
            + "update_time=CURRENT_TIMESTAMP(3),deleted_time=NULL")
    int increment(@Param("moduleId") Long moduleId,@Param("tenantId") Long tenantId,
                  @Param("ruleCode") String ruleCode,@Param("periodKey") String periodKey,
                  @Param("initialValue") Long initialValue);

    /** 读取刚刚递增并由当前事务持有行锁的序号值。 */
    @Select("SELECT current_value FROM un_module_sequence WHERE module_id=#{moduleId} AND tenant_id=#{tenantId} "
            + "AND rule_code=#{ruleCode} AND period_key=#{periodKey} AND deleted_time IS NULL FOR UPDATE")
    Long currentValue(@Param("moduleId") Long moduleId,@Param("tenantId") Long tenantId,
                      @Param("ruleCode") String ruleCode,@Param("periodKey") String periodKey);

}
