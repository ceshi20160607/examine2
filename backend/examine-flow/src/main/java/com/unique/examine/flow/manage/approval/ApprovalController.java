package com.unique.examine.flow.manage.approval;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.flow.manage.approval.ApprovalModels.ApprovalActionRequest;
import com.unique.examine.flow.manage.approval.ApprovalModels.ApprovalActionResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审批任务动作 API 控制器。
 */
@RestController
public class ApprovalController {

    private final ApprovalService approvalService;

    public ApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    /**
     * 审批通过当前任务。
     *
     * @param systemId 系统编号
     * @param taskId 审批任务编号
     * @param request 审批动作请求
     * @return 审批动作结果
     */
    @PostMapping("/api/v1/systems/{systemId}/approval-tasks/{taskId}/approve")
    public ApiResponse<ApprovalActionResult> approve(@PathVariable String systemId, @PathVariable String taskId,
                                                     @RequestBody(required = false)
                                                     ApprovalActionRequest request) {
        return ApiResponse.success(approvalService.approve(systemId, taskId, request));
    }

    /**
     * 拒绝当前审批任务。
     *
     * @param systemId 系统编号
     * @param taskId 审批任务编号
     * @param request 拒绝动作请求
     * @return 审批动作结果
     */
    @PostMapping("/api/v1/systems/{systemId}/approval-tasks/{taskId}/reject")
    public ApiResponse<ApprovalActionResult> reject(@PathVariable String systemId, @PathVariable String taskId,
                                                    @RequestBody(required = false)
                                                    ApprovalActionRequest request) {
        return ApiResponse.success(approvalService.reject(systemId, taskId, request));
    }

    /**
     * 转交当前审批任务。
     *
     * @param systemId 系统编号
     * @param taskId 审批任务编号
     * @param request 转交动作请求
     * @return 审批动作结果
     */
    @PostMapping("/api/v1/systems/{systemId}/approval-tasks/{taskId}/transfer")
    public ApiResponse<ApprovalActionResult> transfer(@PathVariable String systemId, @PathVariable String taskId,
                                                      @RequestBody(required = false)
                                                      ApprovalActionRequest request) {
        return ApiResponse.success(approvalService.transfer(systemId, taskId, request));
    }
}
