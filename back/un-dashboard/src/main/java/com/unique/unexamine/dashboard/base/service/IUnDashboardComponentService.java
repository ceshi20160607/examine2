package com.unique.unexamine.dashboard.base.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.IService;
import com.unique.unexamine.core.bo.SearchBO;
import com.unique.unexamine.dashboard.base.entity.UnDashboardComponent;
import java.util.List;

/** 仪表盘配置组件的基础服务；业务筛选与字段展示由 manage 负责。 */
public interface IUnDashboardComponentService extends IService<UnDashboardComponent> {
    /** 根据公共查询对象分页；具体业务可扩展查询对象并在 manage 实现条件。 */
    Page<UnDashboardComponent> queryPageList(SearchBO search);

    /** 保存主表数据。 */
    UnDashboardComponent addOrUpdate(UnDashboardComponent model);

    /** 根据主键读取未删除的记录。 */
    UnDashboardComponent queryById(Long id);

    /** 按主键批量逻辑删除记录。 */
    boolean deleteByIds(List<Long> ids);
}
