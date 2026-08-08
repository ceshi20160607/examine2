package com.unique.examine.openapi.security;

import com.unique.examine.openapi.domain.OpenApiApplication;
import com.unique.examine.openapi.domain.OpenApiCredential;

public record OpenApiAuthentication(
        OpenApiApplication application,
        OpenApiCredential credential,
        OpenApiMachineSession session
) {
    public static final String REQUEST_ATTRIBUTE =
            "com.unique.examine.openapi.authentication";
}
