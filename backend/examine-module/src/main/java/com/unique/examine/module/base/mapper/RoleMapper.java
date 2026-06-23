package com.unique.examine.module.base.mapper;

import com.unique.examine.module.base.entity.Role;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统内角色，含系统超级管理员。 基础 Mapper。
 *
 * @author examine-generator
 * @since generated
 */
@Mapper
public interface RoleMapper extends BaseMapper<Role> {
}
