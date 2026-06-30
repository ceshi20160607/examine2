package com.unique.examine.core.api;

import com.unique.examine.core.context.RequestContext;
import java.util.List;

/**
 * Unified API response defined by the frozen API contract.
 *
 * @param code response code, SUCCESS or a domain-prefixed error code
 * @param message user-readable message
 * @param requestId request identifier used for troubleshooting
 * @param traceId trace identifier used to link logs and async tasks
 * @param auditLogId optional audit log id for write or security-sensitive actions
 * @param data response payload
 * @param errorFields optional field-level validation failures
 * @param disabledReason optional reason when an action is disabled or denied
 * @param <T> payload type
 */
public record ApiResponse<T>(
        String code,
        String message,
        String requestId,
        String traceId,
        String auditLogId,
        T data,
        List<ErrorField> errorFields,
        String disabledReason
) {

    /**
     * Build a success response using the current request context.
     *
     * @param data payload
     * @return success response
     * @param <T> payload type
     */
    public static <T> ApiResponse<T> success(T data) {
        RequestContext context = RequestContext.current();
        return new ApiResponse<>(
                "SUCCESS",
                "成功",
                context.requestId(),
                context.traceId(),
                context.auditLogId(),
                data,
                List.of(),
                null
        );
    }

    /**
     * Build a failed response using the current request context.
     *
     * @param code domain-prefixed error code
     * @param message user-readable message
     * @param errorFields optional field errors
     * @param disabledReason optional disabled reason
     * @return failure response
     */
    public static ApiResponse<Void> failure(String code, String message, List<ErrorField> errorFields,
                                            String disabledReason) {
        RequestContext context = RequestContext.current();
        List<ErrorField> resolvedErrorFields;
        if (errorFields == null) {
            resolvedErrorFields = List.of();
        } else {
            resolvedErrorFields = List.copyOf(errorFields);
        }
        return new ApiResponse<>(
                code,
                message,
                context.requestId(),
                context.traceId(),
                context.auditLogId(),
                null,
                resolvedErrorFields,
                disabledReason
        );
    }
}

