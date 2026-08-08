package com.unique.examine.web.error;

import com.unique.examine.core.api.ApiError;
import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.AggregateRef;
import com.unique.examine.core.api.OperationAudit;
import com.unique.examine.core.api.OperationAuditFacade;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.core.error.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final Pattern SYSTEM_PATH = Pattern.compile("^/api/v1/systems/([1-9][0-9]*)(?:/|$)");
    private final OperationAuditFacade operationAuditFacade;

    public GlobalExceptionHandler(OperationAuditFacade operationAuditFacade) {
        this.operationAuditFacade = operationAuditFacade;
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> business(BusinessException exception, HttpServletRequest request) {
        var status = exception.status().value();
        auditFailure(request, exception.code(), status == 401 || status == 403 || status == 404);
        return ResponseEntity.status(exception.status()).body(ApiResponse.failure(
                exception.code(),
                exception.getMessage(),
                exception.data(),
                requestId(request),
                traceId(request),
                exception.errors()
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> validation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        auditFailure(request, "VALIDATION_ERROR", false);
        var errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiError("VALIDATION_ERROR", error.getField(), error.getDefaultMessage()))
                .toList();
        return ResponseEntity.unprocessableEntity().body(ApiResponse.failure(
                "VALIDATION_ERROR",
                "请检查输入内容",
                requestId(request),
                traceId(request),
                errors
        ));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiResponse<Void>> duplicate(DuplicateKeyException exception, HttpServletRequest request) {
        LOGGER.info("Duplicate key rejected requestId={}", requestId(request));
        auditFailure(request, "DATA_CONFLICT", false);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.failure(
                "DATA_CONFLICT",
                "数据已存在或正在被其他请求处理",
                requestId(request),
                traceId(request),
                List.of()
        ));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> unreadable(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        auditFailure(request, "VALIDATION_ERROR", false);
        return ResponseEntity.badRequest().body(ApiResponse.failure(
                "VALIDATION_ERROR",
                "请求内容格式无效，请检查字段类型和枚举值",
                requestId(request),
                traceId(request),
                List.of()
        ));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> notFound(NoResourceFoundException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.failure(
                "RESOURCE_NOT_FOUND",
                "请求的资源不存在",
                requestId(request),
                traceId(request),
                List.of()
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> unexpected(Exception exception, HttpServletRequest request) {
        LOGGER.error("Unhandled request failure requestId={}", requestId(request), exception);
        auditFailure(request, "INTERNAL_ERROR", false);
        return ResponseEntity.internalServerError().body(ApiResponse.failure(
                "INTERNAL_ERROR",
                "系统暂时无法完成请求",
                requestId(request),
                traceId(request),
                List.of()
        ));
    }

    private static String requestId(HttpServletRequest request) {
        return String.valueOf(request.getAttribute(WebRequestAttributes.REQUEST_ID));
    }

    private static String traceId(HttpServletRequest request) {
        return String.valueOf(request.getAttribute(WebRequestAttributes.TRACE_ID));
    }

    private void auditFailure(HttpServletRequest request, String failureCode, boolean denied) {
        try {
            var sessionValue = request.getAttribute(RequestSession.REQUEST_ATTRIBUTE);
            var session = sessionValue instanceof RequestSession value ? value : null;
            var actor = new OperationAudit.Actor(session == null ? null : session.accountId(), "WEB");
            var context = auditContext(request, session);
            var audit = denied
                    ? OperationAudit.denied(
                            actor, context, new AggregateRef("HTTP_REQUEST", requestId(request)),
                            "HTTP_REQUEST_DENIED", requestFacts(request), null,
                            new OperationAudit.Failure(failureCode), requestId(request), traceId(request)
                    )
                    : OperationAudit.failed(
                            actor, context, new AggregateRef("HTTP_REQUEST", requestId(request)),
                            "HTTP_REQUEST_FAILED", requestFacts(request), null,
                            new OperationAudit.Failure(failureCode), requestId(request), traceId(request)
                    );
            if (denied) {
                operationAuditFacade.recordDenied(audit);
            } else {
                operationAuditFacade.recordFailed(audit);
            }
        } catch (RuntimeException auditException) {
            LOGGER.error("Failed to record operation audit requestId={}", requestId(request), auditException);
        }
    }

    private static OperationAudit.Context auditContext(
            HttpServletRequest request,
            RequestSession session
    ) {
        if (session != null) {
            return new OperationAudit.Context(session.contextType(), session.systemId(), session.tenantId());
        }
        var matcher = SYSTEM_PATH.matcher(request.getRequestURI());
        if (matcher.find()) {
            return new OperationAudit.Context(ContextType.SYSTEM, Long.parseLong(matcher.group(1)), null);
        }
        return new OperationAudit.Context(ContextType.PLATFORM, null, null);
    }

    private static Map<String, String> requestFacts(HttpServletRequest request) {
        return Map.of(
                "method", request.getMethod(),
                "path", request.getRequestURI()
        );
    }
}
