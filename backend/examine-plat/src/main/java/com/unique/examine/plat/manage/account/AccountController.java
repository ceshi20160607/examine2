package com.unique.examine.plat.manage.account;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.PageRequest;
import com.unique.examine.core.api.PageResult;
import com.unique.examine.plat.manage.account.AccountModels.AccountActionResult;
import com.unique.examine.plat.manage.account.AccountModels.AccountProfileVO;
import com.unique.examine.plat.manage.account.AccountModels.LoginLogVO;
import com.unique.examine.plat.manage.account.AccountModels.PasswordUpdateRequest;
import com.unique.examine.plat.manage.account.AccountModels.ProfileUpdateRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Account profile API controller.
 */
@RestController
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/api/v1/account/me")
    public ApiResponse<AccountProfileVO> me() {
        return ApiResponse.success(accountService.currentProfile());
    }

    @PatchMapping("/api/v1/account/me/profile")
    public ApiResponse<AccountActionResult> updateProfile(@RequestBody ProfileUpdateRequest request) {
        return ApiResponse.success(accountService.updateProfile(request));
    }

    @PatchMapping("/api/v1/account/me/password")
    public ApiResponse<AccountActionResult> updatePassword(@RequestBody PasswordUpdateRequest request) {
        return ApiResponse.success(accountService.updatePassword(request));
    }

    @GetMapping("/api/v1/account/me/login-logs")
    public ApiResponse<PageResult<LoginLogVO>> loginLogs(@RequestParam(defaultValue = "1") int pageNo,
                                                         @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(accountService.loginLogs(new PageRequest(pageNo, pageSize, null,
                List.of(), List.of())));
    }
}
