package com.unique.examine.plat.manage.controller;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.plat.api.AuthenticatedSession;
import com.unique.examine.plat.manage.dto.PlatformAdminModels.PageResult;
import com.unique.examine.plat.manage.dto.PlatformAdminModels.SystemTombstoneView;
import com.unique.examine.plat.manage.dto.PlatformAdminModels.SystemView;
import com.unique.examine.plat.manage.dto.PlatformAdminModels.TombstoneRestore;
import com.unique.examine.plat.manage.service.PlatformSystemAdminService;
import com.unique.examine.plat.manage.service.SessionGuard;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/platform/admin")
public class PlatformSystemTombstoneController {
    private final PlatformSystemAdminService systems;

    public PlatformSystemTombstoneController(PlatformSystemAdminService systems) {
        this.systems = systems;
    }

    @GetMapping("/system-tombstones")
    public ApiResponse<PageResult<SystemTombstoneView>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String keyword,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request
    ) {
        SessionGuard.requirePlatform(sessionValue, "platform.system.manage");
        return success(systems.tombstones(page, size, keyword), request);
    }

    @PostMapping("/systems/{systemId}/tombstone:restore")
    public ApiResponse<SystemView> restore(
            @PathVariable String systemId,
            @RequestBody TombstoneRestore body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request
    ) {
        var session = SessionGuard.requirePlatform(sessionValue, "platform.system.manage");
        return success(systems.restoreTombstone(session, systemId, body, idempotencyKey,
                ControllerSupport.client(request)), request);
    }

    private static <T> ApiResponse<T> success(T data, HttpServletRequest request) {
        return ApiResponse.success(data, ControllerSupport.requestId(request), ControllerSupport.traceId(request));
    }
}
