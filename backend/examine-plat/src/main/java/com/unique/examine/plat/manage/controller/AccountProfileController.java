package com.unique.examine.plat.manage.controller;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.plat.api.AuthenticatedSession;
import com.unique.examine.plat.manage.service.AccountProfileService;
import com.unique.examine.plat.manage.service.SessionGuard;
import com.unique.examine.plat.manage.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/account")
public class AccountProfileController {
    private final AccountProfileService profiles;
    private final SessionService sessions;
    private final SessionCookieSupport cookies;

    public AccountProfileController(AccountProfileService profiles, SessionService sessions,
                                    SessionCookieSupport cookies) {
        this.profiles = profiles;
        this.sessions = sessions;
        this.cookies = cookies;
    }

    @GetMapping("/profile")
    public ApiResponse<AccountProfileService.ProfileView> profile(
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request) {
        return ok(profiles.get(SessionGuard.require(value)), request);
    }

    @PutMapping("/profile")
    public ApiResponse<AccountProfileService.ProfileView> update(
            @RequestBody AccountProfileService.UpdateCommand command,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request) {
        return ok(profiles.update(SessionGuard.require(value), command, ControllerSupport.client(request)), request);
    }

    @GetMapping("/sessions")
    public ApiResponse<List<SessionService.OwnedSessionView>> sessions(
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request) {
        return ok(sessions.listOwnedSessions(SessionGuard.require(value)), request);
    }

    @PostMapping("/sessions/{sessionId}:revoke")
    public ApiResponse<Void> revoke(
            @PathVariable long sessionId,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request, HttpServletResponse response) {
        var current = SessionGuard.require(value);
        if (sessions.revokeOwnedSession(current, sessionId, ControllerSupport.client(request))) cookies.clear(response);
        return ok(null, request);
    }

    @PostMapping("/sessions:revoke-others")
    public ApiResponse<Void> revokeOthers(
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request) {
        sessions.revokeOtherSessions(SessionGuard.require(value), ControllerSupport.client(request));
        return ok(null, request);
    }

    private static <T> ApiResponse<T> ok(T data, HttpServletRequest request) {
        return ApiResponse.success(data, ControllerSupport.requestId(request), ControllerSupport.traceId(request));
    }
}
