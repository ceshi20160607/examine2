package com.unique.examine.web.flyway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Repairs common schema gaps caused by old manual SQL installs or Flyway baselines.
 */
public final class SchemaCompatibilityRepair {

    private static final Logger log = LoggerFactory.getLogger(SchemaCompatibilityRepair.class);

    private SchemaCompatibilityRepair() {
    }

    public static void repair(DataSource dataSource) {
        try (Connection conn = dataSource.getConnection()) {
            repairModuleApp(conn);
            repairModuleMenu(conn);
            repairModuleField(conn);
            repairModuleDept(conn);
            repairModuleRecordData(conn);
            repairModuleRole(conn);
            repairFlowTask(conn);
            repairOpenApiCredential(conn);
        } catch (Exception e) {
            throw new IllegalStateException("Schema compatibility repair failed: " + e.getMessage(), e);
        }
    }

    private static void repairModuleApp(Connection conn) throws Exception {
        execute(conn,
                "CREATE TABLE IF NOT EXISTS un_module_app ("
                        + "id BIGINT NOT NULL,"
                        + "system_id BIGINT NOT NULL,"
                        + "tenant_id BIGINT NOT NULL DEFAULT 0,"
                        + "app_code VARCHAR(64) NOT NULL,"
                        + "app_name VARCHAR(255) NOT NULL,"
                        + "icon_url VARCHAR(512) NULL,"
                        + "status TINYINT NOT NULL DEFAULT 1,"
                        + "published_flag TINYINT NOT NULL DEFAULT 0,"
                        + "remark VARCHAR(512) NULL,"
                        + "create_user_id BIGINT NULL,"
                        + "update_user_id BIGINT NULL,"
                        + "create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),"
                        + "update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),"
                        + "PRIMARY KEY (id),"
                        + "UNIQUE KEY uk_module_app_code (system_id, tenant_id, app_code),"
                        + "KEY idx_module_app_status (system_id, tenant_id, status, update_time)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
        addColumnIfMissing(conn, "un_module_app", "system_id",
                "ALTER TABLE un_module_app ADD COLUMN system_id BIGINT NOT NULL DEFAULT 0 AFTER id");
        addColumnIfMissing(conn, "un_module_app", "tenant_id",
                "ALTER TABLE un_module_app ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 0 AFTER system_id");
        addColumnIfMissing(conn, "un_module_app", "app_code",
                "ALTER TABLE un_module_app ADD COLUMN app_code VARCHAR(64) NOT NULL DEFAULT '' AFTER tenant_id");
        addColumnIfMissing(conn, "un_module_app", "app_name",
                "ALTER TABLE un_module_app ADD COLUMN app_name VARCHAR(255) NOT NULL DEFAULT '' AFTER app_code");
        addColumnIfMissing(conn, "un_module_app", "icon_url",
                "ALTER TABLE un_module_app ADD COLUMN icon_url VARCHAR(512) NULL AFTER app_name");
        addColumnIfMissing(conn, "un_module_app", "status",
                "ALTER TABLE un_module_app ADD COLUMN status TINYINT NOT NULL DEFAULT 1 AFTER icon_url");
        addColumnIfMissing(conn, "un_module_app", "published_flag",
                "ALTER TABLE un_module_app ADD COLUMN published_flag TINYINT NOT NULL DEFAULT 0 AFTER status");
        addColumnIfMissing(conn, "un_module_app", "remark",
                "ALTER TABLE un_module_app ADD COLUMN remark VARCHAR(512) NULL AFTER published_flag");
        addColumnIfMissing(conn, "un_module_app", "create_user_id",
                "ALTER TABLE un_module_app ADD COLUMN create_user_id BIGINT NULL AFTER remark");
        addColumnIfMissing(conn, "un_module_app", "update_user_id",
                "ALTER TABLE un_module_app ADD COLUMN update_user_id BIGINT NULL AFTER create_user_id");
        addColumnIfMissing(conn, "un_module_app", "create_time",
                "ALTER TABLE un_module_app ADD COLUMN create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) AFTER update_user_id");
        addColumnIfMissing(conn, "un_module_app", "update_time",
                "ALTER TABLE un_module_app ADD COLUMN update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) AFTER create_time");
        addIndexIfMissing(conn, "un_module_app", "uk_module_app_code",
                "ALTER TABLE un_module_app ADD UNIQUE KEY uk_module_app_code (system_id, tenant_id, app_code)");
        addIndexIfMissing(conn, "un_module_app", "idx_module_app_status",
                "ALTER TABLE un_module_app ADD KEY idx_module_app_status (system_id, tenant_id, status, update_time)");
    }

    private static void repairModuleMenu(Connection conn) throws Exception {
        if (!tableExists(conn, "un_module_menu")) {
            return;
        }
        addColumnIfMissing(conn, "un_module_menu", "visible_flag",
                "ALTER TABLE un_module_menu ADD COLUMN visible_flag TINYINT NOT NULL DEFAULT 1 AFTER sort_no");
        addColumnIfMissing(conn, "un_module_menu", "perm_key",
                "ALTER TABLE un_module_menu ADD COLUMN perm_key VARCHAR(255) NULL AFTER visible_flag");
        addColumnIfMissing(conn, "un_module_menu", "api_pattern",
                "ALTER TABLE un_module_menu ADD COLUMN api_pattern VARCHAR(255) NULL AFTER perm_key");
        addColumnIfMissing(conn, "un_module_menu", "module_fields_json",
                "ALTER TABLE un_module_menu ADD COLUMN module_fields_json JSON NULL AFTER api_pattern");
        addIndexIfMissing(conn, "un_module_menu", "idx_module_menu_perm",
                "ALTER TABLE un_module_menu ADD KEY idx_module_menu_perm (app_id, perm_key)");
    }

    private static void repairModuleField(Connection conn) throws Exception {
        if (!tableExists(conn, "un_module_field")) {
            return;
        }
        addColumnIfMissing(conn, "un_module_field", "ref_model_id",
                "ALTER TABLE un_module_field ADD COLUMN ref_model_id BIGINT NULL AFTER dict_code");
        addColumnIfMissing(conn, "un_module_field", "ref_display_field",
                "ALTER TABLE un_module_field ADD COLUMN ref_display_field VARCHAR(64) NULL AFTER ref_model_id");
        addColumnIfMissing(conn, "un_module_field", "config_json",
                "ALTER TABLE un_module_field ADD COLUMN config_json JSON NULL AFTER ref_display_field");
        addColumnIfMissing(conn, "un_module_field", "relation_module_label",
                "ALTER TABLE un_module_field ADD COLUMN relation_module_label VARCHAR(128) NULL AFTER ref_display_field");
    }

    private static void repairModuleDept(Connection conn) throws Exception {
        execute(conn,
                "CREATE TABLE IF NOT EXISTS un_module_dept ("
                        + "id BIGINT NOT NULL,"
                        + "system_id BIGINT NOT NULL,"
                        + "tenant_id BIGINT NOT NULL DEFAULT 0,"
                        + "app_id BIGINT NOT NULL,"
                        + "parent_id BIGINT NOT NULL DEFAULT 0,"
                        + "depth VARCHAR(512) NULL,"
                        + "dept_code VARCHAR(64) NOT NULL,"
                        + "dept_name VARCHAR(128) NOT NULL,"
                        + "sort_no INT NOT NULL DEFAULT 0,"
                        + "status TINYINT NOT NULL DEFAULT 1,"
                        + "remark VARCHAR(255) NULL,"
                        + "create_user_id BIGINT NULL,"
                        + "update_user_id BIGINT NULL,"
                        + "create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),"
                        + "update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),"
                        + "PRIMARY KEY (id),"
                        + "UNIQUE KEY uk_module_dept_code (app_id, dept_code),"
                        + "KEY idx_module_dept_query (app_id, parent_id, status, sort_no)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
        addColumnIfMissing(conn, "un_module_dept", "depth",
                "ALTER TABLE un_module_dept ADD COLUMN depth VARCHAR(512) NULL AFTER parent_id");
        addIndexIfMissing(conn, "un_module_dept", "idx_module_dept_depth",
                "ALTER TABLE un_module_dept ADD KEY idx_module_dept_depth (app_id, depth(128))");
        if (tableExists(conn, "un_module_member")) {
            addColumnIfMissing(conn, "un_module_member", "dept_id",
                    "ALTER TABLE un_module_member ADD COLUMN dept_id BIGINT NULL AFTER role_id");
        }
    }

    private static void repairModuleRecordData(Connection conn) throws Exception {
        if (!tableExists(conn, "un_module_record_data")) {
            return;
        }
        addColumnIfMissing(conn, "un_module_record_data", "field_code",
                "ALTER TABLE un_module_record_data ADD COLUMN field_code VARCHAR(64) NOT NULL DEFAULT '' AFTER record_id");
        addColumnIfMissing(conn, "un_module_record_data", "value_text",
                "ALTER TABLE un_module_record_data ADD COLUMN value_text MEDIUMTEXT NULL AFTER field_code");
        addColumnIfMissing(conn, "un_module_record_data", "value_num",
                "ALTER TABLE un_module_record_data ADD COLUMN value_num DECIMAL(38,10) NULL AFTER value_text");
        addColumnIfMissing(conn, "un_module_record_data", "value_dt",
                "ALTER TABLE un_module_record_data ADD COLUMN value_dt DATETIME(3) NULL AFTER value_num");
        addIndexIfMissing(conn, "un_module_record_data", "idx_module_record_data_eq",
                "ALTER TABLE un_module_record_data ADD KEY idx_module_record_data_eq (model_id, field_code, value_text(128), record_id)");
        addIndexIfMissing(conn, "un_module_record_data", "idx_module_record_data_num",
                "ALTER TABLE un_module_record_data ADD KEY idx_module_record_data_num (model_id, field_code, value_num)");
        addIndexIfMissing(conn, "un_module_record_data", "idx_module_record_data_dt",
                "ALTER TABLE un_module_record_data ADD KEY idx_module_record_data_dt (model_id, field_code, value_dt)");
    }

    private static void repairModuleRole(Connection conn) throws Exception {
        if (!tableExists(conn, "un_module_role")) {
            return;
        }
        addColumnIfMissing(conn, "un_module_role", "data_scope",
                "ALTER TABLE un_module_role ADD COLUMN data_scope TINYINT NOT NULL DEFAULT 1 AFTER status");
    }

    private static void repairFlowTask(Connection conn) throws Exception {
        if (!tableExists(conn, "un_flow_task")) {
            return;
        }
        if (!columnExists(conn, "un_flow_task", "record_id")) {
            if (columnExists(conn, "un_flow_task", "instance_id")) {
                execute(conn, "ALTER TABLE un_flow_task CHANGE COLUMN instance_id record_id BIGINT NOT NULL");
            } else {
                execute(conn, "ALTER TABLE un_flow_task ADD COLUMN record_id BIGINT NOT NULL DEFAULT 0 AFTER tenant_id");
            }
        }
        if (!columnExists(conn, "un_flow_task", "node_key")) {
            if (columnExists(conn, "un_flow_task", "node_id")) {
                execute(conn, "ALTER TABLE un_flow_task CHANGE COLUMN node_id node_key VARCHAR(64) NOT NULL");
            } else {
                execute(conn, "ALTER TABLE un_flow_task ADD COLUMN node_key VARCHAR(64) NOT NULL DEFAULT '' AFTER record_id");
            }
        }
        addIndexIfMissing(conn, "un_flow_task", "idx_flow_task_record",
                "ALTER TABLE un_flow_task ADD KEY idx_flow_task_record (system_id, tenant_id, record_id, status, update_time)");
    }

    private static void repairOpenApiCredential(Connection conn) throws Exception {
        if (!tableExists(conn, "un_app_client_credential")) {
            return;
        }
        addColumnIfMissing(conn, "un_app_client_credential", "sign_secret_enc",
                "ALTER TABLE un_app_client_credential ADD COLUMN sign_secret_enc VARCHAR(512) NULL AFTER secret_hash");
    }

    private static void addColumnIfMissing(Connection conn, String table, String column, String ddl) throws Exception {
        if (!columnExists(conn, table, column)) {
            execute(conn, ddl);
            log.info("Schema repair: added column {}.{}", table, column);
        }
    }

    private static void addIndexIfMissing(Connection conn, String table, String index, String ddl) throws Exception {
        if (!indexExists(conn, table, index)) {
            execute(conn, ddl);
            log.info("Schema repair: added index {}.{}", table, index);
        }
    }

    private static boolean tableExists(Connection conn, String table) throws Exception {
        try (ResultSet rs = conn.getMetaData().getTables(conn.getCatalog(), null, table, new String[]{"TABLE"})) {
            return rs.next();
        }
    }

    private static boolean columnExists(Connection conn, String table, String column) throws Exception {
        try (ResultSet rs = conn.getMetaData().getColumns(conn.getCatalog(), null, table, column)) {
            return rs.next();
        }
    }

    private static boolean indexExists(Connection conn, String table, String index) throws Exception {
        try (ResultSet rs = conn.getMetaData().getIndexInfo(conn.getCatalog(), null, table, false, false)) {
            while (rs.next()) {
                if (index.equalsIgnoreCase(rs.getString("INDEX_NAME"))) {
                    return true;
                }
            }
            return false;
        }
    }

    private static void execute(Connection conn, String sql) throws Exception {
        try (Statement st = conn.createStatement()) {
            st.execute(sql);
        }
    }
}
