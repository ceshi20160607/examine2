package com.unique.examine.openapi.api;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.openapi.security.OpenApiAuthentication;
import com.unique.examine.openapi.security.OpenApiSecurityErrors;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OpenApiPingController {
    @GetMapping("/openapi/v1/ping")
    public ApiResponse<Ping> ping(
            @RequestAttribute(
                    value = OpenApiAuthentication.REQUEST_ATTRIBUTE,
                    required = false
            ) Object value,
            HttpServletRequest request
    ) {
        if (!(value instanceof OpenApiAuthentication authentication)) {
            throw OpenApiSecurityErrors.authenticationRequired();
        }
        var application = authentication.application();
        var credential = authentication.credential();
        return ApiResponse.success(
                new Ping(
                        Long.toString(application.id()),
                        Long.toString(application.systemId()),
                        Long.toString(application.tenantId()),
                        Long.toString(application.serviceMemberId()),
                        credential.credentialVersion()
                ),
                attribute(request, WebRequestAttributes.REQUEST_ID),
                attribute(request, WebRequestAttributes.TRACE_ID)
        );
    }

    private static String attribute(HttpServletRequest request, String name) {
        var value = request.getAttribute(name);
        return value == null ? "" : String.valueOf(value);
    }

    public record Ping(
            String applicationId,
            String systemId,
            String tenantId,
            String memberId,
            int credentialVersion
    ) {
    }
}
