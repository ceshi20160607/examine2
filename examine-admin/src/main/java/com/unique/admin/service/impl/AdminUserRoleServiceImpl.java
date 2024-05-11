package com.unique.admin.service.impl;

import com.unique.admin.entity.po.AdminUser;
import com.unique.admin.entity.po.AdminUserRole;
import com.unique.admin.mapper.AdminUserRoleMapper;
import com.unique.admin.service.IAdminUserRoleService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>
 * 用户角色对应关系表 服务实现类
 * </p>
 *
 * @author UNIQUE
 * @since 2023-03-25
 */
@Service
public class AdminUserRoleServiceImpl extends ServiceImpl<AdminUserRoleMapper, AdminUserRole> implements IAdminUserRoleService {
    @Override
    public Map<Long, List<Long>> queryRoleUserIdGroupByRoleId() {
        List<AdminUserRole> allList = getBaseMapper().queryRoleUserIdGroupByRoleId();
        Map<Long, List<Long>> ret = allList.stream()
                .collect(Collectors.groupingBy(AdminUserRole::getRoleId,Collectors.mapping(AdminUserRole::getUserId, Collectors.toList())));
        return ret;
    }
}
