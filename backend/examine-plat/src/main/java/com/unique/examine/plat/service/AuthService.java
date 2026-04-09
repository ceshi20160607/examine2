package com.unique.examine.plat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.core.security.SessionPayload;
import com.unique.examine.core.service.SessionService;
import com.unique.examine.plat.entity.PlatAccount;
import com.unique.examine.plat.entity.PlatLoginLog;
import com.unique.examine.plat.mapper.PlatAccountMapper;
import com.unique.examine.plat.mapper.PlatLoginLogMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AuthService {

    private final PlatAccountMapper platAccountMapper;
    private final PlatLoginLogMapper platLoginLogMapper;
    private final PasswordEncoder passwordEncoder;
    private final SessionService sessionService;

    public AuthService(PlatAccountMapper platAccountMapper, PlatLoginLogMapper platLoginLogMapper,
                       PasswordEncoder passwordEncoder, SessionService sessionService) {
        this.platAccountMapper = platAccountMapper;
        this.platLoginLogMapper = platLoginLogMapper;
        this.passwordEncoder = passwordEncoder;
        this.sessionService = sessionService;
    }

    @Transactional(rollbackFor = Exception.class)
    public PlatAccount register(String username, String rawPassword) {
        if (username == null || username.isBlank()) {
            throw new BusinessException("用户名不能为空");
        }
        if (rawPassword == null || rawPassword.length() < 6) {
            throw new BusinessException("密码至少 6 位");
        }
        String u = username.trim();
        Long cnt = platAccountMapper.selectCount(new LambdaQueryWrapper<PlatAccount>().eq(PlatAccount::getUsername, u));
        if (cnt != null && cnt > 0) {
            throw new BusinessException("用户名已存在");
        }
        PlatAccount acc = new PlatAccount();
        acc.setUsername(u);
        acc.setPasswordHash(passwordEncoder.encode(rawPassword));
        acc.setStatus(1);
        platAccountMapper.insert(acc);
        return acc;
    }

    public LoginResult login(String username, String rawPassword, String ip, String ua) {
        PlatAccount acc = platAccountMapper.selectOne(
                new LambdaQueryWrapper<PlatAccount>().eq(PlatAccount::getUsername, username == null ? "" : username.trim()));
        if (acc == null || !passwordEncoder.matches(rawPassword, acc.getPasswordHash())) {
            PlatLoginLog log = new PlatLoginLog();
            log.setUsernameAttempt(username);
            log.setSuccessFlag(0);
            log.setFailReason("用户名或密码错误");
            log.setIp(ip);
            log.setUa(ua);
            log.setLoginTime(LocalDateTime.now());
            platLoginLogMapper.insert(log);
            throw new BusinessException(401, "用户名或密码错误");
        }
        if (acc.getStatus() != null && acc.getStatus() == 2) {
            throw new BusinessException(403, "账号已禁用");
        }
        acc.setLastLoginTime(LocalDateTime.now());
        acc.setLastLoginIp(ip);
        platAccountMapper.updateById(acc);

        PlatLoginLog ok = new PlatLoginLog();
        ok.setPlatAccountId(acc.getId());
        ok.setUsernameAttempt(acc.getUsername());
        ok.setSuccessFlag(1);
        ok.setIp(ip);
        ok.setUa(ua);
        ok.setLoginTime(LocalDateTime.now());
        platLoginLogMapper.insert(ok);

        String token = sessionService.createSession(new SessionPayload(acc.getId(), 0L, 0L));
        return new LoginResult(token, acc);
    }

    public void logout(String token) {
        sessionService.deleteSession(token);
    }

    public PlatAccount requireAccount(Long platId) {
        PlatAccount acc = platAccountMapper.selectById(platId);
        if (acc == null) {
            throw new BusinessException(401, "未登录或账号不存在");
        }
        return acc;
    }

    public record LoginResult(String token, PlatAccount account) {
    }
}
