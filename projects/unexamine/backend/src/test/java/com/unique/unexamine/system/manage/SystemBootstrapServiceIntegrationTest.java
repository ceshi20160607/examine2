package com.unique.unexamine.system.manage;

import com.unique.unexamine.authentication.manage.AuthenticatedContext;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SystemBootstrapServiceIntegrationTest {
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
        registry.add("app.bootstrap.default-admin.deployment-key", () -> "system-bootstrap-integration-2026");
    }

    @Autowired
    private RegistrationTransactionService registrationService;

    @Autowired
    private PlatformSystemCreationService platformSystemCreationService;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void registrationBootstrapsCompleteSingleTenantSystemWithoutTechnicalInputs() {
        RegistrationResult result = registrationService.create(new RegistrationRequest(
                "skeleton_owner",
                "correct-password",
                "系统创建人",
                "skeleton-owner@example.com",
                "客户管理系统",
                null), "bootstrap-registration");

        assertThat(result.systemCode()).matches("system_[a-f0-9]{10}");
        assertThat(result.tenantMode()).isEqualTo("SINGLE");
        assertThat(result.tenantName()).isEqualTo("默认主租户");
        assertThat(result.tokens().accessToken()).isNotBlank();
        assertThat(result.platformTokens().accessToken()).isNotBlank();
        assertThat(result.platformTokens().accessToken()).isNotEqualTo(result.tokens().accessToken());
        assertCompleteBootstrap(result.systemId(), result.tenantId(), "skeleton_owner");
    }

    @Test
    void platformCreationUsesSameBootstrapAndFailureRollsBackWholeAggregate() {
        long accountId = jdbc.queryForObject(
                "select id from un_plat_account where username = 'admin'", Long.class);
        long platformId = jdbc.queryForObject(
                "select platform_id from un_plat_member where account_id = ?", Long.class, accountId);
        AuthenticatedContext context = new AuthenticatedContext(
                null, accountId, platformId, null, null, null, null,
                "admin", "平台管理员", "NONE", List.of(), List.of(), Map.of());

        jdbc.execute("create trigger test_fail_bootstrap_role before insert on un_sys_role for each row "
                + "signal sqlstate '45000' set message_text = 'forced system role failure'");
        try {
            assertThatThrownBy(() -> platformSystemCreationService.create(
                    context, new CreateSystemRequest("rollback_skeleton", "回滚目标系统"), "bootstrap-rollback"))
                    .isInstanceOf(RuntimeException.class);
        } finally {
            jdbc.execute("drop trigger test_fail_bootstrap_role");
        }
        assertThat(count("un_sys_system", "code = 'rollback_skeleton'")).isZero();
        assertThat(count("un_sys_tenant", "system_id in (select id from un_sys_system where code = 'rollback_skeleton')"))
                .isZero();

        AccessibleSystem created = platformSystemCreationService.create(
                context, new CreateSystemRequest(null, "业务运营系统"), "bootstrap-platform-create");
        assertThat(created.systemCode()).matches("system_[a-f0-9]{10}");
        assertThat(created.tenantMode()).isEqualTo("SINGLE");
        assertThat(created.defaultTenantName()).isEqualTo("默认主租户");
        assertCompleteBootstrap(created.systemId(), created.defaultTenantId(), "admin");
    }

    private void assertCompleteBootstrap(long systemId, long tenantId, String username) {
        assertThat(count("un_sys_system", "id = " + systemId + " and status = 'ACTIVE' and tenant_mode = 'SINGLE'"))
                .isOne();
        assertThat(count("un_sys_system", "id = " + systemId
                + " and creator_account_id = (select id from un_plat_account where username = '" + username + "')"))
                .isOne();
        assertThat(count("un_sys_tenant", "id = " + tenantId + " and system_id = " + systemId
                + " and is_main = 1 and main_marker = 'MAIN' and status = 'ACTIVE'"))
                .isOne();
        assertThat(count("un_sys_department", "tenant_id = " + tenantId
                + " and parent_id is null and code = 'root' and name = '全公司' and path_code = '/root'"))
                .isOne();
        assertThat(count("un_sys_member", "system_id = " + systemId
                + " and account_id = (select id from un_plat_account where username = '" + username + "')"))
                .isOne();
        assertThat(count("un_sys_tenant_member", "tenant_id = " + tenantId
                + " and tenant_admin = 0 and department_id in "
                + "(select id from un_sys_department where tenant_id = " + tenantId + " and code = 'root')"))
                .isOne();
        assertThat(count("un_sys_role", "tenant_id = " + tenantId
                + " and code = 'SYSTEM_SUPER_ADMIN' and built_in = 1 and status = 'ACTIVE'"))
                .isOne();
        assertThat(jdbc.queryForObject(
                "select count(*) from un_sys_role_permission p join un_sys_role r on r.id = p.role_id "
                        + "where r.tenant_id = ? and p.resource_type = '*' and p.resource_code = '*' "
                        + "and p.action_code = '*' and p.data_scope_type = 'ALL'",
                Integer.class, tenantId)).isOne();
        // 当前所属人的最高权限由 un_sys_system.creator_account_id 判定，不绑定显式超管角色。
        assertThat(count("un_sys_member_role", "tenant_id = " + tenantId)).isZero();
    }

    private int count(String table, String condition) {
        return jdbc.queryForObject("select count(*) from " + table + " where " + condition, Integer.class);
    }
}
