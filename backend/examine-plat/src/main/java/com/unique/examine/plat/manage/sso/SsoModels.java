package com.unique.examine.plat.manage.sso;

import com.unique.examine.core.task.AsyncTaskView;
import com.unique.examine.plat.manage.secret.SecretModels.SecretRefVO;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SSO policy API models.
 */
public final class SsoModels {

    private SsoModels() {
    }

    public record IdentityProviderSaveRequest(String name, String protocol, String issuer, String clientId,
                                              String secretRefId, String certRefId, List<String> domainWhitelist,
                                              Map<String, Object> jitPolicy, Map<String, Object> mfaPolicy,
                                              String status) {
    }

    public record IdentityProviderVO(String providerId, String name, String protocol, String issuer, String clientId,
                                     SecretRefVO secretRef, SecretRefVO certRef, List<String> domainWhitelist,
                                     Map<String, Object> jitPolicy, Map<String, Object> mfaPolicy, String status,
                                     LocalDateTime updatedAt) {
    }

    public record IdentityProviderTestRequest(String redirectUri, String testLoginName) {
    }

    public record IdentityProviderTestResultVO(String providerId, boolean passed, String disabledReason,
                                               String traceId, String auditLogId, LocalDateTime checkedAt) {
    }

    public record IdentityProviderPublishResult(String providerId, String publishStatus, List<String> warnings,
                                                String traceId, String auditLogId, LocalDateTime publishedAt) {
    }

    public record SystemSsoPolicyUpdateRequest(List<String> enabledProviderIds, List<String> tenantDomains,
                                               Map<String, Object> orgMapping,
                                               Map<String, Object> employeeBinding,
                                               Map<String, Object> jitMemberPolicy,
                                               Map<String, Object> noMemberFeedback, String status) {
    }

    public record SystemSsoPolicyVO(String systemId, List<String> enabledProviderIds, List<String> tenantDomains,
                                    Map<String, Object> orgMapping, Map<String, Object> employeeBinding,
                                    Map<String, Object> jitMemberPolicy, Map<String, Object> noMemberFeedback,
                                    String status, String traceId, LocalDateTime updatedAt) {
    }

    public record OrgSyncPrecheckRequest(String identityProvider, String tenantId,
                                         List<String> externalDepartmentIds, List<String> externalUserIds,
                                         Boolean dryRun, String idempotencyKey) {
    }

    public record MappingPrecheckIssue(String issueType, String externalId, String displayName, String status,
                                       String targetId, String disabledReason) {
    }

    public record OrgSyncPrecheckResultVO(AsyncTaskView task, int matchedDepartmentCount,
                                          int unmatchedDepartmentCount, int matchedMemberCount,
                                          int unboundMemberCount, int draftBindingCount,
                                          List<MappingPrecheckIssue> issues, String status,
                                          String disabledReason, String traceId, String auditLogId) {
    }

    public record MemberBindingConfirmRequest(String identityProvider, String externalUserId, String externalDeptId,
                                              String accountId, String systemMemberId, String tenantId,
                                              List<String> roleIds, Map<String, Object> dataScope,
                                              String confirmNote, String idempotencyKey) {
    }

    public record MemberBindingConfirmVO(String bindingId, String accountId, String systemMemberId,
                                         String identityProvider, String externalUserId, String tenantId,
                                         String bindingStatus, List<String> roleIds, Map<String, Object> dataScope,
                                         String disabledReason, String traceId, String auditLogId,
                                         LocalDateTime updatedAt) {
    }
}
