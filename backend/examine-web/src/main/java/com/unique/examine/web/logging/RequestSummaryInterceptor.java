package com.unique.examine.web.logging;

import com.unique.examine.core.security.AuthContextHolder;
import com.unique.examine.web.security.RequestContextFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

public class RequestSummaryInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger("TRACE.CHAIN");

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                @Nullable Exception ex) {
        try {
            String requestId = getAttr(request, RequestContextFilter.ATTR_REQUEST_ID);
            String user = safe(AuthContextHolder.getUsername()) + "(" + AuthContextHolder.getPlatId() + ")";
            String module = resolveModuleCode(request.getRequestURI());
            String api = request.getMethod() + " " + request.getRequestURI();
            String handlerSig = handler instanceof HandlerMethod ? getAttr(request, "handlerSig") : "";
            List<String> chain = CallChainHolder.snapshot();

            String errorAt = getAttr(request, "errorAt");
            String exClass = getAttr(request, "exClass");
            String exMsg = getAttr(request, "exMsg");

            StringBuilder sb = new StringBuilder();
            sb.append("requestId=").append(requestId)
                    .append(" | ").append(user)
                    .append(" -> ").append(module)
                    .append(" -> ").append(api)
                    .append(" -> ").append(handlerSig);
            for (String c : chain) {
                sb.append(" -> ").append(c);
            }
            if (!errorAt.isBlank() || !exClass.isBlank() || !exMsg.isBlank()) {
                sb.append(" -> ").append(errorAt)
                        .append(" 异常=").append(exClass)
                        .append(" msg=").append(exMsg);
            }
            log.info(sb.toString());
        } catch (Exception ignore) {
            // ignore
        } finally {
            CallChainHolder.clear();
        }
    }

    private static String getAttr(HttpServletRequest request, String name) {
        Object v = request.getAttribute(name);
        return v == null ? "" : String.valueOf(v);
    }

    private static String resolveModuleCode(String uri) {
        String[] parts = uri.split("/");
        for (int i = 0; i < parts.length; i++) {
            if ("v1".equals(parts[i]) && i + 1 < parts.length) {
                return parts[i + 1];
            }
        }
        return "";
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}

