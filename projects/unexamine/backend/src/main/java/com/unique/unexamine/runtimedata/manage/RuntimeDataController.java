package com.unique.unexamine.runtimedata.manage;

import com.unique.unexamine.shared.manage.web.ApiResult;
import com.unique.unexamine.authentication.manage.AuthenticationContextHolder;
import com.unique.unexamine.shared.manage.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/runtime/modules/{moduleCode}/records")
public class RuntimeDataController {
    private final RuntimeDataService runtimeDataService;

    public RuntimeDataController(RuntimeDataService runtimeDataService) {
        this.runtimeDataService = runtimeDataService;
    }

    @GetMapping
    public ApiResult<RuntimeRecordList> list(
            @PathVariable String moduleCode,
            @RequestParam(defaultValue = "ACTIVE") String lifecycleState,
            @RequestParam(defaultValue = "ALL") String tenantScope,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "[]") String filters,
            @RequestParam(defaultValue = "updatedAt") String sortField,
            @RequestParam(defaultValue = "DESC") String sortDirection,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        return ApiResult.ok(runtimeDataService.list(
                AuthenticationContextHolder.require(), moduleCode, lifecycleState, tenantScope,
                search, filters, sortField, sortDirection, page, pageSize, TraceIdFilter.current(request)));
    }

    @GetMapping("/share-targets")
    public ApiResult<java.util.List<TenantShareTarget>> shareTargets(
            @PathVariable String moduleCode, HttpServletRequest request) {
        return ApiResult.ok(runtimeDataService.shareTargets(AuthenticationContextHolder.require(), moduleCode,
                TraceIdFilter.current(request)));
    }

    @GetMapping("/{recordId}/shares")
    public ApiResult<java.util.List<RuntimeTenantShareView>> shares(
            @PathVariable String moduleCode, @PathVariable Long recordId, HttpServletRequest request) {
        return ApiResult.ok(runtimeDataService.shares(AuthenticationContextHolder.require(), moduleCode, recordId,
                TraceIdFilter.current(request)));
    }

    @PostMapping("/{recordId}/shares")
    public ApiResult<RuntimeTenantShareView> grantShare(
            @PathVariable String moduleCode, @PathVariable Long recordId,
            @Valid @RequestBody CreateTenantShareRequest body, HttpServletRequest request) {
        return ApiResult.ok(runtimeDataService.grantShare(AuthenticationContextHolder.require(), moduleCode, recordId,
                body, TraceIdFilter.current(request)));
    }

    @PostMapping("/{recordId}/shares/{shareId}/revoke")
    public ApiResult<RuntimeTenantShareView> revokeShare(
            @PathVariable String moduleCode, @PathVariable Long recordId, @PathVariable Long shareId,
            @Valid @RequestBody RevokeTenantShareRequest body, HttpServletRequest request) {
        return ApiResult.ok(runtimeDataService.revokeShare(AuthenticationContextHolder.require(), moduleCode, recordId,
                shareId, body, TraceIdFilter.current(request)));
    }

    @GetMapping("/{recordId}")
    public ApiResult<RuntimeRecordView> detail(
            @PathVariable String moduleCode,
            @PathVariable Long recordId,
            HttpServletRequest request) {
        return ApiResult.ok(runtimeDataService.detail(
                AuthenticationContextHolder.require(), moduleCode, recordId, TraceIdFilter.current(request)));
    }

    @GetMapping("/{recordId}/timeline")
    public ApiResult<RuntimeRecordTimeline> timeline(
            @PathVariable String moduleCode,
            @PathVariable Long recordId,
            HttpServletRequest request) {
        return ApiResult.ok(runtimeDataService.timeline(
                AuthenticationContextHolder.require(), moduleCode, recordId, TraceIdFilter.current(request)));
    }

    @GetMapping("/{recordId}/conversions")
    public ApiResult<RuntimeRecordConversions> conversions(
            @PathVariable String moduleCode,
            @PathVariable Long recordId,
            HttpServletRequest request) {
        return ApiResult.ok(runtimeDataService.conversions(
                AuthenticationContextHolder.require(), moduleCode, recordId, TraceIdFilter.current(request)));
    }

    @PostMapping("/{recordId}/transfer-preview")
    public ApiResult<RuntimeRecordTransferPreview> transferPreview(
            @PathVariable String moduleCode,
            @PathVariable Long recordId,
            @Valid @RequestBody RecordTransferRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(runtimeDataService.transferPreview(AuthenticationContextHolder.require(), moduleCode,
                recordId, body, TraceIdFilter.current(request)));
    }

    @PostMapping("/{recordId}/transfer")
    public ApiResult<RuntimeRecordView> transfer(
            @PathVariable String moduleCode,
            @PathVariable Long recordId,
            @Valid @RequestBody RecordTransferRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(runtimeDataService.transfer(AuthenticationContextHolder.require(), moduleCode,
                recordId, body, TraceIdFilter.current(request)));
    }

    @PostMapping("/{recordId}/conversion-preview")
    public ApiResult<RuntimeRecordConversionPreview> conversionPreview(
            @PathVariable String moduleCode,
            @PathVariable Long recordId,
            @Valid @RequestBody RecordConversionRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(runtimeDataService.conversionPreview(AuthenticationContextHolder.require(), moduleCode,
                recordId, body, TraceIdFilter.current(request)));
    }

    @PostMapping("/{recordId}/convert")
    public ApiResult<RuntimeRecordConversionExecution> convert(
            @PathVariable String moduleCode,
            @PathVariable Long recordId,
            @Valid @RequestBody RecordConversionRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(runtimeDataService.convert(AuthenticationContextHolder.require(), moduleCode,
                recordId, body, TraceIdFilter.current(request)));
    }

    @GetMapping("/{recordId}/lifecycle-impact")
    public ApiResult<RuntimeRecordLifecycleImpact> lifecycleImpact(
            @PathVariable String moduleCode,
            @PathVariable Long recordId,
            @RequestParam String action,
            HttpServletRequest request) {
        return ApiResult.ok(runtimeDataService.lifecycleImpact(AuthenticationContextHolder.require(), moduleCode,
                recordId, action, TraceIdFilter.current(request)));
    }

    @PostMapping("/{recordId}/archive")
    public ApiResult<RuntimeRecordView> archive(
            @PathVariable String moduleCode,
            @PathVariable Long recordId,
            @Valid @RequestBody RecordLifecycleRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(runtimeDataService.archive(AuthenticationContextHolder.require(), moduleCode,
                recordId, body, TraceIdFilter.current(request)));
    }

    @PostMapping("/{recordId}/delete")
    public ApiResult<RuntimeRecordView> delete(
            @PathVariable String moduleCode,
            @PathVariable Long recordId,
            @Valid @RequestBody RecordLifecycleRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(runtimeDataService.delete(AuthenticationContextHolder.require(), moduleCode,
                recordId, body, TraceIdFilter.current(request)));
    }

    @PostMapping("/{recordId}/restore")
    public ApiResult<RuntimeRecordView> restore(
            @PathVariable String moduleCode,
            @PathVariable Long recordId,
            @Valid @RequestBody RecordLifecycleRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(runtimeDataService.restore(AuthenticationContextHolder.require(), moduleCode,
                recordId, body, TraceIdFilter.current(request)));
    }

    @GetMapping("/{recordId}/channels/{channel}")
    public ApiResult<RuntimeRecordView> channelView(
            @PathVariable String moduleCode,
            @PathVariable Long recordId,
            @PathVariable String channel,
            HttpServletRequest request) {
        return ApiResult.ok(runtimeDataService.channelView(AuthenticationContextHolder.require(), moduleCode,
                recordId, channel, TraceIdFilter.current(request)));
    }

    @PostMapping
    public ApiResult<RuntimeRecordView> create(
            @PathVariable String moduleCode,
            @Valid @RequestBody CreateRuntimeRecordRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(runtimeDataService.create(
                AuthenticationContextHolder.require(), moduleCode, body, TraceIdFilter.current(request)));
    }

    @PutMapping("/{recordId}")
    public ApiResult<RuntimeRecordView> update(
            @PathVariable String moduleCode,
            @PathVariable Long recordId,
            @Valid @RequestBody UpdateRuntimeRecordRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(runtimeDataService.update(
                AuthenticationContextHolder.require(), moduleCode, recordId, body, TraceIdFilter.current(request)));
    }
}
