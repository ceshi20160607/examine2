package com.unique.examine.plat.manage.context;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * System and tenant context API models.
 */
public final class ContextModels {

    private ContextModels() {
    }

    public record SystemSwitchRequest(String systemId, String tenantId, String reason) {
    }

    public record TenantSwitchRequest(String tenantId, String reason) {
    }

    public record SwitchOption(String systemId, String systemName, String tenantId, String tenantName,
                               boolean switchable, String disabledReason) {
    }

    public record PermissionSnapshotSummary(String snapshotId, String permissionVersion, String disabledReason) {
    }

    public record SystemSwitchContext(String accountId, String accountMemberBindingId, String systemId,
                                      String systemCode, String systemName, String tenantId,
                                      String systemMemberId, List<String> effectiveRoleIds,
                                      Map<String, Object> dataScope,
                                      PermissionSnapshotSummary permissionSnapshotSummary,
                                      Map<String, Object> messageTodoScope, LocalDateTime expiresAt) {
    }

    public record TenantSwitchContext(String systemId, String tenantId, String tenantName,
                                      List<String> tenantRoleIds, Map<String, Object> tenantDataScope,
                                      boolean tenantSwitchable, String disabledReason,
                                      PermissionSnapshotSummary permissionSnapshotSummary) {
    }
}
