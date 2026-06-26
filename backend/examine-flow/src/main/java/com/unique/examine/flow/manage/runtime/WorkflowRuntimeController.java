package com.unique.examine.flow.manage.runtime;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.flow.manage.runtime.RuntimeModels.WorkflowInstanceSnapshot;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 流程实例运行态 API 控制器。
 */
@RestController
public class WorkflowRuntimeController {

    private final WorkflowRuntimeService runtimeService;

    public WorkflowRuntimeController(WorkflowRuntimeService runtimeService) {
        this.runtimeService = runtimeService;
    }

    /**
     * 查询流程实例运行态快照。
     *
     * @param systemId 系统编号
     * @param instanceId 流程实例编号
     * @return 流程实例快照
     */
    @GetMapping("/api/v1/systems/{systemId}/flow-instances/{instanceId}")
    public ApiResponse<WorkflowInstanceSnapshot> snapshot(@PathVariable String systemId,
                                                          @PathVariable String instanceId) {
        return ApiResponse.success(runtimeService.snapshot(systemId, instanceId));
    }
}
