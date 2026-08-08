package com.unique.examine.plat.manage.controller;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.plat.api.AuthenticatedSession;
import com.unique.examine.plat.manage.service.AccessRequestService;
import com.unique.examine.plat.manage.service.SessionGuard;
import com.unique.examine.plat.manage.vo.AccessRequestModels;
import com.unique.examine.plat.manage.vo.PageResultVo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/context")
public class AccessRequestController {
    private final AccessRequestService service;

    public AccessRequestController(AccessRequestService service) {
        this.service = service;
    }

    @GetMapping("/access-requests")
    public ApiResponse<PageResultVo<AccessRequestModels.View>> listOwn(
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            @RequestParam(required = false) Long systemId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request
    ) {
        var session = SessionGuard.require(sessionValue);
        return ApiResponse.success(
                service.listOwn(session, systemId, page, size),
                ControllerSupport.requestId(request), ControllerSupport.traceId(request)
        );
    }

    @PostMapping("/systems/{systemId}/access-requests")
    public ApiResponse<AccessRequestModels.View> submit(
            @PathVariable long systemId,
            @Valid @RequestBody AccessRequestModels.Submit input,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request
    ) {
        var session = SessionGuard.requirePlatform(sessionValue, "platform.runtime.access");
        var client = ControllerSupport.client(request);
        return ApiResponse.success(
                service.submit(session, systemId, input, idempotencyKey, client),
                client.requestId(), client.traceId()
        );
    }

    @PostMapping("/access-requests/{requestId}:cancel")
    public ApiResponse<AccessRequestModels.View> cancel(
            @PathVariable long requestId,
            @Valid @RequestBody AccessRequestModels.Cancel input,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request
    ) {
        var session = SessionGuard.require(sessionValue);
        var client = ControllerSupport.client(request);
        return ApiResponse.success(
                service.cancel(session, requestId, input, idempotencyKey, client),
                client.requestId(), client.traceId()
        );
    }
}
