package com.unique.examine.module.base.mapper;

import com.unique.examine.module.base.entity.RoleExplicitDeny;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 显式禁用权限项，优先于授权并集。 基础 Mapper。
 *
 * @author examine-generator
 * @since generated
 */
@Mapper
public interface RoleExplicitDenyMapper extends BaseMapper<RoleExplicitDeny> {
}
