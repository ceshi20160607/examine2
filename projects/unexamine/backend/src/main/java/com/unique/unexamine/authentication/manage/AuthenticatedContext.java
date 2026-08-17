package com.unique.unexamine.authentication.manage;

import com.unique.unexamine.authorization.manage.DataScopeExpression;
import com.unique.unexamine.authorization.manage.PermissionGrant;

import java.util.List;
import java.util.Map;

public record AuthenticatedContext(
        Long sessionId,
        Long accountId,
        Long systemId,
        Long tenantId,
        Long memberId,
        String username,
        String displayName,
        List<Long> roleIds,
        List<PermissionGrant> permissions,
        Map<String, DataScopeExpression> dataScopes) {
}
