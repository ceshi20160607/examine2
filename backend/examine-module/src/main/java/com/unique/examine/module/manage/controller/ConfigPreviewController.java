package com.unique.examine.module.manage.controller;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.module.manage.api.ConfigPreviewViews;
import com.unique.examine.module.manage.security.ConfigSession;
import com.unique.examine.module.manage.service.ConfigPreviewService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/systems/{systemId}/admin/config/preview")
public class ConfigPreviewController {
    private final ConfigPreviewService previews;

    public ConfigPreviewController(ConfigPreviewService previews) {
        this.previews = previews;
    }

    @GetMapping
    public ApiResponse<ConfigPreviewViews.PermissionPreview> preview(
            @PathVariable long systemId,
            @RequestParam long memberId,
            @RequestParam(required = false) Long tenantId,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        return ApiResponse.success(previews.preview(ConfigSession.require(value, systemId), memberId, tenantId),
                request.getAttribute(WebRequestAttributes.REQUEST_ID).toString(),
                request.getAttribute(WebRequestAttributes.TRACE_ID).toString());
    }
}
