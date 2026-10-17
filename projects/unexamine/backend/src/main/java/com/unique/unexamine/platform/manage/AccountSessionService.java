package com.unique.unexamine.platform.manage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.unique.unexamine.platform.base.entity.PlatformAccount;
import com.unique.unexamine.platform.base.service.PlatformAccountBaseService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;

/** 平台账号的口令验证和 Redis 登录会话；业务接口只使用账号主键确定访问范围。 */
@Service
public class AccountSessionService {
    private static final String COOKIE_NAME = "unexamine_session";
    private static final String KEY_PREFIX = "unexamine:r15:session:";
    private static final Duration SESSION_DURATION = Duration.ofDays(7);
    private static final SecureRandom RANDOM = new SecureRandom();
    private final PlatformAccountBaseService accounts;
    private final StringRedisTemplate redis;

    public AccountSessionService(PlatformAccountBaseService accounts, StringRedisTemplate redis) {
        this.accounts = accounts;
        this.redis = redis;
    }

    /** 注册账号；用户名冲突由数据库唯一约束兜底，绝不保存明文口令。 */
    public PlatformAccount register(String username, String password, String displayName) {
        if (accounts.selectOne(new LambdaQueryWrapper<PlatformAccount>().eq(PlatformAccount::getUsername, username)) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在");
        }
        PlatformAccount account = new PlatformAccount();
        account.setUsername(username);
        account.setDisplayName(displayName);
        account.setPasswordHash(hash(password));
        account.setStatus("ACTIVE");
        accounts.insert(account);
        return account;
    }

    /** 登录失败统一返回同一提示，不向调用方暴露用户名是否存在。 */
    public PlatformAccount login(String username, String password) {
        PlatformAccount account = accounts.selectOne(new LambdaQueryWrapper<PlatformAccount>().eq(PlatformAccount::getUsername, username));
        if (account == null || !"ACTIVE".equals(account.getStatus()) || !verify(password, account.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        return account;
    }

    /** 将不可预测的会话令牌写入现有 Redis，仅通过 HttpOnly Cookie 交给浏览器。 */
    public void open(PlatformAccount account, HttpServletRequest request, HttpServletResponse response) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redis.opsForValue().set(KEY_PREFIX + token, account.getId().toString(), SESSION_DURATION);
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(token, request.isSecure(), SESSION_DURATION).toString());
    }

    /** 从 Redis 校验会话，并再次检查账号状态。 */
    public PlatformAccount requireAccount(HttpServletRequest request) {
        String token = token(request);
        String id = token == null ? null : redis.opsForValue().get(KEY_PREFIX + token);
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        PlatformAccount account = accounts.selectById(Long.valueOf(id));
        if (account == null || !"ACTIVE".equals(account.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录已失效");
        }
        return account;
    }

    /** 删除当前会话并清除浏览器 Cookie。 */
    public void close(HttpServletRequest request, HttpServletResponse response) {
        String token = token(request);
        if (token != null) redis.delete(KEY_PREFIX + token);
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", request.isSecure(), Duration.ZERO).toString());
    }

    private String token(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(cookie -> COOKIE_NAME.equals(cookie.getName()))
                .map(jakarta.servlet.http.Cookie::getValue).findFirst().orElse(null);
    }

    private ResponseCookie cookie(String token, boolean secure, Duration duration) {
        return ResponseCookie.from(COOKIE_NAME, token).httpOnly(true).secure(secure)
                .sameSite("Lax").path("/").maxAge(duration).build();
    }

    private String hash(String password) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        byte[] derived = derive(password, salt);
        return "pbkdf2-sha256:120000:" + Base64.getEncoder().encodeToString(salt) + ":" + Base64.getEncoder().encodeToString(derived);
    }

    private boolean verify(String password, String stored) {
        try {
            String[] parts = stored.split(":", -1);
            if (parts.length != 4 || !"pbkdf2-sha256".equals(parts[0]) || !"120000".equals(parts[1])) return false;
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            return MessageDigest.isEqual(derive(password, salt), Base64.getDecoder().decode(parts[3]));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private byte[] derive(String password, byte[] salt) {
        char[] chars = password.toCharArray();
        PBEKeySpec spec = new PBEKeySpec(chars, salt, 120_000, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception exception) {
            throw new IllegalStateException("口令哈希算法不可用", exception);
        } finally {
            spec.clearPassword();
            Arrays.fill(chars, '\0');
        }
    }
}
