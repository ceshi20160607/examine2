package com.unique.unexamine.moduleconfig.manage;

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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ModuleConfigurationHttpTest {
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
    }

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void administratorBuildsAnIsolatedDraftWithDefaultPagesActionsAndOptimisticLocking() {
        Session first = register("config_owner_a", "config-system-a", "config-a@example.com");
        Session second = register("config_owner_b", "config-system-b", "config-b@example.com");

        ResponseEntity<Map> groupResponse = exchange("/api/admin/module-config/groups", HttpMethod.POST, first.token(),
                Map.of("code", "sales", "name", "销售管理", "sortOrder", 10));
        assertThat(groupResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        long groupId = number(data(groupResponse).get("id"));

        ResponseEntity<Map> duplicateGroup = exchange("/api/admin/module-config/groups", HttpMethod.POST, first.token(),
                Map.of("code", "sales", "name", "重复分组", "sortOrder", 20));
        assertThat(duplicateGroup.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<Map> moduleResponse = exchange("/api/admin/module-config/modules", HttpMethod.POST, first.token(),
                Map.of("groupId", groupId, "code", "customer", "name", "客户"));
        assertThat(moduleResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> initialDraft = data(moduleResponse);
        Map<?, ?> module = (Map<?, ?>) initialDraft.get("module");
        long moduleId = number(module.get("id"));
        assertThat(module.get("status")).isEqualTo("DRAFT");
        List<String> pageTypes = ((List<?>) initialDraft.get("pages")).stream()
                .map(value -> ((Map<?, ?>) value).get("pageType").toString()).toList();
        assertThat(pageTypes).containsExactly("LIST", "FORM", "DETAIL");
        List<String> actionCodes = ((List<?>) initialDraft.get("actions")).stream()
                .map(value -> ((Map<?, ?>) value).get("code").toString()).toList();
        assertThat(actionCodes).containsExactly("LIST", "DETAIL", "CREATE", "UPDATE");
        assertThat(initialDraft.get("published")).isEqualTo(false);
        assertThat(count("cfg_module_publication", "module_id = " + moduleId)).isZero();

        ResponseEntity<Map> overview = exchange("/api/admin/module-config", HttpMethod.GET, first.token(), null);
        assertThat((List<?>) data(overview).get("groups")).hasSize(1);
        assertThat((List<?>) data(overview).get("modules")).hasSize(1);
        ResponseEntity<Map> emptyCatalog = exchange("/api/runtime/modules", HttpMethod.GET, first.token(), null);
        assertThat((List<?>) emptyCatalog.getBody().get("data")).isEmpty();

        ResponseEntity<Map> invalidCheck = exchange(
                "/api/admin/module-config/modules/" + moduleId + "/publication-check",
                HttpMethod.GET, first.token(), null);
        assertThat(data(invalidCheck).get("valid")).isEqualTo(false);
        assertThat((List<?>) data(invalidCheck).get("issues")).hasSize(1);
        ResponseEntity<Map> invalidPublish = exchange(
                "/api/admin/module-config/modules/" + moduleId + "/publish",
                HttpMethod.POST, first.token(), Map.of("expectedDraftRevision", 1));
        assertThat(invalidPublish.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(count("cfg_module_version", "module_id = " + moduleId)).isZero();

        ResponseEntity<Map> fieldResponse = exchange(
                "/api/admin/module-config/modules/" + moduleId + "/fields", HttpMethod.POST, first.token(),
                Map.of("code", "customer_name", "name", "客户名称", "fieldType", "TEXT", "required", true,
                        "sortOrder", 10, "config", Map.of("placeholder", "请输入客户名称")));
        assertThat(fieldResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> field = data(fieldResponse);
        long fieldId = number(field.get("id"));
        int fieldVersion = ((Number) field.get("version")).intValue();

        ResponseEntity<Map> hiddenRequiredField = exchange(
                "/api/admin/module-config/modules/" + moduleId + "/pages/FORM", HttpMethod.PUT, first.token(),
                Map.of("layout", Map.of("fieldCodes", List.of()), "version", 0));
        assertThat(hiddenRequiredField.getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<Map> invalidFormCheck = exchange(
                "/api/admin/module-config/modules/" + moduleId + "/publication-check",
                HttpMethod.GET, first.token(), null);
        assertThat(data(invalidFormCheck).get("valid")).isEqualTo(false);
        assertThat(((List<Map<String, Object>>) data(invalidFormCheck).get("issues")).stream()
                .map(issue -> issue.get("code"))).contains("REQUIRED_FIELD_NOT_IN_FORM");
        ResponseEntity<Map> restoredRequiredField = exchange(
                "/api/admin/module-config/modules/" + moduleId + "/pages/FORM", HttpMethod.PUT, first.token(),
                Map.of("layout", Map.of("fieldCodes", List.of("customer_name")), "version", 1));
        assertThat(restoredRequiredField.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> validCheck = exchange(
                "/api/admin/module-config/modules/" + moduleId + "/publication-check",
                HttpMethod.GET, first.token(), null);
        assertThat(data(validCheck).get("valid")).isEqualTo(true);
        int firstDraftRevision = ((Number) data(validCheck).get("draftRevision")).intValue();
        ResponseEntity<Map> firstPublish = exchange(
                "/api/admin/module-config/modules/" + moduleId + "/publish",
                HttpMethod.POST, first.token(), Map.of("expectedDraftRevision", firstDraftRevision));
        assertThat(firstPublish.getStatusCode()).isEqualTo(HttpStatus.OK);
        long firstVersionId = number(data(firstPublish).get("versionId"));
        assertThat(data(firstPublish).get("versionNumber")).isEqualTo(1);

        ResponseEntity<Map> firstRuntime = exchange(
                "/api/runtime/modules/customer/configuration", HttpMethod.GET, first.token(), null);
        assertThat(firstRuntime.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> firstConfiguration = (Map<?, ?>) data(firstRuntime).get("configuration");
        assertThat(publishedFieldName(firstConfiguration)).isEqualTo("客户名称");
        ResponseEntity<Map> catalog = exchange("/api/runtime/modules", HttpMethod.GET, first.token(), null);
        List<?> catalogItems = (List<?>) catalog.getBody().get("data");
        assertThat(catalogItems).hasSize(1);
        assertThat(((Map<?, ?>) catalogItems.getFirst()).get("moduleCode")).isEqualTo("customer");

        Map<String, Object> update = Map.of(
                "name", "客户全称", "required", true, "sortOrder", 20, "status", "ACTIVE",
                "config", Map.of("placeholder", "请输入客户全称"), "version", fieldVersion);
        ResponseEntity<Map> updated = exchange(
                "/api/admin/module-config/modules/" + moduleId + "/fields/" + fieldId,
                HttpMethod.PUT, first.token(), update);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) data(updated).get("version")).intValue()).isEqualTo(fieldVersion + 1);

        ResponseEntity<Map> staleUpdate = exchange(
                "/api/admin/module-config/modules/" + moduleId + "/fields/" + fieldId,
                HttpMethod.PUT, first.token(), update);
        assertThat(staleUpdate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(staleUpdate.getBody().get("code")).isEqualTo("DRAFT_VERSION_CONFLICT");

        ResponseEntity<Map> runtimeBeforeSecondPublish = exchange(
                "/api/runtime/modules/customer/configuration", HttpMethod.GET, first.token(), null);
        assertThat(publishedFieldName((Map<?, ?>) data(runtimeBeforeSecondPublish).get("configuration")))
                .isEqualTo("客户名称");

        ResponseEntity<Map> changedDraft = exchange(
                "/api/admin/module-config/modules/" + moduleId + "/draft", HttpMethod.GET, first.token(), null);
        int secondDraftRevision = ((Number) ((Map<?, ?>) data(changedDraft).get("module")).get("draftRevision")).intValue();
        ResponseEntity<Map> secondPublish = exchange(
                "/api/admin/module-config/modules/" + moduleId + "/publish",
                HttpMethod.POST, first.token(), Map.of("expectedDraftRevision", secondDraftRevision));
        assertThat(secondPublish.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(secondPublish).get("versionNumber")).isEqualTo(2);
        ResponseEntity<Map> secondRuntime = exchange(
                "/api/runtime/modules/customer/configuration", HttpMethod.GET, first.token(), null);
        assertThat(publishedFieldName((Map<?, ?>) data(secondRuntime).get("configuration"))).isEqualTo("客户全称");

        ResponseEntity<Map> versions = exchange(
                "/api/admin/module-config/modules/" + moduleId + "/versions", HttpMethod.GET, first.token(), null);
        List<?> versionList = (List<?>) versions.getBody().get("data");
        assertThat(versionList).hasSize(2);
        int publicationVersion = ((Number) ((Map<?, ?>) versionList.getFirst()).get("publicationVersion")).intValue();
        ResponseEntity<Map> rollback = exchange(
                "/api/admin/module-config/modules/" + moduleId + "/rollback", HttpMethod.POST, first.token(),
                Map.of("targetVersionId", firstVersionId, "expectedPublicationVersion", publicationVersion));
        assertThat(rollback.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(rollback).get("versionNumber")).isEqualTo(1);
        assertThat(publishedFieldName((Map<?, ?>) data(rollback).get("configuration"))).isEqualTo("客户名称");
        assertThat(count("cfg_module_version", "module_id = " + moduleId)).isEqualTo(2);

        ResponseEntity<Map> draft = exchange(
                "/api/admin/module-config/modules/" + moduleId + "/draft", HttpMethod.GET, first.token(), null);
        assertThat(draft.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) data(draft).get("fields")).hasSize(1);
        assertThat(count("cfg_module_version", "module_id = " + moduleId)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "select current_version_id from cfg_module_publication where module_id = ?", Long.class, moduleId))
                .isEqualTo(firstVersionId);

        ResponseEntity<Map> crossSystemRead = exchange(
                "/api/admin/module-config/modules/" + moduleId + "/draft", HttpMethod.GET, second.token(), null);
        assertThat(crossSystemRead.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ResponseEntity<Map> crossSystemCreate = exchange("/api/admin/module-config/modules", HttpMethod.POST, second.token(),
                Map.of("groupId", groupId, "code", "illegal", "name", "非法跨系统模块"));
        assertThat(crossSystemCreate.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(count("audit_event", "event_code = 'MODULE_DRAFT_CREATED' and result_code = 'SUCCESS'")).isOne();
        assertThat(count("audit_event", "event_code = 'MODULE_FIELD_DRAFT_UPDATED' and result_code = 'SUCCESS'")).isOne();

        ResponseEntity<Map> auditEvents = exchange(
                "/api/admin/audit-events?eventCode=MODULE_PUBLISHED&page=1&pageSize=20",
                HttpMethod.GET, first.token(), null);
        assertThat(auditEvents.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> auditData = data(auditEvents);
        assertThat((List<?>) auditData.get("events")).hasSize(2);
        assertThat(((Number) auditData.get("total")).intValue()).isEqualTo(2);

        ResponseEntity<Map> adminLogin = http.postForEntity("/api/auth/login",
                Map.of("username", "admin", "password", "123123aa"), Map.class);
        String platformToken = (String) data(adminLogin).get("accessToken");
        ResponseEntity<Map> platformOnlyDenied = exchange("/api/admin/module-config/groups", HttpMethod.POST, platformToken,
                Map.of("code", "not_allowed", "name", "无系统身份", "sortOrder", 0));
        assertThat(platformOnlyDenied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(platformOnlyDenied.getBody().get("code")).isEqualTo("PERMISSION_DENIED");
    }

    private Session register(String username, String systemCode, String email) {
        ResponseEntity<Map> response = http.postForEntity("/api/auth/register", Map.of(
                "username", username,
                "password", "correct-password",
                "displayName", username,
                "email", email,
                "systemName", systemCode,
                "systemCode", systemCode), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> result = data(response);
        return new Session((String) ((Map<?, ?>) result.get("tokens")).get("accessToken"));
    }

    private ResponseEntity<Map> exchange(String path, HttpMethod method, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return http.exchange(path, method, new HttpEntity<>(body, headers), Map.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(ResponseEntity<Map> response) {
        return (Map<String, Object>) response.getBody().get("data");
    }

    private long number(Object value) {
        return ((Number) value).longValue();
    }

    private int count(String table, String condition) {
        return jdbc.queryForObject("select count(*) from " + table + " where " + condition, Integer.class);
    }

    private String publishedFieldName(Map<?, ?> configuration) {
        List<?> fields = (List<?>) configuration.get("fields");
        return ((Map<?, ?>) fields.getFirst()).get("name").toString();
    }

    private record Session(String token) {
    }
}
