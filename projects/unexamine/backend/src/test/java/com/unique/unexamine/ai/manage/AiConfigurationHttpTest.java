package com.unique.unexamine.ai.manage;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.dao.DataAccessException;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AiConfigurationHttpTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("unexamine")
            .withUsername("unexamine")
            .withPassword("unexamine_test")
            .withCommand("--log-bin-trust-function-creators=1");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("test.ai.secret", () -> "c45-model-secret-never-persisted");
    }

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void platformGrantAndSystemAgentPublicationConvergeToImmutableAuthorizationSnapshot() {
        Session owner = register("ai_owner", "ai_system", "ai-owner@example.com");
        String platformAdmin = login("admin", "123123aa");

        ResponseEntity<Map> missingCredential = exchange("/api/admin/platform/ai/models", HttpMethod.POST,
                platformAdmin, model("missing_model", "不可用模型", "property:test.ai.missing"));
        assertThat(missingCredential.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(missingCredential.getBody().get("code")).isEqualTo("AI_MODEL_CREDENTIAL_UNAVAILABLE");
        assertThat(count("ai_model", "code='missing_model'")).isZero();

        ResponseEntity<Map> grantedModelResponse = exchange("/api/admin/platform/ai/models", HttpMethod.POST,
                platformAdmin, model("granted_chat", "系统授权模型", "property:test.ai.secret"));
        assertThat(grantedModelResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        long grantedModelId = number(data(grantedModelResponse).get("id"));
        assertThat(data(grantedModelResponse)).doesNotContainKeys("credentialRef", "credentialValue");
        assertThat(data(grantedModelResponse)).containsEntry("credentialReferenceType", "PROPERTY")
                .containsEntry("credentialAvailable", true);

        ResponseEntity<Map> hiddenModelResponse = exchange("/api/admin/platform/ai/models", HttpMethod.POST,
                platformAdmin, model("hidden_chat", "未授权模型", "property:test.ai.secret"));
        long hiddenModelId = number(data(hiddenModelResponse).get("id"));
        assertThat(hiddenModelId).isPositive();

        ResponseEntity<Map> grantResponse = exchange(
                "/api/admin/platform/ai/models/" + grantedModelId + "/grants/" + owner.systemId(),
                HttpMethod.PUT, platformAdmin,
                Map.of("dailyTokenLimit", 40000, "concurrencyLimit", 2));
        assertThat(grantResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        long grantId = number(data(grantResponse).get("id"));
        assertThat(count("audit_event", "event_code='AI_MODEL_GRANTED' and object_id='" + grantId + "'")).isOne();

        ResponseEntity<Map> systemOverview = exchange("/api/admin/system/ai", HttpMethod.GET, owner.systemToken(), null);
        List<Map<String, Object>> available = (List<Map<String, Object>>) data(systemOverview).get("availableModels");
        assertThat(available).singleElement().satisfies(model -> {
            assertThat(model).containsEntry("id", (int) grantedModelId).containsEntry("activeGrantId", (int) grantId);
            assertThat(model.get("code")).isEqualTo("granted_chat");
        });
        assertThat(available.stream().map(model -> model.get("id"))).doesNotContain((int) hiddenModelId);

        publishCustomerModule(owner);
        ResponseEntity<Map> overviewWithModule = exchange("/api/admin/system/ai", HttpMethod.GET, owner.systemToken(), null);
        assertThat((List<Map<String, Object>>) data(overviewWithModule).get("modules")).singleElement()
                .satisfies(module -> {
                    assertThat(module.get("code")).isEqualTo("customer");
                    assertThat(((List<?>) module.get("fields")).stream().map(String::valueOf)).contains("customer_name");
                    assertThat(((List<?>) module.get("actions")).stream().map(String::valueOf)).contains("LIST", "CREATE");
                });

        Map<String, Object> agentRequest = agent("customer_agent", "客户业务助理", grantId, "customer");
        ResponseEntity<Map> createdAgent = exchange("/api/admin/system/ai/agents", HttpMethod.POST,
                owner.systemToken(), agentRequest);
        assertThat(createdAgent.getStatusCode()).isEqualTo(HttpStatus.OK);
        long agentId = number(data(createdAgent).get("id"));
        int draftRevision = ((Number) data(createdAgent).get("draftRevision")).intValue();

        ResponseEntity<Map> preview = exchange("/api/admin/system/ai/agents/" + agentId + "/preview",
                HttpMethod.GET, owner.systemToken(), null);
        assertThat(data(preview)).containsEntry("valid", true).containsEntry("draftRevision", draftRevision);
        Map<String, Object> finalModel = (Map<String, Object>) data(preview).get("finalModel");
        assertThat(finalModel).containsEntry("code", "granted_chat").doesNotContainKeys("credentialRef", "credentialValue");
        List<Map<String, Object>> toolPreviews = (List<Map<String, Object>>) data(preview).get("tools");
        assertThat(toolPreviews).hasSize(2).allSatisfy(tool -> assertThat(tool.get("valid")).isEqualTo(true));
        assertThat((Boolean) toolPreviews.get(1).get("requiresConfirmation")).isTrue();

        ResponseEntity<Map> published = exchange("/api/admin/system/ai/agents/" + agentId + "/publish",
                HttpMethod.POST, owner.systemToken(), Map.of("expectedDraftRevision", draftRevision));
        assertThat(published.getStatusCode()).isEqualTo(HttpStatus.OK);
        long versionId = number(data(published).get("versionId"));
        assertThat(data(published)).containsEntry("versionNumber", 1);
        assertThat(count("ai_agent_version", "id=" + versionId + " and agent_id=" + agentId)).isOne();
        assertThat(count("ai_agent_publication", "agent_id=" + agentId + " and current_version_id=" + versionId)).isOne();
        assertThat(count("audit_event", "event_code='AI_AGENT_PUBLISHED' and object_id='" + versionId + "'")).isOne();
        String snapshot = jdbc.queryForObject("select snapshot_json from ai_agent_version where id=?", String.class, versionId);
        assertThat(snapshot).contains("authorizationSnapshot", "customer_name", "writeActionsRequireConfirmation")
                .doesNotContain("c45-model-secret-never-persisted");

        assertThatThrownBy(() -> jdbc.update("update ai_agent_version set version_number=99 where id=?", versionId))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ai_agent_version is immutable");
        assertThatThrownBy(() -> jdbc.update("delete from ai_agent_version where id=?", versionId))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ai_agent_version is immutable");

        Map<String, Object> excessive = agent("wildcard_agent", "越权范围草稿", grantId, "*");
        ResponseEntity<Map> excessiveDraft = exchange("/api/admin/system/ai/agents", HttpMethod.POST,
                owner.systemToken(), excessive);
        assertThat(excessiveDraft.getStatusCode()).isEqualTo(HttpStatus.OK);
        long excessiveId = number(data(excessiveDraft).get("id"));
        int excessiveRevision = ((Number) data(excessiveDraft).get("draftRevision")).intValue();
        ResponseEntity<Map> excessivePreview = exchange("/api/admin/system/ai/agents/" + excessiveId + "/preview",
                HttpMethod.GET, owner.systemToken(), null);
        assertThat(data(excessivePreview).get("valid")).isEqualTo(false);
        assertThat(((List<Map<String, Object>>) data(excessivePreview).get("issues")).stream()
                .map(issue -> issue.get("code"))).contains("AI_TOOL_SCOPE_EXCESSIVE");
        ResponseEntity<Map> blocked = exchange("/api/admin/system/ai/agents/" + excessiveId + "/publish",
                HttpMethod.POST, owner.systemToken(), Map.of("expectedDraftRevision", excessiveRevision));
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(blocked.getBody().get("code")).isEqualTo("AI_AGENT_PUBLICATION_INVALID");
        assertThat(count("ai_agent_version", "agent_id=" + excessiveId)).isZero();
        assertThat(count("audit_event", "event_code='AI_AGENT_PUBLISH_BLOCKED' and object_id='" + excessiveId
                + "' and result_code='AI_AGENT_PUBLICATION_INVALID'")).isOne();

        jdbc.update("update ai_system_model_grant set status='REVOKED', revoked_at=now(3) where id=?", grantId);
        ResponseEntity<Map> revokedPreview = exchange("/api/admin/system/ai/agents/" + agentId + "/preview",
                HttpMethod.GET, owner.systemToken(), null);
        assertThat(data(revokedPreview).get("valid")).isEqualTo(false);
        assertThat(((List<Map<String, Object>>) data(revokedPreview).get("issues")).stream()
                .map(issue -> issue.get("code"))).contains("AI_MODEL_NOT_GRANTED");
        assertThat((List<?>) data(exchange("/api/admin/system/ai", HttpMethod.GET, owner.systemToken(), null))
                .get("availableModels")).isEmpty();
    }

    private void publishCustomerModule(Session owner) {
        long groupId = number(data(exchange("/api/admin/module-config/groups", HttpMethod.POST, owner.systemToken(),
                Map.of("code", "sales", "name", "销售", "sortOrder", 10))).get("id"));
        ResponseEntity<Map> created = exchange("/api/admin/module-config/modules", HttpMethod.POST, owner.systemToken(),
                Map.of("groupId", groupId, "code", "customer", "name", "客户"));
        long moduleId = number(((Map<?, ?>) data(created).get("module")).get("id"));
        exchange("/api/admin/module-config/modules/" + moduleId + "/fields", HttpMethod.POST, owner.systemToken(),
                Map.of("code", "customer_name", "name", "客户名称", "fieldType", "TEXT", "required", false,
                        "sortOrder", 10, "config", Map.of("placeholder", "客户名称")));
        ResponseEntity<Map> check = exchange("/api/admin/module-config/modules/" + moduleId + "/publication-check",
                HttpMethod.GET, owner.systemToken(), null);
        assertThat(data(check).get("valid")).isEqualTo(true);
        exchange("/api/admin/module-config/modules/" + moduleId + "/publish", HttpMethod.POST, owner.systemToken(),
                Map.of("expectedDraftRevision", data(check).get("draftRevision")));
    }

    private Map<String, Object> model(String code, String name, String credentialRef) {
        return Map.ofEntries(
                Map.entry("code", code), Map.entry("name", name), Map.entry("provider", "LOCAL"),
                Map.entry("modelName", "qwen3-c45"), Map.entry("endpointUrl", "http://localhost:11434/v1"),
                Map.entry("credentialRef", credentialRef), Map.entry("capabilities", List.of("CHAT", "TOOL_CALLING")),
                Map.entry("dailyTokenLimit", 100000), Map.entry("concurrencyLimit", 4),
                Map.entry("logMasking", true), Map.entry("dataResidency", "LOCAL_ONLY"));
    }

    private Map<String, Object> agent(String code, String name, long grantId, String resourceId) {
        LinkedHashMap<String, Object> request = new LinkedHashMap<>();
        request.put("code", code);
        request.put("name", name);
        request.put("description", "C45 Agent 权限收敛验收");
        request.put("modelGrantId", grantId);
        request.put("systemPrompt", "你是客户业务助理，只能使用服务器收敛后的模块、字段和数据范围。");
        request.put("contextPolicy", Map.of("allowedEntryContexts", List.of("SYSTEM_ADMIN", "MODULE_PAGE"),
                "allowExternalData", false, "maskSensitiveData", true));
        request.put("confirmationPolicy", Map.of("writeActionsRequireConfirmation", true,
                "batchActionsRequireConfirmation", true, "showFieldLevelDiff", true));
        request.put("fallbackPolicy", Map.of("mode", "READ_ONLY", "userMessage", "已切换只读"));
        request.put("tools", "*".equals(resourceId) ? List.of(Map.of(
                "toolType", "QUERY", "resourceType", "MODULE", "resourceId", "*", "actionCode", "LIST",
                "fieldCodes", List.of("customer_name"), "requestedDataScope", "CURRENT", "requiresConfirmation", false))
                : List.of(
                Map.of("toolType", "QUERY", "resourceType", "MODULE", "resourceId", resourceId,
                        "actionCode", "LIST", "fieldCodes", List.of("customer_name"),
                        "requestedDataScope", "CURRENT", "requiresConfirmation", false),
                Map.of("toolType", "WRITE", "resourceType", "MODULE", "resourceId", resourceId,
                        "actionCode", "CREATE", "fieldCodes", List.of("customer_name"),
                        "requestedDataScope", "CURRENT", "requiresConfirmation", true)));
        return request;
    }

    private Session register(String username, String systemCode, String email) {
        ResponseEntity<Map> response = http.postForEntity("/api/auth/register", Map.of(
                "username", username, "password", "correct-password", "displayName", username,
                "email", email, "systemName", systemCode, "systemCode", systemCode), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> result = data(response);
        return new Session((String) ((Map<?, ?>) result.get("tokens")).get("accessToken"),
                number(result.get("systemId")), number(result.get("tenantId")));
    }

    private String login(String username, String password) {
        return (String) data(http.postForEntity("/api/auth/login",
                Map.of("username", username, "password", password), Map.class)).get("accessToken");
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

    private record Session(String systemToken, long systemId, long tenantId) {
    }
}
