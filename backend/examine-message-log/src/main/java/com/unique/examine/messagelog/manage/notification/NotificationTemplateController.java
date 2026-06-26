package com.unique.examine.messagelog.manage.notification;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.PageRequest;
import com.unique.examine.core.api.PageResult;
import com.unique.examine.messagelog.manage.notification.NotificationModels.NotificationTemplateDeleteResult;
import com.unique.examine.messagelog.manage.notification.NotificationModels.NotificationTemplateQueryRequest;
import com.unique.examine.messagelog.manage.notification.NotificationModels.NotificationTemplateSaveRequest;
import com.unique.examine.messagelog.manage.notification.NotificationModels.NotificationTemplateVO;
import com.unique.examine.messagelog.manage.notification.NotificationModels.PublishCheckResultVO;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Notification template API controller.
 */
@RestController
public class NotificationTemplateController {

    private final NotificationTemplateService templateService;

    public NotificationTemplateController(NotificationTemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping("/api/v1/systems/{systemId}/notification-templates")
    public ApiResponse<PageResult<NotificationTemplateVO>> templates(@PathVariable String systemId,
                                                                     @RequestParam(defaultValue = "1") int pageNo,
                                                                     @RequestParam(defaultValue = "20") int pageSize,
                                                                     NotificationTemplateQueryRequest query) {
        return ApiResponse.success(templateService.templates(systemId, new PageRequest(pageNo, pageSize, null,
                List.of(), List.of()), query));
    }

    @PostMapping("/api/v1/systems/{systemId}/notification-templates")
    public ApiResponse<NotificationTemplateVO> create(@PathVariable String systemId,
                                                      @RequestBody NotificationTemplateSaveRequest request) {
        return ApiResponse.success(templateService.create(systemId, request));
    }

    @GetMapping("/api/v1/systems/{systemId}/notification-templates/{templateCode}")
    public ApiResponse<NotificationTemplateVO> detail(@PathVariable String systemId,
                                                      @PathVariable String templateCode) {
        return ApiResponse.success(templateService.detail(systemId, templateCode));
    }

    @PatchMapping("/api/v1/systems/{systemId}/notification-templates/{templateCode}")
    public ApiResponse<NotificationTemplateVO> update(@PathVariable String systemId,
                                                      @PathVariable String templateCode,
                                                      @RequestBody NotificationTemplateSaveRequest request) {
        return ApiResponse.success(templateService.update(systemId, templateCode, request));
    }

    @DeleteMapping("/api/v1/systems/{systemId}/notification-templates/{templateCode}")
    public ApiResponse<NotificationTemplateDeleteResult> delete(@PathVariable String systemId,
                                                                @PathVariable String templateCode) {
        return ApiResponse.success(templateService.delete(systemId, templateCode));
    }

    @PostMapping("/api/v1/systems/{systemId}/notification-templates/{templateCode}/publish-check")
    public ApiResponse<PublishCheckResultVO> publishCheck(@PathVariable String systemId,
                                                          @PathVariable String templateCode) {
        return ApiResponse.success(templateService.publishCheck(systemId, templateCode));
    }
}
