package com.unique.unexamine.sys.module.base.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.core.bo.SearchBO;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.sys.module.base.entity.UnModuleScenceConfig;
import com.unique.unexamine.sys.module.base.mapper.UnModuleScenceConfigMapper;
import com.unique.unexamine.sys.module.base.service.IUnModuleScenceConfigService;
import java.util.List;
import org.springframework.stereotype.Service;

/** 模块场景配置的基础 CRUD 实现；业务条件由 manage 传给 MyBatis-Plus。 */
@Service
public class UnModuleScenceConfigServiceImpl extends ServiceImpl<UnModuleScenceConfigMapper, UnModuleScenceConfig>
        implements IUnModuleScenceConfigService {
    /** 公共查询对象负责分页，基础实现仅按主键稳定排序。 */
    @Override
    public Page<UnModuleScenceConfig> queryPageList(SearchBO search) {
        return page(search.parse(),
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<UnModuleScenceConfig>().orderByDesc("id"));
    }

    /** 使用 MyBatis-Plus 原生保存与更新能力。 */
    @Override
    public UnModuleScenceConfig addOrUpdate(UnModuleScenceConfig model) {
        saveOrUpdate(model);
        return model;
    }

    /** 基础读取只负责主键定位；调用方决定业务可见范围。 */
    @Override
    public UnModuleScenceConfig queryById(Long id) {
        UnModuleScenceConfig model = getById(id);
        if (model == null) throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "记录不存在: " + id);
        return model;
    }

    /** 逻辑删除规则由实体与 MyBatis-Plus 统一处理。 */
    @Override
    public boolean deleteByIds(List<Long> ids) {
        return removeByIds(ids);
    }
}
