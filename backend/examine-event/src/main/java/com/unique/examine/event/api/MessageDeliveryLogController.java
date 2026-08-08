package com.unique.examine.event.api;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.event.service.MessageDeliveryLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/systems/{systemId}/event/delivery-logs")
public final class MessageDeliveryLogController {
    private final MessageDeliveryLogService service;

    public MessageDeliveryLogController(MessageDeliveryLogService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<MessageDeliveryLogService.DeliveryLogPage> list(
            @PathVariable long systemId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String templateCode,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object session,
            HttpServletRequest request
    ) {
        var actor = MessageTemplateAdminSession.require(session, systemId);
        return success(service.list(systemId, actor.tenantId(), page, size,
                channel, status, templateCode), request);
    }

    @GetMapping("/{deliveryId}")
    public ApiResponse<MessageDeliveryLogService.DeliveryLogDetail> detail(
            @PathVariable long systemId,
            @PathVariable long deliveryId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object session,
            HttpServletRequest request
    ) {
        var actor = MessageTemplateAdminSession.require(session, systemId);
        return success(service.detail(systemId, actor.tenantId(), deliveryId), request);
    }

    private static <T> ApiResponse<T> success(T data, HttpServletRequest request) {
        return ApiResponse.success(data, attribute(request, WebRequestAttributes.REQUEST_ID),
                attribute(request, WebRequestAttributes.TRACE_ID));
    }

    private static String attribute(HttpServletRequest request, String name) {
        var value = request.getAttribute(name);
        return value == null ? "" : String.valueOf(value);
    }
}
