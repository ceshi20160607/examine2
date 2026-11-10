package com.unique.unexamine.sys.module.base.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.IService;
import com.unique.unexamine.core.bo.SearchBO;
import com.unique.unexamine.sys.module.base.entity.UnModuleSequence;
import java.util.List;

/** 模块自动编号并发序列的基础服务；业务筛选与字段展示由 manage 负责。 */
public interface IUnModuleSequenceService extends IService<UnModuleSequence> {
    /** 为自动编号原子分配一个序号；序列行在当前事务内锁定。 */
    long nextValue(Long moduleId,Long tenantId,String ruleCode,String periodKey,long start);

    /** 根据公共查询对象分页；具体业务可扩展查询对象并在 manage 实现条件。 */
    Page<UnModuleSequence> queryPageList(SearchBO search);

    /** 保存主表数据。 */
    UnModuleSequence addOrUpdate(UnModuleSequence model);

    /** 根据主键读取未删除的记录。 */
    UnModuleSequence queryById(Long id);

    /** 按主键批量逻辑删除记录。 */
    boolean deleteByIds(List<Long> ids);
}
