package com.unique.examine.module.manage.controller;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.module.manage.api.ConfigRequests;
import com.unique.examine.module.manage.api.ConfigViews;
import com.unique.examine.module.manage.security.ConfigSession;
import com.unique.examine.module.manage.service.ConfigPublicationService;
import com.unique.examine.module.manage.service.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/systems/{systemId}/admin/config:publish")
public class ConfigPublicationController {
    private final ConfigPublicationService publications;

    public ConfigPublicationController(ConfigPublicationService publications) {
        this.publications = publications;
    }

    @PostMapping
    public ApiResponse<ConfigViews.PublishResult> publish(
            @PathVariable long systemId,
            @Valid @RequestBody ConfigRequests.PublishConfig body,
            @RequestHeader(name = "Idempotency-Key", required = false) String key,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        var session = ConfigSession.require(value, systemId);
        var context = new RequestContext(attribute(request, WebRequestAttributes.REQUEST_ID),
                attribute(request, WebRequestAttributes.TRACE_ID));
        return ApiResponse.success(publications.publish(session, body, key, context),
                context.requestId(), context.traceId());
    }

    private static String attribute(HttpServletRequest request, String name) {
        return String.valueOf(request.getAttribute(name));
    }
}
