package com.unique.examine.plat.manage.account;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Account profile API models.
 */
public final class AccountModels {

    private AccountModels() {
    }

    public record AccountProfileVO(String accountId, String accountName, String mobile, String email,
                                   String securityLevel, List<SystemAccessSummary> systems,
                                   List<String> platformPermissions) {
    }

    public record SystemAccessSummary(String systemId, String systemName, String tenantId, String systemMemberId,
                                      List<String> roles, boolean switchable, String disabledReason) {
    }

    public record ProfileUpdateRequest(String accountName, String mobile, String email) {
    }

    public record PasswordUpdateRequest(String oldPassword, String newPassword) {
    }

    public record AccountActionResult(String result, String traceId, String auditLogId, LocalDateTime operatedAt) {
    }

    public record LoginLogVO(String logId, String identityProvider, String authMethod, String loginResult,
                             String failureReason, String ip, String device, String requestId, String traceId,
                             LocalDateTime createdAt) {
    }
}
