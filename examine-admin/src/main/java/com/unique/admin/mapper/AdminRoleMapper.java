package com.unique.admin.mapper;

import com.unique.admin.entity.po.AdminRole;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.unique.core.entity.user.bo.SimpleRole;

import java.util.List;

/**
 * <p>
 * 角色表 Mapper 接口
 * </p>
 *
 * @author UNIQUE
 * @since 2023-03-25
 */
public interface AdminRoleMapper extends BaseMapper<AdminRole> {

    List<SimpleRole> querySimpleRole(Long userId);
}
