package com.unique.examine.module.manage.attachment;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.module.manage.attachment.AttachmentModels.AttachmentBindRequest;
import com.unique.examine.module.manage.attachment.AttachmentModels.AttachmentBindResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 业务记录附件接口。
 */
@RestController
public class AttachmentController {

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    /**
     * 绑定业务记录附件。
     *
     * @param systemId 系统编号
     * @param moduleId 模块编号
     * @param recordId 记录编号
     * @param request 附件绑定请求
     * @return 绑定结果
     */
    @PostMapping("/api/v1/systems/{systemId}/runtime/modules/{moduleId}/records/{recordId}/attachments")
    public ApiResponse<AttachmentBindResult> bind(@PathVariable String systemId,
                                                  @PathVariable String moduleId,
                                                  @PathVariable String recordId,
                                                  @RequestBody(required = false)
                                                  AttachmentBindRequest request) {
        return ApiResponse.success(attachmentService.bind(systemId, moduleId, recordId, request));
    }
}
