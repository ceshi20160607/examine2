package com.unique.unexamine.authentication.manage;

import com.unique.unexamine.shared.manage.web.ApiResult;
import com.unique.unexamine.platform.manage.registration.RegistrationRequest;
import com.unique.unexamine.platform.manage.registration.RegistrationResult;
import com.unique.unexamine.platform.manage.registration.RegistrationService;
import com.unique.unexamine.shared.manage.web.DomainException;
import com.unique.unexamine.shared.manage.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.Map;
import java.io.IOException;

@RestController
@RequestMapping("/api/auth")
public class AuthenticationController {
    private final AuthenticationService authenticationService;
    private final RegistrationService registrationService;
    private final EnterpriseSsoService enterpriseSsoService;
    private final PasswordResetService passwordResetService;
    private final PasswordResetDelivery passwordResetDelivery;

    public AuthenticationController(
            AuthenticationService authenticationService,
            RegistrationService registrationService,
            EnterpriseSsoService enterpriseSsoService,
            PasswordResetService passwordResetService,
            PasswordResetDelivery passwordResetDelivery) {
        this.authenticationService = authenticationService;
        this.registrationService = registrationService;
        this.enterpriseSsoService = enterpriseSsoService;
        this.passwordResetService = passwordResetService;
        this.passwordResetDelivery = passwordResetDelivery;
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

    /** 匿名找回请求不返回账号是否存在，也不返回一次性令牌。 */
    @GetMapping("/password-reset/availability")
    public ApiResult<Map<String, Boolean>> passwordResetAvailability() {
        return ApiResult.ok(Map.of("available", passwordResetDelivery.isAvailable()));
    }

    /** 匿名找回请求不返回账号是否存在，也不返回一次性令牌。 */
    @PostMapping("/password-reset/request")
    public ApiResult<PasswordResetRequestView> requestPasswordReset(
            @Valid @RequestBody PasswordResetRequest request, HttpServletRequest servletRequest) {
        return ApiResult.ok(passwordResetService.request(request, TraceIdFilter.current(servletRequest)));
    }

    /** 找回链接的一次性令牌在服务端校验、消费并撤销全部旧会话。 */
    @PostMapping("/password-reset/confirm")
    public ApiResult<Void> confirmPasswordReset(
            @Valid @RequestBody PasswordResetConfirmRequest request, HttpServletRequest servletRequest) {
        passwordResetService.confirm(request, TraceIdFilter.current(servletRequest));
        return ApiResult.ok(null);
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

    /** LDAP 登录使用独立凭据入口，避免把密码放进浏览器跳转地址。 */
    @PostMapping("/sso/{providerCode}/directory/login")
    public ApiResult<SessionTokens> loginDirectory(@PathVariable String providerCode,
            @Valid @RequestBody DirectoryLoginRequest request, HttpServletRequest servletRequest) {
        return ApiResult.ok(enterpriseSsoService.loginDirectory(providerCode, request,
                TraceIdFilter.current(servletRequest), servletRequest.getRemoteAddr(),
                servletRequest.getHeader("User-Agent")));
    }

    /** SAML 浏览器 POST 响应先由后端验签，再以短效一次性交接码返回前端登录页。 */
    @PostMapping(value = "/sso/{providerCode}/saml/acs", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public void acceptSaml(
            @PathVariable String providerCode,
            @RequestParam("SAMLResponse") String samlResponse,
            @RequestParam("RelayState") String relayState,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) throws IOException {
        String callback = enterpriseSsoService.acceptSaml(providerCode, samlResponse, relayState,
                TraceIdFilter.current(servletRequest), servletRequest.getRemoteAddr(),
                servletRequest.getHeader("User-Agent"));
        servletResponse.sendRedirect(callback);
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
