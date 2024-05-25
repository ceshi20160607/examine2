package com.unique.admin.service;

import com.unique.admin.entity.po.AdminUser;
import com.baomidou.mybatisplus.extension.service.IService;
import com.unique.admin.entity.vo.AdminUserVO;
import com.unique.core.common.BasePage;
import com.unique.core.bo.SearchBO;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 用户表 服务类
 * </p>
 *
 * @author UNIQUE
 * @since 2023-03-25
 */
public interface IAdminUserService extends IService<AdminUser> {

    BasePage<List<AdminUserVO>> queryPageList(SearchBO search);


    //-----------------------------其他业务使用------------------------------

    /**
     * 查询父级的下面的所有用户
     * @return {@link Map}<{@link Long},{@link List}<{@link Long}>>
     */
    Map<Long,List<Long>> querySuperUserGroupByUserId();

    /**
     * 查询部门的下面的所有用户
     * @return {@link Map}<{@link Long},{@link List}<{@link Long}>>
     */
    Map<Long, List<Long>> queryDeptUserIdGroupByRoleId();
    //-----------------------------其他业务使用------------------------------
}