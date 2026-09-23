package com.unique.unexamine.runtimedata.manage;

import com.unique.unexamine.foundation.manage.control.BusinessAction;

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
    private final TenantRangeSharingService rangeSharingService;

    public RuntimeDataController(RuntimeDataService runtimeDataService, TenantRangeSharingService rangeSharingService) {
        this.runtimeDataService = runtimeDataService;
        this.rangeSharingService = rangeSharingService;
    }

    /** 来源租户通过结构化字段条件管理动态范围，不开放目标租户重新共享。 */
    @GetMapping("/range-share-fields")
    public ApiResult<java.util.List<TenantRangeShareModels.ConditionField>> rangeShareFields(@PathVariable String moduleCode) {
        return ApiResult.ok(rangeSharingService.conditionFields(AuthenticationContextHolder.require(), moduleCode));
    }

    @GetMapping("/range-shares")
    public ApiResult<java.util.List<TenantRangeShareModels.View>> rangeShares(@PathVariable String moduleCode) {
        return ApiResult.ok(rangeSharingService.list(AuthenticationContextHolder.require(), moduleCode));
    }

    @PostMapping("/range-shares")
    public ApiResult<TenantRangeShareModels.View> grantRangeShare(@PathVariable String moduleCode,
            @Valid @RequestBody TenantRangeShareModels.Grant body, HttpServletRequest request) {
        return ApiResult.ok(rangeSharingService.grant(AuthenticationContextHolder.require(), moduleCode, body, TraceIdFilter.current(request)));
    }

    @PostMapping("/range-shares/{shareId}/revoke")
    public ApiResult<TenantRangeShareModels.View> revokeRangeShare(@PathVariable String moduleCode, @PathVariable Long shareId,
            @Valid @RequestBody RevokeTenantShareRequest body, HttpServletRequest request) {
        return ApiResult.ok(rangeSharingService.revoke(AuthenticationContextHolder.require(), moduleCode, shareId, body, TraceIdFilter.current(request)));
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

    /** 筛选目录只描述当前真实访问关系下的字段，不返回来源配置。 */
    @GetMapping("/query-fields")
    public ApiResult<java.util.List<RuntimeListQueryField>> queryFields(
            @PathVariable String moduleCode,
            @RequestParam(defaultValue = "ACTIVE") String lifecycleState,
            @RequestParam(defaultValue = "ALL") String tenantScope,
            HttpServletRequest request) {
        return ApiResult.ok(runtimeDataService.queryFields(AuthenticationContextHolder.require(), moduleCode,
                lifecycleState, tenantScope, TraceIdFilter.current(request)));
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

    /** 记录编辑或转移的来源组织选项；范围与字段写权限由服务端检查。 */
    @GetMapping("/{recordId}/organization-options")
    public ApiResult<com.unique.unexamine.authorization.manage.SystemPeopleDirectoryModels.Directory> organizationOptions(
            @PathVariable String moduleCode, @PathVariable Long recordId,
            @RequestParam(defaultValue = "UPDATE") String actionCode,
            @RequestParam(required = false) String fieldCode, HttpServletRequest request) {
        return ApiResult.ok(runtimeDataService.organizationOptions(AuthenticationContextHolder.require(), moduleCode,
                recordId, actionCode, fieldCode, TraceIdFilter.current(request)));
    }

    /** 编辑入口直接使用 UPDATE 鉴权的最新记录和来源表单，不借用 DETAIL 权限。 */
    @GetMapping("/{recordId}/edit-context")
    public ApiResult<RuntimeRecordEditContext> editContext(
            @PathVariable String moduleCode, @PathVariable Long recordId, HttpServletRequest request) {
        return ApiResult.ok(runtimeDataService.editContext(AuthenticationContextHolder.require(), moduleCode,
                recordId, TraceIdFilter.current(request)));
    }

    /** 读取实际来源手动动作的可填写参数，不借用接收方动作配置。 */
    @GetMapping("/{recordId}/actions/{actionCode}/context")
    public ApiResult<RuntimeManualActionContext> manualActionContext(
            @PathVariable String moduleCode, @PathVariable Long recordId, @PathVariable String actionCode,
            HttpServletRequest request) {
        return ApiResult.ok(runtimeDataService.manualActionContext(AuthenticationContextHolder.require(), moduleCode,
                recordId, actionCode, TraceIdFilter.current(request)));
    }

    /** 字典来自记录字段的来源发布配置，客户端不能指定其他字典或来源租户。 */
    @GetMapping("/{recordId}/fields/{fieldCode}/dictionary")
    public ApiResult<com.unique.unexamine.moduleconfig.manage.DictionaryModels.RuntimeDictionary> fieldDictionary(
            @PathVariable String moduleCode, @PathVariable Long recordId, @PathVariable String fieldCode,
            @RequestParam(required = false) Long parentId,
            @RequestParam(defaultValue = "UPDATE") String actionCode, HttpServletRequest request) {
        return ApiResult.ok(runtimeDataService.fieldDictionary(AuthenticationContextHolder.require(), moduleCode,
                recordId, fieldCode, parentId, actionCode, TraceIdFilter.current(request)));
    }

    /** 关联选择仅返回同时具备目标记录列表和详情权限的来源租户记录。 */
    @GetMapping("/{recordId}/fields/{fieldCode}/reference-options")
    public ApiResult<java.util.List<RuntimeReferenceOption>> referenceOptions(
            @PathVariable String moduleCode, @PathVariable Long recordId, @PathVariable String fieldCode,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "UPDATE") String actionCode, HttpServletRequest request) {
        return ApiResult.ok(runtimeDataService.referenceOptions(AuthenticationContextHolder.require(), moduleCode,
                recordId, fieldCode, search, actionCode, TraceIdFilter.current(request)));
    }

    @GetMapping("/{recordId}/timeline")
    public ApiResult<RuntimeRecordTimeline> timeline(
            @PathVariable String moduleCode,
            @PathVariable Long recordId,
            HttpServletRequest request) {
        return ApiResult.ok(runtimeDataService.timeline(
                AuthenticationContextHolder.require(), moduleCode, recordId, TraceIdFilter.current(request)));
    }

    /** 解除来源记录的转化关系；版本和原因来自结构化表单，权限由服务端重新检查。 */
    @PostMapping("/{recordId}/conversions/unlink")
    public ApiResult<?> unlinkConversion(@PathVariable String moduleCode, @PathVariable Long recordId,
            @Valid @RequestBody RecordConversionUnlinkRequest body, HttpServletRequest request) {
        return actionResponse(runtimeDataService.unlinkConversionAction(AuthenticationContextHolder.require(), moduleCode,
                recordId, body, TraceIdFilter.current(request), BusinessAction.Options.page(request.getHeader("Idempotency-Key"))), request);
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
    public ApiResult<?> transfer(
            @PathVariable String moduleCode,
            @PathVariable Long recordId,
            @Valid @RequestBody RecordTransferRequest body,
            HttpServletRequest request) {
        return actionResponse(runtimeDataService.transferAction(AuthenticationContextHolder.require(), moduleCode,
                recordId, body, TraceIdFilter.current(request),
                BusinessAction.Options.page(request.getHeader("Idempotency-Key"))), request);
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
    public ApiResult<?> convert(
            @PathVariable String moduleCode,
            @PathVariable Long recordId,
            @Valid @RequestBody RecordConversionRequest body,
            HttpServletRequest request) {
        return actionResponse(runtimeDataService.convertAction(AuthenticationContextHolder.require(), moduleCode,
                recordId, body, TraceIdFilter.current(request),
                BusinessAction.Options.page(request.getHeader("Idempotency-Key"))), request);
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
    public ApiResult<?> delete(
            @PathVariable String moduleCode,
            @PathVariable Long recordId,
            @Valid @RequestBody RecordLifecycleRequest body,
            HttpServletRequest request) {
        return actionResponse(runtimeDataService.deleteAction(AuthenticationContextHolder.require(), moduleCode,
                recordId, body, TraceIdFilter.current(request), BusinessAction.Options.page(request.getHeader("Idempotency-Key"))), request);
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
    public ApiResult<?> create(
            @PathVariable String moduleCode,
            @Valid @RequestBody CreateRuntimeRecordRequest body,
            HttpServletRequest request) {
        return actionResponse(runtimeDataService.createAction(
                AuthenticationContextHolder.require(), moduleCode, body, TraceIdFilter.current(request), BusinessAction.Options.page(request.getHeader("Idempotency-Key"))), request);
    }

    @PutMapping("/{recordId}")
    public ApiResult<?> update(
            @PathVariable String moduleCode,
            @PathVariable Long recordId,
            @Valid @RequestBody UpdateRuntimeRecordRequest body,
            HttpServletRequest request) {
        return actionResponse(runtimeDataService.updateAction(
                AuthenticationContextHolder.require(), moduleCode, recordId, body, TraceIdFilter.current(request), BusinessAction.Options.page(request.getHeader("Idempotency-Key"))), request);
    }

    @PostMapping("/{recordId}/actions/{actionCode}")
    public ApiResult<?> executeManualAction(
            @PathVariable String moduleCode,
            @PathVariable Long recordId,
            @PathVariable String actionCode,
            @Valid @RequestBody ManualRecordActionRequest body,
            HttpServletRequest request) {
        return actionResponse(runtimeDataService.manualAction(
                AuthenticationContextHolder.require(), moduleCode, recordId, actionCode, body,
                TraceIdFilter.current(request), BusinessAction.Options.page(request.getHeader("Idempotency-Key"))), request);
    }

    @GetMapping("/action-results/{actionId}")
    public ApiResult<BusinessAction.Delivery> actionDelivery(@PathVariable String moduleCode, @PathVariable long actionId,
                                                           HttpServletRequest request) {
        return ApiResult.ok(runtimeDataService.actionDelivery(AuthenticationContextHolder.require(), moduleCode,
                actionId, TraceIdFilter.current(request)));
    }

    @PostMapping("/action-results/{actionId}/retry")
    public ApiResult<BusinessAction.Delivery> retryApproval(@PathVariable String moduleCode, @PathVariable long actionId) {
        return ApiResult.ok(runtimeDataService.retryApproval(AuthenticationContextHolder.require(), moduleCode, actionId));
    }

    private ApiResult<?> actionResponse(BusinessAction.Result<?> result, HttpServletRequest request) {
        // 兼容旧客户端的记录或转化响应；未执行业务时必须返回真实动作回执。
        return ApiResult.ok("return=action".equals(request.getHeader("Prefer")) || result.value() == null ? result : result.value());
    }
}
