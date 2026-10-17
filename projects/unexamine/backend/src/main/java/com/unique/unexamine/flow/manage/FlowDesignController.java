package com.unique.unexamine.flow.manage;

import com.unique.unexamine.authentication.manage.AuthenticationContextHolder;
import com.unique.unexamine.application.manage.ApplicationModels;
import com.unique.unexamine.shared.manage.web.ApiResult;
import com.unique.unexamine.shared.manage.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/flows")
public class FlowDesignController {
    private final FlowDesignService service;

    public FlowDesignController(FlowDesignService service) {
        this.service = service;
    }

    /** 仅供当前系统 Flow 设计者选择已授权模型。 */
    @GetMapping("/ai-models")
    public ApiResult<List<Map<String, Object>>> aiModels() {
        return ApiResult.ok(service.aiModels(AuthenticationContextHolder.require()));
    }

    /** 仅返回当前有权模块的已发布条件字段，不携带真实记录或配置默认值。 */
    @GetMapping("/condition-fields")
    public ApiResult<List<FlowBindingModels.ConditionField>> conditionFields(@RequestParam Long moduleId) {
        return ApiResult.ok(service.conditionFields(AuthenticationContextHolder.require(), moduleId));
    }

    /** 中文节点字段选择目录仅提供权限裁剪后的元数据，不返回默认值或业务值。 */
    @GetMapping("/node-form-fields")
    public ApiResult<List<FlowDesignModels.NodeFormField>> nodeFormFields(@RequestParam Long moduleId) {
        return ApiResult.ok(service.nodeFormFields(AuthenticationContextHolder.require(), moduleId));
    }

    /** Webhook/外部节点使用普通选择器读取发布回调，不向浏览器返回地址和签名引用。 */
    @GetMapping("/callback-catalog")
    public ApiResult<List<ApplicationModels.FlowCallbackOption>> callbackCatalog() {
        return ApiResult.ok(service.callbackCatalog(AuthenticationContextHolder.require()));
    }

    @GetMapping
    public ApiResult<List<FlowDesignModels.FlowView>> list() {
        return ApiResult.ok(service.list(AuthenticationContextHolder.require()));
    }

    @PostMapping
    public ApiResult<FlowDesignModels.FlowView> create(
            @Valid @RequestBody FlowDesignModels.CreateFlowRequest body, HttpServletRequest request) {
        return ApiResult.ok(service.create(AuthenticationContextHolder.require(), body,
                TraceIdFilter.current(request)));
    }

    @GetMapping("/{flowId}")
    public ApiResult<FlowDesignModels.FlowView> detail(@PathVariable Long flowId) {
        return ApiResult.ok(service.detail(AuthenticationContextHolder.require(), flowId));
    }

    /** 管理员只修改流程显示名，编码和已有发布引用保持原值。 */
    @PutMapping("/{flowId}/name")
    public ApiResult<FlowDesignModels.FlowView> rename(@PathVariable Long flowId,
            @Valid @RequestBody FlowDesignModels.RenameFlowRequest body, HttpServletRequest request) {
        return ApiResult.ok(service.rename(AuthenticationContextHolder.require(), flowId, body,
                TraceIdFilter.current(request)));
    }

    @PutMapping("/{flowId}/draft")
    public ApiResult<FlowDesignModels.FlowView> saveDraft(
            @PathVariable Long flowId, @Valid @RequestBody FlowDesignModels.SaveDraftRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(service.saveDraft(AuthenticationContextHolder.require(), flowId, body,
                TraceIdFilter.current(request)));
    }

    @GetMapping("/{flowId}/publication-check")
    public ApiResult<FlowDesignModels.PublicationCheck> publicationCheck(@PathVariable Long flowId) {
        return ApiResult.ok(service.publicationCheck(AuthenticationContextHolder.require(), flowId));
    }

    @PostMapping("/{flowId}/simulate")
    public ApiResult<FlowDesignModels.SimulationResult> simulate(
            @PathVariable Long flowId, @RequestBody(required = false) FlowDesignModels.SimulateRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(service.simulate(AuthenticationContextHolder.require(), flowId,
                body == null ? null : body.variables(), TraceIdFilter.current(request)));
    }

    @PostMapping("/{flowId}/publish")
    public ApiResult<FlowDesignModels.PublicationResult> publish(
            @PathVariable Long flowId, @Valid @RequestBody FlowDesignModels.PublishRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(service.publish(AuthenticationContextHolder.require(), flowId, body,
                TraceIdFilter.current(request)));
    }

    /** 管理员暂停或恢复流程的新发起；在途实例继续使用原发布快照。 */
    @PutMapping("/{flowId}/status")
    public ApiResult<FlowDesignModels.FlowView> changeStatus(
            @PathVariable Long flowId, @Valid @RequestBody FlowDesignModels.ChangeStatusRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(service.changeStatus(AuthenticationContextHolder.require(), flowId, body,
                TraceIdFilter.current(request)));
    }
}
