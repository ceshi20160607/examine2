package com.unique.examine.web.vnext.auth;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.plat.vnext.manage.auth.AccountContext;
import com.unique.examine.plat.vnext.manage.auth.VNextSessionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("vnext")
@RequestMapping("/api/v1/me")
public class VNextMeController {
    private final VNextSessionService sessionService;

    public VNextMeController(VNextSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping("/context")
    public ApiResponse<AccountContext> context(HttpServletRequest request) {
        return ApiResponse.success(
                sessionService.current(VNextSessionCookieSupport.value(
                        request, VNextSessionCookieSupport.ACCESS_COOKIE
                )),
                VNextWebRequestSupport.requestId(request),
                VNextWebRequestSupport.traceId(request)
        );
    }
}
