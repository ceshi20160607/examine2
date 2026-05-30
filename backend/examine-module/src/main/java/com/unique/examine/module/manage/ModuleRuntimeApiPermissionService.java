package com.unique.examine.module.manage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.unique.examine.module.entity.po.ModuleApp;
import com.unique.examine.module.entity.po.ModuleMenu;
import com.unique.examine.module.mapper.ModuleMenuMapper;
import com.unique.examine.module.service.IModuleAppService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 从 {@code un_module_menu} 读取带 {@code api_pattern} 的菜单行，解析当前请求 URI 所需的 {@code perm_key}（内存短缓存）。
 */
@Service
public class ModuleRuntimeApiPermissionService {

    private static final Logger log = LoggerFactory.getLogger(ModuleRuntimeApiPermissionService.class);

    private static final String DEFAULT_APP_CODE = "default";
    private static final long CACHE_TTL_MS = 60_000L;

    @Autowired
    private ModuleMenuMapper moduleMenuMapper;
    @Autowired
    private IModuleAppService moduleAppService;

    private final AntPathMatcher matcher = new AntPathMatcher();
    private final ConcurrentHashMap<Long, CacheEntry> rowsByApp = new ConcurrentHashMap<>();

    private static final class CacheEntry {
        final List<ModuleMenu> rows;
        final long expiresAtMs;

        CacheEntry(List<ModuleMenu> rows, long expiresAtMs) {
            this.rows = rows;
            this.expiresAtMs = expiresAtMs;
        }
    }

    public void evictAppCache(Long appId) {
        if (appId != null) {
            rowsByApp.remove(appId);
        }
    }

    /**
     * 无 default 应用、或尚无带 api_pattern 的菜单行时返回 empty（拦截器放行）。
     */
    public Optional<String> resolveRequiredMenuPermKey(String requestUri, long systemId, long tenantId) {
        if (systemId == 0L) {
            return Optional.empty();
        }
        ModuleApp app;
        try {
            app = moduleAppService.getOne(new LambdaQueryWrapper<ModuleApp>()
                    .eq(ModuleApp::getSystemId, systemId)
                    .eq(ModuleApp::getTenantId, tenantId)
                    .eq(ModuleApp::getAppCode, DEFAULT_APP_CODE)
                    .last("LIMIT 1"));
        } catch (RuntimeException e) {
            log.warn("Module API permission default app lookup failed, skip menu ACL. uri={} systemId={} tenantId={} ex={} msg={}",
                    requestUri, systemId, tenantId, e.getClass().getName(), e.getMessage());
            return Optional.empty();
        }
        if (app == null) {
            return Optional.empty();
        }
        List<ModuleMenu> rows = loadSortedRows(app.getId());
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        for (ModuleMenu row : rows) {
            String pattern = row.getApiPattern();
            if (pattern == null || pattern.isBlank()) {
                continue;
            }
            try {
                if (matcher.match(pattern.trim(), requestUri)) {
                    String key = row.getPermKey();
                    return Optional.ofNullable(key == null ? null : key.trim()).filter(s -> !s.isEmpty());
                }
            } catch (IllegalArgumentException ignored) {
                // skip invalid api_pattern rows
            }
        }
        return Optional.empty();
    }

    private List<ModuleMenu> loadSortedRows(Long appId) {
        long now = System.currentTimeMillis();
        CacheEntry cached = rowsByApp.get(appId);
        if (cached != null && now < cached.expiresAtMs) {
            return cached.rows;
        }
        List<ModuleMenu> list;
        try {
            list = moduleMenuMapper.selectMenusWithApiPatternForAcl(appId);
        } catch (RuntimeException e) {
            log.warn("Module API permission menu lookup failed, skip menu ACL. appId={} ex={} msg={}",
                    appId, e.getClass().getName(), e.getMessage());
            rowsByApp.put(appId, new CacheEntry(List.of(), now + CACHE_TTL_MS));
            return List.of();
        }
        if (list == null || list.isEmpty()) {
            rowsByApp.put(appId, new CacheEntry(List.of(), now + CACHE_TTL_MS));
            return List.of();
        }
        List<ModuleMenu> frozen = Collections.unmodifiableList(list.stream()
                .sorted(Comparator
                        .comparingInt((ModuleMenu m) -> patternLength(m.getApiPattern())).reversed()
                        .thenComparing(ModuleMenu::getSortNo, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(ModuleMenu::getId, Comparator.nullsLast(Long::compareTo)))
                .toList());
        rowsByApp.put(appId, new CacheEntry(frozen, now + CACHE_TTL_MS));
        return frozen;
    }

    private static int patternLength(String pattern) {
        return pattern == null ? 0 : pattern.trim().length();
    }
}
