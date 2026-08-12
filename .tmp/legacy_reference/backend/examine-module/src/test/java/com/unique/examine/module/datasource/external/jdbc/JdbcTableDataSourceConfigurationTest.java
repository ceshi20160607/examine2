package com.unique.examine.module.datasource.external.jdbc;

import com.unique.examine.core.api.SecretResolverFacade;
import com.unique.examine.module.datasource.port.DataSourceModuleCatalog;
import com.unique.examine.module.datasource.port.DataSourceRepository;
import com.unique.examine.module.datasource.service.DataSourceService;
import com.unique.examine.module.datasource.service.JdbcTableDataSourceConnectionCheckUseCase;
import com.unique.examine.module.datasource.service.JdbcTableDataSourceDraftRowsPreviewUseCase;
import com.unique.examine.module.datasource.service.JdbcTableDataSourcePublicationPreflight;
import com.unique.examine.module.datasource.service.JdbcTableDataSourceSchemaDiscoveryUseCase;
import com.unique.examine.module.datasource.service.PublishedJdbcTableDataSourceRowsReader;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcTableDataSourceConfigurationTest {
    private final ApplicationContextRunner context =
            new ApplicationContextRunner()
                    .withUserConfiguration(
                            JdbcTableDataSourceConfiguration.class)
                    .withBean(DataSourceService.class,
                            JdbcTableDataSourceConfigurationTest::dataSources)
                    .withBean(SecretResolverFacade.class,
                            () -> request -> Optional.empty());

    @Test
    void registersOnlySpecializedJdbcPortsWithSecureDefaults() {
        context.run(application -> {
            assertThat(application).hasNotFailed()
                    .hasSingleBean(JdbcTableDataSourceProperties.class)
                    .hasSingleBean(JdbcTableSafeClient.class)
                    .hasSingleBean(
                            JdbcTableDataSourceConnectionCheckUseCase.class)
                    .hasSingleBean(
                            JdbcTableDataSourceSchemaDiscoveryUseCase.class)
                    .hasSingleBean(
                            JdbcTableDataSourceDraftRowsPreviewUseCase.class)
                    .hasSingleBean(
                            JdbcTableDataSourcePublicationPreflight.class)
                    .hasSingleBean(
                            PublishedJdbcTableDataSourceRowsReader.class);
            var properties = application.getBean(
                    JdbcTableDataSourceProperties.class);
            assertThat(properties.allowedTargets()).isEmpty();
            assertThat(properties.tlsMode()).isEqualTo(
                    JdbcTableDataSourceProperties.TlsMode.VERIFY_IDENTITY);
        });
    }

    @Test
    void bindsExactTargetsAndExplicitTestTlsMode() {
        context.withPropertyValues(
                        "examine.module.datasource.jdbc.allowed-targets="
                                + "mysql-a.internal:3306,mysql-b.internal:3307",
                        "examine.module.datasource.jdbc.tls-mode=DISABLED")
                .run(application -> {
                    assertThat(application).hasNotFailed();
                    var properties = application.getBean(
                            JdbcTableDataSourceProperties.class);
                    assertThat(properties.allowedTargets()).containsExactly(
                            "mysql-a.internal:3306",
                            "mysql-b.internal:3307");
                    assertThat(properties.tlsMode()).isEqualTo(
                            JdbcTableDataSourceProperties.TlsMode.DISABLED);
                });
    }

    @Test
    void rejectsDowngradeProneOrUnknownTlsModesAtStartup() {
        context.withPropertyValues(
                        "examine.module.datasource.jdbc.tls-mode=PREFERRED")
                .run(application -> {
                    assertThat(application).hasFailed();
                    assertThat(application.getStartupFailure())
                            .hasRootCauseMessage(
                                    "No enum constant com.unique.examine.module.datasource.external.jdbc.JdbcTableDataSourceProperties.TlsMode.PREFERRED");
                });
    }

    private static DataSourceService dataSources() {
        var repository = (DataSourceRepository) Proxy.newProxyInstance(
                DataSourceRepository.class.getClassLoader(),
                new Class<?>[]{DataSourceRepository.class},
                (proxy, method, arguments) -> {
                    if (method.getReturnType() == Optional.class) {
                        return Optional.empty();
                    }
                    if (method.getReturnType() == List.class) {
                        return List.of();
                    }
                    if (method.getReturnType() == long.class) {
                        return 1L;
                    }
                    throw new UnsupportedOperationException(
                            "not used by JDBC configuration test");
                });
        DataSourceModuleCatalog catalog =
                (systemId, tenantId, moduleId) -> Optional.empty();
        return new DataSourceService(repository, catalog, Clock.systemUTC());
    }
}
