package com.unique.examine.web.handler;

import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.core.security.AuthContextHolder;
import com.unique.examine.core.web.ApiResult;
import com.unique.examine.web.security.RequestContextFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResult<Void>> handleValid(MethodArgumentNotValidException e, HttpServletRequest request) {
        String requestId = getRequestId(request);
        request.setAttribute("exClass", e.getClass().getName());
        request.setAttribute("exMsg", e.getMessage());
        request.setAttribute("errorAt", findFirstAppFrame(e));
        String msg = "参数错误";
        if (e.getBindingResult().getFieldError() != null) {
            msg = e.getBindingResult().getFieldError().getDefaultMessage();
        }
        log.warn("[VALIDATION] requestId={} platId={} username={} systemId={} tenantId={} {} {} handler={}",
                requestId,
                AuthContextHolder.getPlatId(),
                AuthContextHolder.getUsername(),
                AuthContextHolder.getSystemIdOrDefault(),
                AuthContextHolder.getTenantIdOrDefault(),
                request.getMethod(),
                request.getRequestURI(),
                getHandlerSig(request));
        return ResponseEntity.badRequest()
                .header("X-Request-Id", requestId)
                .body(ApiResult.fail(400, msg, requestId));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResult<Void>> handleBiz(BusinessException e, HttpServletRequest request) {
        String requestId = getRequestId(request);
        request.setAttribute("exClass", e.getClass().getName());
        request.setAttribute("exMsg", e.getMessage());
        request.setAttribute("errorAt", findFirstAppFrame(e));
        HttpStatus status = HttpStatus.BAD_REQUEST;
        int c = e.getCode();
        if (c == 401) {
            status = HttpStatus.UNAUTHORIZED;
        } else if (c == 403) {
            status = HttpStatus.FORBIDDEN;
        } else if (c == 404) {
            status = HttpStatus.NOT_FOUND;
        }
        log.warn("[BIZ] requestId={} platId={} username={} systemId={} tenantId={} {} {} handler={} code={} msg={}",
                requestId,
                AuthContextHolder.getPlatId(),
                AuthContextHolder.getUsername(),
                AuthContextHolder.getSystemIdOrDefault(),
                AuthContextHolder.getTenantIdOrDefault(),
                request.getMethod(),
                request.getRequestURI(),
                getHandlerSig(request),
                c,
                e.getMessage());
        return ResponseEntity.status(status)
                .header("X-Request-Id", requestId)
                .body(ApiResult.fail(c, e.getMessage(), requestId));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Void>> handleAny(Exception e, HttpServletRequest request) {
        String requestId = getRequestId(request);
        String errorAt = findFirstAppFrame(e);
        String stackTop = formatTopStack(e, 12);
        request.setAttribute("exClass", e.getClass().getName());
        request.setAttribute("exMsg", e.getMessage());
        request.setAttribute("errorAt", errorAt);
        log.error("[ERROR] requestId={} platId={} username={} systemId={} tenantId={} {} {} handler={} errorAt={} ex={} msg={} stackTop={}",
                requestId,
                AuthContextHolder.getPlatId(),
                AuthContextHolder.getUsername(),
                AuthContextHolder.getSystemIdOrDefault(),
                AuthContextHolder.getTenantIdOrDefault(),
                request.getMethod(),
                request.getRequestURI(),
                getHandlerSig(request),
                errorAt,
                e.getClass().getName(),
                e.getMessage(),
                stackTop);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .header("X-Request-Id", requestId)
                .body(ApiResult.fail(500, "服务器错误", requestId));
    }

    private static String getRequestId(HttpServletRequest request) {
        Object rid = request.getAttribute(RequestContextFilter.ATTR_REQUEST_ID);
        return rid == null ? "" : String.valueOf(rid);
    }

    private static String getHandlerSig(HttpServletRequest request) {
        Object sig = request.getAttribute("handlerSig");
        return sig == null ? "" : String.valueOf(sig);
    }

    private static String findFirstAppFrame(Throwable e) {
        if (e == null || e.getStackTrace() == null) {
            return "";
        }
        for (StackTraceElement el : e.getStackTrace()) {
            if (el == null) continue;
            String cn = el.getClassName();
            if (cn != null && cn.startsWith("com.unique.examine.")) {
                return cn + "#" + el.getMethodName() + ":" + el.getLineNumber();
            }
        }
        StackTraceElement[] st = e.getStackTrace();
        if (st.length > 0 && st[0] != null) {
            StackTraceElement el = st[0];
            return el.getClassName() + "#" + el.getMethodName() + ":" + el.getLineNumber();
        }
        return "";
    }

    private static String formatTopStack(Throwable e, int maxLines) {
        if (e == null) {
            return "";
        }
        StackTraceElement[] st = e.getStackTrace();
        if (st == null || st.length == 0) {
            return "";
        }
        int n = Math.min(maxLines, st.length);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            StackTraceElement el = st[i];
            if (el == null) continue;
            if (sb.length() > 0) sb.append(" | ");
            sb.append(el.getClassName()).append("#").append(el.getMethodName()).append(":").append(el.getLineNumber());
        }
        if (st.length > n) {
            sb.append(" | ...(").append(st.length - n).append(" more)");
        }
        return sb.toString();
    }
}
