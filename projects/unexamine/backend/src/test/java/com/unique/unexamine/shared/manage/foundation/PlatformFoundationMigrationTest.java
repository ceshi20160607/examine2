package com.unique.unexamine.shared.manage.foundation;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class PlatformFoundationMigrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("unexamine")
            .withUsername("unexamine")
            .withPassword("unexamine_test")
            .withCommand("--log-bin-trust-function-creators=1");

    @Test
    void migrationEnforcesTenantIdentityAndPermanentAuditAtTheDatabaseBoundary() {
        var dataSource = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("1").load().migrate();
        var jdbc = new JdbcTemplate(dataSource);

        Integer tableCount = jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_schema = database() and table_name not in ('flyway_schema_history')",
                Integer.class
        );
        assertThat(tableCount).isEqualTo(17);

        jdbc.update("insert into plat_account(username, display_name, status) values (?, ?, ?)", "owner", "Owner", "ACTIVE");
        Long accountId = jdbc.queryForObject("select id from plat_account where username = 'owner'", Long.class);
        jdbc.update("insert into sys_system(code, name, creator_account_id, tenant_mode, status) values (?, ?, ?, ?, ?)",
                "sales", "Sales", accountId, "SINGLE", "ACTIVE");
        Long systemId = jdbc.queryForObject("select id from sys_system where code = 'sales'", Long.class);
        jdbc.update("insert into sys_tenant(system_id, code, name, is_main, main_marker, creator_account_id, status) values (?, ?, ?, ?, ?, ?, ?)",
                systemId, "main", "Main tenant", true, "MAIN", accountId, "ACTIVE");
        Long tenantId = jdbc.queryForObject("select id from sys_tenant where system_id = ? and code = 'main'", Long.class, systemId);

        assertThatThrownBy(() -> jdbc.update(
                "insert into sys_tenant(system_id, code, name, is_main, main_marker, creator_account_id, status) values (?, ?, ?, ?, ?, ?, ?)",
                systemId, "second-main", "Second main", true, "MAIN", accountId, "ACTIVE"
        )).hasRootCauseInstanceOf(SQLException.class);
        assertThatThrownBy(() -> jdbc.update(
                "insert into sys_tenant(system_id, code, name, is_main, main_marker, creator_account_id, status) values (?, ?, ?, ?, ?, ?, ?)",
                systemId, "bad-marker", "Bad marker", true, null, accountId, "ACTIVE"
        )).hasRootCauseInstanceOf(SQLException.class);

        jdbc.update("insert into plat_account(username, display_name, status) values (?, ?, ?)", "other-owner", "Other Owner", "ACTIVE");
        Long otherAccountId = jdbc.queryForObject("select id from plat_account where username = 'other-owner'", Long.class);
        jdbc.update("insert into sys_system(code, name, creator_account_id, tenant_mode, status) values (?, ?, ?, ?, ?)",
                "finance", "Finance", otherAccountId, "SINGLE", "ACTIVE");
        Long otherSystemId = jdbc.queryForObject("select id from sys_system where code = 'finance'", Long.class);
        jdbc.update("insert into sys_member(system_id, account_id, display_name, status) values (?, ?, ?, ?)",
                otherSystemId, otherAccountId, "Other Owner", "ACTIVE");
        Long otherSystemMemberId = jdbc.queryForObject("select id from sys_member where system_id = ?", Long.class, otherSystemId);
        assertThatThrownBy(() -> jdbc.update(
                "insert into sys_tenant_member(system_id, tenant_id, system_member_id, tenant_admin, status) values (?, ?, ?, ?, ?)",
                systemId, tenantId, otherSystemMemberId, false, "ACTIVE"
        )).hasRootCauseInstanceOf(SQLException.class);

        jdbc.update("insert into audit_event(trace_id, actor_account_id, system_id, event_code, result_code, detail_json) values (?, ?, ?, ?, ?, cast(? as json))",
                "trace-001", accountId, systemId, "SYSTEM_CREATED", "SUCCESS", "{}");
        Long auditId = jdbc.queryForObject("select id from audit_event where trace_id = 'trace-001'", Long.class);

        assertThatThrownBy(() -> jdbc.update("update audit_event set result_code = 'CHANGED' where id = ?", auditId))
                .hasRootCauseInstanceOf(SQLException.class)
                .hasMessageContaining("immutable");
        assertThatThrownBy(() -> jdbc.update("delete from audit_event where id = ?", auditId))
                .hasRootCauseInstanceOf(SQLException.class)
                .hasMessageContaining("immutable");
        assertThat(jdbc.queryForObject("select count(*) from audit_event where id = ?", Integer.class, auditId)).isEqualTo(1);
    }
}
