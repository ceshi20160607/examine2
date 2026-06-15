package com.unique.examine.module.service.impl;

import com.unique.examine.core.module.ModuleAuthCacheCoordinator;
import com.unique.examine.module.entity.po.ModuleMember;
import com.unique.examine.module.mapper.ModuleMemberMapper;
import com.unique.examine.module.service.IModuleMemberService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.unique.examine.core.entity.BasePage;
import com.unique.examine.core.entity.PageEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.util.List;

/**
 * <p>
 * 应用成员 服务实现类
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-14
 */
@Service
public class ModuleMemberServiceImpl extends ServiceImpl<ModuleMemberMapper, ModuleMember> implements IModuleMemberService {

    @Autowired(required = false)
    private ModuleAuthCacheCoordinator moduleAuthCacheCoordinator;

    /**
     * 查询字段配置
     * @author UNIQUE
     * @since 2026-04-14
     * @param id 主键ID
     * @return data
     */
    @Override
    public ModuleMember queryById(Serializable id) {
        return getById(id);
    }

    /**
     * 保存或新增信息
     * @author UNIQUE
     * @since 2026-04-14
     * @param entity entity
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addOrUpdate(ModuleMember entity) {
        ModuleMember old = null;
        if (entity != null && entity.getId() != null) {
            old = getById(entity.getId());
        }
        saveOrUpdate(entity);

        if (moduleAuthCacheCoordinator == null || entity == null) {
            return;
        }
        // 角色/成员变更后，需要立刻让权限缓存失效（否则可能 5min 内不生效）
        if (old != null && old.getPlatId() != null) {
            moduleAuthCacheCoordinator.invalidateForMember(
                    old.getSystemId() == null ? 0L : old.getSystemId(),
                    old.getTenantId() == null ? 0L : old.getTenantId(),
                    old.getPlatId()
            );
        }
        if (entity.getPlatId() != null) {
            moduleAuthCacheCoordinator.invalidateForMember(
                    entity.getSystemId() == null ? 0L : entity.getSystemId(),
                    entity.getTenantId() == null ? 0L : entity.getTenantId(),
                    entity.getPlatId()
            );
        }
    }


    /**
     * 查询所有数据
     * @author UNIQUE
     * @since 2026-04-14
     * @param search 搜索条件
     * @return list
     */
    @Override
    public BasePage<ModuleMember> queryPageList(PageEntity search) {
        return lambdaQuery().page(search.parse());
    }

    /**
     * 根据ID列表删除数据
     * @author UNIQUE
     * @since 2026-04-14
     * @param ids ids
     */
    @Override
    public void deleteByIds(List<Serializable> ids) {
        if (ids == null || ids.isEmpty()) {
              return;
        }
        List<ModuleMember> rows = listByIds(ids);
        removeByIds(ids);
        if (moduleAuthCacheCoordinator == null || rows == null || rows.isEmpty()) {
            return;
        }
        for (ModuleMember m : rows) {
            if (m == null || m.getPlatId() == null) {
                continue;
            }
            moduleAuthCacheCoordinator.invalidateForMember(
                    m.getSystemId() == null ? 0L : m.getSystemId(),
                    m.getTenantId() == null ? 0L : m.getTenantId(),
                    m.getPlatId()
            );
        }
    }
}
