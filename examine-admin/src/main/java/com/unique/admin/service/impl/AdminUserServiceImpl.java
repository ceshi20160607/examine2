package com.unique.admin.service.impl;

import com.unique.admin.entity.po.AdminUser;
import com.unique.admin.entity.vo.AdminUserVO;
import com.unique.admin.mapper.AdminUserMapper;
import com.unique.admin.service.IAdminUserService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.unique.core.common.BasePage;
import com.unique.core.bo.SearchBO;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>
 * 用户表 服务实现类
 * </p>
 *
 * @author UNIQUE
 * @since 2023-03-25
 */
@Service
public class AdminUserServiceImpl extends ServiceImpl<AdminUserMapper, AdminUser> implements IAdminUserService {

    @Override
    public BasePage<List<AdminUserVO>> queryPageList(SearchBO search) {
        return getBaseMapper().queryPageList(search.parse(), search);
    }

    @Override
    public Map<Long, List<Long>> querySuperUserGroupByUserId() {
        List<AdminUser> allList = lambdaQuery().eq(AdminUser::getStatus, 1).list();
        Map<Long, List<Long>> ret = allList.stream()
                .collect(Collectors.groupingBy(AdminUser::getParentId,Collectors.mapping(AdminUser::getId, Collectors.toList())));
        return ret;
    }

    @Override
    public Map<Long, List<Long>> queryDeptUserIdGroupByRoleId() {
        List<AdminUser> allList = lambdaQuery().eq(AdminUser::getStatus, 1).list();
        Map<Long, List<Long>> ret = allList.stream()
                .collect(Collectors.groupingBy(AdminUser::getDeptId,Collectors.mapping(AdminUser::getId, Collectors.toList())));
        return ret;
    }
}
