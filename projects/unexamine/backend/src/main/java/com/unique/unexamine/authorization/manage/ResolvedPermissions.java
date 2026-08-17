package com.unique.unexamine.authorization.manage;

import java.util.List;
import java.util.Map;

public record ResolvedPermissions(
        List<Long> roleIds,
        List<PermissionGrant> permissions,
        Map<String, DataScopeExpression> dataScopes) {
}
