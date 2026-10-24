package com.unique.unexamine.sys.module.base.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.IService;
import com.unique.unexamine.core.bo.SearchBO;
import com.unique.unexamine.sys.module.base.entity.UnModuleAction;
import java.util.List;

/** 菜单操作的模块动作执行配置的基础服务；业务筛选与字段展示由 manage 负责。 */
public interface IUnModuleActionService extends IService<UnModuleAction> {
    /** 根据公共查询对象分页；具体业务可扩展查询对象并在 manage 实现条件。 */
    Page<UnModuleAction> queryPageList(SearchBO search);

    /** 保存主表数据。 */
    UnModuleAction addOrUpdate(UnModuleAction model);

    /** 根据主键读取未删除的记录。 */
    UnModuleAction queryById(Long id);

    /** 按主键批量逻辑删除记录。 */
    boolean deleteByIds(List<Long> ids);
}
