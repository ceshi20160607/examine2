package com.unique.unexamine.authentication.manage;

import com.unique.unexamine.shared.manage.web.ApiResult;
import com.unique.unexamine.shared.manage.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account")
public class AccountSecurityController {
    private final AccountSecurityService accountSecurityService;

    public AccountSecurityController(AccountSecurityService accountSecurityService) {
        this.accountSecurityService = accountSecurityService;
    }

    @GetMapping("/profile")
    public ApiResult<AccountProfileView> profile() {
        return ApiResult.ok(accountSecurityService.profile());
    }

    @PutMapping("/profile")
    public ApiResult<AccountProfileView> updateProfile(
            @Valid @RequestBody AccountProfileUpdateRequest request,
            HttpServletRequest servletRequest) {
        return ApiResult.ok(accountSecurityService.updateProfile(request, TraceIdFilter.current(servletRequest)));
    }

    @PostMapping("/password")
    public ApiResult<AccountProfileView> changePassword(
            @Valid @RequestBody PasswordChangeRequest request,
            HttpServletRequest servletRequest) {
        return ApiResult.ok(accountSecurityService.changePassword(request, TraceIdFilter.current(servletRequest)));
    }

    @PostMapping("/sessions/{sessionId}/revoke")
    public ApiResult<Void> revokeSession(@PathVariable Long sessionId, HttpServletRequest servletRequest) {
        accountSecurityService.revokeSession(sessionId, TraceIdFilter.current(servletRequest));
        return ApiResult.ok(null);
    }
}
