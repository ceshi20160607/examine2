package com.unique.examine.module.kpi.adapter.jdbc;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static com.unique.examine.module.datasource.HttpDataSourceTestFixtures.activeRoot;
import static com.unique.examine.module.datasource.HttpDataSourceTestFixtures.repository;
import static com.unique.examine.module.datasource.HttpDataSourceTestFixtures.version;

class JdbcKpiSourceCatalogTest {
    @Test
    void exposesPinnedJoinCapabilitiesWithoutJdbcSchemaLookup() {
        var version = com.unique.examine.module.datasource
                .MultiModuleJoinTestFixtures.version();
        var catalog = new JdbcKpiSourceCatalog(
                com.unique.examine.module.datasource.MultiModuleJoinTestFixtures
                        .repository(com.unique.examine.module.datasource
                                .MultiModuleJoinTestFixtures.activeRoot(version),
                                version),
                new JdbcTemplate());

        assertThat(catalog.version(10, 20, 900, 9001)).get()
                .satisfies(source -> assertThat(source.fields())
                        .extracting(value -> value.code())
                        .containsExactly("customers__id", "orders__amount"));
    }

    @Test
    void hidesActiveAndPinnedHttpVersionsBeforeJdbcLookup() {
        var version = version();
        var catalog = new JdbcKpiSourceCatalog(
                repository(activeRoot(version), version),
                new JdbcTemplate());

        assertThat(catalog.active(10, 20, 100)).isEmpty();
        assertThat(catalog.version(10, 20, 100, version.id())).isEmpty();
    }
}
