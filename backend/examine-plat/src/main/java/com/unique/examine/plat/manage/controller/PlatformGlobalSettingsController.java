package com.unique.examine.plat.manage.controller;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.plat.api.AuthenticatedSession;
import com.unique.examine.plat.manage.service.SessionGuard;
import com.unique.examine.plat.manage.settings.PlatformGlobalSettingsModels;
import com.unique.examine.plat.manage.settings.PlatformGlobalSettingsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/admin/global-settings")
public class PlatformGlobalSettingsController {
    private final PlatformGlobalSettingsService settings;

    public PlatformGlobalSettingsController(PlatformGlobalSettingsService settings) {
        this.settings = settings;
    }

    @GetMapping
    public ApiResponse<PlatformGlobalSettingsModels.Settings> get(
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request
    ) {
        SessionGuard.requirePlatform(sessionValue, "platform.settings.manage");
        return success(settings.get(), request);
    }

    @PutMapping
    public ApiResponse<PlatformGlobalSettingsModels.Settings> update(
            @RequestBody PlatformGlobalSettingsModels.Update body,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request
    ) {
        var session = SessionGuard.requirePlatform(sessionValue, "platform.settings.manage");
        return success(settings.update(session, body, ControllerSupport.client(request)), request);
    }

    private static <T> ApiResponse<T> success(T data, HttpServletRequest request) {
        return ApiResponse.success(data, ControllerSupport.requestId(request), ControllerSupport.traceId(request));
    }
}
