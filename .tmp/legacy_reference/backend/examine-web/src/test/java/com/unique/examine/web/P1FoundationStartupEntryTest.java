package com.unique.examine.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLTransientConnectionException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@ActiveProfiles("vnext")
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(
        useMainMethod = SpringBootTest.UseMainMethod.ALWAYS,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class P1FoundationStartupEntryTest {
    private static final String BOOTSTRAP_PASSWORD = "P1-Startup-Only-Secret-84!";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4.10"))
            .withDatabaseName("examine2_p1_startup")
            .withUsername("examine_p1_startup")
            .withPassword("container-database-password");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void foundationProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.ssl.enabled", () -> false);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration-vnext");
        registry.add("examine.foundation.vnext.bootstrap.username", () -> "admin");
        registry.add("examine.foundation.vnext.bootstrap.password", () -> BOOTSTRAP_PASSWORD);
        registry.add("examine.foundation.vnext.bootstrap.display-name", () -> "P1 Startup Administrator");
        registry.add("examine.scheduling.enabled", () -> false);
    }

    @LocalServerPort
    private int port;

    @Test
    void realMainStartsVnextAndServesManagementHealthWithoutLeakingThePassword(
            CapturedOutput output
    ) throws Exception {
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/management/health"))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        var response = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build()
                .send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
        assertThat(output.getAll()).doesNotContain(BOOTSTRAP_PASSWORD).doesNotContain("123123aa");
    }

    @Test
    void diagnosticsExposeStableCodesForMissingConfigurationAndUnavailableDatabase() {
        var missingConfiguration = new MockEnvironment()
                .withProperty("examine.foundation.vnext.enabled", "true")
                .withProperty("spring.flyway.enabled", "true")
                .withProperty("spring.flyway.locations", "classpath:db/migration-vnext");

        assertThatThrownBy(() -> FoundationStartupDiagnostics.validateConfiguration(missingConfiguration))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FOUNDATION_CONFIGURATION_MISSING")
                .hasMessageContaining("EXAMINE_VNEXT_DB_URL")
                .hasMessageContaining("EXAMINE_VNEXT_REDIS_HOST");

        var unavailableDatabase = (DataSource) Proxy.newProxyInstance(
                DataSource.class.getClassLoader(),
                new Class<?>[]{DataSource.class},
                (proxy, method, args) -> {
                    if ("getConnection".equals(method.getName())) {
                        throw new SQLTransientConnectionException("database intentionally unavailable");
                    }
                    if ("toString".equals(method.getName())) {
                        return "UnavailableFoundationDataSource";
                    }
                    if (method.getReturnType().equals(boolean.class)) {
                        return false;
                    }
                    if (method.getReturnType().equals(int.class)) {
                        return 0;
                    }
                    return null;
                }
        );
        var dependencyCheck = new FoundationDependencyStartupCheck(unavailableDatabase, null, null);

        assertThatThrownBy(() -> dependencyCheck.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FOUNDATION_DEPENDENCY_DATABASE_UNAVAILABLE")
                .hasMessageContaining("MySQL")
                .hasMessageNotContaining(BOOTSTRAP_PASSWORD);
    }
}
