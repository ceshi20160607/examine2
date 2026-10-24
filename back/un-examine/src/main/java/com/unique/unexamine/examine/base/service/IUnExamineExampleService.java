package com.unique.unexamine.examine.base.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.IService;
import com.unique.unexamine.core.bo.SearchBO;
import com.unique.unexamine.examine.base.entity.UnExamineExample;
import java.util.List;

/** 保存发起快照的审批实例的基础服务；业务筛选与字段展示由 manage 负责。 */
public interface IUnExamineExampleService extends IService<UnExamineExample> {
    /** 根据公共查询对象分页；具体业务可扩展查询对象并在 manage 实现条件。 */
    Page<UnExamineExample> queryPageList(SearchBO search);

    /** 保存主表数据。 */
    UnExamineExample addOrUpdate(UnExamineExample model);

    /** 根据主键读取未删除的记录。 */
    UnExamineExample queryById(Long id);

    /** 按主键批量逻辑删除记录。 */
    boolean deleteByIds(List<Long> ids);
}
