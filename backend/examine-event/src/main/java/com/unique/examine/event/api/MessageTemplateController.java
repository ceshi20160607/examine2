package com.unique.examine.event.api;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.event.service.MessageTemplateService;
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
@RequestMapping("/api/v1/systems/{systemId}/admin/event/message-templates")
public class MessageTemplateController {
    private final MessageTemplateService service;

    public MessageTemplateController(MessageTemplateService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<MessageTemplateService.TemplateView>> list(
            @PathVariable long systemId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object session,
            HttpServletRequest request
    ) {
        var actor = MessageTemplateAdminSession.require(session, systemId);
        return success(service.list(systemId, actor.memberId()), request);
    }

    @PutMapping("/{templateCode}")
    public ApiResponse<MessageTemplateService.TemplateView> update(
            @PathVariable long systemId,
            @PathVariable String templateCode,
            @RequestBody MessageTemplateService.UpdateCommand command,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object session,
            HttpServletRequest request
    ) {
        var actor = MessageTemplateAdminSession.require(session, systemId);
        return success(service.update(systemId, actor.memberId(), templateCode, command), request);
    }

    @PostMapping("/{templateCode}:publish")
    public ApiResponse<MessageTemplateService.TemplateView> publish(
            @PathVariable long systemId,
            @PathVariable String templateCode,
            @RequestBody PublishRequest command,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object session,
            HttpServletRequest request
    ) {
        var actor = MessageTemplateAdminSession.require(session, systemId);
        return success(service.publish(systemId, actor.memberId(), templateCode,
                command == null ? -1 : command.expectedVersion()), request);
    }

    public record PublishRequest(long expectedVersion) { }

    private static <T> ApiResponse<T> success(T data, HttpServletRequest request) {
        return ApiResponse.success(data, attribute(request, WebRequestAttributes.REQUEST_ID),
                attribute(request, WebRequestAttributes.TRACE_ID));
    }

    private static String attribute(HttpServletRequest request, String name) {
        var value = request.getAttribute(name);
        return value == null ? "" : String.valueOf(value);
    }
}
