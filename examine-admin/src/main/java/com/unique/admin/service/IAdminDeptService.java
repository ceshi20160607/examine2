package com.unique.admin.service;

import com.unique.admin.entity.po.AdminDept;
import com.baomidou.mybatisplus.extension.service.IService;
import com.unique.core.common.BasePage;
import com.unique.core.entity.base.bo.SearchBO;
import com.unique.core.entity.user.bo.SimpleDept;

import java.util.List;

/**
 * <p>
 * 部门表 服务类
 * </p>
 *
 * @author UNIQUE
 * @since 2023-03-25
 */
public interface IAdminDeptService extends IService<AdminDept> {

    BasePage<List<AdminDept>> queryPageList(SearchBO search);


    List<SimpleDept> queryDataDepts(Long userId);

    List<SimpleDept> queryAllDepts();


    //-----------------------------其他业务使用------------------------------
    //-----------------------------其他业务使用------------------------------
}
