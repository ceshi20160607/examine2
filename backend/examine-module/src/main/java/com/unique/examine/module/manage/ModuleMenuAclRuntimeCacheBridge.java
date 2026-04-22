package com.unique.examine.module.manage;

import com.unique.examine.core.module.ModuleMenuAclRuntimeCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ModuleMenuAclRuntimeCacheBridge implements ModuleMenuAclRuntimeCache {

    @Autowired
    private ModuleRuntimeApiPermissionService moduleRuntimeApiPermissionService;

    @Override
    public void evictByAppId(Long appId) {
        moduleRuntimeApiPermissionService.evictAppCache(appId);
    }
}
