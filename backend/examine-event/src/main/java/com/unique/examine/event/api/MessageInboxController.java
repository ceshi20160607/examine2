package com.unique.examine.event.api;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.event.service.MessageInboxService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/systems/{systemId}/event/messages")
public class MessageInboxController {
    private final MessageInboxService service;

    public MessageInboxController(MessageInboxService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<MessageInboxApiModels.Page> list(
            @PathVariable long systemId,
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object session,
            HttpServletRequest request
    ) {
        var messages = service.inbox(
                EventRequestSession.require(session, systemId),
                status,
                page,
                size
        );
        return success(MessageInboxApiModels.Page.from(messages), request);
    }

    @GetMapping("/unread-count")
    public ApiResponse<MessageInboxApiModels.UnreadCount> unreadCount(
            @PathVariable long systemId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object session,
            HttpServletRequest request
    ) {
        return success(new MessageInboxApiModels.UnreadCount(service.unreadCount(
                EventRequestSession.require(session, systemId))), request);
    }

    @PostMapping("/{messageId}:read")
    public ApiResponse<MessageInboxApiModels.MessageView> read(
            @PathVariable long systemId,
            @PathVariable long messageId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object session,
            HttpServletRequest request
    ) {
        var message = service.markRead(
                EventRequestSession.require(session, systemId), messageId);
        return success(MessageInboxApiModels.MessageView.from(message), request);
    }

    @PostMapping("/read-all")
    public ApiResponse<MessageInboxApiModels.ChangedCount> readAll(
            @PathVariable long systemId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object session,
            HttpServletRequest request
    ) {
        return success(new MessageInboxApiModels.ChangedCount(service.markAllRead(
                EventRequestSession.require(session, systemId))), request);
    }

    @PostMapping("/{messageId}:archive")
    public ApiResponse<MessageInboxApiModels.MessageView> archive(
            @PathVariable long systemId,
            @PathVariable long messageId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object session,
            HttpServletRequest request
    ) {
        var message = service.archive(
                EventRequestSession.require(session, systemId), messageId);
        return success(MessageInboxApiModels.MessageView.from(message), request);
    }

    private static <T> ApiResponse<T> success(T data, HttpServletRequest request) {
        return ApiResponse.success(
                data,
                attribute(request, WebRequestAttributes.REQUEST_ID),
                attribute(request, WebRequestAttributes.TRACE_ID));
    }

    private static String attribute(HttpServletRequest request, String name) {
        var value = request.getAttribute(name);
        return value == null ? "" : String.valueOf(value);
    }
}
