package com.unique.examine.manage.service.impl;

import com.unique.examine.base.entity.BusinessSystem;
import com.unique.examine.base.entity.PlatformAccount;
import com.unique.examine.base.service.IBusinessSystemService;
import com.unique.examine.base.service.IPlatformAccountService;
import com.unique.examine.manage.bo.AuthLoginBO;
import com.unique.examine.manage.bo.AuthRegisterBO;
import com.unique.examine.manage.enums.ErrorCode;
import com.unique.examine.manage.enums.StatusEnums;
import com.unique.examine.manage.exception.BusinessException;
import com.unique.examine.manage.security.AuthTokenService;
import com.unique.examine.manage.security.CurrentUser;
import com.unique.examine.manage.security.SecurityContext;
import com.unique.examine.manage.service.AuthManageService;
import com.unique.examine.manage.vo.AuthTokenVO;
import com.unique.examine.manage.vo.UserVO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AuthManageServiceImpl implements AuthManageService {
    private static final String RESET_REQUIRED_HASH = "{RESET_REQUIRED}";
    private static final String INITIAL_ADMIN_PASSWORD = "admin123";

    private final IPlatformAccountService accountService;
    private final IBusinessSystemService systemService;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenService authTokenService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AuthTokenVO register(AuthRegisterBO bo) {
        if (accountService.count(Wrappers.<PlatformAccount>lambdaQuery().eq(PlatformAccount::getAccount, bo.getAccount())) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "账号已存在");
        }
        PlatformAccount account = new PlatformAccount();
        account.setAccount(bo.getAccount());
        account.setRealName(bo.getRealName());
        account.setMobile(bo.getMobile());
        account.setEmail(bo.getEmail());
        account.setPasswordHash(passwordEncoder.encode(bo.getPassword()));
        account.setStatus(StatusEnums.ENABLED);
        accountService.save(account);
        return buildToken(account, false, null, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AuthTokenVO login(AuthLoginBO bo) {
        PlatformAccount account = accountService.getOne(Wrappers.<PlatformAccount>lambdaQuery().eq(PlatformAccount::getAccount, bo.getAccount()), false);
        if (account == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "账号或密码错误");
        }
        if (!StatusEnums.ENABLED.equals(account.getStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已停用");
        }
        boolean resetRequired = RESET_REQUIRED_HASH.equals(account.getPasswordHash());
        boolean passwordMatched = resetRequired
                ? Objects.equals(account.getAccount(), "admin") && Objects.equals(bo.getPassword(), INITIAL_ADMIN_PASSWORD)
                : passwordEncoder.matches(bo.getPassword(), account.getPasswordHash());
        if (!passwordMatched) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "账号或密码错误");
        }
        account.setLastLoginAt(LocalDateTime.now());
        accountService.updateById(account);
        return buildToken(account, resetRequired, null, null);
    }

    @Override
    public AuthTokenVO refresh() {
        CurrentUser currentUser = SecurityContext.currentUser();
        PlatformAccount account = accountService.getById(currentUser.getAccountId());
        if (account == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "账号不存在");
        }
        return buildToken(account, false, currentUser.getSystemId(), currentUser.getTenantId());
    }

    @Override
    public UserVO me() {
        CurrentUser currentUser = SecurityContext.currentUser();
        PlatformAccount account = accountService.getById(currentUser.getAccountId());
        if (account == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "账号不存在");
        }
        UserVO vo = new UserVO();
        vo.setId(account.getId());
        vo.setAccount(account.getAccount());
        vo.setRealName(account.getRealName());
        vo.setMobile(account.getMobile());
        vo.setEmail(account.getEmail());
        vo.setStatus(account.getStatus());
        vo.setSystemId(currentUser.getSystemId());
        vo.setTenantId(currentUser.getTenantId());
        return vo;
    }

    public AuthTokenVO issueForSystem(PlatformAccount account, BusinessSystem system) {
        return buildToken(account, false, system.getId(), system.getTenantId());
    }

    private AuthTokenVO buildToken(PlatformAccount account, boolean resetRequired, Long systemId, Long tenantId) {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setAccountId(account.getId());
        currentUser.setAccount(account.getAccount());
        currentUser.setRealName(account.getRealName());
        currentUser.setSystemId(systemId);
        currentUser.setTenantId(tenantId);
        UserVO userVO = new UserVO();
        userVO.setId(account.getId());
        userVO.setAccount(account.getAccount());
        userVO.setRealName(account.getRealName());
        userVO.setMobile(account.getMobile());
        userVO.setEmail(account.getEmail());
        userVO.setStatus(account.getStatus());
        userVO.setSystemId(systemId);
        userVO.setTenantId(tenantId);
        AuthTokenVO vo = new AuthTokenVO();
        vo.setAccessToken(authTokenService.issue(currentUser));
        vo.setTokenType("Bearer");
        vo.setResetRequired(resetRequired);
        vo.setUser(userVO);
        return vo;
    }
}
