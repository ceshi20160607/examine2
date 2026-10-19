package com.unique.unexamine.manage.platform;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.unique.unexamine.base.entity.UnPlatAccount;
import com.unique.unexamine.base.entity.UnPlatLogLogin;
import com.unique.unexamine.base.mapper.UnPlatAccountMapper;
import com.unique.unexamine.base.mapper.UnPlatLogLoginMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** 平台账号的登录、会话读取与退出入口。 */
@RestController
@RequestMapping("/api/platform/session")
public class PlatformSessionController {
    /** 服务端会话保存的平台账号主键。 */
    private static final String SESSION_ACCOUNT_ID = "platformAccountId";

    private final UnPlatAccountMapper accountMapper;
    private final UnPlatLogLoginMapper loginLogMapper;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public PlatformSessionController(
            UnPlatAccountMapper accountMapper,
            UnPlatLogLoginMapper loginLogMapper
    ) {
        this.accountMapper = accountMapper;
        this.loginLogMapper = loginLogMapper;
    }

    /** 验证平台账号，写入成功或失败日志，成功后更新服务端会话。 */
    @PostMapping
    public AccountView login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        String loginAccount = body.account().trim();
        UnPlatAccount account = accountMapper.selectOne(
                new LambdaQueryWrapper<UnPlatAccount>().eq(UnPlatAccount::getAccount, loginAccount));
        boolean active = account != null && account.getStatus() != null && account.getStatus() == 1;
        boolean matched = active && encoder.matches(body.password(), account.getPasswordHash());
        writeLoginLog(loginAccount, account, matched, request);
        if (!matched) {
            // 对外统一错误，避免通过登录响应识别账号是否存在或停用。
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账号或密码错误");
        }
        HttpSession session = request.getSession(true);
        request.changeSessionId();
        session.setAttribute(SESSION_ACCOUNT_ID, account.getId());
        session.setMaxInactiveInterval(8 * 60 * 60);
        return toView(account);
    }

    /** 每次读取账号状态，停用账号不再保留有效会话。 */
    @GetMapping
    public AccountView current(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object id = session == null ? null : session.getAttribute(SESSION_ACCOUNT_ID);
        if (!(id instanceof Long accountId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        UnPlatAccount account = accountMapper.selectById(accountId);
        if (account == null || account.getStatus() == null || account.getStatus() != 1) {
            session.invalidate();
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "会话已失效");
        }
        return toView(account);
    }

    /** 退出时销毁服务端会话。 */
    @DeleteMapping
    public void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    /** 成功和失败尝试都写入原名登录日志表。 */
    private void writeLoginLog(
            String loginAccount,
            UnPlatAccount account,
            boolean success,
            HttpServletRequest request
    ) {
        UnPlatLogLogin log = new UnPlatLogLogin();
        log.setAccountId(account == null ? null : account.getId());
        log.setLoginAccount(loginAccount);
        log.setSuccess((byte) (success ? 1 : 0));
        log.setFailureReason(success ? null : "账号不存在、停用或密码错误");
        log.setIpAddress(request.getRemoteAddr());
        loginLogMapper.insert(log);
    }

    /** 响应只包含页面需要的公开账号信息。 */
    private AccountView toView(UnPlatAccount account) {
        return new AccountView(account.getId(), account.getAccount(), account.getDisplayName());
    }

    /** 登录时只接收账号和密码，不接受客户端指定的权限信息。 */
    public record LoginRequest(
            @NotBlank @Size(max = 64) String account,
            @NotBlank @Size(max = 200) String password
    ) {
    }

    /** 对外会话信息不暴露密码摘要和内部账号状态。 */
    public record AccountView(Long id, String account, String displayName) {
    }
}
