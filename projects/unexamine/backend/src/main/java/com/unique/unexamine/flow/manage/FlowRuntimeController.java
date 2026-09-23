package com.unique.unexamine.flow.manage;

import com.unique.unexamine.authentication.manage.AuthenticationContextHolder;
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
@RequestMapping("/api/flow-runtime")
public class FlowRuntimeController {
    private final FlowRuntimeService service;

    public FlowRuntimeController(FlowRuntimeService service) {
        this.service = service;
    }

    /** 流程配置与任务转交共用受控的当前作用域人员目录。 */
    @GetMapping("/participants")
    public ApiResult<FlowRuntimeModels.ParticipantDirectory> participants() {
        return ApiResult.ok(service.participants(AuthenticationContextHolder.require()));
    }

    /** 普通审批人与发起人使用运行权限读取最小发布目录，不需要流程设计权限。 */
    @GetMapping("/flows")
    public ApiResult<List<FlowRuntimeModels.FlowCatalogEntry>> flows() {
        return ApiResult.ok(service.flows(AuthenticationContextHolder.require()));
    }

    @GetMapping("/instances")
    public ApiResult<List<FlowRuntimeModels.InstanceView>> instances() {
        return ApiResult.ok(service.instances(AuthenticationContextHolder.require()));
    }

    @PostMapping("/instances")
    public ApiResult<FlowRuntimeModels.ActionResult> start(
            @Valid @RequestBody FlowRuntimeModels.StartRequest body, HttpServletRequest request) {
        return ApiResult.ok(service.start(AuthenticationContextHolder.require(), body,
                TraceIdFilter.current(request)));
    }

    @GetMapping("/instances/{instanceId}")
    public ApiResult<FlowRuntimeModels.InstanceView> instance(@PathVariable Long instanceId) {
        return ApiResult.ok(service.instance(AuthenticationContextHolder.require(), instanceId));
    }

    /** 本人业务申请目录使用当前成员上下文，不需要来源流程目录权限。 */
    @GetMapping("/business-actions")
    public ApiResult<FlowRuntimeModels.BusinessActionApplicationsPage> businessActionApplications(
            @RequestParam(required = false) Long cursor, @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        return ApiResult.ok(service.businessActionApplications(AuthenticationContextHolder.require(), cursor,
                pageSize, TraceIdFilter.current(request)));
    }

    /** 通过本人业务动作读取审批进度，不接受客户端指定来源租户或任意实例。 */
    @GetMapping("/business-actions/{moduleCode}/{actionId}")
    public ApiResult<FlowRuntimeModels.BusinessActionProgress> businessActionProgress(
            @PathVariable String moduleCode, @PathVariable Long actionId, HttpServletRequest request) {
        return ApiResult.ok(service.businessActionProgress(AuthenticationContextHolder.require(), moduleCode,
                actionId, TraceIdFilter.current(request)));
    }

    @PostMapping("/tasks/{taskId}/actions")
    public ApiResult<FlowRuntimeModels.ActionResult> handle(
            @PathVariable Long taskId, @Valid @RequestBody FlowRuntimeModels.HandleTaskRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(service.handle(AuthenticationContextHolder.require(), taskId, body,
                TraceIdFilter.current(request)));
    }

    /** 当前任务绑定表单；不接受客户端指定记录、来源租户或字段版本。 */
    @GetMapping("/tasks/{taskId}/form")
    public ApiResult<FlowRuntimeModels.TaskFormContext> taskForm(@PathVariable Long taskId, HttpServletRequest request) {
        return ApiResult.ok(service.taskForm(AuthenticationContextHolder.require(), taskId, TraceIdFilter.current(request)));
    }

    @GetMapping("/tasks/{taskId}/form/fields/{fieldCode}/dictionary")
    public ApiResult<com.unique.unexamine.moduleconfig.manage.DictionaryModels.RuntimeDictionary> taskDictionary(
            @PathVariable Long taskId, @PathVariable String fieldCode, @RequestParam(required = false) Long parentId,
            HttpServletRequest request) {
        return ApiResult.ok(service.taskDictionary(AuthenticationContextHolder.require(), taskId, fieldCode, parentId,
                TraceIdFilter.current(request)));
    }

    @GetMapping("/tasks/{taskId}/form/fields/{fieldCode}/reference-options")
    public ApiResult<List<com.unique.unexamine.runtimedata.manage.RuntimeReferenceOption>> taskReferences(
            @PathVariable Long taskId, @PathVariable String fieldCode, @RequestParam(required = false) String search,
            HttpServletRequest request) {
        return ApiResult.ok(service.taskReferences(AuthenticationContextHolder.require(), taskId, fieldCode, search,
                TraceIdFilter.current(request)));
    }

    @GetMapping("/tasks/{taskId}/form/fields/{fieldCode}/organization-options")
    public ApiResult<com.unique.unexamine.authorization.manage.SystemPeopleDirectoryModels.Directory> taskOrganization(
            @PathVariable Long taskId, @PathVariable String fieldCode, HttpServletRequest request) {
        return ApiResult.ok(service.taskOrganization(AuthenticationContextHolder.require(), taskId, fieldCode,
                TraceIdFilter.current(request)));
    }

    @PostMapping("/instances/{instanceId}/actions")
    public ApiResult<FlowRuntimeModels.ActionResult> actOnInstance(
            @PathVariable Long instanceId, @Valid @RequestBody FlowRuntimeModels.InstanceActionRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(service.actOnInstance(AuthenticationContextHolder.require(), instanceId, body,
                TraceIdFilter.current(request)));
    }

    @PostMapping("/instances/{instanceId}/manual-node-preview")
    public ApiResult<FlowRuntimeModels.ManualNodePreview> previewManualNode(
            @PathVariable Long instanceId, @Valid @RequestBody FlowRuntimeModels.ManualNodeRequest body) {
        return ApiResult.ok(service.previewManualNode(AuthenticationContextHolder.require(), instanceId, body));
    }

    @PostMapping("/instances/{instanceId}/manual-nodes")
    public ApiResult<FlowRuntimeModels.ManualNodeResult> addManualNode(
            @PathVariable Long instanceId, @Valid @RequestBody FlowRuntimeModels.ManualNodeRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(service.addManualNode(AuthenticationContextHolder.require(), instanceId, body,
                TraceIdFilter.current(request)));
    }

    /** 结构化配置本实例的后续节点，不修改发布模板。 */
    @PostMapping("/instances/{instanceId}/runtime-node-preview")
    public ApiResult<FlowRuntimeModels.RuntimeNodePreview> previewRuntimeNode(
            @PathVariable Long instanceId, @Valid @RequestBody FlowRuntimeModels.RuntimeNodeRequest body) {
        return ApiResult.ok(service.previewRuntimeNode(AuthenticationContextHolder.require(), instanceId, body));
    }

    @PostMapping("/instances/{instanceId}/runtime-nodes")
    public ApiResult<FlowRuntimeModels.ActionResult> addRuntimeNode(
            @PathVariable Long instanceId, @Valid @RequestBody FlowRuntimeModels.RuntimeNodeRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(service.addRuntimeNode(AuthenticationContextHolder.require(), instanceId, body,
                TraceIdFilter.current(request)));
    }

    @PostMapping("/incidents/{incidentId}/actions")
    public ApiResult<FlowRuntimeModels.ActionResult> handleIncident(
            @PathVariable Long incidentId, @Valid @RequestBody FlowRuntimeModels.IncidentActionRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(service.handleIncident(AuthenticationContextHolder.require(), incidentId, body,
                TraceIdFilter.current(request)));
    }
}
