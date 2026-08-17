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
class RuntimeMetadataMigrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("unexamine")
            .withUsername("unexamine")
            .withPassword("unexamine_test")
            .withCommand("--log-bin-trust-function-creators=1");

    @Test
    void runtimeMetadataUsesPublishedSnapshotsAndCannotCrossSystemOrTenantBoundaries() {
        var dataSource = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        var jdbc = new JdbcTemplate(dataSource);

        assertThat(jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_schema = database() and table_name not in ('flyway_schema_history')",
                Integer.class)).isEqualTo(27);

        Context first = createContext(jdbc, "owner-a", "system-a");
        Context second = createContext(jdbc, "owner-b", "system-b");

        jdbc.update("insert into cfg_module_group(system_id, owner_tenant_id, code, name, status, created_by_member_id) values (?, ?, 'sales', '销售管理', 'ACTIVE', ?)",
                first.systemId(), first.tenantId(), first.memberId());
        Long groupId = jdbc.queryForObject("select id from cfg_module_group where owner_tenant_id = ? and code = 'sales'", Long.class, first.tenantId());
        jdbc.update("insert into cfg_module(system_id, owner_tenant_id, group_id, code, name, status, created_by_member_id) values (?, ?, ?, 'customer', '客户', 'DRAFT', ?)",
                first.systemId(), first.tenantId(), groupId, first.memberId());
        Long moduleId = jdbc.queryForObject("select id from cfg_module where owner_tenant_id = ? and code = 'customer'", Long.class, first.tenantId());
        jdbc.update("insert into cfg_module_field(system_id, owner_tenant_id, module_id, code, name, field_type, required, status, config_json) values (?, ?, ?, 'name', '客户名称', 'TEXT', 1, 'ACTIVE', cast('{}' as json))",
                first.systemId(), first.tenantId(), moduleId);
        Long fieldId = jdbc.queryForObject("select id from cfg_module_field where module_id = ? and code = 'name'", Long.class, moduleId);
        jdbc.update("insert into cfg_module_page(system_id, owner_tenant_id, module_id, page_type, name, layout_json, status) values (?, ?, ?, 'LIST', '默认列表', cast('{}' as json), 'ACTIVE')",
                first.systemId(), first.tenantId(), moduleId);
        jdbc.update("insert into cfg_module_action(system_id, owner_tenant_id, module_id, code, name, location, status, config_json) values (?, ?, ?, 'CREATE', '新建', 'LIST_TOOLBAR', 'ACTIVE', cast('{}' as json))",
                first.systemId(), first.tenantId(), moduleId);
        jdbc.update("insert into cfg_module_version(system_id, owner_tenant_id, module_id, version_number, draft_revision, snapshot_json, published_by_member_id) values (?, ?, ?, 1, 1, cast(? as json), ?)",
                first.systemId(), first.tenantId(), moduleId, "{\"moduleCode\":\"customer\"}", first.memberId());
        Long versionId = jdbc.queryForObject("select id from cfg_module_version where module_id = ? and version_number = 1", Long.class, moduleId);
        jdbc.update("insert into cfg_module_publication(system_id, owner_tenant_id, module_id, current_version_id, updated_by_member_id) values (?, ?, ?, ?, ?)",
                first.systemId(), first.tenantId(), moduleId, versionId, first.memberId());
        jdbc.update("insert into biz_record(system_id, tenant_id, module_id, created_config_version_id, updated_config_version_id, title, status, created_by_member_id, updated_by_member_id) values (?, ?, ?, ?, ?, '示例客户', 'ACTIVE', ?, ?)",
                first.systemId(), first.tenantId(), moduleId, versionId, versionId, first.memberId(), first.memberId());
        Long recordId = jdbc.queryForObject("select id from biz_record where module_id = ?", Long.class, moduleId);
        jdbc.update("insert into biz_record_value(record_id, field_id, field_code, value_type, value_text) values (?, ?, 'name', 'TEXT', '示例客户')",
                recordId, fieldId);
        jdbc.update("insert into biz_record_participant(system_id, tenant_id, record_id, system_member_id, created_by_member_id) values (?, ?, ?, ?, ?)",
                first.systemId(), first.tenantId(), recordId, first.memberId(), first.memberId());

        assertThatThrownBy(() -> jdbc.update("update cfg_module_version set version_number = 2 where id = ?", versionId))
                .hasRootCauseInstanceOf(SQLException.class).hasMessageContaining("immutable");
        assertThatThrownBy(() -> jdbc.update(
                "insert into cfg_module_group(system_id, owner_tenant_id, code, name, status, created_by_member_id) values (?, ?, 'illegal', '非法跨系统', 'ACTIVE', ?)",
                first.systemId(), second.tenantId(), first.memberId()))
                .hasRootCauseInstanceOf(SQLException.class);
        assertThatThrownBy(() -> jdbc.update(
                "insert into biz_record(system_id, tenant_id, module_id, created_config_version_id, updated_config_version_id, title, status, created_by_member_id, updated_by_member_id) values (?, ?, ?, ?, ?, '非法跨租户', 'ACTIVE', ?, ?)",
                first.systemId(), second.tenantId(), moduleId, versionId, versionId, first.memberId(), first.memberId()))
                .hasRootCauseInstanceOf(SQLException.class);
        assertThatThrownBy(() -> jdbc.update(
                "insert into biz_record_participant(system_id, tenant_id, record_id, system_member_id, created_by_member_id) values (?, ?, ?, ?, ?)",
                first.systemId(), first.tenantId(), recordId, second.memberId(), first.memberId()))
                .hasRootCauseInstanceOf(SQLException.class);
        assertThat(jdbc.queryForObject("select value_text from biz_record_value where record_id = ?", String.class, recordId))
                .isEqualTo("示例客户");
    }

    private Context createContext(JdbcTemplate jdbc, String username, String systemCode) {
        jdbc.update("insert into plat_account(username, display_name, status) values (?, ?, 'ACTIVE')", username, username);
        Long accountId = jdbc.queryForObject("select id from plat_account where username = ?", Long.class, username);
        jdbc.update("insert into sys_system(code, name, creator_account_id, tenant_mode, status) values (?, ?, ?, 'SINGLE', 'ACTIVE')",
                systemCode, systemCode, accountId);
        Long systemId = jdbc.queryForObject("select id from sys_system where code = ?", Long.class, systemCode);
        jdbc.update("insert into sys_tenant(system_id, code, name, is_main, main_marker, creator_account_id, status) values (?, 'main', '默认主租户', 1, 'MAIN', ?, 'ACTIVE')",
                systemId, accountId);
        Long tenantId = jdbc.queryForObject("select id from sys_tenant where system_id = ?", Long.class, systemId);
        jdbc.update("insert into sys_member(system_id, account_id, display_name, status) values (?, ?, ?, 'ACTIVE')",
                systemId, accountId, username);
        Long memberId = jdbc.queryForObject("select id from sys_member where system_id = ?", Long.class, systemId);
        jdbc.update("insert into sys_tenant_member(system_id, tenant_id, system_member_id, tenant_admin, status) values (?, ?, ?, 1, 'ACTIVE')",
                systemId, tenantId, memberId);
        return new Context(systemId, tenantId, memberId);
    }

    private record Context(Long systemId, Long tenantId, Long memberId) {
    }
}
