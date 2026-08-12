package com.unique.examine.openapi.security;

import com.unique.examine.openapi.domain.OpenApiCredential;
import com.unique.examine.openapi.domain.PlatformOpenApiApplication;

public record PlatformOpenApiAuthentication(PlatformOpenApiApplication application,
        OpenApiCredential credential,PlatformOpenApiMachineSession session) {
    public static final String REQUEST_ATTRIBUTE = PlatformOpenApiAuthentication.class.getName();
}
