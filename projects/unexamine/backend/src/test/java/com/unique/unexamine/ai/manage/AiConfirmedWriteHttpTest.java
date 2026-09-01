package com.unique.unexamine.ai.manage;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class AiConfirmedWriteHttpTest {
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
        registry.add("test.ai.secret", () -> "c47-model-secret-never-persisted");
    }

    @Autowired
    private MockMvc http;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void candidateRequiresConfirmationRechecksRuntimeRulesAndPersistsManualDegradationAudit() {
        Session owner = register("ai_write_owner", "ai_write_system", "ai-write-owner@example.com");
        String platformAdmin = login("admin", "123123aa");
        long modelId = number(data(exchange("/api/admin/platform/ai/models", HttpMethod.POST,
                platformAdmin, model("c47_local_write"))).get("id"));
        long grantId = number(data(exchange("/api/admin/platform/ai/models/" + modelId + "/grants/" + owner.systemId(),
                HttpMethod.PUT, platformAdmin, Map.of("dailyTokenLimit", 40000, "concurrencyLimit", 2))).get("id"));
        publishCustomerModule(owner);
        long agentId = publishAgent(owner, grantId);

        Map<String, Object> overview = data(exchange("/api/ai/writes", HttpMethod.GET, owner.systemToken(), null));
        assertThat((List<Map<String, Object>>) overview.get("agents")).singleElement().satisfies(agent -> {
            assertThat(agent.get("agentId")).isEqualTo((int) agentId);
            assertThat(agent.get("moduleCodes")).isEqualTo(List.of("customer"));
        });

        Map<String, Object> candidate = data(exchange("/api/ai/writes/recognize", HttpMethod.POST,
                owner.systemToken(), recognize(agentId, owner.tenantId(),
                        "标题：C47 待确认客户；记录编号：C47-001；客户名称：星河科技")));
        assertThat(candidate).containsEntry("outcome", "READY")
                .containsEntry("status", "PENDING_CONFIRMATION")
                .containsEntry("confirmationRequired", true).containsEntry("businessWritten", false);
        assertThat(candidate.get("proposedTitle")).isEqualTo("C47 待确认客户");
        assertThat((List<Map<String, Object>>) candidate.get("fields")).singleElement().satisfies(field -> {
            assertThat(field).containsEntry("code", "customer_name").containsEntry("value", "星河科技")
                    .containsEntry("recognized", true).containsEntry("writable", true);
            assertThat(((Number) field.get("confidence")).doubleValue()).isGreaterThan(0.9);
        });
        long pendingId = number(candidate.get("pendingWriteId"));
        long executionId = number(candidate.get("executionId"));
        assertThat(count("biz_record", "system_id=" + owner.systemId())).isZero();
        assertThat(count("ai_pending_write", "id=" + pendingId + " and status='PENDING_CONFIRMATION'"
                + " and confirmed_by_account_id is null")).isOne();
        assertThat(count("ai_execution", "id=" + executionId + " and status='WAITING_CONFIRMATION'"
                + " and fallback_used=0")).isOne();

        Map<String, Object> cancelledCandidate = data(exchange("/api/ai/writes/recognize", HttpMethod.POST,
                owner.systemToken(), recognize(agentId, owner.tenantId(),
                        "标题：取消候选；客户名称：不会写入")));
        Map<String, Object> cancelled = data(exchange("/api/ai/writes/" + cancelledCandidate.get("pendingWriteId")
                + "/cancel", HttpMethod.POST, owner.systemToken(),
                Map.of("expectedVersion", cancelledCandidate.get("version"))));
        assertThat(cancelled).containsEntry("status", "CANCELLED").containsEntry("businessWritten", false);
        assertThat(data(exchange("/api/ai/writes/" + cancelledCandidate.get("pendingWriteId"), HttpMethod.GET,
                owner.systemToken(), null))).containsEntry("outcome", "CANCELLED")
                .containsEntry("errorCode", "AI_WRITE_CANCELLED").containsEntry("businessWritten", false);
        assertThat(count("biz_record", "system_id=" + owner.systemId())).isZero();

        Map<String, Object> invalidCandidate = data(exchange("/api/ai/writes/recognize", HttpMethod.POST,
                owner.systemToken(), recognize(agentId, owner.tenantId(), "标题：缺少必填字段")));
        Map<String, Object> validation = data(exchange("/api/ai/writes/" + invalidCandidate.get("pendingWriteId")
                + "/confirm", HttpMethod.POST, owner.systemToken(), confirm(invalidCandidate, Map.of())));
        assertThat(validation).containsEntry("status", "VALIDATION_FAILED")
                .containsEntry("businessWritten", false).containsKey("errorCode");
        assertThat(count("biz_record", "system_id=" + owner.systemId())).isZero();
        Map<String, Object> rereadInvalid = data(exchange("/api/ai/writes/" + invalidCandidate.get("pendingWriteId"),
                HttpMethod.GET, owner.systemToken(), null));
        assertThat(rereadInvalid).containsEntry("outcome", "FAILED").containsKey("errorCode")
                .containsEntry("businessWritten", false);
        Map<String, Object> corrected = data(exchange("/api/ai/writes/" + invalidCandidate.get("pendingWriteId")
                + "/confirm", HttpMethod.POST, owner.systemToken(), confirm(rereadInvalid,
                Map.of("customer_name", "人工补充客户"))));
        assertThat(corrected).containsEntry("status", "CONFIRMED").containsEntry("businessWritten", true)
                .containsEntry("outcome", "SUCCEEDED");
        Map<String, Object> rereadConfirmed = data(exchange("/api/ai/writes/" + invalidCandidate.get("pendingWriteId"),
                HttpMethod.GET, owner.systemToken(), null));
        assertThat(rereadConfirmed).containsEntry("status", "CONFIRMED").containsEntry("outcome", "SUCCEEDED")
                .containsEntry("businessWritten", true).containsKey("recordId").containsKey("recordPath");
        long firstRecordId = number(corrected.get("recordId"));
        Map<String, Object> firstDetail = data(exchange("/api/runtime/modules/customer/records/" + firstRecordId,
                HttpMethod.GET, owner.systemToken(), null));
        assertThat(((Map<?, ?>) firstDetail.get("fields")).get("customer_name")).isEqualTo("人工补充客户");

        jdbc.update("update ai_model set endpoint_url='http://127.0.0.1:1/v1' where id=?", modelId);
        Map<String, Object> degraded = data(exchange("/api/ai/writes/recognize", HttpMethod.POST,
                owner.systemToken(), recognize(agentId, owner.tenantId(), "原始文件文本仍需保留")));
        assertThat(degraded).containsEntry("outcome", "DEGRADED").containsEntry("status", "NEEDS_MANUAL_INPUT")
                .containsEntry("errorCode", "AI_MODEL_UNAVAILABLE").containsEntry("businessWritten", false);
        assertThat(String.valueOf(degraded.get("message"))).contains("已保留原始输入", "未写入业务", "人工填写");
        Map<String, Object> manual = data(exchange("/api/ai/writes/" + degraded.get("pendingWriteId") + "/confirm",
                HttpMethod.POST, owner.systemToken(), confirm(degraded, Map.of("customer_name", "模型降级人工客户"))));
        assertThat(manual).containsEntry("businessWritten", true).containsEntry("status", "CONFIRMED");
        assertThat(count("biz_record", "system_id=" + owner.systemId() + " and tenant_id=" + owner.tenantId()))
                .isEqualTo(2);

        Map<String, Object> crossTenant = data(exchange("/api/ai/writes/recognize", HttpMethod.POST,
                owner.systemToken(), recognize(agentId, owner.tenantId() + 999, "客户名称：越权客户")));
        assertThat(crossTenant).containsEntry("outcome", "REFUSED").containsEntry("status", "REFUSED")
                .containsEntry("errorCode", "AI_WRITE_SCOPE_DENIED").containsEntry("businessWritten", false);
        assertThat(count("biz_record", "system_id=" + owner.systemId())).isEqualTo(2);

        String pendingPayload = jdbc.queryForObject(
                "select concat(proposed_payload_json, preview_json) from ai_pending_write where id=?",
                String.class, pendingId);
        assertThat(pendingPayload).contains("C47 待确认客户", "星河科技", "customer_name", "inputText")
                .doesNotContain("c47-model-secret-never-persisted");
        String authorization = jdbc.queryForObject(
                "select authorization_snapshot_json from ai_execution where id=?", String.class, executionId);
        assertThat(authorization).contains("systemId", "tenantId", "CREATE", "customer_name", "requiresConfirmation")
                .doesNotContain("c47-model-secret-never-persisted");
        assertThat(count("ai_execution_step", "step_type='STRUCTURED_RECOGNITION' and status='SUCCEEDED'"))
                .isGreaterThanOrEqualTo(4);
        assertThat(count("ai_execution_step", "step_type='CONFIRMED_WRITE' and status='SUCCEEDED'"))
                .isEqualTo(2);
        assertThat(count("audit_event", "event_code='AI_WRITE_CANDIDATE_READY'")).isGreaterThanOrEqualTo(3);
        assertThat(count("audit_event", "event_code='AI_WRITE_CANDIDATE_DEGRADED'")).isOne();
        assertThat(count("audit_event", "event_code='AI_WRITE_CONFIRMED'")).isEqualTo(2);
        assertThat(count("audit_event", "event_code='AI_WRITE_CANCELLED'")).isOne();
        assertThat(count("audit_event", "event_code='AI_WRITE_VALIDATION_FAILED'")).isOne();
        assertThat(count("audit_event", "event_code='AI_WRITE_CANDIDATE_REFUSED'")).isOne();
    }

    private Map<String, Object> recognize(long agentId, long tenantId, String text) {
        return Map.of("agentId", agentId, "moduleCode", "customer", "inputText", text,
                "sourceType", "TEXT", "requestedTenantId", tenantId, "entryType", "MODULE_PAGE");
    }

    private Map<String, Object> confirm(Map<String, Object> candidate, Map<String, Object> fields) {
        return Map.of("expectedVersion", candidate.get("version"), "confirmed", true,
                "title", "C47 最终确认客户", "recordNumber", "C47-FINAL-" + candidate.get("pendingWriteId"),
                "status", "ACTIVE", "participantMemberIds", List.of(), "fields", fields);
    }

    private long publishAgent(Session owner, long grantId) {
        LinkedHashMap<String, Object> request = new LinkedHashMap<>();
        request.put("code", "c47_customer_writer");
        request.put("name", "C47 客户创建助理");
        request.put("description", "识别客户字段并等待人工确认后写入");
        request.put("modelGrantId", grantId);
        request.put("systemPrompt", "只生成结构化候选，必须由当前用户逐字段核对并明确确认后才能调用真实业务 CREATE。"
                + "模型失败保留输入并进入人工填写，任何分支都不得绕过权限和业务规则。");
        request.put("contextPolicy", Map.of("allowedEntryContexts",
                List.of("SYSTEM_AI", "RIGHT_ASSISTANT", "MODULE_PAGE"),
                "allowExternalData", false, "maskSensitiveData", true));
        request.put("confirmationPolicy", Map.of("writeActionsRequireConfirmation", true,
                "batchActionsRequireConfirmation", true, "showFieldLevelDiff", true));
        request.put("fallbackPolicy", Map.of("mode", "TEMPLATE_QUERY",
                "userMessage", "模型不可用，保留输入并允许人工填写"));
        request.put("tools", List.of(Map.of(
                "toolType", "WRITE", "resourceType", "MODULE", "resourceId", "customer",
                "actionCode", "CREATE", "fieldCodes", List.of("customer_name"),
                "requestedDataScope", "CURRENT", "requiresConfirmation", true)));
        Map<String, Object> created = data(exchange("/api/admin/system/ai/agents", HttpMethod.POST,
                owner.systemToken(), request));
        long agentId = number(created.get("id"));
        exchange("/api/admin/system/ai/agents/" + agentId + "/publish", HttpMethod.POST, owner.systemToken(),
                Map.of("expectedDraftRevision", created.get("draftRevision")));
        return agentId;
    }

    private void publishCustomerModule(Session owner) {
        long groupId = number(data(exchange("/api/admin/module-config/groups", HttpMethod.POST, owner.systemToken(),
                Map.of("code", "sales", "name", "销售", "sortOrder", 10))).get("id"));
        long moduleId = number(((Map<?, ?>) data(exchange("/api/admin/module-config/modules", HttpMethod.POST,
                owner.systemToken(), Map.of("groupId", groupId, "code", "customer", "name", "客户")))
                .get("module")).get("id"));
        exchange("/api/admin/module-config/modules/" + moduleId + "/fields", HttpMethod.POST, owner.systemToken(),
                Map.of("code", "customer_name", "name", "客户名称", "fieldType", "TEXT", "required", true,
                        "searchable", true, "sortOrder", 10, "config", Map.of("placeholder", "客户名称")));
        Map<String, Object> check = data(exchange("/api/admin/module-config/modules/" + moduleId
                + "/publication-check", HttpMethod.GET, owner.systemToken(), null));
        exchange("/api/admin/module-config/modules/" + moduleId + "/publish", HttpMethod.POST, owner.systemToken(),
                Map.of("expectedDraftRevision", check.get("draftRevision")));
    }

    private Map<String, Object> model(String code) {
        LinkedHashMap<String, Object> model = new LinkedHashMap<>();
        model.put("code", code);
        model.put("name", "C47 本地安全识别模型");
        model.put("provider", "LOCAL");
        model.put("modelName", "confirmed-write-v1");
        model.put("endpointUrl", null);
        model.put("credentialRef", "property:test.ai.secret");
        model.put("capabilities", List.of("CHAT", "TOOL_CALLING"));
        model.put("dailyTokenLimit", 100000);
        model.put("concurrencyLimit", 4);
        model.put("logMasking", true);
        model.put("dataResidency", "LOCAL_ONLY");
        return model;
    }

    private Session register(String username, String systemCode, String email) {
        Map<String, Object> result = data(exchange("/api/auth/register", HttpMethod.POST, null, Map.of(
                "username", username, "password", "correct-password", "displayName", username,
                "email", email, "systemName", systemCode, "systemCode", systemCode)));
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
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
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
