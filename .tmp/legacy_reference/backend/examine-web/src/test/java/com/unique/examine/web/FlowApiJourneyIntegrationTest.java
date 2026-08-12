package com.unique.examine.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.unique.examine.core.api.DefaultSecretResolverFacade;
import com.unique.examine.core.api.OutboundHttpTransport;
import com.unique.examine.core.api.SecretResolverFacade;
import com.unique.examine.flow.service.FlowApprovalDeadlineWorker;
import com.unique.examine.flow.service.FlowCompensationWorker;
import com.unique.examine.flow.service.FlowPeriodicScheduleWorker;
import com.unique.examine.flow.service.FlowSubflowWorker;
import com.unique.examine.flow.service.FlowWebhookWorker;
import com.unique.examine.module.report.exporting.ReportExportJobWorker;
import com.unique.examine.module.report.scheduling.ReportScheduleDueProducer;
import com.unique.examine.module.report.scheduling.ReportScheduleOccurrenceWorker;
import com.unique.examine.openapi.security.OpenApiCanonicalRequest;
import com.unique.examine.plat.manage.service.AuthzEpochService;
import org.junit.jupiter.api.AfterAll;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(FlowApiJourneyIntegrationTest.WebhookTestConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FlowApiJourneyIntegrationTest {
    private static final String ROOT_USERNAME = "flow_api_root";
    private static final String ROOT_PASSWORD = "Flow-Api-Root-Password-84!";
    private static final String FLOW_RECORD_MODULE_CODE = "flow_order";
    private static final String OPENAPI_RECORD_SECRET =
            "flow-record-openapi-secret-2a5e0c48";
    private static final Path OPENAPI_SECRET_ROOT = openApiSecretRoot();
    private static final Path OPENAPI_SECRET_FILE = openApiSecretFile();
    private static final Path SENSITIVE_KEY_RING_FILE = sensitiveKeyRingFile();
    private static AiProviderFixture aiProviderFixture;

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0.44"))
            .withDatabaseName("examine2_flow_api_test")
            .withUsername("examine_flow_api_test")
            .withPassword("container-test-password")
            .withCommand("--log-bin-trust-function-creators=1");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
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
        registry.add("examine.bootstrap.root.display-name", () -> "Flow API Test Root");
        registry.add("examine.flow.periodic.initial-delay-ms", () -> 3_600_000);
        registry.add("examine.flow.deadline.initial-delay-ms", () -> 3_600_000);
        registry.add("examine.flow.webhook.initial-delay-ms", () -> 3_600_000);
        registry.add("examine.module.datasource.http.allowed-hosts",
                () -> "datasource.example.test");
        registry.add("examine.module.datasource.jdbc.allowed-targets",
                () -> MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306));
        registry.add("examine.module.datasource.jdbc.tls-mode",
                () -> "DISABLED");
        registry.add("examine.flow.subflow.initial-delay-ms", () -> 3_600_000);
        registry.add("examine.flow.compensation.initial-delay-ms", () -> 3_600_000);
        registry.add("examine.jobs.report-schedule.due-delay-ms", () -> 3_600_000);
        registry.add("examine.jobs.report-schedule.start-delay-ms", () -> 3_600_000);
        registry.add("examine.jobs.report-schedule.monitor-delay-ms", () -> 3_600_000);
        registry.add("examine.openapi.secret-file-roots",
                () -> OPENAPI_SECRET_ROOT.toString());
        registry.add("examine.ai.platform.secret-file-roots",
                () -> OPENAPI_SECRET_ROOT.toString());
        registry.add("examine.platform.task.command-secret-ref",
                () -> OPENAPI_SECRET_FILE.toUri().toString());
        registry.add("examine.work.ai-draft.command-secret-ref",
                () -> OPENAPI_SECRET_FILE.toUri().toString());
        registry.add("examine.flow.ai-definition-draft.command-secret-ref",
                () -> OPENAPI_SECRET_FILE.toUri().toString());
        registry.add("examine.module.ai-generated-draft.command-secret-ref",
                () -> OPENAPI_SECRET_FILE.toUri().toString());
        registry.add("examine.runtime.sensitive.key-ring-file",
                () -> SENSITIVE_KEY_RING_FILE.toString());
    }

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuthzEpochService authzEpochService;

    @Autowired
    private FlowPeriodicScheduleWorker periodicScheduleWorker;

    @Autowired
    private FlowApprovalDeadlineWorker approvalDeadlineWorker;

    @Autowired
    private FlowWebhookWorker webhookWorker;

    @Autowired
    private FlowSubflowWorker subflowWorker;

    @Autowired
    private FlowCompensationWorker compensationWorker;

    @Autowired
    private ReportExportJobWorker reportExportJobWorker;

    @Autowired
    private ReportScheduleDueProducer reportScheduleDueProducer;

    @Autowired
    private ReportScheduleOccurrenceWorker reportScheduleOccurrenceWorker;

    @Autowired
    private DeterministicWebhookTransport webhookTransport;

    private String baseUrl;

    @BeforeAll
    static void startAiProviderFixture() throws IOException {
        aiProviderFixture = AiProviderFixture.start();
    }

    @AfterAll
    static void deleteOpenApiSecret() throws IOException {
        if (aiProviderFixture != null) {
            aiProviderFixture.close();
        }
        Files.deleteIfExists(OPENAPI_SECRET_FILE);
        Files.deleteIfExists(SENSITIVE_KEY_RING_FILE);
        Files.deleteIfExists(OPENAPI_SECRET_ROOT);
    }

    @BeforeEach
    void setUp() {
        baseUrl = "http://127.0.0.1:" + port;
        webhookTransport.reset();
    }

    @Test
    void exercisesPublishedApprovalJourneyAndTenantIsolationOverHttp() throws Exception {
        var client = login();
        assertOk(client.postWithCsrf("/api/v1/context/platform:switch", "{}", Map.of()));

        var systemCode = "flow_api_" + Long.toUnsignedString(System.nanoTime(), 36);
        var createdSystem = client.postWithCsrf("/api/v1/platform/admin/systems", json(Map.of(
                "code", systemCode,
                "name", "Flow API Journey",
                "description", "HTTP journey for approval definitions and instances",
                "tenantMode", "MULTI"
        )), Map.of("Idempotency-Key", key()));
        assertOk(createdSystem);
        var systemId = text(createdSystem.body(), "/data/id");
        var flowRoot = "/api/v1/systems/" + systemId + "/flow";
        var workRoot = "/api/v1/systems/" + systemId + "/work/tasks";
        var todoRoot = "/api/v1/systems/" + systemId + "/todos";
        var analyticsRoot = "/api/v1/systems/" + systemId + "/analytics/operations";

        exercisePlatformAiAgent(client, systemId, systemCode);

        assertError(new TestClient().get(flowRoot + "/definitions"),
                401, "AUTH_REQUIRED");
        assertError(client.get(flowRoot + "/definitions"),
                403, "CONTEXT_SYSTEM_MISMATCH");

        var switched = client.postWithCsrf(
                "/api/v1/context/systems/" + systemId + ":switch", "{}", Map.of());
        assertOk(switched);
        assertError(client.get("/api/v1/platform/ai/capability"),
                403, "CONTEXT_PLATFORM_REQUIRED");
        var firstTenantId = text(switched.body(), "/data/context/tenantId");
        var memberId = text(switched.body(), "/data/context/memberId");
        var permissions = StreamSupport.stream(
                        switched.body().at("/data/context/permissions").spliterator(), false)
                .map(JsonNode::asText)
                .toList();
        assertThat(permissions).contains(
                "flow.definition.manage",
                "flow.instance.start",
                "flow.instance.decide",
                "flow.instance.read",
                "flow.instance.withdraw",
                "flow.instance.terminate",
                "flow.instance.urge",
                "flow.instance.comment",
                "flow.instance.transfer",
                "flow.instance.add-sign",
                "flow.instance.return",
                "flow.instance.claim",
                "flow.instance.cancel-claim",
                "flow.instance.reduce-sign",
                "flow.instance.copy",
                "flow.external-task.work");

        var secondUsername = "flow_second_" + Long.toUnsignedString(System.nanoTime(), 36);
        var secondPassword = "Flow-Second-Member-Password-84!";
        var registration = new TestClient().post("/api/v1/auth/register", json(Map.of(
                "username", secondUsername,
                "displayName", "Flow Second Approver",
                "password", secondPassword,
                "systemName", "Flow Second Member Home",
                "systemCode", "flow_second_home_" + Long.toUnsignedString(System.nanoTime(), 36)
        )), Map.of("Idempotency-Key", key()));
        assertOk(registration);

        var roleAdmin = login();
        assertOk(roleAdmin.postWithCsrf(
                "/api/v1/context/systems/" + systemId + ":switch", "{}", Map.of()));
        var dataScopes = roleAdmin.get(
                "/api/v1/systems/" + systemId + "/admin/data-scopes?size=200");
        assertOk(dataScopes);
        var allDataScopeId = itemId(dataScopes.body().at("/data/items"), "kind", "ALL");
        var selfDataScopeId = createSelfDataScope(systemId, memberId);
        var secondApproverRole = roleAdmin.postWithCsrf(
                "/api/v1/systems/" + systemId + "/admin/roles",
                json(Map.of(
                        "code", "flow_second_approver_" + Long.toUnsignedString(System.nanoTime(), 36),
                        "name", "Flow Second Approver",
                        "description", "Minimal permissions for a real sequential approval handoff"
                )),
                Map.of("Idempotency-Key", key()));
        assertOk(secondApproverRole);
        var secondApproverRoleId = text(secondApproverRole.body(), "/data/id");
        var roleDraft = roleAdmin.putWithCsrf(
                "/api/v1/systems/" + systemId + "/admin/roles/"
                        + secondApproverRoleId + "/draft",
                json(Map.of(
                        "name", "Flow Second Approver",
                        "description", "Read and decide assigned flow tasks",
                         "permissionCodes", List.of(
                                 "system.runtime.access",
                                 "flow.instance.decide",
                                 "flow.instance.read",
                                 "flow.instance.withdraw",
                                 "flow.instance.terminate",
                                 "flow.instance.urge",
                                 "flow.instance.comment",
                                 "flow.instance.transfer",
                                 "flow.instance.add-sign",
                                 "flow.instance.return",
                                 "flow.instance.claim",
                                 "flow.instance.cancel-claim",
                                 "flow.instance.reduce-sign",
                                 "flow.instance.copy",
                                 "event.message.access"
                         ),
                        "deniedPermissionCodes", List.of(),
                        "dataScopeId", allDataScopeId,
                        "version", text(secondApproverRole.body(), "/data/version")
                )),
                Map.of());
        assertOk(roleDraft);
        var checkedRole = roleAdmin.postWithCsrf(
                "/api/v1/systems/" + systemId + "/admin/roles/"
                        + secondApproverRoleId + "/draft:check",
                json(Map.of("version", text(roleDraft.body(), "/data/version"))),
                Map.of("Idempotency-Key", key()));
        assertOk(checkedRole);
        var publishedRole = roleAdmin.postWithCsrf(
                "/api/v1/systems/" + systemId + "/admin/roles/"
                        + secondApproverRoleId + "/draft:publish",
                json(Map.of("version", text(checkedRole.body(), "/data/version"))),
                Map.of("Idempotency-Key", key()));
        assertOk(publishedRole);

        var secondClient = login(secondUsername, secondPassword);
        var accessRequest = secondClient.postWithCsrf(
                "/api/v1/context/systems/" + systemId + "/access-requests",
                json(Map.of(
                        "targetTenantId", firstTenantId,
                        "reason", "Join the real sequential approval journey"
                )),
                Map.of("Idempotency-Key", key()));
        assertOk(accessRequest);
        var accessApprover = login();
        assertOk(accessApprover.postWithCsrf(
                "/api/v1/context/systems/" + systemId + ":switch", "{}", Map.of()));
        var approvedAccess = accessApprover.postWithCsrf(
                "/api/v1/systems/" + systemId + "/admin/access-requests/"
                        + text(accessRequest.body(), "/data/id") + ":approve",
                json(Map.of(
                        "reason", "Approve the real sequential flow handoff",
                        "version", text(accessRequest.body(), "/data/version"),
                        "tenantIds", List.of(firstTenantId),
                        "roleIds", List.of(secondApproverRoleId)
                )),
                Map.of("Idempotency-Key", key()));
        assertOk(approvedAccess);

        assertOk(client.postWithCsrf("/api/v1/auth/refresh", "{}", Map.of()));
        var secondSwitch = secondClient.postWithCsrf(
                "/api/v1/context/systems/" + systemId + ":switch", "{}", Map.of());
        assertOk(secondSwitch);
        assertThat(text(secondSwitch.body(), "/data/context/tenantId")).isEqualTo(firstTenantId);
        var secondMemberId = text(secondSwitch.body(), "/data/context/memberId");
        assertThat(secondMemberId).isNotEqualTo(memberId);
        assertThat(secondSwitch.body().at("/data/context/permissions"))
                .extracting(JsonNode::asText)
                 .contains(
                         "system.runtime.access",
                         "flow.instance.decide",
                         "flow.instance.read",
                         "flow.instance.withdraw",
                         "flow.instance.terminate",
                         "flow.instance.urge",
                         "flow.instance.comment",
                         "flow.instance.transfer",
                         "flow.instance.add-sign",
                         "flow.instance.return",
                         "flow.instance.claim",
                         "flow.instance.cancel-claim",
                         "flow.instance.reduce-sign",
                         "flow.instance.copy",
                         "event.message.access"
                )
                .doesNotContain("flow.definition.manage", "flow.instance.start");

        var startOnlyUsername = "flow_start_only_"
                + Long.toUnsignedString(System.nanoTime(), 36);
        var startOnlyPassword = "Flow-Start-Only-Password-84!";
        var startOnlyRegistration = new TestClient().post(
                "/api/v1/auth/register",
                json(Map.of(
                        "username", startOnlyUsername,
                        "displayName", "Flow Start Only Member",
                        "password", startOnlyPassword,
                        "systemName", "Flow Start Only Home",
                        "systemCode", "flow_start_only_home_"
                                + Long.toUnsignedString(System.nanoTime(), 36)
                )),
                Map.of("Idempotency-Key", key()));
        assertOk(startOnlyRegistration);

        roleAdmin = login();
        assertOk(roleAdmin.postWithCsrf(
                "/api/v1/context/systems/" + systemId + ":switch", "{}", Map.of()));
        var startOnlyRole = roleAdmin.postWithCsrf(
                "/api/v1/systems/" + systemId + "/admin/roles",
                json(Map.of(
                        "code", "flow_start_only_"
                                + Long.toUnsignedString(System.nanoTime(), 36),
                        "name", "Flow Start Only",
                        "description", "Discover and start published Flow definitions only"
                )),
                Map.of("Idempotency-Key", key()));
        assertOk(startOnlyRole);
        var startOnlyRoleId = text(startOnlyRole.body(), "/data/id");
        var startOnlyRoleDraft = roleAdmin.putWithCsrf(
                "/api/v1/systems/" + systemId + "/admin/roles/"
                        + startOnlyRoleId + "/draft",
                json(Map.of(
                        "name", "Flow Start Only",
                        "description", "No definition management or instance read permission",
                        "permissionCodes", List.of(
                                "system.runtime.access",
                                "flow.instance.start"
                        ),
                        "deniedPermissionCodes", List.of(),
                        "dataScopeId", allDataScopeId,
                        "version", text(startOnlyRole.body(), "/data/version")
                )),
                Map.of());
        assertOk(startOnlyRoleDraft);
        var checkedStartOnlyRole = roleAdmin.postWithCsrf(
                "/api/v1/systems/" + systemId + "/admin/roles/"
                        + startOnlyRoleId + "/draft:check",
                json(Map.of("version", text(startOnlyRoleDraft.body(), "/data/version"))),
                Map.of("Idempotency-Key", key()));
        assertOk(checkedStartOnlyRole);
        var publishedStartOnlyRole = roleAdmin.postWithCsrf(
                "/api/v1/systems/" + systemId + "/admin/roles/"
                        + startOnlyRoleId + "/draft:publish",
                json(Map.of("version", text(checkedStartOnlyRole.body(), "/data/version"))),
                Map.of("Idempotency-Key", key()));
        assertOk(publishedStartOnlyRole);

        var startOnlyClient = login(startOnlyUsername, startOnlyPassword);
        var startOnlyAccessRequest = startOnlyClient.postWithCsrf(
                "/api/v1/context/systems/" + systemId + "/access-requests",
                json(Map.of(
                        "targetTenantId", firstTenantId,
                        "reason", "Exercise the published manual-start path"
                )),
                Map.of("Idempotency-Key", key()));
        assertOk(startOnlyAccessRequest);
        accessApprover = login();
        assertOk(accessApprover.postWithCsrf(
                "/api/v1/context/systems/" + systemId + ":switch", "{}", Map.of()));
        assertOk(accessApprover.postWithCsrf(
                "/api/v1/systems/" + systemId + "/admin/access-requests/"
                        + text(startOnlyAccessRequest.body(), "/data/id") + ":approve",
                json(Map.of(
                        "reason", "Grant only the frozen manual-start capability",
                        "version", text(startOnlyAccessRequest.body(), "/data/version"),
                        "tenantIds", List.of(firstTenantId),
                        "roleIds", List.of(startOnlyRoleId)
                )),
                Map.of("Idempotency-Key", key())));
        assertOk(client.postWithCsrf("/api/v1/auth/refresh", "{}", Map.of()));
        assertOk(secondClient.postWithCsrf("/api/v1/auth/refresh", "{}", Map.of()));
        var startOnlySwitch = startOnlyClient.postWithCsrf(
                "/api/v1/context/systems/" + systemId + ":switch",
                "{}",
                Map.of());
        assertOk(startOnlySwitch);
        var startOnlyMemberId = text(startOnlySwitch.body(), "/data/context/memberId");
        assertThat(text(startOnlySwitch.body(), "/data/context/tenantId"))
                .isEqualTo(firstTenantId);
        assertThat(startOnlySwitch.body().at("/data/context/permissions"))
                .extracting(JsonNode::asText)
                .contains("system.runtime.access", "flow.instance.start")
                .doesNotContain(
                        "flow.definition.manage",
                        "flow.instance.read",
                        "flow.instance.decide");

        var createdDefinition = client.postWithCsrf(
                flowRoot + "/definitions",
                json(Map.of(
                        "name", "Expense approval draft",
                        "approverId", memberId
                )),
                Map.of());
        assertCreated(createdDefinition);
        var definitionId = text(createdDefinition.body(), "/data/definitionId");
        assertThat(text(createdDefinition.body(), "/data/name")).isEqualTo("Expense approval draft");
        assertThat(text(createdDefinition.body(), "/data/approverId")).isEqualTo(memberId);
        assertThat(createdDefinition.body().at("/data/revision").asInt()).isOne();

        var definitions = client.get(flowRoot + "/definitions?page=1&size=10");
        assertOk(definitions);
        assertThat(definitions.body().at("/data/items")).hasSize(1);
        assertThat(definitions.body().at("/data/total").asLong()).isOne();
        assertThat(text(definitions.body(), "/data/items/0/definitionId")).isEqualTo(definitionId);

        var revisedDefinition = client.putWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/draft",
                json(Map.of(
                        "name", "Expense approval revised",
                        "approverIds", List.of(memberId, secondMemberId)
                )),
                Map.of());
        assertOk(revisedDefinition);
        assertThat(text(revisedDefinition.body(), "/data/name")).isEqualTo("Expense approval revised");
        assertThat(revisedDefinition.body().at("/data/revision").asInt()).isEqualTo(2);
        assertThat(revisedDefinition.body().at("/data/approverIds"))
                .extracting(JsonNode::asText)
                .containsExactly(memberId, secondMemberId);

        var publishedDefinition = client.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + ":publish", "{}", Map.of());
        assertOk(publishedDefinition);
        assertThat(text(publishedDefinition.body(), "/data/definitionId")).isEqualTo(definitionId);
        assertThat(text(publishedDefinition.body(), "/data/name")).isEqualTo("Expense approval revised");
        assertThat(publishedDefinition.body().at("/data/version").asInt()).isOne();
        assertThat(publishedDefinition.body().at("/data/sourceRevision").asInt()).isEqualTo(2);
        assertThat(publishedDefinition.body().at("/data/approverIds"))
                .extracting(JsonNode::asText)
                .containsExactly(memberId, secondMemberId);

        var hiddenDraft = client.postWithCsrf(
                flowRoot + "/definitions",
                json(Map.of(
                        "name", "Start-only catalog must hide this draft",
                        "approverId", memberId
                )),
                Map.of());
        assertCreated(hiddenDraft);
        var hiddenDraftId = text(hiddenDraft.body(), "/data/definitionId");
        var startableCatalogPath = flowRoot + "/startable-definitions?page=1&size=100";
        assertError(new TestClient().get(startableCatalogPath),
                401, "AUTH_REQUIRED");
        assertError(new TestClient().post(
                        flowRoot + "/definitions/" + definitionId + "/instances",
                        json(Map.of("businessKey", "manual-start-no-cookie")),
                        Map.of("Idempotency-Key", key())),
                401,
                "AUTH_REQUIRED");
        assertError(secondClient.get(startableCatalogPath),
                403, "PERMISSION_DENIED");
        assertError(secondClient.postWithCsrf(
                        flowRoot + "/definitions/" + definitionId + "/instances",
                        json(Map.of("businessKey", "missing-start-permission")),
                        Map.of("Idempotency-Key", key())),
                403,
                "PERMISSION_DENIED");

        var platformContextClient = login();
        assertOk(platformContextClient.postWithCsrf(
                "/api/v1/context/platform:switch",
                "{}",
                Map.of()));
        assertError(platformContextClient.get(startableCatalogPath),
                403, "CONTEXT_SYSTEM_MISMATCH");
        assertError(platformContextClient.postWithCsrf(
                        flowRoot + "/definitions/" + definitionId + "/instances",
                        json(Map.of("businessKey", "manual-start-context-mismatch")),
                        Map.of("Idempotency-Key", key())),
                403,
                "CONTEXT_SYSTEM_MISMATCH");

        var startOnlyCatalog = startOnlyClient.get(startableCatalogPath);
        assertOk(startOnlyCatalog);
        assertThat(startOnlyCatalog.body().at("/data/page").asInt()).isOne();
        assertThat(startOnlyCatalog.body().at("/data/size").asInt()).isEqualTo(100);
        assertThat(startOnlyCatalog.body().at("/data/total").asLong()).isOne();
        assertThat(startOnlyCatalog.body().at("/data/items")).hasSize(1);
        assertThat(text(startOnlyCatalog.body(), "/data/items/0/definitionId"))
                .isEqualTo(definitionId);
        assertThat(text(startOnlyCatalog.body(), "/data/items/0/name"))
                .isEqualTo("Expense approval revised");
        assertThat(startOnlyCatalog.body().at("/data/items/0/latestVersion").asInt())
                .isOne();
        assertThat(text(startOnlyCatalog.body(), "/data/items/0/publishedAt"))
                .isNotBlank();
        assertThat(itemValues(startOnlyCatalog.body().at("/data/items"), "definitionId"))
                .doesNotContain(hiddenDraftId);
        assertError(startOnlyClient.get(flowRoot + "/definitions?page=1&size=100"),
                403, "PERMISSION_DENIED");
        assertError(startOnlyClient.postWithCsrf(
                        flowRoot + "/definitions",
                        json(Map.of(
                                "name", "Start-only member cannot create this draft",
                                "approverId", startOnlyMemberId
                        )),
                        Map.of()),
                403,
                "PERMISSION_DENIED");

        var rejectedStartBody = json(Map.of(
                "businessKey", "manual-start-security-rejection"));
        assertError(startOnlyClient.post(
                        flowRoot + "/definitions/" + definitionId + "/instances",
                        rejectedStartBody,
                        Map.of("Idempotency-Key", key())),
                403,
                "CSRF_INVALID");
        assertError(startOnlyClient.postWithCsrf(
                        flowRoot + "/definitions/" + definitionId + "/instances",
                        rejectedStartBody,
                        Map.of()),
                400,
                "IDEMPOTENCY_KEY_REQUIRED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_flow_instance "
                        + "WHERE system_id=? AND tenant_id=? AND business_key=?",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(firstTenantId),
                "manual-start-security-rejection")).isZero();

        var businessKey = "expense-" + Long.toUnsignedString(System.nanoTime(), 36);
        var startedInstance = client.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/instances",
                json(Map.of(
                        "definitionVersion", 1,
                        "businessKey", businessKey
                )),
                Map.of("Idempotency-Key", key()));
        assertCreated(startedInstance);
        var instanceId = text(startedInstance.body(), "/data/instanceId");
        assertThat(text(startedInstance.body(), "/data/definitionId")).isEqualTo(definitionId);
        assertThat(startedInstance.body().at("/data/definitionVersion").asInt()).isOne();
        assertThat(text(startedInstance.body(), "/data/businessKey")).isEqualTo(businessKey);
        assertThat(text(startedInstance.body(), "/data/requesterId")).isEqualTo(memberId);
        assertThat(text(startedInstance.body(), "/data/approverId")).isEqualTo(memberId);
        assertThat(startedInstance.body().at("/data/approverIds"))
                .extracting(JsonNode::asText)
                .containsExactly(memberId, secondMemberId);
        assertThat(text(startedInstance.body(), "/data/status")).isEqualTo("PENDING");

        var instances = client.get(flowRoot + "/instances?page=1&size=10");
        assertOk(instances);
        assertThat(instances.body().at("/data/items")).hasSize(1);
        assertThat(instances.body().at("/data/total").asLong()).isOne();
        assertThat(text(instances.body(), "/data/items/0/instanceId")).isEqualTo(instanceId);

        var pendingTasks = client.get(flowRoot + "/tasks?status=PENDING&page=1&size=10");
        assertOk(pendingTasks);
        assertThat(pendingTasks.body().at("/data/items")).hasSize(1);
        assertThat(pendingTasks.body().at("/data/total").asLong()).isOne();
        assertThat(text(pendingTasks.body(), "/data/items/0/instanceId")).isEqualTo(instanceId);
        assertThat(text(pendingTasks.body(), "/data/items/0/approverId")).isEqualTo(memberId);
        var noEarlySecondTasks = secondClient.get(
                flowRoot + "/tasks?status=PENDING&page=1&size=10");
        assertOk(noEarlySecondTasks);
        assertThat(noEarlySecondTasks.body().at("/data/items")).isEmpty();
        assertThat(noEarlySecondTasks.body().at("/data/total").asLong()).isZero();

        var createdTodoWorkTask = client.postWithCsrf(
                workRoot,
                json(Map.of(
                        "title", "Complete through the unified Todo action center",
                        "assigneeMemberId", Long.parseLong(memberId),
                        "dueAt", "2031-03-04T05:06:07Z"
                )),
                Map.of());
        assertCreated(createdTodoWorkTask);
        var todoWorkTaskId = text(createdTodoWorkTask.body(), "/data/id");

        var refreshedTodos = client.postWithCsrf(todoRoot + ":refresh", "{}", Map.of());
        assertOk(refreshedTodos);
        assertThat(refreshedTodos.body().at("/data/discovered").asInt()).isEqualTo(2);
        assertThat(refreshedTodos.body().at("/data/created").asInt()).isEqualTo(2);
        assertThat(refreshedTodos.body().at("/data/updated").asInt()).isZero();
        assertThat(refreshedTodos.body().at("/data/closed").asInt()).isZero();

        var unifiedTodos = client.get(todoRoot
                + "?category=ALL&state=OPEN&time=ALL&page=1&size=20");
        assertOk(unifiedTodos);
        assertThat(unifiedTodos.body().at("/data/total").asLong()).isEqualTo(2);
        var workTodo = item(unifiedTodos.body().at("/data/items"),
                "sourceType", "WORK_TASK");
        var flowTodo = item(unifiedTodos.body().at("/data/items"),
                "sourceType", "FLOW_APPROVAL");
        assertThat(workTodo.path("sourceId").asText()).isEqualTo(todoWorkTaskId);
        assertThat(workTodo.path("availableActions"))
                .extracting(JsonNode::asText).containsExactly("COMPLETE");
        assertThat(flowTodo.path("sourceId").asText()).isEqualTo(instanceId);
        assertThat(flowTodo.path("representedMemberId").asText()).isEqualTo(memberId);
        assertThat(flowTodo.path("availableActions"))
                .extracting(JsonNode::asText).containsExactly("APPROVE", "REJECT");
        var workTodoId = workTodo.path("id").asText();
        var workTodoVersion = workTodo.path("version").asLong();
        var flowTodoId = flowTodo.path("id").asText();
        var flowTodoVersion = flowTodo.path("version").asLong();

        var todoCounts = client.get(todoRoot + "/counts");
        assertOk(todoCounts);
        assertThat(todoCounts.body().at("/data/openCount").asLong()).isEqualTo(2);
        assertThat(todoCounts.body().at("/data/taskCount").asLong()).isOne();
        assertThat(todoCounts.body().at("/data/approvalCount").asLong()).isOne();

        var analyticsFrom = LocalDate.now(ZoneOffset.UTC);
        var analyticsTo = analyticsFrom.plusDays(1);
        var analyticsPath = analyticsRoot + "?from=" + analyticsFrom + "&to=" + analyticsTo;
        var activeOperations = client.get(analyticsPath);
        assertOk(activeOperations);
        assertThat(text(activeOperations.body(), "/data/range/from"))
                .isEqualTo(analyticsFrom.toString());
        assertThat(activeOperations.body().at("/data/range/days").asInt()).isOne();
        assertThat(activeOperations.body().at("/data/work/available").asBoolean()).isTrue();
        assertThat(activeOperations.body().at("/data/work/openCount").asLong()).isOne();
        assertThat(activeOperations.body().at("/data/work/completedInRangeCount").asLong())
                .isZero();
        assertThat(activeOperations.body().at("/data/work/daily/0/created").asLong()).isOne();
        assertThat(text(activeOperations.body(), "/data/work/openRoute"))
                .startsWith("/systems/" + systemId + "/tasks?");
        var openWorkDrill = client.get(workRoot + queryPart(
                text(activeOperations.body(), "/data/work/openRoute"))
                + "&page=1&size=20");
        assertOk(openWorkDrill);
        assertThat(openWorkDrill.body().at("/data/total").asLong()).isOne();
        assertThat(text(openWorkDrill.body(), "/data/items/0/id"))
                .isEqualTo(todoWorkTaskId);
        assertThat(activeOperations.body().at("/data/flow/available").asBoolean()).isTrue();
        assertThat(activeOperations.body().at("/data/flow/pendingCount").asLong()).isOne();
        assertThat(activeOperations.body().at("/data/flow/terminalInRangeCount").asLong())
                .isZero();
        assertThat(activeOperations.body().at("/data/flow/daily/0/started").asLong()).isOne();
        assertThat(text(activeOperations.body(), "/data/flow/pendingRoute"))
                .isEqualTo("/systems/" + systemId + "/flows?taskStatus=PENDING");
        var pendingFlowDrill = client.get(
                flowRoot + "/tasks?status=PENDING&page=1&size=20");
        assertOk(pendingFlowDrill);
        assertThat(pendingFlowDrill.body().at("/data/total").asLong()).isOne();
        assertThat(text(pendingFlowDrill.body(), "/data/items/0/instanceId"))
                .isEqualTo(instanceId);
        assertThat(activeOperations.body().at("/data/todo/openCount").asLong()).isEqualTo(2);

        var liveFlowTodo = client.get(todoRoot + "/" + flowTodoId);
        assertOk(liveFlowTodo);
        assertThat(text(liveFlowTodo.body(), "/data/status")).isEqualTo("LIVE");
        assertThat(text(liveFlowTodo.body(), "/data/todo/sourceId")).isEqualTo(instanceId);

        var prematureSecondDecision = secondClient.postWithCsrf(
                flowRoot + "/instances/" + instanceId + ":approve",
                json(Map.of("comment", "Too early")),
                Map.of());
        assertError(prematureSecondDecision, 403, "FLOW_APPROVER_FORBIDDEN");

        var detail = client.get(flowRoot + "/instances/" + instanceId);
        assertOk(detail);
        assertThat(text(detail.body(), "/data/instanceId")).isEqualTo(instanceId);
        assertThat(text(detail.body(), "/data/status")).isEqualTo("PENDING");

        var secondTenant = client.postWithCsrf(
                "/api/v1/systems/" + systemId + "/admin/tenants",
                json(Map.of("code", "flow-isolated", "name", "Flow Isolated Tenant")),
                Map.of("Idempotency-Key", key()));
        assertOk(secondTenant);
        var secondTenantId = text(secondTenant.body(), "/data/id");
        assertThat(secondTenantId).isNotEqualTo(firstTenantId);

        assertOk(client.postWithCsrf("/api/v1/auth/refresh", "{}", Map.of()));
        assertOk(secondClient.postWithCsrf("/api/v1/auth/refresh", "{}", Map.of()));
        var switchedTenant = client.postWithCsrf(
                "/api/v1/context/tenants/" + secondTenantId + ":switch", "{}", Map.of());
        assertOk(switchedTenant);
        assertThat(text(switchedTenant.body(), "/data/context/tenantId")).isEqualTo(secondTenantId);

        var isolatedDefinitions = client.get(flowRoot + "/definitions?page=1&size=10");
        assertOk(isolatedDefinitions);
        assertThat(isolatedDefinitions.body().at("/data/items")).isEmpty();
        assertThat(isolatedDefinitions.body().at("/data/total").asLong()).isZero();
        var isolatedStartableDefinitions = client.get(startableCatalogPath);
        assertOk(isolatedStartableDefinitions);
        assertThat(isolatedStartableDefinitions.body().at("/data/items")).isEmpty();
        assertThat(isolatedStartableDefinitions.body().at("/data/total").asLong()).isZero();
        assertError(client.postWithCsrf(
                        flowRoot + "/definitions/" + definitionId + "/instances",
                        json(Map.of("businessKey", "cross-tenant-manual-start")),
                        Map.of("Idempotency-Key", key())),
                404,
                "FLOW_VERSION_NOT_FOUND");

        var isolatedInstances = client.get(flowRoot + "/instances?page=1&size=10");
        assertOk(isolatedInstances);
        assertThat(isolatedInstances.body().at("/data/items")).isEmpty();
        assertThat(isolatedInstances.body().at("/data/total").asLong()).isZero();
        var isolatedTasks = client.get(flowRoot + "/tasks?status=ALL&page=1&size=10");
        assertOk(isolatedTasks);
        assertThat(isolatedTasks.body().at("/data/items")).isEmpty();
        assertThat(isolatedTasks.body().at("/data/total").asLong()).isZero();
        var isolatedTodos = client.get(todoRoot
                + "?category=ALL&state=ALL&time=ALL&page=1&size=20");
        assertOk(isolatedTodos);
        assertThat(isolatedTodos.body().at("/data/items")).isEmpty();
        assertThat(isolatedTodos.body().at("/data/total").asLong()).isZero();
        var isolatedTodoCounts = client.get(todoRoot + "/counts");
        assertOk(isolatedTodoCounts);
        assertThat(isolatedTodoCounts.body().at("/data/openCount").asLong()).isZero();
        var isolatedOperations = client.get(analyticsPath);
        assertOk(isolatedOperations);
        assertThat(isolatedOperations.body().at("/data/work/available").asBoolean()).isTrue();
        assertThat(isolatedOperations.body().at("/data/work/openCount").asLong()).isZero();
        assertThat(isolatedOperations.body().at("/data/work/daily/0/created").asLong()).isZero();
        assertThat(isolatedOperations.body().at("/data/flow/available").asBoolean()).isTrue();
        assertThat(isolatedOperations.body().at("/data/flow/pendingCount").asLong()).isZero();
        assertThat(isolatedOperations.body().at("/data/flow/daily/0/started").asLong()).isZero();
        assertThat(isolatedOperations.body().at("/data/todo/openCount").asLong()).isZero();
        assertError(client.get(todoRoot + "/" + flowTodoId),
                404, "TODO_NOT_FOUND");
        assertError(client.get(flowRoot + "/instances/" + instanceId),
                404, "FLOW_INSTANCE_NOT_FOUND");

        var switchedBack = client.postWithCsrf(
                "/api/v1/context/tenants/" + firstTenantId + ":switch", "{}", Map.of());
        assertOk(switchedBack);
        assertThat(text(switchedBack.body(), "/data/context/tenantId")).isEqualTo(firstTenantId);

        var completedTodoWork = client.postWithCsrf(
                todoRoot + "/" + workTodoId + ":action",
                json(Map.of(
                        "version", workTodoVersion,
                        "action", "COMPLETE"
                )),
                Map.of("Idempotency-Key", "todo-work-complete-" + key()));
        assertOk(completedTodoWork);
        assertThat(text(completedTodoWork.body(), "/data/status")).isEqualTo("SUCCESS");
        assertThat(completedTodoWork.body().at("/data/replayed").asBoolean()).isFalse();
        assertThat(text(completedTodoWork.body(), "/data/todo/status")).isEqualTo("CLOSED");
        assertThat(text(completedTodoWork.body(), "/data/sourceResult/code"))
                .isEqualTo("SUCCESS");
        var nativeCompletedWork = client.get(workRoot + "/" + todoWorkTaskId);
        assertOk(nativeCompletedWork);
        assertThat(text(nativeCompletedWork.body(), "/data/status")).isEqualTo("COMPLETED");

        var flowTodoIdempotencyKey = "todo-flow-approve-" + key();
        var firstStepApproved = client.postWithCsrf(
                todoRoot + "/" + flowTodoId + ":action",
                json(Map.of(
                        "version", flowTodoVersion,
                        "action", "APPROVE",
                        "comment", "Approved sequential step 1"
                )),
                Map.of("Idempotency-Key", flowTodoIdempotencyKey));
        assertOk(firstStepApproved);
        assertThat(text(firstStepApproved.body(), "/data/status")).isEqualTo("SUCCESS");
        assertThat(firstStepApproved.body().at("/data/replayed").asBoolean()).isFalse();
        assertThat(text(firstStepApproved.body(), "/data/todo/status")).isEqualTo("CLOSED");
        assertThat(text(firstStepApproved.body(), "/data/sourceResult/code"))
                .isEqualTo("SUCCESS");

        var replayedFirstStep = client.postWithCsrf(
                todoRoot + "/" + flowTodoId + ":action",
                json(Map.of(
                        "version", flowTodoVersion,
                        "action", "APPROVE",
                        "comment", "Approved sequential step 1"
                )),
                Map.of("Idempotency-Key", flowTodoIdempotencyKey));
        assertOk(replayedFirstStep);
        assertThat(text(replayedFirstStep.body(), "/data/status")).isEqualTo("SUCCESS");
        assertThat(replayedFirstStep.body().at("/data/replayed").asBoolean()).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_todo_action_log WHERE system_id=? AND tenant_id=?",
                Long.class, Long.parseLong(systemId), Long.parseLong(firstTenantId)))
                .isEqualTo(2);

        var firstStepNative = client.get(flowRoot + "/instances/" + instanceId);
        assertOk(firstStepNative);
        assertThat(text(firstStepNative.body(), "/data/status")).isEqualTo("PENDING");
        assertThat(text(firstStepNative.body(), "/data/approverId")).isEqualTo(secondMemberId);
        assertThat(firstStepNative.body().at("/data/completedAt").isMissingNode()).isTrue();
        var clearedRootTodoCounts = client.get(todoRoot + "/counts");
        assertOk(clearedRootTodoCounts);
        assertThat(clearedRootTodoCounts.body().at("/data/openCount").asLong()).isZero();
        var progressedOperations = client.get(analyticsPath);
        assertOk(progressedOperations);
        assertThat(progressedOperations.body().at("/data/work/openCount").asLong()).isZero();
        assertThat(progressedOperations.body().at(
                "/data/work/completedInRangeCount").asLong()).isOne();
        assertThat(progressedOperations.body().at("/data/work/daily/0/completed").asLong())
                .isOne();
        assertThat(progressedOperations.body().at("/data/flow/pendingCount").asLong()).isOne();
        assertThat(progressedOperations.body().at(
                "/data/flow/terminalInRangeCount").asLong()).isZero();

        var noStaleOwnerTasks = client.get(flowRoot + "/tasks?status=PENDING&page=1&size=10");
        assertOk(noStaleOwnerTasks);
        assertThat(noStaleOwnerTasks.body().at("/data/items")).isEmpty();
        assertThat(noStaleOwnerTasks.body().at("/data/total").asLong()).isZero();
        var nextStepTasks = secondClient.get(
                flowRoot + "/tasks?status=PENDING&page=1&size=10");
        assertOk(nextStepTasks);
        assertThat(nextStepTasks.body().at("/data/items")).hasSize(1);
        assertThat(nextStepTasks.body().at("/data/total").asLong()).isOne();
        assertThat(text(nextStepTasks.body(), "/data/items/0/instanceId")).isEqualTo(instanceId);
        assertThat(text(nextStepTasks.body(), "/data/items/0/approverId"))
                .isEqualTo(secondMemberId);

        var staleOwnerDecision = client.postWithCsrf(
                flowRoot + "/instances/" + instanceId + ":reject",
                json(Map.of("reason", "Owner no longer owns this task")),
                Map.of());
        assertError(staleOwnerDecision, 403, "FLOW_APPROVER_FORBIDDEN");

        var approved = secondClient.postWithCsrf(
                flowRoot + "/instances/" + instanceId + ":approve",
                json(Map.of("comment", "Approved sequential step 2")),
                Map.of());
        assertOk(approved);
        assertThat(text(approved.body(), "/data/status")).isEqualTo("APPROVED");
        assertThat(text(approved.body(), "/data/approverId")).isEqualTo(secondMemberId);
        assertThat(approved.body().at("/data/completedAt").isTextual()).isTrue();
        var terminalOperations = client.get(analyticsPath);
        assertOk(terminalOperations);
        assertThat(terminalOperations.body().at("/data/flow/pendingCount").asLong()).isZero();
        assertThat(terminalOperations.body().at(
                "/data/flow/terminalInRangeCount").asLong()).isOne();
        assertThat(terminalOperations.body().at(
                "/data/flow/approvedInRangeCount").asLong()).isOne();
        assertThat(terminalOperations.body().at("/data/flow/daily/0/terminal").asLong())
                .isOne();
        var approvedBreakdown = item(
                terminalOperations.body().at("/data/flow/terminalBreakdown"),
                "status", "APPROVED");
        var approvedFlowDrill = client.get(flowRoot + "/instances"
                + queryPart(approvedBreakdown.path("route").asText())
                + "&page=1&size=20");
        assertOk(approvedFlowDrill);
        assertThat(approvedFlowDrill.body().at("/data/total").asLong()).isOne();
        assertThat(text(approvedFlowDrill.body(), "/data/items/0/instanceId"))
                .isEqualTo(instanceId);

        var repeatedApprovedDecision = secondClient.postWithCsrf(
                flowRoot + "/instances/" + instanceId + ":reject",
                json(Map.of("reason", "Too late")),
                Map.of());
        assertError(repeatedApprovedDecision, 409, "FLOW_INSTANCE_STATE_INVALID");

        var noPendingTasks = client.get(flowRoot + "/tasks?status=PENDING&page=1&size=10");
        assertOk(noPendingTasks);
        assertThat(noPendingTasks.body().at("/data/items")).isEmpty();
        assertThat(noPendingTasks.body().at("/data/total").asLong()).isZero();
        var noOwnerCompletedTasks = client.get(
                flowRoot + "/tasks?status=COMPLETED&page=1&size=10");
        assertOk(noOwnerCompletedTasks);
        assertThat(noOwnerCompletedTasks.body().at("/data/items")).isEmpty();
        assertThat(noOwnerCompletedTasks.body().at("/data/total").asLong()).isZero();
        var noSecondPendingTasks = secondClient.get(
                flowRoot + "/tasks?status=PENDING&page=1&size=10");
        assertOk(noSecondPendingTasks);
        assertThat(noSecondPendingTasks.body().at("/data/items")).isEmpty();
        assertThat(noSecondPendingTasks.body().at("/data/total").asLong()).isZero();
        var completedTasks = secondClient.get(
                flowRoot + "/tasks?status=COMPLETED&page=1&size=10");
        assertOk(completedTasks);
        assertThat(completedTasks.body().at("/data/items")).hasSize(1);
        assertThat(completedTasks.body().at("/data/total").asLong()).isOne();
        assertThat(text(completedTasks.body(), "/data/items/0/instanceId")).isEqualTo(instanceId);
        var allMyTasks = secondClient.get(flowRoot + "/tasks?status=ALL&page=1&size=10");
        assertOk(allMyTasks);
        assertThat(allMyTasks.body().at("/data/items")).hasSize(1);
        assertThat(allMyTasks.body().at("/data/total").asLong()).isOne();

        var approvedDetail = client.get(flowRoot + "/instances/" + instanceId);
        assertOk(approvedDetail);
        assertThat(text(approvedDetail.body(), "/data/status")).isEqualTo("APPROVED");

        var history = client.get(flowRoot + "/instances/" + instanceId + "/history");
        assertOk(history);
        assertThat(text(history.body(), "/data/instanceId")).isEqualTo(instanceId);
        assertThat(history.body().at("/data/events")).hasSize(3);
        assertThat(history.body().at("/data/events/0/sequence").asInt()).isOne();
        assertThat(text(history.body(), "/data/events/0/type")).isEqualTo("STARTED");
        assertThat(text(history.body(), "/data/events/0/actorId")).isEqualTo(memberId);
        assertThat(text(history.body(), "/data/events/0/toStatus")).isEqualTo("PENDING");
        assertThat(history.body().at("/data/events/1/sequence").asInt()).isEqualTo(2);
        assertThat(text(history.body(), "/data/events/1/type")).isEqualTo("APPROVED");
        assertThat(text(history.body(), "/data/events/1/actorId")).isEqualTo(memberId);
        assertThat(text(history.body(), "/data/events/1/fromStatus")).isEqualTo("PENDING");
        assertThat(text(history.body(), "/data/events/1/toStatus")).isEqualTo("PENDING");
        assertThat(text(history.body(), "/data/events/1/comment"))
                .isEqualTo("Approved sequential step 1");
        assertThat(history.body().at("/data/events/2/sequence").asInt()).isEqualTo(3);
        assertThat(text(history.body(), "/data/events/2/type")).isEqualTo("APPROVED");
        assertThat(text(history.body(), "/data/events/2/actorId")).isEqualTo(secondMemberId);
        assertThat(text(history.body(), "/data/events/2/fromStatus")).isEqualTo("PENDING");
        assertThat(text(history.body(), "/data/events/2/toStatus")).isEqualTo("APPROVED");
        assertThat(text(history.body(), "/data/events/2/comment"))
                .isEqualTo("Approved sequential step 2");

        var rejectionInstance = client.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/instances",
                json(Map.of(
                        "definitionVersion", 1,
                        "businessKey", businessKey + "-rejected"
                )),
                Map.of("Idempotency-Key", key()));
        assertCreated(rejectionInstance);
        var rejectionInstanceId = text(rejectionInstance.body(), "/data/instanceId");
        var rejectionFirstStep = client.postWithCsrf(
                flowRoot + "/instances/" + rejectionInstanceId + ":approve",
                json(Map.of("comment", "Hand off rejection instance")),
                Map.of());
        assertOk(rejectionFirstStep);
        assertThat(text(rejectionFirstStep.body(), "/data/status")).isEqualTo("PENDING");
        assertThat(text(rejectionFirstStep.body(), "/data/approverId"))
                .isEqualTo(secondMemberId);
        var rejectionTask = secondClient.get(
                flowRoot + "/tasks?status=PENDING&page=1&size=10");
        assertOk(rejectionTask);
        assertThat(rejectionTask.body().at("/data/items")).hasSize(1);
        assertThat(text(rejectionTask.body(), "/data/items/0/instanceId"))
                .isEqualTo(rejectionInstanceId);

        var rejected = secondClient.postWithCsrf(
                flowRoot + "/instances/" + rejectionInstanceId + ":reject",
                json(Map.of("reason", "Rejected by the real second approver")),
                Map.of());
        assertOk(rejected);
        assertThat(text(rejected.body(), "/data/status")).isEqualTo("REJECTED");
        assertThat(text(rejected.body(), "/data/approverId")).isEqualTo(secondMemberId);
        assertThat(rejected.body().at("/data/completedAt").isTextual()).isTrue();

        var repeatedRejectedDecision = secondClient.postWithCsrf(
                flowRoot + "/instances/" + rejectionInstanceId + ":approve",
                json(Map.of("comment", "Cannot reopen a rejected instance")),
                Map.of());
        assertError(repeatedRejectedDecision, 409, "FLOW_INSTANCE_STATE_INVALID");

        var completedAfterRejection = secondClient.get(
                flowRoot + "/tasks?status=COMPLETED&page=1&size=10");
        assertOk(completedAfterRejection);
        assertThat(completedAfterRejection.body().at("/data/items")).hasSize(2);
        assertThat(completedAfterRejection.body().at("/data/total").asLong()).isEqualTo(2);
        assertThat(StreamSupport.stream(
                        completedAfterRejection.body().at("/data/items").spliterator(), false)
                .map(item -> text(item, "/instanceId"))
                .toList()).containsExactly(rejectionInstanceId, instanceId);
        var ownerCompletedAfterRejection = client.get(
                flowRoot + "/tasks?status=COMPLETED&page=1&size=10");
        assertOk(ownerCompletedAfterRejection);
        assertThat(ownerCompletedAfterRejection.body().at("/data/items")).isEmpty();
        assertThat(ownerCompletedAfterRejection.body().at("/data/total").asLong()).isZero();

        var rejectionHistory = client.get(
                flowRoot + "/instances/" + rejectionInstanceId + "/history");
        assertOk(rejectionHistory);
        assertThat(rejectionHistory.body().at("/data/events")).hasSize(3);
        assertThat(text(rejectionHistory.body(), "/data/events/0/actorId"))
                .isEqualTo(memberId);
        assertThat(text(rejectionHistory.body(), "/data/events/0/toStatus"))
                .isEqualTo("PENDING");
        assertThat(text(rejectionHistory.body(), "/data/events/1/actorId"))
                .isEqualTo(memberId);
        assertThat(text(rejectionHistory.body(), "/data/events/1/fromStatus"))
                .isEqualTo("PENDING");
        assertThat(text(rejectionHistory.body(), "/data/events/1/toStatus"))
                .isEqualTo("PENDING");
        assertThat(text(rejectionHistory.body(), "/data/events/2/actorId"))
                .isEqualTo(secondMemberId);
        assertThat(text(rejectionHistory.body(), "/data/events/2/type"))
                .isEqualTo("REJECTED");
        assertThat(text(rejectionHistory.body(), "/data/events/2/fromStatus"))
                .isEqualTo("PENDING");
        assertThat(text(rejectionHistory.body(), "/data/events/2/toStatus"))
                .isEqualTo("REJECTED");
        assertThat(text(rejectionHistory.body(), "/data/events/2/comment"))
                .isEqualTo("Rejected by the real second approver");

        var withdrawalInstance = client.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/instances",
                json(Map.of(
                        "definitionVersion", 1,
                        "businessKey", businessKey + "-withdrawn"
                )),
                Map.of("Idempotency-Key", key()));
        assertCreated(withdrawalInstance);
        var withdrawalInstanceId = text(withdrawalInstance.body(), "/data/instanceId");
        var withdrawalFirstStep = client.postWithCsrf(
                flowRoot + "/instances/" + withdrawalInstanceId + ":approve",
                json(Map.of("comment", "Hand off before requester withdrawal")),
                Map.of());
        assertOk(withdrawalFirstStep);
        assertThat(text(withdrawalFirstStep.body(), "/data/status")).isEqualTo("PENDING");
        assertThat(text(withdrawalFirstStep.body(), "/data/approverId"))
                .isEqualTo(secondMemberId);

        assertError(client.postWithCsrf(
                        flowRoot + "/instances/" + withdrawalInstanceId + ":withdraw",
                        json(Map.of("reason", "   ")),
                        Map.of("Idempotency-Key", key())),
                422,
                "FLOW_WITHDRAW_REASON_REQUIRED");
        assertError(secondClient.postWithCsrf(
                        flowRoot + "/instances/" + withdrawalInstanceId + ":withdraw",
                        json(Map.of("reason", "I am the approver, not the requester")),
                        Map.of("Idempotency-Key", key())),
                403,
                "FLOW_REQUESTER_FORBIDDEN");

        var withdrawalBody = json(Map.of(
                "reason", "Submitted with the wrong amount"));
        var withdrawalKey = key();
        var withdrawn = client.postWithCsrf(
                flowRoot + "/instances/" + withdrawalInstanceId + ":withdraw",
                withdrawalBody,
                Map.of("Idempotency-Key", withdrawalKey));
        assertOk(withdrawn);
        assertThat(text(withdrawn.body(), "/data/status")).isEqualTo("WITHDRAWN");
        assertThat(text(withdrawn.body(), "/data/requesterId")).isEqualTo(memberId);
        assertThat(text(withdrawn.body(), "/data/approverId")).isEqualTo(secondMemberId);
        assertThat(withdrawn.body().at("/data/completedAt").isTextual()).isTrue();

        var replayedWithdrawal = client.postWithCsrf(
                flowRoot + "/instances/" + withdrawalInstanceId + ":withdraw",
                withdrawalBody,
                Map.of("Idempotency-Key", withdrawalKey));
        assertOk(replayedWithdrawal);
        assertThat(replayedWithdrawal.body().at("/data")).isEqualTo(withdrawn.body().at("/data"));
        assertError(client.postWithCsrf(
                        flowRoot + "/instances/" + withdrawalInstanceId + ":withdraw",
                        json(Map.of("reason", "A different reason under the same key")),
                        Map.of("Idempotency-Key", withdrawalKey)),
                409,
                "IDEMPOTENCY_CONFLICT");
        assertError(client.postWithCsrf(
                        flowRoot + "/instances/" + withdrawalInstanceId + ":withdraw",
                        withdrawalBody,
                        Map.of("Idempotency-Key", key())),
                409,
                "FLOW_INSTANCE_STATE_INVALID");
        assertError(secondClient.postWithCsrf(
                        flowRoot + "/instances/" + withdrawalInstanceId + ":approve",
                        json(Map.of("comment", "Cannot approve a withdrawn instance")),
                        Map.of()),
                409,
                "FLOW_INSTANCE_STATE_INVALID");

        var withdrawalHistory = client.get(
                flowRoot + "/instances/" + withdrawalInstanceId + "/history");
        assertOk(withdrawalHistory);
        assertThat(withdrawalHistory.body().at("/data/events")).hasSize(3);
        assertThat(text(withdrawalHistory.body(), "/data/events/2/type"))
                .isEqualTo("WITHDRAWN");
        assertThat(text(withdrawalHistory.body(), "/data/events/2/actorId"))
                .isEqualTo(memberId);
        assertThat(text(withdrawalHistory.body(), "/data/events/2/fromStatus"))
                .isEqualTo("PENDING");
        assertThat(text(withdrawalHistory.body(), "/data/events/2/toStatus"))
                .isEqualTo("WITHDRAWN");
        assertThat(text(withdrawalHistory.body(), "/data/events/2/comment"))
                .isEqualTo("Submitted with the wrong amount");

        var noPendingAfterWithdrawal = secondClient.get(
                flowRoot + "/tasks?status=PENDING&page=1&size=10");
        assertOk(noPendingAfterWithdrawal);
        assertThat(noPendingAfterWithdrawal.body().at("/data/items")).isEmpty();
        assertThat(noPendingAfterWithdrawal.body().at("/data/total").asLong()).isZero();
        var completedAfterWithdrawal = secondClient.get(
                flowRoot + "/tasks?status=COMPLETED&page=1&size=10");
        assertOk(completedAfterWithdrawal);
        assertThat(completedAfterWithdrawal.body().at("/data/total").asLong()).isEqualTo(3L);
        assertThat(text(completedAfterWithdrawal.body(), "/data/items/0/instanceId"))
                .isEqualTo(withdrawalInstanceId);

        var terminationInstance = client.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/instances",
                json(Map.of(
                        "definitionVersion", 1,
                        "businessKey", businessKey + "-terminated"
                )),
                Map.of("Idempotency-Key", key()));
        assertCreated(terminationInstance);
        var terminationInstanceId = text(terminationInstance.body(), "/data/instanceId");
        var terminationFirstStep = client.postWithCsrf(
                flowRoot + "/instances/" + terminationInstanceId + ":approve",
                json(Map.of("comment", "Hand off before permitted termination")),
                Map.of());
        assertOk(terminationFirstStep);
        assertThat(text(terminationFirstStep.body(), "/data/status")).isEqualTo("PENDING");
        assertThat(text(terminationFirstStep.body(), "/data/approverId")).isEqualTo(secondMemberId);

        assertError(secondClient.postWithCsrf(
                        flowRoot + "/instances/" + terminationInstanceId + ":urge",
                        json(Map.of("message", "The approver cannot urge the requester-owned instance")),
                        Map.of("Idempotency-Key", key())),
                403,
                "FLOW_REQUESTER_FORBIDDEN");
        assertError(client.postWithCsrf(
                        flowRoot + "/instances/" + terminationInstanceId + ":urge",
                        json(Map.of("message", "x".repeat(501))),
                        Map.of("Idempotency-Key", key())),
                422,
                "FLOW_URGE_MESSAGE_INVALID");

        var urgeBody = json(Map.of("message", "  Please review before noon  "));
        var urgeKey = key();
        var urged = client.postWithCsrf(
                flowRoot + "/instances/" + terminationInstanceId + ":urge",
                urgeBody,
                Map.of("Idempotency-Key", urgeKey));
        assertOk(urged);
        var urgeId = text(urged.body(), "/data/urgeId");
        assertThat(text(urged.body(), "/data/instanceId")).isEqualTo(terminationInstanceId);
        assertThat(text(urged.body(), "/data/actorId")).isEqualTo(memberId);
        assertThat(text(urged.body(), "/data/recipientId")).isEqualTo(secondMemberId);
        assertThat(text(urged.body(), "/data/message")).isEqualTo("Please review before noon");
        assertThat(urged.body().at("/data/createdAt").isTextual()).isTrue();

        var replayedUrge = client.postWithCsrf(
                flowRoot + "/instances/" + terminationInstanceId + ":urge",
                urgeBody,
                Map.of("Idempotency-Key", urgeKey));
        assertOk(replayedUrge);
        assertThat(replayedUrge.body().at("/data")).isEqualTo(urged.body().at("/data"));
        assertError(client.postWithCsrf(
                        flowRoot + "/instances/" + terminationInstanceId + ":urge",
                        json(Map.of("message", "Changed urge under the same key")),
                        Map.of("Idempotency-Key", urgeKey)),
                409,
                "IDEMPOTENCY_CONFLICT");

        var urges = client.get(
                flowRoot + "/instances/" + terminationInstanceId + "/urges?page=1&size=20");
        assertOk(urges);
        assertThat(urges.body().at("/data/items")).hasSize(1);
        assertThat(urges.body().at("/data/total").asLong()).isOne();
        assertThat(text(urges.body(), "/data/items/0/urgeId")).isEqualTo(urgeId);
        assertThat(text(urges.body(), "/data/items/0/recipientId")).isEqualTo(secondMemberId);

        var historyAfterUrge = client.get(
                flowRoot + "/instances/" + terminationInstanceId + "/history");
        assertOk(historyAfterUrge);
        assertThat(historyAfterUrge.body().at("/data/events")).hasSize(2);
        assertThat(text(historyAfterUrge.body(), "/data/events/1/type")).isEqualTo("APPROVED");

        var secondInbox = secondClient.get(
                "/api/v1/systems/" + systemId + "/event/messages?status=UNREAD&page=1&size=100");
        assertOk(secondInbox);
        var urgeMessages = StreamSupport.stream(
                        secondInbox.body().at("/data/items").spliterator(), false)
                .filter(item -> "FLOW_INSTANCE_URGED".equals(item.path("templateCode").asText()))
                .toList();
        assertThat(urgeMessages).hasSize(1);
        assertThat(urgeMessages.getFirst().path("senderMemberId").asText()).isEqualTo(memberId);
        assertThat(urgeMessages.getFirst().path("recipientMemberId").asText()).isEqualTo(secondMemberId);
        assertThat(urgeMessages.getFirst().path("status").asText()).isEqualTo("UNREAD");
        assertThat(urgeMessages.getFirst().path("target").path("type").asText())
                .isEqualTo("FLOW_INSTANCE");
        assertThat(urgeMessages.getFirst().path("target").path("id").asText())
                .isEqualTo(terminationInstanceId);
        assertThat(urgeMessages.getFirst().path("body").asText())
                .contains(businessKey + "-terminated", "Please review before noon");

        assertError(secondClient.postWithCsrf(
                        flowRoot + "/instances/" + terminationInstanceId + ":terminate",
                        json(Map.of("reason", "   ")),
                        Map.of("Idempotency-Key", key())),
                422,
                "FLOW_TERMINATE_REASON_REQUIRED");

        var terminationBody = json(Map.of("reason", "Duplicate approval request"));
        var terminationKey = key();
        var terminated = secondClient.postWithCsrf(
                flowRoot + "/instances/" + terminationInstanceId + ":terminate",
                terminationBody,
                Map.of("Idempotency-Key", terminationKey));
        assertOk(terminated);
        assertThat(text(terminated.body(), "/data/status")).isEqualTo("TERMINATED");
        assertThat(text(terminated.body(), "/data/requesterId")).isEqualTo(memberId);
        assertThat(text(terminated.body(), "/data/approverId")).isEqualTo(secondMemberId);
        assertThat(terminated.body().at("/data/completedAt").isTextual()).isTrue();

        var replayedTermination = secondClient.postWithCsrf(
                flowRoot + "/instances/" + terminationInstanceId + ":terminate",
                terminationBody,
                Map.of("Idempotency-Key", terminationKey));
        assertOk(replayedTermination);
        assertThat(replayedTermination.body().at("/data")).isEqualTo(terminated.body().at("/data"));
        assertError(secondClient.postWithCsrf(
                        flowRoot + "/instances/" + terminationInstanceId + ":terminate",
                        json(Map.of("reason", "Changed reason under the same key")),
                        Map.of("Idempotency-Key", terminationKey)),
                409,
                "IDEMPOTENCY_CONFLICT");
        assertError(secondClient.postWithCsrf(
                        flowRoot + "/instances/" + terminationInstanceId + ":terminate",
                        terminationBody,
                        Map.of("Idempotency-Key", key())),
                409,
                "FLOW_INSTANCE_STATE_INVALID");
        assertError(secondClient.postWithCsrf(
                        flowRoot + "/instances/" + terminationInstanceId + ":approve",
                        json(Map.of("comment", "Cannot approve a terminated instance")),
                        Map.of()),
                409,
                "FLOW_INSTANCE_STATE_INVALID");
        assertError(client.postWithCsrf(
                        flowRoot + "/instances/" + terminationInstanceId + ":withdraw",
                        json(Map.of("reason", "Cannot withdraw a terminated instance")),
                        Map.of("Idempotency-Key", key())),
                409,
                "FLOW_INSTANCE_STATE_INVALID");
        assertError(client.postWithCsrf(
                        flowRoot + "/instances/" + terminationInstanceId + ":urge",
                        json(Map.of("message", "Cannot urge a terminated instance")),
                        Map.of("Idempotency-Key", key())),
                409,
                "FLOW_INSTANCE_STATE_INVALID");

        assertError(client.postWithCsrf(
                        flowRoot + "/instances/" + terminationInstanceId + "/comments",
                        json(Map.of("body", "   ")),
                        Map.of("Idempotency-Key", key())),
                422,
                "FLOW_COMMENT_BODY_REQUIRED");
        var firstCommentBody = json(Map.of("body", "  Supporting documents checked.  "));
        var firstCommentKey = key();
        var firstComment = client.postWithCsrf(
                flowRoot + "/instances/" + terminationInstanceId + "/comments",
                firstCommentBody,
                Map.of("Idempotency-Key", firstCommentKey));
        assertOk(firstComment);
        var firstCommentId = text(firstComment.body(), "/data/commentId");
        assertThat(text(firstComment.body(), "/data/instanceId")).isEqualTo(terminationInstanceId);
        assertThat(text(firstComment.body(), "/data/authorId")).isEqualTo(memberId);
        assertThat(text(firstComment.body(), "/data/body")).isEqualTo("Supporting documents checked.");

        var replayedComment = client.postWithCsrf(
                flowRoot + "/instances/" + terminationInstanceId + "/comments",
                firstCommentBody,
                Map.of("Idempotency-Key", firstCommentKey));
        assertOk(replayedComment);
        assertThat(replayedComment.body().at("/data")).isEqualTo(firstComment.body().at("/data"));
        assertError(client.postWithCsrf(
                        flowRoot + "/instances/" + terminationInstanceId + "/comments",
                        json(Map.of("body", "Changed comment under the same key")),
                        Map.of("Idempotency-Key", firstCommentKey)),
                409,
                "IDEMPOTENCY_CONFLICT");

        var secondComment = secondClient.postWithCsrf(
                flowRoot + "/instances/" + terminationInstanceId + "/comments",
                json(Map.of("body", "Termination reason acknowledged.")),
                Map.of("Idempotency-Key", key()));
        assertOk(secondComment);
        var secondCommentId = text(secondComment.body(), "/data/commentId");
        assertThat(text(secondComment.body(), "/data/authorId")).isEqualTo(secondMemberId);

        var comments = client.get(
                flowRoot + "/instances/" + terminationInstanceId + "/comments?page=1&size=20");
        assertOk(comments);
        assertThat(comments.body().at("/data/items")).hasSize(2);
        assertThat(comments.body().at("/data/total").asLong()).isEqualTo(2L);
        assertThat(text(comments.body(), "/data/items/0/commentId")).isEqualTo(firstCommentId);
        assertThat(text(comments.body(), "/data/items/0/authorId")).isEqualTo(memberId);
        assertThat(text(comments.body(), "/data/items/1/commentId")).isEqualTo(secondCommentId);
        assertThat(text(comments.body(), "/data/items/1/authorId")).isEqualTo(secondMemberId);
        assertError(client.get(
                        flowRoot + "/instances/" + terminationInstanceId + "/comments?page=0&size=20"),
                400,
                "FLOW_REQUEST_INVALID");

        var terminationHistory = client.get(
                flowRoot + "/instances/" + terminationInstanceId + "/history");
        assertOk(terminationHistory);
        assertThat(terminationHistory.body().at("/data/events")).hasSize(3);
        assertThat(text(terminationHistory.body(), "/data/events/2/type"))
                .isEqualTo("TERMINATED");
        assertThat(text(terminationHistory.body(), "/data/events/2/actorId"))
                .isEqualTo(secondMemberId);
        assertThat(text(terminationHistory.body(), "/data/events/2/fromStatus"))
                .isEqualTo("PENDING");
        assertThat(text(terminationHistory.body(), "/data/events/2/toStatus"))
                .isEqualTo("TERMINATED");
        assertThat(text(terminationHistory.body(), "/data/events/2/comment"))
                .isEqualTo("Duplicate approval request");

        var noPendingAfterTermination = secondClient.get(
                flowRoot + "/tasks?status=PENDING&page=1&size=10");
        assertOk(noPendingAfterTermination);
        assertThat(noPendingAfterTermination.body().at("/data/total").asLong()).isZero();
        var completedAfterTermination = secondClient.get(
                flowRoot + "/tasks?status=COMPLETED&page=1&size=10");
        assertOk(completedAfterTermination);
        assertThat(completedAfterTermination.body().at("/data/total").asLong()).isEqualTo(4L);
        assertThat(text(completedAfterTermination.body(), "/data/items/0/instanceId"))
                .isEqualTo(terminationInstanceId);

        var assignmentDefinition = client.postWithCsrf(
                flowRoot + "/definitions",
                json(Map.of(
                        "name", "Single-step assignment mutations",
                        "approverId", memberId
                )),
                Map.of());
        assertCreated(assignmentDefinition);
        var assignmentDefinitionId = text(assignmentDefinition.body(), "/data/definitionId");
        var publishedAssignmentDefinition = client.postWithCsrf(
                flowRoot + "/definitions/" + assignmentDefinitionId + ":publish",
                "{}",
                Map.of());
        assertOk(publishedAssignmentDefinition);
        assertThat(publishedAssignmentDefinition.body().at("/data/approverIds"))
                .extracting(JsonNode::asText)
                .containsExactly(memberId);

        var transferInstance = client.postWithCsrf(
                flowRoot + "/definitions/" + assignmentDefinitionId + "/instances",
                json(Map.of(
                        "definitionVersion", 1,
                        "businessKey", businessKey + "-transferred"
                )),
                Map.of("Idempotency-Key", key()));
        assertCreated(transferInstance);
        var transferInstanceId = text(transferInstance.body(), "/data/instanceId");
        assertThat(transferInstance.body().at("/data/approverIds"))
                .extracting(JsonNode::asText)
                .containsExactly(memberId);

        var ownerTaskBeforeTransfer = client.get(
                flowRoot + "/tasks?status=PENDING&page=1&size=100");
        assertOk(ownerTaskBeforeTransfer);
        assertThat(itemValues(ownerTaskBeforeTransfer.body().at("/data/items"), "instanceId"))
                .contains(transferInstanceId);
        var targetTaskBeforeTransfer = secondClient.get(
                flowRoot + "/tasks?status=PENDING&page=1&size=100");
        assertOk(targetTaskBeforeTransfer);
        assertThat(itemValues(targetTaskBeforeTransfer.body().at("/data/items"), "instanceId"))
                .doesNotContain(transferInstanceId);

        var transferBody = json(Map.of(
                "targetMemberId", secondMemberId,
                "reason", "  Route to the owning reviewer  "
        ));
        var transferKey = key();
        var transferred = client.postWithCsrf(
                flowRoot + "/instances/" + transferInstanceId + ":transfer",
                transferBody,
                Map.of("Idempotency-Key", transferKey));
        assertOk(transferred);
        assertThat(text(transferred.body(), "/data/status")).isEqualTo("PENDING");
        assertThat(text(transferred.body(), "/data/approverId")).isEqualTo(secondMemberId);
        assertThat(transferred.body().at("/data/approverIds"))
                .extracting(JsonNode::asText)
                .containsExactly(secondMemberId);

        var replayedTransfer = client.postWithCsrf(
                flowRoot + "/instances/" + transferInstanceId + ":transfer",
                transferBody,
                Map.of("Idempotency-Key", transferKey));
        assertOk(replayedTransfer);
        assertThat(replayedTransfer.body().at("/data")).isEqualTo(transferred.body().at("/data"));
        assertError(client.postWithCsrf(
                        flowRoot + "/instances/" + transferInstanceId + ":transfer",
                        json(Map.of(
                                "targetMemberId", secondMemberId,
                                "reason", "Changed transfer under the same key"
                        )),
                        Map.of("Idempotency-Key", transferKey)),
                409,
                "IDEMPOTENCY_CONFLICT");

        var reloadedTransfer = client.get(flowRoot + "/instances/" + transferInstanceId);
        assertOk(reloadedTransfer);
        assertThat(text(reloadedTransfer.body(), "/data/approverId")).isEqualTo(secondMemberId);
        assertThat(reloadedTransfer.body().at("/data/approverIds"))
                .extracting(JsonNode::asText)
                .containsExactly(secondMemberId);
        var oldTaskAfterTransfer = client.get(
                flowRoot + "/tasks?status=PENDING&page=1&size=100");
        assertOk(oldTaskAfterTransfer);
        assertThat(itemValues(oldTaskAfterTransfer.body().at("/data/items"), "instanceId"))
                .doesNotContain(transferInstanceId);
        var newTaskAfterTransfer = secondClient.get(
                flowRoot + "/tasks?status=PENDING&page=1&size=100");
        assertOk(newTaskAfterTransfer);
        assertThat(itemValues(newTaskAfterTransfer.body().at("/data/items"), "instanceId"))
                .contains(transferInstanceId);

        var approvedTransfer = secondClient.postWithCsrf(
                flowRoot + "/instances/" + transferInstanceId + ":approve",
                json(Map.of("comment", "Approved after transfer")),
                Map.of());
        assertOk(approvedTransfer);
        assertThat(text(approvedTransfer.body(), "/data/status")).isEqualTo("APPROVED");
        assertThat(approvedTransfer.body().at("/data/approverIds"))
                .extracting(JsonNode::asText)
                .containsExactly(secondMemberId);
        var transferHistory = client.get(
                flowRoot + "/instances/" + transferInstanceId + "/history");
        assertOk(transferHistory);
        assertThat(transferHistory.body().at("/data/events")).hasSize(3);
        assertThat(text(transferHistory.body(), "/data/events/1/type")).isEqualTo("TRANSFERRED");
        assertThat(text(transferHistory.body(), "/data/events/1/actorId")).isEqualTo(memberId);
        assertThat(text(transferHistory.body(), "/data/events/1/targetMemberId"))
                .isEqualTo(secondMemberId);
        var transferPosition = transferHistory.body().at("/data/events/1/position");
        assertThat(transferPosition.isMissingNode() || transferPosition.isNull()).isTrue();
        assertThat(text(transferHistory.body(), "/data/events/1/comment"))
                .isEqualTo("Route to the owning reviewer");
        assertThat(text(transferHistory.body(), "/data/events/2/type")).isEqualTo("APPROVED");
        assertThat(text(transferHistory.body(), "/data/events/2/actorId"))
                .isEqualTo(secondMemberId);

        var addSignInstance = client.postWithCsrf(
                flowRoot + "/definitions/" + assignmentDefinitionId + "/instances",
                json(Map.of(
                        "definitionVersion", 1,
                        "businessKey", businessKey + "-add-signed-before"
                )),
                Map.of("Idempotency-Key", key()));
        assertCreated(addSignInstance);
        var addSignInstanceId = text(addSignInstance.body(), "/data/instanceId");
        var addSignBody = json(Map.of(
                "targetMemberId", secondMemberId,
                "position", "BEFORE",
                "reason", "  Obtain security review first  "
        ));
        var addSignKey = key();
        var addSigned = client.postWithCsrf(
                flowRoot + "/instances/" + addSignInstanceId + ":add-sign",
                addSignBody,
                Map.of("Idempotency-Key", addSignKey));
        assertOk(addSigned);
        assertThat(text(addSigned.body(), "/data/status")).isEqualTo("PENDING");
        assertThat(text(addSigned.body(), "/data/approverId")).isEqualTo(secondMemberId);
        assertThat(addSigned.body().at("/data/approverIds"))
                .extracting(JsonNode::asText)
                .containsExactly(secondMemberId, memberId);

        var replayedAddSign = client.postWithCsrf(
                flowRoot + "/instances/" + addSignInstanceId + ":add-sign",
                addSignBody,
                Map.of("Idempotency-Key", addSignKey));
        assertOk(replayedAddSign);
        assertThat(replayedAddSign.body().at("/data")).isEqualTo(addSigned.body().at("/data"));
        assertError(client.postWithCsrf(
                        flowRoot + "/instances/" + addSignInstanceId + ":add-sign",
                        json(Map.of(
                                "targetMemberId", secondMemberId,
                                "position", "AFTER",
                                "reason", "Changed add-sign under the same key"
                        )),
                        Map.of("Idempotency-Key", addSignKey)),
                409,
                "IDEMPOTENCY_CONFLICT");

        var reloadedAddSign = client.get(flowRoot + "/instances/" + addSignInstanceId);
        assertOk(reloadedAddSign);
        assertThat(text(reloadedAddSign.body(), "/data/approverId")).isEqualTo(secondMemberId);
        assertThat(reloadedAddSign.body().at("/data/approverIds"))
                .extracting(JsonNode::asText)
                .containsExactly(secondMemberId, memberId);
        var originalTaskWhileBeforeSignerIsCurrent = client.get(
                flowRoot + "/tasks?status=PENDING&page=1&size=100");
        assertOk(originalTaskWhileBeforeSignerIsCurrent);
        assertThat(itemValues(
                originalTaskWhileBeforeSignerIsCurrent.body().at("/data/items"), "instanceId"))
                .doesNotContain(addSignInstanceId);
        var beforeSignerTask = secondClient.get(
                flowRoot + "/tasks?status=PENDING&page=1&size=100");
        assertOk(beforeSignerTask);
        assertThat(itemValues(beforeSignerTask.body().at("/data/items"), "instanceId"))
                .contains(addSignInstanceId);

        var beforeSignerApproved = secondClient.postWithCsrf(
                flowRoot + "/instances/" + addSignInstanceId + ":approve",
                json(Map.of("comment", "Security review approved")),
                Map.of());
        assertOk(beforeSignerApproved);
        assertThat(text(beforeSignerApproved.body(), "/data/status")).isEqualTo("PENDING");
        assertThat(text(beforeSignerApproved.body(), "/data/approverId")).isEqualTo(memberId);
        assertThat(beforeSignerApproved.body().at("/data/approverIds"))
                .extracting(JsonNode::asText)
                .containsExactly(secondMemberId, memberId);
        var restoredOriginalTask = client.get(
                flowRoot + "/tasks?status=PENDING&page=1&size=100");
        assertOk(restoredOriginalTask);
        assertThat(itemValues(restoredOriginalTask.body().at("/data/items"), "instanceId"))
                .contains(addSignInstanceId);
        var noStaleBeforeSignerTask = secondClient.get(
                flowRoot + "/tasks?status=PENDING&page=1&size=100");
        assertOk(noStaleBeforeSignerTask);
        assertThat(itemValues(noStaleBeforeSignerTask.body().at("/data/items"), "instanceId"))
                .doesNotContain(addSignInstanceId);

        var approvedAddSign = client.postWithCsrf(
                flowRoot + "/instances/" + addSignInstanceId + ":approve",
                json(Map.of("comment", "Original approver completed the instance")),
                Map.of());
        assertOk(approvedAddSign);
        assertThat(text(approvedAddSign.body(), "/data/status")).isEqualTo("APPROVED");
        assertThat(text(approvedAddSign.body(), "/data/approverId")).isEqualTo(memberId);
        var addSignHistory = client.get(
                flowRoot + "/instances/" + addSignInstanceId + "/history");
        assertOk(addSignHistory);
        assertThat(addSignHistory.body().at("/data/events")).hasSize(4);
        assertThat(text(addSignHistory.body(), "/data/events/1/type")).isEqualTo("ADD_SIGNED");
        assertThat(text(addSignHistory.body(), "/data/events/1/actorId")).isEqualTo(memberId);
        assertThat(text(addSignHistory.body(), "/data/events/1/targetMemberId"))
                .isEqualTo(secondMemberId);
        assertThat(text(addSignHistory.body(), "/data/events/1/position")).isEqualTo("BEFORE");
        assertThat(text(addSignHistory.body(), "/data/events/1/comment"))
                .isEqualTo("Obtain security review first");
        assertThat(text(addSignHistory.body(), "/data/events/2/type")).isEqualTo("APPROVED");
        assertThat(text(addSignHistory.body(), "/data/events/2/actorId"))
                .isEqualTo(secondMemberId);
        assertThat(text(addSignHistory.body(), "/data/events/2/toStatus")).isEqualTo("PENDING");
        assertThat(text(addSignHistory.body(), "/data/events/3/type")).isEqualTo("APPROVED");
        assertThat(text(addSignHistory.body(), "/data/events/3/actorId")).isEqualTo(memberId);
        assertThat(text(addSignHistory.body(), "/data/events/3/toStatus")).isEqualTo("APPROVED");

        var returnInstance = client.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/instances",
                json(Map.of(
                        "definitionVersion", 1,
                        "businessKey", businessKey + "-returned-and-reapproved"
                )),
                Map.of("Idempotency-Key", key()));
        assertCreated(returnInstance);
        var returnInstanceId = text(returnInstance.body(), "/data/instanceId");
        assertThat(returnInstance.body().at("/data/currentStepIndex").asInt()).isZero();
        assertThat(text(returnInstance.body(), "/data/claimState")).isEqualTo("CLAIMED");

        var returnFirstApproval = client.postWithCsrf(
                flowRoot + "/instances/" + returnInstanceId + ":approve",
                json(Map.of("comment", "Advance before exercising return")),
                Map.of());
        assertOk(returnFirstApproval);
        assertThat(text(returnFirstApproval.body(), "/data/status")).isEqualTo("PENDING");
        assertThat(text(returnFirstApproval.body(), "/data/approverId")).isEqualTo(secondMemberId);
        assertThat(returnFirstApproval.body().at("/data/currentStepIndex").asInt()).isOne();
        assertThat(text(returnFirstApproval.body(), "/data/claimState")).isEqualTo("CLAIMED");

        var returnBody = json(Map.of(
                "reason", "  Correct the first-step supporting data  "
        ));
        var returnKey = key();
        var returned = secondClient.postWithCsrf(
                flowRoot + "/instances/" + returnInstanceId + ":return",
                returnBody,
                Map.of("Idempotency-Key", returnKey));
        assertOk(returned);
        assertThat(text(returned.body(), "/data/status")).isEqualTo("PENDING");
        assertThat(text(returned.body(), "/data/approverId")).isEqualTo(memberId);
        assertThat(returned.body().at("/data/currentStepIndex").asInt()).isZero();
        assertThat(text(returned.body(), "/data/claimState")).isEqualTo("CLAIMED");
        assertThat(returned.body().at("/data/approverIds"))
                .extracting(JsonNode::asText)
                .containsExactly(memberId, secondMemberId);

        var replayedReturn = secondClient.postWithCsrf(
                flowRoot + "/instances/" + returnInstanceId + ":return",
                returnBody,
                Map.of("Idempotency-Key", returnKey));
        assertOk(replayedReturn);
        assertThat(replayedReturn.body().at("/data")).isEqualTo(returned.body().at("/data"));
        assertError(secondClient.postWithCsrf(
                        flowRoot + "/instances/" + returnInstanceId + ":return",
                        json(Map.of("reason", "Changed return reason under the same key")),
                        Map.of("Idempotency-Key", returnKey)),
                409,
                "IDEMPOTENCY_CONFLICT");

        var reloadedReturn = client.get(flowRoot + "/instances/" + returnInstanceId);
        assertOk(reloadedReturn);
        assertThat(text(reloadedReturn.body(), "/data/approverId")).isEqualTo(memberId);
        assertThat(reloadedReturn.body().at("/data/currentStepIndex").asInt()).isZero();
        assertThat(text(reloadedReturn.body(), "/data/claimState")).isEqualTo("CLAIMED");
        var returnHistoryAtCursorZero = client.get(
                flowRoot + "/instances/" + returnInstanceId + "/history");
        assertOk(returnHistoryAtCursorZero);
        assertThat(returnHistoryAtCursorZero.body().at("/data/events")).hasSize(3);
        assertThat(text(returnHistoryAtCursorZero.body(), "/data/events/2/type"))
                .isEqualTo("RETURNED");
        assertThat(text(returnHistoryAtCursorZero.body(), "/data/events/2/actorId"))
                .isEqualTo(secondMemberId);
        assertThat(text(returnHistoryAtCursorZero.body(), "/data/events/2/targetMemberId"))
                .isEqualTo(memberId);
        assertThat(text(returnHistoryAtCursorZero.body(), "/data/events/2/comment"))
                .isEqualTo("Correct the first-step supporting data");

        var returnedFirstStepApprovedAgain = client.postWithCsrf(
                flowRoot + "/instances/" + returnInstanceId + ":approve",
                json(Map.of("comment", "First step corrected and re-approved")),
                Map.of());
        assertOk(returnedFirstStepApprovedAgain);
        assertThat(text(returnedFirstStepApprovedAgain.body(), "/data/status"))
                .isEqualTo("PENDING");
        assertThat(text(returnedFirstStepApprovedAgain.body(), "/data/approverId"))
                .isEqualTo(secondMemberId);
        assertThat(returnedFirstStepApprovedAgain.body().at("/data/currentStepIndex").asInt())
                .isOne();
        var returnedSecondStepApprovedAgain = secondClient.postWithCsrf(
                flowRoot + "/instances/" + returnInstanceId + ":approve",
                json(Map.of("comment", "Second step re-approved after return")),
                Map.of());
        assertOk(returnedSecondStepApprovedAgain);
        assertThat(text(returnedSecondStepApprovedAgain.body(), "/data/status"))
                .isEqualTo("APPROVED");
        assertThat(returnedSecondStepApprovedAgain.body().at("/data/currentStepIndex").asInt())
                .isOne();
        assertThat(text(returnedSecondStepApprovedAgain.body(), "/data/claimState"))
                .isEqualTo("CLAIMED");

        var completedReturnHistory = client.get(
                flowRoot + "/instances/" + returnInstanceId + "/history");
        assertOk(completedReturnHistory);
        assertThat(completedReturnHistory.body().at("/data/events")).hasSize(5);
        assertThat(itemValues(completedReturnHistory.body().at("/data/events"), "type"))
                .containsExactly("STARTED", "APPROVED", "RETURNED", "APPROVED", "APPROVED");
        assertThat(text(completedReturnHistory.body(), "/data/events/3/actorId"))
                .isEqualTo(memberId);
        assertThat(text(completedReturnHistory.body(), "/data/events/3/toStatus"))
                .isEqualTo("PENDING");
        assertThat(text(completedReturnHistory.body(), "/data/events/4/actorId"))
                .isEqualTo(secondMemberId);
        assertThat(text(completedReturnHistory.body(), "/data/events/4/toStatus"))
                .isEqualTo("APPROVED");

        var claimInstance = client.postWithCsrf(
                flowRoot + "/definitions/" + assignmentDefinitionId + "/instances",
                json(Map.of(
                        "definitionVersion", 1,
                        "businessKey", businessKey + "-cancelled-and-claimed"
                )),
                Map.of("Idempotency-Key", key()));
        assertCreated(claimInstance);
        var claimInstanceId = text(claimInstance.body(), "/data/instanceId");
        assertThat(text(claimInstance.body(), "/data/approverId")).isEqualTo(memberId);
        assertThat(claimInstance.body().at("/data/currentStepIndex").asInt()).isZero();
        assertThat(text(claimInstance.body(), "/data/claimState")).isEqualTo("CLAIMED");
        var ownerPendingBeforeCancel = client.get(
                flowRoot + "/tasks?status=PENDING&page=1&size=100");
        assertOk(ownerPendingBeforeCancel);
        assertThat(itemValues(ownerPendingBeforeCancel.body().at("/data/items"), "instanceId"))
                .contains(claimInstanceId);

        assertError(client.postWithCsrf(
                        flowRoot + "/instances/" + claimInstanceId + ":cancel-claim",
                        json(Map.of("reason", "   ")),
                        Map.of("Idempotency-Key", key())),
                422,
                "FLOW_CANCEL_CLAIM_REQUEST_INVALID");
        var cancelClaimBody = json(Map.of(
                "reason", "  Release this review to the candidate pool  "
        ));
        var cancelClaimKey = key();
        var claimCancelled = client.postWithCsrf(
                flowRoot + "/instances/" + claimInstanceId + ":cancel-claim",
                cancelClaimBody,
                Map.of("Idempotency-Key", cancelClaimKey));
        assertOk(claimCancelled);
        assertThat(text(claimCancelled.body(), "/data/status")).isEqualTo("PENDING");
        assertThat(text(claimCancelled.body(), "/data/approverId")).isEqualTo(memberId);
        assertThat(claimCancelled.body().at("/data/currentStepIndex").asInt()).isZero();
        assertThat(text(claimCancelled.body(), "/data/claimState")).isEqualTo("OPEN");
        assertThat(claimCancelled.body().at("/data/approverIds"))
                .extracting(JsonNode::asText)
                .containsExactly(memberId);

        var replayedCancelClaim = client.postWithCsrf(
                flowRoot + "/instances/" + claimInstanceId + ":cancel-claim",
                cancelClaimBody,
                Map.of("Idempotency-Key", cancelClaimKey));
        assertOk(replayedCancelClaim);
        assertThat(replayedCancelClaim.body().at("/data"))
                .isEqualTo(claimCancelled.body().at("/data"));
        assertError(client.postWithCsrf(
                        flowRoot + "/instances/" + claimInstanceId + ":cancel-claim",
                        json(Map.of("reason", "Changed cancellation reason under the same key")),
                        Map.of("Idempotency-Key", cancelClaimKey)),
                409,
                "IDEMPOTENCY_CONFLICT");

        var reloadedOpenClaim = client.get(flowRoot + "/instances/" + claimInstanceId);
        assertOk(reloadedOpenClaim);
        assertThat(text(reloadedOpenClaim.body(), "/data/status")).isEqualTo("PENDING");
        assertThat(text(reloadedOpenClaim.body(), "/data/approverId")).isEqualTo(memberId);
        assertThat(reloadedOpenClaim.body().at("/data/currentStepIndex").asInt()).isZero();
        assertThat(text(reloadedOpenClaim.body(), "/data/claimState")).isEqualTo("OPEN");
        var ownerPendingAfterCancel = client.get(
                flowRoot + "/tasks?status=PENDING&page=1&size=100");
        assertOk(ownerPendingAfterCancel);
        assertThat(itemValues(ownerPendingAfterCancel.body().at("/data/items"), "instanceId"))
                .doesNotContain(claimInstanceId);
        var secondPendingBeforeClaim = secondClient.get(
                flowRoot + "/tasks?status=PENDING&page=1&size=100");
        assertOk(secondPendingBeforeClaim);
        assertThat(itemValues(secondPendingBeforeClaim.body().at("/data/items"), "instanceId"))
                .doesNotContain(claimInstanceId);
        var secondClaimable = secondClient.get(
                flowRoot + "/claimable-tasks?page=1&size=100");
        assertOk(secondClaimable);
        assertThat(itemValues(secondClaimable.body().at("/data/items"), "instanceId"))
                .contains(claimInstanceId);

        var openDecision = client.postWithCsrf(
                flowRoot + "/instances/" + claimInstanceId + ":approve",
                json(Map.of("comment", "OPEN tasks cannot be decided")),
                Map.of());
        assertThat(openDecision.status()).isEqualTo(409);
        var openUrge = client.postWithCsrf(
                flowRoot + "/instances/" + claimInstanceId + ":urge",
                json(Map.of("message", "OPEN tasks have no claim owner")),
                Map.of("Idempotency-Key", key()));
        assertThat(openUrge.status()).isEqualTo(409);
        var stillOpenAfterConflicts = client.get(flowRoot + "/instances/" + claimInstanceId);
        assertOk(stillOpenAfterConflicts);
        assertThat(text(stillOpenAfterConflicts.body(), "/data/claimState")).isEqualTo("OPEN");
        assertThat(text(stillOpenAfterConflicts.body(), "/data/approverId")).isEqualTo(memberId);
        assertThat(stillOpenAfterConflicts.body().at("/data/currentStepIndex").asInt()).isZero();

        var claimBody = json(Map.of(
                "comment", "  I will handle this released review  "
        ));
        var claimKey = key();
        var claimed = secondClient.postWithCsrf(
                flowRoot + "/instances/" + claimInstanceId + ":claim",
                claimBody,
                Map.of("Idempotency-Key", claimKey));
        assertOk(claimed);
        assertThat(text(claimed.body(), "/data/status")).isEqualTo("PENDING");
        assertThat(text(claimed.body(), "/data/approverId")).isEqualTo(secondMemberId);
        assertThat(claimed.body().at("/data/currentStepIndex").asInt()).isZero();
        assertThat(text(claimed.body(), "/data/claimState")).isEqualTo("CLAIMED");
        assertThat(claimed.body().at("/data/approverIds"))
                .extracting(JsonNode::asText)
                .containsExactly(secondMemberId);

        var replayedClaim = secondClient.postWithCsrf(
                flowRoot + "/instances/" + claimInstanceId + ":claim",
                claimBody,
                Map.of("Idempotency-Key", claimKey));
        assertOk(replayedClaim);
        assertThat(replayedClaim.body().at("/data")).isEqualTo(claimed.body().at("/data"));
        assertError(secondClient.postWithCsrf(
                        flowRoot + "/instances/" + claimInstanceId + ":claim",
                        json(Map.of("comment", "Changed claim comment under the same key")),
                        Map.of("Idempotency-Key", claimKey)),
                409,
                "IDEMPOTENCY_CONFLICT");
        var losingClaim = client.postWithCsrf(
                flowRoot + "/instances/" + claimInstanceId + ":claim",
                json(Map.of("comment", "A second claimant cannot also win")),
                Map.of("Idempotency-Key", key()));
        assertThat(losingClaim.status()).isEqualTo(409);

        var reloadedClaimed = client.get(flowRoot + "/instances/" + claimInstanceId);
        assertOk(reloadedClaimed);
        assertThat(text(reloadedClaimed.body(), "/data/approverId")).isEqualTo(secondMemberId);
        assertThat(text(reloadedClaimed.body(), "/data/claimState")).isEqualTo("CLAIMED");
        assertThat(reloadedClaimed.body().at("/data/currentStepIndex").asInt()).isZero();
        var noLongerClaimable = secondClient.get(
                flowRoot + "/claimable-tasks?page=1&size=100");
        assertOk(noLongerClaimable);
        assertThat(itemValues(noLongerClaimable.body().at("/data/items"), "instanceId"))
                .doesNotContain(claimInstanceId);
        var noFormerOwnerPending = client.get(
                flowRoot + "/tasks?status=PENDING&page=1&size=100");
        assertOk(noFormerOwnerPending);
        assertThat(itemValues(noFormerOwnerPending.body().at("/data/items"), "instanceId"))
                .doesNotContain(claimInstanceId);
        var claimantPending = secondClient.get(
                flowRoot + "/tasks?status=PENDING&page=1&size=100");
        assertOk(claimantPending);
        assertThat(itemValues(claimantPending.body().at("/data/items"), "instanceId"))
                .contains(claimInstanceId);

        var claimedApproved = secondClient.postWithCsrf(
                flowRoot + "/instances/" + claimInstanceId + ":approve",
                json(Map.of("comment", "Claimed review approved")),
                Map.of());
        assertOk(claimedApproved);
        assertThat(text(claimedApproved.body(), "/data/status")).isEqualTo("APPROVED");
        assertThat(text(claimedApproved.body(), "/data/approverId")).isEqualTo(secondMemberId);
        assertThat(claimedApproved.body().at("/data/currentStepIndex").asInt()).isZero();
        assertThat(text(claimedApproved.body(), "/data/claimState")).isEqualTo("CLAIMED");

        var claimHistory = client.get(
                flowRoot + "/instances/" + claimInstanceId + "/history");
        assertOk(claimHistory);
        assertThat(claimHistory.body().at("/data/events")).hasSize(4);
        assertThat(itemValues(claimHistory.body().at("/data/events"), "type"))
                .containsExactly("STARTED", "CLAIM_CANCELLED", "CLAIMED", "APPROVED");
        assertThat(text(claimHistory.body(), "/data/events/1/actorId")).isEqualTo(memberId);
        assertThat(text(claimHistory.body(), "/data/events/1/comment"))
                .isEqualTo("Release this review to the candidate pool");
        assertThat(claimHistory.body().at("/data/events/1/targetMemberId").isMissingNode())
                .isTrue();
        assertThat(text(claimHistory.body(), "/data/events/2/actorId")).isEqualTo(secondMemberId);
        assertThat(text(claimHistory.body(), "/data/events/2/targetMemberId"))
                .isEqualTo(secondMemberId);
        assertThat(text(claimHistory.body(), "/data/events/2/comment"))
                .isEqualTo("I will handle this released review");
        assertThat(text(claimHistory.body(), "/data/events/3/actorId")).isEqualTo(secondMemberId);
        assertThat(text(claimHistory.body(), "/data/events/3/toStatus")).isEqualTo("APPROVED");

        var reduceDefinition = client.postWithCsrf(
                flowRoot + "/definitions",
                json(Map.of(
                        "name", "Three-step index-based reduce-sign",
                        "approverIds", List.of(memberId, secondMemberId, memberId)
                )),
                Map.of());
        assertCreated(reduceDefinition);
        var reduceDefinitionId = text(reduceDefinition.body(), "/data/definitionId");
        var publishedReduceDefinition = client.postWithCsrf(
                flowRoot + "/definitions/" + reduceDefinitionId + ":publish",
                "{}",
                Map.of());
        assertOk(publishedReduceDefinition);
        assertThat(publishedReduceDefinition.body().at("/data/approverIds"))
                .extracting(JsonNode::asText)
                .containsExactly(memberId, secondMemberId, memberId);

        var reducedBusinessKey = businessKey + "-reduced-sign";
        var reduceInstance = client.postWithCsrf(
                flowRoot + "/definitions/" + reduceDefinitionId + "/instances",
                json(Map.of(
                        "definitionVersion", 1,
                        "businessKey", reducedBusinessKey
                )),
                Map.of("Idempotency-Key", key()));
        assertCreated(reduceInstance);
        var reduceInstanceId = text(reduceInstance.body(), "/data/instanceId");
        assertThat(text(reduceInstance.body(), "/data/status")).isEqualTo("PENDING");
        assertThat(reduceInstance.body().at("/data/currentStepIndex").asInt()).isZero();
        assertThat(text(reduceInstance.body(), "/data/claimState")).isEqualTo("CLAIMED");
        assertThat(reduceInstance.body().at("/data/approverIds"))
                .extracting(JsonNode::asText)
                .containsExactly(memberId, secondMemberId, memberId);

        assertError(client.postWithCsrf(
                        flowRoot + "/instances/" + reduceInstanceId + ":reduce-sign",
                        json(Map.of(
                                "targetStepIndex", 2,
                                "reason", "   "
                        )),
                        Map.of("Idempotency-Key", key())),
                422,
                "FLOW_REDUCE_SIGN_REQUEST_INVALID");
        assertError(client.postWithCsrf(
                        flowRoot + "/instances/" + reduceInstanceId + ":reduce-sign",
                        json(Map.of(
                                "targetStepIndex", 0,
                                "reason", "The current step cannot be removed"
                        )),
                        Map.of("Idempotency-Key", key())),
                422,
                "FLOW_REDUCE_SIGN_REQUEST_INVALID");
        assertError(client.postWithCsrf(
                        flowRoot + "/instances/" + reduceInstanceId + ":reduce-sign",
                        json(Map.of(
                                "targetStepIndex", 3,
                                "reason", "A nonexistent future step cannot be removed"
                        )),
                        Map.of("Idempotency-Key", key())),
                422,
                "FLOW_REDUCE_SIGN_REQUEST_INVALID");

        var reduceBody = json(Map.of(
                "targetStepIndex", 2,
                "reason", "  The duplicate final review is no longer required  "
        ));
        var reduceKey = key();
        var reduced = client.postWithCsrf(
                flowRoot + "/instances/" + reduceInstanceId + ":reduce-sign",
                reduceBody,
                Map.of("Idempotency-Key", reduceKey));
        assertOk(reduced);
        assertThat(text(reduced.body(), "/data/status")).isEqualTo("PENDING");
        assertThat(text(reduced.body(), "/data/approverId")).isEqualTo(memberId);
        assertThat(reduced.body().at("/data/currentStepIndex").asInt()).isZero();
        assertThat(text(reduced.body(), "/data/claimState")).isEqualTo("CLAIMED");
        assertThat(reduced.body().at("/data/approverIds"))
                .extracting(JsonNode::asText)
                .containsExactly(memberId, secondMemberId);

        var replayedReduce = client.postWithCsrf(
                flowRoot + "/instances/" + reduceInstanceId + ":reduce-sign",
                reduceBody,
                Map.of("Idempotency-Key", reduceKey));
        assertOk(replayedReduce);
        assertThat(replayedReduce.body().at("/data")).isEqualTo(reduced.body().at("/data"));
        assertError(client.postWithCsrf(
                        flowRoot + "/instances/" + reduceInstanceId + ":reduce-sign",
                        json(Map.of(
                                "targetStepIndex", 1,
                                "reason", "Changed target under the same key"
                        )),
                        Map.of("Idempotency-Key", reduceKey)),
                409,
                "IDEMPOTENCY_CONFLICT");

        var reloadedReduced = client.get(flowRoot + "/instances/" + reduceInstanceId);
        assertOk(reloadedReduced);
        assertThat(text(reloadedReduced.body(), "/data/approverId")).isEqualTo(memberId);
        assertThat(reloadedReduced.body().at("/data/currentStepIndex").asInt()).isZero();
        assertThat(text(reloadedReduced.body(), "/data/claimState")).isEqualTo("CLAIMED");
        assertThat(reloadedReduced.body().at("/data/approverIds"))
                .extracting(JsonNode::asText)
                .containsExactly(memberId, secondMemberId);
        var historyAfterReduce = client.get(
                flowRoot + "/instances/" + reduceInstanceId + "/history");
        assertOk(historyAfterReduce);
        assertThat(historyAfterReduce.body().at("/data/events")).hasSize(2);
        assertThat(text(historyAfterReduce.body(), "/data/events/1/type"))
                .isEqualTo("SIGN_REMOVED");
        assertThat(text(historyAfterReduce.body(), "/data/events/1/actorId"))
                .isEqualTo(memberId);
        assertThat(text(historyAfterReduce.body(), "/data/events/1/targetMemberId"))
                .isEqualTo(memberId);
        assertThat(historyAfterReduce.body().at("/data/events/1/targetStepIndex").asInt())
                .isEqualTo(2);
        assertThat(text(historyAfterReduce.body(), "/data/events/1/comment"))
                .isEqualTo("The duplicate final review is no longer required");

        var reducedFirstApproval = client.postWithCsrf(
                flowRoot + "/instances/" + reduceInstanceId + ":approve",
                json(Map.of("comment", "First shortened step approved")),
                Map.of());
        assertOk(reducedFirstApproval);
        assertThat(text(reducedFirstApproval.body(), "/data/status")).isEqualTo("PENDING");
        assertThat(text(reducedFirstApproval.body(), "/data/approverId")).isEqualTo(secondMemberId);
        assertThat(reducedFirstApproval.body().at("/data/currentStepIndex").asInt()).isOne();
        assertThat(reducedFirstApproval.body().at("/data/approverIds"))
                .extracting(JsonNode::asText)
                .containsExactly(memberId, secondMemberId);
        var reducedTerminal = secondClient.postWithCsrf(
                flowRoot + "/instances/" + reduceInstanceId + ":approve",
                json(Map.of("comment", "Shortened path completed")),
                Map.of());
        assertOk(reducedTerminal);
        assertThat(text(reducedTerminal.body(), "/data/status")).isEqualTo("APPROVED");
        assertThat(text(reducedTerminal.body(), "/data/approverId")).isEqualTo(secondMemberId);
        assertThat(reducedTerminal.body().at("/data/currentStepIndex").asInt()).isOne();
        assertThat(reducedTerminal.body().at("/data/approverIds"))
                .extracting(JsonNode::asText)
                .containsExactly(memberId, secondMemberId);

        var reducedTerminalHistory = client.get(
                flowRoot + "/instances/" + reduceInstanceId + "/history");
        assertOk(reducedTerminalHistory);
        assertThat(itemValues(reducedTerminalHistory.body().at("/data/events"), "type"))
                .containsExactly("STARTED", "SIGN_REMOVED", "APPROVED", "APPROVED");
        assertThat(text(reducedTerminalHistory.body(), "/data/events/2/actorId"))
                .isEqualTo(memberId);
        assertThat(text(reducedTerminalHistory.body(), "/data/events/2/toStatus"))
                .isEqualTo("PENDING");
        assertThat(text(reducedTerminalHistory.body(), "/data/events/3/actorId"))
                .isEqualTo(secondMemberId);
        assertThat(text(reducedTerminalHistory.body(), "/data/events/3/toStatus"))
                .isEqualTo("APPROVED");

        assertError(client.postWithCsrf(
                        flowRoot + "/instances/" + reduceInstanceId + "/copies",
                        json(Map.of(
                                "targetMemberId", memberId,
                                "message", "Self-copy is invalid"
                        )),
                        Map.of("Idempotency-Key", key())),
                422,
                "FLOW_COPY_TARGET_INVALID");
        assertError(client.postWithCsrf(
                        flowRoot + "/instances/" + reduceInstanceId + "/copies",
                        json(Map.of(
                                "targetMemberId", Long.toString(Long.MAX_VALUE),
                                "message", "The target does not resolve to an active scoped member"
                        )),
                        Map.of("Idempotency-Key", key())),
                422,
                "FLOW_COPY_TARGET_INVALID");
        assertError(client.postWithCsrf(
                        flowRoot + "/instances/" + reduceInstanceId + "/copies",
                        json(Map.of(
                                "targetMemberId", secondMemberId,
                                "message", "x".repeat(501)
                        )),
                        Map.of("Idempotency-Key", key())),
                422,
                "FLOW_COPY_MESSAGE_INVALID");

        var copiedInboxBefore = secondClient.get(
                "/api/v1/systems/" + systemId + "/event/messages?status=UNREAD&page=1&size=100");
        assertOk(copiedInboxBefore);
        assertThat(StreamSupport.stream(
                        copiedInboxBefore.body().at("/data/items").spliterator(), false)
                .filter(item -> "FLOW_INSTANCE_COPIED".equals(item.path("templateCode").asText())))
                .isEmpty();

        var copyBody = json(Map.of(
                "targetMemberId", secondMemberId,
                "message", "  Please follow the shortened approval outcome  "
        ));
        var copyKey = key();
        var copied = client.postWithCsrf(
                flowRoot + "/instances/" + reduceInstanceId + "/copies",
                copyBody,
                Map.of("Idempotency-Key", copyKey));
        assertOk(copied);
        var copyId = text(copied.body(), "/data/copyId");
        assertThat(text(copied.body(), "/data/instanceId")).isEqualTo(reduceInstanceId);
        assertThat(text(copied.body(), "/data/actorId")).isEqualTo(memberId);
        assertThat(text(copied.body(), "/data/recipientId")).isEqualTo(secondMemberId);
        assertThat(text(copied.body(), "/data/message"))
                .isEqualTo("Please follow the shortened approval outcome");
        assertThat(copied.body().at("/data/createdAt").isTextual()).isTrue();

        var replayedCopy = client.postWithCsrf(
                flowRoot + "/instances/" + reduceInstanceId + "/copies",
                copyBody,
                Map.of("Idempotency-Key", copyKey));
        assertOk(replayedCopy);
        assertThat(replayedCopy.body().at("/data")).isEqualTo(copied.body().at("/data"));
        assertError(client.postWithCsrf(
                        flowRoot + "/instances/" + reduceInstanceId + "/copies",
                        json(Map.of(
                                "targetMemberId", secondMemberId,
                                "message", "Changed copy message under the same key"
                        )),
                        Map.of("Idempotency-Key", copyKey)),
                409,
                "IDEMPOTENCY_CONFLICT");
        assertError(client.postWithCsrf(
                        flowRoot + "/instances/" + reduceInstanceId + "/copies",
                        copyBody,
                        Map.of("Idempotency-Key", key())),
                409,
                "FLOW_COPY_ALREADY_EXISTS");

        var copies = client.get(
                flowRoot + "/instances/" + reduceInstanceId + "/copies?page=1&size=20");
        assertOk(copies);
        assertThat(copies.body().at("/data/items")).hasSize(1);
        assertThat(copies.body().at("/data/total").asLong()).isOne();
        assertThat(copies.body().at("/data/page").asInt()).isOne();
        assertThat(copies.body().at("/data/size").asInt()).isEqualTo(20);
        assertThat(text(copies.body(), "/data/items/0/copyId")).isEqualTo(copyId);
        assertThat(text(copies.body(), "/data/items/0/instanceId")).isEqualTo(reduceInstanceId);
        assertThat(text(copies.body(), "/data/items/0/actorId")).isEqualTo(memberId);
        assertThat(text(copies.body(), "/data/items/0/recipientId")).isEqualTo(secondMemberId);
        assertThat(text(copies.body(), "/data/items/0/message"))
                .isEqualTo("Please follow the shortened approval outcome");

        var historyAfterCopy = client.get(
                flowRoot + "/instances/" + reduceInstanceId + "/history");
        assertOk(historyAfterCopy);
        assertThat(historyAfterCopy.body().at("/data/events"))
                .isEqualTo(reducedTerminalHistory.body().at("/data/events"));
        var copiedInboxAfter = secondClient.get(
                "/api/v1/systems/" + systemId + "/event/messages?status=UNREAD&page=1&size=100");
        assertOk(copiedInboxAfter);
        var copiedMessages = StreamSupport.stream(
                        copiedInboxAfter.body().at("/data/items").spliterator(), false)
                .filter(item -> "FLOW_INSTANCE_COPIED".equals(item.path("templateCode").asText()))
                .toList();
        assertThat(copiedMessages).hasSize(1);
        assertThat(copiedMessages.getFirst().path("senderMemberId").asText()).isEqualTo(memberId);
        assertThat(copiedMessages.getFirst().path("recipientMemberId").asText())
                .isEqualTo(secondMemberId);
        assertThat(copiedMessages.getFirst().path("status").asText()).isEqualTo("UNREAD");
        assertThat(copiedMessages.getFirst().path("target").path("type").asText())
                .isEqualTo("FLOW_INSTANCE");
        assertThat(copiedMessages.getFirst().path("target").path("id").asText())
                .isEqualTo(reduceInstanceId);
        assertThat(copiedMessages.getFirst().path("body").asText())
                .contains(reducedBusinessKey, "Please follow the shortened approval outcome");

        var flowRecordSchema = publishFlowRecordModule(client, systemId);
        var flowRecordSchemaVersionId = flowRecordSchema.versionId();
        var refreshedForRecordFlow = client.postWithCsrf("/api/v1/auth/refresh", "{}", Map.of());
        assertOk(refreshedForRecordFlow);
        assertThat(refreshedForRecordFlow.body().at("/data/context/permissions"))
                .extracting(JsonNode::asText)
                .contains(
                        "system.runtime.access",
                        "module." + FLOW_RECORD_MODULE_CODE + ".view",
                        "module." + FLOW_RECORD_MODULE_CODE + ".create",
                        "module." + FLOW_RECORD_MODULE_CODE + ".update");

        var runtimeRoot = "/api/v1/systems/" + systemId + "/runtime/modules/"
                + FLOW_RECORD_MODULE_CODE;
        var boundRecord = createActiveRecord(
                client,
                runtimeRoot,
                flowRecordSchemaVersionId,
                "Flow-bound purchase order");
        var boundRecordId = text(boundRecord.body(), "/data/recordId");
        var manualReplayRecord = createActiveRecord(
                client,
                runtimeRoot,
                flowRecordSchemaVersionId,
                "Manual start exact replay record");
        var manualReplayRecordId = text(manualReplayRecord.body(), "/data/recordId");
        var manualRollbackRecord = createActiveRecord(
                client,
                runtimeRoot,
                flowRecordSchemaVersionId,
                "Manual start projection rollback record");
        var manualRollbackRecordId = text(manualRollbackRecord.body(), "/data/recordId");

        exerciseOpenApiRecords(
                roleAdmin,
                client,
                systemId,
                firstTenantId,
                secondTenantId,
                secondMemberId,
                secondApproverRoleId,
                allDataScopeId,
                selfDataScopeId,
                flowRecordSchemaVersionId,
                boundRecordId);

        client = login();
        assertOk(client.postWithCsrf(
                "/api/v1/context/systems/" + systemId + ":switch",
                "{}",
                Map.of()));
        secondClient = login(secondUsername, secondPassword);
        assertOk(secondClient.postWithCsrf(
                "/api/v1/context/systems/" + systemId + ":switch",
                "{}",
                Map.of()));
        startOnlyClient = login(startOnlyUsername, startOnlyPassword);
        assertOk(startOnlyClient.postWithCsrf(
                "/api/v1/context/systems/" + systemId + ":switch",
                "{}",
                Map.of()));

        var publishedSource = exercisePublishedDataSource(
                client, systemId, firstTenantId, secondTenantId,
                flowRecordSchema, boundRecordId);
        var publishedKpi = exercisePublishedKpi(
                client, systemId, firstTenantId, secondTenantId,
                publishedSource, memberId);
        exercisePublishedDashboard(
                client, systemId, firstTenantId, secondTenantId,
                publishedSource, publishedKpi, boundRecordId, memberId);
        exerciseKpiUnmetReminder(
                client, systemId, firstTenantId, secondTenantId,
                publishedKpi);
        var publishedReportCode = exercisePublishedReport(
                client, secondClient, systemId, firstTenantId, secondTenantId,
                publishedSource, boundRecordId, secondMemberId);
        exerciseAiAgent(
                client, systemId, firstTenantId, secondTenantId,
                secondMemberId, flowRecordSchema, publishedSource,
                publishedReportCode);

        exerciseRecordMemberApproverSource(
                client,
                secondClient,
                flowRoot,
                runtimeRoot,
                systemId,
                flowRecordSchema,
                secondMemberId,
                memberId);

        assertError(client.postWithCsrf(
                        flowRoot + "/definitions",
                        json(Map.of(
                                "name", "Structurally invalid status mapping",
                                "approverId", memberId,
                                "recordStatusMapping", Map.of(
                                        "fieldCode", "approval_status",
                                        "approvedValue", flowRecordSchema.approvedOptionId(),
                                        "rejectedValue", flowRecordSchema.rejectedOptionId(),
                                        "withdrawnValue", flowRecordSchema.withdrawnOptionId()
                                )
                        )),
                        Map.of()),
                422,
                "FLOW_RECORD_STATUS_MAPPING_INVALID");

        var invalidRuntimeMappingDefinition = client.postWithCsrf(
                flowRoot + "/definitions",
                json(Map.of(
                        "name", "Wrong field type mapping",
                        "approverId", memberId,
                        "recordStatusMapping", Map.of(
                                "fieldCode", "route",
                                "approvedValue", flowRecordSchema.approvedOptionId(),
                                "rejectedValue", flowRecordSchema.rejectedOptionId(),
                                "withdrawnValue", flowRecordSchema.withdrawnOptionId(),
                                "terminatedValue", flowRecordSchema.terminatedOptionId()
                        )
                )),
                Map.of());
        assertCreated(invalidRuntimeMappingDefinition);
        var invalidRuntimeMappingDefinitionId = text(
                invalidRuntimeMappingDefinition.body(), "/data/definitionId");
        assertOk(client.postWithCsrf(
                flowRoot + "/definitions/" + invalidRuntimeMappingDefinitionId + ":publish",
                "{}",
                Map.of()));
        var invalidRuntimeBusinessKey = "invalid-record-status-mapping-" + boundRecordId;
        assertError(client.postWithCsrf(
                        flowRoot + "/definitions/" + invalidRuntimeMappingDefinitionId + "/instances",
                        json(Map.of(
                                "definitionVersion", 1,
                                "businessKey", invalidRuntimeBusinessKey,
                                "recordBinding", Map.of(
                                        "moduleCode", FLOW_RECORD_MODULE_CODE,
                                        "recordId", boundRecordId
                                )
                        )),
                        Map.of("Idempotency-Key", key())),
                422,
                "RECORD_FLOW_STATUS_MAPPING_INVALID");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_flow_instance "
                        + "WHERE system_id=? AND tenant_id=? AND business_key=?",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(firstTenantId),
                invalidRuntimeBusinessKey)).isZero();

        var recordDefinition = client.postWithCsrf(
                flowRoot + "/definitions",
                json(Map.of(
                        "name", "Record-bound approval",
                        "approverId", memberId,
                        "recordStatusMapping", statusMapping(flowRecordSchema)
                )),
                Map.of());
        assertCreated(recordDefinition);
        var recordDefinitionId = text(recordDefinition.body(), "/data/definitionId");
        assertThat(text(
                recordDefinition.body(), "/data/recordStatusMapping/fieldCode"))
                .isEqualTo("approval_status");
        assertThat(text(
                recordDefinition.body(), "/data/recordStatusMapping/approvedValue"))
                .isEqualTo(flowRecordSchema.approvedOptionId());
        assertOk(client.postWithCsrf(
                flowRoot + "/definitions/" + recordDefinitionId + ":publish",
                "{}",
                Map.of()));

        var boundStartBody = json(Map.of(
                "definitionVersion", 1,
                "businessKey", "record-bound-" + boundRecordId,
                "recordBinding", Map.of(
                        "moduleCode", FLOW_RECORD_MODULE_CODE,
                        "recordId", boundRecordId
                )
        ));
        var boundInstance = client.postWithCsrf(
                flowRoot + "/definitions/" + recordDefinitionId + "/instances",
                boundStartBody,
                Map.of("Idempotency-Key", key()));
        assertCreated(boundInstance);
        var boundInstanceId = text(boundInstance.body(), "/data/instanceId");
        assertThat(text(boundInstance.body(), "/data/recordBinding/moduleCode"))
                .isEqualTo(FLOW_RECORD_MODULE_CODE);
        assertThat(text(boundInstance.body(), "/data/recordBinding/recordId"))
                .isEqualTo(boundRecordId);

        var pendingRecordFlow = client.get(
                runtimeRoot + "/records/" + boundRecordId + "/flow-state");
        assertOk(pendingRecordFlow);
        assertThat(text(pendingRecordFlow.body(), "/data/instanceId"))
                .isEqualTo(boundInstanceId);
        assertThat(text(pendingRecordFlow.body(), "/data/status")).isEqualTo("PENDING");
        assertThat(pendingRecordFlow.body().at("/data/version").asLong()).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_record_flow_state "
                        + "WHERE system_id=? AND tenant_id=? AND instance_id=? "
                        + "AND status_field_code='approval_status' "
                        + "AND status_approved_value=? AND status_rejected_value=? "
                        + "AND status_withdrawn_value=? AND status_terminated_value=?",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(firstTenantId),
                Long.parseLong(boundInstanceId),
                Long.parseLong(flowRecordSchema.approvedOptionId()),
                Long.parseLong(flowRecordSchema.rejectedOptionId()),
                Long.parseLong(flowRecordSchema.withdrawnOptionId()),
                Long.parseLong(flowRecordSchema.terminatedOptionId()))).isOne();

        var conflictingBusinessKey = "record-binding-conflict-" + boundRecordId;
        assertError(client.postWithCsrf(
                        flowRoot + "/definitions/" + recordDefinitionId + "/instances",
                        json(Map.of(
                                "definitionVersion", 1,
                                "businessKey", conflictingBusinessKey,
                                "recordBinding", Map.of(
                                        "moduleCode", FLOW_RECORD_MODULE_CODE,
                                        "recordId", boundRecordId
                                )
                        )),
                        Map.of("Idempotency-Key", key())),
                409,
                "RECORD_FLOW_ALREADY_PENDING");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_flow_instance WHERE system_id=? AND tenant_id=? "
                        + "AND business_key=?",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(firstTenantId),
                conflictingBusinessKey)).isZero();

        var approvedBoundInstance = client.postWithCsrf(
                flowRoot + "/instances/" + boundInstanceId + ":approve",
                json(Map.of("comment", "Approve and write the record status")),
                Map.of());
        assertOk(approvedBoundInstance);
        assertThat(text(approvedBoundInstance.body(), "/data/status")).isEqualTo("APPROVED");
        var approvedRecordFlow = client.get(
                runtimeRoot + "/records/" + boundRecordId + "/flow-state");
        assertOk(approvedRecordFlow);
        assertThat(text(approvedRecordFlow.body(), "/data/instanceId"))
                .isEqualTo(boundInstanceId);
        assertThat(text(approvedRecordFlow.body(), "/data/status")).isEqualTo("APPROVED");
        assertThat(approvedRecordFlow.body().at("/data/version").asLong()).isOne();
        var approvedBoundRecord = client.get(
                runtimeRoot + "/records/" + boundRecordId);
        assertOk(approvedBoundRecord);
        assertThat(fieldValue(approvedBoundRecord.body(), "approval_status"))
                .isEqualTo(flowRecordSchema.approvedOptionId());
        assertThat(approvedBoundRecord.body().at("/data/version").asLong()).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_record_history "
                        + "WHERE system_id=? AND tenant_id=? AND record_id=? "
                        + "AND record_version=2 AND action='FLOW_STATUS_MAPPED' "
                        + "AND actor_member_id=?",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(firstTenantId),
                Long.parseLong(boundRecordId),
                Long.parseLong(memberId))).isOne();

        var reboundInstance = client.postWithCsrf(
                flowRoot + "/definitions/" + recordDefinitionId + "/instances",
                json(Map.of(
                        "definitionVersion", 1,
                        "businessKey", "record-rebound-" + boundRecordId,
                        "recordBinding", Map.of(
                                "moduleCode", FLOW_RECORD_MODULE_CODE,
                                "recordId", boundRecordId
                        )
                )),
                Map.of("Idempotency-Key", key()));
        assertCreated(reboundInstance);
        var reboundInstanceId = text(reboundInstance.body(), "/data/instanceId");
        var reboundPending = client.get(
                runtimeRoot + "/records/" + boundRecordId + "/flow-state");
        assertOk(reboundPending);
        assertThat(text(reboundPending.body(), "/data/instanceId")).isEqualTo(reboundInstanceId);
        assertThat(text(reboundPending.body(), "/data/status")).isEqualTo("PENDING");
        assertThat(reboundPending.body().at("/data/version").asLong()).isEqualTo(2);
        var reboundWithdrawn = client.postWithCsrf(
                flowRoot + "/instances/" + reboundInstanceId + ":withdraw",
                json(Map.of("reason", "Requester cancelled the rebound approval")),
                Map.of("Idempotency-Key", key()));
        assertOk(reboundWithdrawn);
        assertThat(text(reboundWithdrawn.body(), "/data/status")).isEqualTo("WITHDRAWN");
        var withdrawnRecordFlow = client.get(
                runtimeRoot + "/records/" + boundRecordId + "/flow-state");
        assertOk(withdrawnRecordFlow);
        assertThat(text(withdrawnRecordFlow.body(), "/data/status")).isEqualTo("WITHDRAWN");
        assertThat(withdrawnRecordFlow.body().at("/data/version").asLong()).isEqualTo(3);
        var withdrawnBoundRecord = client.get(
                runtimeRoot + "/records/" + boundRecordId);
        assertOk(withdrawnBoundRecord);
        assertThat(fieldValue(withdrawnBoundRecord.body(), "approval_status"))
                .isEqualTo(flowRecordSchema.withdrawnOptionId());
        assertThat(withdrawnBoundRecord.body().at("/data/version").asLong()).isEqualTo(3);

        var rejectedMappedRecord = createActiveRecord(
                client,
                runtimeRoot,
                flowRecordSchemaVersionId,
                "Rejected mapped record");
        var rejectedMappedRecordId = text(rejectedMappedRecord.body(), "/data/recordId");
        var rejectedMappedInstance = client.postWithCsrf(
                flowRoot + "/definitions/" + recordDefinitionId + "/instances",
                json(Map.of(
                        "definitionVersion", 1,
                        "businessKey", "record-rejected-mapping-" + rejectedMappedRecordId,
                        "recordBinding", Map.of(
                                "moduleCode", FLOW_RECORD_MODULE_CODE,
                                "recordId", rejectedMappedRecordId
                        )
                )),
                Map.of("Idempotency-Key", key()));
        assertCreated(rejectedMappedInstance);
        assertOk(client.postWithCsrf(
                flowRoot + "/instances/"
                        + text(rejectedMappedInstance.body(), "/data/instanceId") + ":reject",
                json(Map.of("reason", "Reject and map the record status")),
                Map.of()));
        var rejectedMappedDetail = client.get(
                runtimeRoot + "/records/" + rejectedMappedRecordId);
        assertOk(rejectedMappedDetail);
        assertThat(fieldValue(rejectedMappedDetail.body(), "approval_status"))
                .isEqualTo(flowRecordSchema.rejectedOptionId());
        assertThat(rejectedMappedDetail.body().at("/data/version").asLong()).isEqualTo(2);

        var terminatedMappedRecord = createActiveRecord(
                client,
                runtimeRoot,
                flowRecordSchemaVersionId,
                "Terminated mapped record");
        var terminatedMappedRecordId = text(terminatedMappedRecord.body(), "/data/recordId");
        var terminatedMappedInstance = client.postWithCsrf(
                flowRoot + "/definitions/" + recordDefinitionId + "/instances",
                json(Map.of(
                        "definitionVersion", 1,
                        "businessKey", "record-terminated-mapping-" + terminatedMappedRecordId,
                        "recordBinding", Map.of(
                                "moduleCode", FLOW_RECORD_MODULE_CODE,
                                "recordId", terminatedMappedRecordId
                        )
                )),
                Map.of("Idempotency-Key", key()));
        assertCreated(terminatedMappedInstance);
        assertOk(client.postWithCsrf(
                flowRoot + "/instances/"
                        + text(terminatedMappedInstance.body(), "/data/instanceId") + ":terminate",
                json(Map.of("reason", "Terminate and map the record status")),
                Map.of("Idempotency-Key", key())));
        var terminatedMappedDetail = client.get(
                runtimeRoot + "/records/" + terminatedMappedRecordId);
        assertOk(terminatedMappedDetail);
        assertThat(fieldValue(terminatedMappedDetail.body(), "approval_status"))
                .isEqualTo(flowRecordSchema.terminatedOptionId());
        assertThat(terminatedMappedDetail.body().at("/data/version").asLong()).isEqualTo(2);

        var inactiveRecord = client.postWithCsrf(
                runtimeRoot + "/records",
                json(Map.of(
                        "schemaVersionId", flowRecordSchemaVersionId,
                        "title", "Inactive flow record",
                        "values", Map.of()
                )),
                Map.of("Idempotency-Key", key()));
        assertCreated(inactiveRecord);
        assertError(client.postWithCsrf(
                        flowRoot + "/definitions/" + recordDefinitionId + "/instances",
                        json(Map.of(
                                "definitionVersion", 1,
                                "businessKey", "inactive-record-binding",
                                "recordBinding", Map.of(
                                        "moduleCode", FLOW_RECORD_MODULE_CODE,
                                        "recordId", text(inactiveRecord.body(), "/data/recordId")
                                )
                        )),
                        Map.of("Idempotency-Key", key())),
                409,
                "RECORD_FLOW_RECORD_NOT_ACTIVE");

        var rollbackRecord = createActiveRecord(
                client,
                runtimeRoot,
                flowRecordSchemaVersionId,
                "Projection rollback record");
        var rollbackRecordId = text(rollbackRecord.body(), "/data/recordId");
        var rollbackInstance = client.postWithCsrf(
                flowRoot + "/definitions/" + recordDefinitionId + "/instances",
                json(Map.of(
                        "definitionVersion", 1,
                        "businessKey", "record-writeback-rollback-" + rollbackRecordId,
                        "recordBinding", Map.of(
                                "moduleCode", FLOW_RECORD_MODULE_CODE,
                                "recordId", rollbackRecordId
                        )
                )),
                Map.of("Idempotency-Key", key()));
        assertCreated(rollbackInstance);
        var rollbackInstanceId = text(rollbackInstance.body(), "/data/instanceId");
        assertThat(jdbcTemplate.update(
                "DELETE FROM un_module_record_flow_state "
                        + "WHERE system_id=? AND tenant_id=? AND record_id=? AND instance_id=?",
                Long.parseLong(systemId),
                Long.parseLong(firstTenantId),
                Long.parseLong(rollbackRecordId),
                Long.parseLong(rollbackInstanceId))).isOne();
        assertError(client.postWithCsrf(
                        flowRoot + "/instances/" + rollbackInstanceId + ":approve",
                        json(Map.of("comment", "Projection failure must roll back Flow")),
                        Map.of()),
                409,
                "RECORD_FLOW_STATE_CONFLICT");
        var rolledBackFlow = client.get(flowRoot + "/instances/" + rollbackInstanceId);
        assertOk(rolledBackFlow);
        assertThat(text(rolledBackFlow.body(), "/data/status")).isEqualTo("PENDING");
        var rolledBackHistory = client.get(
                flowRoot + "/instances/" + rollbackInstanceId + "/history");
        assertOk(rolledBackHistory);
        assertThat(itemValues(rolledBackHistory.body().at("/data/events"), "type"))
                .containsExactly("STARTED");

        var atomicMappedRollbackRecord = createActiveRecord(
                client,
                runtimeRoot,
                flowRecordSchemaVersionId,
                "Atomic mapped status rollback");
        var atomicMappedRollbackRecordId = text(
                atomicMappedRollbackRecord.body(), "/data/recordId");
        var atomicMappedRollbackInstance = client.postWithCsrf(
                flowRoot + "/definitions/" + recordDefinitionId + "/instances",
                json(Map.of(
                        "definitionVersion", 1,
                        "businessKey", "atomic-mapped-rollback-" + atomicMappedRollbackRecordId,
                        "recordBinding", Map.of(
                                "moduleCode", FLOW_RECORD_MODULE_CODE,
                                "recordId", atomicMappedRollbackRecordId
                        )
                )),
                Map.of("Idempotency-Key", key()));
        assertCreated(atomicMappedRollbackInstance);
        var atomicMappedRollbackInstanceId = text(
                atomicMappedRollbackInstance.body(), "/data/instanceId");
        jdbcTemplate.execute("""
                CREATE TRIGGER test_fail_terminal_status_projection
                BEFORE UPDATE ON un_module_record_flow_state
                FOR EACH ROW
                BEGIN
                    IF OLD.instance_id = %s THEN
                        SIGNAL SQLSTATE '23000'
                            SET MYSQL_ERRNO = 1062,
                                MESSAGE_TEXT = 'forced terminal status projection conflict';
                    END IF;
                END
                """.formatted(atomicMappedRollbackInstanceId));
        try {
            assertError(client.postWithCsrf(
                            flowRoot + "/instances/"
                                    + atomicMappedRollbackInstanceId + ":approve",
                            json(Map.of("comment", "Everything must roll back")),
                            Map.of()),
                    409,
                    "RECORD_FLOW_STATE_CONFLICT");
        } finally {
            jdbcTemplate.execute(
                    "DROP TRIGGER IF EXISTS test_fail_terminal_status_projection");
        }
        var atomicMappedRollbackFlow = client.get(
                flowRoot + "/instances/" + atomicMappedRollbackInstanceId);
        assertOk(atomicMappedRollbackFlow);
        assertThat(text(atomicMappedRollbackFlow.body(), "/data/status"))
                .isEqualTo("PENDING");
        var atomicMappedRollbackDetail = client.get(
                runtimeRoot + "/records/" + atomicMappedRollbackRecordId);
        assertOk(atomicMappedRollbackDetail);
        assertThat(atomicMappedRollbackDetail.body().at("/data/version").asLong())
                .isOne();
        assertThat(hasFieldValue(
                atomicMappedRollbackDetail.body(), "approval_status")).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_record_history "
                        + "WHERE system_id=? AND tenant_id=? AND record_id=? "
                        + "AND action='FLOW_STATUS_MAPPED'",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(firstTenantId),
                Long.parseLong(atomicMappedRollbackRecordId))).isZero();

        assertOk(client.postWithCsrf(
                "/api/v1/context/tenants/" + secondTenantId + ":switch",
                "{}",
                Map.of()));
        var isolatedRecordDefinition = client.postWithCsrf(
                flowRoot + "/definitions",
                json(Map.of(
                        "name", "Isolated record approval",
                        "approverId", memberId
                )),
                Map.of());
        assertCreated(isolatedRecordDefinition);
        var isolatedRecordDefinitionId = text(
                isolatedRecordDefinition.body(), "/data/definitionId");
        assertOk(client.postWithCsrf(
                flowRoot + "/definitions/" + isolatedRecordDefinitionId + ":publish",
                "{}",
                Map.of()));
        var isolatedBusinessKey = "isolated-record-binding-" + boundRecordId;
        var isolatedBinding = client.postWithCsrf(
                flowRoot + "/definitions/" + isolatedRecordDefinitionId + "/instances",
                json(Map.of(
                        "definitionVersion", 1,
                        "businessKey", isolatedBusinessKey,
                        "recordBinding", Map.of(
                                "moduleCode", FLOW_RECORD_MODULE_CODE,
                                "recordId", boundRecordId
                        )
                )),
                Map.of("Idempotency-Key", key()));
        assertThat(isolatedBinding.status()).isEqualTo(404);
        assertOk(client.postWithCsrf(
                "/api/v1/context/tenants/" + firstTenantId + ":switch",
                "{}",
                Map.of()));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_flow_instance WHERE system_id=? AND business_key=?",
                Long.class,
                Long.parseLong(systemId),
                isolatedBusinessKey)).isZero();

        var lowPriorityTriggerDefinition = client.postWithCsrf(
                flowRoot + "/definitions",
                json(Map.of(
                        "name", "Low priority activation approval",
                        "approverId", memberId,
                        "triggerBinding", Map.of(
                                "moduleCode", FLOW_RECORD_MODULE_CODE,
                                "event", "RECORD_ACTIVATED",
                                "priority", 100,
                                "exclusive", true
                        )
                )),
                Map.of());
        assertCreated(lowPriorityTriggerDefinition);
        var lowPriorityTriggerDefinitionId = text(
                lowPriorityTriggerDefinition.body(), "/data/definitionId");
        assertThat(text(
                lowPriorityTriggerDefinition.body(), "/data/triggerBinding/moduleCode"))
                .isEqualTo(FLOW_RECORD_MODULE_CODE);
        assertThat(lowPriorityTriggerDefinition.body()
                .at("/data/triggerBinding/priority").asInt()).isEqualTo(100);
        assertOk(client.postWithCsrf(
                flowRoot + "/definitions/" + lowPriorityTriggerDefinitionId + ":publish",
                "{}",
                Map.of()));

        var highPriorityTriggerDefinition = client.postWithCsrf(
                flowRoot + "/definitions",
                json(Map.of(
                        "name", "High priority activation approval",
                        "approverId", memberId,
                        "triggerBinding", Map.of(
                                "moduleCode", FLOW_RECORD_MODULE_CODE,
                                "event", "RECORD_ACTIVATED",
                                "priority", 200,
                                "exclusive", true
                        )
                )),
                Map.of());
        assertCreated(highPriorityTriggerDefinition);
        var highPriorityTriggerDefinitionId = text(
                highPriorityTriggerDefinition.body(), "/data/definitionId");
        assertOk(client.postWithCsrf(
                flowRoot + "/definitions/" + highPriorityTriggerDefinitionId + ":publish",
                "{}",
                Map.of()));

        var automaticRecord = client.postWithCsrf(
                runtimeRoot + "/records",
                json(Map.of(
                        "schemaVersionId", flowRecordSchemaVersionId,
                        "title", "Automatically approved record",
                        "values", Map.of()
                )),
                Map.of("Idempotency-Key", key()));
        assertCreated(automaticRecord);
        var automaticRecordId = text(automaticRecord.body(), "/data/recordId");
        var automaticRecordNo = text(automaticRecord.body(), "/data/recordNo");
        var activationKey = key();
        var activationBody = json(Map.of("expectedVersion", 0));
        var activatedAutomaticRecord = client.postWithCsrf(
                runtimeRoot + "/records/" + automaticRecordId + ":activate",
                activationBody,
                Map.of("Idempotency-Key", activationKey));
        assertOk(activatedAutomaticRecord);
        assertThat(text(activatedAutomaticRecord.body(), "/data/status")).isEqualTo("ACTIVE");

        var automaticFlowState = client.get(
                runtimeRoot + "/records/" + automaticRecordId + "/flow-state");
        assertOk(automaticFlowState);
        assertThat(text(automaticFlowState.body(), "/data/status")).isEqualTo("PENDING");
        var automaticInstanceId = text(automaticFlowState.body(), "/data/instanceId");
        var automaticInstance = client.get(
                flowRoot + "/instances/" + automaticInstanceId);
        assertOk(automaticInstance);
        assertThat(text(automaticInstance.body(), "/data/definitionId"))
                .isEqualTo(highPriorityTriggerDefinitionId);
        assertThat(text(automaticInstance.body(), "/data/businessKey"))
                .isEqualTo(automaticRecordNo);
        assertThat(text(automaticInstance.body(), "/data/recordBinding/moduleCode"))
                .isEqualTo(FLOW_RECORD_MODULE_CODE);
        assertThat(text(automaticInstance.body(), "/data/recordBinding/recordId"))
                .isEqualTo(automaticRecordId);
        var automaticEventKey = "record:%s:%s:%s:%s:1:RECORD_ACTIVATED".formatted(
                systemId, firstTenantId, FLOW_RECORD_MODULE_CODE, automaticRecordId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_flow_instance WHERE system_id=? AND tenant_id=? "
                        + "AND definition_id=? AND record_id=?",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(firstTenantId),
                Long.parseLong(lowPriorityTriggerDefinitionId),
                Long.parseLong(automaticRecordId))).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_flow_trigger_dispatch WHERE system_id=? AND tenant_id=? "
                        + "AND event_key=? AND instance_id=?",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(firstTenantId),
                automaticEventKey,
                Long.parseLong(automaticInstanceId))).isOne();

        var activationReplay = client.postWithCsrf(
                runtimeRoot + "/records/" + automaticRecordId + ":activate",
                activationBody,
                Map.of("Idempotency-Key", activationKey));
        assertOk(activationReplay);
        assertThat(activationReplay.body().at("/data"))
                .isEqualTo(activatedAutomaticRecord.body().at("/data"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_flow_instance WHERE system_id=? AND tenant_id=? "
                        + "AND record_id=? AND business_key=?",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(firstTenantId),
                Long.parseLong(automaticRecordId),
                automaticRecordNo)).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_flow_trigger_dispatch WHERE system_id=? AND tenant_id=? "
                        + "AND event_key=?",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(firstTenantId),
                automaticEventKey)).isOne();

        var approvedAutomaticInstance = client.postWithCsrf(
                flowRoot + "/instances/" + automaticInstanceId + ":approve",
                json(Map.of("comment", "Automatic activation approval completed")),
                Map.of());
        assertOk(approvedAutomaticInstance);
        assertThat(text(approvedAutomaticInstance.body(), "/data/status")).isEqualTo("APPROVED");
        var approvedAutomaticFlowState = client.get(
                runtimeRoot + "/records/" + automaticRecordId + "/flow-state");
        assertOk(approvedAutomaticFlowState);
        assertThat(text(approvedAutomaticFlowState.body(), "/data/status"))
                .isEqualTo("APPROVED");

        assertOk(client.postWithCsrf(
                "/api/v1/context/tenants/" + secondTenantId + ":switch",
                "{}",
                Map.of()));
        var noMatchRecord = client.postWithCsrf(
                runtimeRoot + "/records",
                json(Map.of(
                        "schemaVersionId", flowRecordSchemaVersionId,
                        "title", "No matching tenant trigger",
                        "values", Map.of()
                )),
                Map.of("Idempotency-Key", key()));
        assertCreated(noMatchRecord);
        var noMatchRecordId = text(noMatchRecord.body(), "/data/recordId");
        assertOk(client.postWithCsrf(
                runtimeRoot + "/records/" + noMatchRecordId + ":activate",
                json(Map.of("expectedVersion", 0)),
                Map.of("Idempotency-Key", key())));
        var noMatchFlowState = client.get(
                runtimeRoot + "/records/" + noMatchRecordId + "/flow-state");
        assertOk(noMatchFlowState);
        var noMatchFlowStateData = noMatchFlowState.body().at("/data");
        assertThat(noMatchFlowStateData.isMissingNode() || noMatchFlowStateData.isNull()).isTrue();
        var noMatchEventKey = "record:%s:%s:%s:%s:1:RECORD_ACTIVATED".formatted(
                systemId, secondTenantId, FLOW_RECORD_MODULE_CODE, noMatchRecordId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_flow_trigger_dispatch WHERE system_id=? AND tenant_id=? "
                        + "AND event_key=? AND instance_id IS NULL",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(secondTenantId),
                noMatchEventKey)).isOne();
        assertOk(client.postWithCsrf(
                "/api/v1/context/tenants/" + firstTenantId + ":switch",
                "{}",
                Map.of()));

        var failedTriggerRecord = client.postWithCsrf(
                runtimeRoot + "/records",
                json(Map.of(
                        "schemaVersionId", flowRecordSchemaVersionId,
                        "title", "Automatic trigger rollback",
                        "values", Map.of()
                )),
                Map.of("Idempotency-Key", key()));
        assertCreated(failedTriggerRecord);
        var failedTriggerRecordId = text(failedTriggerRecord.body(), "/data/recordId");
        var conflictingInstanceId = Long.MAX_VALUE - 120;
        assertThat(jdbcTemplate.update("""
                        INSERT INTO un_module_record_flow_state
                            (system_id,tenant_id,record_id,logical_module_id,instance_id,status,version,
                             created_at,created_by,updated_at,updated_by)
                        SELECT system_id,tenant_id,record_id,logical_module_id,?,'PENDING',0,
                               NOW(6),?,NOW(6),?
                          FROM un_module_record
                         WHERE system_id=? AND tenant_id=? AND record_id=?
                        """,
                conflictingInstanceId,
                Long.parseLong(memberId),
                Long.parseLong(memberId),
                Long.parseLong(systemId),
                Long.parseLong(firstTenantId),
                Long.parseLong(failedTriggerRecordId))).isOne();
        assertError(client.postWithCsrf(
                        runtimeRoot + "/records/" + failedTriggerRecordId + ":activate",
                        json(Map.of("expectedVersion", 0)),
                        Map.of("Idempotency-Key", key())),
                409,
                "RECORD_FLOW_ALREADY_PENDING");
        var rolledBackRecordActivation = client.get(
                runtimeRoot + "/records/" + failedTriggerRecordId);
        assertOk(rolledBackRecordActivation);
        assertThat(text(rolledBackRecordActivation.body(), "/data/status")).isEqualTo("DRAFT");
        assertThat(rolledBackRecordActivation.body().at("/data/version").asLong()).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_flow_instance WHERE system_id=? AND tenant_id=? AND record_id=?",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(firstTenantId),
                Long.parseLong(failedTriggerRecordId))).isZero();
        var failedTriggerEventKey = "record:%s:%s:%s:%s:1:RECORD_ACTIVATED".formatted(
                systemId, firstTenantId, FLOW_RECORD_MODULE_CODE, failedTriggerRecordId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_flow_trigger_dispatch "
                        + "WHERE system_id=? AND tenant_id=? AND event_key=?",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(firstTenantId),
                failedTriggerEventKey)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_record_history "
                        + "WHERE system_id=? AND tenant_id=? AND record_id=? AND action='RECORD_ACTIVATED'",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(firstTenantId),
                Long.parseLong(failedTriggerRecordId))).isZero();

        assertOk(client.putWithCsrf(
                flowRoot + "/definitions/" + lowPriorityTriggerDefinitionId + "/draft",
                json(Map.of(
                        "name", "Low priority activation approval disabled",
                        "approverId", memberId
                )),
                Map.of()));
        assertOk(client.postWithCsrf(
                flowRoot + "/definitions/" + lowPriorityTriggerDefinitionId + ":publish",
                "{}",
                Map.of()));
        assertOk(client.putWithCsrf(
                flowRoot + "/definitions/" + highPriorityTriggerDefinitionId + "/draft",
                json(Map.of(
                        "name", "High priority activation approval disabled",
                        "approverId", memberId
                )),
                Map.of()));
        assertOk(client.postWithCsrf(
                flowRoot + "/definitions/" + highPriorityTriggerDefinitionId + ":publish",
                "{}",
                Map.of()));

        var firstFanoutDefinition = client.postWithCsrf(
                flowRoot + "/definitions",
                json(Map.of(
                        "name", "Fanout approval first",
                        "approverId", memberId,
                        "triggerBinding", Map.of(
                                "moduleCode", FLOW_RECORD_MODULE_CODE,
                                "event", "RECORD_ACTIVATED",
                                "priority", 300,
                                "exclusive", false,
                                "conditions", List.of(Map.of(
                                        "fieldCode", "route",
                                        "operator", "EQ",
                                        "value", "fanout"
                                ))
                        ),
                        "recordStatusMapping", statusMapping(flowRecordSchema)
                )),
                Map.of());
        assertCreated(firstFanoutDefinition);
        var firstFanoutDefinitionId = text(firstFanoutDefinition.body(), "/data/definitionId");
        assertThat(text(
                firstFanoutDefinition.body(),
                "/data/triggerBinding/conditions/0/fieldCode")).isEqualTo("route");
        assertOk(client.postWithCsrf(
                flowRoot + "/definitions/" + firstFanoutDefinitionId + ":publish",
                "{}",
                Map.of()));

        var secondFanoutDefinition = client.postWithCsrf(
                flowRoot + "/definitions",
                json(Map.of(
                        "name", "Fanout approval second",
                        "approverId", memberId,
                        "triggerBinding", Map.of(
                                "moduleCode", FLOW_RECORD_MODULE_CODE,
                                "event", "RECORD_ACTIVATED",
                                "priority", 100,
                                "exclusive", false,
                                "conditions", List.of(Map.of(
                                        "fieldCode", "route",
                                        "operator", "EQ",
                                        "value", "fanout"
                                ))
                        ),
                        "recordStatusMapping", Map.of(
                                "fieldCode", "approval_status",
                                "approvedValue", flowRecordSchema.rejectedOptionId(),
                                "rejectedValue", flowRecordSchema.approvedOptionId(),
                                "withdrawnValue", flowRecordSchema.withdrawnOptionId(),
                                "terminatedValue", flowRecordSchema.terminatedOptionId()
                        )
                )),
                Map.of());
        assertCreated(secondFanoutDefinition);
        var secondFanoutDefinitionId = text(secondFanoutDefinition.body(), "/data/definitionId");
        assertOk(client.postWithCsrf(
                flowRoot + "/definitions/" + secondFanoutDefinitionId + ":publish",
                "{}",
                Map.of()));

        var rolledBackFanoutRecord = client.postWithCsrf(
                runtimeRoot + "/records",
                json(Map.of(
                        "schemaVersionId", flowRecordSchemaVersionId,
                        "title", "Atomic fanout rollback",
                        "values", Map.of("route", "fanout")
                )),
                Map.of("Idempotency-Key", key()));
        assertCreated(rolledBackFanoutRecord);
        var rolledBackFanoutRecordId = text(
                rolledBackFanoutRecord.body(), "/data/recordId");
        jdbcTemplate.execute("""
                CREATE TRIGGER test_fail_record_flow_fanout_item
                BEFORE INSERT ON un_module_record_flow_state_item
                FOR EACH ROW
                SIGNAL SQLSTATE '23000'
                    SET MYSQL_ERRNO = 1062,
                        MESSAGE_TEXT = 'forced fanout projection conflict'
                """);
        try {
            assertError(client.postWithCsrf(
                            runtimeRoot + "/records/" + rolledBackFanoutRecordId + ":activate",
                            json(Map.of("expectedVersion", 0)),
                            Map.of("Idempotency-Key", key())),
                    409,
                    "RECORD_FLOW_STATE_CONFLICT");
        } finally {
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS test_fail_record_flow_fanout_item");
        }
        var rolledBackFanoutEventKey =
                "record:%s:%s:%s:%s:1:RECORD_ACTIVATED".formatted(
                        systemId,
                        firstTenantId,
                        FLOW_RECORD_MODULE_CODE,
                        rolledBackFanoutRecordId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_flow_instance "
                        + "WHERE system_id=? AND tenant_id=? AND record_id=?",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(firstTenantId),
                Long.parseLong(rolledBackFanoutRecordId))).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_record_flow_state "
                        + "WHERE system_id=? AND tenant_id=? AND record_id=?",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(firstTenantId),
                Long.parseLong(rolledBackFanoutRecordId))).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_record_flow_state_item "
                        + "WHERE system_id=? AND tenant_id=? AND record_id=?",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(firstTenantId),
                Long.parseLong(rolledBackFanoutRecordId))).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_flow_trigger_dispatch "
                        + "WHERE system_id=? AND tenant_id=? AND event_key=?",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(firstTenantId),
                rolledBackFanoutEventKey)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_flow_trigger_dispatch_instance "
                        + "WHERE system_id=? AND tenant_id=? AND event_key=?",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(firstTenantId),
                rolledBackFanoutEventKey)).isZero();
        var rolledBackFanoutReloaded = client.get(
                runtimeRoot + "/records/" + rolledBackFanoutRecordId);
        assertOk(rolledBackFanoutReloaded);
        assertThat(text(rolledBackFanoutReloaded.body(), "/data/status"))
                .isEqualTo("DRAFT");
        assertThat(rolledBackFanoutReloaded.body().at("/data/version").asLong()).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_record_history "
                        + "WHERE system_id=? AND tenant_id=? AND record_id=? "
                        + "AND action='RECORD_ACTIVATED'",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(firstTenantId),
                Long.parseLong(rolledBackFanoutRecordId))).isZero();

        var fanoutRecord = client.postWithCsrf(
                runtimeRoot + "/records",
                json(Map.of(
                        "schemaVersionId", flowRecordSchemaVersionId,
                        "title", "Two automatic approvals",
                        "values", Map.of("route", "fanout")
                )),
                Map.of("Idempotency-Key", key()));
        assertCreated(fanoutRecord);
        var fanoutRecordId = text(fanoutRecord.body(), "/data/recordId");
        var fanoutActivationKey = key();
        var fanoutActivation = client.postWithCsrf(
                runtimeRoot + "/records/" + fanoutRecordId + ":activate",
                json(Map.of("expectedVersion", 0)),
                Map.of("Idempotency-Key", fanoutActivationKey));
        assertOk(fanoutActivation);

        var fanoutStates = client.get(
                runtimeRoot + "/records/" + fanoutRecordId + "/flow-states");
        assertOk(fanoutStates);
        assertThat(fanoutStates.body().at("/data")).hasSize(2);
        assertThat(text(fanoutStates.body(), "/data/0/status")).isEqualTo("PENDING");
        assertThat(text(fanoutStates.body(), "/data/1/status")).isEqualTo("PENDING");
        var firstFanoutInstanceId = text(fanoutStates.body(), "/data/0/instanceId");
        var secondFanoutInstanceId = text(fanoutStates.body(), "/data/1/instanceId");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_record_flow_state_item "
                        + "WHERE system_id=? AND tenant_id=? AND instance_id=? "
                        + "AND status_field_code='approval_status' "
                        + "AND status_approved_value=?",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(firstTenantId),
                Long.parseLong(secondFanoutInstanceId),
                Long.parseLong(flowRecordSchema.rejectedOptionId()))).isOne();
        var firstFanoutInstance = client.get(
                flowRoot + "/instances/" + firstFanoutInstanceId);
        assertOk(firstFanoutInstance);
        assertThat(text(firstFanoutInstance.body(), "/data/definitionId"))
                .isEqualTo(firstFanoutDefinitionId);
        var secondFanoutInstance = client.get(
                flowRoot + "/instances/" + secondFanoutInstanceId);
        assertOk(secondFanoutInstance);
        assertThat(text(secondFanoutInstance.body(), "/data/definitionId"))
                .isEqualTo(secondFanoutDefinitionId);
        var fanoutEventKey = "record:%s:%s:%s:%s:1:RECORD_ACTIVATED".formatted(
                systemId, firstTenantId, FLOW_RECORD_MODULE_CODE, fanoutRecordId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_flow_trigger_dispatch_instance "
                        + "WHERE system_id=? AND tenant_id=? AND event_key=?",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(firstTenantId),
                fanoutEventKey)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForList(
                        "SELECT instance_id FROM un_flow_trigger_dispatch_instance "
                                + "WHERE system_id=? AND tenant_id=? AND event_key=? ORDER BY ordinal",
                        Long.class,
                        Long.parseLong(systemId),
                        Long.parseLong(firstTenantId),
                        fanoutEventKey)
                .stream().map(String::valueOf).toList())
                .containsExactly(firstFanoutInstanceId, secondFanoutInstanceId);

        var fanoutActivationReplay = client.postWithCsrf(
                runtimeRoot + "/records/" + fanoutRecordId + ":activate",
                json(Map.of("expectedVersion", 0)),
                Map.of("Idempotency-Key", fanoutActivationKey));
        assertOk(fanoutActivationReplay);
        assertThat(fanoutActivationReplay.body().at("/data"))
                .isEqualTo(fanoutActivation.body().at("/data"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_flow_instance WHERE system_id=? AND tenant_id=? AND record_id=?",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(firstTenantId),
                Long.parseLong(fanoutRecordId))).isEqualTo(2);

        assertOk(client.postWithCsrf(
                flowRoot + "/instances/" + firstFanoutInstanceId + ":approve",
                json(Map.of("comment", "First automatic approval")),
                Map.of()));
        var firstMappedFanoutRecord = client.get(
                runtimeRoot + "/records/" + fanoutRecordId);
        assertOk(firstMappedFanoutRecord);
        assertThat(fieldValue(firstMappedFanoutRecord.body(), "approval_status"))
                .isEqualTo(flowRecordSchema.approvedOptionId());
        assertThat(firstMappedFanoutRecord.body().at("/data/version").asLong()).isEqualTo(2);
        assertOk(client.postWithCsrf(
                flowRoot + "/instances/" + secondFanoutInstanceId + ":approve",
                json(Map.of("comment", "Second automatic approval")),
                Map.of()));
        var approvedFanoutStates = client.get(
                runtimeRoot + "/records/" + fanoutRecordId + "/flow-states");
        assertOk(approvedFanoutStates);
        assertThat(itemValues(approvedFanoutStates.body().at("/data"), "status"))
                .containsExactly("APPROVED", "APPROVED");
        var secondMappedFanoutRecord = client.get(
                runtimeRoot + "/records/" + fanoutRecordId);
        assertOk(secondMappedFanoutRecord);
        assertThat(fieldValue(secondMappedFanoutRecord.body(), "approval_status"))
                .isEqualTo(flowRecordSchema.rejectedOptionId());
        assertThat(secondMappedFanoutRecord.body().at("/data/version").asLong()).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_record_history "
                        + "WHERE system_id=? AND tenant_id=? AND record_id=? "
                        + "AND action='FLOW_STATUS_MAPPED'",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(firstTenantId),
                Long.parseLong(fanoutRecordId))).isEqualTo(2);

        var competingNonexclusiveDefinition = client.postWithCsrf(
                flowRoot + "/definitions",
                json(Map.of(
                        "name", "Competing nonexclusive approval",
                        "approverId", memberId,
                        "triggerBinding", Map.of(
                                "moduleCode", FLOW_RECORD_MODULE_CODE,
                                "event", "RECORD_ACTIVATED",
                                "priority", 900,
                                "exclusive", false,
                                "conditions", List.of(Map.of(
                                        "fieldCode", "route",
                                        "operator", "EQ",
                                        "value", "exclusive"
                                ))
                        )
                )),
                Map.of());
        assertCreated(competingNonexclusiveDefinition);
        var competingNonexclusiveDefinitionId =
                text(competingNonexclusiveDefinition.body(), "/data/definitionId");
        assertOk(client.postWithCsrf(
                flowRoot + "/definitions/" + competingNonexclusiveDefinitionId + ":publish",
                "{}",
                Map.of()));

        var exclusiveWinnerDefinition = client.postWithCsrf(
                flowRoot + "/definitions",
                json(Map.of(
                        "name", "Exclusive winner approval",
                        "approverId", memberId,
                        "triggerBinding", Map.of(
                                "moduleCode", FLOW_RECORD_MODULE_CODE,
                                "event", "RECORD_ACTIVATED",
                                "priority", 10,
                                "exclusive", true,
                                "conditions", List.of(Map.of(
                                        "fieldCode", "route",
                                        "operator", "EQ",
                                        "value", "exclusive"
                                ))
                        )
                )),
                Map.of());
        assertCreated(exclusiveWinnerDefinition);
        var exclusiveWinnerDefinitionId = text(
                exclusiveWinnerDefinition.body(), "/data/definitionId");
        assertOk(client.postWithCsrf(
                flowRoot + "/definitions/" + exclusiveWinnerDefinitionId + ":publish",
                "{}",
                Map.of()));

        var exclusiveRecord = client.postWithCsrf(
                runtimeRoot + "/records",
                json(Map.of(
                        "schemaVersionId", flowRecordSchemaVersionId,
                        "title", "Exclusive automatic approval",
                        "values", Map.of("route", "exclusive")
                )),
                Map.of("Idempotency-Key", key()));
        assertCreated(exclusiveRecord);
        var exclusiveRecordId = text(exclusiveRecord.body(), "/data/recordId");
        assertOk(client.postWithCsrf(
                runtimeRoot + "/records/" + exclusiveRecordId + ":activate",
                json(Map.of("expectedVersion", 0)),
                Map.of("Idempotency-Key", key())));
        var exclusiveStates = client.get(
                runtimeRoot + "/records/" + exclusiveRecordId + "/flow-states");
        assertOk(exclusiveStates);
        assertThat(exclusiveStates.body().at("/data")).hasSize(1);
        var exclusiveInstanceId = text(exclusiveStates.body(), "/data/0/instanceId");
        var exclusiveInstance = client.get(
                flowRoot + "/instances/" + exclusiveInstanceId);
        assertOk(exclusiveInstance);
        assertThat(text(exclusiveInstance.body(), "/data/definitionId"))
                .isEqualTo(exclusiveWinnerDefinitionId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_flow_instance WHERE system_id=? AND tenant_id=? "
                        + "AND definition_id=? AND record_id=?",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(firstTenantId),
                Long.parseLong(competingNonexclusiveDefinitionId),
                Long.parseLong(exclusiveRecordId))).isZero();

        exerciseExpandedRecordEvents(
                client,
                flowRoot,
                runtimeRoot,
                systemId,
                firstTenantId,
                memberId,
                flowRecordSchema,
                definitionId,
                startOnlyMemberId,
                startOnlyClient,
                startableCatalogPath,
                hiddenDraftId,
                manualReplayRecordId,
                manualRollbackRecordId,
                recordDefinitionId,
                firstTenantId);
        exercisePeriodicTrigger(
                client,
                flowRoot,
                systemId,
                firstTenantId,
                memberId,
                secondMemberId);
        exerciseVersionHistoryRestore(
                client,
                secondClient,
                flowRoot,
                memberId,
                secondMemberId);
        exerciseDraftCheckSimulation(
                client,
                flowRoot,
                systemId,
                firstTenantId,
                memberId,
                secondMemberId);
        exerciseConditionalGateway(
                client,
                flowRoot,
                systemId,
                firstTenantId,
                memberId,
                secondMemberId);
        exerciseApprovalModes(
                client,
                secondClient,
                flowRoot,
                systemId,
                firstTenantId,
                memberId,
                secondMemberId);
        exerciseQuorumApproval(
                client,
                secondClient,
                startOnlyClient,
                flowRoot,
                systemId,
                firstTenantId,
                memberId,
                secondMemberId,
                startOnlyMemberId,
                startOnlyRoleId,
                text(publishedStartOnlyRole.body(), "/data/version"),
                allDataScopeId);
        exerciseApprovalDeadlines(
                client,
                flowRoot,
                systemId,
                firstTenantId,
                memberId);
        exerciseDecisionCommentRules(
                client,
                flowRoot,
                systemId,
                firstTenantId,
                memberId);
        exerciseDelegationProxy(
                client,
                secondClient,
                startOnlyClient,
                flowRoot,
                systemId,
                firstTenantId,
                memberId,
                secondMemberId,
                startOnlyMemberId);
        exerciseOrderedStagesWithPreviousHandler(
                client,
                secondClient,
                startOnlyClient,
                flowRoot,
                systemId,
                firstTenantId,
                memberId,
                secondMemberId,
                startOnlyMemberId);
        exerciseParallelBranches(
                client,
                secondClient,
                flowRoot,
                systemId,
                firstTenantId,
                memberId,
                secondMemberId);
        exerciseInclusiveGateway(
                client,
                secondClient,
                flowRoot,
                systemId,
                firstTenantId,
                memberId,
                secondMemberId);
        exerciseInclusiveBranchOrderedStages(
                client,
                secondClient,
                flowRoot,
                systemId,
                firstTenantId,
                memberId,
                secondMemberId);
        exerciseDecisionEvidenceAndTemplates(
                client,
                secondClient,
                flowRoot,
                systemId,
                firstTenantId,
                secondTenantId,
                memberId,
                secondMemberId);
        exerciseCompletionExecutions(
                client,
                flowRoot,
                systemId,
                firstTenantId,
                secondTenantId,
                memberId);
        exerciseSubflowCompletion(
                client,
                flowRoot,
                systemId,
                firstTenantId,
                memberId);
        exerciseParallelCompletionJoin(
                client,
                flowRoot,
                systemId,
                firstTenantId,
                memberId);
        exerciseCompletionCompensation(
                client,
                flowRoot,
                systemId,
                firstTenantId,
                memberId);
        exerciseDynamicApproverSources(
                client,
                secondClient,
                startOnlyClient,
                flowRoot,
                systemId,
                firstTenantId,
                memberId,
                secondMemberId,
                startOnlyMemberId,
                secondApproverRoleId);
    }

    private void exercisePlatformAiAgent(
            TestClient client,
            String systemId,
            String systemCode
    ) throws Exception {
        var refreshed = client.postWithCsrf("/api/v1/auth/refresh", "{}", Map.of());
        assertOk(refreshed);
        assertThat(refreshed.body().at("/data/context/type").asText())
                .isEqualTo("PLATFORM");
        assertThat(refreshed.body().at("/data/context/permissions"))
                .extracting(JsonNode::asText)
                .contains(
                        "platform.runtime.access",
                        "platform.ai.agent.use",
                        "platform.ai.policy.manage",
                        "platform.task.read",
                        "platform.task.create",
                        "platform.task.manage");

        var adminRoot = "/api/v1/platform/admin/ai";
        var runtimeRoot = "/api/v1/platform/ai";
        assertError(new TestClient().get(runtimeRoot + "/capability"),
                401, "AUTH_REQUIRED");

        var createdProvider = client.postWithCsrf(
                adminRoot + "/providers",
                json(Map.of(
                        "code", "platform_journey_provider",
                        "name", "Platform journey provider",
                        "baseUrl", aiProviderFixture.baseUrl(),
                        "model", "platform-authorized-systems-model",
                        "secretRef", OPENAPI_SECRET_FILE.toUri().toString(),
                        "timeoutSeconds", 5,
                        "enabled", true)),
                Map.of("Idempotency-Key", key()));
        assertOk(createdProvider);
        var providerId = text(createdProvider.body(), "/data/id");
        assertThat(createdProvider.body().at("/data/id").isTextual()).isTrue();
        assertThat(text(createdProvider.body(), "/data/secretRef"))
                .startsWith("file:")
                .doesNotContain(OPENAPI_RECORD_SECRET);

        var savedPolicy = client.putWithCsrf(
                adminRoot + "/policy",
                json(Map.ofEntries(
                        Map.entry("expectedVersion", 0),
                        Map.entry("providerId", providerId),
                        Map.entry("allowedOperations", List.of(
                                "AUTHORIZED_SYSTEMS_QUERY",
                                "SYSTEM_SWITCH_GUIDANCE",
                                "PLATFORM_TASK_DRAFT",
                                "PLATFORM_OPERATIONS_QUERY")),
                        Map.entry("maxSystems", 100),
                        Map.entry("dailyRequestQuota", 100),
                        Map.entry("dailyTokenQuota", 1_000_000),
                        Map.entry("maxConcurrency", 2),
                        Map.entry("strictRedaction", true),
                        Map.entry("dataResidency", "PLATFORM_METADATA_ONLY"),
                        Map.entry("promptVersion", "platform:v1"),
                        Map.entry("enabled", true))),
                Map.of());
        assertOk(savedPolicy);
        var draftVersion = savedPolicy.body().at("/data/draftVersion").asLong();
        assertThat(draftVersion).isOne();

        var checked = client.postWithCsrf(
                adminRoot + "/policy:check", "{}", Map.of());
        assertOk(checked);
        assertThat(text(checked.body(), "/data/status")).isEqualTo("PASSED");
        assertThat(checked.body().at("/data/issues")).isEmpty();

        var publishKey = key();
        var publishBody = json(Map.of("expectedVersion", draftVersion));
        var published = client.postWithCsrf(
                adminRoot + "/policy:publish", publishBody,
                Map.of("Idempotency-Key", publishKey));
        assertOk(published);
        var activeVersionId = text(published.body(), "/data/activeVersionId");
        assertThat(activeVersionId).isNotBlank();
        var replay = client.postWithCsrf(
                adminRoot + "/policy:publish", publishBody,
                Map.of("Idempotency-Key", publishKey));
        assertOk(replay);
        assertThat(text(replay.body(), "/data/activeVersionId"))
                .isEqualTo(activeVersionId);

        var adminCapability = client.get(adminRoot + "/capability");
        assertOk(adminCapability);
        assertThat(adminCapability.body().at("/data/available").asBoolean()).isTrue();
        var runtimeCapability = client.get(runtimeRoot + "/capability");
        assertOk(runtimeCapability);
        assertThat(runtimeCapability.body().at("/data/available").asBoolean()).isTrue();

        var rawTitle = "PLATFORM_AI_RAW_TITLE_78";
        var createdSession = client.postWithCsrf(
                runtimeRoot + "/sessions",
                json(Map.of("title", rawTitle)),
                Map.of("Idempotency-Key", key()));
        assertOk(createdSession);
        var sessionId = text(createdSession.body(), "/data/id");
        assertThat(text(createdSession.body(), "/data/title"))
                .doesNotContain(rawTitle);

        var rawQuery = "PLATFORM_AUTHORIZED_SYSTEMS_78 Which systems may I enter?";
        var submitted = client.postWithCsrf(
                runtimeRoot + "/sessions/" + sessionId + "/messages",
                json(Map.of("content", rawQuery)),
                Map.of("Idempotency-Key", key()));
        assertOk(submitted);
        assertThat(text(submitted.body(), "/data/status")).isEqualTo("SUCCEEDED");
        assertThat(text(submitted.body(), "/data/operation"))
                .isEqualTo("AUTHORIZED_SYSTEMS_QUERY");
        assertThat(text(submitted.body(), "/data/answer"))
                .contains("current authorized-system directory")
                .contains("AI_FIXTURE_SUMMARY_75");
        var authorized = StreamSupport.stream(
                        submitted.body().at("/data/systems").spliterator(), false)
                .filter(value -> systemId.equals(value.path("systemId").asText()))
                .findFirst().orElseThrow();
        assertThat(authorized.path("systemCode").asText()).isEqualTo(systemCode);
        assertThat(authorized.path("status").asText())
                .isIn("ACTIVE", "INITIALIZING");
        assertThat(authorized.path("membershipState").asText()).isEqualTo("ACTIVE");
        assertThat(authorized.path("accessState").asText()).isEqualTo("AUTHORIZED");
        assertThat(authorized.path("switchTarget").asText())
                .isEqualTo("/api/v1/context/systems/" + systemId + ":switch");

        var businessRequest = "PLATFORM_BUSINESS_RECORD_78=" + systemCode
                + " Show its first purchase order";
        var guided = client.postWithCsrf(
                runtimeRoot + "/sessions/" + sessionId + "/messages",
                json(Map.of("content", businessRequest)),
                Map.of("Idempotency-Key", key()));
        assertOk(guided);
        assertThat(text(guided.body(), "/data/status")).isEqualTo("SUCCEEDED");
        assertThat(text(guided.body(), "/data/operation"))
                .isEqualTo("SYSTEM_SWITCH_GUIDANCE");
        assertThat(text(guided.body(), "/data/guidance/requestedSystemCode"))
                .isEqualTo(systemCode);
        assertThat(text(guided.body(), "/data/guidance/switchTarget"))
                .isEqualTo("/api/v1/context/systems/" + systemId + ":switch");
        assertThat(text(guided.body(), "/data/answer"))
                .contains("Switch to").contains("before asking");

        var rootAccountId = jdbcTemplate.queryForObject(
                "SELECT id FROM un_plat_account WHERE username=?",
                Long.class, ROOT_USERNAME);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_platform_task WHERE account_id=?",
                Long.class, rootAccountId)).isZero();

        var taskDraft = client.postWithCsrf(
                runtimeRoot + "/sessions/" + sessionId + "/messages",
                json(Map.of("content",
                        "PLATFORM_TASK_DRAFT_79 create a quota follow-up")),
                Map.of("Idempotency-Key", key()));
        assertOk(taskDraft);
        assertThat(text(taskDraft.body(), "/data/operation"))
                .isEqualTo("PLATFORM_TASK_DRAFT");
        assertThat(text(taskDraft.body(), "/data/proposal/state"))
                .isEqualTo("PENDING");
        assertThat(text(taskDraft.body(), "/data/proposal/preview/title"))
                .isEqualTo("Review platform quota 79");
        assertThat(taskDraft.body().at("/data/proposal/preview/selfAssigned")
                .asBoolean()).isTrue();
        var proposalId = text(taskDraft.body(), "/data/proposal/id");
        var proposalRevision = taskDraft.body()
                .at("/data/proposal/revision").asLong();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_platform_task WHERE account_id=?",
                Long.class, rootAccountId)).isZero();

        var storedDraft = client.get(runtimeRoot + "/sessions/" + sessionId
                + "/proposals/" + proposalId);
        assertOk(storedDraft);
        assertThat(text(storedDraft.body(), "/data/state")).isEqualTo("PENDING");
        assertThat(text(storedDraft.body(), "/data/preview/title"))
                .doesNotContain("Review platform quota 79");

        var confirmationKey = key();
        var confirmationBody = json(Map.of(
                "expectedRevision", proposalRevision));
        var confirmed = client.postWithCsrf(
                runtimeRoot + "/sessions/" + sessionId + "/proposals/"
                        + proposalId + "/confirm",
                confirmationBody, Map.of("Idempotency-Key", confirmationKey));
        assertOk(confirmed);
        assertThat(text(confirmed.body(), "/data/state")).isEqualTo("SUCCEEDED");
        var taskId = text(confirmed.body(), "/data/result/taskId");
        assertThat(text(confirmed.body(), "/data/result/status")).isEqualTo("OPEN");
        assertThat(text(confirmed.body(), "/data/result/source")).isEqualTo("AGENT");
        var confirmedReplay = client.postWithCsrf(
                runtimeRoot + "/sessions/" + sessionId + "/proposals/"
                        + proposalId + "/confirm",
                confirmationBody, Map.of("Idempotency-Key", confirmationKey));
        assertOk(confirmedReplay);
        assertThat(text(confirmedReplay.body(), "/data/result/taskId"))
                .isEqualTo(taskId);
        assertError(client.postWithCsrf(
                        runtimeRoot + "/sessions/" + sessionId + "/proposals/"
                                + proposalId + "/confirm",
                        json(Map.of("expectedRevision", proposalRevision + 1)),
                        Map.of("Idempotency-Key", confirmationKey)),
                409, "PLATFORM_AI_TASK_REPLAY_CONFLICT");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_platform_task "
                        + "WHERE account_id=? AND id=? AND status='OPEN' AND source='AGENT'",
                Long.class, rootAccountId, Long.parseLong(taskId))).isEqualTo(1);

        var platformTaskRoot = "/api/v1/platform/tasks";
        var openTasks = client.get(
                platformTaskRoot + "?status=OPEN&page=1&size=20");
        assertOk(openTasks);
        assertThat(openTasks.body().at("/data/page").asInt()).isOne();
        assertThat(openTasks.body().at("/data/size").asInt()).isEqualTo(20);
        assertThat(openTasks.body().at("/data/total").asLong()).isOne();
        var openTask = item(
                openTasks.body().at("/data/items"), "taskId", taskId);
        assertSafePlatformTask(openTask);
        assertThat(text(openTask, "/status")).isEqualTo("OPEN");
        assertThat(openTask.path("version").asLong()).isZero();
        assertThat(openTask.hasNonNull("completedAt")).isFalse();
        assertThat(openTask.hasNonNull("cancelledAt")).isFalse();
        assertThat(text(openTask, "/updatedAt"))
                .isEqualTo(text(openTask, "/createdAt"));
        var createdTaskAt = Instant.parse(text(openTask, "/createdAt"));

        var completedTask = client.postWithCsrf(
                platformTaskRoot + "/" + taskId + ":complete",
                json(Map.of("version", 0)), Map.of());
        assertOk(completedTask);
        assertSafePlatformTask(completedTask.body().at("/data"));
        assertThat(text(completedTask.body(), "/data/status"))
                .isEqualTo("COMPLETED");
        assertThat(completedTask.body().at("/data/version").asLong())
                .isEqualTo(1);
        assertThat(completedTask.body().at("/data/completedAt").isTextual())
                .isTrue();
        assertThat(completedTask.body().at("/data").hasNonNull("cancelledAt"))
                .isFalse();
        var completedUpdatedAt = Instant.parse(
                text(completedTask.body(), "/data/updatedAt"));
        assertThat(completedUpdatedAt).isAfter(createdTaskAt);

        assertError(client.postWithCsrf(
                        platformTaskRoot + "/" + taskId + ":complete",
                        json(Map.of("version", 0)), Map.of()),
                409, "PLATFORM_TASK_VERSION_CONFLICT");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_platform_task "
                        + "WHERE account_id=? AND id=? AND status='COMPLETED' "
                        + "AND version=1 AND completed_at IS NOT NULL "
                        + "AND cancelled_at IS NULL",
                Long.class, rootAccountId, Long.parseLong(taskId))).isEqualTo(1);

        var reopenedTask = client.postWithCsrf(
                platformTaskRoot + "/" + taskId + ":reopen",
                json(Map.of("version", 1)), Map.of());
        assertOk(reopenedTask);
        assertThat(text(reopenedTask.body(), "/data/status")).isEqualTo("OPEN");
        assertThat(reopenedTask.body().at("/data/version").asLong())
                .isEqualTo(2);
        assertThat(reopenedTask.body().at("/data").hasNonNull("completedAt"))
                .isFalse();
        assertThat(reopenedTask.body().at("/data").hasNonNull("cancelledAt"))
                .isFalse();
        var reopenedUpdatedAt = Instant.parse(
                text(reopenedTask.body(), "/data/updatedAt"));
        assertThat(reopenedUpdatedAt).isAfter(completedUpdatedAt);

        var cancelledTask = client.postWithCsrf(
                platformTaskRoot + "/" + taskId + ":cancel",
                json(Map.of("version", 2)), Map.of());
        assertOk(cancelledTask);
        assertThat(text(cancelledTask.body(), "/data/status"))
                .isEqualTo("CANCELLED");
        assertThat(cancelledTask.body().at("/data/version").asLong())
                .isEqualTo(3);
        assertThat(cancelledTask.body().at("/data").hasNonNull("completedAt"))
                .isFalse();
        assertThat(cancelledTask.body().at("/data/cancelledAt").isTextual())
                .isTrue();
        var cancelledUpdatedAt = Instant.parse(
                text(cancelledTask.body(), "/data/updatedAt"));
        assertThat(cancelledUpdatedAt).isAfter(reopenedUpdatedAt);

        var reopenedCancelledTask = client.postWithCsrf(
                platformTaskRoot + "/" + taskId + ":reopen",
                json(Map.of("version", 3)), Map.of());
        assertOk(reopenedCancelledTask);
        assertThat(text(reopenedCancelledTask.body(), "/data/status"))
                .isEqualTo("OPEN");
        assertThat(reopenedCancelledTask.body().at("/data/version").asLong())
                .isEqualTo(4);
        assertThat(reopenedCancelledTask.body().at("/data")
                .hasNonNull("completedAt")).isFalse();
        assertThat(reopenedCancelledTask.body().at("/data")
                .hasNonNull("cancelledAt")).isFalse();
        var finalUpdatedAt = Instant.parse(
                text(reopenedCancelledTask.body(), "/data/updatedAt"));
        assertThat(finalUpdatedAt).isAfter(cancelledUpdatedAt);

        jdbcTemplate.update(
                "UPDATE un_plat_permission SET status='DISABLED',"
                        + "updated_at=UTC_TIMESTAMP(3),version=version+1 "
                        + "WHERE scope_type='PLATFORM' AND scope_key=0 "
                        + "AND permission_code='platform.task.manage'");
        authzEpochService.bumpPlatform(rootAccountId);
        try {
            var refreshedWithoutManage = client.postWithCsrf(
                    "/api/v1/auth/refresh", "{}", Map.of());
            assertOk(refreshedWithoutManage);
            assertThat(refreshedWithoutManage.body().at(
                    "/data/context/permissions"))
                    .extracting(JsonNode::asText)
                    .doesNotContain("platform.task.manage");
            assertError(client.postWithCsrf(
                            platformTaskRoot + "/" + taskId + ":complete",
                            json(Map.of("version", 4)), Map.of()),
                    403, "PERMISSION_DENIED");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM un_platform_task "
                            + "WHERE account_id=? AND id=? AND status='OPEN' "
                            + "AND version=4 AND completed_at IS NULL "
                            + "AND cancelled_at IS NULL",
                    Long.class, rootAccountId, Long.parseLong(taskId)))
                    .isEqualTo(1);
        } finally {
            jdbcTemplate.update(
                    "UPDATE un_plat_permission SET status='ACTIVE',"
                            + "updated_at=UTC_TIMESTAMP(3),version=version+1 "
                            + "WHERE scope_type='PLATFORM' AND scope_key=0 "
                            + "AND permission_code='platform.task.manage'");
            authzEpochService.bumpPlatform(rootAccountId);
            assertOk(client.postWithCsrf(
                    "/api/v1/auth/refresh", "{}", Map.of()));
        }

        var foreignUsername = "platform_task_foreign_"
                + Long.toUnsignedString(System.nanoTime(), 36);
        var foreignRegistration = new TestClient().post(
                "/api/v1/auth/register", json(Map.of(
                        "username", foreignUsername,
                        "displayName", "Platform task foreign account",
                        "password", "Platform-Task-Foreign-Password-91!",
                        "systemName", "Platform task foreign home",
                        "systemCode", "ptf_"
                                + Long.toUnsignedString(System.nanoTime(), 36))),
                Map.of("Idempotency-Key", key()));
        assertOk(foreignRegistration);
        var foreignAccountId = jdbcTemplate.queryForObject(
                "SELECT id FROM un_plat_account WHERE username=?",
                Long.class, foreignUsername);
        var foreignTaskId = 8_910_000_000_000_091L;
        jdbcTemplate.update("""
                INSERT INTO un_platform_task(
                  id,account_id,title,description,due_at,priority,status,source,
                  authorization_epoch,payload_hash,idempotency_key,request_id,
                  trace_id,created_at,created_by,updated_at,completed_at,
                  cancelled_at,version)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP(6),?,
                       UTC_TIMESTAMP(6),NULL,NULL,0)
                """,
                foreignTaskId, foreignAccountId, "Foreign account task", null,
                null, "NORMAL", "OPEN", "AGENT", 1, "c".repeat(64),
                "platform-task-foreign-91", "platform-task-foreign-request-91",
                "platform-task-foreign-trace-91", foreignAccountId);
        assertError(client.postWithCsrf(
                        platformTaskRoot + "/" + foreignTaskId + ":complete",
                        json(Map.of("version", 0)), Map.of()),
                404, "PLATFORM_TASK_NOT_FOUND");
        var allOwnTasks = client.get(
                platformTaskRoot + "?status=ALL&page=1&size=100");
        assertOk(allOwnTasks);
        assertThat(allOwnTasks.body().at("/data/items"))
                .noneMatch(value -> Long.toString(foreignTaskId).equals(
                        value.path("taskId").asText()));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_platform_task "
                        + "WHERE account_id=? AND id=? AND status='OPEN' "
                        + "AND version=0 AND completed_at IS NULL "
                        + "AND cancelled_at IS NULL",
                Long.class, foreignAccountId, foreignTaskId)).isEqualTo(1);

        var deniedDraft = client.postWithCsrf(
                runtimeRoot + "/sessions/" + sessionId + "/messages",
                json(Map.of("content",
                        "PLATFORM_TASK_DRAFT_79 permission revocation proof")),
                Map.of("Idempotency-Key", key()));
        assertOk(deniedDraft);
        var deniedProposalId = text(deniedDraft.body(), "/data/proposal/id");
        var deniedRevision = deniedDraft.body()
                .at("/data/proposal/revision").asLong();
        jdbcTemplate.update(
                "UPDATE un_plat_permission SET status='DISABLED',"
                        + "updated_at=UTC_TIMESTAMP(3),version=version+1 "
                        + "WHERE scope_type='PLATFORM' AND scope_key=0 "
                        + "AND permission_code='platform.task.create'");
        try {
            var deniedConfirmation = client.postWithCsrf(
                    runtimeRoot + "/sessions/" + sessionId + "/proposals/"
                            + deniedProposalId + "/confirm",
                    json(Map.of("expectedRevision", deniedRevision)),
                    Map.of("Idempotency-Key", key()));
            assertOk(deniedConfirmation);
            assertThat(text(deniedConfirmation.body(), "/data/state"))
                    .isEqualTo("FAILED");
            assertThat(text(deniedConfirmation.body(), "/data/errorCode"))
                    .isEqualTo("PLATFORM_TASK_PERMISSION_DENIED");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM un_platform_task WHERE account_id=?",
                    Long.class, rootAccountId)).isEqualTo(1);
        } finally {
            jdbcTemplate.update(
                    "UPDATE un_plat_permission SET status='ACTIVE',"
                            + "updated_at=UTC_TIMESTAMP(3),version=version+1 "
                            + "WHERE scope_type='PLATFORM' AND scope_key=0 "
                            + "AND permission_code='platform.task.create'");
            authzEpochService.bumpPlatform(rootAccountId);
            assertOk(client.postWithCsrf(
                    "/api/v1/auth/refresh", "{}", Map.of()));
        }

        var personalTasks = client.postWithCsrf(
                runtimeRoot + "/sessions/" + sessionId + "/messages",
                json(Map.of("content", "PLATFORM_OPS_TASKS_80")),
                Map.of("Idempotency-Key", key()));
        assertOk(personalTasks);
        assertThat(text(personalTasks.body(), "/data/status"))
                .isEqualTo("SUCCEEDED");
        assertThat(text(personalTasks.body(), "/data/operations/queryKind"))
                .isEqualTo("PERSONAL_TASKS");
        assertThat(personalTasks.body().at("/data/operations/personalTasks"))
                .anyMatch(value -> taskId.equals(value.path("taskId").asText())
                        && "Review platform quota 79".equals(
                        value.path("title").asText()))
                .allMatch(value -> !value.has("accountId")
                        && !value.has("systemId") && !value.has("description"));

        var quota = client.postWithCsrf(
                runtimeRoot + "/sessions/" + sessionId + "/messages",
                json(Map.of("content", "PLATFORM_OPS_QUOTA_80")),
                Map.of("Idempotency-Key", key()));
        assertOk(quota);
        assertThat(text(quota.body(), "/data/operations/queryKind"))
                .isEqualTo("AI_QUOTA");
        assertThat(quota.body().at("/data/operations/quota/requestLimit").asInt())
                .isEqualTo(100);
        assertThat(quota.body().at("/data/operations/quota/tokenLimit").asLong())
                .isEqualTo(1_000_000L);
        assertThat(quota.body().at(
                "/data/operations/quota/remainingRequests").asInt())
                .isBetween(0, 100);

        var health = client.postWithCsrf(
                runtimeRoot + "/sessions/" + sessionId + "/messages",
                json(Map.of("content", "PLATFORM_OPS_HEALTH_80")),
                Map.of("Idempotency-Key", key()));
        assertOk(health);
        assertThat(text(health.body(), "/data/operations/queryKind"))
                .isEqualTo("SERVICE_HEALTH");
        assertThat(health.body().at(
                "/data/operations/serviceHealth/components"))
                .hasSize(4)
                .allMatch(value -> value.hasNonNull("component")
                        && value.hasNonNull("status")
                        && !value.has("details") && !value.has("host")
                        && !value.has("error"));

        var activity = client.postWithCsrf(
                runtimeRoot + "/sessions/" + sessionId + "/messages",
                json(Map.of("content", "PLATFORM_OPS_ACTIVITY_80")),
                Map.of("Idempotency-Key", key()));
        assertOk(activity);
        assertThat(text(activity.body(), "/data/operations/queryKind"))
                .isEqualTo("AGENT_ACTIVITY");
        assertThat(activity.body().at("/data/operations/agentActivity"))
                .isNotEmpty()
                .allMatch(value -> value.hasNonNull("event")
                        && value.hasNonNull("time")
                        && value.hasNonNull("resultCode")
                        && !value.has("prompt") && !value.has("response")
                        && !value.has("sealedCommand"));

        jdbcTemplate.update(
                "UPDATE un_plat_permission SET status='DISABLED',"
                        + "updated_at=UTC_TIMESTAMP(3),version=version+1 "
                        + "WHERE scope_type='PLATFORM' AND scope_key=0 "
                        + "AND permission_code='platform.audit.view'");
        try {
            var deniedHealth = client.postWithCsrf(
                    runtimeRoot + "/sessions/" + sessionId + "/messages",
                    json(Map.of("content", "PLATFORM_OPS_HEALTH_80 denied")),
                    Map.of("Idempotency-Key", key()));
            assertOk(deniedHealth);
            assertThat(text(deniedHealth.body(), "/data/status"))
                    .isEqualTo("FAILED");
            assertThat(text(deniedHealth.body(), "/data/errorCode"))
                    .isEqualTo("PLATFORM_OPERATIONS_PERMISSION_DENIED");
        } finally {
            jdbcTemplate.update(
                    "UPDATE un_plat_permission SET status='ACTIVE',"
                            + "updated_at=UTC_TIMESTAMP(3),version=version+1 "
                            + "WHERE scope_type='PLATFORM' AND scope_key=0 "
                            + "AND permission_code='platform.audit.view'");
            authzEpochService.bumpPlatform(rootAccountId);
            assertOk(client.postWithCsrf(
                    "/api/v1/auth/refresh", "{}", Map.of()));
        }

        var system = Long.parseLong(systemId);
        try {
            assertThat(jdbcTemplate.update(
                    "UPDATE un_plat_member SET status='DISABLED',version=version+1 "
                            + "WHERE system_id=? AND account_id=? AND status='ACTIVE'",
                    system, rootAccountId)).isEqualTo(1);
            var afterRevocation = client.postWithCsrf(
                    runtimeRoot + "/sessions/" + sessionId + "/messages",
                    json(Map.of("content",
                            "PLATFORM_AUTHORIZED_SYSTEMS_78_AFTER_REVOCATION")),
                    Map.of("Idempotency-Key", key()));
            assertOk(afterRevocation);
            assertThat(afterRevocation.body().at("/data/systems"))
                    .noneMatch(value -> systemId.equals(
                            value.path("systemId").asText()));
        } finally {
            jdbcTemplate.update(
                    "UPDATE un_plat_member SET status='ACTIVE',version=version+1 "
                            + "WHERE system_id=? AND account_id=? AND status='DISABLED'",
                    system, rootAccountId);
        }

        var detail = client.get(runtimeRoot + "/sessions/" + sessionId);
        assertOk(detail);
        assertThat(detail.body().at("/data/messages")).hasSize(19);
        assertThat(detail.body().at("/data/turns")).hasSize(10);
        assertThat(text(detail.body(), "/data/turns/0/evidence/type"))
                .isEqualTo("AUTHORIZED_SYSTEMS");
        assertThat(text(detail.body(), "/data/turns/1/evidence/type"))
                .isEqualTo("SWITCH_GUIDANCE");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_platform_ai_session WHERE account_id=?",
                Long.class, rootAccountId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_platform_ai_turn WHERE account_id=?",
                Long.class, rootAccountId)).isEqualTo(10);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_platform_ai_evidence WHERE account_id=?",
                Long.class, rootAccountId)).isEqualTo(7);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_ai_agent_session",
                Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_platform_ai_task_proposal "
                        + "WHERE account_id=?",
                Long.class, rootAccountId)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_platform_ai_task_proposal_attempt "
                        + "WHERE account_id=?",
                Long.class, rootAccountId)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema=DATABASE() "
                        + "AND table_name LIKE 'un_platform_ai_%' "
                        + "AND column_name IN ('system_id','tenant_id','member_id')",
                Long.class)).isZero();
        var stored = String.join("\n", jdbcTemplate.queryForList(
                "SELECT value FROM ("
                        + "SELECT title_summary AS value FROM un_platform_ai_session "
                        + "WHERE account_id=? AND id=? "
                        + "UNION ALL SELECT redacted_summary FROM un_platform_ai_message "
                        + "WHERE account_id=? AND session_id=? "
                        + "UNION ALL SELECT CONCAT_WS('|',request_summary,response_summary) "
                        + "FROM un_platform_ai_turn WHERE account_id=? AND session_id=?"
                        + ") persisted",
                String.class,
                rootAccountId, Long.parseLong(sessionId),
                rootAccountId, Long.parseLong(sessionId),
                rootAccountId, Long.parseLong(sessionId)));
        assertThat(stored)
                .doesNotContain(rawTitle)
                .doesNotContain(rawQuery)
                .doesNotContain(businessRequest)
                .doesNotContain(OPENAPI_RECORD_SECRET)
                .doesNotContain("purchase order");
        var safeEvidence = String.join("\n", jdbcTemplate.queryForList(
                "SELECT CAST(projection_json AS CHAR) FROM un_platform_ai_evidence "
                        + "WHERE account_id=? ORDER BY created_at,id",
                String.class, rootAccountId));
        assertThat(safeEvidence)
                .contains(systemCode, "switchTarget", "AUTHORIZED")
                .doesNotContain("tenantId", "moduleCode", "recordId", "fieldCode")
                .doesNotContain(rawQuery, businessRequest, OPENAPI_RECORD_SECRET);
    }

    private void exerciseAiAgent(
            TestClient client,
            String systemId,
            String tenantId,
            String isolatedTenantId,
            String secondMemberId,
            FlowRecordSchema flowRecordSchema,
            PublishedSource publishedSource,
            String publishedReportCode
    ) throws Exception {
        aiProviderFixture.configureMutationValues(
                flowRecordSchema.approvedOptionId(),
                flowRecordSchema.rejectedOptionId());
        var refreshed = client.postWithCsrf("/api/v1/auth/refresh", "{}", Map.of());
        assertOk(refreshed);
        assertThat(refreshed.body().at("/data/context/permissions"))
                .extracting(JsonNode::asText)
                .contains(
                        "ai.policy.manage",
                        "ai.agent.use",
                        "system.runtime.access",
                        "work.task.access",
                        "work.task.manage",
                        "work.project.manage",
                        "module." + FLOW_RECORD_MODULE_CODE + ".view",
                        "module." + FLOW_RECORD_MODULE_CODE + ".history.read",
                        "file.create",
                        "file.read",
                        "file.reference");

        var reportDate = LocalDate.now(ZoneOffset.UTC);
        var aiMetricsFrom = reportDate.minusDays(1);
        var aiMetricsTo = reportDate.plusDays(1);
        var aiMetricsProjectTitle = "AI_WORK_PROJECT_METRICS_TITLE_89";
        var aiMetricsProjectRoot = "/api/v1/systems/" + systemId
                + "/work/projects";
        var aiMetricsTaskRoot = "/api/v1/systems/" + systemId
                + "/work/tasks";
        var aiRootMemberId = text(refreshed.body(), "/data/context/memberId");
        var aiMetricsProject = client.postWithCsrf(
                aiMetricsProjectRoot,
                json(Map.of(
                        "title", aiMetricsProjectTitle,
                        "description", "Dedicated Agent metrics project")),
                Map.of("Idempotency-Key", key()));
        assertCreated(aiMetricsProject);
        var aiMetricsProjectId = text(aiMetricsProject.body(), "/data/id");
        var aiMetricsSecondOwner = client.postWithCsrf(
                aiMetricsProjectRoot + "/" + aiMetricsProjectId + "/members",
                json(Map.of(
                        "memberId", Long.parseLong(secondMemberId),
                        "role", "OWNER")),
                Map.of("Idempotency-Key", key()));
        assertCreated(aiMetricsSecondOwner);
        var overdueAt = Instant.now().minus(Duration.ofHours(2));
        var aiMetricsOpenTask = client.postWithCsrf(
                aiMetricsTaskRoot,
                json(Map.of(
                        "title", "AI metrics open task 89",
                        "assigneeMemberId", Long.parseLong(secondMemberId),
                        "projectId", Long.parseLong(aiMetricsProjectId),
                        "description", "Open project metric fact",
                        "dueAt", overdueAt.toString())),
                Map.of("Idempotency-Key", key()));
        assertCreated(aiMetricsOpenTask);
        var aiMetricsCompletedTask = client.postWithCsrf(
                aiMetricsTaskRoot,
                json(Map.of(
                        "title", "AI metrics completed task 89",
                        "assigneeMemberId", Long.parseLong(secondMemberId),
                        "projectId", Long.parseLong(aiMetricsProjectId),
                        "description", "Completed project metric fact",
                        "dueAt", Instant.now().plus(Duration.ofHours(2)).toString())),
                Map.of("Idempotency-Key", key()));
        assertCreated(aiMetricsCompletedTask);
        assertOk(client.postWithCsrf(
                aiMetricsTaskRoot + "/"
                        + text(aiMetricsCompletedTask.body(), "/data/id")
                        + ":complete",
                "{}", Map.of()));
        var aiMetricsOtherProject = client.postWithCsrf(
                aiMetricsProjectRoot,
                json(Map.of(
                        "title", "AI_WORK_PROJECT_METRICS_DECOY_89",
                        "description", "Must never enter project metrics")),
                Map.of("Idempotency-Key", key()));
        assertCreated(aiMetricsOtherProject);
        var aiMetricsDecoyTask = client.postWithCsrf(
                aiMetricsTaskRoot,
                json(Map.of(
                        "title", "AI metrics other-project task 89",
                        "assigneeMemberId", Long.parseLong(aiRootMemberId),
                        "projectId", Long.parseLong(text(
                                aiMetricsOtherProject.body(), "/data/id")),
                        "description", "Cross-project decoy",
                        "dueAt", overdueAt.toString())),
                Map.of("Idempotency-Key", key()));
        assertCreated(aiMetricsDecoyTask);
        var reportCompleted = "AI_WORK_REPORT_COMPLETED_83";
        var reportPlanned = "AI_WORK_REPORT_PLANNED_83";
        var reportBlockers = "AI_WORK_REPORT_BLOCKERS_83";
        var createdWorkReport = client.postWithCsrf(
                "/api/v1/systems/" + systemId + "/work/reports",
                json(Map.of(
                        "workDate", reportDate.toString(),
                        "completedWork", reportCompleted,
                        "plannedWork", reportPlanned,
                        "blockers", reportBlockers)),
                Map.of());
        assertCreated(createdWorkReport);
        var workReportId = text(createdWorkReport.body(), "/data/id");

        var adminRoot = "/api/v1/systems/" + systemId + "/admin/ai";
        var runtimeRoot = "/api/v1/systems/" + systemId + "/ai";
        var createdProvider = client.postWithCsrf(
                adminRoot + "/providers",
                json(Map.of(
                        "code", "journey_provider",
                        "name", "Journey OpenAI-compatible provider",
                        "baseUrl", aiProviderFixture.baseUrl(),
                        "model", "journey-read-model",
                        "secretRef", OPENAPI_SECRET_FILE.toUri().toString(),
                        "timeoutSeconds", 5,
                        "enabled", true)),
                Map.of("Idempotency-Key", key()));
        assertOk(createdProvider);
        var providerId = text(createdProvider.body(), "/data/id");
        assertThat(createdProvider.body().at("/data/id").isTextual()).isTrue();
        assertThat(text(createdProvider.body(), "/data/secretRef"))
                .startsWith("file:")
                .doesNotContain(OPENAPI_RECORD_SECRET);

        var savedPolicy = client.putWithCsrf(
                adminRoot + "/policy",
                json(Map.ofEntries(
                        Map.entry("expectedVersion", 0),
                        Map.entry("providerId", providerId),
                        Map.entry("moduleCodes", List.of(FLOW_RECORD_MODULE_CODE)),
                        Map.entry("outboundFields", Map.of(
                                FLOW_RECORD_MODULE_CODE,
                                List.of("approval_status", "route", "event_time"))),
                        Map.entry("allowedOperations", List.of(
                                 "RECORD_QUERY", "RECORD_CREATE", "RECORD_UPDATE", "AI_FILL",
                                 "CONFIG_FIELD_DRAFT", "CONFIG_SELECTION_FIELD_DRAFT",
                                 "CONFIG_PAGE_LAYOUT_DRAFT", "CONFIG_FILTER_SCENARIO_DRAFT",
                                 "CONFIG_FIELD_PERMISSION_STAGE_DRAFT", "RECORD_CONTEXT_SUMMARY",
                                "WORK_TASK_QUERY", "WORK_DAILY_REPORT_QUERY",
                                "TODO_QUERY", "MESSAGE_QUERY",
                                "WORK_PROJECT_METRICS_QUERY",
                                "RUNTIME_STATISTICS_QUERY",
                                "RUNTIME_REPORT_QUERY",
                                "RECORD_COMMENT_QUERY", "RECORD_HISTORY_QUERY",
                                "RECORD_FILE_QUERY",
                                "WORK_TASK_DRAFT", "WORK_DAILY_REPORT_DRAFT",
                                "FLOW_DEFINITION_DRAFT", "CONFIG_REPORT_DRAFT",
                                "CONFIG_PRINT_TEMPLATE_DRAFT")),
                        Map.entry("writableFields", Map.of(
                                FLOW_RECORD_MODULE_CODE,
                                List.of("approval_status"))),
                        Map.entry("fillFields", Map.of(
                                FLOW_RECORD_MODULE_CODE,
                                List.of("ai_summary"))),
                        Map.entry("maxRows", 3),
                        Map.entry("confirmationMode", "REQUIRED"),
                        Map.entry("confirmationExpiresSeconds", 600),
                        Map.entry("redactionMode", "STRICT"),
                        Map.entry("promptVersion", "journey-v1"),
                        Map.entry("enabled", true))),
                Map.of());
        assertOk(savedPolicy);
        var draftVersion = savedPolicy.body().at("/data/draftVersion").asLong();
        assertThat(draftVersion).isOne();

        var checked = client.postWithCsrf(
                adminRoot + "/policy:check", "{}", Map.of());
        assertOk(checked);
        assertThat(text(checked.body(), "/data/status")).isEqualTo("PASSED");
        assertThat(checked.body().at("/data/issues")).isEmpty();

        var publishKey = key();
        var publishBody = json(Map.of("expectedVersion", draftVersion));
        var published = client.postWithCsrf(
                adminRoot + "/policy:publish", publishBody,
                Map.of("Idempotency-Key", publishKey));
        assertOk(published);
        var policyVersionId = text(published.body(), "/data/activeVersionId");
        assertThat(policyVersionId).isNotBlank();
        var replay = client.postWithCsrf(
                adminRoot + "/policy:publish", publishBody,
                Map.of("Idempotency-Key", publishKey));
        assertOk(replay);
        assertThat(text(replay.body(), "/data/activeVersionId"))
                .isEqualTo(policyVersionId);

        var adminCapability = client.get(adminRoot + "/capability");
        assertOk(adminCapability);
        assertThat(adminCapability.body().at("/data/available").asBoolean()).isTrue();
        var runtimeCapability = client.get(runtimeRoot + "/capability");
        assertOk(runtimeCapability);
        assertThat(runtimeCapability.body().at("/data/available").asBoolean()).isTrue();

        var rawTitle = "AI_RAW_TITLE_75";
        var createdSession = client.postWithCsrf(
                runtimeRoot + "/sessions",
                json(Map.of("title", rawTitle)),
                Map.of("Idempotency-Key", key()));
        assertOk(createdSession);
        var sessionId = text(createdSession.body(), "/data/id");
        assertThat(createdSession.body().at("/data/id").isTextual()).isTrue();
        assertThat(text(createdSession.body(), "/data/title"))
                .doesNotContain(rawTitle);

        var rawQuestion = "AI_RAW_PROMPT_75 查询已审批记录";
        var submitted = client.postWithCsrf(
                runtimeRoot + "/sessions/" + sessionId + "/messages",
                json(Map.of("content", rawQuestion)),
                Map.of("Idempotency-Key", key()));
        assertOk(submitted);
        assertThat(text(submitted.body(), "/data/status")).isEqualTo("SUCCEEDED");
        assertThat(text(submitted.body(), "/data/answer"))
                .isEqualTo("AI_FIXTURE_SUMMARY_75");
        assertThat(text(submitted.body(), "/data/tool/moduleCode"))
                .isEqualTo(FLOW_RECORD_MODULE_CODE);
        assertThat(submitted.body().at("/data/tool/returnedRows").asInt())
                .isBetween(1, 3);
        assertThat(submitted.body().at("/data/tool/rows/0/recordId").isTextual())
                .isTrue();

        var detail = client.get(runtimeRoot + "/sessions/" + sessionId);
        assertOk(detail);
        assertThat(detail.body().at("/data/messages")).hasSize(2);
        assertThat(detail.body().at("/data/turns")).hasSize(1);
        assertThat(text(detail.body(), "/data/turns/0/status"))
                .isEqualTo("SUCCEEDED");

        var system = Long.parseLong(systemId);
        var tenant = Long.parseLong(tenantId);
        var rootAccountId = jdbcTemplate.queryForObject(
                "SELECT id FROM un_plat_account WHERE username=?",
                Long.class, ROOT_USERNAME);

        var configModuleId = jdbcTemplate.queryForObject(
                "SELECT id FROM un_module_definition "
                        + "WHERE system_id=? AND module_code=? AND deleted_at IS NULL",
                Long.class, system, FLOW_RECORD_MODULE_CODE);
        var configRevisionBefore = jdbcTemplate.queryForObject(
                "SELECT draft_revision FROM un_module_config_root WHERE system_id=?",
                Long.class, system);
        var activeConfigVersionBefore = jdbcTemplate.queryForObject(
                "SELECT active_version_id FROM un_module_config_root WHERE system_id=?",
                Long.class, system);
        var activeConfigChecksumBefore = jdbcTemplate.queryForObject(
                "SELECT snapshot_checksum FROM un_module_config_version "
                        + "WHERE system_id=? AND id=?",
                String.class, system, activeConfigVersionBefore);
        var publishCountBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_publish_record WHERE system_id=?",
                Long.class, system);
        var configFieldCountBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_field "
                        + "WHERE system_id=? AND module_id=? AND deleted_at IS NULL",
                Long.class, system, configModuleId);

        var configProposalA = client.postWithCsrf(
                runtimeRoot + "/sessions/" + sessionId + "/messages",
                json(Map.of("content", "AI_CONFIG_FIELD_81_A add priority draft field")),
                Map.of("Idempotency-Key", key()));
        assertOk(configProposalA);
        assertThat(text(configProposalA.body(), "/data/status"))
                .describedAs("configuration proposal response: %s",
                        configProposalA.body())
                .isEqualTo("CONFIRMATION_REQUIRED");
        assertThat(text(configProposalA.body(),
                "/data/configurationProposal/state")).isEqualTo("PENDING");
        assertThat(text(configProposalA.body(),
                "/data/configurationProposal/preview/fieldCode"))
                .isEqualTo("ai_priority_81");
        assertThat(text(configProposalA.body(),
                "/data/configurationProposal/preview/fieldType"))
                .isEqualTo("INTEGER");
        assertThat(configProposalA.body().at(
                "/data/configurationProposal/preview/settings/precision").asInt())
                .isEqualTo(38);
        var configProposalAId = text(configProposalA.body(),
                "/data/configurationProposal/id");
        var configProposalARevision = configProposalA.body().at(
                "/data/configurationProposal/revision").asLong();

        var configProposalB = client.postWithCsrf(
                runtimeRoot + "/sessions/" + sessionId + "/messages",
                json(Map.of("content", "AI_CONFIG_FIELD_81_B add risk note draft field")),
                Map.of("Idempotency-Key", key()));
        assertOk(configProposalB);
        assertThat(text(configProposalB.body(), "/data/status"))
                .describedAs("second configuration proposal response: %s",
                        configProposalB.body())
                .isEqualTo("CONFIRMATION_REQUIRED");
        assertThat(text(configProposalB.body(),
                "/data/configurationProposal/state")).isEqualTo("PENDING");
        var configProposalBId = text(configProposalB.body(),
                "/data/configurationProposal/id");
        var configProposalBRevision = configProposalB.body().at(
                "/data/configurationProposal/revision").asLong();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_field "
                        + "WHERE system_id=? AND module_id=? AND deleted_at IS NULL",
                Long.class, system, configModuleId))
                .isEqualTo(configFieldCountBefore);

        var pendingConfiguration = client.get(
                runtimeRoot + "/sessions/" + sessionId
                        + "/configuration-proposals/" + configProposalAId);
        assertOk(pendingConfiguration);
        assertThat(text(pendingConfiguration.body(), "/data/state"))
                .isEqualTo("PENDING");

        var configConfirmKey = key();
        var configConfirmBody = json(Map.of(
                "expectedRevision", configProposalARevision));
        var confirmedConfiguration = client.postWithCsrf(
                runtimeRoot + "/sessions/" + sessionId
                        + "/configuration-proposals/" + configProposalAId
                        + "/confirm",
                configConfirmBody, Map.of("Idempotency-Key", configConfirmKey));
        assertOk(confirmedConfiguration);
        assertThat(text(confirmedConfiguration.body(), "/data/state"))
                .isEqualTo("SUCCEEDED");
        assertThat(text(confirmedConfiguration.body(),
                "/data/result/fieldCode")).isEqualTo("[field-code:redacted]");
        assertThat(text(confirmedConfiguration.body(),
                "/data/result/draftStatus")).isEqualTo("DRAFT");
        assertThat(confirmedConfiguration.body().at(
                "/data/result/draftRevision").asLong())
                .isEqualTo(configRevisionBefore + 1);
        var createdConfigurationFieldId = text(
                confirmedConfiguration.body(), "/data/result/fieldId");

        var confirmedConfigurationReplay = client.postWithCsrf(
                runtimeRoot + "/sessions/" + sessionId
                        + "/configuration-proposals/" + configProposalAId
                        + "/confirm",
                configConfirmBody, Map.of("Idempotency-Key", configConfirmKey));
        assertOk(confirmedConfigurationReplay);
        assertThat(text(confirmedConfigurationReplay.body(),
                "/data/result/fieldId")).isEqualTo(createdConfigurationFieldId);
        assertError(client.postWithCsrf(
                        runtimeRoot + "/sessions/" + sessionId
                                + "/configuration-proposals/" + configProposalAId
                                + "/confirm",
                        json(Map.of("expectedRevision", configProposalARevision + 1)),
                        Map.of("Idempotency-Key", configConfirmKey)),
                409, "AI_CONFIG_FIELD_REPLAY_CONFLICT");

        var staleConfiguration = client.postWithCsrf(
                runtimeRoot + "/sessions/" + sessionId
                        + "/configuration-proposals/" + configProposalBId
                        + "/confirm",
                json(Map.of("expectedRevision", configProposalBRevision)),
                Map.of("Idempotency-Key", key()));
        assertOk(staleConfiguration);
        assertThat(text(staleConfiguration.body(), "/data/state"))
                .isEqualTo("FAILED");
        assertThat(text(staleConfiguration.body(), "/data/errorCode"))
                .isEqualTo("AI_CONFIG_DRAFT_STALE");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_field "
                        + "WHERE system_id=? AND module_id=? "
                        + "AND field_code='ai_risk_note_81' AND deleted_at IS NULL",
                Long.class, system, configModuleId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_field "
                        + "WHERE system_id=? AND module_id=? "
                        + "AND field_code='ai_priority_81' AND field_type='NUMBER' "
                        + "AND JSON_EXTRACT(property_json,'$.precision')=38 "
                        + "AND JSON_EXTRACT(property_json,'$.scale')=0 "
                        + "AND deleted_at IS NULL",
                Long.class, system, configModuleId)).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT draft_revision FROM un_module_config_root WHERE system_id=?",
                Long.class, system)).isEqualTo(configRevisionBefore + 1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT active_version_id FROM un_module_config_root WHERE system_id=?",
                Long.class, system)).isEqualTo(activeConfigVersionBefore);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT snapshot_checksum FROM un_module_config_version "
                        + "WHERE system_id=? AND id=?",
                String.class, system, activeConfigVersionBefore))
                .isEqualTo(activeConfigChecksumBefore);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_publish_record WHERE system_id=?",
                Long.class, system)).isEqualTo(publishCountBefore);

        var artifactDictionaryCountBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_dictionary "
                        + "WHERE system_id=? AND dictionary_code='ai_priority_options_82' "
                        + "AND deleted_at IS NULL",
                Long.class, system);
        var artifactFieldCountBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_field "
                        + "WHERE system_id=? AND module_id=? "
                        + "AND field_code='ai_priority_82' AND deleted_at IS NULL",
                Long.class, system, configModuleId);
        var formPageId = jdbcTemplate.queryForObject(
                "SELECT id FROM un_module_page WHERE system_id=? AND module_id=? "
                        + "AND page_code='form' AND page_type='FORM' AND deleted_at IS NULL",
                Long.class, system, configModuleId);
        var formLayoutBefore = jdbcTemplate.queryForObject(
                "SELECT CAST(layout_json AS CHAR) FROM un_module_page "
                        + "WHERE system_id=? AND id=?",
                String.class, system, formPageId);
        var formVersionBefore = jdbcTemplate.queryForObject(
                "SELECT version FROM un_module_page WHERE system_id=? AND id=?",
                Long.class, system, formPageId);

        var selectionArtifact = client.postWithCsrf(
                runtimeRoot + "/sessions/" + sessionId + "/messages",
                json(Map.of("content", "AI_CONFIG_SELECTION_82 add priority options")),
                Map.of("Idempotency-Key", key()));
        assertOk(selectionArtifact);
        assertThat(text(selectionArtifact.body(), "/data/status"))
                .describedAs("selection artifact response: %s", selectionArtifact.body())
                .isEqualTo("CONFIRMATION_REQUIRED");
        assertThat(text(selectionArtifact.body(),
                "/data/artifactProposal/state")).isEqualTo("PENDING");
        assertThat(text(selectionArtifact.body(),
                "/data/artifactProposal/operation"))
                .isEqualTo("CONFIG_SELECTION_FIELD_DRAFT");
        assertThat(text(selectionArtifact.body(),
                "/data/artifactProposal/artifactKind")).isEqualTo("SELECTION_FIELD");
        assertThat(text(selectionArtifact.body(),
                "/data/artifactProposal/preview/selectionField/fieldCode"))
                .isEqualTo("ai_priority_82");
        assertThat(text(selectionArtifact.body(),
                "/data/artifactProposal/preview/selectionField/fieldName"))
                .isEqualTo("AI Priority 82");
        assertThat(selectionArtifact.body().at(
                "/data/artifactProposal/preview/selectionField/options"))
                .hasSize(3);
        assertThat(selectionArtifact.body().at(
                "/data/artifactProposal/preview/selectionField/options/0/label")
                .asText()).isEqualTo("Low");
        var selectionArtifactId = text(selectionArtifact.body(),
                "/data/artifactProposal/id");
        var selectionArtifactRevision = selectionArtifact.body().at(
                "/data/artifactProposal/revision").asLong();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_dictionary "
                        + "WHERE system_id=? AND dictionary_code='ai_priority_options_82' "
                        + "AND deleted_at IS NULL",
                Long.class, system)).isEqualTo(artifactDictionaryCountBefore);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_field "
                        + "WHERE system_id=? AND module_id=? "
                        + "AND field_code='ai_priority_82' AND deleted_at IS NULL",
                Long.class, system, configModuleId)).isEqualTo(artifactFieldCountBefore);

        var pendingSelectionArtifact = client.get(
                runtimeRoot + "/sessions/" + sessionId
                        + "/configuration-artifact-proposals/" + selectionArtifactId);
        assertOk(pendingSelectionArtifact);
        assertThat(text(pendingSelectionArtifact.body(), "/data/state"))
                .isEqualTo("PENDING");
        assertThat(text(pendingSelectionArtifact.body(),
                "/data/preview/selectionField/fieldName"))
                .isEqualTo("[name:redacted]");
        assertThat(text(pendingSelectionArtifact.body(),
                "/data/preview/selectionField/options/0/label"))
                .isEqualTo("[label:redacted]");

        var selectionConfirmKey = key();
        var selectionConfirmBody = json(Map.of(
                "expectedRevision", selectionArtifactRevision));
        var confirmedSelectionArtifact = client.postWithCsrf(
                runtimeRoot + "/sessions/" + sessionId
                        + "/configuration-artifact-proposals/" + selectionArtifactId
                        + "/confirm",
                selectionConfirmBody, Map.of("Idempotency-Key", selectionConfirmKey));
        assertOk(confirmedSelectionArtifact);
        assertThat(text(confirmedSelectionArtifact.body(), "/data/state"))
                .describedAs("selection confirmation response: %s",
                        confirmedSelectionArtifact.body())
                .isEqualTo("SUCCEEDED");
        assertThat(text(confirmedSelectionArtifact.body(),
                "/data/result/draftStatus")).isEqualTo("DRAFT");
        assertThat(text(confirmedSelectionArtifact.body(),
                "/data/result/selectionField/dictionary/dictionaryCode"))
                .isEqualTo("ai_priority_options_82");
        assertThat(text(confirmedSelectionArtifact.body(),
                "/data/result/selectionField/dictionary/dictionaryName"))
                .isEqualTo("[name:redacted]");
        assertThat(confirmedSelectionArtifact.body().at(
                "/data/result/selectionField/options")).hasSize(3);
        assertThat(confirmedSelectionArtifact.body().at(
                "/data/result/selectionField/options/0/label").asText())
                .isEqualTo("[label:redacted]");
        var selectionDictionaryId = text(confirmedSelectionArtifact.body(),
                "/data/result/selectionField/dictionary/dictionaryId");
        var selectionFieldId = text(confirmedSelectionArtifact.body(),
                "/data/result/selectionField/fieldId");
        assertThat(confirmedSelectionArtifact.body().at(
                "/data/result/draftRevision").asLong())
                .isEqualTo(configRevisionBefore + 2);

        var confirmedSelectionReplay = client.postWithCsrf(
                runtimeRoot + "/sessions/" + sessionId
                        + "/configuration-artifact-proposals/" + selectionArtifactId
                        + "/confirm",
                selectionConfirmBody, Map.of("Idempotency-Key", selectionConfirmKey));
        assertOk(confirmedSelectionReplay);
        assertThat(text(confirmedSelectionReplay.body(),
                "/data/result/selectionField/dictionary/dictionaryId"))
                .isEqualTo(selectionDictionaryId);
        assertThat(text(confirmedSelectionReplay.body(),
                "/data/result/selectionField/fieldId")).isEqualTo(selectionFieldId);
        assertError(client.postWithCsrf(
                        runtimeRoot + "/sessions/" + sessionId
                                + "/configuration-artifact-proposals/"
                                + selectionArtifactId + "/confirm",
                        json(Map.of("expectedRevision", selectionArtifactRevision + 1)),
                        Map.of("Idempotency-Key", selectionConfirmKey)),
                409, "AI_CONFIG_ARTIFACT_REPLAY_CONFLICT");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_dictionary "
                        + "WHERE system_id=? AND id=? "
                        + "AND dictionary_code='ai_priority_options_82' "
                        + "AND dictionary_type='FIELD_OPTION' AND deleted_at IS NULL",
                Long.class, system, Long.parseLong(selectionDictionaryId))).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_dictionary_item "
                        + "WHERE system_id=? AND dictionary_id=? AND deleted_at IS NULL",
                Long.class, system, Long.parseLong(selectionDictionaryId))).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_dictionary_item_closure "
                        + "WHERE system_id=? AND dictionary_id=? AND depth=0",
                Long.class, system, Long.parseLong(selectionDictionaryId))).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_field "
                        + "WHERE system_id=? AND module_id=? AND id=? "
                        + "AND field_code='ai_priority_82' AND field_type='RADIO' "
                        + "AND is_required=1 AND dictionary_id=? "
                        + "AND JSON_EXTRACT(property_json,'$.multiple')=false "
                        + "AND deleted_at IS NULL",
                Long.class, system, configModuleId, Long.parseLong(selectionFieldId),
                Long.parseLong(selectionDictionaryId))).isOne();

        var pageArtifactA = client.postWithCsrf(
                runtimeRoot + "/sessions/" + sessionId + "/messages",
                json(Map.of("content", "AI_CONFIG_PAGE_82_A organize form layout")),
                Map.of("Idempotency-Key", key()));
        assertOk(pageArtifactA);
        assertThat(text(pageArtifactA.body(), "/data/status"))
                .describedAs("page artifact response: %s", pageArtifactA.body())
                .isEqualTo("CONFIRMATION_REQUIRED");
        assertThat(text(pageArtifactA.body(),
                "/data/artifactProposal/artifactKind")).isEqualTo("PAGE_LAYOUT");
        assertThat(text(pageArtifactA.body(),
                "/data/artifactProposal/preview/pageLayout/pageCode"))
                .isEqualTo("form");
        assertThat(pageArtifactA.body().at(
                "/data/artifactProposal/preview/pageLayout/layout/redacted").asBoolean())
                .isFalse();
        assertThat(pageArtifactA.body().at(
                "/data/artifactProposal/preview/pageLayout/layout/sections"))
                .hasSize(2);
        assertThat(pageArtifactA.body().at(
                "/data/artifactProposal/preview/pageLayout/layout/sectionCount").asInt())
                .isEqualTo(2);
        assertThat(pageArtifactA.body().at(
                "/data/artifactProposal/preview/pageLayout/layout/fieldCount").asInt())
                .isEqualTo(3);
        var pageArtifactAId = text(pageArtifactA.body(),
                "/data/artifactProposal/id");
        var pageArtifactARevision = pageArtifactA.body().at(
                "/data/artifactProposal/revision").asLong();

        var pageArtifactB = client.postWithCsrf(
                runtimeRoot + "/sessions/" + sessionId + "/messages",
                json(Map.of("content", "AI_CONFIG_PAGE_82_B alternate form layout")),
                Map.of("Idempotency-Key", key()));
        assertOk(pageArtifactB);
        assertThat(text(pageArtifactB.body(), "/data/status"))
                .isEqualTo("CONFIRMATION_REQUIRED");
        var pageArtifactBId = text(pageArtifactB.body(),
                "/data/artifactProposal/id");
        var pageArtifactBRevision = pageArtifactB.body().at(
                "/data/artifactProposal/revision").asLong();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT CAST(layout_json AS CHAR) FROM un_module_page "
                        + "WHERE system_id=? AND id=?",
                String.class, system, formPageId)).isEqualTo(formLayoutBefore);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT version FROM un_module_page WHERE system_id=? AND id=?",
                Long.class, system, formPageId)).isEqualTo(formVersionBefore);

        var pageConfirmKey = key();
        var pageConfirmBody = json(Map.of("expectedRevision", pageArtifactARevision));
        var confirmedPageArtifact = client.postWithCsrf(
                runtimeRoot + "/sessions/" + sessionId
                        + "/configuration-artifact-proposals/" + pageArtifactAId
                        + "/confirm",
                pageConfirmBody, Map.of("Idempotency-Key", pageConfirmKey));
        assertOk(confirmedPageArtifact);
        assertThat(text(confirmedPageArtifact.body(), "/data/state"))
                .isEqualTo("SUCCEEDED");
        assertThat(text(confirmedPageArtifact.body(),
                "/data/result/pageLayout/pageId")).isEqualTo(Long.toString(formPageId));
        assertThat(confirmedPageArtifact.body().at(
                "/data/result/pageLayout/version").asLong())
                .isEqualTo(formVersionBefore + 1);
        assertThat(confirmedPageArtifact.body().at(
                "/data/result/pageLayout/layout/sectionCount").asInt())
                .isEqualTo(2);
        var confirmedPageReplay = client.postWithCsrf(
                runtimeRoot + "/sessions/" + sessionId
                        + "/configuration-artifact-proposals/" + pageArtifactAId
                        + "/confirm",
                pageConfirmBody, Map.of("Idempotency-Key", pageConfirmKey));
        assertOk(confirmedPageReplay);
        assertThat(confirmedPageReplay.body().at(
                "/data/result/pageLayout/version").asLong())
                .isEqualTo(formVersionBefore + 1);
        assertError(client.postWithCsrf(
                        runtimeRoot + "/sessions/" + sessionId
                                + "/configuration-artifact-proposals/" + pageArtifactAId
                                + "/confirm",
                        json(Map.of("expectedRevision", pageArtifactARevision + 1)),
                        Map.of("Idempotency-Key", pageConfirmKey)),
                409, "AI_CONFIG_ARTIFACT_REPLAY_CONFLICT");

        var stalePageArtifact = client.postWithCsrf(
                runtimeRoot + "/sessions/" + sessionId
                        + "/configuration-artifact-proposals/" + pageArtifactBId
                        + "/confirm",
                json(Map.of("expectedRevision", pageArtifactBRevision)),
                Map.of("Idempotency-Key", key()));
        assertOk(stalePageArtifact);
        assertThat(text(stalePageArtifact.body(), "/data/state")).isEqualTo("FAILED");
        assertThat(text(stalePageArtifact.body(), "/data/errorCode"))
                .isEqualTo("AI_CONFIG_DRAFT_STALE");
        var formLayoutAfter = objectMapper.readTree(jdbcTemplate.queryForObject(
                "SELECT CAST(layout_json AS CHAR) FROM un_module_page "
                        + "WHERE system_id=? AND id=?",
                String.class, system, formPageId));
        assertThat(formLayoutAfter.path("columns").asInt()).isEqualTo(2);
        assertThat(formLayoutAfter.path("gap").asInt()).isEqualTo(20);
        assertThat(formLayoutAfter.path("density").asText()).isEqualTo("COMPACT");
        assertThat(formLayoutAfter.path("sections")).hasSize(2);
        assertThat(formLayoutAfter.at("/sections/0/fieldCodes")).hasSize(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT version FROM un_module_page WHERE system_id=? AND id=?",
                Long.class, system, formPageId)).isEqualTo(formVersionBefore + 1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT draft_revision FROM un_module_config_root WHERE system_id=?",
                Long.class, system)).isEqualTo(configRevisionBefore + 3);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT active_version_id FROM un_module_config_root WHERE system_id=?",
                Long.class, system)).isEqualTo(activeConfigVersionBefore);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT snapshot_checksum FROM un_module_config_version "
                        + "WHERE system_id=? AND id=?",
                String.class, system, activeConfigVersionBefore))
                .isEqualTo(activeConfigChecksumBefore);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_publish_record WHERE system_id=?",
                Long.class, system)).isEqualTo(publishCountBefore);

        var batch99RevisionBefore = jdbcTemplate.queryForObject(
                "SELECT draft_revision FROM un_module_config_root WHERE system_id=?",
                Long.class, system);
        var listPageId = jdbcTemplate.queryForObject(
                "SELECT id FROM un_module_page WHERE system_id=? AND module_id=? "
                        + "AND page_code='list' AND page_type='LIST' "
                        + "AND desired_status='ENABLED' AND deleted_at IS NULL",
                Long.class, system, configModuleId);
        var listPageVersionBefore = jdbcTemplate.queryForObject(
                "SELECT version FROM un_module_page WHERE system_id=? AND id=?",
                Long.class, system, listPageId);
        var listLayoutBeforeJson = jdbcTemplate.queryForObject(
                "SELECT CAST(layout_json AS CHAR) FROM un_module_page "
                        + "WHERE system_id=? AND id=?",
                String.class, system, listPageId);
        var listLayoutBefore = objectMapper.readTree(listLayoutBeforeJson);
        var listScenarioCountBefore = listLayoutBefore.path("filterScenarios").size();

        var filterScenarioArtifact = client.postWithCsrf(
                runtimeRoot + "/sessions/" + sessionId + "/messages",
                json(Map.of("content", "AI_CONFIG_FILTER_SCENARIO_99 add routed-first list scenario")),
                Map.of("Idempotency-Key", key()));
        assertOk(filterScenarioArtifact);
        assertThat(text(filterScenarioArtifact.body(), "/data/status"))
                .describedAs("Batch99 filter scenario response: %s", filterScenarioArtifact.body())
                .isEqualTo("CONFIRMATION_REQUIRED");
        assertThat(text(filterScenarioArtifact.body(),
                "/data/artifactProposal/operation"))
                .isEqualTo("CONFIG_FILTER_SCENARIO_DRAFT");
        assertThat(text(filterScenarioArtifact.body(),
                "/data/artifactProposal/artifactKind"))
                .isEqualTo("FILTER_SCENARIO");
        assertThat(text(filterScenarioArtifact.body(),
                "/data/artifactProposal/preview/filterScenario/pageCode"))
                .isEqualTo("list");
        assertThat(text(filterScenarioArtifact.body(),
                "/data/artifactProposal/preview/filterScenario/scenario/code"))
                .isEqualTo("routed_first");
        assertThat(filterScenarioArtifact.body().at(
                "/data/artifactProposal/preview/filterScenario/resolvedState/filterScenarios"))
                .hasSize(listScenarioCountBefore + 1);
        var filterScenarioArtifactId = text(filterScenarioArtifact.body(),
                "/data/artifactProposal/id");
        var filterScenarioArtifactRevision = filterScenarioArtifact.body().at(
                "/data/artifactProposal/revision").asLong();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT CAST(layout_json AS CHAR) FROM un_module_page "
                        + "WHERE system_id=? AND id=?",
                String.class, system, listPageId))
                .isEqualTo(listLayoutBeforeJson);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT version FROM un_module_page WHERE system_id=? AND id=?",
                Long.class, system, listPageId)).isEqualTo(listPageVersionBefore);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT draft_revision FROM un_module_config_root WHERE system_id=?",
                Long.class, system)).isEqualTo(batch99RevisionBefore);

        var filterScenarioConfirmKey = key();
        var filterScenarioConfirmBody = json(Map.of(
                "expectedRevision", filterScenarioArtifactRevision));
        var confirmedFilterScenario = client.postWithCsrf(
                runtimeRoot + "/sessions/" + sessionId
                        + "/configuration-artifact-proposals/" + filterScenarioArtifactId
                        + "/confirm",
                filterScenarioConfirmBody,
                Map.of("Idempotency-Key", filterScenarioConfirmKey));
        assertOk(confirmedFilterScenario);
        assertThat(text(confirmedFilterScenario.body(), "/data/state"))
                .isEqualTo("SUCCEEDED");
        assertThat(text(confirmedFilterScenario.body(),
                "/data/result/draftStatus")).isEqualTo("DRAFT");
        assertThat(confirmedFilterScenario.body().at(
                "/data/result/draftRevision").asLong())
                .isEqualTo(batch99RevisionBefore + 1);
        assertThat(confirmedFilterScenario.body().at(
                "/data/result/filterScenario/version").asLong())
                .isEqualTo(listPageVersionBefore + 1);
        assertThat(text(confirmedFilterScenario.body(),
                "/data/result/filterScenario/state/defaultFilterScenarioCode"))
                .isEqualTo("routed_first");
        assertThat(confirmedFilterScenario.body().at(
                "/data/result/filterScenario/state/filterScenarios"))
                .hasSize(listScenarioCountBefore + 1);
        var filterScenarioReplay = client.postWithCsrf(
                runtimeRoot + "/sessions/" + sessionId
                        + "/configuration-artifact-proposals/" + filterScenarioArtifactId
                        + "/confirm",
                filterScenarioConfirmBody,
                Map.of("Idempotency-Key", filterScenarioConfirmKey));
        assertOk(filterScenarioReplay);
        assertThat(filterScenarioReplay.body().at(
                "/data/result/filterScenario/version").asLong())
                .isEqualTo(listPageVersionBefore + 1);
        var listLayoutAfter = objectMapper.readTree(jdbcTemplate.queryForObject(
                "SELECT CAST(layout_json AS CHAR) FROM un_module_page "
                        + "WHERE system_id=? AND id=?",
                String.class, system, listPageId));
        assertThat(listLayoutAfter.path("filterScenarios"))
                .hasSize(listScenarioCountBefore + 1);
        assertThat(listLayoutAfter.path("defaultFilterScenarioCode").asText())
                .isEqualTo("routed_first");

        var routeFieldId = jdbcTemplate.queryForObject(
                "SELECT id FROM un_module_field WHERE system_id=? AND module_id=? "
                        + "AND field_code='route' AND deleted_at IS NULL",
                Long.class, system, configModuleId);
        var routeFieldVersionBefore = jdbcTemplate.queryForObject(
                "SELECT version FROM un_module_field WHERE system_id=? AND id=?",
                Long.class, system, routeFieldId);
        var routePermissionRowsBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_permission WHERE system_id=? "
                        + "AND resource_type='FIELD' AND resource_id=? "
                        + "AND permission_code IN (?,?) AND deleted_at IS NULL",
                Long.class, system, routeFieldId,
                "module.flow_order.field.route.read",
                "module.flow_order.field.route.write");
        var routeRoleGrantsBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_plat_role_permission rp "
                        + "JOIN un_plat_permission p ON p.id=rp.permission_id "
                        + "WHERE rp.scope_type='SYSTEM' AND rp.scope_key=? "
                        + "AND p.permission_code IN (?,?)",
                Long.class, system,
                "module.flow_order.field.route.read",
                "module.flow_order.field.route.write");
        var permissionRevisionBefore = jdbcTemplate.queryForObject(
                "SELECT draft_revision FROM un_module_config_root WHERE system_id=?",
                Long.class, system);

        var permissionStageArtifact = client.postWithCsrf(
                runtimeRoot + "/sessions/" + sessionId + "/messages",
                json(Map.of("content", "AI_CONFIG_FIELD_PERMISSION_STAGE_99 stage route access")),
                Map.of("Idempotency-Key", key()));
        assertOk(permissionStageArtifact);
        assertThat(text(permissionStageArtifact.body(), "/data/status"))
                .describedAs("Batch99 permission stage response: %s", permissionStageArtifact.body())
                .isEqualTo("CONFIRMATION_REQUIRED");
        assertThat(text(permissionStageArtifact.body(),
                "/data/artifactProposal/artifactKind"))
                .isEqualTo("FIELD_PERMISSION_STAGE");
        assertThat(text(permissionStageArtifact.body(),
                "/data/artifactProposal/preview/fieldPermissionStage/fieldCode"))
                .isEqualTo("route");
        assertThat(text(permissionStageArtifact.body(),
                "/data/artifactProposal/preview/fieldPermissionStage/readPermissionMode"))
                .isEqualTo("STAGED");
        assertThat(text(permissionStageArtifact.body(),
                "/data/artifactProposal/preview/fieldPermissionStage/writePermissionMode"))
                .isEqualTo("STAGED");
        var permissionStageArtifactId = text(permissionStageArtifact.body(),
                "/data/artifactProposal/id");
        var permissionStageArtifactRevision = permissionStageArtifact.body().at(
                "/data/artifactProposal/revision").asLong();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_permission WHERE system_id=? "
                        + "AND resource_type='FIELD' AND resource_id=? "
                        + "AND permission_code IN (?,?) AND deleted_at IS NULL",
                Long.class, system, routeFieldId,
                "module.flow_order.field.route.read",
                "module.flow_order.field.route.write"))
                .isEqualTo(routePermissionRowsBefore);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT version FROM un_module_field WHERE system_id=? AND id=?",
                Long.class, system, routeFieldId)).isEqualTo(routeFieldVersionBefore);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT draft_revision FROM un_module_config_root WHERE system_id=?",
                Long.class, system)).isEqualTo(permissionRevisionBefore);

        var permissionStageConfirmKey = key();
        var permissionStageConfirmBody = json(Map.of(
                "expectedRevision", permissionStageArtifactRevision));
        var confirmedPermissionStage = client.postWithCsrf(
                runtimeRoot + "/sessions/" + sessionId
                        + "/configuration-artifact-proposals/" + permissionStageArtifactId
                        + "/confirm",
                permissionStageConfirmBody,
                Map.of("Idempotency-Key", permissionStageConfirmKey));
        assertOk(confirmedPermissionStage);
        assertThat(text(confirmedPermissionStage.body(), "/data/state"))
                .isEqualTo("SUCCEEDED");
        assertThat(confirmedPermissionStage.body().at(
                "/data/result/draftRevision").asLong())
                .isEqualTo(permissionRevisionBefore + 1);
        assertThat(confirmedPermissionStage.body().at(
                "/data/result/fieldPermissionStage/version").asLong())
                .isEqualTo(routeFieldVersionBefore + 1);
        assertThat(text(confirmedPermissionStage.body(),
                "/data/result/fieldPermissionStage/readPermissionMode"))
                .isEqualTo("STAGED");
        assertThat(text(confirmedPermissionStage.body(),
                "/data/result/fieldPermissionStage/writePermissionMode"))
                .isEqualTo("STAGED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_permission WHERE system_id=? "
                        + "AND resource_type='FIELD' AND resource_id=? "
                        + "AND permission_code IN (?,?) AND desired_status='DISABLED' "
                        + "AND deleted_at IS NULL",
                Long.class, system, routeFieldId,
                "module.flow_order.field.route.read",
                "module.flow_order.field.route.write")).isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_plat_role_permission rp "
                        + "JOIN un_plat_permission p ON p.id=rp.permission_id "
                        + "WHERE rp.scope_type='SYSTEM' AND rp.scope_key=? "
                        + "AND p.permission_code IN (?,?)",
                Long.class, system,
                "module.flow_order.field.route.read",
                "module.flow_order.field.route.write"))
                .isEqualTo(routeRoleGrantsBefore);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT active_version_id FROM un_module_config_root WHERE system_id=?",
                Long.class, system)).isEqualTo(activeConfigVersionBefore);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT snapshot_checksum FROM un_module_config_version "
                        + "WHERE system_id=? AND id=?",
                String.class, system, activeConfigVersionBefore))
                .isEqualTo(activeConfigChecksumBefore);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_publish_record WHERE system_id=?",
                Long.class, system)).isEqualTo(publishCountBefore);

        var createdTitle = "AI Created Record 76";
        var createdCountBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_record "
                        + "WHERE system_id=? AND tenant_id=? AND title=?",
                Long.class, system, tenant, createdTitle);
        var proposedCreate = client.postWithCsrf(
                runtimeRoot + "/sessions/" + sessionId + "/messages",
                json(Map.of("content", "AI_CREATE_PROMPT_76 创建一条审批记录")),
                Map.of("Idempotency-Key", key()));
        assertOk(proposedCreate);
        assertThat(text(proposedCreate.body(), "/data/status"))
                .isEqualTo("CONFIRMATION_REQUIRED");
        assertThat(text(proposedCreate.body(), "/data/confirmation/state"))
                .isEqualTo("PENDING");
        assertThat(text(proposedCreate.body(), "/data/confirmation/operation"))
                .isEqualTo("RECORD_CREATE");
        var createConfirmationId = text(
                proposedCreate.body(), "/data/confirmation/id");
        var createConfirmationVersion = proposedCreate.body()
                .at("/data/confirmation/version").asLong();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_record "
                        + "WHERE system_id=? AND tenant_id=? AND title=?",
                Long.class, system, tenant, createdTitle))
                .isEqualTo(createdCountBefore);

        var pendingCreate = client.get(
                runtimeRoot + "/confirmations/" + createConfirmationId);
        assertOk(pendingCreate);
        assertThat(text(pendingCreate.body(), "/data/afterTitle"))
                .isEqualTo(createdTitle);
        assertThat(pendingCreate.body().at("/data/fields")).hasSize(1);

        var createConfirmKey = key();
        var createConfirmBody = json(Map.of(
                "expectedVersion", createConfirmationVersion));
        var confirmedCreate = client.postWithCsrf(
                runtimeRoot + "/confirmations/" + createConfirmationId + "/confirm",
                createConfirmBody, Map.of("Idempotency-Key", createConfirmKey));
        assertOk(confirmedCreate);
        assertThat(text(confirmedCreate.body(), "/data/state"))
                .isEqualTo("SUCCEEDED");
        var aiRecordId = text(confirmedCreate.body(), "/data/result/recordId");
        var aiRecordVersion = confirmedCreate.body()
                .at("/data/result/recordVersion").asLong();
        assertThat(text(confirmedCreate.body(), "/data/result/title"))
                .isEqualTo(createdTitle);
        var confirmedCreateReplay = client.postWithCsrf(
                runtimeRoot + "/confirmations/" + createConfirmationId + "/confirm",
                createConfirmBody, Map.of("Idempotency-Key", createConfirmKey));
        assertOk(confirmedCreateReplay);
        assertThat(text(confirmedCreateReplay.body(), "/data/result/recordId"))
                .isEqualTo(aiRecordId);
        assertError(client.postWithCsrf(
                        runtimeRoot + "/confirmations/" + createConfirmationId + "/confirm",
                        json(Map.of("expectedVersion", createConfirmationVersion + 1)),
                        Map.of("Idempotency-Key", createConfirmKey)),
                409, "AI_CONFIRMATION_REPLAY_CONFLICT");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_record "
                        + "WHERE system_id=? AND tenant_id=? AND id=? AND title=?",
                Long.class, system, tenant, Long.parseLong(aiRecordId), createdTitle))
                .isOne();

        var proposedUpdate = client.postWithCsrf(
                runtimeRoot + "/sessions/" + sessionId + "/messages",
                json(Map.of("content", "AI_UPDATE_PROMPT_76 AI_UPDATE_TARGET_76="
                        + aiRecordId + " AI_UPDATE_VERSION_76=" + aiRecordVersion)),
                Map.of("Idempotency-Key", key()));
        assertOk(proposedUpdate);
        assertThat(text(proposedUpdate.body(), "/data/status"))
                .isEqualTo("CONFIRMATION_REQUIRED");
        var updateConfirmationId = text(
                proposedUpdate.body(), "/data/confirmation/id");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT title FROM un_module_record "
                        + "WHERE system_id=? AND tenant_id=? AND id=?",
                String.class, system, tenant, Long.parseLong(aiRecordId)))
                .isEqualTo(createdTitle);
        var confirmedUpdate = client.postWithCsrf(
                runtimeRoot + "/confirmations/" + updateConfirmationId + "/confirm",
                json(Map.of("expectedVersion", 0)),
                Map.of("Idempotency-Key", key()));
        assertOk(confirmedUpdate);
        assertThat(text(confirmedUpdate.body(), "/data/state"))
                .isEqualTo("SUCCEEDED");
        assertThat(text(confirmedUpdate.body(), "/data/result/title"))
                .isEqualTo("AI Updated Record 76");
        var updatedRecordVersion = confirmedUpdate.body()
                .at("/data/result/recordVersion").asLong();
        assertThat(updatedRecordVersion).isEqualTo(aiRecordVersion + 1);

        var fillRoot = "/api/v1/systems/" + systemId + "/modules/"
                + FLOW_RECORD_MODULE_CODE + "/records/" + aiRecordId
                + "/ai-fill/ai_summary/proposals";
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_ai_fill_materialization "
                        + "WHERE system_id=? AND tenant_id=? AND record_id=?",
                Long.class, system, tenant, Long.parseLong(aiRecordId)))
                .isZero();
        var fillProposeKey = key();
        var fillProposeBody = json(Map.of(
                "expectedRecordVersion", updatedRecordVersion));
        var fillProposal = client.postWithCsrf(
                fillRoot, fillProposeBody,
                Map.of("Idempotency-Key", fillProposeKey));
        assertOk(fillProposal);
        assertThat(text(fillProposal.body(), "/data/state")).isEqualTo("PENDING");
        assertThat(text(fillProposal.body(), "/data/fieldCode"))
                .isEqualTo("ai_summary");
        assertThat(text(fillProposal.body(), "/data/afterDisplayValue"))
                .isEqualTo("AI Filled Summary 77");
        assertThat(fillProposal.body().at("/data/confidence").asDouble())
                .isEqualTo(0.96);
        var fillProposalId = text(fillProposal.body(), "/data/id");
        var fillProposalReplay = client.postWithCsrf(
                fillRoot, fillProposeBody,
                Map.of("Idempotency-Key", fillProposeKey));
        assertOk(fillProposalReplay);
        assertThat(text(fillProposalReplay.body(), "/data/id"))
                .isEqualTo(fillProposalId);
        assertError(client.postWithCsrf(
                        fillRoot,
                        json(Map.of("expectedRecordVersion", updatedRecordVersion + 1)),
                        Map.of("Idempotency-Key", fillProposeKey)),
                409, "AI_FILL_REPLAY_CONFLICT");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_ai_fill_materialization "
                        + "WHERE system_id=? AND tenant_id=? AND record_id=?",
                Long.class, system, tenant, Long.parseLong(aiRecordId)))
                .isZero();
        var pendingFill = client.get(fillRoot + "/" + fillProposalId);
        assertOk(pendingFill);
        assertThat(text(pendingFill.body(), "/data/state")).isEqualTo("PENDING");

        var fillConfirmKey = key();
        var confirmedFill = client.postWithCsrf(
                fillRoot + "/" + fillProposalId + "/confirm",
                json(Map.of("expectedVersion", 0)),
                Map.of("Idempotency-Key", fillConfirmKey));
        assertOk(confirmedFill);
        assertThat(text(confirmedFill.body(), "/data/state"))
                .describedAs("AI_FILL confirm response: %s", confirmedFill.body())
                .isEqualTo("SUCCEEDED");
        assertThat(text(confirmedFill.body(), "/data/result/displayValue"))
                .isEqualTo("AI Filled Summary 77");
        assertThat(confirmedFill.body().at("/data/result/recordVersion").asLong())
                .isEqualTo(updatedRecordVersion);
        var fillMaterializationId = text(
                confirmedFill.body(), "/data/result/materializationId");
        var confirmedFillReplay = client.postWithCsrf(
                fillRoot + "/" + fillProposalId + "/confirm",
                json(Map.of("expectedVersion", 0)),
                Map.of("Idempotency-Key", fillConfirmKey));
        assertOk(confirmedFillReplay);
        assertThat(text(
                confirmedFillReplay.body(), "/data/result/materializationId"))
                .isEqualTo(fillMaterializationId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_ai_fill_materialization "
                        + "WHERE system_id=? AND tenant_id=? AND record_id=? "
                        + "AND field_code='ai_summary' AND display_value=?",
                Long.class, system, tenant, Long.parseLong(aiRecordId),
                "AI Filled Summary 77")).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_ai_fill_history "
                        + "WHERE system_id=? AND tenant_id=? AND record_id=? "
                        + "AND outcome='CREATED'",
                Long.class, system, tenant, Long.parseLong(aiRecordId))).isOne();
        var filledRecord = client.get(
                "/api/v1/systems/" + systemId + "/runtime/modules/"
                        + FLOW_RECORD_MODULE_CODE + "/records/" + aiRecordId);
        assertOk(filledRecord);
        assertThat(filledRecord.body().toString())
                .contains("ai_summary", "AI Filled Summary 77");

        var recordActivityRoot = "/api/v1/systems/" + systemId
                + "/runtime/modules/" + FLOW_RECORD_MODULE_CODE
                + "/records/" + aiRecordId;
        var recordCommentBody = "AI_RECORD_COMMENT_BODY_90";
        var createdRecordComment = client.postWithCsrf(
                recordActivityRoot + "/comments",
                json(Map.of("body", recordCommentBody,
                        "mentionedMemberIds", List.of())),
                Map.of("Idempotency-Key", key()));
        assertCreated(createdRecordComment);
        var recordCommentId = text(
                createdRecordComment.body(), "/data/commentId");
        var recordFileName = "ai-record-activity-90.txt";
        var recordFileContentText = "AI_RECORD_FILE_CONTENT_90";
        var recordFileContent = recordFileContentText
                .getBytes(StandardCharsets.UTF_8);
        var attachedRecordFile = client.uploadWithCsrf(
                recordActivityRoot + "/files", recordFileName,
                "text/plain", recordFileContent);
        assertCreated(attachedRecordFile);
        var recordFileId = text(attachedRecordFile.body(), "/data/fileId");

        var nativeRecordComments = client.get(
                recordActivityRoot + "/comments?page=1&size=3");
        assertOk(nativeRecordComments);
        assertThat(nativeRecordComments.body().at("/data/items")).hasSize(1);
        var nativeRecordHistory = client.get(
                recordActivityRoot + "/history?page=1&size=3");
        assertOk(nativeRecordHistory);
        assertThat(nativeRecordHistory.body().at("/data/items")).isNotEmpty();
        var nativeRecordFiles = client.get(
                recordActivityRoot + "/files?page=1&size=3");
        assertOk(nativeRecordFiles);
        assertThat(nativeRecordFiles.body().at("/data/items")).hasSize(1);

        var recordCountBeforeContextReads = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_record WHERE system_id=? AND tenant_id=?",
                Long.class, system, tenant);
        var taskCountBeforeContextReads = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_work_task WHERE system_id=? AND tenant_id=?",
                Long.class, system, tenant);
        var taskVersionsBeforeContextReads = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(version),0) FROM un_work_task "
                        + "WHERE system_id=? AND tenant_id=?",
                Long.class, system, tenant);
        var projectCountBeforeContextReads = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_work_project WHERE system_id=? AND tenant_id=?",
                Long.class, system, tenant);
        var projectVersionsBeforeContextReads = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(version),0) FROM un_work_project "
                        + "WHERE system_id=? AND tenant_id=?",
                Long.class, system, tenant);
        var dailyReportCountBeforeContextReads = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_work_daily_report WHERE system_id=? AND tenant_id=?",
                Long.class, system, tenant);
        var commentCountBeforeContextReads = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_collab_record_comment "
                        + "WHERE system_id=? AND tenant_id=?",
                Long.class, system, tenant);
        var commentVersionsBeforeContextReads = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(version),0) FROM un_collab_record_comment "
                        + "WHERE system_id=? AND tenant_id=?",
                Long.class, system, tenant);
        var commentMentionCountBeforeContextReads = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_collab_record_comment_mention "
                        + "WHERE system_id=? AND tenant_id=?",
                Long.class, system, tenant);
        var historyCountBeforeContextReads = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_record_history "
                        + "WHERE system_id=? AND tenant_id=?",
                Long.class, system, tenant);
        var historyVersionsBeforeContextReads = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(record_version),0) "
                        + "FROM un_module_record_history "
                        + "WHERE system_id=? AND tenant_id=?",
                Long.class, system, tenant);
        var fileCountBeforeContextReads = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_file_object "
                        + "WHERE system_id=? AND tenant_id=?",
                Long.class, system, tenant);
        var fileVersionsBeforeContextReads = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(version),0) FROM un_file_object "
                        + "WHERE system_id=? AND tenant_id=?",
                Long.class, system, tenant);
        var fileReferenceCountBeforeContextReads = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_file_reference "
                        + "WHERE system_id=? AND tenant_id=?",
                Long.class, system, tenant);
        var dataSourceCountBeforeContextReads = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_data_source "
                        + "WHERE system_id=? AND tenant_id=?",
                Long.class, system, tenant);
        var dataSourceVersionsBeforeContextReads = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(version + draft_version),0) "
                        + "FROM un_module_data_source "
                        + "WHERE system_id=? AND tenant_id=?",
                Long.class, system, tenant);
        var publishedSourceCountBeforeContextReads = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_data_source_version "
                        + "WHERE system_id=? AND tenant_id=?",
                Long.class, system, tenant);
        var publishedSourceVersionsBeforeContextReads = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(version_no + source_draft_version),0) "
                        + "FROM un_module_data_source_version "
                        + "WHERE system_id=? AND tenant_id=?",
                Long.class, system, tenant);
        var moduleReportCountBeforeContextReads = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_report "
                        + "WHERE system_id=? AND tenant_id=?",
                Long.class, system, tenant);
        var moduleReportVersionsBeforeContextReads = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(version + draft_version),0) "
                        + "FROM un_module_report "
                        + "WHERE system_id=? AND tenant_id=?",
                Long.class, system, tenant);
        var publishedModuleReportCountBeforeContextReads = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_report_version "
                        + "WHERE system_id=? AND tenant_id=?",
                Long.class, system, tenant);
        var publishedModuleReportVersionsBeforeContextReads = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(version_no + source_draft_version),0) "
                        + "FROM un_module_report_version "
                        + "WHERE system_id=? AND tenant_id=?",
                Long.class, system, tenant);

        var recordContext = client.postWithCsrf(
                runtimeRoot + "/sessions/" + sessionId + "/messages",
                json(Map.of("content", "AI_RECORD_CONTEXT_83=" + aiRecordId)),
                Map.of("Idempotency-Key", key()));
        assertOk(recordContext);
        assertThat(text(recordContext.body(), "/data/status")).isEqualTo("SUCCEEDED");
        assertThat(text(recordContext.body(), "/data/contextResult/operation"))
                .isEqualTo("RECORD_CONTEXT_SUMMARY");
        assertThat(text(recordContext.body(), "/data/contextResult/record/moduleCode"))
                .isEqualTo(FLOW_RECORD_MODULE_CODE);
        assertThat(text(recordContext.body(), "/data/contextResult/record/recordId"))
                .isEqualTo(aiRecordId);
        assertThat(recordContext.body().at(
                "/data/contextResult/record/values/approval_status").isMissingNode())
                .isFalse();
        assertThat(absentOrNull(recordContext.body().at("/data/confirmation"))).isTrue();

        var nativeRuntimeTrend = client.postWithCsrf(
                "/api/v1/systems/" + systemId + "/data-sources/"
                        + publishedSource.code() + ":statistics",
                json(Map.of(
                        "aggregation", "COUNT",
                        "trend", Map.of(
                                "fieldCode", "event_time",
                                "grain", "DAY",
                                "startInclusive", "2026-07-01",
                                "endExclusive", "2026-07-04"))),
                Map.of());
        assertOk(nativeRuntimeTrend);
        var runtimeStatistics = client.postWithCsrf(
                runtimeRoot + "/sessions/" + sessionId + "/messages",
                json(Map.of("content", "AI_RUNTIME_STATISTICS_TREND_94")),
                Map.of("Idempotency-Key", key()));
        assertOk(runtimeStatistics);
        assertThat(text(runtimeStatistics.body(), "/data/status"))
                .isEqualTo("SUCCEEDED");
        assertThat(text(runtimeStatistics.body(),
                "/data/contextResult/operation"))
                .isEqualTo("RUNTIME_STATISTICS_QUERY");
        var runtimeStatisticsResult = runtimeStatistics.body().at(
                "/data/contextResult/runtimeStatistics");
        assertThat(runtimeStatisticsResult.path("dataSourceCode").asText())
                .isEqualTo(publishedSource.code());
        assertThat(runtimeStatisticsResult.path("moduleCode").asText())
                .isEqualTo(FLOW_RECORD_MODULE_CODE);
        assertThat(runtimeStatisticsResult.path("dataSourceVersionNumber").asInt())
                .isEqualTo(nativeRuntimeTrend.body().at(
                        "/data/dataSourceVersionNumber").asInt());
        assertThat(runtimeStatisticsResult.path("aggregation").asText())
                .isEqualTo("COUNT");
        assertThat(runtimeStatisticsResult.path("value").asText())
                .isEqualTo(nativeRuntimeTrend.body().at("/data/value").asText());
        assertThat(runtimeStatisticsResult.path("matchedRecordCount").asLong())
                .isEqualTo(nativeRuntimeTrend.body().at(
                        "/data/matchedRecordCount").asLong());
        assertThat(runtimeStatisticsResult.path("bucketCount").asInt())
                .isEqualTo(nativeRuntimeTrend.body().at("/data/bucketCount").asInt());
        assertThat(runtimeStatisticsResult.path("totalBucketCount").asLong())
                .isEqualTo(nativeRuntimeTrend.body().at(
                        "/data/totalBucketCount").asLong());
        assertThat(runtimeStatisticsResult.at("/trend/buckets"))
                .hasSize(3)
                .allSatisfy(bucket -> {
                    assertThat(bucket.path("empty").asBoolean()).isTrue();
                    assertThat(bucket.path("value").asText()).isEqualTo("0");
                    assertThat(bucket.path("recordCount").asLong()).isZero();
                });
        assertThat(runtimeStatisticsResult.at("/trend/buckets"))
                .isEqualTo(nativeRuntimeTrend.body().at("/data/trendBuckets"));
        assertThat(absentOrNull(runtimeStatisticsResult.path("grouping"))).isTrue();
        assertThat(absentOrNull(runtimeStatistics.body().at(
                "/data/contextResult/workMetrics"))).isTrue();
        assertThat(absentOrNull(runtimeStatistics.body().at(
                "/data/confirmation"))).isTrue();

        var nativeRuntimeReportMetadata = client.get(
                "/api/v1/systems/" + systemId + "/reports/"
                        + publishedReportCode);
        assertOk(nativeRuntimeReportMetadata);
        var nativeRuntimeReportRows = client.get(
                "/api/v1/systems/" + systemId + "/reports/"
                        + publishedReportCode + "/rows?page=1&size=3");
        assertOk(nativeRuntimeReportRows);
        var runtimeReport = client.postWithCsrf(
                runtimeRoot + "/sessions/" + sessionId + "/messages",
                json(Map.of("content", "AI_RUNTIME_REPORT_QUERY_95")),
                Map.of("Idempotency-Key", key()));
        assertOk(runtimeReport);
        assertThat(text(runtimeReport.body(), "/data/status"))
                .isEqualTo("SUCCEEDED");
        assertThat(text(runtimeReport.body(), "/data/contextResult/operation"))
                .isEqualTo("RUNTIME_REPORT_QUERY");
        var runtimeReportResult = runtimeReport.body().at(
                "/data/contextResult/runtimeReport");
        var runtimeReportFieldNames = new java.util.ArrayList<String>();
        runtimeReportResult.fieldNames().forEachRemaining(
                runtimeReportFieldNames::add);
        assertThat(runtimeReportFieldNames).containsExactlyInAnyOrder(
                "reportCode", "reportName", "reportVersionNumber",
                "dataSourceCode", "dataSourceVersionNumber", "moduleCode",
                "page", "size", "total", "returnedRows", "hasMore",
                "route", "fields", "rows");
        assertThat(runtimeReportResult.path("reportCode").asText())
                .isEqualTo(publishedReportCode);
        assertThat(runtimeReportResult.path("reportName").asText())
                .isEqualTo(nativeRuntimeReportMetadata.body().at(
                        "/data/name").asText());
        assertThat(runtimeReportResult.path("reportVersionNumber").asInt())
                .isEqualTo(nativeRuntimeReportMetadata.body().at(
                        "/data/versionNumber").asInt());
        assertThat(runtimeReportResult.path("dataSourceCode").asText())
                .isEqualTo(nativeRuntimeReportMetadata.body().at(
                        "/data/dataSourceCode").asText());
        assertThat(runtimeReportResult.path("dataSourceVersionNumber").asInt())
                .isEqualTo(nativeRuntimeReportMetadata.body().at(
                        "/data/dataSourceVersionNumber").asInt());
        assertThat(runtimeReportResult.path("moduleCode").asText())
                .isEqualTo(FLOW_RECORD_MODULE_CODE);
        assertThat(runtimeReportResult.path("page").asInt()).isOne();
        assertThat(runtimeReportResult.path("size").asInt()).isEqualTo(3);
        assertThat(runtimeReportResult.path("total").asLong())
                .isEqualTo(nativeRuntimeReportRows.body().at(
                        "/data/total").asLong());
        assertThat(runtimeReportResult.path("returnedRows").asInt())
                .isEqualTo(nativeRuntimeReportRows.body().at(
                        "/data/rows").size());
        assertThat(runtimeReportResult.path("hasMore").asBoolean()).isFalse();
        assertThat(runtimeReportResult.path("route").asText())
                .isEqualTo("/systems/" + systemId + "/reports/"
                        + publishedReportCode);
        assertThat(runtimeReportResult.at("/fields"))
                .isEqualTo(nativeRuntimeReportMetadata.body().at("/data/fields"));
        assertThat(runtimeReportResult.at("/fields"))
                .extracting(field -> field.path("fieldCode").asText())
                .containsExactly("event_time", "route");
        assertThat(runtimeReportResult.at("/rows"))
                .hasSize(nativeRuntimeReportRows.body().at("/data/rows").size());
        for (var rowIndex = 0;
             rowIndex < runtimeReportResult.at("/rows").size();
             rowIndex++) {
            var agentRow = runtimeReportResult.at("/rows/" + rowIndex);
            var nativeRow = nativeRuntimeReportRows.body().at(
                    "/data/rows/" + rowIndex);
            var rowFieldNames = new java.util.ArrayList<String>();
            agentRow.fieldNames().forEachRemaining(rowFieldNames::add);
            assertThat(rowFieldNames).containsExactly("values");
            assertThat(agentRow.at("/values"))
                    .extracting(value -> value.path("fieldCode").asText())
                    .containsExactly("event_time", "route");
            for (var valueIndex = 0;
                 valueIndex < agentRow.at("/values").size();
                 valueIndex++) {
                var agentValue = agentRow.at("/values/" + valueIndex);
                var nativeValue = nativeRow.at("/values/" + valueIndex);
                var valueFieldNames = new java.util.ArrayList<String>();
                agentValue.fieldNames().forEachRemaining(valueFieldNames::add);
                assertThat(valueFieldNames)
                        .containsExactlyInAnyOrder("fieldCode", "displayValue");
                assertThat(agentValue.path("fieldCode"))
                        .isEqualTo(nativeValue.path("fieldCode"));
                var nativeDisplayValue = nativeValue.path("displayValue");
                if (nativeDisplayValue.isMissingNode()
                        || nativeDisplayValue.isNull()) {
                    assertThat(agentValue.path("displayValue").isNull()).isTrue();
                } else {
                    assertThat(agentValue.path("displayValue"))
                            .isEqualTo(nativeDisplayValue);
                }
                assertThat(agentValue.path("value").isMissingNode()).isTrue();
            }
            assertThat(agentRow.path("recordId").isMissingNode()).isTrue();
        }
        assertThat(runtimeReportResult.path("queryHash").isMissingNode()).isTrue();
        assertThat(absentOrNull(runtimeReport.body().at(
                "/data/contextResult/runtimeStatistics"))).isTrue();
        assertThat(absentOrNull(runtimeReport.body().at(
                "/data/confirmation"))).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_report "
                        + "WHERE system_id=? AND tenant_id=?",
                Long.class, system, tenant))
                .isEqualTo(moduleReportCountBeforeContextReads);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(version + draft_version),0) "
                        + "FROM un_module_report "
                        + "WHERE system_id=? AND tenant_id=?",
                Long.class, system, tenant))
                .isEqualTo(moduleReportVersionsBeforeContextReads);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_report_version "
                        + "WHERE system_id=? AND tenant_id=?",
                Long.class, system, tenant))
                .isEqualTo(publishedModuleReportCountBeforeContextReads);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(version_no + source_draft_version),0) "
                        + "FROM un_module_report_version "
                        + "WHERE system_id=? AND tenant_id=?",
                Long.class, system, tenant))
                .isEqualTo(publishedModuleReportVersionsBeforeContextReads);

        var recordActivityRoute = "/systems/" + systemId
                + "/workbench?module=" + FLOW_RECORD_MODULE_CODE
                + "&mode=view&record=" + aiRecordId;
        var recordActivityMarker = FLOW_RECORD_MODULE_CODE + "|" + aiRecordId + "|3";
        var recordComments = client.postWithCsrf(
                runtimeRoot + "/sessions/" + sessionId + "/messages",
                json(Map.of("content", "AI_RECORD_COMMENT_QUERY_90="
                        + recordActivityMarker)),
                Map.of("Idempotency-Key", key()));
        assertOk(recordComments);
        assertThat(text(recordComments.body(), "/data/status"))
                .isEqualTo("SUCCEEDED");
        assertThat(text(recordComments.body(), "/data/contextResult/operation"))
                .isEqualTo("RECORD_COMMENT_QUERY");
        var comments = recordComments.body().at(
                "/data/contextResult/recordComments");
        assertThat(comments.path("moduleCode").asText())
                .isEqualTo(FLOW_RECORD_MODULE_CODE);
        assertThat(comments.path("recordId").asText()).isEqualTo(aiRecordId);
        assertThat(comments.path("total").asLong())
                .isEqualTo(nativeRecordComments.body().at("/data/total").asLong());
        assertThat(comments.path("route").asText()).isEqualTo(recordActivityRoute);
        assertThat(comments.path("items")).hasSize(1).first().satisfies(comment -> {
            var nativeComment = nativeRecordComments.body().at("/data/items/0");
            assertThat(comment.path("commentId").asText()).isEqualTo(recordCommentId);
            assertThat(comment.path("commentId").asText())
                    .isEqualTo(nativeComment.path("commentId").asText());
            assertThat(comment.path("body").asText()).isEqualTo(recordCommentBody);
            assertThat(comment.path("authorMemberId").asText())
                    .isEqualTo(nativeComment.path("authorMemberId").asText());
            assertThat(comment.path("version").asLong())
                    .isEqualTo(nativeComment.path("version").asLong());
            assertThat(comment.path("deleted").asBoolean()).isFalse();
        });
        assertThat(absentOrNull(recordComments.body().at(
                "/data/contextResult/recordHistory"))).isTrue();
        assertThat(absentOrNull(recordComments.body().at(
                "/data/contextResult/recordFiles"))).isTrue();
        assertThat(absentOrNull(recordComments.body().at("/data/confirmation"))).isTrue();

        var recordHistory = client.postWithCsrf(
                runtimeRoot + "/sessions/" + sessionId + "/messages",
                json(Map.of("content", "AI_RECORD_HISTORY_QUERY_90="
                        + recordActivityMarker)),
                Map.of("Idempotency-Key", key()));
        assertOk(recordHistory);
        assertThat(text(recordHistory.body(), "/data/status"))
                .isEqualTo("SUCCEEDED");
        assertThat(text(recordHistory.body(), "/data/contextResult/operation"))
                .isEqualTo("RECORD_HISTORY_QUERY");
        var histories = recordHistory.body().at(
                "/data/contextResult/recordHistory");
        assertThat(histories.path("moduleCode").asText())
                .isEqualTo(FLOW_RECORD_MODULE_CODE);
        assertThat(histories.path("recordId").asText()).isEqualTo(aiRecordId);
        assertThat(histories.path("total").asLong())
                .isEqualTo(nativeRecordHistory.body().at("/data/total").asLong());
        assertThat(histories.path("route").asText()).isEqualTo(recordActivityRoute);
        assertThat(histories.path("items").size())
                .isEqualTo(nativeRecordHistory.body().at("/data/items").size());
        for (var index = 0; index < histories.path("items").size(); index++) {
            var history = histories.path("items").path(index);
            var nativeHistory = nativeRecordHistory.body().at("/data/items").path(index);
            assertThat(history.path("historyId").asText())
                    .isEqualTo(nativeHistory.path("historyId").asText());
            assertThat(history.path("recordVersion").asLong())
                    .isEqualTo(nativeHistory.path("recordVersion").asLong());
            assertThat(history.path("action").asText())
                    .isEqualTo(nativeHistory.path("action").asText());
            assertThat(history.path("diff").size())
                    .isEqualTo(nativeHistory.path("diff").size());
            for (var diffIndex = 0;
                    diffIndex < history.path("diff").size(); diffIndex++) {
                var diff = history.path("diff").path(diffIndex);
                var nativeDiff = nativeHistory.path("diff").path(diffIndex);
                assertThat(diff.path("fieldCode").asText())
                        .isEqualTo(nativeDiff.path("fieldCode").asText());
                assertThat(diff.path("masked").asBoolean())
                        .isEqualTo(nativeDiff.path("masked").asBoolean());
                if (diff.path("masked").asBoolean()) {
                    assertThat(absentOrNull(diff.path("beforeValueJson"))).isTrue();
                    assertThat(absentOrNull(diff.path("afterValueJson"))).isTrue();
                }
            }
        }
        assertThat(absentOrNull(recordHistory.body().at(
                "/data/contextResult/recordComments"))).isTrue();
        assertThat(absentOrNull(recordHistory.body().at(
                "/data/contextResult/recordFiles"))).isTrue();
        assertThat(absentOrNull(recordHistory.body().at("/data/confirmation"))).isTrue();

        var recordFiles = client.postWithCsrf(
                runtimeRoot + "/sessions/" + sessionId + "/messages",
                json(Map.of("content", "AI_RECORD_FILE_QUERY_90="
                        + recordActivityMarker)),
                Map.of("Idempotency-Key", key()));
        assertOk(recordFiles);
        assertThat(text(recordFiles.body(), "/data/status"))
                .isEqualTo("SUCCEEDED");
        assertThat(text(recordFiles.body(), "/data/contextResult/operation"))
                .isEqualTo("RECORD_FILE_QUERY");
        var files = recordFiles.body().at("/data/contextResult/recordFiles");
        assertThat(files.path("moduleCode").asText())
                .isEqualTo(FLOW_RECORD_MODULE_CODE);
        assertThat(files.path("recordId").asText()).isEqualTo(aiRecordId);
        assertThat(files.path("total").asLong())
                .isEqualTo(nativeRecordFiles.body().at("/data/total").asLong());
        assertThat(files.path("route").asText()).isEqualTo(recordActivityRoute);
        assertThat(files.path("items")).hasSize(1).first().satisfies(file -> {
            var nativeFile = nativeRecordFiles.body().at("/data/items/0");
            assertThat(file.path("fileId").asText()).isEqualTo(recordFileId);
            assertThat(file.path("fileId").asText())
                    .isEqualTo(nativeFile.path("fileId").asText());
            assertThat(file.path("originalName").asText()).isEqualTo(recordFileName);
            assertThat(file.path("mediaType").asText()).isEqualTo("text/plain");
            assertThat(file.path("size").asLong()).isEqualTo(recordFileContent.length);
            assertThat(file.path("uploaderMemberId").asText())
                    .isEqualTo(nativeFile.path("uploaderMemberId").asText());
            assertThat(file.has("referencedByMemberId")).isFalse();
            assertThat(file.has("downloadUrl")).isFalse();
            assertThat(file.has("objectKey")).isFalse();
            assertThat(file.has("sha256")).isFalse();
        });
        assertThat(recordFiles.body().toString())
                .doesNotContain(recordFileContentText);
        assertThat(absentOrNull(recordFiles.body().at(
                "/data/contextResult/recordComments"))).isTrue();
        assertThat(absentOrNull(recordFiles.body().at(
                "/data/contextResult/recordHistory"))).isTrue();
        assertThat(absentOrNull(recordFiles.body().at("/data/confirmation"))).isTrue();

        var summaryCallsAfterRecordActivitySuccess = aiProviderFixture.summaryCalls();
        var recordCommentViewPermission = "module."
                + FLOW_RECORD_MODULE_CODE + ".view";
        jdbcTemplate.update(
                "UPDATE un_plat_permission SET status='DISABLED',"
                        + "updated_at=UTC_TIMESTAMP(3),version=version+1 "
                        + "WHERE system_id=? AND permission_code=?",
                system, recordCommentViewPermission);
        authzEpochService.bumpSystem(system, rootAccountId);
        try {
            var refreshedWithoutRecordView = client.postWithCsrf(
                    "/api/v1/auth/refresh", "{}", Map.of());
            assertOk(refreshedWithoutRecordView);
            assertThat(refreshedWithoutRecordView.body().at(
                    "/data/context/permissions"))
                    .extracting(JsonNode::asText)
                    .doesNotContain(recordCommentViewPermission);
            var deniedRecordComments = client.postWithCsrf(
                    runtimeRoot + "/sessions/" + sessionId + "/messages",
                    json(Map.of("content", "AI_RECORD_COMMENT_QUERY_90_REVOKED="
                            + recordActivityMarker)),
                    Map.of("Idempotency-Key", key()));
            assertOk(deniedRecordComments);
            assertThat(text(deniedRecordComments.body(), "/data/status"))
                    .isEqualTo("FAILED");
            assertThat(text(deniedRecordComments.body(), "/data/errorCode"))
                    .isEqualTo("PERMISSION_DENIED");
            assertThat(absentOrNull(deniedRecordComments.body().at(
                    "/data/contextResult"))).isTrue();
            assertThat(aiProviderFixture.summaryCalls())
                    .isEqualTo(summaryCallsAfterRecordActivitySuccess);
        } finally {
            jdbcTemplate.update(
                    "UPDATE un_plat_permission SET status='ACTIVE',"
                            + "updated_at=UTC_TIMESTAMP(3),version=version+1 "
                            + "WHERE system_id=? AND permission_code=?",
                    system, recordCommentViewPermission);
            authzEpochService.bumpSystem(system, rootAccountId);
            assertOk(client.postWithCsrf(
                    "/api/v1/auth/refresh", "{}", Map.of()));
        }

        var recordHistoryPermission = "module."
                + FLOW_RECORD_MODULE_CODE + ".history.read";
        jdbcTemplate.update(
                "UPDATE un_plat_permission SET status='DISABLED',"
                        + "updated_at=UTC_TIMESTAMP(3),version=version+1 "
                        + "WHERE system_id=? AND permission_code=?",
                system, recordHistoryPermission);
        authzEpochService.bumpSystem(system, rootAccountId);
        try {
            var refreshedWithoutHistory = client.postWithCsrf(
                    "/api/v1/auth/refresh", "{}", Map.of());
            assertOk(refreshedWithoutHistory);
            assertThat(refreshedWithoutHistory.body().at(
                    "/data/context/permissions"))
                    .extracting(JsonNode::asText)
                    .doesNotContain(recordHistoryPermission);
            var deniedRecordHistory = client.postWithCsrf(
                    runtimeRoot + "/sessions/" + sessionId + "/messages",
                    json(Map.of("content", "AI_RECORD_HISTORY_QUERY_90_REVOKED="
                            + recordActivityMarker)),
                    Map.of("Idempotency-Key", key()));
            assertOk(deniedRecordHistory);
            assertThat(text(deniedRecordHistory.body(), "/data/status"))
                    .isEqualTo("FAILED");
            assertThat(text(deniedRecordHistory.body(), "/data/errorCode"))
                    .isEqualTo("PERMISSION_DENIED");
            assertThat(absentOrNull(deniedRecordHistory.body().at(
                    "/data/contextResult"))).isTrue();
            assertThat(aiProviderFixture.summaryCalls())
                    .isEqualTo(summaryCallsAfterRecordActivitySuccess);
        } finally {
            jdbcTemplate.update(
                    "UPDATE un_plat_permission SET status='ACTIVE',"
                            + "updated_at=UTC_TIMESTAMP(3),version=version+1 "
                            + "WHERE system_id=? AND permission_code=?",
                    system, recordHistoryPermission);
            authzEpochService.bumpSystem(system, rootAccountId);
            assertOk(client.postWithCsrf(
                    "/api/v1/auth/refresh", "{}", Map.of()));
        }

        var recordFileReadPermission = "file.read";
        jdbcTemplate.update(
                "UPDATE un_plat_permission SET status='DISABLED',"
                        + "updated_at=UTC_TIMESTAMP(3),version=version+1 "
                        + "WHERE system_id=? AND permission_code=?",
                system, recordFileReadPermission);
        authzEpochService.bumpSystem(system, rootAccountId);
        try {
            var refreshedWithoutFileRead = client.postWithCsrf(
                    "/api/v1/auth/refresh", "{}", Map.of());
            assertOk(refreshedWithoutFileRead);
            assertThat(refreshedWithoutFileRead.body().at(
                    "/data/context/permissions"))
                    .extracting(JsonNode::asText)
                    .doesNotContain(recordFileReadPermission);
            var deniedRecordFiles = client.postWithCsrf(
                    runtimeRoot + "/sessions/" + sessionId + "/messages",
                    json(Map.of("content", "AI_RECORD_FILE_QUERY_90_REVOKED="
                            + recordActivityMarker)),
                    Map.of("Idempotency-Key", key()));
            assertOk(deniedRecordFiles);
            assertThat(text(deniedRecordFiles.body(), "/data/status"))
                    .isEqualTo("FAILED");
            assertThat(text(deniedRecordFiles.body(), "/data/errorCode"))
                    .isEqualTo("FILE_FORBIDDEN");
            assertThat(absentOrNull(deniedRecordFiles.body().at(
                    "/data/contextResult"))).isTrue();
            assertThat(aiProviderFixture.summaryCalls())
                    .isEqualTo(summaryCallsAfterRecordActivitySuccess);
        } finally {
            jdbcTemplate.update(
                    "UPDATE un_plat_permission SET status='ACTIVE',"
                            + "updated_at=UTC_TIMESTAMP(3),version=version+1 "
                            + "WHERE system_id=? AND permission_code=?",
                    system, recordFileReadPermission);
            authzEpochService.bumpSystem(system, rootAccountId);
            assertOk(client.postWithCsrf(
                    "/api/v1/auth/refresh", "{}", Map.of()));
        }

        var workTasks = client.postWithCsrf(
                runtimeRoot + "/sessions/" + sessionId + "/messages",
                json(Map.of("content", "AI_WORK_TASK_QUERY_83")),
                Map.of("Idempotency-Key", key()));
        assertOk(workTasks);
        assertThat(text(workTasks.body(), "/data/status")).isEqualTo("SUCCEEDED");
        assertThat(text(workTasks.body(), "/data/contextResult/operation"))
                .isEqualTo("WORK_TASK_QUERY");
        assertThat(workTasks.body().at("/data/contextResult/tasks")).isNotEmpty();
        assertThat(absentOrNull(workTasks.body().at("/data/confirmation"))).isTrue();

        var workReports = client.postWithCsrf(
                runtimeRoot + "/sessions/" + sessionId + "/messages",
                json(Map.of("content", "AI_WORK_DAILY_REPORT_QUERY_83")),
                Map.of("Idempotency-Key", key()));
        assertOk(workReports);
        assertThat(text(workReports.body(), "/data/status")).isEqualTo("SUCCEEDED");
        assertThat(text(workReports.body(), "/data/contextResult/operation"))
                .isEqualTo("WORK_DAILY_REPORT_QUERY");
        assertThat(workReports.body().at("/data/contextResult/reports"))
                .anySatisfy(report -> {
                    assertThat(report.path("reportId").asText()).isEqualTo(workReportId);
                    assertThat(report.path("completedWork").asText())
                            .isEqualTo(reportCompleted);
                });
        assertThat(absentOrNull(workReports.body().at("/data/confirmation"))).isTrue();

        var workMetricsPrompt = "AI_WORK_PROJECT_METRICS_QUERY_89="
                + aiMetricsProjectId + "|" + aiMetricsFrom + "|" + aiMetricsTo;
        var workMetrics = client.postWithCsrf(
                runtimeRoot + "/sessions/" + sessionId + "/messages",
                json(Map.of("content", workMetricsPrompt)),
                Map.of("Idempotency-Key", key()));
        assertOk(workMetrics);
        assertThat(text(workMetrics.body(), "/data/status")).isEqualTo("SUCCEEDED");
        assertThat(text(workMetrics.body(), "/data/contextResult/operation"))
                .isEqualTo("WORK_PROJECT_METRICS_QUERY");
        var metrics = workMetrics.body().at("/data/contextResult/workMetrics");
        assertThat(metrics.path("projectId").asText()).isEqualTo(aiMetricsProjectId);
        assertThat(metrics.path("title").asText()).isEqualTo(aiMetricsProjectTitle);
        assertThat(metrics.path("status").asText()).isEqualTo("ACTIVE");
        assertThat(metrics.path("visibility").asText()).isEqualTo("ALL");
        assertThat(metrics.path("fromInclusive").asText())
                .isEqualTo(aiMetricsFrom.toString());
        assertThat(metrics.path("toExclusive").asText())
                .isEqualTo(aiMetricsTo.toString());
        assertThat(metrics.path("total").asLong()).isEqualTo(2);
        assertThat(metrics.path("open").asLong()).isOne();
        assertThat(metrics.path("completed").asLong()).isOne();
        assertThat(metrics.path("overdueOpen").path("count").asLong()).isOne();
        assertThat(metrics.path("dueInRangeOpen").path("count").asLong()).isOne();
        assertThat(metrics.path("completedInRange").path("count").asLong()).isOne();
        for (var metricName : List.of(
                "overdueOpen", "dueInRangeOpen", "completedInRange")) {
            assertThat(metrics.path(metricName).path("route").asText())
                    .contains("projectId=" + aiMetricsProjectId);
        }
        assertThat(metrics.path("daily"))
                .anySatisfy(day -> {
                    assertThat(day.path("date").asText())
                            .isEqualTo(reportDate.toString());
                    assertThat(day.path("createdCount").asLong()).isEqualTo(2);
                    assertThat(day.path("completedCount").asLong()).isOne();
                    assertThat(day.path("createdRoute").asText())
                            .contains("projectId=" + aiMetricsProjectId);
                    assertThat(day.path("completedRoute").asText())
                            .contains("projectId=" + aiMetricsProjectId);
                });
        assertThat(metrics.path("topAssignees"))
                .anySatisfy(assignee -> {
                    assertThat(assignee.path("assigneeMemberId").asText())
                            .isEqualTo(secondMemberId);
                    assertThat(assignee.path("openCount").asLong()).isOne();
                    assertThat(assignee.path("route").asText())
                            .contains("projectId=" + aiMetricsProjectId);
                });
        assertThat(workMetrics.body().at("/data/contextResult/tasks")).isEmpty();
        assertThat(workMetrics.body().at("/data/contextResult/reports")).isEmpty();
        assertThat(absentOrNull(workMetrics.body().at("/data/confirmation"))).isTrue();

        var metricsDrillRoute = metrics.path("overdueOpen").path("route").asText();
        var parsedMetricsDrillRoute = URI.create(metricsDrillRoute);
        assertThat(parsedMetricsDrillRoute.getPath())
                .isEqualTo("/systems/" + systemId + "/tasks");
        var metricsDrillQuery = parsedMetricsDrillRoute.getRawQuery();
        assertThat(metricsDrillQuery).contains("projectId=" + aiMetricsProjectId);
        var metricsDrill = client.get(aiMetricsTaskRoot + "?" + metricsDrillQuery);
        assertOk(metricsDrill);
        assertThat(metricsDrill.body().at("/data/total").asLong()).isOne();
        assertThat(metricsDrill.body().at("/data/items"))
                .allSatisfy(item -> assertThat(item.path("projectId").asText())
                        .isEqualTo(aiMetricsProjectId));

        var aiMetricsMembers = client.get(
                aiMetricsProjectRoot + "/" + aiMetricsProjectId + "/members");
        assertOk(aiMetricsMembers);
        var aiMetricsRootMembership = StreamSupport.stream(
                        aiMetricsMembers.body().at("/data").spliterator(), false)
                .filter(member -> aiRootMemberId.equals(
                        member.path("memberId").asText()))
                .findFirst().orElseThrow();
        var removedAiMetricsRoot = client.postWithCsrf(
                aiMetricsProjectRoot + "/" + aiMetricsProjectId + "/members/"
                        + aiRootMemberId + ":remove",
                json(Map.of("version",
                        aiMetricsRootMembership.path("version").asLong())),
                Map.of());
        assertOk(removedAiMetricsRoot);
        assertThat(text(removedAiMetricsRoot.body(), "/data/memberId"))
                .isEqualTo(aiRootMemberId);
        assertThat(text(removedAiMetricsRoot.body(), "/data/status"))
                .isEqualTo("REMOVED");
        assertThat(removedAiMetricsRoot.body().at("/data/version").asLong())
                .isEqualTo(aiMetricsRootMembership.path("version").asLong() + 1);
        assertThat(text(aiMetricsSecondOwner.body(), "/data/memberId"))
                .isEqualTo(secondMemberId);
        assertThat(text(aiMetricsSecondOwner.body(), "/data/role"))
                .isEqualTo("OWNER");
        assertThat(text(aiMetricsSecondOwner.body(), "/data/status"))
                .isEqualTo("ACTIVE");
        var projectMemberCountAfterExpectedRemoval = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_work_project_member "
                        + "WHERE system_id=? AND tenant_id=?",
                Long.class, system, tenant);
        var projectMemberVersionsAfterExpectedRemoval = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(version),0) FROM un_work_project_member "
                        + "WHERE system_id=? AND tenant_id=?",
                Long.class, system, tenant);
        var summaryCallsAfterSuccessfulMetrics = aiProviderFixture.summaryCalls();
        var projectManagePermission = "work.project.manage";
        jdbcTemplate.update(
                "UPDATE un_plat_permission SET status='DISABLED',"
                        + "updated_at=UTC_TIMESTAMP(3),version=version+1 "
                        + "WHERE system_id=? AND permission_code=?",
                system, projectManagePermission);
        authzEpochService.bumpSystem(system, rootAccountId);
        try {
            var hiddenProjectRefresh = client.postWithCsrf(
                    "/api/v1/auth/refresh", "{}", Map.of());
            assertOk(hiddenProjectRefresh);
            assertThat(hiddenProjectRefresh.body().at("/data/context/permissions"))
                    .extracting(JsonNode::asText)
                    .doesNotContain(projectManagePermission);
            var hiddenProjectMetrics = client.postWithCsrf(
                    runtimeRoot + "/sessions/" + sessionId + "/messages",
                    json(Map.of("content", "AI_WORK_PROJECT_METRICS_QUERY_89_HIDDEN="
                            + aiMetricsProjectId + "|" + aiMetricsFrom + "|"
                            + aiMetricsTo)),
                    Map.of("Idempotency-Key", key()));
            assertOk(hiddenProjectMetrics);
            assertThat(text(hiddenProjectMetrics.body(), "/data/status"))
                    .isEqualTo("FAILED");
            assertThat(text(hiddenProjectMetrics.body(), "/data/errorCode"))
                    .isEqualTo("WORK_PROJECT_NOT_FOUND");
            assertThat(absentOrNull(hiddenProjectMetrics.body().at(
                    "/data/contextResult"))).isTrue();
            assertThat(aiProviderFixture.summaryCalls())
                    .isEqualTo(summaryCallsAfterSuccessfulMetrics);
        } finally {
            jdbcTemplate.update(
                    "UPDATE un_plat_permission SET status='ACTIVE',"
                            + "updated_at=UTC_TIMESTAMP(3),version=version+1 "
                            + "WHERE system_id=? AND permission_code=?",
                    system, projectManagePermission);
            authzEpochService.bumpSystem(system, rootAccountId);
            assertOk(client.postWithCsrf(
                    "/api/v1/auth/refresh", "{}", Map.of()));
        }

        var workAccessPermission = "work.task.access";
        jdbcTemplate.update(
                "UPDATE un_plat_permission SET status='DISABLED',"
                        + "updated_at=UTC_TIMESTAMP(3),version=version+1 "
                        + "WHERE system_id=? AND permission_code=?",
                system, workAccessPermission);
        authzEpochService.bumpSystem(system, rootAccountId);
        try {
            var workAccessRefresh = client.postWithCsrf(
                    "/api/v1/auth/refresh", "{}", Map.of());
            assertOk(workAccessRefresh);
            assertThat(workAccessRefresh.body().at("/data/context/permissions"))
                    .extracting(JsonNode::asText)
                    .doesNotContain(workAccessPermission);
            var deniedWorkMetrics = client.postWithCsrf(
                    runtimeRoot + "/sessions/" + sessionId + "/messages",
                    json(Map.of("content", "AI_WORK_PROJECT_METRICS_QUERY_89_REVOKED="
                            + aiMetricsProjectId + "|" + aiMetricsFrom + "|"
                            + aiMetricsTo)),
                    Map.of("Idempotency-Key", key()));
            assertOk(deniedWorkMetrics);
            assertThat(text(deniedWorkMetrics.body(), "/data/status"))
                    .isEqualTo("FAILED");
            assertThat(text(deniedWorkMetrics.body(), "/data/errorCode"))
                    .isEqualTo("WORK_PROJECT_FORBIDDEN");
            assertThat(absentOrNull(deniedWorkMetrics.body().at(
                    "/data/contextResult"))).isTrue();
            assertThat(aiProviderFixture.summaryCalls())
                    .isEqualTo(summaryCallsAfterSuccessfulMetrics);
        } finally {
            jdbcTemplate.update(
                    "UPDATE un_plat_permission SET status='ACTIVE',"
                            + "updated_at=UTC_TIMESTAMP(3),version=version+1 "
                            + "WHERE system_id=? AND permission_code=?",
                    system, workAccessPermission);
            authzEpochService.bumpSystem(system, rootAccountId);
            assertOk(client.postWithCsrf(
                    "/api/v1/auth/refresh", "{}", Map.of()));
        }

        var rootMemberId = jdbcTemplate.queryForObject(
                "SELECT id FROM un_plat_member "
                        + "WHERE system_id=? AND account_id=? AND status='ACTIVE'",
                Long.class, system, rootAccountId);
        var todoReadRoot = "/api/v1/systems/" + systemId + "/todos";
        var expectedTodos = client.get(todoReadRoot
                + "?category=ALL&state=ALL&time=ALL&page=1&size=1");
        assertOk(expectedTodos);
        assertThat(expectedTodos.body().at("/data/items")).hasSize(1);
        var expectedTodo = expectedTodos.body().at("/data/items/0");
        assertThat(expectedTodo.path("recipientMemberId").asText())
                .isEqualTo(Long.toString(rootMemberId));
        var expectedTodoCounts = client.get(todoReadRoot + "/counts");
        assertOk(expectedTodoCounts);

        var messageReadRoot = "/api/v1/systems/" + systemId + "/event/messages";
        var expectedMessages = client.get(
                messageReadRoot + "?status=UNREAD&page=1&size=1");
        assertOk(expectedMessages);
        assertThat(expectedMessages.body().at("/data/items")).hasSize(1);
        var expectedMessage = expectedMessages.body().at("/data/items/0");
        assertThat(expectedMessage.path("recipientMemberId").asText())
                .isEqualTo(Long.toString(rootMemberId));
        var expectedUnread = client.get(messageReadRoot + "/unread-count");
        assertOk(expectedUnread);

        var todoRowsBeforeAiReads = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_todo_item "
                        + "WHERE system_id=? AND tenant_id=? AND recipient_member_id=?",
                Long.class, system, tenant, rootMemberId);
        var todoVersionsBeforeAiReads = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(version),0) FROM un_todo_item "
                        + "WHERE system_id=? AND tenant_id=? AND recipient_member_id=?",
                Long.class, system, tenant, rootMemberId);
        var messageRowsBeforeAiReads = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_event_message "
                        + "WHERE system_id=? AND tenant_id=? AND recipient_member_id=?",
                Long.class, system, tenant, rootMemberId);
        var messageVersionsBeforeAiReads = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(version),0) FROM un_event_message "
                        + "WHERE system_id=? AND tenant_id=? AND recipient_member_id=?",
                Long.class, system, tenant, rootMemberId);

        var todoContext = client.postWithCsrf(
                runtimeRoot + "/sessions/" + sessionId + "/messages",
                json(Map.of("content", "AI_TODO_QUERY_87")),
                Map.of("Idempotency-Key", key()));
        assertOk(todoContext);
        assertThat(text(todoContext.body(), "/data/status")).isEqualTo("SUCCEEDED");
        assertThat(text(todoContext.body(), "/data/contextResult/operation"))
                .isEqualTo("TODO_QUERY");
        assertThat(text(todoContext.body(), "/data/contextResult/todos/category"))
                .isEqualTo("ALL");
        assertThat(text(todoContext.body(), "/data/contextResult/todos/state"))
                .isEqualTo("ALL");
        assertThat(text(todoContext.body(), "/data/contextResult/todos/time"))
                .isEqualTo("ALL");
        assertThat(todoContext.body().at("/data/contextResult/todos/total").asLong())
                .isEqualTo(expectedTodos.body().at("/data/total").asLong());
        assertThat(todoContext.body().at("/data/contextResult/todos/items"))
                .hasSize(1).first().satisfies(todo -> {
                    assertThat(todo.path("id").asText())
                            .isEqualTo(expectedTodo.path("id").asText());
                    assertThat(todo.path("title").asText())
                            .isEqualTo(expectedTodo.path("title").asText());
                    assertThat(todo.path("routeHint").asText())
                            .isEqualTo(expectedTodo.path("routeHint").asText());
                    assertThat(todo.path("version").asLong())
                            .isEqualTo(expectedTodo.path("version").asLong());
                });
        assertThat(todoContext.body().at("/data/contextResult/todos/counts/open").asLong())
                .isEqualTo(expectedTodoCounts.body().at("/data/openCount").asLong());
        assertThat(todoContext.body().at("/data/contextResult/todos/counts/task").asLong())
                .isEqualTo(expectedTodoCounts.body().at("/data/taskCount").asLong());
        assertThat(todoContext.body().at(
                "/data/contextResult/todos/counts/approval").asLong())
                .isEqualTo(expectedTodoCounts.body().at("/data/approvalCount").asLong());
        assertThat(absentOrNull(todoContext.body().at(
                "/data/contextResult/messages"))).isTrue();
        assertThat(absentOrNull(todoContext.body().at("/data/confirmation"))).isTrue();

        var messageContext = client.postWithCsrf(
                runtimeRoot + "/sessions/" + sessionId + "/messages",
                json(Map.of("content", "AI_MESSAGE_QUERY_87")),
                Map.of("Idempotency-Key", key()));
        assertOk(messageContext);
        assertThat(text(messageContext.body(), "/data/status")).isEqualTo("SUCCEEDED");
        assertThat(text(messageContext.body(), "/data/contextResult/operation"))
                .isEqualTo("MESSAGE_QUERY");
        assertThat(text(messageContext.body(), "/data/contextResult/messages/status"))
                .isEqualTo("UNREAD");
        assertThat(messageContext.body().at(
                "/data/contextResult/messages/unreadCount").asLong())
                .isEqualTo(expectedUnread.body().at("/data/unreadCount").asLong());
        assertThat(messageContext.body().at("/data/contextResult/messages/total").asLong())
                .isEqualTo(expectedMessages.body().at("/data/total").asLong());
        assertThat(messageContext.body().at("/data/contextResult/messages/items"))
                .hasSize(1).first().satisfies(message -> {
                    assertThat(message.path("id").asText())
                            .isEqualTo(expectedMessage.path("id").asText());
                    assertThat(message.path("templateCode").asText())
                            .isEqualTo(expectedMessage.path("templateCode").asText());
                    assertThat(message.path("title").asText())
                            .isEqualTo(expectedMessage.path("title").asText());
                    assertThat(message.path("body").asText())
                            .isEqualTo(expectedMessage.path("body").asText());
                    assertThat(message.path("target"))
                            .isEqualTo(expectedMessage.path("target"));
                    assertThat(message.path("targetPath").asText())
                            .isEqualTo(expectedMessage.path("targetPath").asText());
                    assertThat(message.path("version").asLong())
                            .isEqualTo(expectedMessage.path("version").asLong());
                });
        assertThat(absentOrNull(messageContext.body().at(
                "/data/contextResult/todos"))).isTrue();
        assertThat(absentOrNull(messageContext.body().at("/data/confirmation"))).isTrue();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_todo_item "
                        + "WHERE system_id=? AND tenant_id=? AND recipient_member_id=?",
                Long.class, system, tenant, rootMemberId)).isEqualTo(todoRowsBeforeAiReads);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(version),0) FROM un_todo_item "
                        + "WHERE system_id=? AND tenant_id=? AND recipient_member_id=?",
                Long.class, system, tenant, rootMemberId)).isEqualTo(todoVersionsBeforeAiReads);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_event_message "
                        + "WHERE system_id=? AND tenant_id=? AND recipient_member_id=?",
                Long.class, system, tenant, rootMemberId)).isEqualTo(messageRowsBeforeAiReads);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(version),0) FROM un_event_message "
                        + "WHERE system_id=? AND tenant_id=? AND recipient_member_id=?",
                Long.class, system, tenant, rootMemberId)).isEqualTo(messageVersionsBeforeAiReads);

        var messageAccessPermission = "event.message.access";
        jdbcTemplate.update(
                "UPDATE un_plat_permission SET status='DISABLED',"
                        + "updated_at=UTC_TIMESTAMP(3),version=version+1 "
                        + "WHERE system_id=? AND permission_code=?",
                system, messageAccessPermission);
        authzEpochService.bumpSystem(system, rootAccountId);
        try {
            var messagePermissionRefresh = client.postWithCsrf(
                    "/api/v1/auth/refresh", "{}", Map.of());
            assertOk(messagePermissionRefresh);
            assertThat(messagePermissionRefresh.body().at("/data/context/permissions"))
                    .extracting(JsonNode::asText)
                    .doesNotContain(messageAccessPermission);
            var deniedMessages = client.postWithCsrf(
                    runtimeRoot + "/sessions/" + sessionId + "/messages",
                    json(Map.of("content", "AI_MESSAGE_QUERY_87_REVOKED")),
                    Map.of("Idempotency-Key", key()));
            assertOk(deniedMessages);
            assertThat(text(deniedMessages.body(), "/data/status")).isEqualTo("FAILED");
            assertThat(text(deniedMessages.body(), "/data/errorCode"))
                    .isEqualTo("AI_MESSAGE_PERMISSION_DENIED");
            assertThat(absentOrNull(deniedMessages.body().at(
                    "/data/contextResult"))).isTrue();
        } finally {
            jdbcTemplate.update(
                    "UPDATE un_plat_permission SET status='ACTIVE',"
                            + "updated_at=UTC_TIMESTAMP(3),version=version+1 "
                            + "WHERE system_id=? AND permission_code=?",
                    system, messageAccessPermission);
            authzEpochService.bumpSystem(system, rootAccountId);
            var restoredMessageAccess = client.postWithCsrf(
                    "/api/v1/auth/refresh", "{}", Map.of());
            assertOk(restoredMessageAccess);
            assertThat(restoredMessageAccess.body().at("/data/context/permissions"))
                    .extracting(JsonNode::asText)
                    .contains(messageAccessPermission);
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_todo_item "
                        + "WHERE system_id=? AND tenant_id=? AND recipient_member_id=?",
                Long.class, system, tenant, rootMemberId)).isEqualTo(todoRowsBeforeAiReads);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(version),0) FROM un_todo_item "
                        + "WHERE system_id=? AND tenant_id=? AND recipient_member_id=?",
                Long.class, system, tenant, rootMemberId)).isEqualTo(todoVersionsBeforeAiReads);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_event_message "
                        + "WHERE system_id=? AND tenant_id=? AND recipient_member_id=?",
                Long.class, system, tenant, rootMemberId)).isEqualTo(messageRowsBeforeAiReads);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(version),0) FROM un_event_message "
                        + "WHERE system_id=? AND tenant_id=? AND recipient_member_id=?",
                Long.class, system, tenant, rootMemberId)).isEqualTo(messageVersionsBeforeAiReads);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_record WHERE system_id=? AND tenant_id=?",
                Long.class, system, tenant)).isEqualTo(recordCountBeforeContextReads);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_work_task WHERE system_id=? AND tenant_id=?",
                Long.class, system, tenant)).isEqualTo(taskCountBeforeContextReads);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(version),0) FROM un_work_task "
                        + "WHERE system_id=? AND tenant_id=?",
                Long.class, system, tenant)).isEqualTo(taskVersionsBeforeContextReads);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_work_project WHERE system_id=? AND tenant_id=?",
                Long.class, system, tenant)).isEqualTo(projectCountBeforeContextReads);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(version),0) FROM un_work_project "
                        + "WHERE system_id=? AND tenant_id=?",
                Long.class, system, tenant)).isEqualTo(projectVersionsBeforeContextReads);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_work_daily_report WHERE system_id=? AND tenant_id=?",
                Long.class, system, tenant)).isEqualTo(dailyReportCountBeforeContextReads);

        var taskDraftTurn = client.postWithCsrf(
                runtimeRoot + "/sessions/" + sessionId + "/messages",
                json(Map.of("content", "AI_WORK_TASK_DRAFT_84_ASSIGNEE="
                        + rootMemberId)),
                Map.of("Idempotency-Key", key()));
        assertOk(taskDraftTurn);
        assertThat(text(taskDraftTurn.body(), "/data/status"))
                .describedAs("Work task draft response: %s", taskDraftTurn.body())
                .isEqualTo("CONFIRMATION_REQUIRED");
        assertThat(text(taskDraftTurn.body(), "/data/workProposal/operation"))
                .isEqualTo("WORK_TASK_DRAFT");
        assertThat(text(taskDraftTurn.body(),
                "/data/workProposal/preview/task/title"))
                .isEqualTo("AI Work Task 84");
        assertThat(text(taskDraftTurn.body(),
                "/data/workProposal/preview/task/assigneeMemberId"))
                .isEqualTo(Long.toString(rootMemberId));
        assertThat(text(taskDraftTurn.body(), "/data/workProposal/state"))
                .isEqualTo("PENDING");
        var taskProposalId = text(
                taskDraftTurn.body(), "/data/workProposal/id");
        var taskProposalRoot = runtimeRoot + "/sessions/" + sessionId
                + "/work-proposals/" + taskProposalId;
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_work_task WHERE system_id=? AND tenant_id=?",
                Long.class, system, tenant)).isEqualTo(taskCountBeforeContextReads);
        var pendingTaskProposal = client.get(taskProposalRoot);
        assertOk(pendingTaskProposal);
        assertThat(text(pendingTaskProposal.body(), "/data/state"))
                .isEqualTo("PENDING");

        var taskConfirmKey = key();
        var confirmedTask = client.postWithCsrf(
                taskProposalRoot + "/confirm",
                json(Map.of("expectedRevision", 0)),
                Map.of("Idempotency-Key", taskConfirmKey));
        assertOk(confirmedTask);
        assertThat(text(confirmedTask.body(), "/data/state"))
                .isEqualTo("SUCCEEDED");
        assertThat(text(confirmedTask.body(), "/data/result/task/status"))
                .isEqualTo("OPEN");
        assertThat(text(confirmedTask.body(), "/data/result/task/title"))
                .isEqualTo("AI Work Task 84");
        var createdAiTaskId = text(
                confirmedTask.body(), "/data/result/task/taskId");
        var confirmedTaskReplay = client.postWithCsrf(
                taskProposalRoot + "/confirm",
                json(Map.of("expectedRevision", 0)),
                Map.of("Idempotency-Key", taskConfirmKey));
        assertOk(confirmedTaskReplay);
        assertThat(text(confirmedTaskReplay.body(), "/data/result/task/taskId"))
                .isEqualTo(createdAiTaskId);
        assertError(client.postWithCsrf(
                        taskProposalRoot + "/confirm",
                        json(Map.of("expectedRevision", 1)),
                        Map.of("Idempotency-Key", taskConfirmKey)),
                409, "AI_WORK_REPLAY_CONFLICT");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_work_task WHERE system_id=? AND tenant_id=?",
                Long.class, system, tenant))
                .isEqualTo(taskCountBeforeContextReads + 1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM un_work_task "
                        + "WHERE system_id=? AND tenant_id=? AND id=?",
                String.class, system, tenant, Long.parseLong(createdAiTaskId)))
                .isEqualTo("OPEN");

        var deniedTaskTurn = client.postWithCsrf(
                runtimeRoot + "/sessions/" + sessionId + "/messages",
                json(Map.of("content", "AI_WORK_TASK_DRAFT_84_ASSIGNEE="
                        + rootMemberId)),
                Map.of("Idempotency-Key", key()));
        assertOk(deniedTaskTurn);
        var deniedTaskProposalId = text(
                deniedTaskTurn.body(), "/data/workProposal/id");

        var aiReportDate = reportDate.minusDays(1);
        var reportDraftPrompt = "AI_WORK_REPORT_DRAFT_84_DATE=" + aiReportDate;
        var reportDraftTurnA = client.postWithCsrf(
                runtimeRoot + "/sessions/" + sessionId + "/messages",
                json(Map.of("content", reportDraftPrompt + " A")),
                Map.of("Idempotency-Key", key()));
        var reportDraftTurnB = client.postWithCsrf(
                runtimeRoot + "/sessions/" + sessionId + "/messages",
                json(Map.of("content", reportDraftPrompt + " B")),
                Map.of("Idempotency-Key", key()));
        assertOk(reportDraftTurnA);
        assertOk(reportDraftTurnB);
        assertThat(text(reportDraftTurnA.body(), "/data/status"))
                .isEqualTo("CONFIRMATION_REQUIRED");
        assertThat(text(reportDraftTurnA.body(),
                "/data/workProposal/preview/dailyReport/workDate"))
                .isEqualTo(aiReportDate.toString());
        assertThat(text(reportDraftTurnA.body(),
                "/data/workProposal/preview/dailyReport/completedWork"))
                .isEqualTo("AI Work Report Completed 84");
        var reportProposalIdA = text(
                reportDraftTurnA.body(), "/data/workProposal/id");
        var reportProposalIdB = text(
                reportDraftTurnB.body(), "/data/workProposal/id");
        var reportProposalRootA = runtimeRoot + "/sessions/" + sessionId
                + "/work-proposals/" + reportProposalIdA;
        var reportProposalRootB = runtimeRoot + "/sessions/" + sessionId
                + "/work-proposals/" + reportProposalIdB;
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_work_daily_report "
                        + "WHERE system_id=? AND tenant_id=? AND work_date=?",
                Long.class, system, tenant, aiReportDate)).isZero();

        var reportConfirmKey = key();
        var confirmedReport = client.postWithCsrf(
                reportProposalRootA + "/confirm",
                json(Map.of("expectedRevision", 0)),
                Map.of("Idempotency-Key", reportConfirmKey));
        assertOk(confirmedReport);
        assertThat(text(confirmedReport.body(), "/data/state"))
                .isEqualTo("SUCCEEDED");
        assertThat(text(confirmedReport.body(),
                "/data/result/dailyReport/status"))
                .isEqualTo("DRAFT");
        assertThat(text(confirmedReport.body(),
                "/data/result/dailyReport/workDate"))
                .isEqualTo(aiReportDate.toString());
        var createdAiReportId = text(
                confirmedReport.body(), "/data/result/dailyReport/reportId");
        var confirmedReportReplay = client.postWithCsrf(
                reportProposalRootA + "/confirm",
                json(Map.of("expectedRevision", 0)),
                Map.of("Idempotency-Key", reportConfirmKey));
        assertOk(confirmedReportReplay);
        assertThat(text(confirmedReportReplay.body(),
                "/data/result/dailyReport/reportId"))
                .isEqualTo(createdAiReportId);
        var staleReport = client.postWithCsrf(
                reportProposalRootB + "/confirm",
                json(Map.of("expectedRevision", 0)),
                Map.of("Idempotency-Key", key()));
        assertOk(staleReport);
        assertThat(text(staleReport.body(), "/data/state")).isEqualTo("STALE");
        assertThat(text(staleReport.body(), "/data/errorCode"))
                .isEqualTo("AI_WORK_REPORT_EXISTS");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_work_daily_report "
                        + "WHERE system_id=? AND tenant_id=? AND work_date=? "
                        + "AND status='DRAFT'",
                Long.class, system, tenant, aiReportDate)).isOne();

        var workTaskCreatePermission = "work.task.create";
        jdbcTemplate.update(
                "UPDATE un_plat_permission SET status='DISABLED',"
                        + "updated_at=UTC_TIMESTAMP(3),version=version+1 "
                        + "WHERE system_id=? AND permission_code=?",
                system, workTaskCreatePermission);
        authzEpochService.bumpSystem(system, rootAccountId);
        try {
            var workPermissionRefresh = client.postWithCsrf(
                    "/api/v1/auth/refresh", "{}", Map.of());
            assertOk(workPermissionRefresh);
            assertThat(workPermissionRefresh.body().at("/data/context/permissions"))
                    .extracting(JsonNode::asText)
                    .doesNotContain(workTaskCreatePermission);
            var permissionDeniedTask = client.postWithCsrf(
                    runtimeRoot + "/sessions/" + sessionId + "/work-proposals/"
                            + deniedTaskProposalId + "/confirm",
                    json(Map.of("expectedRevision", 0)),
                    Map.of("Idempotency-Key", key()));
            assertOk(permissionDeniedTask);
            assertThat(text(permissionDeniedTask.body(), "/data/state"))
                    .isEqualTo("PERMISSION_DENIED");
            assertThat(text(permissionDeniedTask.body(), "/data/errorCode"))
                    .isEqualTo("AI_WORK_PERMISSION_DENIED");
        } finally {
            jdbcTemplate.update(
                    "UPDATE un_plat_permission SET status='ACTIVE',"
                            + "updated_at=UTC_TIMESTAMP(3),version=version+1 "
                            + "WHERE system_id=? AND permission_code=?",
                    system, workTaskCreatePermission);
            authzEpochService.bumpSystem(system, rootAccountId);
            assertOk(client.postWithCsrf(
                    "/api/v1/auth/refresh", "{}", Map.of()));
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_work_task WHERE system_id=? AND tenant_id=?",
                Long.class, system, tenant))
                .isEqualTo(taskCountBeforeContextReads + 1);

        var flowDraftTurn = client.postWithCsrf(
                runtimeRoot + "/sessions/" + sessionId + "/messages",
                json(Map.of("content", "AI_FLOW_DEFINITION_DRAFT_85_APPROVER="
                        + rootMemberId)),
                Map.of("Idempotency-Key", key()));
        assertOk(flowDraftTurn);
        assertThat(text(flowDraftTurn.body(), "/data/status"))
                .describedAs("Flow generated-draft response: %s", flowDraftTurn.body())
                .isEqualTo("CONFIRMATION_REQUIRED");
        assertThat(text(flowDraftTurn.body(),
                "/data/generatedDraftProposal/operation"))
                .isEqualTo("FLOW_DEFINITION_DRAFT");
        assertThat(text(flowDraftTurn.body(),
                "/data/generatedDraftProposal/preview/flowDefinition/name"))
                .isEqualTo("AI Flow Definition 85");
        assertThat(flowDraftTurn.body().at(
                "/data/generatedDraftProposal/preview/flowDefinition/approverMemberIds"))
                .extracting(JsonNode::asText)
                .containsExactly(Long.toString(rootMemberId));
        var flowDraftProposalId = text(flowDraftTurn.body(),
                "/data/generatedDraftProposal/id");
        var flowDraftProposalRoot = runtimeRoot + "/sessions/" + sessionId
                + "/generated-draft-proposals/" + flowDraftProposalId;
        var pendingFlowDraft = client.get(flowDraftProposalRoot);
        assertOk(pendingFlowDraft);
        assertThat(text(pendingFlowDraft.body(), "/data/state"))
                .isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_flow_definition_draft "
                        + "WHERE system_id=? AND tenant_id=? AND name=?",
                Long.class, system, tenant, "AI Flow Definition 85")).isZero();

        var flowDraftConfirmKey = key();
        var confirmedFlowDraft = client.postWithCsrf(
                flowDraftProposalRoot + "/confirm",
                json(Map.of("expectedRevision", 0)),
                Map.of("Idempotency-Key", flowDraftConfirmKey));
        assertOk(confirmedFlowDraft);
        assertThat(text(confirmedFlowDraft.body(), "/data/state"))
                .isEqualTo("SUCCEEDED");
        assertThat(text(confirmedFlowDraft.body(),
                "/data/result/flowDefinition/name"))
                .isEqualTo("AI Flow Definition 85");
        assertThat(confirmedFlowDraft.body().at(
                "/data/result/flowDefinition/published").asBoolean()).isFalse();
        var createdAiFlowDefinitionId = text(confirmedFlowDraft.body(),
                "/data/result/flowDefinition/definitionId");
        var confirmedFlowDraftReplay = client.postWithCsrf(
                flowDraftProposalRoot + "/confirm",
                json(Map.of("expectedRevision", 0)),
                Map.of("Idempotency-Key", flowDraftConfirmKey));
        assertOk(confirmedFlowDraftReplay);
        assertThat(text(confirmedFlowDraftReplay.body(),
                "/data/result/flowDefinition/definitionId"))
                .isEqualTo(createdAiFlowDefinitionId);
        assertError(client.postWithCsrf(
                        flowDraftProposalRoot + "/confirm",
                        json(Map.of("expectedRevision", 1)),
                        Map.of("Idempotency-Key", flowDraftConfirmKey)),
                409, "AI_GENERATED_DRAFT_REPLAY_CONFLICT");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_flow_definition_version "
                        + "WHERE system_id=? AND tenant_id=? AND definition_id=?",
                Long.class, system, tenant,
                Long.parseLong(createdAiFlowDefinitionId))).isZero();

        var reportSourceId = jdbcTemplate.queryForObject(
                "SELECT id FROM un_module_data_source "
                        + "WHERE system_id=? AND tenant_id=? "
                        + "AND data_source_code='flow_empty_routes'",
                Long.class, system, tenant);
        var reportDraftPrompt85 =
                "AI_CONFIG_REPORT_DRAFT_85_SOURCE=" + reportSourceId;
        var generatedReportTurnA = client.postWithCsrf(
                runtimeRoot + "/sessions/" + sessionId + "/messages",
                json(Map.of("content", reportDraftPrompt85 + " A")),
                Map.of("Idempotency-Key", key()));
        var generatedReportTurnB = client.postWithCsrf(
                runtimeRoot + "/sessions/" + sessionId + "/messages",
                json(Map.of("content", reportDraftPrompt85 + " B")),
                Map.of("Idempotency-Key", key()));
        assertOk(generatedReportTurnA);
        assertOk(generatedReportTurnB);
        assertThat(text(generatedReportTurnA.body(),
                "/data/generatedDraftProposal/preview/reportDefinition/code"))
                .isEqualTo("ai_report_85");
        assertThat(generatedReportTurnA.body().at(
                "/data/generatedDraftProposal/preview/reportDefinition/outputFieldCodes"))
                .extracting(JsonNode::asText).containsExactly("route");
        var generatedReportProposalIdA = text(generatedReportTurnA.body(),
                "/data/generatedDraftProposal/id");
        var generatedReportProposalIdB = text(generatedReportTurnB.body(),
                "/data/generatedDraftProposal/id");
        var generatedReportProposalRootA = runtimeRoot + "/sessions/" + sessionId
                + "/generated-draft-proposals/" + generatedReportProposalIdA;
        var generatedReportProposalRootB = runtimeRoot + "/sessions/" + sessionId
                + "/generated-draft-proposals/" + generatedReportProposalIdB;
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_report "
                        + "WHERE system_id=? AND tenant_id=? AND report_code='ai_report_85'",
                Long.class, system, tenant)).isZero();
        var confirmedGeneratedReport = client.postWithCsrf(
                generatedReportProposalRootA + "/confirm",
                json(Map.of("expectedRevision", 0)),
                Map.of("Idempotency-Key", key()));
        assertOk(confirmedGeneratedReport);
        assertThat(text(confirmedGeneratedReport.body(), "/data/state"))
                .isEqualTo("SUCCEEDED");
        assertThat(text(confirmedGeneratedReport.body(),
                "/data/result/reportDefinition/code"))
                .isEqualTo("ai_report_85");
        assertThat(confirmedGeneratedReport.body().at(
                "/data/result/reportDefinition/published").asBoolean()).isFalse();
        var createdAiReportDefinitionId = text(confirmedGeneratedReport.body(),
                "/data/result/reportDefinition/reportId");
        var staleGeneratedReport = client.postWithCsrf(
                generatedReportProposalRootB + "/confirm",
                json(Map.of("expectedRevision", 0)),
                Map.of("Idempotency-Key", key()));
        assertOk(staleGeneratedReport);
        assertThat(text(staleGeneratedReport.body(), "/data/state"))
                .isEqualTo("STALE");
        assertThat(text(staleGeneratedReport.body(), "/data/errorCode"))
                .isEqualTo("AI_MODULE_REPORT_CODE_CONFLICT");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_report "
                        + "WHERE system_id=? AND tenant_id=? AND id=? "
                        + "AND active_version_id IS NULL",
                Long.class, system, tenant,
                Long.parseLong(createdAiReportDefinitionId))).isOne();

        var generatedPrintTurn = client.postWithCsrf(
                runtimeRoot + "/sessions/" + sessionId + "/messages",
                json(Map.of("content", "AI_CONFIG_PRINT_TEMPLATE_DRAFT_85")),
                Map.of("Idempotency-Key", key()));
        assertOk(generatedPrintTurn);
        assertThat(text(generatedPrintTurn.body(),
                "/data/generatedDraftProposal/preview/printTemplate/code"))
                .isEqualTo("ai_print_85");
        assertThat(text(generatedPrintTurn.body(),
                "/data/generatedDraftProposal/preview/printTemplate/moduleCode"))
                .isEqualTo(FLOW_RECORD_MODULE_CODE);
        var generatedPrintProposalId = text(generatedPrintTurn.body(),
                "/data/generatedDraftProposal/id");
        var generatedPrintProposalRoot = runtimeRoot + "/sessions/" + sessionId
                + "/generated-draft-proposals/" + generatedPrintProposalId;
        var confirmedGeneratedPrint = client.postWithCsrf(
                generatedPrintProposalRoot + "/confirm",
                json(Map.of("expectedRevision", 0)),
                Map.of("Idempotency-Key", key()));
        assertOk(confirmedGeneratedPrint);
        assertThat(text(confirmedGeneratedPrint.body(), "/data/state"))
                .isEqualTo("SUCCEEDED");
        assertThat(text(confirmedGeneratedPrint.body(),
                "/data/result/printTemplate/status"))
                .isEqualTo("DISABLED");
        assertThat(confirmedGeneratedPrint.body().at(
                "/data/result/printTemplate/published").asBoolean()).isFalse();
        var createdAiPrintTemplateId = text(confirmedGeneratedPrint.body(),
                "/data/result/printTemplate/templateId");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_print_template "
                        + "WHERE system_id=? AND id=? AND desired_status='DISABLED' "
                        + "AND published_version_id IS NULL",
                Long.class, system,
                Long.parseLong(createdAiPrintTemplateId))).isOne();

        var permissionDeniedFlowTurn = client.postWithCsrf(
                runtimeRoot + "/sessions/" + sessionId + "/messages",
                json(Map.of("content", "AI_FLOW_DENIED_DRAFT_85_APPROVER="
                        + rootMemberId)),
                Map.of("Idempotency-Key", key()));
        assertOk(permissionDeniedFlowTurn);
        var permissionDeniedFlowProposalId = text(permissionDeniedFlowTurn.body(),
                "/data/generatedDraftProposal/id");
        var flowDefinitionPermission = "flow.definition.manage";
        jdbcTemplate.update(
                "UPDATE un_plat_permission SET status='DISABLED',"
                        + "updated_at=UTC_TIMESTAMP(3),version=version+1 "
                        + "WHERE system_id=? AND permission_code=?",
                system, flowDefinitionPermission);
        authzEpochService.bumpSystem(system, rootAccountId);
        try {
            var flowPermissionRefresh = client.postWithCsrf(
                    "/api/v1/auth/refresh", "{}", Map.of());
            assertOk(flowPermissionRefresh);
            assertThat(flowPermissionRefresh.body().at("/data/context/permissions"))
                    .extracting(JsonNode::asText)
                    .doesNotContain(flowDefinitionPermission);
            var deniedGeneratedFlow = client.postWithCsrf(
                    runtimeRoot + "/sessions/" + sessionId
                            + "/generated-draft-proposals/"
                            + permissionDeniedFlowProposalId + "/confirm",
                    json(Map.of("expectedRevision", 0)),
                    Map.of("Idempotency-Key", key()));
            assertOk(deniedGeneratedFlow);
            assertThat(text(deniedGeneratedFlow.body(), "/data/state"))
                    .isEqualTo("PERMISSION_DENIED");
            assertThat(text(deniedGeneratedFlow.body(), "/data/errorCode"))
                    .isEqualTo("AI_FLOW_PERMISSION_DENIED");
        } finally {
            jdbcTemplate.update(
                    "UPDATE un_plat_permission SET status='ACTIVE',"
                            + "updated_at=UTC_TIMESTAMP(3),version=version+1 "
                            + "WHERE system_id=? AND permission_code=?",
                    system, flowDefinitionPermission);
            authzEpochService.bumpSystem(system, rootAccountId);
            assertOk(client.postWithCsrf(
                    "/api/v1/auth/refresh", "{}", Map.of()));
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_flow_definition_draft "
                        + "WHERE system_id=? AND tenant_id=? AND name=?",
                Long.class, system, tenant,
                "AI Permission Denied Flow 85")).isZero();

        var overwriteProposal = client.postWithCsrf(
                fillRoot, fillProposeBody, Map.of("Idempotency-Key", key()));
        assertOk(overwriteProposal);
        assertThat(overwriteProposal.body().at("/data/overwrite").asBoolean()).isTrue();
        var overwriteProposalId = text(overwriteProposal.body(), "/data/id");
        var rejectedFill = client.postWithCsrf(
                fillRoot + "/" + overwriteProposalId + "/reject",
                json(Map.of("expectedVersion", 0)),
                Map.of("Idempotency-Key", key()));
        assertOk(rejectedFill);
        assertThat(text(rejectedFill.body(), "/data/state")).isEqualTo("REJECTED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_ai_fill_history "
                        + "WHERE system_id=? AND tenant_id=? AND record_id=? "
                        + "AND outcome='REJECTED'",
                Long.class, system, tenant, Long.parseLong(aiRecordId))).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT display_value FROM un_module_ai_fill_materialization "
                        + "WHERE system_id=? AND tenant_id=? AND record_id=? "
                        + "AND field_code='ai_summary'",
                String.class, system, tenant, Long.parseLong(aiRecordId)))
                .isEqualTo("AI Filled Summary 77");

        var permissionDeniedFill = client.postWithCsrf(
                fillRoot, fillProposeBody, Map.of("Idempotency-Key", key()));
        assertOk(permissionDeniedFill);
        var permissionDeniedFillId = text(permissionDeniedFill.body(), "/data/id");

        var deniedProposal = client.postWithCsrf(
                runtimeRoot + "/sessions/" + sessionId + "/messages",
                json(Map.of("content", "AI_UPDATE_PROMPT_76 AI_UPDATE_TARGET_76="
                        + aiRecordId + " AI_UPDATE_VERSION_76=" + updatedRecordVersion)),
                Map.of("Idempotency-Key", key()));
        assertOk(deniedProposal);
        var deniedConfirmationId = text(
                deniedProposal.body(), "/data/confirmation/id");
        var moduleUpdatePermission =
                "module." + FLOW_RECORD_MODULE_CODE + ".update";
        jdbcTemplate.update(
                "UPDATE un_plat_permission SET status='DISABLED',"
                        + "updated_at=UTC_TIMESTAMP(3),version=version+1 "
                        + "WHERE system_id=? AND permission_code=?",
                system, moduleUpdatePermission);
        authzEpochService.bumpSystem(system, rootAccountId);
        try {
            assertOk(client.postWithCsrf("/api/v1/auth/refresh", "{}", Map.of()));
            var deniedFill = client.postWithCsrf(
                    fillRoot + "/" + permissionDeniedFillId + "/confirm",
                    json(Map.of("expectedVersion", 0)),
                    Map.of("Idempotency-Key", key()));
            assertOk(deniedFill);
            assertThat(text(deniedFill.body(), "/data/state"))
                    .isEqualTo("FAILED");
            assertThat(text(deniedFill.body(), "/data/errorCode"))
                    .isEqualTo("PERMISSION_DENIED");
            var deniedConfirmation = client.postWithCsrf(
                    runtimeRoot + "/confirmations/" + deniedConfirmationId + "/confirm",
                    json(Map.of("expectedVersion", 0)),
                    Map.of("Idempotency-Key", key()));
            assertOk(deniedConfirmation);
            assertThat(text(deniedConfirmation.body(), "/data/state"))
                    .isEqualTo("FAILED");
            assertThat(text(deniedConfirmation.body(), "/data/errorCode"))
                    .isEqualTo("PERMISSION_DENIED");
        } finally {
            jdbcTemplate.update(
                    "UPDATE un_plat_permission SET status='ACTIVE',"
                            + "updated_at=UTC_TIMESTAMP(3),version=version+1 "
                            + "WHERE system_id=? AND permission_code=?",
                    system, moduleUpdatePermission);
            authzEpochService.bumpSystem(system, rootAccountId);
            assertOk(client.postWithCsrf("/api/v1/auth/refresh", "{}", Map.of()));
        }
        var unchangedAfterDenied = client.get(
                "/api/v1/systems/" + systemId + "/runtime/modules/"
                        + FLOW_RECORD_MODULE_CODE + "/records/" + aiRecordId);
        assertOk(unchangedAfterDenied);
        assertThat(text(unchangedAfterDenied.body(), "/data/title"))
                .isEqualTo("AI Updated Record 76");
        assertThat(unchangedAfterDenied.body().at("/data/version").asLong())
                .isEqualTo(updatedRecordVersion);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_ai_fill_materialization "
                        + "WHERE system_id=? AND tenant_id=? AND record_id=?",
                Long.class, system, tenant, Long.parseLong(aiRecordId)))
                .isOne();

        var moduleReportCountBeforeIsolationReads = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_report "
                        + "WHERE system_id=? AND tenant_id=?",
                Long.class, system, tenant);
        var moduleReportVersionsBeforeIsolationReads = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(version + draft_version),0) "
                        + "FROM un_module_report "
                        + "WHERE system_id=? AND tenant_id=?",
                Long.class, system, tenant);
        var publishedModuleReportCountBeforeIsolationReads =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM un_module_report_version "
                                + "WHERE system_id=? AND tenant_id=?",
                        Long.class, system, tenant);
        var publishedModuleReportVersionsBeforeIsolationReads =
                jdbcTemplate.queryForObject(
                        "SELECT COALESCE(SUM(version_no + source_draft_version),0) "
                                + "FROM un_module_report_version "
                                + "WHERE system_id=? AND tenant_id=?",
                        Long.class, system, tenant);

        var moduleViewPermission = "module." + FLOW_RECORD_MODULE_CODE + ".view";
        jdbcTemplate.update(
                "UPDATE un_plat_permission SET status='DISABLED',"
                        + "updated_at=UTC_TIMESTAMP(3),version=version+1 "
                        + "WHERE system_id=? AND permission_code=?",
                system, moduleViewPermission);
        authzEpochService.bumpSystem(system, rootAccountId);
        try {
            var revokedRefresh = client.postWithCsrf(
                    "/api/v1/auth/refresh", "{}", Map.of());
            assertOk(revokedRefresh);
            assertThat(revokedRefresh.body().at("/data/context/permissions"))
                    .extracting(JsonNode::asText)
                    .doesNotContain(moduleViewPermission);
            var denied = client.postWithCsrf(
                    runtimeRoot + "/sessions/" + sessionId + "/messages",
                    json(Map.of("content", "AI_RECORD_CONTEXT_83=" + aiRecordId)),
                    Map.of("Idempotency-Key", key()));
            assertOk(denied);
            assertThat(text(denied.body(), "/data/status")).isEqualTo("FAILED");
            assertThat(text(denied.body(), "/data/errorCode"))
                    .isEqualTo("PERMISSION_DENIED");
            assertThat(absentOrNull(denied.body().at("/data/answer"))).isTrue();
            var summaryCallsBeforeDeniedStatistics = aiProviderFixture.summaryCalls();
            var deniedStatistics = client.postWithCsrf(
                    runtimeRoot + "/sessions/" + sessionId + "/messages",
                    json(Map.of("content",
                            "AI_RUNTIME_STATISTICS_TREND_94_REVOKED")),
                    Map.of("Idempotency-Key", key()));
            assertOk(deniedStatistics);
            assertThat(text(deniedStatistics.body(), "/data/status"))
                    .isEqualTo("FAILED");
            assertThat(text(deniedStatistics.body(), "/data/errorCode"))
                    .isEqualTo("PERMISSION_DENIED");
            assertThat(absentOrNull(deniedStatistics.body().at(
                    "/data/contextResult"))).isTrue();
            assertThat(aiProviderFixture.summaryCalls())
                    .isEqualTo(summaryCallsBeforeDeniedStatistics);
            var deniedReport = client.postWithCsrf(
                    runtimeRoot + "/sessions/" + sessionId + "/messages",
                    json(Map.of("content", "AI_RUNTIME_REPORT_QUERY_95_REVOKED")),
                    Map.of("Idempotency-Key", key()));
            assertOk(deniedReport);
            assertThat(text(deniedReport.body(), "/data/status"))
                    .isEqualTo("FAILED");
            assertThat(text(deniedReport.body(), "/data/errorCode"))
                    .isEqualTo("PERMISSION_DENIED");
            assertThat(absentOrNull(deniedReport.body().at(
                    "/data/contextResult"))).isTrue();
            assertThat(aiProviderFixture.summaryCalls())
                    .isEqualTo(summaryCallsBeforeDeniedStatistics);
        } finally {
            jdbcTemplate.update(
                    "UPDATE un_plat_permission SET status='ACTIVE',"
                            + "updated_at=UTC_TIMESTAMP(3),version=version+1 "
                            + "WHERE system_id=? AND permission_code=?",
                    system, moduleViewPermission);
            authzEpochService.bumpSystem(system, rootAccountId);
            assertOk(client.postWithCsrf("/api/v1/auth/refresh", "{}", Map.of()));
        }

        var switchedToIsolatedTenant = client.postWithCsrf(
                "/api/v1/context/tenants/" + isolatedTenantId + ":switch",
                "{}", Map.of());
        assertOk(switchedToIsolatedTenant);
        var foreignStatisticsCode = "ai_foreign_stats_94";
        var foreignStatisticsAdminRoot = "/api/v1/systems/" + systemId
                + "/admin/data-sources";
        var foreignStatisticsSource = client.postWithCsrf(
                foreignStatisticsAdminRoot,
                json(Map.of(
                        "code", foreignStatisticsCode,
                        "moduleId", flowRecordSchema.moduleId(),
                        "name", "AI foreign runtime statistics 94",
                        "description", "Tenant-isolated Agent statistics source")),
                Map.of("Idempotency-Key", key()));
        assertOk(foreignStatisticsSource);
        var foreignStatisticsSourceId = text(
                foreignStatisticsSource.body(), "/data/id");
        var foreignStatisticsDraft = client.putWithCsrf(
                foreignStatisticsAdminRoot + "/" + foreignStatisticsSourceId
                        + "/draft",
                json(Map.ofEntries(
                        Map.entry("expectedVersion", 1),
                        Map.entry("name", "AI foreign runtime statistics 94"),
                        Map.entry("description",
                                "Tenant-isolated Agent statistics source"),
                        Map.entry("outputFields", List.of(
                                Map.of("fieldCode", "route"),
                                Map.of("fieldCode", "event_time"))),
                        Map.entry("fixedFilters", List.of(Map.of(
                                "fieldCode", "route", "operator", "EMPTY"))),
                        Map.entry("defaultSort", Map.of(
                                "fieldCode", "route", "direction", "ASC")))),
                Map.of());
        assertOk(foreignStatisticsDraft);
        var foreignStatisticsPublished = client.postWithCsrf(
                foreignStatisticsAdminRoot + "/" + foreignStatisticsSourceId
                        + "/draft:publish",
                json(Map.of("expectedVersion", 2)),
                Map.of("Idempotency-Key", key()));
        assertOk(foreignStatisticsPublished);
        var foreignReportCode = "ai_foreign_report_95";
        var foreignReportAdminRoot = "/api/v1/systems/" + systemId
                + "/admin/reports";
        var foreignReport = client.postWithCsrf(
                foreignReportAdminRoot,
                json(Map.of(
                        "code", foreignReportCode,
                        "name", "AI foreign runtime report 95",
                        "description", "Tenant-isolated Agent report",
                        "dataSourceId", foreignStatisticsSourceId,
                        "outputFieldCodes", List.of("event_time", "route"))),
                Map.of("Idempotency-Key", key()));
        assertOk(foreignReport);
        var foreignReportId = text(foreignReport.body(), "/data/id");
        var foreignReportPublished = client.postWithCsrf(
                foreignReportAdminRoot + "/" + foreignReportId
                        + "/draft:publish",
                json(Map.of("expectedVersion", 1)),
                Map.of("Idempotency-Key", key()));
        assertOk(foreignReportPublished);
        var isolatedAiMetricsProject = client.postWithCsrf(
                aiMetricsProjectRoot,
                json(Map.of(
                        "title", "AI_WORK_PROJECT_METRICS_FOREIGN_89",
                        "description", "Cross-tenant metrics decoy")),
                Map.of("Idempotency-Key", key()));
        assertCreated(isolatedAiMetricsProject);
        var isolatedAiMetricsProjectId = text(
                isolatedAiMetricsProject.body(), "/data/id");
        var isolatedAiMetricsTask = client.postWithCsrf(
                aiMetricsTaskRoot,
                json(Map.of(
                        "title", "AI foreign metrics task 89",
                        "assigneeMemberId", Long.parseLong(aiRootMemberId),
                        "projectId", Long.parseLong(isolatedAiMetricsProjectId),
                        "description", "Must remain tenant-isolated",
                        "dueAt", Instant.now().plus(Duration.ofHours(1)).toString())),
                Map.of("Idempotency-Key", key()));
        assertCreated(isolatedAiMetricsTask);
        var isolatedRecord = createActiveRecord(
                client,
                "/api/v1/systems/" + systemId + "/runtime/modules/"
                        + FLOW_RECORD_MODULE_CODE,
                flowRecordSchema.versionId(),
                "AI record activity tenant decoy 90");
        var isolatedRecordId = text(isolatedRecord.body(), "/data/recordId");
        var isolatedAiTodos = client.get(todoReadRoot
                + "?category=ALL&state=ALL&time=ALL&page=1&size=1");
        assertOk(isolatedAiTodos);
        assertThat(isolatedAiTodos.body().at("/data/items")).isEmpty();
        assertThat(isolatedAiTodos.body().at("/data/total").asLong()).isZero();
        var isolatedAiMessages = client.get(
                messageReadRoot + "?status=UNREAD&page=1&size=1");
        assertOk(isolatedAiMessages);
        assertThat(isolatedAiMessages.body().at("/data/items")).isEmpty();
        assertThat(isolatedAiMessages.body().at("/data/total").asLong()).isZero();
        assertError(client.get(runtimeRoot + "/sessions/" + sessionId),
                404, "AI_NOT_FOUND");
        assertError(client.get(runtimeRoot + "/sessions/" + sessionId
                        + "/work-proposals/" + taskProposalId),
                404, "AI_NOT_FOUND");
        assertError(client.get(runtimeRoot + "/sessions/" + sessionId
                        + "/generated-draft-proposals/" + flowDraftProposalId),
                404, "AI_NOT_FOUND");
        var isolatedWorkReports = client.get(
                "/api/v1/systems/" + systemId + "/work/reports?scope=SELF&page=1&size=10");
        assertOk(isolatedWorkReports);
        assertThat(isolatedWorkReports.body().at("/data/total").asLong()).isZero();
        assertOk(client.postWithCsrf(
                "/api/v1/context/tenants/" + tenantId + ":switch", "{}", Map.of()));
        var foreignRecordActivityMarker = FLOW_RECORD_MODULE_CODE + "|"
                + isolatedRecordId + "|3";
        var summaryCallsBeforeForeignRecordActivity = aiProviderFixture.summaryCalls();
        for (var foreignQuery : List.of(
                Map.entry("AI_RECORD_COMMENT_QUERY_90_FOREIGN=",
                        "RECORD_COMMENT_QUERY"),
                Map.entry("AI_RECORD_HISTORY_QUERY_90_FOREIGN=",
                        "RECORD_HISTORY_QUERY"),
                Map.entry("AI_RECORD_FILE_QUERY_90_FOREIGN=",
                        "RECORD_FILE_QUERY"))) {
            var hiddenForeignRecordActivity = client.postWithCsrf(
                    runtimeRoot + "/sessions/" + sessionId + "/messages",
                    json(Map.of("content", foreignQuery.getKey()
                            + foreignRecordActivityMarker)),
                    Map.of("Idempotency-Key", key()));
            assertOk(hiddenForeignRecordActivity);
            assertThat(text(hiddenForeignRecordActivity.body(), "/data/status"))
                    .isEqualTo("FAILED");
            assertThat(text(hiddenForeignRecordActivity.body(), "/data/errorCode"))
                    .isEqualTo("RECORD_NOT_FOUND");
            assertThat(absentOrNull(hiddenForeignRecordActivity.body().at(
                    "/data/contextResult"))).isTrue();
            assertThat(aiProviderFixture.summaryCalls())
                    .isEqualTo(summaryCallsBeforeForeignRecordActivity);
        }
        var summaryCallsBeforeForeignStatistics = aiProviderFixture.summaryCalls();
        var hiddenForeignStatistics = client.postWithCsrf(
                runtimeRoot + "/sessions/" + sessionId + "/messages",
                json(Map.of("content", "AI_RUNTIME_STATISTICS_FOREIGN_94")),
                Map.of("Idempotency-Key", key()));
        assertOk(hiddenForeignStatistics);
        assertThat(text(hiddenForeignStatistics.body(), "/data/status"))
                .isEqualTo("FAILED");
        assertThat(text(hiddenForeignStatistics.body(), "/data/errorCode"))
                .isEqualTo("DATA_SOURCE_NOT_FOUND");
        assertThat(absentOrNull(hiddenForeignStatistics.body().at(
                "/data/contextResult"))).isTrue();
        assertThat(aiProviderFixture.summaryCalls())
                .isEqualTo(summaryCallsBeforeForeignStatistics);
        var hiddenForeignReport = client.postWithCsrf(
                runtimeRoot + "/sessions/" + sessionId + "/messages",
                json(Map.of("content", "AI_RUNTIME_REPORT_QUERY_95_FOREIGN")),
                Map.of("Idempotency-Key", key()));
        assertOk(hiddenForeignReport);
        assertThat(text(hiddenForeignReport.body(), "/data/status"))
                .isEqualTo("FAILED");
        assertThat(text(hiddenForeignReport.body(), "/data/errorCode"))
                .isEqualTo("REPORT_NOT_FOUND");
        assertThat(absentOrNull(hiddenForeignReport.body().at(
                "/data/contextResult"))).isTrue();
        assertThat(aiProviderFixture.summaryCalls())
                .isEqualTo(summaryCallsBeforeForeignStatistics);
        var summaryCallsBeforeForeignMetrics = aiProviderFixture.summaryCalls();
        var hiddenForeignMetrics = client.postWithCsrf(
                runtimeRoot + "/sessions/" + sessionId + "/messages",
                json(Map.of("content", "AI_WORK_PROJECT_METRICS_QUERY_89_FOREIGN="
                        + isolatedAiMetricsProjectId + "|" + aiMetricsFrom + "|"
                        + aiMetricsTo)),
                Map.of("Idempotency-Key", key()));
        assertOk(hiddenForeignMetrics);
        assertThat(text(hiddenForeignMetrics.body(), "/data/status"))
                .isEqualTo("FAILED");
        assertThat(text(hiddenForeignMetrics.body(), "/data/errorCode"))
                .isEqualTo("WORK_PROJECT_NOT_FOUND");
        assertThat(absentOrNull(hiddenForeignMetrics.body().at(
                "/data/contextResult"))).isTrue();
        assertThat(aiProviderFixture.summaryCalls())
                .isEqualTo(summaryCallsBeforeForeignMetrics);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_work_project_member "
                        + "WHERE system_id=? AND tenant_id=?",
                Long.class, system, tenant))
                .isEqualTo(projectMemberCountAfterExpectedRemoval);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(version),0) FROM un_work_project_member "
                        + "WHERE system_id=? AND tenant_id=?",
                Long.class, system, tenant))
                .isEqualTo(projectMemberVersionsAfterExpectedRemoval);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_collab_record_comment "
                        + "WHERE system_id=? AND tenant_id=?",
                Long.class, system, tenant)).isEqualTo(commentCountBeforeContextReads);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(version),0) FROM un_collab_record_comment "
                        + "WHERE system_id=? AND tenant_id=?",
                Long.class, system, tenant)).isEqualTo(commentVersionsBeforeContextReads);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_collab_record_comment_mention "
                        + "WHERE system_id=? AND tenant_id=?",
                Long.class, system, tenant))
                .isEqualTo(commentMentionCountBeforeContextReads);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_record_history "
                        + "WHERE system_id=? AND tenant_id=?",
                Long.class, system, tenant)).isEqualTo(historyCountBeforeContextReads);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(record_version),0) "
                        + "FROM un_module_record_history "
                        + "WHERE system_id=? AND tenant_id=?",
                Long.class, system, tenant)).isEqualTo(historyVersionsBeforeContextReads);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_file_object "
                        + "WHERE system_id=? AND tenant_id=?",
                Long.class, system, tenant)).isEqualTo(fileCountBeforeContextReads);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(version),0) FROM un_file_object "
                        + "WHERE system_id=? AND tenant_id=?",
                Long.class, system, tenant)).isEqualTo(fileVersionsBeforeContextReads);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_file_reference "
                        + "WHERE system_id=? AND tenant_id=?",
                Long.class, system, tenant))
                .isEqualTo(fileReferenceCountBeforeContextReads);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_data_source "
                        + "WHERE system_id=? AND tenant_id=?",
                Long.class, system, tenant))
                .isEqualTo(dataSourceCountBeforeContextReads);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(version + draft_version),0) "
                        + "FROM un_module_data_source "
                        + "WHERE system_id=? AND tenant_id=?",
                Long.class, system, tenant))
                .isEqualTo(dataSourceVersionsBeforeContextReads);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_data_source_version "
                        + "WHERE system_id=? AND tenant_id=?",
                Long.class, system, tenant))
                .isEqualTo(publishedSourceCountBeforeContextReads);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(version_no + source_draft_version),0) "
                        + "FROM un_module_data_source_version "
                        + "WHERE system_id=? AND tenant_id=?",
                Long.class, system, tenant))
                .isEqualTo(publishedSourceVersionsBeforeContextReads);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_report "
                        + "WHERE system_id=? AND tenant_id=?",
                Long.class, system, tenant))
                .isEqualTo(moduleReportCountBeforeIsolationReads);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(version + draft_version),0) "
                        + "FROM un_module_report "
                        + "WHERE system_id=? AND tenant_id=?",
                Long.class, system, tenant))
                .isEqualTo(moduleReportVersionsBeforeIsolationReads);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_report_version "
                        + "WHERE system_id=? AND tenant_id=?",
                Long.class, system, tenant))
                .isEqualTo(publishedModuleReportCountBeforeIsolationReads);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(version_no + source_draft_version),0) "
                        + "FROM un_module_report_version "
                        + "WHERE system_id=? AND tenant_id=?",
                Long.class, system, tenant))
                .isEqualTo(publishedModuleReportVersionsBeforeIsolationReads);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_ai_agent_policy_version "
                        + "WHERE system_id=? AND tenant_id=?",
                Long.class, system, tenant)).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_ai_agent_policy_publish "
                        + "WHERE system_id=? AND tenant_id=?",
                Long.class, system, tenant)).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_ai_agent_tool_call "
                        + "WHERE system_id=? AND tenant_id=? AND turn_id IN ("
                        + "SELECT id FROM un_ai_agent_turn WHERE session_id=?)",
                Long.class, system, tenant, Long.parseLong(sessionId)))
                .isEqualTo(27);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_ai_agent_tool_call "
                        + "WHERE system_id=? AND tenant_id=? "
                        + "AND tool_name='RUNTIME_STATISTICS_QUERY' "
                        + "AND status='SUCCEEDED' AND result_count=4 "
                        + "AND result_code='OK' AND turn_id IN ("
                        + "SELECT id FROM un_ai_agent_turn WHERE session_id=?)",
                Long.class, system, tenant, Long.parseLong(sessionId)))
                .isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_ai_agent_tool_call "
                        + "WHERE system_id=? AND tenant_id=? "
                        + "AND tool_name='RUNTIME_REPORT_QUERY' "
                        + "AND status='SUCCEEDED' AND result_count=3 "
                        + "AND result_code='OK' AND turn_id IN ("
                        + "SELECT id FROM un_ai_agent_turn WHERE session_id=?)",
                Long.class, system, tenant, Long.parseLong(sessionId)))
                .isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_ai_agent_tool_call "
                        + "WHERE system_id=? AND tenant_id=? "
                        + "AND tool_name='RUNTIME_REPORT_QUERY' "
                        + "AND status='FAILED' AND result_count=0 "
                        + "AND result_code='REPORT_NOT_FOUND' "
                        + "AND turn_id IN (SELECT id FROM un_ai_agent_turn "
                        + "WHERE session_id=?)",
                Long.class, system, tenant, Long.parseLong(sessionId)))
                .isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_ai_agent_tool_call "
                        + "WHERE system_id=? AND tenant_id=? "
                        + "AND tool_name='RUNTIME_REPORT_QUERY' "
                        + "AND status='FAILED' AND result_count=0 "
                        + "AND result_code='PERMISSION_DENIED' "
                        + "AND turn_id IN (SELECT id FROM un_ai_agent_turn "
                        + "WHERE session_id=?)",
                Long.class, system, tenant, Long.parseLong(sessionId)))
                .isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_ai_agent_tool_call "
                        + "WHERE system_id=? AND tenant_id=? "
                        + "AND tool_name='RUNTIME_STATISTICS_QUERY' "
                        + "AND status='FAILED' AND result_count=0 "
                        + "AND result_code='DATA_SOURCE_NOT_FOUND' "
                        + "AND turn_id IN (SELECT id FROM un_ai_agent_turn "
                        + "WHERE session_id=?)",
                Long.class, system, tenant, Long.parseLong(sessionId)))
                .isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_ai_agent_tool_call "
                        + "WHERE system_id=? AND tenant_id=? "
                        + "AND tool_name='RUNTIME_STATISTICS_QUERY' "
                        + "AND status='FAILED' AND result_count=0 "
                        + "AND result_code='PERMISSION_DENIED' "
                        + "AND turn_id IN (SELECT id FROM un_ai_agent_turn "
                        + "WHERE session_id=?)",
                Long.class, system, tenant, Long.parseLong(sessionId)))
                .isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_ai_agent_tool_call "
                        + "WHERE system_id=? AND tenant_id=? "
                        + "AND tool_name='WORK_PROJECT_METRICS_QUERY' "
                        + "AND status='SUCCEEDED' AND result_count=1 "
                        + "AND result_code='OK' AND turn_id IN ("
                        + "SELECT id FROM un_ai_agent_turn WHERE session_id=?)",
                Long.class, system, tenant, Long.parseLong(sessionId)))
                .isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_ai_agent_tool_call "
                        + "WHERE system_id=? AND tenant_id=? "
                        + "AND tool_name='WORK_PROJECT_METRICS_QUERY' "
                        + "AND status='FAILED' AND result_count=0 "
                        + "AND result_code='WORK_PROJECT_NOT_FOUND' "
                        + "AND turn_id IN (SELECT id FROM un_ai_agent_turn "
                        + "WHERE session_id=?)",
                Long.class, system, tenant, Long.parseLong(sessionId)))
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_ai_agent_tool_call "
                        + "WHERE system_id=? AND tenant_id=? "
                        + "AND tool_name='WORK_PROJECT_METRICS_QUERY' "
                        + "AND status='FAILED' AND result_count=0 "
                        + "AND result_code='WORK_PROJECT_FORBIDDEN' "
                        + "AND turn_id IN (SELECT id FROM un_ai_agent_turn "
                        + "WHERE session_id=?)",
                Long.class, system, tenant, Long.parseLong(sessionId)))
                .isOne();
        for (var successfulRecordActivity : List.of(
                Map.entry("RECORD_COMMENT_QUERY", 1),
                Map.entry("RECORD_HISTORY_QUERY",
                        nativeRecordHistory.body().at("/data/items").size()),
                Map.entry("RECORD_FILE_QUERY", 1))) {
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM un_ai_agent_tool_call "
                            + "WHERE system_id=? AND tenant_id=? "
                            + "AND tool_name=? AND status='SUCCEEDED' "
                            + "AND result_count=? AND result_code='OK' "
                            + "AND turn_id IN (SELECT id FROM un_ai_agent_turn "
                            + "WHERE session_id=?)",
                    Long.class, system, tenant, successfulRecordActivity.getKey(),
                    successfulRecordActivity.getValue(), Long.parseLong(sessionId)))
                    .isOne();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM un_ai_agent_tool_call "
                            + "WHERE system_id=? AND tenant_id=? "
                            + "AND tool_name=? AND status='FAILED' "
                            + "AND result_count=0 AND turn_id IN ("
                            + "SELECT id FROM un_ai_agent_turn WHERE session_id=?)",
                    Long.class, system, tenant, successfulRecordActivity.getKey(),
                    Long.parseLong(sessionId))).isEqualTo(2);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM un_ai_agent_tool_call "
                            + "WHERE system_id=? AND tenant_id=? "
                            + "AND tool_name=? AND status='FAILED' "
                            + "AND result_count=0 AND result_code='RECORD_NOT_FOUND' "
                            + "AND turn_id IN (SELECT id FROM un_ai_agent_turn "
                            + "WHERE session_id=?)",
                    Long.class, system, tenant, successfulRecordActivity.getKey(),
                    Long.parseLong(sessionId))).isOne();
        }
        for (var permissionFailure : List.of(
                Map.entry("RECORD_COMMENT_QUERY", "PERMISSION_DENIED"),
                Map.entry("RECORD_HISTORY_QUERY", "PERMISSION_DENIED"),
                Map.entry("RECORD_FILE_QUERY", "FILE_FORBIDDEN"))) {
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM un_ai_agent_tool_call "
                            + "WHERE system_id=? AND tenant_id=? "
                            + "AND tool_name=? AND status='FAILED' "
                            + "AND result_count=0 AND result_code=? "
                            + "AND turn_id IN (SELECT id FROM un_ai_agent_turn "
                            + "WHERE session_id=?)",
                    Long.class, system, tenant, permissionFailure.getKey(),
                    permissionFailure.getValue(), Long.parseLong(sessionId)))
                    .isOne();
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_ai_agent_usage "
                        + "WHERE system_id=? AND tenant_id=? AND turn_id IN ("
                        + "SELECT id FROM un_ai_agent_turn WHERE session_id=?)",
                Long.class, system, tenant, Long.parseLong(sessionId)))
                .isEqualTo(46);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_ai_config_field_proposal "
                        + "WHERE system_id=? AND tenant_id=? AND session_id=?",
                Long.class, system, tenant, Long.parseLong(sessionId)))
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_ai_config_field_proposal "
                        + "WHERE system_id=? AND tenant_id=? AND session_id=? "
                        + "AND state='SUCCEEDED'",
                Long.class, system, tenant, Long.parseLong(sessionId))).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_ai_config_field_proposal "
                        + "WHERE system_id=? AND tenant_id=? AND session_id=? "
                        + "AND state='FAILED' AND result_code='AI_CONFIG_DRAFT_STALE'",
                Long.class, system, tenant, Long.parseLong(sessionId))).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_ai_config_field_attempt "
                        + "WHERE system_id=? AND tenant_id=? AND proposal_id IN ("
                        + "SELECT id FROM un_ai_config_field_proposal "
                        + "WHERE session_id=?)",
                Long.class, system, tenant, Long.parseLong(sessionId)))
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_ai_config_field_event "
                        + "WHERE system_id=? AND tenant_id=? AND proposal_id IN ("
                        + "SELECT id FROM un_ai_config_field_proposal "
                        + "WHERE session_id=?)",
                Long.class, system, tenant, Long.parseLong(sessionId)))
                .isEqualTo(6);
        var sealedConfigurationCommands = String.join("\n",
                jdbcTemplate.queryForList(
                        "SELECT sealed_ciphertext FROM un_ai_config_field_proposal "
                                + "WHERE system_id=? AND tenant_id=? AND session_id=?",
                        String.class, system, tenant, Long.parseLong(sessionId)));
        assertThat(sealedConfigurationCommands)
                .doesNotContain("ai_priority_81")
                .doesNotContain("AI Priority 81")
                .doesNotContain("ai_risk_note_81")
                .doesNotContain("AI Risk Note 81");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_ai_config_artifact_proposal "
                        + "WHERE system_id=? AND tenant_id=? AND session_id=?",
                Long.class, system, tenant, Long.parseLong(sessionId)))
                .isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_ai_config_artifact_proposal "
                        + "WHERE system_id=? AND tenant_id=? AND session_id=? "
                        + "AND state='SUCCEEDED'",
                Long.class, system, tenant, Long.parseLong(sessionId)))
                .isEqualTo(4);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_ai_config_artifact_proposal "
                        + "WHERE system_id=? AND tenant_id=? AND session_id=? "
                        + "AND state='FAILED' AND result_code='AI_CONFIG_DRAFT_STALE'",
                Long.class, system, tenant, Long.parseLong(sessionId))).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_ai_config_artifact_attempt "
                        + "WHERE system_id=? AND tenant_id=? AND proposal_id IN ("
                        + "SELECT id FROM un_ai_config_artifact_proposal "
                        + "WHERE session_id=?)",
                Long.class, system, tenant, Long.parseLong(sessionId)))
                .isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_ai_config_artifact_event "
                        + "WHERE system_id=? AND tenant_id=? AND proposal_id IN ("
                        + "SELECT id FROM un_ai_config_artifact_proposal "
                        + "WHERE session_id=?)",
                Long.class, system, tenant, Long.parseLong(sessionId)))
                .isEqualTo(15);
        // Search only plaintext metadata here. Random sealed ciphertext may
        // legitimately contain short Base64 substrings such as "Low".
        var persistedArtifactMetadata = String.join("\n", jdbcTemplate.queryForList(
                "SELECT CONCAT_WS('|',CAST(preview_json AS CHAR),"
                        + "CAST(result_json AS CHAR),clarification_summary) "
                        + "FROM un_ai_config_artifact_proposal "
                        + "WHERE system_id=? AND tenant_id=? AND session_id=?",
                String.class, system, tenant, Long.parseLong(sessionId)));
        assertThat(persistedArtifactMetadata)
                .doesNotContain("AI Priority 82")
                .doesNotContain("AI Priority Options 82")
                .doesNotContain("Low", "Normal", "High")
                .doesNotContain("Routing", "Timing")
                .doesNotContain("Routed first", "Approval route")
                .doesNotContain("approval_status");
        var persistedLegacyArtifacts = String.join("\n", jdbcTemplate.queryForList(
                "SELECT CONCAT_WS('|',CAST(preview_json AS CHAR),"
                        + "CAST(result_json AS CHAR)) "
                        + "FROM un_ai_config_artifact_proposal "
                        + "WHERE system_id=? AND tenant_id=? AND session_id=? "
                        + "AND artifact_kind IN ('SELECTION_FIELD','PAGE_LAYOUT')",
                String.class, system, tenant, Long.parseLong(sessionId)));
        assertThat(persistedLegacyArtifacts)
                .doesNotContain("approval_status", "event_time");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_ai_work_proposal "
                        + "WHERE system_id=? AND tenant_id=? AND session_id=?",
                Long.class, system, tenant, Long.parseLong(sessionId)))
                .isEqualTo(4);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_ai_work_proposal "
                        + "WHERE system_id=? AND tenant_id=? AND session_id=? "
                        + "AND state='SUCCEEDED'",
                Long.class, system, tenant, Long.parseLong(sessionId)))
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_ai_work_proposal "
                        + "WHERE system_id=? AND tenant_id=? AND session_id=? "
                        + "AND state='STALE' AND result_code='AI_WORK_REPORT_EXISTS'",
                Long.class, system, tenant, Long.parseLong(sessionId))).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_ai_work_proposal "
                        + "WHERE system_id=? AND tenant_id=? AND session_id=? "
                        + "AND state='PERMISSION_DENIED' "
                        + "AND result_code='AI_WORK_PERMISSION_DENIED'",
                Long.class, system, tenant, Long.parseLong(sessionId))).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_ai_work_attempt "
                        + "WHERE system_id=? AND tenant_id=? AND proposal_id IN ("
                        + "SELECT id FROM un_ai_work_proposal WHERE session_id=?)",
                Long.class, system, tenant, Long.parseLong(sessionId)))
                .isEqualTo(4);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_ai_work_event "
                        + "WHERE system_id=? AND tenant_id=? AND proposal_id IN ("
                        + "SELECT id FROM un_ai_work_proposal WHERE session_id=?)",
                Long.class, system, tenant, Long.parseLong(sessionId)))
                .isEqualTo(12);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_work_ai_draft_execution "
                        + "WHERE system_id=? AND tenant_id=? AND member_id=?",
                Long.class, system, tenant, rootMemberId)).isEqualTo(2);
        var persistedWorkProposals = String.join("\n", jdbcTemplate.queryForList(
                "SELECT CONCAT_WS('|',CAST(preview_json AS CHAR),"
                        + "CAST(result_json AS CHAR),clarification_summary,"
                        + "command_ciphertext) FROM un_ai_work_proposal "
                        + "WHERE system_id=? AND tenant_id=? AND session_id=?",
                String.class, system, tenant, Long.parseLong(sessionId)));
        assertThat(persistedWorkProposals)
                .doesNotContain("AI Work Task 84")
                .doesNotContain("AI Work Task Description 84")
                .doesNotContain("AI Work Report Completed 84")
                .doesNotContain("AI Work Report Planned 84")
                .doesNotContain("AI Work Report Blockers 84");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_ai_generated_draft_proposal "
                        + "WHERE system_id=? AND tenant_id=? AND session_id=?",
                Long.class, system, tenant, Long.parseLong(sessionId)))
                .isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_ai_generated_draft_proposal "
                        + "WHERE system_id=? AND tenant_id=? AND session_id=? "
                        + "AND state='SUCCEEDED'",
                Long.class, system, tenant, Long.parseLong(sessionId)))
                .isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_ai_generated_draft_proposal "
                        + "WHERE system_id=? AND tenant_id=? AND session_id=? "
                        + "AND state='STALE' "
                        + "AND result_code='AI_MODULE_REPORT_CODE_CONFLICT'",
                Long.class, system, tenant, Long.parseLong(sessionId))).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_ai_generated_draft_proposal "
                        + "WHERE system_id=? AND tenant_id=? AND session_id=? "
                        + "AND state='PERMISSION_DENIED' "
                        + "AND result_code='AI_FLOW_PERMISSION_DENIED'",
                Long.class, system, tenant, Long.parseLong(sessionId))).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_ai_generated_draft_attempt "
                        + "WHERE system_id=? AND tenant_id=? AND proposal_id IN ("
                        + "SELECT id FROM un_ai_generated_draft_proposal "
                        + "WHERE session_id=?)",
                Long.class, system, tenant, Long.parseLong(sessionId)))
                .isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_ai_generated_draft_event "
                        + "WHERE system_id=? AND tenant_id=? AND proposal_id IN ("
                        + "SELECT id FROM un_ai_generated_draft_proposal "
                        + "WHERE session_id=?)",
                Long.class, system, tenant, Long.parseLong(sessionId)))
                .isEqualTo(15);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_flow_ai_definition_draft_execution "
                        + "WHERE system_id=? AND tenant_id=? AND member_id=?",
                Long.class, system, tenant, rootMemberId)).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_ai_generated_draft_execution "
                        + "WHERE system_id=? AND tenant_id=? AND member_id=?",
                Long.class, system, tenant, rootMemberId)).isEqualTo(2);
        var persistedGeneratedDrafts = String.join("\n",
                jdbcTemplate.queryForList(
                        "SELECT CONCAT_WS('|',CAST(preview_json AS CHAR),"
                                + "CAST(result_json AS CHAR),clarification_summary,"
                                + "command_ciphertext) "
                                + "FROM un_ai_generated_draft_proposal "
                                + "WHERE system_id=? AND tenant_id=? AND session_id=?",
                        String.class, system, tenant, Long.parseLong(sessionId)));
        assertThat(persistedGeneratedDrafts)
                .doesNotContain("AI Flow Definition 85")
                .doesNotContain("AI Permission Denied Flow 85")
                .doesNotContain("AI Report 85")
                .doesNotContain("AI generated report draft 85")
                .doesNotContain("AI Print 85")
                .doesNotContain("AI Print Draft 85")
                .doesNotContain("AI Footer 85");
        var sealedGeneratedDraftCommands = String.join("\n",
                jdbcTemplate.queryForList(
                        "SELECT command_ciphertext "
                                + "FROM un_ai_generated_draft_proposal "
                                + "WHERE system_id=? AND tenant_id=? AND session_id=? "
                                + "AND command_ciphertext IS NOT NULL",
                        String.class, system, tenant, Long.parseLong(sessionId)));
        assertThat(sealedGeneratedDraftCommands)
                .doesNotContain("AI Flow Definition 85")
                .doesNotContain("AI Permission Denied Flow 85")
                .doesNotContain("AI Report 85")
                .doesNotContain("AI Print 85")
                .doesNotContain("ai_report_85", "ai_print_85")
                .doesNotContain("route", "approval_status");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_work_task "
                        + "WHERE system_id=? AND tenant_id=? AND id=? AND status='OPEN'",
                Long.class, system, tenant, Long.parseLong(createdAiTaskId))).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_work_daily_report "
                        + "WHERE system_id=? AND tenant_id=? AND id=? AND status='DRAFT' "
                        + "AND submitted_at IS NULL",
                Long.class, system, tenant, Long.parseLong(createdAiReportId))).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_ai_agent_confirmation "
                        + "WHERE system_id=? AND tenant_id=? AND session_id=?",
                Long.class, system, tenant, Long.parseLong(sessionId)))
                .isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_ai_agent_confirmation "
                        + "WHERE system_id=? AND tenant_id=? AND session_id=? "
                        + "AND state='SUCCEEDED'",
                Long.class, system, tenant, Long.parseLong(sessionId)))
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_ai_agent_confirmation "
                        + "WHERE system_id=? AND tenant_id=? AND session_id=? "
                        + "AND state='FAILED' AND result_code='PERMISSION_DENIED'",
                Long.class, system, tenant, Long.parseLong(sessionId)))
                .isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_ai_agent_confirmation_attempt "
                        + "WHERE system_id=? AND tenant_id=? AND confirmation_id IN ("
                        + "SELECT id FROM un_ai_agent_confirmation WHERE session_id=?)",
                Long.class, system, tenant, Long.parseLong(sessionId)))
                .isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_ai_agent_confirmation_event "
                        + "WHERE system_id=? AND tenant_id=? AND confirmation_id IN ("
                        + "SELECT id FROM un_ai_agent_confirmation WHERE session_id=?)",
                Long.class, system, tenant, Long.parseLong(sessionId)))
                .isEqualTo(9);
        var sealedCommands = String.join("\n", jdbcTemplate.queryForList(
                "SELECT sealed_ciphertext FROM un_ai_agent_confirmation "
                        + "WHERE system_id=? AND tenant_id=? AND session_id=?",
                String.class, system, tenant, Long.parseLong(sessionId)));
        assertThat(sealedCommands)
                .doesNotContain("AI Created Record 76")
                .doesNotContain("AI Updated Record 76")
                .doesNotContain(flowRecordSchema.approvedOptionId())
                .doesNotContain(flowRecordSchema.rejectedOptionId());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_ai_fill_proposal "
                        + "WHERE system_id=? AND tenant_id=?",
                Long.class, system, tenant))
                .isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_ai_fill_proposal "
                        + "WHERE system_id=? AND tenant_id=? "
                        + "AND state='SUCCEEDED'",
                Long.class, system, tenant)).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_ai_fill_proposal "
                        + "WHERE system_id=? AND tenant_id=? "
                        + "AND state='REJECTED'",
                Long.class, system, tenant)).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_ai_fill_proposal "
                        + "WHERE system_id=? AND tenant_id=? "
                        + "AND state='FAILED' AND result_code='PERMISSION_DENIED'",
                Long.class, system, tenant)).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_ai_fill_attempt "
                        + "WHERE system_id=? AND tenant_id=?",
                Long.class, system, tenant)).isEqualTo(6);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_ai_fill_event "
                        + "WHERE system_id=? AND tenant_id=?",
                Long.class, system, tenant)).isEqualTo(9);
        var sealedFills = String.join("\n", jdbcTemplate.queryForList(
                "SELECT sealed_ciphertext FROM un_ai_fill_proposal "
                        + "WHERE system_id=? AND tenant_id=? "
                        + "AND sealed_ciphertext IS NOT NULL",
                String.class, system, tenant));
        assertThat(sealedFills)
                .doesNotContain("AI Filled Summary 77")
                .doesNotContain("ai_summary");

        var persistedProvider = jdbcTemplate.queryForMap(
                "SELECT base_url,model_code,secret_ref FROM un_ai_provider "
                        + "WHERE system_id=? AND tenant_id=? AND id=?",
                system, tenant, Long.parseLong(providerId));
        assertThat(persistedProvider.toString())
                .doesNotContain(OPENAPI_RECORD_SECRET)
                .contains("file:");
        var storedConversation = String.join("\n", jdbcTemplate.queryForList(
                "SELECT value FROM ("
                        + "SELECT title_summary AS value FROM un_ai_agent_session "
                        + "WHERE system_id=? AND tenant_id=? AND id=? "
                        + "UNION ALL SELECT redacted_summary FROM un_ai_agent_message "
                        + "WHERE system_id=? AND tenant_id=? AND session_id=? "
                        + "UNION ALL SELECT CONCAT_WS('|',request_summary,response_summary) "
                        + "FROM un_ai_agent_turn WHERE system_id=? AND tenant_id=? "
                        + "AND session_id=? "
                        + "UNION ALL SELECT CONCAT_WS('|',tool_name,status,request_hash,"
                        + "response_hash,result_count,result_code) "
                        + "FROM un_ai_agent_tool_call WHERE system_id=? AND tenant_id=? "
                        + "AND turn_id IN (SELECT id FROM un_ai_agent_turn "
                        + "WHERE session_id=?) "
                        + "UNION ALL SELECT CONCAT_WS('|',provider_calls,prompt_tokens,"
                        + "completion_tokens,total_tokens,provider_latency_ms) "
                        + "FROM un_ai_agent_usage WHERE system_id=? AND tenant_id=? "
                        + "AND turn_id IN (SELECT id FROM un_ai_agent_turn "
                        + "WHERE session_id=?)) persisted",
                String.class,
                system, tenant, Long.parseLong(sessionId),
                system, tenant, Long.parseLong(sessionId),
                system, tenant, Long.parseLong(sessionId),
                system, tenant, Long.parseLong(sessionId),
                system, tenant, Long.parseLong(sessionId)));
        assertThat(storedConversation)
                .doesNotContain(rawTitle)
                .doesNotContain(rawQuestion)
                .doesNotContain("AI_REVOKED_PROMPT_75")
                .doesNotContain("AI_CREATE_PROMPT_76")
                .doesNotContain("AI_UPDATE_PROMPT_76")
                .doesNotContain("AI_CONFIG_FIELD_81_A")
                .doesNotContain("AI_CONFIG_FIELD_81_B")
                .doesNotContain("AI_CONFIG_SELECTION_82")
                .doesNotContain("AI_CONFIG_PAGE_82_A")
                .doesNotContain("AI_CONFIG_PAGE_82_B")
                .doesNotContain("AI_RECORD_CONTEXT_83")
                .doesNotContain("AI_RECORD_COMMENT_QUERY_90")
                .doesNotContain("AI_RECORD_HISTORY_QUERY_90")
                .doesNotContain("AI_RECORD_FILE_QUERY_90")
                .doesNotContain("AI_WORK_TASK_QUERY_83")
                .doesNotContain("AI_WORK_DAILY_REPORT_QUERY_83")
                .doesNotContain("AI_WORK_PROJECT_METRICS_QUERY_89")
                .doesNotContain("AI_RUNTIME_STATISTICS_TREND_94")
                .doesNotContain("AI_RUNTIME_STATISTICS_FOREIGN_94")
                .doesNotContain("AI_RUNTIME_REPORT_QUERY_95")
                .doesNotContain(publishedSource.code())
                .doesNotContain(foreignStatisticsCode)
                .doesNotContain(publishedReportCode)
                .doesNotContain(foreignReportCode)
                .doesNotContain("AI_TODO_QUERY_87")
                .doesNotContain("AI_MESSAGE_QUERY_87")
                .doesNotContain("AI_WORK_TASK_DRAFT_84_ASSIGNEE")
                .doesNotContain("AI_WORK_REPORT_DRAFT_84_DATE")
                .doesNotContain("AI_FLOW_DEFINITION_DRAFT_85_APPROVER")
                .doesNotContain("AI_FLOW_DENIED_DRAFT_85_APPROVER")
                .doesNotContain("AI_CONFIG_REPORT_DRAFT_85_SOURCE")
                .doesNotContain("AI_CONFIG_PRINT_TEMPLATE_DRAFT_85")
                .doesNotContain("ai_priority_81")
                .doesNotContain("ai_risk_note_81")
                .doesNotContain("ai_priority_82")
                .doesNotContain("ai_priority_options_82")
                .doesNotContain("Routing", "Timing")
                .doesNotContain("AI Created Record 76")
                .doesNotContain("AI Updated Record 76")
                .doesNotContain("AI Work Task 84")
                .doesNotContain("AI Work Task Description 84")
                .doesNotContain("AI Work Report Completed 84")
                .doesNotContain("AI Work Report Planned 84")
                .doesNotContain("AI Work Report Blockers 84")
                .doesNotContain(aiMetricsProjectTitle)
                .doesNotContain("AI_WORK_PROJECT_METRICS_DECOY_89")
                .doesNotContain("AI_WORK_PROJECT_METRICS_FOREIGN_89")
                .doesNotContain("AI Flow Definition 85")
                .doesNotContain("AI Permission Denied Flow 85")
                .doesNotContain("AI Report 85")
                .doesNotContain("AI generated report draft 85")
                .doesNotContain("AI Print 85")
                .doesNotContain("AI Print Draft 85")
                .doesNotContain("AI Footer 85")
                .doesNotContain("AI Filled Summary 77")
                .doesNotContain(recordCommentBody)
                .doesNotContain(recordFileName)
                .doesNotContain(recordFileContentText)
                .doesNotContain(recordActivityRoute)
                .doesNotContain(reportCompleted, reportPlanned, reportBlockers)
                .doesNotContain(
                        expectedTodo.path("title").asText(),
                        expectedTodo.path("routeHint").asText(),
                        expectedMessage.path("title").asText(),
                        expectedMessage.path("body").asText(),
                        expectedMessage.path("targetPath").asText())
                .doesNotContain("AI_FIXTURE_SUMMARY_75")
                .doesNotContain("Flow-bound purchase order");
        assertThat(aiProviderFixture.planCalls()).isGreaterThanOrEqualTo(39);
        assertThat(aiProviderFixture.fillCalls()).isEqualTo(3);
        assertThat(aiProviderFixture.summaryCalls()).isEqualTo(14);
        assertThat(aiProviderFixture.authorizationHeaders())
                .allMatch(value -> value.equals("Bearer " + OPENAPI_RECORD_SECRET));
    }

    private void exerciseDynamicApproverSources(
            TestClient manager,
            TestClient secondApprover,
            TestClient thirdApprover,
            String flowRoot,
            String systemId,
            String tenantId,
            String managerMemberId,
            String secondMemberId,
            String thirdMemberId,
            String roleId
    ) throws Exception {
        jdbcTemplate.update(
                "DELETE FROM un_plat_member_role WHERE system_id=? AND role_id=?",
                Long.parseLong(systemId),
                Long.parseLong(roleId)
        );
        var memberRoleId = UUID.randomUUID().getLeastSignificantBits() & Long.MAX_VALUE;
        if (memberRoleId == 0) {
            memberRoleId = 1;
        }
        assertThat(jdbcTemplate.update(
                """
                INSERT INTO un_plat_member_role
                  (id,system_id,member_id,role_id,tenant_id,valid_from,valid_until,
                   created_at,created_by)
                VALUES (?,?,?,?,?,CURRENT_TIMESTAMP(3),NULL,CURRENT_TIMESTAMP(3),?)
                """,
                memberRoleId,
                Long.parseLong(systemId),
                Long.parseLong(secondMemberId),
                Long.parseLong(roleId),
                Long.parseLong(tenantId),
                Long.parseLong(managerMemberId)
        )).isOne();
        var created = manager.postWithCsrf(
                flowRoot + "/definitions",
                json(Map.of(
                        "name", "Current tenant role approvers",
                        "approvalMode", "SEQUENTIAL",
                        "approverSource", Map.of(
                                "kind", "ROLE",
                                "sourceId", roleId
                        )
                )),
                Map.of());
        assertCreated(created);
        var definitionId = text(created.body(), "/data/definitionId");
        assertThat(text(created.body(), "/data/approverSource/kind")).isEqualTo("ROLE");
        assertThat(text(created.body(), "/data/approverSource/sourceId")).isEqualTo(roleId);
        assertThat(created.body().at("/data/approverIds"))
                .extracting(JsonNode::asText)
                .containsExactly(secondMemberId);

        var checked = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/draft:check",
                "{}",
                Map.of());
        assertOk(checked);
        assertThat(text(checked.body(), "/data/verdict")).isEqualTo("READY");

        var preview = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/draft:simulate",
                json(Map.of("businessKey", "dynamic-role-preview-before")),
                Map.of());
        assertOk(preview);
        assertThat(preview.body().at("/data/steps"))
                .extracting(step -> text(step, "/approverId"))
                .containsExactly(secondMemberId);

        var published = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + ":publish",
                "{}",
                Map.of());
        assertOk(published);
        assertThat(text(published.body(), "/data/approverSource/kind")).isEqualTo("ROLE");

        var firstStarted = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/instances",
                json(Map.of("businessKey", "dynamic-role-snapshot-before")),
                Map.of("Idempotency-Key", key()));
        assertCreated(firstStarted);
        var firstInstanceId = text(firstStarted.body(), "/data/instanceId");
        assertThat(firstStarted.body().at("/data/approverIds"))
                .extracting(JsonNode::asText)
                .containsExactly(secondMemberId);

        assertThat(jdbcTemplate.update(
                """
                UPDATE un_plat_member_role
                   SET member_id=?
                 WHERE system_id=? AND role_id=? AND member_id=?
                """,
                Long.parseLong(managerMemberId),
                Long.parseLong(systemId),
                Long.parseLong(roleId),
                Long.parseLong(secondMemberId)
        )).isOne();

        var currentPreview = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/draft:simulate",
                json(Map.of("businessKey", "dynamic-role-preview-after")),
                Map.of());
        assertOk(currentPreview);
        assertThat(currentPreview.body().at("/data/steps"))
                .extracting(step -> text(step, "/approverId"))
                .containsExactly(managerMemberId);

        var secondStarted = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/instances",
                json(Map.of("businessKey", "dynamic-role-snapshot-after")),
                Map.of("Idempotency-Key", key()));
        assertCreated(secondStarted);
        assertThat(secondStarted.body().at("/data/approverIds"))
                .extracting(JsonNode::asText)
                .containsExactly(managerMemberId);

        var immutableFirst = manager.get(flowRoot + "/instances/" + firstInstanceId);
        assertOk(immutableFirst);
        assertThat(immutableFirst.body().at("/data/approverIds"))
                .extracting(JsonNode::asText)
                .containsExactly(secondMemberId);

        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT JSON_UNQUOTE(JSON_EXTRACT(approver_sources, '$.route.kind'))
                  FROM un_flow_definition_version
                 WHERE system_id=? AND tenant_id=? AND definition_id=? AND version_no=1
                """,
                String.class,
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(definitionId)
        )).isEqualTo("ROLE");

        var department = manager.postWithCsrf(
                "/api/v1/systems/" + systemId + "/admin/departments",
                json(Map.of(
                        "code", "flow_dynamic_" + Long.toUnsignedString(System.nanoTime(), 36),
                        "name", "Flow Dynamic Approvers"
                )),
                Map.of("Idempotency-Key", key()));
        assertOk(department);
        var departmentId = text(department.body(), "/data/id");
        var memberDepartmentId = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
        if (memberDepartmentId == 0) {
            memberDepartmentId = 1;
        }
        assertThat(jdbcTemplate.update(
                """
                INSERT INTO un_plat_member_department
                  (id,scope_type,scope_key,system_id,tenant_id,account_id,member_id,
                   department_id,is_primary,created_at,created_by,updated_at,updated_by,
                   deleted_at,deleted_by,version)
                VALUES (?,'SYSTEM',?,?,?,NULL,?,?,FALSE,CURRENT_TIMESTAMP(3),?,
                        CURRENT_TIMESTAMP(3),?,NULL,NULL,0)
                """,
                memberDepartmentId,
                Long.parseLong(systemId),
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(secondMemberId),
                Long.parseLong(departmentId),
                Long.parseLong(managerMemberId),
                Long.parseLong(managerMemberId)
        )).isOne();
        assertOk(manager.postWithCsrf("/api/v1/auth/refresh", "{}", Map.of()));
        assertOk(manager.postWithCsrf(
                "/api/v1/context/systems/" + systemId + ":switch",
                "{}",
                Map.of()));

        var departmentDefinition = manager.postWithCsrf(
                flowRoot + "/definitions",
                json(Map.of(
                        "name", "Current tenant department approvers",
                        "approverSource", Map.of(
                                "kind", "DEPARTMENT",
                                "sourceId", departmentId
                        )
                )),
                Map.of());
        assertCreated(departmentDefinition);
        assertThat(departmentDefinition.body().at("/data/approverIds"))
                .extracting(JsonNode::asText)
                .containsExactly(secondMemberId);
        var departmentDefinitionId = text(
                departmentDefinition.body(), "/data/definitionId");
        assertOk(manager.postWithCsrf(
                flowRoot + "/definitions/" + departmentDefinitionId + ":publish",
                "{}",
                Map.of()));
        var departmentStarted = manager.postWithCsrf(
                flowRoot + "/definitions/" + departmentDefinitionId + "/instances",
                json(Map.of("businessKey", "dynamic-department-snapshot")),
                Map.of("Idempotency-Key", key()));
        assertCreated(departmentStarted);
        assertThat(departmentStarted.body().at("/data/approverIds"))
                .extracting(JsonNode::asText)
                .containsExactly(secondMemberId);

        assertThat(jdbcTemplate.update(
                """
                UPDATE un_plat_member_role
                   SET member_id=?
                 WHERE system_id=? AND tenant_id=? AND role_id=? AND member_id=?
                """,
                Long.parseLong(secondMemberId),
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(roleId),
                Long.parseLong(managerMemberId)
        )).isOne();
        var thirdApproverRoleLinkId =
                UUID.randomUUID().getLeastSignificantBits() & Long.MAX_VALUE;
        if (thirdApproverRoleLinkId == 0) {
            thirdApproverRoleLinkId = 1;
        }
        assertThat(jdbcTemplate.update(
                """
                INSERT INTO un_plat_member_role
                  (id,system_id,member_id,role_id,tenant_id,valid_from,valid_until,
                   created_at,created_by)
                VALUES (?,?,?,?,?,CURRENT_TIMESTAMP(3),NULL,CURRENT_TIMESTAMP(3),?)
                """,
                thirdApproverRoleLinkId,
                Long.parseLong(systemId),
                Long.parseLong(thirdMemberId),
                Long.parseLong(roleId),
                Long.parseLong(tenantId),
                Long.parseLong(managerMemberId)
        )).isOne();

        exerciseOrganizationalApproverSources(
                manager,
                secondApprover,
                thirdApprover,
                flowRoot,
                systemId,
                tenantId,
                managerMemberId,
                secondMemberId,
                thirdMemberId,
                departmentId,
                text(department.body(), "/data/version"));
    }

    private void exerciseRecordMemberApproverSource(
            TestClient manager,
            TestClient initialApprover,
            String flowRoot,
            String runtimeRoot,
            String systemId,
            FlowRecordSchema schema,
            String initialApproverMemberId,
            String replacementApproverMemberId
    ) throws Exception {
        var catalog = manager.get(
                flowRoot + "/approver-sources/record-member-fields?moduleCode="
                        + FLOW_RECORD_MODULE_CODE);
        assertOk(catalog);
        var memberField = item(
                catalog.body().at("/data/items"),
                "sourceId",
                schema.approverMemberFieldId());
        assertThat(memberField.path("moduleCode").asText()).isEqualTo(FLOW_RECORD_MODULE_CODE);
        assertThat(memberField.path("fieldCode").asText()).isEqualTo("approver_member");
        assertThat(memberField.path("fieldName").asText()).isEqualTo("Record approver");

        var record = manager.postWithCsrf(
                runtimeRoot + "/records",
                json(Map.of(
                        "schemaVersionId", schema.versionId(),
                        "title", "Record member approver source",
                        "values", Map.of(
                                "approver_member", initialApproverMemberId
                        )
                )),
                Map.of("Idempotency-Key", key()));
        assertCreated(record);
        var recordId = text(record.body(), "/data/recordId");
        var activated = manager.postWithCsrf(
                runtimeRoot + "/records/" + recordId + ":activate",
                json(Map.of("expectedVersion", 0)),
                Map.of("Idempotency-Key", key()));
        assertOk(activated);

        var definition = manager.postWithCsrf(
                flowRoot + "/definitions",
                json(Map.of(
                        "name", "Record member field approver",
                        "approverSource", Map.of(
                                "kind", "RECORD_MEMBER_FIELD",
                                "sourceId", schema.approverMemberFieldId(),
                                "moduleCode", FLOW_RECORD_MODULE_CODE
                        )
                )),
                Map.of());
        assertCreated(definition);
        var definitionId = text(definition.body(), "/data/definitionId");
        assertThat(text(definition.body(), "/data/approverSource/kind"))
                .isEqualTo("RECORD_MEMBER_FIELD");
        assertThat(text(definition.body(), "/data/approverSource/sourceId"))
                .isEqualTo(schema.approverMemberFieldId());
        assertThat(text(definition.body(), "/data/approverSource/moduleCode"))
                .isEqualTo(FLOW_RECORD_MODULE_CODE);
        assertOk(manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/draft:check",
                "{}",
                Map.of()));
        var simulation = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/draft:simulate",
                json(Map.of(
                        "businessKey", "record-member-preview",
                        "values", Map.of(
                                "approver_member", initialApproverMemberId
                        )
                )),
                Map.of());
        assertOk(simulation);
        assertThat(simulation.body().at("/data/steps"))
                .extracting(step -> text(step, "/approverId"))
                .containsExactly(initialApproverMemberId);
        assertOk(manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + ":publish",
                "{}",
                Map.of()));

        var firstStarted = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/instances",
                json(Map.of(
                        "businessKey", "record-member-snapshot-before",
                        "recordBinding", Map.of(
                                "moduleCode", FLOW_RECORD_MODULE_CODE,
                                "recordId", recordId
                        )
                )),
                Map.of("Idempotency-Key", key()));
        assertCreated(firstStarted);
        var firstInstanceId = text(firstStarted.body(), "/data/instanceId");
        assertThat(firstStarted.body().at("/data/approverIds"))
                .extracting(JsonNode::asText)
                .containsExactly(initialApproverMemberId);
        refreshSystemContext(initialApprover, systemId);
        var firstApproved = initialApprover.postWithCsrf(
                flowRoot + "/instances/" + firstInstanceId + ":approve",
                json(Map.of("comment", "Approved from original record member")),
                Map.of());
        assertOk(firstApproved);
        assertThat(text(firstApproved.body(), "/data/status")).isEqualTo("APPROVED");

        var updated = manager.putWithCsrf(
                runtimeRoot + "/records/" + recordId,
                json(Map.of(
                        "schemaVersionId", schema.versionId(),
                        "title", "Record member approver source updated",
                        "expectedVersion", 1,
                        "values", Map.of(
                                "approver_member", replacementApproverMemberId
                        )
                )),
                Map.of("Idempotency-Key", key()));
        assertOk(updated);
        assertThat(updated.body().at("/data/version").asLong()).isEqualTo(2);
        var secondStarted = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/instances",
                json(Map.of(
                        "businessKey", "record-member-snapshot-after",
                        "recordBinding", Map.of(
                                "moduleCode", FLOW_RECORD_MODULE_CODE,
                                "recordId", recordId
                        )
                )),
                Map.of("Idempotency-Key", key()));
        assertCreated(secondStarted);
        assertThat(secondStarted.body().at("/data/approverIds"))
                .extracting(JsonNode::asText)
                .containsExactly(replacementApproverMemberId);

        var immutableFirst = manager.get(flowRoot + "/instances/" + firstInstanceId);
        assertOk(immutableFirst);
        assertThat(immutableFirst.body().at("/data/approverIds"))
                .extracting(JsonNode::asText)
                .containsExactly(initialApproverMemberId);
        var secondApproved = manager.postWithCsrf(
                flowRoot + "/instances/"
                        + text(secondStarted.body(), "/data/instanceId") + ":approve",
                json(Map.of("comment", "Approved from replacement record member")),
                Map.of());
        assertOk(secondApproved);
        assertThat(text(secondApproved.body(), "/data/status")).isEqualTo("APPROVED");

        var cleared = manager.putWithCsrf(
                runtimeRoot + "/records/" + recordId,
                json(Map.of(
                        "schemaVersionId", schema.versionId(),
                        "title", "Record member approver source cleared",
                        "expectedVersion", 2,
                        "values", Map.of(
                                "approver_member", objectMapper.getNodeFactory().nullNode()
                        )
                )),
                Map.of("Idempotency-Key", key()));
        assertOk(cleared);
        assertThat(cleared.body().at("/data/version").asLong()).isEqualTo(3);
        var instanceCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_flow_instance "
                        + "WHERE system_id=? AND definition_id=?",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(definitionId));
        assertError(manager.postWithCsrf(
                        flowRoot + "/definitions/" + definitionId + "/instances",
                        json(Map.of(
                                "businessKey", "record-member-empty",
                                "recordBinding", Map.of(
                                        "moduleCode", FLOW_RECORD_MODULE_CODE,
                                        "recordId", recordId
                                )
                        )),
                        Map.of("Idempotency-Key", key())),
                422,
                "FLOW_APPROVER_SOURCE_EMPTY");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_flow_instance "
                        + "WHERE system_id=? AND definition_id=?",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(definitionId))).isEqualTo(instanceCount);
    }

    private void exerciseOrganizationalApproverSources(
            TestClient manager,
            TestClient initialApprover,
            TestClient replacementApprover,
            String flowRoot,
            String systemId,
            String tenantId,
            String requesterMemberId,
            String initialApproverMemberId,
            String replacementApproverMemberId,
            String departmentId,
            String departmentVersion
    ) throws Exception {
        var organizationRoot = "/api/v1/systems/" + systemId + "/admin";
        var requesterVersion = Long.toString(jdbcTemplate.queryForObject(
                "SELECT version FROM un_plat_member WHERE system_id=? AND id=?",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(requesterMemberId)));
        var assignedManager = manager.putWithCsrf(
                organizationRoot + "/members/" + requesterMemberId + "/manager",
                json(Map.of(
                        "managerMemberId", initialApproverMemberId,
                        "version", requesterVersion
                )),
                Map.of());
        assertOk(assignedManager);
        assertThat(text(assignedManager.body(), "/data/managerMemberId"))
                .isEqualTo(initialApproverMemberId);
        refreshSystemContext(manager, systemId);
        var initialApproverVersion = Long.toString(jdbcTemplate.queryForObject(
                "SELECT version FROM un_plat_member WHERE system_id=? AND id=?",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(initialApproverMemberId)));
        assertError(manager.putWithCsrf(
                        organizationRoot + "/members/" + initialApproverMemberId + "/manager",
                        json(Map.of(
                                "managerMemberId", requesterMemberId,
                                "version", initialApproverVersion
                        )),
                        Map.of()),
                422,
                "STATE_TRANSITION_INVALID");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_plat_member_manager_assignment "
                        + "WHERE system_id=? AND tenant_id=? AND member_id=? AND status='ACTIVE'",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(initialApproverMemberId))).isZero();

        var managerDefinition = manager.postWithCsrf(
                flowRoot + "/definitions",
                json(Map.of(
                        "name", "Requester direct manager",
                        "approverSource", Map.of("kind", "REQUESTER_MANAGER")
                )),
                Map.of());
        assertCreated(managerDefinition);
        var managerDefinitionId = text(managerDefinition.body(), "/data/definitionId");
        assertThat(text(managerDefinition.body(), "/data/approverSource/kind"))
                .isEqualTo("REQUESTER_MANAGER");
        assertOk(manager.postWithCsrf(
                flowRoot + "/definitions/" + managerDefinitionId + "/draft:check",
                "{}",
                Map.of()));
        var managerPreview = manager.postWithCsrf(
                flowRoot + "/definitions/" + managerDefinitionId + "/draft:simulate",
                json(Map.of("businessKey", "requester-manager-preview-before")),
                Map.of());
        assertOk(managerPreview);
        assertThat(managerPreview.body().at("/data/steps"))
                .extracting(step -> text(step, "/approverId"))
                .containsExactly(initialApproverMemberId);
        assertOk(manager.postWithCsrf(
                flowRoot + "/definitions/" + managerDefinitionId + ":publish",
                "{}",
                Map.of()));

        var firstManagerInstance = manager.postWithCsrf(
                flowRoot + "/definitions/" + managerDefinitionId + "/instances",
                json(Map.of("businessKey", "requester-manager-snapshot-before")),
                Map.of("Idempotency-Key", key()));
        assertCreated(firstManagerInstance);
        var firstManagerInstanceId = text(firstManagerInstance.body(), "/data/instanceId");
        assertThat(text(firstManagerInstance.body(), "/data/requesterId"))
                .isEqualTo(requesterMemberId);
        assertThat(firstManagerInstance.body().at("/data/approverIds"))
                .extracting(JsonNode::asText)
                .containsExactly(initialApproverMemberId);

        var replacedManager = manager.putWithCsrf(
                organizationRoot + "/members/" + requesterMemberId + "/manager",
                json(Map.of(
                        "managerMemberId", replacementApproverMemberId,
                        "version", text(assignedManager.body(), "/data/version")
                )),
                Map.of());
        assertOk(replacedManager);
        assertThat(text(replacedManager.body(), "/data/managerMemberId"))
                .isEqualTo(replacementApproverMemberId);
        refreshSystemContext(manager, systemId);
        var secondManagerInstance = manager.postWithCsrf(
                flowRoot + "/definitions/" + managerDefinitionId + "/instances",
                json(Map.of("businessKey", "requester-manager-snapshot-after")),
                Map.of("Idempotency-Key", key()));
        assertCreated(secondManagerInstance);
        assertThat(secondManagerInstance.body().at("/data/approverIds"))
                .extracting(JsonNode::asText)
                .containsExactly(replacementApproverMemberId);
        var immutableManagerInstance = manager.get(
                flowRoot + "/instances/" + firstManagerInstanceId);
        assertOk(immutableManagerInstance);
        assertThat(immutableManagerInstance.body().at("/data/approverIds"))
                .extracting(JsonNode::asText)
                .containsExactly(initialApproverMemberId);
        refreshSystemContext(initialApprover, systemId);
        refreshSystemContext(replacementApprover, systemId);
        var firstManagerApproved = initialApprover.postWithCsrf(
                flowRoot + "/instances/" + firstManagerInstanceId + ":approve",
                json(Map.of("comment", "Approved by original direct manager")),
                Map.of());
        assertOk(firstManagerApproved);
        assertThat(text(firstManagerApproved.body(), "/data/status")).isEqualTo("APPROVED");
        var secondManagerApproved = replacementApprover.postWithCsrf(
                flowRoot + "/instances/"
                        + text(secondManagerInstance.body(), "/data/instanceId") + ":approve",
                json(Map.of("comment", "Approved by replacement direct manager")),
                Map.of());
        assertOk(secondManagerApproved);
        assertThat(text(secondManagerApproved.body(), "/data/status")).isEqualTo("APPROVED");

        var clearManager = objectMapper.createObjectNode()
                .putNull("managerMemberId")
                .put("version", text(replacedManager.body(), "/data/version"));
        var clearedManager = manager.putWithCsrf(
                organizationRoot + "/members/" + requesterMemberId + "/manager",
                json(clearManager),
                Map.of());
        assertOk(clearedManager);
        assertThat(absentOrNull(clearedManager.body().at("/data/managerMemberId"))).isTrue();
        refreshSystemContext(manager, systemId);
        var managerInstanceCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_flow_instance "
                        + "WHERE system_id=? AND tenant_id=? AND definition_id=?",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(managerDefinitionId));
        assertError(manager.postWithCsrf(
                        flowRoot + "/definitions/" + managerDefinitionId + "/instances",
                        json(Map.of("businessKey", "requester-manager-cleared")),
                        Map.of("Idempotency-Key", key())),
                422,
                "FLOW_APPROVER_SOURCE_EMPTY");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_flow_instance "
                        + "WHERE system_id=? AND tenant_id=? AND definition_id=?",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(managerDefinitionId))).isEqualTo(managerInstanceCount);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_plat_member_manager_assignment "
                        + "WHERE system_id=? AND tenant_id=? AND member_id=? AND status='CLEARED'",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(requesterMemberId))).isEqualTo(2L);

        var replacementDepartmentLinkId =
                UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
        if (replacementDepartmentLinkId == 0) {
            replacementDepartmentLinkId = 1;
        }
        assertThat(jdbcTemplate.update(
                """
                INSERT INTO un_plat_member_department
                  (id,scope_type,scope_key,system_id,tenant_id,account_id,member_id,
                   department_id,is_primary,created_at,created_by,updated_at,updated_by,
                   deleted_at,deleted_by,version)
                VALUES (?,'SYSTEM',?,?,?,NULL,?,?,FALSE,CURRENT_TIMESTAMP(3),?,
                        CURRENT_TIMESTAMP(3),?,NULL,NULL,0)
                """,
                replacementDepartmentLinkId,
                Long.parseLong(systemId),
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(replacementApproverMemberId),
                Long.parseLong(departmentId),
                Long.parseLong(requesterMemberId),
                Long.parseLong(requesterMemberId)
        )).isOne();

        var assignedLeader = manager.putWithCsrf(
                organizationRoot + "/departments/" + departmentId + "/leader",
                json(Map.of(
                        "leaderMemberId", initialApproverMemberId,
                        "version", departmentVersion
                )),
                Map.of());
        assertOk(assignedLeader);
        assertThat(text(assignedLeader.body(), "/data/leaderMemberId"))
                .isEqualTo(initialApproverMemberId);
        refreshSystemContext(manager, systemId);

        var leaderDefinition = manager.postWithCsrf(
                flowRoot + "/definitions",
                json(Map.of(
                        "name", "Department leader approver",
                        "approverSource", Map.of(
                                "kind", "DEPARTMENT_LEADER",
                                "sourceId", departmentId
                        )
                )),
                Map.of());
        assertCreated(leaderDefinition);
        var leaderDefinitionId = text(leaderDefinition.body(), "/data/definitionId");
        assertThat(leaderDefinition.body().at("/data/approverIds"))
                .extracting(JsonNode::asText)
                .containsExactly(initialApproverMemberId);
        assertOk(manager.postWithCsrf(
                flowRoot + "/definitions/" + leaderDefinitionId + ":publish",
                "{}",
                Map.of()));
        var firstLeaderInstance = manager.postWithCsrf(
                flowRoot + "/definitions/" + leaderDefinitionId + "/instances",
                json(Map.of("businessKey", "department-leader-snapshot-before")),
                Map.of("Idempotency-Key", key()));
        assertCreated(firstLeaderInstance);
        var firstLeaderInstanceId = text(firstLeaderInstance.body(), "/data/instanceId");
        assertThat(firstLeaderInstance.body().at("/data/approverIds"))
                .extracting(JsonNode::asText)
                .containsExactly(initialApproverMemberId);

        var replacedLeader = manager.putWithCsrf(
                organizationRoot + "/departments/" + departmentId + "/leader",
                json(Map.of(
                        "leaderMemberId", replacementApproverMemberId,
                        "version", text(assignedLeader.body(), "/data/version")
                )),
                Map.of());
        assertOk(replacedLeader);
        refreshSystemContext(manager, systemId);
        var secondLeaderInstance = manager.postWithCsrf(
                flowRoot + "/definitions/" + leaderDefinitionId + "/instances",
                json(Map.of("businessKey", "department-leader-snapshot-after")),
                Map.of("Idempotency-Key", key()));
        assertCreated(secondLeaderInstance);
        assertThat(secondLeaderInstance.body().at("/data/approverIds"))
                .extracting(JsonNode::asText)
                .containsExactly(replacementApproverMemberId);
        var immutableLeaderInstance = manager.get(
                flowRoot + "/instances/" + firstLeaderInstanceId);
        assertOk(immutableLeaderInstance);
        assertThat(immutableLeaderInstance.body().at("/data/approverIds"))
                .extracting(JsonNode::asText)
                .containsExactly(initialApproverMemberId);
        refreshSystemContext(initialApprover, systemId);
        refreshSystemContext(replacementApprover, systemId);
        var firstLeaderApproved = initialApprover.postWithCsrf(
                flowRoot + "/instances/" + firstLeaderInstanceId + ":approve",
                json(Map.of("comment", "Approved by original department leader")),
                Map.of());
        assertOk(firstLeaderApproved);
        assertThat(text(firstLeaderApproved.body(), "/data/status")).isEqualTo("APPROVED");
        var secondLeaderApproved = replacementApprover.postWithCsrf(
                flowRoot + "/instances/"
                        + text(secondLeaderInstance.body(), "/data/instanceId") + ":approve",
                json(Map.of("comment", "Approved by replacement department leader")),
                Map.of());
        assertOk(secondLeaderApproved);
        assertThat(text(secondLeaderApproved.body(), "/data/status")).isEqualTo("APPROVED");

        var clearLeader = objectMapper.createObjectNode()
                .putNull("leaderMemberId")
                .put("version", text(replacedLeader.body(), "/data/version"));
        var clearedLeader = manager.putWithCsrf(
                organizationRoot + "/departments/" + departmentId + "/leader",
                json(clearLeader),
                Map.of());
        assertOk(clearedLeader);
        assertThat(absentOrNull(clearedLeader.body().at("/data/leaderMemberId"))).isTrue();
        refreshSystemContext(manager, systemId);
        assertError(manager.postWithCsrf(
                        flowRoot + "/definitions/" + leaderDefinitionId + "/instances",
                        json(Map.of("businessKey", "department-leader-cleared")),
                        Map.of("Idempotency-Key", key())),
                422,
                "FLOW_APPROVER_SOURCE_EMPTY");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_plat_department_leader_assignment "
                        + "WHERE system_id=? AND tenant_id=? AND department_id=? "
                        + "AND status='CLEARED'",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(departmentId))).isEqualTo(2L);
    }

    private void exerciseVersionHistoryRestore(
            TestClient manager,
            TestClient nonManager,
            String flowRoot,
            String managerMemberId,
            String secondMemberId
    ) throws Exception {
        assertOk(nonManager.postWithCsrf("/api/v1/auth/refresh", "{}", Map.of()));
        var created = manager.postWithCsrf(
                flowRoot + "/definitions",
                json(Map.of(
                        "name", "Version history original",
                        "approverIds", List.of(managerMemberId)
                )),
                Map.of());
        assertCreated(created);
        var definitionId = text(created.body(), "/data/definitionId");

        var firstPublished = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + ":publish",
                "{}",
                Map.of());
        assertOk(firstPublished);
        assertThat(firstPublished.body().at("/data/version").asInt()).isOne();
        assertThat(firstPublished.body().at("/data/sourceRevision").asInt()).isOne();

        var firstInstance = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/instances",
                json(Map.of("businessKey", "history-v1-" + key())),
                Map.of("Idempotency-Key", key()));
        assertCreated(firstInstance);
        var firstInstanceId = text(firstInstance.body(), "/data/instanceId");
        assertThat(firstInstance.body().at("/data/definitionVersion").asInt()).isOne();

        var revised = manager.putWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/draft",
                json(Map.of(
                        "name", "Version history replacement",
                        "approverIds", List.of(secondMemberId)
                )),
                Map.of());
        assertOk(revised);
        assertThat(revised.body().at("/data/revision").asInt()).isEqualTo(2);
        var secondPublished = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + ":publish",
                "{}",
                Map.of());
        assertOk(secondPublished);
        assertThat(secondPublished.body().at("/data/version").asInt()).isEqualTo(2);

        var secondInstance = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/instances",
                json(Map.of("businessKey", "history-v2-" + key())),
                Map.of("Idempotency-Key", key()));
        assertCreated(secondInstance);
        var secondInstanceId = text(secondInstance.body(), "/data/instanceId");
        assertThat(secondInstance.body().at("/data/definitionVersion").asInt()).isEqualTo(2);
        assertThat(text(secondInstance.body(), "/data/approverId")).isEqualTo(secondMemberId);

        var newestPage = manager.get(
                flowRoot + "/definitions/" + definitionId + "/versions?page=1&size=1");
        assertOk(newestPage);
        assertThat(newestPage.body().at("/data/page").asInt()).isOne();
        assertThat(newestPage.body().at("/data/size").asInt()).isOne();
        assertThat(newestPage.body().at("/data/total").asLong()).isEqualTo(2);
        assertThat(newestPage.body().at("/data/items")).hasSize(1);
        assertThat(newestPage.body().at("/data/items/0/version").asInt()).isEqualTo(2);
        assertThat(text(newestPage.body(), "/data/items/0/name"))
                .isEqualTo("Version history replacement");
        assertThat(newestPage.body().at("/data/items/0/approverIds"))
                .extracting(JsonNode::asText)
                .containsExactly(secondMemberId);

        var oldestPage = manager.get(
                flowRoot + "/definitions/" + definitionId + "/versions?page=2&size=1");
        assertOk(oldestPage);
        assertThat(oldestPage.body().at("/data/items/0/version").asInt()).isOne();
        assertThat(text(oldestPage.body(), "/data/items/0/name"))
                .isEqualTo("Version history original");
        assertThat(oldestPage.body().at("/data/items/0/approverIds"))
                .extracting(JsonNode::asText)
                .containsExactly(managerMemberId);

        assertError(nonManager.get(
                        flowRoot + "/definitions/" + definitionId + "/versions?page=1&size=10"),
                403,
                "PERMISSION_DENIED");
        assertError(nonManager.postWithCsrf(
                        flowRoot + "/definitions/" + definitionId + "/versions/1:restore",
                        "{}",
                        Map.of("Idempotency-Key", key())),
                403,
                "PERMISSION_DENIED");

        var restored = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/versions/1:restore",
                "{}",
                Map.of("Idempotency-Key", key()));
        assertOk(restored);
        assertThat(restored.body().at("/data/revision").asInt()).isEqualTo(3);
        assertThat(text(restored.body(), "/data/name")).isEqualTo("Version history original");
        assertThat(restored.body().at("/data/approverIds"))
                .extracting(JsonNode::asText)
                .containsExactly(managerMemberId);

        var historyBeforeRepublish = manager.get(
                flowRoot + "/definitions/" + definitionId + "/versions?page=1&size=10");
        assertOk(historyBeforeRepublish);
        assertThat(historyBeforeRepublish.body().at("/data/total").asLong()).isEqualTo(2);
        assertThat(historyBeforeRepublish.body().at("/data/items"))
                .extracting(item -> item.path("version").asInt())
                .containsExactly(2, 1);

        var beforeRepublish = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/instances",
                json(Map.of("businessKey", "history-before-republish-" + key())),
                Map.of("Idempotency-Key", key()));
        assertCreated(beforeRepublish);
        assertThat(beforeRepublish.body().at("/data/definitionVersion").asInt()).isEqualTo(2);
        assertThat(text(beforeRepublish.body(), "/data/approverId")).isEqualTo(secondMemberId);

        var restoredPublished = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + ":publish",
                "{}",
                Map.of());
        assertOk(restoredPublished);
        assertThat(restoredPublished.body().at("/data/version").asInt()).isEqualTo(3);
        assertThat(restoredPublished.body().at("/data/sourceRevision").asInt()).isEqualTo(3);
        assertThat(text(restoredPublished.body(), "/data/name"))
                .isEqualTo("Version history original");

        var afterRepublish = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/instances",
                json(Map.of("businessKey", "history-v3-" + key())),
                Map.of("Idempotency-Key", key()));
        assertCreated(afterRepublish);
        assertThat(afterRepublish.body().at("/data/definitionVersion").asInt()).isEqualTo(3);
        assertThat(text(afterRepublish.body(), "/data/approverId")).isEqualTo(managerMemberId);

        var preservedFirstInstance = manager.get(flowRoot + "/instances/" + firstInstanceId);
        assertOk(preservedFirstInstance);
        assertThat(preservedFirstInstance.body().at("/data/definitionVersion").asInt()).isOne();
        var preservedSecondInstance = manager.get(flowRoot + "/instances/" + secondInstanceId);
        assertOk(preservedSecondInstance);
        assertThat(preservedSecondInstance.body().at("/data/definitionVersion").asInt()).isEqualTo(2);

        var finalHistory = manager.get(
                flowRoot + "/definitions/" + definitionId + "/versions?page=1&size=10");
        assertOk(finalHistory);
        assertThat(finalHistory.body().at("/data/total").asLong()).isEqualTo(3);
        assertThat(finalHistory.body().at("/data/items"))
                .extracting(item -> item.path("version").asInt())
                .containsExactly(3, 2, 1);
    }

    private void exerciseDraftCheckSimulation(
            TestClient manager,
            String flowRoot,
            String systemId,
            String tenantId,
            String managerMemberId,
            String secondMemberId
    ) throws Exception {
        var created = manager.postWithCsrf(
                flowRoot + "/definitions",
                json(Map.of(
                        "name", "Draft preflight HTTP journey",
                        "approverIds", List.of(managerMemberId, secondMemberId),
                        "triggerBinding", Map.of(
                                "moduleCode", FLOW_RECORD_MODULE_CODE,
                                "event", "RECORD_ACTIVATED",
                                "priority", 70,
                                "exclusive", true,
                                "conditions", List.of(Map.of(
                                        "fieldCode", "route",
                                        "operator", "EQ",
                                        "value", "preflight"
                                ))
                        )
                )),
                Map.of());
        assertCreated(created);
        var definitionId = text(created.body(), "/data/definitionId");

        var checked = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/draft:check",
                "{}",
                Map.of("Idempotency-Key", key()));
        assertOk(checked);
        assertThat(text(checked.body(), "/data/verdict")).isEqualTo("READY");
        assertThat(checked.body().at("/data/blockerCount").asInt()).isZero();
        assertThat(checked.body().at("/data/revision").asInt()).isOne();

        var versionsBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_flow_definition_version "
                        + "WHERE system_id=? AND tenant_id=? AND definition_id=?",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(definitionId));
        var instancesBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_flow_instance "
                        + "WHERE system_id=? AND tenant_id=? AND definition_id=?",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(definitionId));

        var simulated = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/draft:simulate",
                json(Map.of(
                        "requesterId", managerMemberId,
                        "businessKey", "preflight-http-preview",
                        "trigger", Map.of(
                                "moduleCode", FLOW_RECORD_MODULE_CODE,
                                "event", "RECORD_ACTIVATED",
                                "values", Map.of("route", "preflight")
                        )
                )),
                Map.of("Idempotency-Key", key()));
        assertOk(simulated);
        assertThat(simulated.body().at("/data/startable").asBoolean()).isTrue();
        assertThat(text(simulated.body(), "/data/reason")).isEqualTo("TRIGGER_MATCHED");
        assertThat(text(simulated.body(), "/data/requesterId")).isEqualTo(managerMemberId);
        assertThat(simulated.body().at("/data/steps"))
                .extracting(item -> item.path("approverId").asText())
                .containsExactly(managerMemberId, secondMemberId);
        assertThat(text(simulated.body(), "/data/trigger/moduleCode"))
                .isEqualTo(FLOW_RECORD_MODULE_CODE);

        var missed = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/draft:simulate",
                json(Map.of(
                        "trigger", Map.of(
                                "moduleCode", FLOW_RECORD_MODULE_CODE,
                                "event", "RECORD_ACTIVATED",
                                "values", Map.of("route", "other")
                        )
                )),
                Map.of("Idempotency-Key", key()));
        assertOk(missed);
        assertThat(missed.body().at("/data/startable").asBoolean()).isFalse();
        assertThat(text(missed.body(), "/data/reason"))
                .isEqualTo("TRIGGER_CONDITIONS_NOT_MATCHED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_flow_definition_version "
                        + "WHERE system_id=? AND tenant_id=? AND definition_id=?",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(definitionId))).isEqualTo(versionsBefore);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_flow_instance "
                        + "WHERE system_id=? AND tenant_id=? AND definition_id=?",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(definitionId))).isEqualTo(instancesBefore);

        assertThat(jdbcTemplate.update(
                "UPDATE un_plat_member_tenant SET status='DISABLED',version=version+1 "
                        + "WHERE system_id=? AND tenant_id=? AND member_id=? AND status='ACTIVE'",
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(secondMemberId))).isOne();
        try {
            var blocked = manager.postWithCsrf(
                    flowRoot + "/definitions/" + definitionId + "/draft:check",
                    "{}",
                    Map.of("Idempotency-Key", key()));
            assertOk(blocked);
            assertThat(text(blocked.body(), "/data/verdict")).isEqualTo("BLOCKED");
            assertThat(blocked.body().at("/data/issues")).singleElement().satisfies(issue -> {
                assertThat(text(issue, "/code")).isEqualTo("APPROVER_INACTIVE");
                assertThat(text(issue, "/path")).isEqualTo("/approverIds/1");
            });
            assertError(manager.postWithCsrf(
                            flowRoot + "/definitions/" + definitionId + ":publish",
                            "{}",
                            Map.of()),
                    422,
                    "FLOW_DRAFT_CHECK_BLOCKED");
        } finally {
            assertThat(jdbcTemplate.update(
                    "UPDATE un_plat_member_tenant SET status='ACTIVE',version=version+1 "
                            + "WHERE system_id=? AND tenant_id=? AND member_id=? "
                            + "AND status='DISABLED'",
                    Long.parseLong(systemId),
                    Long.parseLong(tenantId),
                    Long.parseLong(secondMemberId))).isOne();
        }

        var repaired = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/draft:check",
                "{}",
                Map.of("Idempotency-Key", key()));
        assertOk(repaired);
        assertThat(text(repaired.body(), "/data/verdict")).isEqualTo("READY");
        var published = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + ":publish",
                "{}",
                Map.of());
        assertOk(published);
        assertThat(published.body().at("/data/version").asInt()).isOne();
    }

    private void exerciseConditionalGateway(
            TestClient manager,
            String flowRoot,
            String systemId,
            String tenantId,
            String managerMemberId,
            String secondMemberId
    ) throws Exception {
        var branches = List.of(
                Map.of(
                        "code", "urgent",
                        "name", "Urgent",
                        "defaultBranch", false,
                        "conditions", List.of(Map.of(
                                "fieldCode", "urgent",
                                "operator", "EQ",
                                "value", true
                        )),
                        "approverIds", List.of(secondMemberId)
                ),
                Map.of(
                        "code", "large",
                        "name", "Large amount",
                        "defaultBranch", false,
                        "conditions", List.of(Map.of(
                                "fieldCode", "amount",
                                "operator", "GTE",
                                "value", 100
                        )),
                        "approverIds", List.of(managerMemberId, secondMemberId)
                ),
                Map.of(
                        "code", "default",
                        "name", "Default",
                        "defaultBranch", true,
                        "conditions", List.of(),
                        "approverIds", List.of(managerMemberId)
                )
        );
        var created = manager.postWithCsrf(
                flowRoot + "/definitions",
                json(Map.of(
                        "name", "Conditional gateway HTTP journey",
                        "approverIds", List.of(managerMemberId),
                        "gateway", Map.of("branches", branches)
                )),
                Map.of());
        assertCreated(created);
        var definitionId = text(created.body(), "/data/definitionId");
        assertThat(created.body().at("/data/gateway/branches"))
                .extracting(branch -> branch.path("code").asText())
                .containsExactly("urgent", "large", "default");

        var checked = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/draft:check",
                "{}",
                Map.of("Idempotency-Key", key()));
        assertOk(checked);
        assertThat(text(checked.body(), "/data/verdict")).isEqualTo("READY");

        var firstMatch = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/draft:simulate",
                json(Map.of(
                        "requesterId", managerMemberId,
                        "businessKey", "gateway-first-match",
                        "values", Map.of("urgent", true, "amount", 500)
                )),
                Map.of("Idempotency-Key", key()));
        assertOk(firstMatch);
        assertThat(text(firstMatch.body(), "/data/route/branchCode")).isEqualTo("urgent");
        assertThat(firstMatch.body().at("/data/route/defaultBranch").asBoolean()).isFalse();
        assertThat(firstMatch.body().at("/data/steps"))
                .extracting(step -> step.path("approverId").asText())
                .containsExactly(secondMemberId);

        var secondMatch = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/draft:simulate",
                json(Map.of(
                        "businessKey", "gateway-second-match",
                        "values", Map.of("urgent", false, "amount", 500)
                )),
                Map.of("Idempotency-Key", key()));
        assertOk(secondMatch);
        assertThat(text(secondMatch.body(), "/data/route/branchCode")).isEqualTo("large");
        assertThat(secondMatch.body().at("/data/steps"))
                .extracting(step -> step.path("approverId").asText())
                .containsExactly(managerMemberId, secondMemberId);

        var fallback = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/draft:simulate",
                json(Map.of("businessKey", "gateway-fallback")),
                Map.of("Idempotency-Key", key()));
        assertOk(fallback);
        assertThat(text(fallback.body(), "/data/route/branchCode")).isEqualTo("default");
        assertThat(fallback.body().at("/data/route/defaultBranch").asBoolean()).isTrue();
        assertThat(fallback.body().at("/data/steps"))
                .extracting(step -> step.path("approverId").asText())
                .containsExactly(managerMemberId);

        var published = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + ":publish",
                "{}",
                Map.of());
        assertOk(published);
        assertThat(published.body().at("/data/version").asInt()).isOne();
        assertThat(published.body().at("/data/gateway/branches")).hasSize(3);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT JSON_LENGTH(gateway_branches) FROM un_flow_definition_version "
                        + "WHERE system_id=? AND tenant_id=? AND definition_id=? AND version_no=1",
                Integer.class,
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(definitionId))).isEqualTo(3);

        var selected = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/instances",
                json(Map.of(
                        "businessKey", "gateway-runtime-large-" + key(),
                        "values", Map.of("amount", 500)
                )),
                Map.of("Idempotency-Key", key()));
        assertCreated(selected);
        var selectedInstanceId = text(selected.body(), "/data/instanceId");
        assertThat(selected.body().at("/data/approverIds"))
                .extracting(JsonNode::asText)
                .containsExactly(managerMemberId, secondMemberId);

        var defaultStarted = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/instances",
                json(Map.of("businessKey", "gateway-runtime-default-" + key())),
                Map.of("Idempotency-Key", key()));
        assertCreated(defaultStarted);
        var defaultInstanceId = text(defaultStarted.body(), "/data/instanceId");
        assertThat(defaultStarted.body().at("/data/approverIds"))
                .extracting(JsonNode::asText)
                .containsExactly(managerMemberId);

        var revised = manager.putWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/draft",
                json(Map.of(
                        "name", "Sequential after gateway",
                        "approverIds", List.of(secondMemberId)
                )),
                Map.of());
        assertOk(revised);
        assertThat(revised.body().at("/data/gateway").isMissingNode()).isTrue();
        var versionTwo = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + ":publish",
                "{}",
                Map.of());
        assertOk(versionTwo);
        assertThat(versionTwo.body().at("/data/version").asInt()).isEqualTo(2);

        var selectedReloaded = manager.get(flowRoot + "/instances/" + selectedInstanceId);
        assertOk(selectedReloaded);
        assertThat(selectedReloaded.body().at("/data/definitionVersion").asInt()).isOne();
        assertThat(selectedReloaded.body().at("/data/approverIds"))
                .extracting(JsonNode::asText)
                .containsExactly(managerMemberId, secondMemberId);
        var defaultReloaded = manager.get(flowRoot + "/instances/" + defaultInstanceId);
        assertOk(defaultReloaded);
        assertThat(defaultReloaded.body().at("/data/approverIds"))
                .extracting(JsonNode::asText)
                .containsExactly(managerMemberId);

        var latestStarted = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/instances",
                json(Map.of("businessKey", "gateway-after-revision-" + key())),
                Map.of("Idempotency-Key", key()));
        assertCreated(latestStarted);
        assertThat(latestStarted.body().at("/data/definitionVersion").asInt()).isEqualTo(2);
        assertThat(latestStarted.body().at("/data/approverIds"))
                .extracting(JsonNode::asText)
                .containsExactly(secondMemberId);
    }

    private void exerciseApprovalModes(
            TestClient manager,
            TestClient secondApprover,
            String flowRoot,
            String systemId,
            String tenantId,
            String managerMemberId,
            String secondMemberId
    ) throws Exception {
        var routes = List.of(
                Map.of(
                        "code", "urgent",
                        "name", "Urgent any-one approval",
                        "defaultBranch", false,
                        "conditions", List.of(Map.of(
                                "fieldCode", "urgent",
                                "operator", "EQ",
                                "value", true
                        )),
                        "approverIds", List.of(managerMemberId, secondMemberId),
                        "approvalMode", "ANY"
                ),
                Map.of(
                        "code", "default",
                        "name", "Default all-member approval",
                        "defaultBranch", true,
                        "conditions", List.of(),
                        "approverIds", List.of(managerMemberId, secondMemberId),
                        "approvalMode", "ALL"
                )
        );
        var created = manager.postWithCsrf(
                flowRoot + "/definitions",
                json(Map.of(
                        "name", "Approval modes HTTP journey",
                        "approverIds", List.of(managerMemberId, secondMemberId),
                        "approvalMode", "ALL",
                        "gateway", Map.of("branches", routes)
                )),
                Map.of());
        assertCreated(created);
        var definitionId = text(created.body(), "/data/definitionId");
        assertThat(text(created.body(), "/data/approvalMode")).isEqualTo("ALL");
        assertThat(text(created.body(), "/data/gateway/branches/0/approvalMode"))
                .isEqualTo("ANY");

        var simulated = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/draft:simulate",
                json(Map.of(
                        "businessKey", "approval-mode-simulation-" + key(),
                        "values", Map.of("urgent", true)
                )),
                Map.of("Idempotency-Key", key()));
        assertOk(simulated);
        assertThat(text(simulated.body(), "/data/route/branchCode")).isEqualTo("urgent");
        assertThat(text(simulated.body(), "/data/route/approvalMode")).isEqualTo("ANY");
        assertThat(simulated.body().at("/data/route/activeApproverIds"))
                .extracting(JsonNode::asText)
                .containsExactly(managerMemberId, secondMemberId);
        assertThat(simulated.body().at("/data/steps"))
                .allMatch(step -> step.path("initial").asBoolean());

        var published = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + ":publish",
                "{}",
                Map.of());
        assertOk(published);
        assertThat(text(published.body(), "/data/approvalMode")).isEqualTo("ALL");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT approval_mode FROM un_flow_definition_draft "
                        + "WHERE system_id=? AND tenant_id=? AND definition_id=?",
                String.class,
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(definitionId))).isEqualTo("ALL");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT approval_mode FROM un_flow_definition_version "
                        + "WHERE system_id=? AND tenant_id=? AND definition_id=? AND version_no=1",
                String.class,
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(definitionId))).isEqualTo("ALL");

        var anyStarted = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/instances",
                json(Map.of(
                        "businessKey", "approval-mode-any-" + key(),
                        "values", Map.of("urgent", true)
                )),
                Map.of("Idempotency-Key", key()));
        assertCreated(anyStarted);
        var anyInstanceId = text(anyStarted.body(), "/data/instanceId");
        assertThat(text(anyStarted.body(), "/data/approvalMode")).isEqualTo("ANY");
        assertThat(anyStarted.body().at("/data/activeApproverIds"))
                .extracting(JsonNode::asText)
                .containsExactly(managerMemberId, secondMemberId);

        var managerAnyTasks = manager.get(flowRoot + "/tasks?status=PENDING&page=1&size=100");
        var secondAnyTasks = secondApprover.get(
                flowRoot + "/tasks?status=PENDING&page=1&size=100");
        assertOk(managerAnyTasks);
        assertOk(secondAnyTasks);
        assertThat(itemValues(managerAnyTasks.body().at("/data/items"), "instanceId"))
                .contains(anyInstanceId);
        assertThat(itemValues(secondAnyTasks.body().at("/data/items"), "instanceId"))
                .contains(anyInstanceId);

        var anyRejectedBySecond = secondApprover.postWithCsrf(
                flowRoot + "/instances/" + anyInstanceId + ":reject",
                json(Map.of("reason", "Any-one member declines")),
                Map.of());
        assertOk(anyRejectedBySecond);
        assertThat(text(anyRejectedBySecond.body(), "/data/status")).isEqualTo("PENDING");
        assertThat(anyRejectedBySecond.body().at("/data/rejectedApproverIds"))
                .extracting(JsonNode::asText)
                .containsExactly(secondMemberId);
        assertThat(anyRejectedBySecond.body().at("/data/activeApproverIds"))
                .extracting(JsonNode::asText)
                .containsExactly(managerMemberId);

        var secondAfterReject = secondApprover.get(
                flowRoot + "/tasks?status=PENDING&page=1&size=100");
        var managerAfterReject = manager.get(
                flowRoot + "/tasks?status=PENDING&page=1&size=100");
        assertOk(secondAfterReject);
        assertOk(managerAfterReject);
        assertThat(itemValues(secondAfterReject.body().at("/data/items"), "instanceId"))
                .doesNotContain(anyInstanceId);
        assertThat(itemValues(managerAfterReject.body().at("/data/items"), "instanceId"))
                .contains(anyInstanceId);

        var anyApproved = manager.postWithCsrf(
                flowRoot + "/instances/" + anyInstanceId + ":approve",
                json(Map.of("comment", "Any-one member approves")),
                Map.of());
        assertOk(anyApproved);
        assertThat(text(anyApproved.body(), "/data/status")).isEqualTo("APPROVED");
        assertThat(anyApproved.body().at("/data/approvedApproverIds"))
                .extracting(JsonNode::asText)
                .containsExactly(managerMemberId);

        var allStarted = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/instances",
                json(Map.of("businessKey", "approval-mode-all-" + key())),
                Map.of("Idempotency-Key", key()));
        assertCreated(allStarted);
        var allInstanceId = text(allStarted.body(), "/data/instanceId");
        assertThat(text(allStarted.body(), "/data/approvalMode")).isEqualTo("ALL");

        var forbiddenTransfer = manager.postWithCsrf(
                flowRoot + "/instances/" + allInstanceId + ":transfer",
                json(Map.of(
                        "targetMemberId", secondMemberId,
                        "reason", "Concurrent routes cannot be transferred"
                )),
                Map.of("Idempotency-Key", key()));
        assertError(forbiddenTransfer, 422, "FLOW_ASSIGNMENT_REQUEST_INVALID");

        var firstAllApproval = manager.postWithCsrf(
                flowRoot + "/instances/" + allInstanceId + ":approve",
                json(Map.of("comment", "First all-member approval")),
                Map.of());
        assertOk(firstAllApproval);
        assertThat(text(firstAllApproval.body(), "/data/status")).isEqualTo("PENDING");
        assertThat(firstAllApproval.body().at("/data/approvedApproverIds"))
                .extracting(JsonNode::asText)
                .containsExactly(managerMemberId);
        assertThat(firstAllApproval.body().at("/data/activeApproverIds"))
                .extracting(JsonNode::asText)
                .containsExactly(secondMemberId);

        var allApproved = secondApprover.postWithCsrf(
                flowRoot + "/instances/" + allInstanceId + ":approve",
                json(Map.of("comment", "Second all-member approval")),
                Map.of());
        assertOk(allApproved);
        assertThat(text(allApproved.body(), "/data/status")).isEqualTo("APPROVED");
        assertThat(allApproved.body().at("/data/approvedApproverIds"))
                .extracting(JsonNode::asText)
                .containsExactly(managerMemberId, secondMemberId);

        var allRejectedStarted = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/instances",
                json(Map.of("businessKey", "approval-mode-all-rejected-" + key())),
                Map.of("Idempotency-Key", key()));
        assertCreated(allRejectedStarted);
        var allRejectedInstanceId = text(allRejectedStarted.body(), "/data/instanceId");
        var allRejected = secondApprover.postWithCsrf(
                flowRoot + "/instances/" + allRejectedInstanceId + ":reject",
                json(Map.of("reason", "One rejection terminates all-member approval")),
                Map.of());
        assertOk(allRejected);
        assertThat(text(allRejected.body(), "/data/status")).isEqualTo("REJECTED");
        assertThat(allRejected.body().at("/data/rejectedApproverIds"))
                .extracting(JsonNode::asText)
                .containsExactly(secondMemberId);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT approval_mode FROM un_flow_instance "
                        + "WHERE system_id=? AND tenant_id=? AND instance_id=?",
                String.class,
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(anyInstanceId))).isEqualTo("ANY");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT JSON_LENGTH(approved_approver_ids_json) "
                        + "+ JSON_LENGTH(rejected_approver_ids_json) "
                        + "FROM un_flow_instance "
                        + "WHERE system_id=? AND tenant_id=? AND instance_id=?",
                Integer.class,
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(anyInstanceId))).isEqualTo(2);
    }

    private void exerciseApprovalDeadlines(
            TestClient manager,
            String flowRoot,
            String systemId,
            String tenantId,
            String managerMemberId
    ) throws Exception {
        var approvePolicy = Map.of(
                "timeoutMinutes", 60,
                "remindBeforeMinutes", 30,
                "timeoutAction", "AUTO_APPROVE"
        );
        var approveCreated = manager.postWithCsrf(
                flowRoot + "/definitions",
                json(Map.of(
                        "name", "Deadline auto approval",
                        "approverIds", List.of(managerMemberId),
                        "approvalMode", "SEQUENTIAL",
                        "deadlinePolicy", approvePolicy
                )),
                Map.of());
        assertCreated(approveCreated);
        var approveDefinitionId = text(
                approveCreated.body(), "/data/definitionId");
        assertThat(approveCreated.body()
                .at("/data/deadlinePolicy/timeoutMinutes").asInt())
                .isEqualTo(60);
        assertThat(text(
                approveCreated.body(), "/data/deadlinePolicy/timeoutAction"))
                .isEqualTo("AUTO_APPROVE");

        var checked = manager.postWithCsrf(
                flowRoot + "/definitions/" + approveDefinitionId + "/draft:check",
                "{}",
                Map.of("Idempotency-Key", key()));
        assertOk(checked);
        assertThat(text(checked.body(), "/data/verdict")).isEqualTo("READY");
        var simulated = manager.postWithCsrf(
                flowRoot + "/definitions/" + approveDefinitionId + "/draft:simulate",
                json(Map.of("businessKey", "deadline-simulation-" + key())),
                Map.of("Idempotency-Key", key()));
        assertOk(simulated);
        assertThat(simulated.body()
                .at("/data/route/deadlinePolicy/timeoutMinutes").asInt())
                .isEqualTo(60);
        assertThat(text(
                simulated.body(), "/data/route/deadlinePolicy/timeoutAction"))
                .isEqualTo("AUTO_APPROVE");

        var approvePublished = manager.postWithCsrf(
                flowRoot + "/definitions/" + approveDefinitionId + ":publish",
                "{}",
                Map.of());
        assertOk(approvePublished);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(deadline_policies,"
                        + " '$.route.timeoutAction')) "
                        + "FROM un_flow_definition_version "
                        + "WHERE system_id=? AND tenant_id=? AND definition_id=? "
                        + "AND version_no=1",
                String.class,
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(approveDefinitionId)))
                .isEqualTo("AUTO_APPROVE");

        var approveStarted = manager.postWithCsrf(
                flowRoot + "/definitions/" + approveDefinitionId + "/instances",
                json(Map.of("businessKey", "deadline-approve-" + key())),
                Map.of("Idempotency-Key", key()));
        assertCreated(approveStarted);
        var approveInstanceId = text(
                approveStarted.body(), "/data/instanceId");
        var startedAt = Instant.parse(text(
                approveStarted.body(), "/data/startedAt"));
        var originalRemindAt = Instant.parse(text(
                approveStarted.body(), "/data/deadline/remindAt"));
        var originalDueAt = Instant.parse(text(
                approveStarted.body(), "/data/deadline/dueAt"));
        assertThat(Duration.between(startedAt, originalRemindAt))
                .isEqualTo(Duration.ofMinutes(30));
        assertThat(Duration.between(startedAt, originalDueAt))
                .isEqualTo(Duration.ofMinutes(60));

        var revised = manager.putWithCsrf(
                flowRoot + "/definitions/" + approveDefinitionId + "/draft",
                json(Map.of(
                        "name", "Deadline policy changed later",
                        "approverIds", List.of(managerMemberId),
                        "approvalMode", "SEQUENTIAL",
                        "deadlinePolicy", Map.of(
                                "timeoutMinutes", 120,
                                "remindBeforeMinutes", 20,
                                "timeoutAction", "AUTO_REJECT"
                        )
                )),
                Map.of());
        assertOk(revised);
        assertOk(manager.postWithCsrf(
                flowRoot + "/definitions/" + approveDefinitionId + ":publish",
                "{}",
                Map.of()));
        var immutable = manager.get(
                flowRoot + "/instances/" + approveInstanceId);
        assertOk(immutable);
        assertThat(Instant.parse(text(immutable.body(), "/data/deadline/remindAt")))
                .isCloseTo(originalRemindAt,
                        within(1, java.time.temporal.ChronoUnit.MICROS));
        assertThat(Instant.parse(text(immutable.body(), "/data/deadline/dueAt")))
                .isCloseTo(originalDueAt,
                        within(1, java.time.temporal.ChronoUnit.MICROS));
        assertThat(text(
                immutable.body(), "/data/deadline/policy/timeoutAction"))
                .isEqualTo("AUTO_APPROVE");

        var reminderNow = Instant.now();
        assertThat(jdbcTemplate.update(
                "UPDATE un_flow_instance "
                        + "SET deadline_remind_at=?,deadline_due_at=?,"
                        + " deadline_reminded_at=NULL,deadline_processed_at=NULL "
                        + "WHERE system_id=? AND tenant_id=? AND instance_id=?",
                java.sql.Timestamp.from(reminderNow.minusSeconds(1)),
                java.sql.Timestamp.from(reminderNow.plusSeconds(60)),
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(approveInstanceId))).isEqualTo(1);
        assertThat(approvalDeadlineWorker.pollOnce(20)).isEqualTo(1);
        assertThat(approvalDeadlineWorker.pollOnce(20)).isZero();
        var messages = manager.get(
                "/api/v1/systems/" + systemId
                        + "/event/messages?status=UNREAD&page=1&size=100");
        assertOk(messages);
        assertThat(StreamSupport.stream(
                        messages.body().at("/data/items").spliterator(), false)
                .filter(item -> "FLOW_APPROVAL_DEADLINE_REMINDER"
                        .equals(item.path("templateCode").asText()))
                .filter(item -> approveInstanceId.equals(
                        item.at("/target/id").asText())))
                .hasSize(1);

        var timeoutNow = Instant.now();
        assertThat(jdbcTemplate.update(
                "UPDATE un_flow_instance "
                        + "SET started_at=?,deadline_due_at=?,"
                        + " deadline_processed_at=NULL "
                        + "WHERE system_id=? AND tenant_id=? AND instance_id=?",
                java.sql.Timestamp.from(timeoutNow.minusSeconds(120)),
                java.sql.Timestamp.from(timeoutNow.minusSeconds(1)),
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(approveInstanceId))).isEqualTo(1);
        assertThat(approvalDeadlineWorker.pollOnce(20)).isEqualTo(1);
        var approved = manager.get(
                flowRoot + "/instances/" + approveInstanceId);
        assertOk(approved);
        assertThat(text(approved.body(), "/data/status")).isEqualTo("APPROVED");
        assertThat(text(
                approved.body(), "/data/deadline/policy/timeoutAction"))
                .isEqualTo("AUTO_APPROVE");
        assertThat(text(approved.body(), "/data/deadline/processedAt"))
                .isNotBlank();
        var approvedHistory = manager.get(
                flowRoot + "/instances/" + approveInstanceId + "/history");
        assertOk(approvedHistory);
        assertThat(itemValues(
                approvedHistory.body().at("/data/events"), "type"))
                .contains("DEADLINE_REMINDER_SENT", "DEADLINE_AUTO_APPROVED");

        var rejectCreated = manager.postWithCsrf(
                flowRoot + "/definitions",
                json(Map.of(
                        "name", "Deadline auto rejection",
                        "approverIds", List.of(managerMemberId),
                        "approvalMode", "SEQUENTIAL",
                        "deadlinePolicy", Map.of(
                                "timeoutMinutes", 1,
                                "timeoutAction", "AUTO_REJECT"
                        )
                )),
                Map.of());
        assertCreated(rejectCreated);
        var rejectDefinitionId = text(
                rejectCreated.body(), "/data/definitionId");
        assertOk(manager.postWithCsrf(
                flowRoot + "/definitions/" + rejectDefinitionId + ":publish",
                "{}",
                Map.of()));
        var rejectStarted = manager.postWithCsrf(
                flowRoot + "/definitions/" + rejectDefinitionId + "/instances",
                json(Map.of("businessKey", "deadline-reject-" + key())),
                Map.of("Idempotency-Key", key()));
        assertCreated(rejectStarted);
        var rejectInstanceId = text(
                rejectStarted.body(), "/data/instanceId");
        ageDeadlineToPast(systemId, tenantId, rejectInstanceId);
        assertThat(approvalDeadlineWorker.pollOnce(20)).isEqualTo(1);
        var rejected = manager.get(
                flowRoot + "/instances/" + rejectInstanceId);
        assertOk(rejected);
        assertThat(text(rejected.body(), "/data/status")).isEqualTo("REJECTED");

        var noneCreated = manager.postWithCsrf(
                flowRoot + "/definitions",
                json(Map.of(
                        "name", "Deadline overdue only",
                        "approverIds", List.of(managerMemberId),
                        "approvalMode", "SEQUENTIAL",
                        "deadlinePolicy", Map.of(
                                "timeoutMinutes", 1,
                                "timeoutAction", "NONE"
                        )
                )),
                Map.of());
        assertCreated(noneCreated);
        var noneDefinitionId = text(
                noneCreated.body(), "/data/definitionId");
        assertOk(manager.postWithCsrf(
                flowRoot + "/definitions/" + noneDefinitionId + ":publish",
                "{}",
                Map.of()));
        var noneStarted = manager.postWithCsrf(
                flowRoot + "/definitions/" + noneDefinitionId + "/instances",
                json(Map.of("businessKey", "deadline-none-" + key())),
                Map.of("Idempotency-Key", key()));
        assertCreated(noneStarted);
        var noneInstanceId = text(noneStarted.body(), "/data/instanceId");
        ageDeadlineToPast(systemId, tenantId, noneInstanceId);
        assertThat(approvalDeadlineWorker.pollOnce(20)).isEqualTo(1);
        var overdue = manager.get(flowRoot + "/instances/" + noneInstanceId);
        assertOk(overdue);
        assertThat(text(overdue.body(), "/data/status")).isEqualTo("PENDING");
        assertThat(overdue.body().at("/data/deadline/overdue").asBoolean())
                .isTrue();
        var humanApproved = manager.postWithCsrf(
                flowRoot + "/instances/" + noneInstanceId + ":approve",
                json(Map.of("comment", "Human approval after overdue marker")),
                Map.of());
        assertOk(humanApproved);
        assertThat(text(humanApproved.body(), "/data/status"))
                .isEqualTo("APPROVED");
    }

    private void ageDeadlineToPast(
            String systemId,
            String tenantId,
            String instanceId
    ) {
        var timeoutNow = Instant.now();
        assertThat(jdbcTemplate.update(
                "UPDATE un_flow_instance "
                        + "SET started_at=?,deadline_due_at=?,"
                        + " deadline_processed_at=NULL "
                        + "WHERE system_id=? AND tenant_id=? AND instance_id=?",
                java.sql.Timestamp.from(timeoutNow.minusSeconds(120)),
                java.sql.Timestamp.from(timeoutNow.minusSeconds(1)),
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(instanceId))).isEqualTo(1);
    }

    private void exerciseDecisionCommentRules(
            TestClient manager,
            String flowRoot,
            String systemId,
            String tenantId,
            String managerMemberId
    ) throws Exception {
        var requiredPolicy = Map.of(
                "approveRequired", true,
                "rejectRequired", true,
                "minimumLength", 4
        );
        var created = manager.postWithCsrf(
                flowRoot + "/definitions",
                json(Map.of(
                        "name", "Decision opinion required",
                        "approverIds", List.of(managerMemberId),
                        "approvalMode", "SEQUENTIAL",
                        "decisionCommentPolicy", requiredPolicy
                )),
                Map.of());
        assertCreated(created);
        var definitionId = text(created.body(), "/data/definitionId");
        assertThat(created.body()
                .at("/data/decisionCommentPolicy/minimumLength").asInt())
                .isEqualTo(4);
        assertThat(created.body()
                .at("/data/decisionCommentPolicy/approveRequired").asBoolean())
                .isTrue();

        var checked = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/draft:check",
                "{}",
                Map.of("Idempotency-Key", key()));
        assertOk(checked);
        assertThat(text(checked.body(), "/data/verdict")).isEqualTo("READY");
        var simulated = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/draft:simulate",
                json(Map.of("businessKey", "comment-simulation-" + key())),
                Map.of("Idempotency-Key", key()));
        assertOk(simulated);
        assertThat(simulated.body().at(
                        "/data/route/decisionCommentPolicy/minimumLength").asInt())
                .isEqualTo(4);

        assertOk(manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + ":publish",
                "{}",
                Map.of()));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT JSON_EXTRACT(decision_comment_policies,"
                        + " '$.route.minimumLength') "
                        + "FROM un_flow_definition_version "
                        + "WHERE system_id=? AND tenant_id=? AND definition_id=? "
                        + "AND version_no=1",
                Integer.class,
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(definitionId))).isEqualTo(4);

        var started = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/instances",
                json(Map.of("businessKey", "comment-required-" + key())),
                Map.of("Idempotency-Key", key()));
        assertCreated(started);
        var instanceId = text(started.body(), "/data/instanceId");
        assertThat(started.body()
                .at("/data/decisionCommentPolicy/minimumLength").asInt())
                .isEqualTo(4);

        var tooShort = manager.postWithCsrf(
                flowRoot + "/instances/" + instanceId + ":approve",
                json(Map.of("comment", "短")),
                Map.of());
        assertError(tooShort, 400, "FLOW_APPROVAL_COMMENT_REQUIRED");
        var unchanged = manager.get(flowRoot + "/instances/" + instanceId);
        assertOk(unchanged);
        assertThat(text(unchanged.body(), "/data/status")).isEqualTo("PENDING");
        var unchangedHistory = manager.get(
                flowRoot + "/instances/" + instanceId + "/history");
        assertOk(unchangedHistory);
        assertThat(itemValues(
                unchangedHistory.body().at("/data/events"), "type"))
                .containsExactly("STARTED");

        var revised = manager.putWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/draft",
                json(Map.of(
                        "name", "Decision opinion optional later",
                        "approverIds", List.of(managerMemberId),
                        "approvalMode", "SEQUENTIAL",
                        "decisionCommentPolicy", Map.of(
                                "approveRequired", false,
                                "rejectRequired", true,
                                "minimumLength", 1
                        )
                )),
                Map.of());
        assertOk(revised);
        assertOk(manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + ":publish",
                "{}",
                Map.of()));

        var stillTooShort = manager.postWithCsrf(
                flowRoot + "/instances/" + instanceId + ":approve",
                json(Map.of("comment", "短")),
                Map.of());
        assertError(stillTooShort, 400, "FLOW_APPROVAL_COMMENT_REQUIRED");
        var approved = manager.postWithCsrf(
                flowRoot + "/instances/" + instanceId + ":approve",
                json(Map.of("comment", "同意通过")),
                Map.of());
        assertOk(approved);
        assertThat(text(approved.body(), "/data/status")).isEqualTo("APPROVED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT JSON_EXTRACT(decision_comment_policy,'$.minimumLength') "
                        + "FROM un_flow_instance "
                        + "WHERE system_id=? AND tenant_id=? AND instance_id=?",
                Integer.class,
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(instanceId))).isEqualTo(4);
    }

    private void exerciseDelegationProxy(
            TestClient manager,
            TestClient secondApprover,
            TestClient delegatedApprover,
            String flowRoot,
            String systemId,
            String tenantId,
            String managerMemberId,
            String secondMemberId,
            String delegatedMemberId
    ) throws Exception {
        var created = manager.postWithCsrf(
                flowRoot + "/definitions",
                json(Map.of(
                        "name", "Direct approval delegation",
                        "approverIds", List.of(managerMemberId, secondMemberId),
                        "approvalMode", "ALL"
                )),
                Map.of());
        assertCreated(created);
        var definitionId = text(created.body(), "/data/definitionId");
        assertOk(manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + ":publish",
                "{}",
                Map.of()));

        var activeNow = Instant.now();
        var activeRuleResponse = secondApprover.postWithCsrf(
                flowRoot + "/delegations",
                json(Map.of(
                        "delegatorMemberId", secondMemberId,
                        "delegateMemberId", delegatedMemberId,
                        "startsAt", activeNow.minusSeconds(30).toString(),
                        "endsAt", activeNow.plusSeconds(3600).toString(),
                        "definitionId", definitionId
                )),
                Map.of());
        assertCreated(activeRuleResponse);
        var activeRuleId = text(
                activeRuleResponse.body(),
                "/data/delegationRuleId"
        );
        assertThat(text(activeRuleResponse.body(), "/data/status"))
                .isEqualTo("ACTIVE");

        var outgoing = secondApprover.get(
                flowRoot + "/delegations?delegatorMemberId="
                        + secondMemberId + "&page=1&size=20"
        );
        assertOk(outgoing);
        assertThat(outgoing.body().at("/data/total").asLong()).isOne();
        assertThat(text(outgoing.body(), "/data/items/0/delegationRuleId"))
                .isEqualTo(activeRuleId);

        var started = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/instances",
                json(Map.of("businessKey", "delegated-approved-" + key())),
                Map.of("Idempotency-Key", key()));
        assertCreated(started);
        var instanceId = text(started.body(), "/data/instanceId");
        assertThat(started.body().at("/data/approverIds"))
                .extracting(JsonNode::asText)
                .containsExactly(managerMemberId, secondMemberId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT JSON_LENGTH(approver_ids_json) "
                        + "FROM un_flow_instance "
                        + "WHERE system_id=? AND tenant_id=? AND instance_id=?",
                Integer.class,
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(instanceId))).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT JSON_CONTAINS(approver_ids_json,CAST(? AS JSON),'$') "
                        + "FROM un_flow_instance "
                        + "WHERE system_id=? AND tenant_id=? AND instance_id=?",
                Integer.class,
                secondMemberId,
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(instanceId))).isOne();

        var delegatedTasks = delegatedApprover.get(
                flowRoot + "/tasks?status=PENDING&page=1&size=100"
        );
        assertOk(delegatedTasks);
        var delegatedTask = item(
                delegatedTasks.body().at("/data/items"),
                "instanceId",
                instanceId
        );
        assertThat(delegatedTask.at("/representedAuthorities"))
                .anySatisfy(authority -> {
                    assertThat(authority.path("representedMemberId").asText())
                            .isEqualTo(secondMemberId);
                    assertThat(authority.path("delegationRuleId").asText())
                            .isEqualTo(activeRuleId);
                });

        var delegatedDecision = delegatedApprover.postWithCsrf(
                flowRoot + "/instances/" + instanceId + ":approve",
                json(Map.of(
                        "comment", "Approved on behalf of the second approver",
                        "representedMemberId", secondMemberId
                )),
                Map.of());
        assertOk(delegatedDecision);
        assertThat(text(delegatedDecision.body(), "/data/status"))
                .isEqualTo("PENDING");

        var delegatedHistory = delegatedApprover.get(
                flowRoot + "/instances/" + instanceId + "/history"
        );
        assertOk(delegatedHistory);
        assertThat(delegatedHistory.body().at("/data/events"))
                .anySatisfy(event -> {
                    assertThat(event.path("type").asText()).isEqualTo("APPROVED");
                    assertThat(event.path("actorId").asText())
                            .isEqualTo(delegatedMemberId);
                    assertThat(event.path("representedMemberId").asText())
                            .isEqualTo(secondMemberId);
                    assertThat(event.path("delegationRuleId").asText())
                            .isEqualTo(activeRuleId);
                });
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_flow_history_event "
                        + "WHERE system_id=? AND tenant_id=? AND instance_id=? "
                        + "AND event_type='APPROVED' AND actor_id=? "
                        + "AND represented_member_id=? AND delegation_id=?",
                Integer.class,
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(instanceId),
                Long.parseLong(delegatedMemberId),
                Long.parseLong(secondMemberId),
                Long.parseLong(activeRuleId))).isOne();

        var completed = manager.postWithCsrf(
                flowRoot + "/instances/" + instanceId + ":approve",
                json(Map.of("comment", "Manager direct approval")),
                Map.of());
        assertOk(completed);
        assertThat(text(completed.body(), "/data/status"))
                .isEqualTo("APPROVED");

        var revoked = secondApprover.postWithCsrf(
                flowRoot + "/delegations/" + activeRuleId + "/revoke",
                "{}",
                Map.of());
        assertOk(revoked);
        assertThat(text(revoked.body(), "/data/status")).isEqualTo("REVOKED");
        assertThat(text(revoked.body(), "/data/revokedByMemberId"))
                .isEqualTo(secondMemberId);

        var guardedStarted = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/instances",
                json(Map.of("businessKey", "delegation-guarded-" + key())),
                Map.of("Idempotency-Key", key()));
        assertCreated(guardedStarted);
        var guardedInstanceId = text(
                guardedStarted.body(),
                "/data/instanceId"
        );
        assertDelegatedDecisionInactive(
                delegatedApprover,
                flowRoot,
                guardedInstanceId,
                secondMemberId
        );

        var scheduledNow = Instant.now();
        var scheduledRuleResponse = secondApprover.postWithCsrf(
                flowRoot + "/delegations",
                json(Map.of(
                        "delegatorMemberId", secondMemberId,
                        "delegateMemberId", delegatedMemberId,
                        "startsAt", scheduledNow.plusSeconds(600).toString(),
                        "endsAt", scheduledNow.plusSeconds(1200).toString(),
                        "definitionId", definitionId
                )),
                Map.of());
        assertCreated(scheduledRuleResponse);
        var scheduledRuleId = text(
                scheduledRuleResponse.body(),
                "/data/delegationRuleId"
        );
        assertThat(text(scheduledRuleResponse.body(), "/data/status"))
                .isEqualTo("SCHEDULED");
        assertDelegatedDecisionInactive(
                delegatedApprover,
                flowRoot,
                guardedInstanceId,
                secondMemberId
        );

        var expiredAt = Instant.now();
        assertThat(jdbcTemplate.update(
                "UPDATE un_flow_approval_delegation "
                        + "SET starts_at=?,ends_at=?,status='EXPIRED' "
                        + "WHERE system_id=? AND tenant_id=? AND delegation_id=?",
                java.sql.Timestamp.from(expiredAt.minusSeconds(120)),
                java.sql.Timestamp.from(expiredAt.minusSeconds(60)),
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(scheduledRuleId))).isEqualTo(1);
        assertDelegatedDecisionInactive(
                delegatedApprover,
                flowRoot,
                guardedInstanceId,
                secondMemberId
        );

        var guardedHistory = manager.get(
                flowRoot + "/instances/" + guardedInstanceId + "/history"
        );
        assertOk(guardedHistory);
        assertThat(itemValues(
                guardedHistory.body().at("/data/events"),
                "type"
        )).containsExactly("STARTED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM un_flow_instance "
                        + "WHERE system_id=? AND tenant_id=? AND instance_id=?",
                String.class,
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(guardedInstanceId))).isEqualTo("PENDING");

        var secondDirect = secondApprover.postWithCsrf(
                flowRoot + "/instances/" + guardedInstanceId + ":approve",
                json(Map.of("comment", "Second approver direct approval")),
                Map.of());
        assertOk(secondDirect);
        assertThat(text(secondDirect.body(), "/data/status")).isEqualTo("PENDING");
        var managerDirect = manager.postWithCsrf(
                flowRoot + "/instances/" + guardedInstanceId + ":approve",
                json(Map.of("comment", "Manager completes guarded approval")),
                Map.of());
        assertOk(managerDirect);
        assertThat(text(managerDirect.body(), "/data/status")).isEqualTo("APPROVED");
    }

    private void assertDelegatedDecisionInactive(
            TestClient delegatedApprover,
            String flowRoot,
            String instanceId,
            String representedMemberId
    ) throws Exception {
        var response = delegatedApprover.postWithCsrf(
                flowRoot + "/instances/" + instanceId + ":approve",
                json(Map.of(
                        "comment", "This represented decision must be rejected",
                        "representedMemberId", representedMemberId
                )),
                Map.of());
        assertError(response, 403, "FLOW_DELEGATION_INACTIVE");
    }

    private void exerciseOrderedStagesWithPreviousHandler(
            TestClient manager,
            TestClient secondApprover,
            TestClient proxyApprover,
            String flowRoot,
            String systemId,
            String tenantId,
            String managerMemberId,
            String secondMemberId,
            String proxyMemberId
    ) throws Exception {
        var stages = List.of(
                Map.of(
                        "code", "manager_review",
                        "name", "Manager review",
                        "approverIds", List.of(managerMemberId),
                        "approvalMode", "SEQUENTIAL",
                        "approverSource", Map.of("kind", "FIXED")
                ),
                Map.of(
                        "code", "specialist_review",
                        "name", "Specialist review",
                        "approverIds", List.of(secondMemberId),
                        "approvalMode", "SEQUENTIAL",
                        "approverSource", Map.of("kind", "FIXED")
                ),
                Map.of(
                        "code", "handler_confirmation",
                        "name", "Previous handler confirmation",
                        "approverIds", List.of(),
                        "approvalMode", "SEQUENTIAL",
                        "approverSource", Map.of("kind", "PREVIOUS_HANDLER")
                )
        );
        var created = manager.postWithCsrf(
                flowRoot + "/definitions",
                json(Map.of(
                        "name", "Ordered approval with actual previous handler",
                        "approvalStages", stages
                )),
                Map.of());
        assertCreated(created);
        var definitionId = text(created.body(), "/data/definitionId");
        assertThat(created.body().at("/data/approvalStages")).hasSize(3);
        assertThat(created.body().at("/data/approverIds"))
                .extracting(JsonNode::asText)
                .containsExactly(managerMemberId);
        assertThat(text(created.body(), "/data/approvalStages/2/approverSource/kind"))
                .isEqualTo("PREVIOUS_HANDLER");

        var invalidPreviousFirst = manager.postWithCsrf(
                flowRoot + "/definitions",
                json(Map.of(
                        "name", "Previous handler cannot be first",
                        "approvalStages", List.of(
                                Map.of(
                                        "code", "invalid_previous",
                                        "name", "Invalid previous",
                                        "approverIds", List.of(),
                                        "approvalMode", "SEQUENTIAL",
                                        "approverSource",
                                        Map.of("kind", "PREVIOUS_HANDLER")
                                ),
                                stages.get(1)
                        )
                )),
                Map.of());
        assertError(invalidPreviousFirst, 400, "FLOW_APPROVER_SEQUENCE_INVALID");

        var stagesWithGateway = manager.postWithCsrf(
                flowRoot + "/definitions",
                json(Map.of(
                        "name", "Stages cannot carry a gateway",
                        "approvalStages", stages,
                        "gateway", Map.of(
                                "branches", List.of(
                                        Map.of(
                                                "code", "default_route",
                                                "name", "Default route",
                                                "defaultBranch", true,
                                                "conditions", List.of(),
                                                "approverIds", List.of(managerMemberId)
                                        )
                                )
                        )
                )),
                Map.of());
        assertError(
                stagesWithGateway,
                422,
                "FLOW_APPROVAL_STAGES_GATEWAY_UNSUPPORTED");

        var checked = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/draft:check",
                "{}",
                Map.of("Idempotency-Key", key()));
        assertOk(checked);
        assertThat(text(checked.body(), "/data/verdict")).isEqualTo("READY");
        var simulated = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/draft:simulate",
                json(Map.of("businessKey", "ordered-stages-preview-" + key())),
                Map.of("Idempotency-Key", key()));
        assertOk(simulated);
        assertThat(simulated.body().at("/data/approvalStages")).hasSize(3);

        var published = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + ":publish",
                "{}",
                Map.of());
        assertOk(published);
        assertThat(published.body().at("/data/approvalStages")).hasSize(3);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT JSON_LENGTH(approval_stages) "
                        + "FROM un_flow_definition_version "
                        + "WHERE system_id=? AND tenant_id=? "
                        + "AND definition_id=? AND version_no=1",
                Integer.class,
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(definitionId))).isEqualTo(3);

        var activeNow = Instant.now();
        var delegation = secondApprover.postWithCsrf(
                flowRoot + "/delegations",
                json(Map.of(
                        "delegatorMemberId", secondMemberId,
                        "delegateMemberId", proxyMemberId,
                        "startsAt", activeNow.minusSeconds(30).toString(),
                        "endsAt", activeNow.plusSeconds(3600).toString(),
                        "definitionId", definitionId
                )),
                Map.of());
        assertCreated(delegation);
        var delegationRuleId = text(
                delegation.body(), "/data/delegationRuleId");

        var started = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/instances",
                json(Map.of("businessKey", "ordered-stages-" + key())),
                Map.of("Idempotency-Key", key()));
        assertCreated(started);
        var instanceId = text(started.body(), "/data/instanceId");
        assertThat(started.body().at("/data/currentStageIndex").asInt()).isZero();
        assertThat(text(started.body(), "/data/currentStageCode"))
                .isEqualTo("manager_review");
        assertThat(itemValues(started.body().at("/data/stages"), "status"))
                .containsExactly("ACTIVE", "WAITING", "WAITING");
        assertThat(started.body().at("/data/stages/0/actualHandlerIds")).isEmpty();

        var stageOne = manager.postWithCsrf(
                flowRoot + "/instances/" + instanceId + ":approve",
                json(Map.of("comment", "Manager stage completed")),
                Map.of());
        assertOk(stageOne);
        assertThat(text(stageOne.body(), "/data/instanceId")).isEqualTo(instanceId);
        assertThat(text(stageOne.body(), "/data/status")).isEqualTo("PENDING");
        assertThat(stageOne.body().at("/data/currentStageIndex").asInt()).isEqualTo(1);
        assertThat(text(stageOne.body(), "/data/currentStageCode"))
                .isEqualTo("specialist_review");
        assertThat(itemValues(stageOne.body().at("/data/stages"), "status"))
                .containsExactly("APPROVED", "ACTIVE", "WAITING");
        assertThat(stageOne.body().at("/data/stages/0/actualHandlerIds"))
                .extracting(JsonNode::asText)
                .containsExactly(managerMemberId);
        assertThat(stageOne.body().at("/data/stages/1/actualHandlerIds")).isEmpty();

        var proxyTasks = proxyApprover.get(
                flowRoot + "/tasks?status=PENDING&page=1&size=100");
        assertOk(proxyTasks);
        var proxyTask = item(
                proxyTasks.body().at("/data/items"),
                "instanceId",
                instanceId);
        assertThat(proxyTask.at("/representedAuthorities"))
                .anySatisfy(authority -> {
                    assertThat(authority.path("representedMemberId").asText())
                            .isEqualTo(secondMemberId);
                    assertThat(authority.path("delegationRuleId").asText())
                            .isEqualTo(delegationRuleId);
                });

        var stageTwo = proxyApprover.postWithCsrf(
                flowRoot + "/instances/" + instanceId + ":approve",
                json(Map.of(
                        "comment", "Proxy completes specialist stage",
                        "representedMemberId", secondMemberId
                )),
                Map.of());
        assertOk(stageTwo);
        assertThat(text(stageTwo.body(), "/data/instanceId")).isEqualTo(instanceId);
        assertThat(text(stageTwo.body(), "/data/status")).isEqualTo("PENDING");
        assertThat(stageTwo.body().at("/data/currentStageIndex").asInt()).isEqualTo(2);
        assertThat(text(stageTwo.body(), "/data/currentStageCode"))
                .isEqualTo("handler_confirmation");
        assertThat(stageTwo.body().at("/data/approverIds"))
                .extracting(JsonNode::asText)
                .containsExactly(proxyMemberId);
        assertThat(itemValues(stageTwo.body().at("/data/stages"), "status"))
                .containsExactly("APPROVED", "APPROVED", "ACTIVE");
        assertThat(stageTwo.body().at("/data/stages/1/actualHandlerIds"))
                .extracting(JsonNode::asText)
                .containsExactly(proxyMemberId);
        assertThat(stageTwo.body().at("/data/stages/2/actualHandlerIds")).isEmpty();

        var persistedMidway = manager.get(
                flowRoot + "/instances/" + instanceId);
        assertOk(persistedMidway);
        assertThat(text(persistedMidway.body(), "/data/instanceId"))
                .isEqualTo(instanceId);
        assertThat(persistedMidway.body().at("/data/currentStageIndex").asInt())
                .isEqualTo(2);
        assertThat(persistedMidway.body().at("/data/stages/2/approverIds"))
                .extracting(JsonNode::asText)
                .containsExactly(proxyMemberId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT current_stage_index FROM un_flow_instance "
                        + "WHERE system_id=? AND tenant_id=? AND instance_id=?",
                Integer.class,
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(instanceId))).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT JSON_UNQUOTE(JSON_EXTRACT("
                        + "approval_stage_state,'$[1].actualHandlerIds[0]')) "
                        + "FROM un_flow_instance "
                        + "WHERE system_id=? AND tenant_id=? AND instance_id=?",
                String.class,
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(instanceId))).isEqualTo(proxyMemberId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT JSON_LENGTH(JSON_EXTRACT("
                        + "approval_stage_state,'$[2].actualHandlerIds')) "
                        + "FROM un_flow_instance "
                        + "WHERE system_id=? AND tenant_id=? AND instance_id=?",
                Integer.class,
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(instanceId))).isZero();

        var completed = proxyApprover.postWithCsrf(
                flowRoot + "/instances/" + instanceId + ":approve",
                json(Map.of("comment", "Actual handler confirms directly")),
                Map.of());
        assertOk(completed);
        assertThat(text(completed.body(), "/data/instanceId")).isEqualTo(instanceId);
        assertThat(text(completed.body(), "/data/status")).isEqualTo("APPROVED");
        assertThat(itemValues(completed.body().at("/data/stages"), "status"))
                .containsExactly("APPROVED", "APPROVED", "APPROVED");
        assertThat(completed.body().at("/data/stages/2/actualHandlerIds"))
                .extracting(JsonNode::asText)
                .containsExactly(proxyMemberId);

        var history = manager.get(
                flowRoot + "/instances/" + instanceId + "/history");
        assertOk(history);
        assertThat(history.body().at("/data/events"))
                .anySatisfy(event -> {
                    assertThat(event.path("type").asText()).isEqualTo("APPROVED");
                    assertThat(event.path("actorId").asText())
                            .isEqualTo(proxyMemberId);
                    assertThat(event.path("representedMemberId").asText())
                            .isEqualTo(secondMemberId);
                    assertThat(event.path("delegationRuleId").asText())
                            .isEqualTo(delegationRuleId);
                });
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_flow_history_event "
                        + "WHERE system_id=? AND tenant_id=? AND instance_id=? "
                        + "AND event_type='APPROVED' AND actor_id=? "
                        + "AND represented_member_id=? AND delegation_id=?",
                Integer.class,
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(instanceId),
                Long.parseLong(proxyMemberId),
                Long.parseLong(secondMemberId),
                Long.parseLong(delegationRuleId))).isOne();

        var revoked = secondApprover.postWithCsrf(
                flowRoot + "/delegations/" + delegationRuleId + "/revoke",
                "{}",
                Map.of());
        assertOk(revoked);
        assertThat(text(revoked.body(), "/data/status")).isEqualTo("REVOKED");
    }

    private void exerciseQuorumApproval(
            TestClient manager,
            TestClient secondApprover,
            TestClient thirdApprover,
            String flowRoot,
            String systemId,
            String tenantId,
            String managerMemberId,
            String secondMemberId,
            String thirdMemberId,
            String thirdApproverRoleId,
            String thirdApproverRoleVersion,
            String allDataScopeId
    ) throws Exception {
        var thirdRoleDraft = manager.putWithCsrf(
                "/api/v1/systems/" + systemId + "/admin/roles/"
                        + thirdApproverRoleId + "/draft",
                json(Map.of(
                        "name", "Flow Start and Quorum Approver",
                        "description", "Start published flows and decide assigned quorum tasks",
                        "permissionCodes", List.of(
                                "system.runtime.access",
                                "flow.instance.start",
                                "flow.instance.decide",
                                "flow.instance.read"
                        ),
                        "deniedPermissionCodes", List.of(),
                        "dataScopeId", allDataScopeId,
                        "version", thirdApproverRoleVersion
                )),
                Map.of());
        assertOk(thirdRoleDraft);
        var thirdRoleChecked = manager.postWithCsrf(
                "/api/v1/systems/" + systemId + "/admin/roles/"
                        + thirdApproverRoleId + "/draft:check",
                json(Map.of("version", text(thirdRoleDraft.body(), "/data/version"))),
                Map.of("Idempotency-Key", key()));
        assertOk(thirdRoleChecked);
        var thirdRolePublished = manager.postWithCsrf(
                "/api/v1/systems/" + systemId + "/admin/roles/"
                        + thirdApproverRoleId + "/draft:publish",
                json(Map.of("version", text(thirdRoleChecked.body(), "/data/version"))),
                Map.of("Idempotency-Key", key()));
        assertOk(thirdRolePublished);
        assertOk(manager.postWithCsrf("/api/v1/auth/refresh", "{}", Map.of()));
        assertOk(secondApprover.postWithCsrf("/api/v1/auth/refresh", "{}", Map.of()));
        var thirdRefreshed = thirdApprover.postWithCsrf(
                "/api/v1/auth/refresh",
                "{}",
                Map.of());
        assertOk(thirdRefreshed);
        assertThat(thirdRefreshed.body().at("/data/context/permissions"))
                .extracting(JsonNode::asText)
                .contains("flow.instance.start", "flow.instance.decide", "flow.instance.read");

        var approverIds = List.of(managerMemberId, secondMemberId, thirdMemberId);
        var created = manager.postWithCsrf(
                flowRoot + "/definitions",
                json(Map.of(
                        "name", "Two of three quorum approval",
                        "approverIds", approverIds,
                        "approvalMode", "QUORUM",
                        "quorumRule", Map.of("type", "COUNT", "value", 2)
                )),
                Map.of());
        assertCreated(created);
        var definitionId = text(created.body(), "/data/definitionId");
        assertThat(text(created.body(), "/data/approvalMode")).isEqualTo("QUORUM");
        assertThat(text(created.body(), "/data/quorumRule/type")).isEqualTo("COUNT");
        assertThat(created.body().at("/data/quorumRule/value").asInt()).isEqualTo(2);

        var checked = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/draft:check",
                "{}",
                Map.of("Idempotency-Key", key()));
        assertOk(checked);
        assertThat(text(checked.body(), "/data/verdict")).isEqualTo("READY");

        var simulated = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/draft:simulate",
                json(Map.of("businessKey", "quorum-simulation-" + key())),
                Map.of("Idempotency-Key", key()));
        assertOk(simulated);
        assertThat(text(simulated.body(), "/data/route/approvalMode"))
                .isEqualTo("QUORUM");
        assertThat(simulated.body().at("/data/route/requiredApprovals").asInt())
                .isEqualTo(2);
        assertThat(simulated.body().at("/data/route/activeApproverIds"))
                .extracting(JsonNode::asText)
                .containsExactlyElementsOf(approverIds);

        var published = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + ":publish",
                "{}",
                Map.of());
        assertOk(published);
        assertThat(text(published.body(), "/data/quorumRule/type")).isEqualTo("COUNT");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(quorum_rules, '$.route.type')) "
                        + "FROM un_flow_definition_version "
                        + "WHERE system_id=? AND tenant_id=? AND definition_id=? "
                        + "AND version_no=1",
                String.class,
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(definitionId))).isEqualTo("COUNT");

        var approvalStarted = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/instances",
                json(Map.of("businessKey", "quorum-approved-" + key())),
                Map.of("Idempotency-Key", key()));
        var rejectionStarted = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/instances",
                json(Map.of("businessKey", "quorum-rejected-" + key())),
                Map.of("Idempotency-Key", key()));
        assertCreated(approvalStarted);
        assertCreated(rejectionStarted);
        var approvalInstanceId = text(approvalStarted.body(), "/data/instanceId");
        var rejectionInstanceId = text(rejectionStarted.body(), "/data/instanceId");
        assertThat(approvalStarted.body().at("/data/requiredApprovals").asInt()).isEqualTo(2);
        assertThat(rejectionStarted.body().at("/data/requiredApprovals").asInt()).isEqualTo(2);

        var revised = manager.putWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/draft",
                json(Map.of(
                        "name", "Unanimous quorum approval",
                        "approverIds", approverIds,
                        "approvalMode", "QUORUM",
                        "quorumRule", Map.of("type", "PERCENTAGE", "value", 100)
                )),
                Map.of());
        assertOk(revised);
        var republished = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + ":publish",
                "{}",
                Map.of());
        assertOk(republished);
        assertThat(text(republished.body(), "/data/quorumRule/type"))
                .isEqualTo("PERCENTAGE");

        var firstApproval = manager.postWithCsrf(
                flowRoot + "/instances/" + approvalInstanceId + ":approve",
                json(Map.of("comment", "First quorum approval")),
                Map.of());
        assertOk(firstApproval);
        assertThat(text(firstApproval.body(), "/data/status")).isEqualTo("PENDING");
        assertThat(firstApproval.body().at("/data/requiredApprovals").asInt()).isEqualTo(2);

        var thresholdApproval = secondApprover.postWithCsrf(
                flowRoot + "/instances/" + approvalInstanceId + ":approve",
                json(Map.of("comment", "Second quorum approval")),
                Map.of());
        assertOk(thresholdApproval);
        assertThat(text(thresholdApproval.body(), "/data/status")).isEqualTo("APPROVED");
        assertThat(thresholdApproval.body().at("/data/requiredApprovals").asInt())
                .isEqualTo(2);
        assertThat(thresholdApproval.body().at("/data/activeApproverIds")).isEmpty();

        var firstRejection = secondApprover.postWithCsrf(
                flowRoot + "/instances/" + rejectionInstanceId + ":reject",
                json(Map.of("reason", "First quorum rejection")),
                Map.of());
        assertOk(firstRejection);
        assertThat(text(firstRejection.body(), "/data/status")).isEqualTo("PENDING");

        var impossible = thirdApprover.postWithCsrf(
                flowRoot + "/instances/" + rejectionInstanceId + ":reject",
                json(Map.of("reason", "Quorum is now impossible")),
                Map.of());
        assertOk(impossible);
        assertThat(text(impossible.body(), "/data/status")).isEqualTo("REJECTED");
        assertThat(impossible.body().at("/data/requiredApprovals").asInt()).isEqualTo(2);
        assertThat(impossible.body().at("/data/activeApproverIds")).isEmpty();

        var thirdTasks = thirdApprover.get(
                flowRoot + "/tasks?status=PENDING&page=1&size=100");
        assertOk(thirdTasks);
        assertThat(itemValues(thirdTasks.body().at("/data/items"), "instanceId"))
                .doesNotContain(approvalInstanceId, rejectionInstanceId);
        var managerTasks = manager.get(flowRoot + "/tasks?status=PENDING&page=1&size=100");
        assertOk(managerTasks);
        assertThat(itemValues(managerTasks.body().at("/data/items"), "instanceId"))
                .doesNotContain(approvalInstanceId, rejectionInstanceId);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT required_approvals FROM un_flow_instance "
                        + "WHERE system_id=? AND tenant_id=? AND instance_id=?",
                Integer.class,
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(approvalInstanceId))).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT required_approvals FROM un_flow_instance "
                        + "WHERE system_id=? AND tenant_id=? AND instance_id=?",
                Integer.class,
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(rejectionInstanceId))).isEqualTo(2);
    }

    private void exerciseParallelBranches(
            TestClient manager,
            TestClient secondApprover,
            String flowRoot,
            String systemId,
            String tenantId,
            String managerMemberId,
            String secondMemberId
    ) throws Exception {
        var branches = List.of(
                Map.of(
                        "code", "owner_review",
                        "name", "Owner review",
                        "approverIds", List.of(managerMemberId),
                        "approvalMode", "SEQUENTIAL"
                ),
                Map.of(
                        "code", "peer_review",
                        "name", "Peer review",
                        "approverIds", List.of(secondMemberId),
                        "approvalMode", "SEQUENTIAL"
                )
        );
        var created = manager.postWithCsrf(
                flowRoot + "/definitions",
                json(Map.of(
                        "name", "Parallel branches HTTP journey",
                        "approverIds", List.of(managerMemberId),
                        "approvalMode", "SEQUENTIAL",
                        "parallelGateway", Map.of("branches", branches)
                )),
                Map.of());
        assertCreated(created);
        var definitionId = text(created.body(), "/data/definitionId");
        assertThat(created.body().at("/data/parallelGateway/branches").size()).isEqualTo(2);
        assertThat(text(created.body(), "/data/parallelGateway/branches/0/code"))
                .isEqualTo("owner_review");
        assertThat(text(created.body(), "/data/parallelGateway/branches/1/code"))
                .isEqualTo("peer_review");

        var simulated = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/draft:simulate",
                json(Map.of("businessKey", "parallel-simulation-" + key())),
                Map.of("Idempotency-Key", key()));
        assertOk(simulated);
        assertThat(simulated.body().at("/data/startable").asBoolean()).isTrue();
        assertThat(simulated.body().at("/data/parallelRoutes").size()).isEqualTo(2);
        assertThat(text(simulated.body(), "/data/parallelRoutes/0/branchCode"))
                .isEqualTo("owner_review");
        assertThat(simulated.body().at("/data/parallelRoutes/0/activeApproverIds"))
                .extracting(JsonNode::asText)
                .containsExactly(managerMemberId);
        assertThat(simulated.body().at("/data/parallelRoutes/1/activeApproverIds"))
                .extracting(JsonNode::asText)
                .containsExactly(secondMemberId);

        var published = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + ":publish",
                "{}",
                Map.of());
        assertOk(published);
        assertThat(published.body().at("/data/version").asInt()).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT JSON_LENGTH(parallel_branches) FROM un_flow_definition_draft "
                        + "WHERE system_id=? AND tenant_id=? AND definition_id=?",
                Integer.class,
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(definitionId))).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT JSON_LENGTH(parallel_branches) FROM un_flow_definition_version "
                        + "WHERE system_id=? AND tenant_id=? AND definition_id=? AND version_no=1",
                Integer.class,
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(definitionId))).isEqualTo(2);

        var joinedStarted = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/instances",
                json(Map.of("businessKey", "parallel-joined-" + key())),
                Map.of("Idempotency-Key", key()));
        assertCreated(joinedStarted);
        var joinedInstanceId = text(joinedStarted.body(), "/data/instanceId");
        assertThat(text(joinedStarted.body(), "/data/status")).isEqualTo("PENDING");
        assertThat(joinedStarted.body().at("/data/parallelBranches").size()).isEqualTo(2);
        assertThat(joinedStarted.body().at("/data/parallelBranches/0/status").asText())
                .isEqualTo("PENDING");
        assertThat(joinedStarted.body().at("/data/parallelBranches/1/status").asText())
                .isEqualTo("PENDING");

        var managerTasks = manager.get(flowRoot + "/tasks?status=PENDING&page=1&size=100");
        var secondTasks = secondApprover.get(
                flowRoot + "/tasks?status=PENDING&page=1&size=100");
        assertOk(managerTasks);
        assertOk(secondTasks);
        assertThat(itemValues(managerTasks.body().at("/data/items"), "instanceId"))
                .contains(joinedInstanceId);
        assertThat(itemValues(secondTasks.body().at("/data/items"), "instanceId"))
                .contains(joinedInstanceId);

        var ownerApproved = manager.postWithCsrf(
                flowRoot + "/instances/" + joinedInstanceId
                        + "/branches/owner_review:approve",
                json(Map.of("comment", "Owner branch approved")),
                Map.of());
        assertOk(ownerApproved);
        assertThat(text(ownerApproved.body(), "/data/status")).isEqualTo("PENDING");
        assertThat(text(ownerApproved.body(), "/data/parallelBranches/0/status"))
                .isEqualTo("APPROVED");
        assertThat(text(ownerApproved.body(), "/data/parallelBranches/1/status"))
                .isEqualTo("PENDING");

        var managerAfterOwner = manager.get(
                flowRoot + "/tasks?status=PENDING&page=1&size=100");
        var secondAfterOwner = secondApprover.get(
                flowRoot + "/tasks?status=PENDING&page=1&size=100");
        assertOk(managerAfterOwner);
        assertOk(secondAfterOwner);
        assertThat(itemValues(managerAfterOwner.body().at("/data/items"), "instanceId"))
                .doesNotContain(joinedInstanceId);
        assertThat(itemValues(secondAfterOwner.body().at("/data/items"), "instanceId"))
                .contains(joinedInstanceId);

        var joined = secondApprover.postWithCsrf(
                flowRoot + "/instances/" + joinedInstanceId
                        + "/branches/peer_review:approve",
                json(Map.of("comment", "Peer branch approved")),
                Map.of());
        assertOk(joined);
        assertThat(text(joined.body(), "/data/status")).isEqualTo("APPROVED");
        assertThat(joined.body().at("/data/parallelBranches"))
                .extracting(branch -> branch.path("status").asText())
                .containsExactly("APPROVED", "APPROVED");

        var rejectedStarted = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/instances",
                json(Map.of("businessKey", "parallel-rejected-" + key())),
                Map.of("Idempotency-Key", key()));
        assertCreated(rejectedStarted);
        var rejectedInstanceId = text(rejectedStarted.body(), "/data/instanceId");
        var rejected = secondApprover.postWithCsrf(
                flowRoot + "/instances/" + rejectedInstanceId
                        + "/branches/peer_review:reject",
                json(Map.of("reason", "Peer branch declined")),
                Map.of());
        assertOk(rejected);
        assertThat(text(rejected.body(), "/data/status")).isEqualTo("REJECTED");
        assertThat(text(rejected.body(), "/data/parallelBranches/0/status"))
                .isEqualTo("CANCELLED");
        assertThat(text(rejected.body(), "/data/parallelBranches/1/status"))
                .isEqualTo("REJECTED");

        var managerAfterReject = manager.get(
                flowRoot + "/tasks?status=PENDING&page=1&size=100");
        var secondAfterReject = secondApprover.get(
                flowRoot + "/tasks?status=PENDING&page=1&size=100");
        assertOk(managerAfterReject);
        assertOk(secondAfterReject);
        assertThat(itemValues(managerAfterReject.body().at("/data/items"), "instanceId"))
                .doesNotContain(rejectedInstanceId);
        assertThat(itemValues(secondAfterReject.body().at("/data/items"), "instanceId"))
                .doesNotContain(rejectedInstanceId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_flow_parallel_branch_execution "
                        + "WHERE system_id=? AND tenant_id=? AND instance_id=?",
                Integer.class,
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(joinedInstanceId))).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_flow_parallel_branch_execution "
                        + "WHERE system_id=? AND tenant_id=? AND instance_id=? "
                        + "AND status IN ('CANCELLED', 'REJECTED')",
                Integer.class,
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(rejectedInstanceId))).isEqualTo(2);

        var revisedBranches = List.of(
                Map.of(
                        "code", "owner_review",
                        "name", "Owner review v2",
                        "approverIds", List.of(managerMemberId),
                        "approvalMode", "SEQUENTIAL"
                ),
                Map.of(
                        "code", "peer_review",
                        "name", "Peer review v2",
                        "approverIds", List.of(secondMemberId),
                        "approvalMode", "SEQUENTIAL"
                )
        );
        var revised = manager.putWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/draft",
                json(Map.of(
                        "name", "Parallel branches HTTP journey v2",
                        "approverIds", List.of(managerMemberId),
                        "approvalMode", "SEQUENTIAL",
                        "parallelGateway", Map.of("branches", revisedBranches)
                )),
                Map.of());
        assertOk(revised);
        assertThat(revised.body().at("/data/revision").asInt()).isEqualTo(2);
        var versionTwo = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + ":publish",
                "{}",
                Map.of());
        assertOk(versionTwo);
        assertThat(versionTwo.body().at("/data/version").asInt()).isEqualTo(2);

        var joinedReloaded = manager.get(
                flowRoot + "/instances/" + joinedInstanceId);
        assertOk(joinedReloaded);
        assertThat(joinedReloaded.body().at("/data/definitionVersion").asInt()).isOne();
        assertThat(text(joinedReloaded.body(), "/data/parallelBranches/0/name"))
                .isEqualTo("Owner review");
        assertThat(text(joinedReloaded.body(), "/data/parallelBranches/1/name"))
                .isEqualTo("Peer review");

        var latestStarted = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/instances",
                json(Map.of("businessKey", "parallel-v2-" + key())),
                Map.of("Idempotency-Key", key()));
        assertCreated(latestStarted);
        assertThat(latestStarted.body().at("/data/definitionVersion").asInt()).isEqualTo(2);
        assertThat(text(latestStarted.body(), "/data/parallelBranches/0/name"))
                .isEqualTo("Owner review v2");
        assertThat(text(latestStarted.body(), "/data/parallelBranches/1/name"))
                .isEqualTo("Peer review v2");
    }

    private void exerciseInclusiveGateway(
            TestClient manager,
            TestClient secondApprover,
            String flowRoot,
            String systemId,
            String tenantId,
            String managerMemberId,
            String secondMemberId
    ) throws Exception {
        var branches = List.of(
                Map.of(
                        "code", "urgent",
                        "name", "Urgent review",
                        "defaultBranch", false,
                        "conditions", List.of(Map.of(
                                "fieldCode", "urgent",
                                "operator", "EQ",
                                "value", true
                        )),
                        "approverIds", List.of(managerMemberId),
                        "approvalMode", "SEQUENTIAL"
                ),
                Map.of(
                        "code", "large",
                        "name", "Large review",
                        "defaultBranch", false,
                        "conditions", List.of(Map.of(
                                "fieldCode", "amount",
                                "operator", "GTE",
                                "value", 1000
                        )),
                        "approverIds", List.of(secondMemberId),
                        "approvalMode", "SEQUENTIAL"
                ),
                Map.of(
                        "code", "default",
                        "name", "Default review",
                        "defaultBranch", true,
                        "conditions", List.of(),
                        "approverIds", List.of(secondMemberId),
                        "approvalMode", "SEQUENTIAL"
                )
        );
        var created = manager.postWithCsrf(
                flowRoot + "/definitions",
                json(Map.of(
                        "name", "Inclusive gateway HTTP journey",
                        "approverIds", List.of(managerMemberId),
                        "approvalMode", "SEQUENTIAL",
                        "inclusiveGateway", Map.of("branches", branches)
                )),
                Map.of());
        assertCreated(created);
        var definitionId = text(created.body(), "/data/definitionId");
        assertThat(created.body().at("/data/inclusiveGateway/branches").size()).isEqualTo(3);

        var simulated = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/draft:simulate",
                json(Map.of(
                        "businessKey", "inclusive-simulation-" + key(),
                        "values", Map.of("urgent", true, "amount", 5000)
                )),
                Map.of("Idempotency-Key", key()));
        assertOk(simulated);
        assertThat(simulated.body().at("/data/startable").asBoolean()).isTrue();
        assertThat(simulated.body().at("/data/parallelRoutes"))
                .extracting(route -> route.path("branchCode").asText())
                .containsExactly("urgent", "large");

        var published = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + ":publish",
                "{}",
                Map.of());
        assertOk(published);
        assertThat(published.body().at("/data/version").asInt()).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT JSON_LENGTH(inclusive_branches) FROM un_flow_definition_draft "
                        + "WHERE system_id=? AND tenant_id=? AND definition_id=?",
                Integer.class,
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(definitionId))).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT JSON_LENGTH(inclusive_branches) FROM un_flow_definition_version "
                        + "WHERE system_id=? AND tenant_id=? AND definition_id=? AND version_no=1",
                Integer.class,
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(definitionId))).isEqualTo(3);

        var multiStarted = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/instances",
                json(Map.of(
                        "businessKey", "inclusive-multi-" + key(),
                        "values", Map.of("urgent", true, "amount", 5000)
                )),
                Map.of("Idempotency-Key", key()));
        assertCreated(multiStarted);
        var multiInstanceId = text(multiStarted.body(), "/data/instanceId");
        assertThat(multiStarted.body().at("/data/parallelBranches"))
                .extracting(branch -> branch.path("code").asText())
                .containsExactly("urgent", "large");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_flow_parallel_branch_execution "
                        + "WHERE system_id=? AND tenant_id=? AND instance_id=?",
                Integer.class,
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(multiInstanceId))).isEqualTo(2);

        var managerTasks = manager.get(flowRoot + "/tasks?status=PENDING&page=1&size=100");
        var secondTasks = secondApprover.get(
                flowRoot + "/tasks?status=PENDING&page=1&size=100");
        assertOk(managerTasks);
        assertOk(secondTasks);
        assertThat(itemValues(managerTasks.body().at("/data/items"), "instanceId"))
                .contains(multiInstanceId);
        assertThat(itemValues(secondTasks.body().at("/data/items"), "instanceId"))
                .contains(multiInstanceId);

        var urgentApproved = manager.postWithCsrf(
                flowRoot + "/instances/" + multiInstanceId + "/branches/urgent:approve",
                json(Map.of("comment", "Urgent selected branch accepted")),
                Map.of());
        assertOk(urgentApproved);
        assertThat(text(urgentApproved.body(), "/data/status")).isEqualTo("PENDING");
        var joined = secondApprover.postWithCsrf(
                flowRoot + "/instances/" + multiInstanceId + "/branches/large:approve",
                json(Map.of("comment", "Large selected branch accepted")),
                Map.of());
        assertOk(joined);
        assertThat(text(joined.body(), "/data/status")).isEqualTo("APPROVED");

        var singleStarted = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/instances",
                json(Map.of(
                        "businessKey", "inclusive-single-" + key(),
                        "values", Map.of("urgent", true, "amount", 10)
                )),
                Map.of("Idempotency-Key", key()));
        assertCreated(singleStarted);
        var singleInstanceId = text(singleStarted.body(), "/data/instanceId");
        assertThat(singleStarted.body().at("/data/parallelBranches"))
                .extracting(branch -> branch.path("code").asText())
                .containsExactly("urgent");
        var singleApproved = manager.postWithCsrf(
                flowRoot + "/instances/" + singleInstanceId + "/branches/urgent:approve",
                json(Map.of("comment", "Only selected branch accepted")),
                Map.of());
        assertOk(singleApproved);
        assertThat(text(singleApproved.body(), "/data/status")).isEqualTo("APPROVED");

        var defaultStarted = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/instances",
                json(Map.of(
                        "businessKey", "inclusive-default-" + key(),
                        "values", Map.of("urgent", false, "amount", 10)
                )),
                Map.of("Idempotency-Key", key()));
        assertCreated(defaultStarted);
        var defaultInstanceId = text(defaultStarted.body(), "/data/instanceId");
        assertThat(defaultStarted.body().at("/data/parallelBranches"))
                .extracting(branch -> branch.path("code").asText())
                .containsExactly("default");
        var secondDefaultTasks = secondApprover.get(
                flowRoot + "/tasks?status=PENDING&page=1&size=100");
        assertOk(secondDefaultTasks);
        assertThat(itemValues(secondDefaultTasks.body().at("/data/items"), "instanceId"))
                .contains(defaultInstanceId);
        var defaultApproved = secondApprover.postWithCsrf(
                flowRoot + "/instances/" + defaultInstanceId
                        + "/branches/default:approve",
                json(Map.of("comment", "Fallback accepted")),
                Map.of());
        assertOk(defaultApproved);
        assertThat(text(defaultApproved.body(), "/data/status")).isEqualTo("APPROVED");

        var rejectedStarted = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/instances",
                json(Map.of(
                        "businessKey", "inclusive-rejected-" + key(),
                        "values", Map.of("urgent", true, "amount", 5000)
                )),
                Map.of("Idempotency-Key", key()));
        assertCreated(rejectedStarted);
        var rejectedInstanceId = text(rejectedStarted.body(), "/data/instanceId");
        var rejected = secondApprover.postWithCsrf(
                flowRoot + "/instances/" + rejectedInstanceId + "/branches/large:reject",
                json(Map.of("reason", "Large selected branch declined")),
                Map.of());
        assertOk(rejected);
        assertThat(text(rejected.body(), "/data/status")).isEqualTo("REJECTED");
        assertThat(text(rejected.body(), "/data/parallelBranches/0/status"))
                .isEqualTo("CANCELLED");
        assertThat(text(rejected.body(), "/data/parallelBranches/1/status"))
                .isEqualTo("REJECTED");

        var managerAfterReject = manager.get(
                flowRoot + "/tasks?status=PENDING&page=1&size=100");
        assertOk(managerAfterReject);
        assertThat(itemValues(managerAfterReject.body().at("/data/items"), "instanceId"))
                .doesNotContain(rejectedInstanceId);

        var revisedBranches = List.of(
                Map.of(
                        "code", "urgent",
                        "name", "Urgent review v2",
                        "defaultBranch", false,
                        "conditions", List.of(Map.of(
                                "fieldCode", "urgent",
                                "operator", "EQ",
                                "value", true
                        )),
                        "approverIds", List.of(managerMemberId),
                        "approvalMode", "SEQUENTIAL"
                ),
                Map.of(
                        "code", "large",
                        "name", "Large review v2",
                        "defaultBranch", false,
                        "conditions", List.of(Map.of(
                                "fieldCode", "amount",
                                "operator", "GTE",
                                "value", 1000
                        )),
                        "approverIds", List.of(secondMemberId),
                        "approvalMode", "SEQUENTIAL"
                ),
                Map.of(
                        "code", "default",
                        "name", "Default review v2",
                        "defaultBranch", true,
                        "conditions", List.of(),
                        "approverIds", List.of(secondMemberId),
                        "approvalMode", "SEQUENTIAL"
                )
        );
        var revised = manager.putWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/draft",
                json(Map.of(
                        "name", "Inclusive gateway HTTP journey v2",
                        "approverIds", List.of(managerMemberId),
                        "approvalMode", "SEQUENTIAL",
                        "inclusiveGateway", Map.of("branches", revisedBranches)
                )),
                Map.of());
        assertOk(revised);
        var versionTwo = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + ":publish",
                "{}",
                Map.of());
        assertOk(versionTwo);
        assertThat(versionTwo.body().at("/data/version").asInt()).isEqualTo(2);

        var oldReloaded = manager.get(flowRoot + "/instances/" + multiInstanceId);
        assertOk(oldReloaded);
        assertThat(oldReloaded.body().at("/data/definitionVersion").asInt()).isOne();
        assertThat(text(oldReloaded.body(), "/data/parallelBranches/0/name"))
                .isEqualTo("Urgent review");
        assertThat(text(oldReloaded.body(), "/data/parallelBranches/1/name"))
                .isEqualTo("Large review");
    }

    private void exerciseInclusiveBranchOrderedStages(
            TestClient manager,
            TestClient secondApprover,
            String flowRoot,
            String systemId,
            String tenantId,
            String managerMemberId,
            String secondMemberId
    ) throws Exception {
        var urgentStages = List.of(
                Map.of(
                        "code", "urgent_owner",
                        "name", "Urgent owner",
                        "approverIds", List.of(managerMemberId),
                        "approvalMode", "SEQUENTIAL",
                        "approverSource", Map.of("kind", "FIXED")
                ),
                Map.of(
                        "code", "urgent_previous",
                        "name", "Urgent previous handler",
                        "approverIds", List.of(),
                        "approvalMode", "SEQUENTIAL",
                        "approverSource", Map.of("kind", "PREVIOUS_HANDLER")
                ),
                Map.of(
                        "code", "urgent_requester",
                        "name", "Urgent requester",
                        "approverIds", List.of(),
                        "approvalMode", "SEQUENTIAL",
                        "approverSource", Map.of("kind", "REQUESTER")
                )
        );
        var largeStages = List.of(
                Map.of(
                        "code", "large_specialist",
                        "name", "Large specialist",
                        "approverIds", List.of(secondMemberId),
                        "approvalMode", "SEQUENTIAL",
                        "approverSource", Map.of("kind", "FIXED")
                ),
                Map.of(
                        "code", "large_requester",
                        "name", "Large requester",
                        "approverIds", List.of(),
                        "approvalMode", "SEQUENTIAL",
                        "approverSource", Map.of("kind", "REQUESTER")
                )
        );
        var branches = List.of(
                Map.of(
                        "code", "urgent",
                        "name", "Urgent staged review",
                        "defaultBranch", false,
                        "conditions", List.of(Map.of(
                                "fieldCode", "urgent",
                                "operator", "EQ",
                                "value", true
                        )),
                        "approverIds", List.of(managerMemberId),
                        "approvalMode", "SEQUENTIAL",
                        "approverSource", Map.of("kind", "FIXED"),
                        "approvalStages", urgentStages
                ),
                Map.of(
                        "code", "large",
                        "name", "Large staged review",
                        "defaultBranch", false,
                        "conditions", List.of(Map.of(
                                "fieldCode", "amount",
                                "operator", "GTE",
                                "value", 1000
                        )),
                        "approverIds", List.of(secondMemberId),
                        "approvalMode", "SEQUENTIAL",
                        "approverSource", Map.of("kind", "FIXED"),
                        "approvalStages", largeStages
                ),
                Map.of(
                        "code", "default",
                        "name", "Default staged review",
                        "defaultBranch", true,
                        "conditions", List.of(),
                        "approverIds", List.of(managerMemberId),
                        "approvalMode", "SEQUENTIAL"
                )
        );
        var created = manager.postWithCsrf(
                flowRoot + "/definitions",
                json(Map.of(
                        "name", "Inclusive branch ordered stages",
                        "approverIds", List.of(managerMemberId),
                        "approvalMode", "SEQUENTIAL",
                        "inclusiveGateway", Map.of("branches", branches)
                )),
                Map.of());
        assertCreated(created);
        var definitionId = text(created.body(), "/data/definitionId");
        assertThat(created.body().at(
                "/data/inclusiveGateway/branches/0/approvalStages")).hasSize(3);
        assertThat(created.body().at(
                "/data/inclusiveGateway/branches/1/approvalStages")).hasSize(2);

        var checked = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/draft:check",
                "{}",
                Map.of("Idempotency-Key", key()));
        assertOk(checked);
        assertThat(text(checked.body(), "/data/verdict")).isEqualTo("READY");
        var simulated = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/draft:simulate",
                json(Map.of(
                        "businessKey", "branch-stages-preview-" + key(),
                        "values", Map.of("urgent", true, "amount", 5000)
                )),
                Map.of("Idempotency-Key", key()));
        assertOk(simulated);
        assertThat(simulated.body().at("/data/parallelRoutes"))
                .extracting(route -> route.path("branchCode").asText())
                .containsExactly("urgent", "large");
        assertThat(simulated.body().at(
                "/data/parallelRoutes/0/approvalStages")).hasSize(3);
        assertThat(simulated.body().at(
                "/data/parallelRoutes/1/approvalStages")).hasSize(2);

        var published = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + ":publish",
                "{}",
                Map.of());
        assertOk(published);
        var started = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/instances",
                json(Map.of(
                        "businessKey", "branch-stages-" + key(),
                        "values", Map.of("urgent", true, "amount", 5000)
                )),
                Map.of("Idempotency-Key", key()));
        assertCreated(started);
        var instanceId = text(started.body(), "/data/instanceId");
        assertThat(started.body().at("/data/parallelBranches"))
                .extracting(branch -> branch.path("code").asText())
                .containsExactly("urgent", "large");
        assertThat(started.body().at(
                "/data/parallelBranches/0/currentStageIndex").asInt()).isZero();
        assertThat(started.body().at(
                "/data/parallelBranches/1/currentStageIndex").asInt()).isZero();

        var urgentStageOne = manager.postWithCsrf(
                flowRoot + "/instances/" + instanceId
                        + "/branches/urgent:approve",
                json(Map.of("comment", "Urgent owner completed")),
                Map.of());
        assertOk(urgentStageOne);
        assertThat(urgentStageOne.body().at(
                "/data/parallelBranches/0/currentStageIndex").asInt())
                .isEqualTo(1);
        assertThat(urgentStageOne.body().at(
                "/data/parallelBranches/0/approverIds"))
                .extracting(JsonNode::asText)
                .containsExactly(managerMemberId);
        assertThat(urgentStageOne.body().at(
                "/data/parallelBranches/0/stages/0/actualHandlerIds"))
                .extracting(JsonNode::asText)
                .containsExactly(managerMemberId);

        var largeStageOne = secondApprover.postWithCsrf(
                flowRoot + "/instances/" + instanceId
                        + "/branches/large:approve",
                json(Map.of("comment", "Large specialist completed")),
                Map.of());
        assertOk(largeStageOne);
        assertThat(largeStageOne.body().at(
                "/data/parallelBranches/1/currentStageIndex").asInt())
                .isEqualTo(1);
        assertThat(largeStageOne.body().at(
                "/data/parallelBranches/1/approverIds"))
                .extracting(JsonNode::asText)
                .containsExactly(managerMemberId);

        var urgentStageTwo = manager.postWithCsrf(
                flowRoot + "/instances/" + instanceId
                        + "/branches/urgent:approve",
                json(Map.of("comment", "Branch-local previous handler completed")),
                Map.of());
        assertOk(urgentStageTwo);
        assertThat(urgentStageTwo.body().at(
                "/data/parallelBranches/0/currentStageIndex").asInt())
                .isEqualTo(2);
        assertThat(urgentStageTwo.body().at(
                "/data/parallelBranches/0/stages/1/actualHandlerIds"))
                .extracting(JsonNode::asText)
                .containsExactly(managerMemberId);

        var urgentDone = manager.postWithCsrf(
                flowRoot + "/instances/" + instanceId
                        + "/branches/urgent:approve",
                json(Map.of("comment", "Urgent requester completed")),
                Map.of());
        assertOk(urgentDone);
        assertThat(text(urgentDone.body(), "/data/status")).isEqualTo("PENDING");
        assertThat(text(urgentDone.body(), "/data/parallelBranches/0/status"))
                .isEqualTo("APPROVED");

        var joined = manager.postWithCsrf(
                flowRoot + "/instances/" + instanceId
                        + "/branches/large:approve",
                json(Map.of("comment", "Large requester completed")),
                Map.of());
        assertOk(joined);
        assertThat(text(joined.body(), "/data/status")).isEqualTo("APPROVED");
        assertThat(itemValues(joined.body().at(
                "/data/parallelBranches/0/stages"), "status"))
                .containsExactly("APPROVED", "APPROVED", "APPROVED");
        assertThat(itemValues(joined.body().at(
                "/data/parallelBranches/1/stages"), "status"))
                .containsExactly("APPROVED", "APPROVED");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT JSON_UNQUOTE(JSON_EXTRACT("
                        + "start_context,'$.requesterMemberId')) "
                        + "FROM un_flow_instance "
                        + "WHERE system_id=? AND tenant_id=? AND instance_id=?",
                String.class,
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(instanceId))).isEqualTo(managerMemberId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_flow_parallel_branch_execution "
                        + "WHERE system_id=? AND tenant_id=? AND instance_id=? "
                        + "AND approval_stage_state IS NOT NULL",
                Integer.class,
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(instanceId))).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT SUM(current_stage_index) "
                        + "FROM un_flow_parallel_branch_execution "
                        + "WHERE system_id=? AND tenant_id=? AND instance_id=?",
                Integer.class,
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(instanceId))).isEqualTo(3);

        var reloaded = manager.get(flowRoot + "/instances/" + instanceId);
        assertOk(reloaded);
        assertThat(text(reloaded.body(), "/data/status")).isEqualTo("APPROVED");
        assertThat(reloaded.body().at(
                "/data/parallelBranches/0/currentStageIndex").asInt())
                .isEqualTo(2);
        assertThat(reloaded.body().at(
                "/data/parallelBranches/1/currentStageIndex").asInt())
                .isEqualTo(1);
    }

    private void exerciseDecisionEvidenceAndTemplates(
            TestClient manager,
            TestClient secondApprover,
            String flowRoot,
            String systemId,
            String tenantId,
            String foreignTenantId,
            String managerMemberId,
            String secondMemberId
    ) throws Exception {
        var templateCreated = manager.postWithCsrf(
                flowRoot + "/decision-comment-templates",
                json(Map.of(
                        "name", "Evidence approval",
                        "body", "Approved with governed evidence"
                )),
                Map.of());
        assertCreated(templateCreated);
        var templateId = text(templateCreated.body(), "/data/templateId");
        assertThat(templateCreated.body().at("/data/currentVersion").asInt())
                .isOne();
        assertThat(text(templateCreated.body(), "/data/status"))
                .isEqualTo("ACTIVE");

        var activeTemplates = manager.get(
                flowRoot + "/decision-comment-templates?page=1&size=100");
        assertOk(activeTemplates);
        assertThat(itemValues(
                activeTemplates.body().at("/data/items"), "templateId"))
                .contains(templateId);

        var attachment = manager.uploadWithCsrf(
                "/api/v1/systems/" + systemId + "/files",
                "approval-evidence.pdf",
                "application/pdf",
                "%PDF-1.4 governed approval evidence"
                        .getBytes(StandardCharsets.UTF_8));
        assertCreated(attachment);
        var attachmentFileId = text(attachment.body(), "/data/id");
        var attachmentSha256 = text(attachment.body(), "/data/sha256");

        var signature = manager.uploadWithCsrf(
                "/api/v1/systems/" + systemId + "/files",
                "approval-signature.png",
                "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 1, 2, 3, 4});
        assertCreated(signature);
        var signatureFileId = text(signature.body(), "/data/id");

        var disallowed = manager.uploadWithCsrf(
                "/api/v1/systems/" + systemId + "/files",
                "approval-evidence.txt",
                "text/plain",
                "disallowed evidence family".getBytes(StandardCharsets.UTF_8));
        assertCreated(disallowed);
        var disallowedFileId = text(disallowed.body(), "/data/id");

        assertOk(manager.postWithCsrf(
                "/api/v1/context/tenants/" + foreignTenantId + ":switch",
                "{}",
                Map.of()));
        var foreign = manager.uploadWithCsrf(
                "/api/v1/systems/" + systemId + "/files",
                "foreign-tenant-evidence.pdf",
                "application/pdf",
                "%PDF-1.4 foreign tenant evidence"
                        .getBytes(StandardCharsets.UTF_8));
        assertCreated(foreign);
        var foreignFileId = text(foreign.body(), "/data/id");
        assertOk(manager.postWithCsrf(
                "/api/v1/context/tenants/" + tenantId + ":switch",
                "{}",
                Map.of()));

        var policy = Map.of(
                "minimumAttachments", 1,
                "maximumAttachments", 2,
                "allowedMimeFamilies", List.of("PDF", "IMAGE"),
                "signatureMode", "REQUIRED"
        );
        var stages = List.of(
                Map.of(
                        "code", "evidence_review",
                        "name", "Evidence review",
                        "approverIds", List.of(secondMemberId),
                        "approvalMode", "SEQUENTIAL",
                        "approverSource", Map.of("kind", "FIXED"),
                        "decisionEvidencePolicy", policy
                ),
                Map.of(
                        "code", "evidence_archive",
                        "name", "Evidence archive",
                        "approverIds", List.of(managerMemberId),
                        "approvalMode", "SEQUENTIAL",
                        "approverSource", Map.of("kind", "FIXED")
                )
        );
        var definition = manager.postWithCsrf(
                flowRoot + "/definitions",
                json(Map.of(
                        "name", "Governed decision evidence",
                        "approverIds", List.of(secondMemberId),
                        "approvalMode", "SEQUENTIAL",
                        "approverSource", Map.of("kind", "FIXED"),
                        "decisionEvidencePolicy", policy,
                        "approvalStages", stages
                )),
                Map.of());
        assertCreated(definition);
        var definitionId = text(definition.body(), "/data/definitionId");
        assertThat(text(definition.body(),
                "/data/decisionEvidencePolicy/signatureMode"))
                .isEqualTo("REQUIRED");
        assertThat(text(definition.body(),
                "/data/approvalStages/0/decisionEvidencePolicy/signatureMode"))
                .isEqualTo("REQUIRED");

        var checked = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/draft:check",
                "{}",
                Map.of("Idempotency-Key", key()));
        assertOk(checked);
        assertThat(text(checked.body(), "/data/verdict")).isEqualTo("READY");
        var published = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + ":publish",
                "{}",
                Map.of());
        assertOk(published);

        var activeNow = Instant.now();
        var delegation = secondApprover.postWithCsrf(
                flowRoot + "/delegations",
                json(Map.of(
                        "delegatorMemberId", secondMemberId,
                        "delegateMemberId", managerMemberId,
                        "startsAt", activeNow.minusSeconds(30).toString(),
                        "endsAt", activeNow.plusSeconds(3600).toString(),
                        "definitionId", definitionId
                )),
                Map.of());
        assertCreated(delegation);
        var delegationRuleId = text(
                delegation.body(), "/data/delegationRuleId");

        var started = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/instances",
                json(Map.of("businessKey", "decision-evidence-" + key())),
                Map.of("Idempotency-Key", key()));
        assertCreated(started);
        var instanceId = text(started.body(), "/data/instanceId");
        assertThat(text(started.body(),
                "/data/decisionEvidencePolicy/signatureMode"))
                .isEqualTo("REQUIRED");

        var missingEvidence = manager.postWithCsrf(
                flowRoot + "/instances/" + instanceId + ":approve",
                json(Map.of(
                        "representedMemberId", secondMemberId,
                        "comment", "Must not be accepted"
                )),
                Map.of("Idempotency-Key", key()));
        assertError(missingEvidence, 422, "FLOW_EVIDENCE_INVALID");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_flow_history_event "
                        + "WHERE system_id=? AND tenant_id=? AND instance_id=? "
                        + "AND event_type='APPROVED'",
                Integer.class,
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(instanceId))).isZero();

        var missingFile = manager.postWithCsrf(
                flowRoot + "/instances/" + instanceId + ":approve",
                json(Map.of(
                        "representedMemberId", secondMemberId,
                        "attachmentFileIds", List.of("9223372036854775000"),
                        "signatureFileId", signatureFileId,
                        "commentTemplateId", templateId
                )),
                Map.of("Idempotency-Key", key()));
        assertError(missingFile, 404, "FLOW_EVIDENCE_FILE_NOT_FOUND");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_flow_decision_evidence "
                        + "WHERE system_id=? AND tenant_id=? AND instance_id=?",
                Integer.class,
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(instanceId))).isZero();

        var foreignFile = manager.postWithCsrf(
                flowRoot + "/instances/" + instanceId + ":approve",
                json(Map.of(
                        "representedMemberId", secondMemberId,
                        "attachmentFileIds", List.of(foreignFileId),
                        "signatureFileId", signatureFileId,
                        "commentTemplateId", templateId
                )),
                Map.of("Idempotency-Key", key()));
        assertError(foreignFile, 404, "FLOW_EVIDENCE_FILE_NOT_FOUND");

        var ineligibleFile = manager.postWithCsrf(
                flowRoot + "/instances/" + instanceId + ":approve",
                json(Map.of(
                        "representedMemberId", secondMemberId,
                        "attachmentFileIds", List.of(disallowedFileId),
                        "signatureFileId", signatureFileId,
                        "commentTemplateId", templateId
                )),
                Map.of("Idempotency-Key", key()));
        assertError(ineligibleFile, 422, "FLOW_EVIDENCE_INVALID");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_flow_decision_evidence "
                        + "WHERE system_id=? AND tenant_id=? AND instance_id=?",
                Integer.class,
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(instanceId))).isZero();

        var evidenced = manager.postWithCsrf(
                flowRoot + "/instances/" + instanceId + ":approve",
                json(Map.of(
                        "representedMemberId", secondMemberId,
                        "attachmentFileIds", List.of(attachmentFileId),
                        "signatureFileId", signatureFileId,
                        "commentTemplateId", templateId
                )),
                Map.of("Idempotency-Key", key()));
        assertOk(evidenced);
        assertThat(text(evidenced.body(), "/data/status")).isEqualTo("PENDING");
        assertThat(evidenced.body().at("/data/currentStageIndex").asInt())
                .isEqualTo(1);

        var history = manager.get(
                flowRoot + "/instances/" + instanceId + "/history");
        assertOk(history);
        var approvalEvent = item(
                history.body().at("/data/events"), "type", "APPROVED");
        assertThat(approvalEvent.path("comment").asText())
                .isEqualTo("Approved with governed evidence");
        assertThat(approvalEvent.at("/representedMemberId").asText())
                .isEqualTo(secondMemberId);
        assertThat(approvalEvent.at("/delegationRuleId").asText())
                .isEqualTo(delegationRuleId);
        assertThat(approvalEvent.at("/evidence/attachments/0/fileId").asText())
                .isEqualTo(attachmentFileId);
        assertThat(approvalEvent.at("/evidence/attachments/0/sha256").asText())
                .isEqualTo(attachmentSha256);
        assertThat(approvalEvent.at("/evidence/signature/kind").asText())
                .isEqualTo("FILE");
        assertThat(approvalEvent.at("/evidence/signature/file/fileId").asText())
                .isEqualTo(signatureFileId);
        assertThat(approvalEvent.at("/evidence/template/templateId").asText())
                .isEqualTo(templateId);
        assertThat(approvalEvent.at("/evidence/template/version").asInt())
                .isOne();
        var evidenceId = approvalEvent.at("/evidence/evidenceId").asText();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_file_reference "
                        + "WHERE system_id=? AND tenant_id=? "
                        + "AND target_type='FLOW_DECISION_EVIDENCE' "
                        + "AND target_id=?",
                Integer.class,
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                evidenceId)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_flow_decision_evidence_file "
                        + "WHERE system_id=? AND tenant_id=? AND evidence_id=?",
                Integer.class,
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(evidenceId))).isEqualTo(2);

        var revisedTemplate = manager.putWithCsrf(
                flowRoot + "/decision-comment-templates/" + templateId,
                json(Map.of(
                        "name", "Evidence approval revised",
                        "body", "A later template revision"
                )),
                Map.of());
        assertOk(revisedTemplate);
        assertThat(revisedTemplate.body().at("/data/currentVersion").asInt())
                .isEqualTo(2);
        assertOk(manager.postWithCsrf(
                flowRoot + "/decision-comment-templates/"
                        + templateId + ":deactivate",
                "{}",
                Map.of()));
        assertError(manager.deleteWithCsrf(
                        "/api/v1/systems/" + systemId
                                + "/files/" + attachmentFileId),
                409,
                "FILE_STILL_REFERENCED");

        var immutableHistory = manager.get(
                flowRoot + "/instances/" + instanceId + "/history");
        assertOk(immutableHistory);
        var immutableApproval = item(
                immutableHistory.body().at("/data/events"),
                "type",
                "APPROVED");
        assertThat(immutableApproval.at(
                "/evidence/template/version").asInt()).isOne();
        assertThat(immutableApproval.at(
                "/evidence/template/name").asText())
                .isEqualTo("Evidence approval");
        assertThat(immutableApproval.at(
                "/evidence/attachments/0/sha256").asText())
                .isEqualTo(attachmentSha256);

        var completed = manager.postWithCsrf(
                flowRoot + "/instances/" + instanceId + ":approve",
                json(Map.of("comment", "Archived immutable evidence")),
                Map.of());
        assertOk(completed);
        assertThat(text(completed.body(), "/data/status")).isEqualTo("APPROVED");
    }

    private void exerciseCompletionExecutions(
            TestClient manager,
            String flowRoot,
            String systemId,
            String tenantId,
            String foreignTenantId,
            String managerMemberId
    ) throws Exception {
        var completionSteps = List.of(
                Map.of(
                        "code", "dispatch_job",
                        "name", "Dispatch external job",
                        "type", "EXTERNAL_TASK",
                        "externalTask", Map.of(
                                "topic", "records.export",
                                "leaseSeconds", 60,
                                "maxAttempts", 3,
                                "resultJsonLimitBytes", 8192
                        )
                ),
                Map.of(
                        "code", "notify_webhook",
                        "name", "Notify governed endpoint",
                        "type", "WEBHOOK",
                        "webhook", Map.of(
                                "url", "https://1.1.1.1/examine-flow",
                                "timeoutSeconds", 5,
                                "maxAttempts", 3,
                                "baseBackoffSeconds", 1
                        )
                )
        );
        var created = manager.postWithCsrf(
                flowRoot + "/definitions",
                json(Map.of(
                        "name", "External completion HTTP journey",
                        "approverIds", List.of(managerMemberId),
                        "approvalMode", "SEQUENTIAL",
                        "approverSource", Map.of("kind", "FIXED"),
                        "completionSteps", completionSteps
                )),
                Map.of());
        assertCreated(created);
        var definitionId = text(created.body(), "/data/definitionId");
        assertThat(text(created.body(), "/data/completionSteps/0/type"))
                .isEqualTo("EXTERNAL_TASK");
        assertThat(text(created.body(), "/data/completionSteps/1/type"))
                .isEqualTo("WEBHOOK");

        var checked = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/draft:check",
                "{}",
                Map.of("Idempotency-Key", key()));
        assertOk(checked);
        assertThat(text(checked.body(), "/data/verdict")).isEqualTo("READY");
        var published = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + ":publish",
                "{}",
                Map.of());
        assertOk(published);
        assertThat(published.body().at("/data/completionSteps").size())
                .isEqualTo(2);

        var started = manager.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/instances",
                json(Map.of("businessKey", "completion-" + key())),
                Map.of("Idempotency-Key", key()));
        assertCreated(started);
        var instanceId = text(started.body(), "/data/instanceId");
        assertThat(text(started.body(), "/data/completionPhase"))
                .isEqualTo("HUMAN_APPROVAL");

        var approved = manager.postWithCsrf(
                flowRoot + "/instances/" + instanceId + ":approve",
                json(Map.of("comment", "Human approval completed")),
                Map.of("Idempotency-Key", key()));
        assertOk(approved);
        assertThat(text(approved.body(), "/data/status")).isEqualTo("PENDING");
        assertThat(text(approved.body(), "/data/completionPhase"))
                .isEqualTo("EXTERNAL_EXECUTION");
        assertThat(approved.body().at("/data/activeCompletionOrdinal").asInt())
                .isZero();
        assertThat(itemValues(
                approved.body().at("/data/completionExecutions"), "status"))
                .containsExactly("AVAILABLE", "WAITING");
        var externalExecutionId = text(
                approved.body(), "/data/completionExecutions/0/executionId");
        var webhookExecutionId = text(
                approved.body(), "/data/completionExecutions/1/executionId");

        assertOk(manager.postWithCsrf(
                "/api/v1/context/tenants/" + foreignTenantId + ":switch",
                "{}",
                Map.of()));
        var foreignQueue = manager.get(
                flowRoot + "/external-tasks?page=1&size=100");
        assertOk(foreignQueue);
        assertThat(foreignQueue.body().at("/data/total").asLong()).isZero();
        assertOk(manager.postWithCsrf(
                "/api/v1/context/tenants/" + tenantId + ":switch",
                "{}",
                Map.of()));

        var queue = manager.get(
                flowRoot + "/external-tasks?topic=records.export&page=1&size=100");
        assertOk(queue);
        assertThat(itemValues(queue.body().at("/data/items"), "executionId"))
                .containsExactly(externalExecutionId);

        var claimKey = key();
        var claimed = manager.postWithCsrf(
                flowRoot + "/external-tasks/" + externalExecutionId + ":claim",
                "{}",
                Map.of("Idempotency-Key", claimKey));
        assertOk(claimed);
        var leaseToken = text(claimed.body(), "/data/leaseToken");
        assertThat(leaseToken).isNotBlank();
        assertThat(text(claimed.body(), "/data/execution/status"))
                .isEqualTo("LEASED");
        var claimReplay = manager.postWithCsrf(
                flowRoot + "/external-tasks/" + externalExecutionId + ":claim",
                "{}",
                Map.of("Idempotency-Key", claimKey));
        assertOk(claimReplay);
        assertThat(absentOrNull(claimReplay.body().at("/data/leaseToken")))
                .isTrue();
        assertThat(text(claimReplay.body(), "/data/execution/executionId"))
                .isEqualTo(externalExecutionId);

        var completeKey = key();
        var completionBody = json(Map.of(
                "leaseToken", leaseToken,
                "result", Map.of(
                        "externalJobId", "job-" + key(),
                        "accepted", true
                )
        ));
        var externalCompleted = manager.postWithCsrf(
                flowRoot + "/external-tasks/" + externalExecutionId + ":complete",
                completionBody,
                Map.of("Idempotency-Key", completeKey));
        assertOk(externalCompleted);
        assertThat(text(externalCompleted.body(), "/data/status"))
                .isEqualTo("SUCCEEDED");
        var externalReplay = manager.postWithCsrf(
                flowRoot + "/external-tasks/" + externalExecutionId + ":complete",
                completionBody,
                Map.of("Idempotency-Key", completeKey));
        assertOk(externalReplay);
        assertThat(text(externalReplay.body(), "/data/executionId"))
                .isEqualTo(externalExecutionId);

        var waitingWebhook = manager.get(
                flowRoot + "/instances/" + instanceId);
        assertOk(waitingWebhook);
        assertThat(text(waitingWebhook.body(), "/data/status"))
                .isEqualTo("PENDING");
        assertThat(waitingWebhook.body().at(
                "/data/activeCompletionOrdinal").asInt()).isOne();
        assertThat(itemValues(waitingWebhook.body().at(
                "/data/completionExecutions"), "status"))
                .containsExactly("SUCCEEDED", "AVAILABLE");

        assertThat(webhookWorker.pollOnce(20)).isOne();
        assertThat(webhookTransport.attempts()).isOne();
        var retrying = manager.get(flowRoot + "/instances/" + instanceId);
        assertOk(retrying);
        assertThat(text(retrying.body(),
                "/data/completionExecutions/1/status"))
                .isEqualTo("RETRYING");
        assertThat(itemValues(retrying.body().at(
                "/data/completionExecutions/1/attempts"), "status"))
                .contains("CLAIMED", "RETRIED");

        assertThat(jdbcTemplate.update(
                "UPDATE un_flow_completion_execution "
                        + "SET available_at=DATE_SUB(UTC_TIMESTAMP(6),"
                        + "INTERVAL 1 SECOND) "
                        + "WHERE system_id=? AND tenant_id=? AND execution_id=? "
                        + "AND status='RETRYING'",
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(webhookExecutionId))).isOne();
        assertThat(webhookWorker.pollOnce(20)).isOne();
        assertThat(webhookTransport.attempts()).isEqualTo(2);

        var terminal = manager.get(flowRoot + "/instances/" + instanceId);
        assertOk(terminal);
        assertThat(text(terminal.body(), "/data/status")).isEqualTo("APPROVED");
        assertThat(text(terminal.body(), "/data/completionPhase"))
                .isEqualTo("COMPLETED");
        assertThat(absentOrNull(
                terminal.body().at("/data/activeCompletionOrdinal")))
                .isTrue();
        assertThat(itemValues(terminal.body().at(
                "/data/completionExecutions"), "status"))
                .containsExactly("SUCCEEDED", "SUCCEEDED");
        assertThat(terminal.body().at(
                "/data/completionExecutions/1/attemptCount").asInt())
                .isEqualTo(2);
        assertThat(itemValues(terminal.body().at(
                "/data/completionExecutions/1/attempts"), "httpStatus"))
                .contains("503", "204");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_flow_completion_execution "
                        + "WHERE system_id=? AND tenant_id=? AND instance_id=?",
                Integer.class,
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(instanceId))).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_flow_completion_attempt "
                        + "WHERE system_id=? AND tenant_id=? AND execution_id=? "
                        + "AND http_status IN (503,204) "
                        + "AND response_sha256 REGEXP '^[0-9a-f]{64}$'",
                Integer.class,
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(webhookExecutionId))).isEqualTo(2);

        assertThat(webhookTransport.requests()).hasSize(2);
        var firstRequest = webhookTransport.requests().getFirst();
        assertThat(firstRequest.headers())
                .containsEntry("Content-Type", "application/json")
                .containsKey("X-Examine-Delivery-Id")
                .containsKey("X-Examine-Timestamp")
                .doesNotContainKey("X-Examine-Signature");
        var payload = objectMapper.readTree(firstRequest.body());
        assertThat(payload.at("/version").asInt()).isOne();
        assertThat(payload.at("/instance/id").asText()).isEqualTo(instanceId);
        assertThat(payload.at("/completionStep/code").asText())
                .isEqualTo("notify_webhook");
        assertThat(payload.has("comments")).isFalse();
        assertThat(payload.toString())
                .doesNotContain(leaseToken, "storageKey", "secretRef");

        var reloaded = manager.get(flowRoot + "/instances/" + instanceId);
        assertOk(reloaded);
        assertThat(text(reloaded.body(), "/data/status")).isEqualTo("APPROVED");
        assertThat(itemValues(reloaded.body().at(
                "/data/completionExecutions"), "status"))
                .containsExactly("SUCCEEDED", "SUCCEEDED");
    }

    private void exerciseSubflowCompletion(
            TestClient manager,
            String flowRoot,
            String systemId,
            String tenantId,
            String managerMemberId
    ) throws Exception {
        var childCreated = manager.postWithCsrf(
                flowRoot + "/definitions",
                json(Map.of(
                        "name", "Subflow child HTTP journey",
                        "approverIds", List.of(managerMemberId),
                        "approvalMode", "SEQUENTIAL",
                        "approverSource", Map.of("kind", "FIXED")
                )),
                Map.of());
        assertCreated(childCreated);
        var childDefinitionId = text(
                childCreated.body(), "/data/definitionId");
        var childPublished = manager.postWithCsrf(
                flowRoot + "/definitions/" + childDefinitionId + ":publish",
                "{}",
                Map.of());
        assertOk(childPublished);
        var childVersion = childPublished.body().at("/data/version").asInt();

        var subflowStep = Map.of(
                "code", "child_approval",
                "name", "Run governed child approval",
                "type", "SUBFLOW",
                "subflow", Map.of(
                        "definitionId", childDefinitionId,
                        "version", childVersion
                )
        );
        var parentCreated = manager.postWithCsrf(
                flowRoot + "/definitions",
                json(Map.of(
                        "name", "Subflow parent HTTP journey",
                        "approverIds", List.of(managerMemberId),
                        "approvalMode", "SEQUENTIAL",
                        "approverSource", Map.of("kind", "FIXED"),
                        "completionSteps", List.of(subflowStep)
                )),
                Map.of());
        assertCreated(parentCreated);
        var parentDefinitionId = text(
                parentCreated.body(), "/data/definitionId");
        assertThat(text(parentCreated.body(),
                "/data/completionSteps/0/type")).isEqualTo("SUBFLOW");
        assertThat(text(parentCreated.body(),
                "/data/completionSteps/0/subflow/definitionId"))
                .isEqualTo(childDefinitionId);
        assertThat(parentCreated.body().at(
                "/data/completionSteps/0/subflow/version").asInt())
                .isEqualTo(childVersion);

        var parentChecked = manager.postWithCsrf(
                flowRoot + "/definitions/" + parentDefinitionId
                        + "/draft:check",
                "{}",
                Map.of("Idempotency-Key", key()));
        assertOk(parentChecked);
        assertThat(text(parentChecked.body(), "/data/verdict"))
                .isEqualTo("READY");
        var parentPublished = manager.postWithCsrf(
                flowRoot + "/definitions/" + parentDefinitionId + ":publish",
                "{}",
                Map.of());
        assertOk(parentPublished);

        var successfulParent = manager.postWithCsrf(
                flowRoot + "/definitions/" + parentDefinitionId
                        + "/instances",
                json(Map.of("businessKey", "subflow-success-" + key())),
                Map.of("Idempotency-Key", key()));
        assertCreated(successfulParent);
        var successfulParentId = text(
                successfulParent.body(), "/data/instanceId");
        var humanApproved = manager.postWithCsrf(
                flowRoot + "/instances/" + successfulParentId + ":approve",
                json(Map.of("comment", "Launch the child approval")),
                Map.of("Idempotency-Key", key()));
        assertOk(humanApproved);
        assertThat(text(humanApproved.body(), "/data/status"))
                .isEqualTo("PENDING");
        assertThat(text(humanApproved.body(), "/data/completionPhase"))
                .isEqualTo("EXTERNAL_EXECUTION");
        assertThat(text(humanApproved.body(),
                "/data/completionExecutions/0/status"))
                .isEqualTo("AVAILABLE");

        assertThat(subflowWorker.pollOnce(20)).isOne();
        var childRunning = manager.get(
                flowRoot + "/instances/" + successfulParentId);
        assertOk(childRunning);
        assertThat(text(childRunning.body(),
                "/data/completionExecutions/0/status"))
                .isEqualTo("RUNNING");
        assertThat(childRunning.body().at(
                "/data/completionExecutions/0/subflowRuns")).hasSize(1);
        var successfulChildId = text(childRunning.body(),
                "/data/completionExecutions/0/subflowRuns/0/childInstanceId");
        assertThat(text(childRunning.body(),
                "/data/completionExecutions/0/subflowRuns/0/childStatus"))
                .isEqualTo("RUNNING");
        assertThat(childRunning.body().at(
                "/data/completionExecutions/0/subflowRuns/0")
                .toString())
                .doesNotContain(
                        "launchKey", "rootInstanceId", "subflowDepth",
                        "resultAppliedAt", "stateVersion");

        var childApproved = manager.postWithCsrf(
                flowRoot + "/instances/" + successfulChildId + ":approve",
                json(Map.of("comment", "Child approved")),
                Map.of("Idempotency-Key", key()));
        assertOk(childApproved);
        assertThat(text(childApproved.body(), "/data/status"))
                .isEqualTo("APPROVED");
        assertThat(subflowWorker.pollOnce(20)).isOne();

        var successfulTerminal = manager.get(
                flowRoot + "/instances/" + successfulParentId);
        assertOk(successfulTerminal);
        assertThat(text(successfulTerminal.body(), "/data/status"))
                .isEqualTo("APPROVED");
        assertThat(text(successfulTerminal.body(), "/data/completionPhase"))
                .isEqualTo("COMPLETED");
        assertThat(text(successfulTerminal.body(),
                "/data/completionExecutions/0/status"))
                .isEqualTo("SUCCEEDED");
        assertThat(text(successfulTerminal.body(),
                "/data/completionExecutions/0/subflowRuns/0/childStatus"))
                .isEqualTo("APPROVED_COMPLETED");
        assertThat(text(successfulTerminal.body(),
                "/data/completionExecutions/0/subflowRuns/0/terminalAt"))
                .isNotBlank();
        assertThat(subflowWorker.pollOnce(20)).isZero();

        var retryingParent = manager.postWithCsrf(
                flowRoot + "/definitions/" + parentDefinitionId
                        + "/instances",
                json(Map.of("businessKey", "subflow-retry-" + key())),
                Map.of("Idempotency-Key", key()));
        assertCreated(retryingParent);
        var retryingParentId = text(retryingParent.body(), "/data/instanceId");
        var retryingApproved = manager.postWithCsrf(
                flowRoot + "/instances/" + retryingParentId + ":approve",
                json(Map.of("comment", "Exercise retry")),
                Map.of("Idempotency-Key", key()));
        assertOk(retryingApproved);
        var retryExecutionId = text(retryingApproved.body(),
                "/data/completionExecutions/0/executionId");
        assertThat(subflowWorker.pollOnce(20)).isOne();
        var firstAttempt = manager.get(
                flowRoot + "/instances/" + retryingParentId);
        assertOk(firstAttempt);
        var firstChildId = text(firstAttempt.body(),
                "/data/completionExecutions/0/subflowRuns/0/childInstanceId");

        var childRejected = manager.postWithCsrf(
                flowRoot + "/instances/" + firstChildId + ":reject",
                json(Map.of("reason", "Reject first child attempt")),
                Map.of("Idempotency-Key", key()));
        assertOk(childRejected);
        assertThat(text(childRejected.body(), "/data/status"))
                .isEqualTo("REJECTED");
        assertThat(subflowWorker.pollOnce(20)).isOne();
        var failedParent = manager.get(
                flowRoot + "/instances/" + retryingParentId);
        assertOk(failedParent);
        assertThat(text(failedParent.body(), "/data/status"))
                .isEqualTo("PENDING");
        assertThat(text(failedParent.body(),
                "/data/completionExecutions/0/status"))
                .isEqualTo("FAILED");
        assertThat(text(failedParent.body(),
                "/data/completionExecutions/0/subflowRuns/0/childStatus"))
                .isEqualTo("REJECTED");

        var retried = manager.postWithCsrf(
                flowRoot + "/instances/" + retryingParentId
                        + "/completion-executions/" + retryExecutionId
                        + ":retry",
                "{}",
                Map.of("Idempotency-Key", key()));
        assertOk(retried);
        assertThat(text(retried.body(), "/data/status"))
                .isEqualTo("AVAILABLE");
        assertThat(subflowWorker.pollOnce(20)).isOne();
        var secondAttempt = manager.get(
                flowRoot + "/instances/" + retryingParentId);
        assertOk(secondAttempt);
        assertThat(secondAttempt.body().at(
                "/data/completionExecutions/0/subflowRuns")).hasSize(2);
        var secondChildId = text(secondAttempt.body(),
                "/data/completionExecutions/0/subflowRuns/1/childInstanceId");
        assertThat(secondChildId).isNotEqualTo(firstChildId);
        assertThat(secondAttempt.body().at(
                "/data/completionExecutions/0/subflowRuns/1/attempt").asInt())
                .isEqualTo(2);

        assertOk(manager.postWithCsrf(
                flowRoot + "/instances/" + secondChildId + ":approve",
                json(Map.of("comment", "Approve retry child")),
                Map.of("Idempotency-Key", key())));
        assertThat(subflowWorker.pollOnce(20)).isOne();
        var retryTerminal = manager.get(
                flowRoot + "/instances/" + retryingParentId);
        assertOk(retryTerminal);
        assertThat(text(retryTerminal.body(), "/data/status"))
                .isEqualTo("APPROVED");
        assertThat(itemValues(retryTerminal.body().at(
                "/data/completionExecutions/0/subflowRuns"), "childStatus"))
                .containsExactly("REJECTED", "APPROVED_COMPLETED");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_flow_subflow_run "
                        + "WHERE system_id=? AND tenant_id=? AND execution_id=? "
                        + "AND result_applied_at IS NOT NULL",
                Integer.class,
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(retryExecutionId))).isEqualTo(2);

        var cycleCreated = manager.postWithCsrf(
                flowRoot + "/definitions",
                json(Map.of(
                        "name", "Subflow direct cycle HTTP journey",
                        "approverIds", List.of(managerMemberId)
                )),
                Map.of());
        assertCreated(cycleCreated);
        var cycleDefinitionId = text(
                cycleCreated.body(), "/data/definitionId");
        var cycleVersion = manager.postWithCsrf(
                flowRoot + "/definitions/" + cycleDefinitionId + ":publish",
                "{}",
                Map.of());
        assertOk(cycleVersion);
        assertOk(manager.putWithCsrf(
                flowRoot + "/definitions/" + cycleDefinitionId + "/draft",
                json(Map.of(
                        "name", "Subflow direct cycle HTTP journey",
                        "approverIds", List.of(managerMemberId),
                        "completionSteps", List.of(Map.of(
                                "code", "self",
                                "name", "Forbidden self reference",
                                "type", "SUBFLOW",
                                "subflow", Map.of(
                                        "definitionId", cycleDefinitionId,
                                        "version", cycleVersion.body().at(
                                                "/data/version").asInt()
                                )
                        ))
                )),
                Map.of()));
        var cycleCheck = manager.postWithCsrf(
                flowRoot + "/definitions/" + cycleDefinitionId
                        + "/draft:check",
                "{}",
                Map.of("Idempotency-Key", key()));
        assertOk(cycleCheck);
        assertThat(text(cycleCheck.body(), "/data/verdict"))
                .isEqualTo("BLOCKED");
        assertThat(itemValues(cycleCheck.body().at(
                "/data/issues"), "code")).contains("SUBFLOW_CYCLE");
        assertError(manager.postWithCsrf(
                        flowRoot + "/definitions/" + cycleDefinitionId
                                + ":publish",
                        "{}",
                        Map.of()),
                422,
                "FLOW_DRAFT_CHECK_BLOCKED");
    }

    private void exerciseParallelCompletionJoin(
            TestClient manager,
            String flowRoot,
            String systemId,
            String tenantId,
            String managerMemberId
    ) throws Exception {
        var childCreated = manager.postWithCsrf(
                flowRoot + "/definitions",
                json(Map.of(
                        "name", "Parallel completion child",
                        "approverIds", List.of(managerMemberId),
                        "approvalMode", "SEQUENTIAL",
                        "approverSource", Map.of("kind", "FIXED")
                )),
                Map.of());
        assertCreated(childCreated);
        var childDefinitionId = text(
                childCreated.body(), "/data/definitionId");
        var childPublished = manager.postWithCsrf(
                flowRoot + "/definitions/" + childDefinitionId + ":publish",
                "{}",
                Map.of());
        assertOk(childPublished);
        var childVersion = childPublished.body().at("/data/version").asInt();

        var completionSteps = List.of(
                Map.of(
                        "code", "parallel_export",
                        "name", "Parallel external export",
                        "type", "EXTERNAL_TASK",
                        "parallelGroup", "post_approval",
                        "externalTask", Map.of(
                                "topic", "records.parallel-export",
                                "leaseSeconds", 60,
                                "maxAttempts", 3,
                                "resultJsonLimitBytes", 8192
                        )
                ),
                Map.of(
                        "code", "parallel_child",
                        "name", "Parallel child approval",
                        "type", "SUBFLOW",
                        "parallelGroup", "post_approval",
                        "subflow", Map.of(
                                "definitionId", childDefinitionId,
                                "version", childVersion
                        )
                )
        );
        var parentCreated = manager.postWithCsrf(
                flowRoot + "/definitions",
                json(Map.of(
                        "name", "Parallel completion join",
                        "approverIds", List.of(managerMemberId),
                        "approvalMode", "SEQUENTIAL",
                        "approverSource", Map.of("kind", "FIXED"),
                        "completionSteps", completionSteps
                )),
                Map.of());
        assertCreated(parentCreated);
        var parentDefinitionId = text(
                parentCreated.body(), "/data/definitionId");
        assertThat(itemValues(parentCreated.body().at(
                "/data/completionSteps"), "parallelGroup"))
                .containsExactly("post_approval", "post_approval");

        var checked = manager.postWithCsrf(
                flowRoot + "/definitions/" + parentDefinitionId
                        + "/draft:check",
                "{}",
                Map.of("Idempotency-Key", key()));
        assertOk(checked);
        assertThat(text(checked.body(), "/data/verdict")).isEqualTo("READY");
        var published = manager.postWithCsrf(
                flowRoot + "/definitions/" + parentDefinitionId + ":publish",
                "{}",
                Map.of());
        assertOk(published);
        assertThat(itemValues(published.body().at(
                "/data/completionSteps"), "parallelGroup"))
                .containsExactly("post_approval", "post_approval");

        var successfulParent = manager.postWithCsrf(
                flowRoot + "/definitions/" + parentDefinitionId
                        + "/instances",
                json(Map.of("businessKey", "parallel-success-" + key())),
                Map.of("Idempotency-Key", key()));
        assertCreated(successfulParent);
        var successfulParentId = text(
                successfulParent.body(), "/data/instanceId");
        var activated = manager.postWithCsrf(
                flowRoot + "/instances/" + successfulParentId + ":approve",
                json(Map.of("comment", "Activate parallel completion")),
                Map.of("Idempotency-Key", key()));
        assertOk(activated);
        assertThat(text(activated.body(), "/data/status")).isEqualTo("PENDING");
        assertThat(activated.body().at(
                "/data/activeCompletionOrdinal").asInt()).isZero();
        assertThat(activated.body().at(
                "/data/activeCompletionOrdinals")).hasSize(2);
        assertThat(activated.body().at(
                "/data/activeCompletionOrdinals/0").asInt()).isZero();
        assertThat(activated.body().at(
                "/data/activeCompletionOrdinals/1").asInt()).isOne();
        assertThat(itemValues(activated.body().at(
                "/data/completionExecutions"), "status"))
                .containsExactly("AVAILABLE", "AVAILABLE");
        var externalExecutionId = text(activated.body(),
                "/data/completionExecutions/0/executionId");

        var claimed = manager.postWithCsrf(
                flowRoot + "/external-tasks/" + externalExecutionId + ":claim",
                "{}",
                Map.of("Idempotency-Key", key()));
        assertOk(claimed);
        var leaseToken = text(claimed.body(), "/data/leaseToken");
        var externalCompleted = manager.postWithCsrf(
                flowRoot + "/external-tasks/" + externalExecutionId
                        + ":complete",
                json(Map.of(
                        "leaseToken", leaseToken,
                        "result", Map.of("accepted", true)
                )),
                Map.of("Idempotency-Key", key()));
        assertOk(externalCompleted);
        assertThat(text(externalCompleted.body(), "/data/status"))
                .isEqualTo("SUCCEEDED");

        var waitingForChild = manager.get(
                flowRoot + "/instances/" + successfulParentId);
        assertOk(waitingForChild);
        assertThat(text(waitingForChild.body(), "/data/status"))
                .isEqualTo("PENDING");
        assertThat(waitingForChild.body().at(
                "/data/activeCompletionOrdinals")).hasSize(2);
        assertThat(itemValues(waitingForChild.body().at(
                "/data/completionExecutions"), "status"))
                .containsExactly("SUCCEEDED", "AVAILABLE");

        assertThat(subflowWorker.pollOnce(20)).isOne();
        var childRunning = manager.get(
                flowRoot + "/instances/" + successfulParentId);
        assertOk(childRunning);
        var successfulChildId = text(childRunning.body(),
                "/data/completionExecutions/1/subflowRuns/0/childInstanceId");
        assertThat(itemValues(childRunning.body().at(
                "/data/completionExecutions"), "status"))
                .containsExactly("SUCCEEDED", "RUNNING");
        assertOk(manager.postWithCsrf(
                flowRoot + "/instances/" + successfulChildId + ":approve",
                json(Map.of("comment", "Complete parallel child")),
                Map.of("Idempotency-Key", key())));
        assertThat(subflowWorker.pollOnce(20)).isOne();

        var successfulTerminal = manager.get(
                flowRoot + "/instances/" + successfulParentId);
        assertOk(successfulTerminal);
        assertThat(text(successfulTerminal.body(), "/data/status"))
                .isEqualTo("APPROVED");
        assertThat(text(successfulTerminal.body(), "/data/completionPhase"))
                .isEqualTo("COMPLETED");
        assertThat(absentOrNull(successfulTerminal.body().at(
                "/data/activeCompletionOrdinal"))).isTrue();
        assertThat(successfulTerminal.body().at(
                "/data/activeCompletionOrdinals")).isEmpty();
        var successfulHistory = manager.get(
                flowRoot + "/instances/" + successfulParentId + "/history");
        assertOk(successfulHistory);
        var joinedFacts = StreamSupport.stream(
                        successfulHistory.body().at(
                                "/data/events").spliterator(), false)
                .map(event -> event.path("completionExecution"))
                .filter(event -> !event.isMissingNode() && !event.isNull())
                .filter(event -> "STAGE_JOINED".equals(
                        event.path("event").asText()))
                .toList();
        assertThat(joinedFacts).hasSize(1);
        assertThat(joinedFacts.getFirst().path(
                "parallelGroup").asText()).isEqualTo("post_approval");
        assertThat(joinedFacts.getFirst().toString())
                .doesNotContain("leaseTokenHash", "stateVersion", "lock");

        var retryingParent = manager.postWithCsrf(
                flowRoot + "/definitions/" + parentDefinitionId
                        + "/instances",
                json(Map.of("businessKey", "parallel-retry-" + key())),
                Map.of("Idempotency-Key", key()));
        assertCreated(retryingParent);
        var retryingParentId = text(
                retryingParent.body(), "/data/instanceId");
        var retryActivated = manager.postWithCsrf(
                flowRoot + "/instances/" + retryingParentId + ":approve",
                json(Map.of("comment", "Exercise parallel retry")),
                Map.of("Idempotency-Key", key()));
        assertOk(retryActivated);
        var retryExternalExecutionId = text(retryActivated.body(),
                "/data/completionExecutions/0/executionId");
        var retrySubflowExecutionId = text(retryActivated.body(),
                "/data/completionExecutions/1/executionId");

        assertThat(subflowWorker.pollOnce(20)).isOne();
        var retryChildRunning = manager.get(
                flowRoot + "/instances/" + retryingParentId);
        assertOk(retryChildRunning);
        var rejectedChildId = text(retryChildRunning.body(),
                "/data/completionExecutions/1/subflowRuns/0/childInstanceId");
        assertOk(manager.postWithCsrf(
                flowRoot + "/instances/" + rejectedChildId + ":reject",
                json(Map.of("reason", "Reject one parallel member")),
                Map.of("Idempotency-Key", key())));
        assertThat(subflowWorker.pollOnce(20)).isOne();

        var retryExternalClaimed = manager.postWithCsrf(
                flowRoot + "/external-tasks/" + retryExternalExecutionId
                        + ":claim",
                "{}",
                Map.of("Idempotency-Key", key()));
        assertOk(retryExternalClaimed);
        assertOk(manager.postWithCsrf(
                flowRoot + "/external-tasks/" + retryExternalExecutionId
                        + ":complete",
                json(Map.of(
                        "leaseToken", text(
                                retryExternalClaimed.body(), "/data/leaseToken"),
                        "result", Map.of("accepted", true)
                )),
                Map.of("Idempotency-Key", key())));
        var blockedJoin = manager.get(
                flowRoot + "/instances/" + retryingParentId);
        assertOk(blockedJoin);
        assertThat(text(blockedJoin.body(), "/data/status"))
                .isEqualTo("PENDING");
        assertThat(itemValues(blockedJoin.body().at(
                "/data/completionExecutions"), "status"))
                .containsExactly("SUCCEEDED", "FAILED");
        assertThat(blockedJoin.body().at(
                "/data/activeCompletionOrdinals")).hasSize(2);

        var retried = manager.postWithCsrf(
                flowRoot + "/instances/" + retryingParentId
                        + "/completion-executions/" + retrySubflowExecutionId
                        + ":retry",
                "{}",
                Map.of("Idempotency-Key", key()));
        assertOk(retried);
        assertThat(text(retried.body(), "/data/status"))
                .isEqualTo("AVAILABLE");
        assertThat(subflowWorker.pollOnce(20)).isOne();
        var secondChildRunning = manager.get(
                flowRoot + "/instances/" + retryingParentId);
        assertOk(secondChildRunning);
        assertThat(secondChildRunning.body().at(
                "/data/completionExecutions/1/subflowRuns")).hasSize(2);
        var secondChildId = text(secondChildRunning.body(),
                "/data/completionExecutions/1/subflowRuns/1/childInstanceId");
        assertThat(secondChildId).isNotEqualTo(rejectedChildId);
        assertOk(manager.postWithCsrf(
                flowRoot + "/instances/" + secondChildId + ":approve",
                json(Map.of("comment", "Approve retried parallel member")),
                Map.of("Idempotency-Key", key())));
        assertThat(subflowWorker.pollOnce(20)).isOne();

        var retryTerminal = manager.get(
                flowRoot + "/instances/" + retryingParentId);
        assertOk(retryTerminal);
        assertThat(text(retryTerminal.body(), "/data/status"))
                .isEqualTo("APPROVED");
        assertThat(itemValues(retryTerminal.body().at(
                "/data/completionExecutions"), "status"))
                .containsExactly("SUCCEEDED", "SUCCEEDED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_flow_completion_execution "
                        + "WHERE system_id=? AND tenant_id=? "
                        + "AND instance_id IN (?,?) "
                        + "AND parallel_group='post_approval'",
                Integer.class,
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(successfulParentId),
                Long.parseLong(retryingParentId))).isEqualTo(4);
    }

    private void exerciseCompletionCompensation(
            TestClient manager,
            String flowRoot,
            String systemId,
            String tenantId,
            String managerMemberId
    ) throws Exception {
        var childCreated = manager.postWithCsrf(
                flowRoot + "/definitions",
                json(Map.of(
                        "name", "Compensation forward child",
                        "approverIds", List.of(managerMemberId),
                        "approvalMode", "SEQUENTIAL",
                        "approverSource", Map.of("kind", "FIXED")
                )),
                Map.of());
        assertCreated(childCreated);
        var childDefinitionId = text(
                childCreated.body(), "/data/definitionId");
        var childPublished = manager.postWithCsrf(
                flowRoot + "/definitions/" + childDefinitionId + ":publish",
                "{}",
                Map.of());
        assertOk(childPublished);
        var childVersion = childPublished.body().at("/data/version").asInt();

        var completionSteps = List.of(
                Map.of(
                        "code", "saga_export",
                        "name", "Export before compensation",
                        "type", "EXTERNAL_TASK",
                        "parallelGroup", "saga_group",
                        "externalTask", Map.of(
                                "topic", "records.saga-export",
                                "leaseSeconds", 60,
                                "maxAttempts", 3,
                                "resultJsonLimitBytes", 8192
                        ),
                        "compensation", Map.of(
                                "type", "EXTERNAL_TASK",
                                "externalTask", Map.of(
                                        "topic", "records.undo-saga-export",
                                        "leaseSeconds", 60,
                                        "maxAttempts", 1,
                                        "resultJsonLimitBytes", 8192
                                )
                        )
                ),
                Map.of(
                        "code", "saga_child",
                        "name", "Child that triggers compensation",
                        "type", "SUBFLOW",
                        "parallelGroup", "saga_group",
                        "subflow", Map.of(
                                "definitionId", childDefinitionId,
                                "version", childVersion
                        ),
                        "compensation", Map.of(
                                "type", "EXTERNAL_TASK",
                                "externalTask", Map.of(
                                        "topic", "records.undo-saga-child",
                                        "leaseSeconds", 60,
                                        "maxAttempts", 1,
                                        "resultJsonLimitBytes", 8192
                                )
                        )
                )
        );
        var parentCreated = manager.postWithCsrf(
                flowRoot + "/definitions",
                json(Map.of(
                        "name", "Durable completion compensation",
                        "approverIds", List.of(managerMemberId),
                        "approvalMode", "SEQUENTIAL",
                        "approverSource", Map.of("kind", "FIXED"),
                        "completionFailurePolicy", "COMPENSATE",
                        "completionSteps", completionSteps
                )),
                Map.of());
        assertCreated(parentCreated);
        var parentDefinitionId = text(
                parentCreated.body(), "/data/definitionId");
        assertThat(text(parentCreated.body(),
                "/data/completionFailurePolicy")).isEqualTo("COMPENSATE");
        assertThat(text(parentCreated.body(),
                "/data/completionSteps/0/compensation/type"))
                .isEqualTo("EXTERNAL_TASK");
        assertThat(text(parentCreated.body(),
                "/data/completionSteps/0/compensation/externalTask/topic"))
                .isEqualTo("records.undo-saga-export");

        var checked = manager.postWithCsrf(
                flowRoot + "/definitions/" + parentDefinitionId
                        + "/draft:check",
                "{}",
                Map.of("Idempotency-Key", key()));
        assertOk(checked);
        assertThat(text(checked.body(), "/data/verdict")).isEqualTo("READY");
        var published = manager.postWithCsrf(
                flowRoot + "/definitions/" + parentDefinitionId + ":publish",
                "{}",
                Map.of());
        assertOk(published);
        assertThat(text(published.body(),
                "/data/completionFailurePolicy")).isEqualTo("COMPENSATE");

        var parentStarted = manager.postWithCsrf(
                flowRoot + "/definitions/" + parentDefinitionId
                        + "/instances",
                json(Map.of("businessKey", "compensation-" + key())),
                Map.of("Idempotency-Key", key()));
        assertCreated(parentStarted);
        var parentInstanceId = text(
                parentStarted.body(), "/data/instanceId");
        var activated = manager.postWithCsrf(
                flowRoot + "/instances/" + parentInstanceId + ":approve",
                json(Map.of("comment", "Start compensated completion")),
                Map.of("Idempotency-Key", key()));
        assertOk(activated);
        assertThat(itemValues(activated.body().at(
                "/data/completionExecutions"), "status"))
                .containsExactly("AVAILABLE", "AVAILABLE");
        var externalExecutionId = text(activated.body(),
                "/data/completionExecutions/0/executionId");
        var subflowExecutionId = text(activated.body(),
                "/data/completionExecutions/1/executionId");

        assertThat(subflowWorker.pollOnce(20)).isOne();
        var childRunning = manager.get(
                flowRoot + "/instances/" + parentInstanceId);
        assertOk(childRunning);
        var childInstanceId = text(childRunning.body(),
                "/data/completionExecutions/1/subflowRuns/0/childInstanceId");

        var forwardClaim = manager.postWithCsrf(
                flowRoot + "/external-tasks/" + externalExecutionId + ":claim",
                "{}",
                Map.of("Idempotency-Key", key()));
        assertOk(forwardClaim);
        var forwardLease = text(forwardClaim.body(), "/data/leaseToken");
        var forwardComplete = manager.postWithCsrf(
                flowRoot + "/external-tasks/" + externalExecutionId
                        + ":complete",
                json(Map.of(
                        "leaseToken", forwardLease,
                        "result", Map.of("exported", true)
                )),
                Map.of("Idempotency-Key", key()));
        assertOk(forwardComplete);
        assertThat(text(forwardComplete.body(), "/data/status"))
                .isEqualTo("SUCCEEDED");

        assertOk(manager.postWithCsrf(
                flowRoot + "/instances/" + childInstanceId + ":reject",
                json(Map.of("reason", "Trigger reverse compensation")),
                Map.of("Idempotency-Key", key())));
        assertThat(subflowWorker.pollOnce(20)).isOne();

        var compensating = manager.get(
                flowRoot + "/instances/" + parentInstanceId);
        assertOk(compensating);
        assertThat(text(compensating.body(), "/data/status"))
                .isEqualTo("PENDING");
        assertThat(text(compensating.body(), "/data/completionPhase"))
                .isEqualTo("COMPENSATING");
        assertThat(text(compensating.body(),
                "/data/completionFailurePolicy")).isEqualTo("COMPENSATE");
        assertThat(itemValues(compensating.body().at(
                "/data/completionExecutions"), "status"))
                .containsExactly("SUCCEEDED", "FAILED");
        assertThat(compensating.body().at(
                "/data/compensationExecutions")).hasSize(1);
        assertThat(text(compensating.body(),
                "/data/compensationExecutions/0/status"))
                .isEqualTo("AVAILABLE");
        assertThat(compensating.body().at(
                "/data/compensationExecutions/0/originalOrdinal").asInt())
                .isZero();
        var compensationExecutionId = text(compensating.body(),
                "/data/compensationExecutions/0/compensationExecutionId");
        assertThat(compensating.body().at(
                "/data/compensationExecutions/0").toString())
                .doesNotContain(
                        "leaseTokenHash", "stateVersion", "launchKey", "lock");

        assertError(manager.postWithCsrf(
                        flowRoot + "/instances/" + parentInstanceId
                                + "/completion-executions/"
                                + subflowExecutionId + ":retry",
                        "{}",
                        Map.of("Idempotency-Key", key())),
                409,
                "FLOW_COMPLETION_STATE_CONFLICT");

        var queued = manager.get(
                flowRoot + "/compensation-external-tasks"
                        + "?topic=records.undo-saga-export&page=1&size=100");
        assertOk(queued);
        assertThat(queued.body().at("/data/items")).hasSize(1);
        assertThat(text(queued.body(),
                "/data/items/0/compensationExecutionId"))
                .isEqualTo(compensationExecutionId);

        var compensationClaim = manager.postWithCsrf(
                flowRoot + "/compensation-external-tasks/"
                        + compensationExecutionId + ":claim",
                "{}",
                Map.of("Idempotency-Key", key()));
        assertOk(compensationClaim);
        var compensationLease = text(
                compensationClaim.body(), "/data/leaseToken");
        var compensationFailed = manager.postWithCsrf(
                flowRoot + "/compensation-external-tasks/"
                        + compensationExecutionId + ":fail",
                json(Map.of(
                        "leaseToken", compensationLease,
                        "code", "UNDO_REJECTED",
                        "message", "Undo failed before administrator retry"
                )),
                Map.of("Idempotency-Key", key()));
        assertOk(compensationFailed);
        assertThat(text(compensationFailed.body(), "/data/status"))
                .isEqualTo("FAILED");

        var stillCompensating = manager.get(
                flowRoot + "/instances/" + parentInstanceId);
        assertOk(stillCompensating);
        assertThat(text(stillCompensating.body(), "/data/status"))
                .isEqualTo("PENDING");
        assertThat(text(stillCompensating.body(), "/data/completionPhase"))
                .isEqualTo("COMPENSATING");

        var retried = manager.postWithCsrf(
                flowRoot + "/instances/" + parentInstanceId
                        + "/compensation-executions/"
                        + compensationExecutionId + ":retry",
                "{}",
                Map.of("Idempotency-Key", key()));
        assertOk(retried);
        assertThat(text(retried.body(), "/data/status"))
                .isEqualTo("AVAILABLE");
        assertThat(retried.body().at("/data/attemptCount").asInt()).isOne();

        var retryClaim = manager.postWithCsrf(
                flowRoot + "/compensation-external-tasks/"
                        + compensationExecutionId + ":claim",
                "{}",
                Map.of("Idempotency-Key", key()));
        assertOk(retryClaim);
        var retryLease = text(retryClaim.body(), "/data/leaseToken");
        var completed = manager.postWithCsrf(
                flowRoot + "/compensation-external-tasks/"
                        + compensationExecutionId + ":complete",
                json(Map.of(
                        "leaseToken", retryLease,
                        "result", Map.of("undone", true)
                )),
                Map.of("Idempotency-Key", key()));
        assertOk(completed);
        assertThat(text(completed.body(), "/data/status"))
                .isEqualTo("SUCCEEDED");
        assertThat(completed.body().at("/data/attemptCount").asInt())
                .isEqualTo(2);

        var terminal = manager.get(
                flowRoot + "/instances/" + parentInstanceId);
        assertOk(terminal);
        assertThat(text(terminal.body(), "/data/status"))
                .isEqualTo("TERMINATED");
        assertThat(text(terminal.body(), "/data/completionPhase"))
                .isEqualTo("COMPLETED");
        assertThat(text(terminal.body(),
                "/data/compensationExecutions/0/status"))
                .isEqualTo("SUCCEEDED");

        var history = manager.get(
                flowRoot + "/instances/" + parentInstanceId + "/history");
        assertOk(history);
        var compensatedFacts = StreamSupport.stream(
                        history.body().at("/data/events").spliterator(), false)
                .filter(event -> "COMPLETION_COMPENSATED".equals(
                        event.path("type").asText()))
                .toList();
        assertThat(compensatedFacts).hasSize(1);
        assertThat(history.body().toString()).doesNotContain(
                compensationLease, retryLease, "leaseTokenHash",
                "stateVersion", "launchKey");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_flow_completion_compensation "
                        + "WHERE system_id=? AND tenant_id=? AND instance_id=? "
                        + "AND original_execution_id=? "
                        + "AND original_ordinal=0 AND reverse_ordinal=0 "
                        + "AND status='SUCCEEDED' AND attempt_count=2",
                Integer.class,
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(parentInstanceId),
                Long.parseLong(externalExecutionId))).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_flow_compensation_attempt "
                        + "WHERE system_id=? AND tenant_id=? "
                        + "AND compensation_id=? "
                        + "AND event_type IN ('FAILED','RETRIED','SUCCEEDED')",
                Integer.class,
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(compensationExecutionId))).isEqualTo(3);
    }

    private void exercisePeriodicTrigger(
            TestClient client,
            String flowRoot,
            String systemId,
            String tenantId,
            String requesterId,
            String approverId
    ) throws Exception {
        var startAt = Instant.now()
                .minus(Duration.ofMinutes(5))
                .truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        var created = client.postWithCsrf(
                flowRoot + "/definitions",
                json(Map.of(
                        "name", "Periodic approval HTTP journey",
                        "approverId", approverId,
                        "triggerBinding", Map.of(
                                "event", "PERIODIC",
                                "priority", 0,
                                "exclusive", true,
                                "conditions", List.of(),
                                "startAt", startAt.toString(),
                                "intervalMinutes", 60
                        )
                )),
                Map.of());
        assertCreated(created);
        var definitionId = text(created.body(), "/data/definitionId");
        assertThat(text(created.body(), "/data/triggerBinding/event")).isEqualTo("PERIODIC");
        assertThat(absentOrNull(created.body().at("/data/triggerBinding/moduleCode"))).isTrue();
        assertThat(text(created.body(), "/data/triggerBinding/requesterMemberId"))
                .isEqualTo(requesterId);

        var published = client.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + ":publish",
                "{}",
                Map.of());
        assertOk(published);
        assertThat(published.body().at("/data/version").asInt()).isOne();
        assertThat(text(published.body(), "/data/triggerBinding/startAt"))
                .isEqualTo(startAt.toString());

        var projected = client.get(
                flowRoot + "/definitions/" + definitionId + "/periodic-schedule");
        assertOk(projected);
        assertThat(projected.body().at("/data/definitionVersion").asInt()).isOne();
        assertThat(text(projected.body(), "/data/requesterId")).isEqualTo(requesterId);
        assertThat(text(projected.body(), "/data/status")).isEqualTo("ACTIVE");
        assertThat(text(projected.body(), "/data/nextFireAt")).isEqualTo(startAt.toString());
        assertThat(absentOrNull(projected.body().at("/data/lastInstanceId"))).isTrue();

        assertThat(periodicScheduleWorker.pollOnce(20)).isOne();
        var fired = client.get(
                flowRoot + "/definitions/" + definitionId + "/periodic-schedule");
        assertOk(fired);
        assertThat(text(fired.body(), "/data/lastScheduledAt")).isEqualTo(startAt.toString());
        var instanceId = text(fired.body(), "/data/lastInstanceId");
        assertThat(instanceId).isNotBlank();
        assertThat(Instant.parse(text(fired.body(), "/data/nextFireAt")))
                .isAfter(Instant.now());

        var instance = client.get(flowRoot + "/instances/" + instanceId);
        assertOk(instance);
        assertThat(text(instance.body(), "/data/definitionId")).isEqualTo(definitionId);
        assertThat(instance.body().at("/data/definitionVersion").asInt()).isOne();
        assertThat(text(instance.body(), "/data/requesterId")).isEqualTo(requesterId);
        assertThat(text(instance.body(), "/data/approverId")).isEqualTo(approverId);
        assertThat(text(instance.body(), "/data/businessKey"))
                .startsWith("schedule:" + definitionId + ":v1:");
        assertThat(absentOrNull(instance.body().at("/data/recordBinding"))).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_flow_instance "
                        + "WHERE system_id=? AND tenant_id=? AND definition_id=? "
                        + "AND definition_version=1 AND requester_id=? "
                        + "AND business_key LIKE 'schedule:%'",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(definitionId),
                Long.parseLong(requesterId))).isOne();
        assertThat(periodicScheduleWorker.pollOnce(20)).isZero();

        var replacementStart = Instant.now()
                .plus(Duration.ofHours(1))
                .truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        var revised = client.putWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/draft",
                json(Map.of(
                        "name", "Periodic approval HTTP journey v2",
                        "approverId", approverId,
                        "triggerBinding", Map.of(
                                "event", "PERIODIC",
                                "priority", 0,
                                "exclusive", true,
                                "conditions", List.of(),
                                "startAt", replacementStart.toString(),
                                "intervalMinutes", 30
                        )
                )),
                Map.of());
        assertOk(revised);
        var replacement = client.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + ":publish",
                "{}",
                Map.of());
        assertOk(replacement);
        assertThat(replacement.body().at("/data/version").asInt()).isEqualTo(2);

        var replacedState = client.get(
                flowRoot + "/definitions/" + definitionId + "/periodic-schedule");
        assertOk(replacedState);
        assertThat(replacedState.body().at("/data/definitionVersion").asInt()).isEqualTo(2);
        assertThat(replacedState.body().at("/data/intervalMinutes").asInt()).isEqualTo(30);
        assertThat(text(replacedState.body(), "/data/nextFireAt"))
                .isEqualTo(replacementStart.toString());
        assertThat(absentOrNull(replacedState.body().at("/data/lastScheduledAt"))).isTrue();
        assertThat(absentOrNull(replacedState.body().at("/data/lastInstanceId"))).isTrue();
        assertThat(periodicScheduleWorker.pollOnce(20)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_flow_instance "
                        + "WHERE system_id=? AND tenant_id=? AND definition_id=?",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(definitionId))).isOne();

        assertOk(client.putWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/draft",
                json(Map.of(
                        "name", "Periodic approval HTTP journey disabled",
                        "approverId", approverId
                )),
                Map.of()));
        var disabled = client.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + ":publish",
                "{}",
                Map.of());
        assertOk(disabled);
        assertThat(disabled.body().at("/data/version").asInt()).isEqualTo(3);
        assertThat(absentOrNull(disabled.body().at("/data/triggerBinding"))).isTrue();
        assertError(client.get(
                        flowRoot + "/definitions/" + definitionId + "/periodic-schedule"),
                404,
                "FLOW_VERSION_NOT_FOUND");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_flow_periodic_schedule "
                        + "WHERE system_id=? AND tenant_id=? AND definition_id=?",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(definitionId))).isZero();
    }

    private void exerciseExpandedRecordEvents(
            TestClient client,
            String flowRoot,
            String runtimeRoot,
            String systemId,
            String tenantId,
            String memberId,
            FlowRecordSchema schema,
            String definitionId,
            String startOnlyMemberId,
            TestClient startOnlyClient,
            String startableCatalogPath,
            String hiddenDraftId,
            String manualReplayRecordId,
            String manualRollbackRecordId,
            String recordDefinitionId,
            String firstTenantId
    ) throws Exception {
        assertError(client.postWithCsrf(
                        flowRoot + "/definitions",
                        json(Map.of(
                                "name", "Invalid non-activation status mapping",
                                "approverId", memberId,
                                "triggerBinding", Map.of(
                                        "moduleCode", FLOW_RECORD_MODULE_CODE,
                                        "event", "RECORD_UPDATED",
                                        "priority", 100,
                                        "exclusive", false
                                ),
                                "recordStatusMapping", statusMapping(schema)
                        )),
                        Map.of()),
                422,
                "FLOW_RECORD_STATUS_MAPPING_INVALID");

        var createdDefinitionId = createAndPublishRecordEventDefinition(
                client, flowRoot, memberId, "Created draft approval",
                "RECORD_CREATED", "event-create");
        var updatedDefinitionId = createAndPublishRecordEventDefinition(
                client, flowRoot, memberId, "Explicit update approval",
                "RECORD_UPDATED", "event-update");
        var deletedDefinitionId = createAndPublishRecordEventDefinition(
                client, flowRoot, memberId, "Logical delete approval",
                "RECORD_DELETED", "event-delete");
        var statusDefinitionId = createAndPublishRecordEventDefinition(
                client, flowRoot, memberId, "Lifecycle status approval",
                "RECORD_STATUS_CHANGED", "event-status");

        var createdDraft = client.postWithCsrf(
                runtimeRoot + "/records",
                json(Map.of(
                        "schemaVersionId", schema.versionId(),
                        "title", "Created event draft",
                        "values", Map.of("route", "event-create")
                )),
                Map.of("Idempotency-Key", key()));
        assertCreated(createdDraft);
        var createdRecordId = text(createdDraft.body(), "/data/recordId");
        assertThat(text(createdDraft.body(), "/data/status")).isEqualTo("DRAFT");
        assertThat(createdDraft.body().at("/data/version").asLong()).isZero();
        assertAutomaticRecordEvent(
                client,
                flowRoot,
                systemId,
                tenantId,
                createdRecordId,
                createdDefinitionId,
                0,
                "RECORD_CREATED");

        var updateDraft = client.postWithCsrf(
                runtimeRoot + "/records",
                json(Map.of(
                        "schemaVersionId", schema.versionId(),
                        "title", "Update event source",
                        "values", Map.of("route", "event-update")
                )),
                Map.of("Idempotency-Key", key()));
        assertCreated(updateDraft);
        var updateRecordId = text(updateDraft.body(), "/data/recordId");
        var updateBody = json(Map.of(
                "schemaVersionId", schema.versionId(),
                "title", "Update event persisted",
                "expectedVersion", 0,
                "values", Map.of("route", "event-update")
        ));
        var updateKey = key();
        var updatedDraft = client.putWithCsrf(
                runtimeRoot + "/records/" + updateRecordId,
                updateBody,
                Map.of("Idempotency-Key", updateKey));
        assertOk(updatedDraft);
        assertThat(text(updatedDraft.body(), "/data/status")).isEqualTo("DRAFT");
        assertThat(updatedDraft.body().at("/data/version").asLong()).isOne();
        var updateReplay = client.putWithCsrf(
                runtimeRoot + "/records/" + updateRecordId,
                updateBody,
                Map.of("Idempotency-Key", updateKey));
        assertOk(updateReplay);
        assertThat(updateReplay.body().at("/data")).isEqualTo(updatedDraft.body().at("/data"));
        assertAutomaticRecordEvent(
                client,
                flowRoot,
                systemId,
                tenantId,
                updateRecordId,
                updatedDefinitionId,
                1,
                "RECORD_UPDATED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_flow_trigger_dispatch "
                        + "WHERE system_id=? AND tenant_id=? AND event_key=? "
                        + "AND instance_id IS NULL",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                recordEventKey(systemId, tenantId, updateRecordId, 0, "RECORD_CREATED")))
                .isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_record_history "
                        + "WHERE system_id=? AND tenant_id=? AND record_id=? "
                        + "AND record_version=1 AND action='RECORD_UPDATED'",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(updateRecordId))).isOne();

        var deleteDraft = client.postWithCsrf(
                runtimeRoot + "/records",
                json(Map.of(
                        "schemaVersionId", schema.versionId(),
                        "title", "Delete event source",
                        "values", Map.of("route", "event-delete")
                )),
                Map.of("Idempotency-Key", key()));
        assertCreated(deleteDraft);
        var deleteRecordId = text(deleteDraft.body(), "/data/recordId");
        var trashed = client.postWithCsrf(
                runtimeRoot + "/records/" + deleteRecordId + ":trash",
                json(Map.of("expectedVersion", 0)),
                Map.of("Idempotency-Key", key()));
        assertOk(trashed);
        assertThat(text(trashed.body(), "/data/status")).isEqualTo("TRASHED");
        assertThat(trashed.body().at("/data/version").asLong()).isOne();
        assertAutomaticRecordEvent(
                client,
                flowRoot,
                systemId,
                tenantId,
                deleteRecordId,
                deletedDefinitionId,
                1,
                "RECORD_DELETED");

        var statusDraft = client.postWithCsrf(
                runtimeRoot + "/records",
                json(Map.of(
                        "schemaVersionId", schema.versionId(),
                        "title", "Status event source",
                        "values", Map.of("route", "event-status")
                )),
                Map.of("Idempotency-Key", key()));
        assertCreated(statusDraft);
        var statusRecordId = text(statusDraft.body(), "/data/recordId");
        assertOk(client.postWithCsrf(
                runtimeRoot + "/records/" + statusRecordId + ":activate",
                json(Map.of("expectedVersion", 0)),
                Map.of("Idempotency-Key", key())));
        var archived = client.postWithCsrf(
                runtimeRoot + "/records/" + statusRecordId + ":archive",
                json(Map.of("expectedVersion", 1)),
                Map.of("Idempotency-Key", key()));
        assertOk(archived);
        assertThat(text(archived.body(), "/data/status")).isEqualTo("ARCHIVED");
        assertThat(archived.body().at("/data/version").asLong()).isEqualTo(2);
        assertAutomaticRecordEvent(
                client,
                flowRoot,
                systemId,
                tenantId,
                statusRecordId,
                statusDefinitionId,
                2,
                "RECORD_STATUS_CHANGED");

        var rollbackDraft = client.postWithCsrf(
                runtimeRoot + "/records",
                json(Map.of(
                        "schemaVersionId", schema.versionId(),
                        "title", "Rollback event source",
                        "values", Map.of("route", "rollback-source")
                )),
                Map.of("Idempotency-Key", key()));
        assertCreated(rollbackDraft);
        var rollbackRecordId = text(rollbackDraft.body(), "/data/recordId");
        jdbcTemplate.execute("""
                CREATE TRIGGER test_fail_updated_event_projection
                BEFORE INSERT ON un_module_record_flow_state
                FOR EACH ROW
                SIGNAL SQLSTATE '23000'
                    SET MYSQL_ERRNO = 1062,
                        MESSAGE_TEXT = 'forced updated event projection conflict'
                """);
        try {
            assertError(client.putWithCsrf(
                            runtimeRoot + "/records/" + rollbackRecordId,
                            json(Map.of(
                                    "schemaVersionId", schema.versionId(),
                                    "title", "Rollback event must not persist",
                                    "expectedVersion", 0,
                                    "values", Map.of("route", "event-update")
                            )),
                            Map.of("Idempotency-Key", key())),
                    409,
                    "RECORD_FLOW_STATE_CONFLICT");
        } finally {
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS test_fail_updated_event_projection");
        }
        var rolledBackRecord = client.get(runtimeRoot + "/records/" + rollbackRecordId);
        assertOk(rolledBackRecord);
        assertThat(text(rolledBackRecord.body(), "/data/status")).isEqualTo("DRAFT");
        assertThat(text(rolledBackRecord.body(), "/data/title")).isEqualTo("Rollback event source");
        assertThat(rolledBackRecord.body().at("/data/version").asLong()).isZero();
        assertThat(fieldValue(rolledBackRecord.body(), "route")).isEqualTo("rollback-source");
        var rollbackEventKey = recordEventKey(
                systemId, tenantId, rollbackRecordId, 1, "RECORD_UPDATED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_flow_instance "
                        + "WHERE system_id=? AND tenant_id=? AND record_id=?",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(rollbackRecordId))).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_flow_trigger_dispatch "
                        + "WHERE system_id=? AND tenant_id=? AND event_key=?",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                rollbackEventKey)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_record_flow_state "
                        + "WHERE system_id=? AND tenant_id=? AND record_id=?",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(rollbackRecordId))).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_record_history "
                        + "WHERE system_id=? AND tenant_id=? AND record_id=? "
                        + "AND action='RECORD_UPDATED'",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(rollbackRecordId))).isZero();

        var latestManualStartDraft = client.putWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/draft",
                json(Map.of(
                        "name", "Expense approval latest manual version",
                        "approverIds", List.of(startOnlyMemberId)
                )),
                Map.of());
        assertOk(latestManualStartDraft);
        var latestManualStartDefinition = client.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + ":publish",
                "{}",
                Map.of());
        assertOk(latestManualStartDefinition);
        assertThat(latestManualStartDefinition.body().at("/data/version").asInt())
                .isEqualTo(2);

        assertOk(startOnlyClient.postWithCsrf("/api/v1/auth/refresh", "{}", Map.of()));
        var finalStartOnlyCatalog = startOnlyClient.get(startableCatalogPath);
        assertOk(finalStartOnlyCatalog);
        var latestCatalogItems = StreamSupport.stream(
                        finalStartOnlyCatalog.body().at("/data/items").spliterator(), false)
                .filter(item -> definitionId.equals(item.path("definitionId").asText()))
                .toList();
        assertThat(latestCatalogItems).hasSize(1);
        assertThat(latestCatalogItems.getFirst().path("latestVersion").asInt())
                .isEqualTo(2);
        assertThat(latestCatalogItems.getFirst().path("name").asText())
                .isEqualTo("Expense approval latest manual version");
        assertThat(latestCatalogItems.getFirst().path("publishedAt").asText())
                .isNotBlank();
        assertThat(itemValues(
                finalStartOnlyCatalog.body().at("/data/items"), "definitionId"))
                .doesNotContain(hiddenDraftId);

        var startOnlyBusinessKey = "start-only-latest-"
                + Long.toUnsignedString(System.nanoTime(), 36);
        var startOnlyKey = key();
        var startOnlyStarted = startOnlyClient.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/instances",
                json(Map.of("businessKey", startOnlyBusinessKey)),
                Map.of("Idempotency-Key", startOnlyKey));
        assertCreated(startOnlyStarted);
        assertThat(text(startOnlyStarted.body(), "/data/definitionId"))
                .isEqualTo(definitionId);
        assertThat(startOnlyStarted.body().at("/data/definitionVersion").asInt())
                .isEqualTo(2);
        assertThat(text(startOnlyStarted.body(), "/data/requesterId"))
                .isEqualTo(startOnlyMemberId);
        assertThat(text(startOnlyStarted.body(), "/data/approverId"))
                .isEqualTo(startOnlyMemberId);
        assertThat(text(startOnlyStarted.body(), "/data/businessKey"))
                .isEqualTo(startOnlyBusinessKey);
        assertError(startOnlyClient.get(
                        flowRoot + "/instances/"
                                + text(startOnlyStarted.body(), "/data/instanceId")),
                403,
                "PERMISSION_DENIED");

        var replayBusinessKey = "manual-start-replay-" + manualReplayRecordId;
        var replayStartBody = json(Map.of(
                "definitionVersion", 1,
                "businessKey", replayBusinessKey,
                "recordBinding", Map.of(
                        "moduleCode", FLOW_RECORD_MODULE_CODE,
                        "recordId", manualReplayRecordId
                )
        ));
        var replayKey = key();
        var manualStarted = client.postWithCsrf(
                flowRoot + "/definitions/" + recordDefinitionId + "/instances",
                replayStartBody,
                Map.of("Idempotency-Key", replayKey));
        assertCreated(manualStarted);
        var manualStartedInstanceId = text(manualStarted.body(), "/data/instanceId");
        assertThat(text(manualStarted.body(), "/data/recordBinding/moduleCode"))
                .isEqualTo(FLOW_RECORD_MODULE_CODE);
        assertThat(text(manualStarted.body(), "/data/recordBinding/recordId"))
                .isEqualTo(manualReplayRecordId);

        var replayedManualStart = client.postWithCsrf(
                flowRoot + "/definitions/" + recordDefinitionId + "/instances",
                replayStartBody,
                Map.of("Idempotency-Key", replayKey));
        assertCreated(replayedManualStart);
        assertThat(replayedManualStart.body().at("/data"))
                .isEqualTo(manualStarted.body().at("/data"));
        assertError(client.postWithCsrf(
                        flowRoot + "/definitions/" + recordDefinitionId + "/instances",
                        json(Map.of(
                                "definitionVersion", 1,
                                "businessKey", replayBusinessKey + "-changed",
                                "recordBinding", Map.of(
                                        "moduleCode", FLOW_RECORD_MODULE_CODE,
                                        "recordId", manualReplayRecordId
                                )
                        )),
                        Map.of("Idempotency-Key", replayKey)),
                409,
                "IDEMPOTENCY_CONFLICT");

        var replayProjection = client.get(
                runtimeRoot + "/records/" + manualReplayRecordId + "/flow-state");
        assertOk(replayProjection);
        assertThat(text(replayProjection.body(), "/data/instanceId"))
                .isEqualTo(manualStartedInstanceId);
        assertThat(text(replayProjection.body(), "/data/status")).isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_flow_instance "
                        + "WHERE system_id=? AND tenant_id=? AND business_key=? "
                        + "AND instance_id=? AND definition_id=? AND definition_version=1",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(firstTenantId),
                replayBusinessKey,
                Long.parseLong(manualStartedInstanceId),
                Long.parseLong(recordDefinitionId))).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_flow_history_event "
                        + "WHERE system_id=? AND tenant_id=? AND instance_id=? "
                        + "AND event_sequence=1 AND event_type='STARTED' "
                        + "AND actor_id=? AND from_status IS NULL AND to_status='PENDING'",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(firstTenantId),
                Long.parseLong(manualStartedInstanceId),
                Long.parseLong(memberId))).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_record_flow_state "
                        + "WHERE system_id=? AND tenant_id=? AND record_id=? "
                        + "AND instance_id=? AND status='PENDING' AND version=0",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(firstTenantId),
                Long.parseLong(manualReplayRecordId),
                Long.parseLong(manualStartedInstanceId))).isOne();
        var replayScopeKey = "%s:%s:%s:%s:start".formatted(
                systemId, firstTenantId, memberId, recordDefinitionId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_sys_idempotency "
                        + "WHERE scope_type='FLOW_INSTANCE' AND scope_key=? "
                        + "AND idempotency_key=? AND status='COMPLETED' "
                        + "AND response_http_status=201 AND response_code='OK'",
                Long.class,
                replayScopeKey,
                replayKey)).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_audit_operation "
                        + "WHERE system_id=? AND tenant_id=? "
                        + "AND operation_type='FLOW_INSTANCE_STARTED' "
                        + "AND aggregate_type='FLOW_INSTANCE' AND aggregate_id=? "
                        + "AND source_type='WEB' AND result='SUCCESS' "
                        + "AND actor_account_id IS NOT NULL AND after_json IS NOT NULL "
                        + "AND request_id<>'' AND trace_id<>''",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(firstTenantId),
                manualStartedInstanceId)).isOne();
        var tasksAfterReplay = client.get(
                flowRoot + "/tasks?status=PENDING&page=1&size=100");
        assertOk(tasksAfterReplay);
        assertThat(StreamSupport.stream(
                        tasksAfterReplay.body().at("/data/items").spliterator(), false)
                .filter(item -> manualStartedInstanceId.equals(
                        item.path("instanceId").asText()))
                .toList()).hasSize(1);

        var rollbackStartBusinessKey = "manual-start-atomic-rollback-"
                + manualRollbackRecordId;
        var rollbackStartBody = json(Map.of(
                "definitionVersion", 1,
                "businessKey", rollbackStartBusinessKey,
                "recordBinding", Map.of(
                        "moduleCode", FLOW_RECORD_MODULE_CODE,
                        "recordId", manualRollbackRecordId
                )
        ));
        var rollbackStartKey = key();
        var successAuditsBeforeRollback = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_audit_operation "
                        + "WHERE system_id=? AND tenant_id=? "
                        + "AND operation_type='FLOW_INSTANCE_STARTED' "
                        + "AND result='SUCCESS'",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(firstTenantId));
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS test_fail_manual_start_projection");
        jdbcTemplate.execute("""
                CREATE TRIGGER test_fail_manual_start_projection
                BEFORE INSERT ON un_module_record_flow_state
                FOR EACH ROW
                SIGNAL SQLSTATE '23000'
                    SET MESSAGE_TEXT='forced manual-start projection failure'
                """);
        try {
            assertError(client.postWithCsrf(
                            flowRoot + "/definitions/" + recordDefinitionId + "/instances",
                            rollbackStartBody,
                            Map.of("Idempotency-Key", rollbackStartKey)),
                    409,
                    "RECORD_FLOW_STATE_CONFLICT");
        } finally {
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS test_fail_manual_start_projection");
        }

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_flow_instance "
                        + "WHERE system_id=? AND tenant_id=? AND business_key=?",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(firstTenantId),
                rollbackStartBusinessKey)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_flow_history_event h "
                        + "JOIN un_flow_instance i "
                        + "ON i.system_id=h.system_id AND i.tenant_id=h.tenant_id "
                        + "AND i.instance_id=h.instance_id "
                        + "WHERE i.system_id=? AND i.tenant_id=? AND i.business_key=?",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(firstTenantId),
                rollbackStartBusinessKey)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_record_flow_state "
                        + "WHERE system_id=? AND tenant_id=? AND record_id=?",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(firstTenantId),
                Long.parseLong(manualRollbackRecordId))).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_sys_idempotency "
                        + "WHERE scope_type='FLOW_INSTANCE' AND scope_key=? "
                        + "AND idempotency_key=?",
                Long.class,
                replayScopeKey,
                rollbackStartKey)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_audit_operation "
                        + "WHERE system_id=? AND tenant_id=? "
                        + "AND operation_type='FLOW_INSTANCE_STARTED' "
                        + "AND result='SUCCESS'",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(firstTenantId)))
                .isEqualTo(successAuditsBeforeRollback);

        var retriedManualStart = client.postWithCsrf(
                flowRoot + "/definitions/" + recordDefinitionId + "/instances",
                rollbackStartBody,
                Map.of("Idempotency-Key", rollbackStartKey));
        assertCreated(retriedManualStart);
        var retriedManualStartInstanceId = text(
                retriedManualStart.body(), "/data/instanceId");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_flow_instance "
                        + "WHERE system_id=? AND tenant_id=? AND business_key=? "
                        + "AND instance_id=?",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(firstTenantId),
                rollbackStartBusinessKey,
                Long.parseLong(retriedManualStartInstanceId))).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_flow_history_event "
                        + "WHERE system_id=? AND tenant_id=? AND instance_id=? "
                        + "AND event_sequence=1 AND event_type='STARTED'",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(firstTenantId),
                Long.parseLong(retriedManualStartInstanceId))).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_record_flow_state "
                        + "WHERE system_id=? AND tenant_id=? AND record_id=? "
                        + "AND instance_id=? AND status='PENDING'",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(firstTenantId),
                Long.parseLong(manualRollbackRecordId),
                Long.parseLong(retriedManualStartInstanceId))).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_sys_idempotency "
                        + "WHERE scope_type='FLOW_INSTANCE' AND scope_key=? "
                        + "AND idempotency_key=? AND status='COMPLETED'",
                Long.class,
                replayScopeKey,
                rollbackStartKey)).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_audit_operation "
                        + "WHERE system_id=? AND tenant_id=? "
                        + "AND operation_type='FLOW_INSTANCE_STARTED' "
                        + "AND aggregate_type='FLOW_INSTANCE' AND aggregate_id=? "
                        + "AND source_type='WEB' AND result='SUCCESS'",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(firstTenantId),
                retriedManualStartInstanceId)).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_audit_operation "
                        + "WHERE system_id=? AND tenant_id=? "
                        + "AND operation_type='FLOW_INSTANCE_STARTED' "
                        + "AND result='SUCCESS'",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(firstTenantId)))
                .isEqualTo(successAuditsBeforeRollback + 1);
    }

    private String createAndPublishRecordEventDefinition(
            TestClient client,
            String flowRoot,
            String approverId,
            String name,
            String event,
            String route
    ) throws Exception {
        var created = client.postWithCsrf(
                flowRoot + "/definitions",
                json(Map.of(
                        "name", name,
                        "approverId", approverId,
                        "triggerBinding", Map.of(
                                "moduleCode", FLOW_RECORD_MODULE_CODE,
                                "event", event,
                                "priority", 100,
                                "exclusive", false,
                                "conditions", List.of(Map.of(
                                        "fieldCode", "route",
                                        "operator", "EQ",
                                        "value", route
                                ))
                        )
                )),
                Map.of());
        assertCreated(created);
        assertThat(text(created.body(), "/data/triggerBinding/event")).isEqualTo(event);
        var definitionId = text(created.body(), "/data/definitionId");
        assertOk(client.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + ":publish",
                "{}",
                Map.of()));
        return definitionId;
    }

    private String assertAutomaticRecordEvent(
            TestClient client,
            String flowRoot,
            String systemId,
            String tenantId,
            String recordId,
            String definitionId,
            long recordVersion,
            String event
    ) throws Exception {
        var instanceIds = jdbcTemplate.queryForList(
                "SELECT instance_id FROM un_flow_instance "
                        + "WHERE system_id=? AND tenant_id=? AND record_id=? "
                        + "AND definition_id=?",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(recordId),
                Long.parseLong(definitionId));
        assertThat(instanceIds).hasSize(1);
        var instanceId = Long.toString(instanceIds.getFirst());
        var instance = client.get(flowRoot + "/instances/" + instanceId);
        assertOk(instance);
        assertThat(text(instance.body(), "/data/definitionId")).isEqualTo(definitionId);
        assertThat(text(instance.body(), "/data/recordBinding/moduleCode"))
                .isEqualTo(FLOW_RECORD_MODULE_CODE);
        assertThat(text(instance.body(), "/data/recordBinding/recordId")).isEqualTo(recordId);
        var eventKey = recordEventKey(systemId, tenantId, recordId, recordVersion, event);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_flow_trigger_dispatch "
                        + "WHERE system_id=? AND tenant_id=? AND event_key=? "
                        + "AND definition_id=? AND instance_id=?",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                eventKey,
                Long.parseLong(definitionId),
                Long.parseLong(instanceId))).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_flow_trigger_dispatch_instance "
                        + "WHERE system_id=? AND tenant_id=? AND event_key=? "
                        + "AND ordinal=0 AND definition_id=? AND instance_id=?",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                eventKey,
                Long.parseLong(definitionId),
                Long.parseLong(instanceId))).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_record_flow_state "
                        + "WHERE system_id=? AND tenant_id=? AND record_id=? "
                        + "AND instance_id=? AND status='PENDING' "
                        + "AND status_field_code IS NULL "
                        + "AND status_approved_value IS NULL "
                        + "AND status_rejected_value IS NULL "
                        + "AND status_withdrawn_value IS NULL "
                        + "AND status_terminated_value IS NULL",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(recordId),
                Long.parseLong(instanceId))).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_flow_instance "
                        + "WHERE system_id=? AND tenant_id=? AND record_id=?",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(recordId))).isOne();
        return instanceId;
    }

    private static String recordEventKey(
            String systemId,
            String tenantId,
            String recordId,
            long recordVersion,
            String event
    ) {
        return "record:%s:%s:%s:%s:%d:%s".formatted(
                systemId,
                tenantId,
                FLOW_RECORD_MODULE_CODE,
                recordId,
                recordVersion,
                event);
    }

    private FlowRecordSchema publishFlowRecordModule(TestClient client, String systemId) throws Exception {
        var configRoot = "/api/v1/systems/" + systemId + "/admin/config";
        var statusDictionary = client.postWithCsrf(
                configRoot + "/dictionaries",
                json(Map.of(
                        "code", "flow_approval_status",
                        "name", "Flow approval status",
                        "type", "STATUS",
                        "category", "flow",
                        "description", "Terminal approval outcomes written by Flow",
                        "status", "ENABLED",
                        "draftRevision", "0"
                )),
                Map.of("Idempotency-Key", key()));
        assertOk(statusDictionary);
        var statusDictionaryId = text(statusDictionary.body(), "/data/id");
        var approvedOptionId = createStatusOption(
                client, configRoot, statusDictionaryId, "approved", "Approved",
                "APPROVED", "#16a34a", 0, true, "1");
        var rejectedOptionId = createStatusOption(
                client, configRoot, statusDictionaryId, "rejected", "Rejected",
                "REJECTED", "#dc2626", 1, false, "2");
        var withdrawnOptionId = createStatusOption(
                client, configRoot, statusDictionaryId, "withdrawn", "Withdrawn",
                "WITHDRAWN", "#64748b", 2, false, "3");
        var terminatedOptionId = createStatusOption(
                client, configRoot, statusDictionaryId, "terminated", "Terminated",
                "TERMINATED", "#7c3aed", 3, false, "4");
        var group = client.postWithCsrf(configRoot + "/module-groups", json(Map.of(
                "code", "flow_records",
                "name", "Flow records",
                "description", "",
                "iconKey", "folder",
                "sortOrder", 0,
                "status", "ENABLED",
                "draftRevision", "5"
        )), Map.of("Idempotency-Key", key()));
        assertOk(group);
        var module = client.postWithCsrf(configRoot + "/modules", json(Map.ofEntries(
                Map.entry("groupId", text(group.body(), "/data/id")),
                Map.entry("code", FLOW_RECORD_MODULE_CODE),
                Map.entry("name", "Flow order"),
                Map.entry("description", ""),
                Map.entry("iconKey", "clipboard"),
                Map.entry("sortOrder", 0),
                Map.entry("status", "ENABLED"),
                Map.entry("allowComments", true),
                Map.entry("allowTeam", false),
                Map.entry("draftRevision", "6")
        )), Map.of("Idempotency-Key", key()));
        assertOk(module);
        var moduleId = text(module.body(), "/data/id");
        var routeField = client.postWithCsrf(
                configRoot + "/modules/" + moduleId + "/fields",
                json(Map.ofEntries(
                        Map.entry("dictionaryId", ""),
                        Map.entry("targetModuleId", ""),
                        Map.entry("code", "route"),
                        Map.entry("name", "Approval route"),
                        Map.entry("type", "TEXT"),
                        Map.entry("sortOrder", 0),
                        Map.entry("required", false),
                        Map.entry("hidden", false),
                        Map.entry("readonly", false),
                        Map.entry("searchable", false),
                        Map.entry("filterable", true),
                        Map.entry("showInList", true),
                        Map.entry("showInDetail", true),
                        Map.entry("indexMode", "SORT"),
                        Map.entry("status", "ENABLED"),
                        Map.entry("properties", Map.of("maxLength", 64)),
                        Map.entry("draftRevision", "7")
                )),
                Map.of("Idempotency-Key", key()));
        assertOk(routeField);
        var approvalStatusField = client.postWithCsrf(
                configRoot + "/modules/" + moduleId + "/fields",
                json(Map.ofEntries(
                        Map.entry("dictionaryId", statusDictionaryId),
                        Map.entry("targetModuleId", ""),
                        Map.entry("code", "approval_status"),
                        Map.entry("name", "Approval status"),
                        Map.entry("type", "STATUS"),
                        Map.entry("sortOrder", 1),
                        Map.entry("required", false),
                        Map.entry("hidden", false),
                        Map.entry("readonly", false),
                        Map.entry("searchable", false),
                        Map.entry("filterable", true),
                        Map.entry("showInList", true),
                        Map.entry("showInDetail", true),
                        Map.entry("indexMode", "FILTER"),
                        Map.entry("status", "ENABLED"),
                        Map.entry("properties", Map.of(
                                "initialStateIds", List.of(approvedOptionId),
                                "transitions", List.of(
                                        Map.of(
                                                "from", approvedOptionId,
                                                "to", rejectedOptionId
                                        ),
                                        Map.of(
                                                "from", approvedOptionId,
                                                "to", withdrawnOptionId
                                        ),
                                        Map.of(
                                                "from", approvedOptionId,
                                                "to", terminatedOptionId
                                        )
                                )
                        )),
                        Map.entry("draftRevision", "8")
                )),
                Map.of("Idempotency-Key", key()));
        assertOk(approvalStatusField);
        var approverMemberField = client.postWithCsrf(
                configRoot + "/modules/" + moduleId + "/fields",
                json(Map.ofEntries(
                        Map.entry("dictionaryId", ""),
                        Map.entry("targetModuleId", ""),
                        Map.entry("code", "approver_member"),
                        Map.entry("name", "Record approver"),
                        Map.entry("type", "MEMBER"),
                        Map.entry("sortOrder", 2),
                        Map.entry("required", false),
                        Map.entry("hidden", false),
                        Map.entry("readonly", false),
                        Map.entry("searchable", false),
                        Map.entry("filterable", true),
                        Map.entry("showInList", true),
                        Map.entry("showInDetail", true),
                        Map.entry("indexMode", "FILTER"),
                        Map.entry("status", "ENABLED"),
                        Map.entry("properties", Map.of(
                                "multiple", false,
                                "selectionScope", "CURRENT_TENANT",
                                "allowInactive", false
                        )),
                        Map.entry("draftRevision", "9")
                )),
                Map.of("Idempotency-Key", key()));
        assertOk(approverMemberField);
        var eventTimeField = client.postWithCsrf(
                configRoot + "/modules/" + moduleId + "/fields",
                json(Map.ofEntries(
                        Map.entry("dictionaryId", ""),
                        Map.entry("targetModuleId", ""),
                        Map.entry("code", "event_time"),
                        Map.entry("name", "Event time"),
                        Map.entry("type", "DATETIME"),
                        Map.entry("sortOrder", 3),
                        Map.entry("required", false),
                        Map.entry("hidden", false),
                        Map.entry("readonly", false),
                        Map.entry("searchable", false),
                        Map.entry("filterable", true),
                        Map.entry("showInList", true),
                        Map.entry("showInDetail", true),
                        Map.entry("indexMode", "STATISTIC"),
                        Map.entry("status", "ENABLED"),
                        Map.entry("properties", Map.of()),
                        Map.entry("draftRevision", "10")
                )),
                Map.of("Idempotency-Key", key()));
        assertOk(eventTimeField);
        var relatedOrdersField = client.postWithCsrf(
                configRoot + "/modules/" + moduleId + "/fields",
                json(Map.ofEntries(
                        Map.entry("dictionaryId", ""),
                        Map.entry("targetModuleId", moduleId),
                        Map.entry("code", "related_orders"),
                        Map.entry("name", "Related orders"),
                        Map.entry("type", "RELATION"),
                        Map.entry("sortOrder", 4),
                        Map.entry("required", false),
                        Map.entry("hidden", false),
                        Map.entry("readonly", false),
                        Map.entry("searchable", false),
                        Map.entry("filterable", false),
                        Map.entry("showInList", false),
                        Map.entry("showInDetail", true),
                        Map.entry("indexMode", "NONE"),
                        Map.entry("status", "ENABLED"),
                        Map.entry("properties", Map.of(
                                "multiple", true,
                                "displayFieldId", text(routeField.body(), "/data/id"))),
                        Map.entry("draftRevision", "11")
                )),
                Map.of("Idempotency-Key", key()));
        assertOk(relatedOrdersField);
        var lineItemsField = client.postWithCsrf(
                configRoot + "/modules/" + moduleId + "/fields",
                json(Map.ofEntries(
                        Map.entry("dictionaryId", ""),
                        Map.entry("targetModuleId", moduleId),
                        Map.entry("code", "line_items"),
                        Map.entry("name", "Line items"),
                        Map.entry("type", "SUBTABLE"),
                        Map.entry("sortOrder", 5),
                        Map.entry("required", false),
                        Map.entry("hidden", false),
                        Map.entry("readonly", false),
                        Map.entry("searchable", false),
                        Map.entry("filterable", false),
                        Map.entry("showInList", false),
                        Map.entry("showInDetail", true),
                        Map.entry("indexMode", "NONE"),
                        Map.entry("status", "ENABLED"),
                        Map.entry("properties", Map.ofEntries(
                                Map.entry("columnFieldIds", List.of(
                                        text(routeField.body(), "/data/id"))),
                                Map.entry("allowRowCreate", true),
                                Map.entry("allowRowUpdate", true),
                                Map.entry("allowRowDelete", true),
                                Map.entry("allowRowReorder", true),
                                Map.entry("minRows", 0),
                                Map.entry("maxRows", 20))),
                        Map.entry("draftRevision", "12")
                )),
                Map.of("Idempotency-Key", key()));
        assertOk(lineItemsField);
        var aiSummaryField = client.postWithCsrf(
                configRoot + "/modules/" + moduleId + "/fields",
                json(Map.ofEntries(
                        Map.entry("dictionaryId", ""),
                        Map.entry("targetModuleId", ""),
                        Map.entry("code", "ai_summary"),
                        Map.entry("name", "AI summary"),
                        Map.entry("type", "AI_FILL"),
                        Map.entry("sortOrder", 6),
                        Map.entry("required", false),
                        Map.entry("hidden", false),
                        Map.entry("readonly", true),
                        Map.entry("searchable", false),
                        Map.entry("filterable", false),
                        Map.entry("showInList", true),
                        Map.entry("showInDetail", true),
                        Map.entry("indexMode", "NONE"),
                        Map.entry("status", "ENABLED"),
                        Map.entry("properties", Map.ofEntries(
                                Map.entry("resultSchema", "STRING"),
                                Map.entry("sourceFieldIds", List.of(
                                        text(routeField.body(), "/data/id"),
                                        text(eventTimeField.body(), "/data/id"))),
                                Map.entry("promptTemplate",
                                        "Summarize the approval route and event time."),
                                Map.entry("modelPolicy", "SYSTEM_DEFAULT"),
                                Map.entry("minConfidence", 0.80),
                                Map.entry("overwriteMode", "CONFIRM"))),
                        Map.entry("draftRevision", "13")
                )),
                Map.of("Idempotency-Key", key()));
        assertOk(aiSummaryField);
        var archiveAction = client.postWithCsrf(
                configRoot + "/modules/" + moduleId + "/actions",
                json(Map.of(
                        "code", "archive",
                        "name", "Archive Flow record",
                        "type", "CUSTOM",
                        "placement", "DETAIL",
                        "confirmMessage", "Archive this Flow record?",
                        "sortOrder", 10,
                        "status", "ENABLED",
                        "properties", Map.of(
                                "style", "DEFAULT",
                                "successMessage", "Flow record archived"),
                        "draftRevision", "14"
                )),
                Map.of("Idempotency-Key", key()));
        assertOk(archiveAction);
        var unarchiveAction = client.postWithCsrf(
                configRoot + "/modules/" + moduleId + "/actions",
                json(Map.of(
                        "code", "unarchive",
                        "name", "Unarchive Flow record",
                        "type", "CUSTOM",
                        "placement", "DETAIL",
                        "confirmMessage", "Unarchive this Flow record?",
                        "sortOrder", 11,
                        "status", "ENABLED",
                        "properties", Map.of(
                                "style", "DEFAULT",
                                "successMessage", "Flow record unarchived"),
                        "draftRevision", "15"
                )),
                Map.of("Idempotency-Key", key()));
        assertOk(unarchiveAction);
        var restoreTrashAction = client.postWithCsrf(
                configRoot + "/modules/" + moduleId + "/actions",
                json(Map.of(
                        "code", "restore_trash",
                        "name", "Restore Flow record",
                        "type", "CUSTOM",
                        "placement", "DETAIL",
                        "confirmMessage", "Restore this Flow record?",
                        "sortOrder", 12,
                        "status", "ENABLED",
                        "properties", Map.of(
                                "style", "DEFAULT",
                                "successMessage", "Flow record restored"),
                        "draftRevision", "16"
                )),
                Map.of("Idempotency-Key", key()));
        assertOk(restoreTrashAction);
        var checked = client.postWithCsrf(
                configRoot + "/checks",
                json(Map.of("draftRevision", "17")),
                Map.of());
        assertOk(checked);
        assertThat(checked.body().at("/data/blockerCount").asLong()).isZero();
        var config = client.get(configRoot);
        assertOk(config);
        var published = client.postWithCsrf(configRoot + ":publish", json(Map.of(
                "checkId", text(checked.body(), "/data/id"),
                "draftRevision", "17",
                "configRootVersion", text(config.body(), "/data/version"),
                "reason", "Flow record binding HTTP journey"
        )), Map.of("Idempotency-Key", key()));
        assertOk(published);
        return new FlowRecordSchema(
                moduleId,
                text(published.body(), "/data/version/id"),
                approvedOptionId,
                rejectedOptionId,
                withdrawnOptionId,
                terminatedOptionId,
                text(approverMemberField.body(), "/data/id"),
                text(aiSummaryField.body(), "/data/id"));
    }

    private String createStatusOption(
            TestClient client,
            String configRoot,
            String dictionaryId,
            String code,
            String label,
            String semanticKey,
            String color,
            int sortOrder,
            boolean isDefault,
            String draftRevision
    ) throws Exception {
        var option = client.postWithCsrf(
                configRoot + "/dictionaries/" + dictionaryId + "/items",
                json(Map.of(
                        "parentId", "",
                        "code", code,
                        "label", label,
                        "semanticKey", semanticKey,
                        "color", color,
                        "iconKey", "",
                        "sortOrder", sortOrder,
                        "isDefault", isDefault,
                        "status", "ENABLED",
                        "draftRevision", draftRevision
                )),
                Map.of("Idempotency-Key", key()));
        assertOk(option);
        return text(option.body(), "/data/id");
    }

    private TestResponse createActiveRecord(
            TestClient client,
            String runtimeRoot,
            String schemaVersionId,
            String title
    ) throws Exception {
        var created = client.postWithCsrf(runtimeRoot + "/records", json(Map.of(
                "schemaVersionId", schemaVersionId,
                "title", title,
                "values", Map.of()
        )), Map.of("Idempotency-Key", key()));
        assertCreated(created);
        assertOk(client.postWithCsrf(
                runtimeRoot + "/records/" + text(created.body(), "/data/recordId") + ":activate",
                json(Map.of("expectedVersion", 0)),
                Map.of("Idempotency-Key", key())));
        return created;
    }

    private void exerciseOpenApiRecords(
            TestClient roleAdmin,
            TestClient tenantAdmin,
            String systemId,
            String firstTenantId,
            String secondTenantId,
            String serviceMemberId,
            String serviceRoleId,
            String originalDataScopeId,
            String selfDataScopeId,
            String schemaVersionId,
            String otherOwnerRecordId
    ) throws Exception {
        roleAdmin = login();
        assertOk(roleAdmin.postWithCsrf(
                "/api/v1/context/systems/" + systemId + ":switch",
                "{}",
                Map.of()));
        publishOpenApiRecordRole(
                roleAdmin, systemId, serviceRoleId, selfDataScopeId,
                true, true, true, true, true);

        roleAdmin = login();
        assertOk(roleAdmin.postWithCsrf(
                "/api/v1/context/systems/" + systemId + ":switch",
                "{}",
                Map.of()));

        var applicationRoot = "/api/v1/systems/" + systemId
                + "/admin/openapi/applications";
        var application = roleAdmin.postWithCsrf(
                applicationRoot,
                json(Map.of(
                        "name", "Flow record OpenAPI client",
                        "tenantId", firstTenantId,
                        "serviceMemberId", serviceMemberId,
                        "secretRef", OPENAPI_SECRET_FILE.toUri().toString(),
                        "scopes", List.of(
                                "record.write", "record.read", "file.write", "file.read",
                                "flow.instance.start", "flow.read"),
                        "ipAllowlist", List.of("127.0.0.1"),
                        "rateLimitPerMinute", 200
                )),
                Map.of("Idempotency-Key", key()));
        assertCreated(application);
        var applicationId = text(application.body(), "/data/id");
        var appKey = text(application.body(), "/data/appKey");
        var recordRoot = "/openapi/v1/modules/" + FLOW_RECORD_MODULE_CODE
                + "/records";
        var createBody = json(Map.of(
                "lifecycleState", "ACTIVE",
                "values", Map.of("route", "openapi-sensitive-route")
        ));
        var createKey = key();
        var created = new TestClient().post(
                recordRoot,
                createBody,
                signedOpenApiHeaders(
                        "POST", recordRoot, null, createBody,
                        appKey, createKey, "record-create"));
        assertCreated(created);
        var recordId = text(created.body(), "/data/recordId");
        assertThat(text(created.body(), "/data/status")).isEqualTo("ACTIVE");
        assertThat(fieldValue(created.body(), "route"))
                .isEqualTo("openapi-sensitive-route");

        var flowRoot = "/api/v1/systems/" + systemId + "/flow";
        var flowDefinition = roleAdmin.postWithCsrf(
                flowRoot + "/definitions",
                json(Map.of(
                        "name", "Signed record status approval",
                        "approverId", serviceMemberId)),
                Map.of());
        assertCreated(flowDefinition);
        var flowDefinitionId = text(flowDefinition.body(), "/data/definitionId");
        assertOk(roleAdmin.postWithCsrf(
                flowRoot + "/definitions/" + flowDefinitionId + ":publish",
                "{}",
                Map.of()));
        var flowStartPath = "/openapi/v1/flow/definitions/"
                + flowDefinitionId + "/instances";
        var flowStartBody = json(Map.of(
                "businessKey", "openapi-record-status-" + key(),
                "recordBinding", Map.of(
                        "moduleCode", FLOW_RECORD_MODULE_CODE,
                        "recordId", recordId)));
        var flowStarted = new TestClient().post(
                flowStartPath,
                flowStartBody,
                signedOpenApiHeaders(
                        "POST", flowStartPath, null, flowStartBody,
                        appKey, key(), "flow-status-start"));
        assertCreated(flowStarted);
        var flowInstanceId = text(flowStarted.body(), "/data/instanceId");
        var flowStatusPath = "/openapi/v1/flow/instances/" + flowInstanceId;
        var flowStatus = new TestClient().get(
                flowStatusPath,
                signedOpenApiHeaders(
                        "GET", flowStatusPath, null, "",
                        appKey, key(), "flow-status-read"));
        assertOk(flowStatus);
        assertThat(text(flowStatus.body(), "/data/instanceId")).isEqualTo(flowInstanceId);
        assertThat(text(flowStatus.body(), "/data/definitionId"))
                .isEqualTo(flowDefinitionId);
        assertThat(text(flowStatus.body(), "/data/status")).isEqualTo("PENDING");
        assertThat(text(flowStatus.body(), "/data/recordBinding/moduleCode"))
                .isEqualTo(FLOW_RECORD_MODULE_CODE);
        assertThat(text(flowStatus.body(), "/data/recordBinding/recordId"))
                .isEqualTo(recordId);
        assertThat(flowStatus.body().at("/data/currentStepIndex").asInt()).isZero();
        assertThat(flowStatus.body().at("/data/currentStageIndex").asInt()).isZero();
        assertThat(flowStatus.body().at("/data/startedAt").isTextual()).isTrue();
        assertThat(flowStatus.body().toString())
                .doesNotContain("approverId")
                .doesNotContain("decisionEvidence")
                .doesNotContain("completionExecutions")
                .doesNotContain("compensationExecutions");

        var hiddenRecordRoot = "/api/v1/systems/" + systemId
                + "/runtime/modules/" + FLOW_RECORD_MODULE_CODE;
        var hiddenRecord = createActiveRecord(
                roleAdmin,
                hiddenRecordRoot,
                schemaVersionId,
                "OpenAPI Flow status row-hiding fixture");
        var hiddenRecordId = text(hiddenRecord.body(), "/data/recordId");
        var hiddenFlow = roleAdmin.postWithCsrf(
                flowRoot + "/definitions/" + flowDefinitionId + "/instances",
                json(Map.of(
                        "businessKey", "openapi-hidden-row-status-" + key(),
                        "recordBinding", Map.of(
                                "moduleCode", FLOW_RECORD_MODULE_CODE,
                                "recordId", hiddenRecordId))),
                Map.of("Idempotency-Key", key()));
        assertCreated(hiddenFlow);
        var hiddenFlowInstanceId = text(hiddenFlow.body(), "/data/instanceId");
        var hiddenFlowStatusPath = "/openapi/v1/flow/instances/"
                + hiddenFlowInstanceId;
        assertError(new TestClient().get(
                        hiddenFlowStatusPath,
                        signedOpenApiHeaders(
                                "GET", hiddenFlowStatusPath, null, "",
                                appKey, key(), "flow-status-row-hide")),
                404,
                "RECORD_NOT_FOUND");
        assertOk(roleAdmin.postWithCsrf(
                flowRoot + "/instances/" + hiddenFlowInstanceId + ":terminate",
                json(Map.of("reason", "Complete OpenAPI row-hiding fixture")),
                Map.of("Idempotency-Key", key())));
        assertOk(roleAdmin.postWithCsrf(
                hiddenRecordRoot + "/records/" + hiddenRecordId + ":trash",
                json(Map.of("expectedVersion", 1)),
                Map.of("Idempotency-Key", key())));

        var replayed = new TestClient().post(
                recordRoot,
                createBody,
                signedOpenApiHeaders(
                        "POST", recordRoot, null, createBody,
                        appKey, createKey, "record-replay"));
        assertCreated(replayed);
        assertThat(text(replayed.body(), "/data/recordId")).isEqualTo(recordId);
        assertError(new TestClient().post(
                        recordRoot,
                        json(Map.of(
                                "lifecycleState", "ACTIVE",
                                "values", Map.of("route", "changed-payload"))),
                        signedOpenApiHeaders(
                                "POST", recordRoot, null,
                                json(Map.of(
                                        "lifecycleState", "ACTIVE",
                                        "values", Map.of("route", "changed-payload"))),
                                appKey, createKey, "record-conflict")),
                409,
                "IDEMPOTENCY_CONFLICT");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_record WHERE system_id=? "
                        + "AND tenant_id=? AND record_id=?",
                Integer.class,
                Long.parseLong(systemId),
                Long.parseLong(firstTenantId),
                Long.parseLong(recordId))).isOne();

        var recordPath = recordRoot + "/" + recordId;
        var fileRoot = recordPath + "/files";
        var fileContent = "signed-openapi-file-sensitive-content"
                .getBytes(StandardCharsets.UTF_8);
        var encodedFileContent = Base64.getEncoder().encodeToString(fileContent);
        var fileBody = json(Map.of(
                "originalName", "openapi-evidence.txt",
                "mediaType", "text/plain",
                "contentBase64", encodedFileContent
        ));
        var fileUploadKey = key();
        var uploadedFile = new TestClient().post(
                fileRoot,
                fileBody,
                signedOpenApiHeaders(
                        "POST", fileRoot, null, fileBody,
                        appKey, fileUploadKey, "record-file-upload"));
        assertCreated(uploadedFile);
        var fileId = text(uploadedFile.body(), "/data/fileId");
        assertThat(text(uploadedFile.body(), "/data/originalName"))
                .isEqualTo("openapi-evidence.txt");
        assertThat(text(uploadedFile.body(), "/data/mediaType")).isEqualTo("text/plain");
        assertThat(uploadedFile.body().at("/data/sizeBytes").asLong())
                .isEqualTo(fileContent.length);
        assertThat(text(uploadedFile.body(), "/data/sha256")).isEqualTo(sha256(fileContent));
        var fileDownloadPath = text(uploadedFile.body(), "/data/downloadPath");
        assertThat(fileDownloadPath).isEqualTo(fileRoot + "/" + fileId + "/content");
        assertThat(uploadedFile.body().toString())
                .doesNotContain("objectKey")
                .doesNotContain("system/")
                .doesNotContain(encodedFileContent);

        var replayedFile = new TestClient().post(
                fileRoot,
                fileBody,
                signedOpenApiHeaders(
                        "POST", fileRoot, null, fileBody,
                        appKey, fileUploadKey, "record-file-replay"));
        assertCreated(replayedFile);
        assertThat(text(replayedFile.body(), "/data/fileId")).isEqualTo(fileId);

        var changedFileBody = json(Map.of(
                "originalName", "openapi-evidence.txt",
                "mediaType", "text/plain",
                "contentBase64", Base64.getEncoder().encodeToString(
                        "changed-file-content".getBytes(StandardCharsets.UTF_8))
        ));
        assertError(new TestClient().post(
                        fileRoot,
                        changedFileBody,
                        signedOpenApiHeaders(
                                "POST", fileRoot, null, changedFileBody,
                                appKey, fileUploadKey, "record-file-conflict")),
                409,
                "IDEMPOTENCY_CONFLICT");

        var fileQuery = "page=1&size=20";
        var listedFiles = new TestClient().get(
                fileRoot + "?" + fileQuery,
                signedOpenApiHeaders(
                        "GET", fileRoot, fileQuery, "",
                        appKey, key(), "record-file-list"));
        assertOk(listedFiles);
        assertThat(listedFiles.body().at("/data/total").asLong()).isOne();
        assertThat(text(listedFiles.body(), "/data/items/0/fileId")).isEqualTo(fileId);
        assertThat(listedFiles.body().toString()).doesNotContain("objectKey");

        var downloadedFile = new TestClient().download(
                fileDownloadPath,
                signedOpenApiHeaders(
                        "GET", fileDownloadPath, null, "",
                        appKey, key(), "record-file-download"));
        assertThat(downloadedFile.status()).isEqualTo(200);
        assertThat(downloadedFile.body()).isEqualTo(fileContent);
        assertThat(downloadedFile.header("Content-Type")).startsWith("text/plain");
        assertThat(downloadedFile.header("Content-Length"))
                .isEqualTo(Integer.toString(fileContent.length));
        assertThat(downloadedFile.header("Content-Disposition"))
                .contains("openapi-evidence.txt");
        assertThat(downloadedFile.header(OpenApiRecordFileController.CONTENT_SHA256_HEADER))
                .isEqualTo(sha256(fileContent));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_file_object WHERE system_id=? AND tenant_id=? AND id=?",
                Integer.class,
                Long.parseLong(systemId),
                Long.parseLong(firstTenantId),
                Long.parseLong(fileId))).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_file_reference WHERE system_id=? AND tenant_id=? "
                        + "AND file_id=? AND target_type='RUNTIME_RECORD' AND target_id=?",
                Integer.class,
                Long.parseLong(systemId),
                Long.parseLong(firstTenantId),
                Long.parseLong(fileId),
                recordId)).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_audit_operation WHERE aggregate_type='FILE_ASSET' "
                        + "AND aggregate_id=? AND operation_type='RUNTIME_RECORD_FILE_ATTACHED' "
                        + "AND source_type='OPENAPI' AND result='SUCCESS'",
                Integer.class,
                fileId)).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_sys_outbox_event WHERE aggregate_type='FILE_ASSET' "
                        + "AND aggregate_id=? AND event_type='RUNTIME_RECORD_FILE_ATTACHED'",
                Integer.class,
                fileId)).isOne();
        var fileOutboxPayload = jdbcTemplate.queryForObject(
                "SELECT CAST(payload_json AS CHAR) FROM un_sys_outbox_event "
                        + "WHERE aggregate_type='FILE_ASSET' AND aggregate_id=? "
                        + "AND event_type='RUNTIME_RECORD_FILE_ATTACHED'",
                String.class,
                fileId);
        assertThat(fileOutboxPayload)
                .doesNotContain("objectKey")
                .doesNotContain(encodedFileContent)
                .doesNotContain(fileUploadKey);

        assertError(new TestClient().get(
                        recordRoot + "/" + otherOwnerRecordId + "/files?page=1&size=20",
                        signedOpenApiHeaders(
                                "GET", recordRoot + "/" + otherOwnerRecordId + "/files",
                                "page=1&size=20", "", appKey, key(), "record-file-scope-hide")),
                404,
                "RECORD_NOT_FOUND");

        var orphan = roleAdmin.uploadWithCsrf(
                "/api/v1/systems/" + systemId + "/files",
                "unreferenced.txt",
                "text/plain",
                "unreferenced".getBytes(StandardCharsets.UTF_8));
        assertCreated(orphan);
        var orphanDownloadPath = fileRoot + "/" + text(orphan.body(), "/data/id") + "/content";
        assertError(new TestClient().get(
                        orphanDownloadPath,
                        signedOpenApiHeaders(
                                "GET", orphanDownloadPath, null, "",
                                appKey, key(), "record-file-reference-hide")),
                404,
                "FILE_NOT_FOUND");

        var updateBody = json(Map.of(
                "expectedVersion", 0,
                "values", Map.of("route", "openapi-updated-sensitive-route")
        ));
        var updateKey = key();
        var updated = new TestClient().put(
                recordPath,
                updateBody,
                signedOpenApiHeaders(
                        "PUT", recordPath, null, updateBody,
                        appKey, updateKey, "record-update"));
        assertOk(updated);
        assertThat(updated.body().at("/data/version").asLong()).isEqualTo(1);
        assertThat(fieldValue(updated.body(), "route"))
                .isEqualTo("openapi-updated-sensitive-route");

        var updateReplay = new TestClient().put(
                recordPath,
                updateBody,
                signedOpenApiHeaders(
                        "PUT", recordPath, null, updateBody,
                        appKey, updateKey, "record-update-replay"));
        assertOk(updateReplay);
        assertThat(updateReplay.body().at("/data/version").asLong()).isEqualTo(1);
        var changedUpdateBody = json(Map.of(
                "expectedVersion", 0,
                "values", Map.of("route", "openapi-changed-update")
        ));
        assertError(new TestClient().put(
                        recordPath,
                        changedUpdateBody,
                        signedOpenApiHeaders(
                                "PUT", recordPath, null, changedUpdateBody,
                                appKey, updateKey, "record-update-conflict")),
                409,
                "IDEMPOTENCY_CONFLICT");
        assertError(new TestClient().put(
                        recordPath,
                        updateBody,
                        signedOpenApiHeaders(
                                "PUT", recordPath, null, updateBody,
                                appKey, key(), "record-update-stale")),
                409,
                "RECORD_VERSION_CONFLICT");

        var archivePath = recordPath + ":archive";
        var archiveBody = json(Map.of("expectedVersion", 1));
        var archiveKey = key();
        var archived = new TestClient().post(
                archivePath,
                archiveBody,
                signedOpenApiHeaders(
                        "POST", archivePath, null, archiveBody,
                        appKey, archiveKey, "record-archive"));
        assertOk(archived);
        assertThat(text(archived.body(), "/data/status")).isEqualTo("ARCHIVED");
        assertThat(archived.body().at("/data/version").asLong()).isEqualTo(2);
        var archivedReplay = new TestClient().post(
                archivePath,
                archiveBody,
                signedOpenApiHeaders(
                        "POST", archivePath, null, archiveBody,
                        appKey, archiveKey, "record-archive-replay"));
        assertOk(archivedReplay);
        assertThat(archivedReplay.body().at("/data/version").asLong()).isEqualTo(2);

        var unarchivePath = recordPath + ":unarchive";
        var unarchiveBody = json(Map.of("expectedVersion", 2));
        var unarchived = new TestClient().post(
                unarchivePath,
                unarchiveBody,
                signedOpenApiHeaders(
                        "POST", unarchivePath, null, unarchiveBody,
                        appKey, key(), "record-unarchive"));
        assertOk(unarchived);
        assertThat(text(unarchived.body(), "/data/status")).isEqualTo("ACTIVE");
        assertThat(unarchived.body().at("/data/version").asLong()).isEqualTo(3);

        var trashPath = recordPath + ":trash";
        var trashBody = json(Map.of("expectedVersion", 3));
        var trashed = new TestClient().post(
                trashPath,
                trashBody,
                signedOpenApiHeaders(
                        "POST", trashPath, null, trashBody,
                        appKey, key(), "record-trash"));
        assertOk(trashed);
        assertThat(text(trashed.body(), "/data/status")).isEqualTo("TRASHED");
        assertThat(trashed.body().at("/data/version").asLong()).isEqualTo(4);

        var restorePath = recordPath + ":restore-from-trash";
        var restoreBody = json(Map.of("expectedVersion", 4));
        var restored = new TestClient().post(
                restorePath,
                restoreBody,
                signedOpenApiHeaders(
                        "POST", restorePath, null, restoreBody,
                        appKey, key(), "record-restore"));
        assertOk(restored);
        assertThat(text(restored.body(), "/data/status")).isEqualTo("ACTIVE");
        assertThat(restored.body().at("/data/version").asLong()).isEqualTo(5);

        var detail = new TestClient().get(
                recordRoot + "/" + recordId,
                signedOpenApiHeaders(
                        "GET", recordRoot + "/" + recordId, null, "",
                        appKey, key(), "record-detail"));
        assertOk(detail);
        assertThat(text(detail.body(), "/data/recordId")).isEqualTo(recordId);
        assertThat(fieldValue(detail.body(), "route"))
                .isEqualTo("openapi-updated-sensitive-route");

        var query = "page=1&size=20";
        var listed = new TestClient().get(
                recordRoot + "?" + query,
                signedOpenApiHeaders(
                        "GET", recordRoot, query, "",
                        appKey, key(), "record-list"));
        assertOk(listed);
        assertThat(listed.body().at("/data/total").asLong()).isOne();
        assertThat(listed.body().at("/data/rows"))
                .extracting(item -> item.path("recordId").asText())
                .containsExactly(recordId)
                .doesNotContain(otherOwnerRecordId);
        assertError(new TestClient().get(
                        recordRoot + "/" + otherOwnerRecordId,
                        signedOpenApiHeaders(
                                "GET", recordRoot + "/" + otherOwnerRecordId,
                                null, "", appKey, key(), "record-scope-hide")),
                404,
                "RECORD_NOT_FOUND");

        var draftBody = json(Map.of(
                "lifecycleState", "DRAFT",
                "values", Map.of("route", "openapi-draft-sensitive-route")
        ));
        var draftCreated = new TestClient().post(
                recordRoot,
                draftBody,
                signedOpenApiHeaders(
                        "POST", recordRoot, null, draftBody,
                        appKey, key(), "record-draft-create"));
        assertCreated(draftCreated);
        assertThat(text(draftCreated.body(), "/data/status")).isEqualTo("DRAFT");
        var draftRecordId = text(draftCreated.body(), "/data/recordId");
        var activatePath = recordRoot + "/" + draftRecordId + ":activate";
        var activateBody = json(Map.of("expectedVersion", 0));
        var activated = new TestClient().post(
                activatePath,
                activateBody,
                signedOpenApiHeaders(
                        "POST", activatePath, null, activateBody,
                        appKey, key(), "record-activate"));
        assertOk(activated);
        assertThat(text(activated.body(), "/data/status")).isEqualTo("ACTIVE");
        assertThat(activated.body().at("/data/version").asLong()).isEqualTo(1);

        tenantAdmin = login();
        assertOk(tenantAdmin.postWithCsrf(
                "/api/v1/context/systems/" + systemId + ":switch",
                "{}", Map.of()));
        var switchedToSecondTenant = tenantAdmin.postWithCsrf(
                "/api/v1/context/tenants/" + secondTenantId + ":switch",
                "{}", Map.of());
        assertOk(switchedToSecondTenant);
        var secondTenantMemberId = text(
                switchedToSecondTenant.body(), "/data/context/memberId");
        var crossTenantRecord = createActiveRecord(
                tenantAdmin,
                "/api/v1/systems/" + systemId + "/runtime/modules/"
                        + FLOW_RECORD_MODULE_CODE,
                schemaVersionId,
                "OpenAPI cross-tenant hidden record");
        var crossTenantRecordId = text(crossTenantRecord.body(), "/data/recordId");
        var crossTenantDefinition = tenantAdmin.postWithCsrf(
                "/api/v1/systems/" + systemId + "/flow/definitions",
                json(Map.of(
                        "name", "Cross-tenant hidden status approval",
                        "approverId", secondTenantMemberId)),
                Map.of());
        assertCreated(crossTenantDefinition);
        var crossTenantDefinitionId = text(
                crossTenantDefinition.body(), "/data/definitionId");
        assertOk(tenantAdmin.postWithCsrf(
                "/api/v1/systems/" + systemId + "/flow/definitions/"
                        + crossTenantDefinitionId + ":publish",
                "{}",
                Map.of()));
        var crossTenantFlow = tenantAdmin.postWithCsrf(
                "/api/v1/systems/" + systemId + "/flow/definitions/"
                        + crossTenantDefinitionId + "/instances",
                json(Map.of("businessKey", "cross-tenant-hidden-status-" + key())),
                Map.of("Idempotency-Key", key()));
        assertCreated(crossTenantFlow);
        var crossTenantFlowStatusPath = "/openapi/v1/flow/instances/"
                + text(crossTenantFlow.body(), "/data/instanceId");
        assertOk(tenantAdmin.postWithCsrf(
                "/api/v1/context/tenants/" + firstTenantId + ":switch",
                "{}", Map.of()));
        assertError(new TestClient().get(
                        recordRoot + "/" + crossTenantRecordId,
                        signedOpenApiHeaders(
                                "GET", recordRoot + "/" + crossTenantRecordId,
                                null, "", appKey, key(), "record-tenant-hide")),
                404,
                "RECORD_NOT_FOUND");
        assertError(new TestClient().get(
                        crossTenantFlowStatusPath,
                        signedOpenApiHeaders(
                                "GET", crossTenantFlowStatusPath, null, "",
                                appKey, key(), "flow-status-tenant-hide")),
                404,
                "FLOW_INSTANCE_NOT_FOUND");
        var crossTenantFileRoot = recordRoot + "/" + crossTenantRecordId + "/files";
        assertError(new TestClient().get(
                        crossTenantFileRoot + "?page=1&size=20",
                        signedOpenApiHeaders(
                                "GET", crossTenantFileRoot, "page=1&size=20", "",
                                appKey, key(), "record-file-tenant-hide")),
                404,
                "RECORD_NOT_FOUND");

        var relationRoot = recordPath + "/relations/related_orders";
        var emptyRelations = new TestClient().get(
                relationRoot + "?page=1&size=20",
                signedOpenApiHeaders(
                        "GET", relationRoot, "page=1&size=20", "",
                        appKey, key(), "record-relation-empty"));
        assertOk(emptyRelations);
        assertThat(emptyRelations.body().at("/data/total").asLong()).isZero();
        assertThat(emptyRelations.body().at("/data/capabilities/relationRead/enabled")
                .asBoolean()).isTrue();

        var relationMutationBody = json(Map.of(
                "expectedVersion", 5,
                "add", List.of(Map.of(
                        "targetRecordId", recordId,
                        "targetExpectedVersion", 5,
                        "ordinal", 0)),
                "remove", List.of(),
                "order", List.of()
        ));
        var relationMutationKey = key();
        var relationMutation = new TestClient().post(
                relationRoot + ":mutate",
                relationMutationBody,
                signedOpenApiHeaders(
                        "POST", relationRoot + ":mutate", null,
                        relationMutationBody, appKey, relationMutationKey,
                        "record-relation-mutate"));
        assertOk(relationMutation);
        assertThat(text(relationMutation.body(), "/data/recordId")).isEqualTo(recordId);
        assertThat(relationMutation.body().at("/data/version").asLong()).isEqualTo(6);
        assertThat(text(relationMutation.body(), "/data/status")).isEqualTo("ACTIVE");

        var relationReplay = new TestClient().post(
                relationRoot + ":mutate",
                relationMutationBody,
                signedOpenApiHeaders(
                        "POST", relationRoot + ":mutate", null,
                        relationMutationBody, appKey, relationMutationKey,
                        "record-relation-replay"));
        assertOk(relationReplay);
        assertThat(relationReplay.body().at("/data/version").asLong()).isEqualTo(6);
        var changedRelationBody = json(Map.of(
                "expectedVersion", 5,
                "add", List.of(),
                "remove", List.of(),
                "order", List.of()
        ));
        assertError(new TestClient().post(
                        relationRoot + ":mutate",
                        changedRelationBody,
                        signedOpenApiHeaders(
                                "POST", relationRoot + ":mutate", null,
                                changedRelationBody, appKey, relationMutationKey,
                                "record-relation-conflict")),
                409,
                "IDEMPOTENCY_CONFLICT");

        var related = new TestClient().get(
                relationRoot + "?page=1&size=20",
                signedOpenApiHeaders(
                        "GET", relationRoot, "page=1&size=20", "",
                        appKey, key(), "record-relation-list"));
        assertOk(related);
        assertThat(related.body().at("/data/total").asLong()).isOne();
        assertThat(text(related.body(), "/data/items/0/targetRecordId"))
                .isEqualTo(recordId);
        assertThat(text(related.body(), "/data/items/0/title"))
                .isEqualTo("openapi-updated-sensitive-route");

        var hiddenTargetBody = json(Map.of(
                "expectedVersion", 6,
                "add", List.of(Map.of(
                        "targetRecordId", otherOwnerRecordId,
                        "targetExpectedVersion", 1,
                        "ordinal", 1)),
                "remove", List.of(),
                "order", List.of()
        ));
        assertError(new TestClient().post(
                        relationRoot + ":mutate",
                        hiddenTargetBody,
                        signedOpenApiHeaders(
                                "POST", relationRoot + ":mutate", null,
                                hiddenTargetBody, appKey, key(),
                                "record-relation-target-hide")),
                422,
                "RECORD_RELATION_INVALID");
        assertError(new TestClient().get(
                        recordRoot + "/" + otherOwnerRecordId
                                + "/relations/related_orders?page=1&size=20",
                        signedOpenApiHeaders(
                                "GET", recordRoot + "/" + otherOwnerRecordId
                                        + "/relations/related_orders",
                                "page=1&size=20", "", appKey, key(),
                                "record-relation-row-hide")),
                404,
                "RECORD_NOT_FOUND");
        assertError(new TestClient().get(
                        recordRoot + "/" + crossTenantRecordId
                                + "/relations/related_orders?page=1&size=20",
                        signedOpenApiHeaders(
                                "GET", recordRoot + "/" + crossTenantRecordId
                                        + "/relations/related_orders",
                                "page=1&size=20", "", appKey, key(),
                                "record-relation-tenant-hide")),
                404,
                "RECORD_NOT_FOUND");

        var subtableRoot = recordPath + "/subtables/line_items";
        var subtableMutationBody = json(Map.of(
                "expectedVersion", 6,
                "add", List.of(Map.of(
                        "clientRowKey", "signed-line-1",
                        "ordinal", 0,
                        "values", Map.of(
                                "route", "openapi-subtable-sensitive-line"))),
                "update", List.of(),
                "remove", List.of(),
                "order", List.of()
        ));
        var subtableMutationKey = key();
        var subtableMutation = new TestClient().post(
                subtableRoot + ":mutate",
                subtableMutationBody,
                signedOpenApiHeaders(
                        "POST", subtableRoot + ":mutate", null,
                        subtableMutationBody, appKey, subtableMutationKey,
                        "record-subtable-mutate"));
        assertOk(subtableMutation);
        assertThat(subtableMutation.body().at("/data/version").asLong()).isEqualTo(7);
        var subtableReplay = new TestClient().post(
                subtableRoot + ":mutate",
                subtableMutationBody,
                signedOpenApiHeaders(
                        "POST", subtableRoot + ":mutate", null,
                        subtableMutationBody, appKey, subtableMutationKey,
                        "record-subtable-replay"));
        assertOk(subtableReplay);
        assertThat(subtableReplay.body().at("/data/version").asLong()).isEqualTo(7);
        var changedSubtableBody = json(Map.of(
                "expectedVersion", 6,
                "add", List.of(),
                "update", List.of(),
                "remove", List.of(),
                "order", List.of()
        ));
        assertError(new TestClient().post(
                        subtableRoot + ":mutate",
                        changedSubtableBody,
                        signedOpenApiHeaders(
                                "POST", subtableRoot + ":mutate", null,
                                changedSubtableBody, appKey, subtableMutationKey,
                                "record-subtable-conflict")),
                409,
                "IDEMPOTENCY_CONFLICT");

        var subtable = new TestClient().get(
                subtableRoot + "?page=1&size=20",
                signedOpenApiHeaders(
                        "GET", subtableRoot, "page=1&size=20", "",
                        appKey, key(), "record-subtable-list"));
        assertOk(subtable);
        assertThat(subtable.body().at("/data/total").asLong()).isOne();
        assertThat(text(item(subtable.body().at("/data/items/0/values"),
                "fieldCode", "route"), "/value"))
                .isEqualTo("openapi-subtable-sensitive-line");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT version FROM un_module_record WHERE system_id=? AND tenant_id=? AND record_id=?",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(firstTenantId),
                Long.parseLong(recordId))).isEqualTo(7L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_audit_operation WHERE aggregate_type='RUNTIME_RECORD' "
                        + "AND aggregate_id=? AND operation_type='RECORD_RELATION_MUTATED' "
                        + "AND result='SUCCESS'",
                Integer.class,
                recordId)).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_audit_operation WHERE aggregate_type='RUNTIME_RECORD' "
                        + "AND aggregate_id=? AND operation_type='RECORD_SUBTABLE_MUTATED' "
                        + "AND result='SUCCESS'",
                Integer.class,
                recordId)).isOne();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_audit_operation "
                        + "WHERE aggregate_type='RUNTIME_RECORD' "
                        + "AND aggregate_id=? AND source_type='OPENAPI' "
                        + "AND result='SUCCESS'",
                Integer.class,
                recordId)).isEqualTo(8);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_record_history "
                        + "WHERE system_id=? AND tenant_id=? AND record_id=?",
                Integer.class,
                Long.parseLong(systemId),
                Long.parseLong(firstTenantId),
                Long.parseLong(recordId))).isEqualTo(8);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT route_template) FROM un_openapi_call_log "
                        + "WHERE application_id=? AND route_template IN ("
                        + "'/openapi/v1/modules/{moduleCode}/records/{recordId}:activate',"
                        + "'/openapi/v1/modules/{moduleCode}/records/{recordId}:archive',"
                        + "'/openapi/v1/modules/{moduleCode}/records/{recordId}:unarchive',"
                        + "'/openapi/v1/modules/{moduleCode}/records/{recordId}:trash',"
                        + "'/openapi/v1/modules/{moduleCode}/records/{recordId}:restore-from-trash')",
                Integer.class,
                Long.parseLong(applicationId))).isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_openapi_call_log "
                        + "WHERE application_id=? AND route_template IN ("
                        + "'/openapi/v1/modules/{moduleCode}/records',"
                        + "'/openapi/v1/modules/{moduleCode}/records/{recordId}')",
                Integer.class,
                Long.parseLong(applicationId))).isGreaterThanOrEqualTo(6);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT route_template) FROM un_openapi_call_log "
                        + "WHERE application_id=? AND route_template IN ("
                        + "'/openapi/v1/modules/{moduleCode}/records/{recordId}/files',"
                        + "'/openapi/v1/modules/{moduleCode}/records/{recordId}/files/{fileId}/content')",
                Integer.class,
                Long.parseLong(applicationId))).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_openapi_call_log "
                        + "WHERE application_id=? AND route_template="
                        + "'/openapi/v1/flow/instances/{instanceId}' "
                        + "AND request_method='GET'",
                Integer.class,
                Long.parseLong(applicationId))).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT route_template) FROM un_openapi_call_log "
                        + "WHERE application_id=? AND route_template IN ("
                        + "'/openapi/v1/modules/{moduleCode}/records/{recordId}/relations/{fieldCode}',"
                        + "'/openapi/v1/modules/{moduleCode}/records/{recordId}/relations/{fieldCode}:mutate',"
                        + "'/openapi/v1/modules/{moduleCode}/records/{recordId}/subtables/{fieldCode}',"
                        + "'/openapi/v1/modules/{moduleCode}/records/{recordId}/subtables/{fieldCode}:mutate')",
                Integer.class,
                Long.parseLong(applicationId))).isEqualTo(4);
        var serializedLogs = String.join("\n", jdbcTemplate.queryForList(
                "SELECT CONCAT_WS('|',app_key_hash,route_template,request_method,"
                        + "result_category,http_status,request_id,trace_id) "
                        + "FROM un_openapi_call_log WHERE application_id=?",
                String.class,
                Long.parseLong(applicationId)));
        assertThat(serializedLogs)
                .doesNotContain(appKey)
                .doesNotContain(OPENAPI_RECORD_SECRET)
                .doesNotContain("openapi-sensitive-route")
                .doesNotContain("openapi-updated-sensitive-route")
                .doesNotContain("openapi-draft-sensitive-route")
                .doesNotContain("openapi-subtable-sensitive-line")
                .doesNotContain("openapi-evidence.txt")
                .doesNotContain(encodedFileContent)
                .doesNotContain(createKey)
                .doesNotContain(updateKey)
                .doesNotContain(fileUploadKey)
                .doesNotContain(relationMutationKey)
                .doesNotContain(subtableMutationKey);

        jdbcTemplate.update(
                "UPDATE un_plat_permission SET status='DISABLED',"
                        + "updated_at=UTC_TIMESTAMP(3),version=version+1 "
                        + "WHERE system_id=? AND permission_code='flow.instance.read'",
                Long.parseLong(systemId));
        try {
            assertError(new TestClient().get(
                            flowStatusPath,
                            signedOpenApiHeaders(
                                    "GET", flowStatusPath, null, "",
                                    appKey, key(), "flow-status-permission-revoked")),
                    403,
                    "OPENAPI_PERMISSION_DENIED");
        } finally {
            jdbcTemplate.update(
                    "UPDATE un_plat_permission SET status='ACTIVE',"
                            + "updated_at=UTC_TIMESTAMP(3),version=version+1 "
                            + "WHERE system_id=? AND permission_code='flow.instance.read'",
                    Long.parseLong(systemId));
        }

        roleAdmin = login();
        assertOk(roleAdmin.postWithCsrf(
                "/api/v1/context/systems/" + systemId + ":switch",
                "{}",
                Map.of()));
        publishOpenApiRecordRole(
                roleAdmin, systemId, serviceRoleId, selfDataScopeId,
                true, true, false, false, true);
        var deniedFileBody = json(Map.of(
                "originalName", "denied.txt",
                "mediaType", "text/plain",
                "contentBase64", Base64.getEncoder().encodeToString(new byte[]{1})
        ));
        assertError(new TestClient().post(
                        fileRoot,
                        deniedFileBody,
                        signedOpenApiHeaders(
                                "POST", fileRoot, null, deniedFileBody,
                                appKey, key(), "record-file-permission-revoked")),
                403,
                "FILE_FORBIDDEN");
        var revokedUpdateBody = json(Map.of(
                "expectedVersion", 7,
                "values", Map.of("route", "mutation-must-not-run")
        ));
        assertError(new TestClient().put(
                        recordPath,
                        revokedUpdateBody,
                        signedOpenApiHeaders(
                                "PUT", recordPath, null, revokedUpdateBody,
                                appKey, key(), "record-mutation-revoked")),
                403,
                "PERMISSION_DENIED");
        var revokedSubtableBody = json(Map.of(
                "expectedVersion", 7,
                "add", List.of(),
                "update", List.of(),
                "remove", List.of(),
                "order", List.of()
        ));
        assertError(new TestClient().post(
                        subtableRoot + ":mutate",
                        revokedSubtableBody,
                        signedOpenApiHeaders(
                                "POST", subtableRoot + ":mutate", null,
                                revokedSubtableBody, appKey, key(),
                                "record-subtable-permission-revoked")),
                403,
                "PERMISSION_DENIED");

        roleAdmin = login();
        assertOk(roleAdmin.postWithCsrf(
                "/api/v1/context/systems/" + systemId + ":switch",
                "{}",
                Map.of()));
        publishOpenApiRecordRole(
                roleAdmin, systemId, serviceRoleId, selfDataScopeId,
                true, false, false, true, false);
        assertError(new TestClient().get(
                        recordRoot + "/" + recordId,
                        signedOpenApiHeaders(
                                "GET", recordRoot + "/" + recordId,
                                null, "", appKey, key(), "record-permission-revoked")),
                403,
                "PERMISSION_DENIED");
        assertError(new TestClient().get(
                        fileRoot + "?page=1&size=20",
                        signedOpenApiHeaders(
                                "GET", fileRoot, "page=1&size=20", "",
                                appKey, key(), "record-file-read-revoked")),
                403,
                "PERMISSION_DENIED");
        assertError(new TestClient().get(
                        relationRoot + "?page=1&size=20",
                        signedOpenApiHeaders(
                                "GET", relationRoot, "page=1&size=20", "",
                                appKey, key(), "record-relation-read-revoked")),
                403,
                "PERMISSION_DENIED");

        roleAdmin = login();
        assertOk(roleAdmin.postWithCsrf(
                "/api/v1/context/systems/" + systemId + ":switch",
                "{}",
                Map.of()));
        publishOpenApiRecordRole(
                roleAdmin, systemId, serviceRoleId, originalDataScopeId,
                false, false, false, false, false);
    }

    private void publishOpenApiRecordRole(
            TestClient roleAdmin,
            String systemId,
            String roleId,
            String dataScopeId,
            boolean allowCreate,
            boolean allowRead,
            boolean allowMutation,
            boolean allowFileWrite,
            boolean allowFileRead
    ) throws Exception {
        var roleRoot = "/api/v1/systems/" + systemId + "/admin/roles";
        var roles = roleAdmin.get(roleRoot + "?page=1&size=200");
        assertOk(roles);
        var role = item(roles.body().at("/data/items"), "id", roleId);
        var permissions = new java.util.ArrayList<>(List.of(
                "system.runtime.access",
                "flow.instance.start",
                "flow.instance.decide",
                "flow.instance.read",
                "flow.instance.withdraw",
                "flow.instance.terminate",
                "flow.instance.urge",
                "flow.instance.comment",
                "flow.instance.transfer",
                "flow.instance.add-sign",
                "flow.instance.return",
                "flow.instance.claim",
                "flow.instance.cancel-claim",
                "flow.instance.reduce-sign",
                "flow.instance.copy",
                "event.message.access"
        ));
        if (allowCreate) {
            permissions.add("module." + FLOW_RECORD_MODULE_CODE + ".create");
        }
        if (allowRead) {
            permissions.add("module." + FLOW_RECORD_MODULE_CODE + ".view");
        }
        if (allowMutation) {
            permissions.add("module." + FLOW_RECORD_MODULE_CODE + ".update");
            permissions.add("module." + FLOW_RECORD_MODULE_CODE + ".delete");
            permissions.add("module." + FLOW_RECORD_MODULE_CODE + ".action.archive");
            permissions.add("module." + FLOW_RECORD_MODULE_CODE + ".action.unarchive");
            permissions.add("module." + FLOW_RECORD_MODULE_CODE + ".action.restore_trash");
        }
        if (allowFileWrite) {
            permissions.add("file.create");
            permissions.add("file.reference");
        }
        if (allowFileRead) {
            permissions.add("file.read");
        }
        var draft = roleAdmin.putWithCsrf(
                roleRoot + "/" + roleId + "/draft",
                json(Map.of(
                        "name", "Flow Second Approver",
                        "description", "Flow approvals and scoped OpenAPI records",
                        "permissionCodes", permissions,
                        "deniedPermissionCodes", List.of(),
                        "dataScopeId", dataScopeId,
                        "version", role.path("version").asText()
                )),
                Map.of());
        assertOk(draft);
        var checked = roleAdmin.postWithCsrf(
                roleRoot + "/" + roleId + "/draft:check",
                json(Map.of("version", text(draft.body(), "/data/version"))),
                Map.of("Idempotency-Key", key()));
        assertOk(checked);
        assertOk(roleAdmin.postWithCsrf(
                roleRoot + "/" + roleId + "/draft:publish",
                json(Map.of("version", text(checked.body(), "/data/version"))),
                Map.of("Idempotency-Key", key())));
    }

    private String createSelfDataScope(String systemId, String actorMemberId) {
        var system = Long.parseLong(systemId);
        var actorMember = Long.parseLong(actorMemberId);
        var createdBy = jdbcTemplate.queryForObject(
                "SELECT account_id FROM un_plat_member WHERE system_id=? AND id=?",
                Long.class,
                system,
                actorMember);
        var dataScopeId = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(id),0)+1 FROM un_plat_data_scope",
                Long.class);
        assertThat(createdBy).isNotNull();
        assertThat(dataScopeId).isNotNull();
        assertThat(jdbcTemplate.update(
                "INSERT INTO un_plat_data_scope ("
                        + "id,scope_type,scope_key,system_id,tenant_id,"
                        + "scope_code,name,scope_kind,field_rule_json,"
                        + "is_builtin,status,created_at,created_by,"
                        + "updated_at,updated_by,version) VALUES ("
                        + "?,'SYSTEM',?,?,NULL,?,?,'SELF',NULL,0,'ACTIVE',"
                        + "NOW(3),?,NOW(3),?,0)",
                dataScopeId,
                system,
                system,
                "openapi_record_self_" + UUID.randomUUID().toString().replace("-", ""),
                "OpenAPI record self scope",
                createdBy,
                createdBy)).isOne();
        return Long.toString(dataScopeId);
    }

    private static Map<String, String> signedOpenApiHeaders(
            String method,
            String rawPath,
            String rawQuery,
            String body,
            String appKey,
            String idempotencyKey,
            String noncePrefix
    ) {
        var timestamp = Long.toString(Instant.now().getEpochSecond());
        var nonce = noncePrefix + "-" + UUID.randomUUID();
        var canonical = OpenApiCanonicalRequest.canonical(
                method,
                rawPath,
                rawQuery,
                body.getBytes(StandardCharsets.UTF_8),
                timestamp,
                nonce,
                idempotencyKey
        );
        return Map.of(
                "X-App-Key", appKey,
                "X-Timestamp", timestamp,
                "X-Nonce", nonce,
                "X-Signature", OpenApiCanonicalRequest.signature(
                        OPENAPI_RECORD_SECRET.getBytes(StandardCharsets.UTF_8),
                        canonical),
                "Idempotency-Key", idempotencyKey
        );
    }

    private void exerciseOrdinaryScopedDashboardStatistics(
            String systemId,
            String tenantId,
            String originalOwnerMemberId,
            String visibleRecordId
    ) throws Exception {
        var suffix = Long.toUnsignedString(System.nanoTime(), 36);
        var username = "statistics_scope_" + suffix;
        var password = "Statistics-Scope-Password-84!";
        var registration = new TestClient().post(
                "/api/v1/auth/register",
                json(Map.of(
                        "username", username,
                        "displayName", "Statistics Scope Member",
                        "password", password,
                        "systemName", "Statistics Scope Home",
                        "systemCode", "statistics_scope_home_" + suffix)),
                Map.of("Idempotency-Key", key()));
        assertOk(registration);

        var roleAdmin = login();
        assertOk(roleAdmin.postWithCsrf(
                "/api/v1/context/systems/" + systemId + ":switch",
                "{}", Map.of()));
        var system = Long.parseLong(systemId);
        var tenant = Long.parseLong(tenantId);
        var ownerMember = Long.parseLong(originalOwnerMemberId);
        var createdBy = jdbcTemplate.queryForObject(
                "SELECT account_id FROM un_plat_member "
                        + "WHERE system_id=? AND id=?",
                Long.class, system, ownerMember);
        var selfScopeId = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(id),0)+1 FROM un_plat_data_scope",
                Long.class);
        assertThat(createdBy).isNotNull();
        assertThat(selfScopeId).isNotNull();
        assertThat(jdbcTemplate.update(
                "INSERT INTO un_plat_data_scope ("
                        + "id,scope_type,scope_key,system_id,tenant_id,"
                        + "scope_code,name,scope_kind,field_rule_json,"
                        + "is_builtin,status,created_at,created_by,"
                        + "updated_at,updated_by,version) VALUES ("
                        + "?,'SYSTEM',?,?,NULL,?,?,'SELF',NULL,0,'ACTIVE',"
                        + "NOW(3),?,NOW(3),?,0)",
                selfScopeId, system, system,
                "statistics_self_" + suffix,
                "Statistics self scope", createdBy, createdBy)).isOne();

        var roleRoot = "/api/v1/systems/" + systemId + "/admin/roles";
        var role = roleAdmin.postWithCsrf(
                roleRoot,
                json(Map.of(
                        "code", "statistics_scope_" + suffix,
                        "name", "Statistics Scope Member",
                        "description", "SELF-scoped native statistics")),
                Map.of("Idempotency-Key", key()));
        assertOk(role);
        var roleId = text(role.body(), "/data/id");
        var roleDraft = roleAdmin.putWithCsrf(
                roleRoot + "/" + roleId + "/draft",
                json(Map.of(
                        "name", "Statistics Scope Member",
                        "description", "SELF-scoped native statistics",
                        "permissionCodes", List.of(
                                "system.runtime.access",
                                "module." + FLOW_RECORD_MODULE_CODE + ".view"),
                        "deniedPermissionCodes", List.of(),
                        "dataScopeId", Long.toString(selfScopeId),
                        "version", text(role.body(), "/data/version"))),
                Map.of());
        assertOk(roleDraft);
        var checked = roleAdmin.postWithCsrf(
                roleRoot + "/" + roleId + "/draft:check",
                json(Map.of("version", text(roleDraft.body(), "/data/version"))),
                Map.of("Idempotency-Key", key()));
        assertOk(checked);
        var published = roleAdmin.postWithCsrf(
                roleRoot + "/" + roleId + "/draft:publish",
                json(Map.of("version", text(checked.body(), "/data/version"))),
                Map.of("Idempotency-Key", key()));
        assertOk(published);

        var ordinary = login(username, password);
        var access = ordinary.postWithCsrf(
                "/api/v1/context/systems/" + systemId + "/access-requests",
                json(Map.of(
                        "targetTenantId", tenantId,
                        "reason", "Verify SELF-scoped database statistics")),
                Map.of("Idempotency-Key", key()));
        assertOk(access);
        var approver = login();
        assertOk(approver.postWithCsrf(
                "/api/v1/context/systems/" + systemId + ":switch",
                "{}", Map.of()));
        assertOk(approver.postWithCsrf(
                "/api/v1/systems/" + systemId + "/admin/access-requests/"
                        + text(access.body(), "/data/id") + ":approve",
                json(Map.of(
                        "reason", "Approve statistics scope verification",
                        "version", text(access.body(), "/data/version"),
                        "tenantIds", List.of(tenantId),
                        "roleIds", List.of(roleId))),
                Map.of("Idempotency-Key", key())));
        var switched = ordinary.postWithCsrf(
                "/api/v1/context/systems/" + systemId + ":switch",
                "{}", Map.of());
        assertOk(switched);
        var scopedMemberId = text(switched.body(), "/data/context/memberId");
        assertThat(switched.body().at("/data/context/permissions"))
                .extracting(JsonNode::asText)
                .contains("module." + FLOW_RECORD_MODULE_CODE + ".view");

        var recordId = Long.parseLong(visibleRecordId);
        try {
            assertThat(jdbcTemplate.update(
                    "UPDATE un_module_record SET owner_member_id=? "
                            + "WHERE system_id=? AND tenant_id=? "
                            + "AND record_id=? AND owner_member_id=?",
                    Long.parseLong(scopedMemberId), system, tenant, recordId,
                    ownerMember)).isOne();
            var home = ordinary.get(
                    "/api/v1/systems/" + systemId
                            + "/dashboards/system-home");
            assertOk(home);
            assertThat(item(home.body().at("/data/widgets"),
                    "code", "total").path("total").asLong()).isOne();
            assertThat(item(home.body().at("/data/widgets"),
                    "code", "metric").path("statisticsResult")
                    .path("value").asText()).isEqualTo("1");
            assertThat(item(home.body().at("/data/widgets"),
                    "code", "byRoute").path("statisticsResult")
                    .path("groupBuckets").get(0)
                    .path("recordCount").asLong()).isOne();
            assertThat(item(home.body().at("/data/widgets"),
                    "code", "records").path("rows"))
                    .extracting(row -> row.path("recordId").asText())
                    .containsExactly(visibleRecordId);
        } finally {
            jdbcTemplate.update(
                    "UPDATE un_module_record SET owner_member_id=? "
                            + "WHERE system_id=? AND tenant_id=? "
                            + "AND record_id=?",
                    ownerMember, system, tenant, recordId);
        }
    }

    private PublishedSource exercisePublishedDataSource(
            TestClient client,
            String systemId,
            String firstTenantId,
            String secondTenantId,
            FlowRecordSchema module,
            String expectedRecordId
    ) throws Exception {
        var adminRoot = "/api/v1/systems/" + systemId
                + "/admin/data-sources";
        var runtimeRoot = "/api/v1/systems/" + systemId
                + "/data-sources";
        var code = "flow_empty_routes";

        var catalog = client.get(adminRoot + "/catalog");
        assertOk(catalog);
        var catalogModule = item(
                catalog.body().at("/data/modules"),
                "moduleId", module.moduleId());
        assertThat(text(catalogModule, "/moduleCode"))
                .isEqualTo(FLOW_RECORD_MODULE_CODE);
        assertThat(item(catalogModule.path("fields"),
                "fieldCode", "route").path("operators"))
                .extracting(JsonNode::asText)
                .contains("EMPTY");
        assertThat(item(catalogModule.path("fields"),
                "fieldCode", "event_time").path("temporal").asBoolean())
                .isTrue();

        var created = client.postWithCsrf(adminRoot, json(Map.of(
                "code", code,
                "moduleId", module.moduleId(),
                "name", "Published empty routes",
                "description", "Native module query source"
        )), Map.of("Idempotency-Key", key()));
        assertOk(created);
        var sourceId = text(created.body(), "/data/id");
        assertThat(created.body().at("/data/draftVersion").asLong())
                .isOne();

        var saved = client.putWithCsrf(
                adminRoot + "/" + sourceId + "/draft",
                json(Map.ofEntries(
                        Map.entry("expectedVersion", 1),
                        Map.entry("name", "Published empty routes"),
                        Map.entry("description", "Native module query source"),
                        Map.entry("outputFields", List.of(
                                Map.of("fieldCode", "route"),
                                Map.of("fieldCode", "event_time"))),
                        Map.entry("fixedFilters", List.of(Map.of(
                                "fieldCode", "route",
                                "operator", "EMPTY"))),
                        Map.entry("defaultSort", Map.of(
                                "fieldCode", "route",
                                "direction", "ASC"))
                )), Map.of());
        assertOk(saved);
        assertThat(saved.body().at("/data/draftVersion").asLong())
                .isEqualTo(2);

        var checked = client.postWithCsrf(
                adminRoot + "/" + sourceId + "/draft:check",
                "{}", Map.of());
        assertOk(checked);
        assertThat(checked.body().at("/data/valid").asBoolean()).isTrue();
        assertThat(checked.body().at("/data/blockerCount").asLong())
                .isZero();

        var published = client.postWithCsrf(
                adminRoot + "/" + sourceId + "/draft:publish",
                json(Map.of("expectedVersion", 2)),
                Map.of("Idempotency-Key", key()));
        assertOk(published);
        var publishedVersionId = text(
                published.body(), "/data/version/id");
        assertThat(text(published.body(), "/data/version/schemaVersionId"))
                .isEqualTo(module.versionId());

        var replayed = client.postWithCsrf(
                adminRoot + "/" + sourceId + "/draft:publish",
                json(Map.of("expectedVersion", 2)),
                Map.of("Idempotency-Key", key()));
        assertOk(replayed);
        assertThat(text(replayed.body(), "/data/version/id"))
                .isEqualTo(publishedVersionId);
        var versions = client.get(adminRoot + "/" + sourceId + "/versions");
        assertOk(versions);
        assertThat(versions.body().at("/data")).hasSize(1);

        var metadata = client.get(runtimeRoot + "/" + code);
        assertOk(metadata);
        assertThat(text(metadata.body(), "/data/name"))
                .isEqualTo("Published empty routes");
        assertThat(text(metadata.body(), "/data/outputFields/0/fieldCode"))
                .isEqualTo("route");
        var rows = client.get(runtimeRoot + "/" + code
                + "/rows?page=1&size=20");
        assertOk(rows);
        assertThat(rows.body().at("/data/total").asLong())
                .isEqualTo(3);
        assertThat(rows.body().at("/data/rows"))
                .anySatisfy(row -> assertThat(
                        row.path("recordId").asText())
                        .isEqualTo(expectedRecordId));

        var statisticsCapabilities = client.get(
                runtimeRoot + "/" + code + "/statistics-capabilities");
        assertOk(statisticsCapabilities);
        assertThat(statisticsCapabilities.body().at(
                "/data/dataSourceId").isTextual()).isTrue();
        assertThat(text(statisticsCapabilities.body(),
                "/data/dataSourceVersionId")).isEqualTo(publishedVersionId);
        assertThat(item(statisticsCapabilities.body().at("/data/fields"),
                "code", "event_time").path("temporal").asBoolean()).isTrue();

        var scalarStatistics = client.postWithCsrf(
                runtimeRoot + "/" + code + ":statistics",
                json(Map.of("aggregation", "COUNT")), Map.of());
        assertOk(scalarStatistics);
        assertThat(text(scalarStatistics.body(), "/data/value"))
                .isEqualTo("3");
        assertThat(scalarStatistics.body().at(
                "/data/matchedRecordCount").asLong()).isEqualTo(3);
        assertThat(scalarStatistics.body().at("/data/bucketCount").asInt())
                .isZero();

        var groupedStatistics = client.postWithCsrf(
                runtimeRoot + "/" + code + ":statistics",
                json(Map.of(
                        "aggregation", "COUNT",
                        "grouping", Map.of(
                                "fieldCode", "route",
                                "bucketLimit", 10))), Map.of());
        assertOk(groupedStatistics);
        assertThat(groupedStatistics.body().at("/data/groupBuckets"))
                .hasSize(1);
        assertThat(groupedStatistics.body().at(
                "/data/groupBuckets/0/nullBucket").asBoolean()).isTrue();
        assertThat(text(groupedStatistics.body(),
                "/data/groupBuckets/0/value")).isEqualTo("3");

        var trendStatistics = client.postWithCsrf(
                runtimeRoot + "/" + code + ":statistics",
                json(Map.of(
                        "aggregation", "COUNT",
                        "trend", Map.of(
                                "fieldCode", "event_time",
                                "grain", "DAY",
                                "startInclusive", "2026-07-01",
                                "endExclusive", "2026-07-04"))), Map.of());
        assertOk(trendStatistics);
        assertThat(trendStatistics.body().at("/data/trendBuckets"))
                .hasSize(3)
                .allSatisfy(bucket -> {
                    assertThat(bucket.path("empty").asBoolean()).isTrue();
                    assertThat(bucket.path("value").asText()).isEqualTo("0");
                });

        var draftOnly = client.putWithCsrf(
                adminRoot + "/" + sourceId + "/draft",
                json(Map.ofEntries(
                        Map.entry("expectedVersion", 2),
                        Map.entry("name", "Draft-only changed name"),
                        Map.entry("description", "Must not affect runtime"),
                        Map.entry("outputFields", List.of(
                                Map.of("fieldCode", "route"),
                                Map.of("fieldCode", "event_time"))),
                        Map.entry("fixedFilters", List.of(Map.of(
                                "fieldCode", "route", "operator", "EQ",
                                "canonicalValue", "no-match"))),
                        Map.entry("defaultSort", Map.of(
                                "fieldCode", "route", "direction", "DESC"))
                )), Map.of());
        assertOk(draftOnly);
        assertThat(draftOnly.body().at("/data/draftVersion").asLong())
                .isEqualTo(3);
        var publishedMetadata = client.get(runtimeRoot + "/" + code);
        assertOk(publishedMetadata);
        assertThat(text(publishedMetadata.body(), "/data/name"))
                .isEqualTo("Published empty routes");
        var publishedRows = client.get(runtimeRoot + "/" + code
                + "/rows?page=1&size=20");
        assertOk(publishedRows);
        assertThat(publishedRows.body().at("/data/total").asLong())
                .isEqualTo(3);

        var httpSecretRef = "env://EXAMINE_DS_S" + systemId
                + "_T" + firstTenantId + "_BATCH103_V1";
        var httpCreated = client.postWithCsrf(
                adminRoot,
                """
                {"code":"http_connection_103","moduleId":"%s",
                 "name":"HTTP connection 103",
                 "description":"Draft-only safe HTTP connection",
                 "sourceKind":"HTTP_JSON",
                 "httpJsonConnection":{
                   "endpoint":"https://datasource.example.test/rows",
                   "authSecretRef":"%s","timeoutSeconds":3}}
                """.formatted(module.moduleId(), httpSecretRef),
                Map.of("Idempotency-Key", key()));
        assertOk(httpCreated);
        var httpSourceId = text(httpCreated.body(), "/data/id");
        assertThat(text(httpCreated.body(), "/data/draft/sourceKind"))
                .isEqualTo("HTTP_JSON");
        assertThat(text(httpCreated.body(),
                "/data/draft/httpJsonConnection/authSecretRef"))
                .isEqualTo(httpSecretRef);
        var persistedHttpDraft = jdbcTemplate.queryForObject(
                "SELECT draft_json FROM un_module_data_source "
                        + "WHERE system_id=? AND tenant_id=? "
                        + "AND id=?",
                String.class,
                Long.parseLong(systemId),
                Long.parseLong(firstTenantId),
                Long.parseLong(httpSourceId));
        assertThat(persistedHttpDraft)
                .contains(httpSecretRef)
                .doesNotContain("Bearer ")
                .doesNotContain("Authorization");

        var unavailableSecret = client.postWithCsrf(
                adminRoot + "/" + httpSourceId
                        + "/draft:connection-check",
                json(Map.of("expectedVersion", 1)), Map.of());
        assertOk(unavailableSecret);
        assertThat(text(unavailableSecret.body(), "/data/code"))
                .isEqualTo("SECRET_UNAVAILABLE");
        assertThat(unavailableSecret.body().at("/data/reachable")
                .asBoolean()).isFalse();
        assertThat(unavailableSecret.body().toString())
                .doesNotContain(httpSecretRef)
                .doesNotContain("datasource.example.test");
        assertThat(webhookTransport.dataSourceRequests()).isEmpty();

        var httpSaved = client.putWithCsrf(
                adminRoot + "/" + httpSourceId + "/draft",
                """
                {"expectedVersion":1,"name":"HTTP connection 103",
                 "description":"Draft-only safe HTTP connection",
                 "sourceKind":"HTTP_JSON",
                 "httpJsonConnection":{
                   "endpoint":"https://datasource.example.test/rows",
                   "authSecretRef":null,"timeoutSeconds":3},
                 "outputFields":[],"fixedFilters":[],
                 "defaultSort":null,"defaultTimeFieldCode":null}
                """,
                Map.of());
        assertOk(httpSaved);
        assertThat(httpSaved.body().at("/data/draftVersion").asLong())
                .isEqualTo(2);

        var connection = client.postWithCsrf(
                adminRoot + "/" + httpSourceId
                        + "/draft:connection-check",
                json(Map.of("expectedVersion", 2)), Map.of());
        assertOk(connection);
        assertThat(text(connection.body(), "/data/code"))
                .isEqualTo("SUCCESS");
        assertThat(connection.body().at("/data/reachable").asBoolean())
                .isTrue();
        assertThat(connection.body().at("/data/contractValid").asBoolean())
                .isTrue();
        assertThat(connection.body().at("/data/httpStatus").asInt())
                .isEqualTo(200);
        assertThat(connection.body().at("/data/durationMillis").asLong())
                .isEqualTo(7);
        assertThat(webhookTransport.dataSourceRequests()).hasSize(1);
        var connectionRequest = webhookTransport.dataSourceRequests()
                .getFirst();
        assertThat(connectionRequest.uri()).isEqualTo(
                URI.create("https://datasource.example.test/rows"));
        assertThat(connectionRequest.headers())
                .containsOnly(
                        Map.entry("Content-Type", "application/json"),
                        Map.entry("Accept", "application/json"));
        assertThat(new String(connectionRequest.body(),
                StandardCharsets.UTF_8)).isEqualTo(
                "{\"page\":1,\"size\":1}");
        assertThat(connectionRequest.timeout()).isEqualTo(
                Duration.ofSeconds(3));

        var discovered = client.postWithCsrf(
                adminRoot + "/" + httpSourceId
                        + "/draft:schema-discovery",
                json(Map.of("expectedVersion", 2)), Map.of());
        assertOk(discovered);
        assertThat(text(discovered.body(), "/data/code"))
                .isEqualTo("SUCCESS");
        assertThat(discovered.body().at("/data/checkedDraftVersion").asLong())
                .isEqualTo(2);
        assertThat(discovered.body().at("/data/reachable").asBoolean())
                .isTrue();
        assertThat(discovered.body().at("/data/contractValid").asBoolean())
                .isTrue();
        assertThat(discovered.body().at("/data/fields"))
                .anySatisfy(field -> {
                    assertThat(field.path("sourceField").asText())
                            .isEqualTo("route");
                    assertThat(field.path("inferredType").asText())
                            .isEqualTo("STRING");
                    assertThat(field.path("selectable").asBoolean()).isTrue();
                });
        assertThat(discovered.body().toString())
                .doesNotContain("row-1")
                .doesNotContain("datasource.example.test")
                .doesNotContain(httpSecretRef);
        assertThat(webhookTransport.dataSourceRequests()).hasSize(2);
        var discoveryRequest = webhookTransport.dataSourceRequests()
                .get(1);
        assertThat(discoveryRequest.headers())
                .containsOnly(
                        Map.entry("Content-Type", "application/json"),
                        Map.entry("Accept", "application/json"));
        assertThat(new String(discoveryRequest.body(),
                StandardCharsets.UTF_8)).isEqualTo(
                "{\"page\":1,\"size\":25}");
        assertThat(discoveryRequest.timeout()).isEqualTo(
                Duration.ofSeconds(3));

        assertError(client.postWithCsrf(
                        adminRoot + "/" + httpSourceId
                                + "/draft:rows-preview",
                        json(Map.of("expectedVersion", 2)), Map.of()),
                422, "DATA_SOURCE_HTTP_PREVIEW_BLOCKED");
        assertThat(webhookTransport.dataSourceRequests()).hasSize(2);

        var projectionSaved = client.putWithCsrf(
                adminRoot + "/" + httpSourceId + "/draft",
                """
                {"expectedVersion":2,"name":"HTTP connection 103",
                 "description":"Mapped safe HTTP schema",
                 "sourceKind":"HTTP_JSON",
                 "httpJsonConnection":{
                   "endpoint":"https://datasource.example.test/rows",
                   "authSecretRef":null,"timeoutSeconds":3},
                 "httpFieldProjections":[{
                   "sourceField":"route","fieldCode":"route",
                   "sourceType":"STRING"}],
                 "outputFields":[],"fixedFilters":[],
                 "defaultSort":null,"defaultTimeFieldCode":null}
                """,
                Map.of());
        assertOk(projectionSaved);
        assertThat(projectionSaved.body().at("/data/draftVersion").asLong())
                .isEqualTo(3);
        assertThat(text(projectionSaved.body(),
                "/data/draft/httpFieldProjections/0/sourceField"))
                .isEqualTo("route");
        assertThat(text(projectionSaved.body(),
                "/data/draft/httpFieldProjections/0/fieldCode"))
                .isEqualTo("route");
        var mappedHttpDraft = jdbcTemplate.queryForObject(
                "SELECT draft_json FROM un_module_data_source "
                        + "WHERE system_id=? AND tenant_id=? AND id=?",
                String.class, Long.parseLong(systemId),
                Long.parseLong(firstTenantId), Long.parseLong(httpSourceId));
        assertThat(mappedHttpDraft)
                .contains("httpFieldProjections")
                .contains("sourceField")
                .contains("route")
                .doesNotContain("row-1")
                .doesNotContain("Bearer ")
                .doesNotContain("Authorization");

        assertError(client.postWithCsrf(
                        adminRoot + "/" + httpSourceId
                                + "/draft:rows-preview",
                        json(Map.of("expectedVersion", 2)), Map.of()),
                409, "DATA_SOURCE_VERSION_CONFLICT");
        assertThat(webhookTransport.dataSourceRequests()).hasSize(2);

        var previewed = client.postWithCsrf(
                adminRoot + "/" + httpSourceId
                        + "/draft:rows-preview",
                json(Map.of("expectedVersion", 3)), Map.of());
        assertOk(previewed);
        assertThat(text(previewed.body(), "/data/code"))
                .isEqualTo("SUCCESS");
        assertThat(previewed.body().at("/data/checkedDraftVersion").asLong())
                .isEqualTo(3);
        assertThat(text(previewed.body(), "/data/fields/0/fieldCode"))
                .isEqualTo("route");
        assertThat(text(previewed.body(), "/data/fields/0/sourceType"))
                .isEqualTo("STRING");
        assertThat(previewed.body().at("/data/rows/0/rowIndex").asInt())
                .isEqualTo(1);
        assertThat(text(previewed.body(), "/data/rows/0/values/route"))
                .isEqualTo("external");
        assertThat(previewed.body().toString())
                .doesNotContain("sourceField")
                .doesNotContain("row-1")
                .doesNotContain("active")
                .doesNotContain("datasource.example.test")
                .doesNotContain(httpSecretRef)
                .doesNotContain("recordId")
                .doesNotContain("total");
        assertThat(webhookTransport.dataSourceRequests()).hasSize(3);
        var previewRequest = webhookTransport.dataSourceRequests().get(2);
        assertThat(previewRequest.headers())
                .containsOnly(
                        Map.entry("Content-Type", "application/json"),
                        Map.entry("Accept", "application/json"));
        assertThat(new String(previewRequest.body(),
                StandardCharsets.UTF_8)).isEqualTo(
                "{\"page\":1,\"size\":25}");
        assertThat(previewRequest.timeout()).isEqualTo(Duration.ofSeconds(3));
        var previewDidNotPersist = jdbcTemplate.queryForObject(
                "SELECT draft_json FROM un_module_data_source "
                        + "WHERE system_id=? AND tenant_id=? AND id=?",
                String.class, Long.parseLong(systemId),
                Long.parseLong(firstTenantId), Long.parseLong(httpSourceId));
        assertThat(previewDidNotPersist)
                .doesNotContain("external")
                .doesNotContain("row-1")
                .doesNotContain("Authorization")
                .doesNotContain("Bearer ");

        var httpChecked = client.postWithCsrf(
                adminRoot + "/" + httpSourceId + "/draft:check",
                "{}", Map.of());
        assertOk(httpChecked);
        assertThat(httpChecked.body().at("/data/issues"))
                .anySatisfy(issue -> assertThat(issue.path("code").asText())
                        .isEqualTo("SOURCE_RUNTIME_UNAVAILABLE"));
        var httpPublished = client.postWithCsrf(
                adminRoot + "/" + httpSourceId + "/draft:publish",
                json(Map.of("expectedVersion", 3)),
                Map.of("Idempotency-Key", key()));
        assertOk(httpPublished);
        var httpPublishedVersionId = text(
                httpPublished.body(), "/data/version/id");
        assertThat(text(httpPublished.body(),
                "/data/version/snapshot/sourceKind"))
                .isEqualTo("HTTP_JSON");
        assertThat(httpPublished.body().toString())
                .doesNotContain("row-1")
                .doesNotContain("external")
                .doesNotContain(httpSecretRef)
                .doesNotContain("Authorization")
                .doesNotContain("Bearer ");
        assertThat(webhookTransport.dataSourceRequests()).hasSize(4);
        var publicationRequest = webhookTransport.dataSourceRequests().get(3);
        assertThat(publicationRequest.headers())
                .containsOnly(
                        Map.entry("Content-Type", "application/json"),
                        Map.entry("Accept", "application/json"));
        assertThat(new String(publicationRequest.body(),
                StandardCharsets.UTF_8)).isEqualTo(
                "{\"page\":1,\"size\":25}");
        assertThat(publicationRequest.timeout()).isEqualTo(
                Duration.ofSeconds(3));

        var httpReplayed = client.postWithCsrf(
                adminRoot + "/" + httpSourceId + "/draft:publish",
                json(Map.of("expectedVersion", 3)),
                Map.of("Idempotency-Key", key()));
        assertOk(httpReplayed);
        assertThat(text(httpReplayed.body(), "/data/version/id"))
                .isEqualTo(httpPublishedVersionId);
        assertThat(webhookTransport.dataSourceRequests()).hasSize(4);
        var httpVersions = client.get(
                adminRoot + "/" + httpSourceId + "/versions");
        assertOk(httpVersions);
        assertThat(httpVersions.body().at("/data")).hasSize(1);

        var persistedHttpSnapshot = jdbcTemplate.queryForObject(
                "SELECT snapshot_json FROM un_module_data_source_version "
                        + "WHERE system_id=? AND tenant_id=? "
                        + "AND data_source_id=? AND id=?",
                String.class, Long.parseLong(systemId),
                Long.parseLong(firstTenantId), Long.parseLong(httpSourceId),
                Long.parseLong(httpPublishedVersionId));
        assertThat(persistedHttpSnapshot)
                .contains("HTTP_JSON")
                .contains("httpFieldProjections")
                .contains("route")
                .doesNotContain("row-1")
                .doesNotContain("external")
                .doesNotContain(httpSecretRef)
                .doesNotContain("Authorization")
                .doesNotContain("Bearer ");

        var activeHttpRows = client.get(runtimeRoot
                + "/http_connection_103/http-rows");
        assertOk(activeHttpRows);
        assertThat(text(activeHttpRows.body(), "/data/dataSourceId"))
                .isEqualTo(httpSourceId);
        assertThat(text(activeHttpRows.body(), "/data/dataSourceCode"))
                .isEqualTo("http_connection_103");
        assertThat(text(activeHttpRows.body(), "/data/dataSourceVersionId"))
                .isEqualTo(httpPublishedVersionId);
        assertThat(activeHttpRows.body().at(
                "/data/dataSourceVersionNumber").asInt())
                .isEqualTo(1);
        assertThat(text(activeHttpRows.body(), "/data/fields/0/fieldCode"))
                .isEqualTo("route");
        assertThat(text(activeHttpRows.body(), "/data/fields/0/sourceType"))
                .isEqualTo("STRING");
        assertThat(activeHttpRows.body().at("/data/rows/0/rowIndex").asInt())
                .isEqualTo(1);
        assertThat(text(activeHttpRows.body(), "/data/rows/0/values/route"))
                .isEqualTo("external");
        assertThat(activeHttpRows.body().toString())
                .doesNotContain("sourceField")
                .doesNotContain("row-1")
                .doesNotContain("datasource.example.test")
                .doesNotContain(httpSecretRef)
                .doesNotContain("recordId")
                .doesNotContain("recordNo")
                .doesNotContain("total")
                .doesNotContain("queryHash");
        assertThat(webhookTransport.dataSourceRequests()).hasSize(5);
        assertThat(new String(webhookTransport.dataSourceRequests().get(4)
                .body(), StandardCharsets.UTF_8))
                .isEqualTo("{\"page\":1,\"size\":25}");

        var pinnedHttpRows = client.get(runtimeRoot + "/" + httpSourceId
                + "/versions/" + httpPublishedVersionId + "/http-rows");
        assertOk(pinnedHttpRows);
        assertThat(pinnedHttpRows.body().at("/data"))
                .isEqualTo(activeHttpRows.body().at("/data"));
        assertThat(webhookTransport.dataSourceRequests()).hasSize(6);
        assertThat(new String(webhookTransport.dataSourceRequests().get(5)
                .body(), StandardCharsets.UTF_8))
                .isEqualTo("{\"page\":1,\"size\":25}");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_data_source_version "
                        + "WHERE system_id=? AND tenant_id=? "
                        + "AND data_source_id=?",
                Integer.class, Long.parseLong(systemId),
                Long.parseLong(firstTenantId),
                Long.parseLong(httpSourceId))).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT draft_json FROM un_module_data_source "
                        + "WHERE system_id=? AND tenant_id=? AND id=?",
                String.class, Long.parseLong(systemId),
                Long.parseLong(firstTenantId),
                Long.parseLong(httpSourceId)))
                .doesNotContain("external")
                .doesNotContain("row-1");

        var httpRuntimeMetadata = client.get(runtimeRoot + "/http_connection_103");
        assertOk(httpRuntimeMetadata);
        assertThat(text(httpRuntimeMetadata.body(), "/data/versionId"))
                .isEqualTo(httpPublishedVersionId);
        var httpRuntimeRows = client.get(runtimeRoot + "/http_connection_103"
                + "/rows?page=1&size=20");
        assertOk(httpRuntimeRows);
        assertThat(httpRuntimeRows.body().at("/data/total").asLong()).isEqualTo(1);
        var httpStatisticsCapabilities = client.get(runtimeRoot + "/http_connection_103"
                + "/statistics-capabilities");
        assertOk(httpStatisticsCapabilities);
        assertThat(text(httpStatisticsCapabilities.body(), "/data/sourceKind"))
                .isEqualTo("HTTP_JSON");
        var httpStatistics = client.postWithCsrf(
                runtimeRoot + "/http_connection_103:statistics",
                json(Map.of("aggregation", "COUNT")), Map.of());
        assertOk(httpStatistics);
        assertThat(text(httpStatistics.body(), "/data/value")).isEqualTo("1");
        assertThat(webhookTransport.dataSourceRequests()).hasSize(8);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS datasource_cycle108_rows (
                  route VARCHAR(64) NOT NULL,
                  amount DECIMAL(18,2) NULL
                )
                """);
        jdbcTemplate.update("DELETE FROM datasource_cycle108_rows");
        jdbcTemplate.update(
                "INSERT INTO datasource_cycle108_rows(route,amount) VALUES (?,?)",
                "jdbc-alpha", new java.math.BigDecimal("7.50"));
        jdbcTemplate.update(
                "INSERT INTO datasource_cycle108_rows(route,amount) VALUES (?,?)",
                "jdbc-beta", new java.math.BigDecimal("9.25"));
        var jdbcUsernameRef = "env://EXAMINE_DS_S" + systemId
                + "_T" + firstTenantId + "_JDBC_USERNAME_V1";
        var jdbcPasswordRef = "env://EXAMINE_DS_S" + systemId
                + "_T" + firstTenantId + "_JDBC_PASSWORD_V1";
        var jdbcCreated = client.postWithCsrf(
                adminRoot,
                """
                {"code":"jdbc_table_108","moduleId":"%s",
                 "name":"Cycle 108 MySQL table",
                 "description":"Read-only exact table source",
                 "sourceKind":"JDBC_TABLE",
                 "jdbcTableConnection":{
                   "host":"%s","port":%d,
                   "databaseName":"%s",
                   "tableName":"datasource_cycle108_rows",
                   "usernameSecretRef":"%s",
                   "passwordSecretRef":"%s",
                   "connectTimeoutSeconds":3,"queryTimeoutSeconds":3}}
                """.formatted(
                        module.moduleId(), MYSQL.getHost(),
                        MYSQL.getMappedPort(3306), MYSQL.getDatabaseName(),
                        jdbcUsernameRef, jdbcPasswordRef),
                Map.of("Idempotency-Key", key()));
        assertOk(jdbcCreated);
        var jdbcSourceId = text(jdbcCreated.body(), "/data/id");
        assertThat(text(jdbcCreated.body(), "/data/draft/sourceKind"))
                .isEqualTo("JDBC_TABLE");
        assertThat(jdbcCreated.body().at(
                "/data/draft/jdbcTableConnection/usernameConfigured")
                .asBoolean()).isTrue();
        assertThat(jdbcCreated.body().at(
                "/data/draft/jdbcTableConnection/passwordConfigured")
                .asBoolean()).isTrue();
        assertThat(jdbcCreated.body().toString())
                .doesNotContain(jdbcUsernameRef)
                .doesNotContain(jdbcPasswordRef);

        var jdbcConnection = client.postWithCsrf(
                adminRoot + "/" + jdbcSourceId
                        + "/draft:connection-check",
                json(Map.of("expectedVersion", 1)), Map.of());
        assertOk(jdbcConnection);
        assertThat(text(jdbcConnection.body(), "/data/code"))
                .isEqualTo("SUCCESS");
        assertThat(jdbcConnection.body().at("/data/reachable").asBoolean())
                .isTrue();
        assertThat(jdbcConnection.body().at(
                "/data/contractValid").asBoolean()).isTrue();
        assertThat(jdbcConnection.body().toString())
                .doesNotContain(jdbcUsernameRef)
                .doesNotContain(jdbcPasswordRef)
                .doesNotContain(MYSQL.getPassword());

        var jdbcSchema = client.postWithCsrf(
                adminRoot + "/" + jdbcSourceId
                        + "/draft:schema-discovery",
                json(Map.of("expectedVersion", 1)), Map.of());
        assertOk(jdbcSchema);
        assertThat(jdbcSchema.body().at("/data/fields"))
                .anySatisfy(field -> {
                    assertThat(field.path("sourceColumn").asText())
                            .isEqualTo("route");
                    assertThat(field.path("inferredType").asText())
                            .isEqualTo("STRING");
                    assertThat(field.path("selectable").asBoolean()).isTrue();
                });
        assertThat(jdbcSchema.body().toString())
                .doesNotContain("jdbc-alpha")
                .doesNotContain(jdbcUsernameRef)
                .doesNotContain(jdbcPasswordRef);

        var jdbcSaved = client.putWithCsrf(
                adminRoot + "/" + jdbcSourceId + "/draft",
                """
                {"expectedVersion":1,"name":"Cycle 108 MySQL table",
                 "description":"Mapped read-only table source",
                 "sourceKind":"JDBC_TABLE",
                 "jdbcTableConnection":{
                   "host":"%s","port":%d,
                   "databaseName":"%s",
                   "tableName":"datasource_cycle108_rows",
                   "usernameSecretRef":null,"passwordSecretRef":null,
                   "connectTimeoutSeconds":3,"queryTimeoutSeconds":3},
                 "jdbcFieldProjections":[{
                   "sourceColumn":"route","fieldCode":"route",
                   "sourceType":"STRING"}],
                 "outputFields":[],"fixedFilters":[],
                 "defaultSort":null,"defaultTimeFieldCode":null}
                """.formatted(MYSQL.getHost(), MYSQL.getMappedPort(3306),
                        MYSQL.getDatabaseName()), Map.of());
        assertOk(jdbcSaved);
        assertThat(jdbcSaved.body().at("/data/draftVersion").asLong())
                .isEqualTo(2);
        assertThat(jdbcSaved.body().at(
                "/data/draft/jdbcTableConnection/usernameConfigured")
                .asBoolean()).isTrue();
        assertThat(jdbcSaved.body().toString())
                .doesNotContain(jdbcUsernameRef)
                .doesNotContain(jdbcPasswordRef);

        var jdbcPreview = client.postWithCsrf(
                adminRoot + "/" + jdbcSourceId + "/draft:rows-preview",
                json(Map.of("expectedVersion", 2)), Map.of());
        assertOk(jdbcPreview);
        assertThat(text(jdbcPreview.body(), "/data/fields/0/fieldCode"))
                .isEqualTo("route");
        assertThat(jdbcPreview.body().at("/data/rows")).hasSize(2);
        assertThat(jdbcPreview.body().at("/data/rows"))
                .anySatisfy(row -> assertThat(
                        row.at("/values/route").asText())
                        .isEqualTo("jdbc-alpha"));
        assertThat(jdbcPreview.body().toString())
                .doesNotContain(jdbcUsernameRef)
                .doesNotContain(jdbcPasswordRef)
                .doesNotContain("sourceColumn");

        var jdbcChecked = client.postWithCsrf(
                adminRoot + "/" + jdbcSourceId + "/draft:check",
                "{}", Map.of());
        assertOk(jdbcChecked);
        assertThat(jdbcChecked.body().at("/data/valid").asBoolean()).isTrue();
        var jdbcPublished = client.postWithCsrf(
                adminRoot + "/" + jdbcSourceId + "/draft:publish",
                json(Map.of("expectedVersion", 2)),
                Map.of("Idempotency-Key", key()));
        assertOk(jdbcPublished);
        var jdbcPublishedVersionId = text(
                jdbcPublished.body(), "/data/version/id");
        assertThat(text(jdbcPublished.body(),
                "/data/version/snapshot/sourceKind"))
                .isEqualTo("JDBC_TABLE");
        assertThat(jdbcPublished.body().toString())
                .doesNotContain(jdbcUsernameRef)
                .doesNotContain(jdbcPasswordRef)
                .doesNotContain("jdbc-alpha");

        var activeJdbcRows = client.get(
                runtimeRoot + "/jdbc_table_108/jdbc-rows");
        assertOk(activeJdbcRows);
        assertThat(text(activeJdbcRows.body(), "/data/dataSourceId"))
                .isEqualTo(jdbcSourceId);
        assertThat(text(activeJdbcRows.body(),
                "/data/dataSourceVersionId"))
                .isEqualTo(jdbcPublishedVersionId);
        assertThat(text(activeJdbcRows.body(),
                "/data/rows/0/values/route")).isEqualTo("jdbc-alpha");
        assertThat(activeJdbcRows.body().at("/data/rows")).hasSize(2);
        assertThat(activeJdbcRows.body().toString())
                .doesNotContain(jdbcUsernameRef)
                .doesNotContain(jdbcPasswordRef)
                .doesNotContain("sourceColumn")
                .doesNotContain("recordId")
                .doesNotContain("total");
        var pinnedJdbcRows = client.get(runtimeRoot + "/" + jdbcSourceId
                + "/versions/" + jdbcPublishedVersionId + "/jdbc-rows");
        assertOk(pinnedJdbcRows);
        assertThat(pinnedJdbcRows.body().at("/data"))
                .isEqualTo(activeJdbcRows.body().at("/data"));

        var jdbcRuntimeMetadata = client.get(runtimeRoot + "/jdbc_table_108");
        assertOk(jdbcRuntimeMetadata);
        assertThat(text(jdbcRuntimeMetadata.body(), "/data/versionId"))
                .isEqualTo(jdbcPublishedVersionId);
        var jdbcRuntimeRows = client.get(runtimeRoot + "/jdbc_table_108"
                + "/rows?page=1&size=20");
        assertOk(jdbcRuntimeRows);
        assertThat(jdbcRuntimeRows.body().at("/data/total").asLong()).isEqualTo(2);
        var jdbcStatisticsCapabilities = client.get(runtimeRoot + "/jdbc_table_108"
                + "/statistics-capabilities");
        assertOk(jdbcStatisticsCapabilities);
        assertThat(text(jdbcStatisticsCapabilities.body(), "/data/sourceKind"))
                .isEqualTo("JDBC_TABLE");
        var jdbcStatistics = client.postWithCsrf(
                runtimeRoot + "/jdbc_table_108:statistics",
                json(Map.of("aggregation", "COUNT")), Map.of());
        assertOk(jdbcStatistics);
        assertThat(text(jdbcStatistics.body(), "/data/value")).isEqualTo("2");

        assertOk(client.postWithCsrf(
                "/api/v1/context/tenants/" + secondTenantId + ":switch",
                "{}", Map.of()));
        var isolatedSources = client.get(adminRoot);
        assertOk(isolatedSources);
        assertThat(isolatedSources.body().at("/data")).isEmpty();
        assertError(client.get(runtimeRoot + "/" + code),
                404, "DATA_SOURCE_NOT_FOUND");
        assertError(client.get(runtimeRoot
                        + "/http_connection_103/http-rows"),
                404, "DATA_SOURCE_NOT_FOUND");
        assertError(client.get(runtimeRoot + "/jdbc_table_108/jdbc-rows"),
                404, "DATA_SOURCE_NOT_FOUND");
        assertThat(webhookTransport.dataSourceRequests()).hasSize(8);
        assertOk(client.postWithCsrf(
                "/api/v1/context/tenants/" + firstTenantId + ":switch",
                "{}", Map.of()));
        return new PublishedSource(
                sourceId, code, publishedVersionId);
    }

    private PublishedKpi exercisePublishedKpi(
            TestClient client,
            String systemId,
            String firstTenantId,
            String secondTenantId,
            PublishedSource source,
            String memberId
    ) throws Exception {
        var adminRoot = "/api/v1/systems/" + systemId + "/admin/kpis";
        var runtimeRoot = "/api/v1/systems/" + systemId + "/kpis";
        var created = client.postWithCsrf(
                adminRoot,
                json(Map.ofEntries(
                        Map.entry("code", "monthly_flow_records"),
                        Map.entry("name", "Monthly flow records"),
                        Map.entry("description",
                                "Member-owned monthly exact-source KPI"),
                        Map.entry("dataSourceId", source.id()),
                        Map.entry("subjectType", "MEMBER"),
                        Map.entry("periodType", "MONTH"),
                        Map.entry("aggregation", "COUNT"),
                        Map.entry("timeFieldCode", "event_time"),
                        Map.entry("direction", "AT_LEAST"),
                        Map.entry("warningThreshold", "0.8"))),
                Map.of("Idempotency-Key", key()));
        assertOk(created);
        var kpiId = text(created.body(), "/data/id");
        assertThat(text(created.body(), "/data/draft/dataSourceId"))
                .isEqualTo(source.id());

        var checked = client.postWithCsrf(
                adminRoot + "/" + kpiId + "/draft:check",
                "{}", Map.of());
        assertOk(checked);
        assertThat(checked.body().at("/data/valid").asBoolean()).isTrue();
        assertThat(checked.body().at("/data/blockerCount").asLong()).isZero();

        var published = client.postWithCsrf(
                adminRoot + "/" + kpiId + "/draft:publish",
                json(Map.of("expectedVersion", 1)),
                Map.of("Idempotency-Key", key()));
        assertOk(published);
        var versionId = text(published.body(), "/data/version/id");
        assertThat(text(published.body(),
                "/data/version/dataSourceVersionId"))
                .isEqualTo(source.versionId());
        assertThat(text(published.body(), "/data/version/timeField/code"))
                .isEqualTo("event_time");

        var replayedPublish = client.postWithCsrf(
                adminRoot + "/" + kpiId + "/draft:publish",
                json(Map.of("expectedVersion", 1)),
                Map.of("Idempotency-Key", key()));
        assertOk(replayedPublish);
        assertThat(text(replayedPublish.body(), "/data/version/id"))
                .isEqualTo(versionId);

        var target = client.postWithCsrf(
                adminRoot + "/" + kpiId + "/targets",
                json(Map.of(
                        "subjectId", memberId,
                        "periodStart", "2026-08-01",
                        "targetValue", "0")),
                Map.of("Idempotency-Key", key()));
        assertOk(target);
        var targetId = text(target.body(), "/data/id");
        assertThat(text(target.body(), "/data/kpiVersionId"))
                .isEqualTo(versionId);

        var commandKey = key();
        var calculated = client.postWithCsrf(
                "/api/v1/systems/" + systemId
                        + "/admin/kpi-targets/" + targetId + ":calculate",
                "{}", Map.of("Idempotency-Key", commandKey));
        assertOk(calculated);
        assertThat(text(calculated.body(), "/data/status"))
                .isEqualTo("ACHIEVED");
        assertThat(text(calculated.body(), "/data/actualValue"))
                .isEqualTo("0");
        assertThat(text(calculated.body(),
                "/data/explanation/dataSourceVersionId"))
                .isEqualTo(source.versionId());
        assertThat(calculated.body().at("/data/explanation/trend"))
                .hasSize(1);

        var replayedCalculation = client.postWithCsrf(
                "/api/v1/systems/" + systemId
                        + "/admin/kpi-targets/" + targetId + ":calculate",
                "{}", Map.of("Idempotency-Key", commandKey));
        assertOk(replayedCalculation);
        assertThat(text(replayedCalculation.body(), "/data/id"))
                .isEqualTo(text(calculated.body(), "/data/id"));

        var history = client.get(
                "/api/v1/systems/" + systemId
                        + "/admin/kpi-targets/" + targetId
                        + "/calculations");
        assertOk(history);
        assertThat(history.body().at("/data")).hasSize(1);

        var runtime = client.get(runtimeRoot
                + "?periodType=MONTH&periodStart=2026-08-01");
        assertOk(runtime);
        assertThat(runtime.body().at("/data")).hasSize(1);
        assertThat(text(runtime.body(), "/data/0/id")).isEqualTo(targetId);
        assertThat(text(runtime.body(),
                "/data/0/latestCalculation/status"))
                .isEqualTo("ACHIEVED");

        assertOk(client.postWithCsrf(
                "/api/v1/context/tenants/" + secondTenantId + ":switch",
                "{}", Map.of()));
        var isolated = client.get(adminRoot);
        assertOk(isolated);
        assertThat(isolated.body().at("/data")).isEmpty();
        var isolatedRuntime = client.get(runtimeRoot
                + "?periodType=MONTH&periodStart=2026-08-01");
        assertOk(isolatedRuntime);
        assertThat(isolatedRuntime.body().at("/data")).isEmpty();
        assertOk(client.postWithCsrf(
                "/api/v1/context/tenants/" + firstTenantId + ":switch",
                "{}", Map.of()));
        return new PublishedKpi(kpiId, versionId, targetId);
    }

    private void exercisePublishedDashboard(
            TestClient client,
            String systemId,
            String firstTenantId,
            String secondTenantId,
            PublishedSource source,
            PublishedKpi kpi,
            String expectedRecordId,
            String originalOwnerMemberId
    ) throws Exception {
        var adminRoot = "/api/v1/systems/" + systemId
                + "/admin/dashboards";
        var runtimeRoot = "/api/v1/systems/" + systemId
                + "/dashboards";
        var sourceAdminRoot = "/api/v1/systems/" + systemId
                + "/admin/data-sources/" + source.id();
        var sourceRuntimeRoot = "/api/v1/systems/" + systemId
                + "/data-sources/" + source.code();

        var created = client.postWithCsrf(adminRoot, json(Map.of(
                "code", "system_home",
                "placement", "SYSTEM_HOME",
                "name", "Flow system home",
                "description", "Published native dashboard"
        )), Map.of("Idempotency-Key", key()));
        assertOk(created);
        var dashboardId = text(created.body(), "/data/id");
        assertThat(created.body().at("/data/draftVersion").asLong()).isOne();

        var widgets = List.of(
                Map.of(
                        "code", "total",
                        "type", "STAT_COUNT",
                        "title", "Total empty routes",
                        "dataSourceId", source.id(),
                        "grid", Map.of(
                                "x", 0, "y", 0,
                                "width", 3, "height", 2)),
                Map.of(
                        "code", "metric",
                        "type", "STAT_VALUE",
                        "title", "Native metric",
                        "dataSourceId", source.id(),
                        "statistics", Map.of(
                                "aggregation", "COUNT"),
                        "grid", Map.of(
                                "x", 3, "y", 0,
                                "width", 3, "height", 2)),
                Map.of(
                        "code", "byRoute",
                        "type", "BAR_CHART",
                        "title", "By route",
                        "dataSourceId", source.id(),
                        "statistics", Map.of(
                                "aggregation", "COUNT",
                                "grouping", Map.of(
                                        "fieldCode", "route",
                                        "bucketLimit", 10)),
                        "grid", Map.of(
                                "x", 6, "y", 0,
                                "width", 3, "height", 3)),
                Map.of(
                        "code", "daily",
                        "type", "LINE_TREND",
                        "title", "Daily trend",
                        "dataSourceId", source.id(),
                        "statistics", Map.of(
                                "aggregation", "COUNT",
                                "trend", Map.of(
                                        "fieldCode", "event_time",
                                        "grain", "DAY",
                                        "startInclusive", "2026-07-01",
                                        "endExclusive", "2026-07-04")),
                        "grid", Map.of(
                                "x", 9, "y", 0,
                                "width", 3, "height", 3)),
                Map.of(
                        "code", "records",
                        "type", "DATA_LIST",
                        "title", "Empty route records",
                        "dataSourceId", source.id(),
                        "rowLimit", 10,
                        "grid", Map.of(
                                "x", 0, "y", 3,
                                "width", 12, "height", 4)),
                Map.of(
                        "code", "monthlyKpi",
                        "type", "KPI_VALUE",
                        "title", "Monthly flow target",
                        "kpiId", kpi.id(),
                        "grid", Map.of(
                                "x", 0, "y", 7,
                                "width", 12, "height", 3)));
        var saved = client.putWithCsrf(
                adminRoot + "/" + dashboardId + "/draft",
                json(Map.of(
                        "expectedVersion", 1,
                        "name", "Flow system home",
                        "description", "Published native dashboard",
                        "widgets", widgets)), Map.of());
        assertOk(saved);
        assertThat(saved.body().at("/data/draftVersion").asLong())
                .isEqualTo(2);

        var checked = client.postWithCsrf(
                adminRoot + "/" + dashboardId + "/draft:check",
                "{}", Map.of());
        assertOk(checked);
        assertThat(checked.body().at("/data/valid").asBoolean()).isTrue();
        assertThat(checked.body().at("/data/blockerCount").asLong()).isZero();

        var published = client.postWithCsrf(
                adminRoot + "/" + dashboardId + "/draft:publish",
                json(Map.of("expectedVersion", 2)),
                Map.of("Idempotency-Key", key()));
        assertOk(published);
        var dashboardVersionId = text(published.body(), "/data/version/id");
        assertThat(text(published.body(),
                "/data/version/widgets/0/dataSourceVersionId"))
                .isEqualTo(source.versionId());
        assertThat(published.body().at(
                "/data/version/widgets/2/statistics/groupField/logicalFieldId")
                .isTextual()).isTrue();
        assertThat(text(published.body(),
                "/data/version/widgets/2/statistics/groupField/code"))
                .isEqualTo("route");
        assertThat(text(published.body(),
                "/data/version/widgets/3/statistics/timeField/code"))
                .isEqualTo("event_time");
        var publishedKpiWidget = item(
                published.body().at("/data/version/widgets"),
                "code", "monthlyKpi");
        assertThat(publishedKpiWidget.path("kpiId").asText())
                .isEqualTo(kpi.id());
        assertThat(publishedKpiWidget.path("kpiVersionId").asText())
                .isEqualTo(kpi.versionId());
        assertThat(publishedKpiWidget.path("kpiCode").asText())
                .isEqualTo("monthly_flow_records");
        assertThat(published.body().at(
                "/data/version/sourceDraftVersion").asLong()).isEqualTo(2);

        var replay = client.postWithCsrf(
                adminRoot + "/" + dashboardId + "/draft:publish",
                json(Map.of("expectedVersion", 2)),
                Map.of("Idempotency-Key", key()));
        assertOk(replay);
        assertThat(text(replay.body(), "/data/version/id"))
                .isEqualTo(dashboardVersionId);
        var versions = client.get(
                adminRoot + "/" + dashboardId + "/versions");
        assertOk(versions);
        assertThat(versions.body().at("/data")).hasSize(1);

        var home = client.get(runtimeRoot + "/system-home");
        assertOk(home);
        assertThat(text(home.body(), "/data/versionId"))
                .isEqualTo(dashboardVersionId);
        var count = item(home.body().at("/data/widgets"), "code", "total");
        var records = item(
                home.body().at("/data/widgets"), "code", "records");
        assertThat(count.path("status").asText()).isEqualTo("OK");
        assertThat(count.path("total").asLong()).isEqualTo(3);
        assertThat(records.path("status").asText()).isEqualTo("OK");
        assertThat(records.path("total").asLong()).isEqualTo(3);
        assertThat(records.path("rows"))
                .anySatisfy(row -> assertThat(
                        row.path("recordId").asText())
                        .isEqualTo(expectedRecordId));
        var metric = item(home.body().at("/data/widgets"),
                "code", "metric");
        assertThat(metric.path("statisticsResult").path("value").asText())
                .isEqualTo("3");
        assertThat(metric.path("statisticsResult")
                .path("dataSourceVersionId").asText())
                .isEqualTo(source.versionId());
        var grouped = item(home.body().at("/data/widgets"),
                "code", "byRoute");
        assertThat(grouped.path("statisticsResult")
                .path("groupBuckets")).hasSize(1);
        assertThat(grouped.path("statisticsResult")
                .path("groupBuckets").get(0)
                .path("nullBucket").asBoolean()).isTrue();
        var trend = item(home.body().at("/data/widgets"),
                "code", "daily");
        assertThat(trend.path("statisticsResult")
                .path("trendBuckets")).hasSize(3);
        var kpiWidget = item(home.body().at("/data/widgets"),
                "code", "monthlyKpi");
        assertThat(kpiWidget.path("status").asText()).isEqualTo("OK");
        assertThat(kpiWidget.path("kpiVersionId").asText())
                .isEqualTo(kpi.versionId());
        assertThat(kpiWidget.path("kpiTargets")).hasSize(1);
        assertThat(kpiWidget.path("kpiTargets").get(0)
                .path("id").asText()).isEqualTo(kpi.targetId());
        assertThat(kpiWidget.path("kpiTargets").get(0)
                .path("latestCalculation").path("status").asText())
                .isEqualTo("ACHIEVED");
        var byCode = client.get(runtimeRoot + "/system_home");
        assertOk(byCode);
        assertThat(text(byCode.body(), "/data/versionId"))
                .isEqualTo(dashboardVersionId);

        var revisedKpi = client.putWithCsrf(
                "/api/v1/systems/" + systemId + "/admin/kpis/"
                        + kpi.id() + "/draft",
                json(Map.ofEntries(
                        Map.entry("expectedVersion", 1),
                        Map.entry("name", "Monthly flow records revised"),
                        Map.entry("description", "Later KPI publication"),
                        Map.entry("dataSourceId", source.id()),
                        Map.entry("subjectType", "MEMBER"),
                        Map.entry("periodType", "MONTH"),
                        Map.entry("aggregation", "COUNT"),
                        Map.entry("timeFieldCode", "event_time"),
                        Map.entry("direction", "AT_LEAST"),
                        Map.entry("warningThreshold", "0.8"))), Map.of());
        assertOk(revisedKpi);
        var republishedKpi = client.postWithCsrf(
                "/api/v1/systems/" + systemId + "/admin/kpis/"
                        + kpi.id() + "/draft:publish",
                json(Map.of("expectedVersion", 2)),
                Map.of("Idempotency-Key", key()));
        assertOk(republishedKpi);
        assertThat(text(republishedKpi.body(), "/data/version/id"))
                .isNotEqualTo(kpi.versionId());
        var stillPinnedToKpiVersion = client.get(
                runtimeRoot + "/system-home");
        assertOk(stillPinnedToKpiVersion);
        var pinnedKpiWidget = item(
                stillPinnedToKpiVersion.body().at("/data/widgets"),
                "code", "monthlyKpi");
        assertThat(pinnedKpiWidget.path("kpiVersionId").asText())
                .isEqualTo(kpi.versionId());
        assertThat(pinnedKpiWidget.path("kpiTargets")).hasSize(1);

        var republishedSource = client.postWithCsrf(
                sourceAdminRoot + "/draft:publish",
                json(Map.of("expectedVersion", 3)),
                Map.of("Idempotency-Key", key()));
        assertOk(republishedSource);
        assertThat(text(republishedSource.body(), "/data/version/id"))
                .isNotEqualTo(source.versionId());
        var latestSourceRows = client.get(
                sourceRuntimeRoot + "/rows?page=1&size=20");
        assertOk(latestSourceRows);
        assertThat(latestSourceRows.body().at("/data/total").asLong())
                .isZero();

        var stillPinned = client.get(runtimeRoot + "/system-home");
        assertOk(stillPinned);
        assertThat(item(stillPinned.body().at("/data/widgets"),
                "code", "total").path("total").asLong()).isEqualTo(3);
        assertThat(text(stillPinned.body(),
                "/data/widgets/0/dataSourceVersionId"))
                .isEqualTo(source.versionId());
        assertThat(item(stillPinned.body().at("/data/widgets"),
                "code", "metric").path("statisticsResult")
                .path("value").asText()).isEqualTo("3");
        exerciseOrdinaryScopedDashboardStatistics(
                systemId, firstTenantId, originalOwnerMemberId,
                expectedRecordId);
        assertOk(client.postWithCsrf(
                "/api/v1/auth/refresh", "{}", Map.of()));
        var republishedDashboard = client.postWithCsrf(
                adminRoot + "/" + dashboardId + "/draft:publish",
                json(Map.of("expectedVersion", 2)),
                Map.of("Idempotency-Key", key()));
        assertOk(republishedDashboard);
        assertThat(text(republishedDashboard.body(), "/data/version/id"))
                .isNotEqualTo(dashboardVersionId);
        assertThat(text(republishedDashboard.body(),
                "/data/version/widgets/0/dataSourceVersionId"))
                .isEqualTo(text(republishedSource.body(), "/data/version/id"));
        var latestHome = client.get(runtimeRoot + "/system-home");
        assertOk(latestHome);
        assertThat(item(latestHome.body().at("/data/widgets"),
                "code", "metric").path("statisticsResult")
                .path("value").asText()).isEqualTo("0");
        var republishedVersions = client.get(
                adminRoot + "/" + dashboardId + "/versions");
        assertOk(republishedVersions);
        assertThat(republishedVersions.body().at("/data")).hasSize(2);
        var draftOnly = client.putWithCsrf(
                adminRoot + "/" + dashboardId + "/draft",
                json(Map.of(
                        "expectedVersion", 2,
                        "name", "Draft-only dashboard name",
                        "description", "Must not affect dashboard runtime",
                        "widgets", widgets)), Map.of());
        assertOk(draftOnly);
        assertThat(draftOnly.body().at("/data/draftVersion").asLong())
                .isEqualTo(3);
        var publishedRuntime = client.get(runtimeRoot + "/system-home");
        assertOk(publishedRuntime);
        assertThat(text(publishedRuntime.body(), "/data/name"))
                .isEqualTo("Flow system home");

        assertOk(client.postWithCsrf(
                "/api/v1/context/tenants/" + secondTenantId + ":switch",
                "{}", Map.of()));
        var isolated = client.get(adminRoot);
        assertOk(isolated);
        assertThat(isolated.body().at("/data")).isEmpty();
        assertError(client.get(runtimeRoot + "/system-home"),
                404, "DASHBOARD_NOT_FOUND");
        assertOk(client.postWithCsrf(
                "/api/v1/context/tenants/" + firstTenantId + ":switch",
                "{}", Map.of()));
    }

    private void exerciseKpiUnmetReminder(
            TestClient client,
            String systemId,
            String firstTenantId,
            String secondTenantId,
            PublishedKpi kpi
    ) throws Exception {
        var messagesRoot = "/api/v1/systems/" + systemId
                + "/event/messages?status=UNREAD&page=1&size=100";
        var before = client.get(messagesRoot);
        assertOk(before);
        assertThat(items(before.body().at("/data/items"),
                "templateCode", "KPI_UNMET")).isEmpty();

        var updated = client.putWithCsrf(
                "/api/v1/systems/" + systemId
                        + "/admin/kpi-targets/" + kpi.targetId(),
                json(Map.of("targetValue", "10", "expectedVersion", 1)),
                Map.of());
        assertOk(updated);
        var commandKey = key();
        var calculated = client.postWithCsrf(
                "/api/v1/systems/" + systemId
                        + "/admin/kpi-targets/" + kpi.targetId()
                        + ":calculate",
                "{}", Map.of("Idempotency-Key", commandKey));
        assertOk(calculated);
        assertThat(text(calculated.body(), "/data/status"))
                .isEqualTo("MISSED");
        var calculationId = text(calculated.body(), "/data/id");

        var replay = client.postWithCsrf(
                "/api/v1/systems/" + systemId
                        + "/admin/kpi-targets/" + kpi.targetId()
                        + ":calculate",
                "{}", Map.of("Idempotency-Key", commandKey));
        assertOk(replay);
        assertThat(text(replay.body(), "/data/id")).isEqualTo(calculationId);

        var inbox = client.get(messagesRoot);
        assertOk(inbox);
        var reminders = items(inbox.body().at("/data/items"),
                "templateCode", "KPI_UNMET");
        assertThat(reminders).singleElement().satisfies(message -> {
            assertThat(message.path("status").asText()).isEqualTo("UNREAD");
            assertThat(message.path("target").path("type").asText())
                    .isEqualTo("KPI_TARGET");
            assertThat(message.path("target").path("id").asText())
                    .isEqualTo(kpi.targetId());
            assertThat(message.path("targetPath").asText()).isEqualTo(
                    "/systems/" + systemId
                            + "/kpis?periodType=MONTH&periodStart=2026-08-01");
        });
        var followed = client.get(
                "/api/v1/systems/" + systemId
                        + "/kpis?periodType=MONTH&periodStart=2026-08-01");
        assertOk(followed);
        assertThat(text(followed.body(),
                "/data/0/latestCalculation/status")).isEqualTo("MISSED");

        assertOk(client.postWithCsrf(
                "/api/v1/context/tenants/" + secondTenantId + ":switch",
                "{}", Map.of()));
        var isolated = client.get(messagesRoot);
        assertOk(isolated);
        assertThat(items(isolated.body().at("/data/items"),
                "templateCode", "KPI_UNMET")).isEmpty();
        assertOk(client.postWithCsrf(
                "/api/v1/context/tenants/" + firstTenantId + ":switch",
                "{}", Map.of()));
    }

    private String exercisePublishedReport(
            TestClient client,
            TestClient recipientClient,
            String systemId,
            String firstTenantId,
            String secondTenantId,
            PublishedSource source,
            String expectedRecordId,
            String recipientMemberId
    ) throws Exception {
        var adminRoot = "/api/v1/systems/" + systemId + "/admin/reports";
        var runtimeRoot = "/api/v1/systems/" + systemId + "/reports";
        var sourceAdminRoot = "/api/v1/systems/" + systemId
                + "/admin/data-sources";
        var code = "flow_empty_route_report";

        var created = client.postWithCsrf(adminRoot, json(Map.of(
                "code", code,
                "name", "Flow empty route report",
                "description", "Pinned native data-source report",
                "dataSourceId", source.id(),
                "outputFieldCodes", List.of("route")
        )), Map.of("Idempotency-Key", key()));
        assertOk(created);
        var reportId = text(created.body(), "/data/id");
        assertThat(created.body().at("/data/draftVersion").asLong()).isOne();

        var checked = client.postWithCsrf(
                adminRoot + "/" + reportId + "/draft:check",
                "{}", Map.of());
        assertOk(checked);
        assertThat(checked.body().at("/data/valid").asBoolean()).isTrue();
        var firstSourceVersionId = text(
                checked.body(), "/data/source/dataSourceVersionId");
        assertThat(text(checked.body(), "/data/source/fields/0/code"))
                .isEqualTo("route");

        var published = client.postWithCsrf(
                adminRoot + "/" + reportId + "/draft:publish",
                json(Map.of("expectedVersion", 1)),
                Map.of("Idempotency-Key", key()));
        assertOk(published);
        var firstReportVersionId = text(published.body(), "/data/version/id");
        assertThat(text(published.body(),
                "/data/version/dataSourceVersionId"))
                .isEqualTo(firstSourceVersionId);
        assertThat(text(published.body(), "/data/version/fields/0/code"))
                .isEqualTo("route");

        var replayed = client.postWithCsrf(
                adminRoot + "/" + reportId + "/draft:publish",
                json(Map.of("expectedVersion", 1)),
                Map.of("Idempotency-Key", key()));
        assertOk(replayed);
        assertThat(text(replayed.body(), "/data/version/id"))
                .isEqualTo(firstReportVersionId);

        var runtimeCatalog = client.get(runtimeRoot);
        assertOk(runtimeCatalog);
        assertThat(item(runtimeCatalog.body().at("/data"), "code", code)
                .path("fields")).extracting(value -> value.path("fieldCode").asText())
                .containsExactly("route");
        var firstRows = client.get(runtimeRoot + "/" + code
                + "/rows?page=1&size=20");
        assertOk(firstRows);
        assertThat(firstRows.body().at("/data/total").asLong()).isZero();

        var revisedSource = client.putWithCsrf(
                sourceAdminRoot + "/" + source.id() + "/draft",
                json(Map.ofEntries(
                        Map.entry("expectedVersion", 3),
                        Map.entry("name", "Report source restored"),
                        Map.entry("description", "Restored empty route filter"),
                        Map.entry("outputFields", List.of(
                                Map.of("fieldCode", "route"),
                                Map.of("fieldCode", "event_time"))),
                        Map.entry("fixedFilters", List.of(Map.of(
                                "fieldCode", "route",
                                "operator", "EMPTY"))),
                        Map.entry("defaultSort", Map.of(
                                "fieldCode", "route", "direction", "ASC"))
                )), Map.of());
        assertOk(revisedSource);
        assertThat(revisedSource.body().at("/data/draftVersion").asLong())
                .isEqualTo(4);

        var republishedSource = client.postWithCsrf(
                sourceAdminRoot + "/" + source.id() + "/draft:publish",
                json(Map.of("expectedVersion", 4)),
                Map.of("Idempotency-Key", key()));
        assertOk(republishedSource);
        var secondSourceVersionId = text(
                republishedSource.body(), "/data/version/id");
        assertThat(secondSourceVersionId).isNotEqualTo(source.versionId());

        var stillPinnedRows = client.get(runtimeRoot + "/" + code
                + "/rows?page=1&size=20");
        assertOk(stillPinnedRows);
        assertThat(stillPinnedRows.body().at("/data/total").asLong())
                .isZero();

        var revised = client.putWithCsrf(
                adminRoot + "/" + reportId + "/draft",
                json(Map.of(
                        "expectedVersion", 1,
                        "name", "Flow empty route report v2",
                        "description", "Pins the next source publication",
                        "dataSourceId", source.id(),
                        "outputFieldCodes", List.of("event_time", "route")
                )), Map.of());
        assertOk(revised);
        assertThat(revised.body().at("/data/draftVersion").asLong())
                .isEqualTo(2);
        var secondPublished = client.postWithCsrf(
                adminRoot + "/" + reportId + "/draft:publish",
                json(Map.of("expectedVersion", 2)),
                Map.of("Idempotency-Key", key()));
        assertOk(secondPublished);
        var secondReportVersionId = text(
                secondPublished.body(), "/data/version/id");
        assertThat(secondPublished.body().at("/data/version/versionNumber")
                .asInt()).isEqualTo(2);
        assertThat(text(secondPublished.body(),
                "/data/version/dataSourceVersionId"))
                .isEqualTo(secondSourceVersionId);
        assertThat(secondPublished.body().at("/data/version/fields"))
                .extracting(value -> value.path("code").asText())
                .containsExactly("event_time", "route");

        var activeRows = client.get(runtimeRoot + "/" + code
                + "/rows?page=1&size=20");
        assertOk(activeRows);
        assertThat(activeRows.body().at("/data/total").asLong()).isEqualTo(3);
        assertThat(activeRows.body().at("/data/rows"))
                .anySatisfy(row -> assertThat(row.path("recordId").asText())
                        .isEqualTo(expectedRecordId));
        var firstVersion = client.get(adminRoot + "/" + reportId
                + "/versions/1");
        assertOk(firstVersion);
        assertThat(text(firstVersion.body(), "/data/dataSourceVersionId"))
                .isEqualTo(firstSourceVersionId);
        assertThat(firstVersion.body().at("/data/fields"))
                .extracting(value -> value.path("code").asText())
                .containsExactly("route");

        var exportRoot = runtimeRoot + "/" + code + "/exports";
        var exportKey = key();
        var startedExport = client.postWithCsrf(
                exportRoot,
                json(Map.of("requestKey", exportKey)),
                Map.of("Idempotency-Key", exportKey));
        assertAccepted(startedExport);
        var exportId = text(startedExport.body(), "/data/exportId");
        assertThat(text(startedExport.body(), "/data/reportVersionId"))
                .isEqualTo(secondReportVersionId);
        assertThat(startedExport.body().at("/data/fieldCodes"))
                .extracting(JsonNode::asText)
                .containsExactly("event_time", "route");

        var replayedExport = client.postWithCsrf(
                exportRoot,
                json(Map.of("requestKey", exportKey)),
                Map.of("Idempotency-Key", exportKey));
        assertAccepted(replayedExport);
        assertThat(text(replayedExport.body(), "/data/exportId"))
                .isEqualTo(exportId);

        var completedExport = awaitReportExport(
                client, exportRoot, exportId, "SUCCEEDED");
        assertThat(completedExport.body().at("/data/totalRows").asLong())
                .isEqualTo(3);
        assertThat(completedExport.body().at("/data/processedRows").asInt())
                .isEqualTo(3);
        assertThat(completedExport.body().at("/data/truncated").asBoolean())
                .isFalse();
        long deliveredNotificationCount = 0;
        for (var attempt = 0; attempt < 200; attempt++) {
            deliveredNotificationCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM un_event_message_delivery_log "
                            + "WHERE system_id=? AND tenant_id=? "
                            + "AND dedupe_key=? AND status='DELIVERED'",
                    Long.class, systemId, firstTenantId,
                    "job-result:report-export:" + exportId + ":SUCCEEDED");
            if (deliveredNotificationCount == 1) {
                break;
            }
            Thread.sleep(25);
        }
        assertThat(deliveredNotificationCount).isOne();
        var exportHistory = client.get(exportRoot + "?page=1&size=20");
        assertOk(exportHistory);
        assertThat(exportHistory.body().at("/data/items")
                .findValuesAsText("exportId")).contains(exportId);

        var workbookResponse = client.download(
                exportRoot + "/" + exportId + "/result.xlsx");
        assertThat(workbookResponse.status()).isEqualTo(200);
        assertThat(workbookResponse.header("Cache-Control"))
                .contains("no-store");
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(
                workbookResponse.body()))) {
            var sheet = workbook.getSheet("report");
            assertThat(sheet.getLastRowNum()).isEqualTo(3);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue())
                    .isEqualTo("Event time");
            assertThat(sheet.getRow(0).getCell(1).getStringCellValue())
                    .isEqualTo("Approval route");
            assertThat(workbook.getSheet("summary").getRow(0).getCell(1)
                    .getStringCellValue()).isEqualTo("3");
        }

        var scheduleRoot = adminRoot + "/" + reportId + "/schedules";
        var preview = client.postWithCsrf(
                scheduleRoot + "/next-fire:preview",
                json(Map.of(
                        "timeZone", "Asia/Shanghai",
                        "cadence", Map.of(
                                "kind", "DAILY",
                                "localTime", "09:30",
                                "daysOfWeek", List.of())
                )), Map.of());
        assertOk(preview);
        assertThat(Instant.parse(text(preview.body(), "/data/nextFireAt")))
                .isAfter(Instant.now().minusSeconds(1));

        var createdSchedule = client.postWithCsrf(scheduleRoot, json(Map.of(
                "code", "daily_delivery",
                "name", "Daily report delivery",
                "enabled", true,
                "timeZone", "Asia/Shanghai",
                "cadence", Map.of(
                        "kind", "DAILY",
                        "localTime", "09:30",
                        "daysOfWeek", List.of()),
                "recipientMemberIds", List.of(recipientMemberId)
        )), Map.of("Idempotency-Key", key()));
        assertOk(createdSchedule);
        var scheduleId = text(createdSchedule.body(), "/data/id");
        assertThat(text(createdSchedule.body(), "/data/code"))
                .isEqualTo("daily_delivery");
        assertThat(createdSchedule.body().at("/data/recipientMemberIds"))
                .extracting(JsonNode::asText)
                .containsExactly(recipientMemberId);

        var schedules = client.get(scheduleRoot + "?page=1&size=20");
        assertOk(schedules);
        assertThat(schedules.body().at("/data/items").findValuesAsText("id"))
                .contains(scheduleId);

        jdbcTemplate.update("""
                UPDATE un_module_report_schedule
                   SET next_fire_at=CURRENT_TIMESTAMP(6)-INTERVAL 1 SECOND,
                       updated_at=CURRENT_TIMESTAMP(6),version=version+1
                 WHERE system_id=? AND tenant_id=? AND id=?
                """, Long.parseLong(systemId), Long.parseLong(firstTenantId),
                Long.parseLong(scheduleId));
        reportScheduleDueProducer.produce();
        reportScheduleDueProducer.produce();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM un_module_report_schedule_occurrence
                 WHERE system_id=? AND tenant_id=? AND schedule_id=?
                """, Long.class, Long.parseLong(systemId),
                Long.parseLong(firstTenantId), Long.parseLong(scheduleId)))
                .isOne();
        var occurrenceId = jdbcTemplate.queryForObject("""
                SELECT id
                  FROM un_module_report_schedule_occurrence
                 WHERE system_id=? AND tenant_id=? AND schedule_id=?
                """, Long.class, Long.parseLong(systemId),
                Long.parseLong(firstTenantId), Long.parseLong(scheduleId));
        assertThat(occurrenceId).isNotNull();

        reportScheduleOccurrenceWorker.startPending();
        TestResponse scheduledRun = null;
        var scheduledRunRoot = runtimeRoot + "/" + code + "/scheduled-runs";
        for (var attempt = 0; attempt < 200; attempt++) {
            reportExportJobWorker.poll();
            jdbcTemplate.update("""
                    UPDATE un_module_report_schedule_occurrence
                       SET available_at=CURRENT_TIMESTAMP(6)-INTERVAL 1 SECOND
                     WHERE system_id=? AND tenant_id=? AND id=?
                       AND status='RUNNING'
                    """, Long.parseLong(systemId), Long.parseLong(firstTenantId),
                    occurrenceId);
            reportScheduleOccurrenceWorker.monitorRunning();
            var status = jdbcTemplate.queryForObject("""
                    SELECT status
                      FROM un_module_report_schedule_occurrence
                     WHERE system_id=? AND tenant_id=? AND id=?
                    """, String.class, Long.parseLong(systemId),
                    Long.parseLong(firstTenantId), occurrenceId);
            if ("SUCCEEDED".equals(status)) {
                break;
            }
            Thread.sleep(25);
        }
        assertThat(jdbcTemplate.queryForObject("""
                SELECT status
                  FROM un_module_report_schedule_occurrence
                 WHERE system_id=? AND tenant_id=? AND id=?
                """, String.class, Long.parseLong(systemId),
                Long.parseLong(firstTenantId), occurrenceId))
                .isEqualTo("SUCCEEDED");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM un_module_report_schedule_delivery
                 WHERE system_id=? AND tenant_id=? AND occurrence_id=?
                   AND recipient_member_id=?
                """, Long.class, Long.parseLong(systemId),
                Long.parseLong(firstTenantId), occurrenceId,
                Long.parseLong(recipientMemberId))).isOne();

        refreshSystemContext(recipientClient, systemId);
        var deliveredRuns = recipientClient.get(
                scheduledRunRoot + "?page=1&size=20");
        assertOk(deliveredRuns);
        assertThat(deliveredRuns.body().at("/data/items")
                .findValuesAsText("id"))
                .contains(Long.toString(occurrenceId));
        scheduledRun = recipientClient.get(scheduledRunRoot + "/"
                + occurrenceId);
        assertOk(scheduledRun);
        assertThat(text(scheduledRun.body(), "/data/status"))
                .isEqualTo("SUCCEEDED");
        assertThat(scheduledRun.body().at("/data/totalRows").asLong())
                .isEqualTo(3);
        var scheduledWorkbook = recipientClient.download(
                scheduledRunRoot + "/" + occurrenceId + "/result.xlsx");
        assertThat(scheduledWorkbook.status()).isEqualTo(200);
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(
                scheduledWorkbook.body()))) {
            assertThat(workbook.getSheet("report").getLastRowNum())
                    .isEqualTo(3);
            assertThat(workbook.getSheet("summary").getRow(0).getCell(1)
                    .getStringCellValue()).isEqualTo("3");
        }

        reportScheduleDueProducer.produce();
        reportScheduleOccurrenceWorker.startPending();
        reportScheduleOccurrenceWorker.monitorRunning();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM un_module_report_schedule_occurrence
                 WHERE system_id=? AND tenant_id=? AND schedule_id=?
                """, Long.class, Long.parseLong(systemId),
                Long.parseLong(firstTenantId), Long.parseLong(scheduleId)))
                .isOne();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM un_module_report_schedule_delivery
                 WHERE system_id=? AND tenant_id=? AND occurrence_id=?
                """, Long.class, Long.parseLong(systemId),
                Long.parseLong(firstTenantId), occurrenceId)).isOne();

        assertOk(client.postWithCsrf(
                "/api/v1/context/tenants/" + secondTenantId + ":switch",
                "{}", Map.of()));
        var isolated = client.get(runtimeRoot);
        assertOk(isolated);
        assertThat(isolated.body().at("/data")).isEmpty();
        assertError(client.get(runtimeRoot + "/" + code),
                404, "REPORT_NOT_FOUND");
        assertError(client.get(exportRoot + "?page=1&size=20"),
                404, "REPORT_NOT_FOUND");
        assertError(client.get(exportRoot + "/" + exportId),
                404, "REPORT_EXPORT_NOT_FOUND");
        assertThat(client.download(exportRoot + "/" + exportId
                + "/result.xlsx").status()).isEqualTo(404);
        var isolatedRuns = client.get(scheduledRunRoot + "?page=1&size=20");
        assertOk(isolatedRuns);
        assertThat(isolatedRuns.body().at("/data/items")).isEmpty();
        assertError(client.get(scheduledRunRoot + "/" + occurrenceId),
                404, "REPORT_SCHEDULE_RUN_NOT_FOUND");
        assertThat(client.download(scheduledRunRoot + "/" + occurrenceId
                + "/result.xlsx").status()).isEqualTo(404);
        assertOk(client.postWithCsrf(
                "/api/v1/context/tenants/" + firstTenantId + ":switch",
                "{}", Map.of()));
        return code;
    }

    private TestResponse awaitReportExport(
            TestClient client,
            String exportRoot,
            String exportId,
            String target
    ) throws Exception {
        TestResponse last = null;
        for (var attempt = 0; attempt < 200; attempt++) {
            reportExportJobWorker.poll();
            last = client.get(exportRoot + "/" + exportId);
            assertOk(last);
            if (target.equals(text(last.body(), "/data/status"))) {
                return last;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Report export did not reach " + target
                + ": " + (last == null ? "none" : last.body()));
    }

    private void refreshSystemContext(TestClient client, String systemId) throws Exception {
        assertOk(client.postWithCsrf("/api/v1/auth/refresh", "{}", Map.of()));
        assertOk(client.postWithCsrf(
                "/api/v1/context/systems/" + systemId + ":switch",
                "{}",
                Map.of()));
    }

    private TestClient login() throws Exception {
        return login(ROOT_USERNAME, ROOT_PASSWORD);
    }

    private TestClient login(String username, String password) throws Exception {
        var client = new TestClient();
        assertOk(client.post("/api/v1/auth/login", json(Map.of(
                "account", username,
                "password", password
        )), Map.of()));
        return client;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private static String text(JsonNode node, String pointer) {
        return node.at(pointer).asText();
    }

    private static void assertSafePlatformTask(JsonNode value) {
        var fields = new java.util.ArrayList<String>();
        value.fieldNames().forEachRemaining(fields::add);
        var allowed = java.util.Set.of(
                "taskId", "title", "description", "dueAt", "priority",
                "status", "source", "createdAt", "updatedAt",
                "completedAt", "cancelledAt", "version");
        assertThat(fields)
                .contains("taskId", "title", "priority", "status", "source",
                        "createdAt", "updatedAt", "version")
                .allMatch(allowed::contains);
    }

    private static String queryPart(String route) {
        var queryStart = route.indexOf('?');
        if (queryStart < 0 || queryStart == route.length() - 1) {
            throw new AssertionError("Drill route must contain native query filters: " + route);
        }
        return route.substring(queryStart);
    }

    private static String itemId(JsonNode items, String field, String value) {
        for (var item : items) {
            if (value.equals(item.path(field).asText())) {
                return item.path("id").asText();
            }
        }
        throw new AssertionError("Cannot find item where " + field + "=" + value);
    }

    private static JsonNode item(JsonNode items, String field, String value) {
        for (var item : items) {
            if (value.equals(item.path(field).asText())) {
                return item;
            }
        }
        throw new AssertionError("Cannot find item where " + field + "=" + value);
    }

    private static List<JsonNode> items(
            JsonNode values,
            String field,
            String value
    ) {
        return StreamSupport.stream(values.spliterator(), false)
                .filter(item -> value.equals(item.path(field).asText()))
                .toList();
    }

    private static List<String> itemValues(JsonNode items, String field) {
        return StreamSupport.stream(items.spliterator(), false)
                .map(item -> item.path(field).asText())
                .toList();
    }

    private static Map<String, String> statusMapping(FlowRecordSchema schema) {
        return Map.of(
                "fieldCode", "approval_status",
                "approvedValue", schema.approvedOptionId(),
                "rejectedValue", schema.rejectedOptionId(),
                "withdrawnValue", schema.withdrawnOptionId(),
                "terminatedValue", schema.terminatedOptionId());
    }

    private static String fieldValue(JsonNode responseBody, String fieldCode) {
        for (var field : responseBody.at("/data/values")) {
            if (fieldCode.equals(field.path("fieldCode").asText())) {
                return field.path("value").asText();
            }
        }
        throw new AssertionError("Missing field value " + fieldCode + " in " + responseBody);
    }

    private static boolean hasFieldValue(JsonNode responseBody, String fieldCode) {
        for (var field : responseBody.at("/data/values")) {
            if (fieldCode.equals(field.path("fieldCode").asText())) {
                return true;
            }
        }
        return false;
    }

    private static boolean absentOrNull(JsonNode value) {
        return value.isMissingNode() || value.isNull();
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }

    private static String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(content));
    }

    private static void assertOk(TestResponse response) {
        assertThat(response.status())
                .withFailMessage("Expected 200, got %s: %s", response.status(), response.body())
                .isEqualTo(200);
        assertThat(text(response.body(), "/code")).isEqualTo("OK");
    }

    private static void assertAccepted(TestResponse response) {
        assertThat(response.status())
                .withFailMessage("Expected 202, got %s: %s",
                        response.status(), response.body())
                .isEqualTo(202);
        assertThat(text(response.body(), "/code")).isEqualTo("OK");
    }

    private static void assertCreated(TestResponse response) {
        assertThat(response.status())
                .withFailMessage("Expected 201, got %s: %s", response.status(), response.body())
                .isEqualTo(201);
        assertThat(text(response.body(), "/code")).isEqualTo("OK");
    }

    private static void assertError(TestResponse response, int status, String code) {
        assertThat(response.status())
                .withFailMessage("Expected %s, got %s: %s", status, response.status(), response.body())
                .isEqualTo(status);
        assertThat(text(response.body(), "/code")).isEqualTo(code);
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

    private static Path openApiSecretRoot() {
        try {
            return Files.createTempDirectory("examine-flow-openapi-secrets-")
                    .toAbsolutePath()
                    .normalize();
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static Path openApiSecretFile() {
        try {
            return Files.writeString(
                    OPENAPI_SECRET_ROOT.resolve("record-api.secret"),
                    OPENAPI_RECORD_SECRET,
                    StandardCharsets.UTF_8
            );
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static Path sensitiveKeyRingFile() {
        try {
            var encryptionKey = Base64.getEncoder().encodeToString(
                    "flow-ai-confirm-encryption-key01"
                            .getBytes(StandardCharsets.UTF_8));
            var hashKey = Base64.getEncoder().encodeToString(
                    "flow-ai-confirm-hash-key-materia"
                            .getBytes(StandardCharsets.UTF_8));
            return Files.writeString(
                    OPENAPI_SECRET_ROOT.resolve("sensitive-key-ring.json"),
                    """
                    {"activeEncryptionKeyVersion":"flow-v1",
                     "activeHashKeyVersion":"flow-h1",
                     "encryptionKeys":{"flow-v1":"%s"},
                     "hashKeys":{"flow-h1":"%s"},
                     "queryHashKeyVersions":["flow-h1"]}
                    """.formatted(encryptionKey, hashKey),
                    StandardCharsets.UTF_8
            );
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    static final class AiProviderFixture implements AutoCloseable {
        private final HttpServer server;
        private final AtomicInteger planCalls = new AtomicInteger();
        private final AtomicInteger summaryCalls = new AtomicInteger();
        private final AtomicInteger fillCalls = new AtomicInteger();
        private final CopyOnWriteArrayList<String> authorizationHeaders =
                new CopyOnWriteArrayList<>();
        private volatile String createStatusValue;
        private volatile String updateStatusValue;

        private AiProviderFixture(HttpServer server) {
            this.server = server;
        }

        static AiProviderFixture start() throws IOException {
            var server = HttpServer.create(
                    new InetSocketAddress("127.0.0.1", 0), 0);
            var fixture = new AiProviderFixture(server);
            server.createContext("/chat/completions", fixture::complete);
            server.start();
            return fixture;
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        int planCalls() {
            return planCalls.get();
        }

        int summaryCalls() {
            return summaryCalls.get();
        }

        int fillCalls() {
            return fillCalls.get();
        }

        List<String> authorizationHeaders() {
            return List.copyOf(authorizationHeaders);
        }

        void configureMutationValues(String createStatusValue, String updateStatusValue) {
            this.createStatusValue = createStatusValue;
            this.updateStatusValue = updateStatusValue;
        }

        private void complete(HttpExchange exchange) throws IOException {
            try (exchange) {
                authorizationHeaders.add(exchange.getRequestHeaders()
                        .getFirst("Authorization"));
                var request = new String(
                        exchange.getRequestBody().readAllBytes(),
                        StandardCharsets.UTF_8);
                if (!("POST".equals(exchange.getRequestMethod())
                        && ("Bearer " + OPENAPI_RECORD_SECRET).equals(
                        exchange.getRequestHeaders().getFirst("Authorization")))) {
                    exchange.sendResponseHeaders(401, -1);
                    return;
                }
                var planning = request.contains("\"response_format\"");
                var content = planning
                        ? mutationOrQueryPlan(request)
                        : "AI_FIXTURE_SUMMARY_75";
                if (planning) planCalls.incrementAndGet();
                else summaryCalls.incrementAndGet();
                var response = new ObjectMapper().writeValueAsBytes(Map.of(
                        "choices", List.of(Map.of(
                                "message", Map.of("content", content))),
                        "usage", Map.of(
                                "prompt_tokens", 12,
                                "completion_tokens", planning ? 8 : 4)));
                exchange.getResponseHeaders().set(
                        "Content-Type", "application/json; charset=utf-8");
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
            }
        }

        private String mutationOrQueryPlan(String request) {
            if (request.contains("AI_FLOW_DEFINITION_DRAFT_85_APPROVER=")) {
                var marker = "AI_FLOW_DEFINITION_DRAFT_85_APPROVER=";
                var start = request.indexOf(marker);
                var approverMemberId = request.substring(start + marker.length())
                        .replaceFirst("[^0-9].*$", "");
                return """
                       {"operation":"FLOW_DEFINITION_DRAFT","name":"AI Flow Definition 85",
                        "approverMemberIds":["%s"],"confidence":0.99,"clarification":null}
                       """.formatted(approverMemberId).strip();
            }
            if (request.contains("AI_FLOW_DENIED_DRAFT_85_APPROVER=")) {
                var marker = "AI_FLOW_DENIED_DRAFT_85_APPROVER=";
                var start = request.indexOf(marker);
                var approverMemberId = request.substring(start + marker.length())
                        .replaceFirst("[^0-9].*$", "");
                return """
                       {"operation":"FLOW_DEFINITION_DRAFT","name":"AI Permission Denied Flow 85",
                        "approverMemberIds":["%s"],"confidence":0.98,"clarification":null}
                       """.formatted(approverMemberId).strip();
            }
            if (request.contains("AI_CONFIG_REPORT_DRAFT_85_SOURCE=")) {
                var marker = "AI_CONFIG_REPORT_DRAFT_85_SOURCE=";
                var start = request.indexOf(marker);
                var dataSourceId = request.substring(start + marker.length())
                        .replaceFirst("[^0-9].*$", "");
                return """
                       {"operation":"CONFIG_REPORT_DRAFT","code":"ai_report_85",
                        "name":"AI Report 85","description":"AI generated report draft 85",
                        "dataSourceId":"%s","outputFieldCodes":["route"],
                        "confidence":0.99,"clarification":null}
                       """.formatted(dataSourceId).strip();
            }
            if (request.contains("AI_CONFIG_PRINT_TEMPLATE_DRAFT_85")) {
                return """
                       {"operation":"CONFIG_PRINT_TEMPLATE_DRAFT","moduleCode":"flow_order",
                        "code":"ai_print_85","name":"AI Print 85","paperSize":"A4",
                        "orientation":"PORTRAIT","title":"AI Print Draft 85",
                        "fieldCodes":["route","approval_status"],"footer":"AI Footer 85",
                        "confidence":0.99,"clarification":null}
                       """.strip();
            }
            if (request.contains("AI_WORK_TASK_DRAFT_84_ASSIGNEE=")) {
                var marker = "AI_WORK_TASK_DRAFT_84_ASSIGNEE=";
                var start = request.indexOf(marker);
                var assigneeMemberId = request.substring(start + marker.length())
                        .replaceFirst("[^0-9].*$", "");
                return """
                       {"operation":"WORK_TASK_DRAFT","title":"AI Work Task 84",
                        "description":"AI Work Task Description 84",
                        "assigneeMemberId":"%s","projectId":null,"dueAt":null,
                        "confidence":0.98,"clarification":null}
                       """.formatted(assigneeMemberId).strip();
            }
            if (request.contains("AI_WORK_REPORT_DRAFT_84_DATE=")) {
                var marker = "AI_WORK_REPORT_DRAFT_84_DATE=";
                var start = request.indexOf(marker);
                var workDate = request.substring(start + marker.length())
                        .replaceFirst("[^0-9-].*$", "");
                return """
                       {"operation":"WORK_DAILY_REPORT_DRAFT","workDate":"%s",
                        "completedWork":"AI Work Report Completed 84",
                        "plannedWork":"AI Work Report Planned 84",
                        "blockers":"AI Work Report Blockers 84",
                        "confidence":0.97,"clarification":null}
                       """.formatted(workDate).strip();
            }
            for (var marker : List.of(
                    "AI_RECORD_COMMENT_QUERY_90=",
                    "AI_RECORD_COMMENT_QUERY_90_REVOKED=",
                    "AI_RECORD_COMMENT_QUERY_90_FOREIGN=",
                    "AI_RECORD_HISTORY_QUERY_90=",
                    "AI_RECORD_HISTORY_QUERY_90_REVOKED=",
                    "AI_RECORD_HISTORY_QUERY_90_FOREIGN=",
                    "AI_RECORD_FILE_QUERY_90=",
                    "AI_RECORD_FILE_QUERY_90_REVOKED=",
                    "AI_RECORD_FILE_QUERY_90_FOREIGN=")) {
                if (request.contains(marker)) {
                    var start = request.indexOf(marker);
                    var value = request.substring(start + marker.length())
                            .replaceFirst("[^A-Za-z0-9_|].*$", "");
                    var parts = value.split("\\|", -1);
                    if (parts.length != 3) {
                        throw new IllegalArgumentException(
                                "Invalid record activity fixture marker");
                    }
                    var operation = marker.contains("COMMENT")
                            ? "RECORD_COMMENT_QUERY"
                            : marker.contains("HISTORY")
                            ? "RECORD_HISTORY_QUERY"
                            : "RECORD_FILE_QUERY";
                    return """
                           {"operation":"%s","moduleCode":"%s",
                            "recordId":"%s","limit":%s}
                           """.formatted(
                            operation, parts[0], parts[1], parts[2]).strip();
                }
            }
            if (request.contains("AI_RUNTIME_REPORT_QUERY_95_FOREIGN")) {
                return """
                       {"operation":"RUNTIME_REPORT_QUERY",
                        "moduleCode":"flow_order",
                        "reportCode":"ai_foreign_report_95",
                        "page":1,"size":3}
                       """.strip();
            }
            if (request.contains("AI_RUNTIME_REPORT_QUERY_95")) {
                return """
                       {"operation":"RUNTIME_REPORT_QUERY",
                        "moduleCode":"flow_order",
                        "reportCode":"flow_empty_route_report",
                        "page":1,"size":3}
                       """.strip();
            }
            if (request.contains("AI_RUNTIME_STATISTICS_FOREIGN_94")) {
                return """
                       {"operation":"RUNTIME_STATISTICS_QUERY",
                        "moduleCode":"flow_order",
                        "dataSourceCode":"ai_foreign_stats_94",
                        "aggregation":"COUNT","measureFieldCode":null,
                        "grouping":null,"trend":{"fieldCode":"event_time",
                        "grain":"DAY","startInclusive":"2026-07-01",
                        "endExclusive":"2026-07-04"}}
                       """.strip();
            }
            if (request.contains("AI_RUNTIME_STATISTICS_TREND_94")) {
                return """
                       {"operation":"RUNTIME_STATISTICS_QUERY",
                        "moduleCode":"flow_order",
                        "dataSourceCode":"flow_empty_routes",
                        "aggregation":"COUNT","measureFieldCode":null,
                        "grouping":null,"trend":{"fieldCode":"event_time",
                        "grain":"DAY","startInclusive":"2026-07-01",
                        "endExclusive":"2026-07-04"}}
                       """.strip();
            }
            if (request.contains("AI_RECORD_CONTEXT_83=")) {
                var marker = "AI_RECORD_CONTEXT_83=";
                var start = request.indexOf(marker);
                var recordId = request.substring(start + marker.length())
                        .replaceFirst("[^0-9].*$", "");
                return """
                       {"operation":"RECORD_CONTEXT_SUMMARY","moduleCode":"flow_order",
                        "recordId":"%s","outputFields":["approval_status"]}
                       """.formatted(recordId).strip();
            }
            if (request.contains("AI_WORK_TASK_QUERY_83")) {
                return """
                       {"operation":"WORK_TASK_QUERY","keyword":null,"projectId":null,
                        "dueFrom":null,"dueTo":null,"status":"ALL",
                        "role":"PARTICIPATING","limit":3}
                       """.strip();
            }
            if (request.contains("AI_WORK_DAILY_REPORT_QUERY_83")) {
                var reportDate = LocalDate.now(ZoneOffset.UTC);
                return """
                       {"operation":"WORK_DAILY_REPORT_QUERY","scope":"SELF",
                        "authorMemberId":null,"dateFrom":"%s","dateTo":"%s",
                       "status":"ALL","limit":3}
                       """.formatted(reportDate.minusDays(6), reportDate).strip();
            }
            for (var marker : List.of(
                    "AI_WORK_PROJECT_METRICS_QUERY_89=",
                    "AI_WORK_PROJECT_METRICS_QUERY_89_HIDDEN=",
                    "AI_WORK_PROJECT_METRICS_QUERY_89_REVOKED=",
                    "AI_WORK_PROJECT_METRICS_QUERY_89_FOREIGN=")) {
                if (request.contains(marker)) {
                    var start = request.indexOf(marker);
                    var value = request.substring(start + marker.length())
                            .replaceFirst("[^0-9|\\-].*$", "");
                    var parts = value.split("\\|", -1);
                    if (parts.length != 3) {
                        throw new IllegalArgumentException(
                                "Invalid Work project metrics fixture marker");
                    }
                    return """
                           {"operation":"WORK_PROJECT_METRICS_QUERY",
                            "projectId":"%s","fromInclusive":"%s",
                            "toExclusive":"%s"}
                           """.formatted(parts[0], parts[1], parts[2]).strip();
                }
            }
            if (request.contains("AI_TODO_QUERY_87")) {
                return """
                       {"operation":"TODO_QUERY","category":"ALL",
                        "state":"ALL","time":"ALL","limit":1}
                       """.strip();
            }
            if (request.contains("AI_MESSAGE_QUERY_87")) {
                return """
                       {"operation":"MESSAGE_QUERY","status":"UNREAD","limit":1}
                       """.strip();
            }
            if (request.contains("AI_CONFIG_SELECTION_82")) {
                return """
                       {"operation":"CONFIG_SELECTION_FIELD_DRAFT","moduleCode":"flow_order",
                        "fieldCode":"ai_priority_82","fieldName":"AI Priority 82",
                        "fieldType":"RADIO","required":true,
                        "dictionaryCode":"ai_priority_options_82",
                        "dictionaryName":"AI Priority Options 82","options":[
                          {"code":"low","label":"Low","semanticKey":"LOW","color":"#64748B","default":false},
                          {"code":"normal","label":"Normal","semanticKey":"NORMAL","color":"#2563EB","default":true},
                          {"code":"high","label":"High","semanticKey":"HIGH","color":"#DC2626","default":false}],
                        "maxSelections":null,"confidence":0.99,"clarification":null}
                       """.strip();
            }
            if (request.contains("AI_CONFIG_PAGE_82_A")) {
                return """
                       {"operation":"CONFIG_PAGE_LAYOUT_DRAFT","moduleCode":"flow_order",
                        "pageCode":"form","pageType":"FORM","layout":{
                          "columns":2,"gap":20,"labelPosition":"TOP","density":"COMPACT",
                          "stickyActions":true,"pageSize":null,"searchEnabled":null,
                          "filterEnabled":null,"sections":[
                            {"code":"routing","title":"Routing","fieldCodes":["route","approval_status"]},
                            {"code":"timing","title":"Timing","fieldCodes":["event_time"]}]},
                        "confidence":0.98,"clarification":null}
                       """.strip();
            }
             if (request.contains("AI_CONFIG_PAGE_82_B")) {
                 return """
                        {"operation":"CONFIG_PAGE_LAYOUT_DRAFT","moduleCode":"flow_order",
                        "pageCode":"form","pageType":"FORM","layout":{
                          "columns":1,"gap":12,"labelPosition":"LEFT","density":"DEFAULT",
                          "stickyActions":false,"pageSize":null,"searchEnabled":null,
                          "filterEnabled":null,"sections":[
                            {"code":"main","title":"Main","fieldCodes":["route","approval_status","event_time"]}]},
                        "confidence":0.97,"clarification":null}
                        """.strip();
             }
             if (request.contains("AI_CONFIG_FILTER_SCENARIO_99")) {
                 return """
                        {"operation":"CONFIG_FILTER_SCENARIO_DRAFT","moduleCode":"flow_order",
                         "pageCode":"list","scenario":{"code":"routed_first","name":"Routed first",
                         "filter":{"kind":"PREDICATE","fieldCode":"route","operator":"NOT_EMPTY","value":null},
                         "sort":[{"fieldCode":"event_time","direction":"DESC","nulls":"LAST"}]},
                         "makeDefault":true,"confidence":0.99,"clarification":null}
                        """.strip();
             }
             if (request.contains("AI_CONFIG_FIELD_PERMISSION_STAGE_99")) {
                 return """
                        {"operation":"CONFIG_FIELD_PERMISSION_STAGE_DRAFT","moduleCode":"flow_order",
                         "fieldCode":"route","stageRead":true,"stageWrite":true,
                         "confidence":0.99,"clarification":null}
                        """.strip();
             }
             if (request.contains("AI_CONFIG_FIELD_81_A")) {
                return """
                       {"operation":"CONFIG_FIELD_DRAFT","moduleCode":"flow_order",
                        "fieldCode":"ai_priority_81","fieldName":"AI Priority 81",
                        "fieldType":"INTEGER","required":false,
                        "settings":{"minimum":0,"maximum":100},
                        "confidence":0.99,"clarification":null}
                       """.strip();
            }
            if (request.contains("AI_CONFIG_FIELD_81_B")) {
                return """
                       {"operation":"CONFIG_FIELD_DRAFT","moduleCode":"flow_order",
                        "fieldCode":"ai_risk_note_81","fieldName":"AI Risk Note 81",
                        "fieldType":"TEXT","required":false,
                        "settings":{"maxLength":80},
                        "confidence":0.97,"clarification":null}
                       """.strip();
            }
            if (request.contains("PLATFORM_OPS_TASKS_80")) {
                return """
                       {"operation":"PLATFORM_OPERATIONS_QUERY",
                        "queryKind":"PERSONAL_TASKS","limit":10,
                        "confidence":0.98,"clarification":null}
                       """.strip();
            }
            if (request.contains("PLATFORM_OPS_QUOTA_80")) {
                return """
                       {"operation":"PLATFORM_OPERATIONS_QUERY",
                        "queryKind":"AI_QUOTA","limit":10,
                        "confidence":0.98,"clarification":null}
                       """.strip();
            }
            if (request.contains("PLATFORM_OPS_HEALTH_80")) {
                return """
                       {"operation":"PLATFORM_OPERATIONS_QUERY",
                        "queryKind":"SERVICE_HEALTH","limit":10,
                        "confidence":0.98,"clarification":null}
                       """.strip();
            }
            if (request.contains("PLATFORM_OPS_ACTIVITY_80")) {
                return """
                       {"operation":"PLATFORM_OPERATIONS_QUERY",
                        "queryKind":"AGENT_ACTIVITY","limit":10,
                        "confidence":0.98,"clarification":null}
                       """.strip();
            }
            if (request.contains("PLATFORM_TASK_DRAFT_79")) {
                return """
                       {"operation":"PLATFORM_TASK_DRAFT",
                        "title":"Review platform quota 79",
                        "description":"Follow up the platform capacity alert",
                        "dueAt":"2099-01-01T00:00:00Z","priority":"HIGH",
                        "confidence":0.97,"clarification":null}
                       """.strip();
            }
            if (request.contains("PLATFORM_BUSINESS_RECORD_78=")) {
                var marker = "PLATFORM_BUSINESS_RECORD_78=";
                var start = request.indexOf(marker);
                var systemCode = request.substring(start + marker.length())
                        .replaceFirst("[^A-Za-z0-9_].*$", "");
                return """
                       {"operation":"SYSTEM_SWITCH_GUIDANCE",
                        "requestedSystemCode":"%s"}
                       """.formatted(systemCode).strip();
            }
            if (request.contains("PLATFORM_AUTHORIZED_SYSTEMS_78")) {
                return """
                       {"operation":"AUTHORIZED_SYSTEMS_QUERY"}
                       """.strip();
            }
            if (request.contains("ai_summary") || request.contains("AI summary")) {
                fillCalls.incrementAndGet();
                return """
                       {"value":"AI Filled Summary 77","confidence":0.96,
                        "clarification":null}
                       """.strip();
            }
            if (request.contains("AI_CREATE_PROMPT_76")) {
                return """
                       {"operation":"RECORD_CREATE","moduleCode":"flow_order",
                        "recordId":null,"expectedVersion":null,
                        "title":"AI Created Record 76",
                        "values":{"approval_status":"%s"},
                        "relations":[],"subtables":[],
                        "confidence":{"approval_status":0.99},"clarifications":[]}
                       """.formatted(createStatusValue).strip();
            }
            if (request.contains("AI_UPDATE_PROMPT_76")) {
                var marker = "AI_UPDATE_TARGET_76=";
                var start = request.indexOf(marker);
                var recordId = start < 0 ? "0" : request.substring(
                        start + marker.length()).replaceFirst("[^0-9].*$", "");
                var versionMarker = "AI_UPDATE_VERSION_76=";
                var versionStart = request.indexOf(versionMarker);
                var expectedVersion = versionStart < 0 ? "-1" : request.substring(
                        versionStart + versionMarker.length())
                        .replaceFirst("[^0-9].*$", "");
                return """
                       {"operation":"RECORD_UPDATE","moduleCode":"flow_order",
                        "recordId":"%s","expectedVersion":%s,
                        "title":"AI Updated Record 76",
                        "values":{"approval_status":"%s"},
                        "relations":[],"subtables":[],
                        "confidence":{"approval_status":0.99},"clarifications":[]}
                       """.formatted(recordId, expectedVersion, updateStatusValue).strip();
            }
            return """
                   {"operation":"RECORD_QUERY","moduleCode":"flow_order",
                    "filter":null,"sort":[],
                    "outputFields":["approval_status"],"limit":3}
                   """.strip();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class WebhookTestConfiguration {
        @Bean
        @Primary
        DeterministicWebhookTransport deterministicWebhookTransport() {
            return new DeterministicWebhookTransport();
        }

        @Bean
        @Primary
        SecretResolverFacade deterministicTenantSecrets() {
            var delegate = new DefaultSecretResolverFacade(
                    List.of(OPENAPI_SECRET_ROOT));
            return request -> {
                if (request.reference().endsWith("_JDBC_USERNAME_V1")) {
                    return Optional.of(
                            SecretResolverFacade.ResolvedSecret.utf8(
                                    MYSQL.getUsername()));
                }
                if (request.reference().endsWith("_JDBC_PASSWORD_V1")) {
                    return Optional.of(
                            SecretResolverFacade.ResolvedSecret.utf8(
                                    MYSQL.getPassword()));
                }
                return delegate.resolve(request);
            };
        }
    }

    static final class DeterministicWebhookTransport
            implements OutboundHttpTransport {
        private final AtomicInteger attempts = new AtomicInteger();
        private final CopyOnWriteArrayList<Request> requests =
                new CopyOnWriteArrayList<>();
        private final CopyOnWriteArrayList<Request> dataSourceRequests =
                new CopyOnWriteArrayList<>();

        @Override
        public Response post(Request request) {
            if ("datasource.example.test".equals(request.uri().getHost())) {
                dataSourceRequests.add(request);
                return new Response(
                        200,
                        ("{\"rows\":[{\"id\":\"row-1\","
                                + "\"route\":\"external\","
                                + "\"active\":true}]}")
                                .getBytes(StandardCharsets.UTF_8),
                        Duration.ofMillis(7)
                );
            }
            requests.add(request);
            var attempt = attempts.incrementAndGet();
            return new Response(
                    attempt == 1 ? 503 : 204,
                    ("{\"attempt\":" + attempt + "}")
                            .getBytes(StandardCharsets.UTF_8),
                    Duration.ofMillis(5)
            );
        }

        void reset() {
            attempts.set(0);
            requests.clear();
            dataSourceRequests.clear();
        }

        int attempts() {
            return attempts.get();
        }

        List<Request> requests() {
            return List.copyOf(requests);
        }

        List<Request> dataSourceRequests() {
            return List.copyOf(dataSourceRequests);
        }
    }

    private record FlowRecordSchema(
            String moduleId,
            String versionId,
            String approvedOptionId,
            String rejectedOptionId,
            String withdrawnOptionId,
            String terminatedOptionId,
            String approverMemberFieldId,
            String aiFillFieldId
    ) {
    }

    private record PublishedSource(
            String id,
            String code,
            String versionId
    ) {
    }

    private record PublishedKpi(
            String id,
            String versionId,
            String targetId
    ) {
    }

    private record TestResponse(int status, JsonNode body) {
    }

    private record BinaryResponse(
            int status,
            java.net.http.HttpHeaders headers,
            byte[] body
    ) {
        String header(String name) {
            return headers.firstValue(name).orElse("");
        }
    }

    private final class TestClient {
        private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        private final HttpClient client = HttpClient.newBuilder()
                .cookieHandler(cookies)
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        TestResponse get(String path) throws Exception {
            return send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET());
        }

        TestResponse get(String path, Map<String, String> headers) throws Exception {
            var builder = HttpRequest.newBuilder(URI.create(baseUrl + path)).GET();
            headers.forEach(builder::header);
            return send(builder);
        }

        BinaryResponse download(String path) throws Exception {
            return download(path, Map.of());
        }

        BinaryResponse download(String path, Map<String, String> headers) throws Exception {
            var builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .header("X-Request-ID", "flow-api-test-" + key())
                    .GET();
            headers.forEach(builder::header);
            var response = client.send(
                    builder.build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            return new BinaryResponse(
                    response.statusCode(), response.headers(), response.body());
        }

        TestResponse post(String path, String body, Map<String, String> headers) throws Exception {
            return request("POST", path, body, headers, false);
        }

        TestResponse postWithCsrf(String path, String body, Map<String, String> headers) throws Exception {
            return request("POST", path, body, headers, true);
        }

        TestResponse put(String path, String body, Map<String, String> headers) throws Exception {
            return request("PUT", path, body, headers, false);
        }

        TestResponse putWithCsrf(String path, String body, Map<String, String> headers) throws Exception {
            return request("PUT", path, body, headers, true);
        }

        TestResponse deleteWithCsrf(String path) throws Exception {
            return request("DELETE", path, "", Map.of(), true);
        }

        TestResponse uploadWithCsrf(
                String path,
                String fileName,
                String contentType,
                byte[] content
        ) throws Exception {
            var boundary = "----examine-flow-" + key();
            var prefix = (
                    "--" + boundary + "\r\n"
                            + "Content-Disposition: form-data; name=\"file\"; "
                            + "filename=\"" + fileName + "\"\r\n"
                            + "Content-Type: " + contentType + "\r\n\r\n"
            ).getBytes(StandardCharsets.UTF_8);
            var suffix = ("\r\n--" + boundary + "--\r\n")
                    .getBytes(StandardCharsets.UTF_8);
            var builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .header(
                            "Content-Type",
                            "multipart/form-data; boundary=" + boundary
                    )
                    .header("X-CSRF-Token", csrf())
                    .POST(HttpRequest.BodyPublishers.concat(
                            HttpRequest.BodyPublishers.ofByteArray(prefix),
                            HttpRequest.BodyPublishers.ofByteArray(content),
                            HttpRequest.BodyPublishers.ofByteArray(suffix)
                    ));
            return send(builder);
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

        private TestResponse send(HttpRequest.Builder builder) throws IOException, InterruptedException {
            var response = client.send(
                    builder.header("X-Request-ID", "flow-api-test-" + key()).build(),
                    HttpResponse.BodyHandlers.ofString());
            return new TestResponse(response.statusCode(), objectMapper.readTree(response.body()));
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
