package com.unique.unexamine.application.manage;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 外部平台接入的结构化管理模型；管理页不接收原始 JSON 配置。 */
public final class ApplicationProviderModels {
    private ApplicationProviderModels() {
    }

    public record RouteInput(
            @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{1,99}") String eventCode,
            @NotBlank @Pattern(regexp = "FLOW|MODULE") String resourceType,
            @NotBlank @Size(max = 100) String resourceId,
            @NotBlank @Size(max = 64) String actionCode,
            Long targetSystemId,
            Long targetTenantId) {
    }

    public record SaveConnectionRequest(
            Long id,
            Integer expectedVersion,
            @NotBlank @Pattern(regexp = "WECOM|FEISHU|DINGTALK|WECHAT") String providerCode,
            @NotBlank @Size(max = 64) String productCode,
            @NotBlank @Size(max = 200) String displayName,
            @NotBlank @Size(max = 255) String tenantKey,
            @Size(max = 255) String applicationKey,
            @Size(max = 1000) String apiSecret,
            @Size(max = 1000) String callbackToken,
            @Size(max = 1000) String encodingAesKey,
            @Valid RouteInput route) {
    }

    public record DiagnosticRequest(@NotNull Boolean activateAfterPass) {
    }

    public record SaveIdentityRequest(
            Long id,
            Integer expectedVersion,
            @NotBlank @Size(max = 255) String externalUserId,
            @NotBlank @Pattern(regexp = "PLATFORM|SYSTEM") String targetType,
            @NotNull Long targetMemberId) {
    }

    public record SendMessageRequest(
            @NotNull Long identityId,
            @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{1,99}") String templateCode,
            @NotBlank @Size(max = 200) String idempotencyKey,
            Map<String, Object> variables) {
    }

    public record SecretReferenceView(
            String secretType,
            Integer secretVersion,
            String referencePath,
            String secretHint,
            String status,
            LocalDateTime createdTime) {
    }

    public record RouteView(
            Long id,
            String eventCode,
            String resourceType,
            String resourceId,
            String actionCode,
            Long targetSystemId,
            Long targetTenantId,
            String status,
            Integer version) {
    }

    public record ConnectionView(
            Long id,
            Long applicationId,
            String providerCode,
            String productCode,
            String connectionKey,
            String displayName,
            String tenantKey,
            String applicationKey,
            String callbackPath,
            String status,
            String diagnosticStatus,
            String diagnosticSummary,
            LocalDateTime diagnosticTime,
            List<SecretReferenceView> secrets,
            RouteView route,
            Integer version,
            LocalDateTime createdTime,
            LocalDateTime updatedTime) {
    }

    public record DiagnosticResult(
            Long connectionId,
            String status,
            String summary,
            List<String> checks,
            boolean externallyVerified,
            ConnectionView connection) {
    }

    public record EventView(
            Long id,
            String providerEventId,
            String eventCode,
            String senderExternalId,
            String status,
            Long applicationCallId,
            String responseCode,
            LocalDateTime receivedTime,
            LocalDateTime processedTime) {
    }

    public record IdentityOptionView(
            String targetType,
            Long targetMemberId,
            String memberName,
            String employeeNumber,
            String contextName) {
    }

    public record IdentityView(
            Long id,
            Long connectionId,
            String externalUserId,
            String targetType,
            Long targetMemberId,
            String memberName,
            String employeeNumber,
            String contextName,
            String status,
            Integer version,
            LocalDateTime createdTime,
            LocalDateTime updatedTime) {
    }

    public record DeliveryView(
            Long id,
            Long connectionId,
            Long identityId,
            String externalUserId,
            String memberName,
            Long messageId,
            Long messageDeliveryId,
            String idempotencyKey,
            String deepLink,
            String status,
            String errorCode,
            LocalDateTime createTime,
            LocalDateTime updateTime,
            LocalDateTime sentTime) {
    }

    public record CapabilityView(
            String providerCode,
            String providerName,
            String productCode,
            String productName,
            String implementationStatus,
            String identityBoundary,
            String callbackBoundary,
            String officialDocumentation) {
    }
}
