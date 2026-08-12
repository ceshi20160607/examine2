package com.unique.examine.web.vnext.registration;

import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.plat.vnext.manage.auth.ClientRequest;
import jakarta.servlet.http.HttpServletRequest;

final class RegistrationWebRequestSupport {
    private RegistrationWebRequestSupport() {
    }

    static String requestId(HttpServletRequest request) {
        return String.valueOf(request.getAttribute(WebRequestAttributes.REQUEST_ID));
    }

    static String traceId(HttpServletRequest request) {
        return String.valueOf(request.getAttribute(WebRequestAttributes.TRACE_ID));
    }

    static ClientRequest client(HttpServletRequest request) {
        return new ClientRequest(
                requestId(request), traceId(request), remoteAddress(request),
                request.getHeader("User-Agent")
        );
    }

    private static String remoteAddress(HttpServletRequest request) {
        var forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",", 2)[0].trim();
        }
        return request.getRemoteAddr();
    }
}
