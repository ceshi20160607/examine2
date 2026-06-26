package com.unique.examine.aiwork.manage.agent;

import com.unique.examine.aiwork.manage.agent.AgentModels.AgentAuditLogQuery;
import com.unique.examine.aiwork.manage.agent.AgentModels.AgentAuditLogVO;
import com.unique.examine.aiwork.manage.agent.AgentModels.AgentMessageRequest;
import com.unique.examine.aiwork.manage.agent.AgentModels.AgentMessageResultVO;
import com.unique.examine.aiwork.manage.agent.AgentModels.AgentPolicyPublishCheckVO;
import com.unique.examine.aiwork.manage.agent.AgentModels.AgentPolicySaveRequest;
import com.unique.examine.aiwork.manage.agent.AgentModels.AgentPolicyVO;
import com.unique.examine.aiwork.manage.agent.AgentModels.AgentSessionCreateRequest;
import com.unique.examine.aiwork.manage.agent.AgentModels.AgentSessionVO;
import com.unique.examine.aiwork.manage.agent.AgentModels.ConfirmationActionRequest;
import com.unique.examine.aiwork.manage.agent.AgentModels.ModelAuthorizationSaveRequest;
import com.unique.examine.aiwork.manage.agent.AgentModels.ModelAuthorizationVO;
import com.unique.examine.aiwork.manage.agent.AgentModels.PlatformAgentConfirmRequest;
import com.unique.examine.aiwork.manage.agent.AgentModels.PlatformAgentConfirmResultVO;
import com.unique.examine.aiwork.manage.agent.AgentModels.SystemWriteConfirmationRequest;
import com.unique.examine.aiwork.manage.agent.AgentModels.SystemWriteConfirmationVO;
import com.unique.examine.aiwork.manage.agent.AgentModels.WorkDraftConfirmRequest;
import com.unique.examine.aiwork.manage.agent.AgentModels.WorkDraftConfirmResultVO;
import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.PageRequest;
import com.unique.examine.core.api.PageResult;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI Agent API 控制器。
 */
@RestController
public class AgentController {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    /**
     * 查询平台模型授权。
     *
     * @param pageNo 页码
     * @param pageSize 每页数量
     * @return 模型授权分页
     */
    @GetMapping("/api/v1/platform/agent/model-authorizations")
    public ApiResponse<PageResult<ModelAuthorizationVO>> modelAuthorizations(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(agentService.modelAuthorizations(pageRequest(pageNo, pageSize, null)));
    }

    /**
     * 创建平台模型授权。
     *
     * @param idempotencyKey 幂等键
     * @param request 保存请求
     * @return 模型授权
     */
    @PostMapping("/api/v1/platform/agent/model-authorizations")
    public ApiResponse<ModelAuthorizationVO> createModelAuthorization(
            @RequestHeader(name = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @RequestBody(required = false) ModelAuthorizationSaveRequest request) {
        return ApiResponse.success(agentService.saveModelAuthorization(null, request, idempotencyKey));
    }

    /**
     * 更新平台模型授权。
     *
     * @param authorizationId 授权 ID
     * @param idempotencyKey 幂等键
     * @param request 保存请求
     * @return 模型授权
     */
    @PatchMapping("/api/v1/platform/agent/model-authorizations/{authorizationId}")
    public ApiResponse<ModelAuthorizationVO> updateModelAuthorization(
            @PathVariable String authorizationId,
            @RequestHeader(name = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @RequestBody(required = false) ModelAuthorizationSaveRequest request) {
        return ApiResponse.success(agentService.saveModelAuthorization(authorizationId, request, idempotencyKey));
    }

    /**
     * 创建平台 Agent 会话。
     *
     * @param request 创建请求
     * @return 会话
     */
    @PostMapping("/api/v1/platform/agent/sessions")
    public ApiResponse<AgentSessionVO> createPlatformSession(
            @RequestBody(required = false) AgentSessionCreateRequest request) {
        return ApiResponse.success(agentService.createPlatformSession(request));
    }

    /**
     * 发送平台 Agent 消息。
     *
     * @param sessionId 会话 ID
     * @param request 消息请求
     * @return 消息结果
     */
    @PostMapping("/api/v1/platform/agent/sessions/{sessionId}/messages")
    public ApiResponse<AgentMessageResultVO> sendPlatformMessage(
            @PathVariable String sessionId,
            @RequestBody(required = false) AgentMessageRequest request) {
        return ApiResponse.success(agentService.sendPlatformMessage(sessionId, request));
    }

    /**
     * 平台 Agent 确认。
     *
     * @param idempotencyKey 幂等键
     * @param request 确认请求
     * @return 平台确认结果
     */
    @PostMapping("/api/v1/platform/agent/platform-agent-confirm")
    public ApiResponse<PlatformAgentConfirmResultVO> platformConfirm(
            @RequestHeader(name = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @RequestBody(required = false) PlatformAgentConfirmRequest request) {
        return ApiResponse.success(agentService.platformConfirm(request, idempotencyKey));
    }

    /**
     * 查询系统 Agent 策略。
     *
     * @param systemId 系统 ID
     * @param pageNo 页码
     * @param pageSize 每页数量
     * @return 策略分页
     */
    @GetMapping("/api/v1/systems/{systemId}/agent/policies")
    public ApiResponse<PageResult<AgentPolicyVO>> policies(@PathVariable String systemId,
                                                           @RequestParam(defaultValue = "1") int pageNo,
                                                           @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(agentService.policies(systemId, pageRequest(pageNo, pageSize, null)));
    }

    /**
     * 创建系统 Agent 策略。
     *
     * @param systemId 系统 ID
     * @param idempotencyKey 幂等键
     * @param request 保存请求
     * @return 策略
     */
    @PostMapping("/api/v1/systems/{systemId}/agent/policies")
    public ApiResponse<AgentPolicyVO> createPolicy(
            @PathVariable String systemId,
            @RequestHeader(name = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @RequestBody(required = false) AgentPolicySaveRequest request) {
        return ApiResponse.success(agentService.savePolicy(systemId, null, request, idempotencyKey));
    }

    /**
     * 更新系统 Agent 策略。
     *
     * @param systemId 系统 ID
     * @param policyId 策略 ID
     * @param idempotencyKey 幂等键
     * @param request 保存请求
     * @return 策略
     */
    @PatchMapping("/api/v1/systems/{systemId}/agent/policies/{policyId}")
    public ApiResponse<AgentPolicyVO> updatePolicy(
            @PathVariable String systemId,
            @PathVariable String policyId,
            @RequestHeader(name = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @RequestBody(required = false) AgentPolicySaveRequest request) {
        return ApiResponse.success(agentService.savePolicy(systemId, policyId, request, idempotencyKey));
    }

    /**
     * 系统 Agent 策略发布检查。
     *
     * @param systemId 系统 ID
     * @param policyId 策略 ID
     * @return 发布检查
     */
    @PostMapping("/api/v1/systems/{systemId}/agent/policies/{policyId}/publish-check")
    public ApiResponse<AgentPolicyPublishCheckVO> publishCheck(@PathVariable String systemId,
                                                               @PathVariable String policyId) {
        return ApiResponse.success(agentService.publishCheck(systemId, policyId));
    }

    /**
     * 创建系统 Agent 会话。
     *
     * @param systemId 系统 ID
     * @param request 创建请求
     * @return 会话
     */
    @PostMapping("/api/v1/systems/{systemId}/agent/sessions")
    public ApiResponse<AgentSessionVO> createSystemSession(
            @PathVariable String systemId,
            @RequestBody(required = false) AgentSessionCreateRequest request) {
        return ApiResponse.success(agentService.createSystemSession(systemId, request));
    }

    /**
     * 发送系统 Agent 消息。
     *
     * @param systemId 系统 ID
     * @param sessionId 会话 ID
     * @param request 消息请求
     * @return 消息结果
     */
    @PostMapping("/api/v1/systems/{systemId}/agent/sessions/{sessionId}/messages")
    public ApiResponse<AgentMessageResultVO> sendSystemMessage(
            @PathVariable String systemId,
            @PathVariable String sessionId,
            @RequestBody(required = false) AgentMessageRequest request) {
        return ApiResponse.success(agentService.sendSystemMessage(systemId, sessionId, request));
    }

    /**
     * 创建系统 Agent 写入确认。
     *
     * @param systemId 系统 ID
     * @param idempotencyKey 幂等键
     * @param request 确认请求
     * @return 写入确认
     */
    @PostMapping("/api/v1/systems/{systemId}/agent/system-agent-write-confirmations")
    public ApiResponse<SystemWriteConfirmationVO> createSystemWriteConfirmation(
            @PathVariable String systemId,
            @RequestHeader(name = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @RequestBody(required = false) SystemWriteConfirmationRequest request) {
        return ApiResponse.success(agentService.createSystemWriteConfirmation(systemId, request, idempotencyKey));
    }

    /**
     * 确认系统 Agent 写入。
     *
     * @param systemId 系统 ID
     * @param confirmationId 确认 ID
     * @param idempotencyKey 幂等键
     * @param request 处理请求
     * @return 写入确认结果
     */
    @PostMapping("/api/v1/systems/{systemId}/agent/system-agent-write-confirmations/{confirmationId}/confirm")
    public ApiResponse<SystemWriteConfirmationVO> confirmSystemWrite(
            @PathVariable String systemId,
            @PathVariable String confirmationId,
            @RequestHeader(name = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @RequestBody(required = false) ConfirmationActionRequest request) {
        return ApiResponse.success(agentService.confirmSystemWrite(systemId, confirmationId, request, idempotencyKey));
    }

    /**
     * 拒绝系统 Agent 写入。
     *
     * @param systemId 系统 ID
     * @param confirmationId 确认 ID
     * @param idempotencyKey 幂等键
     * @param request 处理请求
     * @return 写入拒绝结果
     */
    @PostMapping("/api/v1/systems/{systemId}/agent/system-agent-write-confirmations/{confirmationId}/reject")
    public ApiResponse<SystemWriteConfirmationVO> rejectSystemWrite(
            @PathVariable String systemId,
            @PathVariable String confirmationId,
            @RequestHeader(name = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @RequestBody(required = false) ConfirmationActionRequest request) {
        return ApiResponse.success(agentService.rejectSystemWrite(systemId, confirmationId, request, idempotencyKey));
    }

    /**
     * 工作 Agent 草稿确认。
     *
     * @param systemId 系统 ID
     * @param idempotencyKey 幂等键
     * @param request 确认请求
     * @return 工作草稿确认
     */
    @PostMapping("/api/v1/systems/{systemId}/work/agent/work-agent-draft-confirm")
    public ApiResponse<WorkDraftConfirmResultVO> workDraftConfirm(
            @PathVariable String systemId,
            @RequestHeader(name = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @RequestBody(required = false) WorkDraftConfirmRequest request) {
        return ApiResponse.success(agentService.workDraftConfirm(systemId, request, idempotencyKey));
    }

    /**
     * 查询系统 Agent 审计日志。
     *
     * @param systemId 系统 ID
     * @param pageNo 页码
     * @param pageSize 每页数量
     * @param query 查询条件
     * @return Agent 审计分页
     */
    @GetMapping("/api/v1/systems/{systemId}/agent/audit-logs")
    public ApiResponse<PageResult<AgentAuditLogVO>> auditLogs(
            @PathVariable String systemId,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            AgentAuditLogQuery query) {
        return ApiResponse.success(agentService.auditLogs(systemId, pageRequest(pageNo, pageSize,
                query == null ? null : query.keyword()), query));
    }

    private PageRequest pageRequest(int pageNo, int pageSize, String keyword) {
        return new PageRequest(pageNo, pageSize, keyword, List.of(), List.of());
    }
}
