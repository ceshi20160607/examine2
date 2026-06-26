package com.unique.examine.module.manage.sequence;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.module.manage.sequence.SequenceModels.SequenceAllocateRequest;
import com.unique.examine.module.manage.sequence.SequenceModels.SequenceAllocationResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运行态自动编号接口。
 */
@RestController
public class SequenceController {

    private final SequenceService sequenceService;

    public SequenceController(SequenceService sequenceService) {
        this.sequenceService = sequenceService;
    }

    /**
     * 原子分配自动编号。
     *
     * @param systemId 系统编号
     * @param moduleId 模块编号
     * @param request 分配请求
     * @return 分配结果
     */
    @PostMapping("/api/v1/systems/{systemId}/runtime/modules/{moduleId}/sequences/allocate")
    public ApiResponse<SequenceAllocationResult> allocate(@PathVariable String systemId,
                                                          @PathVariable String moduleId,
                                                          @RequestBody(required = false)
                                                          SequenceAllocateRequest request) {
        return ApiResponse.success(sequenceService.allocate(systemId, moduleId, request));
    }
}
