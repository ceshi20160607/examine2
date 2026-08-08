package com.unique.examine.plat.manage.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.id.IdService;
import com.unique.examine.plat.base.entity.AuthzEpoch;
import com.unique.examine.plat.base.mapper.PlatAuthzEpochMapper;
import com.unique.examine.plat.base.mapper.PlatSystemMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AuthzEpochService {
    private final PlatAuthzEpochMapper epochMapper;
    private final PlatSystemMapper systemMapper;
    private final IdService idService;

    public AuthzEpochService(PlatAuthzEpochMapper epochMapper, PlatSystemMapper systemMapper, IdService idService) {
        this.epochMapper = epochMapper;
        this.systemMapper = systemMapper;
        this.idService = idService;
    }

    @Transactional
    public AuthzEpoch ensurePlatform(long actorAccountId) {
        var existing = find("PLATFORM", 0L);
        if (existing != null) {
            return existing;
        }
        var now = LocalDateTime.now();
        var epoch = new AuthzEpoch();
        epoch.setId(idService.nextId());
        epoch.setScopeType("PLATFORM");
        epoch.setScopeKey(0L);
        epoch.setEpoch(1L);
        epoch.setCreatedAt(now);
        epoch.setCreatedBy(actorAccountId);
        epoch.setUpdatedAt(now);
        epoch.setUpdatedBy(actorAccountId);
        epoch.setVersion(0L);
        epochMapper.insert(epoch);
        return epoch;
    }

    @Transactional
    public AuthzEpoch ensureSystem(long systemId, long actorAccountId) {
        var existing = find("SYSTEM", systemId);
        if (existing != null) {
            return existing;
        }
        var system = systemMapper.selectById(systemId);
        if (system == null) {
            throw new BusinessException("RESOURCE_NOT_FOUND", "系统不存在", HttpStatus.NOT_FOUND);
        }
        var now = LocalDateTime.now();
        var epoch = new AuthzEpoch();
        epoch.setId(systemId);
        epoch.setScopeType("SYSTEM");
        epoch.setScopeKey(systemId);
        epoch.setSystemId(systemId);
        epoch.setEpoch(Math.max(system.getPermissionVersion(), 1L));
        epoch.setCreatedAt(now);
        epoch.setCreatedBy(actorAccountId);
        epoch.setUpdatedAt(now);
        epoch.setUpdatedBy(actorAccountId);
        epoch.setVersion(0L);
        epochMapper.insert(epoch);
        return epoch;
    }

    @Transactional
    public long bumpPlatform(long actorAccountId) {
        return bump(ensurePlatform(actorAccountId), actorAccountId);
    }

    @Transactional
    public long bumpSystem(long systemId, long actorAccountId) {
        var next = bump(ensureSystem(systemId, actorAccountId), actorAccountId);
        var system = systemMapper.selectById(systemId);
        if (system == null) {
            throw new BusinessException("RESOURCE_NOT_FOUND", "系统不存在", HttpStatus.NOT_FOUND);
        }
        system.setPermissionVersion(next);
        system.setUpdatedAt(LocalDateTime.now());
        system.setUpdatedBy(actorAccountId);
        if (systemMapper.updateById(system) != 1) {
            throw new BusinessException("VERSION_CONFLICT", "系统权限版本已变化，请重试", HttpStatus.CONFLICT);
        }
        return next;
    }

    public long currentPlatform() {
        return require("PLATFORM", 0L).getEpoch();
    }

    public long currentSystem(long systemId) {
        return require("SYSTEM", systemId).getEpoch();
    }

    private long bump(AuthzEpoch epoch, long actorAccountId) {
        epoch.setEpoch(epoch.getEpoch() + 1);
        epoch.setUpdatedAt(LocalDateTime.now());
        epoch.setUpdatedBy(actorAccountId);
        if (epochMapper.updateById(epoch) != 1) {
            throw new BusinessException("VERSION_CONFLICT", "权限版本已变化，请重试", HttpStatus.CONFLICT);
        }
        return epoch.getEpoch();
    }

    private AuthzEpoch require(String scopeType, long scopeKey) {
        var epoch = find(scopeType, scopeKey);
        if (epoch == null) {
            throw new BusinessException("AUTHZ_STATE_MISSING", "权限状态尚未初始化", HttpStatus.CONFLICT);
        }
        return epoch;
    }

    private AuthzEpoch find(String scopeType, long scopeKey) {
        return epochMapper.selectOne(Wrappers.<AuthzEpoch>lambdaQuery()
                .eq(AuthzEpoch::getScopeType, scopeType)
                .eq(AuthzEpoch::getScopeKey, scopeKey));
    }
}
