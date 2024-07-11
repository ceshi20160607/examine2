package com.unique.admin.service.impl;

import com.unique.admin.entity.po.AdminRole;
import com.unique.admin.mapper.AdminRoleMapper;
import com.unique.admin.service.IAdminRoleService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.unique.core.entity.user.bo.SimpleRole;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>
 * 角色表 服务实现类
 * </p>
 *
 * @author UNIQUE
 * @since 2023-03-25
 */
@Service
public class AdminRoleServiceImpl extends ServiceImpl<AdminRoleMapper, AdminRole> implements IAdminRoleService {

    @Override
    public List<SimpleRole> querySimpleRole(Long userId) {
        return getBaseMapper().querySimpleRole(userId);
    }
}
