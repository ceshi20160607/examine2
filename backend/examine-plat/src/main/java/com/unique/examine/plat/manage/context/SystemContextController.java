package com.unique.examine.plat.manage.context;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.plat.manage.context.ContextModels.SwitchOption;
import com.unique.examine.plat.manage.context.ContextModels.SystemSwitchContext;
import com.unique.examine.plat.manage.context.ContextModels.SystemSwitchRequest;
import com.unique.examine.plat.manage.context.ContextModels.TenantSwitchContext;
import com.unique.examine.plat.manage.context.ContextModels.TenantSwitchRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * System and tenant context API controller.
 */
@RestController
public class SystemContextController {

    private final SystemContextService contextService;

    public SystemContextController(SystemContextService contextService) {
        this.contextService = contextService;
    }

    @GetMapping("/api/v1/platform/system-switch/options")
    public ApiResponse<List<SwitchOption>> options() {
        return ApiResponse.success(contextService.options());
    }

    @PostMapping("/api/v1/platform/system-switch")
    public ApiResponse<SystemSwitchContext> switchSystem(@RequestBody SystemSwitchRequest request) {
        return ApiResponse.success(contextService.switchSystem(request));
    }

    @GetMapping("/api/v1/context/current-system")
    public ApiResponse<SystemSwitchContext> currentSystem() {
        return ApiResponse.success(contextService.currentSystem());
    }

    @PostMapping("/api/v1/systems/{systemId}/tenant-switch")
    public ApiResponse<TenantSwitchContext> switchTenant(@PathVariable String systemId,
                                                         @RequestBody TenantSwitchRequest request) {
        return ApiResponse.success(contextService.switchTenant(systemId, request));
    }
}
