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
@RequestMapping("/api/admin/system/operations/security-performance")
public class OperationsSecurityController {
    private final OperationsSecurityService service;

    public OperationsSecurityController(OperationsSecurityService service) {
        this.service = service;
    }

    @GetMapping
    @RequirePermission(resourceType = "OPERATIONS", resourceCode = "SECURITY", actionCode = "READ")
    public ApiResult<OperationsSecurityModels.Overview> overview() {
        return ApiResult.ok(service.overview(AuthenticationContextHolder.require()));
    }

    @PostMapping("/secret-refs")
    @RequirePermission(resourceType = "OPERATIONS", resourceCode = "SECRET", actionCode = "ROTATE")
    public ApiResult<OperationsSecurityModels.OneTimeSecretIssue> createSecret(
            @Valid @RequestBody OperationsSecurityModels.CreateSecretRequest input, HttpServletRequest request) {
        return ApiResult.ok(service.createSecret(AuthenticationContextHolder.require(), input,
                TraceIdFilter.current(request)));
    }

    @PostMapping("/secret-refs/{secretRefId}/rotations")
    @RequirePermission(resourceType = "OPERATIONS", resourceCode = "SECRET", actionCode = "ROTATE")
    public ApiResult<OperationsSecurityModels.OneTimeSecretIssue> prepareRotation(
            @PathVariable long secretRefId,
            @Valid @RequestBody OperationsSecurityModels.PrepareRotationRequest input,
            HttpServletRequest request) {
        return ApiResult.ok(service.prepareRotation(AuthenticationContextHolder.require(), secretRefId, input,
                TraceIdFilter.current(request)));
    }

    @PostMapping("/rotations/{rotationId}/activate")
    @RequirePermission(resourceType = "OPERATIONS", resourceCode = "SECRET", actionCode = "ROTATE")
    public ApiResult<OperationsSecurityModels.RotationView> activateRotation(
            @PathVariable long rotationId,
            @Valid @RequestBody OperationsSecurityModels.ActivateRotationRequest input,
            HttpServletRequest request) {
        return ApiResult.ok(service.activateRotation(AuthenticationContextHolder.require(), rotationId, input,
                TraceIdFilter.current(request)));
    }

    @PostMapping("/security-runs")
    @RequirePermission(resourceType = "OPERATIONS", resourceCode = "SECURITY", actionCode = "MANAGE")
    public ApiResult<OperationsSecurityModels.VerificationRunView> runSecurity(
            @Valid @RequestBody OperationsSecurityModels.SecurityRunRequest input, HttpServletRequest request) {
        return ApiResult.ok(service.runSecurity(AuthenticationContextHolder.require(), input,
                TraceIdFilter.current(request)));
    }

    @PostMapping("/performance-runs")
    @RequirePermission(resourceType = "OPERATIONS", resourceCode = "PERFORMANCE", actionCode = "MANAGE")
    public ApiResult<OperationsSecurityModels.VerificationRunView> runPerformance(
            @Valid @RequestBody OperationsSecurityModels.PerformanceRunRequest input, HttpServletRequest request) {
        return ApiResult.ok(service.runPerformance(AuthenticationContextHolder.require(), input,
                TraceIdFilter.current(request)));
    }
}
