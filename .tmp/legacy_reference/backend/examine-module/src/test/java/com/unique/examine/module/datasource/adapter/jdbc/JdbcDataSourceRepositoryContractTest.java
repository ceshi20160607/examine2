package com.unique.examine.module.datasource.adapter.jdbc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.id.IdService;
import com.unique.examine.module.datasource.domain.DataSourceDraft;
import com.unique.examine.module.datasource.domain.DataSourceException;
import com.unique.examine.module.datasource.domain.DataSourceVersion;
import com.unique.examine.module.datasource.domain.ModuleDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcDataSourceRepositoryContractTest {
    @Test
    void draftSaveUsesScopedImmutableIdentityAndDualCasFacts() {
        var jdbc = new RecordingJdbcTemplate();
        var repository = repository(jdbc);
        var expected = root();
        var revised = expected.reviseDraft(
                "Orders v2", "Current orders", draft("amount"),
                expected.updatedAt().plusSeconds(1));

        assertThat(repository.saveDraft(expected, revised)).isEqualTo(revised);

        var call = jdbc.updates.getFirst();
        assertThat(normalize(call.sql()))
                .contains("where system_id=? and tenant_id=? and id=?")
                .contains("data_source_code=? and module_id=?")
                .contains("draft_version=? and version=?")
                .contains("active_version_id <=> ?")
                .contains("active_version_no <=> ?")
                .doesNotContain("set data_source_code=", "set module_id=");
        assertThat(call.arguments())
                .contains(revised.draftVersion(), revised.version(),
                        expected.systemId(), expected.tenantId(), expected.id(),
                        expected.code(), expected.moduleId(),
                        expected.draftVersion(), expected.version());
    }

    @Test
    void publishAtomicallyInsertsImmutableSnapshotThenAdvancesScopedPointer() {
        var jdbc = new RecordingJdbcTemplate();
        var repository = repository(jdbc);
        var expected = root();
        var version = new DataSourceVersion(
                200, expected.id(), expected.systemId(), expected.tenantId(),
                1, expected.code(), expected.moduleId(), "orders", "501",
                expected.name(), expected.description(), expected.draft(),
                "a".repeat(64), 30, expected.updatedAt().plusSeconds(1));
        var activated = expected.activate(
                version, expected.updatedAt().plusSeconds(1));

        assertThat(repository.publish(expected, activated, version))
                .isEqualTo(version);

        assertThat(jdbc.updates).hasSize(2);
        assertThat(normalize(jdbc.updates.getFirst().sql()))
                .startsWith("insert into un_module_data_source_version")
                .contains("source_draft_version")
                .contains("snapshot_json", "snapshot_fingerprint");
        assertThat(jdbc.updates.getFirst().arguments()[5])
                .isEqualTo(expected.draftVersion());
        assertThat(normalize(jdbc.updates.get(1).sql()))
                .startsWith("update un_module_data_source")
                .contains("set active_version_id=?,active_version_no=?")
                .contains("where system_id=? and tenant_id=? and id=?")
                .contains("draft_version=? and version=?");
    }

    @Test
    void staleDraftCasFailsWithoutReturningAFalseSuccess() {
        var jdbc = new RecordingJdbcTemplate();
        jdbc.affected = 0;
        var repository = repository(jdbc);
        var expected = root();
        var revised = expected.reviseDraft(
                "Orders v2", null, draft("amount"),
                expected.updatedAt().plusSeconds(1));

        assertThatThrownBy(() -> repository.saveDraft(expected, revised))
                .isInstanceOfSatisfying(DataSourceException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("DATA_SOURCE_VERSION_CONFLICT"));
    }

    @Test
    void readsAndListsAreAlwaysTenantScopedWithStableOrdering() {
        var jdbc = new RecordingJdbcTemplate();
        var repository = repository(jdbc);

        assertThat(repository.findById(10, 20, 100)).isEmpty();
        assertThat(repository.findByCode(10, 20, "orders")).isEmpty();
        assertThat(repository.findAll(10, 20)).isEmpty();
        assertThat(repository.findActiveVersion(10, 20, 100)).isEmpty();
        assertThat(repository.findVersion(10, 20, 100, 1)).isEmpty();
        assertThat(repository.findVersions(10, 20, 100)).isEmpty();

        assertThat(jdbc.queries)
                .allSatisfy(call -> assertThat(normalize(call.sql()))
                        .contains("system_id=?", "tenant_id=?"));
        assertThat(normalize(jdbc.queries.get(2).sql()))
                .contains("order by updated_at desc,id desc");
        assertThat(normalize(jdbc.queries.get(3).sql()))
                .contains("join un_module_data_source_version version_row")
                .contains("version_row.system_id=root.system_id")
                .contains("version_row.tenant_id=root.tenant_id")
                .contains("version_row.id=root.active_version_id")
                .contains("version_row.version_no=root.active_version_no")
                .contains("select version_row.id,version_row.data_source_id");
        assertThat(normalize(jdbc.queries.getLast().sql()))
                .contains("order by version_no desc,id desc");
    }

    @Test
    void httpProjectionRoundTripsOnlyThroughExistingDraftJson()
            throws Exception {
        var jdbc = new RecordingJdbcTemplate();
        var repository = repository(jdbc);
        var expected = root();
        var httpDraft = new DataSourceDraft(
                List.of(), List.of(), null, null,
                DataSourceDraft.SourceKind.HTTP_JSON,
                new DataSourceDraft.HttpJsonConnection(
                        "https://datasource.example.test/query",
                        "env://EXAMINE_DS_S10_T20_ORDERS_V1", 5),
                List.of(new DataSourceDraft.HttpJsonFieldProjection(
                        "external_title", "title",
                        DataSourceDraft.HttpJsonSourceType.STRING)));
        var revised = expected.reviseDraft(
                expected.name(), expected.description(), httpDraft,
                expected.updatedAt().plusSeconds(1));

        repository.saveDraft(expected, revised);

        var storedJson = (String) jdbc.updates.getFirst().arguments()[2];
        var stored = new ObjectMapper().readValue(
                storedJson, DataSourceDraft.class);
        assertThat(stored).isEqualTo(httpDraft);
        assertThat(storedJson)
                .contains("httpFieldProjections", "external_title")
                .doesNotContain("sample", "responseBody");
    }

    @Test
    void jdbcSnapshotRoundTripsThroughExistingJsonColumns() throws Exception {
        var jdbc = new RecordingJdbcTemplate();
        var repository = repository(jdbc);
        var expected = root();
        var jdbcDraft = new DataSourceDraft(
                List.of(), List.of(), null, null,
                DataSourceDraft.SourceKind.JDBC_TABLE, null, List.of(),
                new DataSourceDraft.JdbcTableConnection(
                        "mysql.internal", 3306, "operations", "orders",
                        "secret://systems/10/tenants/20/mysql-user",
                        "secret://systems/10/tenants/20/mysql-password",
                        3, 5),
                List.of(new DataSourceDraft.JdbcTableFieldProjection(
                        "external_title", "title",
                        DataSourceDraft.JdbcTableSourceType.STRING)));
        var revised = expected.reviseDraft(
                expected.name(), expected.description(), jdbcDraft,
                expected.updatedAt().plusSeconds(1));

        repository.saveDraft(expected, revised);

        var storedJson = (String) jdbc.updates.getFirst().arguments()[2];
        var stored = new ObjectMapper().readValue(
                storedJson, DataSourceDraft.class);
        assertThat(stored).isEqualTo(jdbcDraft);
        assertThat(storedJson)
                .contains("JDBC_TABLE", "jdbcTableConnection",
                        "jdbcFieldProjections", "external_title")
                .doesNotContain("jdbc:", "SELECT", "driverClassName");
    }

    private static JdbcDataSourceRepository repository(
            RecordingJdbcTemplate jdbc
    ) {
        var sequence = new AtomicLong(900);
        var ids = new IdService() {
            @Override
            public long nextId() {
                return sequence.incrementAndGet();
            }
        };
        return new JdbcDataSourceRepository(
                jdbc, new ObjectMapper(), ids, transactions());
    }

    private static ModuleDataSource root() {
        return ModuleDataSource.create(
                100, 10, 20, "orders", 50,
                "Orders", null, draft("title"),
                Instant.parse("2026-08-01T00:00:00Z"));
    }

    private static DataSourceDraft draft(String fieldCode) {
        return new DataSourceDraft(
                List.of(new DataSourceDraft.OutputField(fieldCode)),
                List.of(), null, null);
    }

    private static PlatformTransactionManager transactions() {
        return new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(
                    TransactionDefinition definition
            ) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
            }

            @Override
            public void rollback(TransactionStatus status) {
            }
        };
    }

    private static String normalize(String value) {
        return value.toLowerCase().replaceAll("\\s+", " ").trim();
    }

    private static final class RecordingJdbcTemplate extends JdbcTemplate {
        private final List<Call> updates = new ArrayList<>();
        private final List<Call> queries = new ArrayList<>();
        private int affected = 1;

        @Override
        public int update(String sql, Object... arguments) {
            updates.add(new Call(sql, arguments));
            return affected;
        }

        @Override
        public <T> List<T> query(
                String sql,
                org.springframework.jdbc.core.RowMapper<T> rowMapper,
                Object... arguments
        ) {
            queries.add(new Call(sql, arguments));
            return List.of();
        }
    }

    private record Call(String sql, Object[] arguments) {
    }
}
