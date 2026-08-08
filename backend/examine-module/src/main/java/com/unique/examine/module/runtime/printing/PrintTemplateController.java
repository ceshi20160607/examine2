package com.unique.examine.module.runtime.printing;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.module.manage.security.ConfigSession;
import com.unique.examine.module.manage.service.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/systems/{systemId}/admin/config/modules/{moduleId}/print-templates")
public class PrintTemplateController {
    private final PrintTemplateService service;

    public PrintTemplateController(PrintTemplateService service) { this.service = service; }

    @GetMapping
    public ApiResponse<List<PrintViews.Template>> list(
            @PathVariable long systemId, @PathVariable long moduleId,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request) {
        return ok(service.list(ConfigSession.require(value, systemId), moduleId), request);
    }

    @PostMapping
    public ApiResponse<PrintViews.Template> create(
            @PathVariable long systemId, @PathVariable long moduleId,
            @RequestBody PrintViews.CreateTemplateRequest body,
            @RequestHeader(name = "Idempotency-Key", required = false) String key,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request) {
        return ok(service.create(ConfigSession.require(value, systemId), moduleId, body, key, context(request)), request);
    }

    @PutMapping("/{templateId}")
    public ApiResponse<PrintViews.Template> update(
            @PathVariable long systemId, @PathVariable long moduleId, @PathVariable long templateId,
            @RequestBody PrintViews.UpdateTemplateRequest body,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request) {
        return ok(service.update(ConfigSession.require(value, systemId), moduleId, templateId, body,
                context(request)), request);
    }

    @PostMapping("/{templateId}:publish")
    public ApiResponse<PrintViews.Template> publish(
            @PathVariable long systemId, @PathVariable long moduleId, @PathVariable long templateId,
            @RequestBody PrintViews.PublishTemplateRequest body,
            @RequestHeader(name = "Idempotency-Key", required = false) String key,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request) {
        return ok(service.publish(ConfigSession.require(value, systemId), moduleId, templateId, body, key,
                context(request)), request);
    }

    private static RequestContext context(HttpServletRequest request) {
        return new RequestContext(attribute(request, WebRequestAttributes.REQUEST_ID),
                attribute(request, WebRequestAttributes.TRACE_ID));
    }
    private static <T> ApiResponse<T> ok(T value, HttpServletRequest request) {
        return ApiResponse.success(value, attribute(request, WebRequestAttributes.REQUEST_ID),
                attribute(request, WebRequestAttributes.TRACE_ID));
    }
    private static String attribute(HttpServletRequest request, String name) {
        return String.valueOf(request.getAttribute(name));
    }
}
