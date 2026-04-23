package com.unique.examine.web.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.module.ModuleAuthCacheCoordinator;
import com.unique.examine.core.security.ModuleAuthContextHolder;
import com.unique.examine.module.mapper.ModuleMemberMapper;
import com.unique.examine.plat.entity.po.PlatSystem;
import com.unique.examine.plat.service.IPlatSystemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Objects;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 自建系统内 module 权限：所有者通配符；成员经角色-权限表解析；Redis 缓存（TTL 与 README/ADR 5min 一致）。
 */
@Service
public class ModuleAuthService implements ModuleAuthCacheCoordinator {

    private static final String CACHE_PREFIX = "examine:modauth:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    @Autowired
    private IPlatSystemService platSystemService;
    @Autowired
    private ModuleMemberMapper moduleMemberMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    public Set<String> resolveAndCache(long systemId, long tenantId, long platId) {
        PlatSystem sys = platSystemService.getById(systemId);
        if (sys != null && Objects.equals(platId, sys.getOwnerPlatAccountId())) {
            return Set.of(ModuleAuthContextHolder.OWNER_WILDCARD);
        }
        String key = cacheKey(systemId, tenantId, platId);
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json != null && !json.isBlank()) {
            try {
                List<String> list = objectMapper.readValue(json, new TypeReference<>() {});
                return new HashSet<>(list);
            } catch (Exception ignore) {
                // fall through to DB
            }
        }
        List<String> rows = moduleMemberMapper.selectModulePermKeysByPlat(systemId, tenantId, platId);
        Set<String> set = new HashSet<>();
        if (rows != null) {
            for (String r : rows) {
                if (r != null) {
                    String t = r.trim();
                    if (!t.isEmpty()) {
                        set.add(t);
                    }
                }
            }
        }
        try {
            stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(set), CACHE_TTL);
        } catch (Exception ignore) {
            // ignore cache write failure
        }
        return set;
    }

    public void invalidateCache(Long systemId, Long tenantId, Long platId) {
        if (systemId == null || tenantId == null || platId == null) {
            return;
        }
        stringRedisTemplate.delete(cacheKey(systemId, tenantId, platId));
    }

    @Override
    public void invalidateForMember(long systemId, long tenantId, long platId) {
        invalidateCache(systemId, tenantId, platId);
    }

    @Override
    public void invalidateForRole(long systemId, long tenantId, long roleId) {
        if (roleId <= 0L) {
            return;
        }
        List<Long> platIds = moduleMemberMapper.selectPlatIdsByRole(systemId, tenantId, roleId);
        if (platIds == null || platIds.isEmpty()) {
            return;
        }
        for (Long pid : platIds) {
            if (pid != null) {
                invalidateCache(systemId, tenantId, pid);
            }
        }
    }

    private static String cacheKey(long systemId, long tenantId, long platId) {
        return CACHE_PREFIX + systemId + ":" + tenantId + ":" + platId;
    }
}
