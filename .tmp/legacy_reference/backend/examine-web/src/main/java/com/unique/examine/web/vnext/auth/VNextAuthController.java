package com.unique.examine.web.vnext.auth;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.plat.vnext.manage.auth.AccountContext;
import com.unique.examine.plat.vnext.manage.auth.VNextLoginService;
import com.unique.examine.plat.vnext.manage.auth.VNextSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("vnext")
@RequestMapping("/api/v1/auth")
public class VNextAuthController {
    private final VNextLoginService loginService;
    private final VNextSessionService sessionService;
    private final VNextSessionCookieSupport cookieSupport;

    public VNextAuthController(
            VNextLoginService loginService,
            VNextSessionService sessionService,
            VNextSessionCookieSupport cookieSupport
    ) {
        this.loginService = loginService;
        this.sessionService = sessionService;
        this.cookieSupport = cookieSupport;
    }

    @PostMapping("/login")
    public ApiResponse<AccountContext> login(
            @Valid @RequestBody LoginRequest body,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        var issued = loginService.login(body.account(), body.password(), VNextWebRequestSupport.client(request));
        cookieSupport.write(response, issued);
        return ApiResponse.success(
                issued.accountContext(),
                VNextWebRequestSupport.requestId(request),
                VNextWebRequestSupport.traceId(request)
        );
    }

    @PostMapping("/refresh")
    public ApiResponse<AccountContext> refresh(HttpServletRequest request, HttpServletResponse response) {
        try {
            var issued = sessionService.refresh(
                    VNextSessionCookieSupport.value(request, VNextSessionCookieSupport.REFRESH_COOKIE),
                    VNextWebRequestSupport.client(request)
            );
            cookieSupport.write(response, issued);
            return ApiResponse.success(
                    issued.accountContext(),
                    VNextWebRequestSupport.requestId(request),
                    VNextWebRequestSupport.traceId(request)
            );
        } catch (BusinessException exception) {
            cookieSupport.clear(response);
            throw exception;
        }
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        sessionService.logout(
                VNextSessionCookieSupport.value(request, VNextSessionCookieSupport.ACCESS_COOKIE),
                VNextWebRequestSupport.client(request)
        );
        cookieSupport.clear(response);
        return ApiResponse.success(
                null,
                VNextWebRequestSupport.requestId(request),
                VNextWebRequestSupport.traceId(request)
        );
    }
}
