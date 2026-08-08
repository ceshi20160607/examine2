package com.unique.examine.plat.manage.controller;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.plat.api.AuthenticatedSession;
import com.unique.examine.plat.identity.EnterpriseIdentityService;
import com.unique.examine.plat.identity.IdentityApi;
import com.unique.examine.plat.manage.service.SessionGuard;
import com.unique.examine.plat.manage.service.AnonymousRateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;

@RestController
@RequestMapping("/api/v1/auth/sso")
public class EnterpriseSsoController {
    private final EnterpriseIdentityService identity;
    private final SessionCookieSupport cookies;
    private final AnonymousRateLimitService rateLimits;

    public EnterpriseSsoController(EnterpriseIdentityService identity, SessionCookieSupport cookies,
                                   AnonymousRateLimitService rateLimits) {
        this.identity = identity;
        this.cookies = cookies;
        this.rateLimits = rateLimits;
    }

    @GetMapping("/start")
    public ApiResponse<IdentityApi.LoginStart> start(
            @RequestParam String providerCode,
            @RequestParam(required = false) String systemId,
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String redirectUri,
            HttpServletRequest request) {
        return success(identity.start(providerCode, systemId, tenantId, redirectUri), request);
    }

    @PostMapping("/callback")
    public ApiResponse<Object> callback(@RequestBody IdentityApi.CallbackCommand body,
                                        HttpServletRequest request, HttpServletResponse response) {
        var completion = identity.callback(body, ControllerSupport.client(request));
        return complete(completion, request, response);
    }

    @PostMapping("/directory")
    public ApiResponse<Object> directory(@RequestBody IdentityApi.DirectoryLoginCommand body,
                                         HttpServletRequest request, HttpServletResponse response) {
        var client = ControllerSupport.client(request);
        rateLimits.check("directory-login", client.remoteAddress(), 10);
        return complete(identity.directoryLogin(body, client), request, response);
    }

    @PostMapping("/mfa:verify")
    public ApiResponse<Object> verify(@RequestBody IdentityApi.MfaVerifyCommand body,
                                      HttpServletRequest request, HttpServletResponse response) {
        var completion = identity.verify(body, ControllerSupport.client(request));
        return complete(completion, request, response);
    }

    @PostMapping("/mfa:enroll")
    public ApiResponse<Object> enroll(@RequestBody IdentityApi.MfaEnrollmentCommand body,
                                      HttpServletRequest request, HttpServletResponse response) {
        var completion = identity.enrollFromChallenge(body, ControllerSupport.client(request));
        cookies.write(response, completion.issued());
        var value = new LinkedHashMap<String, Object>();
        value.put("status", "AUTHENTICATED");
        value.put("enrollment", completion.enrollment());
        value.put("session", completion.issued().result());
        return success(value, request);
    }

    @PostMapping("/mfa:enroll-authenticated")
    public ApiResponse<IdentityApi.MfaEnrollmentView> enrollAuthenticated(
            @RequestBody IdentityApi.MfaEnrollmentCommand body,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request) {
        var session = SessionGuard.require(sessionValue);
        return success(identity.enrollAuthenticated(session, body, ControllerSupport.client(request)), request);
    }

    private ApiResponse<Object> complete(EnterpriseIdentityService.Completion completion,
                                         HttpServletRequest request, HttpServletResponse response) {
        var value = new LinkedHashMap<String, Object>();
        if (completion.issued() != null) {
            cookies.write(response, completion.issued());
            value.put("status", "AUTHENTICATED");
            value.put("session", completion.issued().result());
            value.put("redirectUri", completion.redirectUri());
        } else {
            value.put("status", "MFA_REQUIRED");
            value.put("challenge", completion.challenge());
            value.put("enrollmentRequired", completion.enrollmentRequired());
        }
        return success(value, request);
    }

    private static <T> ApiResponse<T> success(T value, HttpServletRequest request) {
        return ApiResponse.success(value, ControllerSupport.requestId(request), ControllerSupport.traceId(request));
    }
}
