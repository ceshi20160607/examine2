package com.unique.unexamine.system.manage;

import com.unique.unexamine.authorization.manage.DataScopeExpression;

import java.util.List;
import java.util.Map;

public record TenantSwitchContext(
        Long tenantId,
        List<Long> tenantRoleIds,
        Map<String, DataScopeExpression> tenantDataScope,
        boolean tenantSwitchable,
        String disabledReason) {
}
