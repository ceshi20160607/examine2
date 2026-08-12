package com.unique.examine.web.ai;

import com.unique.examine.ai.PlatformAiActor;
import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.core.error.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;

final class PlatformAiControllerSupport {
    private PlatformAiControllerSupport() {
    }

    static PlatformAiActor actor(Object value, HttpServletRequest request) {
        if (!(value instanceof RequestSession session)) {
            throw new BusinessException(
                    "AUTH_REQUIRED", "Authentication is required",
                    HttpStatus.UNAUTHORIZED);
        }
        if (session.contextType() != ContextType.PLATFORM
                || session.systemId() != null
                || session.tenantId() != null
                || session.memberId() != null) {
            throw new BusinessException(
                    "CONTEXT_PLATFORM_REQUIRED",
                    "A platform context without system or tenant identity is required",
                    HttpStatus.FORBIDDEN);
        }
        try {
            return new PlatformAiActor(
                    session.accountId(), session.permissions(),
                    session.permissionVersion(),
                    attribute(request, WebRequestAttributes.REQUEST_ID),
                    attribute(request, WebRequestAttributes.TRACE_ID));
        } catch (IllegalArgumentException failure) {
            throw new BusinessException(
                    "PLATFORM_AI_CONTEXT_INVALID",
                    "Platform AI request context is invalid",
                    HttpStatus.FORBIDDEN);
        }
    }

    static <T> ApiResponse<T> success(T value, HttpServletRequest request) {
        return ApiResponse.success(
                value,
                attribute(request, WebRequestAttributes.REQUEST_ID),
                attribute(request, WebRequestAttributes.TRACE_ID));
    }

    static BusinessException invalid(String message) {
        return new BusinessException(
                "PLATFORM_AI_REQUEST_INVALID",
                message == null || message.isBlank()
                        ? "Platform AI request is invalid" : message,
                HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private static String attribute(HttpServletRequest request, String name) {
        var value = request.getAttribute(name);
        return value == null ? "" : String.valueOf(value);
    }
}
