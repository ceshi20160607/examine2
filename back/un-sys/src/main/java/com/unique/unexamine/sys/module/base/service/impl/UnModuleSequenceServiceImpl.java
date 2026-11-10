package com.unique.unexamine.sys.module.base.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.core.bo.SearchBO;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.sys.module.base.entity.UnModuleSequence;
import com.unique.unexamine.sys.module.base.mapper.UnModuleSequenceMapper;
import com.unique.unexamine.sys.module.base.service.IUnModuleSequenceService;
import java.util.List;
import org.springframework.stereotype.Service;

/** 模块自动编号并发序列的基础 CRUD 实现；业务条件由 manage 传给 MyBatis-Plus。 */
@Service
public class UnModuleSequenceServiceImpl extends ServiceImpl<UnModuleSequenceMapper, UnModuleSequence>
        implements IUnModuleSequenceService {
    /** 公共查询对象负责分页，基础实现仅按主键稳定排序。 */
    @Override
    public Page<UnModuleSequence> queryPageList(SearchBO search) {
        return page(search.parse(),
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<UnModuleSequence>().orderByDesc("id"));
    }

    /** 使用 MyBatis-Plus 原生保存与更新能力。 */
    @Override
    public UnModuleSequence addOrUpdate(UnModuleSequence model) {
        saveOrUpdate(model);
        return model;
    }

    /** 业务自动编号使用数据库唯一键和行锁，不通过查询最大值推算序号。 */
    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public long nextValue(Long moduleId,Long tenantId,String ruleCode,String periodKey,long start) {
        if (baseMapper.increment(moduleId,tenantId,ruleCode,periodKey,start)<1)
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "自动编号序列分配失败");
        Long value=baseMapper.currentValue(moduleId,tenantId,ruleCode,periodKey);
        if (value==null) throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "自动编号序列读取失败");
        return value;
    }

    /** 基础读取只负责主键定位；调用方决定业务可见范围。 */
    @Override
    public UnModuleSequence queryById(Long id) {
        UnModuleSequence model = getById(id);
        if (model == null) throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "记录不存在: " + id);
        return model;
    }

    /** 逻辑删除规则由实体与 MyBatis-Plus 统一处理。 */
    @Override
    public boolean deleteByIds(List<Long> ids) {
        return removeByIds(ids);
    }
}
