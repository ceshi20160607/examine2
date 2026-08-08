package com.unique.examine.event.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.event.service.DeliveryPreferenceService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/systems/{systemId}/event/delivery-preferences")
public class DeliveryPreferenceController {
    private final DeliveryPreferenceService service;

    public DeliveryPreferenceController(DeliveryPreferenceService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<DeliveryPreferenceApiModels.PreferenceView>> list(
            @PathVariable long systemId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object session,
            HttpServletRequest request
    ) {
        var items = service.list(EventRequestSession.require(session, systemId)).stream()
                .map(DeliveryPreferenceApiModels.PreferenceView::from)
                .toList();
        return success(items, request);
    }

    @PutMapping("/{templateCode}")
    public ApiResponse<DeliveryPreferenceApiModels.PreferenceView> update(
            @PathVariable long systemId,
            @PathVariable String templateCode,
            @RequestBody(required = false) JsonNode body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object session,
            HttpServletRequest request
    ) {
        var actor = EventRequestSession.require(session, systemId);
        var command = DeliveryPreferenceApiModels.UpdateRequest.parse(body);
        var value = service.update(actor, templateCode,
                command.enabled(), command.expectedVersion());
        return success(DeliveryPreferenceApiModels.PreferenceView.from(value), request);
    }

    @PutMapping("/{templateCode}/{channel}")
    public ApiResponse<DeliveryPreferenceApiModels.PreferenceView> updateChannel(
            @PathVariable long systemId,
            @PathVariable String templateCode,
            @PathVariable String channel,
            @RequestBody(required = false) JsonNode body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object session,
            HttpServletRequest request
    ) {
        var actor = EventRequestSession.require(session, systemId);
        var command = DeliveryPreferenceApiModels.UpdateRequest.parse(body);
        var value = service.update(actor, templateCode, channel,
                command.enabled(), command.expectedVersion());
        return success(DeliveryPreferenceApiModels.PreferenceView.from(value), request);
    }

    private static <T> ApiResponse<T> success(T data, HttpServletRequest request) {
        return ApiResponse.success(data,
                attribute(request, WebRequestAttributes.REQUEST_ID),
                attribute(request, WebRequestAttributes.TRACE_ID));
    }

    private static String attribute(HttpServletRequest request, String name) {
        var value = request.getAttribute(name);
        return value == null ? "" : String.valueOf(value);
    }
}
