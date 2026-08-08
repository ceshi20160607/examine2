package com.unique.examine.event.api;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.event.domain.DeliveryChannel;
import com.unique.examine.event.service.EventChannelConfigurationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/systems/{systemId}/event/channels")
public class EventChannelConfigurationController {
    private final EventChannelConfigurationService service;

    public EventChannelConfigurationController(EventChannelConfigurationService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<EventChannelConfigurationService.ConfigurationView>> list(
            @PathVariable long systemId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object session,
            HttpServletRequest request) {
        EventDeliveryAdminSession.require(session, systemId);
        return success(service.list(systemId), request);
    }

    @PutMapping("/{channel}")
    public ApiResponse<EventChannelConfigurationService.ConfigurationView> update(
            @PathVariable long systemId, @PathVariable String channel,
            @RequestBody EventChannelConfigurationService.UpdateCommand command,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object session,
            HttpServletRequest request) {
        var actor = EventDeliveryAdminSession.require(session, systemId);
        return success(service.update(systemId, actor.memberId(), DeliveryChannel.parse(channel), command), request);
    }

    @PostMapping("/{channel}:check")
    public ApiResponse<EventChannelConfigurationService.CheckView> check(
            @PathVariable long systemId, @PathVariable String channel,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object session,
            HttpServletRequest request) {
        var actor = EventDeliveryAdminSession.require(session, systemId);
        return success(service.check(systemId, actor.tenantId(), actor.memberId(),
                DeliveryChannel.parse(channel)), request);
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
