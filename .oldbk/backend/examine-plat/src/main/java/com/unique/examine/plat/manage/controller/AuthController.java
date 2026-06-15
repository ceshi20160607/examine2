package com.unique.examine.plat.manage.controller;

import java.util.Map;

import com.unique.examine.core.error.CommonErrorCode;
import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.plat.manage.bo.LoginBO;
import com.unique.examine.plat.manage.bo.RefreshTokenBO;
import com.unique.examine.plat.manage.bo.RegisterBO;
import com.unique.examine.plat.manage.service.AuthSessionService;
import com.unique.examine.plat.manage.vo.AuthAccountVO;
import com.unique.examine.plat.manage.vo.AuthTokenVO;
import com.unique.examine.plat.manage.vo.CurrentUserVO;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证会话接口。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthSessionService authSessionService;

    /**
     * 注册平台账号。
     *
     * @param registerBO 注册入参
     * @return 注册账号
     */
    @Operation(summary = "注册平台账号")
    @PostMapping("/register")
    public AuthAccountVO register(@Valid @RequestBody RegisterBO registerBO) {
        return authSessionService.register(registerBO);
    }

    /**
     * 平台账号登录。
     *
     * @param loginBO 登录入参
     * @return token 信息
     */
    @Operation(summary = "平台账号登录")
    @PostMapping("/login")
    public AuthTokenVO login(@Valid @RequestBody LoginBO loginBO) {
        return authSessionService.login(loginBO);
    }

    /**
     * 刷新 accessToken。
     *
     * @param refreshTokenBO 刷新入参
     * @return token 信息
     */
    @Operation(summary = "刷新 accessToken")
    @PostMapping("/refresh")
    public AuthTokenVO refresh(@Valid @RequestBody RefreshTokenBO refreshTokenBO) {
        return authSessionService.refresh(refreshTokenBO);
    }

    /**
     * 退出当前会话。
     *
     * @param authorization Authorization 请求头
     * @return 退出结果
     */
    @Operation(summary = "退出当前会话")
    @PostMapping("/logout")
    public Map<String, Boolean> logout(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        authSessionService.logout(resolveBearer(authorization));
        return Map.of("loggedOut", true);
    }

    /**
     * 查询当前登录用户。
     *
     * @param authorization Authorization 请求头
     * @return 当前用户
     */
    @Operation(summary = "查询当前登录用户")
    @GetMapping("/me")
    public CurrentUserVO me(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return authSessionService.me(resolveBearer(authorization));
    }

    private static String resolveBearer(String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        String token = authorization.substring(BEARER_PREFIX.length()).strip();
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        return token;
    }
}
