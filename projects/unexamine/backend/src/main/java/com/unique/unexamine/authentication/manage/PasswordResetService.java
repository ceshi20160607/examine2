package com.unique.unexamine.authentication.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.manage.repository.PasswordResetLockMapper;
import com.unique.unexamine.platform.base.entity.PlatPasswordReset;
import com.unique.unexamine.platform.base.entity.PlatformAccount;
import com.unique.unexamine.platform.base.service.PlatPasswordResetBaseService;
import com.unique.unexamine.platform.base.service.PlatformAccountBaseService;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 平台账号找回密码：匿名请求统一响应，令牌摘要入库并在重置事务中一次性消费。 */
@Service
public class PasswordResetService {
    private final PlatformAccountBaseService accountService;
    private final AccountSecurityService accountSecurityService;
    private final PasswordResetDelivery delivery;
    private final TokenFactory tokenFactory;
    private final PlatPasswordResetBaseService resetService;
    private final PasswordResetLockMapper resetLockMapper;
    private final AuditRecorder auditRecorder;

    public PasswordResetService(PlatformAccountBaseService accountService,
            AccountSecurityService accountSecurityService, PasswordResetDelivery delivery,
            TokenFactory tokenFactory, PlatPasswordResetBaseService resetService,
            PasswordResetLockMapper resetLockMapper, AuditRecorder auditRecorder) {
        this.accountService = accountService;
        this.accountSecurityService = accountSecurityService;
        this.delivery = delivery;
        this.tokenFactory = tokenFactory;
        this.resetService = resetService;
        this.resetLockMapper = resetLockMapper;
        this.auditRecorder = auditRecorder;
    }

    /** 匿名申请始终使用统一响应；先锁账号，再统计和替换令牌，串行化同一账号的请求。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PasswordResetRequestView request(PasswordResetRequest request, String traceId) {
        String mode = delivery.availableMode();
        String identifier = request.account().strip().toLowerCase(Locale.ROOT);
        List<PlatformAccount> matches = accountService.selectList(Wrappers.<PlatformAccount>lambdaQuery()
                .eq(PlatformAccount::getUsername, identifier)
                .or().eq(PlatformAccount::getEmail, identifier));
        PlatformAccount account = matches.isEmpty() ? null : matches.getFirst();
        // 未知、停用或未绑定邮箱的账号一律返回相同内容，避免匿名枚举。
        if (account == null || !"ACTIVE".equals(account.getStatus())
                || account.getEmail() == null || account.getEmail().isBlank()) {
            return new PasswordResetRequestView(mode);
        }
        PlatformAccount lockedAccount = resetLockMapper.findAccountByIdForUpdate(account.getId());
        // 等待账号锁期间账号可能被停用或换绑邮箱，投递必须使用加锁后的当前值。
        if (lockedAccount == null || !"ACTIVE".equals(lockedAccount.getStatus())
                || lockedAccount.getEmail() == null || lockedAccount.getEmail().isBlank()) {
            return new PasswordResetRequestView(mode);
        }
        LocalDateTime now = LocalDateTime.now();
        // READ_COMMITTED 让等待账号锁后的 Base 查询看到前一个请求刚提交的计数。
        long recent = resetService.selectCount(Wrappers.<PlatPasswordReset>lambdaQuery()
                .eq(PlatPasswordReset::getAccountId, lockedAccount.getId())
                .gt(PlatPasswordReset::getCreateTime, now.minusMinutes(10)));
        if (recent >= 3) return new PasswordResetRequestView(mode);

        String token = tokenFactory.create();
        // 新请求使旧链接立即失效；送达失败时整个数据库事务回滚。
        invalidateOpenTokens(lockedAccount.getId(), now);
        PlatPasswordReset reset = new PlatPasswordReset();
        reset.setAccountId(lockedAccount.getId());
        reset.setTokenHash(tokenFactory.hash(token));
        reset.setCreateTime(now);
        reset.setExpireTime(now.plusMinutes(30));
        resetService.insert(reset);
        delivery.deliver(lockedAccount.getEmail(), token);
        auditRecorder.record(traceId, lockedAccount.getId(), null, null, null,
                "ACCOUNT_PASSWORD_RESET_REQUESTED", "PLATFORM_ACCOUNT", lockedAccount.getId().toString(), "SUCCESS",
                Map.of("deliveryMode", mode));
        return new PasswordResetRequestView(mode);
    }

    /** 先锁账号再锁令牌；重读状态后消费，避免与新申请交错或出现反向锁顺序。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void confirm(PasswordResetConfirmRequest request, String traceId) {
        String tokenHash = tokenFactory.hash(request.token());
        PlatPasswordReset candidate = resetService.selectOne(Wrappers.<PlatPasswordReset>lambdaQuery()
                .eq(PlatPasswordReset::getTokenHash, tokenHash));
        if (candidate == null) {
            throw invalidToken();
        }
        if (resetLockMapper.findAccountByIdForUpdate(candidate.getAccountId()) == null) {
            throw invalidToken();
        }
        PlatPasswordReset reset = resetLockMapper.findByTokenHashForUpdate(tokenHash);
        LocalDateTime now = LocalDateTime.now();
        if (reset == null || reset.getUsedTime() != null || !reset.getExpireTime().isAfter(now)) {
            throw invalidToken();
        }
        accountSecurityService.resetPassword(reset.getAccountId(), request.newPassword(), traceId);
        invalidateOpenTokens(reset.getAccountId(), now);
    }

    /** 未知、已用和过期的令牌共享同一个对外错误。 */
    private DomainException invalidToken() {
        return new DomainException("PASSWORD_RESET_TOKEN_INVALID", "找回链接无效或已过期，请重新申请", HttpStatus.BAD_REQUEST);
    }

    /** 仅使当前仍可使用的找回链接失效；调用方负责事务边界。 */
    private void invalidateOpenTokens(Long accountId, LocalDateTime usedTime) {
        resetService.update(Wrappers.<PlatPasswordReset>lambdaUpdate()
                .set(PlatPasswordReset::getUsedTime, usedTime)
                .eq(PlatPasswordReset::getAccountId, accountId)
                .isNull(PlatPasswordReset::getUsedTime));
    }
}
