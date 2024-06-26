package com.unique.admin.service;

import com.unique.admin.entity.po.AdminMenu;
import com.baomidou.mybatisplus.extension.service.IService;
import com.unique.core.entity.user.bo.SimpleMenu;

import java.util.List;

/**
 * <p>
 * 菜单权限配置表 服务类
 * </p>
 *
 * @author UNIQUE
 * @since 2023-03-25
 */
public interface IAdminMenuService extends IService<AdminMenu> {

    List<SimpleMenu> querySimpleMenu(Long userId);
}
