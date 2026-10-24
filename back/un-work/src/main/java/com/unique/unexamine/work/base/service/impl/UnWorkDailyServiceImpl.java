package com.unique.unexamine.work.base.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.core.bo.SearchBO;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.work.base.entity.UnWorkDaily;
import com.unique.unexamine.work.base.mapper.UnWorkDailyMapper;
import com.unique.unexamine.work.base.service.IUnWorkDailyService;
import java.util.List;
import org.springframework.stereotype.Service;

/** 用户手动创建的每日工作日志及版本的基础 CRUD 实现；业务条件由 manage 传给 MyBatis-Plus。 */
@Service
public class UnWorkDailyServiceImpl extends ServiceImpl<UnWorkDailyMapper, UnWorkDaily>
        implements IUnWorkDailyService {
    /** 公共查询对象负责分页，基础实现仅按主键稳定排序。 */
    @Override
    public Page<UnWorkDaily> queryPageList(SearchBO search) {
        return page(search.parse(),
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<UnWorkDaily>().orderByDesc("id"));
    }

    /** 使用 MyBatis-Plus 原生保存与更新能力。 */
    @Override
    public UnWorkDaily addOrUpdate(UnWorkDaily model) {
        saveOrUpdate(model);
        return model;
    }

    /** 基础读取只负责主键定位；调用方决定业务可见范围。 */
    @Override
    public UnWorkDaily queryById(Long id) {
        UnWorkDaily model = getById(id);
        if (model == null) throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "记录不存在: " + id);
        return model;
    }

    /** 逻辑删除规则由实体与 MyBatis-Plus 统一处理。 */
    @Override
    public boolean deleteByIds(List<Long> ids) {
        return removeByIds(ids);
    }
}
