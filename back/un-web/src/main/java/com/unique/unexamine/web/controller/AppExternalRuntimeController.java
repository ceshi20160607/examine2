package com.unique.unexamine.web.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.unexamine.app.manage.dto.AppAccessDto.AccessRequest;
import com.unique.unexamine.app.manage.dto.AppAccessDto.Decision;
import com.unique.unexamine.app.manage.dto.AppExternalRuntimeDto.ExamineActionRequest;
import com.unique.unexamine.app.manage.dto.AppExternalRuntimeDto.ExamineQueryRequest;
import com.unique.unexamine.app.manage.dto.AppExternalRuntimeDto.ExamineTodoRequest;
import com.unique.unexamine.app.manage.dto.AppExternalRuntimeDto.FlowActionRequest;
import com.unique.unexamine.app.manage.dto.AppExternalRuntimeDto.FlowQueryRequest;
import com.unique.unexamine.app.manage.dto.AppExternalRuntimeDto.FlowStartRequest;
import com.unique.unexamine.app.manage.dto.AppExternalRuntimeDto.FlowTodoRequest;
import com.unique.unexamine.app.manage.dto.AppExternalRuntimeDto.PlatformExamineActionRequest;
import com.unique.unexamine.app.manage.dto.AppExternalRuntimeDto.PlatformExamineQueryRequest;
import com.unique.unexamine.app.manage.dto.AppExternalRuntimeDto.PlatformExamineTodoRequest;
import com.unique.unexamine.app.manage.dto.AppExternalRuntimeDto.PlatformExamineStartRequest;
import com.unique.unexamine.app.manage.dto.AppExternalRuntimeDto.PlatformFlowActionRequest;
import com.unique.unexamine.app.manage.dto.AppExternalRuntimeDto.PlatformFlowQueryRequest;
import com.unique.unexamine.app.manage.dto.AppExternalRuntimeDto.PlatformFlowTodoRequest;
import com.unique.unexamine.app.manage.dto.AppExternalRuntimeDto.PlatformFlowStartRequest;
import com.unique.unexamine.app.manage.service.AppAccessService;
import com.unique.unexamine.core.common.Result;
import com.unique.unexamine.examine.manage.dto.ExamineRuntimeDto.ActionRequest;
import com.unique.unexamine.examine.manage.dto.ExamineRuntimeDto.ActionView;
import com.unique.unexamine.examine.manage.service.ExamineRuntimeService;
import com.unique.unexamine.sys.flow.manage.service.FlowRuntimeService;
import com.unique.unexamine.sys.flow.manage.dto.FlowRuntimeDto.ExternalTodoView;
import com.unique.unexamine.sys.flow.manage.dto.FlowRuntimeDto.SystemExternalTodoView;
import com.unique.unexamine.sys.module.manage.service.ModuleRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 受控应用的外部 Flow 与审批入口；应用授权和业务记录边界在同一次调用中核对。 */
@RestController
@RequestMapping("/api/app-access/external")
@Tag(name = "应用外部运行")
public class AppExternalRuntimeController {
    private final AppAccessService access;
    private final ExamineRuntimeService examines;
    private final FlowRuntimeService flows;
    private final ModuleRecordService records;

    /** 注入应用授权、审批运行和业务记录基础查询。 */
    public AppExternalRuntimeController(AppAccessService access, ExamineRuntimeService examines,
                                        FlowRuntimeService flows, ModuleRecordService records) {
        this.access = access;
        this.examines = examines;
        this.flows = flows;
        this.records = records;
    }

    /**
     * 外部审批人办理系统内审批节点。负责人和部门范围从真实业务记录读取，
     * 不接受调用方在 payload 中伪造；审批人再按实例快照中的应用外部身份匹配。
     */
    @PostMapping("/examines/actions")
    @Operation(summary = "外部应用办理审批节点")
    public Result<ActionView> examine(@Valid @RequestBody ExamineActionRequest request, HttpServletRequest http) {
        Map<String, Object> clientPayload = access.externalPayload(request.payload(), request.externalId(), request);
        access.verifyExternalRequest(new AccessRequest(
                request.appId(), request.secret(), "EXAMINE", request.examineId(), request.actionCode(),
                request.requestId(), request.systemId(), request.tenantId(), request.timestamp(), request.signature(),
                clientPayload, http.getRemoteAddr()));
        ModuleRecordService.ExternalRecordBoundary record = records.requireExternalBoundary(
                request.systemId(), request.tenantId(), request.moduleId(), request.recordId());

        Map<String, Object> payload = new LinkedHashMap<>(clientPayload);
        payload.put("ownerAccountId", record.ownerAccountId());
        payload.put("ownerDeptId", record.ownerDeptId());
        Decision decision = access.authorizeExternalAfterPreflight(new AccessRequest(
                request.appId(), request.secret(), "EXAMINE", request.examineId(), request.actionCode(),
                request.requestId(), request.systemId(), request.tenantId(), request.timestamp(), request.signature(),
                payload, http.getRemoteAddr()), clientPayload);
        return Result.ok(access.executeExternal(decision, request.requestId(), () -> examines.actionExternal(decision.applicationId(), request.externalId(),
                request.systemId(), request.tenantId(), request.moduleId(), request.recordId(),
                request.examineId(), request.exampleId(), request.nodeId(),
                new ActionRequest(request.requestId(), request.actionCode(), request.opinion(), request.payload()))));
    }

    /** 外部应用查询系统内审批实例；负责人和部门范围从真实业务记录读取。 */
    @PostMapping("/examines/query")
    @Operation(summary = "外部应用查询审批实例")
    public Result<com.unique.unexamine.examine.manage.dto.ExamineRuntimeDto.ExampleView> examineQuery(
            @Valid @RequestBody ExamineQueryRequest request, HttpServletRequest http) {
        Map<String, Object> clientPayload = access.externalPayload(Map.of(), request.externalId(), request);
        access.verifyExternalRequest(new AccessRequest(
                request.appId(), request.secret(), "EXAMINE", request.examineId(), "QUERY",
                request.requestId(), request.systemId(), request.tenantId(), request.timestamp(), request.signature(),
                clientPayload, http.getRemoteAddr()));
        ModuleRecordService.ExternalRecordBoundary record = record(request.systemId(), request.tenantId(),
                request.moduleId(), request.recordId());
        Map<String, Object> payload = ownerPayload(clientPayload, record, request.externalId());
        Decision decision = access.authorizeExternalAfterPreflight(new AccessRequest(
                request.appId(), request.secret(), "EXAMINE", request.examineId(), "QUERY",
                request.requestId(), request.systemId(), request.tenantId(), request.timestamp(), request.signature(),
                payload, http.getRemoteAddr()), clientPayload);
        return Result.ok(access.executeExternal(decision, request.requestId(), () -> examines.queryExternal(decision.applicationId(), request.externalId(), request.systemId(),
                request.tenantId(), request.moduleId(), request.recordId(), request.examineId(), request.exampleId())));
    }

    /** 系统应用外部审批待办按实际记录负责人范围过滤后分页。 */
    @PostMapping("/examines/todo")
    @Operation(summary = "外部应用查询系统审批待办")
    public Result<Page<com.unique.unexamine.examine.manage.dto.ExamineRuntimeDto.SystemExternalTodoView>> examineTodo(
            @Valid @RequestBody ExamineTodoRequest request, HttpServletRequest http) {
        AccessRequest accessRequest = new AccessRequest(request.appId(), request.secret(), "EXAMINE",
                request.examineId(), "TODO", request.requestId(), request.systemId(), request.tenantId(),
                request.timestamp(), request.signature(),
                access.externalPayload(Map.of(), request.externalId(), request), http.getRemoteAddr());
        Decision decision = access.authorizeExternalTodo(accessRequest);
        return Result.ok(access.executeExternal(decision, request.requestId(), () ->
                examines.pendingSystemExternal(decision.applicationId(), request.externalId(),
                        request.systemId(), request.tenantId(), request.examineId(), request.search(),
                        access.externalTodoRecordScope(decision, accessRequest))));
    }

    /** 外部应用办理 Flow 节点；权限范围同样以真实业务记录负责人和部门为准。 */
    @PostMapping("/flows/actions")
    @Operation(summary = "外部应用办理 Flow 节点")
    public Result<com.unique.unexamine.sys.flow.manage.dto.FlowRuntimeDto.ActionView> flow(
            @Valid @RequestBody FlowActionRequest request, HttpServletRequest http) {
        Map<String, Object> clientPayload = access.externalPayload(request.payload(), request.externalId(), request);
        access.verifyExternalRequest(new AccessRequest(
                request.appId(), request.secret(), "FLOW", request.bindingId(), request.actionCode(),
                request.requestId(), request.systemId(), request.tenantId(), request.timestamp(), request.signature(),
                clientPayload, http.getRemoteAddr()));
        ModuleRecordService.ExternalRecordBoundary record = records.requireExternalBoundary(
                request.systemId(), request.tenantId(), request.moduleId(), request.recordId());
        Map<String, Object> payload = new LinkedHashMap<>(clientPayload);
        payload.put("ownerAccountId", record.ownerAccountId());
        payload.put("ownerDeptId", record.ownerDeptId());
        Decision decision = access.authorizeExternalAfterPreflight(new AccessRequest(
                request.appId(), request.secret(), "FLOW", request.bindingId(), request.actionCode(),
                request.requestId(), request.systemId(), request.tenantId(), request.timestamp(), request.signature(),
                payload, http.getRemoteAddr()), clientPayload);
        return Result.ok(access.executeExternal(decision, request.requestId(), () -> flows.actionExternal(decision.applicationId(), request.externalId(), request.systemId(),
                request.tenantId(), request.moduleId(), request.recordId(), request.bindingId(), request.exampleId(), request.taskId(),
                new com.unique.unexamine.sys.flow.manage.dto.FlowRuntimeDto.ActionRequest(
                        request.requestId(), request.actionCode(), request.payload()))));
    }

    /** 外部应用查询系统内 Flow 实例；已删除业务记录不能作为运行态查询对象。 */
    @PostMapping("/flows/query")
    @Operation(summary = "外部应用查询 Flow 实例")
    public Result<com.unique.unexamine.sys.flow.manage.dto.FlowRuntimeDto.ExampleView> flowQuery(
            @Valid @RequestBody FlowQueryRequest request, HttpServletRequest http) {
        Map<String, Object> clientPayload = access.externalPayload(Map.of(), request.externalId(), request);
        access.verifyExternalRequest(new AccessRequest(
                request.appId(), request.secret(), "FLOW", request.bindingId(), "QUERY",
                request.requestId(), request.systemId(), request.tenantId(), request.timestamp(), request.signature(),
                clientPayload, http.getRemoteAddr()));
        ModuleRecordService.ExternalRecordBoundary record = record(request.systemId(), request.tenantId(),
                request.moduleId(), request.recordId());
        Decision decision = access.authorizeExternalAfterPreflight(new AccessRequest(
                request.appId(), request.secret(), "FLOW", request.bindingId(), "QUERY",
                request.requestId(), request.systemId(), request.tenantId(), request.timestamp(), request.signature(),
                ownerPayload(clientPayload, record, request.externalId()), http.getRemoteAddr()), clientPayload);
        return Result.ok(access.executeExternal(decision, request.requestId(), () -> flows.queryExternal(decision.applicationId(), request.externalId(), request.systemId(),
                request.tenantId(), request.moduleId(), request.recordId(), request.bindingId(), request.exampleId())));
    }

    /** 系统应用外部身份的 Flow 待办按真实记录负责人范围过滤后分页。 */
    @PostMapping("/flows/todo")
    @Operation(summary = "外部应用查询系统 Flow 待办")
    public Result<Page<SystemExternalTodoView>> flowTodo(
            @Valid @RequestBody FlowTodoRequest request, HttpServletRequest http) {
        AccessRequest accessRequest = new AccessRequest(request.appId(), request.secret(), "FLOW",
                request.bindingId(), "TODO", request.requestId(), request.systemId(), request.tenantId(),
                request.timestamp(), request.signature(),
                access.externalPayload(Map.of(), request.externalId(), request), http.getRemoteAddr());
        Decision decision = access.authorizeExternalTodo(accessRequest);
        return Result.ok(access.executeExternal(decision, request.requestId(), () ->
                flows.pendingSystemExternal(decision.applicationId(), request.externalId(),
                        request.systemId(), request.tenantId(), request.bindingId(), request.search(),
                        access.externalTodoRecordScope(decision, accessRequest))));
    }

    /** 外部应用按绑定主键发起模块 Flow；绑定版本、模块和记录范围由后端重新核对。 */
    @PostMapping("/flows/start")
    @Operation(summary = "外部应用发起 Flow")
    public Result<com.unique.unexamine.sys.flow.manage.dto.FlowRuntimeDto.ExampleView> flowStart(
            @Valid @RequestBody FlowStartRequest request, HttpServletRequest http) {
        Map<String, Object> clientPayload = access.externalPayload(request.values(), request.externalId(), request);
        access.verifyExternalRequest(new AccessRequest(
                request.appId(), request.secret(), "FLOW", request.bindingId(), "START",
                request.requestId(), request.systemId(), request.tenantId(), request.timestamp(), request.signature(),
                clientPayload, http.getRemoteAddr()));
        ModuleRecordService.ExternalRecordBoundary record = record(request.systemId(), request.tenantId(),
                request.moduleId(), request.recordId());
        Decision decision = access.authorizeExternalAfterPreflight(new AccessRequest(
                request.appId(), request.secret(), "FLOW", request.bindingId(), "START",
                request.requestId(), request.systemId(), request.tenantId(), request.timestamp(), request.signature(),
                ownerPayload(clientPayload, record, request.externalId()), http.getRemoteAddr()), clientPayload);
        return Result.ok(access.executeExternal(decision, request.requestId(), () -> flows.startExternal(decision.applicationId(), request.externalId(), request.systemId(),
                request.tenantId(), request.moduleId(), request.recordId(), request.bindingId(), request.requestId(),
                request.values())));
    }

    /** 外部应用按平台 Flow 定义发起独立实例，不借用系统模块绑定或业务记录上下文。 */
    @PostMapping("/platform-flows/start")
    @Operation(summary = "外部应用发起平台 Flow")
    public Result<com.unique.unexamine.sys.flow.manage.dto.FlowRuntimeDto.ExampleView> platformFlowStart(
            @Valid @RequestBody PlatformFlowStartRequest request, HttpServletRequest http) {
        Decision decision = access.authorizeExternal(new AccessRequest(
                request.appId(), request.secret(), "PLATFORM_FLOW", request.flowId(), "START",
                request.requestId(), null, null, request.timestamp(), request.signature(),
                access.externalPayload(request.values(), request.externalId(), request), http.getRemoteAddr()));
        return Result.ok(access.executeExternal(decision, request.requestId(), () -> flows.startPlatformExternal(decision.applicationId(), request.externalId(),
                request.flowId(), request.requestId(), request.values())));
    }

    /** 查询平台 Flow 时还要由运行服务核对外部发起人或节点候选身份。 */
    @PostMapping("/platform-flows/query")
    @Operation(summary = "外部应用查询平台 Flow")
    public Result<com.unique.unexamine.sys.flow.manage.dto.FlowRuntimeDto.ExampleView> platformFlowQuery(
            @Valid @RequestBody PlatformFlowQueryRequest request, HttpServletRequest http) {
        Decision decision = access.authorizeExternal(new AccessRequest(
                request.appId(), request.secret(), "PLATFORM_FLOW", request.flowId(), "QUERY",
                request.requestId(), null, null, request.timestamp(), request.signature(),
                access.externalPayload(Map.of(), request.externalId(), request), http.getRemoteAddr()));
        return Result.ok(access.executeExternal(decision, request.requestId(), () -> flows.queryPlatformExternal(decision.applicationId(), request.externalId(),
                request.flowId(), request.exampleId())));
    }

    /** 外部应用按独立 TODO 授权查询当前身份的待办，不返回其他候选人。 */
    @PostMapping("/platform-flows/todo")
    @Operation(summary = "外部应用查询平台 Flow 待办")
    public Result<Page<ExternalTodoView>> platformFlowTodo(
            @Valid @RequestBody PlatformFlowTodoRequest request, HttpServletRequest http) {
        Decision decision = access.authorizeExternal(new AccessRequest(
                request.appId(), request.secret(), "PLATFORM_FLOW", request.flowId(), "TODO",
                request.requestId(), null, null, request.timestamp(), request.signature(),
                access.externalPayload(Map.of(), request.externalId(), request), http.getRemoteAddr()));
        return Result.ok(access.executeExternal(decision, request.requestId(), () ->
                flows.pendingPlatformExternal(decision.applicationId(), request.externalId(),
                        request.flowId(), request.search())));
    }

    /** 外部候选人按平台 Flow 实例的发布快照办理当前待办。 */
    @PostMapping("/platform-flows/actions")
    @Operation(summary = "外部应用办理平台 Flow")
    public Result<com.unique.unexamine.sys.flow.manage.dto.FlowRuntimeDto.ActionView> platformFlowAction(
            @Valid @RequestBody PlatformFlowActionRequest request, HttpServletRequest http) {
        Decision decision = access.authorizeExternal(new AccessRequest(
                request.appId(), request.secret(), "PLATFORM_FLOW", request.flowId(), request.actionCode(),
                request.requestId(), null, null, request.timestamp(), request.signature(),
                access.externalPayload(request.payload(), request.externalId(), request), http.getRemoteAddr()));
        return Result.ok(access.executeExternal(decision, request.requestId(), () -> flows.actionPlatformExternal(decision.applicationId(), request.externalId(),
                request.flowId(), request.exampleId(), request.taskId(), new com.unique.unexamine.sys.flow.manage.dto.FlowRuntimeDto.ActionRequest(
                        request.requestId(), request.actionCode(), request.payload()))));
    }

    /** 外部应用按已发布的平台审批定义发起独立实例。 */
    @PostMapping("/platform-examines/start")
    @Operation(summary = "外部应用发起平台审批")
    public Result<com.unique.unexamine.examine.manage.dto.ExamineRuntimeDto.ExampleView> platformExamineStart(
            @Valid @RequestBody PlatformExamineStartRequest request, HttpServletRequest http) {
        Decision decision = access.authorizeExternal(new AccessRequest(
                request.appId(), request.secret(), "PLATFORM_EXAMINE", request.examineId(), "START",
                request.requestId(), null, null, request.timestamp(), request.signature(),
                access.externalPayload(request.values(), request.externalId(), request), http.getRemoteAddr()));
        return Result.ok(access.executeExternal(decision, request.requestId(), () -> examines.startPlatformExternal(decision.applicationId(), request.externalId(),
                request.examineId(), request.requestId(), request.values())));
    }

    /** 外部应用办理不关联业务记录的平台审批节点。 */
    @PostMapping("/platform-examines/actions")
    @Operation(summary = "外部应用办理平台审批节点")
    public Result<ActionView> platformExamine(@Valid @RequestBody PlatformExamineActionRequest request,
                                               HttpServletRequest http) {
        Decision decision = access.authorizeExternal(new AccessRequest(
                request.appId(), request.secret(), "PLATFORM_EXAMINE", request.examineId(),
                request.actionCode(), request.requestId(), null, null, request.timestamp(), request.signature(),
                access.externalPayload(request.payload(), request.externalId(), request), http.getRemoteAddr()));
        return Result.ok(access.executeExternal(decision, request.requestId(), () -> examines.actionPlatformExternal(decision.applicationId(), request.externalId(),
                request.examineId(), request.exampleId(), request.nodeId(),
                new ActionRequest(request.requestId(), request.actionCode(), request.opinion(), request.payload()))));
    }

    /** 外部应用查询不关联业务记录的平台审批实例。 */
    @PostMapping("/platform-examines/query")
    @Operation(summary = "外部应用查询平台审批实例")
    public Result<com.unique.unexamine.examine.manage.dto.ExamineRuntimeDto.ExampleView> platformExamineQuery(
            @Valid @RequestBody PlatformExamineQueryRequest request, HttpServletRequest http) {
        Decision decision = access.authorizeExternal(new AccessRequest(
                request.appId(), request.secret(), "PLATFORM_EXAMINE", request.examineId(), "QUERY",
                request.requestId(), null, null, request.timestamp(), request.signature(),
                access.externalPayload(Map.of(), request.externalId(), request), http.getRemoteAddr()));
        return Result.ok(access.executeExternal(decision, request.requestId(), () -> examines.queryPlatformExternal(decision.applicationId(), request.externalId(),
                request.examineId(), request.exampleId())));
    }

    /** 外部审批人按独立 TODO 授权读取自己的平台审批待办。 */
    @PostMapping("/platform-examines/todo")
    @Operation(summary = "外部应用查询平台审批待办")
    public Result<Page<com.unique.unexamine.examine.manage.dto.ExamineRuntimeDto.ExternalTodoView>> platformExamineTodo(
            @Valid @RequestBody PlatformExamineTodoRequest request, HttpServletRequest http) {
        Decision decision = access.authorizeExternal(new AccessRequest(
                request.appId(), request.secret(), "PLATFORM_EXAMINE", request.examineId(), "TODO",
                request.requestId(), null, null, request.timestamp(), request.signature(),
                access.externalPayload(Map.of(), request.externalId(), request), http.getRemoteAddr()));
        return Result.ok(access.executeExternal(decision, request.requestId(), () ->
                examines.pendingPlatformExternal(decision.applicationId(), request.externalId(),
                        request.examineId(), request.search())));
    }

    /** 按真实记录读取负责人和部门，作为应用授权数据范围的唯一来源。 */
    private ModuleRecordService.ExternalRecordBoundary record(Long systemId, Long tenantId,
                                                              Long moduleId, Long recordId) {
        return records.requireExternalBoundary(systemId, tenantId, moduleId, recordId);
    }

    private Map<String, Object> ownerPayload(Map<String, Object> source,
                                             ModuleRecordService.ExternalRecordBoundary record,
                                             String externalId) {
        Map<String, Object> payload = new LinkedHashMap<>(source == null ? Map.of() : source);
        payload.put("ownerAccountId", record.ownerAccountId());
        payload.put("ownerDeptId", record.ownerDeptId());
        payload.put("__externalId", externalId);
        return payload;
    }

}
