package com.unique.examine.core.manage.ops;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.PageRequest;
import com.unique.examine.core.api.PageResult;
import com.unique.examine.core.manage.ops.OpsGovernanceModels.ApiCachePolicyUpdateRequest;
import com.unique.examine.core.manage.ops.OpsGovernanceModels.ApiCachePolicyVO;
import com.unique.examine.core.manage.ops.OpsGovernanceModels.ArchiveRestoreRequest;
import com.unique.examine.core.manage.ops.OpsGovernanceModels.BackupCreateRequest;
import com.unique.examine.core.manage.ops.OpsGovernanceModels.DeploymentQueryRequest;
import com.unique.examine.core.manage.ops.OpsGovernanceModels.DeploymentRollbackRequest;
import com.unique.examine.core.manage.ops.OpsGovernanceModels.DeploymentVO;
import com.unique.examine.core.manage.ops.OpsGovernanceModels.FeatureFlagQueryRequest;
import com.unique.examine.core.manage.ops.OpsGovernanceModels.FeatureFlagUpdateRequest;
import com.unique.examine.core.manage.ops.OpsGovernanceModels.FeatureFlagVO;
import com.unique.examine.core.manage.ops.OpsGovernanceModels.HealthCheckRequest;
import com.unique.examine.core.manage.ops.OpsGovernanceModels.OpsHealthCheckVO;
import com.unique.examine.core.manage.ops.OpsGovernanceModels.QuotaQueryRequest;
import com.unique.examine.core.manage.ops.OpsGovernanceModels.QuotaUpdateRequest;
import com.unique.examine.core.manage.ops.OpsGovernanceModels.QuotaVO;
import com.unique.examine.core.manage.ops.OpsGovernanceModels.RateLimitPolicyVO;
import com.unique.examine.core.manage.ops.OpsGovernanceModels.RateLimitQueryRequest;
import com.unique.examine.core.manage.ops.OpsGovernanceModels.RateLimitUpdateRequest;
import com.unique.examine.core.manage.ops.OpsGovernanceModels.RestoreDrillRequest;
import com.unique.examine.core.task.AsyncTaskView;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ops governance API controller.
 */
@RestController
public class OpsGovernanceController {

    private final OpsGovernanceService opsService;

    public OpsGovernanceController(OpsGovernanceService opsService) {
        this.opsService = opsService;
    }

    @PostMapping("/api/v1/platform/ops/health-check")
    public ApiResponse<OpsHealthCheckVO> platformHealthCheck(@RequestBody(required = false)
                                                            HealthCheckRequest request) {
        return ApiResponse.success(opsService.platformHealthCheck(request));
    }

    @PostMapping("/api/v1/systems/{systemId}/ops/health-check")
    public ApiResponse<OpsHealthCheckVO> systemHealthCheck(@PathVariable String systemId,
                                                           @RequestBody(required = false)
                                                           HealthCheckRequest request) {
        return ApiResponse.success(opsService.systemHealthCheck(systemId, request));
    }

    @GetMapping("/api/v1/platform/ops/feature-flags")
    public ApiResponse<PageResult<FeatureFlagVO>> featureFlags(@RequestParam(defaultValue = "1") int pageNo,
                                                               @RequestParam(defaultValue = "20") int pageSize,
                                                               FeatureFlagQueryRequest query) {
        return ApiResponse.success(opsService.featureFlags(new PageRequest(pageNo, pageSize, null,
                List.of(), List.of()), query));
    }

    @PatchMapping("/api/v1/platform/ops/feature-flags/{flagId}")
    public ApiResponse<FeatureFlagVO> updateFeatureFlag(@PathVariable String flagId,
                                                        @RequestBody FeatureFlagUpdateRequest request) {
        return ApiResponse.success(opsService.updateFeatureFlag(flagId, request));
    }

    @GetMapping("/api/v1/platform/ops/quotas")
    public ApiResponse<PageResult<QuotaVO>> quotas(@RequestParam(defaultValue = "1") int pageNo,
                                                   @RequestParam(defaultValue = "20") int pageSize,
                                                   QuotaQueryRequest query) {
        return ApiResponse.success(opsService.quotas(new PageRequest(pageNo, pageSize, null,
                List.of(), List.of()), query));
    }

    @PatchMapping("/api/v1/platform/ops/quotas/{quotaId}")
    public ApiResponse<QuotaVO> updateQuota(@PathVariable String quotaId,
                                            @RequestBody QuotaUpdateRequest request) {
        return ApiResponse.success(opsService.updateQuota(quotaId, request));
    }

    @GetMapping("/api/v1/platform/ops/rate-limit-policies")
    public ApiResponse<PageResult<RateLimitPolicyVO>> rateLimitPolicies(@RequestParam(defaultValue = "1") int pageNo,
                                                                        @RequestParam(defaultValue = "20")
                                                                        int pageSize,
                                                                        RateLimitQueryRequest query) {
        return ApiResponse.success(opsService.rateLimitPolicies(new PageRequest(pageNo, pageSize, null,
                List.of(), List.of()), query));
    }

    @PatchMapping("/api/v1/platform/ops/rate-limit-policies/{policyId}")
    public ApiResponse<RateLimitPolicyVO> updateRateLimitPolicy(@PathVariable String policyId,
                                                                @RequestBody RateLimitUpdateRequest request) {
        return ApiResponse.success(opsService.updateRateLimitPolicy(policyId, request));
    }

    @PostMapping("/api/v1/platform/ops/backups")
    public ApiResponse<AsyncTaskView> createBackup(@RequestBody BackupCreateRequest request) {
        return ApiResponse.success(opsService.createBackup(request));
    }

    @PostMapping("/api/v1/platform/ops/backups/{backupId}/restore-drill")
    public ApiResponse<AsyncTaskView> restoreDrill(@PathVariable String backupId,
                                                   @RequestBody RestoreDrillRequest request) {
        return ApiResponse.success(opsService.restoreDrill(backupId, request));
    }

    @PostMapping("/api/v1/platform/ops/archive-restore-requests")
    public ApiResponse<AsyncTaskView> archiveRestore(@RequestBody ArchiveRestoreRequest request) {
        return ApiResponse.success(opsService.archiveRestore(request));
    }

    @GetMapping("/api/v1/platform/ops/deployments")
    public ApiResponse<PageResult<DeploymentVO>> deployments(@RequestParam(defaultValue = "1") int pageNo,
                                                             @RequestParam(defaultValue = "20") int pageSize,
                                                             DeploymentQueryRequest query) {
        return ApiResponse.success(opsService.deployments(new PageRequest(pageNo, pageSize, null,
                List.of(), List.of()), query));
    }

    @PostMapping("/api/v1/platform/ops/deployments/{deploymentId}/rollback")
    public ApiResponse<AsyncTaskView> rollbackDeployment(@PathVariable String deploymentId,
                                                         @RequestBody DeploymentRollbackRequest request) {
        return ApiResponse.success(opsService.rollbackDeployment(deploymentId, request));
    }

    @GetMapping("/api/v1/platform/ops/api-cache-policy")
    public ApiResponse<List<ApiCachePolicyVO>> cachePolicy() {
        return ApiResponse.success(opsService.cachePolicy());
    }

    @PatchMapping("/api/v1/platform/ops/api-cache-policy")
    public ApiResponse<List<ApiCachePolicyVO>> updateCachePolicy(@RequestBody ApiCachePolicyUpdateRequest request) {
        return ApiResponse.success(opsService.updateCachePolicy(request));
    }
}
