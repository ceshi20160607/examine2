package com.unique.unexamine.examine.base.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.IService;
import com.unique.unexamine.core.bo.SearchBO;
import com.unique.unexamine.examine.base.entity.UnExamineExampleLog;
import java.util.List;

/** 审批实例日志基础 CRUD；实例范围和权限由 manage 服务负责。 */
public interface IUnExamineExampleLogService extends IService<UnExamineExampleLog> {
    Page<UnExamineExampleLog> queryPageList(SearchBO search);
    UnExamineExampleLog addOrUpdate(UnExamineExampleLog model);
    UnExamineExampleLog queryById(Long id);
    boolean deleteByIds(List<Long> ids);
}
