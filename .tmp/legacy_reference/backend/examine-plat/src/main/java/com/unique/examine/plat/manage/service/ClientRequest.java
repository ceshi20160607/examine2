package com.unique.examine.plat.manage.service;

public record ClientRequest(
        String requestId,
        String traceId,
        String remoteAddress,
        String userAgent
) {
}
