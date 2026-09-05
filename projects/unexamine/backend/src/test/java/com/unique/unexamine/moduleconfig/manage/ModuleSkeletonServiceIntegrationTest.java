package com.unique.unexamine.moduleconfig.manage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.authorization.manage.DataScopeExpression;
import com.unique.unexamine.authorization.manage.DataScopeTerm;
import com.unique.unexamine.authorization.manage.PermissionGrant;
import com.unique.unexamine.platform.manage.registration.RegistrationRequest;
import com.unique.unexamine.platform.manage.registration.RegistrationResult;
import com.unique.unexamine.platform.manage.registration.RegistrationTransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ModuleSkeletonServiceIntegrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("unexamine")
            .withUsername("unexamine")
            .withPassword("unexamine_test")
            .withCommand("--log-bin-trust-function-creators=1");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("app.bootstrap.default-admin.deployment-key", () -> "module-skeleton-integration-2026");
    }

    @Autowired
    private RegistrationTransactionService registrationService;

    @Autowired
    private ModuleConfigurationService moduleConfigurationService;

    @Autowired
    private ModulePublicationService publicationService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void businessNamesCreateGroupModuleFieldDefaultsAndPublishedRuntimeWithoutTechnicalInputs() {
        RegistrationResult registration = registrationService.create(new RegistrationRequest(
                "module_owner", "correct-password", "模块管理员", "module-owner@example.com",
                "业务系统", null), "module-skeleton-registration");
        AuthenticatedContext context = systemContext(registration);

        ModuleDraft created = moduleConfigurationService.createModule(context,
                new CreateModuleRequest(null, "客户运营", null, "客户"), "module-skeleton-create");
        assertThat(created.module().getCode()).matches("module_[a-f0-9]{10}");
        assertThat(jdbc.queryForObject("select code from cfg_module_group where id = ?", String.class,
                created.module().getGroupId())).matches("group_[a-f0-9]{10}");
        assertThat(created.pages()).extracting(page -> page.getPageType())
                .containsExactly("LIST", "FORM", "DETAIL");
        assertThat(created.menus()).singleElement();
        assertThat(created.actions()).extracting(action -> action.getCode())
                .contains("LIST", "DETAIL", "CREATE", "UPDATE", "DELETE", "IMPORT", "EXPORT");

        var fieldConfig = objectMapper.createObjectNode();
        fieldConfig.put("placeholder", "请输入客户名称");
        var field = moduleConfigurationService.createField(context, created.module().getId(),
                new CreateModuleFieldRequest(null, "客户名称", "TEXT", true, false, true,
                        null, null, 10, fieldConfig), "module-skeleton-field");
        assertThat(field.getCode()).matches("field_[a-f0-9]{10}");

        PublicationCheckResult check = publicationService.check(context, created.module().getId());
        assertThat(check.valid()).isTrue();
        PublishedModuleResult published = publicationService.publish(context, created.module().getId(),
                new PublishModuleRequest(check.draftRevision()), "module-skeleton-publish");
        assertThat(published.versionNumber()).isOne();

        RuntimeModuleConfiguration runtime = publicationService.published(context, created.module().getCode());
        assertThat(runtime.versionNumber()).isOne();
        assertThat(runtime.configuration().path("module").path("name").asText()).isEqualTo("客户");
        assertThat(runtime.configuration().path("group").path("name").asText()).isEqualTo("客户运营");
        assertThat(runtime.configuration().path("fields").size()).isOne();
        assertThat(runtime.configuration().path("fields").path(0).path("name").asText()).isEqualTo("客户名称");
    }

    private AuthenticatedContext systemContext(RegistrationResult registration) {
        long memberId = jdbc.queryForObject(
                "select id from sys_member where system_id = ? and account_id = ?",
                Long.class, registration.systemId(), registration.accountId());
        long tenantMemberId = jdbc.queryForObject(
                "select id from sys_tenant_member where tenant_id = ? and system_member_id = ?",
                Long.class, registration.tenantId(), memberId);
        long roleId = jdbc.queryForObject(
                "select id from sys_role where tenant_id = ? and code = 'SYSTEM_SUPER_ADMIN'",
                Long.class, registration.tenantId());
        return new AuthenticatedContext(null, registration.accountId(),
                jdbc.queryForObject("select platform_id from sys_system where id = ?", Long.class, registration.systemId()),
                registration.systemId(), registration.tenantId(), memberId, tenantMemberId,
                "module_owner", "模块管理员", "NONE", List.of(roleId),
                List.of(new PermissionGrant("*", "*", "*", List.of(roleId))),
                Map.of("*:*:*", new DataScopeExpression("ALL",
                        List.of(new DataScopeTerm("ALL", com.fasterxml.jackson.databind.node.NullNode.getInstance(),
                                List.of(roleId))))));
    }
}
