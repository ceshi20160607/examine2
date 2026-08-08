package com.unique.examine.module.datasource.external.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.OutboundHttpTransport;
import com.unique.examine.core.api.SecretResolverFacade;
import com.unique.examine.module.datasource.port.DataSourceModuleCatalog;
import com.unique.examine.module.datasource.port.DataSourceRepository;
import com.unique.examine.module.datasource.service.DataSourceConnectionCheckUseCase;
import com.unique.examine.module.datasource.service.DataSourceDraftRowsPreviewUseCase;
import com.unique.examine.module.datasource.service.DataSourcePublicationPreflight;
import com.unique.examine.module.datasource.service.PublishedHttpDataSourceRowsReader;
import com.unique.examine.module.datasource.service.DataSourceSchemaDiscoveryUseCase;
import com.unique.examine.module.datasource.service.DataSourceService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class HttpDataSourceCheckConfigurationTest {
    private final ApplicationContextRunner context =
            new ApplicationContextRunner().withUserConfiguration(
                            HttpDataSourceCheckConfiguration.class)
                    .withBean(DataSourceService.class,
                            HttpDataSourceCheckConfigurationTest::dataSources)
                    .withBean(OutboundHttpTransport.class, () -> request ->
                            new OutboundHttpTransport.Response(
                                    200, "{\"rows\":[]}".getBytes(
                                            StandardCharsets.UTF_8),
                                    Duration.ZERO))
                    .withBean(SecretResolverFacade.class,
                            () -> request -> Optional.empty())
                    .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    void registersDenyByDefaultProperties() {
        context.run(application -> {
            assertThat(application)
                    .hasSingleBean(HttpDataSourceCheckProperties.class);
            assertThat(application)
                    .hasSingleBean(HttpJsonDataSourceProbe.class)
                    .hasSingleBean(DataSourceConnectionCheckUseCase.class)
                    .hasSingleBean(DataSourceSchemaDiscoveryUseCase.class)
                    .hasSingleBean(DataSourceDraftRowsPreviewUseCase.class)
                    .hasSingleBean(DataSourcePublicationPreflight.class)
                    .hasSingleBean(PublishedHttpDataSourceRowsReader.class);
            assertThat(application.getBean(
                    HttpDataSourceCheckProperties.class).allowedHosts())
                    .isEmpty();
        });
    }

    @Test
    void bindsCommaSeparatedDeploymentAllowlist() {
        context.withPropertyValues(
                        "examine.module.datasource.http.allowed-hosts="
                                + "api.example.com,*.trusted.example")
                .run(application -> assertThat(application.getBean(
                                HttpDataSourceCheckProperties.class)
                        .allowedHosts()).containsExactly(
                        "api.example.com", "*.trusted.example"));
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
                            "not used by configuration binding test");
                });
        DataSourceModuleCatalog catalog =
                (systemId, tenantId, moduleId) -> Optional.empty();
        return new DataSourceService(repository, catalog, Clock.systemUTC());
    }
}
