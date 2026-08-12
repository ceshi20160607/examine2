package com.unique.examine.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.openapi.domain.OpenApiCallbackAttempt;
import com.unique.examine.openapi.domain.OpenApiCallbackDelivery;
import com.unique.examine.openapi.domain.OpenApiCallbackSubscription;
import com.unique.examine.openapi.domain.OpenApiCallbackVersion;
import com.unique.examine.openapi.repository.jdbc.JdbcOpenApiCallbackRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class OpenApiCallbackPersistenceJourneyIntegrationTest {
    private static final long SYSTEM_ID = 10;
    private static final long TENANT_ID = 20;
    private static final long APPLICATION_ID = 30;
    private static final long SUBSCRIPTION_ID = 100;
    private static final Instant NOW = Instant.parse("2026-08-06T14:00:00Z");

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(
            DockerImageName.parse("mysql:8.0.44"))
            .withDatabaseName("examine2_openapi_callback_test")
            .withUsername("examine_openapi_callback_test")
            .withPassword("container-test-password")
            .withCommand("--log-bin-trust-function-creators=1");

    private static JdbcTemplate jdbc;
    private static JdbcOpenApiCallbackRepository callbacks;

    @BeforeAll
    static void migrateAndSeedApplication() {
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("filesystem:" + migrationRoot().toString().replace('\\', '/'))
                .load()
                .migrate();
        var dataSource = new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        seedApplication();
        callbacks = new JdbcOpenApiCallbackRepository(jdbc, new ObjectMapper());
    }

    @Test
    void provesVersionDedupeAttemptAuditIsolationAndSecretRefOnlyOnRealMySql() {
        var subscription = new OpenApiCallbackSubscription(SUBSCRIPTION_ID, SYSTEM_ID, TENANT_ID,
                APPLICATION_ID, "ERP callback", OpenApiCallbackSubscription.Status.ACTIVE, 1,
                NOW, 7, NOW, 7, 0);
        var firstVersion = callbackVersion(101, 1, "https://callback.example.test/v1",
                Set.of("RECORD_CREATED"), "env://CALLBACK_SECRET_V1", 1,
                OpenApiCallbackVersion.Status.ACTIVE, null);

        callbacks.insert(subscription, firstVersion);

        assertThat(callbacks.find(SYSTEM_ID, TENANT_ID, APPLICATION_ID, SUBSCRIPTION_ID))
                .hasValueSatisfying(bundle -> {
                    assertThat(bundle.subscription()).isEqualTo(subscription);
                    assertThat(bundle.version()).isEqualTo(firstVersion);
                });
        assertThat(callbacks.find(SYSTEM_ID, TENANT_ID + 1, APPLICATION_ID, SUBSCRIPTION_ID))
                .isEmpty();
        assertThat(callbacks.find(SYSTEM_ID, TENANT_ID, APPLICATION_ID + 1, SUBSCRIPTION_ID))
                .isEmpty();
        assertThatThrownBy(() -> callbacks.insert(
                subscription(110, TENANT_ID + 1, APPLICATION_ID),
                callbackVersion(111, 110, 1, "https://callback.example.test/foreign-tenant",
                        Set.of("RECORD_CREATED"), "env://CALLBACK_SECRET_V1", 1,
                        OpenApiCallbackVersion.Status.ACTIVE, null)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> callbacks.insert(
                subscription(120, TENANT_ID, APPLICATION_ID + 1),
                callbackVersion(121, 120, 1, "https://callback.example.test/foreign-application",
                        Set.of("RECORD_CREATED"), "env://CALLBACK_SECRET_V1", 1,
                        OpenApiCallbackVersion.Status.ACTIVE, null)))
                .isInstanceOf(DataIntegrityViolationException.class);

        var changedAt = NOW.plusSeconds(30);
        var updatedSubscription = new OpenApiCallbackSubscription(SUBSCRIPTION_ID, SYSTEM_ID,
                TENANT_ID, APPLICATION_ID, "ERP callback v2",
                OpenApiCallbackSubscription.Status.ACTIVE, 2,
                NOW, 7, changedAt, 8, 1);
        var retiredVersion = callbackVersion(101, 1, "https://callback.example.test/v1",
                Set.of("RECORD_CREATED"), "env://CALLBACK_SECRET_V1", 1,
                OpenApiCallbackVersion.Status.RETIRED, changedAt);
        var activeVersion = callbackVersion(102, 2, "https://callback.example.test/v2",
                Set.of("RECORD_CREATED", "FLOW_STARTED"), "env://CALLBACK_SECRET_V2", 2,
                OpenApiCallbackVersion.Status.ACTIVE, null);

        assertThat(callbacks.replaceVersion(updatedSubscription, retiredVersion, activeVersion, 0))
                .isTrue();
        assertThat(callbacks.replaceVersion(updatedSubscription, retiredVersion, activeVersion, 0))
                .isFalse();
        assertThat(callbacks.find(SYSTEM_ID, TENANT_ID, APPLICATION_ID, SUBSCRIPTION_ID))
                .hasValueSatisfying(bundle -> {
                    assertThat(bundle.subscription().currentConfigVersion()).isEqualTo(2);
                    assertThat(bundle.version()).isEqualTo(activeVersion);
                });
        assertThat(callbacks.listActiveForEvent(
                SYSTEM_ID, TENANT_ID, APPLICATION_ID, "FLOW_STARTED"))
                .singleElement().extracting(bundle -> bundle.version().id()).isEqualTo(102L);
        assertThat(jdbc.queryForList(
                "select status from un_openapi_callback_version "
                        + "where subscription_id=? order by config_version",
                String.class, SUBSCRIPTION_ID))
                .containsExactly("RETIRED", "ACTIVE");

        var pending = delivery(200, OpenApiCallbackDelivery.Status.PENDING, 0,
                null, null, NOW.plusSeconds(60), null, 0);
        var duplicate = delivery(201, OpenApiCallbackDelivery.Status.PENDING, 0,
                null, null, NOW.plusSeconds(60), null, 0);
        assertThat(callbacks.insertDelivery(pending)).isTrue();
        assertThat(callbacks.insertDelivery(duplicate)).isFalse();
        assertThat(callbacks.countDeliveries(
                SYSTEM_ID, TENANT_ID, APPLICATION_ID, SUBSCRIPTION_ID)).isEqualTo(1);
        assertThat(callbacks.listDeliveries(
                SYSTEM_ID, TENANT_ID + 1, APPLICATION_ID, SUBSCRIPTION_ID, 0, 10)).isEmpty();
        assertThat(callbacks.listDeliveries(
                SYSTEM_ID, TENANT_ID, APPLICATION_ID + 1, SUBSCRIPTION_ID, 0, 10)).isEmpty();
        assertThat(callbacks.findDelivery(200)).hasValueSatisfying(bundle -> {
            assertThat(bundle.delivery()).usingRecursiveComparison()
                    .ignoringFields("payloadJson")
                    .isEqualTo(pending);
            assertThat(bundle.delivery().payloadJson())
                    .contains("evt-record-501-v3", "RECORD_CREATED");
            assertThat(bundle.subscription().applicationId()).isEqualTo(APPLICATION_ID);
            assertThat(bundle.callbackVersion()).isEqualTo(activeVersion);
        });

        var retrying = delivery(200, OpenApiCallbackDelivery.Status.RETRYING, 1,
                503, "CALLBACK_HTTP_RETRYABLE", NOW.plusSeconds(61), null, 1);
        var firstAttempt = new OpenApiCallbackAttempt(300, 200, 1,
                OpenApiCallbackAttempt.Outcome.RETRYABLE_FAILURE, 503, 25,
                "CALLBACK_HTTP_RETRYABLE", NOW.plusSeconds(60), NOW.plusSeconds(61));
        assertThat(callbacks.completeAttempt(retrying, firstAttempt, 0, 0)).isTrue();

        var succeeded = delivery(200, OpenApiCallbackDelivery.Status.SUCCEEDED, 2,
                204, null, NOW.plusSeconds(63), NOW.plusSeconds(63), 2);
        var secondAttempt = new OpenApiCallbackAttempt(301, 200, 2,
                OpenApiCallbackAttempt.Outcome.SUCCEEDED, 204, 20, null,
                NOW.plusSeconds(62), NOW.plusSeconds(63));
        assertThat(callbacks.completeAttempt(succeeded, secondAttempt, 1, 1)).isTrue();
        assertThat(callbacks.completeAttempt(succeeded, secondAttempt, 1, 1)).isFalse();
        assertThat(jdbc.queryForList(
                "select outcome from un_openapi_callback_attempt "
                        + "where delivery_id=? order by attempt_no",
                String.class, 200L))
                .containsExactly("RETRYABLE_FAILURE", "SUCCEEDED");
        assertThat(callbacks.findDelivery(200)).hasValueSatisfying(bundle ->
                assertThat(bundle.delivery().status())
                        .isEqualTo(OpenApiCallbackDelivery.Status.SUCCEEDED));

        assertSecretRefOnlyPersistence();
    }

    private static OpenApiCallbackVersion callbackVersion(
            long id, int configVersion, String endpoint, Set<String> events,
            String secretRef, int secretVersion, OpenApiCallbackVersion.Status status,
            Instant retiredAt) {
        return callbackVersion(id, SUBSCRIPTION_ID, configVersion, endpoint, events,
                secretRef, secretVersion, status, retiredAt);
    }

    private static OpenApiCallbackVersion callbackVersion(
            long id, long subscriptionId, int configVersion, String endpoint, Set<String> events,
            String secretRef, int secretVersion, OpenApiCallbackVersion.Status status,
            Instant retiredAt) {
        return new OpenApiCallbackVersion(id, subscriptionId, configVersion,
                URI.create(endpoint), events, secretRef, secretVersion, 3, 5, status,
                NOW, retiredAt, NOW, 7);
    }

    private static OpenApiCallbackSubscription subscription(
            long id, long tenantId, long applicationId) {
        return new OpenApiCallbackSubscription(id, SYSTEM_ID, tenantId, applicationId,
                "Foreign scope probe", OpenApiCallbackSubscription.Status.ACTIVE, 1,
                NOW, 7, NOW, 7, 0);
    }

    private static OpenApiCallbackDelivery delivery(
            long id, OpenApiCallbackDelivery.Status status, int attemptCount,
            Integer httpStatus, String failureCode, Instant updatedAt,
            Instant completedAt, long version) {
        return new OpenApiCallbackDelivery(id, SYSTEM_ID, TENANT_ID, APPLICATION_ID,
                SUBSCRIPTION_ID, 102, "evt-record-501-v3", "RECORD_CREATED",
                "{\"id\":\"evt-record-501-v3\",\"type\":\"RECORD_CREATED\"}",
                "a".repeat(64), status, attemptCount, httpStatus, failureCode,
                "request-501", "trace-501", NOW.plusSeconds(60), updatedAt,
                completedAt, version);
    }

    private static void assertSecretRefOnlyPersistence() {
        assertThat(jdbc.queryForList(
                "select secret_ref from un_openapi_callback_version "
                        + "where subscription_id=? order by config_version",
                String.class, SUBSCRIPTION_ID))
                .containsExactly("env://CALLBACK_SECRET_V1", "env://CALLBACK_SECRET_V2");
        assertThat(jdbc.queryForList(
                "select concat(table_name, '.', column_name) "
                        + "from information_schema.columns "
                        + "where table_schema=database() "
                        + "and table_name like 'un_openapi_callback_%' "
                        + "and column_name like '%secret%' order by table_name,column_name",
                String.class))
                .containsExactly(
                        "un_openapi_callback_version.secret_ref",
                        "un_openapi_callback_version.signing_secret_version");
        assertThat(jdbc.queryForObject(
                "select cast(payload_json as char) from un_openapi_callback_delivery where id=200",
                String.class))
                .doesNotContain("CALLBACK_SECRET", "secret_ref", "secretRef");
    }

    private static void seedApplication() {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try {
                try (var statement = connection.createStatement()) {
                    statement.execute("set foreign_key_checks=0");
                    statement.executeUpdate("""
                            insert into un_openapi_application
                              (id,system_id,tenant_id,service_member_id,app_key,name,status,
                               scopes_json,ip_allowlist_json,rate_limit_per_minute,
                               current_credential_version,created_at,created_by,updated_at,updated_by,version)
                            values
                              (30,10,20,40,'abcdefghijklmnop','Callback persistence fixture','ACTIVE',
                               json_array('record.read'),json_array(),60,1,
                               '2026-08-06 14:00:00.000',7,'2026-08-06 14:00:00.000',7,0)
                            """);
                }
            } finally {
                try (var statement = connection.createStatement()) {
                    statement.execute("set foreign_key_checks=1");
                }
            }
            return null;
        });
    }

    private static Path migrationRoot() {
        var current = Path.of("").toAbsolutePath();
        while (current != null) {
            var candidate = current.resolve("sql").resolve("migration");
            if (Files.isDirectory(candidate)) return candidate;
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate sql/migration");
    }
}
