package com.unique.examine.module.dashboard.adapter.jdbc;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcDashboardKpiCatalogTest {
    @Test
    void activeLookupIsTenantScopedAndPinsTheExactRootPointer() {
        var jdbc = new RecordingJdbcTemplate();
        var catalog = new JdbcDashboardKpiCatalog(jdbc);

        assertThat(catalog.activeVersion(10, 20, 900)).isEmpty();

        assertThat(normalize(jdbc.sql))
                .contains("from un_module_kpi root")
                .contains("join un_module_kpi_version version_row")
                .contains("version_row.system_id=root.system_id")
                .contains("version_row.tenant_id=root.tenant_id")
                .contains("version_row.kpi_id=root.id")
                .contains("version_row.id=root.active_version_id")
                .contains("version_row.version_no=root.active_version_no")
                .contains("where root.system_id=? and root.tenant_id=?")
                .contains("and root.id=?");
        assertThat(jdbc.arguments).containsExactly(10L, 20L, 900L);
    }

    private static String normalize(String value) {
        return value.toLowerCase().replaceAll("\\s+", " ").trim();
    }

    private static final class RecordingJdbcTemplate extends JdbcTemplate {
        private String sql;
        private Object[] arguments;

        @Override
        public <T> List<T> query(
                String sql,
                RowMapper<T> mapper,
                Object... arguments
        ) {
            this.sql = sql;
            this.arguments = arguments;
            return List.of();
        }
    }
}
