package com.unique.unexamine.examine.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.core.bo.SearchBO;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.examine.base.entity.UnExamineExampleLog;
import com.unique.unexamine.examine.base.mapper.UnExamineExampleLogMapper;
import com.unique.unexamine.examine.base.service.IUnExamineExampleLogService;
import java.util.List;
import org.springframework.stereotype.Service;

/** 审批实例日志基础实现；按主键排序，业务筛选由 manage 组合。 */
@Service
public class UnExamineExampleLogServiceImpl extends ServiceImpl<UnExamineExampleLogMapper, UnExamineExampleLog>
        implements IUnExamineExampleLogService {
    @Override
    public Page<UnExamineExampleLog> queryPageList(SearchBO search) {
        return page(search.parse(), new QueryWrapper<UnExamineExampleLog>().orderByDesc("id"));
    }

    @Override
    public UnExamineExampleLog addOrUpdate(UnExamineExampleLog model) {
        saveOrUpdate(model);
        return model;
    }

    @Override
    public UnExamineExampleLog queryById(Long id) {
        UnExamineExampleLog model = getById(id);
        if (model == null) throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "记录不存在: " + id);
        return model;
    }

    @Override
    public boolean deleteByIds(List<Long> ids) {
        return removeByIds(ids);
    }
}
