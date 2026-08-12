package com.unique.examine.openapi.api;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.openapi.service.OpenApiCallbackUseCase;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/systems/{systemId}/admin/openapi/applications/{applicationId}/callbacks")
public final class OpenApiCallbackController {
    private final OpenApiCallbackUseCase callbacks;

    public OpenApiCallbackController(OpenApiCallbackUseCase callbacks) {
        this.callbacks = Objects.requireNonNull(callbacks, "callbacks");
    }

    @PostMapping
    public ResponseEntity<ApiResponse<OpenApiCallbackViews.Subscription>> create(
            @PathVariable long systemId, @PathVariable long applicationId,
            @RequestBody OpenApiCallbackRequests.Create body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object session,
            HttpServletRequest request) {
        var value = callbacks.create(OpenApiAdminSession.require(session, systemId), applicationId, body,
                attribute(request, WebRequestAttributes.REQUEST_ID),
                attribute(request, WebRequestAttributes.TRACE_ID));
        return ResponseEntity.status(HttpStatus.CREATED).body(success(value, request));
    }

    @GetMapping
    public ApiResponse<List<OpenApiCallbackViews.Subscription>> list(
            @PathVariable long systemId, @PathVariable long applicationId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object session,
            HttpServletRequest request) {
        return success(callbacks.list(OpenApiAdminSession.require(session, systemId), applicationId), request);
    }

    @GetMapping("/{subscriptionId}")
    public ApiResponse<OpenApiCallbackViews.Subscription> detail(
            @PathVariable long systemId, @PathVariable long applicationId, @PathVariable long subscriptionId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object session,
            HttpServletRequest request) {
        return success(callbacks.detail(OpenApiAdminSession.require(session, systemId), applicationId,
                subscriptionId), request);
    }

    @PutMapping("/{subscriptionId}")
    public ApiResponse<OpenApiCallbackViews.Subscription> replace(
            @PathVariable long systemId, @PathVariable long applicationId, @PathVariable long subscriptionId,
            @RequestBody OpenApiCallbackRequests.ReplaceConfiguration body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object session,
            HttpServletRequest request) {
        return success(callbacks.replace(OpenApiAdminSession.require(session, systemId), applicationId,
                subscriptionId, body, attribute(request, WebRequestAttributes.REQUEST_ID),
                attribute(request, WebRequestAttributes.TRACE_ID)), request);
    }

    @PostMapping("/{subscriptionId}:rotate-signing-secret")
    public ApiResponse<OpenApiCallbackViews.Subscription> rotate(
            @PathVariable long systemId, @PathVariable long applicationId, @PathVariable long subscriptionId,
            @RequestBody OpenApiCallbackRequests.RotateSigningSecret body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object session,
            HttpServletRequest request) {
        return success(callbacks.rotateSecret(OpenApiAdminSession.require(session, systemId), applicationId,
                subscriptionId, body, attribute(request, WebRequestAttributes.REQUEST_ID),
                attribute(request, WebRequestAttributes.TRACE_ID)), request);
    }

    @PostMapping("/{subscriptionId}:enable")
    public ApiResponse<OpenApiCallbackViews.Subscription> enable(
            @PathVariable long systemId, @PathVariable long applicationId, @PathVariable long subscriptionId,
            @RequestBody OpenApiCallbackRequests.ChangeStatus body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object session,
            HttpServletRequest request) {
        return success(callbacks.enable(OpenApiAdminSession.require(session, systemId), applicationId,
                subscriptionId, body, attribute(request, WebRequestAttributes.REQUEST_ID),
                attribute(request, WebRequestAttributes.TRACE_ID)), request);
    }

    @PostMapping("/{subscriptionId}:disable")
    public ApiResponse<OpenApiCallbackViews.Subscription> disable(
            @PathVariable long systemId, @PathVariable long applicationId, @PathVariable long subscriptionId,
            @RequestBody OpenApiCallbackRequests.ChangeStatus body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object session,
            HttpServletRequest request) {
        return success(callbacks.disable(OpenApiAdminSession.require(session, systemId), applicationId,
                subscriptionId, body, attribute(request, WebRequestAttributes.REQUEST_ID),
                attribute(request, WebRequestAttributes.TRACE_ID)), request);
    }

    @GetMapping("/{subscriptionId}/deliveries")
    public ApiResponse<OpenApiCallbackViews.DeliveryPage> deliveries(
            @PathVariable long systemId, @PathVariable long applicationId, @PathVariable long subscriptionId,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object session,
            HttpServletRequest request) {
        return success(callbacks.deliveries(OpenApiAdminSession.require(session, systemId), applicationId,
                subscriptionId, page, size), request);
    }

    private static <T> ApiResponse<T> success(T value, HttpServletRequest request) {
        return ApiResponse.success(value, attribute(request, WebRequestAttributes.REQUEST_ID),
                attribute(request, WebRequestAttributes.TRACE_ID));
    }

    private static String attribute(HttpServletRequest request, String name) {
        var value = request.getAttribute(name);
        return value == null ? "" : String.valueOf(value);
    }
}
