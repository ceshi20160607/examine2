package com.unique.examine.web.controller;

import com.unique.examine.core.security.AuthContextHolder;
import com.unique.examine.core.web.ApiResult;
import com.unique.examine.plat.entity.po.PlatAccount;
import com.unique.examine.plat.manage.PlatAuthManageService;
import com.unique.examine.web.dto.LoginBody;
import com.unique.examine.web.dto.RegisterBody;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "平台认证")
@RestController
@RequestMapping("/v1/platform/auth")
public class AuthController {

    @Autowired
    private PlatAuthManageService authService;

    @Operation(summary = "注册")
    @PostMapping("/register")
    public ApiResult<PlatAccount> register(@Valid @RequestBody RegisterBody body) {
        return ApiResult.ok(authService.register(body.getUsername(), body.getPassword()));
    }

    @Operation(summary = "登录")
    @PostMapping("/login")
    public ApiResult<Map<String, Object>> login(@Valid @RequestBody LoginBody body, HttpServletRequest request) {
        PlatAuthManageService.LoginResult r = authService.login(body.getUsername(), body.getPassword(),
                request.getRemoteAddr(), request.getHeader("User-Agent"));
        return ApiResult.ok(Map.of(
                "token", r.token(),
                "account", r.account()
        ));
    }

    @Operation(summary = "刷新 token（旧 token 作废，换发新 token；会话内 systemId/tenantId 不变）")
    @PostMapping("/refresh")
    public ApiResult<Map<String, String>> refresh(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String token = null;
        if (authorization != null && authorization.startsWith("Bearer ")) {
            token = authorization.substring(7).trim();
        }
        String newToken = authService.refreshToken(token != null ? token : "");
        return ApiResult.ok(Map.of("token", newToken));
    }

    @Operation(summary = "登出")
    @PostMapping("/logout")
    public ApiResult<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String token = null;
        if (authorization != null && authorization.startsWith("Bearer ")) {
            token = authorization.substring(7).trim();
        }
        authService.logout(token != null ? token : "");
        return ApiResult.ok();
    }

    @Operation(summary = "当前用户")
    @GetMapping("/me")
    public ApiResult<PlatAccount> me() {
        Long platId = AuthContextHolder.getPlatId();
        return ApiResult.ok(authService.requireAccount(platId));
    }
}
