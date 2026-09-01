package com.unique.unexamine.foundation.manage.control;

import com.unique.unexamine.authentication.manage.AuthenticationContextHolder;
import com.unique.unexamine.authorization.manage.RequirePermission;
import com.unique.unexamine.shared.manage.web.ApiResult;
import com.unique.unexamine.shared.manage.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/system/foundation-control")
@RequirePermission(resourceType = "CONFIG", resourceCode = "SYSTEM", actionCode = "MANAGE")
public class FoundationControlController {
    private final FoundationControlService controlService;
    private final FoundationPolicyService policyService;

    public FoundationControlController(FoundationControlService controlService, FoundationPolicyService policyService) {
        this.controlService = controlService;
        this.policyService = policyService;
    }

    @GetMapping
    public ApiResult<FoundationControlModels.ControlOverview> overview() {
        return ApiResult.ok(controlService.overview(AuthenticationContextHolder.require()));
    }

    @PostMapping("/commands")
    public ApiResult<FoundationControlModels.CommandResult> submitCommand(
            @Valid @RequestBody FoundationControlModels.SubmitCommandRequest input,
            HttpServletRequest request) {
        return ApiResult.ok(controlService.submit(AuthenticationContextHolder.require(), input,
                TraceIdFilter.current(request)));
    }

    @GetMapping("/cache/permission")
    public ApiResult<FoundationControlModels.CacheProbe> permissionCache() {
        return ApiResult.ok(controlService.cacheProbe(AuthenticationContextHolder.require()));
    }

    @PostMapping("/cache/permission/invalidate")
    public ApiResult<FoundationControlModels.CacheProbe> invalidatePermissionCache(
            @Valid @RequestBody FoundationControlModels.InvalidateCacheRequest input,
            HttpServletRequest request) {
        return ApiResult.ok(controlService.invalidateAuthorizationCache(AuthenticationContextHolder.require(), input,
                TraceIdFilter.current(request)));
    }

    @GetMapping("/feature-flags")
    public ApiResult<List<FoundationControlModels.FeatureFlagView>> featureFlags() {
        return ApiResult.ok(policyService.featureFlags(AuthenticationContextHolder.require()));
    }

    @PostMapping("/feature-flags")
    public ApiResult<FoundationControlModels.FeatureFlagView> saveFeatureFlag(
            @Valid @RequestBody FoundationControlModels.SaveFeatureFlagRequest input,
            HttpServletRequest request) {
        return ApiResult.ok(policyService.saveFeatureFlag(AuthenticationContextHolder.require(), input,
                TraceIdFilter.current(request)));
    }

    @GetMapping("/feature-flags/{flagKey}/resolve")
    public ApiResult<FoundationControlModels.FeatureFlagResolution> resolveFeatureFlag(
            @PathVariable String flagKey,
            @RequestParam(required = false) String targetCode) {
        return ApiResult.ok(policyService.resolveFeatureFlag(AuthenticationContextHolder.require(), flagKey, targetCode));
    }

    @GetMapping("/quotas")
    public ApiResult<List<FoundationControlModels.QuotaView>> quotas() {
        return ApiResult.ok(policyService.quotas(AuthenticationContextHolder.require()));
    }

    @PostMapping("/quotas")
    public ApiResult<FoundationControlModels.QuotaView> saveQuota(
            @Valid @RequestBody FoundationControlModels.SaveQuotaRequest input,
            HttpServletRequest request) {
        return ApiResult.ok(policyService.saveQuota(AuthenticationContextHolder.require(), input,
                TraceIdFilter.current(request)));
    }

    @PostMapping("/quotas/{quotaId}/consume")
    public ApiResult<FoundationControlModels.QuotaView> consumeQuota(
            @PathVariable long quotaId,
            @Valid @RequestBody FoundationControlModels.ConsumeQuotaRequest input,
            HttpServletRequest request) {
        return ApiResult.ok(policyService.consumeQuota(AuthenticationContextHolder.require(), quotaId, input,
                TraceIdFilter.current(request)));
    }

    @GetMapping("/sequences")
    public ApiResult<List<FoundationControlModels.SequenceView>> sequences() {
        return ApiResult.ok(policyService.sequences(AuthenticationContextHolder.require()));
    }

    @PostMapping("/sequences")
    public ApiResult<FoundationControlModels.SequenceView> saveSequence(
            @Valid @RequestBody FoundationControlModels.SaveSequenceRequest input,
            HttpServletRequest request) {
        return ApiResult.ok(policyService.saveSequence(AuthenticationContextHolder.require(), input,
                TraceIdFilter.current(request)));
    }

    @PostMapping("/sequences/{sequenceId}/next")
    public ApiResult<FoundationControlModels.SequenceValue> nextSequence(
            @PathVariable long sequenceId,
            HttpServletRequest request) {
        return ApiResult.ok(policyService.nextSequence(AuthenticationContextHolder.require(), sequenceId,
                TraceIdFilter.current(request)));
    }
}
