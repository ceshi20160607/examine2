package com.unique.unexamine.operations.manage;

import com.unique.unexamine.authentication.manage.AuthenticationContextHolder;
import com.unique.unexamine.authorization.manage.RequirePermission;
import com.unique.unexamine.shared.manage.web.ApiResult;
import com.unique.unexamine.shared.manage.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/system/operations/deployments")
@RequirePermission(resourceType = "OPERATIONS", resourceCode = "DEPLOY", actionCode = "MANAGE")
public class OperationsDeploymentController {
    private final OperationsDeploymentService service;

    public OperationsDeploymentController(OperationsDeploymentService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResult<OperationsDeploymentModels.Overview> overview() {
        return ApiResult.ok(service.overview(AuthenticationContextHolder.require()));
    }

    @PostMapping("/releases")
    public ApiResult<OperationsDeploymentModels.ReleaseView> register(
            @Valid @RequestBody OperationsDeploymentModels.ReleaseRequest input,
            HttpServletRequest request) {
        return ApiResult.ok(service.register(AuthenticationContextHolder.require(), input, TraceIdFilter.current(request)));
    }

    @PostMapping("/execute")
    public ApiResult<OperationsDeploymentModels.DeploymentView> execute(
            @Valid @RequestBody OperationsDeploymentModels.DeployRequest input,
            HttpServletRequest request) {
        return ApiResult.ok(service.execute(AuthenticationContextHolder.require(), input, TraceIdFilter.current(request)));
    }

    @PostMapping("/{deploymentId}/rollback")
    public ApiResult<OperationsDeploymentModels.DeploymentView> rollback(
            @PathVariable Long deploymentId,
            @Valid @RequestBody OperationsDeploymentModels.RollbackRequest input,
            HttpServletRequest request) {
        return ApiResult.ok(service.rollback(AuthenticationContextHolder.require(), deploymentId, input,
                TraceIdFilter.current(request)));
    }
}
