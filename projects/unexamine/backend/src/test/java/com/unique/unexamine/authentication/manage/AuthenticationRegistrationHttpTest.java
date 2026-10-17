package com.unique.unexamine.authentication.manage;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import com.fasterxml.jackson.databind.ObjectMapper;

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
        registry.add("spring.http.client.factory", () -> "simple");
        registry.add("test.sso.secret", () -> "local-oidc-secret");
        registry.add("springdoc.api-docs.enabled", () -> "true");
    }

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EnterpriseSsoService enterpriseSsoService;

    @TempDir
    Path samlFixtureDirectory;

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
        assertThat(jdbc.queryForObject("select access_token_hash from un_auth_session limit 1", String.class))
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
        Map<?, ?> systemTokens = (Map<?, ?>) result.get("tokens");
        Map<?, ?> platformTokens = (Map<?, ?>) result.get("platformTokens");
        assertThat(platformTokens.containsKey("accessToken")).isTrue();
        assertThat(systemTokens.containsKey("accessToken")).isTrue();
        assertThat(systemTokens.get("accessToken")).isNotEqualTo(platformTokens.get("accessToken"));
        assertThat(platformTokens.get("accessExpiresAt").toString()).matches(".*(?:Z|[+-]\\d{2}:\\d{2})$");
        assertThat(systemTokens.get("accessExpiresAt").toString()).matches(".*(?:Z|[+-]\\d{2}:\\d{2})$");

        Map<String, Object> platformContext = data(exchangeWithBearer(
                "/api/auth/me", HttpMethod.GET, (String) platformTokens.get("accessToken"), null));
        Map<String, Object> systemContext = data(exchangeWithBearer(
                "/api/auth/me", HttpMethod.GET, (String) systemTokens.get("accessToken"), null));
        assertThat(platformContext.get("systemId")).isNull();
        assertThat(platformContext.get("tenantId")).isNull();
        assertThat(((Number) systemContext.get("systemId")).longValue()).isEqualTo(systemId);
        assertThat(((Number) systemContext.get("tenantId")).longValue()).isEqualTo(tenantId);

        assertThat(count("un_plat_account", "username = 'owner_one'")).isOne();
        assertThat(count("un_sys_system", "code = 'customer_ops'")).isOne();
        assertThat(count("un_sys_tenant", "system_id = " + systemId + " and is_main = 1 and main_marker = 'MAIN'")).isOne();
        assertThat(count("un_sys_department", "tenant_id = " + tenantId + " and parent_id is null and code = 'root' and name = '全公司'")).isOne();
        assertThat(count("un_sys_member", "system_id = " + systemId + " and status = 'ACTIVE'")).isOne();
        assertThat(count("un_sys_tenant_member", "tenant_id = " + tenantId + " and tenant_admin = 1 and department_id in "
                + "(select id from un_sys_department where tenant_id = " + tenantId + " and code = 'root')")).isOne();
        assertThat(count("un_sys_member_role", "tenant_id = " + tenantId)).isOne();
        assertThat(count("un_auth_session", "account_id = (select id from un_plat_account where username = 'owner_one') and system_id is null")).isOne();
        assertThat(count("un_auth_session", "account_id = (select id from un_plat_account where username = 'owner_one') and system_id = " + systemId)).isOne();
        assertThat(jdbc.queryForObject(
                "select count(*) from un_sys_role_permission p join un_sys_role r on r.id = p.role_id where r.tenant_id = ? and p.data_scope_type = 'ALL'",
                Integer.class, tenantId)).isOne();

        ResponseEntity<Map> duplicate = http.postForEntity("/api/auth/register", Map.of(
                "username", "owner_two",
                "password", "correct-password",
                "displayName", "另一个创建人",
                "email", "other@example.com",
                "systemName", "重复编码系统",
                "systemCode", "customer_ops"), Map.class);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(count("un_plat_account", "username = 'owner_two'")).isZero();
        assertThat(count("un_audit_event", "event_code = 'ACCOUNT_REGISTER_SYSTEM_CREATED' and result_code = 'RESOURCE_CONFLICT'")).isOne();
    }

    @Test
    void standalonePlatformAccountCanJoinAnExistingSystemWithoutCreatingAnotherSystem() {
        Map<String, Object> owner = data(http.postForEntity("/api/auth/register", Map.of(
                "username", "member_join_owner", "password", "correct-password",
                "displayName", "成员开通管理员", "email", "member-join-owner@example.com",
                "systemName", "成员开通系统", "systemCode", "member_join_sys"), Map.class));
        long systemId = ((Number) owner.get("systemId")).longValue();
        String ownerSystemToken = (String) ((Map<?, ?>) owner.get("tokens")).get("accessToken");

        registerAccessApplicant("member_join_user", "待开通成员");
        String memberPlatformToken = (String) ((Map<?, ?>) data(http.postForEntity(
                "/api/auth/login", Map.of("username", "member_join_user", "password", "correct-password"),
                Map.class))).get("accessToken");
        assertThat(exchangeWithBearer("/api/systems/" + systemId + "/enter", HttpMethod.POST,
                memberPlatformToken, null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<Map> added = exchangeWithBearer("/api/admin/system/authorization/members",
                HttpMethod.POST, ownerSystemToken, Map.of("account", "member_join_user", "roleIds", List.of()));
        assertThat(added.getStatusCode()).withFailMessage("开通成员失败：%s", added.getBody())
                .isEqualTo(HttpStatus.OK);
        assertThat(data(added)).containsEntry("displayName", "待开通成员");
        assertThat(data(added).get("departmentId")).isNotNull();
        assertThat(count("un_sys_system", "code = 'member_join_sys'")).isOne();
        assertThat(count("un_sys_member", "account_id = (select id from un_plat_account where username = 'member_join_user')"))
                .isOne();

        ResponseEntity<Map> entered = exchangeWithBearer("/api/systems/" + systemId + "/enter",
                HttpMethod.POST, memberPlatformToken, null);
        assertThat(entered.getStatusCode()).isEqualTo(HttpStatus.OK);
        String memberSystemToken = (String) ((Map<?, ?>) data(entered).get("tokens")).get("accessToken");
        assertThat(exchangeWithBearer("/api/admin/system/authorization", HttpMethod.GET,
                memberSystemToken, null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void standalonePlatformAccountCanCreateTwoSystemsButDisabledMemberCannotCreateMore() {
        registerAccessApplicant("multi_system_owner", "多个系统创建人");
        String platformToken = (String) data(http.postForEntity("/api/auth/login", Map.of(
                "username", "multi_system_owner", "password", "correct-password"), Map.class)).get("accessToken");
        Map<String, Object> platformContext = data(exchangeWithBearer(
                "/api/auth/me", HttpMethod.GET, platformToken, null));
        assertThat((List<Map<String, Object>>) platformContext.get("permissions"))
                .anySatisfy(permission -> assertThat(permission)
                        .containsEntry("resourceType", "PLATFORM")
                        .containsEntry("resourceCode", "SYSTEM")
                        .containsEntry("actionCode", "CREATE"));

        ResponseEntity<Map> first = exchangeWithBearer("/api/systems", HttpMethod.POST, platformToken,
                Map.of("name", "多系统一", "code", "multi_owner_one"));
        ResponseEntity<Map> second = exchangeWithBearer("/api/systems", HttpMethod.POST, platformToken,
                Map.of("name", "多系统二", "code", "multi_owner_two"));
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        long firstId = ((Number) data(first).get("systemId")).longValue();
        long secondId = ((Number) data(second).get("systemId")).longValue();
        assertThat(firstId).isNotEqualTo(secondId);
        assertThat(exchangeWithBearer("/api/systems/" + firstId + "/enter", HttpMethod.POST,
                platformToken, null).getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> secondEntry = data(exchangeWithBearer("/api/systems/" + secondId + "/enter",
                HttpMethod.POST, platformToken, null));
        String systemToken = (String) ((Map<?, ?>) secondEntry.get("tokens")).get("accessToken");
        assertThat(exchangeWithBearer("/api/systems", HttpMethod.POST, systemToken,
                Map.of("name", "错误上下文", "code", "multi_owner_wrong_context")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(count("un_sys_tenant", "system_id in (" + firstId + ", " + secondId + ") and is_main = 1"))
                .isEqualTo(2);

        jdbc.update("update un_plat_member set status = 'DISABLED' where account_id = "
                + "(select id from un_plat_account where username = 'multi_system_owner')");
        ResponseEntity<Map> denied = exchangeWithBearer("/api/systems", HttpMethod.POST, platformToken,
                Map.of("name", "不允许的系统", "code", "multi_owner_denied"));
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(count("un_sys_system", "code = 'multi_owner_denied'")).isZero();
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
        assertThat(count("un_audit_event", "event_code = 'AUTH_LOGIN' and result_code = 'AUTHENTICATION_FAILED'"))
                .isGreaterThanOrEqualTo(2);

        ResponseEntity<Map> login = http.postForEntity("/api/auth/login",
                Map.of("username", "admin", "password", "123123aa"), Map.class);
        String accessToken = (String) data(login).get("accessToken");
        try {
            jdbc.update("update un_plat_account set status = 'DISABLED' where username = 'admin'");
            ResponseEntity<Map> me = exchangeWithBearer("/api/auth/me", HttpMethod.GET, accessToken, null);
            assertThat(me.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(me.getBody().get("code")).isEqualTo("ACCOUNT_DISABLED");
            assertThat(count("un_auth_session", "revoked = 1")).isGreaterThanOrEqualTo(1);
            assertThat(count("un_audit_event", "event_code = 'AUTH_ACCESS' and result_code = 'ACCOUNT_DISABLED'")).isOne();
        } finally {
            jdbc.update("update un_plat_account set status = 'ACTIVE' where username = 'admin'");
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
        assertThat(count("un_plat_account_credential",
                "account_id = (select id from un_plat_account where username = 'locked_owner') and failed_attempts = 5 and locked_until is not null"))
                .isOne();
        assertThat(count("un_audit_event", "event_code = 'AUTH_LOGIN' and result_code = 'ACCOUNT_TEMPORARILY_LOCKED'"))
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
        assertThat(count("un_plat_account", "username = 'profile_owner' and display_name = '更新后的姓名'"))
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
        assertThat(count("un_plat_password_history", "account_id = (select id from un_plat_account where username = 'profile_owner')"))
                .isOne();
        assertThat(count("un_auth_session", "account_id = (select id from un_plat_account where username = 'profile_owner') and revoked_reason = 'PASSWORD_CHANGED'"))
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
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair signingKey = generator.generateKeyPair();
        RSAPublicKey publicKey = (RSAPublicKey) signingKey.getPublic();
        ObjectMapper json = new ObjectMapper();
        String jwks = json.writeValueAsString(Map.of("keys", List.of(Map.of(
                "kty", "RSA", "kid", "cycle-key", "alg", "RS256", "use", "sig",
                "n", base64Unsigned(publicKey.getModulus()), "e", base64Unsigned(publicKey.getPublicExponent())))));
        AtomicReference<String> issuer = new AtomicReference<>();
        AtomicReference<String> nonce = new AtomicReference<>();
        HttpServer identityProvider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        identityProvider.createContext("/token", exchange -> respondJson(exchange,
                json.writeValueAsString(Map.of("access_token", "provider-access-token", "token_type", "Bearer",
                        "id_token", signedOidcToken(json, signingKey, issuer.get(), nonce.get())))));
        identityProvider.createContext("/userinfo", exchange -> respondJson(exchange,
                "{\"sub\":\"mapped-subject\",\"email\":\"mapped@example.test\",\"email_verified\":true,\"name\":\"Mapped User\",\"amr\":[\"pwd\",\"mfa\"]}"));
        identityProvider.createContext("/jwks", exchange -> respondJson(exchange, jwks));
        identityProvider.createContext("/invalid-jwks", exchange -> respondJson(exchange, "{\"keys\":[]}"));
        identityProvider.createContext("/health", exchange -> respondJson(exchange, "{\"status\":\"UP\"}"));
        identityProvider.start();
        try {
            int port = identityProvider.getAddress().getPort();
            String baseUrl = "http://127.0.0.1:" + port;
            issuer.set(baseUrl);
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
            assertThat(count("un_plat_sso_provider_version", "provider_id = " + providerId
                    + " and version_number = 1 and status = 'PUBLISHED' and test_status = 'PASSED'")).isOne();
            assertThat(count("un_audit_event", "event_code = 'SSO_PROVIDER_PUBLISHED' and object_id = '" + versionId + "'")).isOne();

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

            long adminId = jdbc.queryForObject("select id from un_plat_account where username = 'admin'", Long.class);
            jdbc.update("insert into un_plat_sso_identity(provider_id, external_subject, account_id) values (?, 'mapped-subject', ?)", providerId, adminId);

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
            nonce.set(query.get("nonce").getFirst());

            ResponseEntity<Map> completed = http.postForEntity("/api/auth/sso/cycle-oidc/complete",
                    Map.of("code", "authorization-code", "state", state), Map.class);
            assertThat(completed.getStatusCode()).isEqualTo(HttpStatus.OK);
            String accessToken = (String) data(completed).get("accessToken");
            ResponseEntity<Map> me = exchangeWithBearer("/api/auth/me", HttpMethod.GET, accessToken, null);
            assertThat(data(me)).containsEntry("username", "admin").containsEntry("mfaLevel", "MFA");
            assertThat(count("un_plat_sso_identity", "provider_id = " + providerId + " and external_subject = 'mapped-subject' and last_login_time is not null")).isOne();
            assertThat(count("un_audit_event", "event_code = 'SSO_LOGIN' and result_code = 'SUCCESS' and detail_json like '%mapped-subject%' and detail_json like '%cycle-oidc%'" )).isOne();

            jdbc.update("delete from un_plat_sso_identity where provider_id = ?", providerId);
            ResponseEntity<Map> secondStart = http.postForEntity("/api/auth/sso/cycle-oidc/start", null, Map.class);
            String secondUrl = (String) data(secondStart).get("authorizationUrl");
            String secondState = UriComponentsBuilder.fromUriString(secondUrl).build().getQueryParams().getFirst("state");
            nonce.set(UriComponentsBuilder.fromUriString(secondUrl).build().getQueryParams().getFirst("nonce"));
            ResponseEntity<Map> missingMapping = http.postForEntity("/api/auth/sso/cycle-oidc/complete",
                    Map.of("code", "authorization-code", "state", secondState), Map.class);
            assertThat(missingMapping.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(missingMapping.getBody().get("code")).isEqualTo("SSO_MAPPING_MISSING");
            assertThat(count("un_audit_event", "event_code = 'SSO_LOGIN' and result_code = 'SSO_MAPPING_MISSING'" )).isOne();
        } finally {
            identityProvider.stop(0);
        }
    }

    /** 通用 OAuth2 身份源必须完成授权码与 PKCE 交换，再由已确认映射进入系统。 */
    @Test
    void oauth2ProviderPublishesAndOnlyMappedIdentityEntersSystem() throws Exception {
        AtomicReference<String> challenge = new AtomicReference<>();
        AtomicReference<String> subject = new AtomicReference<>("oauth-subject");
        AtomicReference<String> email = new AtomicReference<>("oauth-owner@example.test");
        AtomicReference<Boolean> emailVerified = new AtomicReference<>(true);
        HttpServer provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        provider.createContext("/authorize", exchange -> {
            Map<String, String> query = formValues(exchange.getRequestURI().getRawQuery());
            if (!"code".equals(query.get("response_type")) || !"S256".equals(query.get("code_challenge_method"))
                    || query.get("code_challenge") == null || query.get("scope").contains("openid")) {
                exchange.sendResponseHeaders(400, -1);
                return;
            }
            challenge.set(query.get("code_challenge"));
            exchange.getResponseHeaders().set("Location", query.get("redirect_uri")
                    + "?code=oauth-code&state=" + query.get("state"));
            exchange.sendResponseHeaders(302, -1);
        });
        provider.createContext("/token", exchange -> {
            Map<String, String> form = formValues(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] digest;
            try {
                digest = MessageDigest.getInstance("SHA-256")
                        .digest(form.getOrDefault("code_verifier", "").getBytes(StandardCharsets.US_ASCII));
            } catch (java.security.NoSuchAlgorithmException exception) {
                throw new IllegalStateException("测试环境缺少 SHA-256", exception);
            }
            String expected = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
            if (!"authorization_code".equals(form.get("grant_type")) || !"oauth-code".equals(form.get("code"))
                    || !"local-oidc-secret".equals(form.get("client_secret")) || !expected.equals(challenge.get())) {
                exchange.sendResponseHeaders(400, -1);
                return;
            }
            respondJson(exchange, "{\"access_token\":\"oauth-access\",\"token_type\":\"Bearer\"}");
        });
        provider.createContext("/userinfo", exchange -> {
            if (!"Bearer oauth-access".equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
                exchange.sendResponseHeaders(401, -1);
                return;
            }
            respondJson(exchange, new ObjectMapper().writeValueAsString(Map.of(
                    "employee_id", subject.get(), "mail", email.get(), "mail_verified", emailVerified.get())));
        });
        provider.createContext("/health", exchange -> respondJson(exchange, "{\"status\":\"UP\"}"));
        provider.start();
        try {
            String base = "http://127.0.0.1:" + provider.getAddress().getPort();
            String callback = "http://127.0.0.1:15173/sso/callback";
            String adminToken = (String) data(http.postForEntity("/api/auth/login",
                    Map.of("username", "admin", "password", "123123aa"), Map.class)).get("accessToken");
            Map<String, Object> owner = data(http.postForEntity("/api/auth/register", Map.of(
                    "username", "oauth_owner", "password", "correct-password", "displayName", "OAuth 成员",
                    "email", "oauth-owner@example.test", "systemName", "OAuth 目标系统",
                    "systemCode", "oauth_target"), Map.class));
            long systemId = ((Number) owner.get("systemId")).longValue();
            long tenantId = ((Number) owner.get("tenantId")).longValue();
            String systemToken = (String) ((Map<?, ?>) owner.get("tokens")).get("accessToken");

            Map<String, Object> draft = oidcDraft("cycle-oauth2", "周期 OAuth2", "OAUTH2",
                    base, callback, "property:test.sso.secret", "");
            Map<String, Object> config = (Map<String, Object>) draft.get("protocolConfig");
            config.remove("jwksUri");
            config.put("scopes", List.of("profile", "email"));
            draft.put("attributeMapping", Map.of("externalUserId", "employee_id", "email", "mail",
                    "emailVerified", "mail_verified"));
            draft.put("mfaPolicy", Map.of("required", false));
            Map<String, Object> created = data(exchangeWithBearer("/api/admin/platform/sso-providers",
                    HttpMethod.POST, adminToken, draft));
            long providerId = ((Number) created.get("id")).longValue();
            long versionId = ((Number) ((Map<?, ?>) ((List<?>) created.get("versions")).getFirst()).get("id")).longValue();
            assertThat(((List<?>) http.getForEntity("/api/auth/sso/providers", Map.class).getBody().get("data")))
                    .noneMatch(item -> "cycle-oauth2".equals(((Map<?, ?>) item).get("code")));
            assertThat(data(exchangeWithBearer("/api/admin/platform/sso-providers/" + providerId
                    + "/versions/" + versionId + "/test", HttpMethod.POST, adminToken, null)))
                    .containsEntry("status", "PASSED");
            assertThat(exchangeWithBearer("/api/admin/platform/sso-providers/" + providerId
                    + "/versions/" + versionId + "/publish", HttpMethod.POST, adminToken, null).getStatusCode())
                    .isEqualTo(HttpStatus.OK);

            long rootDepartmentId = jdbc.queryForObject("select id from un_sys_department where tenant_id = ? and code = 'root'",
                    Long.class, tenantId);
            assertThat(exchangeWithBearer("/api/admin/system/identity-mapping/configuration", HttpMethod.POST,
                    systemToken, Map.of("providerId", providerId, "providerVersionId", versionId,
                            "tenantDomain", "example.test", "departmentMappings", Map.of("HQ", rootDepartmentId),
                            "matchOrder", List.of("EMAIL"), "jitPolicy", "ACCESS_REQUEST")).getStatusCode())
                    .isEqualTo(HttpStatus.OK);
            Map<String, Object> preflight = data(exchangeWithBearer("/api/admin/system/identity-mapping/preflight",
                    HttpMethod.POST, systemToken, Map.of("samples", List.of(Map.of(
                            "externalUserId", "oauth-subject", "externalDepartment", "HQ",
                            "email", "oauth-owner@example.test", "displayName", "OAuth 成员")))));
            assertThat(exchangeWithBearer("/api/admin/system/identity-mapping/confirm", HttpMethod.POST,
                    systemToken, Map.of("preflightId", preflight.get("preflightId"), "confirmed", true)).getStatusCode())
                    .isEqualTo(HttpStatus.OK);
            assertThat(count("un_plat_sso_identity", "provider_id = " + providerId
                    + " and external_subject = 'oauth-subject'")).isOne();
            assertThat(count("un_sys_sso_binding", "provider_id = " + providerId + " and status = 'ACTIVE'")).isOne();

            String loginPath = "/api/auth/sso/cycle-oauth2";
            String authorizationUrl = (String) data(http.postForEntity(loginPath + "/start", null, Map.class))
                    .get("authorizationUrl");
            HttpClient browser = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
            HttpResponse<Void> redirect = browser.send(HttpRequest.newBuilder(URI.create(authorizationUrl)).GET().build(),
                    HttpResponse.BodyHandlers.discarding());
            assertThat(redirect.statusCode()).isEqualTo(302);
            Map<String, String> callbackQuery = formValues(URI.create(redirect.headers()
                    .firstValue("Location").orElseThrow()).getRawQuery());
            ResponseEntity<Map> completed = http.postForEntity(loginPath + "/complete",
                    Map.of("code", callbackQuery.get("code"), "state", callbackQuery.get("state")), Map.class);
            assertThat(completed.getStatusCode()).isEqualTo(HttpStatus.OK);
            String platformToken = (String) data(completed).get("accessToken");
            assertThat(data(exchangeWithBearer("/api/auth/me", HttpMethod.GET, platformToken, null)))
                    .containsEntry("username", "oauth_owner");
            assertThat(exchangeWithBearer("/api/systems/" + systemId + "/enter", HttpMethod.POST,
                    platformToken, null).getStatusCode()).isEqualTo(HttpStatus.OK);
            long otherSystemId = ((Number) data(exchangeWithBearer("/api/systems", HttpMethod.POST,
                    adminToken, Map.of("name", "未授权 SSO 系统", "code", "oauth_denied_target")))
                    .get("systemId")).longValue();
            assertThat(exchangeWithBearer("/api/systems/" + otherSystemId + "/enter", HttpMethod.POST,
                    platformToken, null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(http.postForEntity(loginPath + "/complete", Map.of(
                    "code", "oauth-code", "state", callbackQuery.get("state")), Map.class).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(http.postForEntity(loginPath + "/complete", Map.of(
                    "code", "oauth-code", "state", "unknown-state"), Map.class).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);

            email.set("outsider@other.test");
            String deniedUrl = (String) data(http.postForEntity(loginPath + "/start", null, Map.class))
                    .get("authorizationUrl");
            String deniedState = UriComponentsBuilder.fromUriString(deniedUrl).build().getQueryParams().getFirst("state");
            assertThat(browser.send(HttpRequest.newBuilder(URI.create(deniedUrl)).GET().build(),
                    HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(302);
            assertThat(http.postForEntity(loginPath + "/complete", Map.of(
                    "code", "oauth-code", "state", deniedState), Map.class).getBody())
                    .containsEntry("code", "SSO_DOMAIN_NOT_ALLOWED");
            email.set("oauth-owner@example.test");
            emailVerified.set(false);
            String unverifiedUrl = (String) data(http.postForEntity(loginPath + "/start", null, Map.class))
                    .get("authorizationUrl");
            assertThat(browser.send(HttpRequest.newBuilder(URI.create(unverifiedUrl)).GET().build(),
                    HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(302);
            String unverifiedState = UriComponentsBuilder.fromUriString(unverifiedUrl).build()
                    .getQueryParams().getFirst("state");
            assertThat(http.postForEntity(loginPath + "/complete", Map.of(
                    "code", "oauth-code", "state", unverifiedState), Map.class).getBody())
                    .containsEntry("code", "SSO_DOMAIN_NOT_ALLOWED");
            emailVerified.set(true);
            subject.set("unmapped-subject");
            String unmappedUrl = (String) data(http.postForEntity(loginPath + "/start", null, Map.class))
                    .get("authorizationUrl");
            assertThat(browser.send(HttpRequest.newBuilder(URI.create(unmappedUrl)).GET().build(),
                    HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(302);
            String unmappedState = UriComponentsBuilder.fromUriString(unmappedUrl).build()
                    .getQueryParams().getFirst("state");
            assertThat(http.postForEntity(loginPath + "/complete", Map.of(
                    "code", "oauth-code", "state", unmappedState), Map.class).getBody())
                    .containsEntry("code", "SSO_MAPPING_MISSING");
            String expiringUrl = (String) data(http.postForEntity(loginPath + "/start", null, Map.class))
                    .get("authorizationUrl");
            String expiringState = UriComponentsBuilder.fromUriString(expiringUrl).build()
                    .getQueryParams().getFirst("state");
            Object originalClock = org.springframework.test.util.ReflectionTestUtils.getField(enterpriseSsoService, "clock");
            try {
                org.springframework.test.util.ReflectionTestUtils.setField(enterpriseSsoService, "clock",
                        java.time.Clock.offset(java.time.Clock.systemDefaultZone(), java.time.Duration.ofMinutes(6)));
                assertThat(http.postForEntity(loginPath + "/complete", Map.of(
                        "code", "oauth-code", "state", expiringState), Map.class).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED);
            } finally {
                org.springframework.test.util.ReflectionTestUtils.setField(enterpriseSsoService, "clock", originalClock);
            }
            assertThat(count("un_audit_event", "event_code = 'SSO_LOGIN' and result_code = 'SSO_STATE_INVALID'"
                    + " and detail_json like '%cycle-oauth2%'")).isEqualTo(3);
            assertThat(count("un_audit_event", "event_code = 'SSO_LOGIN' and result_code = 'SUCCESS'"
                    + " and detail_json like '%cycle-oauth2%'")).isOne();
        } finally {
            provider.stop(0);
        }
    }

    /** SAML2 必须由已发布证书验签，经一次性 ACS 交接后才复用平台及系统显式映射。 */
    @Test
    void signedSamlProviderPublishesAndOnlyMappedIdentityEntersSystem() throws Exception {
        SamlResponseVerifierTest.KeyMaterial key = SamlResponseVerifierTest.material(samlFixtureDirectory);
        System.setProperty("test.sso.certificate", key.certificatePem());
        HttpServer idp = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        idp.createContext("/sso", exchange -> {
            byte[] page = "<html><body>隔离 SAML2 身份源授权页</body></html>".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, page.length);
            try (var output = exchange.getResponseBody()) { output.write(page); }
        });
        idp.createContext("/health", exchange -> respondJson(exchange, "{\"status\":\"UP\"}"));
        idp.start();
        try {
            String base = "http://127.0.0.1:" + idp.getAddress().getPort();
            String loginPath = "/api/auth/sso/cycle-saml2";
            String acs = http.getRootUri() + loginPath + "/saml/acs";
            String sp = "https://service.example.test/saml";
            String adminToken = (String) data(http.postForEntity("/api/auth/login",
                    Map.of("username", "admin", "password", "123123aa"), Map.class)).get("accessToken");
            Map<String, Object> owner = data(http.postForEntity("/api/auth/register", Map.of(
                    "username", "saml_owner", "password", "correct-password", "displayName", "SAML 成员",
                    "email", "member@example.test", "systemName", "SAML 目标系统",
                    "systemCode", "saml_target"), Map.class));
            long systemId = ((Number) owner.get("systemId")).longValue();
            long tenantId = ((Number) owner.get("tenantId")).longValue();
            String systemToken = (String) ((Map<?, ?>) owner.get("tokens")).get("accessToken");
            Map<String, Object> draft = new LinkedHashMap<>();
            draft.put("code", "cycle-saml2");
            draft.put("name", "周期 SAML2");
            draft.put("protocol", "SAML2");
            draft.put("issuer", base + "/issuer");
            draft.put("clientId", sp);
            draft.put("protocolConfig", Map.of("ssoEndpoint", base + "/sso", "acsUri", acs,
                    "certificateRef", "property:test.sso.certificate", "healthEndpoint", base + "/health"));
            draft.put("allowedDomains", List.of("example.test"));
            draft.put("attributeMapping", Map.of("externalUserId", "sub", "email", "email",
                    "emailVerified", "email_verified"));
            draft.put("jitPolicy", Map.of("enabled", false));
            draft.put("mfaPolicy", Map.of("required", false));
            draft.put("callbackUris", List.of(acs));
            Map<String, Object> created = data(exchangeWithBearer("/api/admin/platform/sso-providers",
                    HttpMethod.POST, adminToken, draft));
            long providerId = ((Number) created.get("id")).longValue();
            long versionId = ((Number) ((Map<?, ?>) ((List<?>) created.get("versions")).getFirst()).get("id")).longValue();
            assertThat(((List<?>) http.getForEntity("/api/auth/sso/providers", Map.class).getBody().get("data")))
                    .noneMatch(item -> "cycle-saml2".equals(((Map<?, ?>) item).get("code")));
            assertThat(data(exchangeWithBearer("/api/admin/platform/sso-providers/" + providerId
                    + "/versions/" + versionId + "/test", HttpMethod.POST, adminToken, null)))
                    .containsEntry("status", "PASSED");
            assertThat(exchangeWithBearer("/api/admin/platform/sso-providers/" + providerId
                    + "/versions/" + versionId + "/publish", HttpMethod.POST, adminToken, null).getStatusCode())
                    .isEqualTo(HttpStatus.OK);

            long departmentId = jdbc.queryForObject("select id from un_sys_department where tenant_id = ? and code = 'root'",
                    Long.class, tenantId);
            assertThat(exchangeWithBearer("/api/admin/system/identity-mapping/configuration", HttpMethod.POST,
                    systemToken, Map.of("providerId", providerId, "providerVersionId", versionId,
                            "tenantDomain", "example.test", "departmentMappings", Map.of("HQ", departmentId),
                            "matchOrder", List.of("EMAIL"), "jitPolicy", "ACCESS_REQUEST")).getStatusCode())
                    .isEqualTo(HttpStatus.OK);
            Map<String, Object> preflight = data(exchangeWithBearer("/api/admin/system/identity-mapping/preflight",
                    HttpMethod.POST, systemToken, Map.of("samples", List.of(Map.of(
                            "externalUserId", "external-1", "externalDepartment", "HQ",
                            "email", "member@example.test", "displayName", "SAML 成员")))));
            assertThat(exchangeWithBearer("/api/admin/system/identity-mapping/confirm", HttpMethod.POST,
                    systemToken, Map.of("preflightId", preflight.get("preflightId"), "confirmed", true)).getStatusCode())
                    .isEqualTo(HttpStatus.OK);

            String authorizationUrl = (String) data(http.postForEntity(loginPath + "/start", null, Map.class))
                    .get("authorizationUrl");
            Map<String, String> requestQuery = formValues(URI.create(authorizationUrl).getRawQuery());
            assertThat(requestQuery).containsKeys("SAMLRequest", "RelayState");
            byte[] compressed = Base64.getDecoder().decode(requestQuery.get("SAMLRequest"));
            String requestXml;
            try (var input = new InflaterInputStream(new java.io.ByteArrayInputStream(compressed), new Inflater(true))) {
                requestXml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
            String requestId = requestXml.split("ID=\"", 2)[1].split("\"", 2)[0];
            assertThat(requestXml).contains(sp, acs, base + "/sso");
            HttpClient browser = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
            assertThat(browser.send(HttpRequest.newBuilder(URI.create(authorizationUrl)).GET().build(),
                    HttpResponse.BodyHandlers.ofString()).body()).contains("隔离 SAML2 身份源授权页");
            String signed = SamlResponseVerifierTest.signed(key, requestId, sp, Instant.now().plusSeconds(300),
                    acs, base + "/issuer", Instant.now());
            String form = "SAMLResponse=" + URLEncoder.encode(signed, StandardCharsets.UTF_8)
                    + "&RelayState=" + URLEncoder.encode(requestQuery.get("RelayState"), StandardCharsets.UTF_8);
            HttpRequest acsRequest = HttpRequest.newBuilder(URI.create(acs))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form)).build();
            HttpResponse<Void> accepted = browser.send(acsRequest, HttpResponse.BodyHandlers.discarding());
            assertThat(accepted.statusCode()).isEqualTo(302);
            Map<String, String> callback = formValues(URI.create(accepted.headers()
                    .firstValue("Location").orElseThrow()).getRawQuery());
            ResponseEntity<Map> completed = http.postForEntity(loginPath + "/complete",
                    Map.of("code", callback.get("code"), "state", callback.get("state")), Map.class);
            assertThat(completed.getStatusCode()).isEqualTo(HttpStatus.OK);
            String platformToken = (String) data(completed).get("accessToken");
            assertThat(data(exchangeWithBearer("/api/auth/me", HttpMethod.GET, platformToken, null)))
                    .containsEntry("username", "saml_owner");
            assertThat(exchangeWithBearer("/api/systems/" + systemId + "/enter", HttpMethod.POST,
                    platformToken, null).getStatusCode()).isEqualTo(HttpStatus.OK);
            long deniedSystemId = ((Number) data(exchangeWithBearer("/api/systems", HttpMethod.POST,
                    adminToken, Map.of("name", "SAML 无权系统", "code", "saml_denied_target"))).get("systemId")).longValue();
            assertThat(exchangeWithBearer("/api/systems/" + deniedSystemId + "/enter", HttpMethod.POST,
                    platformToken, null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(browser.send(acsRequest, HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(401);
            assertThat(http.postForEntity(loginPath + "/complete",
                    Map.of("code", callback.get("code"), "state", callback.get("state")), Map.class).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);

            for (String failure : List.of("DOMAIN", "UNVERIFIED", "UNMAPPED", "TAMPERED")) {
                String secondUrl = (String) data(http.postForEntity(loginPath + "/start", null, Map.class))
                        .get("authorizationUrl");
                Map<String, String> secondQuery = formValues(URI.create(secondUrl).getRawQuery());
                String secondId = samlRequestId(secondQuery.get("SAMLRequest"));
                String secondSigned = SamlResponseVerifierTest.signedWithIdentity(key, secondId, sp,
                        Instant.now().plusSeconds(300), acs, base + "/issuer", Instant.now(),
                        "UNMAPPED".equals(failure) ? "unmapped-subject" : "external-1",
                        "DOMAIN".equals(failure) ? "outsider@other.test" : "member@example.test",
                        "UNVERIFIED".equals(failure) ? "false" : "true");
                if ("TAMPERED".equals(failure)) {
                    String xml = new String(Base64.getDecoder().decode(secondSigned), StandardCharsets.UTF_8)
                            .replace("external-1", "forged-subject");
                    secondSigned = Base64.getEncoder().encodeToString(xml.getBytes(StandardCharsets.UTF_8));
                }
                String secondForm = "SAMLResponse=" + URLEncoder.encode(secondSigned, StandardCharsets.UTF_8)
                        + "&RelayState=" + URLEncoder.encode(secondQuery.get("RelayState"), StandardCharsets.UTF_8);
                HttpResponse<Void> secondAcs = browser.send(HttpRequest.newBuilder(URI.create(acs))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(secondForm)).build(),
                        HttpResponse.BodyHandlers.discarding());
                if ("TAMPERED".equals(failure)) {
                    assertThat(secondAcs.statusCode()).isEqualTo(401);
                } else {
                    assertThat(secondAcs.statusCode()).isEqualTo(302);
                    Map<String, String> deniedCallback = formValues(URI.create(secondAcs.headers()
                            .firstValue("Location").orElseThrow()).getRawQuery());
                    ResponseEntity<Map> denied = http.postForEntity(loginPath + "/complete",
                            Map.of("code", deniedCallback.get("code"), "state", deniedCallback.get("state")), Map.class);
                    assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(denied.getBody()).containsEntry("code",
                            "UNMAPPED".equals(failure) ? "SSO_MAPPING_MISSING" : "SSO_DOMAIN_NOT_ALLOWED");
                }
            }

            String expiringUrl = (String) data(http.postForEntity(loginPath + "/start", null, Map.class))
                    .get("authorizationUrl");
            Map<String, String> expiringQuery = formValues(URI.create(expiringUrl).getRawQuery());
            String expiringSigned = SamlResponseVerifierTest.signed(key,
                    samlRequestId(expiringQuery.get("SAMLRequest")), sp, Instant.now().plusSeconds(300),
                    acs, base + "/issuer", Instant.now());
            Object originalClock = org.springframework.test.util.ReflectionTestUtils.getField(enterpriseSsoService, "clock");
            try {
                org.springframework.test.util.ReflectionTestUtils.setField(enterpriseSsoService, "clock",
                        java.time.Clock.offset(java.time.Clock.systemDefaultZone(), java.time.Duration.ofMinutes(6)));
                String expiredForm = "SAMLResponse=" + URLEncoder.encode(expiringSigned, StandardCharsets.UTF_8)
                        + "&RelayState=" + URLEncoder.encode(expiringQuery.get("RelayState"), StandardCharsets.UTF_8);
                assertThat(browser.send(HttpRequest.newBuilder(URI.create(acs))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(expiredForm)).build(),
                        HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(401);
            } finally {
                org.springframework.test.util.ReflectionTestUtils.setField(enterpriseSsoService, "clock", originalClock);
            }
            assertThat(count("un_audit_event", "event_code = 'SSO_LOGIN' and result_code = 'SUCCESS'"
                    + " and detail_json like '%cycle-saml2%'")).isOne();
        } finally {
            idp.stop(0);
            System.clearProperty("test.sso.certificate");
        }
    }

    private static String samlRequestId(String encoded) throws Exception {
        byte[] compressed = Base64.getDecoder().decode(encoded);
        try (var input = new InflaterInputStream(new java.io.ByteArrayInputStream(compressed), new Inflater(true))) {
            String xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            return xml.split("ID=\"", 2)[1].split("\"", 2)[0];
        }
    }

    private static Map<String, String> formValues(String raw) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String pair : raw.split("&")) {
            String[] parts = pair.split("=", 2);
            values.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(parts.length == 2 ? parts[1] : "", StandardCharsets.UTF_8));
        }
        return values;
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
                "select tm.id from un_sys_tenant_member tm join un_sys_member m on m.id = tm.system_member_id where m.account_id = (select id from un_plat_account where username = 'permission_owner')",
                Long.class);

        jdbc.update("delete from un_sys_member_role where tenant_member_id = ?", tenantMemberId);
        jdbc.update("delete from un_sys_role_permission where role_id in (select id from un_sys_role where tenant_id = ?)", tenantId);
        jdbc.update("delete from un_sys_role where tenant_id = ?", tenantId);
        jdbc.update("insert into un_sys_role(system_id, tenant_id, code, name, status) values (?, ?, 'OWNER_ROLE', '本人数据角色', 'ACTIVE')", systemId, tenantId);
        long ownerRoleId = jdbc.queryForObject("select id from un_sys_role where tenant_id = ? and code = 'OWNER_ROLE'", Long.class, tenantId);
        jdbc.update("insert into un_sys_role(system_id, tenant_id, code, name, status) values (?, ?, 'DEPARTMENT_ROLE', '部门数据角色', 'ACTIVE')", systemId, tenantId);
        long departmentRoleId = jdbc.queryForObject("select id from un_sys_role where tenant_id = ? and code = 'DEPARTMENT_ROLE'", Long.class, tenantId);
        jdbc.update("insert into un_sys_member_role(tenant_id, tenant_member_id, role_id) values (?, ?, ?), (?, ?, ?)",
                tenantId, tenantMemberId, ownerRoleId, tenantId, tenantMemberId, departmentRoleId);
        jdbc.update("insert into un_sys_role_permission(system_id, tenant_id, role_id, resource_type, resource_code, action_code, data_scope_type) values (?, ?, ?, 'MODULE', 'customer', 'LIST', 'SELF')", systemId, tenantId, ownerRoleId);
        jdbc.update("insert into un_sys_role_permission(system_id, tenant_id, role_id, resource_type, resource_code, action_code, data_scope_type) values (?, ?, ?, 'MODULE', 'customer', 'UPDATE', 'SELF')", systemId, tenantId, ownerRoleId);
        jdbc.update("insert into un_sys_role_permission(system_id, tenant_id, role_id, resource_type, resource_code, action_code, data_scope_type) values (?, ?, ?, 'MODULE', 'customer', 'LIST', 'DEPARTMENT')", systemId, tenantId, departmentRoleId);
        jdbc.update("insert into un_sys_role_permission(system_id, tenant_id, role_id, resource_type, resource_code, action_code, data_scope_type, data_scope_json) values (?, ?, ?, 'MODULE', 'customer', 'EXPORT', 'FIELD_RULE', '{\"field\":\"creator\"}')", systemId, tenantId, ownerRoleId);
        jdbc.update("insert into un_sys_role_permission(system_id, tenant_id, role_id, resource_type, resource_code, action_code, data_scope_type, data_scope_json) values (?, ?, ?, 'MODULE', 'customer', 'EXPORT', 'FIELD_RULE', '{\"field\":\"participant\"}')", systemId, tenantId, departmentRoleId);

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
                "select role_ids_json from un_auth_session where system_id = ? order by id desc limit 1", String.class, systemId);
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
        assertThat(count("un_audit_event", "event_code = 'PERMISSION_CHECK' and result_code = 'PERMISSION_DENIED'"
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
        assertThat(count("un_audit_event", "event_code = 'SYSTEM_ENTER' and result_code = 'SYSTEM_ACCESS_DENIED'")).isOne();
    }

    @Test
    @SuppressWarnings("unchecked")
    void platformAdministratorCreatesSystemAndDecidesCompleteNoMemberAccessLifecycle() {
        String adminToken = (String) data(http.postForEntity("/api/auth/login",
                Map.of("username", "admin", "password", "123123aa"), Map.class)).get("accessToken");
        jdbc.execute("create trigger test_fail_system_role before insert on un_sys_role for each row "
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
        assertThat(count("un_sys_system", "code = 'rollback_target'")).isZero();
        assertThat(count("un_sys_tenant", "system_id in (select id from un_sys_system where code = 'rollback_target')")).isZero();

        ResponseEntity<Map> createdSystem = exchangeWithBearer("/api/systems", HttpMethod.POST, adminToken, Map.of(
                "name", "访问审批目标系统"));
        assertThat(createdSystem.getStatusCode())
                .withFailMessage("创建系统失败：%s", createdSystem.getBody())
                .isEqualTo(HttpStatus.OK);
        Map<String, Object> target = data(createdSystem);
        long systemId = ((Number) target.get("systemId")).longValue();
        long tenantId = ((Number) target.get("defaultTenantId")).longValue();
        assertThat(count("un_sys_system", "id = " + systemId + " and code like 'system\\_%'")).isOne();
        assertThat(count("un_sys_tenant", "system_id = " + systemId + " and is_main = 1")).isOne();
        assertThat(count("un_sys_department", "tenant_id = " + tenantId + " and code = 'root' and parent_id is null")).isOne();
        assertThat(count("un_sys_member", "system_id = " + systemId + " and account_id = (select id from un_plat_account where username = 'admin')")).isOne();
        assertThat(count("un_sys_tenant_member", "tenant_id = " + tenantId + " and tenant_admin = 1 and department_id in "
                + "(select id from un_sys_department where tenant_id = " + tenantId + " and code = 'root')")).isOne();
        ResponseEntity<Map> latestSystems = exchangeWithBearer("/api/systems", HttpMethod.GET, adminToken, null);
        Map<?, ?> firstSystem = (Map<?, ?>) ((List<?>) latestSystems.getBody().get("data")).getFirst();
        assertThat(((Number) firstSystem.get("systemId")).longValue()).isEqualTo(systemId);
        assertThat(firstSystem.get("defaultTenantName")).isEqualTo("默认主租户");

        registerAccessApplicant("access_applicant", "访问申请人");
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
        assertThat(count("un_sys_access_request", "system_id = " + systemId + " and account_id = (select id from un_plat_account where username = 'access_applicant')")).isOne();
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
        assertThat(count("un_sys_member", "system_id = " + systemId + " and account_id = (select id from un_plat_account where username = 'access_applicant')")).isOne();
        assertThat(count("un_sys_member_role", "role_id = " + roleId + " and tenant_id = " + tenantId)).isEqualTo(2);
        assertThat(exchangeWithBearer("/api/systems/" + systemId + "/enter", HttpMethod.POST, applicantToken, null).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> rejectedSystem = exchangeWithBearer("/api/systems", HttpMethod.POST, adminToken, Map.of(
                "code", "reject_target",
                "name", "拒绝分支目标系统",
                "tenantMode", "SINGLE"));
        long rejectedSystemId = ((Number) data(rejectedSystem).get("systemId")).longValue();
        long rejectedTenantId = ((Number) data(rejectedSystem).get("defaultTenantId")).longValue();
        registerAccessApplicant("rejected_applicant", "被拒申请人");
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
        assertThat(count("un_audit_event", "object_type = 'SYSTEM_ACCESS_REQUEST' and event_code = 'SYSTEM_ACCESS_REQUEST_APPROVED'")).isOne();
        assertThat(count("un_audit_event", "object_type = 'SYSTEM_ACCESS_REQUEST' and event_code = 'SYSTEM_ACCESS_REQUEST_REJECTED'")).isOne();
    }

    /** 申请人只注册平台账号，不为加入别人系统而创建无用的个人系统。 */
    private void registerAccessApplicant(String username, String displayName) {
        ResponseEntity<Map> registration = http.postForEntity("/api/auth/register", Map.of(
                "username", username,
                "password", "correct-password",
                "displayName", displayName,
                "email", username + "@example.com"), Map.class);
        assertThat(registration.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(registration).get("systemId")).isNull();
        assertThat(data(registration).get("tokens")).isNull();
        assertThat(data(registration).get("platformTokens")).isNotNull();
        assertThat(count("un_sys_member", "account_id = (select id from un_plat_account where username = '" + username + "')"))
                .isZero();
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
        if (method != HttpMethod.GET) headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return http.exchange(path, method, new HttpEntity<>(body, headers), Map.class);
    }

    /** 本地身份源签发真实 RS256 身份令牌，避免只用 userinfo 冒充 OIDC 成功。 */
    private static String signedOidcToken(ObjectMapper json, KeyPair key, String issuer, String nonce) throws java.io.IOException {
        try {
            long now = Instant.now().getEpochSecond();
            String header = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    json.writeValueAsBytes(Map.of("alg", "RS256", "kid", "cycle-key", "typ", "JWT")));
            String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(json.writeValueAsBytes(Map.of(
                    "iss", issuer, "aud", "cycle-client", "sub", "mapped-subject", "nonce", nonce,
                    "iat", now, "exp", now + 300)));
            String signed = header + "." + payload;
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(key.getPrivate());
            signature.update(signed.getBytes(StandardCharsets.US_ASCII));
            return signed + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign());
        } catch (java.security.GeneralSecurityException exception) {
            throw new java.io.IOException("Cannot sign local OIDC fixture", exception);
        }
    }

    private static String base64Unsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                bytes[0] == 0 ? java.util.Arrays.copyOfRange(bytes, 1, bytes.length) : bytes);
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
