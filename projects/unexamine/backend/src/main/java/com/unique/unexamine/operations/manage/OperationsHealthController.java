package com.unique.unexamine.operations.manage;

import com.unique.unexamine.authentication.manage.AuthenticationContextHolder;
import com.unique.unexamine.authorization.manage.RequirePermission;
import com.unique.unexamine.shared.manage.web.ApiResult;
import com.unique.unexamine.shared.manage.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/system/operations")
@RequirePermission(resourceType = "CONFIG", resourceCode = "SYSTEM", actionCode = "MANAGE")
public class OperationsHealthController {
    private final OperationsHealthService service;

    public OperationsHealthController(OperationsHealthService service) {
        this.service = service;
    }

    @GetMapping("/health")
    public ApiResult<OperationsHealthModels.HealthView> latest() {
        return ApiResult.ok(service.latest(AuthenticationContextHolder.require()));
    }

    @PostMapping("/health/recheck")
    public ApiResult<OperationsHealthModels.HealthView> recheck(HttpServletRequest request) {
        return ApiResult.ok(service.recheck(AuthenticationContextHolder.require(), TraceIdFilter.current(request)));
    }

    @GetMapping("/observability")
    public ApiResult<OperationsHealthModels.ObservationView> observe(@RequestParam String requestId) {
        return ApiResult.ok(service.observe(AuthenticationContextHolder.require(), requestId));
    }
}
