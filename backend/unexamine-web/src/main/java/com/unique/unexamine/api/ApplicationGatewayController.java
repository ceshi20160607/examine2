package com.unique.unexamine.api;

import com.unique.unexamine.core.gateway.ApplicationAuthorization;
import com.unique.unexamine.core.gateway.ApplicationAuthorizationRequest;
import com.unique.unexamine.core.gateway.ApplicationConfig;
import com.unique.unexamine.core.gateway.FlowRun;
import com.unique.unexamine.core.gateway.FlowRunRequest;
import com.unique.unexamine.service.AuthService;
import com.unique.unexamine.service.FlowApplicationGatewayService;
import java.util.List;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin(origins = {"http://127.0.0.1:5173", "http://localhost:5173", "null"}, allowedHeaders = "*")
@RestController
public class ApplicationGatewayController {
    private final FlowApplicationGatewayService gatewayService;
    private final AuthService authService;

    public ApplicationGatewayController(FlowApplicationGatewayService gatewayService, AuthService authService) {
        this.gatewayService = gatewayService;
        this.authService = authService;
    }

    @GetMapping("/api/applications")
    public List<ApplicationConfig> applications(@RequestHeader(name = "Authorization", required = false) String authorization) {
        authService.profile(authorization);
        return gatewayService.applications();
    }

    @GetMapping("/api/applications/{appCode}")
    public ApplicationConfig application(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable String appCode
    ) {
        authService.profile(authorization);
        return gatewayService.application(appCode);
    }

    @PostMapping("/api/applications/{appCode}/authorizations")
    public ApplicationAuthorization authorize(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable String appCode,
            @RequestBody ApplicationAuthorizationRequest request
    ) {
        authService.profile(authorization);
        return gatewayService.authorize(appCode, request);
    }

    @PostMapping("/openapi/applications/{appCode}/flows/{flowCode}/invoke")
    public FlowRun openInvoke(
            @PathVariable String appCode,
            @PathVariable String flowCode,
            @RequestBody FlowRunRequest request
    ) {
        return gatewayService.invokeAuthorizedFlow(appCode, flowCode, request);
    }
}