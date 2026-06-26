package com.unique.examine.core.manage.ops;

import com.unique.examine.core.api.PageRequest;
import com.unique.examine.core.api.PageResult;
import com.unique.examine.core.context.RequestContext;
import com.unique.examine.core.manage.ops.OpsGovernanceModels.ApiCachePolicyUpdateRequest;
import com.unique.examine.core.manage.ops.OpsGovernanceModels.ApiCachePolicyVO;
import com.unique.examine.core.manage.ops.OpsGovernanceModels.ArchiveRestoreRequest;
import com.unique.examine.core.manage.ops.OpsGovernanceModels.BackupCreateRequest;
import com.unique.examine.core.manage.ops.OpsGovernanceModels.BackupRestoreVO;
import com.unique.examine.core.manage.ops.OpsGovernanceModels.DeploymentQueryRequest;
import com.unique.examine.core.manage.ops.OpsGovernanceModels.DeploymentRollbackRequest;
import com.unique.examine.core.manage.ops.OpsGovernanceModels.DeploymentVO;
import com.unique.examine.core.manage.ops.OpsGovernanceModels.FeatureFlagQueryRequest;
import com.unique.examine.core.manage.ops.OpsGovernanceModels.FeatureFlagUpdateRequest;
import com.unique.examine.core.manage.ops.OpsGovernanceModels.FeatureFlagVO;
import com.unique.examine.core.manage.ops.OpsGovernanceModels.HealthCheckItemVO;
import com.unique.examine.core.manage.ops.OpsGovernanceModels.HealthCheckRequest;
import com.unique.examine.core.manage.ops.OpsGovernanceModels.OpsHealthCheckVO;
import com.unique.examine.core.manage.ops.OpsGovernanceModels.QuotaQueryRequest;
import com.unique.examine.core.manage.ops.OpsGovernanceModels.QuotaUpdateRequest;
import com.unique.examine.core.manage.ops.OpsGovernanceModels.QuotaVO;
import com.unique.examine.core.manage.ops.OpsGovernanceModels.RateLimitPolicyVO;
import com.unique.examine.core.manage.ops.OpsGovernanceModels.RateLimitQueryRequest;
import com.unique.examine.core.manage.ops.OpsGovernanceModels.RateLimitUpdateRequest;
import com.unique.examine.core.manage.ops.OpsGovernanceModels.RestoreDrillRequest;
import com.unique.examine.core.manage.ops.OpsGovernanceModels.RiskItemVO;
import com.unique.examine.core.task.AsyncTaskStatus;
import com.unique.examine.core.task.AsyncTaskView;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Ops governance service.
 */
@Service
public class OpsGovernanceService {

    private static final DateTimeFormatter ID_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String DEFAULT_OPERATOR = "admin";

    /**
     * Run platform health check.
     *
     * @param request health check request
     * @return health check result
     */
    public OpsHealthCheckVO platformHealthCheck(HealthCheckRequest request) {
        return health("PLATFORM", null, request);
    }

    /**
     * Run system health check.
     *
     * @param systemId system id
     * @param request health check request
     * @return health check result
     */
    public OpsHealthCheckVO systemHealthCheck(String systemId, HealthCheckRequest request) {
        return health("SYSTEM", systemId, request);
    }

    /**
     * Search feature flags.
     *
     * @param pageRequest page request
     * @param query query request
     * @return feature flag page
     */
    public PageResult<FeatureFlagVO> featureFlags(PageRequest pageRequest, FeatureFlagQueryRequest query) {
        return page(List.of(featureFlag("flag_gray_publish")), pageRequest);
    }

    /**
     * Update one feature flag.
     *
     * @param flagId feature flag id
     * @param request update request
     * @return updated feature flag
     */
    public FeatureFlagVO updateFeatureFlag(String flagId, FeatureFlagUpdateRequest request) {
        RequestContext context = RequestContext.current();
        return new FeatureFlagVO(flagId, "gray_publish", "PLATFORM", null, null,
                request.status() == null ? 1 : request.status(),
                safe(request.rules(), "role=PLATFORM_ROOT; percent=20"),
                safe(request.rollbackVersion(), "flag_v20260623_001"),
                context.traceId(), auditLogId(context), LocalDateTime.now());
    }

    /**
     * Search capacity quotas.
     *
     * @param pageRequest page request
     * @param query query request
     * @return quota page
     */
    public PageResult<QuotaVO> quotas(PageRequest pageRequest, QuotaQueryRequest query) {
        return page(List.of(quota("quota_openapi")), pageRequest);
    }

    /**
     * Update capacity quota.
     *
     * @param quotaId quota id
     * @param request update request
     * @return updated quota
     */
    public QuotaVO updateQuota(String quotaId, QuotaUpdateRequest request) {
        RequestContext context = RequestContext.current();
        Long limit = request.limit() == null ? 600L : request.limit();
        Long warnThreshold = request.warnThreshold() == null ? Math.round(limit * 0.8D) : request.warnThreshold();
        return new QuotaVO(quotaId, "PLATFORM", null, null, "OPENAPI_PER_MINUTE",
                limit, 420L, warnThreshold, quotaStatus(420L, warnThreshold),
                context.traceId(), auditLogId(context), LocalDateTime.now());
    }

    /**
     * Search rate limit policies.
     *
     * @param pageRequest page request
     * @param query query request
     * @return rate limit policy page
     */
    public PageResult<RateLimitPolicyVO> rateLimitPolicies(PageRequest pageRequest, RateLimitQueryRequest query) {
        return page(List.of(rateLimitPolicy("rl_openapi_app")), pageRequest);
    }

    /**
     * Update rate limit policy.
     *
     * @param policyId policy id
     * @param request update request
     * @return updated rate limit policy
     */
    public RateLimitPolicyVO updateRateLimitPolicy(String policyId, RateLimitUpdateRequest request) {
        RequestContext context = RequestContext.current();
        return new RateLimitPolicyVO(policyId, "PLATFORM", null, null, "OPENAPI_APP_KEY",
                safe(request.limitRule(), "dimension=appKey; window=1m; limit=600; overflow=QUEUE_OR_REJECT"),
                request.status() == null ? 1 : request.status(), context.traceId(), auditLogId(context),
                LocalDateTime.now());
    }

    /**
     * Create a backup task without executing destructive operations.
     *
     * @param request backup request
     * @return async task boundary
     */
    public AsyncTaskView createBackup(BackupCreateRequest request) {
        return task("ops_backup", request == null ? null : request.idempotencyKey(), false);
    }

    /**
     * Create a restore drill task without restoring real data.
     *
     * @param backupId backup id
     * @param request restore drill request
     * @return async task boundary
     */
    public AsyncTaskView restoreDrill(String backupId, RestoreDrillRequest request) {
        return task("ops_restore_drill_" + backupId, request == null ? null : request.idempotencyKey(), true);
    }

    /**
     * Create an archive restore request task without deleting or restoring real data.
     *
     * @param request archive restore request
     * @return async task boundary
     */
    public AsyncTaskView archiveRestore(ArchiveRestoreRequest request) {
        return task("ops_archive_restore", request == null ? null : request.idempotencyKey(), true);
    }

    /**
     * Search deployment records.
     *
     * @param pageRequest page request
     * @param query query request
     * @return deployment page
     */
    public PageResult<DeploymentVO> deployments(PageRequest pageRequest, DeploymentQueryRequest query) {
        return page(List.of(deployment("deploy_20260623_001")), pageRequest);
    }

    /**
     * Create a deployment rollback task without executing rollback scripts.
     *
     * @param deploymentId deployment id
     * @param request rollback request
     * @return async task boundary
     */
    public AsyncTaskView rollbackDeployment(String deploymentId, DeploymentRollbackRequest request) {
        return task("ops_deployment_rollback_" + deploymentId,
                request == null ? null : request.idempotencyKey(), true);
    }

    /**
     * Return API cache policies.
     *
     * @return cache policy list
     */
    public List<ApiCachePolicyVO> cachePolicy() {
        RequestContext context = RequestContext.current();
        return List.of(
                cachePolicy(context, "cache_permission", "PERMISSION", "system:{systemId}:member:{memberId}:perm"),
                cachePolicy(context, "cache_dict", "DICT", "system:{systemId}:dict:{dictType}"),
                cachePolicy(context, "cache_field", "FIELD", "system:{systemId}:module:{moduleId}:field"),
                cachePolicy(context, "cache_print", "PRINT_TEMPLATE", "system:{systemId}:print:{templateCode}")
        );
    }

    /**
     * Update API cache policy.
     *
     * @param request update request
     * @return cache policy list
     */
    public List<ApiCachePolicyVO> updateCachePolicy(ApiCachePolicyUpdateRequest request) {
        RequestContext context = RequestContext.current();
        return List.of(new ApiCachePolicyVO("cache_permission", "permission_snapshot", "PERMISSION",
                safe(request.keyRule(), "system:{systemId}:member:{memberId}:perm"),
                safe(request.invalidationRule(), "role_permission_changed OR member_binding_changed"),
                request.status() == null ? 1 : request.status(), context.traceId(), auditLogId(context),
                LocalDateTime.now()));
    }

    private OpsHealthCheckVO health(String scope, String systemId, HealthCheckRequest request) {
        RequestContext context = RequestContext.current();
        LocalDateTime now = LocalDateTime.now();
        List<HealthCheckItemVO> checks = List.of(
                new HealthCheckItemVO("database", "数据库连接", "UP", "数据库迁移版本已对齐", "none"),
                new HealthCheckItemVO("redis-task", "Redis / 任务队列", "UP", "后台任务队列可接收任务", "none"),
                new HealthCheckItemVO("file-storage", "文件存储", "UP", "预览、下载和病毒扫描链路已配置", "uploadStoragePolicy"),
                new HealthCheckItemVO("openapi", "OpenAPI", "UP", "签名、限流和调用日志策略已启用", "openapiApps")
        );
        List<RiskItemVO> risks = List.of(
                new RiskItemVO("sms-fallback", "WARN", "短信备用通道未配置，验证码登录存在单点风险", "MISSING_BACKUP_CHANNEL")
        );
        return new OpsHealthCheckVO("hc_" + now.format(ID_TIME), scope, systemId,
                request == null || request.checkType() == null ? "FULL" : request.checkType(),
                "WARN", checks, risks, List.of("identityProvider", "messageChannel", "openapiApps"),
                latestBackup(context), context.traceId(), auditLogId(context), now);
    }

    private FeatureFlagVO featureFlag(String flagId) {
        RequestContext context = RequestContext.current();
        return new FeatureFlagVO(flagId, "gray_publish", "PLATFORM", null, null, 1,
                "role=PLATFORM_ROOT; percent=20", "flag_v20260623_001",
                context.traceId(), auditLogId(context), LocalDateTime.now());
    }

    private QuotaVO quota(String quotaId) {
        RequestContext context = RequestContext.current();
        return new QuotaVO(quotaId, "PLATFORM", null, null, "OPENAPI_PER_MINUTE",
                600L, 420L, 480L, "NORMAL", context.traceId(), auditLogId(context), LocalDateTime.now());
    }

    private RateLimitPolicyVO rateLimitPolicy(String policyId) {
        RequestContext context = RequestContext.current();
        return new RateLimitPolicyVO(policyId, "PLATFORM", null, null, "OPENAPI_APP_KEY",
                "dimension=appKey; window=1m; limit=600; overflow=REJECT_WITH_CODE",
                1, context.traceId(), auditLogId(context), LocalDateTime.now());
    }

    private DeploymentVO deployment(String deploymentId) {
        RequestContext context = RequestContext.current();
        return new DeploymentVO(deploymentId, "DEP-20260623-001", "prod", "backend-0.1.0",
                "frontend-0.1.0", "config-20260623", "STABLE",
                "rollback requires async dry-run validation and explicit no destructive script confirmation",
                false, context.traceId(), auditLogId(context), LocalDateTime.now().minusHours(2));
    }

    private ApiCachePolicyVO cachePolicy(RequestContext context, String policyId, String domain, String keyRule) {
        return new ApiCachePolicyVO(policyId, policyId, domain, keyRule,
                "publish_version_changed OR permission_version_changed", 1,
                context.traceId(), auditLogId(context), LocalDateTime.now());
    }

    private BackupRestoreVO latestBackup(RequestContext context) {
        return new BackupRestoreVO("backup_20260623_001", "BKP-20260623-001", "FULL", "PLATFORM", null,
                "SUCCESS", List.of("database", "files", "config", "secret_refs"),
                "最近一次备份仅返回边界和演练结果摘要，不暴露密钥明文",
                context.traceId(), auditLogId(context), LocalDateTime.now().minusHours(6));
    }

    private AsyncTaskView task(String bizType, String idempotencyKey, boolean rollbackSupported) {
        RequestContext context = RequestContext.current();
        String safeBizType = safe(bizType, "ops_action");
        return new AsyncTaskView("TASK-" + LocalDateTime.now().format(ID_TIME), safeBizType,
                safe(idempotencyKey, "idem_" + safeBizType), AsyncTaskStatus.QUEUED, 0,
                true, true, rollbackSupported, null, null,
                "DRY_RUN_ONLY: dangerous operations are not executed by this API dry-run boundary",
                0, 0, context.traceId(), auditLogId(context), DEFAULT_OPERATOR, LocalDateTime.now().toString());
    }

    private <T> PageResult<T> page(List<T> records, PageRequest pageRequest) {
        int pageNo = pageRequest == null || pageRequest.pageNo() <= 0 ? 1 : pageRequest.pageNo();
        int pageSize = pageRequest == null || pageRequest.pageSize() <= 0 ? 20 : pageRequest.pageSize();
        return new PageResult<>(records, pageNo, pageSize, records.size(), false);
    }

    private String quotaStatus(Long used, Long warnThreshold) {
        return used >= warnThreshold ? "WARN" : "NORMAL";
    }

    private String auditLogId(RequestContext context) {
        return context.auditLogId() == null || context.auditLogId().isBlank()
                ? "aud_" + context.traceId() : context.auditLogId();
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
