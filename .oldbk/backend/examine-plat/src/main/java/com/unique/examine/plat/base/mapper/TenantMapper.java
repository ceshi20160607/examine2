package com.unique.examine.plat.base.mapper;

import com.unique.examine.plat.base.entity.Tenant;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统下租户，单租户系统也初始化默认租户。 基础 Mapper。
 *
 * @author examine-generator
 * @since generated
 */
@Mapper
public interface TenantMapper extends BaseMapper<Tenant> {
}
