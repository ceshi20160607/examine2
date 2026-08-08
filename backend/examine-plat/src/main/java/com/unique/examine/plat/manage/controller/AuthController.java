package com.unique.examine.plat.manage.controller;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.plat.api.AuthenticatedSession;
import com.unique.examine.plat.manage.dto.ChangePasswordRequest;
import com.unique.examine.plat.manage.dto.LoginRequest;
import com.unique.examine.plat.manage.dto.PasswordRecoveryRequest;
import com.unique.examine.plat.manage.dto.PasswordRecoveryResetRequest;
import com.unique.examine.plat.manage.dto.RegisterRequest;
import com.unique.examine.plat.manage.service.AccountPasswordService;
import com.unique.examine.plat.manage.service.AnonymousRateLimitService;
import com.unique.examine.plat.manage.service.AuthenticationService;
import com.unique.examine.plat.manage.service.PasswordRecoveryService;
import com.unique.examine.plat.manage.service.RegistrationService;
import com.unique.examine.plat.manage.service.SessionGuard;
import com.unique.examine.plat.manage.service.SessionService;
import com.unique.examine.plat.manage.vo.AuthResultVo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final RegistrationService registrationService;
    private final AuthenticationService authenticationService;
    private final AccountPasswordService accountPasswordService;
    private final PasswordRecoveryService passwordRecoveryService;
    private final SessionService sessionService;
    private final SessionCookieSupport cookieSupport;
    private final AnonymousRateLimitService rateLimitService;

    public AuthController(
            RegistrationService registrationService,
            AuthenticationService authenticationService,
            AccountPasswordService accountPasswordService,
            PasswordRecoveryService passwordRecoveryService,
            SessionService sessionService,
            SessionCookieSupport cookieSupport,
            AnonymousRateLimitService rateLimitService
    ) {
        this.registrationService = registrationService;
        this.authenticationService = authenticationService;
        this.accountPasswordService = accountPasswordService;
        this.passwordRecoveryService = passwordRecoveryService;
        this.sessionService = sessionService;
        this.cookieSupport = cookieSupport;
        this.rateLimitService = rateLimitService;
    }

    @PostMapping("/register")
    public ApiResponse<AuthResultVo> register(
            @Valid @RequestBody RegisterRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        var client = ControllerSupport.client(request);
        rateLimitService.check("register", client.remoteAddress(), 5);
        var issued = registrationService.register(body, idempotencyKey, client);
        cookieSupport.write(response, issued);
        return ApiResponse.success(issued.result(), client.requestId(), client.traceId());
    }

    @PostMapping("/login")
    public ApiResponse<AuthResultVo> login(
            @Valid @RequestBody LoginRequest body,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        var client = ControllerSupport.client(request);
        rateLimitService.check("login", client.remoteAddress(), 20);
        var issued = authenticationService.login(body, client);
        cookieSupport.write(response, issued);
        return ApiResponse.success(issued.result(), client.requestId(), client.traceId());
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthResultVo> refresh(HttpServletRequest request, HttpServletResponse response) {
        var client = ControllerSupport.client(request);
        var issued = sessionService.refresh(
                SessionCookieSupport.value(request, SessionCookieSupport.REFRESH_COOKIE),
                client
        );
        cookieSupport.write(response, issued);
        return ApiResponse.success(issued.result(), client.requestId(), client.traceId());
    }

    @PostMapping("/password:change")
    public ApiResponse<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest body,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        var session = SessionGuard.require(sessionValue);
        var client = ControllerSupport.client(request);
        accountPasswordService.changePassword(session, body, client);
        cookieSupport.clear(response);
        return ApiResponse.success(null, client.requestId(), client.traceId());
    }

    @PostMapping("/password-recovery/request")
    public ApiResponse<Void> requestPasswordRecovery(
            @Valid @RequestBody PasswordRecoveryRequest body,
            HttpServletRequest request
    ) {
        var client = ControllerSupport.client(request);
        passwordRecoveryService.request(body, client);
        return ApiResponse.success(null, client.requestId(), client.traceId());
    }

    @PostMapping("/password-recovery/reset")
    public ApiResponse<Void> resetPassword(
            @Valid @RequestBody PasswordRecoveryResetRequest body,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        var client = ControllerSupport.client(request);
        passwordRecoveryService.reset(body, client);
        cookieSupport.clear(response);
        return ApiResponse.success(null, client.requestId(), client.traceId());
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        var session = SessionGuard.require(sessionValue);
        var client = ControllerSupport.client(request);
        sessionService.logout(session, client);
        cookieSupport.clear(response);
        return ApiResponse.success(null, client.requestId(), client.traceId());
    }
}
