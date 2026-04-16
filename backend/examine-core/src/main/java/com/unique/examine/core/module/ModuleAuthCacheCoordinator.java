package com.unique.examine.core.module;

/**
 * 自建应用 module 权限 Redis 缓存失效（由 examine-web 的 {@code ModuleAuthService} 实现）。
 */
public interface ModuleAuthCacheCoordinator {

    void invalidateForMember(long systemId, long tenantId, long platId);

    void invalidateForRole(long systemId, long tenantId, long roleId);
}
