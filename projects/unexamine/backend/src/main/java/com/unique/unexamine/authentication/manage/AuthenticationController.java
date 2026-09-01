package com.unique.unexamine.authentication.manage;

import com.unique.unexamine.shared.manage.web.ApiResult;
import com.unique.unexamine.platform.manage.registration.RegistrationRequest;
import com.unique.unexamine.platform.manage.registration.RegistrationResult;
import com.unique.unexamine.platform.manage.registration.RegistrationService;
import com.unique.unexamine.shared.manage.web.DomainException;
import com.unique.unexamine.shared.manage.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthenticationController {
    private final AuthenticationService authenticationService;
    private final RegistrationService registrationService;
    private final EnterpriseSsoService enterpriseSsoService;

    public AuthenticationController(
            AuthenticationService authenticationService,
            RegistrationService registrationService,
            EnterpriseSsoService enterpriseSsoService) {
        this.authenticationService = authenticationService;
        this.registrationService = registrationService;
        this.enterpriseSsoService = enterpriseSsoService;
    }

    @PostMapping("/login")
    public ApiResult<SessionTokens> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        return ApiResult.ok(authenticationService.login(
                request.username(), request.password().toCharArray(), TraceIdFilter.current(servletRequest)));
    }

    @PostMapping("/register")
    public ApiResult<RegistrationResult> register(
            @Valid @RequestBody RegistrationRequest request,
            HttpServletRequest servletRequest) {
        return ApiResult.ok(registrationService.register(request, TraceIdFilter.current(servletRequest)));
    }

    @PostMapping("/refresh")
    public ApiResult<SessionTokens> refresh(
            @Valid @RequestBody RefreshRequest request,
            HttpServletRequest servletRequest) {
        return ApiResult.ok(authenticationService.refresh(request.refreshToken(), TraceIdFilter.current(servletRequest)));
    }

    @GetMapping("/sso/providers")
    public ApiResult<List<SsoProviderView>> ssoProviders() {
        return ApiResult.ok(enterpriseSsoService.publishedProviders());
    }

    @PostMapping("/sso/{providerCode}/start")
    public ApiResult<SsoStartResult> startSso(
            @PathVariable String providerCode,
            HttpServletRequest servletRequest) {
        return ApiResult.ok(enterpriseSsoService.start(providerCode, TraceIdFilter.current(servletRequest)));
    }

    @PostMapping("/sso/{providerCode}/complete")
    public ApiResult<SessionTokens> completeSso(
            @PathVariable String providerCode,
            @Valid @RequestBody SsoCompleteRequest request,
            HttpServletRequest servletRequest) {
        return ApiResult.ok(enterpriseSsoService.complete(
                providerCode,
                request,
                TraceIdFilter.current(servletRequest),
                servletRequest.getRemoteAddr(),
                servletRequest.getHeader("User-Agent")));
    }

    @GetMapping("/me")
    public ApiResult<AuthenticatedContext> me() {
        return ApiResult.ok(AuthenticationContextHolder.require());
    }

    @PostMapping("/logout")
    public ApiResult<Void> logout(HttpServletRequest request) {
        authenticationService.logout(bearerToken(request), TraceIdFilter.current(request));
        return ApiResult.ok(null);
    }

    private String bearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ") || header.length() <= 7) {
            throw new DomainException("AUTHENTICATION_REQUIRED", "请先登录", HttpStatus.UNAUTHORIZED);
        }
        return header.substring(7);
    }
}
