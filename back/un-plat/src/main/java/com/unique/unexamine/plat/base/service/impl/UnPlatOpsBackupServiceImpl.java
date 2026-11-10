package com.unique.unexamine.plat.base.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.core.bo.SearchBO;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.plat.base.entity.UnPlatOpsBackup;
import com.unique.unexamine.plat.base.mapper.UnPlatOpsBackupMapper;
import com.unique.unexamine.plat.base.service.IUnPlatOpsBackupService;
import java.util.List;
import org.springframework.stereotype.Service;

/** 运维备份元数据的基础 CRUD 实现；业务条件由 manage 传给 MyBatis-Plus。 */
@Service
public class UnPlatOpsBackupServiceImpl extends ServiceImpl<UnPlatOpsBackupMapper, UnPlatOpsBackup>
        implements IUnPlatOpsBackupService {
    /** 公共查询对象负责分页，基础实现仅按主键稳定排序。 */
    @Override
    public Page<UnPlatOpsBackup> queryPageList(SearchBO search) {
        return page(search.parse(),
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<UnPlatOpsBackup>().orderByDesc("id"));
    }

    /** 使用 MyBatis-Plus 原生保存与更新能力。 */
    @Override
    public UnPlatOpsBackup addOrUpdate(UnPlatOpsBackup model) {
        saveOrUpdate(model);
        return model;
    }

    /** 基础读取只负责主键定位；调用方决定业务可见范围。 */
    @Override
    public UnPlatOpsBackup queryById(Long id) {
        UnPlatOpsBackup model = getById(id);
        if (model == null) throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "记录不存在: " + id);
        return model;
    }

    /** 逻辑删除规则由实体与 MyBatis-Plus 统一处理。 */
    @Override
    public boolean deleteByIds(List<Long> ids) {
        return removeByIds(ids);
    }
}
