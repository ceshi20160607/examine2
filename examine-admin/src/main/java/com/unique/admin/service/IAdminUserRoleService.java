package com.unique.admin.service;

import com.unique.admin.entity.po.AdminUser;
import com.unique.admin.entity.po.AdminUserRole;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>
 * 用户角色对应关系表 服务类
 * </p>
 *
 * @author UNIQUE
 * @since 2023-03-25
 */
public interface IAdminUserRoleService extends IService<AdminUserRole> {

    //-----------------------------其他业务使用------------------------------

    Map<Long, List<Long>> queryRoleUserIdGroupByRoleId();
    //-----------------------------其他业务使用------------------------------

}
