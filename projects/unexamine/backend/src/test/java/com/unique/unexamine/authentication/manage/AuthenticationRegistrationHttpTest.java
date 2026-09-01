package com.unique.unexamine.authentication.manage;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.unique.unexamine.shared.manage.web.ApiResult;
import com.unique.unexamine.authorization.manage.RequirePermission;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(AuthenticationRegistrationHttpTest.TestPermissionController.class)
class AuthenticationRegistrationHttpTest {
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
        registry.add("test.sso.secret", () -> "local-oidc-secret");
        registry.add("springdoc.api-docs.enabled", () -> "true");
    }

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void defaultAdministratorCanLoginRefreshReadCurrentUserAndLogout() {
        ResponseEntity<Map> login = http.postForEntity("/api/auth/login",
                Map.of("username", "admin", "password", "123123aa"), Map.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getBody().get("requestId")).isEqualTo(login.getHeaders().getFirst("X-Trace-Id"));
        Map<String, Object> tokens = data(login);
        String accessToken = (String) tokens.get("accessToken");
        String refreshToken = (String) tokens.get("refreshToken");
        assertThat(accessToken).isNotBlank();
        assertThat(jdbc.queryForObject("select access_token_hash from auth_session limit 1", String.class))
                .isNotEqualTo(accessToken);

        ResponseEntity<Map> me = exchangeWithBearer("/api/auth/me", HttpMethod.GET, accessToken, null);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(me)).containsEntry("username", "admin")
                .containsEntry("mfaLevel", "NONE")
                .containsKeys("platformId", "tenantMemberId");

        ResponseEntity<Map> unknownFailure = exchangeWithBearer(
                "/api/test-support/permission/unknown-failure", HttpMethod.GET, accessToken, null);
        assertThat(unknownFailure.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(unknownFailure.getBody()).containsEntry("code", "INTERNAL_ERROR")
                .containsEntry("requestId", unknownFailure.getHeaders().getFirst("X-Trace-Id"));
        assertThat(unknownFailure.getBody().get("message").toString()).doesNotContain("diagnostic-only");

        ResponseEntity<Map> scalars = exchangeWithBearer(
                "/api/test-support/permission/contract-scalars", HttpMethod.GET, accessToken, null);
        assertThat(data(scalars)).containsEntry("amount", "1234567890.1234567890")
                .containsEntry("occurredAt", "2026-08-17T12:34:56+08:00");

        ResponseEntity<String> apiDocumentation = http.getForEntity("/v3/api-docs", String.class);
        assertThat(apiDocumentation.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(apiDocumentation.getBody()).contains("/api/auth/login", "Unexamine 产品接口");

        ResponseEntity<Map> refreshed = http.postForEntity("/api/auth/refresh", Map.of("refreshToken", refreshToken), Map.class);
        assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);
        String rotatedAccessToken = (String) data(refreshed).get("accessToken");
        assertThat(rotatedAccessToken).isNotEqualTo(accessToken);
        assertThat(exchangeWithBearer("/api/auth/me", HttpMethod.GET, accessToken, null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        assertThat(exchangeWithBearer("/api/auth/logout", HttpMethod.POST, rotatedAccessToken, null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(exchangeWithBearer("/api/auth/me", HttpMethod.GET, rotatedAccessToken, null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void registrationCreatesACompleteSingleTenantSystemAndConflictRollsBack() {
        Map<String, Object> request = Map.of(
                "username", "owner_one",
                "password", "correct-password",
                "displayName", "系统创建人",
                "email", "owner@example.com",
                "systemName", "客户管理系统",
                "systemCode", "customer_ops");

        ResponseEntity<Map> response = http.postForEntity("/api/auth/register", request, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> result = data(response);
        long systemId = ((Number) result.get("systemId")).longValue();
        long tenantId = ((Number) result.get("tenantId")).longValue();
        assertThat(result).containsEntry("systemCode", "customer_ops")
                .containsEntry("tenantMode", "SINGLE")
                .containsEntry("tenantName", "默认主租户");
        assertThat(((Map<?, ?>) result.get("tokens")).containsKey("accessToken")).isTrue();

        assertThat(count("plat_account", "username = 'owner_one'")).isOne();
        assertThat(count("sys_system", "code = 'customer_ops'")).isOne();
        assertThat(count("sys_tenant", "system_id = " + systemId + " and is_main = 1 and main_marker = 'MAIN'")).isOne();
        assertThat(count("sys_member", "system_id = " + systemId + " and status = 'ACTIVE'")).isOne();
        assertThat(count("sys_tenant_member", "tenant_id = " + tenantId + " and tenant_admin = 1")).isOne();
        assertThat(count("sys_member_role", "tenant_id = " + tenantId)).isOne();
        assertThat(jdbc.queryForObject(
                "select count(*) from sys_role_permission p join sys_role r on r.id = p.role_id where r.tenant_id = ? and p.data_scope_type = 'ALL'",
                Integer.class, tenantId)).isOne();

        ResponseEntity<Map> duplicate = http.postForEntity("/api/auth/register", Map.of(
                "username", "owner_two",
                "password", "correct-password",
                "displayName", "另一个创建人",
                "email", "other@example.com",
                "systemName", "重复编码系统",
                "systemCode", "customer_ops"), Map.class);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(count("plat_account", "username = 'owner_two'")).isZero();
        assertThat(count("audit_event", "event_code = 'ACCOUNT_REGISTER_SYSTEM_CREATED' and result_code = 'RESOURCE_CONFLICT'")).isOne();
    }

    @Test
    void wrongPasswordDoesNotRevealAccountExistenceAndDisabledAccountCannotUseExistingSession() {
        ResponseEntity<Map> known = http.postForEntity("/api/auth/login",
                Map.of("username", "admin", "password", "wrong-value"), Map.class);
        ResponseEntity<Map> unknown = http.postForEntity("/api/auth/login",
                Map.of("username", "missing-user", "password", "wrong-value"), Map.class);
        assertThat(known.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(known.getBody().get("code")).isEqualTo(unknown.getBody().get("code"));
        assertThat(known.getBody().get("message")).isEqualTo(unknown.getBody().get("message"));
        assertThat(count("audit_event", "event_code = 'AUTH_LOGIN' and result_code = 'AUTHENTICATION_FAILED'"))
                .isGreaterThanOrEqualTo(2);

        ResponseEntity<Map> login = http.postForEntity("/api/auth/login",
                Map.of("username", "admin", "password", "123123aa"), Map.class);
        String accessToken = (String) data(login).get("accessToken");
        try {
            jdbc.update("update plat_account set status = 'DISABLED' where username = 'admin'");
            ResponseEntity<Map> me = exchangeWithBearer("/api/auth/me", HttpMethod.GET, accessToken, null);
            assertThat(me.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(me.getBody().get("code")).isEqualTo("ACCOUNT_DISABLED");
            assertThat(count("auth_session", "revoked = 1")).isGreaterThanOrEqualTo(1);
            assertThat(count("audit_event", "event_code = 'AUTH_ACCESS' and result_code = 'ACCOUNT_DISABLED'")).isOne();
        } finally {
            jdbc.update("update plat_account set status = 'ACTIVE' where username = 'admin'");
        }
    }

    @Test
    void repeatedPasswordFailuresTemporarilyLockOnlyTheTargetAccount() {
        ResponseEntity<Map> registration = http.postForEntity("/api/auth/register", Map.of(
                "username", "locked_owner",
                "password", "Original123!@#",
                "displayName", "锁定测试账号",
                "email", "locked-owner@example.com",
                "systemName", "锁定测试系统",
                "systemCode", "locked_test"), Map.class);
        assertThat(registration.getStatusCode()).isEqualTo(HttpStatus.OK);

        for (int attempt = 0; attempt < 5; attempt++) {
            ResponseEntity<Map> failed = http.postForEntity("/api/auth/login",
                    Map.of("username", "locked_owner", "password", "WrongPassword123!"), Map.class);
            assertThat(failed.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(failed.getBody().get("code")).isEqualTo("AUTHENTICATION_FAILED");
        }
        ResponseEntity<Map> locked = http.postForEntity("/api/auth/login",
                Map.of("username", "locked_owner", "password", "Original123!@#"), Map.class);
        assertThat(locked.getStatusCode()).isEqualTo(HttpStatus.LOCKED);
        assertThat(locked.getBody().get("code")).isEqualTo("ACCOUNT_TEMPORARILY_LOCKED");
        assertThat(count("plat_account_credential",
                "account_id = (select id from plat_account where username = 'locked_owner') and failed_attempts = 5 and locked_until is not null"))
                .isOne();
        assertThat(count("audit_event", "event_code = 'AUTH_LOGIN' and result_code = 'ACCOUNT_TEMPORARILY_LOCKED'"))
                .isOne();
    }

    @Test
    void accountOwnerCanUpdateProfileChangePasswordAndRevokeOtherSessions() {
        ResponseEntity<Map> registration = http.postForEntity("/api/auth/register", Map.of(
                "username", "profile_owner",
                "password", "Original123!@#",
                "displayName", "原始姓名",
                "email", "profile-owner@example.com",
                "systemName", "个人资料测试系统",
                "systemCode", "profile_test"), Map.class);
        assertThat(registration.getStatusCode()).isEqualTo(HttpStatus.OK);
        String currentToken = (String) ((Map<?, ?>) data(registration).get("tokens")).get("accessToken");

        ResponseEntity<Map> secondLogin = http.postForEntity("/api/auth/login", Map.of(
                "username", "profile_owner", "password", "Original123!@#"), Map.class);
        assertThat(secondLogin.getStatusCode()).isEqualTo(HttpStatus.OK);
        String otherToken = (String) data(secondLogin).get("accessToken");

        ResponseEntity<Map> initialProfile = exchangeWithBearer(
                "/api/account/profile", HttpMethod.GET, currentToken, null);
        assertThat(initialProfile.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> initial = data(initialProfile);
        int initialVersion = ((Number) initial.get("version")).intValue();
        assertThat(initial).containsEntry("displayName", "原始姓名")
                .containsEntry("credentialVersion", 1);
        assertThat((List<?>) initial.get("sessions")).hasSizeGreaterThanOrEqualTo(2);

        Map<String, Object> update = Map.of(
                "displayName", "更新后的姓名",
                "email", "updated-profile@example.com",
                "mobile", "13800138000",
                "locale", "zh-CN",
                "timezone", "Asia/Shanghai",
                "version", initialVersion);
        ResponseEntity<Map> updatedProfile = exchangeWithBearer(
                "/api/account/profile", HttpMethod.PUT, currentToken, update);
        assertThat(updatedProfile.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(updatedProfile)).containsEntry("displayName", "更新后的姓名")
                .containsEntry("email", "updated-profile@example.com")
                .containsEntry("mobile", "13800138000")
                .containsEntry("version", initialVersion + 1);
        assertThat(count("plat_account", "username = 'profile_owner' and display_name = '更新后的姓名'"))
                .isOne();

        ResponseEntity<Map> staleUpdate = exchangeWithBearer(
                "/api/account/profile", HttpMethod.PUT, currentToken, update);
        assertThat(staleUpdate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(staleUpdate.getBody().get("code")).isEqualTo("ACCOUNT_VERSION_CONFLICT");

        ResponseEntity<Map> invalidCurrentPassword = exchangeWithBearer(
                "/api/account/password", HttpMethod.POST, currentToken,
                Map.of("currentPassword", "not-the-current-password", "newPassword", "Replacement456$%"));
        assertThat(invalidCurrentPassword.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(invalidCurrentPassword.getBody().get("code")).isEqualTo("CURRENT_PASSWORD_INVALID");

        ResponseEntity<Map> changedPassword = exchangeWithBearer(
                "/api/account/password", HttpMethod.POST, currentToken,
                Map.of("currentPassword", "Original123!@#", "newPassword", "Replacement456$%"));
        assertThat(changedPassword.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(changedPassword)).containsEntry("credentialVersion", 2);
        assertThat(count("plat_password_history", "account_id = (select id from plat_account where username = 'profile_owner')"))
                .isOne();
        assertThat(count("auth_session", "account_id = (select id from plat_account where username = 'profile_owner') and revoked_reason = 'PASSWORD_CHANGED'"))
                .isOne();
        assertThat(exchangeWithBearer("/api/auth/me", HttpMethod.GET, otherToken, null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchangeWithBearer("/api/auth/me", HttpMethod.GET, currentToken, null).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(http.postForEntity("/api/auth/login", Map.of(
                "username", "profile_owner", "password", "Original123!@#"), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(http.postForEntity("/api/auth/login", Map.of(
                "username", "profile_owner", "password", "Replacement456$%"), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void publishedOidcProviderMapsExternalIdentityAndCreatesAuditedMfaSession() throws Exception {
        HttpServer identityProvider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        identityProvider.createContext("/token", exchange -> respondJson(exchange,
                "{\"access_token\":\"provider-access-token\",\"token_type\":\"Bearer\"}"));
        identityProvider.createContext("/userinfo", exchange -> respondJson(exchange,
                "{\"sub\":\"mapped-subject\",\"email\":\"mapped@example.test\",\"name\":\"Mapped User\",\"amr\":[\"pwd\",\"mfa\"]}"));
        identityProvider.createContext("/jwks", exchange -> respondJson(exchange,
                "{\"keys\":[{\"kty\":\"RSA\",\"kid\":\"cycle-key\",\"n\":\"AQAB\",\"e\":\"AQAB\"}]}"));
        identityProvider.createContext("/invalid-jwks", exchange -> respondJson(exchange, "{\"keys\":[]}"));
        identityProvider.createContext("/health", exchange -> respondJson(exchange, "{\"status\":\"UP\"}"));
        identityProvider.start();
        try {
            int port = identityProvider.getAddress().getPort();
            String baseUrl = "http://127.0.0.1:" + port;
            String callback = "http://127.0.0.1:15173/sso/callback";
            String adminToken = (String) data(http.postForEntity("/api/auth/login",
                    Map.of("username", "admin", "password", "123123aa"), Map.class)).get("accessToken");

            ResponseEntity<Map> unsupported = exchangeWithBearer(
                    "/api/admin/platform/sso-providers", HttpMethod.POST, adminToken,
                    oidcDraft("unsupported-cycle", "不支持协议", "CAS", baseUrl, callback,
                            "property:test.sso.secret", baseUrl + "/jwks"));
            assertThat(unsupported.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(unsupported.getBody()).containsEntry("code", "SSO_PROTOCOL_UNSUPPORTED");

            ResponseEntity<Map> created = exchangeWithBearer(
                    "/api/admin/platform/sso-providers", HttpMethod.POST, adminToken,
                    oidcDraft("cycle-oidc", "周期 OIDC", "OIDC", baseUrl, callback,
                            "property:test.sso.secret", baseUrl + "/jwks"));
            assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
            Map<String, Object> providerDraft = data(created);
            long providerId = ((Number) providerDraft.get("id")).longValue();
            Map<?, ?> versionDraft = (Map<?, ?>) ((List<?>) providerDraft.get("versions")).getFirst();
            long versionId = ((Number) versionDraft.get("id")).longValue();
            assertThat(providerDraft).containsEntry("status", "DRAFT");
            assertThat(created.getBody().toString()).contains("property:test.sso.secret")
                    .doesNotContain("local-oidc-secret");

            ResponseEntity<Map> hiddenDrafts = http.getForEntity("/api/auth/sso/providers", Map.class);
            assertThat((List<?>) hiddenDrafts.getBody().get("data")).noneMatch(value ->
                    "cycle-oidc".equals(((Map<?, ?>) value).get("code")));
            ResponseEntity<Map> publishBeforeTest = exchangeWithBearer(
                    "/api/admin/platform/sso-providers/" + providerId + "/versions/" + versionId + "/publish",
                    HttpMethod.POST, adminToken, null);
            assertThat(publishBeforeTest.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(publishBeforeTest.getBody()).containsEntry("code", "SSO_PROVIDER_TEST_REQUIRED");

            ResponseEntity<Map> tested = exchangeWithBearer(
                    "/api/admin/platform/sso-providers/" + providerId + "/versions/" + versionId + "/test",
                    HttpMethod.POST, adminToken, null);
            assertThat(tested.getStatusCode()).withFailMessage("身份源检测失败：%s", tested.getBody()).isEqualTo(HttpStatus.OK);
            assertThat(data(tested)).containsEntry("status", "PASSED").containsEntry("failureCode", null);
            assertThat((List<?>) data(tested).get("checks")).allMatch(check ->
                    "PASSED".equals(((Map<?, ?>) check).get("status")));
            assertThat(tested.getBody().toString()).doesNotContain("local-oidc-secret");

            ResponseEntity<Map> published = exchangeWithBearer(
                    "/api/admin/platform/sso-providers/" + providerId + "/versions/" + versionId + "/publish",
                    HttpMethod.POST, adminToken, null);
            assertThat(published.getStatusCode()).withFailMessage("身份源发布失败：%s", published.getBody()).isEqualTo(HttpStatus.OK);
            assertThat(data(published)).containsEntry("status", "PUBLISHED")
                    .containsEntry("publishedVersionNumber", 1);
            assertThat(published.getBody().toString()).doesNotContain("local-oidc-secret");
            assertThat(count("plat_sso_provider_version", "provider_id = " + providerId
                    + " and version_number = 1 and status = 'PUBLISHED' and test_status = 'PASSED'")).isOne();
            assertThat(count("audit_event", "event_code = 'SSO_PROVIDER_PUBLISHED' and object_id = '" + versionId + "'")).isOne();

            ResponseEntity<Map> callbackMismatch = exchangeWithBearer(
                    "/api/admin/platform/sso-providers", HttpMethod.POST, adminToken,
                    oidcDraft("cycle-callback-bad", "错误回调", "OIDC", baseUrl,
                            "http://127.0.0.1:15173/another-callback", "property:test.sso.secret", baseUrl + "/jwks"));
            Map<String, Object> callbackProvider = data(callbackMismatch);
            long callbackProviderId = ((Number) callbackProvider.get("id")).longValue();
            long callbackVersionId = ((Number) ((Map<?, ?>) ((List<?>) callbackProvider.get("versions")).getFirst()).get("id")).longValue();
            ResponseEntity<Map> failedCallbackTest = exchangeWithBearer(
                    "/api/admin/platform/sso-providers/" + callbackProviderId + "/versions/" + callbackVersionId + "/test",
                    HttpMethod.POST, adminToken, null);
            assertThat(data(failedCallbackTest)).containsEntry("status", "FAILED")
                    .containsEntry("failureCode", "CALLBACK_MISMATCH");
            assertThat(exchangeWithBearer(
                    "/api/admin/platform/sso-providers/" + callbackProviderId + "/versions/" + callbackVersionId + "/publish",
                    HttpMethod.POST, adminToken, null).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

            ResponseEntity<Map> badJwks = exchangeWithBearer(
                    "/api/admin/platform/sso-providers", HttpMethod.POST, adminToken,
                    oidcDraft("cycle-jwks-bad", "错误 JWKS", "OIDC", baseUrl, callback,
                            "property:test.sso.secret", baseUrl + "/invalid-jwks"));
            Map<String, Object> badJwksProvider = data(badJwks);
            long badJwksProviderId = ((Number) badJwksProvider.get("id")).longValue();
            long badJwksVersionId = ((Number) ((Map<?, ?>) ((List<?>) badJwksProvider.get("versions")).getFirst()).get("id")).longValue();
            assertThat(data(exchangeWithBearer(
                    "/api/admin/platform/sso-providers/" + badJwksProviderId + "/versions/" + badJwksVersionId + "/test",
                    HttpMethod.POST, adminToken, null))).containsEntry("status", "FAILED")
                    .containsEntry("failureCode", "JWKS_INVALID");

            ResponseEntity<Map> missingSecret = exchangeWithBearer(
                    "/api/admin/platform/sso-providers", HttpMethod.POST, adminToken,
                    oidcDraft("cycle-secret-bad", "不可用 SecretRef", "OIDC", baseUrl, callback,
                            "property:test.sso.missing", baseUrl + "/jwks"));
            Map<String, Object> missingSecretProvider = data(missingSecret);
            long missingSecretProviderId = ((Number) missingSecretProvider.get("id")).longValue();
            long missingSecretVersionId = ((Number) ((Map<?, ?>) ((List<?>) missingSecretProvider.get("versions")).getFirst()).get("id")).longValue();
            assertThat(data(exchangeWithBearer(
                    "/api/admin/platform/sso-providers/" + missingSecretProviderId + "/versions/" + missingSecretVersionId + "/test",
                    HttpMethod.POST, adminToken, null))).containsEntry("status", "FAILED")
                    .containsEntry("failureCode", "SECRET_REFERENCE_UNAVAILABLE");

            ResponseEntity<Map> registration = http.postForEntity("/api/auth/register", Map.of(
                    "username", "identity_non_admin", "password", "correct-password", "displayName", "普通身份用户",
                    "email", "identity-non-admin@example.com", "systemName", "普通用户系统", "systemCode", "identity_non_admin"), Map.class);
            String nonAdminToken = (String) ((Map<?, ?>) data(registration).get("tokens")).get("accessToken");
            assertThat(exchangeWithBearer("/api/admin/platform/sso-providers", HttpMethod.GET, nonAdminToken, null)
                    .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

            long adminId = jdbc.queryForObject("select id from plat_account where username = 'admin'", Long.class);
            jdbc.update("insert into plat_sso_identity(provider_id, external_subject, account_id) values (?, 'mapped-subject', ?)", providerId, adminId);

            ResponseEntity<Map> providerList = http.getForEntity("/api/auth/sso/providers", Map.class);
            assertThat(providerList.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat((List<?>) providerList.getBody().get("data")).anyMatch(value ->
                    "cycle-oidc".equals(((Map<?, ?>) value).get("code")));

            ResponseEntity<Map> started = http.postForEntity("/api/auth/sso/cycle-oidc/start", null, Map.class);
            assertThat(started.getStatusCode()).isEqualTo(HttpStatus.OK);
            String authorizationUrl = (String) data(started).get("authorizationUrl");
            Map<String, List<String>> query = UriComponentsBuilder.fromUriString(authorizationUrl).build().getQueryParams();
            assertThat(query).containsKeys("state", "code_challenge", "redirect_uri");
            String state = query.get("state").getFirst();

            ResponseEntity<Map> completed = http.postForEntity("/api/auth/sso/cycle-oidc/complete",
                    Map.of("code", "authorization-code", "state", state), Map.class);
            assertThat(completed.getStatusCode()).isEqualTo(HttpStatus.OK);
            String accessToken = (String) data(completed).get("accessToken");
            ResponseEntity<Map> me = exchangeWithBearer("/api/auth/me", HttpMethod.GET, accessToken, null);
            assertThat(data(me)).containsEntry("username", "admin").containsEntry("mfaLevel", "MFA");
            assertThat(count("plat_sso_identity", "provider_id = " + providerId + " and external_subject = 'mapped-subject' and last_login_at is not null")).isOne();
            assertThat(count("audit_event", "event_code = 'SSO_LOGIN' and result_code = 'SUCCESS' and detail_json like '%mapped-subject%' and detail_json like '%cycle-oidc%'" )).isOne();

            jdbc.update("delete from plat_sso_identity where provider_id = ?", providerId);
            ResponseEntity<Map> secondStart = http.postForEntity("/api/auth/sso/cycle-oidc/start", null, Map.class);
            String secondUrl = (String) data(secondStart).get("authorizationUrl");
            String secondState = UriComponentsBuilder.fromUriString(secondUrl).build().getQueryParams().getFirst("state");
            ResponseEntity<Map> missingMapping = http.postForEntity("/api/auth/sso/cycle-oidc/complete",
                    Map.of("code", "authorization-code", "state", secondState), Map.class);
            assertThat(missingMapping.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(missingMapping.getBody().get("code")).isEqualTo("SSO_MAPPING_MISSING");
            assertThat(count("audit_event", "event_code = 'SSO_LOGIN' and result_code = 'SSO_MAPPING_MISSING'" )).isOne();
        } finally {
            identityProvider.stop(0);
        }
    }

    private Map<String, Object> oidcDraft(
            String code,
            String name,
            String protocol,
            String baseUrl,
            String callback,
            String secretRef,
            String jwksUri) {
        Map<String, Object> protocolConfig = new LinkedHashMap<>();
        protocolConfig.put("authorizationEndpoint", baseUrl + "/authorize");
        protocolConfig.put("tokenEndpoint", baseUrl + "/token");
        protocolConfig.put("userinfoEndpoint", baseUrl + "/userinfo");
        protocolConfig.put("jwksUri", jwksUri);
        protocolConfig.put("redirectUri", "http://127.0.0.1:15173/sso/callback");
        protocolConfig.put("healthEndpoint", baseUrl + "/health");
        protocolConfig.put("scopes", List.of("openid", "profile", "email"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", code);
        result.put("name", name);
        result.put("protocol", protocol);
        result.put("issuer", baseUrl);
        result.put("clientId", "cycle-client");
        result.put("clientSecretRef", secretRef);
        result.put("protocolConfig", protocolConfig);
        result.put("allowedDomains", List.of("example.test"));
        result.put("attributeMapping", Map.of("externalUserId", "sub", "email", "email"));
        result.put("jitPolicy", Map.of("enabled", false));
        result.put("mfaPolicy", Map.of("required", true, "claim", "amr"));
        result.put("callbackUris", List.of(callback));
        return result;
    }

    @Test
    void enteringSystemMergesRoleActionsAndEachActionDataScopeWithoutRoleSwitching() {
        ResponseEntity<Map> registration = http.postForEntity("/api/auth/register", Map.of(
                "username", "permission_owner",
                "password", "correct-password",
                "displayName", "权限测试人",
                "email", "permission@example.com",
                "systemName", "权限测试系统",
                "systemCode", "permission_test"), Map.class);
        assertThat(registration.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> registrationData = data(registration);
        long systemId = ((Number) registrationData.get("systemId")).longValue();
        long tenantId = ((Number) registrationData.get("tenantId")).longValue();
        String registrationToken = (String) ((Map<?, ?>) registrationData.get("tokens")).get("accessToken");
        Long tenantMemberId = jdbc.queryForObject(
                "select tm.id from sys_tenant_member tm join sys_member m on m.id = tm.system_member_id where m.account_id = (select id from plat_account where username = 'permission_owner')",
                Long.class);

        jdbc.update("delete from sys_member_role where tenant_member_id = ?", tenantMemberId);
        jdbc.update("delete from sys_role_permission where role_id in (select id from sys_role where tenant_id = ?)", tenantId);
        jdbc.update("delete from sys_role where tenant_id = ?", tenantId);
        jdbc.update("insert into sys_role(system_id, tenant_id, code, name, status) values (?, ?, 'OWNER_ROLE', '本人数据角色', 'ACTIVE')", systemId, tenantId);
        long ownerRoleId = jdbc.queryForObject("select id from sys_role where tenant_id = ? and code = 'OWNER_ROLE'", Long.class, tenantId);
        jdbc.update("insert into sys_role(system_id, tenant_id, code, name, status) values (?, ?, 'DEPARTMENT_ROLE', '部门数据角色', 'ACTIVE')", systemId, tenantId);
        long departmentRoleId = jdbc.queryForObject("select id from sys_role where tenant_id = ? and code = 'DEPARTMENT_ROLE'", Long.class, tenantId);
        jdbc.update("insert into sys_member_role(tenant_id, tenant_member_id, role_id) values (?, ?, ?), (?, ?, ?)",
                tenantId, tenantMemberId, ownerRoleId, tenantId, tenantMemberId, departmentRoleId);
        jdbc.update("insert into sys_role_permission(system_id, tenant_id, role_id, resource_type, resource_code, action_code, data_scope_type) values (?, ?, ?, 'MODULE', 'customer', 'LIST', 'SELF')", systemId, tenantId, ownerRoleId);
        jdbc.update("insert into sys_role_permission(system_id, tenant_id, role_id, resource_type, resource_code, action_code, data_scope_type) values (?, ?, ?, 'MODULE', 'customer', 'UPDATE', 'SELF')", systemId, tenantId, ownerRoleId);
        jdbc.update("insert into sys_role_permission(system_id, tenant_id, role_id, resource_type, resource_code, action_code, data_scope_type) values (?, ?, ?, 'MODULE', 'customer', 'LIST', 'DEPARTMENT')", systemId, tenantId, departmentRoleId);
        jdbc.update("insert into sys_role_permission(system_id, tenant_id, role_id, resource_type, resource_code, action_code, data_scope_type, data_scope_json) values (?, ?, ?, 'MODULE', 'customer', 'EXPORT', 'FIELD_RULE', '{\"field\":\"creator\"}')", systemId, tenantId, ownerRoleId);
        jdbc.update("insert into sys_role_permission(system_id, tenant_id, role_id, resource_type, resource_code, action_code, data_scope_type, data_scope_json) values (?, ?, ?, 'MODULE', 'customer', 'EXPORT', 'FIELD_RULE', '{\"field\":\"participant\"}')", systemId, tenantId, departmentRoleId);

        ResponseEntity<Map> entered = exchangeWithBearer(
                "/api/systems/" + systemId + "/enter", HttpMethod.POST, registrationToken, null);
        assertThat(entered.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> context = data(entered);
        assertThat(context.get("tenantMemberId")).isEqualTo(tenantMemberId.intValue());
        assertThat((List<?>) context.get("roleIds")).hasSize(2);
        assertThat((List<?>) context.get("permissions")).hasSize(3);
        Map<?, ?> scopes = (Map<?, ?>) context.get("dataScopes");
        Map<?, ?> listScope = (Map<?, ?>) scopes.get("MODULE:customer:LIST");
        assertThat(listScope.get("mode")).isEqualTo("SINGLE");
        assertThat(((Map<?, ?>) ((List<?>) listScope.get("terms")).getFirst()).get("type")).isEqualTo("DEPARTMENT");
        Map<?, ?> updateScope = (Map<?, ?>) scopes.get("MODULE:customer:UPDATE");
        assertThat(((Map<?, ?>) ((List<?>) updateScope.get("terms")).getFirst()).get("type")).isEqualTo("SELF");
        Map<?, ?> exportScope = (Map<?, ?>) scopes.get("MODULE:customer:EXPORT");
        assertThat(exportScope.get("mode")).isEqualTo("UNION");
        assertThat((List<?>) exportScope.get("terms")).hasSize(2);

        String sessionRoles = jdbc.queryForObject(
                "select role_ids_json from auth_session where system_id = ? order by id desc limit 1", String.class, systemId);
        assertThat(sessionRoles).contains(Long.toString(ownerRoleId), Long.toString(departmentRoleId));
        String systemToken = (String) ((Map<?, ?>) context.get("tokens")).get("accessToken");
        ResponseEntity<Map> me = exchangeWithBearer("/api/auth/me", HttpMethod.GET, systemToken, null);
        Map<String, Object> meData = data(me);
        assertThat((List<?>) meData.get("roleIds")).hasSize(2);
        assertThat(((Number) meData.get("tenantMemberId")).longValue()).isEqualTo(tenantMemberId);
        assertThat(exchangeWithBearer("/api/test-support/permission/customer-list", HttpMethod.GET, systemToken, null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        ResponseEntity<Map> permissionDenied = exchangeWithBearer(
                "/api/test-support/permission/customer-delete", HttpMethod.GET, systemToken, null);
        assertThat(permissionDenied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(permissionDenied.getBody().get("code")).isEqualTo("PERMISSION_DENIED");
        assertThat(count("audit_event", "event_code = 'PERMISSION_CHECK' and result_code = 'PERMISSION_DENIED'"
                + " and object_id = 'MODULE:customer:DELETE'")).isOne();

        ResponseEntity<Map> systems = exchangeWithBearer("/api/systems", HttpMethod.GET, registrationToken, null);
        assertThat(systems.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) systems.getBody().get("data")).hasSize(1);

        ResponseEntity<Map> adminLogin = http.postForEntity("/api/auth/login",
                Map.of("username", "admin", "password", "123123aa"), Map.class);
        String adminToken = (String) data(adminLogin).get("accessToken");
        ResponseEntity<Map> denied = exchangeWithBearer(
                "/api/systems/" + systemId + "/enter", HttpMethod.POST, adminToken, null);
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(denied.getBody().get("code")).isEqualTo("SYSTEM_ACCESS_DENIED");
        assertThat(count("audit_event", "event_code = 'SYSTEM_ENTER' and result_code = 'SYSTEM_ACCESS_DENIED'")).isOne();
    }

    @Test
    @SuppressWarnings("unchecked")
    void platformAdministratorCreatesSystemAndDecidesCompleteNoMemberAccessLifecycle() {
        String adminToken = (String) data(http.postForEntity("/api/auth/login",
                Map.of("username", "admin", "password", "123123aa"), Map.class)).get("accessToken");
        jdbc.execute("create trigger test_fail_system_role before insert on sys_role for each row "
                + "signal sqlstate '45000' set message_text = 'forced system role failure'");
        try {
            ResponseEntity<Map> rolledBack = exchangeWithBearer("/api/systems", HttpMethod.POST, adminToken, Map.of(
                    "code", "rollback_target",
                    "name", "事务回滚目标系统",
                    "tenantMode", "SINGLE"));
            assertThat(rolledBack.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        } finally {
            jdbc.execute("drop trigger test_fail_system_role");
        }
        assertThat(count("sys_system", "code = 'rollback_target'")).isZero();
        assertThat(count("sys_tenant", "system_id in (select id from sys_system where code = 'rollback_target')")).isZero();

        ResponseEntity<Map> createdSystem = exchangeWithBearer("/api/systems", HttpMethod.POST, adminToken, Map.of(
                "code", "access_target",
                "name", "访问审批目标系统",
                "tenantMode", "SINGLE"));
        assertThat(createdSystem.getStatusCode())
                .withFailMessage("创建系统失败：%s", createdSystem.getBody())
                .isEqualTo(HttpStatus.OK);
        Map<String, Object> target = data(createdSystem);
        long systemId = ((Number) target.get("systemId")).longValue();
        long tenantId = ((Number) target.get("defaultTenantId")).longValue();
        assertThat(count("sys_system", "code = 'access_target'")).isOne();
        assertThat(count("sys_tenant", "system_id = " + systemId + " and is_main = 1")).isOne();
        assertThat(count("sys_member", "system_id = " + systemId + " and account_id = (select id from plat_account where username = 'admin')")).isOne();
        ResponseEntity<Map> latestSystems = exchangeWithBearer("/api/systems", HttpMethod.GET, adminToken, null);
        Map<?, ?> firstSystem = (Map<?, ?>) ((List<?>) latestSystems.getBody().get("data")).getFirst();
        assertThat(((Number) firstSystem.get("systemId")).longValue()).isEqualTo(systemId);
        assertThat(firstSystem.get("defaultTenantName")).isEqualTo("默认主租户");

        registerAccessApplicant("access_applicant", "applicant_home", "访问申请人");
        String applicantToken = (String) data(http.postForEntity("/api/auth/login",
                Map.of("username", "access_applicant", "password", "correct-password"), Map.class)).get("accessToken");
        ResponseEntity<Map> directoryResponse = exchangeWithBearer(
                "/api/systems/directory", HttpMethod.GET, applicantToken, null);
        List<Map<String, Object>> directory = (List<Map<String, Object>>) (List<?>) directoryResponse.getBody().get("data");
        Map<String, Object> directoryTarget = directory.stream()
                .filter(item -> ((Number) item.get("systemId")).longValue() == systemId).findFirst().orElseThrow();
        assertThat(directoryTarget).containsEntry("accessible", false);

        ResponseEntity<Map> submitted = exchangeWithBearer(
                "/api/systems/" + systemId + "/access-requests", HttpMethod.POST, applicantToken, Map.of(
                        "tenantId", tenantId,
                        "reason", "需要参与目标系统业务",
                        "requestedRole", "普通成员"));
        assertThat(submitted.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> request = data(submitted);
        long requestId = ((Number) request.get("id")).longValue();
        assertThat(request).containsEntry("status", "PENDING").containsEntry("identityProvider", "LOCAL");
        assertThat(request.get("requestTraceId")).isNotNull();

        ResponseEntity<Map> merged = exchangeWithBearer(
                "/api/systems/" + systemId + "/access-requests", HttpMethod.POST, applicantToken, Map.of(
                        "tenantId", tenantId,
                        "reason", "补充：负责日常业务",
                        "requestedRole", "业务成员"));
        assertThat(((Number) data(merged).get("id")).longValue()).isEqualTo(requestId);
        assertThat(count("sys_access_request", "system_id = " + systemId + " and account_id = (select id from plat_account where username = 'access_applicant')")).isOne();
        assertThat(exchangeWithBearer("/api/systems/" + systemId + "/enter", HttpMethod.POST, applicantToken, null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        Map<String, Object> adminEntry = data(exchangeWithBearer(
                "/api/systems/" + systemId + "/enter", HttpMethod.POST, adminToken, null));
        String adminSystemToken = (String) ((Map<?, ?>) adminEntry.get("tokens")).get("accessToken");
        ResponseEntity<Map> rolesResponse = exchangeWithBearer(
                "/api/admin/system/access-request-roles", HttpMethod.GET, adminSystemToken, null);
        List<Map<String, Object>> roles = (List<Map<String, Object>>) (List<?>) rolesResponse.getBody().get("data");
        long roleId = ((Number) roles.getFirst().get("id")).longValue();
        ResponseEntity<Map> queueResponse = exchangeWithBearer(
                "/api/admin/system/access-requests?status=PENDING", HttpMethod.GET, adminSystemToken, null);
        List<Map<String, Object>> queue = (List<Map<String, Object>>) (List<?>) queueResponse.getBody().get("data");
        Map<String, Object> queued = queue.stream()
                .filter(item -> ((Number) item.get("id")).longValue() == requestId).findFirst().orElseThrow();

        ResponseEntity<Map> approved = exchangeWithBearer(
                "/api/admin/system/access-requests/" + requestId + "/decision", HttpMethod.POST, adminSystemToken, Map.of(
                        "decision", "APPROVE",
                        "roleIds", List.of(roleId),
                        "comment", "批准加入",
                        "version", queued.get("version")));
        assertThat(approved.getStatusCode())
                .withFailMessage("批准访问申请失败：%s", approved.getBody())
                .isEqualTo(HttpStatus.OK);
        assertThat(data(approved)).containsEntry("status", "APPROVED").containsEntry("decisionComment", "批准加入");
        assertThat(count("sys_member", "system_id = " + systemId + " and account_id = (select id from plat_account where username = 'access_applicant')")).isOne();
        assertThat(count("sys_member_role", "role_id = " + roleId + " and tenant_id = " + tenantId)).isEqualTo(2);
        assertThat(exchangeWithBearer("/api/systems/" + systemId + "/enter", HttpMethod.POST, applicantToken, null).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> rejectedSystem = exchangeWithBearer("/api/systems", HttpMethod.POST, adminToken, Map.of(
                "code", "reject_target",
                "name", "拒绝分支目标系统",
                "tenantMode", "SINGLE"));
        long rejectedSystemId = ((Number) data(rejectedSystem).get("systemId")).longValue();
        long rejectedTenantId = ((Number) data(rejectedSystem).get("defaultTenantId")).longValue();
        registerAccessApplicant("rejected_applicant", "rejected_home", "被拒申请人");
        String rejectedApplicantToken = (String) data(http.postForEntity("/api/auth/login",
                Map.of("username", "rejected_applicant", "password", "correct-password"), Map.class)).get("accessToken");
        Map<String, Object> rejectedRequest = data(exchangeWithBearer(
                "/api/systems/" + rejectedSystemId + "/access-requests", HttpMethod.POST, rejectedApplicantToken,
                Map.of("tenantId", rejectedTenantId, "reason", "申请访问", "requestedRole", "成员")));
        Map<String, Object> rejectedAdminEntry = data(exchangeWithBearer(
                "/api/systems/" + rejectedSystemId + "/enter", HttpMethod.POST, adminToken, null));
        String rejectedAdminToken = (String) ((Map<?, ?>) rejectedAdminEntry.get("tokens")).get("accessToken");
        ResponseEntity<Map> missingReason = exchangeWithBearer(
                "/api/admin/system/access-requests/" + rejectedRequest.get("id") + "/decision", HttpMethod.POST,
                rejectedAdminToken, Map.of("decision", "REJECT", "roleIds", List.of(),
                        "comment", "", "version", rejectedRequest.get("version")));
        assertThat(missingReason.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(missingReason.getBody()).containsEntry("code", "ACCESS_REQUEST_REJECTION_REASON_REQUIRED");
        ResponseEntity<Map> rejected = exchangeWithBearer(
                "/api/admin/system/access-requests/" + rejectedRequest.get("id") + "/decision", HttpMethod.POST,
                rejectedAdminToken, Map.of("decision", "REJECT", "roleIds", List.of(),
                        "comment", "当前不符合准入条件", "version", rejectedRequest.get("version")));
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(rejected)).containsEntry("status", "REJECTED").containsEntry("decisionComment", "当前不符合准入条件");
        assertThat(exchangeWithBearer("/api/systems/" + rejectedSystemId + "/enter", HttpMethod.POST,
                rejectedApplicantToken, null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(count("audit_event", "object_type = 'SYSTEM_ACCESS_REQUEST' and event_code = 'SYSTEM_ACCESS_REQUEST_APPROVED'")).isOne();
        assertThat(count("audit_event", "object_type = 'SYSTEM_ACCESS_REQUEST' and event_code = 'SYSTEM_ACCESS_REQUEST_REJECTED'")).isOne();
    }

    private void registerAccessApplicant(String username, String systemCode, String displayName) {
        ResponseEntity<Map> registration = http.postForEntity("/api/auth/register", Map.of(
                "username", username,
                "password", "correct-password",
                "displayName", displayName,
                "email", username + "@example.com",
                "systemName", displayName + "的系统",
                "systemCode", systemCode), Map.class);
        assertThat(registration.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private int count(String table, String condition) {
        return jdbc.queryForObject("select count(*) from " + table + " where " + condition, Integer.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(ResponseEntity<Map> response) {
        return (Map<String, Object>) response.getBody().get("data");
    }

    private ResponseEntity<Map> exchangeWithBearer(String path, HttpMethod method, String accessToken, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return http.exchange(path, method, new HttpEntity<>(body, headers), Map.class);
    }

    private static void respondJson(com.sun.net.httpserver.HttpExchange exchange, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @TestConfiguration
    static class PermissionTestConfiguration {
    }

    @RestController
    @RequestMapping("/api/test-support/permission")
    static class TestPermissionController {
        @GetMapping("/customer-list")
        @RequirePermission(resourceType = "MODULE", resourceCode = "customer", actionCode = "LIST")
        ApiResult<String> customerList() {
            return ApiResult.ok("allowed");
        }

        @GetMapping("/customer-delete")
        @RequirePermission(resourceType = "MODULE", resourceCode = "customer", actionCode = "DELETE")
        ApiResult<String> customerDelete() {
            return ApiResult.ok("allowed");
        }

        @GetMapping("/unknown-failure")
        ApiResult<String> unknownFailure() {
            throw new IllegalStateException("diagnostic-only exception detail");
        }

        @GetMapping("/contract-scalars")
        ApiResult<Map<String, Object>> contractScalars() {
            return ApiResult.ok(Map.of(
                    "amount", new BigDecimal("1234567890.1234567890"),
                    "occurredAt", LocalDateTime.of(2026, 8, 17, 12, 34, 56)));
        }
    }
}
