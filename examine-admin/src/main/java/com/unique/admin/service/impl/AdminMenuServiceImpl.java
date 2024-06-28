package com.unique.admin.service.impl;

import com.unique.admin.entity.po.AdminMenu;
import com.unique.admin.mapper.AdminMenuMapper;
import com.unique.admin.service.IAdminMenuService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.unique.core.entity.user.bo.SimpleMenu;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * <p>
 * 菜单权限配置表 服务实现类
 * </p>
 *
 * @author UNIQUE
 * @since 2023-03-25
 */
@Service
public class AdminMenuServiceImpl extends ServiceImpl<AdminMenuMapper, AdminMenu> implements IAdminMenuService {

    @Override
    public List<SimpleMenu> querySimpleMenu(Long userId) {
        return getBaseMapper().querySimpleMenu(userId);
    }
}
