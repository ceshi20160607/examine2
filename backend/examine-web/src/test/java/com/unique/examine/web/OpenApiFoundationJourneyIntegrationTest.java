package com.unique.examine.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.openapi.security.OpenApiCanonicalRequest;
import com.unique.examine.plat.manage.service.AuthzEpochService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OpenApiFoundationJourneyIntegrationTest {
    private static final String ROOT_USERNAME = "openapi_foundation_root";
    private static final String ROOT_PASSWORD = "OpenApi-Foundation-Root-84!";
    private static final String PING_PATH = "/openapi/v1/ping";
    private static final String SECRET_V1 = "openapi-http-secret-v1-4b776e45";
    private static final String SECRET_V2 = "openapi-http-secret-v2-6d3a0c21";
    private static final Path SECRET_ROOT = secretRoot();
    private static final Path SECRET_V1_FILE = secretFile("credential-v1.ref", SECRET_V1);
    private static final Path SECRET_V2_FILE = secretFile("credential-v2.ref", SECRET_V2);
    private static final Set<String> CALL_LOG_SAFE_FIELDS = Set.of(
            "id",
            "credentialVersion",
            "routeTemplate",
            "requestMethod",
            "resultCategory",
            "httpStatus",
            "latencyMs",
            "requestId",
            "traceId",
            "observedIp",
            "createdAt"
    );
    private static final Set<String> CALL_LOG_REQUIRED_FIELDS = Set.of(
            "id",
            "routeTemplate",
            "requestMethod",
            "resultCategory",
            "httpStatus",
            "latencyMs",
            "requestId",
            "traceId",
            "observedIp",
            "createdAt"
    );
    private static volatile RestartEvidence restartEvidence;

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(
            DockerImageName.parse("mysql:8.0.44"))
            .withDatabaseName("examine2_openapi_foundation_test")
            .withUsername("examine_openapi_foundation_test")
            .withPassword("container-test-password")
            .withCommand("--log-bin-trust-function-creators=1");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.flyway.locations", () -> "filesystem:"
                + migrationRoot().toString().replace('\\', '/'));
        registry.add("examine.security.secure-cookies", () -> false);
        registry.add("examine.bootstrap.root.username", () -> ROOT_USERNAME);
        registry.add("examine.bootstrap.root.password", () -> ROOT_PASSWORD);
        registry.add("examine.bootstrap.root.display-name",
                () -> "OpenAPI Foundation Test Root");
        registry.add("examine.openapi.secret-file-roots",
                () -> SECRET_ROOT.toAbsolutePath().toString());
    }

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuthzEpochService authzEpochService;

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://127.0.0.1:" + port;
    }

    @AfterAll
    static void deleteSecrets() throws IOException {
        Files.deleteIfExists(SECRET_V1_FILE);
        Files.deleteIfExists(SECRET_V2_FILE);
        Files.deleteIfExists(SECRET_ROOT);
    }

    @Test
    @Order(1)
    void provesAdministrationAuthenticationPolicyAndSanitizedEvidenceOverRealHttp()
            throws Exception {
        var admin = login();
        var main = createSystem(admin, "openapi_main");
        var permissions = strings(main.switchResponse(), "/data/context/permissions");
        assertThat(permissions).contains(
                "openapi.application.manage",
                "system.runtime.access"
        );

        var adminRoot = "/api/v1/systems/" + main.systemId()
                + "/admin/openapi/applications";
        var createBody = json(Map.of(
                "name", "Foundation application",
                "tenantId", main.tenantId(),
                "serviceMemberId", main.memberId(),
                "secretRef", SECRET_V1_FILE.toUri().toString(),
                "scopes", List.of("openapi.ping"),
                "ipAllowlist", List.of("127.0.0.1"),
                "rateLimitPerMinute", 200
        ));
        var createKey = key();
        var created = admin.postWithCsrf(
                adminRoot, createBody, Map.of("Idempotency-Key", createKey));
        assertCreated(created);
        var applicationId = text(created.body(), "/data/id");
        var appKey = text(created.body(), "/data/appKey");
        var applicationVersion = number(created.body(), "/data/version");
        assertThat(text(created.body(), "/data/tenantId")).isEqualTo(main.tenantId());
        assertThat(text(created.body(), "/data/serviceMemberId")).isEqualTo(main.memberId());
        assertThat(number(created.body(), "/data/credentialVersion")).isEqualTo(1);
        assertThat(text(created.body(), "/data/secretRef"))
                .isEqualTo(SECRET_V1_FILE.toUri().toString());
        assertThat(created.body().toString())
                .doesNotContain(SECRET_V1)
                .doesNotContain(SECRET_V2);

        var replayedCreate = admin.postWithCsrf(
                adminRoot, createBody, Map.of("Idempotency-Key", createKey));
        assertCreated(replayedCreate);
        assertThat(text(replayedCreate.body(), "/data/id")).isEqualTo(applicationId);
        assertThat(text(replayedCreate.body(), "/data/appKey")).isEqualTo(appKey);
        assertError(admin.postWithCsrf(
                adminRoot,
                json(Map.of(
                        "name", "Changed replay payload",
                        "tenantId", main.tenantId(),
                        "serviceMemberId", main.memberId(),
                        "secretRef", SECRET_V1_FILE.toUri().toString(),
                        "scopes", List.of("openapi.ping"),
                        "ipAllowlist", List.of("127.0.0.1"),
                        "rateLimitPerMinute", 200
                )),
                Map.of("Idempotency-Key", createKey)
        ), 409, "IDEMPOTENCY_CONFLICT");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from un_openapi_application "
                        + "where system_id=? and tenant_id=? and name='Foundation application'",
                Integer.class,
                Long.parseLong(main.systemId()), Long.parseLong(main.tenantId())
        )).isEqualTo(1);

        var listed = admin.get(adminRoot + "?page=1&size=100");
        assertOk(listed);
        var listedApplication = findItem(listed, "id", applicationId);
        assertThat(listedApplication.path("appKey").asText()).isEqualTo(appKey);
        assertThat(listedApplication.path("credentialVersion").asInt()).isEqualTo(1);
        var detailed = admin.get(adminRoot + "/" + applicationId);
        assertOk(detailed);
        assertThat(text(detailed.body(), "/data/id")).isEqualTo(applicationId);
        assertThat(detailed.body().toString())
                .contains(SECRET_V1_FILE.toUri().toString())
                .doesNotContain(SECRET_V1);

        assertSecretRefOnlyPersistence(applicationId);

        assertError(new TestClient().get(PING_PATH),
                401, "OPENAPI_AUTH_REQUIRED");

        var badSignatureNonce = nonce("bad-signature");
        var badTimestamp = timestamp();
        var badIdempotencyKey = key();
        var badHeaders = signedHeaders(
                appKey, SECRET_V1, badTimestamp, badSignatureNonce, badIdempotencyKey);
        var omittedSignature = badHeaders.get("X-Signature");
        badHeaders.put("X-Signature", "0".repeat(64));
        assertError(new TestClient().get(PING_PATH, badHeaders),
                401, "OPENAPI_SIGNATURE_INVALID");
        assertThat(nonceCount(applicationId, 1, badSignatureNonce)).isZero();

        var sameNonceValid = new TestClient().get(PING_PATH, signedHeaders(
                appKey, SECRET_V1, badTimestamp, badSignatureNonce, badIdempotencyKey));
        assertOk(sameNonceValid);
        assertThat(nonceCount(applicationId, 1, badSignatureNonce)).isEqualTo(1);

        var staleTimestamp = Long.toString(Instant.now().minusSeconds(600).getEpochSecond());
        assertError(new TestClient().get(PING_PATH, signedHeaders(
                appKey, SECRET_V1, staleTimestamp, nonce("stale"), key())),
                401, "OPENAPI_TIMESTAMP_INVALID");

        var replayHeaders = signedHeaders(
                appKey, SECRET_V1, timestamp(), nonce("replay"), key());
        var validPing = new TestClient().get(PING_PATH, replayHeaders);
        assertOk(validPing);
        assertThat(text(validPing.body(), "/data/applicationId")).isEqualTo(applicationId);
        assertThat(text(validPing.body(), "/data/systemId")).isEqualTo(main.systemId());
        assertThat(text(validPing.body(), "/data/tenantId")).isEqualTo(main.tenantId());
        assertThat(text(validPing.body(), "/data/memberId")).isEqualTo(main.memberId());
        assertError(new TestClient().get(PING_PATH, replayHeaders),
                409, "OPENAPI_REPLAY_DETECTED");

        var scopeDenied = updatePolicy(
                admin, adminRoot, applicationId, applicationVersion,
                List.of("other.scope"), List.of("127.0.0.1"), 200);
        applicationVersion = number(scopeDenied.body(), "/data/version");
        assertError(signedPing(appKey, SECRET_V1, "scope-denied"),
                403, "OPENAPI_SCOPE_DENIED");

        var scopeRestored = updatePolicy(
                admin, adminRoot, applicationId, applicationVersion,
                List.of("openapi.ping"), List.of("127.0.0.0/8"), 200);
        applicationVersion = number(scopeRestored.body(), "/data/version");
        assertOk(signedPing(appKey, SECRET_V1, "cidr-allowed"));
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from un_openapi_call_log "
                        + "where application_id=? and observed_ip=INET6_ATON('127.0.0.1')",
                Integer.class, Long.parseLong(applicationId)
        )).isGreaterThan(0);

        var ipDenied = updatePolicy(
                admin, adminRoot, applicationId, applicationVersion,
                List.of("openapi.ping"), List.of("192.0.2.0/24"), 200);
        applicationVersion = number(ipDenied.body(), "/data/version");
        assertError(signedPing(appKey, SECRET_V1, "ip-denied"),
                403, "OPENAPI_IP_DENIED");

        var ipRestored = updatePolicy(
                admin, adminRoot, applicationId, applicationVersion,
                List.of("openapi.ping"), List.of("127.0.0.1"), 200);
        applicationVersion = number(ipRestored.body(), "/data/version");

        var rateApplication = createApplication(
                admin,
                adminRoot,
                "Rate limited application",
                main.tenantId(),
                main.memberId(),
                SECRET_V1_FILE,
                List.of("openapi.ping"),
                List.of("127.0.0.1"),
                1
        );
        assertCreated(rateApplication);
        var rateAppKey = text(rateApplication.body(), "/data/appKey");
        assertOk(signedPing(rateAppKey, SECRET_V1, "rate-first"));
        var rateRejected = signedPing(rateAppKey, SECRET_V1, "rate-second");
        assertError(rateRejected, 429, "OPENAPI_RATE_LIMITED");
        assertThat(rateRejected.headers().firstValue("Retry-After").orElseThrow())
                .matches("[1-9][0-9]*");

        var oldSecretAfterRotation = signedHeaders(
                appKey, SECRET_V1, timestamp(), nonce("old-after-rotate"), key());
        var rotated = admin.postWithCsrf(
                adminRoot + "/" + applicationId + ":rotate-secret-ref",
                json(Map.of(
                        "secretRef", SECRET_V2_FILE.toUri().toString(),
                        "version", applicationVersion
                )),
                Map.of("Idempotency-Key", key())
        );
        assertOk(rotated);
        applicationVersion = number(rotated.body(), "/data/version");
        assertThat(number(rotated.body(), "/data/credentialVersion")).isEqualTo(2);
        assertError(new TestClient().get(PING_PATH, oldSecretAfterRotation),
                401, "OPENAPI_SIGNATURE_INVALID");
        var rotatedPing = signedPing(appKey, SECRET_V2, "rotated-new-secret");
        assertOk(rotatedPing);
        assertThat(number(rotatedPing.body(), "/data/credentialVersion")).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from un_openapi_credential "
                        + "where application_id=? and credential_version=1 and status='REVOKED' "
                        + "and secret_ref=?",
                Integer.class,
                Long.parseLong(applicationId), SECRET_V1_FILE.toUri().toString()
        )).isEqualTo(1);

        var disabled = changeStatus(
                admin, adminRoot, applicationId, "disable", applicationVersion,
                "security drill");
        applicationVersion = number(disabled.body(), "/data/version");
        assertThat(text(disabled.body(), "/data/status")).isEqualTo("DISABLED");
        assertError(signedPing(appKey, SECRET_V2, "disabled"),
                401, "OPENAPI_APP_UNAVAILABLE");
        var enabled = changeStatus(
                admin, adminRoot, applicationId, "enable", applicationVersion,
                "security drill complete");
        applicationVersion = number(enabled.body(), "/data/version");
        assertThat(text(enabled.body(), "/data/status")).isEqualTo("ACTIVE");
        assertOk(signedPing(appKey, SECRET_V2, "enabled"));

        jdbcTemplate.update(
                "update un_plat_member set status='DISABLED',updated_at=UTC_TIMESTAMP(3),"
                        + "version=version+1 where system_id=? and id=?",
                Long.parseLong(main.systemId()), Long.parseLong(main.memberId())
        );
        assertError(signedPing(appKey, SECRET_V2, "member-disabled"),
                403, "OPENAPI_PERMISSION_DENIED");
        jdbcTemplate.update(
                "update un_plat_member set status='ACTIVE',updated_at=UTC_TIMESTAMP(3),"
                        + "version=version+1 where system_id=? and id=?",
                Long.parseLong(main.systemId()), Long.parseLong(main.memberId())
        );
        assertOk(signedPing(appKey, SECRET_V2, "member-restored"));

        var flowRoot = "/api/v1/systems/" + main.systemId() + "/flow";
        var flowDefinition = admin.postWithCsrf(
                flowRoot + "/definitions",
                json(Map.of(
                        "name", "Signed OpenAPI approval",
                        "approverId", main.memberId()
                )),
                Map.of()
        );
        assertCreated(flowDefinition);
        var flowDefinitionId = text(flowDefinition.body(), "/data/definitionId");
        assertOk(admin.postWithCsrf(
                flowRoot + "/definitions/" + flowDefinitionId + ":publish",
                "{}",
                Map.of()
        ));
        var openApiFlowPath = "/openapi/v1/flow/definitions/"
                + flowDefinitionId + "/instances";
        var latestBody = json(Map.of("businessKey", "openapi-latest-" + key()));
        assertError(new TestClient().post(
                        openApiFlowPath,
                        latestBody,
                        signedPostHeaders(
                                openApiFlowPath,
                                appKey,
                                SECRET_V2,
                                latestBody,
                                timestamp(),
                                nonce("flow-scope-denied"),
                                key()
                        )),
                403,
                "OPENAPI_SCOPE_DENIED");

        var flowPolicy = updatePolicy(
                admin,
                adminRoot,
                applicationId,
                applicationVersion,
                List.of("openapi.ping", "flow.instance.start"),
                List.of("127.0.0.1"),
                200
        );
        applicationVersion = number(flowPolicy.body(), "/data/version");

        jdbcTemplate.update(
                "update un_plat_permission set status='DISABLED',"
                        + "updated_at=UTC_TIMESTAMP(3),version=version+1 "
                        + "where system_id=? and permission_code='flow.instance.start'",
                Long.parseLong(main.systemId())
        );
        try {
            assertError(new TestClient().post(
                            openApiFlowPath,
                            latestBody,
                            signedPostHeaders(
                                    openApiFlowPath,
                                    appKey,
                                    SECRET_V2,
                                    latestBody,
                                    timestamp(),
                                    nonce("flow-permission-denied"),
                                    key()
                            )),
                    403,
                    "OPENAPI_PERMISSION_DENIED");
        } finally {
            jdbcTemplate.update(
                    "update un_plat_permission set status='ACTIVE',"
                            + "updated_at=UTC_TIMESTAMP(3),version=version+1 "
                            + "where system_id=? and permission_code='flow.instance.start'",
                    Long.parseLong(main.systemId())
            );
        }

        var flowStartKey = key();
        var flowStarted = new TestClient().post(
                openApiFlowPath,
                latestBody,
                signedPostHeaders(
                        openApiFlowPath,
                        appKey,
                        SECRET_V2,
                        latestBody,
                        timestamp(),
                        nonce("flow-latest"),
                        flowStartKey
                )
        );
        assertCreated(flowStarted);
        var flowInstanceId = text(flowStarted.body(), "/data/instanceId");
        assertThat(number(flowStarted.body(), "/data/definitionVersion")).isEqualTo(1);
        assertThat(text(flowStarted.body(), "/data/requesterId")).isEqualTo(main.memberId());

        var flowReplay = new TestClient().post(
                openApiFlowPath,
                latestBody,
                signedPostHeaders(
                        openApiFlowPath,
                        appKey,
                        SECRET_V2,
                        latestBody,
                        timestamp(),
                        nonce("flow-replay"),
                        flowStartKey
                )
        );
        assertCreated(flowReplay);
        assertThat(flowReplay.body().at("/data")).isEqualTo(flowStarted.body().at("/data"));

        var changedBody = json(Map.of("businessKey", "openapi-changed-" + key()));
        assertError(new TestClient().post(
                        openApiFlowPath,
                        changedBody,
                        signedPostHeaders(
                                openApiFlowPath,
                                appKey,
                                SECRET_V2,
                                changedBody,
                                timestamp(),
                                nonce("flow-conflict"),
                                flowStartKey
                        )),
                409,
                "IDEMPOTENCY_CONFLICT");

        var explicitBody = json(Map.of(
                "definitionVersion", 1,
                "businessKey", "openapi-explicit-" + key()
        ));
        var explicitStarted = new TestClient().post(
                openApiFlowPath,
                explicitBody,
                signedPostHeaders(
                        openApiFlowPath,
                        appKey,
                        SECRET_V2,
                        explicitBody,
                        timestamp(),
                        nonce("flow-explicit"),
                        key()
                )
        );
        assertCreated(explicitStarted);
        assertThat(number(explicitStarted.body(), "/data/definitionVersion")).isEqualTo(1);
        assertThat(text(explicitStarted.body(), "/data/instanceId")).isNotEqualTo(flowInstanceId);

        var flowScopeKey = "%s:%s:%s:%s:%s:openapi-start".formatted(
                main.systemId(),
                main.tenantId(),
                main.memberId(),
                applicationId,
                flowDefinitionId
        );
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from un_sys_idempotency "
                        + "where scope_type='FLOW_INSTANCE' and scope_key=? "
                        + "and idempotency_key=? and status='COMPLETED' "
                        + "and response_http_status=201",
                Integer.class,
                flowScopeKey,
                flowStartKey
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from un_flow_instance "
                        + "where system_id=? and tenant_id=? and definition_id=?",
                Integer.class,
                Long.parseLong(main.systemId()),
                Long.parseLong(main.tenantId()),
                Long.parseLong(flowDefinitionId)
        )).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from un_audit_operation "
                        + "where system_id=? and tenant_id=? "
                        + "and operation_type='FLOW_INSTANCE_STARTED' "
                        + "and source_type='OPENAPI' and result='SUCCESS'",
                Integer.class,
                Long.parseLong(main.systemId()),
                Long.parseLong(main.tenantId())
        )).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from un_openapi_call_log "
                        + "where application_id=? "
                        + "and route_template="
                        + "'/openapi/v1/flow/definitions/{definitionId}/instances' "
                        + "and request_method='POST'",
                Integer.class,
                Long.parseLong(applicationId)
        )).isGreaterThanOrEqualTo(6);

        var other = createSystem(admin, "openapi_other");
        var otherFlowRoot = "/api/v1/systems/" + other.systemId() + "/flow";
        var otherDefinition = admin.postWithCsrf(
                otherFlowRoot + "/definitions",
                json(Map.of(
                        "name", "Other tenant approval",
                        "approverId", other.memberId()
                )),
                Map.of()
        );
        assertCreated(otherDefinition);
        var otherDefinitionId = text(otherDefinition.body(), "/data/definitionId");
        assertOk(admin.postWithCsrf(
                otherFlowRoot + "/definitions/" + otherDefinitionId + ":publish",
                "{}",
                Map.of()
        ));
        assertOk(admin.postWithCsrf(
                "/api/v1/context/systems/" + main.systemId() + ":switch",
                "{}", Map.of()
        ));
        var isolatedFlowPath = "/openapi/v1/flow/definitions/"
                + otherDefinitionId + "/instances";
        var isolatedBody = json(Map.of("businessKey", "openapi-isolated-" + key()));
        assertError(new TestClient().post(
                        isolatedFlowPath,
                        isolatedBody,
                        signedPostHeaders(
                                isolatedFlowPath,
                                appKey,
                                SECRET_V2,
                                isolatedBody,
                                timestamp(),
                                nonce("flow-isolated"),
                                key()
                        )),
                404,
                "FLOW_VERSION_NOT_FOUND");
        assertError(createApplication(
                admin,
                adminRoot,
                "Cross-system tenant",
                other.tenantId(),
                main.memberId(),
                SECRET_V1_FILE,
                List.of("openapi.ping"),
                List.of("127.0.0.1"),
                20
        ), 422, "OPENAPI_REQUEST_INVALID");
        assertError(createApplication(
                admin,
                adminRoot,
                "Cross-system service member",
                main.tenantId(),
                other.memberId(),
                SECRET_V1_FILE,
                List.of("openapi.ping"),
                List.of("127.0.0.1"),
                20
        ), 422, "OPENAPI_SERVICE_MEMBER_INVALID");

        assertSanitizedCallLogs(applicationId, appKey, omittedSignature);

        var durableNonce = nonce("restart-safe");
        var durableTimestamp = timestamp();
        var durableIdempotencyKey = key();
        var durableHeaders = signedHeaders(
                appKey, SECRET_V2, durableTimestamp, durableNonce, durableIdempotencyKey);
        assertOk(new TestClient().get(PING_PATH, durableHeaders));
        assertThat(nonceCount(applicationId, 2, durableNonce)).isEqualTo(1);
        restartEvidence = new RestartEvidence(
                applicationId,
                appKey,
                durableNonce
        );

        var foreignTenant = admin.postWithCsrf(
                "/api/v1/systems/" + main.systemId() + "/admin/tenants",
                json(Map.of(
                        "code", "openapi_foreign_"
                                + Long.toUnsignedString(System.nanoTime(), 36),
                        "name", "OpenAPI foreign tenant"
                )),
                Map.of("Idempotency-Key", key())
        );
        assertOk(foreignTenant);
        var foreignTenantId = text(foreignTenant.body(), "/data/id");
        assertOk(admin.postWithCsrf("/api/v1/auth/refresh", "{}", Map.of()));
        var foreignTenantSwitch = admin.postWithCsrf(
                "/api/v1/context/tenants/" + foreignTenantId + ":switch",
                "{}",
                Map.of()
        );
        assertOk(foreignTenantSwitch);
        var foreignMemberId = text(
                foreignTenantSwitch.body(), "/data/context/memberId");
        var foreignApplication = createApplication(
                admin,
                adminRoot,
                "Foreign tenant application",
                foreignTenantId,
                foreignMemberId,
                SECRET_V1_FILE,
                List.of("openapi.ping"),
                List.of("127.0.0.1"),
                20
        );
        assertCreated(foreignApplication);
        var foreignApplicationId = text(foreignApplication.body(), "/data/id");
        assertOk(admin.postWithCsrf(
                "/api/v1/context/tenants/" + main.tenantId() + ":switch",
                "{}",
                Map.of()
        ));

        assertNativeApplicationCallLogs(
                admin,
                adminRoot,
                applicationId,
                foreignApplicationId,
                main,
                appKey,
                omittedSignature
        );

        assertThat(applicationVersion).isEqualTo(8);
    }

    @Test
    @Order(2)
    void rejectsTheSameNonceAfterTheSpringContextRestarts() throws Exception {
        var evidence = restartEvidence;
        assertThat(evidence)
                .as("the ordered first journey must persist restart evidence")
                .isNotNull();
        assertThat(nonceCount(evidence.applicationId(), 2, evidence.nonce())).isEqualTo(1);

        assertError(new TestClient().get(PING_PATH, signedHeaders(
                        evidence.appKey(),
                        SECRET_V2,
                        timestamp(),
                        evidence.nonce(),
                        key()
                )),
                409, "OPENAPI_REPLAY_DETECTED");
        assertThat(nonceCount(evidence.applicationId(), 2, evidence.nonce())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from un_openapi_call_log "
                        + "where application_id=? and result_category='REPLAY_REJECTED'",
                Integer.class, Long.parseLong(evidence.applicationId())
        )).isGreaterThanOrEqualTo(2);
    }

    private SystemContext createSystem(TestClient client, String prefix) throws Exception {
        assertOk(client.postWithCsrf("/api/v1/context/platform:switch", "{}", Map.of()));
        var created = client.postWithCsrf(
                "/api/v1/platform/admin/systems",
                json(Map.of(
                        "code", prefix + "_" + Long.toUnsignedString(System.nanoTime(), 36),
                        "name", prefix + " system",
                        "description", "OpenAPI foundation real HTTP system",
                        "tenantMode", "MULTI"
                )),
                Map.of("Idempotency-Key", key())
        );
        assertOk(created);
        var systemId = text(created.body(), "/data/id");
        var switched = client.postWithCsrf(
                "/api/v1/context/systems/" + systemId + ":switch", "{}", Map.of());
        assertOk(switched);
        return new SystemContext(
                systemId,
                text(switched.body(), "/data/context/tenantId"),
                text(switched.body(), "/data/context/memberId"),
                switched.body()
        );
    }

    private TestResponse createApplication(
            TestClient client,
            String adminRoot,
            String name,
            String tenantId,
            String serviceMemberId,
            Path secretFile,
            List<String> scopes,
            List<String> ipAllowlist,
            int rateLimitPerMinute
    ) throws Exception {
        return client.postWithCsrf(
                adminRoot,
                json(Map.of(
                        "name", name,
                        "tenantId", tenantId,
                        "serviceMemberId", serviceMemberId,
                        "secretRef", secretFile.toUri().toString(),
                        "scopes", scopes,
                        "ipAllowlist", ipAllowlist,
                        "rateLimitPerMinute", rateLimitPerMinute
                )),
                Map.of("Idempotency-Key", key())
        );
    }

    private TestResponse updatePolicy(
            TestClient client,
            String adminRoot,
            String applicationId,
            long version,
            List<String> scopes,
            List<String> ipAllowlist,
            int rateLimitPerMinute
    ) throws Exception {
        var response = client.putWithCsrf(
                adminRoot + "/" + applicationId + "/policy",
                json(Map.of(
                        "scopes", scopes,
                        "ipAllowlist", ipAllowlist,
                        "rateLimitPerMinute", rateLimitPerMinute,
                        "version", version
                )),
                Map.of("Idempotency-Key", key())
        );
        assertOk(response);
        return response;
    }

    private TestResponse changeStatus(
            TestClient client,
            String adminRoot,
            String applicationId,
            String command,
            long version,
            String reason
    ) throws Exception {
        var response = client.postWithCsrf(
                adminRoot + "/" + applicationId + ":" + command,
                json(Map.of("version", version, "reason", reason)),
                Map.of("Idempotency-Key", key())
        );
        assertOk(response);
        return response;
    }

    private TestResponse signedPing(String appKey, String secret, String noncePrefix)
            throws Exception {
        return new TestClient().get(PING_PATH, signedHeaders(
                appKey, secret, timestamp(), nonce(noncePrefix), key()));
    }

    private static Map<String, String> signedHeaders(
            String appKey,
            String secret,
            String timestamp,
            String nonce,
            String idempotencyKey
    ) {
        var canonical = OpenApiCanonicalRequest.canonical(
                "GET",
                PING_PATH,
                null,
                new byte[0],
                timestamp,
                nonce,
                idempotencyKey
        );
        var signature = OpenApiCanonicalRequest.signature(
                secret.getBytes(StandardCharsets.UTF_8), canonical);
        return new java.util.LinkedHashMap<>(Map.of(
                "X-App-Key", appKey,
                "X-Timestamp", timestamp,
                "X-Nonce", nonce,
                "X-Signature", signature,
                "Idempotency-Key", idempotencyKey
        ));
    }

    private static Map<String, String> signedPostHeaders(
            String path,
            String appKey,
            String secret,
            String body,
            String timestamp,
            String nonce,
            String idempotencyKey
    ) {
        var canonical = OpenApiCanonicalRequest.canonical(
                "POST",
                path,
                null,
                body.getBytes(StandardCharsets.UTF_8),
                timestamp,
                nonce,
                idempotencyKey
        );
        var signature = OpenApiCanonicalRequest.signature(
                secret.getBytes(StandardCharsets.UTF_8), canonical);
        return new java.util.LinkedHashMap<>(Map.of(
                "X-App-Key", appKey,
                "X-Timestamp", timestamp,
                "X-Nonce", nonce,
                "X-Signature", signature,
                "Idempotency-Key", idempotencyKey
        ));
    }

    private int nonceCount(String applicationId, int credentialVersion, String nonce) {
        return jdbcTemplate.queryForObject(
                "select count(*) from un_openapi_nonce "
                        + "where application_id=? and credential_version=? and nonce=?",
                Integer.class,
                Long.parseLong(applicationId), credentialVersion, nonce
        );
    }

    private void assertSecretRefOnlyPersistence(String applicationId) {
        var secretColumns = jdbcTemplate.queryForList(
                "select lower(column_name) from information_schema.columns "
                        + "where table_schema=database() and table_name like 'un_openapi_%' "
                        + "and lower(column_name) like '%secret%' order by table_name,column_name",
                String.class
        );
        assertThat(secretColumns).containsExactly(
                "secret_ref", "signing_secret_version", "secret_ref");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from information_schema.columns "
                        + "where table_schema=database() and table_name like 'un_openapi_%' "
                        + "and (lower(column_name) like '%signature%' "
                        + "or lower(column_name) in ('body','request_body','response_body',"
                        + "'secret','secret_value','plaintext_secret'))",
                Integer.class
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from un_openapi_credential "
                        + "where application_id=? and secret_ref=?",
                Integer.class,
                Long.parseLong(applicationId), SECRET_V1_FILE.toUri().toString()
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from un_openapi_credential "
                        + "where secret_ref like concat('%',?,'%') "
                        + "or secret_ref like concat('%',?,'%')",
                Integer.class, SECRET_V1, SECRET_V2
        )).isZero();
    }

    private void assertSanitizedCallLogs(
            String applicationId,
            String rawAppKey,
            String rawSignature
    ) {
        var categories = jdbcTemplate.queryForList(
                "select distinct result_category from un_openapi_call_log",
                String.class
        );
        assertThat(categories).contains(
                "SUCCESS",
                "AUTH_REJECTED",
                "SIGNATURE_REJECTED",
                "REPLAY_REJECTED",
                "SCOPE_REJECTED",
                "PERMISSION_REJECTED",
                "IP_REJECTED",
                "RATE_REJECTED"
        );
        var callLogColumns = jdbcTemplate.queryForList(
                "select lower(column_name) from information_schema.columns "
                        + "where table_schema=database() "
                        + "and table_name='un_openapi_call_log'",
                String.class
        );
        assertThat(callLogColumns).noneMatch(name ->
                name.contains("signature")
                        || name.contains("body")
                        || name.contains("secret"));
        var serializedLogs = String.join("\n", jdbcTemplate.queryForList(
                "select concat_ws('|',coalesce(cast(application_id as char),''),"
                        + "app_key_hash,coalesce(cast(credential_version as char),''),"
                        + "route_template,request_method,result_category,"
                        + "cast(http_status as char),cast(latency_ms as char),"
                        + "request_id,trace_id,hex(observed_ip)) "
                        + "from un_openapi_call_log",
                String.class
        ));
        assertThat(serializedLogs)
                .doesNotContain(SECRET_V1)
                .doesNotContain(SECRET_V2)
                .doesNotContain(SECRET_V1_FILE.toUri().toString())
                .doesNotContain(SECRET_V2_FILE.toUri().toString())
                .doesNotContain(rawSignature)
                .doesNotContain(rawAppKey);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from un_openapi_call_log "
                        + "where application_id=? and route_template=? "
                        + "and request_method='GET' and request_id<>'' and trace_id<>''",
                Integer.class,
                Long.parseLong(applicationId), PING_PATH
        )).isGreaterThan(0);
    }

    private void assertNativeApplicationCallLogs(
            TestClient admin,
            String adminRoot,
            String applicationId,
            String foreignApplicationId,
            SystemContext main,
            String rawAppKey,
            String rawSignature
    ) throws Exception {
        var beforeReads = openApiReadState();
        var path = adminRoot + "/" + applicationId + "/call-logs";
        var ownerRows = applicationCallLogRows(applicationId, "ALL", "ALL");
        assertThat(ownerRows)
                .extracting(ApplicationCallLogRow::resultCategory)
                .contains("SUCCESS", "SIGNATURE_REJECTED");

        assertCallLogPage(
                admin.get(path
                        + "?resultCategory=ALL&requestMethod=ALL&page=1&size=100"),
                ownerRows,
                applicationCallLogCount(applicationId, "ALL", "ALL"),
                rawAppKey,
                rawSignature
        );
        assertCallLogPage(
                admin.get(path
                        + "?resultCategory=SUCCESS&requestMethod=ALL&page=1&size=100"),
                applicationCallLogRows(applicationId, "SUCCESS", "ALL"),
                applicationCallLogCount(applicationId, "SUCCESS", "ALL"),
                rawAppKey,
                rawSignature
        );
        assertCallLogPage(
                admin.get(path
                        + "?resultCategory=ALL&requestMethod=POST&page=1&size=100"),
                applicationCallLogRows(applicationId, "ALL", "POST"),
                applicationCallLogCount(applicationId, "ALL", "POST"),
                rawAppKey,
                rawSignature
        );
        assertCallLogPage(
                admin.get(path
                        + "?resultCategory=SUCCESS&requestMethod=POST&page=1&size=100"),
                applicationCallLogRows(applicationId, "SUCCESS", "POST"),
                applicationCallLogCount(applicationId, "SUCCESS", "POST"),
                rawAppKey,
                rawSignature
        );

        assertError(admin.get(
                        adminRoot + "/" + foreignApplicationId
                                + "/call-logs?page=1&size=1"),
                404,
                "OPENAPI_APPLICATION_NOT_FOUND");

        var systemId = Long.parseLong(main.systemId());
        var rootAccountId = jdbcTemplate.queryForObject(
                "select id from un_plat_account where username=?",
                Long.class,
                ROOT_USERNAME
        );
        assertThat(jdbcTemplate.update(
                "update un_plat_permission set status='DISABLED',"
                        + "updated_at=UTC_TIMESTAMP(3),version=version+1 "
                        + "where system_id=? and permission_code=?",
                systemId,
                "openapi.application.manage"
        )).isEqualTo(1);
        authzEpochService.bumpSystem(systemId, rootAccountId);
        try {
            var refreshed = admin.postWithCsrf(
                    "/api/v1/auth/refresh", "{}", Map.of());
            assertOk(refreshed);
            assertThat(strings(refreshed.body(), "/data/context/permissions"))
                    .doesNotContain("openapi.application.manage");
            assertError(admin.get(path + "?page=1&size=1"),
                    403,
                    "PERMISSION_DENIED");
        } finally {
            assertThat(jdbcTemplate.update(
                    "update un_plat_permission set status='ACTIVE',"
                            + "updated_at=UTC_TIMESTAMP(3),version=version+1 "
                            + "where system_id=? and permission_code=?",
                    systemId,
                    "openapi.application.manage"
            )).isEqualTo(1);
            authzEpochService.bumpSystem(systemId, rootAccountId);
            assertOk(admin.postWithCsrf(
                    "/api/v1/auth/refresh", "{}", Map.of()));
        }

        assertThat(openApiReadState()).isEqualTo(beforeReads);
    }

    private void assertCallLogPage(
            TestResponse response,
            List<ApplicationCallLogRow> expectedRows,
            long expectedTotal,
            String rawAppKey,
            String rawSignature
    ) {
        assertOk(response);
        var data = response.body().at("/data");
        assertThat(fieldNames(data)).containsExactlyInAnyOrder(
                "items", "page", "size", "total");
        assertThat(data.path("page").asInt()).isEqualTo(1);
        assertThat(data.path("size").asInt()).isEqualTo(100);
        assertThat(data.path("total").asLong()).isEqualTo(expectedTotal);
        assertThat(expectedTotal).isEqualTo(expectedRows.size());

        var items = data.path("items");
        assertThat(items.isArray()).isTrue();
        assertThat(items).hasSize(expectedRows.size());
        for (var index = 0; index < expectedRows.size(); index++) {
            var expected = expectedRows.get(index);
            var item = items.get(index);
            var itemFields = fieldNames(item);
            assertThat(itemFields)
                    .containsAll(CALL_LOG_REQUIRED_FIELDS)
                    .isSubsetOf(CALL_LOG_SAFE_FIELDS);
            assertThat(item.path("id").asText()).isEqualTo(expected.id());
            if (expected.credentialVersion() == null) {
                assertThat(item.path("credentialVersion").isMissingNode()
                        || item.path("credentialVersion").isNull()).isTrue();
            } else {
                assertThat(item.path("credentialVersion").asInt())
                        .isEqualTo(expected.credentialVersion());
            }
            assertThat(item.path("routeTemplate").asText())
                    .isEqualTo(expected.routeTemplate());
            assertThat(item.path("requestMethod").asText())
                    .isEqualTo(expected.requestMethod());
            assertThat(item.path("resultCategory").asText())
                    .isEqualTo(expected.resultCategory());
            assertThat(item.path("httpStatus").asInt())
                    .isEqualTo(expected.httpStatus());
            assertThat(item.path("latencyMs").asLong())
                    .isEqualTo(expected.latencyMs());
            assertThat(item.path("requestId").asText())
                    .isEqualTo(expected.requestId());
            assertThat(item.path("traceId").asText())
                    .isEqualTo(expected.traceId());
            assertThat(item.path("observedIp").asText())
                    .isEqualTo(expected.observedIp());
            assertThat(Instant.parse(item.path("createdAt").asText()))
                    .isEqualTo(expected.createdAt());
        }
        assertThat(response.body().toString())
                .doesNotContain(rawAppKey)
                .doesNotContain(rawSignature)
                .doesNotContain(SECRET_V1)
                .doesNotContain(SECRET_V2)
                .doesNotContain(SECRET_V1_FILE.toUri().toString())
                .doesNotContain(SECRET_V2_FILE.toUri().toString());
    }

    private List<ApplicationCallLogRow> applicationCallLogRows(
            String applicationId,
            String resultCategory,
            String requestMethod
    ) {
        return jdbcTemplate.query(
                "select cast(id as char) as id,credential_version,route_template,"
                        + "request_method,result_category,http_status,latency_ms,"
                        + "request_id,trace_id,INET6_NTOA(observed_ip) as observed_ip,"
                        + "created_at from un_openapi_call_log where application_id=? "
                        + "and (?='ALL' or result_category=?) "
                        + "and (?='ALL' or request_method=?) "
                        + "order by created_at desc,id desc limit 100",
                (result, row) -> new ApplicationCallLogRow(
                        result.getString("id"),
                        result.getObject("credential_version", Integer.class),
                        result.getString("route_template"),
                        result.getString("request_method"),
                        result.getString("result_category"),
                        result.getInt("http_status"),
                        result.getLong("latency_ms"),
                        result.getString("request_id"),
                        result.getString("trace_id"),
                        result.getString("observed_ip"),
                        result.getTimestamp("created_at").toInstant()
                ),
                Long.parseLong(applicationId),
                resultCategory,
                resultCategory,
                requestMethod,
                requestMethod
        );
    }

    private long applicationCallLogCount(
            String applicationId,
            String resultCategory,
            String requestMethod
    ) {
        return jdbcTemplate.queryForObject(
                "select count(*) from un_openapi_call_log where application_id=? "
                        + "and (?='ALL' or result_category=?) "
                        + "and (?='ALL' or request_method=?)",
                Long.class,
                Long.parseLong(applicationId),
                resultCategory,
                resultCategory,
                requestMethod,
                requestMethod
        );
    }

    private OpenApiReadState openApiReadState() {
        return jdbcTemplate.queryForObject(
                "select "
                        + "(select count(*) from un_openapi_call_log) as call_log_count,"
                        + "(select count(*) from un_openapi_application) "
                        + "as application_count,"
                        + "(select coalesce(sum(version),0) "
                        + "from un_openapi_application) as application_version_sum,"
                        + "(select count(*) from un_openapi_credential) "
                        + "as credential_count,"
                        + "(select coalesce(sum(credential_version),0) "
                        + "from un_openapi_credential) as credential_version_sum,"
                        + "(select count(*) from un_openapi_rate_bucket) "
                        + "as rate_bucket_count,"
                        + "(select coalesce(sum(version),0) "
                        + "from un_openapi_rate_bucket) as rate_bucket_version_sum,"
                        + "(select coalesce(sum(request_count),0) "
                        + "from un_openapi_rate_bucket) as rate_request_count_sum,"
                        + "(select count(*) from un_openapi_nonce) as nonce_count,"
                        + "(select coalesce(sum(credential_version),0) "
                        + "from un_openapi_nonce) as nonce_credential_version_sum",
                (result, row) -> new OpenApiReadState(
                        result.getLong("call_log_count"),
                        result.getLong("application_count"),
                        result.getLong("application_version_sum"),
                        result.getLong("credential_count"),
                        result.getLong("credential_version_sum"),
                        result.getLong("rate_bucket_count"),
                        result.getLong("rate_bucket_version_sum"),
                        result.getLong("rate_request_count_sum"),
                        result.getLong("nonce_count"),
                        result.getLong("nonce_credential_version_sum")
                )
        );
    }

    private static Set<String> fieldNames(JsonNode node) {
        var names = new java.util.LinkedHashSet<String>();
        node.fieldNames().forEachRemaining(names::add);
        return Set.copyOf(names);
    }

    private JsonNode findItem(TestResponse response, String field, String expected) {
        var items = response.body().at("/data/items");
        assertThat(items.isArray()).isTrue();
        for (var item : items) {
            if (expected.equals(item.path(field).asText())) {
                return item;
            }
        }
        throw new AssertionError("No item with " + field + "=" + expected + " in " + items);
    }

    private TestClient login() throws Exception {
        var client = new TestClient();
        var response = client.post(
                "/api/v1/auth/login",
                json(Map.of("account", ROOT_USERNAME, "password", ROOT_PASSWORD)),
                Map.of()
        );
        assertOk(response);
        return client;
    }

    private static void assertCreated(TestResponse response) {
        assertThat(response.status())
                .withFailMessage("Expected HTTP 201 but got %s: %s",
                        response.status(), response.body())
                .isEqualTo(201);
        assertThat(text(response.body(), "/code")).isEqualTo("OK");
    }

    private static void assertOk(TestResponse response) {
        assertThat(response.status())
                .withFailMessage("Expected HTTP 200 but got %s: %s",
                        response.status(), response.body())
                .isEqualTo(200);
        assertThat(text(response.body(), "/code")).isEqualTo("OK");
    }

    private static void assertError(TestResponse response, int status, String code) {
        assertThat(response.status())
                .withFailMessage("Expected HTTP %s but got %s: %s",
                        status, response.status(), response.body())
                .isEqualTo(status);
        assertThat(text(response.body(), "/code")).isEqualTo(code);
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private static String text(JsonNode node, String pointer) {
        return node.at(pointer).asText();
    }

    private static long number(JsonNode node, String pointer) {
        return node.at(pointer).asLong();
    }

    private static Set<String> strings(JsonNode node, String pointer) {
        return StreamSupport.stream(node.at(pointer).spliterator(), false)
                .map(JsonNode::asText)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }

    private static String nonce(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private static String timestamp() {
        return Long.toString(Instant.now().getEpochSecond());
    }

    private static Path secretRoot() {
        try {
            return Files.createTempDirectory("examine-openapi-secrets-")
                    .toAbsolutePath()
                    .normalize();
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static Path secretFile(String name, String value) {
        try {
            return Files.writeString(
                    SECRET_ROOT.resolve(name),
                    value,
                    StandardCharsets.UTF_8
            );
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static Path migrationRoot() {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            var candidate = current.resolve("sql/migration");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate sql/migration from test process");
    }

    private record ApplicationCallLogRow(
            String id,
            Integer credentialVersion,
            String routeTemplate,
            String requestMethod,
            String resultCategory,
            int httpStatus,
            long latencyMs,
            String requestId,
            String traceId,
            String observedIp,
            Instant createdAt
    ) {
    }

    private record OpenApiReadState(
            long callLogCount,
            long applicationCount,
            long applicationVersionSum,
            long credentialCount,
            long credentialVersionSum,
            long rateBucketCount,
            long rateBucketVersionSum,
            long rateRequestCountSum,
            long nonceCount,
            long nonceCredentialVersionSum
    ) {
    }

    private record SystemContext(
            String systemId,
            String tenantId,
            String memberId,
            JsonNode switchResponse
    ) {
    }

    private record RestartEvidence(
            String applicationId,
            String appKey,
            String nonce
    ) {
    }

    private record TestResponse(int status, JsonNode body, HttpHeaders headers) {
    }

    private final class TestClient {
        private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        private final HttpClient client = HttpClient.newBuilder()
                .cookieHandler(cookies)
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        TestResponse get(String path) throws Exception {
            return get(path, Map.of());
        }

        TestResponse get(String path, Map<String, String> headers) throws Exception {
            var builder = HttpRequest.newBuilder(URI.create(baseUrl + path)).GET();
            headers.forEach(builder::header);
            return send(builder);
        }

        TestResponse post(String path, String body, Map<String, String> headers)
                throws Exception {
            return request("POST", path, body, headers, false);
        }

        TestResponse postWithCsrf(String path, String body, Map<String, String> headers)
                throws Exception {
            return request("POST", path, body, headers, true);
        }

        TestResponse putWithCsrf(String path, String body, Map<String, String> headers)
                throws Exception {
            return request("PUT", path, body, headers, true);
        }

        private TestResponse request(
                String method,
                String path,
                String body,
                Map<String, String> headers,
                boolean csrf
        ) throws Exception {
            var builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
            headers.forEach(builder::header);
            if (csrf) {
                builder.header("X-CSRF-Token", csrf());
            }
            return send(builder);
        }

        private TestResponse send(HttpRequest.Builder builder)
                throws IOException, InterruptedException {
            var response = client.send(
                    builder.header("X-Request-ID", "openapi-http-" + UUID.randomUUID())
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            return new TestResponse(
                    response.statusCode(),
                    objectMapper.readTree(response.body()),
                    response.headers()
            );
        }

        private String csrf() {
            return cookies.getCookieStore().getCookies().stream()
                    .filter(cookie -> "EXAMINE_CSRF".equals(cookie.getName()))
                    .map(HttpCookie::getValue)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("CSRF cookie is missing"));
        }
    }
}
