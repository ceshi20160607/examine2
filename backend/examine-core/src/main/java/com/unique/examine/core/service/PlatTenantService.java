package com.unique.examine.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.unique.examine.core.entity.PlatTenant;
import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.core.mapper.PlatTenantMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PlatTenantService extends ServiceImpl<PlatTenantMapper, PlatTenant> {

    public List<PlatTenant> listBySystem(Long systemId) {
        return list(new LambdaQueryWrapper<PlatTenant>()
                .eq(PlatTenant::getSystemId, systemId)
                .orderByAsc(PlatTenant::getId));
    }

    @Transactional(rollbackFor = Exception.class)
    public PlatTenant create(Long systemId, String name) {
        if (systemId == null) {
            throw new BusinessException("systemId 不能为空");
        }
        if (name == null || name.isBlank()) {
            throw new BusinessException("租户名称不能为空");
        }
        PlatTenant t = new PlatTenant();
        t.setSystemId(systemId);
        t.setName(name.trim());
        t.setStatus(1);
        save(t);
        return t;
    }

    public void updateTenant(PlatTenant t) {
        if (t.getId() != null && t.getId() == 0L) {
            throw new BusinessException("不能修改占位租户 id=0");
        }
        updateById(t);
    }

    public void removeTenant(Long id) {
        if (id == null || id == 0L) {
            throw new BusinessException("不能删除占位租户");
        }
        removeById(id);
    }
}
