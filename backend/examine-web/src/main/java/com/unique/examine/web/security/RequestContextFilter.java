package com.unique.examine.web.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.MDC;
import com.unique.examine.web.logging.CallChainHolder;

import java.io.IOException;
import java.util.UUID;

/**
 * 统一解析请求上下文（requestId）。
 * 鉴权不在这里做，交给 TokenAuthenticationFilter。
 */
public class RequestContextFilter extends OncePerRequestFilter {

    public static final String ATTR_REQUEST_ID = "requestId";
    public static final String HEADER_REQUEST_ID = "X-Request-Id";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            String requestId = request.getHeader(HEADER_REQUEST_ID);
            if (requestId == null || requestId.isBlank()) {
                requestId = UUID.randomUUID().toString().replace("-", "");
            } else {
                requestId = sanitize(requestId);
            }
            request.setAttribute(ATTR_REQUEST_ID, requestId);
            response.setHeader(HEADER_REQUEST_ID, requestId);

            MDC.put("requestId", requestId);
            MDC.put("httpMethod", request.getMethod());
            MDC.put("uri", request.getRequestURI());

            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
            CallChainHolder.clear();
        }
    }

    private static String sanitize(String s) {
        String t = s.trim();
        if (t.length() > 64) {
            t = t.substring(0, 64);
        }
        StringBuilder sb = new StringBuilder(t.length());
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if ((c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '-' || c == '_' || c == '.') {
                sb.append(c);
            }
        }
        String out = sb.toString();
        return out.isBlank() ? UUID.randomUUID().toString().replace("-", "") : out;
    }
}

