package com.unique.examine.flow.interaction.jdbc;

import com.unique.examine.flow.domain.ApprovalDomainException;
import com.unique.examine.flow.interaction.FlowUrge;
import com.unique.examine.flow.interaction.FlowCopy;
import com.unique.examine.flow.repository.jdbc.FlowTenantScope;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

import static com.unique.examine.flow.domain.ApprovalDomainException.Code.INSTANCE_STATE_INVALID;
import static com.unique.examine.flow.domain.ApprovalDomainException.Code.COPY_ALREADY_EXISTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlowInteractionJdbcContractTest {
    @Test
    void everyInteractionReadIsTenantScopedAndAscendingWithStableTieBreak() {
        var reads = List.of(
                JdbcFlowInteractionSql.SELECT_URGES,
                JdbcFlowInteractionSql.COUNT_URGES,
                JdbcFlowInteractionSql.SELECT_COMMENTS,
                JdbcFlowInteractionSql.COUNT_COMMENTS,
                JdbcFlowInteractionSql.SELECT_COPIES,
                JdbcFlowInteractionSql.COUNT_COPIES
        );
        assertThat(reads).allSatisfy(sql ->
                assertThat(normalize(sql))
                        .contains("system_id=?", "tenant_id=?", "instance_id=?"));
        assertThat(normalize(JdbcFlowInteractionSql.SELECT_URGES))
                .contains("order by created_at asc,urge_id asc", "limit ? offset ?");
        assertThat(normalize(JdbcFlowInteractionSql.SELECT_COMMENTS))
                .contains("order by created_at asc,comment_id asc", "limit ? offset ?");
        assertThat(normalize(JdbcFlowInteractionSql.SELECT_COPIES))
                .contains("order by created_at asc,copy_id asc", "limit ? offset ?");
    }

    @Test
    void interactionWritesAreAppendOnlyDedicatedTableInserts() {
        assertThat(normalize(JdbcFlowInteractionSql.INSERT_URGE))
                .startsWith("insert into un_flow_urge")
                .contains(
                        "select ?,?,?,instance_id,?,?,?,? from un_flow_instance",
                        "status='pending'",
                        "requester_id=?",
                        "approver_id=?")
                .doesNotContain(" update ", " delete ");
        assertThat(normalize(JdbcFlowInteractionSql.INSERT_COMMENT))
                .startsWith("insert into un_flow_comment")
                .doesNotContain(" update ", " delete ");
        assertThat(normalize(JdbcFlowInteractionSql.INSERT_COPY))
                .startsWith("insert into un_flow_copy_recipient")
                .doesNotContain(" update ", " delete ");
    }

    @Test
    void urgeRepositoryMapsFailedAtomicPendingGuardToStateConflict() {
        var repository = new JdbcFlowInteractionRepository(
                new ZeroRowJdbcTemplate(),
                new FlowTenantScope(1, 2)
        );

        assertThatThrownBy(() -> repository.saveUrge(new FlowUrge(
                3,
                4,
                5,
                6,
                "review",
                Instant.parse("2026-07-27T10:00:00Z")
        ))).isInstanceOfSatisfying(
                ApprovalDomainException.class,
                error -> assertThat(error.code()).isEqualTo(INSTANCE_STATE_INVALID)
        );
    }

    @Test
    void copyRepositoryMapsUniqueConflictToFrozenCopyError() {
        var repository = new JdbcFlowInteractionRepository(
                new DuplicateJdbcTemplate(),
                new FlowTenantScope(1, 2)
        );

        assertThatThrownBy(() -> repository.saveCopy(new FlowCopy(
                3,
                4,
                5,
                6,
                "follow",
                Instant.parse("2026-07-27T10:00:00Z")
        ))).isInstanceOfSatisfying(
                ApprovalDomainException.class,
                error -> assertThat(error.code()).isEqualTo(COPY_ALREADY_EXISTS)
        );
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private static final class ZeroRowJdbcTemplate extends JdbcTemplate {
        @Override
        public int update(String sql, Object... args) {
            return 0;
        }
    }

    private static final class DuplicateJdbcTemplate extends JdbcTemplate {
        @Override
        public int update(String sql, Object... args) {
            throw new org.springframework.dao.DuplicateKeyException("duplicate");
        }
    }
}
