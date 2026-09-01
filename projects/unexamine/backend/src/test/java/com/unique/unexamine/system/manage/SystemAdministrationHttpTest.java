package com.unique.unexamine.system.manage;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SystemAdministrationHttpTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("unexamine")
            .withUsername("unexamine")
            .withPassword("unexamine_test")
            .withCommand("--log-bin-trust-function-creators=1");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("app.domain-verification.allow-private-addresses", () -> "true");
    }

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void administratorEnablesMultiTenantCreatesTenantAndSwitchesContext() {
        Fixture fixture = register("tenant_owner", "tenant_system");

        ResponseEntity<Map> settings = exchange("/api/admin/system/settings", HttpMethod.GET, fixture.systemToken(), null);
        assertThat(settings.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(settings)).containsEntry("tenantMode", "SINGLE");

        ResponseEntity<Map> createBeforeEnable = exchange("/api/admin/system/tenants", HttpMethod.POST,
                fixture.systemToken(), Map.of("code", "branch", "name", "分公司"));
        assertThat(createBeforeEnable.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(createBeforeEnable.getBody()).containsEntry("code", "MULTI_TENANT_NOT_ENABLED");

        ResponseEntity<Map> updated = exchange("/api/admin/system/settings", HttpMethod.PUT, fixture.systemToken(),
                Map.of("name", "租户管理系统", "tenantMode", "SINGLE"));
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(updated)).containsEntry("name", "租户管理系统").containsEntry("tenantMode", "SINGLE");
        migrateToMulti(fixture);

        ResponseEntity<Map> created = exchange("/api/admin/system/tenants", HttpMethod.POST, fixture.systemToken(),
                Map.of("code", "branch", "name", "分公司"));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        long branchId = ((Number) data(created).get("id")).longValue();
        assertThat(data(created)).containsEntry("main", false).containsEntry("status", "ACTIVE");
        assertThat(count("sys_tenant", "system_id = " + fixture.systemId())).isEqualTo(2);
        assertThat(count("sys_tenant_member", "tenant_id = " + branchId + " and tenant_admin = 1")).isOne();
        assertThat(count("sys_role_permission", "role_id in (select id from sys_role where tenant_id = " + branchId + ") and data_scope_type = 'ALL'")).isOne();

        ResponseEntity<Map> entered = exchange("/api/systems/" + fixture.systemId() + "/tenants/" + branchId + "/enter",
                HttpMethod.POST, fixture.platformToken(), Map.of(
                        "previousSystemId", fixture.systemId(), "previousTenantId", fixture.mainTenantId()));
        assertThat(entered.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) data(entered).get("tenantId")).longValue()).isEqualTo(branchId);
        assertThat(data(entered)).containsEntry("tenantName", "分公司");
        assertThat((List<?>) data(entered).get("roleIds")).hasSize(1);
        assertThat((String) data(entered).get("contextRevision")).isNotBlank();
        assertThat(((List<?>) data(entered).get("redrawScopes")).stream().map(String::valueOf).toList())
                .contains("NAVIGATION", "MODULES", "TODO", "MESSAGES");
        Map<?, ?> tenantSwitch = (Map<?, ?>) data(entered).get("tenantSwitchContext");
        assertThat(tenantSwitch.get("tenantId")).isEqualTo((int) branchId);
        assertThat(tenantSwitch.get("tenantSwitchable")).isEqualTo(true);
        String branchToken = (String) ((Map<?, ?>) data(entered).get("tokens")).get("accessToken");

        ResponseEntity<Map> disable = exchange("/api/admin/system/tenants/" + branchId + "/status",
                HttpMethod.PUT, fixture.systemToken(), Map.of("status", "DISABLED"));
        assertThat(disable.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(disable)).containsEntry("status", "DISABLED");
        ResponseEntity<Map> revokedSession = exchange("/api/auth/me", HttpMethod.GET, branchToken, null);
        assertThat(revokedSession.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        ResponseEntity<Map> enterDisabled = exchange("/api/systems/" + fixture.systemId() + "/tenants/" + branchId + "/enter",
                HttpMethod.POST, fixture.platformToken(), null);
        assertThat(enterDisabled.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(enterDisabled.getBody()).containsEntry("code", "TENANT_ACCESS_DENIED");

        assertThat(count("audit_event", "event_code = 'SYSTEM_SETTINGS_UPDATED' and result_code = 'SUCCESS'")).isOne();
        assertThat(count("audit_event", "event_code = 'TENANT_MODE_MIGRATION_COMPLETED' and result_code = 'SUCCESS'")).isOne();
        assertThat(count("audit_event", "event_code = 'SYSTEM_CONTEXT_SWITCHED' and tenant_id = " + branchId)).isOne();
        assertThat(count("audit_event", "event_code = 'TENANT_CREATED' and object_id = '" + branchId + "'")).isOne();
        assertThat(count("audit_event", "event_code = 'TENANT_ENTER' and result_code = 'TENANT_ACCESS_DENIED'")).isOne();
    }

    @Test
    void mainTenantIsProtectedAndMultiTenantCannotReturnToSingleWithSecondaryTenant() {
        Fixture fixture = register("tenant_safety", "tenant_safety_system");
        migrateToMulti(fixture);
        ResponseEntity<Map> created = exchange("/api/admin/system/tenants", HttpMethod.POST, fixture.systemToken(),
                Map.of("code", "branch", "name", "分公司"));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> mainDisabled = exchange("/api/admin/system/tenants/" + fixture.mainTenantId() + "/status",
                HttpMethod.PUT, fixture.systemToken(), Map.of("status", "DISABLED"));
        assertThat(mainDisabled.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(mainDisabled.getBody()).containsEntry("code", "MAIN_TENANT_PROTECTED");
        assertThat(jdbc.queryForObject("select status from sys_tenant where id = ?", String.class, fixture.mainTenantId()))
                .isEqualTo("ACTIVE");

        ResponseEntity<Map> preflight = exchange("/api/admin/system/tenant-mode-migrations/preflight", HttpMethod.POST,
                fixture.systemToken(), Map.of("toMode", "SINGLE"));
        assertThat(preflight.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(preflight)).containsEntry("allowed", false);
        assertThat(((List<?>) data(preflight).get("blockers")).stream().map(String::valueOf).toList())
                .contains("存在其他租户，必须先执行独立租户合并方案");

        ResponseEntity<Map> direct = exchange("/api/admin/system/settings", HttpMethod.PUT, fixture.systemToken(),
                Map.of("name", "租户安全系统", "tenantMode", "SINGLE"));
        assertThat(direct.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(direct.getBody()).containsEntry("code", "TENANT_MODE_MIGRATION_REQUIRED");
    }

    @Test
    void systemDomainRequiresRealProofBeforePublicationAndKeepsTraceableFailure() throws Exception {
        Fixture fixture = register("domain_owner", "domain_system");
        HttpServer proofServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try {
            int port = proofServer.getAddress().getPort();
            ResponseEntity<Map> saved = exchange("/api/admin/system/domains", HttpMethod.POST, fixture.systemToken(),
                    Map.of("domainType", "CUSTOM", "host", "127.0.0.1:" + port,
                            "basePath", "/workspace", "tlsRequired", false));
            assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
            long domainId = ((Number) data(saved).get("id")).longValue();
            String token = (String) data(saved).get("verificationToken");
            assertThat(data(saved)).containsEntry("status", "PENDING_VERIFICATION");

            ResponseEntity<Map> notVerified = exchange("/api/admin/system/domains/" + domainId + "/publish",
                    HttpMethod.POST, fixture.systemToken(), null);
            assertThat(notVerified.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(notVerified.getBody()).containsEntry("code", "DOMAIN_NOT_VERIFIED");
            assertThat((String) notVerified.getBody().get("requestId")).isNotBlank();
            assertThat(((Map<?, ?>) notVerified.getBody().get("data")).get("traceId"))
                    .isEqualTo(notVerified.getBody().get("requestId"));

            proofServer.createContext("/workspace/.well-known/unexamine-domain-verification.txt", exchange -> {
                byte[] body = token.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            proofServer.start();

            ResponseEntity<Map> verified = exchange("/api/admin/system/domains/" + domainId + "/verify",
                    HttpMethod.POST, fixture.systemToken(), null);
            assertThat(verified.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(data(verified)).containsEntry("status", "VERIFIED");
            assertThat(data(verified).get("verifiedAt")).isNotNull();

            ResponseEntity<Map> published = exchange("/api/admin/system/domains/" + domainId + "/publish",
                    HttpMethod.POST, fixture.systemToken(), null);
            assertThat(published.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(data(published)).containsEntry("status", "PUBLISHED");
            assertThat(jdbc.queryForObject("select status from sys_system_domain where id=?", String.class, domainId))
                    .isEqualTo("PUBLISHED");
            assertThat(count("core_setting", "system_id = " + fixture.systemId()
                    + " and setting_key = 'domain.verification." + domainId + "'")).isOne();
            assertThat(count("audit_event", "event_code = 'SYSTEM_DOMAIN_PUBLISH' and result_code = 'DOMAIN_NOT_VERIFIED'")).isOne();
            assertThat(count("audit_event", "event_code = 'SYSTEM_DOMAIN_VERIFIED' and object_id = '" + domainId + "'")).isOne();
            assertThat(count("audit_event", "event_code = 'SYSTEM_DOMAIN_PUBLISHED' and object_id = '" + domainId + "'")).isOne();
        } finally {
            proofServer.stop(0);
        }
    }

    @Test
    void memberWithoutSystemManagementPermissionCannotReadOrChangeSettings() {
        Fixture fixture = register("tenant_limited", "tenant_limited_system");
        Long tenantMemberId = jdbc.queryForObject("select id from sys_tenant_member where tenant_id = ?", Long.class,
                fixture.mainTenantId());
        jdbc.update("delete from sys_member_role where tenant_member_id = ?", tenantMemberId);
        ResponseEntity<Map> entered = exchange("/api/systems/" + fixture.systemId() + "/enter", HttpMethod.POST,
                fixture.platformToken(), null);
        String restrictedToken = (String) ((Map<?, ?>) data(entered).get("tokens")).get("accessToken");

        ResponseEntity<Map> denied = exchange("/api/admin/system/settings", HttpMethod.GET, restrictedToken, null);
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(denied.getBody()).containsEntry("code", "PERMISSION_DENIED");
    }

    @Test
    void platformAndSystemFixedConfigurationsStayInTheirAuthorizedContexts() {
        Fixture fixture = register("configuration_owner", "configuration_system");
        String platformAdminToken = (String) data(http.postForEntity("/api/auth/login",
                Map.of("username", "admin", "password", "123123aa"), Map.class)).get("accessToken");

        ResponseEntity<Map> platformSaved = exchange("/api/admin/platform/configurations", HttpMethod.POST,
                platformAdminToken, Map.of(
                        "category", "BUSINESS_PARAMETER",
                        "settingKey", "contact.follow-up-days",
                        "value", Map.of("days", 7),
                        "sensitive", false));
        assertThat(platformSaved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(platformSaved)).containsEntry("contextType", "PLATFORM")
                .containsEntry("platformId", 1)
                .containsEntry("systemId", null)
                .containsEntry("version", 0);
        assertThat(((Map<?, ?>) data(platformSaved).get("value")).get("days")).isEqualTo(7);

        ResponseEntity<Map> systemCannotWritePlatform = exchange("/api/admin/platform/configurations", HttpMethod.POST,
                fixture.systemToken(), Map.of(
                        "category", "BUSINESS_PARAMETER", "settingKey", "cross-context", "value", true,
                        "sensitive", false));
        assertThat(systemCannotWritePlatform.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(systemCannotWritePlatform.getBody()).containsEntry("code", "PERMISSION_DENIED");

        ResponseEntity<Map> platformCannotWriteSystem = exchange("/api/admin/system/configurations", HttpMethod.POST,
                platformAdminToken, Map.of(
                        "category", "BUSINESS_PARAMETER", "settingKey", "cross-context", "value", true,
                        "sensitive", false));
        assertThat(platformCannotWriteSystem.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(platformCannotWriteSystem.getBody()).containsEntry("code", "PERMISSION_DENIED");

        ResponseEntity<Map> systemSaved = exchange("/api/admin/system/configurations", HttpMethod.POST,
                fixture.systemToken(), Map.of(
                        "category", "BUSINESS_PARAMETER",
                        "settingKey", "contact.follow-up-days",
                        "value", Map.of("days", 3),
                        "sensitive", false));
        assertThat(systemSaved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(systemSaved)).containsEntry("contextType", "SYSTEM")
                .containsEntry("systemId", (int) fixture.systemId())
                .containsEntry("version", 0);
        assertThat(((Map<?, ?>) data(systemSaved).get("value")).get("days")).isEqualTo(3);

        long systemSettingId = ((Number) data(systemSaved).get("id")).longValue();
        ResponseEntity<Map> updated = exchange("/api/admin/system/configurations", HttpMethod.POST,
                fixture.systemToken(), Map.of(
                        "category", "BUSINESS_PARAMETER",
                        "settingKey", "contact.follow-up-days",
                        "value", Map.of("days", 5),
                        "sensitive", false,
                        "expectedVersion", 0));
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(updated)).containsEntry("id", (int) systemSettingId).containsEntry("version", 1);

        ResponseEntity<Map> stale = exchange("/api/admin/system/configurations", HttpMethod.POST,
                fixture.systemToken(), Map.of(
                        "category", "BUSINESS_PARAMETER",
                        "settingKey", "contact.follow-up-days",
                        "value", Map.of("days", 9),
                        "sensitive", false,
                        "expectedVersion", 0));
        assertThat(stale.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(stale.getBody()).containsEntry("code", "CONCURRENT_MODIFICATION");

        assertThat(jdbc.queryForObject("select count(*) from core_setting where context_type='PLATFORM' and system_id is null and setting_key='contact.follow-up-days'", Integer.class)).isOne();
        assertThat(jdbc.queryForObject("select count(*) from core_setting where context_type='SYSTEM' and system_id=? and setting_key='contact.follow-up-days'", Integer.class, fixture.systemId())).isOne();
        assertThat(jdbc.queryForObject("select json_extract(value_json, '$.days') from core_setting where id=?", Integer.class, systemSettingId)).isEqualTo(5);
        assertThat(count("audit_event", "event_code = 'PLATFORM_SETTING_SAVED' and result_code = 'SUCCESS'")).isOne();
        assertThat(count("audit_event", "event_code = 'SYSTEM_SETTING_SAVED' and object_id = '" + systemSettingId + "'")).isEqualTo(2);
    }

    @Test
    void systemInheritsPublishedIdentityProviderPreflightsAndConfirmsMemberMappings() {
        Fixture fixture = register("identity_owner", "identity_target_system");
        Fixture guest = register("identity_guest", "identity_guest_system");
        register("identity_conflict", "identity_conflict_system");
        long ownerAccountId = jdbc.queryForObject("select id from plat_account where username='identity_owner'", Long.class);
        long guestAccountId = jdbc.queryForObject("select id from plat_account where username='identity_guest'", Long.class);
        long conflictAccountId = jdbc.queryForObject("select id from plat_account where username='identity_conflict'", Long.class);
        jdbc.update("insert into sys_department(system_id,tenant_id,parent_id,code,name,path_code,sort_order,status) values (?,?,null,'HQ','总部','/HQ',0,'ACTIVE')",
                fixture.systemId(), fixture.mainTenantId());
        long departmentId = jdbc.queryForObject(
                "select id from sys_department where system_id=? and tenant_id=? and code='HQ'", Long.class,
                fixture.systemId(), fixture.mainTenantId());

        jdbc.update("insert into plat_sso_provider(code,name,protocol,issuer,client_id,metadata_json,status) values (?,?,?,?,?,json_object(),'DRAFT')",
                "mapping_oidc", "企业目录", "OIDC", "https://id.example.com", "mapping-client");
        long providerId = jdbc.queryForObject("select id from plat_sso_provider where code='mapping_oidc'", Long.class);
        jdbc.update("insert into plat_sso_provider_version(provider_id,version_number,protocol,issuer,client_id,protocol_config_json,allowed_domains_json,attribute_mapping_json,jit_policy_json,mfa_policy_json,callback_uris_json,test_status,test_report_json,status,published_at,published_by_account_id) " +
                        "values (?,1,'OIDC','https://id.example.com','mapping-client',json_object(),json_array('example.com'),json_object(),json_object(),json_object(),json_array('http://localhost:5173/auth/sso/callback'),'PASSED',json_object(),'PUBLISHED',now(3),1)",
                providerId);
        long providerVersionId = jdbc.queryForObject(
                "select id from plat_sso_provider_version where provider_id=? and version_number=1", Long.class, providerId);
        jdbc.update("update plat_sso_provider set status='PUBLISHED', published_version_id=?, published_version_number=1 where id=?",
                providerVersionId, providerId);
        jdbc.update("insert into plat_sso_identity(provider_id,external_subject,account_id,attributes_json) values (?,?,?,json_object())",
                providerId, "conflict-ext", conflictAccountId);

        ResponseEntity<Map> providers = exchange("/api/admin/system/identity-mapping/providers", HttpMethod.GET,
                fixture.systemToken(), null);
        assertThat(providers.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) providers.getBody().get("data")).anySatisfy(value -> {
            assertThat(((Map<?, ?>) value).get("code")).isEqualTo("mapping_oidc");
            assertThat(((Map<?, ?>) value).get("publishedVersionId")).isEqualTo((int) providerVersionId);
        });

        ResponseEntity<Map> saved = exchange("/api/admin/system/identity-mapping/configuration", HttpMethod.POST,
                fixture.systemToken(), Map.of(
                        "providerId", providerId,
                        "providerVersionId", providerVersionId,
                        "tenantDomain", "tenant.example.com",
                        "departmentMappings", Map.of("HQ", departmentId),
                        "matchOrder", List.of("EXTERNAL_USER_ID", "EMAIL", "MOBILE", "EMPLOYEE_NO"),
                        "jitPolicy", "ACCESS_REQUEST"));
        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(saved)).containsEntry("providerCode", "mapping_oidc")
                .containsEntry("tenantDomain", "tenant.example.com").containsEntry("version", 0);

        List<Map<String, Object>> samples = List.of(
                Map.of("externalUserId", "owner-ext", "externalDepartment", "HQ",
                        "email", "identity_owner@example.com", "displayName", "身份管理员",
                        "mfaLevel", "MFA", "device", "Chrome/Windows", "requestId", "req-owner"),
                Map.of("externalUserId", "guest-ext", "externalDepartment", "HQ",
                        "email", "identity_guest@example.com", "displayName", "待授权成员",
                        "mfaLevel", "MFA", "device", "Edge/Windows", "requestId", "req-guest"),
                Map.of("externalUserId", "missing-dept-ext", "externalDepartment", "UNKNOWN",
                        "email", "missing@example.com", "displayName", "缺失部门成员", "requestId", "req-missing"),
                Map.of("externalUserId", "conflict-ext", "externalDepartment", "HQ",
                        "email", "identity_guest@example.com", "displayName", "冲突成员", "requestId", "req-conflict"));
        ResponseEntity<Map> preflight = exchange("/api/admin/system/identity-mapping/preflight", HttpMethod.POST,
                fixture.systemToken(), Map.of("samples", samples));
        assertThat(preflight.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> preflightData = data(preflight);
        Map<?, ?> preflightSummary = (Map<?, ?>) preflightData.get("summary");
        assertThat(preflightSummary.get("READY")).isEqualTo(1);
        assertThat(preflightSummary.get("ACCESS_REQUEST")).isEqualTo(1);
        assertThat(preflightSummary.get("MANUAL_REVIEW")).isEqualTo(1);
        assertThat(preflightSummary.get("CONFLICT")).isEqualTo(1);
        assertThat((List<?>) preflightData.get("items")).anySatisfy(value -> {
            assertThat(((Map<?, ?>) value).get("externalUserId")).isEqualTo("missing-dept-ext");
            assertThat(((Map<?, ?>) value).get("plannedAction")).isEqualTo("MANUAL_REVIEW");
        });

        ResponseEntity<Map> confirmed = exchange("/api/admin/system/identity-mapping/confirm", HttpMethod.POST,
                fixture.systemToken(), Map.of("preflightId", preflightData.get("preflightId"), "confirmed", true));
        assertThat(confirmed.getStatusCode()).withFailMessage("confirm response: %s", confirmed.getBody())
                .isEqualTo(HttpStatus.OK);
        assertThat(data(confirmed)).containsEntry("status", "SUCCEEDED_WITH_WARNINGS")
                .containsEntry("progressCurrent", 4).containsEntry("progressTotal", 4);
        long jobId = ((Number) data(confirmed).get("id")).longValue();

        assertThat(jdbc.queryForObject("select count(*) from job_background where id=? and job_type='SYSTEM_IDENTITY_MAPPING_SYNC'", Integer.class, jobId)).isOne();
        assertThat(jdbc.queryForObject("select count(*) from job_item_result where job_id=?", Integer.class, jobId)).isEqualTo(4);
        assertThat(jdbc.queryForObject("select count(*) from plat_sso_identity where provider_id=? and external_subject in ('owner-ext','guest-ext')", Integer.class, providerId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from sys_access_request where system_id=? and account_id=? and status='PENDING'", Integer.class, fixture.systemId(), guestAccountId)).isOne();
        assertThat(jdbc.queryForObject("select count(*) from sys_member where system_id=? and account_id=?", Integer.class, fixture.systemId(), guestAccountId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from sys_member where system_id=? and account_id=?", Integer.class, fixture.systemId(), ownerAccountId)).isOne();

        ResponseEntity<Map> logs = exchange(
                "/api/admin/system/identity-mapping/logs?identityProvider=mapping_oidc&externalUserId=guest-ext&requestId=req-guest",
                HttpMethod.GET, fixture.systemToken(), null);
        assertThat(logs.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) logs.getBody().get("data")).singleElement().satisfies(value -> {
            assertThat(((Map<?, ?>) value).get("resultCode")).isEqualTo("ACCESS_REQUEST_PENDING");
            assertThat(((Map<?, ?>) value).get("systemMemberId")).isNull();
            assertThat(((Map<?, ?>) value).get("jobId")).isEqualTo((int) jobId);
        });

        ResponseEntity<Map> reread = exchange("/api/admin/system/identity-mapping/configuration", HttpMethod.GET,
                fixture.systemToken(), null);
        assertThat(reread.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(reread)).containsEntry("confirmedJobId", (int) jobId).containsEntry("version", 2);
    }

    @Test
    void organizationRoleDraftPublishRefreshesTargetSessionAndRejectsInvalidAuthorization() {
        Fixture owner = register("authorization_owner", "authorization_system");
        Fixture target = register("authorization_target", "authorization_target_system");
        long targetAccountId = jdbc.queryForObject(
                "select id from plat_account where username='authorization_target'", Long.class);
        jdbc.update("insert into sys_member(system_id,account_id,employee_number,display_name,status) values (?,?,?,'目标成员','ACTIVE')",
                owner.systemId(), targetAccountId, "AUTH-002");
        long targetSystemMemberId = jdbc.queryForObject(
                "select id from sys_member where system_id=? and account_id=?", Long.class,
                owner.systemId(), targetAccountId);
        jdbc.update("insert into sys_tenant_member(system_id,tenant_id,system_member_id,tenant_admin,status) values (?,?,?,0,'ACTIVE')",
                owner.systemId(), owner.mainTenantId(), targetSystemMemberId);
        long targetTenantMemberId = jdbc.queryForObject(
                "select id from sys_tenant_member where tenant_id=? and system_member_id=?", Long.class,
                owner.mainTenantId(), targetSystemMemberId);

        ResponseEntity<Map> root = exchange("/api/admin/system/authorization/departments", HttpMethod.POST,
                owner.systemToken(), Map.of("code", "headquarters", "name", "总部", "sortOrder", 10));
        assertThat(root.getStatusCode()).isEqualTo(HttpStatus.OK);
        long rootId = ((Number) data(root).get("id")).longValue();
        ResponseEntity<Map> child = exchange("/api/admin/system/authorization/departments", HttpMethod.POST,
                owner.systemToken(), Map.of("parentId", rootId, "code", "sales", "name", "销售部", "sortOrder", 20));
        assertThat(child.getStatusCode()).isEqualTo(HttpStatus.OK);
        long childId = ((Number) data(child).get("id")).longValue();
        assertThat(data(child)).containsEntry("pathCode", "headquarters/sales");
        ResponseEntity<Map> cycle = exchange("/api/admin/system/authorization/departments", HttpMethod.POST,
                owner.systemToken(), Map.of("id", rootId, "parentId", childId, "code", "headquarters",
                        "name", "总部", "sortOrder", 10, "expectedVersion", 0));
        assertThat(cycle.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(cycle.getBody()).containsEntry("code", "ORGANIZATION_CYCLE");

        long groupId = ((Number) data(exchange("/api/admin/module-config/groups", HttpMethod.POST,
                owner.systemToken(), Map.of("code", "auth_sales", "name", "授权测试", "sortOrder", 10))).get("id")).longValue();
        Map<String, Object> moduleDraft = data(exchange("/api/admin/module-config/modules", HttpMethod.POST,
                owner.systemToken(), Map.of("groupId", groupId, "code", "auth_customer", "name", "权限客户")));
        long moduleId = ((Number) ((Map<?, ?>) moduleDraft.get("module")).get("id")).longValue();
        ResponseEntity<Map> field = exchange("/api/admin/module-config/modules/" + moduleId + "/fields",
                HttpMethod.POST, owner.systemToken(), Map.of("code", "customer_name", "name", "客户名称",
                        "fieldType", "TEXT", "required", true, "sortOrder", 10, "config", Map.of()));
        assertThat(field.getStatusCode()).isEqualTo(HttpStatus.OK);
        int draftRevision = jdbc.queryForObject("select draft_revision from cfg_module where id=?", Integer.class, moduleId);
        assertThat(exchange("/api/admin/module-config/modules/" + moduleId + "/publish", HttpMethod.POST,
                owner.systemToken(), Map.of("expectedDraftRevision", draftRevision)).getStatusCode()).isEqualTo(HttpStatus.OK);

        List<Map<String, Object>> managerPermissions = List.of(Map.of(
                "resourceType", "CONFIG", "resourceCode", "SYSTEM", "actionCode", "MANAGE",
                "dataScopeType", "SELF"));
        ResponseEntity<Map> managerDraft = exchange("/api/admin/system/authorization/roles", HttpMethod.POST,
                owner.systemToken(), Map.of("code", "limited_manager", "name", "有限权限管理员",
                        "description", "只维护组织角色", "permissions", managerPermissions, "fieldPolicies", List.of()));
        assertThat(managerDraft.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(managerDraft)).containsEntry("status", "DRAFT");
        long managerRoleId = ((Number) data(managerDraft).get("id")).longValue();

        ResponseEntity<Map> assignedDraft = exchange(
                "/api/admin/system/authorization/members/" + targetTenantMemberId + "/assignment",
                HttpMethod.POST, owner.systemToken(), Map.of("departmentId", childId,
                        "roleIds", List.of(managerRoleId), "expectedVersion", 0));
        assertThat(assignedDraft.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> targetEntered = exchange("/api/systems/" + owner.systemId() + "/enter",
                HttpMethod.POST, target.platformToken(), null);
        assertThat(targetEntered.getStatusCode()).isEqualTo(HttpStatus.OK);
        String targetSystemToken = (String) ((Map<?, ?>) data(targetEntered).get("tokens")).get("accessToken");
        ResponseEntity<Map> beforePublish = exchange("/api/admin/system/authorization", HttpMethod.GET,
                targetSystemToken, null);
        assertThat(beforePublish.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        ResponseEntity<Map> previewDenied = exchange("/api/admin/system/authorization/preview", HttpMethod.POST,
                targetSystemToken, Map.of("tenantMemberId", targetTenantMemberId,
                        "resourceCode", "auth_customer", "actionCode", "LIST", "expectedPermissionVersion", 0));
        assertThat(previewDenied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<Map> managerPublished = exchange(
                "/api/admin/system/authorization/roles/" + managerRoleId + "/publish", HttpMethod.POST,
                owner.systemToken(), Map.of("reason", "开放组织维护入口", "expectedVersion", 0));
        assertThat(managerPublished.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(managerPublished)).containsEntry("status", "ACTIVE");
        ResponseEntity<Map> refreshedManager = exchange("/api/admin/system/authorization", HttpMethod.GET,
                targetSystemToken, null);
        assertThat(refreshedManager.getStatusCode()).isEqualTo(HttpStatus.OK);
        // The assignment and the role publication each invalidate the effective permission snapshot.
        assertThat(data(refreshedManager)).containsEntry("permissionVersion", 2);

        ResponseEntity<Map> overgrant = exchange("/api/admin/system/authorization/roles", HttpMethod.POST,
                targetSystemToken, Map.of("code", "illegal_auditor", "name", "越权审计员",
                        "permissions", List.of(Map.of("resourceType", "AUDIT", "resourceCode", "EVENT",
                                "actionCode", "VIEW", "dataScopeType", "ALL")), "fieldPolicies", List.of()));
        assertThat(overgrant.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(overgrant.getBody()).containsEntry("code", "AUTHORIZATION_OVERGRANT");
        ResponseEntity<Map> invalidAction = exchange("/api/admin/system/authorization/roles", HttpMethod.POST,
                owner.systemToken(), Map.of("code", "invalid_action", "name", "非法动作",
                        "permissions", List.of(Map.of("resourceType", "MODULE", "resourceCode", "auth_customer",
                                "actionCode", "DROP_DATABASE", "dataScopeType", "ALL")), "fieldPolicies", List.of()));
        assertThat(invalidAction.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(invalidAction.getBody()).containsEntry("code", "AUTHORIZATION_REFERENCE_INVALID");
        ResponseEntity<Map> invalidField = exchange("/api/admin/system/authorization/roles", HttpMethod.POST,
                owner.systemToken(), Map.of("code", "invalid_field", "name", "非法字段",
                        "permissions", List.of(Map.of("resourceType", "MODULE", "resourceCode", "auth_customer",
                                "actionCode", "LIST", "dataScopeType", "ALL")),
                        "fieldPolicies", List.of(Map.of("resourceCode", "auth_customer", "fieldCode", "missing",
                                "channel", "PAGE", "readable", true, "writable", false))));
        assertThat(invalidField.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(invalidField.getBody()).containsEntry("code", "AUTHORIZATION_FIELD_REFERENCE_INVALID");

        List<Map<String, Object>> operatorPermissions = List.of(
                Map.of("resourceType", "MODULE", "resourceCode", "auth_customer", "actionCode", "LIST", "dataScopeType", "DEPARTMENT"),
                Map.of("resourceType", "AUDIT", "resourceCode", "EVENT", "actionCode", "VIEW", "dataScopeType", "SELF"));
        ResponseEntity<Map> operatorDraft = exchange("/api/admin/system/authorization/roles", HttpMethod.POST,
                owner.systemToken(), Map.of("code", "sales_operator", "name", "销售查看员",
                        "permissions", operatorPermissions,
                        "fieldPolicies", List.of(
                                Map.of("resourceCode", "auth_customer", "fieldCode", "customer_name",
                                        "channel", "PAGE", "readable", true, "writable", false, "maskStrategy", "PARTIAL"),
                                Map.of("resourceCode", "auth_customer", "fieldCode", "customer_name",
                                        "channel", "APPLICATION", "readable", true, "writable", false, "maskStrategy", "PARTIAL"),
                                Map.of("resourceCode", "auth_customer", "fieldCode", "customer_name",
                                        "channel", "FILE", "readable", true, "writable", false, "maskStrategy", "FULL"))));
        assertThat(operatorDraft.getStatusCode()).isEqualTo(HttpStatus.OK);
        long operatorRoleId = ((Number) data(operatorDraft).get("id")).longValue();
        int currentMemberVersion = jdbc.queryForObject(
                "select version from sys_tenant_member where id=?", Integer.class, targetTenantMemberId);
        assertThat(exchange("/api/admin/system/authorization/members/" + targetTenantMemberId + "/assignment",
                HttpMethod.POST, owner.systemToken(), Map.of("departmentId", childId,
                        "roleIds", List.of(managerRoleId, operatorRoleId), "expectedVersion", currentMemberVersion)).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(exchange("/api/admin/system/authorization/roles/" + operatorRoleId + "/publish", HttpMethod.POST,
                owner.systemToken(), Map.of("reason", "发布销售查看范围", "expectedVersion", 0)).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> visibleModules = exchange("/api/runtime/modules", HttpMethod.GET, targetSystemToken, null);
        assertThat(visibleModules.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) visibleModules.getBody().get("data")).singleElement().satisfies(value ->
                assertThat(((Map<?, ?>) value).get("moduleCode")).isEqualTo("auth_customer"));
        assertThat(exchange("/api/admin/audit-events", HttpMethod.GET, targetSystemToken, null).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> preview = exchange("/api/admin/system/authorization/preview", HttpMethod.POST,
                owner.systemToken(), Map.of("tenantMemberId", targetTenantMemberId,
                        "resourceCode", "auth_customer", "actionCode", "LIST", "expectedPermissionVersion", 4));
        assertThat(preview.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(preview)).containsEntry("permissionVersion", 4);
        assertThat((List<?>) data(preview).get("roles")).hasSize(2).anySatisfy(value -> {
            Map<?, ?> role = (Map<?, ?>) value;
            if ("sales_operator".equals(role.get("roleCode"))) {
                assertThat((List<?>) role.get("contributedPermissions")).singleElement();
            }
        });
        assertThat((List<?>) data(preview).get("actions")).singleElement().satisfies(value -> {
            Map<?, ?> decision = (Map<?, ?>) value;
            assertThat(decision.get("actionCode")).isEqualTo("LIST");
            assertThat(decision.get("allowed")).isEqualTo(true);
            assertThat(((Map<?, ?>) decision.get("dataScope")).get("mode")).isEqualTo("SINGLE");
        });
        assertThat((List<?>) data(preview).get("fields")).singleElement().satisfies(value -> {
            Map<?, ?> fieldDecision = (Map<?, ?>) value;
            assertThat(fieldDecision.get("fieldCode")).isEqualTo("customer_name");
            assertThat(fieldDecision.get("readable")).isEqualTo(true);
            assertThat(fieldDecision.get("writable")).isEqualTo(false);
        });
        ResponseEntity<Map> stalePreview = exchange("/api/admin/system/authorization/preview", HttpMethod.POST,
                owner.systemToken(), Map.of("tenantMemberId", targetTenantMemberId,
                        "resourceCode", "auth_customer", "actionCode", "LIST", "expectedPermissionVersion", 1));
        assertThat(stalePreview.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(stalePreview.getBody()).containsEntry("code", "AUTHORIZATION_VERSION_EXPIRED");

        long previewAuditId = jdbc.queryForObject(
                "select id from audit_event where event_code='AUTHORIZATION_PERMISSION_PREVIEW' and object_id=? order by id desc limit 1",
                Long.class, String.valueOf(targetTenantMemberId));
        jdbc.update("insert into audit_field_change(audit_event_id,field_code,value_type,before_value_json,after_value_json,sensitivity) values (?,'email','STRING',json_quote('old@example.com'),json_quote('new@example.com'),'SENSITIVE')",
                previewAuditId);
        ResponseEntity<Map> maskedDetail = exchange("/api/admin/audit-events/" + previewAuditId,
                HttpMethod.GET, targetSystemToken, null);
        assertThat(maskedDetail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(maskedDetail)).containsEntry("sensitiveValuesVisible", false);
        assertThat((List<?>) data(maskedDetail).get("fieldChanges")).singleElement().satisfies(value -> {
            Map<?, ?> change = (Map<?, ?>) value;
            assertThat(change.get("masked")).isEqualTo(true);
            assertThat(change.get("afterValueJson")).isEqualTo("\"***\"");
        });
        ResponseEntity<Map> visibleDetail = exchange("/api/admin/audit-events/" + previewAuditId,
                HttpMethod.GET, owner.systemToken(), null);
        assertThat(data(visibleDetail)).containsEntry("sensitiveValuesVisible", true);

        ResponseEntity<Map> broadRetention = exchange("/api/admin/audit-events/retention/preflight",
                HttpMethod.POST, owner.systemToken(), Map.of("objectType", "*", "objectId", "*"));
        assertThat(broadRetention.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(broadRetention)).containsEntry("allowed", false);
        ResponseEntity<Map> retentionPreflight = exchange("/api/admin/audit-events/retention/preflight",
                HttpMethod.POST, owner.systemToken(), Map.of("objectType", "SYSTEM_TENANT_MEMBER",
                        "objectId", String.valueOf(targetTenantMemberId)));
        assertThat(retentionPreflight.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(retentionPreflight)).containsEntry("allowed", true)
                .containsEntry("auditEventCount", 3).containsEntry("fieldChangeCount", 1);
        ResponseEntity<Map> retentionDenied = exchange("/api/admin/audit-events/retention",
                HttpMethod.POST, targetSystemToken, Map.of("objectType", "SYSTEM_TENANT_MEMBER",
                        "objectId", String.valueOf(targetTenantMemberId), "businessKey", "AUTH-002",
                        "reason", "依法永久保留审计摘要", "approvalReference", "APPROVAL-C16-001",
                        "referenceImpactConfirmed", true));
        assertThat(retentionDenied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        ResponseEntity<Map> retained = exchange("/api/admin/audit-events/retention",
                HttpMethod.POST, owner.systemToken(), Map.of("objectType", "SYSTEM_TENANT_MEMBER",
                        "objectId", String.valueOf(targetTenantMemberId), "businessKey", "AUTH-002",
                        "reason", "依法永久保留审计摘要", "approvalReference", "APPROVAL-C16-001",
                        "referenceImpactConfirmed", true));
        assertThat(retained.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((String) data(retained).get("snapshotHash")).hasSize(64);
        assertThat(count("audit_retention_marker", "object_type = 'SYSTEM_TENANT_MEMBER' and object_id = '" + targetTenantMemberId + "'")).isOne();
        assertThat(count("audit_event", "id = " + previewAuditId)).isOne();

        assertThat(jdbc.queryForObject("select max(version_number) from auth_permission_version where system_id=? and tenant_id=?",
                Long.class, owner.systemId(), owner.mainTenantId())).isEqualTo(4L);
        assertThat(jdbc.queryForObject("select permission_version from auth_session where account_id=? and system_id=? order by id desc limit 1",
                Long.class, targetAccountId, owner.systemId())).isEqualTo(4L);
        assertThat(count("auth_field_policy", "role_id = " + operatorRoleId
                + " and field_code = 'customer_name' and readable = 1")).isEqualTo(3);
        assertThat(count("auth_field_policy", "role_id = " + operatorRoleId
                + " and field_code = 'customer_name' and `channel` in ('PAGE','APPLICATION','FILE')")).isEqualTo(3);
        assertThat(count("audit_event", "event_code = 'SYSTEM_AUTHORIZATION_PUBLISHED' and system_id = " + owner.systemId())).isEqualTo(2);
        assertThat(count("audit_event", "event_code = 'AUTHORIZATION_PERMISSION_PREVIEW' and object_id = '" + targetTenantMemberId + "'")).isOne();
    }

    private void migrateToMulti(Fixture fixture) {
        ResponseEntity<Map> preflight = exchange("/api/admin/system/tenant-mode-migrations/preflight", HttpMethod.POST,
                fixture.systemToken(), Map.of("toMode", "MULTI"));
        assertThat(preflight.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(preflight)).containsEntry("fromMode", "SINGLE").containsEntry("toMode", "MULTI")
                .containsEntry("allowed", true);

        ResponseEntity<Map> requested = exchange("/api/admin/system/tenant-mode-migrations", HttpMethod.POST,
                fixture.systemToken(), Map.of("toMode", "MULTI"));
        assertThat(requested.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(requested)).containsEntry("status", "PENDING_APPROVAL");
        long migrationId = ((Number) data(requested).get("id")).longValue();

        ResponseEntity<Map> approved = exchange(
                "/api/admin/system/tenant-mode-migrations/" + migrationId + "/decision",
                HttpMethod.POST, fixture.systemToken(), Map.of(
                        "approved", true, "comment", "发布检查通过，执行模式迁移", "expectedVersion", 0));
        assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(approved)).containsEntry("status", "COMPLETED");
        assertThat(data(approved).get("jobId")).isNotNull();
        assertThat(jdbc.queryForObject("select tenant_mode from sys_system where id=?", String.class, fixture.systemId()))
                .isEqualTo("MULTI");
        assertThat(count("job_item_result", "job_id = " + data(approved).get("jobId"))).isEqualTo(4);
    }

    private Fixture register(String username, String systemCode) {
        ResponseEntity<Map> registration = http.postForEntity("/api/auth/register", Map.of(
                "username", username,
                "password", "correct-password",
                "displayName", "租户管理员",
                "email", username + "@example.com",
                "systemName", "租户测试系统",
                "systemCode", systemCode), Map.class);
        assertThat(registration.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> registrationData = data(registration);
        long systemId = ((Number) registrationData.get("systemId")).longValue();
        long tenantId = ((Number) registrationData.get("tenantId")).longValue();
        String systemToken = (String) ((Map<?, ?>) registrationData.get("tokens")).get("accessToken");

        ResponseEntity<Map> login = http.postForEntity("/api/auth/login",
                Map.of("username", username, "password", "correct-password"), Map.class);
        String platformToken = (String) data(login).get("accessToken");
        return new Fixture(systemId, tenantId, systemToken, platformToken);
    }

    private int count(String table, String condition) {
        return jdbc.queryForObject("select count(*) from " + table + " where " + condition, Integer.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(ResponseEntity<Map> response) {
        return (Map<String, Object>) response.getBody().get("data");
    }

    private ResponseEntity<Map> exchange(String path, HttpMethod method, String accessToken, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return http.exchange(path, method, new HttpEntity<>(body, headers), Map.class);
    }

    private record Fixture(long systemId, long mainTenantId, String systemToken, String platformToken) {
    }
}
