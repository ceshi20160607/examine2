package com.unique.examine.module.manage.security;

import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.core.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigSessionTest {
    @Test
    void requiresBothSystemAdminAndModuleConfigurationAuthority() {
        assertThatThrownBy(() -> ConfigSession.require(
                session(Set.of("system.admin.access")), 10))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("PERMISSION_DENIED"));

        assertThat(ConfigSession.require(session(Set.of(
                        "system.admin.access", "module.config.manage")), 10)
                .permissions()).contains(
                "system.admin.access", "module.config.manage");
    }

    private static RequestSession session(Set<String> permissions) {
        return new RequestSession() {
            @Override public long sessionId() { return 1; }
            @Override public long accountId() { return 2; }
            @Override public ContextType contextType() {
                return ContextType.SYSTEM;
            }
            @Override public Long systemId() { return 10L; }
            @Override public Long tenantId() { return 20L; }
            @Override public Long memberId() { return 30L; }
            @Override public long permissionVersion() { return 1; }
            @Override public Set<String> permissions() { return permissions; }
        };
    }
}
