package com.unique.examine.plat.manage.platformflowapp;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.PageRequest;
import com.unique.examine.core.api.PageResult;
import com.unique.examine.plat.manage.platformflowapp.PlatformFlowApplicationModels.PlatformAuthorizationActionRequest;
import com.unique.examine.plat.manage.platformflowapp.PlatformFlowApplicationModels.PlatformAuthorizationActionResult;
import com.unique.examine.plat.manage.platformflowapp.PlatformFlowApplicationModels.PlatformAuthorizationQuery;
import com.unique.examine.plat.manage.platformflowapp.PlatformFlowApplicationModels.PlatformAuthorizationSaveRequest;
import com.unique.examine.plat.manage.platformflowapp.PlatformFlowApplicationModels.PlatformAuthorizationView;
import com.unique.examine.plat.manage.platformflowapp.PlatformFlowApplicationModels.PlatformFlowActionRequest;
import com.unique.examine.plat.manage.platformflowapp.PlatformFlowApplicationModels.PlatformFlowQuery;
import com.unique.examine.plat.manage.platformflowapp.PlatformFlowApplicationModels.PlatformFlowRunResult;
import com.unique.examine.plat.manage.platformflowapp.PlatformFlowApplicationModels.PlatformFlowSaveRequest;
import com.unique.examine.plat.manage.platformflowapp.PlatformFlowApplicationModels.PlatformFlowView;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Platform Flow and Application authorization persistence API controller.
 */
@RestController
public class PlatformFlowApplicationController {

    private final PlatformFlowApplicationService service;

    public PlatformFlowApplicationController(PlatformFlowApplicationService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/platform/flows")
    public ApiResponse<PageResult<PlatformFlowView>> flows(@RequestParam(defaultValue = "1") int pageNo,
                                                           @RequestParam(defaultValue = "20") int pageSize,
                                                           PlatformFlowQuery query) {
        return ApiResponse.success(service.flows(new PageRequest(pageNo, pageSize, null, List.of(), List.of()), query));
    }

    @PostMapping("/api/v1/platform/flows")
    public ApiResponse<PlatformFlowView> createFlow(@RequestBody PlatformFlowSaveRequest request) {
        return ApiResponse.success(service.createFlow(request));
    }

    @GetMapping("/api/v1/platform/flows/{flowId}")
    public ApiResponse<PlatformFlowView> flowDetail(@PathVariable String flowId) {
        return ApiResponse.success(service.flowDetail(flowId));
    }

    @PatchMapping("/api/v1/platform/flows/{flowId}")
    public ApiResponse<PlatformFlowView> updateFlow(@PathVariable String flowId,
                                                    @RequestBody PlatformFlowSaveRequest request) {
        return ApiResponse.success(service.updateFlow(flowId, request));
    }

    @PostMapping("/api/v1/platform/flows/{flowId}/run-check")
    public ApiResponse<PlatformFlowRunResult> runCheck(@PathVariable String flowId,
                                                       @RequestBody(required = false) PlatformFlowActionRequest request) {
        return ApiResponse.success(service.runFlowAction(flowId, "run-check", request));
    }

    @PostMapping("/api/v1/platform/flows/{flowId}/retry")
    public ApiResponse<PlatformFlowRunResult> retry(@PathVariable String flowId,
                                                    @RequestBody(required = false) PlatformFlowActionRequest request) {
        return ApiResponse.success(service.runFlowAction(flowId, "retry", request));
    }

    @PostMapping("/api/v1/platform/flows/{flowId}/compensate")
    public ApiResponse<PlatformFlowRunResult> compensate(@PathVariable String flowId,
                                                         @RequestBody(required = false) PlatformFlowActionRequest request) {
        return ApiResponse.success(service.runFlowAction(flowId, "compensate", request));
    }

    @GetMapping("/api/v1/platform/applications/authorizations")
    public ApiResponse<PageResult<PlatformAuthorizationView>> authorizations(@RequestParam(defaultValue = "1") int pageNo,
                                                                             @RequestParam(defaultValue = "20") int pageSize,
                                                                             PlatformAuthorizationQuery query) {
        return ApiResponse.success(service.authorizations(new PageRequest(pageNo, pageSize, null, List.of(), List.of()), query));
    }

    @PostMapping("/api/v1/platform/applications/authorizations")
    public ApiResponse<PlatformAuthorizationView> createAuthorization(@RequestBody PlatformAuthorizationSaveRequest request) {
        return ApiResponse.success(service.createAuthorization(request));
    }

    @GetMapping("/api/v1/platform/applications/authorizations/{authorizationId}")
    public ApiResponse<PlatformAuthorizationView> authorizationDetail(@PathVariable String authorizationId) {
        return ApiResponse.success(service.authorizationDetail(authorizationId));
    }

    @PatchMapping("/api/v1/platform/applications/authorizations/{authorizationId}")
    public ApiResponse<PlatformAuthorizationActionResult> adjustAuthorization(@PathVariable String authorizationId,
                                                                              @RequestBody PlatformAuthorizationActionRequest request) {
        return ApiResponse.success(service.adjustAuthorization(authorizationId, request));
    }

    @PostMapping("/api/v1/platform/applications/authorizations/{authorizationId}/disable")
    public ApiResponse<PlatformAuthorizationActionResult> disableAuthorization(@PathVariable String authorizationId,
                                                                               @RequestBody(required = false) PlatformAuthorizationActionRequest request) {
        return ApiResponse.success(service.disableAuthorization(authorizationId, request));
    }
}
