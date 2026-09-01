package com.unique.unexamine.authentication.manage;

import com.unique.unexamine.authorization.manage.DataScopeExpression;
import com.unique.unexamine.authorization.manage.PermissionGrant;

import java.util.List;
import java.util.Map;

public record AuthenticatedContext(
        Long sessionId,
        Long accountId,
        Long platformId,
        Long systemId,
        Long tenantId,
        Long memberId,
        Long tenantMemberId,
        String username,
        String displayName,
        String mfaLevel,
        List<Long> roleIds,
        List<PermissionGrant> permissions,
        Map<String, DataScopeExpression> dataScopes) {
}
