package com.unique.examine.plat.manage.service;

import com.unique.examine.core.api.AccountRecoveryMailFacade;
import com.unique.examine.core.api.AuditEvent;
import com.unique.examine.core.api.AuditFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.id.IdService;
import com.unique.examine.plat.base.entity.Account;
import com.unique.examine.plat.base.entity.Credential;
import com.unique.examine.plat.base.entity.PasswordRecoveryToken;
import com.unique.examine.plat.base.mapper.PlatAccountMapper;
import com.unique.examine.plat.base.mapper.PlatCredentialMapper;
import com.unique.examine.plat.base.mapper.PlatPasswordRecoveryTokenMapper;
import com.unique.examine.plat.manage.config.PasswordRecoveryProperties;
import com.unique.examine.plat.manage.dto.PasswordRecoveryRequest;
import com.unique.examine.plat.manage.dto.PasswordRecoveryResetRequest;
import com.unique.examine.plat.manage.security.PasswordService;
import com.unique.examine.plat.manage.security.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordRecoveryServiceTest {
    private static final String RAW_TOKEN = "A".repeat(43);
    private static final String REPLACEMENT = "replacement-password";

    @Test
    void activeAccountStoresOnlyHashAppliesTwoLimitsAndDeliversTransientToken() {
        var fixture = fixture();

        fixture.service.request(new PasswordRecoveryRequest(" Owner_1 "), client());

        assertThat(fixture.rateLimits).hasSize(2);
        assertThat(fixture.rateLimits.get(0)).contains("password-recovery-ip", "127.0.0.1");
        assertThat(fixture.rateLimits.get(1)).startsWith("password-recovery-account:")
                .doesNotContain("owner_1");
        assertThat(fixture.stored).isNotNull();
        assertThat(fixture.stored.getTokenHash()).hasSize(64)
                .isNotEqualTo(fixture.delivered.rawRecoveryToken());
        assertThat(fixture.tokenService.hash(fixture.delivered.rawRecoveryToken()))
                .isEqualTo(fixture.stored.getTokenHash());
        assertThat(fixture.delivered.recipientEmail()).isEqualTo("owner@example.com");
        assertThat(fixture.events).extracting(AuditEvent::eventType)
                .containsExactly("PASSWORD_RECOVERY_REQUEST", "PASSWORD_RECOVERY_DELIVERY");
        assertThat(fixture.events).allSatisfy(event ->
                assertThat(event.toString()).doesNotContain(fixture.delivered.rawRecoveryToken()));
    }

    @Test
    void unknownAndMailFailureRemainGenericWhileFailedTokenIsRevoked() {
        var missing = fixture();
        missing.account = null;
        missing.service.request(new PasswordRecoveryRequest("missing"), client());
        assertThat(missing.stored).isNull();
        assertThat(missing.delivered).isNull();
        assertThat(missing.events).singleElement()
                .extracting(AuditEvent::eventType).isEqualTo("PASSWORD_RECOVERY_REQUEST");

        var failed = fixture();
        failed.deliveryStatus = AccountRecoveryMailFacade.Status.FAILED;
        failed.service.request(new PasswordRecoveryRequest("owner_1"), client());
        assertThat(failed.revokedIds).containsExactly(failed.stored.getId());
        assertThat(failed.events.getLast()).satisfies(event -> {
            assertThat(event.result()).isEqualTo("FAILED");
            assertThat(event.failureCode()).isEqualTo("PASSWORD_RECOVERY_MAIL_FAILED");
        });
    }

    @Test
    void validTokenAtomicallyRehashesConsumesRevokesSiblingsAndAllSessions() {
        var fixture = fixture();
        fixture.prepareRecovery(RAW_TOKEN, LocalDateTime.now().plusMinutes(10));
        var previousHash = fixture.credential.getPasswordHash();

        fixture.service.reset(
                new PasswordRecoveryResetRequest(RAW_TOKEN, REPLACEMENT), client());

        assertThat(fixture.passwordService.matches(
                REPLACEMENT, fixture.credential.getPasswordHash())).isTrue();
        assertThat(fixture.credential.getPasswordHash()).isNotEqualTo(previousHash);
        assertThat(fixture.credential.getFailedAttempts()).isZero();
        assertThat(fixture.credential.getLockedUntil()).isNull();
        assertThat(fixture.consumedIds).containsExactly(fixture.stored.getId());
        assertThat(fixture.siblingRevocations).containsExactly(fixture.stored.getId());
        assertThat(fixture.sessionService.revokedAccounts).containsExactly(17L);
        assertThat(fixture.events.getLast()).satisfies(event -> {
            assertThat(event.eventType()).isEqualTo("PASSWORD_RECOVERY_RESET");
            assertThat(event.result()).isEqualTo("SUCCESS");
            assertThat(event.failureCode()).isNull();
            assertThat(event.toString()).doesNotContain(RAW_TOKEN, REPLACEMENT, previousHash);
        });
    }

    @Test
    void invalidExpiredUsedAndDisabledTokensFailBeforePasswordOrSessionWrites() {
        var invalid = fixture();
        assertCode("PASSWORD_RECOVERY_INVALID", () -> invalid.service.reset(
                new PasswordRecoveryResetRequest(RAW_TOKEN, REPLACEMENT), client()));
        assertNoResetWrites(invalid);

        var expired = fixture();
        expired.prepareRecovery(RAW_TOKEN, LocalDateTime.now().minusSeconds(1));
        assertCode("PASSWORD_RECOVERY_EXPIRED", () -> expired.service.reset(
                new PasswordRecoveryResetRequest(RAW_TOKEN, REPLACEMENT), client()));
        assertNoResetWrites(expired);

        var used = fixture();
        used.prepareRecovery(RAW_TOKEN, LocalDateTime.now().plusMinutes(10));
        used.stored.setStatus("USED");
        assertCode("PASSWORD_RECOVERY_INVALID", () -> used.service.reset(
                new PasswordRecoveryResetRequest(RAW_TOKEN, REPLACEMENT), client()));
        assertNoResetWrites(used);

        var disabled = fixture();
        disabled.prepareRecovery(RAW_TOKEN, LocalDateTime.now().plusMinutes(10));
        disabled.account.setStatus("DISABLED");
        assertCode("PASSWORD_RECOVERY_ACCOUNT_DISABLED", () -> disabled.service.reset(
                new PasswordRecoveryResetRequest(RAW_TOKEN, REPLACEMENT), client()));
        assertNoResetWrites(disabled);
        assertThat(disabled.events.getLast().failureCode())
                .isEqualTo("PASSWORD_RECOVERY_ACCOUNT_DISABLED");
    }

    private static void assertNoResetWrites(Fixture fixture) {
        assertThat(fixture.credentialUpdates).isZero();
        assertThat(fixture.consumedIds).isEmpty();
        assertThat(fixture.sessionService.revokedAccounts).isEmpty();
        assertThat(fixture.events.getLast().result()).isEqualTo("DENIED");
    }

    private static void assertCode(String code, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.code()).isEqualTo(code));
    }

    private static Fixture fixture() {
        return new Fixture();
    }

    private static ClientRequest client() {
        return new ClientRequest("request-1", "trace-1", "127.0.0.1", "owner-test");
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(), new Class<?>[]{type},
                (instance, method, arguments) -> invocation.call(method.getName(), arguments));
    }

    private static Object unexpected(String method) {
        throw new AssertionError("Unexpected mapper method: " + method);
    }

    @FunctionalInterface
    private interface Invocation {
        Object call(String method, Object[] arguments);
    }

    private static final class Fixture {
        private final PasswordService passwordService = new PasswordService();
        private final TokenService tokenService = new TokenService();
        private final List<String> rateLimits = new ArrayList<>();
        private final List<Long> revokedIds = new ArrayList<>();
        private final List<Long> consumedIds = new ArrayList<>();
        private final List<Long> siblingRevocations = new ArrayList<>();
        private final List<AuditEvent> events = new ArrayList<>();
        private final RecordingSessionService sessionService = new RecordingSessionService();
        private final PasswordRecoveryService service;
        private Account account = account();
        private Credential credential = credential(passwordService);
        private PasswordRecoveryToken stored;
        private AccountRecoveryMailFacade.Command delivered;
        private AccountRecoveryMailFacade.Status deliveryStatus = AccountRecoveryMailFacade.Status.SENT;
        private int credentialUpdates;

        private Fixture() {
            var accountMapper = proxy(PlatAccountMapper.class, (method, arguments) -> switch (method) {
                case "selectList" -> account == null ? List.of() : List.of(account);
                case "selectById" -> account;
                default -> unexpected(method);
            });
            var credentialMapper = proxy(PlatCredentialMapper.class, (method, arguments) -> switch (method) {
                case "selectOne" -> credential;
                case "replacePassword" -> {
                    credentialUpdates++;
                    yield 1;
                }
                default -> unexpected(method);
            });
            var recoveryMapper = proxy(PlatPasswordRecoveryTokenMapper.class,
                    (method, arguments) -> switch (method) {
                        case "insert" -> {
                            stored = (PasswordRecoveryToken) arguments[0];
                            yield 1;
                        }
                        case "revoke" -> {
                            revokedIds.add(((Number) arguments[0]).longValue());
                            yield 1;
                        }
                        case "revokeActiveForAccount" -> {
                            if (arguments[1] != null) {
                                siblingRevocations.add(((Number) arguments[1]).longValue());
                            }
                            yield 1;
                        }
                        case "lockByHash" -> stored != null
                                && stored.getTokenHash().equals(arguments[0]) ? stored : null;
                        case "consume" -> {
                            consumedIds.add(((Number) arguments[0]).longValue());
                            yield 1;
                        }
                        default -> unexpected(method);
                    });
            var limits = new RecordingRateLimitService(rateLimits);
            AccountRecoveryMailFacade mail = command -> {
                delivered = command;
                return new AccountRecoveryMailFacade.DeliveryReceipt(command.requestId(), deliveryStatus);
            };
            AuditFacade audit = events::add;
            service = new PasswordRecoveryService(
                    accountMapper, credentialMapper, recoveryMapper,
                    passwordService, tokenService, sessionService, limits, mail,
                    new PasswordRecoveryProperties(null, 5, 3),
                    new IdService(), audit, transactionTemplate());
        }

        private void prepareRecovery(String raw, LocalDateTime expiresAt) {
            stored = new PasswordRecoveryToken();
            stored.setId(71L);
            stored.setAccountId(17L);
            stored.setTokenHash(tokenService.hash(raw));
            stored.setStatus("ACTIVE");
            stored.setExpiresAt(expiresAt);
            stored.setVersion(0L);
        }
    }

    private static Account account() {
        var value = new Account();
        value.setId(17L);
        value.setUsernameNormalized("owner_1");
        value.setEmail("owner@example.com");
        value.setEmailNormalized("owner@example.com");
        value.setStatus("ACTIVE");
        return value;
    }

    private static Credential credential(PasswordService passwords) {
        var hash = passwords.hash("current-password");
        var value = new Credential();
        value.setId(27L);
        value.setAccountId(17L);
        value.setCredentialType("PASSWORD");
        value.setPasswordHash(hash.encoded());
        value.setPasswordAlgorithm(hash.algorithm());
        value.setPasswordParameters(hash.parameters());
        value.setFailedAttempts(4);
        value.setLockedUntil(LocalDateTime.now().plusHours(1));
        value.setVersion(0L);
        return value;
    }

    private static TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
            }

            @Override
            public void rollback(TransactionStatus status) {
            }
        });
    }

    private static final class RecordingRateLimitService extends AnonymousRateLimitService {
        private final List<String> calls;

        private RecordingRateLimitService(List<String> calls) {
            super((StringRedisTemplate) null);
            this.calls = calls;
        }

        @Override
        public void check(String action, String subject, int limit, java.time.Duration window) {
            calls.add(action + ":" + subject + ":" + limit + ":" + window);
        }
    }

    private static final class RecordingSessionService extends SessionService {
        private final List<Long> revokedAccounts = new ArrayList<>();

        private RecordingSessionService() {
            super(null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null);
        }

        @Override
        public void revokeAllForAccount(long accountId) {
            revokedAccounts.add(accountId);
        }
    }
}
