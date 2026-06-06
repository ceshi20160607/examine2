package com.unique.examine.manage.controller;

import com.unique.examine.manage.bo.AuthLoginBO;
import com.unique.examine.manage.bo.AuthRegisterBO;
import com.unique.examine.manage.service.AuthManageService;
import com.unique.examine.manage.vo.ApiResponse;
import com.unique.examine.manage.vo.AuthTokenVO;
import com.unique.examine.manage.vo.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthManageService authManageService;

    @PostMapping("/register")
    public ApiResponse<AuthTokenVO> register(@Valid @RequestBody AuthRegisterBO bo) { return ApiResponse.ok(authManageService.register(bo)); }

    @PostMapping("/login")
    public ApiResponse<AuthTokenVO> login(@Valid @RequestBody AuthLoginBO bo) { return ApiResponse.ok(authManageService.login(bo)); }

    @PostMapping("/refresh")
    public ApiResponse<AuthTokenVO> refresh() { return ApiResponse.ok(authManageService.refresh()); }

    @GetMapping("/me")
    public ApiResponse<UserVO> me() { return ApiResponse.ok(authManageService.me()); }

    @PostMapping("/logout")
    public ApiResponse<Boolean> logout() { return ApiResponse.ok(Boolean.TRUE); }
}
