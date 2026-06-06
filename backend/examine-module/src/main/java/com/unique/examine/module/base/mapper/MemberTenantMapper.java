package com.unique.examine.module.base.mapper;

import com.unique.examine.module.base.entity.MemberTenant;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 成员可访问租户集合。 基础 Mapper。
 *
 * @author examine-generator
 * @since 2026-06-06
 */
@Mapper
public interface MemberTenantMapper extends BaseMapper<MemberTenant> {
}
