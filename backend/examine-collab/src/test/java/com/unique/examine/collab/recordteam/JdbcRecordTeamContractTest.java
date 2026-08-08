package com.unique.examine.collab.recordteam;

import com.unique.examine.core.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcRecordTeamContractTest {
    private static final String MIGRATION = "V5_0_0__collab_record_team.sql";

    private JdbcTemplate jdbc;
    private JdbcRecordTeamRepository repository;
    private TransactionTemplate transaction;

    @BeforeEach
    void setUpRepository() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:record_team_" + UUID.randomUUID()
                        + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE un_collab_record_team (
                    system_id BIGINT NOT NULL,
                    tenant_id BIGINT NOT NULL,
                    record_id BIGINT NOT NULL,
                    version BIGINT NOT NULL,
                    created_at TIMESTAMP(3) NOT NULL,
                    created_by BIGINT NOT NULL,
                    updated_at TIMESTAMP(3) NOT NULL,
                    updated_by BIGINT NOT NULL,
                    PRIMARY KEY (system_id, tenant_id, record_id)
                )
                """);
        jdbc.execute("""
                CREATE TABLE un_collab_record_team_member (
                    system_id BIGINT NOT NULL,
                    tenant_id BIGINT NOT NULL,
                    record_id BIGINT NOT NULL,
                    member_id BIGINT NOT NULL,
                    team_role VARCHAR(32) NOT NULL,
                    row_version BIGINT NOT NULL,
                    created_at TIMESTAMP(3) NOT NULL,
                    created_by BIGINT NOT NULL,
                    updated_at TIMESTAMP(3) NOT NULL,
                    updated_by BIGINT NOT NULL,
                    PRIMARY KEY (system_id, tenant_id, record_id, member_id)
                )
                """);
        var transactionManager = new DataSourceTransactionManager(dataSource);
        repository = new JdbcRecordTeamRepository(jdbc, transactionManager);
        transaction = new TransactionTemplate(transactionManager);
    }

    @Test
    void jdbcInitializationIsIdempotentTenantScopedAndDoesNotReplaceTheOwner() {
        var key = new RecordTeamKey("1", "2", "3");

        var first = repository.initialize(RecordTeam.initialize(key, "10"));
        var repeated = repository.initialize(RecordTeam.initialize(key, "11"));
        var otherTenant = repository.initialize(RecordTeam.initialize(
                new RecordTeamKey("1", "4", "3"),
                "11"));

        assertThat(first.created()).isTrue();
        assertThat(first.team().owner().memberId()).isEqualTo("10");
        assertThat(repeated.created()).isFalse();
        assertThat(repeated.team().owner().memberId()).isEqualTo("10");
        assertThat(repeated.team().version()).isEqualTo(1);
        assertThat(otherTenant.created()).isTrue();
        assertThat(otherTenant.team().owner().memberId()).isEqualTo("11");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM un_collab_record_team",
                Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM un_collab_record_team_member",
                Long.class)).isEqualTo(2);
    }

    @Test
    void everyRepositoryLookupIsTenantScopedAndMutationsUseLocksAndCas() throws Exception {
        for (var sql : List.of(
                JdbcRecordTeamRepository.FIND_TEAM_SQL,
                JdbcRecordTeamRepository.LOCK_TEAM_SQL,
                JdbcRecordTeamRepository.FIND_MEMBERS_SQL,
                JdbcRecordTeamRepository.DELETE_MEMBER_SQL,
                JdbcRecordTeamRepository.UPDATE_MEMBER_ROLE_SQL,
                JdbcRecordTeamRepository.UPDATE_TEAM_SQL)) {
            assertThat(sql)
                    .contains("system_id = ?")
                    .contains("tenant_id = ?")
                    .contains("record_id = ?");
        }
        assertThat(JdbcRecordTeamRepository.LOCK_TEAM_SQL).containsIgnoringCase("FOR UPDATE");
        assertThat(RecordTeamRepository.class.getMethod("lockAll", List.class)).isNotNull();
        assertThat(JdbcRecordTeamRepository.UPDATE_TEAM_SQL)
                .contains("version = ?")
                .contains("AND version = ?");
    }

    @Test
    void persistedRowsRestoreTheDomainInvariantAndVersion() {
        var key = new RecordTeamKey("1", "2", "3");
        var restored = RecordTeam.restore(
                key,
                List.of(
                        new RecordTeamMember("10", RecordTeamRole.OWNER),
                        new RecordTeamMember("11", RecordTeamRole.VIEWER)),
                7);

        assertThat(restored.key()).isEqualTo(key);
        assertThat(restored.version()).isEqualTo(7);
        assertThat(restored.owner().memberId()).isEqualTo("10");
        assertThat(restored.members()).hasSize(2);

        assertThatThrownBy(() -> RecordTeam.restore(
                key,
                List.of(
                        new RecordTeamMember("10", RecordTeamRole.OWNER),
                        new RecordTeamMember("11", RecordTeamRole.OWNER)),
                7))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("RECORD_TEAM_OWNER_INVARIANT_VIOLATION"));
    }

    @Test
    void existingTeamOwnershipTransferCommitsWithoutDuplicateInitializationRollback() {
        var key = new RecordTeamKey("1", "2", "3");
        repository.create(RecordTeam.restore(
                key,
                List.of(
                        new RecordTeamMember("10", RecordTeamRole.OWNER),
                        new RecordTeamMember("11", RecordTeamRole.VIEWER)),
                5));
        var bridge = new RuntimeRecordTeamOwnershipBridge(repository);

        transaction.executeWithoutResult(status -> {
            assertThat(bridge.lockExistingTeams(1, 2, List.of(3L), 11)).hasSize(1);
            bridge.transferOwnership(1, 2, 3, 10, 11, 10);
        });

        var committed = repository.find(key).orElseThrow();
        assertThat(committed.owner().memberId()).isEqualTo("11");
        assertThat(committed.member("10")).contains(
                new RecordTeamMember("10", RecordTeamRole.COLLABORATOR));
        assertThat(committed.version()).isEqualTo(6);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM un_collab_record_team",
                Long.class)).isOne();
    }

    @Test
    void migrationDeclaresTenantMemberAndUniqueOwnerContracts() throws IOException {
        var sql = Files.readString(locateMigration());

        assertThat(sql)
                .contains("CREATE TABLE un_collab_record_team")
                .contains("CREATE TABLE un_collab_record_team_member")
                .contains("FOREIGN KEY (system_id, tenant_id, record_id)")
                .contains("FOREIGN KEY (system_id, member_id, tenant_id)")
                .contains("uk_collab_team_member")
                .contains("uk_collab_team_single_owner")
                .contains("owner_slot TINYINT GENERATED ALWAYS AS")
                .contains("ck_collab_team_role")
                .contains("ck_collab_team_version")
                .contains("created_at DATETIME(3) NOT NULL")
                .contains("updated_at DATETIME(3) NOT NULL");
    }

    private static Path locateMigration() {
        var cursor = Path.of("").toAbsolutePath();
        while (cursor != null) {
            var candidate = cursor.resolve("sql").resolve("migration").resolve(MIGRATION);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            cursor = cursor.getParent();
        }
        throw new AssertionError("Cannot locate migration " + MIGRATION);
    }
}
