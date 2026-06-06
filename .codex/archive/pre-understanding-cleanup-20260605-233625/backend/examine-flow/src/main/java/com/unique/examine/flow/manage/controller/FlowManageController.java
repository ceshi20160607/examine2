package com.unique.examine.flow.manage.controller;

import com.unique.examine.core.common.ApiResult;
import com.unique.examine.flow.manage.bo.FlowStartBO;
import com.unique.examine.flow.manage.bo.FlowTaskHandleBO;
import com.unique.examine.flow.manage.bo.FlowTemplatePublishBO;
import com.unique.examine.flow.manage.bo.FlowTemplateSaveBO;
import com.unique.examine.flow.manage.dto.FlowTaskQueryDTO;
import com.unique.examine.flow.manage.service.FlowManageService;
import com.unique.examine.flow.manage.vo.FlowManageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 流程工作台接口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/flows")
@Tag(name = "流程工作台")
public class FlowManageController {

    private final FlowManageService flowManageService;

    /**
     * 查询流程模板。
     *
     * @param moduleId 模块 ID
     * @return 模板列表
     */
    @GetMapping("/templates")
    @Operation(summary = "查询流程模板")
    public ApiResult<List<FlowManageVO>> listTemplates(@RequestParam(required = false) Long moduleId) {
        return ApiResult.success(flowManageService.listTemplates(moduleId));
    }

    /**
     * 创建流程模板。
     *
     * @param bo 模板入参
     * @return 模板信息
     */
    @PostMapping("/templates")
    @Operation(summary = "创建流程模板")
    public ApiResult<FlowManageVO> createTemplate(@RequestBody FlowTemplateSaveBO bo) {
        return ApiResult.success(flowManageService.createTemplate(bo));
    }

    /**
     * 发布流程模板。
     *
     * @param bo 发布入参
     * @return 版本信息
     */
    @PostMapping("/templates/publish")
    @Operation(summary = "发布流程模板")
    public ApiResult<FlowManageVO> publishTemplate(@RequestBody FlowTemplatePublishBO bo) {
        return ApiResult.success(flowManageService.publishTemplate(bo));
    }

    /**
     * 发起流程。
     *
     * @param bo 发起入参
     * @return 流程实例
     */
    @PostMapping("/instances")
    @Operation(summary = "发起流程")
    public ApiResult<FlowManageVO> start(@RequestBody FlowStartBO bo) {
        return ApiResult.success(flowManageService.start(bo));
    }

    /**
     * 查询待办任务。
     *
     * @param assigneeId 处理人账号 ID
     * @param status 任务状态
     * @return 任务列表
     */
    @GetMapping("/tasks")
    @Operation(summary = "查询流程任务")
    public ApiResult<List<FlowManageVO>> listTasks(@RequestParam(required = false) Long assigneeId,
                                                   @RequestParam(required = false) String status) {
        FlowTaskQueryDTO dto = new FlowTaskQueryDTO();
        dto.setAssigneeId(assigneeId);
        dto.setStatus(status);
        return ApiResult.success(flowManageService.listTasks(dto));
    }

    /**
     * 处理任务。
     *
     * @param taskId 任务 ID
     * @param bo 处理入参
     * @return 任务信息
     */
    @PatchMapping("/tasks/handle")
    @Operation(summary = "处理流程任务")
    public ApiResult<FlowManageVO> handleTask(@RequestParam Long taskId, @RequestBody FlowTaskHandleBO bo) {
        return ApiResult.success(flowManageService.handleTask(taskId, bo));
    }
}
