package com.unique.examine.openapi.api;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.openapi.service.OpenApiApplicationUseCase;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

import java.util.Objects;

@RestController
@RequestMapping("/api/v1/systems/{systemId}/admin/openapi/applications")
public class OpenApiApplicationController {
    private final OpenApiApplicationUseCase useCase;

    public OpenApiApplicationController(OpenApiApplicationUseCase useCase) {
        this.useCase = Objects.requireNonNull(useCase, "useCase");
    }

    @PostMapping
    public ResponseEntity<ApiResponse<OpenApiViews.Application>> create(
            @PathVariable long systemId,
            @RequestBody OpenApiRequests.CreateApplication body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false)
            Object sessionValue,
            HttpServletRequest request
    ) {
        var result = useCase.create(
                OpenApiAdminSession.require(sessionValue, systemId),
                body,
                idempotencyKey,
                requestAttribute(request, WebRequestAttributes.REQUEST_ID),
                requestAttribute(request, WebRequestAttributes.TRACE_ID)
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(success(result, request));
    }

    @GetMapping
    public ApiResponse<OpenApiViews.ApplicationPage> list(
            @PathVariable long systemId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false)
            Object sessionValue,
            HttpServletRequest request
    ) {
        return success(useCase.list(
                OpenApiAdminSession.require(sessionValue, systemId), page, size), request);
    }

    @GetMapping("/{applicationId}")
    public ApiResponse<OpenApiViews.Application> detail(
            @PathVariable long systemId,
            @PathVariable long applicationId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false)
            Object sessionValue,
            HttpServletRequest request
    ) {
        return success(useCase.detail(
                OpenApiAdminSession.require(sessionValue, systemId), applicationId), request);
    }

    @GetMapping("/{applicationId}/call-logs")
    public ApiResponse<OpenApiViews.CallLogPage> callLogs(
            @PathVariable long systemId,
            @PathVariable long applicationId,
            @RequestParam(defaultValue = "ALL") String resultCategory,
            @RequestParam(defaultValue = "ALL") String requestMethod,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false)
            Object sessionValue,
            HttpServletRequest request
    ) {
        return success(useCase.callLogs(
                OpenApiAdminSession.require(sessionValue, systemId),
                applicationId, resultCategory, requestMethod, page, size), request);
    }

    @PutMapping("/{applicationId}/policy")
    public ApiResponse<OpenApiViews.Application> updatePolicy(
            @PathVariable long systemId,
            @PathVariable long applicationId,
            @RequestBody OpenApiRequests.UpdatePolicy body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false)
            Object sessionValue,
            HttpServletRequest request
    ) {
        return success(useCase.updatePolicy(
                OpenApiAdminSession.require(sessionValue, systemId),
                applicationId,
                body,
                idempotencyKey,
                requestAttribute(request, WebRequestAttributes.REQUEST_ID),
                requestAttribute(request, WebRequestAttributes.TRACE_ID)
        ), request);
    }

    @PostMapping("/{applicationId}:rotate-secret-ref")
    public ApiResponse<OpenApiViews.Application> rotateSecretRef(
            @PathVariable long systemId,
            @PathVariable long applicationId,
            @RequestBody OpenApiRequests.RotateSecretRef body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false)
            Object sessionValue,
            HttpServletRequest request
    ) {
        return success(useCase.rotateSecretRef(
                OpenApiAdminSession.require(sessionValue, systemId),
                applicationId,
                body,
                idempotencyKey,
                requestAttribute(request, WebRequestAttributes.REQUEST_ID),
                requestAttribute(request, WebRequestAttributes.TRACE_ID)
        ), request);
    }

    @PostMapping("/{applicationId}:enable")
    public ApiResponse<OpenApiViews.Application> enable(
            @PathVariable long systemId,
            @PathVariable long applicationId,
            @RequestBody OpenApiRequests.ChangeStatus body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false)
            Object sessionValue,
            HttpServletRequest request
    ) {
        return success(useCase.enable(
                OpenApiAdminSession.require(sessionValue, systemId),
                applicationId,
                body,
                idempotencyKey,
                requestAttribute(request, WebRequestAttributes.REQUEST_ID),
                requestAttribute(request, WebRequestAttributes.TRACE_ID)
        ), request);
    }

    @PostMapping("/{applicationId}:disable")
    public ApiResponse<OpenApiViews.Application> disable(
            @PathVariable long systemId,
            @PathVariable long applicationId,
            @RequestBody OpenApiRequests.ChangeStatus body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false)
            Object sessionValue,
            HttpServletRequest request
    ) {
        return success(useCase.disable(
                OpenApiAdminSession.require(sessionValue, systemId),
                applicationId,
                body,
                idempotencyKey,
                requestAttribute(request, WebRequestAttributes.REQUEST_ID),
                requestAttribute(request, WebRequestAttributes.TRACE_ID)
        ), request);
    }

    private static <T> ApiResponse<T> success(T value, HttpServletRequest request) {
        return ApiResponse.success(
                value,
                requestAttribute(request, WebRequestAttributes.REQUEST_ID),
                requestAttribute(request, WebRequestAttributes.TRACE_ID)
        );
    }

    private static String requestAttribute(HttpServletRequest request, String name) {
        var value = request.getAttribute(name);
        return value == null ? "" : String.valueOf(value);
    }
}
