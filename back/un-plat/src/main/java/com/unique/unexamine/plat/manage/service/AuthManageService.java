package com.unique.unexamine.plat.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.unique.unexamine.plat.base.entity.UnPlatAccount;
import com.unique.unexamine.plat.base.entity.UnPlatLogLogin;
import com.unique.unexamine.plat.base.entity.UnPlatMember;
import com.unique.unexamine.plat.base.entity.UnPlatMemberRole;
import com.unique.unexamine.plat.base.entity.UnPlatRole;
import com.unique.unexamine.plat.base.service.IUnPlatAccountService;
import com.unique.unexamine.plat.base.service.IUnPlatLogLoginService;
import com.unique.unexamine.plat.base.service.IUnPlatMemberRoleService;
import com.unique.unexamine.plat.base.service.IUnPlatMemberService;
import com.unique.unexamine.plat.base.service.IUnPlatRoleService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.NoSuchElementException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 平台账号注册、登录及会话规则，统一账号供多个业务系统使用。 */
@Service
public class AuthManageService {
    private static final Duration SESSION_LIFE = Duration.ofHours(12);
    private static final Duration FAILURE_WINDOW = Duration.ofMinutes(15);
    private static final SecureRandom RANDOM = new SecureRandom();
    private final IUnPlatAccountService accounts;
    private final IUnPlatLogLoginService loginLogs;
    private final IUnPlatMemberService members;
    private final IUnPlatRoleService roles;
    private final IUnPlatMemberRoleService memberRoles;
    private final StringRedisTemplate redis;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();

    /** 平台注册参数；密码只进入哈希计算，不写入日志或响应。 */
    public record RegisterRequest(@NotBlank @Size(min = 3, max = 80) String username,
                                  @NotBlank @Size(min = 8, max = 72) String password,
                                  @NotBlank @Size(max = 100) String displayName) {}

    /** 平台登录参数。 */
    public record LoginRequest(@NotBlank @Size(max = 80) String username,
                               @NotBlank String password) {}

    /** 返回可展示的账号资料，不泄漏密码哈希。 */
    public record AccountView(Long id, String username, String displayName, Long defaultSystemId) {}

    /** 登录成功后给客户端的短期凭据。 */
    public record LoginView(String accessToken, AccountView account) {}

    /** 注入贴表基础服务及 Redis 会话存储。 */
    public AuthManageService(IUnPlatAccountService accounts, IUnPlatLogLoginService loginLogs,
                             IUnPlatMemberService members, IUnPlatRoleService roles,
                             IUnPlatMemberRoleService memberRoles, StringRedisTemplate redis) {
        this.accounts = accounts;
        this.loginLogs = loginLogs;
        this.members = members;
        this.roles = roles;
        this.memberRoles = memberRoles;
        this.redis = redis;
    }

    /** 创建全局平台账号，用户名由数据库唯一键保护并发注册。 */
    @Transactional(rollbackFor = Exception.class)
    public AccountView register(RegisterRequest request) {
        String username = normalize(request.username());
        if (!username.matches("[a-z0-9_.-]{3,80}")) {
            throw new IllegalArgumentException("登录名只能使用字母、数字、点、下划线和横线");
        }
        if (accounts.count(new LambdaQueryWrapper<UnPlatAccount>()
                .eq(UnPlatAccount::getUsername, username)) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "登录名已存在");
        }
        UnPlatAccount account = new UnPlatAccount();
        account.setUsername(username);
        account.setPasswordHash(passwords.encode(request.password()));
        account.setDisplayName(request.displayName().trim());
        account.setStatus("ACTIVE");
        try {
            accounts.addOrUpdate(account);
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "登录名已存在", ex);
        }
        return view(account);
    }

    /** 密码核对、失败限制和成功会话由平台业务层统一处理。 */
    public LoginView login(LoginRequest request) {
        String username = normalize(request.username());
        String failureKey = "examine:login-failed:" + username;
        String attempts = redis.opsForValue().get(failureKey);
        if (attempts != null && Long.parseLong(attempts) >= 5) {
            writeLoginLog(null, username, "LOCKED", "短时失败次数过多");
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "登录尝试过多，请稍后再试");
        }
        UnPlatAccount account = accounts.getOne(new LambdaQueryWrapper<UnPlatAccount>()
                .eq(UnPlatAccount::getUsername, username), false);
        if (account == null || !passwords.matches(request.password(), account.getPasswordHash())
                || !"ACTIVE".equals(account.getStatus())) {
            Long count = redis.opsForValue().increment(failureKey);
            if (count != null && count == 1) {
                redis.expire(failureKey, FAILURE_WINDOW);
            }
            writeLoginLog(account, username, "FAILED", "凭据无效或账号停用");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录失败");
        }
        redis.delete(failureKey);
        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        String token = HexFormat.of().formatHex(random);
        redis.opsForValue().set(sessionKey(token), account.getId().toString(), SESSION_LIFE);
        account.setLastLoginTime(LocalDateTime.now());
        accounts.addOrUpdate(account);
        writeLoginLog(account, username, "SUCCESS", null);
        return new LoginView(token, view(account));
    }

    /** 由会话读取统一账号，禁止请求方自行声明账号主键。 */
    public UnPlatAccount requireAccount(String authorization) {
        String token = bearerToken(authorization);
        String value = redis.opsForValue().get(sessionKey(token));
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录状态已失效");
        }
        try {
            UnPlatAccount account = accounts.queryById(Long.parseLong(value));
            if (!"ACTIVE".equals(account.getStatus())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "账号已停用");
            }
            return account;
        } catch (NumberFormatException | NoSuchElementException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录状态无效", ex);
        }
    }

    /** 返回当前可展示账号信息。 */
    public AccountView current(String authorization) {
        return view(requireAccount(authorization));
    }

    /** 退出时撤销当前会话。 */
    public void logout(String authorization) {
        redis.delete(sessionKey(bearerToken(authorization)));
    }

    /** 初始化一次默认平台管理员，绝不覆盖已存在账号的密码。 */
    @Transactional(rollbackFor = Exception.class)
    public void ensureInitialAdmin() {
        UnPlatAccount admin = accounts.getOne(new LambdaQueryWrapper<UnPlatAccount>()
                .eq(UnPlatAccount::getUsername, "admin"), false);
        if (admin == null) {
            admin = new UnPlatAccount();
            admin.setUsername("admin");
            admin.setPasswordHash(passwords.encode("123123aa"));
            admin.setDisplayName("平台管理员");
            admin.setStatus("ACTIVE");
            accounts.addOrUpdate(admin);
        }
        UnPlatMember member = members.getOne(new LambdaQueryWrapper<UnPlatMember>()
                .eq(UnPlatMember::getAccountId, admin.getId()), false);
        if (member == null) {
            member = new UnPlatMember();
            member.setAccountId(admin.getId());
            member.setStatus("ACTIVE");
            members.addOrUpdate(member);
        }
        UnPlatRole role = roles.getOne(new LambdaQueryWrapper<UnPlatRole>()
                .eq(UnPlatRole::getCode, "PLATFORM_SUPER_ADMIN"), false);
        if (role == null) {
            role = new UnPlatRole();
            role.setCode("PLATFORM_SUPER_ADMIN");
            role.setName("平台超级管理员");
            role.setStatus("ACTIVE");
            roles.addOrUpdate(role);
        }
        if (memberRoles.count(new LambdaQueryWrapper<UnPlatMemberRole>()
                .eq(UnPlatMemberRole::getMemberId, member.getId())
                .eq(UnPlatMemberRole::getRoleId, role.getId())) == 0) {
            UnPlatMemberRole grant = new UnPlatMemberRole();
            grant.setMemberId(member.getId());
            grant.setRoleId(role.getId());
            memberRoles.addOrUpdate(grant);
        }
    }

    /** 只保留登录日志的业务结果和时间，不记录密码或令牌。 */
    private void writeLoginLog(UnPlatAccount account, String username, String result, String reason) {
        UnPlatLogLogin log = new UnPlatLogLogin();
        log.setAccountId(account == null ? null : account.getId());
        log.setUsername(username);
        log.setResultCode(result);
        log.setFailureReason(reason);
        loginLogs.addOrUpdate(log);
    }

    /** 会话键仅保存令牌摘要，避免 Redis 键出现原始令牌。 */
    private String sessionKey(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return "examine:session:" + HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("缺少会话摘要算法", ex);
        }
    }

    /** HTTP Bearer 凭据必须完整且有固定格式。 */
    private String bearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")
                || !authorization.substring(7).matches("[0-9a-f]{64}")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        return authorization.substring(7);
    }

    /** 登录名大小写不作为不同账号。 */
    private String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    /** 清理可展示账号属性。 */
    private AccountView view(UnPlatAccount account) {
        return new AccountView(account.getId(), account.getUsername(),
                account.getDisplayName(), account.getDefaultSystemId());
    }
}
