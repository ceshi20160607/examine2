package com.unique.examine.plat.manage.service;

import com.unique.examine.core.api.AuditEvent;
import com.unique.examine.core.api.AuditFacade;
import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.plat.api.AuthenticatedSession;
import com.unique.examine.plat.base.entity.Account;
import com.unique.examine.plat.base.mapper.PlatAccountMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountProfileServiceTest {
    @Test
    void updatesOnlyTheAuthenticatedAccountWithOptimisticVersionAndAudits() {
        var account = account(7, 3);
        var events = new ArrayList<AuditEvent>();
        var mapper = mapper(account, true);
        var service = new AccountProfileService(mapper, events::add);
        var session = new AuthenticatedSession(90, 7, ContextType.PLATFORM, null, null, null, 1, Set.of());

        var result = service.update(session, new AccountProfileService.UpdateCommand(
                "New Name", "Member@Example.com", "+86 138-0000-0000", "zh-CN", "Asia/Shanghai", 3L),
                new ClientRequest("request-1", "trace-1", "127.0.0.1", "test"));

        assertThat(result.id()).isEqualTo("7");
        assertThat(account.getDisplayName()).isEqualTo("New Name");
        assertThat(account.getEmailNormalized()).isEqualTo("member@example.com");
        assertThat(events).singleElement().extracting(AuditEvent::eventType).isEqualTo("ACCOUNT_PROFILE_UPDATE");
    }

    @Test
    void rejectsStaleVersionBeforeWriting() {
        var account = account(7, 4);
        var service = new AccountProfileService(mapper(account, false), event -> { });
        var session = new AuthenticatedSession(90, 7, ContextType.PLATFORM, null, null, null, 1, Set.of());

        assertThatThrownBy(() -> service.update(session, new AccountProfileService.UpdateCommand(
                "Name", null, null, null, null, 3L), new ClientRequest("r", "t", "ip", "ua")))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code()).isEqualTo("ACCOUNT_PROFILE_CONFLICT"));
    }

    private static Account account(long id, long version) {
        var account = new Account();
        account.setId(id); account.setUsername("member"); account.setDisplayName("Member");
        account.setStatus("ACTIVE"); account.setVersion(version); account.setUpdatedAt(LocalDateTime.now());
        return account;
    }

    private static PlatAccountMapper mapper(Account account, boolean allowUpdate) {
        return (PlatAccountMapper) Proxy.newProxyInstance(PlatAccountMapper.class.getClassLoader(),
                new Class<?>[]{PlatAccountMapper.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "selectById" -> account;
                    case "updateById" -> allowUpdate ? 1 : 0;
                    case "toString" -> "AccountMapperStub";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
