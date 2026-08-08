package com.unique.examine.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.id.IdService;
import com.unique.examine.module.manage.service.DerivedFieldContractService;
import com.unique.examine.module.manage.service.RuntimeSchemaProjectionService;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class P4A1SchemaIntegrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4.10"))
            .withDatabaseName("examine2_p4_schema")
            .withUsername("examine_p4_schema")
            .withPassword("container-test-password");

    @Test
    void upgradesPopulatedV45ThroughP4C4AndEnforcesRuntimeValueConstraints() throws Exception {
        var location = "filesystem:" + migrationRoot().toString().replace('\\', '/');
        var throughV32 = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations(location)
                .target(MigrationVersion.fromVersion("3.2.0"))
                .load()
                .migrate();
        assertThat(throughV32.migrationsExecuted).isEqualTo(5);

        var throughV45 = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations(location)
                .target(MigrationVersion.fromVersion("4.5.0"))
                .load();
        assertThat(throughV45.migrate().migrationsExecuted).isEqualTo(6);

        try (var connection = connection()) {
            seedPublishedRecordParents(connection);
            insertRecord(connection, 9_300_000_000_000_001L, 9_200_000_000_000_001L);
            insertTextValue(connection, 9_400_000_000_000_001L, "TEXT", "Readable value", null, 0);
            insertTimeIndex(connection, 9_500_000_000_000_010L);
        }

        var throughP4C3 = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations(location)
                .target(MigrationVersion.fromVersion("4.9.0"))
                .load();
        assertThat(throughP4C3.migrate().migrationsExecuted).isEqualTo(4);
        long tableCountBeforeP4C4;
        try (var connection = connection()) {
            tableCountBeforeP4C4 = queryLong(connection, "SELECT COUNT(*) FROM information_schema.tables "
                    + "WHERE table_schema=DATABASE()");
        }

        var throughP4C4 = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations(location)
                .target(MigrationVersion.fromVersion("4.10.0"))
                .load();
        assertThat(throughP4C4.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(throughP4C4.migrate().migrationsExecuted).isZero();

        try (var connection = connection()) {
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM information_schema.tables "
                    + "WHERE table_schema=DATABASE()")).isEqualTo(tableCountBeforeP4C4);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM information_schema.tables "
                    + "WHERE table_schema=DATABASE() AND table_name='un_module_saved_view'"))
                    .isEqualTo(1);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM information_schema.tables "
                    + "WHERE table_schema=DATABASE() AND table_name IN ("
                    + "'un_module_runtime_schema_module','un_module_runtime_schema_field',"
                    + "'un_module_record_relation','un_module_sub_record','un_module_sub_value',"
                    + "'un_module_reference_state','un_module_reference_recalc_task')")).isEqualTo(7);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM un_module_runtime_schema_module "
                    + "WHERE system_id=9100000000000001 AND schema_version_id=9200000000000001 "
                    + "AND module_snapshot_id=9200000000000010")).isEqualTo(1);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM un_module_runtime_schema_field "
                    + "WHERE system_id=9100000000000001 AND schema_version_id=9200000000000001 "
                    + "AND module_snapshot_id=9200000000000010 AND field_snapshot_id=9200000000000020"))
                    .isEqualTo(1);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM information_schema.referential_constraints "
                    + "WHERE constraint_schema=DATABASE() AND table_name IN ("
                    + "'un_module_runtime_schema_module','un_module_runtime_schema_field',"
                    + "'un_module_record_relation','un_module_sub_record','un_module_sub_value',"
                    + "'un_module_reference_state') AND delete_rule<>'RESTRICT'"))
                    .isZero();
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM information_schema.tables "
                    + "WHERE table_schema=DATABASE() AND table_name LIKE 'un_module_record%'")) .isEqualTo(6);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM information_schema.columns "
                    + "WHERE table_schema=DATABASE() AND table_name='un_module_record_unique' "
                    + "AND column_name='currency_key' AND extra LIKE '%STORED GENERATED%'"))
                    .isEqualTo(1);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM information_schema.columns "
                    + "WHERE table_schema=DATABASE() AND table_name='un_module_record_value' "
                    + "AND column_name IN ('time_value','boolean_value','currency_code')")).isEqualTo(3);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM information_schema.columns "
                    + "WHERE table_schema=DATABASE() AND table_name='un_module_record_value' "
                    + "AND column_name IN ('encryption_key_version','hash_key_version')")).isEqualTo(2);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM information_schema.columns "
                    + "WHERE table_schema=DATABASE() AND table_name='un_module_record_value' "
                    + "AND column_name='encrypted_value' AND data_type='blob'")).isEqualTo(1);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM information_schema.columns "
                    + "WHERE table_schema=DATABASE() AND table_name='un_module_runtime_schema_field' "
                    + "AND column_name IN ('result_schema','evaluator_version','expression_checksum',"
                    + "'topological_rank','dependency_json')")).isEqualTo(5);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM information_schema.columns "
                    + "WHERE table_schema=DATABASE() AND table_name='un_module_record_value' "
                    + "AND column_name IN ('field_scope','result_schema','dependency_version_json',"
                    + "'evaluator_version','recalculation_state','failure_correlation_id')")).isEqualTo(6);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM information_schema.columns "
                    + "WHERE table_schema=DATABASE() AND table_name='un_module_record_index' "
                    + "AND column_name='time_value'")).isEqualTo(1);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM information_schema.columns "
                    + "WHERE table_schema=DATABASE() AND table_name='un_module_record_index' "
                    + "AND column_name IN ('hash_key_version','geohash','geo_lat','geo_lng','hash_version_key')"))
                    .isEqualTo(5);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM information_schema.columns "
                    + "WHERE table_schema=DATABASE() AND table_name='un_module_record_unique' "
                    + "AND column_name IN ('hash_key_version','hash_version_key')")).isEqualTo(2);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM un_module_record_value "
                    + "WHERE id=9400000000000001 AND string_value='Readable value'")).isEqualTo(1);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM un_module_record_index "
                    + "WHERE id=9500000000000010 AND value_kind='TIME' AND time_value='09:30:00'"))
                    .isEqualTo(1);
            projectDerivedSnapshot(connection);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM un_module_runtime_schema_field "
                    + "WHERE system_id=9100000000000001 AND schema_version_id=9200000000000003 "
                    + "AND field_type IN ('FORMULA','CALCULATED') AND evaluator_version=1 "
                    + "AND expression_checksum REGEXP '^[a-f0-9]{64}$' ")).isEqualTo(2);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM un_module_runtime_schema_field "
                    + "WHERE system_id=9100000000000001 AND schema_version_id=9200000000000003 "
                    + "AND field_snapshot_id=9200000000000033 AND result_schema='DECIMAL' "
                    + "AND topological_rank=1 "
                    + "AND JSON_UNQUOTE(JSON_EXTRACT(dependency_json,'$[0].fieldId'))='9200000000000032'"))
                    .isEqualTo(1);
            insertDerivedRuntimeField(connection, 9_200_000_000_000_021L, "DECIMAL", true);
            insertDerivedValue(connection, 9_400_000_000_000_011L, "DECIMAL", "12.50", "READY", null);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM un_module_record_value "
                    + "WHERE id=9400000000000011 AND field_type='FORMULA' AND result_schema='DECIMAL' "
                    + "AND decimal_value=12.50 AND recalculation_state='READY'")).isEqualTo(1);
            assertConstraintRejected(() -> insertDerivedValue(
                    connection, 9_400_000_000_000_012L, "STRING", "13.00", "READY", null));
            assertConstraintRejected(() -> insertDerivedValue(
                    connection, 9_400_000_000_000_013L, "DECIMAL", "13.00", "FAILED", null));
            assertConstraintRejected(() -> insertDerivedRuntimeField(
                    connection, 9_200_000_000_000_022L, "DECIMAL", false));
            insertSearchToken(connection, 9_600_000_000_000_001L, "RICH_TEXT");
            assertConstraintRejected(() -> insertSearchToken(
                    connection, 9_600_000_000_000_002L, "SECRET"));

            assertConstraintRejected(() -> insertRecord(
                    connection, 9_300_000_000_000_002L, 9_299_999_999_999_999L));

            assertConstraintRejected(() -> insertTextValue(
                    connection, 9_400_000_000_000_002L, "TEXT", "Duplicate value", null, 0));
            assertConstraintRejected(() -> insertTextValue(
                    connection, 9_400_000_000_000_003L, "TEXT", null, "12.50", 1));
            insertTextValue(connection, 9_400_000_000_000_005L, "PERCENT", null, "12.50", 1);
            assertConstraintRejected(() -> insertTextValue(
                    connection, 9_400_000_000_000_006L, "MONEY", null, "12.50", 2));
            insertSensitiveValue(connection, 9_400_000_000_000_007L, "IDENTITY", 2,
                    null, "enc-v1", "hash-v1");
            assertConstraintRejected(() -> insertSensitiveValue(
                    connection, 9_400_000_000_000_008L, "IDENTITY", 3,
                    "110101199001011237", "enc-v1", "hash-v1"));
            assertConstraintRejected(() -> insertSensitiveValue(
                    connection, 9_400_000_000_000_009L, "SECRET", 4,
                    null, null, null));
            assertConstraintRejected(() -> insertSensitiveValue(
                    connection, 9_400_000_000_000_010L, "URL", 5,
                    "https://example.com/", "enc-v1", "hash-v1"));
            insertHashIndex(connection, 9_500_000_000_000_001L, "hash-v1");
            assertConstraintRejected(() -> insertHashIndex(
                    connection, 9_500_000_000_000_002L, null));
            insertGeoIndex(connection, 9_500_000_000_000_003L, 39.9, 116.4);
            assertConstraintRejected(() -> insertGeoIndex(
                    connection, 9_500_000_000_000_004L, 91.0, 116.4));
            assertConstraintRejected(() -> insertValueForMissingRecord(connection));
        }

        var throughV89 = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations(location)
                .target(MigrationVersion.fromVersion("8.9.0"))
                .load();
        assertThat(throughV89.migrate().migrationsExecuted).isEqualTo(15);
        assertThat(throughV89.migrate().migrationsExecuted).isZero();
        try (var connection = connection()) {
            insertLegacyFlowInstanceBeforeV810(connection);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM un_flow_instance "
                    + "WHERE system_id=9100000000000001 AND tenant_id=9100000000000002 "
                    + "AND instance_id=9700000000000010 AND approver_id=9100000000000003 "
                    + "AND state_version=0 AND status='PENDING'")).isEqualTo(1);
        }

        var throughV810 = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations(location)
                .target(MigrationVersion.fromVersion("8.10.0"))
                .load();
        assertThat(throughV810.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(throughV810.migrate().migrationsExecuted).isZero();
        try (var connection = connection()) {
            advanceLegacyFlowInstanceBeforeV811(connection);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM un_flow_instance "
                    + "WHERE system_id=9100000000000001 AND tenant_id=9100000000000002 "
                    + "AND instance_id=9700000000000010 AND approver_id=9100000000000004 "
                    + "AND state_version=1 AND status='PENDING' "
                    + "AND JSON_LENGTH(approver_ids_json)=2")).isEqualTo(1);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM un_flow_history_event "
                    + "WHERE system_id=9100000000000001 AND tenant_id=9100000000000002 "
                    + "AND instance_id=9700000000000010 AND event_sequence=2 "
                    + "AND event_type='APPROVED' AND actor_id=9100000000000003 "
                    + "AND from_status='PENDING' AND to_status='PENDING'"))
                    .isEqualTo(1);
        }

        var throughV811 = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations(location)
                .target(MigrationVersion.fromVersion("8.11.0"))
                .load();
        assertThat(throughV811.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(throughV811.migrate().migrationsExecuted).isZero();
        try (var connection = connection()) {
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM un_flow_instance "
                    + "WHERE system_id=9100000000000001 AND tenant_id=9100000000000002 "
                    + "AND instance_id=9700000000000010 "
                    + "AND approver_id=9100000000000004 "
                    + "AND current_step_index=1 AND claim_state='CLAIMED' "
                    + "AND JSON_LENGTH(approver_ids_json)=2")).isEqualTo(1);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM un_flow_history_event "
                    + "WHERE system_id=9100000000000001 AND tenant_id=9100000000000002 "
                    + "AND instance_id=9700000000000010 AND event_sequence IN (1,2)"))
                    .isEqualTo(2);
        }

        var throughV815 = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations(location)
                .target(MigrationVersion.fromVersion("8.15.0"))
                .load();
        assertThat(throughV815.migrate().migrationsExecuted).isEqualTo(4);
        assertThat(throughV815.migrate().migrationsExecuted).isZero();
        try (var connection = connection()) {
            insertLegacyTriggerDispatchBeforeV817(connection);
        }

        var throughV817 = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations(location)
                .target(MigrationVersion.fromVersion("8.17.0"))
                .load();
        assertThat(throughV817.migrate().migrationsExecuted).isEqualTo(2);
        assertThat(throughV817.migrate().migrationsExecuted).isZero();

        var throughV819 = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations(location)
                .target(MigrationVersion.fromVersion("8.19.0"))
                .load();
        assertThat(throughV819.migrate().migrationsExecuted).isEqualTo(2);
        assertThat(throughV819.migrate().migrationsExecuted).isZero();

        var throughV820 = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations(location)
                .target(MigrationVersion.fromVersion("8.20.0"))
                .load();
        assertThat(throughV820.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(throughV820.migrate().migrationsExecuted).isZero();
        assertThat(throughV820.info().applied()).hasSize(42);

        var current = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations(location)
                .load();
        var expectedMigrationCount = migrationFileCount();
        assertThat(current.migrate().migrationsExecuted).isEqualTo(expectedMigrationCount - 42);
        assertThat(current.migrate().migrationsExecuted).isZero();
        assertThat(current.info().applied()).hasSize(expectedMigrationCount);
        try (var connection = connection()) {
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM information_schema.tables "
                    + "WHERE table_schema=DATABASE() AND table_name IN ("
                    + "'un_module_auto_number_sequence','un_module_record_history',"
                    + "'un_module_record_flow_state','un_module_record_flow_state_item',"
                    + "'un_collab_record_team','un_collab_record_team_member','un_collab_record_comment',"
                    + "'un_collab_record_comment_mention',"
                    + "'un_flow_definition_draft','un_flow_definition_version',"
                    + "'un_flow_instance','un_flow_history_event','un_flow_urge','un_flow_comment',"
                    + "'un_flow_copy_recipient','un_flow_trigger_dispatch',"
                    + "'un_flow_trigger_dispatch_instance','un_flow_periodic_schedule',"
                    + "'un_work_task','un_work_project','un_work_project_member',"
                    + "'un_work_daily_report','un_work_task_reminder',"
                    + "'un_work_ai_draft_execution','un_flow_ai_definition_draft_execution',"
                    + "'un_todo_item','un_todo_action_log',"
                    + "'un_event_message','un_file_object','un_file_reference',"
                    + "'un_module_favorite','un_module_recent','un_module_import_batch',"
                    + "'un_module_import_row','un_module_export_task',"
                    + "'un_module_print_template','un_module_print_template_version','un_module_print_task',"
                    + "'un_module_ai_generated_draft_execution',"
                    + "'un_event_message_template','un_event_message_template_version',"
                    + "'un_event_message_delivery_log',"
                    + "'un_flow_decision_comment_template',"
                    + "'un_flow_decision_comment_template_version',"
                    + "'un_flow_decision_evidence',"
                    + "'un_flow_decision_evidence_file',"
                    + "'un_flow_completion_execution',"
                    + "'un_flow_completion_attempt',"
                    + "'un_flow_subflow_run',"
                    + "'un_flow_completion_compensation',"
                    + "'un_flow_compensation_attempt',"
                    + "'un_flow_compensation_subflow_run',"
                    + "'un_module_data_source','un_module_data_source_version',"
                    + "'un_module_dashboard','un_module_dashboard_version',"
                    + "'un_module_dashboard_version_widget',"
                    + "'un_module_kpi','un_module_kpi_version',"
                    + "'un_module_kpi_target','un_module_kpi_calculation',"
                    + "'un_module_kpi_calculation_member',"
                    + "'un_module_report','un_module_report_version',"
                    + "'un_module_report_version_field',"
                    + "'un_module_report_export_run',"
                    + "'un_module_report_schedule','un_module_report_schedule_recipient',"
                    + "'un_module_report_schedule_occurrence',"
                    + "'un_module_report_schedule_occurrence_recipient',"
                    + "'un_module_report_schedule_delivery',"
                    + "'un_ai_provider','un_ai_agent_policy','un_ai_agent_policy_check',"
                    + "'un_ai_agent_policy_version','un_ai_agent_policy_publish',"
                    + "'un_ai_agent_session','un_ai_agent_turn','un_ai_agent_message',"
                    + "'un_ai_agent_tool_call','un_ai_agent_usage',"
                    + "'un_ai_agent_confirmation','un_ai_agent_confirmation_attempt',"
                    + "'un_ai_agent_confirmation_event',"
                    + "'un_ai_config_field_proposal','un_ai_config_field_attempt',"
                    + "'un_ai_config_field_event',"
                    + "'un_ai_config_artifact_proposal','un_ai_config_artifact_attempt',"
                    + "'un_ai_config_artifact_event',"
                    + "'un_ai_work_proposal','un_ai_work_attempt','un_ai_work_event',"
                    + "'un_ai_generated_draft_proposal','un_ai_generated_draft_attempt',"
                    + "'un_ai_generated_draft_event',"
                    + "'un_module_ai_fill_materialization','un_module_ai_fill_history',"
                    + "'un_ai_fill_proposal','un_ai_fill_attempt',"
                    + "'un_ai_fill_event',"
                    + "'un_platform_ai_provider','un_platform_ai_policy',"
                    + "'un_platform_ai_policy_check','un_platform_ai_policy_version',"
                    + "'un_platform_ai_policy_publish_replay',"
                    + "'un_platform_ai_session','un_platform_ai_message',"
                    + "'un_platform_ai_turn','un_platform_ai_usage',"
                    + "'un_platform_ai_evidence','un_platform_ai_audit_event',"
                    + "'un_platform_ai_quota_bucket','un_platform_task',"
                    + "'un_platform_ai_task_proposal',"
                    + "'un_platform_ai_task_proposal_attempt',"
                    + "'un_platform_ai_task_proposal_event')"))
                    .isEqualTo(117);
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.columns "
                            + "WHERE table_schema=DATABASE() "
                            + "AND table_name LIKE 'un_platform_ai_%' "
                            + "AND column_name IN ('system_id','tenant_id','member_id')"))
                    .isZero();
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.columns "
                            + "WHERE table_schema=DATABASE() "
                            + "AND table_name IN ('un_platform_task',"
                            + "'un_platform_ai_task_proposal',"
                            + "'un_platform_ai_task_proposal_attempt',"
                            + "'un_platform_ai_task_proposal_event') "
                            + "AND column_name IN ('system_id','tenant_id','member_id',"
                            + "'module_code','field_code','record_id')"))
                    .isZero();
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.columns "
                            + "WHERE table_schema=DATABASE() "
                            + "AND table_name='un_platform_task' AND ("
                            + "(column_name='updated_at' AND is_nullable='NO') OR "
                            + "(column_name IN ('completed_at','cancelled_at') "
                            + "AND is_nullable='YES') OR "
                            + "(column_name='version' AND is_nullable='NO' "
                            + "AND column_type LIKE '%unsigned%' "
                            + "AND column_default='0'))"))
                    .isEqualTo(4);
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.check_constraints "
                            + "WHERE constraint_schema=DATABASE() "
                            + "AND constraint_name='ck_platform_task_status' "
                            + "AND UPPER(check_clause) LIKE '%OPEN%' "
                            + "AND UPPER(check_clause) LIKE '%COMPLETED%' "
                            + "AND UPPER(check_clause) LIKE '%CANCELLED%'"))
                    .isOne();
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.check_constraints "
                            + "WHERE constraint_schema=DATABASE() "
                            + "AND constraint_name='ck_platform_task_lifecycle' "
                            + "AND UPPER(check_clause) LIKE '%UPDATED_AT%CREATED_AT%' "
                            + "AND UPPER(check_clause) LIKE "
                            + "'%COMPLETED_AT%IS NOT NULL%' "
                            + "AND UPPER(check_clause) LIKE "
                            + "'%CANCELLED_AT%IS NOT NULL%'"))
                    .isOne();
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.referential_constraints "
                            + "WHERE constraint_schema=DATABASE() "
                            + "AND constraint_name="
                            + "'fk_platform_ai_task_proposal_result' "
                            + "AND table_name='un_platform_ai_task_proposal' "
                            + "AND referenced_table_name='un_platform_task' "
                            + "AND delete_rule='RESTRICT'"))
                    .isOne();
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.check_constraints "
                            + "WHERE constraint_schema=DATABASE() "
                            + "AND constraint_name="
                            + "'ck_platform_ai_task_proposal_result' "
                            + "AND UPPER(check_clause) LIKE "
                            + "'%TASK_STATUS%OPEN%'"))
                    .isOne();
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.check_constraints "
                            + "WHERE constraint_schema=DATABASE() "
                            + "AND constraint_name IN ("
                            + "'ck_platform_ai_session_scope',"
                            + "'ck_platform_ai_turn_scope',"
                            + "'ck_platform_ai_message_scope',"
                            + "'ck_platform_ai_usage_scope',"
                            + "'ck_platform_ai_evidence_scope',"
                            + "'ck_platform_ai_audit_scope',"
                            + "'ck_platform_ai_task_proposal_scope',"
                            + "'ck_platform_ai_task_attempt_scope',"
                            + "'ck_platform_ai_task_event_scope')"))
                    .isEqualTo(9);
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.check_constraints "
                            + "WHERE constraint_schema=DATABASE() AND ("
                            + "(constraint_name='ck_platform_ai_turn_operation' "
                            + "AND check_clause LIKE '%PLATFORM_OPERATIONS_QUERY%') "
                            + "OR (constraint_name='ck_platform_ai_evidence_type' "
                            + "AND check_clause LIKE '%OPERATIONS_CLARIFICATION%' "
                            + "AND check_clause LIKE '%AGENT_ACTIVITY%'))"))
                    .isEqualTo(2);
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM un_plat_permission "
                            + "WHERE scope_type='PLATFORM' AND scope_key=0 "
                            + "AND system_id IS NULL AND status='ACTIVE' "
                            + "AND permission_code IN ("
                            + "'platform.ai.agent.use','platform.ai.policy.manage',"
                            + "'platform.task.read','platform.task.create',"
                            + "'platform.task.manage')"))
                    .isEqualTo(5);
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM un_plat_authz_epoch "
                            + "WHERE scope_type='PLATFORM' AND scope_key=0 "
                            + "AND system_id IS NULL AND epoch>=4"))
                    .isEqualTo(1);
            var platformRootRoleCount = queryLong(connection,
                    "SELECT COUNT(*) FROM un_plat_role "
                            + "WHERE scope_type='PLATFORM' AND scope_key=0 "
                            + "AND role_type='ROOT' AND status='ACTIVE' "
                            + "AND deleted_at IS NULL");
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM un_plat_role_permission grant_row "
                            + "JOIN un_plat_permission permission_row "
                            + "ON permission_row.id=grant_row.permission_id "
                            + "JOIN un_plat_role role_row "
                            + "ON role_row.id=grant_row.role_id "
                            + "WHERE grant_row.scope_type='PLATFORM' "
                            + "AND grant_row.scope_key=0 "
                            + "AND grant_row.effect='ALLOW' "
                            + "AND permission_row.permission_code="
                            + "'platform.task.manage' "
                            + "AND role_row.scope_type='PLATFORM' "
                            + "AND role_row.scope_key=0 "
                            + "AND role_row.role_type='ROOT' "
                            + "AND role_row.status='ACTIVE' "
                            + "AND role_row.deleted_at IS NULL"))
                    .isEqualTo(platformRootRoleCount);
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM un_plat_role_permission grant_row "
                            + "JOIN un_plat_permission permission_row "
                            + "ON permission_row.id=grant_row.permission_id "
                            + "JOIN un_plat_role role_row "
                            + "ON role_row.id=grant_row.role_id "
                            + "WHERE grant_row.scope_type='PLATFORM' "
                            + "AND grant_row.scope_key=0 "
                            + "AND permission_row.permission_code="
                            + "'platform.task.manage' "
                            + "AND role_row.role_type<>'ROOT'"))
                    .isZero();
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.tables "
                            + "WHERE table_schema=DATABASE() AND table_name IN ("
                            + "'un_module_dashboard',"
                            + "'un_module_dashboard_version',"
                            + "'un_module_dashboard_version_widget')"))
                    .isEqualTo(3);
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.columns "
                            + "WHERE table_schema=DATABASE() AND table_name IN ("
                            + "'un_module_dashboard',"
                            + "'un_module_dashboard_version',"
                            + "'un_module_dashboard_version_widget')"))
                    .isEqualTo(81);
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.columns "
                            + "WHERE table_schema=DATABASE() AND ((table_name='un_module_dashboard' "
                            + "AND column_name='system_home_singleton') OR "
                            + "(table_name='un_module_dashboard_version_widget' AND column_name IN "
                            + "('refresh_seconds','click_through','style_variant')))"))
                    .isEqualTo(4);
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.referential_constraints "
                            + "WHERE constraint_schema=DATABASE() "
                            + "AND delete_rule='RESTRICT' AND constraint_name IN ("
                            + "'fk_module_dashboard_tenant',"
                            + "'fk_module_dashboard_version_root',"
                            + "'fk_module_dashboard_version_publisher',"
                            + "'fk_module_dashboard_widget_version',"
                            + "'fk_module_dashboard_widget_source_version',"
                            + "'fk_module_dashboard_widget_kpi_version',"
                            + "'fk_module_dashboard_active_version')"))
                    .isEqualTo(7);
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.key_column_usage "
                            + "WHERE constraint_schema=DATABASE() "
                            + "AND constraint_name="
                            + "'fk_module_dashboard_widget_source_version'"))
                    .isEqualTo(5);
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.key_column_usage "
                            + "WHERE constraint_schema=DATABASE() "
                            + "AND constraint_name="
                            + "'fk_module_dashboard_widget_kpi_version'"))
                    .isEqualTo(5);
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.check_constraints "
                            + "WHERE constraint_schema=DATABASE() "
                            + "AND constraint_name LIKE 'ck_module_dashboard%'")).isEqualTo(27);
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.check_constraints "
                            + "WHERE constraint_schema=DATABASE() AND constraint_name IN ("
                            + "'ck_module_dashboard_scope_identity',"
                            + "'ck_module_dashboard_scope_key',"
                            + "'ck_module_dashboard_scope_owner',"
                            + "'ck_module_dashboard_widget_behavior')")).isEqualTo(4);
            assertThat(queryLong(connection,
                    "SELECT COUNT(DISTINCT CONCAT(table_name,':',index_name)) "
                            + "FROM information_schema.statistics "
                            + "WHERE table_schema=DATABASE() AND table_name IN ("
                            + "'un_module_dashboard',"
                            + "'un_module_dashboard_version',"
                            + "'un_module_dashboard_version_widget')"))
                    .isEqualTo(18);
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.statistics "
                            + "WHERE table_schema=DATABASE() "
                            + "AND index_name IN ("
                            + "'uk_module_data_source_draft_publish',"
                            + "'uk_module_dashboard_draft_publish')"))
                    .isZero();
            assertThat(queryLong(connection,
                    "SELECT COUNT(DISTINCT index_name) "
                            + "FROM information_schema.statistics "
                            + "WHERE table_schema=DATABASE() "
                            + "AND table_name='un_module_dashboard' "
                            + "AND index_name='uk_module_dashboard_system_home' "
                            + "AND non_unique=0"))
                    .isEqualTo(1);
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.tables "
                            + "WHERE table_schema=DATABASE() AND table_name IN ("
                            + "'un_module_kpi','un_module_kpi_version',"
                            + "'un_module_kpi_target','un_module_kpi_calculation',"
                            + "'un_module_kpi_calculation_member')"))
                    .isEqualTo(5);
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.columns "
                            + "WHERE table_schema=DATABASE() AND table_name IN ("
                            + "'un_module_kpi','un_module_kpi_version',"
                            + "'un_module_kpi_target','un_module_kpi_calculation',"
                            + "'un_module_kpi_calculation_member')"))
                    .isEqualTo(116);
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.referential_constraints "
                            + "WHERE constraint_schema=DATABASE() "
                            + "AND delete_rule='RESTRICT' "
                            + "AND constraint_name LIKE 'fk_module_kpi%'"))
                    .isEqualTo(13);
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.check_constraints "
                            + "WHERE constraint_schema=DATABASE() "
                            + "AND constraint_name LIKE 'ck_module_kpi%'"))
                    .isEqualTo(27);
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.key_column_usage "
                            + "WHERE constraint_schema=DATABASE() "
                            + "AND constraint_name='fk_module_kpi_version_source'"))
                    .isEqualTo(5);
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.statistics "
                            + "WHERE table_schema=DATABASE() "
                            + "AND table_name='un_module_kpi_calculation' "
                            + "AND index_name='uk_module_kpi_calculation_running' "
                            + "AND non_unique=0"))
                    .isEqualTo(3);
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.tables "
                            + "WHERE table_schema=DATABASE() AND table_name IN ("
                            + "'un_module_report','un_module_report_version',"
                            + "'un_module_report_version_field')"))
                    .isEqualTo(3);
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.columns "
                            + "WHERE table_schema=DATABASE() AND table_name IN ("
                            + "'un_module_report','un_module_report_version',"
                            + "'un_module_report_version_field')"))
                    .isEqualTo(47);
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.referential_constraints "
                            + "WHERE constraint_schema=DATABASE() "
                            + "AND delete_rule='RESTRICT' "
                            + "AND constraint_name LIKE 'fk_module_report%'"))
                    .isEqualTo(21);
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.check_constraints "
                            + "WHERE constraint_schema=DATABASE() "
                            + "AND constraint_name LIKE 'ck_module_report%'"))
                    .isEqualTo(40);
            assertThat(queryLong(connection,
                    "SELECT COUNT(DISTINCT CONCAT(table_name,':',index_name)) "
                            + "FROM information_schema.statistics "
                            + "WHERE table_schema=DATABASE() AND table_name IN ("
                            + "'un_module_report','un_module_report_version',"
                            + "'un_module_report_version_field')"))
                    .isEqualTo(17);
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.key_column_usage "
                            + "WHERE constraint_schema=DATABASE() "
                            + "AND constraint_name="
                            + "'fk_module_report_version_source'"))
                    .isEqualTo(5);
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.tables "
                            + "WHERE table_schema=DATABASE() "
                            + "AND table_name='un_module_report_export_run'"))
                    .isEqualTo(1);
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.columns "
                            + "WHERE table_schema=DATABASE() "
                            + "AND table_name='un_module_report_export_run'"))
                    .isEqualTo(39);
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.referential_constraints "
                            + "WHERE constraint_schema=DATABASE() "
                            + "AND table_name='un_module_report_export_run' "
                            + "AND delete_rule='RESTRICT' "
                            + "AND constraint_name LIKE 'fk_module_report_export%'"))
                    .isEqualTo(4);
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.check_constraints "
                            + "WHERE constraint_schema=DATABASE() "
                            + "AND constraint_name LIKE 'ck_module_report_export%'"))
                    .isEqualTo(8);
            assertThat(queryLong(connection,
                    "SELECT COUNT(DISTINCT index_name) "
                            + "FROM information_schema.statistics "
                            + "WHERE table_schema=DATABASE() "
                            + "AND table_name='un_module_report_export_run'"))
                    .isEqualTo(9);
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.key_column_usage "
                            + "WHERE constraint_schema=DATABASE() "
                            + "AND constraint_name IN ("
                            + "'fk_module_report_export_version',"
                            + "'fk_module_report_export_source')"))
                    .isEqualTo(10);
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.tables "
                            + "WHERE table_schema=DATABASE() AND table_name IN ("
                            + "'un_module_report_schedule',"
                            + "'un_module_report_schedule_recipient',"
                            + "'un_module_report_schedule_occurrence',"
                            + "'un_module_report_schedule_occurrence_recipient',"
                            + "'un_module_report_schedule_delivery')"))
                    .isEqualTo(5);
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.columns "
                            + "WHERE table_schema=DATABASE() AND table_name IN ("
                            + "'un_module_report_schedule',"
                            + "'un_module_report_schedule_recipient',"
                            + "'un_module_report_schedule_occurrence',"
                            + "'un_module_report_schedule_occurrence_recipient',"
                            + "'un_module_report_schedule_delivery')"))
                    .isEqualTo(76);
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.referential_constraints "
                            + "WHERE constraint_schema=DATABASE() "
                            + "AND delete_rule='RESTRICT' "
                            + "AND constraint_name LIKE 'fk_module_report_schedule%'"))
                    .isEqualTo(11);
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.check_constraints "
                            + "WHERE constraint_schema=DATABASE() "
                            + "AND constraint_name LIKE 'ck_module_report_schedule%'"))
                    .isEqualTo(15);
            assertThat(queryLong(connection,
                    "SELECT COUNT(DISTINCT CONCAT(table_name,':',index_name)) "
                            + "FROM information_schema.statistics "
                            + "WHERE table_schema=DATABASE() AND table_name IN ("
                            + "'un_module_report_schedule',"
                            + "'un_module_report_schedule_recipient',"
                            + "'un_module_report_schedule_occurrence',"
                            + "'un_module_report_schedule_occurrence_recipient',"
                            + "'un_module_report_schedule_delivery')"))
                    .isEqualTo(31);
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.tables "
                            + "WHERE table_schema=DATABASE() AND table_name IN ("
                            + "'un_ai_provider','un_ai_agent_policy','un_ai_agent_policy_check',"
                            + "'un_ai_agent_policy_version','un_ai_agent_policy_publish',"
                            + "'un_ai_agent_session','un_ai_agent_turn','un_ai_agent_message',"
                            + "'un_ai_agent_tool_call','un_ai_agent_usage',"
                    + "'un_ai_agent_confirmation','un_ai_agent_confirmation_attempt',"
                    + "'un_ai_agent_confirmation_event',"
                    + "'un_ai_config_field_proposal','un_ai_config_field_attempt',"
                    + "'un_ai_config_field_event',"
                    + "'un_ai_config_artifact_proposal','un_ai_config_artifact_attempt',"
                    + "'un_ai_config_artifact_event',"
                    + "'un_ai_work_proposal','un_ai_work_attempt','un_ai_work_event',"
                    + "'un_ai_generated_draft_proposal','un_ai_generated_draft_attempt',"
                    + "'un_ai_generated_draft_event',"
                    + "'un_ai_fill_proposal','un_ai_fill_attempt',"
                    + "'un_ai_fill_event')"))
                    .isEqualTo(28);
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.check_constraints "
                            + "WHERE constraint_schema=DATABASE() "
                            + "AND constraint_name='ck_ai_agent_tool_call_name' "
                            + "AND UPPER(check_clause) LIKE '%RECORD_QUERY%' "
                            + "AND UPPER(check_clause) LIKE '%RECORD_CONTEXT_SUMMARY%' "
                            + "AND UPPER(check_clause) LIKE '%WORK_TASK_QUERY%' "
                            + "AND UPPER(check_clause) LIKE '%WORK_DAILY_REPORT_QUERY%' "
                            + "AND UPPER(check_clause) LIKE '%WORK_PROJECT_METRICS_QUERY%' "
                            + "AND UPPER(check_clause) LIKE '%RECORD_COMMENT_QUERY%' "
                            + "AND UPPER(check_clause) LIKE '%RECORD_HISTORY_QUERY%' "
                            + "AND UPPER(check_clause) LIKE '%RECORD_FILE_QUERY%' "
                            + "AND UPPER(check_clause) LIKE '%RUNTIME_STATISTICS_QUERY%' "
                            + "AND UPPER(check_clause) LIKE '%RUNTIME_REPORT_QUERY%'"))
                    .isOne();
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.columns "
                            + "WHERE table_schema=DATABASE() "
                            + "AND table_name='un_ai_config_field_proposal' "
                            + "AND column_name IN ('account_id','system_id','tenant_id',"
                            + "'member_id','session_id','turn_id','policy_version_id',"
                            + "'provider_id','provider_version','authorization_epoch',"
                            + "'sealed_ciphertext','sealed_key_version',"
                            + "'sealed_command_hash')"))
                    .isEqualTo(13);
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.referential_constraints "
                            + "WHERE constraint_schema=DATABASE() "
                            + "AND table_name IN ('un_ai_config_field_proposal',"
                            + "'un_ai_config_field_attempt','un_ai_config_field_event') "
                            + "AND delete_rule='RESTRICT' "
                            + "AND constraint_name LIKE 'fk_ai_config_field%'"))
                    .isEqualTo(7);
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.check_constraints "
                            + "WHERE constraint_schema=DATABASE() "
                            + "AND constraint_name IN ('ck_ai_config_field_payload',"
                            + "'ck_ai_config_field_result',"
                            + "'ck_ai_config_field_attempt_state',"
                            + "'ck_ai_config_field_event_type')"))
                    .isEqualTo(4);
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.columns "
                            + "WHERE table_schema=DATABASE() "
                            + "AND table_name IN ('un_ai_config_field_proposal',"
                            + "'un_ai_config_field_attempt','un_ai_config_field_event') "
                            + "AND column_name IN ('raw_command','command_json','payload_json',"
                    + "'raw_prompt','raw_response','api_key','credential','token')"))
                    .isZero();
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.columns "
                            + "WHERE table_schema=DATABASE() "
                            + "AND table_name='un_ai_config_artifact_proposal' "
                            + "AND column_name IN ('account_id','system_id','tenant_id',"
                            + "'member_id','session_id','turn_id','policy_version_id',"
                            + "'provider_id','provider_version','authorization_epoch',"
                            + "'artifact_kind','sealed_ciphertext','sealed_key_version',"
                            + "'sealed_command_hash','request_id','trace_id')"))
                    .isEqualTo(16);
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.referential_constraints "
                            + "WHERE constraint_schema=DATABASE() "
                            + "AND table_name IN ('un_ai_config_artifact_proposal',"
                            + "'un_ai_config_artifact_attempt','un_ai_config_artifact_event') "
                            + "AND delete_rule='RESTRICT' "
                            + "AND constraint_name LIKE 'fk_ai_config_artifact%'"))
                    .isEqualTo(7);
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.check_constraints "
                            + "WHERE constraint_schema=DATABASE() "
                            + "AND constraint_name IN ('ck_ai_config_artifact_kind',"
                            + "'ck_ai_config_artifact_payload',"
                            + "'ck_ai_config_artifact_result',"
                            + "'ck_ai_config_artifact_attempt_state',"
                            + "'ck_ai_config_artifact_event_type')"))
                    .isEqualTo(5);
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.columns "
                            + "WHERE table_schema=DATABASE() "
                            + "AND table_name IN ('un_ai_config_artifact_proposal',"
                            + "'un_ai_config_artifact_attempt','un_ai_config_artifact_event') "
                            + "AND column_name IN ('raw_command','command_json','payload_json',"
                            + "'raw_prompt','raw_response','api_key','credential','token',"
                            + "'option_label','field_name','page_name','section_json')"))
                    .isZero();
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.columns "
                            + "WHERE table_schema=DATABASE() "
                            + "AND table_name='un_ai_work_proposal' "
                            + "AND column_name IN ('account_id','system_id','tenant_id',"
                            + "'member_id','session_id','turn_id','policy_version_id',"
                            + "'provider_id','provider_version','authorization_epoch',"
                            + "'operation','command_ciphertext','command_key_version',"
                            + "'command_hash','request_id','trace_id')"))
                    .isEqualTo(16);
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.referential_constraints "
                            + "WHERE constraint_schema=DATABASE() "
                            + "AND table_name IN ('un_ai_work_proposal',"
                            + "'un_ai_work_attempt','un_ai_work_event') "
                            + "AND delete_rule='RESTRICT' "
                            + "AND constraint_name LIKE 'fk_ai_work%'"))
                    .isEqualTo(7);
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.check_constraints "
                            + "WHERE constraint_schema=DATABASE() "
                            + "AND constraint_name IN ('ck_ai_work_proposal_operation',"
                            + "'ck_ai_work_proposal_payload','ck_ai_work_proposal_result',"
                            + "'ck_ai_work_attempt_state','ck_ai_work_event_type')"))
                    .isEqualTo(5);
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.columns "
                            + "WHERE table_schema=DATABASE() "
                            + "AND table_name IN ('un_ai_work_proposal',"
                            + "'un_ai_work_attempt','un_ai_work_event') "
                            + "AND column_name IN ('raw_command','command_json','payload_json',"
                            + "'raw_prompt','raw_response','api_key','credential','token',"
                            + "'title','description','completed_work','planned_work','blockers')"))
                    .isZero();
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.columns "
                            + "WHERE table_schema=DATABASE() "
                            + "AND table_name='un_work_ai_draft_execution' "
                            + "AND column_name IN ('id','system_id','tenant_id','member_id',"
                            + "'proposal_id','session_id','turn_id','account_id',"
                            + "'authorization_epoch','operation','policy_version_id',"
                            + "'provider_id','provider_version','prompt_version','expires_at',"
                            + "'prepare_request_id','prepare_trace_id','payload_hash',"
                            + "'idempotency_key','execute_request_id','execute_trace_id',"
                            + "'result_json','created_at','completed_at')"))
                    .isEqualTo(24);
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.referential_constraints "
                            + "WHERE constraint_schema=DATABASE() "
                            + "AND table_name='un_work_ai_draft_execution' "
                            + "AND delete_rule='RESTRICT'"))
                    .isOne();
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.check_constraints "
                            + "WHERE constraint_schema=DATABASE() "
                            + "AND constraint_name IN ("
                            + "'ck_work_ai_draft_execution_identity',"
                            + "'ck_work_ai_draft_execution_operation',"
                            + "'ck_work_ai_draft_execution_tokens',"
                            + "'ck_work_ai_draft_execution_hash',"
                            + "'ck_work_ai_draft_execution_result',"
                            + "'ck_work_ai_draft_execution_time')"))
                    .isEqualTo(6);
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.columns "
                            + "WHERE table_schema=DATABASE() "
                            + "AND table_name='un_ai_generated_draft_proposal' "
                            + "AND column_name IN ('account_id','system_id','tenant_id',"
                            + "'member_id','session_id','turn_id','policy_version_id',"
                            + "'provider_id','provider_version','authorization_epoch',"
                            + "'operation','command_ciphertext','command_key_version',"
                            + "'command_hash','request_id','trace_id')"))
                    .isEqualTo(16);
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.referential_constraints "
                            + "WHERE constraint_schema=DATABASE() "
                            + "AND table_name IN ('un_ai_generated_draft_proposal',"
                            + "'un_ai_generated_draft_attempt',"
                            + "'un_ai_generated_draft_event') "
                            + "AND delete_rule='RESTRICT' "
                            + "AND constraint_name LIKE 'fk_ai_generated_draft%'"))
                    .isEqualTo(7);
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.check_constraints "
                            + "WHERE constraint_schema=DATABASE() "
                            + "AND constraint_name IN ("
                            + "'ck_ai_generated_draft_proposal_operation',"
                            + "'ck_ai_generated_draft_proposal_payload',"
                            + "'ck_ai_generated_draft_proposal_result',"
                            + "'ck_ai_generated_draft_attempt_state',"
                            + "'ck_ai_generated_draft_event_type')"))
                    .isEqualTo(5);
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.columns "
                            + "WHERE table_schema=DATABASE() "
                            + "AND table_name IN ('un_ai_generated_draft_proposal',"
                            + "'un_ai_generated_draft_attempt',"
                            + "'un_ai_generated_draft_event') "
                            + "AND column_name IN ('raw_command','command_json',"
                            + "'payload_json','raw_prompt','raw_response','api_key',"
                            + "'credential','token','name','description','title',"
                            + "'footer','approver_member_ids','output_field_codes')"))
                    .isZero();
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.columns "
                            + "WHERE table_schema=DATABASE() "
                            + "AND table_name IN ("
                            + "'un_flow_ai_definition_draft_execution',"
                            + "'un_module_ai_generated_draft_execution') "
                            + "AND column_name IN ('id','system_id','tenant_id',"
                            + "'member_id','proposal_id','session_id','turn_id',"
                            + "'account_id','authorization_epoch','operation',"
                            + "'policy_version_id','provider_id','provider_version',"
                            + "'prompt_version','expires_at','prepare_request_id',"
                            + "'prepare_trace_id','payload_hash','idempotency_key',"
                            + "'execute_request_id','execute_trace_id','result_json',"
                            + "'created_at','completed_at')"))
                    .isEqualTo(48);
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.referential_constraints "
                            + "WHERE constraint_schema=DATABASE() "
                            + "AND table_name IN ("
                            + "'un_flow_ai_definition_draft_execution',"
                            + "'un_module_ai_generated_draft_execution') "
                            + "AND delete_rule='RESTRICT'"))
                    .isEqualTo(2);
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.table_constraints "
                            + "WHERE constraint_schema=DATABASE() "
                            + "AND table_name IN ("
                            + "'un_flow_ai_definition_draft_execution',"
                            + "'un_module_ai_generated_draft_execution') "
                            + "AND constraint_type='UNIQUE'"))
                    .isEqualTo(6);
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.tables "
                            + "WHERE table_schema=DATABASE() AND table_name IN ("
                            + "'un_module_ai_fill_materialization',"
                            + "'un_module_ai_fill_history')"))
                    .isEqualTo(2);
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.columns "
                            + "WHERE table_schema=DATABASE() "
                            + "AND table_name='un_module_ai_fill_materialization' "
                            + "AND column_name IN ('string_value','decimal_value',"
                            + "'date_value','datetime_value','boolean_value',"
                            + "'source_version_hash','confidence','proposal_id',"
                            + "'version')"))
                    .isEqualTo(9);
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.columns "
                            + "WHERE table_schema=DATABASE() "
                            + "AND table_name='un_ai_fill_proposal' "
                            + "AND column_name IN ('sealed_ciphertext','sealed_key_version',"
                            + "'sealed_command_hash')"))
                    .isEqualTo(3);
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.columns "
                            + "WHERE table_schema=DATABASE() "
                            + "AND table_name IN ('un_ai_fill_proposal',"
                            + "'un_module_ai_fill_materialization',"
                            + "'un_module_ai_fill_history') "
                            + "AND column_name IN ('raw_prompt','raw_response','secret_ref',"
                            + "'api_key','credential','token')"))
                    .isZero();
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.columns "
                            + "WHERE table_schema=DATABASE() "
                            + "AND table_name='un_ai_agent_confirmation' "
                            + "AND column_name IN ('sealed_ciphertext','sealed_key_version',"
                            + "'sealed_command_hash')"))
                    .isEqualTo(3);
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.columns "
                            + "WHERE table_schema=DATABASE() "
                            + "AND table_name='un_ai_agent_confirmation' "
                            + "AND column_name IN ('raw_command','command_json','payload_json',"
                            + "'raw_prompt','raw_response')"))
                    .isZero();
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.columns "
                            + "WHERE table_schema=DATABASE() "
                            + "AND table_name='un_ai_provider' "
                            + "AND column_name='secret_ref'"))
                    .isEqualTo(1);
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.columns "
                            + "WHERE table_schema=DATABASE() "
                            + "AND table_name='un_ai_provider' "
                            + "AND column_name IN ('api_key','secret','credential','token')"))
                    .isZero();
            assertThat(queryLong(connection,
                    "SELECT COUNT(*) FROM un_plat_permission "
                            + "WHERE system_id=9100000000000001 "
                            + "AND permission_code IN ('ai.policy.manage','ai.agent.use') "
                            + "AND status='ACTIVE'"))
                    .isEqualTo(2);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM information_schema.tables "
                    + "WHERE table_schema=DATABASE() AND table_name IN ("
                    + "'un_plat_member_manager_assignment',"
                    + "'un_plat_department_leader_assignment')"))
                    .isEqualTo(2);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM information_schema.columns "
                    + "WHERE table_schema=DATABASE() AND ("
                    + "(table_name='un_plat_member_manager_assignment' "
                    + "AND column_name='active_member_id' "
                    + "AND extra LIKE '%STORED GENERATED%') OR "
                    + "(table_name='un_plat_department_leader_assignment' "
                    + "AND column_name='active_department_id' "
                    + "AND extra LIKE '%STORED GENERATED%'))"))
                    .isEqualTo(2);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM information_schema.statistics "
                    + "WHERE table_schema=DATABASE() AND non_unique=0 AND ("
                    + "(table_name='un_plat_member_manager_assignment' "
                    + "AND index_name='uk_plat_member_manager_active') OR "
                    + "(table_name='un_plat_department_leader_assignment' "
                    + "AND index_name='uk_plat_department_leader_active'))"))
                    .isEqualTo(6);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM un_plat_permission "
                    + "WHERE system_id=9100000000000001 AND permission_code='module.schema_record.import' "
                    + "AND status='ACTIVE'")).isEqualTo(1);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM information_schema.check_constraints "
                    + "WHERE constraint_schema=DATABASE() "
                    + "AND constraint_name IN ('ck_flow_draft_trigger_binding',"
                    + "'ck_flow_version_trigger_binding') "
                    + "AND check_clause LIKE '%IMPORT_COMPLETED%'"))
                    .isEqualTo(2);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM information_schema.check_constraints "
                    + "WHERE constraint_schema=DATABASE() "
                    + "AND constraint_name IN ('ck_flow_draft_trigger_binding',"
                    + "'ck_flow_version_trigger_binding') "
                    + "AND check_clause LIKE '%PERIODIC%'"))
                    .isEqualTo(2);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM un_plat_permission "
                    + "WHERE system_id=9100000000000001 AND permission_code='module.schema_record.export' "
                    + "AND status='ACTIVE'")).isEqualTo(1);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM un_plat_permission "
                    + "WHERE system_id=9100000000000001 AND permission_code='module.schema_record.print' "
                    + "AND status='ACTIVE'")).isEqualTo(1);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM un_plat_permission "
                    + "WHERE system_id=9100000000000001 AND permission_code='event.template.manage' "
                    + "AND status='ACTIVE'")).isEqualTo(1);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM un_plat_permission "
                    + "WHERE system_id=9100000000000001 AND permission_code='module.config.manage' "
                    + "AND status='ACTIVE'")).isEqualTo(1);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM un_plat_role_permission rp "
                    + "JOIN un_plat_permission p ON p.id=rp.permission_id "
                    + "WHERE rp.role_id=9100000000000006 AND p.permission_code='module.config.manage' "
                    + "AND rp.effect='ALLOW'")).isEqualTo(1);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM un_plat_role_permission rp "
                    + "JOIN un_plat_permission p ON p.id=rp.permission_id "
                    + "WHERE rp.role_id=9100000000000006 AND p.permission_code='event.template.manage' "
                    + "AND rp.effect='ALLOW'")).isEqualTo(1);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM un_plat_role_permission rp "
                    + "JOIN un_plat_permission p ON p.id=rp.permission_id "
                    + "WHERE rp.role_id=9100000000000006 AND p.permission_code='module.schema_record.print' "
                    + "AND rp.effect='ALLOW'")).isEqualTo(1);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM un_plat_permission "
                    + "WHERE system_id=9100000000000001 AND (permission_code LIKE 'flow.%' "
                    + "OR permission_code LIKE 'work.%' OR permission_code='event.message.access' "
                    + "OR permission_code LIKE 'file.%')")).isEqualTo(31);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM un_plat_permission "
                    + "WHERE system_id=9100000000000001 "
                    + "AND permission_code IN ('flow.instance.transfer','flow.instance.add-sign') "
                    + "AND status='ACTIVE'")).isEqualTo(2);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM un_plat_permission "
                    + "WHERE system_id=9100000000000001 "
                    + "AND permission_code IN ('flow.instance.return','flow.instance.claim',"
                    + "'flow.instance.cancel-claim') AND status='ACTIVE'")).isEqualTo(3);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM un_plat_permission "
                    + "WHERE system_id=9100000000000001 "
                    + "AND permission_code IN ('flow.instance.reduce-sign','flow.instance.copy') "
                    + "AND status='ACTIVE'")).isEqualTo(2);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM information_schema.columns "
                    + "WHERE table_schema=DATABASE() AND ("
                    + "(table_name='un_flow_instance' AND column_name='approver_ids_json') OR "
                    + "(table_name='un_flow_history_event' "
                    + "AND column_name IN ('target_member_id','assignment_position')))"))
                    .isEqualTo(3);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM information_schema.columns "
                    + "WHERE table_schema=DATABASE() AND table_name='un_flow_instance' "
                    + "AND column_name='approver_ids_json' AND data_type='json' AND is_nullable='NO'"))
                    .isEqualTo(1);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM information_schema.columns "
                    + "WHERE table_schema=DATABASE() AND table_name='un_flow_instance' "
                    + "AND column_name IN ('current_step_index','claim_state') "
                    + "AND is_nullable='NO'")).isEqualTo(2);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM information_schema.columns "
                    + "WHERE table_schema=DATABASE() "
                    + "AND table_name IN ('un_flow_definition_draft','un_flow_definition_version') "
                    + "AND column_name='approval_stages' AND data_type='json' "
                    + "AND is_nullable='YES'")).isEqualTo(2);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM information_schema.columns "
                    + "WHERE table_schema=DATABASE() AND table_name='un_flow_instance' AND ("
                    + "(column_name='approval_stage_state' AND data_type='json' "
                    + "AND is_nullable='YES') OR "
                    + "(column_name='current_stage_index' AND data_type='int' "
                    + "AND column_type='int unsigned' AND is_nullable='NO' "
                    + "AND column_default='0'))")).isEqualTo(2);
            assertThat(queryLong(connection, "SELECT COUNT(*) "
                    + "FROM information_schema.table_constraints table_constraint "
                    + "JOIN information_schema.check_constraints check_constraint "
                    + "ON check_constraint.constraint_schema=table_constraint.constraint_schema "
                    + "AND check_constraint.constraint_name=table_constraint.constraint_name "
                    + "WHERE table_constraint.constraint_schema=DATABASE() "
                    + "AND table_constraint.table_name IN "
                    + "('un_flow_definition_draft','un_flow_definition_version') "
                    + "AND UPPER(check_constraint.check_clause) LIKE '%APPROVAL_STAGES%' "
                    + "AND UPPER(check_constraint.check_clause) LIKE '%JSON_LENGTH%'"))
                    .isEqualTo(2);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM information_schema.columns "
                    + "WHERE table_schema=DATABASE() AND ("
                    + "(table_name='un_flow_instance' AND column_name='start_context' "
                    + "AND data_type='json' AND is_nullable='YES') OR "
                    + "(table_name='un_flow_parallel_branch_execution' "
                    + "AND column_name='approval_stage_state' "
                    + "AND data_type='json' AND is_nullable='YES') OR "
                    + "(table_name='un_flow_parallel_branch_execution' "
                    + "AND column_name='current_stage_index' "
                    + "AND data_type='int' AND column_type='int unsigned' "
                    + "AND is_nullable='NO' AND column_default='0'))"))
                    .isEqualTo(3);
            assertThat(queryLong(connection, "SELECT COUNT(*) "
                    + "FROM information_schema.table_constraints table_constraint "
                    + "JOIN information_schema.check_constraints check_constraint "
                    + "ON check_constraint.constraint_schema=table_constraint.constraint_schema "
                    + "AND check_constraint.constraint_name=table_constraint.constraint_name "
                    + "WHERE table_constraint.constraint_schema=DATABASE() AND ("
                    + "(table_constraint.table_name='un_flow_instance' "
                    + "AND table_constraint.constraint_name='ck_flow_instance_start_context' "
                    + "AND UPPER(check_constraint.check_clause) LIKE '%REQUESTERMEMBERID%' "
                    + "AND UPPER(check_constraint.check_clause) LIKE '%VALUESJSON%' "
                    + "AND UPPER(check_constraint.check_clause) LIKE '%ROOTINSTANCEID%' "
                    + "AND UPPER(check_constraint.check_clause) LIKE '%SUBFLOWDEPTH%') OR "
                    + "(table_constraint.table_name='un_flow_parallel_branch_execution' "
                    + "AND table_constraint.constraint_name IN ("
                    + "'ck_flow_parallel_branch_current_stage_index',"
                    + "'ck_flow_parallel_branch_approval_stage_state')))"))
                    .isEqualTo(3);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM information_schema.columns "
                    + "WHERE table_schema=DATABASE() AND ("
                    + "(table_name IN ('un_flow_definition_draft',"
                    + "'un_flow_definition_version') "
                    + "AND column_name='decision_evidence_policies' "
                    + "AND data_type='json' AND is_nullable='YES') OR "
                     + "(table_name IN ('un_flow_instance',"
                     + "'un_flow_parallel_branch_execution') "
                     + "AND column_name='decision_evidence_policy' "
                     + "AND data_type='json' AND is_nullable='YES'))"))
                     .isEqualTo(4);
            assertThat(queryLong(connection, "SELECT COUNT(*) "
                    + "FROM information_schema.table_constraints "
                    + "WHERE constraint_schema=DATABASE() "
                    + "AND table_name IN ('un_flow_decision_comment_template_version',"
                    + "'un_flow_decision_evidence','un_flow_decision_evidence_file') "
                    + "AND constraint_type='FOREIGN KEY'"))
                    .isGreaterThanOrEqualTo(3);
            assertThat(queryLong(connection, "SELECT COUNT(*) "
                    + "FROM information_schema.table_constraints table_constraint "
                    + "JOIN information_schema.check_constraints check_constraint "
                    + "ON check_constraint.constraint_schema=table_constraint.constraint_schema "
                    + "AND check_constraint.constraint_name=table_constraint.constraint_name "
                    + "WHERE table_constraint.constraint_schema=DATABASE() "
                    + "AND table_constraint.table_name='un_flow_instance' "
                    + "AND UPPER(check_constraint.check_clause) LIKE '%APPROVAL_STAGE_STATE%' "
                    + "AND UPPER(check_constraint.check_clause) LIKE '%CURRENT_STAGE_INDEX%'"))
                    .isEqualTo(1);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM information_schema.columns "
                    + "WHERE table_schema=DATABASE() AND table_name='un_flow_history_event' "
                    + "AND column_name='target_step_index' AND data_type='int' "
                    + "AND is_nullable='YES'")).isEqualTo(1);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM information_schema.columns "
                    + "WHERE table_schema=DATABASE() AND table_name='un_flow_instance' "
                    + "AND column_name IN ('module_code','record_id') AND is_nullable='YES'"))
                    .isEqualTo(2);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM information_schema.columns "
                    + "WHERE table_schema=DATABASE() "
                    + "AND table_name IN ('un_flow_definition_draft','un_flow_definition_version') "
                    + "AND column_name IN ('trigger_module_code','trigger_event',"
                    + "'trigger_priority','trigger_exclusive','trigger_conditions') "
                    + "AND is_nullable='YES'"))
                    .isEqualTo(10);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM information_schema.columns "
                    + "WHERE table_schema=DATABASE() "
                    + "AND table_name IN ('un_flow_definition_draft','un_flow_definition_version') "
                    + "AND column_name='trigger_conditions' AND data_type='json'"))
                    .isEqualTo(2);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM information_schema.columns "
                    + "WHERE table_schema=DATABASE() "
                    + "AND table_name IN "
                    + "('un_module_record_flow_state','un_module_record_flow_state_item') "
                    + "AND column_name IN ('status_field_code','status_approved_value',"
                    + "'status_rejected_value','status_withdrawn_value','status_terminated_value') "
                    + "AND is_nullable='YES'"))
                    .isEqualTo(10);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM information_schema.columns "
                    + "WHERE table_schema=DATABASE() "
                    + "AND table_name IN "
                    + "('un_flow_definition_draft','un_flow_definition_version') "
                    + "AND column_name IN ('status_field_code','status_approved_value',"
                    + "'status_rejected_value','status_withdrawn_value','status_terminated_value') "
                    + "AND is_nullable='YES'"))
                    .isEqualTo(10);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM un_flow_definition_draft "
                    + "WHERE trigger_module_code IS NOT NULL OR trigger_event IS NOT NULL "
                    + "OR trigger_priority IS NOT NULL OR trigger_exclusive IS NOT NULL "
                    + "OR trigger_conditions IS NOT NULL "
                    + "OR status_field_code IS NOT NULL OR status_approved_value IS NOT NULL "
                    + "OR status_rejected_value IS NOT NULL OR status_withdrawn_value IS NOT NULL "
                    + "OR status_terminated_value IS NOT NULL"))
                    .isZero();
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM un_flow_definition_version "
                    + "WHERE trigger_module_code IS NOT NULL OR trigger_event IS NOT NULL "
                    + "OR trigger_priority IS NOT NULL OR trigger_exclusive IS NOT NULL "
                    + "OR trigger_conditions IS NOT NULL "
                    + "OR status_field_code IS NOT NULL OR status_approved_value IS NOT NULL "
                    + "OR status_rejected_value IS NOT NULL OR status_withdrawn_value IS NOT NULL "
                    + "OR status_terminated_value IS NOT NULL"))
                    .isZero();
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM un_flow_instance "
                    + "WHERE system_id=9100000000000001 AND tenant_id=9100000000000002 "
                    + "AND instance_id=9700000000000010 "
                    + "AND approver_id=9100000000000004 "
                    + "AND current_step_index=1 AND claim_state='CLAIMED' "
                    + "AND module_code IS NULL AND record_id IS NULL "
                    + "AND JSON_LENGTH(approver_ids_json)=2 "
                    + "AND JSON_UNQUOTE(JSON_EXTRACT(approver_ids_json,'$[0]'))='9100000000000003' "
                    + "AND JSON_UNQUOTE(JSON_EXTRACT(approver_ids_json,'$[1]'))='9100000000000004'"))
                    .isEqualTo(1);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM un_flow_history_event "
                    + "WHERE system_id=9100000000000001 AND tenant_id=9100000000000002 "
                    + "AND instance_id=9700000000000010 AND event_sequence IN (1,2) "
                    + "AND target_member_id IS NULL AND assignment_position IS NULL "
                    + "AND target_step_index IS NULL"))
                    .isEqualTo(2);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM information_schema.tables "
                    + "WHERE table_schema=DATABASE() AND table_name='un_flow_copy_recipient' "
                    + "AND table_type='BASE TABLE'")).isEqualTo(1);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM ("
                    + "SELECT index_name FROM information_schema.statistics "
                    + "WHERE table_schema=DATABASE() AND table_name='un_flow_copy_recipient' "
                    + "AND non_unique=0 GROUP BY index_name,non_unique "
                    + "HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index)="
                    + "'system_id,tenant_id,instance_id,recipient_id') unique_indexes"))
                    .isEqualTo(1);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM ("
                    + "SELECT index_name FROM information_schema.statistics "
                    + "WHERE table_schema=DATABASE() AND table_name='un_flow_copy_recipient' "
                    + "AND non_unique=1 GROUP BY index_name,non_unique "
                    + "HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index)="
                    + "'system_id,tenant_id,instance_id,created_at,copy_id') page_indexes"))
                    .isEqualTo(1);
            assertThat(queryLong(connection, "SELECT COUNT(*) "
                    + "FROM information_schema.referential_constraints "
                    + "WHERE constraint_schema=DATABASE() "
                    + "AND table_name='un_flow_copy_recipient' "
                    + "AND referenced_table_name='un_flow_instance' "
                    + "AND delete_rule='RESTRICT'")).isEqualTo(1);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM information_schema.tables "
                    + "WHERE table_schema=DATABASE() "
                    + "AND table_name='un_module_record_flow_state' "
                    + "AND table_type='BASE TABLE'")).isEqualTo(1);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM ("
                    + "SELECT index_name FROM information_schema.statistics "
                    + "WHERE table_schema=DATABASE() "
                    + "AND table_name='un_module_record_flow_state' AND non_unique=0 "
                    + "GROUP BY index_name,non_unique "
                    + "HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index)="
                    + "'system_id,tenant_id,instance_id') unique_instance_indexes"))
                    .isEqualTo(1);
            assertThat(queryLong(connection, "SELECT COUNT(*) "
                    + "FROM information_schema.referential_constraints "
                    + "WHERE constraint_schema=DATABASE() "
                    + "AND table_name='un_module_record_flow_state' "
                    + "AND referenced_table_name='un_module_record' "
                    + "AND delete_rule='RESTRICT'")).isEqualTo(1);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM information_schema.tables "
                    + "WHERE table_schema=DATABASE() "
                    + "AND table_name='un_module_record_flow_state_item' "
                    + "AND table_type='BASE TABLE'")).isEqualTo(1);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM ("
                    + "SELECT index_name FROM information_schema.statistics "
                    + "WHERE table_schema=DATABASE() "
                    + "AND table_name='un_module_record_flow_state_item' AND index_name='PRIMARY' "
                    + "GROUP BY index_name "
                    + "HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index)="
                    + "'system_id,tenant_id,record_id,instance_id') scoped_items"))
                    .isEqualTo(1);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM ("
                    + "SELECT index_name FROM information_schema.statistics "
                    + "WHERE table_schema=DATABASE() "
                    + "AND table_name='un_module_record_flow_state_item' AND non_unique=1 "
                    + "GROUP BY index_name,non_unique "
                    + "HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index)="
                    + "'system_id,tenant_id,record_id,status,instance_id') pending_indexes"))
                    .isEqualTo(1);
            assertThat(queryLong(connection, "SELECT COUNT(*) "
                    + "FROM information_schema.referential_constraints "
                    + "WHERE constraint_schema=DATABASE() "
                    + "AND table_name='un_module_record_flow_state_item' "
                    + "AND referenced_table_name='un_module_record' "
                    + "AND delete_rule='RESTRICT'")).isEqualTo(1);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM information_schema.tables "
                    + "WHERE table_schema=DATABASE() "
                    + "AND table_name='un_flow_trigger_dispatch' "
                    + "AND table_type='BASE TABLE'")).isEqualTo(1);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM ("
                    + "SELECT index_name FROM information_schema.statistics "
                    + "WHERE table_schema=DATABASE() "
                    + "AND table_name='un_flow_trigger_dispatch' AND index_name='PRIMARY' "
                    + "GROUP BY index_name "
                    + "HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index)="
                    + "'system_id,tenant_id,event_key') scoped_event_keys"))
                    .isEqualTo(1);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM ("
                    + "SELECT index_name FROM information_schema.statistics "
                    + "WHERE table_schema=DATABASE() "
                    + "AND table_name='un_flow_trigger_dispatch' AND non_unique=0 "
                    + "GROUP BY index_name,non_unique "
                    + "HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index)="
                    + "'system_id,tenant_id,instance_id') unique_instance_indexes"))
                    .isEqualTo(1);
            assertThat(queryLong(connection, "SELECT COUNT(*) "
                    + "FROM information_schema.referential_constraints "
                    + "WHERE constraint_schema=DATABASE() "
                    + "AND table_name='un_flow_trigger_dispatch' "
                    + "AND referenced_table_name IN "
                    + "('un_flow_definition_version','un_flow_instance') "
                    + "AND delete_rule='RESTRICT'")).isEqualTo(2);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM information_schema.tables "
                    + "WHERE table_schema=DATABASE() "
                    + "AND table_name='un_flow_trigger_dispatch_instance' "
                    + "AND table_type='BASE TABLE'")).isEqualTo(1);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM ("
                    + "SELECT index_name FROM information_schema.statistics "
                    + "WHERE table_schema=DATABASE() "
                    + "AND table_name='un_flow_trigger_dispatch_instance' "
                    + "AND index_name='PRIMARY' GROUP BY index_name "
                    + "HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index)="
                    + "'system_id,tenant_id,event_key,ordinal') scoped_dispatch_items"))
                    .isEqualTo(1);
            assertThat(queryLong(connection, "SELECT COUNT(*) "
                    + "FROM information_schema.referential_constraints "
                    + "WHERE constraint_schema=DATABASE() "
                    + "AND table_name='un_flow_trigger_dispatch_instance' "
                    + "AND referenced_table_name IN ('un_flow_trigger_dispatch',"
                    + "'un_flow_definition_version','un_flow_instance') "
                    + "AND delete_rule='RESTRICT'")).isEqualTo(3);
            assertThat(queryLong(connection, "SELECT COUNT(*) "
                    + "FROM un_flow_trigger_dispatch_instance "
                    + "WHERE system_id=9100000000000001 AND tenant_id=9100000000000002 "
                    + "AND event_key='legacy-record-activated' AND ordinal=0 "
                    + "AND definition_id=9700000000000001 AND definition_version=1 "
                    + "AND instance_id=9700000000000010")).isEqualTo(1);
            assertThat(queryLong(connection, "SELECT COUNT(*) "
                    + "FROM information_schema.table_constraints table_constraint "
                    + "JOIN information_schema.check_constraints check_constraint "
                    + "ON check_constraint.constraint_schema=table_constraint.constraint_schema "
                    + "AND check_constraint.constraint_name=table_constraint.constraint_name "
                    + "WHERE table_constraint.constraint_schema=DATABASE() "
                    + "AND table_constraint.table_name='un_flow_instance' "
                    + "AND UPPER(check_constraint.check_clause) LIKE '%CURRENT_STEP_INDEX%'"))
                    .isGreaterThanOrEqualTo(1);
            assertThat(queryLong(connection, "SELECT COUNT(*) "
                    + "FROM information_schema.table_constraints table_constraint "
                    + "JOIN information_schema.check_constraints check_constraint "
                    + "ON check_constraint.constraint_schema=table_constraint.constraint_schema "
                    + "AND check_constraint.constraint_name=table_constraint.constraint_name "
                    + "WHERE table_constraint.constraint_schema=DATABASE() "
                    + "AND table_constraint.table_name='un_flow_instance' "
                    + "AND UPPER(check_constraint.check_clause) LIKE '%CLAIM_STATE%'"))
                    .isGreaterThanOrEqualTo(1);
            assertThat(queryLong(connection, "SELECT COUNT(*) "
                    + "FROM information_schema.table_constraints table_constraint "
                    + "JOIN information_schema.check_constraints check_constraint "
                    + "ON check_constraint.constraint_schema=table_constraint.constraint_schema "
                    + "AND check_constraint.constraint_name=table_constraint.constraint_name "
                    + "WHERE table_constraint.constraint_schema=DATABASE() "
                    + "AND table_constraint.table_name='un_flow_instance' "
                    + "AND UPPER(check_constraint.check_clause) LIKE '%MODULE_CODE%' "
                    + "AND UPPER(check_constraint.check_clause) LIKE '%RECORD_ID%'"))
                    .isGreaterThanOrEqualTo(1);
            assertThat(queryLong(connection, "SELECT COUNT(*) "
                    + "FROM information_schema.table_constraints table_constraint "
                    + "JOIN information_schema.check_constraints check_constraint "
                    + "ON check_constraint.constraint_schema=table_constraint.constraint_schema "
                    + "AND check_constraint.constraint_name=table_constraint.constraint_name "
                    + "WHERE table_constraint.constraint_schema=DATABASE() "
                    + "AND table_constraint.table_name IN "
                    + "('un_module_record_flow_state','un_module_record_flow_state_item') "
                    + "AND UPPER(check_constraint.check_clause) LIKE '%PENDING%' "
                    + "AND UPPER(check_constraint.check_clause) LIKE '%TERMINATED%'"))
                    .isGreaterThanOrEqualTo(2);
            assertThat(queryLong(connection, "SELECT COUNT(*) "
                    + "FROM information_schema.table_constraints table_constraint "
                    + "JOIN information_schema.check_constraints check_constraint "
                    + "ON check_constraint.constraint_schema=table_constraint.constraint_schema "
                    + "AND check_constraint.constraint_name=table_constraint.constraint_name "
                    + "WHERE table_constraint.constraint_schema=DATABASE() "
                    + "AND table_constraint.table_name IN "
                    + "('un_flow_definition_draft','un_flow_definition_version') "
                    + "AND UPPER(check_constraint.check_clause) LIKE '%TRIGGER_MODULE_CODE%' "
                    + "AND UPPER(check_constraint.check_clause) LIKE '%TRIGGER_EXCLUSIVE%'"))
                    .isEqualTo(2);
            assertThat(queryLong(connection, "SELECT COUNT(*) "
                    + "FROM information_schema.table_constraints table_constraint "
                    + "JOIN information_schema.check_constraints check_constraint "
                    + "ON check_constraint.constraint_schema=table_constraint.constraint_schema "
                    + "AND check_constraint.constraint_name=table_constraint.constraint_name "
                    + "WHERE table_constraint.constraint_schema=DATABASE() "
                    + "AND table_constraint.table_name IN "
                    + "('un_flow_definition_draft','un_flow_definition_version') "
                    + "AND table_constraint.constraint_name IN "
                    + "('ck_flow_draft_trigger_binding','ck_flow_version_trigger_binding') "
                    + "AND UPPER(check_constraint.check_clause) LIKE '%RECORD_ACTIVATED%' "
                    + "AND UPPER(check_constraint.check_clause) LIKE '%RECORD_CREATED%' "
                    + "AND UPPER(check_constraint.check_clause) LIKE '%RECORD_UPDATED%' "
                    + "AND UPPER(check_constraint.check_clause) LIKE '%RECORD_DELETED%' "
                    + "AND UPPER(check_constraint.check_clause) LIKE '%RECORD_STATUS_CHANGED%'"))
                    .isEqualTo(2);
            assertThat(queryLong(connection, "SELECT COUNT(*) "
                    + "FROM information_schema.table_constraints table_constraint "
                    + "JOIN information_schema.check_constraints check_constraint "
                    + "ON check_constraint.constraint_schema=table_constraint.constraint_schema "
                    + "AND check_constraint.constraint_name=table_constraint.constraint_name "
                    + "WHERE table_constraint.constraint_schema=DATABASE() "
                    + "AND table_constraint.table_name IN "
                    + "('un_flow_definition_draft','un_flow_definition_version') "
                    + "AND table_constraint.constraint_name IN "
                    + "('ck_flow_draft_trigger_status_mapping',"
                    + "'ck_flow_version_trigger_status_mapping') "
                    + "AND UPPER(check_constraint.check_clause) LIKE '%STATUS_FIELD_CODE%' "
                    + "AND UPPER(check_constraint.check_clause) LIKE '%TRIGGER_EVENT%' "
                    + "AND UPPER(check_constraint.check_clause) LIKE '%RECORD_ACTIVATED%'"))
                    .isEqualTo(2);
            assertExpandedTriggerConstraints(connection);
            assertThat(queryLong(connection, "SELECT COUNT(*) "
                    + "FROM information_schema.table_constraints table_constraint "
                    + "JOIN information_schema.check_constraints check_constraint "
                    + "ON check_constraint.constraint_schema=table_constraint.constraint_schema "
                    + "AND check_constraint.constraint_name=table_constraint.constraint_name "
                    + "WHERE table_constraint.constraint_schema=DATABASE() "
                    + "AND table_constraint.table_name IN "
                    + "('un_module_record_flow_state','un_module_record_flow_state_item') "
                    + "AND UPPER(check_constraint.check_clause) LIKE '%STATUS_FIELD_CODE%' "
                    + "AND UPPER(check_constraint.check_clause) LIKE '%STATUS_APPROVED_VALUE%' "
                    + "AND UPPER(check_constraint.check_clause) LIKE '%STATUS_TERMINATED_VALUE%'"))
                    .isEqualTo(4);
            assertThat(queryLong(connection, "SELECT COUNT(*) "
                    + "FROM information_schema.table_constraints table_constraint "
                    + "JOIN information_schema.check_constraints check_constraint "
                    + "ON check_constraint.constraint_schema=table_constraint.constraint_schema "
                    + "AND check_constraint.constraint_name=table_constraint.constraint_name "
                    + "WHERE table_constraint.constraint_schema=DATABASE() "
                    + "AND table_constraint.table_name IN "
                    + "('un_flow_definition_draft','un_flow_definition_version') "
                    + "AND UPPER(check_constraint.check_clause) LIKE '%STATUS_FIELD_CODE%' "
                    + "AND UPPER(check_constraint.check_clause) LIKE '%STATUS_APPROVED_VALUE%' "
                    + "AND UPPER(check_constraint.check_clause) LIKE '%STATUS_TERMINATED_VALUE%'"))
                    .isEqualTo(2);
            assertThat(queryLong(connection, "SELECT COUNT(*) "
                    + "FROM information_schema.table_constraints table_constraint "
                    + "JOIN information_schema.check_constraints check_constraint "
                    + "ON check_constraint.constraint_schema=table_constraint.constraint_schema "
                    + "AND check_constraint.constraint_name=table_constraint.constraint_name "
                    + "WHERE table_constraint.constraint_schema=DATABASE() "
                    + "AND table_constraint.table_name='un_flow_trigger_dispatch' "
                    + "AND UPPER(check_constraint.check_clause) LIKE '%DEFINITION_ID%' "
                    + "AND UPPER(check_constraint.check_clause) LIKE '%INSTANCE_ID%'"))
                    .isEqualTo(1);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM information_schema.check_constraints "
                    + "WHERE constraint_schema=DATABASE() "
                    + "AND constraint_name IN ('ck_flow_instance_status','ck_flow_instance_completion',"
                    + "'ck_flow_history_event') "
                    + "AND UPPER(check_clause) LIKE '%WITHDRAWN%' "
                    + "AND UPPER(check_clause) LIKE '%TERMINATED%'"))
                    .isEqualTo(3);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM information_schema.check_constraints "
                    + "WHERE constraint_schema=DATABASE() "
                    + "AND constraint_name='ck_flow_history_event' "
                    + "AND UPPER(check_clause) LIKE '%TRANSFERRED%' "
                    + "AND UPPER(check_clause) LIKE '%ADD_SIGNED%'"))
                    .isEqualTo(1);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM information_schema.check_constraints "
                    + "WHERE constraint_schema=DATABASE() "
                    + "AND constraint_name='ck_flow_history_event' "
                    + "AND UPPER(check_clause) LIKE '%RETURNED%' "
                    + "AND UPPER(check_clause) LIKE '%CLAIM_CANCELLED%' "
                    + "AND UPPER(check_clause) LIKE '%CLAIMED%'"))
                    .isEqualTo(1);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM information_schema.check_constraints "
                    + "WHERE constraint_schema=DATABASE() "
                    + "AND constraint_name='ck_flow_history_event' "
                    + "AND UPPER(check_clause) LIKE '%SIGN_REMOVED%' "
                    + "AND UPPER(check_clause) LIKE '%TARGET_STEP_INDEX%'"))
                    .isEqualTo(1);
            assertFlowCompletionFoundation(connection);
            assertWorkProjectFoundation(connection);
            assertWorkDailyReportFoundation(connection);
            assertWorkTaskReminderFoundation(connection);
            assertTodoFoundation(connection);
            assertOpenApiFoundation(connection);
        }
    }

    private static void assertFlowCompletionFoundation(Connection connection)
            throws SQLException {
        assertThat(queryLong(connection, "SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema=DATABASE() AND table_type='BASE TABLE' "
                + "AND table_name IN ('un_flow_completion_execution',"
                + "'un_flow_completion_attempt','un_flow_subflow_run',"
                + "'un_flow_completion_compensation',"
                + "'un_flow_compensation_attempt',"
                + "'un_flow_compensation_subflow_run')"))
                .isEqualTo(6);
        assertThat(queryLong(connection, "SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema=DATABASE() AND ("
                + "(table_name IN ('un_flow_definition_draft',"
                + "'un_flow_definition_version') "
                + "AND column_name='completion_steps' "
                + "AND data_type='json' AND is_nullable='YES') OR "
                + "(table_name IN ('un_flow_definition_draft',"
                + "'un_flow_definition_version') "
                + "AND column_name='completion_failure_policy') OR "
                + "(table_name='un_flow_instance' "
                + "AND column_name IN ('completion_phase',"
                + "'completion_failure_policy','active_completion_ordinal')) OR "
                + "(table_name='un_flow_completion_execution' "
                + "AND column_name IN ('execution_id','instance_id','definition_id',"
                + "'definition_version','ordinal','execution_type','config_json',"
                + "'payload_json','status','attempt_count','state_version',"
                + "'available_at','lease_token_hash','lease_expires_at',"
                + "'result_json','failure_code','failure_message',"
                + "'parallel_group')) OR "
                + "(table_name='un_flow_completion_attempt' "
                + "AND column_name IN ('attempt_id','execution_id','attempt_number',"
                + "'event_sequence','event_type','idempotency_key_hash',"
                + "'result_json','failure_code','failure_message','http_status',"
                + "'duration_ms','response_sha256','started_at','completed_at'))"
                + " OR (table_name='un_flow_subflow_run' "
                + "AND column_name IN ('system_id','tenant_id','subflow_run_id',"
                + "'execution_id','attempt_number','launch_key','child_instance_id',"
                + "'target_definition_id','target_definition_version',"
                + "'root_instance_id','subflow_depth','child_status','launched_at',"
                + "'terminal_at','result_code','result_applied_at','state_version'))"
                + " OR (table_name='un_flow_completion_compensation' "
                + "AND column_name IN ('system_id','tenant_id','compensation_id',"
                + "'instance_id','original_execution_id','original_ordinal',"
                + "'reverse_ordinal','definition_id','definition_version',"
                + "'step_code','step_name','execution_type','config_json',"
                + "'payload_json','status','attempt_count','state_version',"
                + "'available_at','lease_owner','lease_token_hash',"
                + "'lease_expires_at','created_at','started_at','terminal_at',"
                + "'result_json','failure_code','failure_message',"
                + "'failure_retryable'))"
                + " OR (table_name='un_flow_compensation_attempt' "
                + "AND column_name IN ('system_id','tenant_id','attempt_id',"
                + "'compensation_id','attempt_number','event_sequence',"
                + "'event_type','actor_member_id','lease_owner',"
                + "'idempotency_key_hash','result_json','failure_code',"
                + "'failure_message','http_status','duration_ms',"
                + "'response_sha256','started_at','completed_at','occurred_at'))"
                + " OR (table_name='un_flow_compensation_subflow_run' "
                + "AND column_name IN ('system_id','tenant_id',"
                + "'compensation_subflow_run_id','compensation_id',"
                + "'attempt_number','launch_key','child_instance_id',"
                + "'target_definition_id','target_definition_version',"
                + "'root_instance_id','subflow_depth','child_status',"
                + "'launched_at','terminal_at','result_code',"
                + "'result_applied_at','state_version'))"
                + ")"))
                .isEqualTo(120);
        assertThat(queryLong(connection, "SELECT COUNT(*) "
                + "FROM information_schema.table_constraints tc "
                + "JOIN information_schema.check_constraints cc "
                + "ON cc.constraint_schema=tc.constraint_schema "
                + "AND cc.constraint_name=tc.constraint_name "
                + "WHERE tc.constraint_schema=DATABASE() "
                + "AND tc.constraint_name IN ("
                + "'ck_flow_draft_completion_steps',"
                + "'ck_flow_version_completion_steps',"
                + "'ck_flow_draft_completion_failure_policy',"
                + "'ck_flow_version_completion_failure_policy',"
                + "'ck_flow_instance_completion_failure_policy',"
                + "'ck_flow_instance_completion_phase',"
                + "'ck_flow_instance_completion_ordinal',"
                + "'ck_flow_instance_compensating_policy',"
                + "'ck_flow_completion_identity',"
                + "'ck_flow_completion_step',"
                + "'ck_flow_completion_status',"
                + "'ck_flow_completion_lease',"
                + "'ck_flow_completion_times',"
                + "'ck_flow_completion_result_failure',"
                + "'ck_flow_completion_parallel_group',"
                + "'ck_flow_completion_attempt_identity',"
                + "'ck_flow_completion_attempt_event',"
                + "'ck_flow_completion_attempt_values',"
                + "'ck_flow_subflow_identity',"
                + "'ck_flow_subflow_result',"
                + "'ck_flow_compensation_identity',"
                + "'ck_flow_compensation_step',"
                + "'ck_flow_compensation_status',"
                + "'ck_flow_compensation_lease',"
                + "'ck_flow_compensation_times',"
                + "'ck_flow_compensation_result',"
                + "'ck_flow_compensation_attempt_identity',"
                + "'ck_flow_compensation_attempt_event',"
                + "'ck_flow_compensation_attempt_values',"
                + "'ck_flow_compensation_subflow_identity',"
                + "'ck_flow_compensation_subflow_result')"))
                .isEqualTo(31);
        assertThat(queryLong(connection, "SELECT COUNT(DISTINCT CONCAT("
                + "table_name, ':', index_name)) "
                + "FROM information_schema.statistics "
                + "WHERE table_schema=DATABASE() "
                + "AND table_name IN ('un_flow_completion_execution',"
                + "'un_flow_completion_attempt','un_flow_subflow_run',"
                + "'un_flow_completion_compensation',"
                + "'un_flow_compensation_attempt',"
                + "'un_flow_compensation_subflow_run') "
                + "AND index_name IN ('uk_flow_completion_instance_ordinal',"
                + "'idx_flow_completion_active_stage',"
                + "'idx_flow_completion_stage_join',"
                + "'idx_flow_completion_due',"
                + "'idx_flow_completion_lease_due',"
                + "'uk_flow_completion_attempt_sequence',"
                + "'uk_flow_completion_attempt_idempotency',"
                + "'idx_flow_completion_attempt_execution',"
                + "'uk_flow_subflow_execution_attempt',"
                + "'uk_flow_subflow_launch_key','uk_flow_subflow_child',"
                + "'idx_flow_subflow_execution','idx_flow_subflow_running',"
                + "'idx_flow_subflow_result_due',"
                + "'uk_flow_compensation_original',"
                + "'uk_flow_compensation_reverse',"
                + "'idx_flow_compensation_lock_order',"
                + "'idx_flow_compensation_due',"
                + "'idx_flow_compensation_lease_due',"
                + "'uk_flow_compensation_attempt_sequence',"
                + "'uk_flow_compensation_attempt_idempotency',"
                + "'idx_flow_compensation_attempt',"
                + "'uk_flow_compensation_subflow_attempt',"
                + "'uk_flow_compensation_subflow_launch',"
                + "'uk_flow_compensation_subflow_child',"
                + "'idx_flow_compensation_subflow_result')"))
                .isEqualTo(26);
        assertThat(queryLong(connection, "SELECT COUNT(*) "
                + "FROM information_schema.statistics "
                + "WHERE table_schema=DATABASE() "
                + "AND table_name='un_flow_completion_execution' "
                + "AND index_name='uk_flow_completion_one_active'"))
                .isZero();
        assertThat(queryLong(connection, "SELECT COUNT(*) "
                + "FROM information_schema.check_constraints "
                + "WHERE constraint_schema=DATABASE() "
                + "AND constraint_name='ck_flow_completion_attempt_event' "
                + "AND UPPER(check_clause) LIKE '%STAGE_JOINED%'"))
                .isEqualTo(1);
        assertThat(queryLong(connection, "SELECT COUNT(*) "
                + "FROM information_schema.referential_constraints "
                + "WHERE constraint_schema=DATABASE() "
                + "AND table_name='un_flow_subflow_run' "
                + "AND constraint_name IN ('fk_flow_subflow_execution',"
                + "'fk_flow_subflow_child','fk_flow_subflow_root',"
                + "'fk_flow_subflow_target') "
                + "AND delete_rule='RESTRICT'"))
                .isEqualTo(4);
        assertThat(queryLong(connection, "SELECT COUNT(*) "
                + "FROM information_schema.check_constraints "
                + "WHERE constraint_schema=DATABASE() "
                + "AND constraint_name='ck_flow_completion_status' "
                + "AND UPPER(check_clause) LIKE '%RUNNING%'"))
                .isEqualTo(1);
        assertThat(queryLong(connection, "SELECT COUNT(*) "
                + "FROM information_schema.check_constraints "
                + "WHERE constraint_schema=DATABASE() "
                + "AND constraint_name='ck_flow_history_event' "
                + "AND UPPER(check_clause) LIKE '%COMPLETION_COMPENSATED%'"))
                .isEqualTo(1);
        assertThat(queryLong(connection, "SELECT COUNT(*) "
                + "FROM information_schema.referential_constraints "
                + "WHERE constraint_schema=DATABASE() "
                + "AND table_name IN ('un_flow_completion_compensation',"
                + "'un_flow_compensation_attempt',"
                + "'un_flow_compensation_subflow_run') "
                + "AND delete_rule='RESTRICT'"))
                .isEqualTo(8);
        assertThat(queryLong(connection, "SELECT COUNT(*) "
                + "FROM information_schema.columns "
                + "WHERE table_schema=DATABASE() "
                + "AND table_name IN ('un_flow_definition_draft',"
                + "'un_flow_definition_version','un_flow_instance') "
                + "AND column_name='completion_failure_policy' "
                + "AND column_default='MANUAL_RETRY' "
                + "AND is_nullable='NO'"))
                .isEqualTo(3);
        assertThat(queryLong(connection, "SELECT COUNT(*) FROM un_plat_permission "
                + "WHERE system_id=9100000000000001 "
                + "AND permission_code='flow.external-task.work' "
                + "AND status='ACTIVE'"))
                .isEqualTo(1);
        assertThat(queryLong(connection, "SELECT COUNT(*) "
                + "FROM un_plat_role_permission rp "
                + "JOIN un_plat_permission p ON p.id=rp.permission_id "
                + "WHERE rp.role_id=9100000000000006 "
                + "AND p.permission_code='flow.external-task.work' "
                + "AND rp.effect='ALLOW'"))
                .isEqualTo(1);
    }

    private static void assertWorkProjectFoundation(Connection connection)
            throws SQLException {
        assertThat(queryLong(connection, "SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema=DATABASE() AND table_type='BASE TABLE' "
                + "AND table_name IN ('un_work_project','un_work_project_member')"))
                .isEqualTo(2);
        assertThat(queryLong(connection, "SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema=DATABASE() AND ("
                + "(table_name='un_work_project' AND column_name IN ("
                + "'id','system_id','tenant_id','creator_member_id','title','description',"
                + "'status','created_at','updated_at','version')) OR "
                + "(table_name='un_work_project_member' AND column_name IN ("
                + "'system_id','tenant_id','project_id','member_id','role','status',"
                + "'joined_at','updated_at','version')) OR "
                + "(table_name='un_work_task' AND column_name IN ("
                + "'project_id','description','due_at')))"))
                .isEqualTo(22);
        assertThat(queryLong(connection, "SELECT COUNT(*) "
                + "FROM information_schema.check_constraints "
                + "WHERE constraint_schema=DATABASE() AND constraint_name IN ("
                + "'ck_work_project_identity','ck_work_project_title',"
                + "'ck_work_project_description','ck_work_project_status',"
                + "'ck_work_project_state','ck_work_project_member_identity',"
                + "'ck_work_project_member_role','ck_work_project_member_status',"
                + "'ck_work_project_member_state','ck_work_task_description')"))
                .isEqualTo(10);
        assertThat(queryLong(connection, "SELECT COUNT(DISTINCT CONCAT(table_name, ':', index_name)) "
                + "FROM information_schema.statistics WHERE table_schema=DATABASE() "
                + "AND index_name IN ('uk_work_project_id','idx_work_project_list',"
                + "'idx_work_project_creator','idx_work_project_member_access',"
                + "'idx_work_project_member_owner','idx_work_task_project_state',"
                + "'idx_work_task_project_due','idx_work_task_due_window')"))
                .isEqualTo(8);
        assertThat(queryLong(connection, "SELECT COUNT(*) "
                + "FROM information_schema.referential_constraints "
                + "WHERE constraint_schema=DATABASE() "
                + "AND constraint_name IN ('fk_work_project_member_project',"
                + "'fk_work_task_project') AND delete_rule='RESTRICT'"))
                .isEqualTo(2);
        assertThat(queryLong(connection, "SELECT COUNT(*) FROM un_plat_permission "
                + "WHERE system_id=9100000000000001 "
                + "AND permission_code IN ('work.project.access',"
                + "'work.project.create','work.project.manage') "
                + "AND status='ACTIVE'"))
                .isEqualTo(3);
        assertThat(queryLong(connection, "SELECT COUNT(*) FROM un_plat_role_permission rp "
                + "JOIN un_plat_permission p ON p.id=rp.permission_id "
                + "WHERE rp.role_id=9100000000000006 "
                + "AND p.permission_code IN ('work.project.access',"
                + "'work.project.create','work.project.manage') "
                + "AND rp.effect='ALLOW'"))
                .isEqualTo(3);
    }

    private static void assertWorkDailyReportFoundation(Connection connection)
            throws SQLException {
        assertThat(queryLong(connection, "SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema=DATABASE() AND table_type='BASE TABLE' "
                + "AND table_name='un_work_daily_report'"))
                .isEqualTo(1);
        assertThat(queryLong(connection, "SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema=DATABASE() AND table_name='un_work_daily_report' "
                + "AND column_name IN ('id','system_id','tenant_id','author_member_id',"
                + "'work_date','completed_work','planned_work','blockers','status',"
                + "'created_at','updated_at','submitted_at','version')"))
                .isEqualTo(13);
        assertThat(queryLong(connection, "SELECT COUNT(*) "
                + "FROM information_schema.check_constraints "
                + "WHERE constraint_schema=DATABASE() AND constraint_name IN ("
                + "'ck_work_daily_report_identity','ck_work_daily_report_completed',"
                + "'ck_work_daily_report_planned','ck_work_daily_report_blockers',"
                + "'ck_work_daily_report_status','ck_work_daily_report_state')"))
                .isEqualTo(6);
        assertThat(queryLong(connection, "SELECT COUNT(DISTINCT index_name) "
                + "FROM information_schema.statistics "
                + "WHERE table_schema=DATABASE() AND table_name='un_work_daily_report' "
                + "AND index_name IN ('PRIMARY','uk_work_daily_report_id',"
                + "'uk_work_daily_report_author_date','idx_work_daily_report_list',"
                + "'idx_work_daily_report_member_list','idx_work_daily_report_status_list')"))
                .isEqualTo(6);
        assertThat(queryLong(connection, "SELECT COUNT(*) "
                + "FROM information_schema.referential_constraints "
                + "WHERE constraint_schema=DATABASE() "
                + "AND constraint_name='fk_work_daily_report_author' "
                + "AND delete_rule='RESTRICT'"))
                .isEqualTo(1);
        assertThat(queryLong(connection, "SELECT COUNT(*) FROM un_plat_permission "
                + "WHERE system_id=9100000000000001 "
                + "AND permission_code IN ('work.report.access',"
                + "'work.report.create','work.report.manage') "
                + "AND status='ACTIVE'"))
                .isEqualTo(3);
        assertThat(queryLong(connection, "SELECT COUNT(*) FROM un_plat_role_permission rp "
                + "JOIN un_plat_permission p ON p.id=rp.permission_id "
                + "WHERE rp.role_id=9100000000000006 "
                + "AND p.permission_code IN ('work.report.access',"
                + "'work.report.create','work.report.manage') "
                + "AND rp.effect='ALLOW'"))
                .isEqualTo(3);
    }

    private static void assertWorkTaskReminderFoundation(Connection connection)
            throws SQLException {
        assertThat(queryLong(connection, "SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema=DATABASE() AND table_type='BASE TABLE' "
                + "AND table_name='un_work_task_reminder'"))
                .isEqualTo(1);
        assertThat(queryLong(connection, "SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema=DATABASE() AND ("
                + "(table_name='un_work_task' AND column_name='reminder_at') OR "
                + "(table_name='un_work_task_reminder' AND column_name IN ("
                + "'system_id','tenant_id','task_id','generation','scheduled_at','status',"
                + "'attempt_count','lease_owner','lease_token_hash','lease_expires_at',"
                + "'created_at','updated_at','sent_at','failed_at','cancelled_at',"
                + "'failure_code','failure_message','version')))"))
                .isEqualTo(19);
        assertThat(queryLong(connection, "SELECT COUNT(*) "
                + "FROM information_schema.check_constraints "
                + "WHERE constraint_schema=DATABASE() AND constraint_name IN ("
                + "'ck_work_task_reminder_schedule','ck_work_task_reminder_identity',"
                + "'ck_work_task_reminder_status','ck_work_task_reminder_time',"
                + "'ck_work_task_reminder_lease','ck_work_task_reminder_state')"))
                .isEqualTo(6);
        assertThat(queryLong(connection, "SELECT COUNT(DISTINCT index_name) "
                + "FROM information_schema.statistics "
                + "WHERE table_schema=DATABASE() AND ((table_name='un_work_task' "
                + "AND index_name='idx_work_task_reminder_window') OR "
                + "(table_name='un_work_task_reminder' AND index_name IN ("
                + "'PRIMARY','uk_work_task_reminder_generation',"
                + "'idx_work_task_reminder_due','idx_work_task_reminder_lease')))"))
                .isEqualTo(5);
        assertThat(queryLong(connection, "SELECT COUNT(*) "
                + "FROM information_schema.referential_constraints "
                + "WHERE constraint_schema=DATABASE() "
                + "AND constraint_name='fk_work_task_reminder_task' "
                + "AND delete_rule='RESTRICT'"))
                .isEqualTo(1);
    }

    private static void assertTodoFoundation(Connection connection)
            throws SQLException {
        assertThat(queryLong(connection, "SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema=DATABASE() AND table_type='BASE TABLE' "
                + "AND table_name IN ('un_todo_item','un_todo_action_log')"))
                .isEqualTo(2);
        assertThat(queryLong(connection, "SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema=DATABASE() AND ((table_name='un_todo_item' "
                + "AND column_name IN ('id','system_id','tenant_id','recipient_member_id',"
                + "'source_type','source_id','source_version','action_scope','category',"
                + "'priority','title','due_at','route_hint','available_actions',"
                + "'represented_member_id','status','close_reason','created_at','updated_at',"
                + "'closed_at','version')) OR (table_name='un_todo_action_log' "
                + "AND column_name IN ('id','system_id','tenant_id','todo_item_id',"
                + "'recipient_member_id','actor_member_id','caller_idempotency_key',"
                + "'source_type','source_id','source_version','requested_action','status',"
                + "'result_code','result_message','request_id','trace_id','created_at',"
                + "'completed_at','version')))"))
                .isEqualTo(40);
        assertThat(queryLong(connection, "SELECT COUNT(*) "
                + "FROM information_schema.check_constraints "
                + "WHERE constraint_schema=DATABASE() AND constraint_name IN ("
                + "'ck_todo_item_identity','ck_todo_item_source','ck_todo_item_text',"
                + "'ck_todo_item_priority','ck_todo_item_status','ck_todo_item_state',"
                + "'ck_todo_action_identity','ck_todo_action_source','ck_todo_action_text',"
                + "'ck_todo_action_status','ck_todo_action_state')"))
                .isEqualTo(11);
        assertThat(queryLong(connection, "SELECT COUNT(*) FROM ("
                + "SELECT table_name,index_name FROM information_schema.statistics "
                + "WHERE table_schema=DATABASE() AND ((table_name='un_todo_item' "
                + "AND index_name IN ('PRIMARY','uk_todo_item_id','uk_todo_item_identity',"
                + "'uk_todo_item_scope_recipient','idx_todo_item_recipient_status',"
                + "'idx_todo_item_recipient_category','idx_todo_item_source')) OR "
                + "(table_name='un_todo_action_log' AND index_name IN ('PRIMARY',"
                + "'uk_todo_action_log_id','uk_todo_action_idempotency',"
                + "'idx_todo_action_item'))) GROUP BY table_name,index_name) todo_indexes"))
                .isEqualTo(11);
        assertThat(queryLong(connection, "SELECT COUNT(*) "
                + "FROM information_schema.referential_constraints "
                + "WHERE constraint_schema=DATABASE() "
                + "AND constraint_name='fk_todo_action_item' "
                + "AND referenced_table_name='un_todo_item' "
                + "AND delete_rule='RESTRICT'"))
                .isEqualTo(1);
    }

    private static void assertOpenApiFoundation(Connection connection) throws SQLException {
        assertThat(queryLong(connection, "SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema=DATABASE() AND table_type='BASE TABLE' "
                + "AND table_name IN ('un_openapi_application','un_openapi_credential',"
                + "'un_openapi_nonce','un_openapi_rate_bucket','un_openapi_call_log')"))
                .isEqualTo(5);
        assertThat(queryLong(connection, "SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema=DATABASE() "
                + "AND table_name IN ('un_openapi_application','un_openapi_credential',"
                + "'un_openapi_nonce','un_openapi_rate_bucket','un_openapi_call_log')"))
                .isEqualTo(47);
        assertThat(queryLong(connection, "SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema=DATABASE() AND table_name='un_openapi_application' "
                + "AND column_name IN ('scopes_json','ip_allowlist_json') "
                + "AND data_type='json' AND is_nullable='NO'"))
                .isEqualTo(2);
        assertThat(queryLong(connection, "SELECT COUNT(*) "
                + "FROM information_schema.table_constraints tc "
                + "JOIN information_schema.check_constraints cc "
                + "ON cc.constraint_schema=tc.constraint_schema "
                + "AND cc.constraint_name=tc.constraint_name "
                + "WHERE tc.constraint_schema=DATABASE() "
                + "AND tc.table_name IN ('un_openapi_application','un_openapi_credential',"
                + "'un_openapi_nonce','un_openapi_rate_bucket','un_openapi_call_log') "
                + "AND tc.constraint_type='CHECK' "
                + "AND tc.constraint_name LIKE 'ck_openapi_%'"))
                .isEqualTo(26);
        assertThat(queryLong(connection, "SELECT COUNT(*) "
                + "FROM information_schema.table_constraints tc "
                + "JOIN information_schema.check_constraints cc "
                + "ON cc.constraint_schema=tc.constraint_schema "
                + "AND cc.constraint_name=tc.constraint_name "
                + "WHERE tc.constraint_schema=DATABASE() "
                + "AND tc.table_name='un_openapi_application' "
                + "AND tc.constraint_name IN ('ck_openapi_application_scopes',"
                + "'ck_openapi_application_ip_allowlist') "
                + "AND UPPER(cc.check_clause) LIKE '%JSON_TYPE%'"))
                .isEqualTo(2);
        assertThat(queryLong(connection, "SELECT COUNT(*) "
                + "FROM information_schema.referential_constraints "
                + "WHERE constraint_schema=DATABASE() "
                + "AND table_name IN ('un_openapi_application','un_openapi_credential',"
                + "'un_openapi_nonce','un_openapi_rate_bucket','un_openapi_call_log') "
                + "AND delete_rule='RESTRICT'"))
                .isEqualTo(8);
        assertThat(queryLong(connection, "SELECT COUNT(DISTINCT referenced_table_name) "
                + "FROM information_schema.referential_constraints "
                + "WHERE constraint_schema=DATABASE() "
                + "AND table_name='un_openapi_application' "
                + "AND referenced_table_name IN ('un_plat_system','un_plat_tenant',"
                + "'un_plat_member','un_plat_member_tenant')"))
                .isEqualTo(4);
        assertThat(queryLong(connection, "SELECT COUNT(DISTINCT index_name) "
                + "FROM information_schema.statistics "
                + "WHERE table_schema=DATABASE() AND table_name='un_openapi_call_log' "
                + "AND index_name IN ('idx_openapi_call_application','idx_openapi_call_app_key',"
                + "'idx_openapi_call_result','idx_openapi_call_request','idx_openapi_call_trace')"))
                .isEqualTo(5);
        assertThat(queryLong(connection, "SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema=DATABASE() AND table_name LIKE 'un_openapi_%' "
                + "AND column_name LIKE '%secret%'"))
                .isEqualTo(3);
        assertThat(queryLong(connection, "SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema=DATABASE() AND table_name='un_openapi_credential' "
                + "AND column_name='secret_ref' AND data_type='varchar'"))
                .isEqualTo(1);
        assertThat(queryLong(connection, "SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema=DATABASE() AND table_name='un_openapi_callback_version' "
                + "AND ((column_name='secret_ref' AND data_type='varchar') "
                + "OR (column_name='signing_secret_version' AND data_type IN ('int','bigint')))"))
                .isEqualTo(2);
        assertThat(queryLong(connection, "SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema=DATABASE() AND table_name LIKE 'un_openapi_%' "
                + "AND column_name IN ('secret','secret_value','client_secret','secret_hash',"
                + "'secret_ciphertext','encrypted_secret','plaintext_secret')"))
                .isZero();

        assertThat(queryLong(connection, "SELECT COUNT(*) FROM un_plat_permission "
                + "WHERE system_id=9100000000000001 AND scope_type='SYSTEM' "
                + "AND scope_key=9100000000000001 "
                + "AND permission_code='openapi.application.manage' "
                + "AND resource_type='ACTION' AND status='ACTIVE'"))
                .isEqualTo(1);
        assertThat(queryLong(connection, "SELECT COUNT(*) FROM un_plat_role_permission rp "
                + "JOIN un_plat_role r ON r.id=rp.role_id "
                + "JOIN un_plat_permission p ON p.id=rp.permission_id "
                + "WHERE r.id=9100000000000006 AND r.role_type='ROOT' "
                + "AND p.permission_code='openapi.application.manage' "
                + "AND rp.scope_type='SYSTEM' AND rp.scope_key=9100000000000001 "
                + "AND rp.effect='ALLOW'"))
                .isEqualTo(1);
        assertThat(queryLong(connection, "SELECT COUNT(*) FROM un_plat_authz_epoch "
                + "WHERE scope_type='SYSTEM' AND scope_key=9100000000000001 "
                + "AND system_id=9100000000000001 AND epoch=13 AND version=12"))
                .isEqualTo(1);
        assertThat(queryLong(connection, "SELECT COUNT(*) FROM un_plat_system "
                + "WHERE id=9100000000000001 AND permission_version=13"))
                .isEqualTo(1);

        insertOpenApiApplication(
                connection,
                9_800_000_000_000_001L,
                9_100_000_000_000_002L,
                "schema_openapi_app_0001",
                "ACTIVE",
                "[\"flow.instance.start\"]",
                "[\"127.0.0.1\"]");
        execute(connection, "INSERT INTO un_openapi_credential "
                        + "(id,application_id,credential_version,secret_ref,status,activated_at,"
                        + "revoked_at,created_at,created_by) "
                        + "VALUES (?,?,1,?,'ACTIVE',NOW(3),NULL,NOW(3),?)",
                9_800_000_000_000_002L,
                9_800_000_000_000_001L,
                "test-secret://openapi/schema/v1",
                9_100_000_000_000_000L);
        execute(connection, "INSERT INTO un_openapi_nonce "
                        + "(application_id,credential_version,nonce,expires_at,created_at) "
                        + "VALUES (?,1,?,DATE_ADD(NOW(3),INTERVAL 5 MINUTE),NOW(3))",
                9_800_000_000_000_001L,
                "nonce-schema-00000001");
        execute(connection, "INSERT INTO un_openapi_rate_bucket "
                        + "(application_id,window_start,request_count,version) "
                        + "VALUES (?,'2026-07-28 12:30:00.000',1,0)",
                9_800_000_000_000_001L);
        execute(connection, "INSERT INTO un_openapi_call_log "
                        + "(id,application_id,app_key_hash,credential_version,route_template,"
                        + "request_method,result_category,http_status,latency_ms,request_id,trace_id,"
                        + "observed_ip,created_at) "
                        + "VALUES (?,?,?,1,?,'POST','SUCCESS',201,12,?,?,INET6_ATON(?),NOW(3))",
                9_800_000_000_000_003L,
                9_800_000_000_000_001L,
                "a".repeat(64),
                "/openapi/v1/flow/definitions/{definitionId}/instances",
                "openapi-schema-request",
                "openapi-schema-trace",
                "127.0.0.1");

        assertThat(queryLong(connection, "SELECT COUNT(*) FROM un_openapi_application "
                + "WHERE id=9800000000000001 AND status='ACTIVE' "
                + "AND JSON_LENGTH(scopes_json)=1 AND JSON_LENGTH(ip_allowlist_json)=1 "
                + "AND current_credential_version=1 AND rate_limit_per_minute=60"))
                .isEqualTo(1);
        assertThat(queryLong(connection, "SELECT COUNT(*) FROM un_openapi_credential "
                + "WHERE application_id=9800000000000001 AND credential_version=1 "
                + "AND status='ACTIVE' AND secret_ref='test-secret://openapi/schema/v1'"))
                .isEqualTo(1);
        assertThat(queryLong(connection, "SELECT COUNT(*) FROM un_openapi_call_log "
                + "WHERE application_id=9800000000000001 AND request_method='POST' "
                + "AND result_category='SUCCESS' AND http_status=201 "
                + "AND OCTET_LENGTH(observed_ip)=4"))
                .isEqualTo(1);

        assertConstraintRejected(() -> insertOpenApiApplication(
                connection,
                9_800_000_000_000_004L,
                9_100_000_000_000_002L,
                "schema_openapi_app_0001",
                "ACTIVE",
                "[\"flow.instance.start\"]",
                "[]"));
        assertConstraintRejected(() -> insertOpenApiApplication(
                connection,
                9_800_000_000_000_005L,
                9_199_999_999_999_999L,
                "schema_openapi_app_0002",
                "ACTIVE",
                "[\"flow.instance.start\"]",
                "[]"));
        assertConstraintRejected(() -> insertOpenApiApplication(
                connection,
                9_800_000_000_000_006L,
                9_100_000_000_000_002L,
                "schema_openapi_app_0003",
                "DELETED",
                "[\"flow.instance.start\"]",
                "[]"));
        assertConstraintRejected(() -> insertOpenApiApplication(
                connection,
                9_800_000_000_000_007L,
                9_100_000_000_000_002L,
                "schema_openapi_app_0004",
                "ACTIVE",
                "{\"scope\":\"flow.instance.start\"}",
                "[]"));
        assertConstraintRejected(() -> insertOpenApiApplication(
                connection,
                9_800_000_000_000_011L,
                9_100_000_000_000_002L,
                "schema_openapi_app_0005",
                "ACTIVE",
                "[\"flow.instance.start\"]",
                "{\"cidr\":\"127.0.0.1/32\"}"));
        assertConstraintRejected(() -> execute(connection,
                "INSERT INTO un_openapi_credential "
                        + "(id,application_id,credential_version,secret_ref,status,activated_at,"
                        + "revoked_at,created_at,created_by) "
                        + "VALUES (?,?,1,?,'ACTIVE',NOW(3),NULL,NOW(3),?)",
                9_800_000_000_000_012L,
                9_800_000_000_000_001L,
                "test-secret://openapi/schema/duplicate-v1",
                9_100_000_000_000_000L));
        assertConstraintRejected(() -> execute(connection,
                "INSERT INTO un_openapi_credential "
                        + "(id,application_id,credential_version,secret_ref,status,activated_at,"
                        + "revoked_at,created_at,created_by) "
                        + "VALUES (?,?,2,?,'ACTIVE',NOW(3),NULL,NOW(3),?)",
                9_800_000_000_000_008L,
                9_800_000_000_000_001L,
                "plaintext-secret",
                9_100_000_000_000_000L));
        assertConstraintRejected(() -> execute(connection,
                "INSERT INTO un_openapi_credential "
                        + "(id,application_id,credential_version,secret_ref,status,activated_at,"
                        + "revoked_at,created_at,created_by) "
                        + "VALUES (?,?,2,?,'REVOKED',NOW(3),NULL,NOW(3),?)",
                9_800_000_000_000_009L,
                9_800_000_000_000_001L,
                "test-secret://openapi/schema/v2",
                9_100_000_000_000_000L));
        assertConstraintRejected(() -> execute(connection,
                "INSERT INTO un_openapi_nonce "
                        + "(application_id,credential_version,nonce,expires_at,created_at) "
                        + "VALUES (?,1,?,DATE_SUB(NOW(3),INTERVAL 1 SECOND),NOW(3))",
                9_800_000_000_000_001L,
                "nonce-schema-00000002"));
        assertConstraintRejected(() -> execute(connection,
                "INSERT INTO un_openapi_nonce "
                        + "(application_id,credential_version,nonce,expires_at,created_at) "
                        + "VALUES (?,1,?,DATE_ADD(NOW(3),INTERVAL 5 MINUTE),NOW(3))",
                9_800_000_000_000_001L,
                "nonce-schema-00000001"));
        assertConstraintRejected(() -> execute(connection,
                "INSERT INTO un_openapi_rate_bucket "
                        + "(application_id,window_start,request_count,version) "
                        + "VALUES (?,'2026-07-28 12:30:01.000',1,0)",
                9_800_000_000_000_001L));
        assertConstraintRejected(() -> execute(connection,
                "INSERT INTO un_openapi_call_log "
                        + "(id,application_id,app_key_hash,credential_version,route_template,"
                        + "request_method,result_category,http_status,latency_ms,request_id,trace_id,"
                        + "observed_ip,created_at) "
                        + "VALUES (?,?,?,1,?,'POST','SUCCESS',201,12,?,?,INET6_ATON(?),NOW(3))",
                9_800_000_000_000_010L,
                9_800_000_000_000_001L,
                "not-a-sha256-hash",
                "/openapi/v1/flow/definitions/{definitionId}/instances",
                "openapi-invalid-request",
                "openapi-invalid-trace",
                "127.0.0.1"));
    }

    private static void insertOpenApiApplication(
            Connection connection,
            long id,
            long tenantId,
            String appKey,
            String status,
            String scopesJson,
            String ipAllowlistJson
    ) throws SQLException {
        execute(connection, "INSERT INTO un_openapi_application "
                        + "(id,system_id,tenant_id,service_member_id,app_key,name,status,"
                        + "scopes_json,ip_allowlist_json,rate_limit_per_minute,"
                        + "current_credential_version,created_at,created_by,updated_at,updated_by,version) "
                        + "VALUES (?,?,?,?,?,?,?,CAST(? AS JSON),CAST(? AS JSON),60,1,"
                        + "NOW(3),?,NOW(3),?,0)",
                id,
                9_100_000_000_000_001L,
                tenantId,
                9_100_000_000_000_003L,
                appKey,
                "Schema OpenAPI application",
                status,
                scopesJson,
                ipAllowlistJson,
                9_100_000_000_000_000L,
                9_100_000_000_000_000L);
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }

    private static void insertLegacyTriggerDispatchBeforeV817(Connection connection) throws SQLException {
        execute(connection, "INSERT INTO un_flow_trigger_dispatch "
                        + "(system_id,tenant_id,event_key,definition_id,definition_version,instance_id) "
                        + "VALUES (?,?,?,?,?,?)",
                9_100_000_000_000_001L,
                9_100_000_000_000_002L,
                "legacy-record-activated",
                9_700_000_000_000_001L,
                1,
                9_700_000_000_000_010L);
    }

    private static void assertExpandedTriggerConstraints(Connection connection) throws SQLException {
        for (var table : new String[]{
                "un_flow_definition_draft",
                "un_flow_definition_version"
        }) {
            for (var event : new String[]{
                    "RECORD_ACTIVATED",
                    "RECORD_CREATED",
                    "RECORD_UPDATED",
                    "RECORD_DELETED",
                    "RECORD_STATUS_CHANGED"
            }) {
                execute(connection, "UPDATE " + table + " "
                                + "SET trigger_module_code='schema_record',trigger_event=?,"
                                + "trigger_priority=0,trigger_exclusive=FALSE,"
                                + "trigger_conditions=JSON_ARRAY() "
                                + "WHERE system_id=9100000000000001 "
                                + "AND tenant_id=9100000000000002 "
                                + "AND definition_id=9700000000000001",
                        event);
            }
            execute(connection, "UPDATE " + table + " "
                    + "SET trigger_module_code=NULL,trigger_event=NULL,trigger_priority=NULL,"
                    + "trigger_exclusive=NULL,trigger_conditions=NULL "
                    + "WHERE system_id=9100000000000001 "
                    + "AND tenant_id=9100000000000002 "
                    + "AND definition_id=9700000000000001");
            assertConstraintRejected(() -> execute(connection, "UPDATE " + table + " "
                    + "SET trigger_module_code='schema_record',trigger_event='RECORD_UPDATED',"
                    + "trigger_priority=0,trigger_exclusive=FALSE,trigger_conditions=JSON_ARRAY(),"
                    + "status_field_code='approval_status',status_approved_value=101,"
                    + "status_rejected_value=102,status_withdrawn_value=103,"
                    + "status_terminated_value=104 "
                    + "WHERE system_id=9100000000000001 "
                    + "AND tenant_id=9100000000000002 "
                    + "AND definition_id=9700000000000001"));
        }
    }

    private static void insertLegacyFlowInstanceBeforeV810(Connection connection) throws SQLException {
        execute(connection, "INSERT INTO un_flow_definition_draft "
                        + "(system_id,tenant_id,definition_id,name,approver_id,revision,updated_at,created_at) "
                        + "VALUES (?,?,?,'Legacy assignment definition',?,1,NOW(3),NOW(3))",
                9_100_000_000_000_001L,
                9_100_000_000_000_002L,
                9_700_000_000_000_001L,
                9_100_000_000_000_003L);
        execute(connection, "INSERT INTO un_flow_definition_draft_step "
                        + "(system_id,tenant_id,definition_id,step_no,approver_id) VALUES "
                        + "(?,?,?,1,?),(?,?,?,2,?)",
                9_100_000_000_000_001L,
                9_100_000_000_000_002L,
                9_700_000_000_000_001L,
                9_100_000_000_000_003L,
                9_100_000_000_000_001L,
                9_100_000_000_000_002L,
                9_700_000_000_000_001L,
                9_100_000_000_000_004L);
        execute(connection, "INSERT INTO un_flow_definition_version "
                        + "(system_id,tenant_id,definition_id,version_no,name,approver_id,"
                        + "source_revision,published_at) "
                        + "VALUES (?,?,?,1,'Legacy assignment definition',?,1,NOW(3))",
                9_100_000_000_000_001L,
                9_100_000_000_000_002L,
                9_700_000_000_000_001L,
                9_100_000_000_000_003L);
        execute(connection, "INSERT INTO un_flow_definition_version_step "
                        + "(system_id,tenant_id,definition_id,version_no,step_no,approver_id) VALUES "
                        + "(?,?,?,1,1,?),(?,?,?,1,2,?)",
                9_100_000_000_000_001L,
                9_100_000_000_000_002L,
                9_700_000_000_000_001L,
                9_100_000_000_000_003L,
                9_100_000_000_000_001L,
                9_100_000_000_000_002L,
                9_700_000_000_000_001L,
                9_100_000_000_000_004L);
        execute(connection, "INSERT INTO un_flow_instance "
                        + "(system_id,tenant_id,instance_id,definition_id,definition_version,business_key,"
                        + "requester_id,approver_id,status,state_version,started_at,completed_at) "
                        + "VALUES (?,?,?,?,1,'legacy-assignment-snapshot',?,?,'PENDING',0,NOW(3),NULL)",
                9_100_000_000_000_001L,
                9_100_000_000_000_002L,
                9_700_000_000_000_010L,
                9_700_000_000_000_001L,
                9_100_000_000_000_003L,
                9_100_000_000_000_003L);
        execute(connection, "INSERT INTO un_flow_history_event "
                        + "(system_id,tenant_id,instance_id,event_sequence,event_type,actor_id,"
                        + "from_status,to_status,comment,occurred_at) "
                        + "VALUES (?,?,?,1,'STARTED',?,NULL,'PENDING','',NOW(3))",
                9_100_000_000_000_001L,
                9_100_000_000_000_002L,
                9_700_000_000_000_010L,
                9_100_000_000_000_003L);
    }

    private static void advanceLegacyFlowInstanceBeforeV811(Connection connection) throws SQLException {
        execute(connection, "UPDATE un_flow_instance "
                        + "SET approver_id=?, state_version=1 "
                        + "WHERE system_id=? AND tenant_id=? AND instance_id=?",
                9_100_000_000_000_004L,
                9_100_000_000_000_001L,
                9_100_000_000_000_002L,
                9_700_000_000_000_010L);
        execute(connection, "INSERT INTO un_flow_history_event "
                        + "(system_id,tenant_id,instance_id,event_sequence,event_type,actor_id,"
                        + "from_status,to_status,comment,occurred_at,target_member_id,assignment_position) "
                        + "VALUES (?,?,?,2,'APPROVED',?,'PENDING','PENDING',?,NOW(3),NULL,NULL)",
                9_100_000_000_000_001L,
                9_100_000_000_000_002L,
                9_700_000_000_000_010L,
                9_100_000_000_000_003L,
                "Legacy first step approved");
    }

    private static void seedPublishedRecordParents(Connection connection) throws SQLException {
        var accountId = 9_100_000_000_000_000L;
        execute(connection, "INSERT INTO un_plat_account "
                        + "(id,account_code,username,username_normalized,display_name,locale,time_zone,status,"
                        + "created_at,created_by,updated_at,updated_by,version) "
                        + "VALUES (?,?,?,?,?,'zh-CN','Asia/Shanghai','ACTIVE',NOW(3),NULL,NOW(3),NULL,0)",
                accountId, "p4_schema_owner", "p4_schema_owner", "p4_schema_owner", "P4 Schema Owner");
        execute(connection, "INSERT INTO un_plat_system "
                        + "(id,system_code,name,status,tenant_mode,owner_account_id,permission_version,initialized_at,"
                        + "created_at,created_by,updated_at,updated_by,version) "
                        + "VALUES (?,?,?,?,?,?,1,NOW(3),NOW(3),?,NOW(3),?,0)",
                9_100_000_000_000_001L, "p4_schema", "P4 Schema", "ACTIVE", "SINGLE", accountId,
                accountId, accountId);
        execute(connection, "INSERT INTO un_plat_tenant "
                        + "(id,system_id,tenant_code,name,is_default,status,created_at,created_by,updated_at,updated_by,version) "
                        + "VALUES (?,?,?, ?,1,'ACTIVE',NOW(3),?,NOW(3),?,0)",
                9_100_000_000_000_002L, 9_100_000_000_000_001L, "default", "Default", accountId, accountId);
        execute(connection, "INSERT INTO un_plat_member "
                        + "(id,system_id,account_id,member_code,display_name,default_tenant_id,status,joined_at,"
                        + "created_at,created_by,updated_at,updated_by,version) "
                        + "VALUES (?,?,?,?,?,?,'ACTIVE',NOW(3),NOW(3),?,NOW(3),?,0)",
                9_100_000_000_000_003L, 9_100_000_000_000_001L, accountId, "owner", "Owner",
                9_100_000_000_000_002L, accountId, accountId);
        execute(connection, "INSERT INTO un_plat_member_tenant "
                        + "(id,system_id,member_id,tenant_id,status,granted_at,granted_by,expires_at,"
                        + "created_at,created_by,updated_at,updated_by,deleted_at,deleted_by,version) "
                        + "VALUES (?,?,?,?,'ACTIVE',NOW(3),?,NULL,NOW(3),?,NOW(3),?,NULL,NULL,0)",
                9_100_000_000_000_005L,
                9_100_000_000_000_001L,
                9_100_000_000_000_003L,
                9_100_000_000_000_002L,
                accountId,
                accountId,
                accountId);
        execute(connection, "INSERT INTO un_plat_role "
                        + "(id,scope_type,scope_key,system_id,tenant_id,role_code,name,role_type,status,"
                        + "permission_version,data_scope_id,is_builtin,published_version,"
                        + "created_at,created_by,updated_at,updated_by,deleted_at,version) "
                        + "VALUES (?,'SYSTEM',?,?,NULL,'system_owner','System owner','ROOT','ACTIVE',"
                        + "1,NULL,TRUE,1,NOW(3),?,NOW(3),?,NULL,0)",
                9_100_000_000_000_006L,
                9_100_000_000_000_001L,
                9_100_000_000_000_001L,
                accountId,
                accountId);
        execute(connection, "INSERT INTO un_module_group "
                        + "(id,system_id,group_code,group_name,description,icon_key,sort_order,desired_status,"
                        + "code_locked_at,created_revision,updated_revision,created_at,created_by,updated_at,"
                        + "updated_by,version,deleted_at,deleted_by) "
                        + "VALUES (?,?,?,'Schema group',NULL,NULL,0,'ENABLED',NOW(3),1,1,NOW(3),?,NOW(3),?,0,NULL,NULL)",
                9_200_000_000_000_009L,
                9_100_000_000_000_001L,
                "schema_group",
                accountId,
                accountId);
        execute(connection, "INSERT INTO un_module_definition "
                        + "(id,system_id,group_id,module_code,module_name,description,icon_key,sort_order,"
                        + "desired_status,allow_comments,allow_team,code_locked_at,created_revision,updated_revision,"
                        + "created_at,created_by,updated_at,updated_by,version,deleted_at,deleted_by) "
                        + "VALUES (?,?,?,'schema_record','Schema record',NULL,NULL,0,'ENABLED',FALSE,FALSE,"
                        + "NOW(3),1,1,NOW(3),?,NOW(3),?,0,NULL,NULL)",
                9_200_000_000_000_010L,
                9_100_000_000_000_001L,
                9_200_000_000_000_009L,
                accountId,
                accountId);
        execute(connection, "INSERT INTO un_module_config_check "
                        + "(id,system_id,base_version_id,draft_revision,draft_checksum,status,blocker_count,"
                        + "warning_count,snapshot_size_bytes,report_json,started_at,completed_at,expires_at,checked_by,version) "
                        + "VALUES (?,?,NULL,1,?,'PASSED',0,0,2,JSON_OBJECT(),NOW(3),NOW(3),DATE_ADD(NOW(3),INTERVAL 1 DAY),?,0)",
                9_200_000_000_000_002L, 9_100_000_000_000_001L, "0".repeat(64), accountId);
        var publishedSnapshot = """
                {
                  "modules": [{
                    "id": "9200000000000010",
                    "module_code": "schema_record",
                    "module_name": "Schema record"
                  }],
                  "fields": [{
                    "id": "9200000000000020",
                    "module_id": "9200000000000010",
                    "dictionary_id": null,
                    "target_module_id": null,
                    "field_code": "schema_value",
                    "field_name": "Schema value",
                    "field_type": "TEXT",
                    "is_required": false,
                    "is_readonly": false
                  }]
                }
                """;
        execute(connection, "INSERT INTO un_module_config_version "
                        + "(id,system_id,version_no,source_type,based_on_version_id,rollback_target_version_id,"
                        + "source_check_id,snapshot_json,snapshot_checksum,snapshot_size_bytes,impact_report_json,"
                        + "published_at,published_by,publish_reason) "
                        + "VALUES (?,?,1,'PUBLISH',NULL,NULL,?,CAST(? AS JSON),?,?,JSON_OBJECT(),NOW(3),?,'schema test')",
                9_200_000_000_000_001L, 9_100_000_000_000_001L, 9_200_000_000_000_002L,
                publishedSnapshot, "0".repeat(64), publishedSnapshot.getBytes(java.nio.charset.StandardCharsets.UTF_8).length,
                accountId);
    }

    private static void insertRecord(Connection connection, long recordId, long schemaVersionId) throws SQLException {
        execute(connection, "INSERT INTO un_module_record "
                        + "(id,system_id,tenant_id,record_id,schema_version_id,module_snapshot_id,logical_module_id,"
                        + "record_no,title,status,prior_status,owner_member_id,owner_department_id,draft_expires_at,"
                        + "deleted_at,deleted_by,created_at,created_by,updated_at,updated_by,version) "
                        + "VALUES (?,?,?,?,?,?,?,'P4-001','First record','ACTIVE',NULL,?,NULL,NULL,NULL,NULL,NOW(3),?,NOW(3),?,0)",
                recordId, 9_100_000_000_000_001L, 9_100_000_000_000_002L, recordId, schemaVersionId,
                9_200_000_000_000_010L, 9_200_000_000_000_010L, 9_100_000_000_000_003L,
                9_100_000_000_000_003L, 9_100_000_000_000_003L);
    }

    private static void insertTextValue(
            Connection connection,
            long id,
            String fieldType,
            String stringValue,
            String decimalValue,
            int ordinal
    ) throws SQLException {
        execute(connection, "INSERT INTO un_module_record_value "
                        + "(id,system_id,tenant_id,record_id,schema_version_id,module_snapshot_id,logical_module_id,"
                        + "field_snapshot_id,logical_field_id,field_version,field_type,ordinal,string_value,text_value,"
                        + "decimal_value,date_value,datetime_value,reference_value,encrypted_value,value_hash,display_value,"
                        + "created_at,created_by,updated_at,updated_by,version) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,1,?,?,?,NULL,?,NULL,NULL,NULL,NULL,NULL,NULL,NOW(3),?,NOW(3),?,0)",
                id, 9_100_000_000_000_001L, 9_100_000_000_000_002L, 9_300_000_000_000_001L,
                9_200_000_000_000_001L, 9_200_000_000_000_010L, 9_200_000_000_000_010L,
                9_200_000_000_000_020L, 9_200_000_000_000_020L, fieldType, ordinal, stringValue,
                decimalValue == null ? null : new java.math.BigDecimal(decimalValue), 9_100_000_000_000_003L,
                9_100_000_000_000_003L);
    }

    private static void insertDerivedRuntimeField(
            Connection connection,
            long fieldId,
            String resultSchema,
            boolean readonly
    ) throws SQLException {
        execute(connection, "INSERT INTO un_module_runtime_schema_field "
                        + "(id,system_id,schema_version_id,module_snapshot_id,field_snapshot_id,source_field_id,"
                        + "logical_module_id,logical_field_id,parent_field_snapshot_id,dictionary_id,target_module_id,"
                        + "field_code,field_name,field_type,field_scope,is_required,is_readonly,property_json,"
                        + "result_schema,evaluator_version,expression_checksum,topological_rank,dependency_json,created_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,NULL,NULL,NULL,?,?,'FORMULA','RECORD',FALSE,?,CAST(? AS JSON),"
                        + "?,1,?,0,JSON_ARRAY(),NOW(3))",
                fieldId, 9_100_000_000_000_001L, 9_200_000_000_000_001L,
                9_200_000_000_000_010L, fieldId, fieldId, 9_200_000_000_000_010L, fieldId,
                "derived_" + fieldId, "Derived " + fieldId, readonly,
                "{\"resultSchema\":\"" + resultSchema + "\",\"astVersion\":1,"
                        + "\"expressionAst\":{\"literalType\":\"DECIMAL\",\"value\":\"12.50\"}}",
                resultSchema, "d".repeat(64));
    }

    private static void projectDerivedSnapshot(Connection connection) throws Exception {
        var accountId = 9_100_000_000_000_000L;
        var snapshot = """
                {
                  "modules":[{"id":"9200000000000030","module_code":"derived_order","module_name":"Derived order"}],
                  "fields":[
                    {"id":"9200000000000031","module_id":"9200000000000030","field_code":"amount",
                     "field_name":"Amount","field_type":"NUMBER","is_required":false,"is_readonly":false,
                     "property_json":{}},
                    {"id":"9200000000000032","module_id":"9200000000000030","field_code":"taxed",
                     "field_name":"Taxed","field_type":"FORMULA","is_required":false,"is_readonly":true,
                     "property_json":{"resultSchema":"DECIMAL","astVersion":1,
                       "expressionAst":{"fieldId":"9200000000000031"}}},
                    {"id":"9200000000000033","module_id":"9200000000000030","field_code":"total",
                     "field_name":"Total","field_type":"CALCULATED","is_required":false,"is_readonly":true,
                     "property_json":{"resultSchema":"DECIMAL","astVersion":1,
                       "expressionAst":{"op":"ADD","args":[{"fieldId":"9200000000000032"},
                         {"literalType":"INTEGER","value":1}]}}}
                  ]
                }
                """;
        execute(connection, "INSERT INTO un_module_config_check "
                        + "(id,system_id,base_version_id,draft_revision,draft_checksum,status,blocker_count,"
                        + "warning_count,snapshot_size_bytes,report_json,started_at,completed_at,expires_at,checked_by,version) "
                        + "VALUES (?,?,NULL,2,?,'PASSED',0,0,?,JSON_OBJECT(),NOW(3),NOW(3),"
                        + "DATE_ADD(NOW(3),INTERVAL 1 DAY),?,0)",
                9_200_000_000_000_004L, 9_100_000_000_000_001L, "1".repeat(64),
                snapshot.getBytes(java.nio.charset.StandardCharsets.UTF_8).length, accountId);
        execute(connection, "INSERT INTO un_module_config_version "
                        + "(id,system_id,version_no,source_type,based_on_version_id,rollback_target_version_id,"
                        + "source_check_id,snapshot_json,snapshot_checksum,snapshot_size_bytes,impact_report_json,"
                        + "published_at,published_by,publish_reason) "
                        + "VALUES (?,?,2,'PUBLISH',?,NULL,?,CAST(? AS JSON),?,?,JSON_OBJECT(),NOW(3),?,'derived projection test')",
                9_200_000_000_000_003L, 9_100_000_000_000_001L, 9_200_000_000_000_001L,
                9_200_000_000_000_004L, snapshot, "1".repeat(64),
                snapshot.getBytes(java.nio.charset.StandardCharsets.UTF_8).length, accountId);

        var dataSource = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        var objectMapper = new ObjectMapper();
        var derivedFields = new DerivedFieldContractService(objectMapper);
        new RuntimeSchemaProjectionService(new JdbcTemplate(dataSource), new IdService(), objectMapper, derivedFields)
                .project(9_100_000_000_000_001L, 9_200_000_000_000_003L,
                        objectMapper.readTree(snapshot), LocalDateTime.now());
    }

    private static void insertDerivedValue(
            Connection connection,
            long id,
            String resultSchema,
            String decimalValue,
            String state,
            String failureCorrelationId
    ) throws SQLException {
        execute(connection, "INSERT INTO un_module_record_value "
                        + "(id,system_id,tenant_id,record_id,schema_version_id,module_snapshot_id,logical_module_id,"
                        + "field_snapshot_id,logical_field_id,field_version,field_type,field_scope,result_schema,"
                        + "dependency_version_json,evaluator_version,recalculation_state,failure_correlation_id,ordinal,"
                        + "decimal_value,created_at,created_by,updated_at,updated_by,version) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,1,'FORMULA','RECORD',?,JSON_ARRAY(JSON_OBJECT('fieldId','9200000000000020',"
                        + "'version','0')),1,?,?,0,?,NOW(3),?,NOW(3),?,0)",
                id, 9_100_000_000_000_001L, 9_100_000_000_000_002L, 9_300_000_000_000_001L,
                9_200_000_000_000_001L, 9_200_000_000_000_010L, 9_200_000_000_000_010L,
                9_200_000_000_000_021L, 9_200_000_000_000_021L, resultSchema, state,
                failureCorrelationId, new java.math.BigDecimal(decimalValue),
                9_100_000_000_000_003L, 9_100_000_000_000_003L);
    }

    private static void insertValueForMissingRecord(Connection connection) throws SQLException {
        execute(connection, "INSERT INTO un_module_record_value "
                        + "(id,system_id,tenant_id,record_id,schema_version_id,module_snapshot_id,logical_module_id,"
                        + "field_snapshot_id,logical_field_id,field_version,field_type,ordinal,string_value,"
                        + "created_at,created_by,updated_at,updated_by,version) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,1,'TEXT',0,'missing',NOW(3),?,NOW(3),?,0)",
                9_400_000_000_000_004L, 9_100_000_000_000_001L, 9_100_000_000_000_002L,
                9_399_999_999_999_999L, 9_200_000_000_000_001L, 9_200_000_000_000_010L,
                9_200_000_000_000_010L, 9_200_000_000_000_020L, 9_200_000_000_000_020L,
                9_100_000_000_000_003L, 9_100_000_000_000_003L);
    }

    private static void insertSensitiveValue(
            Connection connection,
            long id,
            String fieldType,
            int ordinal,
            String stringValue,
            String encryptionKeyVersion,
            String hashKeyVersion
    ) throws SQLException {
        execute(connection, "INSERT INTO un_module_record_value "
                        + "(id,system_id,tenant_id,record_id,schema_version_id,module_snapshot_id,logical_module_id,"
                        + "field_snapshot_id,logical_field_id,field_version,field_type,ordinal,string_value,"
                        + "encrypted_value,encryption_key_version,value_hash,hash_key_version,display_value,"
                        + "created_at,created_by,updated_at,updated_by,version) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,1,?,?,?,?,?,?,?,NULL,NOW(3),?,NOW(3),?,0)",
                id, 9_100_000_000_000_001L, 9_100_000_000_000_002L, 9_300_000_000_000_001L,
                9_200_000_000_000_001L, 9_200_000_000_000_010L, 9_200_000_000_000_010L,
                9_200_000_000_000_020L, 9_200_000_000_000_020L, fieldType, ordinal, stringValue,
                new byte[48], encryptionKeyVersion, "a".repeat(64), hashKeyVersion,
                9_100_000_000_000_003L, 9_100_000_000_000_003L);
    }

    private static void insertHashIndex(Connection connection, long id, String hashVersion) throws SQLException {
        execute(connection, "INSERT INTO un_module_record_index "
                        + "(id,system_id,tenant_id,record_id,schema_version_id,module_snapshot_id,logical_module_id,"
                        + "logical_field_id,index_generation_id,normalization_generation_id,path_snapshot_id,ordinal,"
                        + "record_status,value_kind,hash_value,hash_key_version,created_at,updated_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,1,1,0,0,'ACTIVE','HASH',?,?,NOW(3),NOW(3))",
                id, 9_100_000_000_000_001L, 9_100_000_000_000_002L, 9_300_000_000_000_001L,
                9_200_000_000_000_001L, 9_200_000_000_000_010L, 9_200_000_000_000_010L,
                9_200_000_000_000_021L, "b".repeat(64), hashVersion);
    }

    private static void insertTimeIndex(Connection connection, long id) throws SQLException {
        execute(connection, "INSERT INTO un_module_record_index "
                        + "(id,system_id,tenant_id,record_id,schema_version_id,module_snapshot_id,logical_module_id,"
                        + "logical_field_id,index_generation_id,normalization_generation_id,path_snapshot_id,ordinal,"
                        + "record_status,value_kind,time_value,created_at,updated_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,1,1,0,0,'ACTIVE','TIME','09:30:00',NOW(3),NOW(3))",
                id, 9_100_000_000_000_001L, 9_100_000_000_000_002L, 9_300_000_000_000_001L,
                9_200_000_000_000_001L, 9_200_000_000_000_010L, 9_200_000_000_000_010L,
                9_200_000_000_000_019L);
    }

    private static void insertGeoIndex(Connection connection, long id, double lat, double lng) throws SQLException {
        execute(connection, "INSERT INTO un_module_record_index "
                        + "(id,system_id,tenant_id,record_id,schema_version_id,module_snapshot_id,logical_module_id,"
                        + "logical_field_id,index_generation_id,normalization_generation_id,path_snapshot_id,ordinal,"
                        + "record_status,value_kind,geohash,geo_lat,geo_lng,created_at,updated_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,1,1,0,0,'ACTIVE','GEO','wx4fbxxfke4u',?,?,NOW(3),NOW(3))",
                id, 9_100_000_000_000_001L, 9_100_000_000_000_002L, 9_300_000_000_000_001L,
                9_200_000_000_000_001L, 9_200_000_000_000_010L, 9_200_000_000_000_010L,
                9_200_000_000_000_022L, lat, lng);
    }

    private static void insertSearchToken(Connection connection, long id, String fieldType) throws SQLException {
        execute(connection, "INSERT INTO un_module_record_search "
                        + "(id,system_id,tenant_id,record_id,schema_version_id,module_snapshot_id,logical_module_id,"
                        + "logical_field_id,index_generation_id,record_status,field_type,token_ordinal,token,token_hash,"
                        + "created_at) VALUES (?,?,?,?,?,?,?,?,1,'ACTIVE',?,0,'search-token',?,NOW(3))",
                id, 9_100_000_000_000_001L, 9_100_000_000_000_002L, 9_300_000_000_000_001L,
                9_200_000_000_000_001L, 9_200_000_000_000_010L, 9_200_000_000_000_010L,
                9_200_000_000_000_023L, fieldType, "c".repeat(64));
    }

    private static void assertConstraintRejected(ThrowingSql operation) {
        assertThatThrownBy(operation::run)
                .isInstanceOf(SQLException.class)
                .satisfies(error -> assertThat(((SQLException) error).getErrorCode())
                        .isIn(1062, 1452, 3819));
    }

    private static void execute(Connection connection, String sql, Object... values) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) {
            for (var index = 0; index < values.length; index++) {
                statement.setObject(index + 1, values[index]);
            }
            statement.executeUpdate();
        }
    }

    private static long queryLong(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getLong(1);
        }
    }

    private static Path migrationRoot() {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            var candidate = current.resolve("sql/migration");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate sql/migration from test process");
    }

    private static int migrationFileCount() throws Exception {
        try (var migrationFiles = Files.list(migrationRoot())) {
            return Math.toIntExact(migrationFiles
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().matches("V.+__.+\\.sql"))
                    .count());
        }
    }

    @FunctionalInterface
    private interface ThrowingSql {
        void run() throws SQLException;
    }
}
