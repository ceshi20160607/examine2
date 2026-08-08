package com.unique.examine.plat.directory;

import com.unique.examine.core.api.KpiReminderRecipientFacade;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

class KpiReminderRecipientBridgeContractTest {
    @Test
    void queryHidesInactiveAndCrossTenantDepartmentsAndMembers() {
        var sql = normalize(
                KpiReminderRecipientBridge.ACTIVE_DEPARTMENT_MEMBERS_SQL);

        assertThat(sql)
                .contains("from un_plat_department d")
                .contains("md.tenant_key=d.tenant_key")
                .contains("mt.tenant_id=?")
                .contains("d.scope_type='system'")
                .contains("d.scope_key=?")
                .contains("d.system_id=?")
                .contains("d.id=?")
                .contains("d.tenant_id is null or d.tenant_id=?")
                .contains("d.status='active'")
                .contains("d.deleted_at is null")
                .contains("md.system_id=?")
                .contains("md.tenant_id is null or md.tenant_id=?")
                .contains("md.deleted_at is null")
                .contains("m.status='active'")
                .contains("m.deleted_at is null")
                .contains("mt.status='active'")
                .contains("mt.deleted_at is null")
                .contains("mt.expires_at>current_timestamp(3)")
                .contains("select distinct m.id")
                .contains("order by m.id")
                .contains("limit 1000")
                .contains("for share");
    }

    @Test
    void returnsSortedDeduplicatedBoundedIdsAndPassesExactScope() {
        var rows = LongStream.rangeClosed(1, 1_005)
                .map(value -> 1_006 - value)
                .boxed()
                .toList();
        var jdbc = new RecordingJdbcTemplate(rows);
        var bridge = new KpiReminderRecipientBridge(jdbc);

        var result = bridge.activeDepartmentMemberIds(10, 20, 30);

        assertThat(result).hasSize(KpiReminderRecipientFacade.MAX_RECIPIENTS);
        assertThat(result.getFirst()).isEqualTo(1L);
        assertThat(result.getLast()).isEqualTo(1_000L);
        assertThat(jdbc.arguments)
                .containsExactly(20L, 10L, 10L, 30L, 20L, 10L, 20L);
    }

    @Test
    void invalidOrMissingIdentityReturnsAnEmptyNonLeakingResult()
            throws Exception {
        var bridge = new KpiReminderRecipientBridge(new JdbcTemplate());

        assertThat(bridge.activeDepartmentMemberIds(0, 20, 30)).isEmpty();
        assertThat(bridge.activeDepartmentMemberIds(10, 0, 30)).isEmpty();
        assertThat(bridge.activeDepartmentMemberIds(10, 20, 0)).isEmpty();
        assertThat(KpiReminderRecipientBridge.class.getMethod(
                "activeDepartmentMemberIds",
                long.class, long.class, long.class)
                .getAnnotation(Transactional.class).propagation().name())
                .isEqualTo("MANDATORY");
    }

    private static String normalize(String value) {
        return value.toLowerCase().replaceAll("\\s+", " ").trim();
    }

    private static final class RecordingJdbcTemplate extends JdbcTemplate {
        private final List<Long> rows;
        private Object[] arguments;

        private RecordingJdbcTemplate(List<Long> rows) {
            this.rows = rows;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> query(
                String sql,
                RowMapper<T> mapper,
                Object... arguments
        ) {
            this.arguments = arguments;
            return (List<T>) rows;
        }
    }
}
