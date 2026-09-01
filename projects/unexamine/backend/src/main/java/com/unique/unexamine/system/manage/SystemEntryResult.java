package com.unique.unexamine.system.manage;

import com.unique.unexamine.authentication.manage.SessionTokens;
import com.unique.unexamine.authorization.manage.DataScopeExpression;
import com.unique.unexamine.authorization.manage.PermissionGrant;

import java.util.List;
import java.util.Map;

public record SystemEntryResult(
        Long systemId,
        String systemCode,
        String systemName,
        String tenantMode,
        Long tenantId,
        String tenantName,
        Long systemMemberId,
        Long tenantMemberId,
        List<Long> roleIds,
        List<PermissionGrant> permissions,
        Map<String, DataScopeExpression> dataScopes,
        String contextRevision,
        List<String> redrawScopes,
        TenantSwitchContext tenantSwitchContext,
        SessionTokens tokens) {
}
