package com.unique.unexamine.plat.base.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.IService;
import com.unique.unexamine.core.bo.SearchBO;
import com.unique.unexamine.plat.base.entity.UnPlatDepartment;
import java.util.List;

/** 平台组织部门的基础服务；业务筛选与字段展示由 manage 负责。 */
public interface IUnPlatDepartmentService extends IService<UnPlatDepartment> {
    /** 根据公共查询对象分页；具体业务可扩展查询对象并在 manage 实现条件。 */
    Page<UnPlatDepartment> queryPageList(SearchBO search);

    /** 保存主表数据。 */
    UnPlatDepartment addOrUpdate(UnPlatDepartment model);

    /** 根据主键读取未删除的记录。 */
    UnPlatDepartment queryById(Long id);

    /** 按主键批量逻辑删除记录。 */
    boolean deleteByIds(List<Long> ids);
}
