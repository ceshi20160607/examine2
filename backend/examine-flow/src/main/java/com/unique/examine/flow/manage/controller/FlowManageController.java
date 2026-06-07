package com.unique.examine.flow.manage.controller;

import java.util.List;

import com.unique.examine.core.common.response.PageResult;
import com.unique.examine.core.error.CommonErrorCode;
import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.flow.manage.bo.FlowActionBO;
import com.unique.examine.flow.manage.bo.FlowBindingSaveBO;
import com.unique.examine.flow.manage.bo.FlowClaimBO;
import com.unique.examine.flow.manage.bo.FlowPublishBO;
import com.unique.examine.flow.manage.bo.FlowTaskQueryBO;
import com.unique.examine.flow.manage.bo.FlowTemplateGraphBO;
import com.unique.examine.flow.manage.bo.FlowTemplateSaveBO;
import com.unique.examine.flow.manage.bo.FlowTemplateStatusBO;
import com.unique.examine.flow.manage.bo.FlowWithdrawBO;
import com.unique.examine.flow.manage.service.FlowManageService;
import com.unique.examine.flow.manage.vo.FlowActionResultVO;
import com.unique.examine.flow.manage.vo.FlowBindingVO;
import com.unique.examine.flow.manage.vo.FlowDiagramVO;
import com.unique.examine.flow.manage.vo.FlowHistoryItemVO;
import com.unique.examine.flow.manage.vo.FlowInstanceVO;
import com.unique.examine.flow.manage.vo.FlowPublishCheckResultVO;
import com.unique.examine.flow.manage.vo.FlowTaskDetailVO;
import com.unique.examine.flow.manage.vo.FlowTaskListItemVO;
import com.unique.examine.flow.manage.vo.FlowTemplateGraphVO;
import com.unique.examine.flow.manage.vo.FlowTemplateVO;
import com.unique.examine.plat.manage.service.AuthSessionService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 流程模板、实例和任务接口。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/systems/{systemId}/flow")
public class FlowManageController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final FlowManageService flowManageService;

    private final AuthSessionService authSessionService;

    @Operation(summary = "查询流程模板")
    @GetMapping("/templates")
    public List<FlowTemplateVO> listTemplates(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        validateLogin(authorization);
        return flowManageService.listTemplates(systemId, keyword, status);
    }

    @Operation(summary = "创建流程模板")
    @PostMapping("/templates")
    public FlowTemplateVO createTemplate(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @Valid @RequestBody FlowTemplateSaveBO saveBO) {
        validateLogin(authorization);
        return flowManageService.createTemplate(systemId, saveBO);
    }

    @Operation(summary = "查询流程模板详情")
    @GetMapping("/templates/{templateId}")
    public FlowTemplateVO templateDetail(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long templateId) {
        validateLogin(authorization);
        return flowManageService.templateDetail(systemId, templateId);
    }

    @Operation(summary = "保存流程图")
    @PutMapping("/templates/{templateId}/graph")
    public FlowTemplateGraphVO saveGraph(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long templateId, @Valid @RequestBody FlowTemplateGraphBO graphBO) {
        validateLogin(authorization);
        return flowManageService.saveGraph(systemId, templateId, graphBO);
    }

    @Operation(summary = "查询流程图")
    @GetMapping("/templates/{templateId}/graph")
    public FlowTemplateGraphVO templateGraph(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long templateId) {
        validateLogin(authorization);
        return flowManageService.templateGraph(systemId, templateId);
    }

    @Operation(summary = "流程发布检查")
    @PostMapping("/templates/{templateId}/publish-check")
    public FlowPublishCheckResultVO publishCheck(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long templateId) {
        validateLogin(authorization);
        return flowManageService.publishCheck(systemId, templateId);
    }

    @Operation(summary = "发布流程模板")
    @PostMapping("/templates/{templateId}/publish")
    public FlowTemplateVO publish(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long templateId, @RequestBody FlowPublishBO publishBO) {
        validateLogin(authorization);
        return flowManageService.publish(systemId, templateId, publishBO);
    }

    @Operation(summary = "绑定模块流程")
    @PutMapping("/bindings/modules/{moduleId}")
    public FlowBindingVO bindModule(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long moduleId, @Valid @RequestBody FlowBindingSaveBO saveBO) {
        validateLogin(authorization);
        return flowManageService.bindModule(systemId, moduleId, saveBO);
    }

    @Operation(summary = "查询待办任务")
    @GetMapping("/tasks/todo")
    public PageResult<FlowTaskListItemVO> todoTasks(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, FlowTaskQueryBO queryBO) {
        validateLogin(authorization);
        return flowManageService.todoTasks(systemId, queryBO);
    }

    @Operation(summary = "查询任务详情")
    @GetMapping("/tasks/{taskId}")
    public FlowTaskDetailVO taskDetail(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long taskId) {
        validateLogin(authorization);
        return flowManageService.taskDetail(systemId, taskId);
    }

    @Operation(summary = "处理任务")
    @PostMapping("/tasks/{taskId}/actions")
    public FlowActionResultVO handleTask(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long taskId, @Valid @RequestBody FlowActionBO actionBO) {
        validateLogin(authorization);
        return flowManageService.handleTask(systemId, taskId, actionBO);
    }

    @Operation(summary = "撤回实例")
    @PostMapping("/instances/{instanceId}/withdraw")
    public FlowActionResultVO withdraw(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long instanceId, @Valid @RequestBody FlowWithdrawBO withdrawBO) {
        validateLogin(authorization);
        return flowManageService.withdraw(systemId, instanceId, withdrawBO);
    }

    @Operation(summary = "查询实例详情")
    @GetMapping("/instances/{instanceId}")
    public FlowInstanceVO instanceDetail(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long instanceId) {
        validateLogin(authorization);
        return flowManageService.instanceDetail(systemId, instanceId);
    }

    @Operation(summary = "查询实例图")
    @GetMapping("/instances/{instanceId}/diagram")
    public FlowDiagramVO instanceDiagram(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long instanceId) {
        validateLogin(authorization);
        return flowManageService.instanceDiagram(systemId, instanceId);
    }

    @Operation(summary = "领取任务")
    @PostMapping("/tasks/{taskId}/claim")
    public FlowActionResultVO claim(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long taskId, @Valid @RequestBody FlowClaimBO claimBO) {
        validateLogin(authorization);
        return flowManageService.claim(systemId, taskId, claimBO);
    }

    @Operation(summary = "取消领取任务")
    @PostMapping("/tasks/{taskId}/unclaim")
    public FlowActionResultVO unclaim(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long taskId, @Valid @RequestBody FlowClaimBO claimBO) {
        validateLogin(authorization);
        return flowManageService.unclaim(systemId, taskId, claimBO);
    }

    @Operation(summary = "查询实例列表")
    @GetMapping("/instances")
    public PageResult<FlowInstanceVO> listInstances(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, FlowTaskQueryBO queryBO) {
        validateLogin(authorization);
        return flowManageService.listInstances(systemId, queryBO);
    }

    @Operation(summary = "查询实例历史")
    @GetMapping("/instances/{instanceId}/history")
    public List<FlowHistoryItemVO> instanceHistory(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long instanceId) {
        validateLogin(authorization);
        return flowManageService.instanceHistory(systemId, instanceId);
    }

    @Operation(summary = "变更模板状态")
    @PatchMapping("/templates/{templateId}/status")
    public FlowTemplateVO changeTemplateStatus(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long templateId,
            @Valid @RequestBody FlowTemplateStatusBO statusBO) {
        validateLogin(authorization);
        return flowManageService.changeTemplateStatus(systemId, templateId, statusBO);
    }

    private void validateLogin(String authorization) {
        authSessionService.me(resolveBearer(authorization));
    }

    private static String resolveBearer(String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        String token = authorization.substring(BEARER_PREFIX.length()).strip();
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        return token;
    }
}
