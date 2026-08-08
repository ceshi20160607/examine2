package com.unique.examine.openapi.security;

import java.util.Optional;

public record PlatformOpenApiRoutePolicy(String method, String routeTemplate,
                                         String requiredScope, String requiredPermission) {
    public static Optional<PlatformOpenApiRoutePolicy> resolve(String method,String path) {
        if ("GET".equalsIgnoreCase(method) && "/openapi/v1/platform/tasks".equals(path))
            return Optional.of(new PlatformOpenApiRoutePolicy("GET", path,
                    "platform.task.read", "platform.task.read"));
        return Optional.empty();
    }
}
