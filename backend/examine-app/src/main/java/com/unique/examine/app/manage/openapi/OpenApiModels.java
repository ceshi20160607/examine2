package com.unique.examine.app.manage.openapi;

import com.unique.examine.core.task.AsyncTaskView;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * OpenAPI 外部应用、密钥引用、限流和调用日志 API 模型。
 */
/**
 * OpenAPI external application, SecretRef, rate-limit, and call log API models.
 */
public final class OpenApiModels {

    private OpenApiModels() {
    }

    public record OpenApiAppQuery(String externalAppId, String externalAppCode, String status, String scope,
                                  String tenantId, String keyword) {
    }

    public record OpenApiAppSaveRequest(String externalAppCode, String appName, Integer status, String tenantId,
                                        List<String> scopes, String callbackUrl, RateLimitMetadata rateLimit,
                                        String secretMaterialRef, String requestedSecretVersion,
                                        String idempotencyKey) {
    }

    public record OpenApiScopeUpdateRequest(List<String> scopes, String reason, String idempotencyKey) {
    }

    public record OpenApiRateLimitUpdateRequest(RateLimitMetadata rateLimit, String reason,
                                                String idempotencyKey) {
    }

    public record OpenApiSecretRotationRequest(String newMaterialRef, String requestedVersion,
                                               Boolean dualWriteValidation, Boolean switchAfterValidation,
                                               String rollbackPlan, String idempotencyKey) {
    }

    public record OpenApiCallLogQuery(String externalAppId, String externalAppCode, String scope, String result,
                                      String startTime, String endTime, String traceId) {
    }

    public record OpenApiAppVO(String externalAppId, String systemId, String tenantId, String externalAppCode,
                               String appName, Integer status, OpenApiSecretRefVO openApiSecretRef,
                               List<OpenApiScopeVO> scopes, String callbackUrl, RateLimitMetadata rateLimit,
                               LocalDateTime lastUsedAt, OperationMetadata operation, LocalDateTime createdAt,
                               LocalDateTime updatedAt) {
    }

    public record OpenApiSecretRefVO(String secretRefId, String refType, String version, LocalDateTime expiresAt,
                                     String rotationStatus, LocalDateTime lastUsedAt, String displayName,
                                     String activeVersion, LocalDateTime nextRotationDueAt) {
    }

    public record OpenApiScopeVO(String scopeCode, String scopeName, String description, boolean enabled,
                                 String permissionCode, String version, LocalDateTime lastUsedAt,
                                 String disabledReason) {
    }

    public record RateLimitMetadata(String dimension, Integer windowSeconds, Integer limit, Integer burstLimit,
                                    String overflowPolicy, Integer status, String version,
                                    LocalDateTime updatedAt) {
    }

    public record OperationMetadata(String operation, String idempotencyKey, String traceId, String auditLogId,
                                    String result, String disabledReason, LocalDateTime operatedAt) {
    }

    public record OpenApiSecretRotationPhaseVO(String phase, String status, String disabledReason,
                                               LocalDateTime operatedAt) {
    }

    public record OpenApiSecretRotationJobVO(String jobId, OpenApiSecretRefVO openApiSecretRef, String status,
                                             String newVersion, List<OpenApiSecretRotationPhaseVO> phases,
                                             String rollbackPlan, AsyncTaskView task, OperationMetadata operation,
                                             String disabledReason, LocalDateTime createdAt) {
    }

    public record OpenApiDeleteResultVO(String externalAppId, String status, boolean deleted,
                                        OperationMetadata operation) {
    }

    public record OpenApiCallLogVO(String callLogId, String externalAppId, String externalAppCode, String appName,
                                   String systemId, String tenantId, String scope, String requestMethod,
                                   String requestPath, String result, String idempotencyKey, String traceId,
                                   String failureReason, Map<String, Object> rateLimitSnapshot,
                                   LocalDateTime createdAt) {
    }

    /**
     * 外部 OpenAPI 业务数据查询请求。
     *
     * @param pageNo 页码
     * @param pageSize 每页条数
     * @param keyword 关键字
     * @param fields 指定返回字段编码
     */
    public record ExternalRecordSearchRequest(Integer pageNo, Integer pageSize, String keyword,
                                              List<String> fields) {
    }

    /**
     * 外部 OpenAPI 业务数据行。
     *
     * @param recordId 记录编号
     * @param recordNo 业务编号
     * @param title 标题
     * @param status 状态
     * @param fields 字段值
     * @param updatedAt 更新时间
     */
    public record ExternalRecordRow(String recordId, String recordNo, String title, String status,
                                    Map<String, Object> fields, LocalDateTime updatedAt) {
    }

    /**
     * 外部 OpenAPI 写入业务数据请求。
     *
     * @param fieldValues 字段值
     * @param sourceType 来源类型
     */
    public record ExternalRecordMutationRequest(Map<String, Object> fieldValues, String sourceType) {
    }

    /**
     * 外部 OpenAPI 写入结果。
     *
     * @param recordId 记录编号
     * @param result 结果
     * @param idempotencyKey 幂等键
     * @param traceId 链路编号
     * @param operatedAt 操作时间
     */
    public record ExternalRecordMutationResult(String recordId, String result, String idempotencyKey,
                                               String traceId, LocalDateTime operatedAt) {
    }
}
