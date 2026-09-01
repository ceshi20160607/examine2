package com.unique.unexamine.platform.manage.configuration;

import com.unique.unexamine.authentication.manage.AuthenticationContextHolder;
import com.unique.unexamine.authorization.manage.RequirePermission;
import com.unique.unexamine.foundation.manage.configuration.ContextSettingCategory;
import com.unique.unexamine.foundation.manage.configuration.ContextSettingManagementService;
import com.unique.unexamine.foundation.manage.configuration.ContextSettingView;
import com.unique.unexamine.foundation.manage.configuration.SaveContextSettingRequest;
import com.unique.unexamine.shared.manage.web.ApiResult;
import com.unique.unexamine.shared.manage.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/platform/configurations")
@RequirePermission(resourceType = "PLATFORM", resourceCode = "CONFIGURATION", actionCode = "MANAGE")
public class PlatformConfigurationController {
    private final ContextSettingManagementService settingService;

    public PlatformConfigurationController(ContextSettingManagementService settingService) {
        this.settingService = settingService;
    }

    @GetMapping("/catalog")
    public ApiResult<List<ContextSettingCategory>> catalog() {
        return ApiResult.ok(settingService.platformCatalog());
    }

    @GetMapping
    public ApiResult<List<ContextSettingView>> list() {
        return ApiResult.ok(settingService.listPlatform(AuthenticationContextHolder.require()));
    }

    @PostMapping
    public ApiResult<ContextSettingView> save(
            @Valid @RequestBody SaveContextSettingRequest input,
            HttpServletRequest request) {
        return ApiResult.ok(settingService.savePlatform(AuthenticationContextHolder.require(), input,
                TraceIdFilter.current(request)));
    }
}
