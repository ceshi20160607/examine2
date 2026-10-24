package com.unique.unexamine.plat.manage.controller;

import com.unique.unexamine.core.common.Result;
import com.unique.unexamine.plat.manage.dto.AuthManageDto.AccountView;
import com.unique.unexamine.plat.manage.dto.AuthManageDto.LoginRequest;
import com.unique.unexamine.plat.manage.dto.AuthManageDto.LoginView;
import com.unique.unexamine.plat.manage.dto.AuthManageDto.RegisterRequest;
import com.unique.unexamine.plat.manage.service.AuthManageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 平台账号和会话接口，仅转交平台业务服务。 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "平台账号")
public class AuthController {
    private final AuthManageService service;

    /** 注入平台账号业务服务。 */
    public AuthController(AuthManageService service) {
        this.service = service;
    }

    /** 注册统一平台账号。 */
    @PostMapping("/register")
    @Operation(summary = "注册平台账号")
    public Result<AccountView> register(@Valid @RequestBody RegisterRequest request) {
        return Result.ok(service.register(request));
    }

    /** 登录并取得会话凭据。 */
    @PostMapping("/login")
    @Operation(summary = "登录平台")
    public Result<LoginView> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(service.login(request));
    }

    /** 获取当前账号信息。 */
    @GetMapping("/me")
    @Operation(summary = "当前账号")
    public Result<AccountView> current(@RequestHeader(value = "Authorization", required = false) String token) {
        return Result.ok(service.current(token));
    }

    /** 撤销当前登录会话。 */
    @PostMapping("/logout")
    @Operation(summary = "退出登录")
    public Result<Void> logout(@RequestHeader(value = "Authorization", required = false) String token) {
        service.logout(token);
        return Result.ok();
    }
}
