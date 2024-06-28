package com.unique.admin.mapper;

import com.unique.admin.entity.po.AdminMenu;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.unique.core.entity.user.bo.SimpleMenu;

import java.util.List;

/**
 * <p>
 * 菜单权限配置表 Mapper 接口
 * </p>
 *
 * @author UNIQUE
 * @since 2023-03-25
 */
public interface AdminMenuMapper extends BaseMapper<AdminMenu> {

    List<SimpleMenu> querySimpleMenu(Long userId);
}
