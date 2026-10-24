package com.unique.unexamine.sys.module.base.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.core.bo.SearchBO;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.sys.module.base.entity.UnModuleField;
import com.unique.unexamine.sys.module.base.mapper.UnModuleFieldMapper;
import com.unique.unexamine.sys.module.base.service.IUnModuleFieldService;
import java.util.List;
import org.springframework.stereotype.Service;

/** 模块字段的基础 CRUD 实现；业务条件由 manage 传给 MyBatis-Plus。 */
@Service
public class UnModuleFieldServiceImpl extends ServiceImpl<UnModuleFieldMapper, UnModuleField>
        implements IUnModuleFieldService {
    /** 公共查询对象负责分页，基础实现仅按主键稳定排序。 */
    @Override
    public Page<UnModuleField> queryPageList(SearchBO search) {
        return page(search.parse(),
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<UnModuleField>().orderByDesc("id"));
    }

    /** 使用 MyBatis-Plus 原生保存与更新能力。 */
    @Override
    public UnModuleField addOrUpdate(UnModuleField model) {
        saveOrUpdate(model);
        return model;
    }

    /** 基础读取只负责主键定位；调用方决定业务可见范围。 */
    @Override
    public UnModuleField queryById(Long id) {
        UnModuleField model = getById(id);
        if (model == null) throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "记录不存在: " + id);
        return model;
    }

    /** 逻辑删除规则由实体与 MyBatis-Plus 统一处理。 */
    @Override
    public boolean deleteByIds(List<Long> ids) {
        return removeByIds(ids);
    }

    /** 槽位一旦分配即保留，逻辑删除字段也不能让新字段覆盖旧记录的主表值。 */
    @Override
    public List<String> allFieldKeysForStorage(Long moduleId) {
        return getBaseMapper().selectAllFieldKeysForStorage(moduleId);
    }
}
