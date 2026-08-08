package com.unique.examine.openapi.security;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PlatformOpenApiRoutePolicyTest {
    @Test void exposesOnlyTheBoundedPlatformTaskReadRoute() {
        var route=PlatformOpenApiRoutePolicy.resolve("GET","/openapi/v1/platform/tasks");
        assertThat(route).isPresent();
        assertThat(route.orElseThrow().requiredScope()).isEqualTo("platform.task.read");
        assertThat(route.orElseThrow().requiredPermission()).isEqualTo("platform.task.read");
        assertThat(PlatformOpenApiRoutePolicy.resolve("POST","/openapi/v1/platform/tasks")).isEmpty();
        assertThat(PlatformOpenApiRoutePolicy.resolve("GET","/openapi/v1/ping")).isEmpty();
    }
}
