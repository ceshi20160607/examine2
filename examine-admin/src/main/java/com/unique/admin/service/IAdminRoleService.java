package com.unique.admin.service;

import com.unique.admin.entity.po.AdminRole;
import com.baomidou.mybatisplus.extension.service.IService;
import com.unique.core.entity.user.bo.SimpleRole;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 角色表 服务类
 * </p>
 *
 * @author UNIQUE
 * @since 2023-03-25
 */
public interface IAdminRoleService extends IService<AdminRole> {
    List<SimpleRole> querySimpleRole(Long userId);
}
