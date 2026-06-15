package com.unique.examine.core.module;

/**
 * 菜单上 {@code api_pattern} 等变更后，失效进程内「路径→perm_key」解析缓存（由 examine-web 实现）。
 */
public interface ModuleMenuAclRuntimeCache {

    void evictByAppId(Long appId);
}
