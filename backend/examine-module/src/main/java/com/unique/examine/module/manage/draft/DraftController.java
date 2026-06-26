package com.unique.examine.module.manage.draft;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.module.manage.draft.DraftModels.DraftSaveRequest;
import com.unique.examine.module.manage.draft.DraftModels.DraftVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运行态表单草稿接口。
 */
@RestController
public class DraftController {

    private final DraftService draftService;

    public DraftController(DraftService draftService) {
        this.draftService = draftService;
    }

    /**
     * 保存草稿。
     *
     * @param systemId 系统编号
     * @param moduleId 模块编号
     * @param request 草稿保存请求
     * @param idempotencyKey 幂等键
     * @return 草稿详情
     */
    @PostMapping("/api/v1/systems/{systemId}/runtime/modules/{moduleId}/drafts")
    public ApiResponse<DraftVO> save(@PathVariable String systemId,
                                     @PathVariable String moduleId,
                                     @RequestBody(required = false) DraftSaveRequest request,
                                     @RequestHeader(value = "Idempotency-Key",
                                             required = false) String idempotencyKey) {
        return ApiResponse.success(draftService.save(systemId, moduleId, request, idempotencyKey));
    }

    /**
     * 获取草稿。
     *
     * @param systemId 系统编号
     * @param moduleId 模块编号
     * @param draftId 草稿编号
     * @return 草稿详情
     */
    @GetMapping("/api/v1/systems/{systemId}/runtime/modules/{moduleId}/drafts/{draftId}")
    public ApiResponse<DraftVO> get(@PathVariable String systemId,
                                    @PathVariable String moduleId,
                                    @PathVariable String draftId) {
        return ApiResponse.success(draftService.get(systemId, moduleId, draftId));
    }
}
