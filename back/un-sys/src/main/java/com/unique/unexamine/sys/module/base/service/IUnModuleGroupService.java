package com.unique.unexamine.sys.module.base.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.IService;
import com.unique.unexamine.core.bo.SearchBO;
import com.unique.unexamine.sys.module.base.entity.UnModuleGroup;
import java.util.List;

/** 模块分组配置的基础服务；业务筛选与字段展示由 manage 负责。 */
public interface IUnModuleGroupService extends IService<UnModuleGroup> {
    /** 根据公共查询对象分页；具体业务可扩展查询对象并在 manage 实现条件。 */
    Page<UnModuleGroup> queryPageList(SearchBO search);

    /** 保存主表数据。 */
    UnModuleGroup addOrUpdate(UnModuleGroup model);

    /** 根据主键读取未删除的记录。 */
    UnModuleGroup queryById(Long id);

    /** 按主键批量逻辑删除记录。 */
    boolean deleteByIds(List<Long> ids);
}
