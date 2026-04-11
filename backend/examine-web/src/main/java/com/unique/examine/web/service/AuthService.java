package com.unique.examine.web.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.core.security.SessionPayload;
import com.unique.examine.core.service.SessionService;
import com.unique.examine.plat.manage.PlatRbacManageService;
import com.unique.examine.plat.entity.po.PlatAccount;
import com.unique.examine.plat.entity.po.PlatLoginLog;
import com.unique.examine.plat.service.IPlatAccountService;
import com.unique.examine.plat.service.IPlatLoginLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AuthService {


    @Autowired
    private IPlatAccountService platAccountService;
    @Autowired
    private IPlatLoginLogService platLoginLogService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private SessionService sessionService;
    @Autowired
    private PlatRbacManageService platRbacManageService;

    @Transactional(rollbackFor = Exception.class)
    public PlatAccount register(String username, String rawPassword) {
        if (username == null || username.isBlank()) {
            throw new BusinessException("用户名不能为空");
        }
        if (rawPassword == null || rawPassword.length() < 6) {
            throw new BusinessException("密码至少 6 位");
        }
        String u = username.trim();
        Long cnt = platAccountService.count(new LambdaQueryWrapper<PlatAccount>().eq(PlatAccount::getUsername, u));
        if (cnt != null && cnt > 0) {
            throw new BusinessException("用户名已存在");
        }
        long existedBefore = platAccountService.count();
        PlatAccount acc = new PlatAccount();
        acc.setUsername(u);
        acc.setPasswordHash(passwordEncoder.encode(rawPassword));
        acc.setStatus(1);
        platAccountService.save(acc);
        platRbacManageService.bindDefaultRoleOnRegister(acc.getId(), existedBefore == 0);
        return acc;
    }

    public LoginResult login(String username, String rawPassword, String ip, String ua) {
        LambdaQueryWrapper<PlatAccount> eq = new LambdaQueryWrapper<PlatAccount>().eq(PlatAccount::getUsername, username == null ? "" : username.trim());

        PlatAccount acc = platAccountService.getOne(eq);
        if (acc == null || !passwordEncoder.matches(rawPassword, acc.getPasswordHash())) {
            PlatLoginLog log = new PlatLoginLog();
            log.setUsernameAttempt(username);
            log.setSuccessFlag(0);
            log.setFailReason("用户名或密码错误");
            log.setIp(ip);
            log.setUa(ua);
            log.setLoginTime(LocalDateTime.now());
            platLoginLogService.save(log);
            throw new BusinessException(401, "用户名或密码错误");
        }
        if (acc.getStatus() != null && acc.getStatus() == 2) {
            throw new BusinessException(403, "账号已禁用");
        }
        acc.setLastLoginTime(LocalDateTime.now());
        acc.setLastLoginIp(ip);
        platAccountService.updateById(acc);

        PlatLoginLog ok = new PlatLoginLog();
        ok.setPlatAccountId(acc.getId());
        ok.setUsernameAttempt(acc.getUsername());
        ok.setSuccessFlag(1);
        ok.setIp(ip);
        ok.setUa(ua);
        ok.setLoginTime(LocalDateTime.now());
        platLoginLogService.save(ok);

        String token = sessionService.createSession(new SessionPayload(acc.getId(), acc.getUsername(), 0L, 0L));
        return new LoginResult(token, acc);
    }

    public void logout(String token) {
        sessionService.deleteSession(token);
    }

    public PlatAccount requireAccount(Long platId) {
        PlatAccount acc = platAccountService.queryById(platId);
        if (acc == null) {
            throw new BusinessException(401, "未登录或账号不存在");
        }
        return acc;
    }

    public record LoginResult(String token, PlatAccount account) {
    }
}
