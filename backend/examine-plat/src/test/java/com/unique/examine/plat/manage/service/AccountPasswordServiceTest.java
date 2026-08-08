package com.unique.examine.plat.manage.service;

import com.unique.examine.core.api.AuditEvent;
import com.unique.examine.core.api.AuditFacade;
import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.plat.api.AuthenticatedSession;
import com.unique.examine.plat.base.entity.Account;
import com.unique.examine.plat.base.entity.Credential;
import com.unique.examine.plat.base.mapper.PlatAccountMapper;
import com.unique.examine.plat.base.mapper.PlatCredentialMapper;
import com.unique.examine.plat.manage.dto.ChangePasswordRequest;
import com.unique.examine.plat.manage.security.PasswordService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountPasswordServiceTest {
    private static final String CURRENT = "current-password";
    private static final String REPLACEMENT = "replacement-password";

    @Test
    void replacesArgonCredentialResetsLockRevokesSessionsAndAuditsSuccess() {
        var fixture = fixture(1, account("ACTIVE"), credential(CURRENT));
        var previousHash = fixture.credential.getPasswordHash();
        var previousChangedAt = fixture.credential.getPasswordChangedAt();

        fixture.service.changePassword(
                session(), new ChangePasswordRequest(CURRENT, REPLACEMENT), client());

        assertThat(fixture.credentialUpdates).hasValue(1);
        assertThat(fixture.passwordService.matches(
                REPLACEMENT, fixture.credential.getPasswordHash())).isTrue();
        assertThat(fixture.passwordService.matches(
                CURRENT, fixture.credential.getPasswordHash())).isFalse();
        assertThat(fixture.credential.getPasswordHash()).isNotEqualTo(previousHash);
        assertThat(fixture.credential.getPasswordAlgorithm()).isEqualTo("ARGON2ID");
        assertThat(fixture.credential.getPasswordParameters()).isEqualTo("m=65536,t=3,p=1");
        assertThat(fixture.credential.getFailedAttempts()).isZero();
        assertThat(fixture.credential.getLockedUntil()).isNull();
        assertThat(fixture.credential.getPasswordChangedAt()).isAfter(previousChangedAt);
        assertThat(fixture.credential.getUpdatedAt())
                .isEqualTo(fixture.credential.getPasswordChangedAt());
        assertThat(fixture.sessionService.revokedAccounts).containsExactly(17L);
        assertThat(fixture.events).singleElement().satisfies(event -> {
            assertThat(event.eventType()).isEqualTo("PASSWORD_CHANGE");
            assertThat(event.accountId()).isEqualTo(17L);
            assertThat(event.accountHint()).isNull();
            assertThat(event.systemId()).isEqualTo(41L);
            assertThat(event.tenantId()).isEqualTo(51L);
            assertThat(event.result()).isEqualTo("SUCCESS");
            assertThat(event.failureCode()).isNull();
            assertThat(event.detailJson()).isEqualTo("{}");
            assertThat(event.toString())
                    .doesNotContain(CURRENT, REPLACEMENT, previousHash);
        });
        assertThat(fixture.order).containsExactly("credential", "sessions", "audit");
    }

    @Test
    void wrongCurrentPasswordAuditsDeniedWithoutCredentialOrSessionWrites() {
        var fixture = fixture(1, account("ACTIVE"), credential(CURRENT));
        var previousHash = fixture.credential.getPasswordHash();

        assertCode("PASSWORD_CURRENT_INVALID", () -> fixture.service.changePassword(
                session(), new ChangePasswordRequest("wrong-password", REPLACEMENT), client()));

        assertThat(fixture.credentialUpdates).hasValue(0);
        assertThat(fixture.credential.getPasswordHash()).isEqualTo(previousHash);
        assertThat(fixture.sessionService.revokedAccounts).isEmpty();
        assertThat(fixture.events).singleElement().satisfies(event -> {
            assertThat(event.eventType()).isEqualTo("PASSWORD_CHANGE");
            assertThat(event.result()).isEqualTo("DENIED");
            assertThat(event.failureCode()).isEqualTo("PASSWORD_CURRENT_INVALID");
            assertThat(event.accountHint()).isNull();
            assertThat(event.detailJson()).isEqualTo("{}");
            assertThat(event.toString())
                    .doesNotContain("wrong-password", REPLACEMENT, previousHash);
        });
        assertThat(fixture.order).containsExactly("audit");
    }

    @Test
    void unchangedPasswordAndUnavailableOwnerFailBeforeAllWrites() {
        var unchanged = fixture(1, account("ACTIVE"), credential(CURRENT));
        assertCode("PASSWORD_UNCHANGED", () -> unchanged.service.changePassword(
                session(), new ChangePasswordRequest(CURRENT, CURRENT), client()));
        assertNoWrites(unchanged);

        var inactive = fixture(1, account("DISABLED"), credential(CURRENT));
        assertCode("PASSWORD_CHANGE_UNAVAILABLE", () -> inactive.service.changePassword(
                session(), new ChangePasswordRequest(CURRENT, REPLACEMENT), client()));
        assertNoWrites(inactive);

        var missingCredential = fixture(1, account("ACTIVE"), null);
        assertCode("PASSWORD_CHANGE_UNAVAILABLE", () -> missingCredential.service.changePassword(
                session(), new ChangePasswordRequest(CURRENT, REPLACEMENT), client()));
        assertNoWrites(missingCredential);
    }

    @Test
    void optimisticConflictStopsBeforeSessionRevocationAndSuccessAudit() {
        var fixture = fixture(0, account("ACTIVE"), credential(CURRENT));

        assertCode("PASSWORD_CHANGE_CONFLICT", () -> fixture.service.changePassword(
                session(), new ChangePasswordRequest(CURRENT, REPLACEMENT), client()));

        assertThat(fixture.credentialUpdates).hasValue(1);
        assertThat(fixture.sessionService.revokedAccounts).isEmpty();
        assertThat(fixture.events).isEmpty();
        assertThat(fixture.order).containsExactly("credential");
    }

    @Test
    void deniedAuditIsExplicitlyCommittedWhileOtherFailuresRollBack() throws Exception {
        var method = AccountPasswordService.class.getMethod(
                "changePassword",
                AuthenticatedSession.class,
                ChangePasswordRequest.class,
                ClientRequest.class
        );
        var transaction = method.getAnnotation(Transactional.class);

        assertThat(transaction).isNotNull();
        assertThat(transaction.noRollbackFor())
                .containsExactly(PasswordChangeDeniedException.class);
    }

    private static Fixture fixture(int updateResult, Account account, Credential credential) {
        var credentialUpdates = new AtomicInteger();
        var order = new ArrayList<String>();
        var accountMapper = proxy(PlatAccountMapper.class, (method, arguments) -> switch (method) {
            case "selectById" -> account;
            default -> unexpected(method);
        });
        var credentialMapper = proxy(PlatCredentialMapper.class, (method, arguments) -> switch (method) {
            case "selectOne" -> credential;
            case "replacePassword" -> {
                credentialUpdates.incrementAndGet();
                order.add("credential");
                yield updateResult;
            }
            default -> unexpected(method);
        });
        var passwordService = new PasswordService();
        var sessionService = new RecordingSessionService(order);
        var events = new ArrayList<AuditEvent>();
        AuditFacade auditFacade = event -> {
            events.add(event);
            order.add("audit");
        };
        var service = new AccountPasswordService(
                accountMapper,
                credentialMapper,
                passwordService,
                sessionService,
                auditFacade
        );
        return new Fixture(
                service,
                passwordService,
                sessionService,
                credential,
                credentialUpdates,
                events,
                order
        );
    }

    private static void assertNoWrites(Fixture fixture) {
        assertThat(fixture.credentialUpdates).hasValue(0);
        assertThat(fixture.sessionService.revokedAccounts).isEmpty();
        assertThat(fixture.events).isEmpty();
        assertThat(fixture.order).isEmpty();
    }

    private static Account account(String status) {
        var value = new Account();
        value.setId(17L);
        value.setStatus(status);
        return value;
    }

    private static Credential credential(String password) {
        var passwordService = new PasswordService();
        var hash = passwordService.hash(password);
        var value = new Credential();
        value.setId(27L);
        value.setAccountId(17L);
        value.setCredentialType("PASSWORD");
        value.setPasswordHash(hash.encoded());
        value.setPasswordAlgorithm(hash.algorithm());
        value.setPasswordParameters(hash.parameters());
        value.setFailedAttempts(4);
        value.setLockedUntil(LocalDateTime.now().plusHours(1));
        value.setPasswordChangedAt(LocalDateTime.now().minusDays(1));
        value.setUpdatedAt(LocalDateTime.now().minusDays(1));
        value.setVersion(3L);
        return value;
    }

    private static AuthenticatedSession session() {
        return new AuthenticatedSession(
                7L,
                17L,
                ContextType.SYSTEM,
                41L,
                51L,
                61L,
                1L,
                Set.of("system.runtime.access")
        );
    }

    private static ClientRequest client() {
        return new ClientRequest(
                "request-1", "trace-1", "127.0.0.1", "owner-test");
    }

    private static void assertCode(String code, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.code()).isEqualTo(code));
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                (instance, method, arguments) -> invocation.call(method.getName(), arguments)
        );
    }

    private static Object unexpected(String method) {
        throw new AssertionError("Unexpected mapper method: " + method);
    }

    private record Fixture(
            AccountPasswordService service,
            PasswordService passwordService,
            RecordingSessionService sessionService,
            Credential credential,
            AtomicInteger credentialUpdates,
            List<AuditEvent> events,
            List<String> order
    ) {
    }

    @FunctionalInterface
    private interface Invocation {
        Object call(String method, Object[] arguments);
    }

    private static final class RecordingSessionService extends SessionService {
        private final List<Long> revokedAccounts = new ArrayList<>();
        private final List<String> order;

        private RecordingSessionService(List<String> order) {
            super(
                    null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null
            );
            this.order = order;
        }

        @Override
        public void revokeAllForAccount(long accountId) {
            revokedAccounts.add(accountId);
            order.add("sessions");
        }
    }
}
