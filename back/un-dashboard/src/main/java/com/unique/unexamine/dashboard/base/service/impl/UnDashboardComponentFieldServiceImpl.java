package com.unique.unexamine.dashboard.base.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.core.bo.SearchBO;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.dashboard.base.entity.UnDashboardComponentField;
import com.unique.unexamine.dashboard.base.mapper.UnDashboardComponentFieldMapper;
import com.unique.unexamine.dashboard.base.service.IUnDashboardComponentFieldService;
import java.util.List;
import org.springframework.stereotype.Service;

/** 仪表组件字段配置的基础 CRUD 实现；业务条件由 manage 传给 MyBatis-Plus。 */
@Service
public class UnDashboardComponentFieldServiceImpl extends ServiceImpl<UnDashboardComponentFieldMapper, UnDashboardComponentField>
        implements IUnDashboardComponentFieldService {
    /** 公共查询对象负责分页，基础实现仅按主键稳定排序。 */
    @Override
    public Page<UnDashboardComponentField> queryPageList(SearchBO search) {
        return page(search.parse(),
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<UnDashboardComponentField>().orderByDesc("id"));
    }

    /** 使用 MyBatis-Plus 原生保存与更新能力。 */
    @Override
    public UnDashboardComponentField addOrUpdate(UnDashboardComponentField model) {
        saveOrUpdate(model);
        return model;
    }

    /** 基础读取只负责主键定位；调用方决定业务可见范围。 */
    @Override
    public UnDashboardComponentField queryById(Long id) {
        UnDashboardComponentField model = getById(id);
        if (model == null) throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "记录不存在: " + id);
        return model;
    }

    /** 逻辑删除规则由实体与 MyBatis-Plus 统一处理。 */
    @Override
    public boolean deleteByIds(List<Long> ids) {
        return removeByIds(ids);
    }
}
