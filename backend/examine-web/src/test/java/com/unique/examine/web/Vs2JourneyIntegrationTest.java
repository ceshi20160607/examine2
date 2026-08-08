package com.unique.examine.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class Vs2JourneyIntegrationTest {
    private static final String ROOT_USERNAME = "vs2_root";
    private static final String ROOT_PASSWORD = "Vs2-Root-Test-Password-84!";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0.44"))
            .withDatabaseName("examine2_vs2_test")
            .withUsername("examine_vs2_test")
            .withPassword("container-test-password");

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
        registry.add("examine.bootstrap.root.display-name", () -> "VS2 Test Root");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://127.0.0.1:" + port;
    }

    @Test
    void completesVs2AdministrationAuthorizationAndAccessJourney() throws Exception {
        var applicant = register("applicant");
        assertThat(applicant.response().status()).isEqualTo(200);
        assertThat(text(applicant.response().body(), "/data/context/type")).isEqualTo("PLATFORM");

        var ordinaryDenied = applicant.client().get("/api/v1/platform/admin/systems");
        assertError(ordinaryDenied, 403, "PERMISSION_DENIED");

        var root = login(ROOT_USERNAME, ROOT_PASSWORD);
        assertThat(text(root.login().body(), "/data/context/type")).isEqualTo("PLATFORM");
        var rootAccountId = text(root.login().body(), "/data/account/id");
        var platformSwitch = root.client().postWithCsrf("/api/v1/context/platform:switch", "{}", Map.of());
        assertOk(platformSwitch);
        assertThat(text(platformSwitch.body(), "/data/context/type")).isEqualTo("PLATFORM");

        var systemKey = key();
        var systemCreateBody = json(Map.of(
                "code", "vs2_target_" + System.nanoTime(),
                "name", "VS2 Target System",
                "description", "VS2 end-to-end target",
                "tenantMode", "MULTI"
        ));
        var systemCreated = root.client().postWithCsrf(
                "/api/v1/platform/admin/systems", systemCreateBody,
                Map.of("Idempotency-Key", systemKey)
        );
        assertOk(systemCreated);
        var systemId = text(systemCreated.body(), "/data/id");
        assertThat(text(systemCreated.body(), "/data/status")).isEqualTo("ACTIVE");
        var systemReplay = root.client().postWithCsrf(
                "/api/v1/platform/admin/systems", systemCreateBody,
                Map.of("Idempotency-Key", systemKey)
        );
        assertOk(systemReplay);
        assertThat(text(systemReplay.body(), "/data/id")).isEqualTo(systemId);

        var initialSystemVersion = text(systemCreated.body(), "/data/version");
        var systemUpdateBody = json(Map.of(
                "name", "VS2 Target System Updated",
                "description", "Updated once for optimistic locking",
                "version", initialSystemVersion
        ));
        var systemUpdated = root.client().putWithCsrf(
                "/api/v1/platform/admin/systems/" + systemId, systemUpdateBody, Map.of()
        );
        assertOk(systemUpdated);
        var staleSystemUpdate = root.client().putWithCsrf(
                "/api/v1/platform/admin/systems/" + systemId, systemUpdateBody, Map.of()
        );
        assertError(staleSystemUpdate, 409, "VERSION_CONFLICT");

        var platformDepartmentKey = key();
        var platformDepartmentBody = json(Map.of(
                "name", "VS2 Platform Operations",
                "code", "vs2_platform_ops_" + System.nanoTime()
        ));
        var platformDepartment = root.client().postWithCsrf(
                "/api/v1/platform/admin/departments", platformDepartmentBody,
                Map.of("Idempotency-Key", platformDepartmentKey)
        );
        assertOk(platformDepartment);
        var platformDepartmentId = text(platformDepartment.body(), "/data/id");
        assertError(root.client().get("/api/v1/platform/admin/systems"), 401, "AUTHZ_SNAPSHOT_STALE");

        root = login(ROOT_USERNAME, ROOT_PASSWORD);
        var platformDepartmentReplay = root.client().postWithCsrf(
                "/api/v1/platform/admin/departments", platformDepartmentBody,
                Map.of("Idempotency-Key", platformDepartmentKey)
        );
        assertOk(platformDepartmentReplay);
        assertThat(text(platformDepartmentReplay.body(), "/data/id")).isEqualTo(platformDepartmentId);

        var platformRoleKey = key();
        var platformRoleBody = json(Map.of(
                "code", "vs2_platform_observer_" + System.nanoTime(),
                "name", "VS2 Platform Observer",
                "description", "Published by the VS2 journey"
        ));
        var platformRole = root.client().postWithCsrf(
                "/api/v1/platform/admin/roles", platformRoleBody,
                Map.of("Idempotency-Key", platformRoleKey)
        );
        assertOk(platformRole);
        var platformRoleId = text(platformRole.body(), "/data/id");
        var platformRoleReplay = root.client().postWithCsrf(
                "/api/v1/platform/admin/roles", platformRoleBody,
                Map.of("Idempotency-Key", platformRoleKey)
        );
        assertOk(platformRoleReplay);
        assertThat(text(platformRoleReplay.body(), "/data/id")).isEqualTo(platformRoleId);

        var platformDraft = root.client().putWithCsrf(
                "/api/v1/platform/admin/roles/" + platformRoleId + "/draft",
                json(Map.of(
                        "name", "VS2 Platform Observer",
                        "description", "Runtime access with explicit audit deny",
                        "permissionCodes", List.of("platform.runtime.access"),
                        "deniedPermissionCodes", List.of("platform.audit.view"),
                        "version", text(platformRole.body(), "/data/version")
                )),
                Map.of()
        );
        assertOk(platformDraft);
        assertThat(text(platformDraft.body(), "/data/draftStatus")).isEqualTo("DRAFT");

        var platformCheckKey = key();
        var platformCheckBody = json(Map.of("version", text(platformDraft.body(), "/data/version")));
        var platformChecked = root.client().postWithCsrf(
                "/api/v1/platform/admin/roles/" + platformRoleId + "/draft:check",
                platformCheckBody, Map.of("Idempotency-Key", platformCheckKey)
        );
        assertOk(platformChecked);
        assertThat(text(platformChecked.body(), "/data/draftStatus")).isEqualTo("CHECKED");
        var platformCheckReplay = root.client().postWithCsrf(
                "/api/v1/platform/admin/roles/" + platformRoleId + "/draft:check",
                platformCheckBody, Map.of("Idempotency-Key", platformCheckKey)
        );
        assertOk(platformCheckReplay);
        assertThat(text(platformCheckReplay.body(), "/data/version"))
                .isEqualTo(text(platformChecked.body(), "/data/version"));

        var platformPublishKey = key();
        var platformPublishBody = json(Map.of("version", text(platformChecked.body(), "/data/version")));
        var platformPublished = root.client().postWithCsrf(
                "/api/v1/platform/admin/roles/" + platformRoleId + "/draft:publish",
                platformPublishBody, Map.of("Idempotency-Key", platformPublishKey)
        );
        assertOk(platformPublished);
        assertThat(text(platformPublished.body(), "/data/draftStatus")).isEqualTo("PUBLISHED");
        assertThat(text(platformPublished.body(), "/data/publishedVersion")).isEqualTo("1");

        root = login(ROOT_USERNAME, ROOT_PASSWORD);
        var platformPublishReplay = root.client().postWithCsrf(
                "/api/v1/platform/admin/roles/" + platformRoleId + "/draft:publish",
                platformPublishBody, Map.of("Idempotency-Key", platformPublishKey)
        );
        assertOk(platformPublishReplay);
        assertThat(text(platformPublishReplay.body(), "/data/publishedVersion")).isEqualTo("1");
        var platformDraftV2 = root.client().putWithCsrf(
                "/api/v1/platform/admin/roles/" + platformRoleId + "/draft",
                json(Map.of(
                        "name", "VS2 Platform Observer",
                        "description", "Second published revision after the original role was already published",
                        "permissionCodes", List.of("platform.runtime.access"),
                        "deniedPermissionCodes", List.of("platform.audit.view"),
                        "version", text(platformPublishReplay.body(), "/data/version")
                )),
                Map.of()
        );
        assertOk(platformDraftV2);
        assertThat(text(platformDraftV2.body(), "/data/draftStatus")).isEqualTo("DRAFT");
        var platformCheckedV2 = root.client().postWithCsrf(
                "/api/v1/platform/admin/roles/" + platformRoleId + "/draft:check",
                json(Map.of("version", text(platformDraftV2.body(), "/data/version"))),
                Map.of("Idempotency-Key", key())
        );
        assertOk(platformCheckedV2);
        var platformPublishedV2 = root.client().postWithCsrf(
                "/api/v1/platform/admin/roles/" + platformRoleId + "/draft:publish",
                json(Map.of("version", text(platformCheckedV2.body(), "/data/version"))),
                Map.of("Idempotency-Key", key())
        );
        assertOk(platformPublishedV2);
        assertThat(text(platformPublishedV2.body(), "/data/publishedVersion")).isEqualTo("2");
        root = login(ROOT_USERNAME, ROOT_PASSWORD);


        var accounts = root.client().get("/api/v1/platform/admin/accounts?size=200");
        assertOk(accounts);
        var applicantAccount = findItem(accounts, "id", applicant.accountId());
        var applicantPlatformRoles = new LinkedHashSet<String>();
        applicantAccount.path("roleIds").forEach(value -> applicantPlatformRoles.add(value.asText()));
        applicantPlatformRoles.add(platformRoleId);
        var accountUpdated = root.client().putWithCsrf(
                "/api/v1/platform/admin/accounts/" + applicant.accountId(),
                json(Map.of(
                        "displayName", "VS2 Approved Applicant",
                        "status", "ACTIVE",
                        "departmentIds", List.of(platformDepartmentId),
                        "roleIds", List.copyOf(applicantPlatformRoles),
                        "version", applicantAccount.path("version").asText()
                )),
                Map.of()
        );
        assertOk(accountUpdated);
        assertThat(accountUpdated.body().at("/data/departmentIds").toString()).contains(platformDepartmentId);
        assertError(root.client().get("/api/v1/platform/admin/accounts"), 401, "AUTHZ_SNAPSHOT_STALE");

        root = login(ROOT_USERNAME, ROOT_PASSWORD);
        var platformEvaluation = root.client().get(
                "/api/v1/platform/admin/permission-evaluations?accountId=" + applicant.accountId()
        );
        assertOk(platformEvaluation);
        assertDecision(platformEvaluation, "platform.runtime.access", "ALLOW");
        assertDecision(platformEvaluation, "platform.audit.view", "DENY");
        assertThat(platformEvaluation.body().at("/data/sourceRoles").toString()).contains(platformRoleId);

        var systemAdmin = loginRootInSystem(systemId, null);
        var defaultTenantId = text(systemAdmin.switchResponse().body(), "/data/context/tenantId");
        var settings = systemAdmin.client().get("/api/v1/systems/" + systemId + "/admin/settings");
        assertOk(settings);
        var settingsUpdateBody = json(Map.of(
                "name", "VS2 Managed System",
                "description", "System settings updated through HTTP",
                "tenantMode", "MULTI",
                "version", text(settings.body(), "/data/version")
        ));
        var settingsUpdated = systemAdmin.client().putWithCsrf(
                "/api/v1/systems/" + systemId + "/admin/settings", settingsUpdateBody, Map.of()
        );
        assertOk(settingsUpdated);
        var staleSettingsUpdate = systemAdmin.client().putWithCsrf(
                "/api/v1/systems/" + systemId + "/admin/settings", settingsUpdateBody, Map.of()
        );
        assertError(staleSettingsUpdate, 409, "VERSION_CONFLICT");

        var tenantKey = key();
        var tenantBody = json(Map.of(
                "code", "vs2_tenant_" + System.nanoTime(),
                "name", "VS2 Review Tenant"
        ));
        var tenantCreated = systemAdmin.client().postWithCsrf(
                "/api/v1/systems/" + systemId + "/admin/tenants", tenantBody,
                Map.of("Idempotency-Key", tenantKey)
        );
        assertOk(tenantCreated);
        var tenantId = text(tenantCreated.body(), "/data/id");
        assertError(systemAdmin.client().get("/api/v1/systems/" + systemId + "/admin/settings"),
                401, "AUTHZ_SNAPSHOT_STALE");

        systemAdmin = loginRootInSystem(systemId, null);
        var tenantReplay = systemAdmin.client().postWithCsrf(
                "/api/v1/systems/" + systemId + "/admin/tenants", tenantBody,
                Map.of("Idempotency-Key", tenantKey)
        );
        assertOk(tenantReplay);
        assertThat(text(tenantReplay.body(), "/data/id")).isEqualTo(tenantId);

        systemAdmin = loginRootInSystem(systemId, tenantId);
        var systemDepartmentKey = key();
        var systemDepartmentBody = json(Map.of(
                "name", "VS2 Tenant Operations",
                "code", "vs2_tenant_ops_" + System.nanoTime()
        ));
        var systemDepartment = systemAdmin.client().postWithCsrf(
                "/api/v1/systems/" + systemId + "/admin/departments", systemDepartmentBody,
                Map.of("Idempotency-Key", systemDepartmentKey)
        );
        assertOk(systemDepartment);
        var systemDepartmentId = text(systemDepartment.body(), "/data/id");

        systemAdmin = loginRootInSystem(systemId, tenantId);
        var systemDepartmentReplay = systemAdmin.client().postWithCsrf(
                "/api/v1/systems/" + systemId + "/admin/departments", systemDepartmentBody,
                Map.of("Idempotency-Key", systemDepartmentKey)
        );
        assertOk(systemDepartmentReplay);
        assertThat(text(systemDepartmentReplay.body(), "/data/id")).isEqualTo(systemDepartmentId);

        var dataScopes = systemAdmin.client().get(
                "/api/v1/systems/" + systemId + "/admin/data-scopes?size=200"
        );
        assertOk(dataScopes);
        var allDataScope = findItem(dataScopes, "kind", "ALL");
        var allDataScopeId = allDataScope.path("id").asText();

        var systemRoleKey = key();
        var systemRoleBody = json(Map.of(
                "code", "vs2_system_operator_" + System.nanoTime(),
                "name", "VS2 System Operator",
                "description", "Runtime role for approved applicants"
        ));
        var systemRole = systemAdmin.client().postWithCsrf(
                "/api/v1/systems/" + systemId + "/admin/roles", systemRoleBody,
                Map.of("Idempotency-Key", systemRoleKey)
        );
        assertOk(systemRole);
        var systemRoleId = text(systemRole.body(), "/data/id");
        var systemRoleReplay = systemAdmin.client().postWithCsrf(
                "/api/v1/systems/" + systemId + "/admin/roles", systemRoleBody,
                Map.of("Idempotency-Key", systemRoleKey)
        );
        assertOk(systemRoleReplay);
        assertThat(text(systemRoleReplay.body(), "/data/id")).isEqualTo(systemRoleId);

        var systemDraft = systemAdmin.client().putWithCsrf(
                "/api/v1/systems/" + systemId + "/admin/roles/" + systemRoleId + "/draft",
                json(Map.of(
                        "name", "VS2 System Operator",
                        "description", "Runtime allow with settings deny",
                        "permissionCodes", List.of("system.runtime.access", "system.workbench.view"),
                        "deniedPermissionCodes", List.of("system.settings.manage"),
                        "dataScopeId", allDataScopeId,
                        "version", text(systemRole.body(), "/data/version")
                )),
                Map.of()
        );
        assertOk(systemDraft);
        assertThat(text(systemDraft.body(), "/data/draftStatus")).isEqualTo("DRAFT");

        var systemCheckKey = key();
        var systemCheckBody = json(Map.of("version", text(systemDraft.body(), "/data/version")));
        var systemChecked = systemAdmin.client().postWithCsrf(
                "/api/v1/systems/" + systemId + "/admin/roles/" + systemRoleId + "/draft:check",
                systemCheckBody, Map.of("Idempotency-Key", systemCheckKey)
        );
        assertOk(systemChecked);
        assertThat(text(systemChecked.body(), "/data/draftStatus")).isEqualTo("CHECKED");
        var systemCheckReplay = systemAdmin.client().postWithCsrf(
                "/api/v1/systems/" + systemId + "/admin/roles/" + systemRoleId + "/draft:check",
                systemCheckBody, Map.of("Idempotency-Key", systemCheckKey)
        );
        assertOk(systemCheckReplay);

        var systemPublishKey = key();
        var systemPublishBody = json(Map.of("version", text(systemChecked.body(), "/data/version")));
        var systemPublished = systemAdmin.client().postWithCsrf(
                "/api/v1/systems/" + systemId + "/admin/roles/" + systemRoleId + "/draft:publish",
                systemPublishBody, Map.of("Idempotency-Key", systemPublishKey)
        );
        assertOk(systemPublished);
        assertThat(text(systemPublished.body(), "/data/draftStatus")).isEqualTo("PUBLISHED");
        assertThat(text(systemPublished.body(), "/data/publishedVersion")).isEqualTo("1");
        assertError(systemAdmin.client().get("/api/v1/systems/" + systemId + "/admin/settings"),
                401, "AUTHZ_SNAPSHOT_STALE");

        systemAdmin = loginRootInSystem(systemId, tenantId);
        var systemPublishReplay = systemAdmin.client().postWithCsrf(
                "/api/v1/systems/" + systemId + "/admin/roles/" + systemRoleId + "/draft:publish",
                systemPublishBody, Map.of("Idempotency-Key", systemPublishKey)
        );
        assertOk(systemPublishReplay);
        assertThat(text(systemPublishReplay.body(), "/data/publishedVersion")).isEqualTo("1");

        var systemPermissions = systemAdmin.client().get(
                "/api/v1/systems/" + systemId + "/admin/permissions?size=200"
        );
        assertOk(systemPermissions);
        assertThat(systemPermissions.body().at("/data/items").toString())
                .contains("system.runtime.access", "system.permission.explain");
        var ownerMembers = systemAdmin.client().get(
                "/api/v1/systems/" + systemId + "/admin/members?size=200"
        );
        assertOk(ownerMembers);
        var ownerMember = findItem(ownerMembers, "accountId", rootAccountId);
        var ownerEvaluation = systemAdmin.client().get(
                "/api/v1/systems/" + systemId + "/admin/permission-evaluations?memberId="
                        + ownerMember.path("id").asText() + "&tenantId=" + tenantId
        );
        assertOk(ownerEvaluation);
        assertDecision(ownerEvaluation, "system.permission.explain", "ALLOW");

        var applicantSession = login(applicant.username(), applicant.password());
        var accessRequestKey = key();
        var accessRequestBody = json(Map.of(
                "targetTenantId", tenantId,
                "reason", "Need access for the VS2 integration journey"
        ));
        var accessRequest = applicantSession.client().postWithCsrf(
                "/api/v1/context/systems/" + systemId + "/access-requests",
                accessRequestBody, Map.of("Idempotency-Key", accessRequestKey)
        );
        assertOk(accessRequest);
        var accessRequestId = text(accessRequest.body(), "/data/id");
        assertThat(text(accessRequest.body(), "/data/status")).isEqualTo("SUBMITTED");
        var accessRequestReplay = applicantSession.client().postWithCsrf(
                "/api/v1/context/systems/" + systemId + "/access-requests",
                accessRequestBody, Map.of("Idempotency-Key", accessRequestKey)
        );
        assertOk(accessRequestReplay);
        assertThat(text(accessRequestReplay.body(), "/data/id")).isEqualTo(accessRequestId);
        var ownRequests = applicantSession.client().get(
                "/api/v1/context/access-requests?systemId=" + systemId
        );
        assertOk(ownRequests);
        assertThat(findItem(ownRequests, "id", accessRequestId).path("status").asText())
                .isEqualTo("SUBMITTED");

        var reviewer = loginRootInSystem(systemId, tenantId);
        var pendingRequests = reviewer.client().get(
                "/api/v1/systems/" + systemId + "/admin/access-requests?status=SUBMITTED&size=200"
        );
        assertOk(pendingRequests);
        var pendingRequest = findItem(pendingRequests, "id", accessRequestId);
        var approveKey = key();
        var approveBody = json(Map.of(
                "reason", "Approved by the VS2 journey",
                "version", pendingRequest.path("version").asText(),
                "tenantIds", List.of(tenantId),
                "roleIds", List.of(systemRoleId)
        ));
        var approved = reviewer.client().postWithCsrf(
                "/api/v1/systems/" + systemId + "/admin/access-requests/" + accessRequestId + ":approve",
                approveBody, Map.of("Idempotency-Key", approveKey)
        );
        assertOk(approved);
        assertThat(text(approved.body(), "/data/status")).isEqualTo("APPROVED");
        assertError(reviewer.client().get("/api/v1/systems/" + systemId + "/admin/settings"),
                401, "AUTHZ_SNAPSHOT_STALE");

        reviewer = loginRootInSystem(systemId, tenantId);
        var approveReplay = reviewer.client().postWithCsrf(
                "/api/v1/systems/" + systemId + "/admin/access-requests/" + accessRequestId + ":approve",
                approveBody, Map.of("Idempotency-Key", approveKey)
        );
        assertOk(approveReplay);
        assertThat(text(approveReplay.body(), "/data/status")).isEqualTo("APPROVED");

        var applicantSystemSwitch = applicantSession.client().postWithCsrf(
                "/api/v1/context/systems/" + systemId + ":switch", "{}", Map.of()
        );
        assertOk(applicantSystemSwitch);
        assertThat(text(applicantSystemSwitch.body(), "/data/context/systemId")).isEqualTo(systemId);
        var applicantTenants = applicantSession.client().get("/api/v1/context/tenants");
        assertOk(applicantTenants);
        assertThat(applicantTenants.body().at("/data").toString()).contains(tenantId);
        var applicantTenantSwitch = applicantSession.client().postWithCsrf(
                "/api/v1/context/tenants/" + tenantId + ":switch", "{}", Map.of()
        );
        assertOk(applicantTenantSwitch);
        assertThat(text(applicantTenantSwitch.body(), "/data/context/tenantId")).isEqualTo(tenantId);
        assertThat(applicantTenantSwitch.body().at("/data/context/permissions").toString())
                .contains("system.runtime.access", "system.workbench.view")
                .doesNotContain("system.settings.manage");
        assertError(applicantSession.client().get(
                "/api/v1/systems/" + systemId + "/admin/settings"), 403, "PERMISSION_DENIED");

        var crossSystem = reviewer.client().get(
                "/api/v1/systems/" + applicant.firstSystemId() + "/admin/settings"
        );
        assertError(crossSystem, 403, "CONTEXT_SYSTEM_MISMATCH");

        reviewer = loginRootInSystem(systemId, tenantId);
        var approvedMembers = reviewer.client().get(
                "/api/v1/systems/" + systemId + "/admin/members?size=200"
        );
        assertOk(approvedMembers);
        var applicantMember = findItem(approvedMembers, "accountId", applicant.accountId());
        var applicantMemberId = applicantMember.path("id").asText();
        var memberUpdated = reviewer.client().putWithCsrf(
                "/api/v1/systems/" + systemId + "/admin/members/" + applicantMemberId,
                json(Map.of(
                        "displayName", "VS2 Tenant Operator",
                        "status", "ACTIVE",
                        "primaryDepartmentId", systemDepartmentId,
                        "departmentIds", List.of(systemDepartmentId),
                        "tenantIds", List.of(tenantId),
                        "roleIds", List.of(systemRoleId),
                        "version", applicantMember.path("version").asText()
                )),
                Map.of()
        );
        assertOk(memberUpdated);
        assertThat(memberUpdated.body().at("/data/departmentIds").toString()).contains(systemDepartmentId);
        assertError(reviewer.client().get("/api/v1/systems/" + systemId + "/admin/members"),
                401, "AUTHZ_SNAPSHOT_STALE");

        reviewer = loginRootInSystem(systemId, tenantId);
        var applicantEvaluation = reviewer.client().get(
                "/api/v1/systems/" + systemId + "/admin/permission-evaluations?memberId="
                        + applicantMemberId + "&tenantId=" + tenantId
        );
        assertOk(applicantEvaluation);
        assertDecision(applicantEvaluation, "system.runtime.access", "ALLOW");
        assertDecision(applicantEvaluation, "system.settings.manage", "DENY");
        assertThat(applicantEvaluation.body().at("/data/dataScopes").toString()).contains("ALL");

        assertDatabaseEvidence(
                applicant.accountId(), systemId, tenantId, platformRoleId, systemRoleId,
                accessRequestId, applicantMemberId, defaultTenantId
        );
    }

    private RegisteredUser register(String label) throws Exception {
        var unique = Long.toUnsignedString(System.nanoTime(), 36);
        var suffix = label + unique;
        var username = "vs2_" + suffix;
        var password = "Vs2-Applicant-Test-Password-42!";
        var body = json(Map.of(
                "username", username,
                "displayName", "VS2 " + label,
                "password", password,
                "systemName", "VS2 Personal " + label,
                "systemCode", "v2p_" + unique
        ));
        var client = new TestClient();
        var response = client.post(
                "/api/v1/auth/register", body, Map.of("Idempotency-Key", key())
        );
        return new RegisteredUser(
                client, response, username, password,
                text(response.body(), "/data/account/id"),
                text(response.body(), "/data/firstSystemId")
        );
    }

    private LoginSession login(String username, String password) throws Exception {
        var client = new TestClient();
        var response = client.post(
                "/api/v1/auth/login",
                json(Map.of("account", username, "password", password)),
                Map.of()
        );
        assertOk(response);
        return new LoginSession(client, response);
    }

    private SystemSession loginRootInSystem(String systemId, String tenantId) throws Exception {
        var root = login(ROOT_USERNAME, ROOT_PASSWORD);
        var switched = root.client().postWithCsrf(
                "/api/v1/context/systems/" + systemId + ":switch", "{}", Map.of()
        );
        assertOk(switched);
        assertThat(text(switched.body(), "/data/context/systemId")).isEqualTo(systemId);
        if (tenantId != null && !tenantId.equals(text(switched.body(), "/data/context/tenantId"))) {
            switched = root.client().postWithCsrf(
                    "/api/v1/context/tenants/" + tenantId + ":switch", "{}", Map.of()
            );
            assertOk(switched);
        }
        return new SystemSession(root.client(), switched);
    }

    private void assertDatabaseEvidence(
            String applicantAccountId,
            String systemId,
            String tenantId,
            String platformRoleId,
            String systemRoleId,
            String accessRequestId,
            String applicantMemberId,
            String defaultTenantId
    ) {
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from un_audit_operation "
                        + "where result = 'SUCCESS' and operation_type in ("
                        + "'PLATFORM_SYSTEM_CREATED','PLATFORM_DEPARTMENT_CREATED',"
                        + "'PLATFORM_ROLE_DRAFT_PUBLISHED','SYSTEM_TENANT_CREATE',"
                        + "'SYSTEM_DEPARTMENT_CREATE','SYSTEM_ROLE_DRAFT_PUBLISH',"
                        + "'ACCESS_REQUEST_SUBMITTED','SYSTEM_ACCESS_REQUEST_APPROVE','SYSTEM_MEMBER_UPDATE')",
                Integer.class
        )).isGreaterThanOrEqualTo(9);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from un_sys_outbox_event where event_type in ("
                        + "'PLATFORM_ROLE_DRAFT_PUBLISHED','SYSTEM_ROLE_PUBLISHED',"
                        + "'ACCESS_REQUEST_SUBMITTED','SYSTEM_ACCESS_REQUEST_APPROVED')",
                Integer.class
        )).isGreaterThanOrEqualTo(4);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from un_sys_outbox_event where dedupe_key is null or dedupe_key = ''",
                Integer.class
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from un_plat_authz_version "
                        + "where (scope_type = 'PLATFORM' and role_id = ?) "
                        + "or (scope_type = 'SYSTEM' and system_id = ? and role_id = ?)",
                Integer.class, Long.parseLong(platformRoleId), Long.parseLong(systemId), Long.parseLong(systemRoleId)
        )).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from un_plat_member_tenant "
                        + "where system_id = ? and member_id = ? and tenant_id = ? and status = 'ACTIVE'",
                Integer.class, Long.parseLong(systemId), Long.parseLong(applicantMemberId), Long.parseLong(tenantId)
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from un_plat_access_request "
                        + "where id = ? and account_id = ? and system_id = ? and status = 'APPROVED' "
                        + "and resolved_member_id = ?",
                Integer.class,
                Long.parseLong(accessRequestId), Long.parseLong(applicantAccountId),
                Long.parseLong(systemId), Long.parseLong(applicantMemberId)
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from un_plat_member_tenant "
                        + "where system_id = ? and member_id = ? and tenant_id <> ? and status = 'ACTIVE'",
                Integer.class, Long.parseLong(systemId), Long.parseLong(applicantMemberId), Long.parseLong(tenantId)
        )).isZero();
        assertThat(defaultTenantId).isNotBlank().isNotEqualTo(tenantId);
    }

    private JsonNode findItem(TestResponse response, String field, String expected) {
        assertOk(response);
        var items = response.body().at("/data/items");
        assertThat(items.isArray()).isTrue();
        for (var item : items) {
            if (expected.equals(item.path(field).asText())) {
                return item;
            }
        }
        throw new AssertionError("No item with " + field + "=" + expected + " in " + items);
    }

    private static void assertDecision(TestResponse response, String permissionCode, String result) {
        var decisions = response.body().at("/data/decisions");
        assertThat(decisions.isArray()).isTrue();
        for (var decision : decisions) {
            if (permissionCode.equals(decision.path("permissionCode").asText())) {
                assertThat(decision.path("result").asText()).isEqualTo(result);
                return;
            }
        }
        throw new AssertionError("No permission decision for " + permissionCode + " in " + decisions);
    }

    private static void assertOk(TestResponse response) {
        assertThat(response.status())
                .withFailMessage("Expected HTTP 200 but got %s: %s", response.status(), response.body())
                .isEqualTo(200);
        assertThat(text(response.body(), "/code")).isEqualTo("OK");
    }

    private static void assertError(TestResponse response, int status, String code) {
        assertThat(response.status())
                .withFailMessage("Expected HTTP %s but got %s: %s", status, response.status(), response.body())
                .isEqualTo(status);
        assertThat(text(response.body(), "/code")).isEqualTo(code);
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private static String text(JsonNode node, String pointer) {
        return node.at(pointer).asText();
    }

    private static String key() {
        return UUID.randomUUID().toString();
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

    private record RegisteredUser(
            TestClient client,
            TestResponse response,
            String username,
            String password,
            String accountId,
            String firstSystemId
    ) {
    }

    private record LoginSession(TestClient client, TestResponse login) {
    }

    private record SystemSession(TestClient client, TestResponse switchResponse) {
    }

    private record TestResponse(int status, JsonNode body) {
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

        TestResponse post(String path, String body, Map<String, String> headers) throws Exception {
            return request("POST", path, body, headers, false);
        }

        TestResponse postWithCsrf(String path, String body, Map<String, String> headers) throws Exception {
            return request("POST", path, body, headers, true);
        }

        TestResponse putWithCsrf(String path, String body, Map<String, String> headers) throws Exception {
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

        private TestResponse send(HttpRequest.Builder builder) throws IOException, InterruptedException {
            var response = client.send(
                    builder.header("X-Request-ID", "vs2-test-" + UUID.randomUUID()).build(),
                    HttpResponse.BodyHandlers.ofString()
            );
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
