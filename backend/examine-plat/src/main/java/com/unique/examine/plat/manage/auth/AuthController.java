package com.unique.examine.plat.manage.auth;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.plat.manage.auth.AuthModels.AuthActionResult;
import com.unique.examine.plat.manage.auth.AuthModels.LoginRequest;
import com.unique.examine.plat.manage.auth.AuthModels.LoginResponse;
import com.unique.examine.plat.manage.auth.AuthModels.PasswordResetConfirmRequest;
import com.unique.examine.plat.manage.auth.AuthModels.PasswordResetRequest;
import com.unique.examine.plat.manage.auth.AuthModels.PasswordResetResponse;
import com.unique.examine.plat.manage.auth.AuthModels.RegisterWithSystemRequest;
import com.unique.examine.plat.manage.auth.AuthModels.RegisterWithSystemResponse;
import com.unique.examine.plat.manage.auth.AuthModels.TokenRefreshRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication API controller.
 */
@RestController
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/api/v1/auth/login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @PostMapping("/api/v1/auth/logout")
    public ApiResponse<AuthActionResult> logout() {
        return ApiResponse.success(authService.logout());
    }

    @PostMapping("/api/v1/auth/token/refresh")
    public ApiResponse<LoginResponse> refresh(@RequestBody TokenRefreshRequest request) {
        return ApiResponse.success(authService.refresh(request));
    }

    @PostMapping("/api/v1/auth/register-with-system")
    public ApiResponse<RegisterWithSystemResponse> registerWithSystem(@RequestBody RegisterWithSystemRequest request) {
        return ApiResponse.success(authService.registerWithSystem(request));
    }

    @PostMapping("/api/v1/auth/password-reset/request")
    public ApiResponse<PasswordResetResponse> requestPasswordReset(@RequestBody PasswordResetRequest request) {
        return ApiResponse.success(authService.requestPasswordReset(request));
    }

    @PostMapping("/api/v1/auth/password-reset/confirm")
    public ApiResponse<AuthActionResult> confirmPasswordReset(@RequestBody PasswordResetConfirmRequest request) {
        return ApiResponse.success(authService.confirmPasswordReset(request));
    }
}
