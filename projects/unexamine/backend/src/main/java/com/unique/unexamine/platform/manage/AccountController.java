package com.unique.unexamine.platform.manage;

import com.unique.unexamine.platform.base.entity.PlatformAccount;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 平台账号注册、登录及当前会话入口。响应从不包含口令哈希。 */
@RestController
@RequestMapping("/api/auth")
public class AccountController {
    private final AccountSessionService sessions;

    public AccountController(AccountSessionService sessions) {
        this.sessions = sessions;
    }

    /** 创建平台账号并直接进入登录态。 */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountView register(@Valid @RequestBody RegisterRequest body, HttpServletRequest request, HttpServletResponse response) {
        PlatformAccount account = sessions.register(body.username(), body.password(), body.displayName());
        sessions.open(account, request, response);
        return AccountView.from(account);
    }

    /** 根据用户名和口令建立 Redis 会话。 */
    @PostMapping("/login")
    public AccountView login(@Valid @RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        PlatformAccount account = sessions.login(body.username(), body.password());
        sessions.open(account, request, response);
        return AccountView.from(account);
    }

    /** 页面刷新时读取当前有效账号。 */
    @GetMapping("/me")
    public AccountView me(HttpServletRequest request) {
        return AccountView.from(sessions.requireAccount(request));
    }

    /** 退出当前浏览器会话。 */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        sessions.close(request, response);
    }

    /** 注册输入由页面和接口共用约束；第一版仅使用用户名登录。 */
    public record RegisterRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{2,39}", message = "用户名需为3到40位字母、数字或下划线，并以字母开头") String username,
            @NotBlank @Size(min = 8, max = 128) String password,
            @NotBlank @Size(max = 120) String displayName) {}

    /** 登录输入不改变注册时的用户名规则。 */
    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    /** 对页面公开的账号字段。 */
    public record AccountView(Long id, String username, String displayName) {
        public static AccountView from(PlatformAccount account) {
            return new AccountView(account.getId(), account.getUsername(), account.getDisplayName());
        }
    }
}
