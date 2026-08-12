package com.unique.examine.plat.vnext.manage.auth;

public record ClientRequest(
        String requestId,
        String traceId,
        String remoteAddress,
        String userAgent
) {
}
