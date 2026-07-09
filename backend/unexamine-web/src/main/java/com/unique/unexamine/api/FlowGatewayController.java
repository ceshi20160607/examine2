package com.unique.unexamine.api;

import com.unique.unexamine.core.gateway.FlowDefinition;
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
public class FlowGatewayController {
    private final FlowApplicationGatewayService gatewayService;
    private final AuthService authService;

    public FlowGatewayController(FlowApplicationGatewayService gatewayService, AuthService authService) {
        this.gatewayService = gatewayService;
        this.authService = authService;
    }

    @GetMapping("/api/flow/definitions")
    public List<FlowDefinition> flows(@RequestHeader(name = "Authorization", required = false) String authorization) {
        authService.profile(authorization);
        return gatewayService.flows();
    }

    @GetMapping("/api/flow/definitions/{flowCode}")
    public FlowDefinition flow(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable String flowCode
    ) {
        authService.profile(authorization);
        return gatewayService.flow(flowCode);
    }

    @PostMapping("/api/flow/definitions/{flowCode}/runs")
    public FlowRun run(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable String flowCode,
            @RequestBody FlowRunRequest request
    ) {
        authService.profile(authorization);
        return gatewayService.runFlow(flowCode, request, "INTERNAL");
    }

    @GetMapping("/api/flow/runs")
    public List<FlowRun> runs(@RequestHeader(name = "Authorization", required = false) String authorization) {
        authService.profile(authorization);
        return gatewayService.runs();
    }

    @GetMapping("/api/flow/runs/{runId}")
    public FlowRun runReadback(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable String runId
    ) {
        authService.profile(authorization);
        return gatewayService.run(runId);
    }
}
