package com.unique.examine.core.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.unique.examine.core.context.RequestContext;
import com.unique.examine.core.context.RequestContextHolder;
import com.unique.examine.core.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultPermissionServiceTest {

    private DefaultPermissionService service;

    @BeforeEach
    void setUp() {
        RequestContextHolder.set(RequestContext.builder()
                .systemId("100")
                .tenantId("300")
                .memberId("200")
                .build());
        service = new DefaultPermissionService(List.of(new FixedPermissionSnapshotProvider(permission())));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    @Test
    void shouldAllowConfiguredMenuAndOperation() {
        PermissionDecision decision = service.decide(PermissionCheck.builder()
                .menuCode("SYS_MANAGE")
                .operationCode("RECORD_EDIT")
                .build());

        assertThat(decision.isAllowed()).isTrue();
    }

    @Test
    void shouldDenyMissingOperation() {
        assertThatThrownBy(() -> service.requireOperation("RECORD_DELETE"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldAllowAllSystemOperationsForSystemSuperAdmin() {
        service = new DefaultPermissionService(List.of(new FixedPermissionSnapshotProvider(EffectivePermissionVO.builder()
                .systemId("100")
                .tenantId("300")
                .memberId("200")
                .roles(Set.of("1"))
                .menus(Set.of("SYS_MANAGE"))
                .operations(Set.of("SYS_MANAGE_ALL"))
                .openapiScopes(Set.of())
                .fieldPermissions(Map.of())
                .dataScopes(List.of())
                .version("1")
                .build())));

        assertThat(service.decide(PermissionCheck.builder()
                .operationCode("APP_CREATE")
                .build()).isAllowed()).isTrue();
        assertThat(service.decide(PermissionCheck.builder()
                .menuCode("ANY_MENU")
                .operationCode("MODULE_PUBLISH")
                .build()).isAllowed()).isTrue();
    }

    @Test
    void shouldEvaluateOpenApiScope() {
        service.requireOpenApiScope("record:read");

        assertThatThrownBy(() -> service.requireOpenApiScope("record:delete"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldDenyFieldWithoutWritablePermission() {
        assertThatThrownBy(() -> service.requireFieldWritable("readonly_field"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldEvaluateSelfAndCustomDataScope() {
        assertThat(service.decide(PermissionCheck.builder()
                .resourceType("MODULE")
                .resourceId("10")
                .targetMemberId("200")
                .build()).isAllowed()).isTrue();
        assertThat(service.decide(PermissionCheck.builder()
                .resourceType("MODULE")
                .resourceId("11")
                .targetMemberId("201")
                .build()).isAllowed()).isTrue();
        assertThat(service.decide(PermissionCheck.builder()
                .resourceType("MODULE")
                .resourceId("10")
                .targetMemberId("201")
                .build()).isAllowed()).isFalse();
    }

    private EffectivePermissionVO permission() {
        return EffectivePermissionVO.builder()
                .systemId("100")
                .tenantId("300")
                .memberId("200")
                .roles(Set.of("1"))
                .menus(Set.of("SYS_MANAGE"))
                .operations(Set.of("RECORD_EDIT"))
                .openapiScopes(Set.of("record:read"))
                .fieldPermissions(Map.of(
                        "write_field", FieldPermissionVO.builder()
                                .fieldCode("write_field")
                                .visible(true)
                                .writable(true)
                                .build(),
                        "readonly_field", FieldPermissionVO.builder()
                                .fieldCode("readonly_field")
                                .visible(true)
                                .writable(false)
                                .build()))
                .dataScopes(List.of(
                        DataScopeRuleVO.builder()
                                .resourceType("MODULE")
                                .resourceId("10")
                                .scopeType("SELF")
                                .build(),
                        DataScopeRuleVO.builder()
                                .resourceType("MODULE")
                                .resourceId("11")
                                .scopeType("CUSTOM")
                                .memberIds(Set.of("201"))
                                .build()))
                .version("1")
                .build();
    }

    private record FixedPermissionSnapshotProvider(EffectivePermissionVO permission)
            implements PermissionSnapshotProvider {

        @Override
        public boolean supports(RequestContext context) {
            return true;
        }

        @Override
        public EffectivePermissionVO load(RequestContext context) {
            return permission;
        }
    }
}
