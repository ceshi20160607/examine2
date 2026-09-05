package com.unique.unexamine.notification.manage;

import com.unique.unexamine.authentication.manage.AuthenticationContextHolder;
import com.unique.unexamine.shared.manage.web.ApiResult;
import com.unique.unexamine.shared.manage.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/messages")
public class MessageController {
    private final MessageService service;

    public MessageController(MessageService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResult<MessageModels.InboxView> list(
            @RequestParam(defaultValue = "ACTIVE") String status,
            @RequestParam(defaultValue = "ALL") String sourceType) {
        return ApiResult.ok(service.list(AuthenticationContextHolder.require(), status, sourceType));
    }

    @GetMapping("/unread-count")
    public ApiResult<MessageModels.UnreadCount> unreadCount() {
        return ApiResult.ok(service.unreadCount(AuthenticationContextHolder.require()));
    }

    @PostMapping("/events")
    public ApiResult<MessageModels.MessageView> send(
            @Valid @RequestBody MessageModels.SendEventRequest input,
            HttpServletRequest request) {
        return ApiResult.ok(service.send(AuthenticationContextHolder.require(), input,
                TraceIdFilter.current(request)));
    }

    @PostMapping("/read-all")
    public ApiResult<MessageModels.UnreadCount> readAll(HttpServletRequest request) {
        return ApiResult.ok(service.readAll(AuthenticationContextHolder.require(), TraceIdFilter.current(request)));
    }

    @PostMapping("/{messageId}/read")
    public ApiResult<MessageModels.MessageView> read(
            @PathVariable Long messageId, HttpServletRequest request) {
        return ApiResult.ok(service.read(AuthenticationContextHolder.require(), messageId,
                TraceIdFilter.current(request)));
    }

    @PostMapping("/{messageId}/archive")
    public ApiResult<MessageModels.MessageView> archive(
            @PathVariable Long messageId, HttpServletRequest request) {
        return ApiResult.ok(service.archive(AuthenticationContextHolder.require(), messageId,
                TraceIdFilter.current(request)));
    }

    @PostMapping("/{messageId}/open")
    public ApiResult<MessageModels.OpenResult> open(
            @PathVariable Long messageId, HttpServletRequest request) {
        return ApiResult.ok(service.open(AuthenticationContextHolder.require(), messageId,
                TraceIdFilter.current(request)));
    }

    @PostMapping("/deliveries/{deliveryId}/retry")
    public ApiResult<MessageModels.MessageView> retry(
            @PathVariable Long deliveryId, HttpServletRequest request) {
        return ApiResult.ok(service.retryDelivery(AuthenticationContextHolder.require(), deliveryId,
                TraceIdFilter.current(request)));
    }

    @GetMapping("/diagnostics/{messageId}/deliveries")
    public ApiResult<MessageModels.DeliveryDiagnosticsView> diagnostics(@PathVariable Long messageId) {
        return ApiResult.ok(service.deliveryDiagnostics(AuthenticationContextHolder.require(), messageId));
    }
}
