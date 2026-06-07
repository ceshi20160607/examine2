package com.unique.examine.plat.manage.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.plat.base.entity.Account;
import com.unique.examine.plat.manage.bo.LoginBO;
import com.unique.examine.plat.manage.bo.RefreshTokenBO;
import com.unique.examine.plat.manage.enums.AccountStatus;
import com.unique.examine.plat.manage.enums.AuthErrorCode;
import com.unique.examine.plat.manage.service.AuthAccountRepository;
import com.unique.examine.plat.manage.service.AuthOperationLogger;
import com.unique.examine.plat.manage.vo.AuthTokenVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthSessionServiceImplTest {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private final FakeAccountRepository accountRepository = new FakeAccountRepository();

    private final AuthOperationLogger operationLogger = (account, action, success, errorCode, summary) -> {
    };

    private MutableClock clock;

    private AuthSessionServiceImpl service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-06-06T10:00:00Z"));
        InMemoryAuthTokenStore tokenStore = new InMemoryAuthTokenStore(Duration.ofMinutes(30), Duration.ofDays(7),
                clock);
        service = new AuthSessionServiceImpl(accountRepository, operationLogger, passwordEncoder, tokenStore, clock);
    }

    @Test
    void shouldLoginAndQueryCurrentUser() {
        Account account = account("demo", "Password123!");
        account.setFailedLoginCount(2);
        accountRepository.save(account);

        AuthTokenVO token = service.login(login("demo", "Password123!"));

        assertThat(token.getAccessToken()).isNotBlank();
        assertThat(token.getRefreshToken()).isNotBlank();
        assertThat(service.me(token.getAccessToken()).getAccount().getLoginName()).isEqualTo("demo");
        assertThat(account.getFailedLoginCount()).isZero();
        assertThat(account.getLastLoginAt()).isNotNull();
    }

    @Test
    void shouldLockAccountAfterContinuousPasswordFailures() {
        Account account = account("demo", "Password123!");
        accountRepository.save(account);

        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> service.login(login("demo", "bad-password")))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(AuthErrorCode.INVALID_CREDENTIAL);
        }
        assertThatThrownBy(() -> service.login(login("demo", "bad-password")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.ACCOUNT_LOCKED);

        assertThat(account.getStatus()).isEqualTo(AccountStatus.LOCKED.getCode());
        assertThat(account.getLockedUntil()).isAfter(LocalDateTime.now(clock));
    }

    @Test
    void shouldRejectDisabledAccount() {
        Account account = account("demo", "Password123!");
        account.setStatus(AccountStatus.DISABLED.getCode());
        accountRepository.save(account);

        assertThatThrownBy(() -> service.login(login("demo", "Password123!")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.ACCOUNT_DISABLED);
    }

    @Test
    void shouldRefreshTokenAndRevokeOldAccessToken() {
        Account account = account("demo", "Password123!");
        accountRepository.save(account);
        AuthTokenVO token = service.login(login("demo", "Password123!"));

        RefreshTokenBO refreshTokenBO = new RefreshTokenBO();
        refreshTokenBO.setRefreshToken(token.getRefreshToken());
        AuthTokenVO refreshed = service.refresh(refreshTokenBO);

        assertThat(refreshed.getAccessToken()).isNotEqualTo(token.getAccessToken());
        assertThatThrownBy(() -> service.me(token.getAccessToken()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.TOKEN_EXPIRED);
        assertThat(service.me(refreshed.getAccessToken()).getAccount().getLoginName()).isEqualTo("demo");
    }

    @Test
    void shouldLogoutIdempotentlyAndRejectRevokedToken() {
        Account account = account("demo", "Password123!");
        accountRepository.save(account);
        AuthTokenVO token = service.login(login("demo", "Password123!"));

        service.logout(token.getAccessToken());
        service.logout(token.getAccessToken());

        assertThatThrownBy(() -> service.me(token.getAccessToken()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.TOKEN_EXPIRED);
    }

    @Test
    void shouldRejectExpiredAccessToken() {
        Account account = account("demo", "Password123!");
        accountRepository.save(account);
        AuthTokenVO token = service.login(login("demo", "Password123!"));

        clock.plus(Duration.ofMinutes(31));

        assertThatThrownBy(() -> service.me(token.getAccessToken()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.TOKEN_EXPIRED);
    }

    private Account account(String loginName, String password) {
        return new Account()
                .setLoginName(loginName)
                .setPasswordHash(passwordEncoder.encode(password))
                .setDisplayName(loginName)
                .setStatus(AccountStatus.NORMAL.getCode())
                .setFailedLoginCount(0)
                .setDeleteToken(0L)
                .setCreatedAt(LocalDateTime.now(clock))
                .setUpdatedAt(LocalDateTime.now(clock));
    }

    private LoginBO login(String loginName, String password) {
        LoginBO loginBO = new LoginBO();
        loginBO.setLoginName(loginName);
        loginBO.setPassword(password);
        return loginBO;
    }

    private static final class FakeAccountRepository implements AuthAccountRepository {

        private final Map<Long, Account> accounts = new LinkedHashMap<>();

        private long nextId = 1L;

        @Override
        public Optional<Account> findActiveByLoginName(String loginName) {
            return accounts.values().stream()
                    .filter(account -> loginName.equals(account.getLoginName()))
                    .filter(account -> Long.valueOf(0L).equals(account.getDeleteToken()))
                    .findFirst();
        }

        @Override
        public Optional<Account> findActiveById(Long accountId) {
            Account account = accounts.get(accountId);
            if (account == null || !Long.valueOf(0L).equals(account.getDeleteToken())) {
                return Optional.empty();
            }
            return Optional.of(account);
        }

        @Override
        public void save(Account account) {
            if (account.getId() == null) {
                account.setId(nextId++);
            }
            accounts.put(account.getId(), account);
        }

        @Override
        public void updateById(Account account) {
            accounts.put(account.getId(), account);
        }
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private final ZoneId zone = ZoneId.systemDefault();

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void plus(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
