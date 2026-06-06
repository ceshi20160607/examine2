package com.unique.examine.manage.service;

public interface PermissionService {
    void requireScope(Long systemId, Long tenantId);

    void requireAction(Long systemId, Long tenantId, String actionCode);

    void requirePlatformAction(String actionCode);
}
