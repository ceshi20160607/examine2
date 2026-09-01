package com.unique.unexamine.ai.manage;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class AiContextQueryHttpTest {
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
        registry.add("spring.http.client.factory", () -> "simple");
        registry.add("test.ai.secret", () -> "c46-model-secret-never-persisted");
    }

    @Autowired
    private MockMvc http;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void contextualQueryUsesRuntimeAuthorizationPersistsEvidenceAndDegradesWithoutFabrication() {
        Session owner = register("ai_query_owner", "ai_query_system", "ai-query-owner@example.com");
        String platformAdmin = login("admin", "123123aa");
        long modelId = number(data(exchange("/api/admin/platform/ai/models", HttpMethod.POST,
                platformAdmin, model("c46_local_query", null))).get("id"));
        long grantId = number(data(exchange("/api/admin/platform/ai/models/" + modelId + "/grants/" + owner.systemId(),
                HttpMethod.PUT, platformAdmin, Map.of("dailyTokenLimit", 40000, "concurrencyLimit", 2))).get("id"));

        publishCustomerModule(owner);
        long agentId = publishAgent(owner, grantId);
        ResponseEntity<Map> created = exchange("/api/runtime/modules/customer/records", HttpMethod.POST,
                owner.systemToken(), Map.of("title", "C46 可见客户", "recordNumber", "C46-001", "status", "ACTIVE",
                        "participantMemberIds", List.of(), "fields", Map.of("customer_name", "雪松科技")));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> ordinary = exchange("/api/runtime/modules/customer/records?page=1&pageSize=5",
                HttpMethod.GET, owner.systemToken(), null);
        assertThat(data(ordinary).get("total")).isEqualTo(1);

        ResponseEntity<Map> overview = exchange("/api/ai", HttpMethod.GET, owner.systemToken(), null);
        assertThat(overview.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<Map<String, Object>>) data(overview).get("agents")).singleElement().satisfies(agent -> {
            assertThat(agent.get("agentId")).isEqualTo((int) agentId);
            assertThat(((List<?>) agent.get("moduleCodes")).stream().map(String::valueOf).toList())
                    .containsExactly("customer");
        });

        ResponseEntity<Map> queried = exchange("/api/ai/queries", HttpMethod.POST, owner.systemToken(), query(
                agentId, null, "当前页面有多少条客户记录？", List.of("customer_name"), owner.tenantId(), "SYSTEM_AI"));
        assertThat(queried.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> queryResult = data(queried);
        assertThat(queryResult).containsEntry("outcome", "SUCCEEDED").containsEntry("retryable", false)
                .containsEntry("errorCode", null);
        assertThat(String.valueOf(queryResult.get("answer"))).contains("1 条");
        assertThat(String.valueOf(queryResult.get("metricDefinition"))).contains("普通业务列表同一套权限");
        List<Map<String, Object>> sources = (List<Map<String, Object>>) queryResult.get("sources");
        assertThat(sources).singleElement().satisfies(source -> {
            assertThat(source.get("title")).isEqualTo("C46 可见客户");
            assertThat(String.valueOf(((Map<?, ?>) source.get("fields")).get("customer_name")))
                    .isEqualTo("雪松科技");
            assertThat(String.valueOf(source.get("path"))).contains("workspace=runtime", "module=customer");
        });
        long conversationId = number(queryResult.get("conversationId"));
        long executionId = number(queryResult.get("executionId"));

        ResponseEntity<Map> persisted = exchange("/api/ai/conversations/" + conversationId,
                HttpMethod.GET, owner.systemToken(), null);
        assertThat(((List<?>) data(persisted).get("messages"))).hasSize(2);
        assertThat(count("ai_execution", "id=" + executionId + " and status='SUCCEEDED' and fallback_used=0")).isOne();
        assertThat(count("ai_execution_step", "execution_id=" + executionId + " and status='SUCCEEDED'")).isOne();
        String authorization = jdbc.queryForObject(
                "select authorization_snapshot_json from ai_execution where id=?", String.class, executionId);
        assertThat(authorization).contains("customer_name", "systemId", "tenantId", "dataScopes")
                .doesNotContain("c46-model-secret-never-persisted");

        ResponseEntity<Map> fieldRefused = exchange("/api/ai/queries", HttpMethod.POST, owner.systemToken(), query(
                agentId, conversationId, "告诉我客户密钥", List.of("customer_secret"), owner.tenantId(), "RIGHT_ASSISTANT"));
        assertThat(data(fieldRefused)).containsEntry("outcome", "REFUSED")
                .containsEntry("errorCode", "AI_QUERY_SCOPE_DENIED");
        assertThat((List<?>) data(fieldRefused).get("sources")).isEmpty();
        assertThat(String.valueOf(data(fieldRefused).get("answer"))).contains("不会提示字段值");

        ResponseEntity<Map> tenantRefused = exchange("/api/ai/queries", HttpMethod.POST, owner.systemToken(), query(
                agentId, null, "查询其他租户客户", List.of("customer_name"), owner.tenantId() + 999,
                "MODULE_PAGE"));
        assertThat(data(tenantRefused)).containsEntry("outcome", "REFUSED")
                .containsEntry("errorCode", "AI_QUERY_SCOPE_DENIED");
        assertThat(String.valueOf(data(tenantRefused).get("answer"))).contains("不会确认该数据是否存在");

        jdbc.update("update ai_model set endpoint_url='http://127.0.0.1:1/v1' where id=?", modelId);
        ResponseEntity<Map> degraded = exchange("/api/ai/queries", HttpMethod.POST, owner.systemToken(), query(
                agentId, null, "现在有多少客户？", List.of("customer_name"), owner.tenantId(), "MODULE_PAGE"));
        assertThat(data(degraded)).containsEntry("outcome", "DEGRADED")
                .containsEntry("errorCode", "AI_MODEL_UNAVAILABLE").containsEntry("retryable", true);
        assertThat((List<?>) data(degraded).get("sources")).isEmpty();
        assertThat(String.valueOf(data(degraded).get("answer"))).contains("未生成或猜测任何业务结果", "重试");

        assertThat(count("ai_conversation", "system_id=" + owner.systemId() + " and tenant_id=" + owner.tenantId()))
                .isEqualTo(3);
        assertThat(count("ai_execution", "status='REFUSED'")).isEqualTo(2);
        assertThat(count("ai_execution", "status='FAILED' and fallback_used=1 and error_code='AI_MODEL_UNAVAILABLE'"))
                .isOne();
        assertThat(count("audit_event", "event_code='AI_CONTEXT_QUERY_SUCCEEDED' and object_id='" + executionId + "'"))
                .isOne();
        assertThat(count("audit_event", "event_code='AI_CONTEXT_QUERY_REFUSED'")).isEqualTo(2);
        assertThat(count("audit_event", "event_code='AI_CONTEXT_QUERY_DEGRADED'")).isOne();
    }

    private long publishAgent(Session owner, long grantId) {
        LinkedHashMap<String, Object> request = new LinkedHashMap<>();
        request.put("code", "c46_customer_query");
        request.put("name", "C46 客户查询助理");
        request.put("description", "按页面上下文和当前用户权限查询客户");
        request.put("modelGrantId", grantId);
        request.put("systemPrompt", "只允许使用服务器收敛后的当前系统、租户、字段与数据范围回答，并提供可追溯来源。");
        request.put("contextPolicy", Map.of("allowedEntryContexts",
                List.of("SYSTEM_AI", "RIGHT_ASSISTANT", "MODULE_PAGE"),
                "allowExternalData", false, "maskSensitiveData", true));
        request.put("confirmationPolicy", Map.of("writeActionsRequireConfirmation", true,
                "batchActionsRequireConfirmation", true, "showFieldLevelDiff", true));
        request.put("fallbackPolicy", Map.of("mode", "TEMPLATE_QUERY",
                "userMessage", "模型不可用，未返回业务结果，请稍后重试"));
        request.put("tools", List.of(Map.of(
                "toolType", "QUERY", "resourceType", "MODULE", "resourceId", "customer",
                "actionCode", "LIST", "fieldCodes", List.of("customer_name"),
                "requestedDataScope", "CURRENT", "requiresConfirmation", false)));
        Map<String, Object> created = data(exchange("/api/admin/system/ai/agents", HttpMethod.POST,
                owner.systemToken(), request));
        long agentId = number(created.get("id"));
        exchange("/api/admin/system/ai/agents/" + agentId + "/publish", HttpMethod.POST, owner.systemToken(),
                Map.of("expectedDraftRevision", created.get("draftRevision")));
        return agentId;
    }

    private Map<String, Object> query(
            long agentId, Long conversationId, String question, List<String> fields,
            long tenantId, String entryType) {
        LinkedHashMap<String, Object> request = new LinkedHashMap<>();
        request.put("agentId", agentId);
        request.put("conversationId", conversationId);
        request.put("question", question);
        request.put("requestedFieldCodes", fields);
        request.put("requestedTenantId", tenantId);
        request.put("entryContext", Map.ofEntries(
                Map.entry("entryType", entryType), Map.entry("moduleCode", "customer"),
                Map.entry("sourcePath", "/systems/" + tenantId), Map.entry("lifecycleState", "ACTIVE"),
                Map.entry("tenantScope", "ALL"), Map.entry("search", ""), Map.entry("filters", List.of()),
                Map.entry("sortField", "updatedAt"), Map.entry("sortDirection", "DESC"), Map.entry("pageSize", 5)));
        return request;
    }

    private Map<String, Object> model(String code, String endpoint) {
        LinkedHashMap<String, Object> model = new LinkedHashMap<>();
        model.put("code", code);
        model.put("name", "C46 本地安全查询模型");
        model.put("provider", "LOCAL");
        model.put("modelName", "permission-query-v1");
        model.put("endpointUrl", endpoint);
        model.put("credentialRef", "property:test.ai.secret");
        model.put("capabilities", List.of("CHAT", "TOOL_CALLING"));
        model.put("dailyTokenLimit", 100000);
        model.put("concurrencyLimit", 4);
        model.put("logMasking", true);
        model.put("dataResidency", "LOCAL_ONLY");
        return model;
    }

    private void publishCustomerModule(Session owner) {
        long groupId = number(data(exchange("/api/admin/module-config/groups", HttpMethod.POST, owner.systemToken(),
                Map.of("code", "sales", "name", "销售", "sortOrder", 10))).get("id"));
        long moduleId = number(((Map<?, ?>) data(exchange("/api/admin/module-config/modules", HttpMethod.POST,
                owner.systemToken(), Map.of("groupId", groupId, "code", "customer", "name", "客户")))
                .get("module")).get("id"));
        exchange("/api/admin/module-config/modules/" + moduleId + "/fields", HttpMethod.POST, owner.systemToken(),
                Map.of("code", "customer_name", "name", "客户名称", "fieldType", "TEXT", "required", false,
                        "searchable", true, "sortOrder", 10, "config", Map.of("placeholder", "客户名称")));
        Map<String, Object> check = data(exchange("/api/admin/module-config/modules/" + moduleId + "/publication-check",
                HttpMethod.GET, owner.systemToken(), null));
        exchange("/api/admin/module-config/modules/" + moduleId + "/publish", HttpMethod.POST, owner.systemToken(),
                Map.of("expectedDraftRevision", check.get("draftRevision")));
    }

    private Session register(String username, String systemCode, String email) {
        ResponseEntity<Map> response = exchange("/api/auth/register", HttpMethod.POST, null, Map.of(
                "username", username, "password", "correct-password", "displayName", username,
                "email", email, "systemName", systemCode, "systemCode", systemCode));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> result = data(response);
        return new Session((String) ((Map<?, ?>) result.get("tokens")).get("accessToken"),
                number(result.get("systemId")), number(result.get("tenantId")));
    }

    private String login(String username, String password) {
        return (String) data(exchange("/api/auth/login", HttpMethod.POST, null,
                Map.of("username", username, "password", password))).get("accessToken");
    }

    private ResponseEntity<Map> exchange(String path, HttpMethod method, String token, Object body) {
        try {
            var request = MockMvcRequestBuilders.request(method, path).contentType(MediaType.APPLICATION_JSON);
            if (token != null) request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            if (body != null) request.content(objectMapper.writeValueAsBytes(body));
            var response = http.perform(request).andReturn().getResponse();
            Map payload = objectMapper.readValue(response.getContentAsByteArray(), Map.class);
            return new ResponseEntity<>(payload, HttpStatus.valueOf(response.getStatus()));
        } catch (Exception exception) {
            throw new IllegalStateException("HTTP test request failed: " + method + " " + path, exception);
        }
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
