package com.unique.examine.module.runtime.controller;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.module.runtime.api.SavedViewViews;
import com.unique.examine.module.runtime.security.RuntimeSession;
import com.unique.examine.module.runtime.service.SavedViewService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/systems/{systemId}/runtime/saved-views")
public class SavedViewController {
    private final SavedViewService service;

    public SavedViewController(SavedViewService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<SavedViewViews.SavedViewListResponse> list(
            @PathVariable long systemId,
            @RequestParam String moduleCode,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(service.list(RuntimeSession.require(value, systemId), moduleCode, requestId(request)), request);
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SavedViewViews.SavedViewResponse>> create(
            @PathVariable long systemId,
            @RequestBody String body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        var response = service.create(RuntimeSession.require(value, systemId), body, idempotencyKey,
                requestId(request), traceId(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(ok(response, request));
    }

    @PutMapping("/{viewId}")
    public ApiResponse<SavedViewViews.SavedViewResponse> update(
            @PathVariable long systemId,
            @PathVariable long viewId,
            @RequestBody String body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(service.update(RuntimeSession.require(value, systemId), viewId, body, idempotencyKey,
                requestId(request), traceId(request)), request);
    }

    @DeleteMapping("/{viewId}")
    public ApiResponse<SavedViewViews.DeleteResponse> delete(
            @PathVariable long systemId,
            @PathVariable long viewId,
            @RequestBody String body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(service.delete(RuntimeSession.require(value, systemId), viewId, body, idempotencyKey,
                requestId(request), traceId(request)), request);
    }

    private static <T> ApiResponse<T> ok(T data, HttpServletRequest request) {
        return ApiResponse.success(data, requestId(request), traceId(request));
    }

    private static String requestId(HttpServletRequest request) {
        return String.valueOf(request.getAttribute(WebRequestAttributes.REQUEST_ID));
    }

    private static String traceId(HttpServletRequest request) {
        return String.valueOf(request.getAttribute(WebRequestAttributes.TRACE_ID));
    }
}
