package com.unique.unexamine.runtimedata.manage;

import com.unique.unexamine.backgroundjobs.manage.BackgroundJobWorker;
import com.unique.unexamine.foundation.manage.control.FoundationCommandExtension;
import com.unique.unexamine.foundation.manage.control.FoundationExtensionDispatcher;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "unexamine.actions.outbox.poll-delay-ms=3600000")
@Import(RuntimeDataHttpTest.ActionDeliveryFixture.class)
class RuntimeDataHttpTest {
    static final AtomicInteger deliveryAttempts = new AtomicInteger();
    static final java.util.concurrent.atomic.AtomicBoolean failBoundDelivery = new java.util.concurrent.atomic.AtomicBoolean();

    @TestConfiguration
    static class ActionDeliveryFixture {
        @Bean
        @org.springframework.core.annotation.Order(Integer.MAX_VALUE)
        FoundationCommandExtension actionDeliveryFixture() {
            return event -> {
                if (event.getPayloadJson().contains("bound-delivery") && failBoundDelivery.compareAndSet(true, false)) {
                    throw new IllegalStateException("controlled failure after the Flow insert");
                }
                if ("BUSINESS_ACTION_COMPLETED".equals(event.getEventType())
                        && event.getPayloadJson().contains("deliver-once")) {
                    if (deliveryAttempts.incrementAndGet() == 1) throw new IllegalStateException("controlled delivery failure");
                }
            };
        }
    }

    @Autowired
    private FoundationExtensionDispatcher actionDispatcher;
    @Autowired
    private RuntimeDataService runtimeActions;
    @Autowired
    private com.unique.unexamine.authorization.manage.PermissionResolver testActionPermissions;
    @Autowired
    private com.unique.unexamine.foundation.manage.control.BusinessActionApprovalCoordinator approvalActions;

    @Test
    void publishedStructuredContentValidatesDefaultsSanitizesMarkupAndRollsBackBadUpdates() {
        Session owner = register("content_owner", "content-system", "content-owner@example.com");
        long groupId = number(data(exchange("/api/admin/module-config/groups", HttpMethod.POST, owner.token(),
                Map.of("code", "content", "name", "内容管理", "sortOrder", 10))).get("id"));
        long moduleId = number(map(data(exchange("/api/admin/module-config/modules", HttpMethod.POST, owner.token(),
                Map.of("groupId", groupId, "code", "content_customer", "name", "结构化内容客户"))).get("module")).get("id"));
        createField(owner, moduleId, "name", "客户名称", "TEXT", true, 10, Map.of());
        var rich = exchange("/api/admin/module-config/modules/" + moduleId + "/fields", HttpMethod.POST, owner.token(),
                Map.of("code", "notes", "name", "合作说明", "fieldType", "RICH_TEXT", "required", true,
                        "searchable", true, "sortOrder", 20, "config", Map.of("minLength", 2, "maxLength", 80,
                                "richTextMode", "FULL", "defaultValue", "<b>默认说明</b>")));
        assertThat(rich.getStatusCode()).as("rich draft: %s", rich.getBody()).isEqualTo(HttpStatus.OK);
        Map<String, Object> schema = Map.of("type", "OBJECT", "allowExtraProperties", false, "properties", List.of(
                Map.of("key", "company", "label", "公司", "required", true, "schema", Map.of("type", "STRING", "minLength", 2)),
                Map.of("key", "budget", "label", "预算", "schema", Map.of("type", "NUMBER", "minimum", 0)),
                Map.of("key", "contacts", "label", "联系人", "schema", Map.of("type", "ARRAY", "maxItems", 2,
                        "items", Map.of("type", "OBJECT", "properties", List.of(
                                Map.of("key", "name", "label", "姓名", "required", true, "schema", Map.of("type", "STRING")),
                                Map.of("key", "active", "label", "启用", "required", true, "schema", Map.of("type", "BOOLEAN"))))))));
        var badDraft = exchange("/api/admin/module-config/modules/" + moduleId + "/fields", HttpMethod.POST, owner.token(),
                Map.of("code", "bad_default", "name", "错误默认内容", "fieldType", "JSON", "required", false,
                        "sortOrder", 30, "config", Map.of("jsonSchema", schema, "defaultValue", Map.of())));
        assertThat(badDraft.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(badDraft.getBody()).containsEntry("code", "FIELD_CONFIG_INVALID");
        createField(owner, moduleId, "profile", "客户资料", "JSON", true, 30,
                Map.of("jsonSchema", schema, "defaultValue", Map.of("company", "默认公司")));
        int revision = jdbc.queryForObject("select draft_revision from un_cfg_module where id=?", Integer.class, moduleId);
        assertThat(exchange("/api/admin/module-config/modules/" + moduleId + "/publish", HttpMethod.POST, owner.token(),
                Map.of("expectedDraftRevision", revision)).getStatusCode()).isEqualTo(HttpStatus.OK);
        String path = "/api/runtime/modules/content_customer/records";
        var defaults = exchange(path, HttpMethod.POST, owner.token(), Map.of("participantMemberIds", List.of(), "title", "默认客户", "fields", Map.of("name", "默认客户")));
        assertThat(defaults.getStatusCode()).as("defaults: %s", defaults.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(map(data(defaults).get("fields"))).containsEntry("notes", "<b>默认说明</b>")
                .containsEntry("profile", Map.of("company", "默认公司"));
        var profile = Map.of("company", "实际公司", "budget", 12.5, "contacts", List.of(Map.of("name", "张三", "active", false)));
        var created = exchange(path, HttpMethod.POST, owner.token(), Map.of("participantMemberIds", List.of(), "title", "实际客户", "fields", Map.of(
                "name", "实际客户", "notes", "<p><b>合作</b>方案<script>alert(1)</script><img src=x onerror=steal()></p>", "profile", profile)));
        assertThat(created.getStatusCode()).as("create: %s", created.getBody()).isEqualTo(HttpStatus.OK);
        long id = number(data(created).get("id"));
        var values = map(data(created).get("fields"));
        assertThat(values.get("notes").toString()).contains("<b>合作</b>方案").doesNotContain("script", "onerror", "alert", "<img");
        assertThat(values.get("profile")).isEqualTo(profile);
        assertThat(jdbc.queryForObject("select value_type from un_biz_record_value where record_id=? and field_code='notes'", String.class, id)).isEqualTo("RICH_TEXT");
        assertThat(jdbc.queryForObject("select value_json from un_biz_record_value where record_id=? and field_code='profile'", String.class, id)).contains("false", "12.5");
        assertThat(number(data(exchangeEncoded(path + "?search=" + URLEncoder.encode("合作方案", StandardCharsets.UTF_8), HttpMethod.GET, owner.token(), null)).get("total"))).isOne();
        String filledFilter = URLEncoder.encode("[{\"fieldCode\":\"profile\",\"operator\":\"NOT_EMPTY\"}]", StandardCharsets.UTF_8);
        assertThat(number(data(exchangeEncoded(path + "?filters=" + filledFilter, HttpMethod.GET, owner.token(), null)).get("total"))).isEqualTo(2);
        assertThat(exchange(path + "?sortField=profile", HttpMethod.GET, owner.token(), null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        String invalidFilter = URLEncoder.encode("[{\"fieldCode\":\"profile\",\"operator\":\"EQ\",\"value\":\"x\"}]", StandardCharsets.UTF_8);
        assertThat(exchangeEncoded(path + "?filters=" + invalidFilter, HttpMethod.GET, owner.token(), null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        int version = ((Number) data(created).get("version")).intValue();
        for (Map<String, Object> invalidFields : List.<Map<String, Object>>of(
                Map.of("name", "不应落库", "notes", "<p>有效说明</p>", "profile", Map.of()),
                Map.of("name", "不应落库", "notes", "<p>有效说明</p>", "profile", Map.of("company", "公司", "budget", -1)),
                Map.of("name", "不应落库", "notes", "<script>alert(1)</script>", "profile", profile))) {
            var invalid = exchange(path + "/" + id, HttpMethod.PUT, owner.token(), Map.of("participantMemberIds", List.of(), "title", "不应落库", "version", version, "fields", invalidFields));
            assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(invalid.getBody()).containsEntry("code", "FIELD_VALIDATION_FAILED");
        }
        assertThat(jdbc.queryForObject("select version from un_biz_record where id=?", Integer.class, id)).isEqualTo(version);
        assertThat(count("un_audit_event", "event_code='BUSINESS_RECORD_UPDATED' and object_id='" + id + "'")).isZero();
        var updatedProfile = Map.of("company", "更新公司", "contacts", List.of(Map.of("name", "李四", "active", true)));
        var updated = exchange(path + "/" + id, HttpMethod.PUT, owner.token(), Map.of("participantMemberIds", List.of(), "title", "更新客户", "version", version,
                "fields", Map.of("name", "更新客户", "notes", "<h2>更新方案</h2>", "profile", updatedProfile)));
        assertThat(updated.getStatusCode()).as("update: %s", updated.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(map(data(exchange(path + "/" + id, HttpMethod.GET, owner.token(), null)).get("fields"))).containsEntry("profile", updatedProfile);
        var timeline = exchange(path + "/" + id + "/timeline", HttpMethod.GET, owner.token(), null);
        assertThat(timeline.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(timeline.getBody().toString()).contains("RICH_TEXT", "JSON", "合作说明", "客户资料", "张三", "李四").doesNotContain("onerror", "alert(1)");
        assertThat(count("un_biz_record", "module_id=" + moduleId)).isEqualTo(2);
    }

    @Test
    void actionBoundaryUsesRealApiAndReplaysCreateUpdateDeleteWithoutDuplicateWrites() {
        Session owner = register("action_owner", "action-system", "action-owner@example.com");
        long moduleId = createAndPublishCustomerModule(owner);
        String path = "/api/runtime/modules/customer/records";
        Map<String, Object> body = Map.of("title", "Action customer", "participantMemberIds", List.of(),
                "fields", Map.of("customer_name", "Action customer", "level", "A"));
        var first = actionRequest(path, HttpMethod.POST, owner, "action-create", body);
        assertThat(first.getStatusCode()).as("create: %s", first.getBody()).isEqualTo(HttpStatus.OK);
        long id = number(data(first).get("id"));
        var reordered = new java.util.LinkedHashMap<String, Object>();
        reordered.put("fields", new java.util.LinkedHashMap<>(Map.of("level", "A", "customer_name", "Action customer")));
        reordered.put("participantMemberIds", List.of());
        reordered.put("title", "Action customer");
        assertThat(data(actionRequest(path, HttpMethod.POST, owner, "action-create", reordered))).isEqualTo(data(first));
        var commandPayload = new java.util.LinkedHashMap<>(body);
        commandPayload.put("moduleCode", "customer");
        var commandReplay = exchange("/api/admin/system/foundation-control/commands", HttpMethod.POST, owner.token(),
                Map.of("idempotencyKey", "action-create", "applicationCode", "runtime",
                        "operationCode", "RUNTIME_RECORD_CREATE_DIRECT", "payload", commandPayload));
        assertThat(commandReplay.getStatusCode()).as("command: %s", commandReplay.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(data(commandReplay)).containsEntry("replayed", true).containsEntry("resultReference", "business-record:" + id);
        assertThat(count("un_biz_record", "module_id=" + moduleId)).isOne();
        assertThat(count("un_flow_instance", "system_id=" + owner.systemId())).isZero();
        assertThat(count("un_audit_event", "event_code='BUSINESS_RECORD_CREATED' and object_id='" + id + "'" )).isOne();
        assertThat(count("un_core_event_outbox", "aggregate_type='BUSINESS_ACTION' and system_id=" + owner.systemId())).isOne();
        assertThat(data(exchange(path + "/" + id, HttpMethod.GET, owner.token(), null)).get("title")).isEqualTo("Action customer");

        var changed = new java.util.LinkedHashMap<>(body);
        changed.put("title", "Different request");
        assertThat(actionRequest(path, HttpMethod.POST, owner, "action-create", changed).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        var update = new java.util.LinkedHashMap<>(body);
        update.put("title", "Updated customer");
        update.put("version", data(first).get("version"));
        var updated = actionRequest(path + "/" + id, HttpMethod.PUT, owner, "action-update", update);
        assertThat(updated.getStatusCode()).as("update: %s", updated.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(data(actionRequest(path + "/" + id, HttpMethod.PUT, owner, "action-update", update))).isEqualTo(data(updated));
        assertThat(actionRequest(path + "/" + id, HttpMethod.PUT, owner, "action-stale", update).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(count("un_core_idempotency_record", "idempotency_key='action-stale'")).isZero();
        var deletion = Map.of("version", data(updated).get("version"), "reason", "remove customer");
        var deleted = actionRequest(path + "/" + id + "/delete", HttpMethod.POST, owner, "action-delete", deletion);
        assertThat(deleted.getStatusCode()).as("delete: %s", deleted.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(data(actionRequest(path + "/" + id + "/delete", HttpMethod.POST, owner, "action-delete", deletion))).isEqualTo(data(deleted));
        assertThat(jdbc.queryForObject("select deleted from un_biz_record where id=?", Boolean.class, id)).isTrue();
        assertThat(count("un_core_event_outbox", "aggregate_type='BUSINESS_ACTION' and system_id=" + owner.systemId())).isEqualTo(3);

        commandPayload.put("title", "");
        var invalidCommand = exchange("/api/admin/system/foundation-control/commands", HttpMethod.POST, owner.token(),
                Map.of("idempotencyKey", "action-invalid", "applicationCode", "runtime",
                        "operationCode", "RUNTIME_RECORD_CREATE_DIRECT", "payload", commandPayload));
        assertThat(invalidCommand.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(count("un_core_idempotency_record", "idempotency_key='action-invalid'")).isZero();
        commandPayload.put("title", "Invalid owner");
        commandPayload.put("ownerMemberId", "not-a-number");
        var invalidOwner = exchange("/api/admin/system/foundation-control/commands", HttpMethod.POST, owner.token(),
                Map.of("idempotencyKey", "action-invalid-owner", "applicationCode", "runtime",
                        "operationCode", "RUNTIME_RECORD_CREATE_DIRECT", "payload", commandPayload));
        assertThat(invalidOwner.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(count("un_biz_record", "module_id=" + moduleId)).isOne();

        // Revoking membership must also block reading a cached result.
        jdbc.update("update un_sys_tenant_member set status='DISABLED' where tenant_id=?", owner.tenantId());
        assertThat(actionRequest(path, HttpMethod.POST, owner, "action-create", body).getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    void actionFailureRollsBackPartialDataResultAndEventAndAllowsCorrectedRetry() {
        Session owner = register("action_rollback", "action-rollback", "action-rollback@example.com");
        long moduleId = createAndPublishCustomerModule(owner);
        String path = "/api/runtime/modules/customer/records";
        Map<String, Object> body = Map.of("title", "write-failure", "participantMemberIds", List.of(),
                "fields", Map.of("customer_name", "write-failure", "level", "A"));
        jdbc.execute("create trigger b01_fail_value before insert on un_biz_record_value for each row "
                + "begin if NEW.value_text='write-failure' then signal sqlstate '45000' set message_text='controlled value failure'; end if; end");
        try {
            assertThat(actionRequest(path, HttpMethod.POST, owner, "action-rollback", body).getStatusCode().is5xxServerError()).isTrue();
            assertThat(count("un_biz_record", "module_id=" + moduleId)).isZero();
            assertThat(count("un_core_idempotency_record", "idempotency_key='action-rollback'")).isZero();
            assertThat(count("un_core_event_outbox", "aggregate_type='BUSINESS_ACTION' and system_id=" + owner.systemId())).isZero();
        } finally {
            jdbc.execute("drop trigger b01_fail_value");
        }
        assertThat(actionRequest(path, HttpMethod.POST, owner, "action-rollback", body).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(count("un_biz_record", "module_id=" + moduleId)).isOne();
    }

    @Test
    void configuredFieldDefaultIsValidatedAndStoredWhenCreateOmitsTheField() {
        Session owner = register("default_owner", "default-system", "default-owner@example.com");
        long groupId = number(data(exchange("/api/admin/module-config/groups", HttpMethod.POST, owner.token(),
                Map.of("code", "customers", "name", "客户管理", "sortOrder", 10))).get("id"));
        Map<String, Object> moduleDraft = data(exchange("/api/admin/module-config/modules", HttpMethod.POST,
                owner.token(), Map.of("groupId", groupId, "code", "default_customer", "name", "客户档案")));
        long moduleId = number(((Map<?, ?>) moduleDraft.get("module")).get("id"));
        createField(owner, moduleId, "customer_name", "客户名称", "TEXT", true, 10, Map.of());
        createField(owner, moduleId, "customer_status", "客户状态", "STATUS", true, 20,
                Map.of("defaultValue", "active", "options", List.of(
                        Map.of("value", "active", "label", "启用", "status", "ACTIVE"),
                        Map.of("value", "inactive", "label", "停用", "status", "ACTIVE"))));
        createField(owner, moduleId, "customer_score", "客户评分", "NUMBER", false, 30,
                Map.of("defaultValue", 12.5));
        createField(owner, moduleId, "next_contact", "下次联系日期", "DATE", false, 40,
                Map.of("defaultValue", "2026-10-01"));
        createField(owner, moduleId, "priority_customer", "重点客户", "BOOLEAN", false, 50,
                Map.of("defaultValue", false));
        int revision = jdbc.queryForObject("select draft_revision from un_cfg_module where id=?", Integer.class, moduleId);
        assertThat(exchange("/api/admin/module-config/modules/" + moduleId + "/publish", HttpMethod.POST,
                owner.token(), Map.of("expectedDraftRevision", revision)).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> created = exchange("/api/runtime/modules/default_customer/records", HttpMethod.POST,
                owner.token(), Map.of("title", "默认值客户", "participantMemberIds", List.of(),
                        "fields", Map.of("customer_name", "默认值客户")));
        assertThat(created.getStatusCode()).as("create: %s", created.getBody()).isEqualTo(HttpStatus.OK);
        Map<String, Object> fields = map(data(created).get("fields"));
        assertThat(fields).containsEntry("customer_status", "active")
                .containsEntry("customer_score", 12.5)
                .containsEntry("next_contact", "2026-10-01")
                .containsEntry("priority_customer", false);
        long recordId = number(data(created).get("id"));
        assertThat(jdbc.queryForObject("select value_text from un_biz_record_value where record_id=? and field_code='customer_status'",
                String.class, recordId)).isEqualTo("active");
        assertThat(jdbc.queryForObject("select value_number from un_biz_record_value where record_id=? and field_code='customer_score'",
                java.math.BigDecimal.class, recordId)).isEqualByComparingTo("12.5");
        assertThat(jdbc.queryForObject("select value_date from un_biz_record_value where record_id=? and field_code='next_contact'",
                java.time.LocalDate.class, recordId)).isEqualTo(java.time.LocalDate.of(2026, 10, 1));
        assertThat(jdbc.queryForObject("select value_boolean from un_biz_record_value where record_id=? and field_code='priority_customer'",
                Boolean.class, recordId)).isFalse();

        ResponseEntity<Map> invalid = exchange("/api/runtime/modules/default_customer/records", HttpMethod.POST,
                owner.token(), Map.of("title", "非法数字", "participantMemberIds", List.of(),
                        "fields", Map.of("customer_name", "非法数字", "customer_score", "not-a-number")));
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(invalid.getBody()).containsEntry("code", "FIELD_VALIDATION_FAILED");
        assertThat(count("un_biz_record", "module_id=" + moduleId)).isOne();
    }

    @Test
    void fieldUniqueSwitchReadOnlySystemValuesAndTypedHistoryUseThePublishedRuntimeChain() {
        Session owner = register("constraint_owner", "constraint-system", "constraint-owner@example.com");
        long groupId = number(data(exchange("/api/admin/module-config/groups", HttpMethod.POST, owner.token(),
                Map.of("code", "customers", "name", "客户管理", "sortOrder", 10))).get("id"));
        Map<String, Object> moduleDraft = data(exchange("/api/admin/module-config/modules", HttpMethod.POST,
                owner.token(), Map.of("groupId", groupId, "code", "constraint_customer", "name", "客户约束验收")));
        long moduleId = number(((Map<?, ?>) moduleDraft.get("module")).get("id"));
        createField(owner, moduleId, "customer_name", "客户名称", "TEXT", true, 10, Map.of());
        ResponseEntity<Map> uniqueField = exchange("/api/admin/module-config/modules/" + moduleId + "/fields",
                HttpMethod.POST, owner.token(), Map.of("code", "customer_code", "name", "客户编码",
                        "fieldType", "TEXT", "required", false, "uniqueValue", true,
                        "searchable", true, "sortOrder", 20, "config", Map.of()));
        assertThat(uniqueField.getStatusCode()).as("unique field: %s", uniqueField.getBody()).isEqualTo(HttpStatus.OK);
        long uniqueFieldId = number(data(uniqueField).get("id"));
        createField(owner, moduleId, "created_time", "创建时间", "SYSTEM_CREATED_AT", false, 30,
                Map.of("readOnly", true));
        createField(owner, moduleId, "created_user", "创建人", "SYSTEM_CREATED_BY", false, 40,
                Map.of("readOnly", true));

        long generatedIndexId = jdbc.queryForObject(
                "select id from un_cfg_query_index where module_id=? and code=?", Long.class,
                moduleId, "field_unique_" + uniqueFieldId);
        assertThat(jdbc.queryForObject("select unique_index from un_cfg_query_index where id=?", Boolean.class,
                generatedIndexId)).isTrue();
        assertThat(jdbc.queryForObject("select status from un_cfg_query_index where id=?", String.class,
                generatedIndexId)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("select field_id from un_cfg_query_index_field where query_index_id=?",
                Long.class, generatedIndexId)).isEqualTo(uniqueFieldId);

        int revision = jdbc.queryForObject("select draft_revision from un_cfg_module where id=?", Integer.class, moduleId);
        ResponseEntity<Map> published = exchange("/api/admin/module-config/modules/" + moduleId + "/publish",
                HttpMethod.POST, owner.token(), Map.of("expectedDraftRevision", revision));
        assertThat(published.getStatusCode()).as("publish: %s", published.getBody()).isEqualTo(HttpStatus.OK);
        String recordsPath = "/api/runtime/modules/constraint_customer/records";

        ResponseEntity<Map> missingRequired = exchange(recordsPath, HttpMethod.POST, owner.token(), Map.of(
                "title", "缺少名称", "participantMemberIds", List.of(), "fields", Map.of("customer_code", "C-000")));
        assertThat(missingRequired.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(missingRequired.getBody()).containsEntry("code", "FIELD_VALIDATION_FAILED");
        assertThat(missingRequired.getBody().get("message").toString()).contains("customer_name");

        ResponseEntity<Map> craftedReadOnly = exchange(recordsPath, HttpMethod.POST, owner.token(), Map.of(
                "title", "伪造系统时间", "participantMemberIds", List.of(), "fields", Map.of(
                        "customer_name", "伪造系统时间", "customer_code", "C-RO",
                        "created_time", "2000-01-01T00:00:00")));
        assertThat(craftedReadOnly.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(craftedReadOnly.getBody().get("message").toString()).contains("created_time", "只读字段由系统维护");
        assertThat(count("un_biz_record", "module_id=" + moduleId)).isZero();

        ResponseEntity<Map> first = exchange(recordsPath, HttpMethod.POST, owner.token(), Map.of(
                "title", "客户甲", "participantMemberIds", List.of(),
                "fields", Map.of("customer_name", "客户甲", "customer_code", "C-001")));
        assertThat(first.getStatusCode()).as("first: %s", first.getBody()).isEqualTo(HttpStatus.OK);
        long firstId = number(data(first).get("id"));
        assertThat(map(data(first).get("fields"))).containsKeys("created_time", "created_user");
        assertThat(map(data(first).get("fields")).get("created_time").toString()).isNotBlank();
        assertThat(number(map(data(first).get("fields")).get("created_user"))).isEqualTo(memberId(owner.systemId(), owner.accountId()));

        int successfulCreates = count("un_audit_event", "event_code='BUSINESS_RECORD_CREATED' and object_id='" + firstId + "'");
        ResponseEntity<Map> duplicateCreate = exchange(recordsPath, HttpMethod.POST, owner.token(), Map.of(
                "title", "客户乙重复", "participantMemberIds", List.of(),
                "fields", Map.of("customer_name", "客户乙重复", "customer_code", "C-001")));
        assertThat(duplicateCreate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicateCreate.getBody()).containsEntry("code", "UNIQUE_RULE_VIOLATION");
        assertThat(duplicateCreate.getBody().get("message").toString()).startsWith("customer_code:");
        assertThat(count("un_biz_record", "module_id=" + moduleId)).isOne();
        assertThat(count("un_audit_event", "event_code='BUSINESS_RECORD_CREATED' and object_id='" + firstId + "'"))
                .isEqualTo(successfulCreates);

        ResponseEntity<Map> second = exchange(recordsPath, HttpMethod.POST, owner.token(), Map.of(
                "title", "客户乙", "participantMemberIds", List.of(),
                "fields", Map.of("customer_name", "客户乙", "customer_code", "C-002")));
        assertThat(second.getStatusCode()).as("second: %s", second.getBody()).isEqualTo(HttpStatus.OK);
        long secondId = number(data(second).get("id"));
        int secondVersion = ((Number) data(second).get("version")).intValue();

        ResponseEntity<Map> duplicateUpdate = exchange(recordsPath + "/" + secondId, HttpMethod.PUT,
                owner.token(), Map.of("title", "不应保存", "participantMemberIds", List.of(), "version", secondVersion,
                        "fields", Map.of("customer_name", "不应保存", "customer_code", "C-001")));
        assertThat(duplicateUpdate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicateUpdate.getBody()).containsEntry("code", "UNIQUE_RULE_VIOLATION");
        assertThat(jdbc.queryForObject("select title from un_biz_record where id=?", String.class, secondId)).isEqualTo("客户乙");
        assertThat(jdbc.queryForObject("select value_text from un_biz_record_value where record_id=? and field_code='customer_code'",
                String.class, secondId)).isEqualTo("C-002");
        assertThat(jdbc.queryForObject("select version from un_biz_record where id=?", Integer.class, secondId)).isEqualTo(secondVersion);
        assertThat(count("un_audit_event", "event_code='BUSINESS_RECORD_UPDATED' and object_id='" + secondId + "' and result_code='SUCCESS'")).isZero();

        ResponseEntity<Map> readOnlyUpdate = exchange(recordsPath + "/" + secondId, HttpMethod.PUT,
                owner.token(), Map.of("title", "也不应保存", "participantMemberIds", List.of(), "version", secondVersion,
                        "fields", Map.of("customer_name", "也不应保存", "customer_code", "C-002",
                                "created_user", 999999L)));
        assertThat(readOnlyUpdate.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(jdbc.queryForObject("select title from un_biz_record where id=?", String.class, secondId)).isEqualTo("客户乙");

        ResponseEntity<Map> updated = exchange(recordsPath + "/" + secondId, HttpMethod.PUT,
                owner.token(), Map.of("title", "客户乙已更新", "participantMemberIds", List.of(), "version", secondVersion,
                        "fields", Map.of("customer_name", "客户乙已更新", "customer_code", "C-003")));
        assertThat(updated.getStatusCode()).as("update: %s", updated.getBody()).isEqualTo(HttpStatus.OK);
        ResponseEntity<Map> timeline = exchange(recordsPath + "/" + secondId + "/timeline",
                HttpMethod.GET, owner.token(), null);
        assertThat(timeline.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> latest = map(((List<?>) data(timeline).get("entries")).get(0));
        assertThat(latest).containsEntry("eventCode", "BUSINESS_RECORD_UPDATED");
        assertThat((List<?>) latest.get("changes")).anySatisfy(change -> assertThat(map(change))
                .containsEntry("fieldCode", "customer_code")
                .containsEntry("fieldName", "客户编码")
                .containsEntry("valueType", "TEXT")
                .containsEntry("beforeValue", "C-002")
                .containsEntry("afterValue", "C-003"));
    }

    @Test
    void publishedTextFamilyUsesStructuredConstraintsTypedStorageAndFieldLevelRollback() {
        Session owner = register("text_family_owner", "text-family-system", "text-family-owner@example.com");
        long groupId = number(data(exchange("/api/admin/module-config/groups", HttpMethod.POST, owner.token(),
                Map.of("code", "customers", "name", "客户管理", "sortOrder", 10))).get("id"));
        long moduleId = number(((Map<?, ?>) data(exchange("/api/admin/module-config/modules", HttpMethod.POST,
                owner.token(), Map.of("groupId", groupId, "code", "text_customer", "name", "文本客户")))
                .get("module")).get("id"));

        ResponseEntity<Map> invalidConfiguration = exchange(
                "/api/admin/module-config/modules/" + moduleId + "/fields", HttpMethod.POST, owner.token(),
                Map.of("code", "invalid_length", "name", "错误长度", "fieldType", "MULTILINE_TEXT",
                        "required", false, "sortOrder", 5, "config", Map.of("minLength", 20, "maxLength", 10)));
        assertThat(invalidConfiguration.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(invalidConfiguration.getBody()).containsEntry("code", "FIELD_CONFIG_INVALID");

        List<Map<String, Object>> definitions = List.of(
                Map.of("code", "notes", "name", "跟进说明", "fieldType", "MULTILINE_TEXT", "required", true,
                        "searchable", true, "sortOrder", 10,
                        "config", Map.of("minLength", 5, "maxLength", 80)),
                Map.of("code", "phone", "name", "联系电话", "fieldType", "PHONE", "required", true,
                        "searchable", true, "sortOrder", 20,
                        "config", Map.of("minLength", 11, "maxLength", 15)),
                Map.of("code", "email", "name", "工作邮箱", "fieldType", "EMAIL", "required", false,
                        "searchable", true, "sortOrder", 30,
                        "config", Map.of("maxLength", 100, "patternMode", "CUSTOM",
                                "customPattern", "^[^@]+@example\\.com$", "defaultValue", "default@example.com")),
                Map.of("code", "website", "name", "公司网站", "fieldType", "URL", "required", true,
                        "searchable", true, "sortOrder", 40, "config", Map.of("maxLength", 200)),
                Map.of("code", "identity", "name", "身份证号", "fieldType", "ID_CARD", "required", true,
                        "searchable", true, "sortOrder", 50, "config", Map.of("maxLength", 18)));
        for (Map<String, Object> definition : definitions) {
            ResponseEntity<Map> created = exchange("/api/admin/module-config/modules/" + moduleId + "/fields",
                    HttpMethod.POST, owner.token(), definition);
            assertThat(created.getStatusCode()).as("field %s: %s", definition.get("code"), created.getBody())
                    .isEqualTo(HttpStatus.OK);
        }
        int revision = jdbc.queryForObject("select draft_revision from un_cfg_module where id=?", Integer.class, moduleId);
        ResponseEntity<Map> published = exchange("/api/admin/module-config/modules/" + moduleId + "/publish",
                HttpMethod.POST, owner.token(), Map.of("expectedDraftRevision", revision));
        assertThat(published.getStatusCode()).as("publish: %s", published.getBody()).isEqualTo(HttpStatus.OK);

        String recordsPath = "/api/runtime/modules/text_customer/records";
        Map<String, Object> initialFields = new java.util.LinkedHashMap<>();
        initialFields.put("notes", "首次联系，客户有明确需求");
        initialFields.put("phone", "139-0013-9000");
        initialFields.put("website", "https://example.com/customer");
        initialFields.put("identity", "11010519491231002X");
        ResponseEntity<Map> created = exchange(recordsPath, HttpMethod.POST, owner.token(), Map.of(
                "title", "文本字段客户", "participantMemberIds", List.of(), "fields", initialFields));
        assertThat(created.getStatusCode()).as("create: %s", created.getBody()).isEqualTo(HttpStatus.OK);
        long recordId = number(data(created).get("id"));
        int version = ((Number) data(created).get("version")).intValue();
        Map<String, Object> createdFields = map(data(created).get("fields"));
        assertThat(createdFields).containsEntry("phone", "13900139000")
                .containsEntry("email", "default@example.com")
                .containsEntry("website", "https://example.com/customer")
                .containsEntry("identity", "11010519491231002X");
        assertThat(count("un_biz_record_value", "record_id=" + recordId + " and value_text is not null")).isEqualTo(5);
        assertThat(jdbc.queryForList("select value_type from un_biz_record_value where record_id=?", String.class, recordId))
                .containsExactlyInAnyOrder("MULTILINE_TEXT", "PHONE", "EMAIL", "URL", "ID_CARD");

        Map<String, Object> validFields = new java.util.LinkedHashMap<>(createdFields);
        List<Map.Entry<String, String>> invalidValues = List.of(
                Map.entry("notes", "短"), Map.entry("phone", "not-a-phone"),
                Map.entry("email", "user@other.com"), Map.entry("website", "example.com/path"),
                Map.entry("identity", "110105194912310021"));
        for (Map.Entry<String, String> invalidValue : invalidValues) {
            Map<String, Object> submitted = new java.util.LinkedHashMap<>(validFields);
            submitted.put(invalidValue.getKey(), invalidValue.getValue());
            ResponseEntity<Map> rejected = exchange(recordsPath, HttpMethod.POST, owner.token(), Map.of(
                    "title", "不应落库-" + invalidValue.getKey(), "participantMemberIds", List.of(), "fields", submitted));
            assertThat(rejected.getStatusCode()).as("invalid %s: %s", invalidValue.getKey(), rejected.getBody())
                    .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(rejected.getBody()).containsEntry("code", "FIELD_VALIDATION_FAILED");
            assertThat(rejected.getBody().get("message").toString()).contains(invalidValue.getKey());
        }
        assertThat(count("un_biz_record", "module_id=" + moduleId)).isOne();
        assertThat(count("un_audit_event", "event_code='BUSINESS_RECORD_CREATED' and object_id='" + recordId
                + "' and result_code='SUCCESS'")).isOne();

        Map<String, Object> updatedFields = new java.util.LinkedHashMap<>(validFields);
        updatedFields.put("notes", "再次联系，已经约定演示时间");
        updatedFields.put("phone", "+86 139 0013 9001");
        updatedFields.put("email", "updated@example.com");
        updatedFields.put("website", "https://example.com/customer/updated");
        ResponseEntity<Map> updated = exchange(recordsPath + "/" + recordId, HttpMethod.PUT, owner.token(), Map.of(
                "title", "文本字段客户已更新", "participantMemberIds", List.of(), "version", version,
                "fields", updatedFields));
        assertThat(updated.getStatusCode()).as("update: %s", updated.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(map(data(updated).get("fields"))).containsEntry("phone", "+8613900139001")
                .containsEntry("email", "updated@example.com");

        ResponseEntity<Map> reopened = exchange(recordsPath + "/" + recordId, HttpMethod.GET, owner.token(), null);
        assertThat(reopened.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(map(data(reopened).get("fields"))).containsEntry("notes", "再次联系，已经约定演示时间")
                .containsEntry("website", "https://example.com/customer/updated");
        ResponseEntity<Map> searched = exchangeEncoded(recordsPath + "?search="
                + URLEncoder.encode("updated@example.com", StandardCharsets.UTF_8), HttpMethod.GET, owner.token(), null);
        assertThat(searched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) data(searched).get("records")).singleElement();
        String filters = URLEncoder.encode("[{\"fieldCode\":\"phone\",\"operator\":\"CONTAINS\",\"value\":\"39001\"}]",
                StandardCharsets.UTF_8);
        ResponseEntity<Map> filtered = exchangeEncoded(recordsPath + "?filters=" + filters,
                HttpMethod.GET, owner.token(), null);
        assertThat(filtered.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) data(filtered).get("records")).singleElement();

        ResponseEntity<Map> timeline = exchange(recordsPath + "/" + recordId + "/timeline",
                HttpMethod.GET, owner.token(), null);
        assertThat(timeline.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> latest = map(((List<?>) data(timeline).get("entries")).get(0));
        assertThat((List<?>) latest.get("changes")).anySatisfy(change -> assertThat(map(change))
                .containsEntry("fieldCode", "email").containsEntry("fieldName", "工作邮箱")
                .containsEntry("valueType", "EMAIL").containsEntry("beforeValue", "default@example.com")
                .containsEntry("afterValue", "updated@example.com"));
    }

    @Test
    void concurrentActionsAndRecoverableDeliveryDoNotRepeatBusiness() throws Exception {
        Session owner = register("action_parallel", "action-parallel", "action-parallel@example.com");
        long moduleId = createAndPublishCustomerModule(owner);
        String path = "/api/runtime/modules/customer/records";
        Map<String, Object> body = Map.of("title", "deliver-once", "participantMemberIds", List.of(),
                "fields", Map.of("customer_name", "deliver-once", "level", "A"));
        deliveryAttempts.set(0);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var results = executor.invokeAll(List.<Callable<ResponseEntity<Map>>>of(
                    () -> actionRequest(path, HttpMethod.POST, owner, "action-parallel", body),
                    () -> actionRequest(path, HttpMethod.POST, owner, "action-parallel", body)));
            assertThat(results.get(0).get().getStatusCode()).as("parallel: %s", results.get(0).get().getBody()).isEqualTo(HttpStatus.OK);
            assertThat(results.get(1).get().getStatusCode()).as("parallel: %s", results.get(1).get().getBody()).isEqualTo(HttpStatus.OK);
            assertThat(data(results.get(0).get())).isEqualTo(data(results.get(1).get()));
        }
        long outboxId = jdbc.queryForObject("select o.id from un_core_event_outbox o join un_core_idempotency_record r "
                + "on o.aggregate_id=cast(r.id as char) where o.aggregate_type='BUSINESS_ACTION' and r.idempotency_key='action-parallel'", Long.class);
        assertThat(jdbc.queryForObject("select status from un_core_event_outbox where id=?", String.class, outboxId)).isEqualTo("FAILED");
        assertThat(count("un_biz_record", "module_id=" + moduleId)).isOne();
        jdbc.update("update un_core_event_outbox set available_time=? where id=?", LocalDateTime.now().minusSeconds(1), outboxId);
        actionDispatcher.retryDue();
        actionDispatcher.dispatch(outboxId);
        assertThat(jdbc.queryForObject("select status from un_core_event_outbox where id=?", String.class, outboxId))
                .as("delivery: %s", jdbc.queryForMap("select status,available_time,retry_count,last_error from un_core_event_outbox where id=?", outboxId))
                .isEqualTo("PUBLISHED");
        assertThat(jdbc.queryForObject("select retry_count from un_core_event_outbox where id=?", Integer.class, outboxId)).isOne();
        assertThat(deliveryAttempts.get()).isEqualTo(2);
        assertThat(count("un_biz_record", "module_id=" + moduleId)).isOne();
    }

    private ResponseEntity<Map> actionRequest(String path, HttpMethod method, Session owner, String key, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(owner.token());
        headers.set("Idempotency-Key", key);
        return http.exchange(path, method, new HttpEntity<>(body, headers), Map.class);
    }

    @Test
    void publishedDirectBindingAndUnmatchedConditionPreserveOrdinarySave() {
        Session owner = register("action_binding", "action-binding", "action-binding@example.com");
        long moduleId = createAndPublishCustomerModule(owner);
        var flow = exchange("/api/flows", HttpMethod.POST, owner.token(), Map.of("code", "action_binding", "name", "Action binding"));
        assertThat(flow.getStatusCode()).isEqualTo(HttpStatus.OK);
        long flowId = number(data(flow).get("id"));
        var draft = exchange("/api/flows/" + flowId + "/draft", HttpMethod.PUT, owner.token(), Map.of(
                "expectedVersion", 0, "nodes", List.of(
                        Map.of("nodeKey", "start", "nodeType", "START", "name", "Start", "positionX", 0, "positionY", 0, "config", Map.of()),
                        Map.of("nodeKey", "end", "nodeType", "END", "name", "End", "positionX", 400, "positionY", 0, "config", Map.of())),
                "edges", List.of(Map.of("edgeKey", "finish", "sourceNodeKey", "start", "targetNodeKey", "end",
                        "priorityOrder", 0, "config", Map.of()))));
        assertThat(draft.getStatusCode()).as("flow draft: %s", draft.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(exchange("/api/flows/" + flowId + "/publish", HttpMethod.POST, owner.token(),
                Map.of("expectedDraftRevision", data(draft).get("draftRevision"), "changeSummary", "Action fixture"))
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        var binding = new java.util.LinkedHashMap<String, Object>(Map.of("moduleId", moduleId, "flowId", flowId,
                "triggerEvent", "CREATE", "executionMode", "DIRECT", "priorityOrder", 0,
                "mutuallyExclusive", true, "replaceExisting", false));
        assertThat(exchange("/api/flow-bindings/publish", HttpMethod.POST, owner.token(), binding).getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = Map.of("title", "Bound save", "participantMemberIds", List.of(),
                "fields", Map.of("customer_name", "Bound save", "level", "A", "amount", 5));
        assertThat(actionRequest("/api/runtime/modules/customer/records", HttpMethod.POST, owner, "binding-direct", body)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(count("un_flow_binding_resolution", "system_id=" + owner.systemId())).isOne();
        binding.put("replaceExisting", true);
        binding.put("executionMode", "AFTER_APPROVAL");
        binding.put("conditionExpression", "amount > 10");
        assertThat(exchange("/api/flow-bindings/publish", HttpMethod.POST, owner.token(), binding).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(actionRequest("/api/runtime/modules/customer/records", HttpMethod.POST, owner, "binding-miss", body)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(count("un_biz_record", "module_id=" + moduleId)).isEqualTo(2);
        assertThat(count("un_flow_instance", "system_id=" + owner.systemId())).isZero();
    }
    @Test
    void boundAfterExecutionPinsVersionAndRecoversWithoutRepeatingCrudOrFlow() {
        Session owner = register("bound_after", "bound-after", "bound-after@example.com");
        long moduleId = createAndPublishCustomerModule(owner);
        long flowId = number(data(exchange("/api/flows", HttpMethod.POST, owner.token(),
                Map.of("code", "bound_after", "name", "Bound after"))).get("id"));
        long originalVersion = publishActionFlow(owner, flowId, "original_end");
        publishActionBinding(owner, moduleId, flowId, "CREATE", "AFTER_EXECUTION");
        String path = "/api/runtime/modules/customer/records";
        var body = Map.of("title", "bound-delivery", "participantMemberIds", List.of(),
                "fields", Map.of("customer_name", "Bound customer", "level", "A", "amount", 8));
        failBoundDelivery.set(true);
        var created = actionEnvelope(path, HttpMethod.POST, owner, "bound-after-create", body);
        assertThat(created.getStatusCode()).as("create: %s", created.getBody()).isEqualTo(HttpStatus.OK);
        var result = data(created);
        var record = map(result.get("value"));
        long recordId = number(record.get("id"));
        long actionId = number(result.get("idempotencyRecordId"));
        long outboxId = number(result.get("outboxId"));
        String receipt = path + "/action-results/" + actionId;
        assertThat(result).containsEntry("mode", "AFTER_EXECUTION").containsEntry("status", "COMPLETED");
        assertThat(data(exchange(path + "/" + recordId, HttpMethod.GET, owner.token(), null))).containsEntry("title", "bound-delivery");
        assertThat(count("un_flow_instance", "system_id=" + owner.systemId())).isZero();
        assertThat(data(exchange(receipt, HttpMethod.GET, owner.token(), null))).containsEntry("status", "RETRYING");
        assertThat(jdbc.queryForObject("select status from un_core_event_outbox where id=?", String.class, outboxId)).isEqualTo("FAILED");
        long newerVersion = publishActionFlow(owner, flowId, "new_end");
        assertThat(newerVersion).isNotEqualTo(originalVersion);
        publishActionBinding(owner, moduleId, flowId, "CREATE", "FLOW_ONLY");
        assertThat(data(actionEnvelope(path, HttpMethod.POST, owner, "bound-after-create", body)))
                .containsEntry("mode", "AFTER_EXECUTION").containsEntry("replayed", true).containsEntry("value", record);
        jdbc.update("update un_core_event_outbox set available_time=? where id=?", java.time.LocalDateTime.now().minusSeconds(1), outboxId);
        actionDispatcher.dispatch(outboxId);
        actionDispatcher.dispatch(outboxId);
        assertThat(count("un_flow_instance", "system_id=" + owner.systemId())).isOne();
        assertThat(jdbc.queryForObject("select flow_version_id from un_flow_instance where system_id=?", Long.class, owner.systemId())).isEqualTo(originalVersion);
        assertThat(jdbc.queryForObject("select current_node_key from un_flow_instance where system_id=?", String.class, owner.systemId())).isEqualTo("original_end");
        assertThat(data(exchange(receipt, HttpMethod.GET, owner.token(), null))).containsEntry("status", "STARTED");
        assertThat(count("un_biz_record", "module_id=" + moduleId)).isOne();
        assertThat(count("un_audit_event", "event_code='BUSINESS_RECORD_CREATED' and object_id='" + recordId + "'" )).isOne();
        Session outsider = register("bound_other", "bound-other", "bound-other@example.com");
        assertThat(exchange(receipt, HttpMethod.GET, outsider.token(), null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        publishActionBinding(owner, moduleId, flowId, "UPDATE", "AFTER_EXECUTION");
        var update = new java.util.LinkedHashMap<String, Object>(body);
        update.put("version", record.get("version"));
        update.put("title", "After updated");
        var updated = data(actionEnvelope(path + "/" + recordId, HttpMethod.PUT, owner, "bound-after-update", update));
        assertThat(updated).containsEntry("mode", "AFTER_EXECUTION");
        assertThat(data(actionEnvelope(path + "/" + recordId, HttpMethod.PUT, owner, "bound-after-update", update)))
                .containsEntry("replayed", true).containsEntry("value", updated.get("value"));
        publishActionBinding(owner, moduleId, flowId, "DELETE", "AFTER_EXECUTION");
        var deletion = Map.of("version", map(updated.get("value")).get("version"), "reason", "delete after Flow");
        var deleted = data(actionEnvelope(path + "/" + recordId + "/delete", HttpMethod.POST, owner, "bound-after-delete", deletion));
        assertThat(map(deleted.get("value"))).containsEntry("deleted", true);
        assertThat(data(actionEnvelope(path + "/" + recordId + "/delete", HttpMethod.POST, owner, "bound-after-delete", deletion)))
                .containsEntry("replayed", true);
        assertThat(count("un_flow_instance", "system_id=" + owner.systemId())).isEqualTo(3);
    }

    @Test
    void flowOnlyReusesTheSameBoundaryForTwoModulesAndNeverWritesCrud() {
        Session owner = register("bound_only", "bound-only", "bound-only@example.com");
        long customerModule = createAndPublishCustomerModule(owner);
        long opportunityModule = createAndPublishOpportunityModule(owner);
        long flowId = number(data(exchange("/api/flows", HttpMethod.POST, owner.token(),
                Map.of("code", "bound_only", "name", "Bound only"))).get("id"));
        publishActionFlow(owner, flowId, "end");
        String customers = "/api/runtime/modules/customer/records";
        String opportunities = "/api/runtime/modules/opportunity/records";
        var customer = Map.of("title", "Customer", "participantMemberIds", List.of(),
                "fields", Map.of("customer_name", "Customer", "level", "A"));
        var original = data(actionRequest(customers, HttpMethod.POST, owner, "only-original", customer));
        long recordId = number(original.get("id"));
        publishActionBinding(owner, customerModule, flowId, "CREATE", "FLOW_ONLY");
        publishActionBinding(owner, opportunityModule, flowId, "CREATE", "FLOW_ONLY");
        var started = actionEnvelope(customers, HttpMethod.POST, owner, "only-create", customer);
        assertThat(started.getStatusCode()).as("Flow-only: %s", started.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(data(started)).containsEntry("mode", "FLOW_ONLY").containsEntry("status", "FLOW_STARTED").containsEntry("value", null);
        assertThat(data(started).get("flowInstanceId")).isNotNull();
        assertThat(data(actionEnvelope(customers, HttpMethod.POST, owner, "only-create", customer)))
                .containsEntry("flowInstanceId", data(started).get("flowInstanceId")).containsEntry("replayed", true);
        var opportunity = Map.of("title", "Opportunity", "participantMemberIds", List.of(),
                "fields", Map.of("opportunity_name", "Opportunity", "source_level", "A"));
        assertThat(data(actionEnvelope(opportunities, HttpMethod.POST, owner, "only-create", opportunity)))
                .containsEntry("mode", "FLOW_ONLY").containsEntry("value", null);
        var invalid = Map.of("title", "Invalid", "participantMemberIds", List.of(), "fields", Map.of());
        assertThat(actionEnvelope(opportunities, HttpMethod.POST, owner, "only-invalid", invalid).getStatusCode().is4xxClientError()).isTrue();
        assertThat(count("un_core_idempotency_record", "idempotency_key='only-invalid'")).isZero();
        publishActionBinding(owner, customerModule, flowId, "UPDATE", "FLOW_ONLY");
        publishActionBinding(owner, customerModule, flowId, "DELETE", "FLOW_ONLY");
        var update = new java.util.LinkedHashMap<String, Object>(customer);
        update.put("title", "Must not save");
        update.put("version", original.get("version"));
        assertThat(data(actionEnvelope(customers + "/" + recordId, HttpMethod.PUT, owner, "only-update", update)))
                .containsEntry("mode", "FLOW_ONLY").containsEntry("value", null);
        update.put("version", 999);
        assertThat(actionEnvelope(customers + "/" + recordId, HttpMethod.PUT, owner, "only-stale", update).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(data(actionEnvelope(customers + "/" + recordId + "/delete", HttpMethod.POST, owner, "only-delete",
                Map.of("version", original.get("version"), "reason", "Flow only deletion")))).containsEntry("mode", "FLOW_ONLY");
        assertThat(data(exchange(customers + "/" + recordId, HttpMethod.GET, owner.token(), null)))
                .containsEntry("title", "Customer").containsEntry("version", original.get("version")).containsEntry("deleted", false);
        assertThat(count("un_biz_record", "module_id=" + customerModule)).isOne();
        assertThat(count("un_biz_record", "module_id=" + opportunityModule)).isZero();
        assertThat(count("un_flow_instance", "system_id=" + owner.systemId())).isEqualTo(4);
        assertThat(count("un_audit_event", "event_code in ('BUSINESS_RECORD_UPDATED','BUSINESS_RECORD_DELETED') and system_id=" + owner.systemId())).isZero();
        // An unadapted importer/AI/application caller must not commit a Flow and then dereference a null record.
        long memberId = memberId(owner.systemId(), owner.accountId());
        long tenantMemberId = jdbc.queryForObject("select id from un_sys_tenant_member where tenant_id=? and system_member_id=?",
                Long.class, owner.tenantId(), memberId);
        var permissions = testActionPermissions.resolve(owner.systemId(), owner.tenantId(), tenantMemberId);
        var context = new com.unique.unexamine.authentication.manage.AuthenticatedContext(null, owner.accountId(), null,
                owner.systemId(), owner.tenantId(), memberId, tenantMemberId, "bound_only", "Bound only", "NONE",
                permissions.roleIds(), permissions.permissions(), permissions.dataScopes());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> runtimeActions.create(context, "customer",
                new CreateRuntimeRecordRequest("Legacy call", null, null, null, null, List.of(), Map.of(
                        "customer_name", new com.fasterxml.jackson.databind.node.TextNode("Legacy"),
                        "level", new com.fasterxml.jackson.databind.node.TextNode("A"))), "legacy-flow-only"))
                .isInstanceOf(com.unique.unexamine.shared.manage.web.DomainException.class)
                .hasMessageContaining("当前入口尚不能处理流程结果");
        assertThat(count("un_flow_instance", "system_id=" + owner.systemId())).isEqualTo(4);
        assertThat(count("un_biz_record", "module_id=" + customerModule)).isOne();
    }

    @Test
    void approvalIntentsStayInvisibleAndResumeOnceWhileRejectionAndWithdrawalNeverWrite() {
        Session owner = register("intent_owner", "intent-system", "intent-owner@example.com");
        long moduleId = createAndPublishCustomerModule(owner);
        long flowId = publishApprovalActionFlow(owner, "intent_flow");
        publishActionBinding(owner, moduleId, flowId, "CREATE", "AFTER_APPROVAL");
        String path = "/api/runtime/modules/customer/records";
        var body = Map.of("title", "Approved customer", "participantMemberIds", List.of(),
                "fields", Map.of("customer_name", "Approved customer", "level", "A"));
        var pendingResponse = actionEnvelope(path, HttpMethod.POST, owner, "intent-create", body);
        assertThat(pendingResponse.getStatusCode()).as("pending: %s", pendingResponse.getBody()).isEqualTo(HttpStatus.OK);
        var pending = data(pendingResponse);
        long actionId = number(pending.get("idempotencyRecordId"));
        long instanceId = number(pending.get("flowInstanceId"));
        String receipt = path + "/action-results/" + actionId;
        assertThat(pending).containsEntry("status", "PENDING_APPROVAL").containsEntry("value", null);
        assertThat(count("un_biz_record", "module_id=" + moduleId)).isZero();
        assertThat(count("un_audit_event", "event_code='BUSINESS_RECORD_CREATED' and system_id=" + owner.systemId())).isZero();
        assertThat(jdbc.queryForObject("select business_id from un_flow_instance where id=?", String.class, instanceId)).isNull();
        assertThat(data(actionEnvelope(path, HttpMethod.POST, owner, "intent-create", body)))
                .containsEntry("replayed", true).containsEntry("flowInstanceId", pending.get("flowInstanceId"));
        assertThat(count("un_flow_instance", "system_id=" + owner.systemId())).isOne();
        assertThat(data(exchange(receipt + "/retry", HttpMethod.POST, owner.token(), Map.of("approved", true))))
                .containsEntry("businessStatus", "PENDING_APPROVAL");
        assertThat(count("un_biz_record", "module_id=" + moduleId)).isZero();
        finishApprovalTask(owner, instanceId, "APPROVE");
        var one = java.util.concurrent.CompletableFuture.runAsync(() -> approvalActions.reconcile(actionId));
        var two = java.util.concurrent.CompletableFuture.runAsync(() -> approvalActions.reconcile(actionId));
        java.util.concurrent.CompletableFuture.allOf(one, two).join();
        approvalActions.reconcileDue();
        var executed = data(actionEnvelope(path, HttpMethod.POST, owner, "intent-create", body));
        assertThat(executed).containsEntry("status", "EXECUTED").containsEntry("replayed", true);
        var record = map(executed.get("value"));
        long recordId = number(record.get("id"));
        assertThat(count("un_biz_record", "module_id=" + moduleId)).isOne();
        assertThat(count("un_core_event_outbox", "aggregate_type='BUSINESS_ACTION_COMPLETION' and aggregate_id='" + actionId + "'" )).isOne();
        assertThat(count("un_audit_event", "event_code='BUSINESS_RECORD_CREATED' and object_id='" + recordId + "'" )).isOne();
        assertThat(data(exchange(receipt, HttpMethod.GET, owner.token(), null))).containsEntry("businessStatus", "EXECUTED");

        publishActionBinding(owner, moduleId, flowId, "UPDATE", "AFTER_APPROVAL");
        var update = new java.util.LinkedHashMap<String, Object>(body);
        update.put("title", "Approved update"); update.put("version", record.get("version"));
        var change = data(actionEnvelope(path + "/" + recordId, HttpMethod.PUT, owner, "intent-update", update));
        assertThat(data(exchange(path + "/" + recordId, HttpMethod.GET, owner.token(), null))).containsEntry("title", "Approved customer");
        finishApprovalTask(owner, number(change.get("flowInstanceId")), "APPROVE");
        actionDispatcher.retryDue();
        var updated = data(actionEnvelope(path + "/" + recordId, HttpMethod.PUT, owner, "intent-update", update));
        assertThat(map(updated.get("value"))).containsEntry("title", "Approved update");
        publishActionBinding(owner, moduleId, flowId, "DELETE", "AFTER_APPROVAL");
        var deletion = Map.of("version", map(updated.get("value")).get("version"), "reason", "delete only after approval");
        var remove = data(actionEnvelope(path + "/" + recordId + "/delete", HttpMethod.POST, owner, "intent-delete", deletion));
        var withdrawn = exchange("/api/flow-runtime/instances/" + remove.get("flowInstanceId") + "/actions", HttpMethod.POST,
                owner.token(), Map.of("actionCode", "WITHDRAW", "comment", "keep the record", "idempotencyKey", "intent-withdraw"));
        assertThat(withdrawn.getStatusCode()).as("withdraw: %s", withdrawn.getBody()).isEqualTo(HttpStatus.OK);
        approvalActions.reconcileDue();
        assertThat(data(actionEnvelope(path + "/" + recordId + "/delete", HttpMethod.POST, owner, "intent-delete", deletion)))
                .containsEntry("status", "WITHDRAWN").containsEntry("value", null);
        assertThat(data(exchange(path + "/" + recordId, HttpMethod.GET, owner.token(), null))).containsEntry("deleted", false);
        var rejected = data(actionEnvelope(path, HttpMethod.POST, owner, "intent-reject", body));
        finishApprovalTask(owner, number(rejected.get("flowInstanceId")), "REJECT");
        approvalActions.reconcileDue();
        var rejectedReceipt = path + "/action-results/" + rejected.get("idempotencyRecordId");
        assertThat(data(exchange(rejectedReceipt + "/retry", HttpMethod.POST, owner.token(), null))).containsEntry("businessStatus", "REJECTED");
        assertThat(count("un_biz_record", "module_id=" + moduleId)).isOne();
        Session outsider = register("intent_other", "intent-other", "intent-other@example.com");
        assertThat(exchange(rejectedReceipt + "/retry", HttpMethod.POST, outsider.token(), null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        var approvedDelete = data(actionEnvelope(path + "/" + recordId + "/delete", HttpMethod.POST, owner, "intent-delete-approved", deletion));
        finishApprovalTask(owner, number(approvedDelete.get("flowInstanceId")), "APPROVE");
        approvalActions.reconcileDue();
        var deleted = data(actionEnvelope(path + "/" + recordId + "/delete", HttpMethod.POST, owner, "intent-delete-approved", deletion));
        assertThat(deleted).containsEntry("status", "EXECUTED").containsEntry("replayed", true);
        assertThat(map(deleted.get("value"))).containsEntry("deleted", true);
        approvalActions.reconcile(number(approvedDelete.get("idempotencyRecordId")));
        assertThat(count("un_audit_event", "event_code='BUSINESS_RECORD_DELETED' and object_id='" + recordId + "'" )).isOne();
        assertThat(count("un_core_event_outbox", "aggregate_type='BUSINESS_ACTION_COMPLETION' and aggregate_id='" + approvedDelete.get("idempotencyRecordId") + "'" )).isOne();
    }

    @Test
    void approvedActionsRecheckAuthorityVersionsAndConfigurationAndRollBackFailedWrites() {
        Session owner = register("intent_conflict", "intent-conflict", "intent-conflict@example.com");
        long moduleId = createAndPublishCustomerModule(owner);
        long flowId = publishApprovalActionFlow(owner, "intent_conflict_flow");
        publishActionBinding(owner, moduleId, flowId, "CREATE", "AFTER_APPROVAL");
        String path = "/api/runtime/modules/customer/records";
        var body = Map.of("title", "Intent conflict", "participantMemberIds", List.of(),
                "fields", Map.of("customer_name", "Intent conflict", "level", "A"));
        var pending = data(actionEnvelope(path, HttpMethod.POST, owner, "intent-revoke", body));
        finishApprovalTask(owner, number(pending.get("flowInstanceId")), "APPROVE");
        long actorMember = memberId(owner.systemId(), owner.accountId());
        jdbc.update("update un_sys_tenant_member set status='DISABLED' where tenant_id=? and system_member_id=?", owner.tenantId(), actorMember);
        approvalActions.reconcile(number(pending.get("idempotencyRecordId")));
        jdbc.update("update un_sys_tenant_member set status='ACTIVE' where tenant_id=? and system_member_id=?", owner.tenantId(), actorMember);
        String receipt = path + "/action-results/" + pending.get("idempotencyRecordId");
        assertThat(data(exchange(receipt, HttpMethod.GET, owner.token(), null)))
                .containsEntry("businessStatus", "CONFLICT").containsEntry("errorCode", "ACTION_ACTOR_INACTIVE");
        assertThat(count("un_biz_record", "module_id=" + moduleId)).isZero();
        assertThat(data(exchange(receipt + "/retry", HttpMethod.POST, owner.token(), null))).containsEntry("businessStatus", "EXECUTED");
        var record = map(data(actionEnvelope(path, HttpMethod.POST, owner, "intent-revoke", body)).get("value"));
        long recordId = number(record.get("id"));
        publishActionBinding(owner, moduleId, flowId, "UPDATE", "AFTER_APPROVAL");
        var update = new java.util.LinkedHashMap<String, Object>(body);
        update.put("version", record.get("version")); update.put("title", "Must not overwrite");
        var stale = data(actionEnvelope(path + "/" + recordId, HttpMethod.PUT, owner, "intent-stale", update));
        jdbc.update("update un_biz_record set title='Changed elsewhere', version=version+1 where id=?", recordId);
        finishApprovalTask(owner, number(stale.get("flowInstanceId")), "APPROVE");
        approvalActions.reconcileDue();
        assertThat(data(exchange(path + "/action-results/" + stale.get("idempotencyRecordId") + "/retry", HttpMethod.POST, owner.token(), null)))
                .containsEntry("businessStatus", "CONFLICT").containsEntry("errorCode", "RECORD_VERSION_CONFLICT");
        assertThat(data(exchange(path + "/" + recordId, HttpMethod.GET, owner.token(), null))).containsEntry("title", "Changed elsewhere");

        var failing = data(actionEnvelope(path, HttpMethod.POST, owner, "intent-sql-fail", body));
        finishApprovalTask(owner, number(failing.get("flowInstanceId")), "APPROVE");
        jdbc.execute("CREATE TRIGGER b01_approval_fail BEFORE INSERT ON un_biz_record_value FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='approval controlled failure'");
        try { approvalActions.reconcile(number(failing.get("idempotencyRecordId"))); }
        finally { jdbc.execute("DROP TRIGGER b01_approval_fail"); }
        String failureReceipt = path + "/action-results/" + failing.get("idempotencyRecordId");
        assertThat(data(exchange(failureReceipt, HttpMethod.GET, owner.token(), null)))
                .containsEntry("businessStatus", "EXECUTION_FAILED").containsEntry("errorCode", "ACTION_EXECUTION_FAILED");
        assertThat(count("un_biz_record", "module_id=" + moduleId)).isOne();
        assertThat(count("un_core_event_outbox", "aggregate_type='BUSINESS_ACTION_COMPLETION' and aggregate_id='" + failing.get("idempotencyRecordId") + "'" )).isZero();
        assertThat(data(exchange(failureReceipt + "/retry", HttpMethod.POST, owner.token(), null))).containsEntry("businessStatus", "EXECUTED");
        assertThat(data(exchange(failureReceipt + "/retry", HttpMethod.POST, owner.token(), null))).containsEntry("businessStatus", "EXECUTED");
        assertThat(count("un_biz_record", "module_id=" + moduleId)).isEqualTo(2);

        var configChanged = data(actionEnvelope(path, HttpMethod.POST, owner, "intent-config", body));
        createField(owner, moduleId, "new_optional", "New optional", "TEXT", false, 90, Map.of());
        int revision = jdbc.queryForObject("select draft_revision from un_cfg_module where id=?", Integer.class, moduleId);
        assertThat(exchange("/api/admin/module-config/modules/" + moduleId + "/publish", HttpMethod.POST, owner.token(),
                Map.of("expectedDraftRevision", revision)).getStatusCode()).isEqualTo(HttpStatus.OK);
        finishApprovalTask(owner, number(configChanged.get("flowInstanceId")), "APPROVE");
        approvalActions.reconcileDue();
        assertThat(data(exchange(path + "/action-results/" + configChanged.get("idempotencyRecordId"), HttpMethod.GET, owner.token(), null)))
                .containsEntry("businessStatus", "CONFLICT").containsEntry("errorCode", "ACTION_CONFIGURATION_CHANGED");
        assertThat(count("un_biz_record", "module_id=" + moduleId)).isEqualTo(2);
        // A mismatched intent snapshot must fail even if the base publication/version id is unchanged.
        var drift = data(actionEnvelope(path, HttpMethod.POST, owner, "intent-effective-drift", body));
        long currentVersion = jdbc.queryForObject("select current_version_id from un_cfg_module_publication where module_id=?", Long.class, moduleId);
        jdbc.update("update un_core_event_outbox set payload_json=JSON_SET(payload_json, '$.configurationSnapshot.module.name', 'Mismatched intent snapshot') where id=?",
                number(drift.get("outboxId")));
        finishApprovalTask(owner, number(drift.get("flowInstanceId")), "APPROVE");
        approvalActions.reconcileDue();
        assertThat(data(exchange(path + "/action-results/" + drift.get("idempotencyRecordId"), HttpMethod.GET, owner.token(), null)))
                .containsEntry("businessStatus", "CONFLICT").containsEntry("errorCode", "ACTION_CONFIGURATION_CHANGED");
        assertThat(jdbc.queryForObject("select current_version_id from un_cfg_module_publication where module_id=?", Long.class, moduleId)).isEqualTo(currentVersion);
        assertThat(count("un_biz_record", "module_id=" + moduleId)).isEqualTo(2);
    }

    /** 模块审批动作必须先发布，再绑定 Flow；草稿开关、幂等及任务均按同一记录验证。 */
    @Test
    void publishedModuleApprovalStartsBoundFlowFromAuthorizedRecord() {
        Session owner = register("approval_owner", "approval_system", "approval@example.com");
        long moduleId = createAndPublishCustomerModule(owner);
        var created = exchange("/api/runtime/modules/customer/records", HttpMethod.POST, owner.token(),
                Map.of("title", "Approval customer", "participantMemberIds", List.of(),
                        "fields", Map.of("customer_name", "Approval customer", "level", "A")));
        assertThat(created.getStatusCode()).as("record: %s", created.getBody()).isEqualTo(HttpStatus.OK);
        long recordId = number(data(created).get("id"));
        var draft = exchange("/api/admin/module-config/modules/" + moduleId + "/actions/approval",
                HttpMethod.POST, owner.token(), Map.of());
        assertThat(draft.getStatusCode()).as("action draft: %s", draft.getBody()).isEqualTo(HttpStatus.OK);
        long actionId = number(data(draft).get("id"));
        String path = "/api/flow-runtime/module-approvals";
        Map<String, Object> start = Map.of("moduleCode", "customer", "recordId", recordId,
                "idempotencyKey", "module-approval-1");
        assertThat(exchange(path, HttpMethod.POST, owner.token(), start).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(count("un_flow_instance", "system_id=" + owner.systemId())).isZero();

        int revision = jdbc.queryForObject("select draft_revision from un_cfg_module where id=?", Integer.class, moduleId);
        assertThat(exchange("/api/admin/module-config/modules/" + moduleId + "/publish", HttpMethod.POST,
                owner.token(), Map.of("expectedDraftRevision", revision)).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(exchange(path, HttpMethod.POST, owner.token(), start).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        long ordinaryFlowId = number(data(exchange("/api/flows", HttpMethod.POST, owner.token(),
                Map.of("code", "ordinary_module_flow", "name", "Ordinary module flow"))).get("id"));
        publishActionFlow(owner, ordinaryFlowId, "ordinary_end");
        var noApprovalNode = exchange("/api/flow-bindings/publish", HttpMethod.POST, owner.token(), Map.of(
                "moduleId", moduleId, "actionId", actionId, "flowId", ordinaryFlowId,
                "triggerEvent", "MANUAL", "executionMode", "FLOW_ONLY", "priorityOrder", 0));
        assertThat(noApprovalNode.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(noApprovalNode.getBody()).containsEntry("code", "MODULE_APPROVAL_FLOW_NODE_REQUIRED");
        long flowId = publishApprovalActionFlow(owner, "module_approval_flow");
        var invalidBinding = exchange("/api/flow-bindings/publish", HttpMethod.POST, owner.token(), Map.of(
                "moduleId", moduleId, "actionId", actionId, "flowId", flowId,
                "triggerEvent", "MANUAL", "executionMode", "DIRECT", "priorityOrder", 0));
        assertThat(invalidBinding.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(invalidBinding.getBody()).containsEntry("code", "MODULE_APPROVAL_BINDING_INVALID");
        var binding = exchange("/api/flow-bindings/publish", HttpMethod.POST, owner.token(), Map.of(
                "moduleId", moduleId, "actionId", actionId, "flowId", flowId,
                "triggerEvent", "MANUAL", "executionMode", "FLOW_ONLY", "priorityOrder", 0,
                "mutuallyExclusive", true, "replaceExisting", true));
        assertThat(binding.getStatusCode()).as("binding: %s", binding.getBody()).isEqualTo(HttpStatus.OK);

        var visible = data(exchange("/api/runtime/modules/customer/records/" + recordId,
                HttpMethod.GET, owner.token(), null));
        assertThat((List<Map<String, Object>>) visible.get("actions"))
                .anySatisfy(item -> assertThat(item).containsEntry("code", "APPROVAL")
                        .containsEntry("location", "DETAIL_HEADER"));

        var started = exchange(path, HttpMethod.POST, owner.token(), start);
        assertThat(started.getStatusCode()).as("approval start: %s", started.getBody()).isEqualTo(HttpStatus.OK);
        Map<String, Object> result = data(started);
        long instanceId = number(map(result.get("instance")).get("id"));
        assertThat(result).containsEntry("replayed", false);
        assertThat(jdbc.queryForMap("select system_id, tenant_id, flow_id, business_type, business_id from un_flow_instance where id=?", instanceId))
                .containsEntry("system_id", owner.systemId()).containsEntry("tenant_id", owner.tenantId())
                .containsEntry("flow_id", flowId).containsEntry("business_type", "MODULE:customer")
                .containsEntry("business_id", String.valueOf(recordId));
        assertThat(count("un_flow_task", "instance_id=" + instanceId + " and status='PENDING'")).isOne();
        assertThat(data(exchange(path, HttpMethod.POST, owner.token(), start))).containsEntry("replayed", true);
        assertThat(count("un_flow_instance", "system_id=" + owner.systemId())).isOne();
        jdbc.update("update un_sys_system set tenant_mode='MULTI' where id=?", owner.systemId());
        var otherTenant = exchange("/api/admin/system/tenants", HttpMethod.POST, owner.token(),
                Map.of("code", "approval_other", "name", "审批隔离租户"));
        assertThat(otherTenant.getStatusCode()).isEqualTo(HttpStatus.OK);
        long otherTenantId = number(data(otherTenant).get("id"));
        var enteredOther = exchange("/api/systems/" + owner.systemId() + "/tenants/" + otherTenantId + "/enter",
                HttpMethod.POST, owner.token(), Map.of("previousSystemId", owner.systemId(),
                        "previousTenantId", owner.tenantId()));
        assertThat(enteredOther.getStatusCode()).isEqualTo(HttpStatus.OK);
        String otherToken = map(data(enteredOther).get("tokens")).get("accessToken").toString();
        assertThat(exchange(path, HttpMethod.POST, otherToken, Map.of("moduleCode", "customer",
                "recordId", recordId, "idempotencyKey", "approval-cross-tenant"))
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exchange("/api/flow-runtime/instances", HttpMethod.POST, owner.token(), Map.of(
                "flowId", flowId, "title", "Spoof approval key", "businessType", "MODULE:customer",
                "businessId", String.valueOf(recordId), "idempotencyKey", "module-approval:module-approval-1"))
                .getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        var secondRecord = exchange("/api/runtime/modules/customer/records", HttpMethod.POST, owner.token(),
                Map.of("title", "Another customer", "participantMemberIds", List.of(),
                        "fields", Map.of("customer_name", "Another customer", "level", "A")));
        assertThat(secondRecord.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(exchange(path, HttpMethod.POST, owner.token(), Map.of("moduleCode", "customer",
                "recordId", number(data(secondRecord).get("id")), "idempotencyKey", "module-approval-1"))
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        finishApprovalTask(owner, instanceId, "APPROVE");

        int actionVersion = jdbc.queryForObject("select version from un_cfg_module_action where id=?", Integer.class, actionId);
        var invalidLocation = exchange("/api/admin/module-config/modules/" + moduleId + "/actions/APPROVAL",
                HttpMethod.PUT, owner.token(), Map.of("location", "BATCH", "config", Map.of(),
                        "status", "ACTIVE", "version", actionVersion));
        assertThat(invalidLocation.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(invalidLocation.getBody()).containsEntry("code", "MODULE_APPROVAL_LOCATION_INVALID");
        assertThat(exchange("/api/admin/module-config/modules/" + moduleId + "/actions/APPROVAL", HttpMethod.PUT,
                owner.token(), Map.of("location", "DETAIL_HEADER", "config", Map.of(), "status", "DISABLED",
                        "version", actionVersion)).getStatusCode()).isEqualTo(HttpStatus.OK);
        // 草稿停用不改变当前发布动作；真正发布后才拒绝新的审批。
        assertThat(exchange(path, HttpMethod.POST, owner.token(), Map.of("moduleCode", "customer",
                "recordId", recordId, "idempotencyKey", "module-approval-before-disable"))
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        revision = jdbc.queryForObject("select draft_revision from un_cfg_module where id=?", Integer.class, moduleId);
        assertThat(exchange("/api/admin/module-config/modules/" + moduleId + "/publish", HttpMethod.POST,
                owner.token(), Map.of("expectedDraftRevision", revision)).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(exchange(path, HttpMethod.POST, owner.token(), Map.of("moduleCode", "customer",
                "recordId", recordId, "idempotencyKey", "module-approval-after-disable"))
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    private long publishApprovalActionFlow(Session owner, String code) {
        long flowId = number(data(exchange("/api/flows", HttpMethod.POST, owner.token(), Map.of("code", code, "name", code))).get("id"));
        var draft = exchange("/api/flows/" + flowId + "/draft", HttpMethod.PUT, owner.token(), Map.of(
                "expectedVersion", 0, "nodes", List.of(
                        Map.of("nodeKey", "start", "nodeType", "START", "name", "Start", "positionX", 0, "positionY", 0, "config", Map.of()),
                        Map.of("nodeKey", "approve", "nodeType", "APPROVAL", "name", "Owner approval", "positionX", 200, "positionY", 0,
                                "assigneePolicy", Map.of("type", "RECORD_OWNER"), "config", Map.of()),
                        Map.of("nodeKey", "end", "nodeType", "END", "name", "End", "positionX", 400, "positionY", 0, "config", Map.of())),
                "edges", List.of(Map.of("edgeKey", "review", "sourceNodeKey", "start", "targetNodeKey", "approve", "priorityOrder", 0, "config", Map.of()),
                        Map.of("edgeKey", "finish", "sourceNodeKey", "approve", "targetNodeKey", "end", "priorityOrder", 0, "config", Map.of()))));
        assertThat(draft.getStatusCode()).as("approval draft: %s", draft.getBody()).isEqualTo(HttpStatus.OK);
        var published = exchange("/api/flows/" + flowId + "/publish", HttpMethod.POST, owner.token(),
                Map.of("expectedDraftRevision", data(draft).get("draftRevision"), "changeSummary", "Intent approval"));
        assertThat(published.getStatusCode()).as("approval publish: %s", published.getBody()).isEqualTo(HttpStatus.OK);
        return flowId;
    }

    private void finishApprovalTask(Session owner, long instanceId, String decision) {
        long taskId = jdbc.queryForObject("select id from un_flow_task where instance_id=? and status='PENDING'", Long.class, instanceId);
        var result = exchange("/api/flow-runtime/tasks/" + taskId + "/actions", HttpMethod.POST, owner.token(),
                Map.of("actionCode", decision, "comment", "Intent contract " + decision, "idempotencyKey", "intent-task-" + taskId + "-" + decision));
        assertThat(result.getStatusCode()).as("approval outcome: %s", result.getBody()).isEqualTo(HttpStatus.OK);
    }

    private long publishActionFlow(Session owner, long flowId, String endKey) {
        var flow = data(exchange("/api/flows/" + flowId, HttpMethod.GET, owner.token(), null));
        var draft = exchange("/api/flows/" + flowId + "/draft", HttpMethod.PUT, owner.token(), Map.of(
                "expectedVersion", flow.get("version"), "nodes", List.of(
                        Map.of("nodeKey", "start", "nodeType", "START", "name", "Start", "positionX", 0, "positionY", 0, "config", Map.of()),
                        Map.of("nodeKey", endKey, "nodeType", "END", "name", "End", "positionX", 400, "positionY", 0, "config", Map.of())),
                "edges", List.of(Map.of("edgeKey", "finish", "sourceNodeKey", "start", "targetNodeKey", endKey,
                        "priorityOrder", 0, "config", Map.of()))));
        assertThat(draft.getStatusCode()).as("draft: %s", draft.getBody()).isEqualTo(HttpStatus.OK);
        var published = exchange("/api/flows/" + flowId + "/publish", HttpMethod.POST, owner.token(),
                Map.of("expectedDraftRevision", data(draft).get("draftRevision"), "changeSummary", endKey));
        assertThat(published.getStatusCode()).as("publish: %s", published.getBody()).isEqualTo(HttpStatus.OK);
        return number(data(published).get("versionId"));
    }

    private void publishActionBinding(Session owner, long moduleId, long flowId, String action, String mode) {
        var binding = exchange("/api/flow-bindings/publish", HttpMethod.POST, owner.token(), Map.of(
                "moduleId", moduleId, "flowId", flowId, "triggerEvent", action, "executionMode", mode,
                "priorityOrder", 0, "mutuallyExclusive", true, "replaceExisting", true));
        assertThat(binding.getStatusCode()).as("binding: %s", binding.getBody()).isEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<Map> actionEnvelope(String path, HttpMethod method, Session owner, String key, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(owner.token());
        headers.set("Idempotency-Key", key);
        headers.set("Prefer", "return=action");
        return http.exchange(path, method, new HttpEntity<>(body, headers), Map.class);
    }

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("unexamine")
            .withUsername("unexamine")
            .withPassword("unexamine_test")
            .withCommand("--log-bin-trust-function-creators=1");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("unexamine.exports.max-rows", () -> "2");
    }

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private BackgroundJobWorker backgroundJobWorker;

    @Test
    void publishedRuntimeStoresTypedValuesVersionsAndEnforcesActionScopeAndTenantBoundary() {
        Session owner = register("runtime_owner", "runtime-system", "runtime-owner@example.com");
        Session colleagueAccount = register("runtime_colleague", "colleague-system", "runtime-colleague@example.com");
        long ownerMemberId = memberId(owner.systemId(), owner.accountId());
        long colleagueAccountId = colleagueAccount.accountId();

        jdbc.update("insert into un_sys_member(system_id, account_id, display_name, status) values (?, ?, '同部门成员', 'ACTIVE')",
                owner.systemId(), colleagueAccountId);
        long colleagueMemberId = memberId(owner.systemId(), colleagueAccountId);
        jdbc.update("insert into un_sys_department(system_id, tenant_id, code, name, path_code, status) values (?, ?, 'sales', '销售部', '/sales', 'ACTIVE')",
                owner.systemId(), owner.tenantId());
        long departmentId = jdbc.queryForObject(
                "select id from un_sys_department where tenant_id = ? and code = 'sales'", Long.class, owner.tenantId());
        jdbc.update("update un_sys_tenant_member set department_id = ? where tenant_id = ? and system_member_id = ?",
                departmentId, owner.tenantId(), ownerMemberId);
        jdbc.update("insert into un_sys_tenant_member(system_id, tenant_id, system_member_id, department_id, tenant_admin, status) values (?, ?, ?, ?, 0, 'ACTIVE')",
                owner.systemId(), owner.tenantId(), colleagueMemberId, departmentId);
        long colleagueTenantMemberId = jdbc.queryForObject(
                "select id from un_sys_tenant_member where tenant_id = ? and system_member_id = ?",
                Long.class, owner.tenantId(), colleagueMemberId);
        installColleaguePermissions(owner, colleagueTenantMemberId);

        long moduleId = createAndPublishCustomerModule(owner);
        long firstConfigVersionId = jdbc.queryForObject(
                "select current_version_id from un_cfg_module_publication where module_id = ?", Long.class, moduleId);

        ResponseEntity<Map> created = exchange("/api/runtime/modules/customer/records", HttpMethod.POST, owner.token(), Map.of(
                "recordNumber", "C-001",
                "title", "北京客户",
                "status", "ACTIVE",
                "ownerMemberId", ownerMemberId,
                "departmentId", departmentId,
                "participantMemberIds", List.of(colleagueMemberId),
                "fields", Map.of(
                        "customer_name", "北京客户",
                        "level", "A",
                        "amount", new BigDecimal("1200.50"),
                        "enabled", true)));
        assertThat(created.getStatusCode())
                .withFailMessage("创建业务记录失败：%s", created.getBody())
                .isEqualTo(HttpStatus.OK);
        Map<String, Object> createdData = data(created);
        long recordId = number(createdData.get("id"));
        int initialRecordVersion = ((Number) createdData.get("version")).intValue();
        String commentsPath = "/api/runtime/modules/customer/records/" + recordId + "/comments";
        ResponseEntity<Map> comment = exchange(commentsPath, HttpMethod.POST, owner.token(),
                Map.of("body", "核对客户档案"));
        assertThat(comment.getStatusCode()).isEqualTo(HttpStatus.OK);
        long commentId = number(data(comment).get("id"));
        ResponseEntity<Map> reply = exchange(commentsPath, HttpMethod.POST, owner.token(),
                Map.of("body", "已确认", "parentCommentId", commentId));
        assertThat(reply.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(reply)).containsEntry("parentCommentId", (int) commentId);
        assertThat((List<?>) data(exchange(commentsPath, HttpMethod.GET, owner.token(), null))
                .get("comments")).hasSize(2);
        // 另一个系统的账号不能借评论接口绕过当前记录的详情数据权限。
        assertThat(exchange(commentsPath, HttpMethod.GET, colleagueAccount.token(), null).getStatusCode())
                .isIn(HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND);
        assertThat(exchange(commentsPath, HttpMethod.POST, colleagueAccount.token(),
                Map.of("body", "越权评论", "parentCommentId", commentId)).getStatusCode())
                .isIn(HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND);
        assertThat(count("un_biz_record_comment", "record_id = " + recordId)).isEqualTo(2);
        LocalDateTime createdAt = jdbc.queryForObject(
                "select create_time from un_biz_record where id = ?", LocalDateTime.class, recordId);
        assertThat(number(createdData.get("createdConfigVersionId"))).isEqualTo(firstConfigVersionId);
        assertThat(number(createdData.get("updatedConfigVersionId"))).isEqualTo(firstConfigVersionId);
        assertThat(((List<?>) createdData.get("participantMemberIds")).stream()
                .map(value -> ((Number) value).longValue()).toList()).containsExactly(colleagueMemberId);
        assertThat(jdbc.queryForObject(
                "select value_number from un_biz_record_value where record_id = ? and field_code = 'amount'",
                BigDecimal.class, recordId)).isEqualByComparingTo("1200.50000000");
        assertThat(jdbc.queryForObject(
                "select value_boolean from un_biz_record_value where record_id = ? and field_code = 'enabled'",
                Boolean.class, recordId)).isTrue();
        assertThat(count("un_biz_record_participant", "record_id = " + recordId + " and system_member_id = " + colleagueMemberId)).isOne();
        ResponseEntity<Map> listAfterCreate = exchange(
                "/api/runtime/modules/customer/records", HttpMethod.GET, owner.token(), null);
        Map<String, Object> firstRow = map(((List<?>) data(listAfterCreate).get("records")).get(0));
        assertThat(number(firstRow.get("id"))).isEqualTo(recordId);
        assertThat(number(firstRow.get("ownerMemberId"))).isEqualTo(ownerMemberId);
        assertThat(firstRow).containsEntry("status", "ACTIVE").containsKey("createdAt");

        int recordsBeforeInvalid = count("un_biz_record", "module_id = " + moduleId);
        int validationFailuresBeforeInvalid = count(
                "un_audit_event", "event_code = 'BUSINESS_RECORD_CREATE' and result_code = 'FIELD_VALIDATION_FAILED'");
        ResponseEntity<Map> invalidMissingRequired = exchange(
                "/api/runtime/modules/customer/records", HttpMethod.POST, owner.token(), Map.of(
                        "title", "缺少必填字段", "participantMemberIds", List.of(),
                        "fields", Map.of("level", "A")));
        assertThat(invalidMissingRequired.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(invalidMissingRequired.getBody().get("code")).isEqualTo("FIELD_VALIDATION_FAILED");
        ResponseEntity<Map> invalidDisabledOption = exchange(
                "/api/runtime/modules/customer/records", HttpMethod.POST, owner.token(), Map.of(
                        "title", "停用选项", "participantMemberIds", List.of(),
                        "fields", Map.of("customer_name", "停用选项", "level", "OLD")));
        assertThat(invalidDisabledOption.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        ResponseEntity<Map> invalidReference = exchange(
                "/api/runtime/modules/customer/records", HttpMethod.POST, owner.token(), Map.of(
                        "title", "无效引用", "participantMemberIds", List.of(),
                        "fields", Map.of("customer_name", "无效引用", "level", "A", "parent_customer", 99999999)));
        assertThat(invalidReference.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(invalidReference.getBody().get("message").toString()).contains("parent_customer", "引用记录不存在");
        assertThat(count("un_biz_record", "module_id = " + moduleId)).isEqualTo(recordsBeforeInvalid);
        assertThat(count("un_audit_event", "event_code = 'BUSINESS_RECORD_CREATE' and result_code = 'FIELD_VALIDATION_FAILED'"))
                .isEqualTo(validationFailuresBeforeInvalid + 3);

        publishSecondVersion(owner, moduleId);
        long secondConfigVersionId = jdbc.queryForObject(
                "select current_version_id from un_cfg_module_publication where module_id = ?", Long.class, moduleId);
        assertThat(secondConfigVersionId).isNotEqualTo(firstConfigVersionId);
        ResponseEntity<Map> updated = exchange(
                "/api/runtime/modules/customer/records/" + recordId, HttpMethod.PUT, owner.token(), Map.of(
                        "recordNumber", "C-001", "title", "北京重要客户", "status", "ACTIVE",
                        "ownerMemberId", ownerMemberId, "departmentId", departmentId,
                        "participantMemberIds", List.of(colleagueMemberId), "version", initialRecordVersion,
                        "fields", Map.of("customer_name", "北京重要客户", "level", "A",
                                "amount", new BigDecimal("1500.75"), "enabled", false,
                                "parent_customer", recordId)));
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(number(data(updated).get("createdConfigVersionId"))).isEqualTo(firstConfigVersionId);
        assertThat(number(data(updated).get("updatedConfigVersionId"))).isEqualTo(secondConfigVersionId);
        assertThat(((Number) data(updated).get("version")).intValue()).isEqualTo(initialRecordVersion + 1);
        assertThat(jdbc.queryForObject("select update_time from un_biz_record where id = ?", LocalDateTime.class, recordId))
                .isAfter(createdAt);
        long updatedAuditId = jdbc.queryForObject(
                "select id from un_audit_event where event_code='BUSINESS_RECORD_UPDATED' and object_id=? order by id desc limit 1",
                Long.class, String.valueOf(recordId));
        assertThat(count("un_biz_record_relation", "source_record_id = " + recordId
                + " and target_record_id = " + recordId)).isOne();
        assertThat(count("un_audit_field_change", "audit_event_id = " + updatedAuditId)).isEqualTo(4);
        assertThat(count("un_audit_field_change", "audit_event_id = " + updatedAuditId
                + " and field_code = 'customer_name' and sensitivity = 'SENSITIVE'")).isOne();
        String updateRequestId = jdbc.queryForObject("select request_id from un_audit_event where id=?", String.class,
                updatedAuditId);
        ResponseEntity<Map> filteredAudit = exchange("/api/admin/audit-events?eventCode=BUSINESS_RECORD_UPDATED"
                        + "&actorAccountId=" + owner.accountId() + "&requestId=" + updateRequestId
                        + "&occurredFrom=2000-01-01T00:00:00", HttpMethod.GET, owner.token(), null);
        assertThat(filteredAudit.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) data(filteredAudit).get("events")).singleElement();
        ResponseEntity<Map> updatedAuditDetail = exchange("/api/admin/audit-events/" + updatedAuditId,
                HttpMethod.GET, owner.token(), null);
        assertThat(updatedAuditDetail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) data(updatedAuditDetail).get("fieldChanges")).hasSize(4);
        assertThat(data(updatedAuditDetail)).containsEntry("sensitiveValuesVisible", true);

        ResponseEntity<Map> timeline = exchange(
                "/api/runtime/modules/customer/records/" + recordId + "/timeline", HttpMethod.GET, owner.token(), null);
        assertThat(timeline.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> timelineEntries = (List<?>) data(timeline).get("entries");
        assertThat(timelineEntries).hasSize(2);
        Map<String, Object> latestTimelineEntry = map(timelineEntries.get(0));
        assertThat(latestTimelineEntry).containsEntry("eventCode", "BUSINESS_RECORD_UPDATED")
                .containsEntry("actorDisplayName", "runtime_owner");
        assertThat(number(latestTimelineEntry.get("actorMemberId"))).isEqualTo(ownerMemberId);
        assertThat((List<?>) latestTimelineEntry.get("changes")).anySatisfy(value ->
                assertThat(map(value)).containsEntry("fieldCode", "customer_name")
                        .containsEntry("beforeValue", "北京客户")
                        .containsEntry("afterValue", "北京重要客户"));

        ResponseEntity<Map> staleUpdate = exchange(
                "/api/runtime/modules/customer/records/" + recordId, HttpMethod.PUT, owner.token(), Map.of(
                        "title", "过期修改", "participantMemberIds", List.of(), "version", initialRecordVersion,
                        "fields", Map.of("customer_name", "过期修改", "level", "A")));
        assertThat(staleUpdate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(staleUpdate.getBody().get("code")).isEqualTo("RECORD_VERSION_CONFLICT");
        assertThat(jdbc.queryForObject("select title from un_biz_record where id = ?", String.class, recordId))
                .isEqualTo("北京重要客户");
        assertThat(count("un_biz_record_relation", "source_record_id = " + recordId
                + " and target_record_id = " + recordId)).isOne();
        assertThat(count("un_audit_event", "event_code = 'BUSINESS_RECORD_UPDATE' and result_code = 'RECORD_VERSION_CONFLICT' and system_id=" + owner.systemId()))
                .isOne();

        ResponseEntity<Map> colleaguePlatformLogin = http.postForEntity("/api/auth/login", Map.of(
                "username", "runtime_colleague", "password", "correct-password"), Map.class);
        String colleaguePlatformToken = data(colleaguePlatformLogin).get("accessToken").toString();
        ResponseEntity<Map> colleagueEntered = exchange(
                "/api/systems/" + owner.systemId() + "/enter", HttpMethod.POST, colleaguePlatformToken, null);
        String colleagueToken = ((Map<?, ?>) data(colleagueEntered).get("tokens")).get("accessToken").toString();

        ResponseEntity<Map> colleagueConfiguration = exchange(
                "/api/runtime/modules/customer/configuration", HttpMethod.GET, colleagueToken, null);
        List<String> visibleActions = ((List<?>) ((Map<?, ?>) data(colleagueConfiguration).get("configuration")).get("actions"))
                .stream().map(action -> ((Map<?, ?>) action).get("code").toString()).toList();
        assertThat(visibleActions).containsExactly("LIST", "DETAIL", "UPDATE");
        assertThat(visibleActions).doesNotContain("CREATE");
        List<?> guardedFields = (List<?>) ((Map<?, ?>) data(colleagueConfiguration).get("configuration")).get("fields");
        assertThat(guardedFields).anySatisfy(value -> {
            Map<?, ?> field = (Map<?, ?>) value;
            if ("customer_name".equals(field.get("code"))) {
                assertThat(map(field.get("access"))).containsEntry("readable", true)
                        .containsEntry("writable", false).containsEntry("maskStrategy", "PARTIAL");
            }
        }).anySatisfy(value -> {
            Map<?, ?> field = (Map<?, ?>) value;
            if ("level".equals(field.get("code"))) {
                assertThat(map(field.get("access"))).containsEntry("readable", false);
            }
        });

        ResponseEntity<Map> colleagueList = exchange(
                "/api/runtime/modules/customer/records", HttpMethod.GET, colleagueToken, null);
        assertThat(colleagueList.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) data(colleagueList).get("records")).hasSize(1);
        Map<String, Object> colleagueListFields = map(((Map<?, ?>) ((List<?>) data(colleagueList).get("records")).get(0)).get("fields"));
        assertThat(colleagueListFields).containsEntry("customer_name", "北***户")
                .containsEntry("amount", "***").containsEntry("enabled", false)
                .doesNotContainKey("level");
        ResponseEntity<Map> colleagueDetail = exchange(
                "/api/runtime/modules/customer/records/" + recordId, HttpMethod.GET, colleagueToken, null);
        assertThat(colleagueDetail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(map(data(colleagueDetail).get("fields"))).containsEntry("customer_name", "北***户")
                .doesNotContainKey("level");
        ResponseEntity<Map> applicationChannel = exchange(
                "/api/runtime/modules/customer/records/" + recordId + "/channels/APPLICATION",
                HttpMethod.GET, colleagueToken, null);
        assertThat(applicationChannel.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(map(data(applicationChannel).get("fields"))).containsEntry("customer_name", "北***户")
                .containsEntry("enabled", false).doesNotContainKeys("level", "amount");
        ResponseEntity<Map> fileChannel = exchange(
                "/api/runtime/modules/customer/records/" + recordId + "/channels/FILE",
                HttpMethod.GET, colleagueToken, null);
        assertThat(fileChannel.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(map(data(fileChannel).get("fields"))).containsOnlyKeys("enabled");
        ResponseEntity<Map> invalidChannel = exchange(
                "/api/runtime/modules/customer/records/" + recordId + "/channels/UNKNOWN",
                HttpMethod.GET, colleagueToken, null);
        assertThat(invalidChannel.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        ResponseEntity<Map> colleagueUpdate = exchange(
                "/api/runtime/modules/customer/records/" + recordId, HttpMethod.PUT, colleagueToken, Map.of(
                        "title", "越权修改", "participantMemberIds", List.of(),
                        "version", ((Number) data(updated).get("version")).intValue(),
                        "fields", Map.of("customer_name", "越权修改", "level", "A")));
        assertThat(colleagueUpdate.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        ResponseEntity<Map> colleagueCreate = exchange(
                "/api/runtime/modules/customer/records", HttpMethod.POST, colleagueToken, Map.of(
                        "title", "越权新建", "participantMemberIds", List.of(),
                        "fields", Map.of("customer_name", "越权新建", "level", "A")));
        assertThat(colleagueCreate.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        ResponseEntity<Map> colleagueAudit = exchange(
                "/api/admin/audit-events", HttpMethod.GET, colleagueToken, null);
        assertThat(colleagueAudit.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(jdbc.queryForObject("select title from un_biz_record where id = ?", String.class, recordId))
                .isEqualTo("北京重要客户");

        long otherTenantId = createOtherTenant(owner);
        jdbc.update("insert into un_biz_record(system_id, tenant_id, module_id, created_config_version_id, updated_config_version_id, title, status, owner_member_id, created_by_member_id, updated_by_member_id) values (?, ?, ?, ?, ?, '其他租户数据', 'ACTIVE', ?, ?, ?)",
                owner.systemId(), otherTenantId, moduleId, firstConfigVersionId, firstConfigVersionId,
                ownerMemberId, ownerMemberId, ownerMemberId);
        long otherTenantRecordId = jdbc.queryForObject(
                "select id from un_biz_record where tenant_id = ? and title = '其他租户数据'", Long.class, otherTenantId);
        ResponseEntity<Map> ownerList = exchange(
                "/api/runtime/modules/customer/records?tenantId=" + otherTenantId, HttpMethod.GET, owner.token(), null);
        assertThat((List<?>) data(ownerList).get("records")).hasSize(1);
        ResponseEntity<Map> crossTenantDetail = exchange(
                "/api/runtime/modules/customer/records/" + otherTenantRecordId, HttpMethod.GET, owner.token(), null);
        assertThat(crossTenantDetail.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<Map> parentCreated = exchange(
                "/api/runtime/modules/customer/records", HttpMethod.POST, owner.token(), Map.of(
                        "recordNumber", "C-002", "title", "华北集团", "status", "ACTIVE",
                        "ownerMemberId", ownerMemberId, "departmentId", departmentId,
                        "participantMemberIds", List.of(),
                        "fields", Map.of("customer_name", "华北集团", "level", "A")));
        assertThat(parentCreated.getStatusCode()).isEqualTo(HttpStatus.OK);
        long parentRecordId = number(data(parentCreated).get("id"));
        int parentVersion = ((Number) data(parentCreated).get("version")).intValue();
        ResponseEntity<Map> linkedUpdate = exchange(
                "/api/runtime/modules/customer/records/" + recordId, HttpMethod.PUT, owner.token(), Map.of(
                        "recordNumber", "C-001", "title", "北京重要客户", "status", "ACTIVE",
                        "ownerMemberId", ownerMemberId, "departmentId", departmentId,
                        "participantMemberIds", List.of(colleagueMemberId),
                        "version", ((Number) data(updated).get("version")).intValue(),
                        "fields", Map.of("customer_name", "北京重要客户", "level", "A",
                                "amount", new BigDecimal("1500.75"), "enabled", false,
                                "parent_customer", parentRecordId)));
        int linkedVersion = ((Number) data(linkedUpdate).get("version")).intValue();

        ResponseEntity<Map> archiveImpact = exchange(
                "/api/runtime/modules/customer/records/" + recordId + "/lifecycle-impact?action=ARCHIVE",
                HttpMethod.GET, owner.token(), null);
        assertThat(archiveImpact.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(archiveImpact)).containsEntry("currentState", "ACTIVE")
                .containsEntry("outgoingRelationCount", 1);
        ResponseEntity<Map> archived = exchange(
                "/api/runtime/modules/customer/records/" + recordId + "/archive", HttpMethod.POST, owner.token(),
                Map.of("reason", "客户资料暂时停用", "version", linkedVersion));
        assertThat(archived.getStatusCode()).isEqualTo(HttpStatus.OK);
        int archivedVersion = ((Number) data(archived).get("version")).intValue();
        assertThat(data(archived)).containsEntry("archived", true).containsEntry("deleted", false);
        assertThat((List<?>) data(exchange("/api/runtime/modules/customer/records?lifecycleState=ACTIVE",
                HttpMethod.GET, owner.token(), null)).get("records")).hasSize(1);
        assertThat((List<?>) data(exchange("/api/runtime/modules/customer/records?lifecycleState=ARCHIVED",
                HttpMethod.GET, owner.token(), null)).get("records")).singleElement()
                .satisfies(value -> assertThat(number(map(value).get("id"))).isEqualTo(recordId));

        ResponseEntity<Map> deletedParent = exchange(
                "/api/runtime/modules/customer/records/" + parentRecordId + "/delete", HttpMethod.POST, owner.token(),
                Map.of("reason", "合并重复集团", "version", parentVersion));
        // 父记录仍被子记录引用时必须先处理引用，不能直接删除后留下失效关系。
        assertThat(deletedParent.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(deletedParent.getBody().get("code")).isEqualTo("RECORD_IN_USE");
        assertThat(jdbc.queryForObject("select archived from un_biz_record where id=?", Boolean.class, recordId)).isTrue();

        ResponseEntity<Map> restoredChild = exchange(
                "/api/runtime/modules/customer/records/" + recordId + "/restore", HttpMethod.POST, owner.token(),
                Map.of("reason", "确认集团仍有效", "version", archivedVersion));
        assertThat(restoredChild.getStatusCode()).isEqualTo(HttpStatus.OK);
        int restoredChildVersion = ((Number) data(restoredChild).get("version")).intValue();
        assertThat(data(restoredChild)).containsEntry("archived", false).containsEntry("deleted", false);

        ResponseEntity<Map> deletedChild = exchange(
                "/api/runtime/modules/customer/records/" + recordId + "/delete", HttpMethod.POST, owner.token(),
                Map.of("reason", "进入回收站验收", "version", restoredChildVersion));
        assertThat(deletedChild.getStatusCode()).isEqualTo(HttpStatus.OK);
        int deletedChildVersion = ((Number) data(deletedChild).get("version")).intValue();
        assertThat((List<?>) data(exchange("/api/runtime/modules/customer/records?lifecycleState=DELETED",
                HttpMethod.GET, owner.token(), null)).get("records")).singleElement()
                .satisfies(value -> assertThat(number(map(value).get("id"))).isEqualTo(recordId));
        ResponseEntity<Map> restoredFromRecycle = exchange(
                "/api/runtime/modules/customer/records/" + recordId + "/restore", HttpMethod.POST, owner.token(),
                Map.of("reason", "回收站恢复验收", "version", deletedChildVersion));
        assertThat(restoredFromRecycle.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) data(exchange("/api/runtime/modules/customer/records?lifecycleState=ACTIVE",
                HttpMethod.GET, owner.token(), null)).get("records")).hasSize(2);
        assertThat(count("un_biz_record_state_history", "record_id in (" + recordId + "," + parentRecordId + ")"))
                .isEqualTo(4);
        ResponseEntity<Map> lifecycleTimeline = exchange(
                "/api/runtime/modules/customer/records/" + recordId + "/timeline", HttpMethod.GET, owner.token(), null);
        assertThat(((List<?>) data(lifecycleTimeline).get("entries")).stream()
                .map(value -> map(value).get("eventCode").toString()).toList())
                .contains("BUSINESS_RECORD_ARCHIVED", "BUSINESS_RECORD_DELETED", "BUSINESS_RECORD_RESTORED");

        long opportunityModuleId = createAndPublishOpportunityModule(owner);
        createReferenceField(owner, opportunityModuleId, "self_reference", "本模块关联",
                opportunityModuleId, 30);
        int opportunityRevision = jdbc.queryForObject("select draft_revision from un_cfg_module where id=?",
                Integer.class, opportunityModuleId);
        assertThat(exchange("/api/admin/module-config/modules/" + opportunityModuleId + "/publish",
                HttpMethod.POST, owner.token(), Map.of("expectedDraftRevision", opportunityRevision)).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        configureInvalidCustomerConversion(owner, moduleId, "opportunity");
        ResponseEntity<Map> invalidConversionCheck = exchange(
                "/api/admin/module-config/modules/" + moduleId + "/publication-check",
                HttpMethod.GET, owner.token(), null);
        assertThat(data(invalidConversionCheck).get("valid")).isEqualTo(false);
        assertThat(((List<Map<String, Object>>) data(invalidConversionCheck).get("issues")).stream()
                .map(issue -> issue.get("code"))).contains("CONVERSION_REQUIRED_FIELD_UNMAPPED");
        configureAndPublishCustomerConversion(owner, moduleId, "opportunity");
        ResponseEntity<Map> transferPreview = exchange(
                "/api/runtime/modules/customer/records/" + recordId + "/transfer-preview",
                HttpMethod.POST, owner.token(), Map.of(
                        "version", data(restoredFromRecycle).get("version"),
                        "ownerMemberId", colleagueMemberId,
                        "departmentId", departmentId,
                        "reason", "交由同部门同事跟进"));
        assertThat(transferPreview.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(number(data(transferPreview).get("fromOwnerMemberId"))).isEqualTo(ownerMemberId);
        assertThat(number(data(transferPreview).get("toOwnerMemberId"))).isEqualTo(colleagueMemberId);
        assertThat(data(transferPreview)).containsEntry("toOwnerName", "同部门成员");
        ResponseEntity<Map> transferred = exchange(
                "/api/runtime/modules/customer/records/" + recordId + "/transfer",
                HttpMethod.POST, owner.token(), Map.of(
                        "version", data(restoredFromRecycle).get("version"),
                        "ownerMemberId", colleagueMemberId,
                        "departmentId", departmentId,
                        "reason", "交由同部门同事跟进"));
        assertThat(transferred.getStatusCode()).isEqualTo(HttpStatus.OK);
        int transferredVersion = ((Number) data(transferred).get("version")).intValue();
        assertThat(number(data(transferred).get("ownerMemberId"))).isEqualTo(colleagueMemberId);
        assertThat(count("un_biz_record_owner_history", "record_id=" + recordId
                + " and from_owner_member_id=" + ownerMemberId + " and to_owner_member_id=" + colleagueMemberId)).isOne();
        Map<String, Object> transferredListRow = ((List<?>) data(exchange(
                "/api/runtime/modules/customer/records", HttpMethod.GET, owner.token(), null)).get("records")).stream()
                .map(this::map).filter(row -> number(row.get("id")) == recordId).findFirst().orElseThrow();
        assertThat(number(transferredListRow.get("ownerMemberId"))).isEqualTo(colleagueMemberId);

        // 目标模块改用当前发布选项后，旧来源值必须在预览和执行时被拒绝，且不能留下半条目标记录。
        Map<String, Object> targetDraft = data(exchange(
                "/api/admin/module-config/modules/" + opportunityModuleId + "/draft",
                HttpMethod.GET, owner.token(), null));
        Map<?, ?> levelField = ((List<Map<?, ?>>) targetDraft.get("fields")).stream()
                .filter(value -> "source_level".equals(value.get("code"))).findFirst().orElseThrow();
        String targetFieldPath = "/api/admin/module-config/modules/" + opportunityModuleId
                + "/fields/" + levelField.get("id");
        Map<String, Object> targetFieldBody = new java.util.LinkedHashMap<>();
        targetFieldBody.put("name", "来源客户级别");
        targetFieldBody.put("required", true);
        targetFieldBody.put("sortOrder", 20);
        targetFieldBody.put("status", "ACTIVE");
        targetFieldBody.put("version", levelField.get("version"));
        targetFieldBody.put("config", Map.of("options", List.of(
                Map.of("value", "B", "label", "B级", "status", "ACTIVE"))));
        ResponseEntity<Map> restrictedField = exchange(targetFieldPath, HttpMethod.PUT,
                owner.token(), targetFieldBody);
        assertThat(restrictedField.getStatusCode()).isEqualTo(HttpStatus.OK);
        int targetRevision = jdbc.queryForObject("select draft_revision from un_cfg_module where id=?",
                Integer.class, opportunityModuleId);
        assertThat(exchange("/api/admin/module-config/modules/" + opportunityModuleId + "/publish",
                HttpMethod.POST, owner.token(), Map.of("expectedDraftRevision", targetRevision)).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        ResponseEntity<Map> invalidOptionPreview = exchange(
                "/api/runtime/modules/customer/records/" + recordId + "/conversion-preview",
                HttpMethod.POST, owner.token(), Map.of(
                        "version", transferredVersion, "targetModuleCode", "opportunity"));
        assertThat(invalidOptionPreview.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(invalidOptionPreview)).containsEntry("executable", false);
        assertThat(data(invalidOptionPreview).get("issues").toString()).contains("来源客户级别");
        ResponseEntity<Map> invalidOptionConversion = exchange(
                "/api/runtime/modules/customer/records/" + recordId + "/convert",
                HttpMethod.POST, owner.token(), Map.of(
                        "version", transferredVersion, "targetModuleCode", "opportunity"));
        assertThat(invalidOptionConversion.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(invalidOptionConversion.getBody().get("code")).isEqualTo("CONVERSION_MAPPING_INVALID");
        assertThat(count("un_biz_record", "module_id=" + opportunityModuleId)).isZero();
        assertThat(count("un_biz_record_conversion", "source_record_id=" + recordId)).isZero();
        targetFieldBody.put("version", data(restrictedField).get("version"));
        targetFieldBody.put("config", Map.of("options", List.of(
                Map.of("value", "A", "label", "A级", "status", "ACTIVE"))));
        assertThat(exchange(targetFieldPath, HttpMethod.PUT, owner.token(), targetFieldBody).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        targetRevision = jdbc.queryForObject("select draft_revision from un_cfg_module where id=?",
                Integer.class, opportunityModuleId);
        assertThat(exchange("/api/admin/module-config/modules/" + opportunityModuleId + "/publish",
                HttpMethod.POST, owner.token(), Map.of("expectedDraftRevision", targetRevision)).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        // 关联目标属于商机模块，来源记录却引用客户；预览须按目标当前发布规则拒绝。
        Map<String, Object> sourceDraft = data(exchange(
                "/api/admin/module-config/modules/" + moduleId + "/draft",
                HttpMethod.GET, owner.token(), null));
        Map<?, ?> convertAction = ((List<Map<?, ?>>) sourceDraft.get("actions")).stream()
                .filter(value -> "CONVERT".equals(value.get("code"))).findFirst().orElseThrow();
        assertThat(exchange("/api/admin/module-config/modules/" + moduleId + "/actions/CONVERT",
                HttpMethod.PUT, owner.token(), Map.of("location", "DETAIL_MORE",
                        "version", convertAction.get("version"), "config", Map.of("targets", List.of(Map.of(
                                "moduleCode", "opportunity", "fieldMappings", Map.of(
                                        "opportunity_name", "customer_name", "source_level", "level",
                                        "self_reference", "parent_customer")))))).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        int sourceRevision = jdbc.queryForObject("select draft_revision from un_cfg_module where id=?",
                Integer.class, moduleId);
        assertThat(exchange("/api/admin/module-config/modules/" + moduleId + "/publish",
                HttpMethod.POST, owner.token(), Map.of("expectedDraftRevision", sourceRevision)).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        ResponseEntity<Map> invalidReferencePreview = exchange(
                "/api/runtime/modules/customer/records/" + recordId + "/conversion-preview",
                HttpMethod.POST, owner.token(), Map.of(
                        "version", transferredVersion, "targetModuleCode", "opportunity"));
        assertThat(invalidReferencePreview.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(invalidReferencePreview)).containsEntry("executable", false);
        assertThat(data(invalidReferencePreview).get("issues").toString()).contains("本模块关联");
        ResponseEntity<Map> invalidReferenceConversion = exchange(
                "/api/runtime/modules/customer/records/" + recordId + "/convert",
                HttpMethod.POST, owner.token(), Map.of(
                        "version", transferredVersion, "targetModuleCode", "opportunity"));
        assertThat(invalidReferenceConversion.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(invalidReferenceConversion.getBody().get("code")).isEqualTo("CONVERSION_MAPPING_INVALID");
        assertThat(count("un_biz_record", "module_id=" + opportunityModuleId)).isZero();
        assertThat(count("un_biz_record_conversion", "source_record_id=" + recordId)).isZero();
        configureAndPublishCustomerConversion(owner, moduleId, "opportunity");
        ResponseEntity<Map> conversionPreview = exchange(
                "/api/runtime/modules/customer/records/" + recordId + "/conversion-preview",
                HttpMethod.POST, owner.token(), Map.of(
                        "version", transferredVersion, "targetModuleCode", "opportunity"));
        assertThat(conversionPreview.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(conversionPreview)).containsEntry("targetModuleCode", "opportunity")
                .containsEntry("targetModuleName", "商机").containsEntry("executable", true)
                .containsEntry("alreadyConverted", false);
        assertThat((List<?>) data(conversionPreview).get("mappings")).hasSize(2)
                .allSatisfy(value -> assertThat(map(value)).containsEntry("status", "READY"));
        int recordCountBeforeConversion = count("un_biz_record", "tenant_id=" + owner.tenantId());
        ResponseEntity<Map> converted = exchange(
                "/api/runtime/modules/customer/records/" + recordId + "/convert",
                HttpMethod.POST, owner.token(), Map.of(
                        "version", transferredVersion, "targetModuleCode", "opportunity"));
        assertThat(converted.getStatusCode()).isEqualTo(HttpStatus.OK);
        long conversionId = number(data(converted).get("conversionId"));
        Map<String, Object> targetRecord = map(data(converted).get("targetRecord"));
        long targetRecordId = number(targetRecord.get("id"));
        assertThat(targetRecord).containsEntry("title", "北京重要客户");
        assertThat(number(targetRecord.get("ownerMemberId"))).isEqualTo(colleagueMemberId);
        assertThat(map(targetRecord.get("fields"))).containsEntry("opportunity_name", "北京重要客户")
                .containsEntry("source_level", "A");
        assertThat(count("un_biz_record", "tenant_id=" + owner.tenantId())).isEqualTo(recordCountBeforeConversion + 1);
        assertThat(count("un_biz_record_conversion", "id=" + conversionId + " and source_record_id=" + recordId
                + " and status='SUCCEEDED'")).isOne();
        assertThat(count("un_biz_record_conversion_result", "conversion_id=" + conversionId
                + " and target_module_id=" + opportunityModuleId + " and target_record_id=" + targetRecordId)).isOne();

        ResponseEntity<Map> sourceLinks = exchange(
                "/api/runtime/modules/customer/records/" + recordId + "/conversions",
                HttpMethod.GET, owner.token(), null);
        assertThat((List<?>) data(sourceLinks).get("links")).singleElement().satisfies(value -> {
                assertThat(map(value)).containsEntry("direction", "TARGET")
                        .containsEntry("moduleCode", "opportunity");
                assertThat(number(map(value).get("recordId"))).isEqualTo(targetRecordId);
            });
        ResponseEntity<Map> targetLinks = exchange(
                "/api/runtime/modules/opportunity/records/" + targetRecordId + "/conversions",
                HttpMethod.GET, owner.token(), null);
        assertThat((List<?>) data(targetLinks).get("links")).singleElement().satisfies(value -> {
                assertThat(map(value)).containsEntry("direction", "SOURCE")
                        .containsEntry("moduleCode", "customer");
                assertThat(number(map(value).get("recordId"))).isEqualTo(recordId);
            });

        ResponseEntity<Map> duplicateConversion = exchange(
                "/api/runtime/modules/customer/records/" + recordId + "/convert",
                HttpMethod.POST, owner.token(), Map.of(
                        "version", transferredVersion, "targetModuleCode", "opportunity"));
        assertThat(duplicateConversion.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicateConversion.getBody().get("code")).isEqualTo("RECORD_ALREADY_CONVERTED");
        assertThat(count("un_biz_record", "module_id=" + opportunityModuleId)).isOne();
        assertThat(count("un_biz_record_conversion", "source_record_id=" + recordId)).isOne();

        ResponseEntity<Map> missingMapping = exchange(
                "/api/runtime/modules/customer/records/" + recordId + "/convert",
                HttpMethod.POST, owner.token(), Map.of(
                        "version", transferredVersion, "targetModuleCode", "customer"));
        assertThat(missingMapping.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(missingMapping.getBody().get("code")).isEqualTo("CONVERSION_MAPPING_INVALID");
        assertThat(count("un_biz_record_conversion", "source_record_id=" + recordId)).isOne();
        ResponseEntity<Map> crossSystemDirect = exchange(
                "/api/runtime/modules/customer/records/" + recordId + "/convert",
                HttpMethod.POST, owner.token(), Map.of(
                        "version", transferredVersion, "targetModuleCode", "external_module"));
        assertThat(crossSystemDirect.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(count("un_biz_record", "tenant_id=" + owner.tenantId())).isEqualTo(recordCountBeforeConversion + 1);
        ResponseEntity<Map> colleagueTransferDenied = exchange(
                "/api/runtime/modules/customer/records/" + recordId + "/transfer",
                HttpMethod.POST, colleagueToken, Map.of(
                        "version", transferredVersion, "ownerMemberId", ownerMemberId,
                        "departmentId", departmentId, "reason", "无权限转回"));
        assertThat(colleagueTransferDenied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(count("un_biz_record_owner_history", "record_id=" + recordId)).isOne();

        ResponseEntity<Map> transferConversionTimeline = exchange(
                "/api/runtime/modules/customer/records/" + recordId + "/timeline",
                HttpMethod.GET, owner.token(), null);
        assertThat(((List<?>) data(transferConversionTimeline).get("entries")).stream()
                .map(value -> map(value).get("eventCode").toString()).toList())
                .contains("BUSINESS_RECORD_TRANSFERRED", "BUSINESS_RECORD_CONVERTED");

        assertThat(count("un_audit_event", "system_id = " + owner.systemId()
                + " and event_code = 'BUSINESS_RECORD_CREATED' and result_code = 'SUCCESS'")).isEqualTo(3);
        assertThat(count("un_audit_event", "system_id = " + owner.systemId()
                + " and event_code = 'BUSINESS_RECORD_UPDATED' and result_code = 'SUCCESS'")).isEqualTo(2);
        assertThat(count("un_audit_event", "system_id = " + owner.systemId()
                + " and event_code = 'PERMISSION_CHECK' and result_code = 'PERMISSION_DENIED'"))
                .isGreaterThanOrEqualTo(2);
        assertThat(count("un_audit_event", "event_code = 'AUTHORIZATION_CHANNEL_VIEWED' and object_id = '" + recordId + "'"))
                .isEqualTo(2);

        // 普通成员仅获模块创建、列表和详情权限时，仍可保存并读回本人负责的业务记录。
        Session writerAccount = register("runtime_writer", "writer-system", "runtime-writer@example.com");
        jdbc.update("insert into un_sys_member(system_id, account_id, display_name, status) values (?, ?, '业务录入员', 'ACTIVE')",
                owner.systemId(), writerAccount.accountId());
        long writerMemberId = memberId(owner.systemId(), writerAccount.accountId());
        jdbc.update("insert into un_sys_tenant_member(system_id, tenant_id, system_member_id, department_id, tenant_admin, status) values (?, ?, ?, ?, 0, 'ACTIVE')",
                owner.systemId(), owner.tenantId(), writerMemberId, departmentId);
        long writerTenantMemberId = jdbc.queryForObject(
                "select id from un_sys_tenant_member where tenant_id=? and system_member_id=?", Long.class,
                owner.tenantId(), writerMemberId);
        jdbc.update("insert into un_sys_role(system_id, tenant_id, code, name, status) values (?, ?, 'WRITER', '业务录入', 'ACTIVE')",
                owner.systemId(), owner.tenantId());
        long writerRoleId = jdbc.queryForObject(
                "select id from un_sys_role where tenant_id=? and code='WRITER'", Long.class, owner.tenantId());
        jdbc.update("insert into un_sys_member_role(tenant_id, tenant_member_id, role_id) values (?, ?, ?)",
                owner.tenantId(), writerTenantMemberId, writerRoleId);
        for (String action : List.of("CREATE", "LIST", "DETAIL")) {
            jdbc.update("insert into un_sys_role_permission(system_id, tenant_id, role_id, resource_type, resource_code, action_code, data_scope_type) values (?, ?, ?, 'MODULE', 'customer', ?, 'SELF')",
                    owner.systemId(), owner.tenantId(), writerRoleId, action);
        }
        long platformId = jdbc.queryForObject("select platform_id from un_sys_system where id=?", Long.class,
                owner.systemId());
        for (String fieldCode : List.of("customer_name", "level")) {
            jdbc.update("insert into un_auth_field_policy(context_type, platform_id, system_id, tenant_id, role_id, system_role_id, resource_code, field_code, `channel`, readable, writable) values ('SYSTEM', ?, ?, ?, ?, ?, 'customer', ?, 'PAGE', 1, 1)",
                    platformId, owner.systemId(), owner.tenantId(), writerRoleId, writerRoleId, fieldCode);
        }
        ResponseEntity<Map> writerLogin = http.postForEntity("/api/auth/login", Map.of(
                "username", "runtime_writer", "password", "correct-password"), Map.class);
        String writerPlatformToken = data(writerLogin).get("accessToken").toString();
        ResponseEntity<Map> writerEntered = exchange(
                "/api/systems/" + owner.systemId() + "/enter", HttpMethod.POST, writerPlatformToken, null);
        String writerToken = map(data(writerEntered).get("tokens")).get("accessToken").toString();
        ResponseEntity<Map> writerCreated = exchange(
                "/api/runtime/modules/customer/records", HttpMethod.POST, writerToken, Map.of(
                        "title", "普通成员客户", "ownerMemberId", writerMemberId, "participantMemberIds", List.of(),
                        "fields", Map.of("customer_name", "普通成员客户", "level", "A")));
        assertThat(writerCreated.getStatusCode()).withFailMessage("普通成员创建失败：%s", writerCreated.getBody())
                .isEqualTo(HttpStatus.OK);
        long writerRecordId = number(data(writerCreated).get("id"));
        assertThat(number(data(writerCreated).get("ownerMemberId"))).isEqualTo(writerMemberId);
        assertThat(number(data(writerCreated).get("createdByMemberId"))).isEqualTo(ownerMemberId);
        assertThat(jdbc.queryForObject("select value_text from un_biz_record_value where record_id=? and field_code='customer_name'",
                String.class, writerRecordId)).isEqualTo("普通成员客户");
        assertThat(data(exchange("/api/runtime/modules/customer/records/" + writerRecordId,
                HttpMethod.GET, writerToken, null))).containsEntry("title", "普通成员客户");
        assertThat(((List<?>) data(exchange("/api/runtime/modules/customer/records",
                HttpMethod.GET, writerToken, null)).get("records"))).hasSize(1);
    }

    @Test
    void sameSystemTenantShareKeepsOneSourceRecordIntersectsPermissionsAndRevokesImmediately() {
        Session source = register("share_source", "share-runtime-system", "share-source@example.com");
        long sourceMemberId = memberId(source.systemId(), source.accountId());
        jdbc.update("update un_sys_system set tenant_mode='MULTI' where id=?", source.systemId());
        long moduleId = createAndPublishCustomerModule(source);
        createAndPublishOpportunityModule(source);
        configureAndPublishCustomerConversion(source, moduleId, "opportunity");

        ResponseEntity<Map> created = exchange("/api/runtime/modules/customer/records", HttpMethod.POST,
                source.token(), Map.of("recordNumber", "SH-001", "title", "来源租户客户", "status", "ACTIVE",
                        "ownerMemberId", sourceMemberId, "participantMemberIds", List.of(),
                        "fields", Map.of("customer_name", "来源租户客户", "level", "A", "enabled", true)));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        long recordId = number(data(created).get("id"));
        int recordVersion = ((Number) data(created).get("version")).intValue();

        ResponseEntity<Map> targetCreated = exchange("/api/admin/system/tenants", HttpMethod.POST, source.token(),
                Map.of("code", "cooperate", "name", "协作租户"));
        assertThat(targetCreated.getStatusCode()).isEqualTo(HttpStatus.OK);
        long targetTenantId = number(data(targetCreated).get("id"));
        ResponseEntity<Map> targetEntered = exchange(
                "/api/systems/" + source.systemId() + "/tenants/" + targetTenantId + "/enter",
                HttpMethod.POST, source.token(), Map.of("previousSystemId", source.systemId(),
                        "previousTenantId", source.tenantId()));
        assertThat(targetEntered.getStatusCode()).isEqualTo(HttpStatus.OK);
        String targetToken = map(data(targetEntered).get("tokens")).get("accessToken").toString();

        ResponseEntity<Map> beforeShare = exchange(
                "/api/runtime/modules/customer/records?tenantScope=ALL", HttpMethod.GET, targetToken, null);
        assertThat(beforeShare.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) data(beforeShare).get("records")).isEmpty();

        ResponseEntity<Map> granted = exchange(
                "/api/runtime/modules/customer/records/" + recordId + "/shares", HttpMethod.POST, source.token(),
                Map.of("targetTenantId", targetTenantId, "allowedActions", List.of()));
        assertThat(granted.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> grantData = data(granted);
        long shareId = number(grantData.get("id"));
        assertThat(((List<?>) grantData.get("allowedActions")).stream().map(Object::toString).toList())
                .containsExactly("LIST", "DETAIL");
        assertThat(count("un_biz_record", "id=" + recordId + " and tenant_id=" + source.tenantId())).isOne();
        assertThat(count("un_biz_record", "tenant_id=" + targetTenantId)).isZero();

        ResponseEntity<Map> sharedOnly = exchange(
                "/api/runtime/modules/customer/records?tenantScope=SHARED", HttpMethod.GET, targetToken, null);
        assertThat(sharedOnly.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> sharedRows = (List<?>) data(sharedOnly).get("records");
        assertThat(sharedRows).singleElement();
        Map<String, Object> sharedRow = map(sharedRows.get(0));
        String sourceTenantName = jdbc.queryForObject("select name from un_sys_tenant where id=?", String.class,
                source.tenantId());
        assertThat(number(sharedRow.get("id"))).isEqualTo(recordId);
        assertThat(sharedRow).containsEntry("shared", true)
                .containsEntry("ownedByCurrentTenant", false)
                .containsEntry("dataTenantName", sourceTenantName);
        assertThat(number(sharedRow.get("dataTenantId"))).isEqualTo(source.tenantId());
        assertThat(((List<?>) sharedRow.get("sharedActions")).stream().map(Object::toString).toList())
                .containsExactly("LIST", "DETAIL");
        ResponseEntity<Map> ownOnly = exchange(
                "/api/runtime/modules/customer/records?tenantScope=OWN", HttpMethod.GET, targetToken, null);
        assertThat((List<?>) data(ownOnly).get("records")).isEmpty();

        ResponseEntity<Map> sharedDetail = exchange(
                "/api/runtime/modules/customer/records/" + recordId, HttpMethod.GET, targetToken, null);
        assertThat(sharedDetail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(sharedDetail)).containsEntry("shared", true).containsEntry("shareStatus", "ACTIVE");

        ResponseEntity<Map> defaultReadOnlyUpdate = exchange(
                "/api/runtime/modules/customer/records/" + recordId, HttpMethod.PUT, targetToken,
                Map.of("recordNumber", "SH-001", "title", "不应写入", "status", "ACTIVE",
                        "ownerMemberId", sourceMemberId, "participantMemberIds", List.of(), "version", recordVersion,
                        "fields", Map.of("customer_name", "不应写入", "level", "A", "enabled", true)));
        assertThat(defaultReadOnlyUpdate.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(jdbc.queryForObject("select title from un_biz_record where id=?", String.class, recordId))
                .isEqualTo("来源租户客户");

        // 共享记录不能转化；即便请求显式加入 CONVERT，授权入口也须拒绝。
        ResponseEntity<Map> invalidConversionGrant = exchange(
                "/api/runtime/modules/customer/records/" + recordId + "/shares", HttpMethod.POST, source.token(),
                Map.of("targetTenantId", targetTenantId, "allowedActions", List.of("CONVERT"),
                        "version", ((Number) grantData.get("version")).intValue()));
        assertThat(invalidConversionGrant.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(invalidConversionGrant.getBody().get("code")).isEqualTo("SHARE_ACTION_INVALID");
        ResponseEntity<Map> invalidRangeConversionGrant = exchange(
                "/api/runtime/modules/customer/records/range-shares", HttpMethod.POST, source.token(),
                Map.of("name", "不得跨租户转化", "targetTenantId", targetTenantId,
                        "allowedActions", List.of("CONVERT"),
                        "condition", Map.of("mode", "ALL", "conditions", List.of())));
        assertThat(invalidRangeConversionGrant.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(invalidRangeConversionGrant.getBody().get("code")).isEqualTo("SHARE_ACTION_INVALID");

        ResponseEntity<Map> changedGrant = exchange(
                "/api/runtime/modules/customer/records/" + recordId + "/shares", HttpMethod.POST, source.token(),
                Map.of("targetTenantId", targetTenantId, "allowedActions", List.of("UPDATE"),
                        "version", ((Number) grantData.get("version")).intValue()));
        assertThat(changedGrant.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> changedGrantData = data(changedGrant);
        assertThat(((List<?>) changedGrantData.get("allowedActions")).stream().map(Object::toString).toList())
                .containsExactly("LIST", "DETAIL", "UPDATE");

        ResponseEntity<Map> sharedUpdated = exchange(
                "/api/runtime/modules/customer/records/" + recordId, HttpMethod.PUT, targetToken,
                Map.of("recordNumber", "SH-001", "title", "协作租户获权编辑", "status", "ACTIVE",
                        "ownerMemberId", sourceMemberId, "participantMemberIds", List.of(), "version", recordVersion,
                        "fields", Map.of("customer_name", "协作租户获权编辑", "level", "A", "enabled", true)));
        assertThat(sharedUpdated.getStatusCode())
                .withFailMessage("共享写入失败：%s", sharedUpdated.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(data(sharedUpdated)).containsEntry("shared", true);
        assertThat(number(data(sharedUpdated).get("dataTenantId"))).isEqualTo(source.tenantId());
        assertThat(count("un_biz_record", "id=" + recordId + " and tenant_id=" + source.tenantId())).isOne();
        assertThat(count("un_biz_record", "tenant_id=" + targetTenantId)).isZero();
        String sharedAuditDetail = jdbc.queryForObject(
                "select detail_json from un_audit_event where event_code='BUSINESS_RECORD_UPDATED' and object_id=? order by id desc limit 1",
                String.class, String.valueOf(recordId));
        assertThat(sharedAuditDetail).contains("\"actorTenantId\": " + targetTenantId,
                "\"dataTenantId\": " + source.tenantId(), "\"shareId\": " + shareId);
        assertThat(count("un_biz_tenant_share_usage", "share_id=" + shareId
                + " and action_code='UPDATE' and result_code='SUCCESS'")).isOne();

        int updatedRecordVersion = ((Number) data(sharedUpdated).get("version")).intValue();
        ResponseEntity<Map> sharedConversionPreview = exchange(
                "/api/runtime/modules/customer/records/" + recordId + "/conversion-preview",
                HttpMethod.POST, targetToken, Map.of("version", updatedRecordVersion,
                        "targetModuleCode", "opportunity"));
        assertThat(sharedConversionPreview.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ResponseEntity<Map> sharedConverted = exchange(
                "/api/runtime/modules/customer/records/" + recordId + "/convert",
                HttpMethod.POST, targetToken, Map.of("version", updatedRecordVersion,
                        "targetModuleCode", "opportunity"));
        assertThat(sharedConverted.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(count("un_biz_record", "tenant_id=" + targetTenantId)).isZero();
        assertThat(count("un_biz_record_conversion", "source_record_id=" + recordId)).isZero();
        assertThat(count("un_biz_tenant_share_usage", "share_id=" + shareId
                + " and action_code='CONVERT' and result_code='SUCCESS'")).isZero();

        ResponseEntity<Map> reShare = exchange(
                "/api/runtime/modules/customer/records/" + recordId + "/shares", HttpMethod.POST, targetToken,
                Map.of("targetTenantId", source.tenantId(), "allowedActions", List.of()));
        assertThat(reShare.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        Session foreignSystem = register("share_foreign", "foreign-share-system", "share-foreign@example.com");
        ResponseEntity<Map> crossSystem = exchange(
                "/api/runtime/modules/customer/records/" + recordId + "/shares", HttpMethod.POST, source.token(),
                Map.of("targetTenantId", foreignSystem.tenantId(), "allowedActions", List.of()));
        assertThat(crossSystem.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(crossSystem.getBody().get("code")).isEqualTo("SHARE_TARGET_INVALID");

        ResponseEntity<Map> revoked = exchange(
                "/api/runtime/modules/customer/records/" + recordId + "/shares/" + shareId + "/revoke",
                HttpMethod.POST, source.token(), Map.of("version",
                        ((Number) changedGrantData.get("version")).intValue(), "reason", "协作结束"));
        assertThat(revoked.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(revoked)).containsEntry("status", "REVOKED");
        ResponseEntity<Map> afterRevokeList = exchange(
                "/api/runtime/modules/customer/records?tenantScope=SHARED", HttpMethod.GET, targetToken, null);
        assertThat((List<?>) data(afterRevokeList).get("records")).isEmpty();
        ResponseEntity<Map> afterRevokeDetail = exchange(
                "/api/runtime/modules/customer/records/" + recordId, HttpMethod.GET, targetToken, null);
        assertThat(afterRevokeDetail.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(jdbc.queryForObject("select title from un_biz_record where id=?", String.class, recordId))
                .isEqualTo("协作租户获权编辑");
        assertThat(count("un_biz_record", "tenant_id=" + targetTenantId)).isZero();
        assertThat(count("un_audit_event", "event_code='BUSINESS_RECORD_SHARED' and object_id='" + shareId + "'"))
                .isEqualTo(2);
        assertThat(count("un_audit_event", "event_code='BUSINESS_RECORD_SHARE_REVOKED' and object_id='" + shareId + "'"))
                .isOne();
    }

    @Test
    void advancedListFiltersSortsOnServerAndPersistsAccountScopedViews() {
        Session owner = register("list_view_owner", "list-view-system", "list-view-owner@example.com");
        createAndPublishCustomerModule(owner);
        long ownerMemberId = memberId(owner.systemId(), owner.accountId());

        for (Map<String, ?> sample : List.of(
                Map.of("title", "华北重点客户", "amount", new BigDecimal("800.00"), "level", "A"),
                Map.of("title", "华东普通客户", "amount", new BigDecimal("90.00"), "level", "A"),
                Map.of("title", "华南重点客户", "amount", new BigDecimal("300.00"), "level", "A"))) {
            ResponseEntity<Map> created = exchange("/api/runtime/modules/customer/records", HttpMethod.POST,
                    owner.token(), Map.of(
                            "title", sample.get("title"), "status", "ACTIVE", "ownerMemberId", ownerMemberId,
                            "participantMemberIds", List.of(), "fields", Map.of(
                                    "customer_name", sample.get("title"), "level", sample.get("level"),
                                    "amount", sample.get("amount"), "enabled", true)));
            assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        String filters = URLEncoder.encode("[{\"fieldCode\":\"amount\",\"operator\":\"GTE\",\"value\":\"100\"}]",
                StandardCharsets.UTF_8);
        ResponseEntity<Map> queried = exchangeEncoded("/api/runtime/modules/customer/records?search="
                        + URLEncoder.encode("重点", StandardCharsets.UTF_8) + "&filters=" + filters
                        + "&sortField=amount&sortDirection=DESC&page=1&pageSize=20",
                HttpMethod.GET, owner.token(), null);
        assertThat(queried.getStatusCode()).withFailMessage("高级列表查询失败：%s", queried.getBody())
                .isEqualTo(HttpStatus.OK);
        List<?> rows = (List<?>) data(queried).get("records");
        assertThat(rows).hasSize(2);
        assertThat(rows.stream().map(this::map).map(row -> row.get("title"))).containsExactly("华北重点客户", "华南重点客户");
        assertThat(number(data(queried).get("total"))).isEqualTo(2);

        ResponseEntity<Map> forbiddenFilter = exchangeEncoded(
                "/api/runtime/modules/customer/records?filters=" + URLEncoder.encode(
                        "[{\"fieldCode\":\"unknown_secret\",\"operator\":\"EQ\",\"value\":\"x\"}]",
                        StandardCharsets.UTF_8), HttpMethod.GET, owner.token(), null);
        assertThat(forbiddenFilter.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(forbiddenFilter.getBody().get("code")).isEqualTo("LIST_FILTER_FIELD_FORBIDDEN");

        Map<String, Object> viewBody = Map.of(
                "name", "重点客户", "search", "重点",
                "filters", List.of(Map.of("fieldCode", "amount", "operator", "GTE", "value", "100")),
                "sortField", "amount", "sortDirection", "DESC",
                "visibleFieldCodes", List.of("title", "customer_name", "amount", "status"),
                "fixedFieldCodes", List.of("title"), "pageSize", 50, "defaultView", true);
        ResponseEntity<Map> saved = exchange("/api/runtime/modules/customer/list-views/key_customers",
                HttpMethod.PUT, owner.token(), viewBody);
        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> savedData = data(saved);
        assertThat(savedData).containsEntry("code", "key_customers").containsEntry("name", "重点客户")
                .containsEntry("sortField", "amount").containsEntry("defaultView", true);
        int savedVersion = ((Number) savedData.get("version")).intValue();

        ResponseEntity<Map> views = exchange("/api/runtime/modules/customer/list-views",
                HttpMethod.GET, owner.token(), null);
        assertThat((List<?>) views.getBody().get("data")).singleElement().satisfies(value -> {
            Map<String, Object> restored = map(value);
            assertThat(restored).containsEntry("search", "重点").containsEntry("pageSize", 50);
            assertThat(restored.get("visibleFieldCodes")).isEqualTo(List.of("title", "customer_name", "amount", "status"));
        });
        assertThat(count("un_core_setting", "tenant_id = " + owner.tenantId()
                + " and category = 'RUNTIME_LIST_VIEW' and setting_key like '" + owner.accountId() + ":customer:%'"))
                .isOne();

        Map<String, Object> staleBody = new java.util.LinkedHashMap<>(viewBody);
        staleBody.put("expectedVersion", savedVersion + 1);
        ResponseEntity<Map> stale = exchange("/api/runtime/modules/customer/list-views/key_customers",
                HttpMethod.PUT, owner.token(), staleBody);
        assertThat(stale.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<Map> deleted = exchange("/api/runtime/modules/customer/list-views/key_customers",
                HttpMethod.DELETE, owner.token(), Map.of("expectedVersion", savedVersion));
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<Map> afterDelete = exchange("/api/runtime/modules/customer/list-views",
                HttpMethod.GET, owner.token(), null);
        assertThat((List<?>) afterDelete.getBody().get("data")).isEmpty();
        assertThat(count("un_audit_event", "tenant_id = " + owner.tenantId()
                + " and event_code in ('RUNTIME_LIST_VIEW_SAVED','RUNTIME_LIST_VIEW_DELETED')")).isEqualTo(2);
    }

    @Test
    void commandCenterDiscoversAuthorizedTargetsPersistsStateAndRechecksBeforeExecution() {
        Session owner = register("command_center_owner", "command-center-system", "command-center@example.com");
        createAndPublishCustomerModule(owner);
        long ownerMemberId = memberId(owner.systemId(), owner.accountId());
        ResponseEntity<Map> created = exchange("/api/runtime/modules/customer/records", HttpMethod.POST,
                owner.token(), Map.of(
                        "title", "C31 每日重点客户", "recordNumber", "C31-001", "status", "ACTIVE",
                        "ownerMemberId", ownerMemberId, "participantMemberIds", List.of(),
                        "fields", Map.of("customer_name", "C31 每日重点客户", "level", "A",
                                "amount", new BigDecimal("310.00"), "enabled", true)));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> createdData = data(created);
        long recordId = number(createdData.get("id"));
        int recordVersion = ((Number) createdData.get("version")).intValue();
        String recordCommandId = "record:customer:" + recordId;

        ResponseEntity<Map> discovered = exchangeEncoded("/api/command-center?query="
                        + URLEncoder.encode("C31 每日", StandardCharsets.UTF_8),
                HttpMethod.GET, owner.token(), null);
        assertThat(discovered.getStatusCode()).withFailMessage("命令发现失败：%s", discovered.getBody())
                .isEqualTo(HttpStatus.OK);
        List<?> results = (List<?>) data(discovered).get("results");
        assertThat(results.stream().map(this::map).map(row -> row.get("id")))
                .contains(recordCommandId);
        assertThat(results.stream().map(this::map).map(row -> row.get("groupCode")))
                .contains("RECORD");

        ResponseEntity<Map> saved = exchange("/api/command-center/state", HttpMethod.PUT, owner.token(), Map.of(
                "favoriteIds", List.of("module:customer", recordCommandId, "record:customer:999999"),
                "recentIds", List.of()));
        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> savedData = data(saved);
        Map<String, Object> savedState = map(savedData.get("state"));
        assertThat(savedState.get("favoriteIds")).isEqualTo(List.of("module:customer", recordCommandId));
        assertThat((List<?>) savedData.get("invalidated")).singleElement();
        int stateVersion = ((Number) savedState.get("version")).intValue();

        ResponseEntity<Map> executed = exchange("/api/command-center/execute", HttpMethod.POST, owner.token(),
                Map.of("commandId", recordCommandId));
        assertThat(executed.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> executedData = data(executed);
        Map<String, Object> executedCommand = map(executedData.get("command"));
        assertThat(executedCommand).containsEntry("target", "RUNTIME_RECORD");
        assertThat(number(executedCommand.get("recordId"))).isEqualTo(recordId);
        assertThat(map(executedData.get("state")).get("recentIds")).isEqualTo(List.of(recordCommandId));

        ResponseEntity<Map> stale = exchange("/api/command-center/state", HttpMethod.PUT, owner.token(), Map.of(
                "favoriteIds", List.of("module:customer"), "recentIds", List.of(recordCommandId),
                "expectedVersion", stateVersion));
        assertThat(stale.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(stale.getBody().get("code")).isEqualTo("COMMAND_STATE_VERSION_CONFLICT");

        assertThat(exchange("/api/runtime/modules/customer/records/" + recordId + "/delete",
                HttpMethod.POST, owner.token(), Map.of("version", recordVersion, "reason", "验证命令目标失效"))
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<Map> reconciled = exchange("/api/command-center", HttpMethod.GET, owner.token(), null);
        Map<String, Object> reconciledData = data(reconciled);
        assertThat(map(reconciledData.get("state")).get("favoriteIds")).isEqualTo(List.of("module:customer"));
        assertThat(map(reconciledData.get("state")).get("recentIds")).isEqualTo(List.of());
        assertThat(((List<?>) reconciledData.get("invalidated")).stream().map(this::map)
                .map(row -> row.get("id"))).contains(recordCommandId);
        assertThat(count("un_core_setting", "tenant_id=" + owner.tenantId()
                + " and category='UI_COMMAND_CENTER' and setting_key='" + owner.accountId() + ":command-center'"))
                .isOne();
        assertThat(count("un_audit_event", "tenant_id=" + owner.tenantId()
                + " and event_code in ('COMMAND_CENTER_STATE_SAVED','COMMAND_CENTER_EXECUTED')")).isEqualTo(2);
    }

    @Test
    void publishedPhoneAndCascadeFieldsNormalizeAgainstThePublishedDictionaryPath() {
        Session owner = register("typed_runtime_owner", "typed-runtime-system", "typed-runtime@example.com");

        ResponseEntity<Map> dictionary = exchange("/api/admin/dictionaries", HttpMethod.POST, owner.token(),
                Map.of("code", "customer_stage", "name", "客户阶段", "hierarchical", true));
        long dictionaryId = number(((Map<?, ?>) data(dictionary).get("dictionary")).get("id"));
        long prospectId = number(data(exchange("/api/admin/dictionaries/" + dictionaryId + "/items",
                HttpMethod.POST, owner.token(), Map.of(
                        "code", "prospect", "label", "潜在客户", "sortOrder", 10))).get("id"));
        long contactedId = number(data(exchange("/api/admin/dictionaries/" + dictionaryId + "/items",
                HttpMethod.POST, owner.token(), Map.of(
                        "parentId", prospectId, "code", "contacted", "label", "已联系", "sortOrder", 20))).get("id"));
        int dictionaryRevision = ((Number) data(exchange(
                "/api/admin/dictionaries/" + dictionaryId + "/publication-check",
                HttpMethod.GET, owner.token(), null)).get("draftRevision")).intValue();
        assertThat(exchange("/api/admin/dictionaries/" + dictionaryId + "/publish", HttpMethod.POST,
                owner.token(), Map.of("expectedDraftRevision", dictionaryRevision)).getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<Map> runtimeDictionary = exchange(
                "/api/runtime/dictionaries/by-id/" + dictionaryId, HttpMethod.GET, owner.token(), null);
        assertThat(runtimeDictionary.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) data(runtimeDictionary).get("items")).singleElement();

        long groupId = number(data(exchange("/api/admin/module-config/groups", HttpMethod.POST, owner.token(),
                Map.of("code", "crm", "name", "客户经营", "sortOrder", 10))).get("id"));
        long moduleId = number(((Map<?, ?>) data(exchange("/api/admin/module-config/modules", HttpMethod.POST,
                owner.token(), Map.of("groupId", groupId, "code", "typed_customer", "name", "类型客户")))
                .get("module")).get("id"));
        ResponseEntity<Map> phone = exchange("/api/admin/module-config/modules/" + moduleId + "/fields",
                HttpMethod.POST, owner.token(), Map.of(
                        "code", "phone", "name", "联系电话", "fieldType", "PHONE", "required", true,
                        "searchable", true, "sortOrder", 10, "config", Map.of()));
        assertThat(phone.getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<Map> stage = exchange("/api/admin/module-config/modules/" + moduleId + "/fields",
                HttpMethod.POST, owner.token(), Map.of(
                        "code", "stage", "name", "客户阶段", "fieldType", "CASCADE", "required", true,
                        "searchable", true, "dictionaryId", dictionaryId, "sortOrder", 20,
                        "config", Map.of("maxDepth", 3, "allowIntermediate", false, "saveMode", "FINAL")));
        assertThat(stage.getStatusCode()).isEqualTo(HttpStatus.OK);
        int moduleRevision = jdbc.queryForObject(
                "select draft_revision from un_cfg_module where id=?", Integer.class, moduleId);
        assertThat(exchange("/api/admin/module-config/modules/" + moduleId + "/publish", HttpMethod.POST,
                owner.token(), Map.of("expectedDraftRevision", moduleRevision)).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> created = exchange("/api/runtime/modules/typed_customer/records", HttpMethod.POST,
                owner.token(), Map.of("title", "可用客户", "participantMemberIds", List.of(), "fields", Map.of(
                        "phone", "139-0013-9000", "stage", List.of(prospectId, contactedId))));
        assertThat(created.getStatusCode()).withFailMessage("创建带手机号和级联字段的记录失败：%s", created.getBody())
                .isEqualTo(HttpStatus.OK);
        long recordId = number(data(created).get("id"));
        Map<?, ?> values = (Map<?, ?>) data(created).get("fields");
        assertThat(values.get("phone")).isEqualTo("13900139000");
        assertThat(number(values.get("stage"))).isEqualTo(contactedId);
        assertThat(jdbc.queryForObject(
                "select value_text from un_biz_record_value where record_id=? and field_code='phone'",
                String.class, recordId)).isEqualTo("13900139000");
        assertThat(jdbc.queryForObject(
                "select value_reference_id from un_biz_record_value where record_id=? and field_code='stage'",
                Long.class, recordId)).isEqualTo(contactedId);

        ResponseEntity<Map> invalidPhone = exchange("/api/runtime/modules/typed_customer/records", HttpMethod.POST,
                owner.token(), Map.of("title", "错误手机号", "participantMemberIds", List.of(), "fields", Map.of(
                        "phone", "not-a-phone", "stage", List.of(prospectId, contactedId))));
        assertThat(invalidPhone.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(invalidPhone.getBody().get("message").toString()).contains("phone", "手机号格式不正确");
        ResponseEntity<Map> intermediateStage = exchange("/api/runtime/modules/typed_customer/records", HttpMethod.POST,
                owner.token(), Map.of("title", "中间层", "participantMemberIds", List.of(), "fields", Map.of(
                        "phone", "13900139001", "stage", List.of(prospectId))));
        assertThat(intermediateStage.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(intermediateStage.getBody().get("message").toString()).contains("stage", "只能选择末级字典项");
    }

    @Test
    void moduleImportPreviewsWithoutWritesExecutesInBackgroundAndRollsBackWithoutOverwritingLaterChanges()
            throws Exception {
        Session owner = register("c43_import_owner", "c43-import-system", "c43-import@example.com");
        long moduleId = createAndPublishCustomerModule(owner);
        int recordsBeforePreview = count("un_biz_record", "module_id=" + moduleId);

        long sourceFileId = uploadCsv(owner, "c43-new-customers.csv",
                "title,recordNumber,customer_name,level,amount,enabled\n"
                        + "上海新客户,C43-NEW-001,上海新客户,A,800.50,true\n"
                        + "缺少必填名称,C43-BAD-001,,A,10,true\n");
        ResponseEntity<Map> preview = exchange("/api/runtime/modules/customer/imports/preview", HttpMethod.POST,
                owner.token(), Map.of("sourceFileId", sourceFileId, "conflictPolicy", "ERROR",
                        "mappingName", "C43 新增客户映射", "columnMapping", Map.of(
                                "title", "title", "recordNumber", "recordNumber", "customer_name", "customer_name",
                                "level", "level", "amount", "amount", "enabled", "enabled")));
        assertThat(preview.getStatusCode()).withFailMessage("导入预演失败：%s", preview.getBody())
                .isEqualTo(HttpStatus.OK);
        Map<String, Object> previewData = data(preview);
        long firstBatchId = number(previewData.get("id"));
        assertThat(previewData).containsEntry("status", "PREVIEWED").containsEntry("totalRows", 2)
                .containsEntry("validRows", 1).containsEntry("failedRows", 1);
        assertThat((List<?>) previewData.get("rows")).anySatisfy(value ->
                assertThat(map(value)).containsEntry("operation", "CREATE").containsEntry("status", "READY"))
                .anySatisfy(value -> assertThat(map(value)).containsEntry("status", "ERROR")
                        .containsEntry("errorCode", "FIELD_VALIDATION_FAILED"));
        assertThat(count("un_biz_record", "module_id=" + moduleId)).isEqualTo(recordsBeforePreview);

        ResponseEntity<Map> queued = exchange("/api/runtime/modules/customer/imports/" + firstBatchId + "/execute",
                HttpMethod.POST, owner.token(), Map.of("version", previewData.get("version")));
        assertThat(queued.getStatusCode()).withFailMessage("导入入队失败：%s", queued.getBody())
                .isEqualTo(HttpStatus.OK);
        Map<String, Object> completed = awaitImport(owner, firstBatchId);
        assertThat(completed).containsEntry("status", "COMPLETED").containsEntry("successRows", 1)
                .containsEntry("failedRows", 1);
        assertThat(count("un_biz_record", "module_id=" + moduleId + " and record_number='C43-NEW-001' and deleted=0"))
                .isOne();
        assertThat(count("un_exchange_import_row", "batch_id=" + firstBatchId + " and status='SUCCEEDED'"))
                .isOne();
        ResponseEntity<String> errors = exchangeText(
                "/api/runtime/modules/customer/imports/" + firstBatchId + "/errors.csv", owner.token());
        assertThat(errors.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(errors.getBody()).contains("FIELD_VALIDATION_FAILED", "必填字段不能为空");

        ResponseEntity<Map> rolledBack = exchange("/api/runtime/modules/customer/imports/" + firstBatchId + "/rollback",
                HttpMethod.POST, owner.token(), Map.of("reason", "C43 验证只撤销本批次"));
        assertThat(rolledBack.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(rolledBack)).containsEntry("status", "ROLLED_BACK");
        assertThat(count("un_biz_record", "module_id=" + moduleId + " and record_number='C43-NEW-001' and deleted=1"))
                .isOne();

        ResponseEntity<Map> existing = exchange("/api/runtime/modules/customer/records", HttpMethod.POST,
                owner.token(), Map.of("recordNumber", "C43-EXISTING-001", "title", "导入前客户", "status", "ACTIVE",
                        "participantMemberIds", List.of(), "fields", Map.of(
                                "customer_name", "导入前客户", "level", "A", "amount", 100, "enabled", true)));
        assertThat(existing.getStatusCode()).isEqualTo(HttpStatus.OK);
        long existingId = number(data(existing).get("id"));
        long updateSourceFileId = uploadCsv(owner, "c43-update-customer.csv",
                "title,recordNumber,customer_name,level,amount,enabled\n"
                        + "导入更新客户,C43-EXISTING-001,导入更新客户,A,200,false\n");
        ResponseEntity<Map> updatePreview = exchange("/api/runtime/modules/customer/imports/preview", HttpMethod.POST,
                owner.token(), Map.of("sourceFileId", updateSourceFileId, "conflictPolicy", "UPDATE",
                        "columnMapping", Map.of("title", "title", "recordNumber", "recordNumber",
                                "customer_name", "customer_name", "level", "level", "amount", "amount",
                                "enabled", "enabled")));
        assertThat(updatePreview.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> updatePreviewData = data(updatePreview);
        long updateBatchId = number(updatePreviewData.get("id"));
        assertThat((List<?>) updatePreviewData.get("rows")).singleElement().satisfies(value ->
                assertThat(map(value)).containsEntry("operation", "UPDATE").containsEntry("targetRecordId", (int) existingId));
        assertThat(exchange("/api/runtime/modules/customer/imports/" + updateBatchId + "/execute", HttpMethod.POST,
                owner.token(), Map.of("version", updatePreviewData.get("version"))).getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> updateCompleted = awaitImport(owner, updateBatchId);
        assertThat(updateCompleted).containsEntry("status", "COMPLETED").containsEntry("successRows", 1);

        Map<String, Object> imported = data(exchange("/api/runtime/modules/customer/records/" + existingId,
                HttpMethod.GET, owner.token(), null));
        assertThat(imported).containsEntry("title", "导入更新客户");
        ResponseEntity<Map> laterEdit = exchange("/api/runtime/modules/customer/records/" + existingId,
                HttpMethod.PUT, owner.token(), Map.of("recordNumber", "C43-EXISTING-001", "title", "导入后的人工修改",
                        "status", "ACTIVE", "ownerMemberId", imported.get("ownerMemberId"),
                        "participantMemberIds", imported.get("participantMemberIds"), "version", imported.get("version"),
                        "fields", imported.get("fields")));
        assertThat(laterEdit.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> partialRollback = exchange(
                "/api/runtime/modules/customer/imports/" + updateBatchId + "/rollback", HttpMethod.POST,
                owner.token(), Map.of("reason", "不得覆盖导入后的人工修改"));
        assertThat(partialRollback.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(partialRollback)).containsEntry("status", "ROLLBACK_PARTIAL");
        assertThat((List<?>) data(partialRollback).get("rows")).singleElement().satisfies(value ->
                assertThat(map(value)).containsEntry("status", "ROLLBACK_CONFLICT")
                        .containsEntry("errorCode", "IMPORT_ROLLBACK_VERSION_CONFLICT"));
        assertThat(jdbc.queryForObject("select title from un_biz_record where id=?", String.class, existingId))
                .isEqualTo("导入后的人工修改");
        assertThat(count("un_audit_event", "tenant_id=" + owner.tenantId()
                + " and event_code in ('MODULE_IMPORT_PREVIEWED','MODULE_IMPORT_COMPLETED','MODULE_IMPORT_ROLLED_BACK')"))
                .isGreaterThanOrEqualTo(6);
    }

    @Test
    void moduleExportFreezesSelectionRunsInBackgroundStoresControlledFileAndRejectsOverQuota() throws Exception {
        Session owner = register("c44_export_owner", "c44-export-system", "c44-export@example.com");
        long moduleId = createAndPublishCustomerModule(owner);
        long firstId = 0;
        for (int index = 1; index <= 3; index++) {
            ResponseEntity<Map> created = exchange("/api/runtime/modules/customer/records", HttpMethod.POST,
                    owner.token(), Map.of("recordNumber", "C44-00" + index, "title", "导出客户" + index,
                            "participantMemberIds", List.of(), "fields", Map.of("customer_name", "导出客户" + index,
                                    "level", "A", "amount", index * 100, "enabled", true)));
            assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
            if (index == 1) firstId = number(data(created).get("id"));
        }

        Map<String, Object> selectedRequest = Map.of(
                "lifecycleState", "ACTIVE", "tenantScope", "ALL", "search", "导出客户",
                "filters", List.of(), "sortField", "updatedAt", "sortDirection", "DESC",
                "selectedRecordIds", List.of(firstId),
                "selectedFields", List.of("recordNumber", "title", "customer_name", "amount"));
        ResponseEntity<Map> estimate = exchange("/api/runtime/modules/customer/exports/estimate", HttpMethod.POST,
                owner.token(), selectedRequest);
        assertThat(estimate.getStatusCode()).withFailMessage("导出预估失败：%s", estimate.getBody())
                .isEqualTo(HttpStatus.OK);
        assertThat(data(estimate)).containsEntry("scope", "SELECTED").containsEntry("estimatedRows", 1)
                .containsEntry("maximumRows", 2).containsEntry("withinQuota", true);

        int filesBefore = count("un_file_object", "system_id=" + owner.systemId());
        ResponseEntity<Map> queued = exchange("/api/runtime/modules/customer/exports", HttpMethod.POST,
                owner.token(), selectedRequest);
        assertThat(queued.getStatusCode()).withFailMessage("导出入队失败：%s", queued.getBody())
                .isEqualTo(HttpStatus.OK);
        long batchId = number(data(queued).get("id"));
        assertThat(data(queued)).containsEntry("status", "QUEUED").containsEntry("totalRows", 1);
        Map<String, Object> completed = awaitExport(owner, batchId);
        assertThat(completed).containsEntry("status", "COMPLETED").containsEntry("exportedRows", 1);
        long fileId = number(completed.get("resultFileId"));
        assertThat(count("un_file_object", "id=" + fileId + " and upload_session_id is null and status='ACTIVE' and scan_status='CLEAN'"))
                .isOne();
        assertThat(count("un_file_reference", "file_id=" + fileId + " and owner_type='ACCOUNT' and reference_type='RESULT'"))
                .isOne();
        assertThat(count("un_file_object", "system_id=" + owner.systemId())).isEqualTo(filesBefore + 1);

        HttpHeaders downloadHeaders = new HttpHeaders();
        downloadHeaders.setBearerAuth(owner.token());
        ResponseEntity<byte[]> download = http.exchange(
                "/api/runtime/modules/customer/exports/" + batchId + "/download", HttpMethod.GET,
                new HttpEntity<>(downloadHeaders), byte[].class);
        assertThat(download.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(new String(download.getBody(), StandardCharsets.UTF_8))
                .contains("记录编号", "客户名称", "C44-001", "导出客户1")
                .doesNotContain("C44-002", "导出客户2");
        assertThat(count("un_audit_event", "tenant_id=" + owner.tenantId()
                + " and event_code in ('MODULE_EXPORT_QUEUED','MODULE_EXPORT_COMPLETED','MODULE_EXPORT_DOWNLOADED')"))
                .isEqualTo(3);
        assertThat(count("un_msg_message", "tenant_id=" + owner.tenantId()
                + " and source_type='MODULE_EXPORT' and source_id='batch-" + batchId + "-completed'"))
                .isOne();

        Map<String, Object> overQuotaRequest = Map.of(
                "lifecycleState", "ACTIVE", "tenantScope", "ALL", "search", "导出客户",
                "filters", List.of(), "sortField", "updatedAt", "sortDirection", "DESC",
                "selectedRecordIds", List.of(), "selectedFields", List.of("title", "customer_name"));
        ResponseEntity<Map> quotaEstimate = exchange("/api/runtime/modules/customer/exports/estimate", HttpMethod.POST,
                owner.token(), overQuotaRequest);
        assertThat(quotaEstimate.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(quotaEstimate)).containsEntry("estimatedRows", 3).containsEntry("withinQuota", false);
        int batchesBeforeRejected = count("un_exchange_export_batch", "module_id=" + moduleId);
        int jobsBeforeRejected = count("un_job_background", "source_type='EXPORT_BATCH'");
        ResponseEntity<Map> rejected = exchange("/api/runtime/modules/customer/exports", HttpMethod.POST,
                owner.token(), overQuotaRequest);
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(rejected.getBody().get("code")).isEqualTo("EXPORT_ROW_QUOTA_EXCEEDED");
        assertThat(count("un_exchange_export_batch", "module_id=" + moduleId)).isEqualTo(batchesBeforeRejected);
        assertThat(count("un_job_background", "source_type='EXPORT_BATCH'")).isEqualTo(jobsBeforeRejected);
        assertThat(count("un_file_object", "system_id=" + owner.systemId())).isEqualTo(filesBefore + 1);

        Session limitedHome = register("c44_export_limited", "c44-limited-home", "c44-limited@example.com");
        jdbc.update("insert into un_sys_member(system_id,account_id,display_name,status) values (?,?,?,'ACTIVE')",
                owner.systemId(), limitedHome.accountId(), "受限导出成员");
        long limitedMemberId = memberId(owner.systemId(), limitedHome.accountId());
        jdbc.update("insert into un_sys_tenant_member(system_id,tenant_id,system_member_id,tenant_admin,status) values (?,?,?,0,'ACTIVE')",
                owner.systemId(), owner.tenantId(), limitedMemberId);
        long limitedTenantMemberId = jdbc.queryForObject(
                "select id from un_sys_tenant_member where tenant_id=? and system_member_id=?",
                Long.class, owner.tenantId(), limitedMemberId);
        List<Map<String, Object>> exportPermissions = List.of(
                Map.of("resourceType", "MODULE", "resourceCode", "customer", "actionCode", "LIST",
                        "dataScopeType", "ALL"),
                Map.of("resourceType", "MODULE", "resourceCode", "customer", "actionCode", "EXPORT",
                        "dataScopeType", "ALL"));
        List<Map<String, Object>> exportFields = List.of(
                Map.of("resourceCode", "customer", "fieldCode", "customer_name", "channel", "PAGE",
                        "readable", true, "writable", false, "maskStrategy", "PARTIAL"),
                Map.of("resourceCode", "customer", "fieldCode", "customer_name", "channel", "FILE",
                        "readable", true, "writable", false, "maskStrategy", "PARTIAL"));
        ResponseEntity<Map> roleDraft = exchange("/api/admin/system/authorization/roles", HttpMethod.POST,
                owner.token(), Map.of("code", "export_limited", "name", "受限导出角色",
                        "description", "只允许脱敏导出", "permissions", exportPermissions,
                        "fieldPolicies", exportFields));
        assertThat(roleDraft.getStatusCode()).isEqualTo(HttpStatus.OK);
        long roleId = number(data(roleDraft).get("id"));
        ResponseEntity<Map> rolePublished = exchange(
                "/api/admin/system/authorization/roles/" + roleId + "/publish", HttpMethod.POST,
                owner.token(), Map.of("reason", "C44 验证文件渠道脱敏", "expectedVersion", data(roleDraft).get("version")));
        assertThat(rolePublished.getStatusCode()).isEqualTo(HttpStatus.OK);
        int memberVersion = jdbc.queryForObject("select version from un_sys_tenant_member where id=?",
                Integer.class, limitedTenantMemberId);
        assertThat(exchange("/api/admin/system/authorization/members/" + limitedTenantMemberId + "/assignment",
                HttpMethod.POST, owner.token(), Map.of("roleIds", List.of(roleId), "expectedVersion", memberVersion))
                .getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> limitedLogin = http.postForEntity("/api/auth/login", Map.of(
                "username", "c44_export_limited", "password", "correct-password"), Map.class);
        String limitedPlatformToken = data(limitedLogin).get("accessToken").toString();
        ResponseEntity<Map> limitedEntered = exchange("/api/systems/" + owner.systemId() + "/enter",
                HttpMethod.POST, limitedPlatformToken, null);
        String limitedToken = ((Map<?, ?>) data(limitedEntered).get("tokens")).get("accessToken").toString();
        Map<String, Object> maskedRequest = Map.of(
                "lifecycleState", "ACTIVE", "tenantScope", "OWN", "search", "导出客户1",
                "filters", List.of(), "sortField", "updatedAt", "sortDirection", "DESC",
                "selectedRecordIds", List.of(), "selectedFields", List.of("title", "customer_name"));
        ResponseEntity<Map> maskedEstimate = exchange("/api/runtime/modules/customer/exports/estimate",
                HttpMethod.POST, limitedToken, maskedRequest);
        assertThat(maskedEstimate.getStatusCode()).withFailMessage("受限导出预估失败：%s", maskedEstimate.getBody())
                .isEqualTo(HttpStatus.OK);
        assertThat(((List<?>) data(maskedEstimate).get("maskedFields")).stream().map(Object::toString).toList())
                .containsExactly("customer_name");
        ResponseEntity<Map> maskedQueued = exchange("/api/runtime/modules/customer/exports", HttpMethod.POST,
                limitedToken, maskedRequest);
        assertThat(maskedQueued.getStatusCode()).isEqualTo(HttpStatus.OK);
        long maskedBatchId = number(data(maskedQueued).get("id"));
        Map<String, Object> maskedCompleted = awaitExport(new Session(limitedHome.accountId(), owner.systemId(),
                owner.tenantId(), limitedToken), maskedBatchId);
        assertThat(maskedCompleted).containsEntry("status", "COMPLETED");
        HttpHeaders maskedHeaders = new HttpHeaders();
        maskedHeaders.setBearerAuth(limitedToken);
        String maskedCsv = new String(Objects.requireNonNull(http.exchange(
                "/api/runtime/modules/customer/exports/" + maskedBatchId + "/download", HttpMethod.GET,
                new HttpEntity<>(maskedHeaders), byte[].class).getBody()), StandardCharsets.UTF_8);
        assertThat(maskedCsv).contains("导出客户1", "导***1").doesNotContain("\"导出客户1\",\"导出客户1\"");

        ResponseEntity<Map> revokeQueued = exchange("/api/runtime/modules/customer/exports", HttpMethod.POST,
                limitedToken, maskedRequest);
        assertThat(revokeQueued.getStatusCode()).isEqualTo(HttpStatus.OK);
        long revokeBatchId = number(data(revokeQueued).get("id"));
        int generatedBeforeRevokedJob = count("un_file_object", "system_id=" + owner.systemId()
                + " and upload_session_id is null");
        ResponseEntity<Map> revokedDraft = exchange("/api/admin/system/authorization/roles", HttpMethod.POST,
                owner.token(), Map.of("id", roleId, "code", "export_limited", "name", "受限导出角色",
                        "description", "撤销导出动作", "permissions", List.of(exportPermissions.get(0)),
                        "fieldPolicies", List.of(exportFields.get(0)),
                        "expectedVersion", data(rolePublished).get("version")));
        assertThat(revokedDraft.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(exchange("/api/admin/system/authorization/roles/" + roleId + "/publish", HttpMethod.POST,
                owner.token(), Map.of("reason", "C44 验证排队后权限失效", "expectedVersion",
                        data(revokedDraft).get("version"))).getStatusCode()).isEqualTo(HttpStatus.OK);
        backgroundJobWorker.runOnce();
        assertThat(jdbc.queryForObject("select status from un_exchange_export_batch where id=?",
                String.class, revokeBatchId)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("select result_file_id from un_exchange_export_batch where id=?",
                Long.class, revokeBatchId)).isNull();
        assertThat(jdbc.queryForObject("select status from un_job_background where source_type='EXPORT_BATCH' and source_id=?",
                String.class, String.valueOf(revokeBatchId))).isEqualTo("PERMANENT_FAILED");
        assertThat(count("un_file_object", "system_id=" + owner.systemId() + " and upload_session_id is null"))
                .isEqualTo(generatedBeforeRevokedJob);
        assertThat(count("un_msg_message", "tenant_id=" + owner.tenantId()
                + " and source_type='MODULE_EXPORT' and source_id='batch-" + revokeBatchId + "-failed'"))
                .isOne();
    }

    private long uploadCsv(Session owner, String fileName, String content) throws Exception {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        ResponseEntity<Map> started = exchange("/api/files/uploads", HttpMethod.POST, owner.token(), Map.of(
                "originalName", fileName, "contentType", "text/csv", "expectedSize", bytes.length,
                "expectedSha256", sha));
        assertThat(started.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> session = data(started);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(owner.token());
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM);
        headers.set("X-Upload-Token", session.get("uploadToken").toString());
        ResponseEntity<Map> uploaded = http.exchange("/api/files/uploads/" + session.get("id") + "/content",
                HttpMethod.PUT, new HttpEntity<>(bytes, headers), Map.class);
        assertThat(uploaded.getStatusCode()).withFailMessage("CSV 上传失败：%s", uploaded.getBody())
                .isEqualTo(HttpStatus.OK);
        long fileId = number(data(uploaded).get("id"));
        ResponseEntity<Map> referenced = exchange("/api/files/" + fileId + "/references", HttpMethod.POST,
                owner.token(), Map.of("ownerType", "ACCOUNT", "ownerId", String.valueOf(owner.accountId()),
                        "fieldCode", "managed_files", "referenceType", "DOCUMENT"));
        assertThat(referenced.getStatusCode()).isEqualTo(HttpStatus.OK);
        return fileId;
    }

    private Map<String, Object> awaitImport(Session owner, long batchId) throws InterruptedException {
        Map<String, Object> current = Map.of();
        for (int attempt = 0; attempt < 60; attempt++) {
            backgroundJobWorker.runOnce();
            ResponseEntity<Map> response = exchange("/api/runtime/modules/customer/imports/" + batchId,
                    HttpMethod.GET, owner.token(), null);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            current = data(response);
            if (List.of("COMPLETED", "COMPLETED_WITH_ERRORS", "PERMANENT_FAILED").contains(current.get("status"))) {
                return current;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("导入批次在限定时间内没有完成：" + current);
    }

    private Map<String, Object> awaitExport(Session owner, long batchId) throws InterruptedException {
        Map<String, Object> current = Map.of();
        for (int attempt = 0; attempt < 60; attempt++) {
            backgroundJobWorker.runOnce();
            ResponseEntity<Map> response = exchange("/api/runtime/modules/customer/exports/" + batchId,
                    HttpMethod.GET, owner.token(), null);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            current = data(response);
            if (List.of("COMPLETED", "FAILED").contains(current.get("status"))) return current;
            Thread.sleep(200);
        }
        throw new AssertionError("导出批次在限定时间内没有完成：" + current);
    }

    private ResponseEntity<String> exchangeText(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    /** 命名状态动作的授权独立于普通编辑，发布后可见且执行与审计使用同一动作编码。 */
    @Test
    void publishedNamedStatusActionUsesItsOwnPermissionWithoutOrdinaryEditGrant() {
        Session owner = register("named_action_owner", "named-action-system", "named-action-owner@example.com");
        Session workerAccount = register("named_action_worker", "named-action-worker-system", "named-action-worker@example.com");
        long moduleId = createAndPublishCustomerModule(owner);
        jdbc.update("insert into un_sys_member(system_id, account_id, display_name, status) values (?, ?, '动作操作员', 'ACTIVE')",
                owner.systemId(), workerAccount.accountId());
        long workerMemberId = memberId(owner.systemId(), workerAccount.accountId());
        jdbc.update("insert into un_sys_tenant_member(system_id, tenant_id, system_member_id, tenant_admin, status) values (?, ?, ?, 0, 'ACTIVE')",
                owner.systemId(), owner.tenantId(), workerMemberId);
        long tenantMemberId = jdbc.queryForObject(
                "select id from un_sys_tenant_member where tenant_id=? and system_member_id=?",
                Long.class, owner.tenantId(), workerMemberId);

        ResponseEntity<Map> created = exchange("/api/runtime/modules/customer/records", HttpMethod.POST, owner.token(), Map.of(
                "title", "动作客户", "ownerMemberId", workerMemberId, "participantMemberIds", List.of(),
                "fields", Map.of("customer_name", "动作客户", "level", "A")));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        long recordId = number(data(created).get("id"));
        ResponseEntity<Map> actionCreated = exchange("/api/admin/module-config/modules/" + moduleId + "/actions",
                HttpMethod.POST, owner.token(), Map.of(
                        "name", "标记已确认", "location", "DETAIL_HEADER", "parameterFieldCodes", List.of(),
                        "confirmation", false, "operation", "SET_STATUS", "statusValue", "CONFIRMED"));
        assertThat(actionCreated.getStatusCode()).isEqualTo(HttpStatus.OK);
        String actionCode = data(actionCreated).get("code").toString();

        jdbc.update("insert into un_sys_role(system_id, tenant_id, code, name, status) values (?, ?, 'ACTION_ONLY', '仅可确认', 'ACTIVE')",
                owner.systemId(), owner.tenantId());
        long roleId = jdbc.queryForObject("select id from un_sys_role where tenant_id=? and code='ACTION_ONLY'",
                Long.class, owner.tenantId());
        jdbc.update("insert into un_sys_member_role(tenant_id, tenant_member_id, role_id) values (?, ?, ?)",
                owner.tenantId(), tenantMemberId, roleId);
        for (String permission : List.of("LIST", "DETAIL", actionCode)) {
            jdbc.update("insert into un_sys_role_permission(system_id, tenant_id, role_id, resource_type, resource_code, action_code, data_scope_type) values (?, ?, ?, 'MODULE', 'customer', ?, 'SELF')",
                    owner.systemId(), owner.tenantId(), roleId, permission);
        }
        ResponseEntity<Map> workerLogin = http.postForEntity("/api/auth/login", Map.of(
                "username", "named_action_worker", "password", "correct-password"), Map.class);
        String platformToken = data(workerLogin).get("accessToken").toString();
        ResponseEntity<Map> entered = exchange("/api/systems/" + owner.systemId() + "/enter",
                HttpMethod.POST, platformToken, null);
        String workerToken = map(data(entered).get("tokens")).get("accessToken").toString();
        String detailPath = "/api/runtime/modules/customer/records/" + recordId;
        ResponseEntity<Map> beforePublish = exchange(detailPath, HttpMethod.GET, workerToken, null);
        assertThat(beforePublish.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) data(beforePublish).get("actions")).noneSatisfy(value ->
                assertThat(map(value).get("code")).isEqualTo(actionCode));

        int draftRevision = jdbc.queryForObject("select draft_revision from un_cfg_module where id=?",
                Integer.class, moduleId);
        ResponseEntity<Map> published = exchange("/api/admin/module-config/modules/" + moduleId + "/publish",
                HttpMethod.POST, owner.token(), Map.of("expectedDraftRevision", draftRevision));
        assertThat(published.getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<Map> available = exchange(detailPath, HttpMethod.GET, workerToken, null);
        assertThat(available.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) data(available).get("actions")).anySatisfy(value ->
                assertThat(map(value).get("code")).isEqualTo(actionCode));
        Map<String, Object> workerConfiguration = map(data(exchange(
                "/api/runtime/modules/customer/configuration", HttpMethod.GET, workerToken, null)).get("configuration"));
        assertThat((List<?>) workerConfiguration.get("actions")).noneSatisfy(value ->
                assertThat(map(value).get("code")).isEqualTo("UPDATE"));

        String actionPath = detailPath + "/actions/" + actionCode;
        ResponseEntity<Map> context = exchange(actionPath + "/context", HttpMethod.GET, workerToken, null);
        assertThat(context.getStatusCode()).isEqualTo(HttpStatus.OK);
        int version = ((Number) data(available).get("version")).intValue();
        ResponseEntity<Map> applied = exchange(actionPath, HttpMethod.POST, workerToken,
                Map.of("version", version, "values", Map.of()));
        assertThat(applied.getStatusCode()).withFailMessage("命名动作失败：%s", applied.getBody())
                .isEqualTo(HttpStatus.OK);
        assertThat(data(exchange(detailPath, HttpMethod.GET, workerToken, null))).containsEntry("status", "CONFIRMED");
        assertThat(jdbc.queryForObject("select status from un_biz_record where id=?", String.class, recordId))
                .isEqualTo("CONFIRMED");
        assertThat(count("un_biz_record_state_history", "record_id=" + recordId + " and to_status='CONFIRMED'")).isOne();
        assertThat(count("un_audit_event", "object_id='" + recordId
                + "' and event_code='BUSINESS_RECORD_UPDATED' and result_code='SUCCESS'")).isOne();
        ResponseEntity<Map> stale = exchange(actionPath, HttpMethod.POST, workerToken,
                Map.of("version", version, "values", Map.of()));
        assertThat(stale.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(jdbc.queryForObject("select status from un_biz_record where id=?", String.class, recordId))
                .isEqualTo("CONFIRMED");

        ResponseEntity<Map> amountAction = exchange("/api/admin/module-config/modules/" + moduleId + "/actions",
                HttpMethod.POST, owner.token(), Map.of(
                        "name", "录入确认金额", "location", "DETAIL_HEADER",
                        "parameterFieldCodes", List.of("amount"), "confirmation", false,
                        "operation", "UPDATE_FIELDS"));
        assertThat(amountAction.getStatusCode()).isEqualTo(HttpStatus.OK);
        String amountActionCode = data(amountAction).get("code").toString();
        jdbc.update("insert into un_sys_role_permission(system_id, tenant_id, role_id, resource_type, resource_code, action_code, data_scope_type) values (?, ?, ?, 'MODULE', 'customer', ?, 'SELF')",
                owner.systemId(), owner.tenantId(), roleId, amountActionCode);
        // 测试直接写角色表，需同步推进授权缓存世代；正式后台发布会自动完成该步骤。
        jdbc.update("insert into un_core_cache_epoch(context_key,cache_namespace,epoch_value,reason) values (?,'AUTHORIZATION',1,'test-action-grant') on duplicate key update epoch_value=epoch_value+1,reason='test-action-grant'",
                "system:" + owner.systemId() + ":tenant:" + owner.tenantId());
        int amountDraftRevision = jdbc.queryForObject("select draft_revision from un_cfg_module where id=?",
                Integer.class, moduleId);
        assertThat(exchange("/api/admin/module-config/modules/" + moduleId + "/publish", HttpMethod.POST,
                owner.token(), Map.of("expectedDraftRevision", amountDraftRevision)).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        // 系统会话携带动作授权快照；新增角色动作后重新进入系统取得新会话。
        workerToken = map(data(exchange("/api/systems/" + owner.systemId() + "/enter",
                HttpMethod.POST, platformToken, null)).get("tokens")).get("accessToken").toString();
        Map<String, Object> refreshedConfiguration = map(data(exchange(
                "/api/runtime/modules/customer/configuration", HttpMethod.GET, workerToken, null)).get("configuration"));
        assertThat((List<?>) refreshedConfiguration.get("actions")).anySatisfy(value ->
                assertThat(map(value).get("code")).isEqualTo(amountActionCode));
        String amountPath = detailPath + "/actions/" + amountActionCode;
        assertThat(exchange(amountPath + "/context", HttpMethod.GET, workerToken, null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        long platformId = jdbc.queryForObject("select platform_id from un_sys_system where id=?",
                Long.class, owner.systemId());
        jdbc.update("insert into un_auth_field_policy(context_type,platform_id,system_id,tenant_id,role_id,system_role_id,resource_code,field_code,`channel`,readable,writable) values ('SYSTEM',?,?,?,?,?,?,?,'PAGE',1,1)",
                platformId, owner.systemId(), owner.tenantId(), roleId, roleId, "customer", "amount");
        workerToken = map(data(exchange("/api/systems/" + owner.systemId() + "/enter",
                HttpMethod.POST, platformToken, null)).get("tokens")).get("accessToken").toString();
        Map<String, Object> permittedConfiguration = map(data(exchange(
                "/api/runtime/modules/customer/configuration", HttpMethod.GET, workerToken, null)).get("configuration"));
        assertThat((List<?>) permittedConfiguration.get("actions")).anySatisfy(value ->
                assertThat(map(value).get("code")).isEqualTo(amountActionCode));
        ResponseEntity<Map> amountContext = exchange(amountPath + "/context", HttpMethod.GET, workerToken, null);
        assertThat(amountContext.getStatusCode()).withFailMessage("字段动作上下文失败：%s", amountContext.getBody())
                .isEqualTo(HttpStatus.OK);
        int currentVersion = ((Number) data(exchange(detailPath, HttpMethod.GET, workerToken, null))
                .get("version")).intValue();
        ResponseEntity<Map> amountApplied = exchange(amountPath, HttpMethod.POST, workerToken,
                Map.of("version", currentVersion, "values", Map.of("amount", new BigDecimal("42.50"))));
        assertThat(amountApplied.getStatusCode()).withFailMessage("字段动作失败：%s", amountApplied.getBody())
                .isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject(
                "select value_number from un_biz_record_value where record_id=? and field_code='amount'",
                BigDecimal.class, recordId)).isEqualByComparingTo("42.50000000");
        assertThat(jdbc.queryForObject("select status from un_biz_record where id=?", String.class, recordId))
                .isEqualTo("CONFIRMED");
        long flowId = publishApprovalActionFlow(owner, "named_action_approval_flow");
        long amountActionId = jdbc.queryForObject("select id from un_cfg_module_action where module_id=? and code=?",
                Long.class, moduleId, amountActionCode);
        ResponseEntity<Map> binding = exchange("/api/flow-bindings/publish", HttpMethod.POST, owner.token(), Map.of(
                "moduleId", moduleId, "actionId", amountActionId, "triggerEvent", "MANUAL", "flowId", flowId,
                "executionMode", "AFTER_APPROVAL", "priorityOrder", 0,
                "mutuallyExclusive", true, "replaceExisting", false));
        assertThat(binding.getStatusCode()).withFailMessage("命名动作流程绑定失败：%s", binding.getBody())
                .isEqualTo(HttpStatus.OK);
        HttpHeaders actionHeaders = new HttpHeaders();
        actionHeaders.setBearerAuth(workerToken);
        actionHeaders.set("Idempotency-Key", "named-amount-approval");
        actionHeaders.set("Prefer", "return=action");
        ResponseEntity<Map> pendingResponse = http.exchange(amountPath, HttpMethod.POST,
                new HttpEntity<>(Map.of("version", currentVersion + 1,
                        "values", Map.of("amount", new BigDecimal("55.00"))), actionHeaders), Map.class);
        assertThat(pendingResponse.getStatusCode()).withFailMessage("命名动作审批发起失败：%s", pendingResponse.getBody())
                .isEqualTo(HttpStatus.OK);
        Map<String, Object> pending = data(pendingResponse);
        assertThat(pending).containsEntry("status", "PENDING_APPROVAL");
        assertThat(jdbc.queryForObject("select title from un_flow_instance where id=?", String.class,
                number(pending.get("flowInstanceId")))).isEqualTo("named_action_approval_flow · 业务动作");
        assertThat(jdbc.queryForObject("select value_number from un_biz_record_value where record_id=? and field_code='amount'",
                BigDecimal.class, recordId)).isEqualByComparingTo("42.50000000");
        ResponseEntity<Map> progress = exchange("/api/flow-runtime/business-actions/customer/"
                + pending.get("idempotencyRecordId"), HttpMethod.GET, workerToken, null);
        assertThat(progress.getStatusCode()).withFailMessage("发起人缺普通编辑权时仍须能查看命名动作审批进度：%s", progress.getBody())
                .isEqualTo(HttpStatus.OK);
        assertThat(data(progress)).containsEntry("businessStatus", "PENDING_APPROVAL");
    }

    private long createAndPublishCustomerModule(Session owner) {
        long groupId = number(data(exchange("/api/admin/module-config/groups", HttpMethod.POST, owner.token(),
                Map.of("code", "sales", "name", "销售管理", "sortOrder", 10))).get("id"));
        Map<String, Object> moduleDraft = data(exchange("/api/admin/module-config/modules", HttpMethod.POST, owner.token(),
                Map.of("groupId", groupId, "code", "customer", "name", "客户")));
        long moduleId = number(((Map<?, ?>) moduleDraft.get("module")).get("id"));
        createField(owner, moduleId, "customer_name", "客户名称", "TEXT", true, 10, Map.of());
        createField(owner, moduleId, "level", "客户级别", "SINGLE_SELECT", true, 20, Map.of("options", List.of(
                Map.of("value", "A", "label", "A级", "status", "ACTIVE"),
                Map.of("value", "OLD", "label", "旧级别", "status", "DISABLED"))));
        createField(owner, moduleId, "amount", "金额", "MONEY", false, 30,
                Map.of("currency", "CNY", "precision", 2));
        createField(owner, moduleId, "enabled", "是否有效", "BOOLEAN", false, 40, Map.of());
        createReferenceField(owner, moduleId, "parent_customer", "上级客户", moduleId, 50);
        int revision = jdbc.queryForObject("select draft_revision from un_cfg_module where id = ?", Integer.class, moduleId);
        ResponseEntity<Map> published = exchange("/api/admin/module-config/modules/" + moduleId + "/publish",
                HttpMethod.POST, owner.token(), Map.of("expectedDraftRevision", revision));
        assertThat(published.getStatusCode()).isEqualTo(HttpStatus.OK);
        return moduleId;
    }

    private long createAndPublishOpportunityModule(Session owner) {
        long groupId = jdbc.queryForObject("select id from un_cfg_module_group where system_id=? and code='sales'",
                Long.class, owner.systemId());
        Map<String, Object> moduleDraft = data(exchange("/api/admin/module-config/modules", HttpMethod.POST,
                owner.token(), Map.of("groupId", groupId, "code", "opportunity", "name", "商机")));
        long moduleId = number(((Map<?, ?>) moduleDraft.get("module")).get("id"));
        createField(owner, moduleId, "opportunity_name", "商机名称", "TEXT", true, 10, Map.of());
        createField(owner, moduleId, "source_level", "来源客户级别", "SINGLE_SELECT", true, 20,
                Map.of("options", List.of(Map.of("value", "A", "label", "A级", "status", "ACTIVE"))));
        int revision = jdbc.queryForObject("select draft_revision from un_cfg_module where id=?", Integer.class, moduleId);
        ResponseEntity<Map> published = exchange("/api/admin/module-config/modules/" + moduleId + "/publish",
                HttpMethod.POST, owner.token(), Map.of("expectedDraftRevision", revision));
        assertThat(published.getStatusCode()).isEqualTo(HttpStatus.OK);
        return moduleId;
    }

    private void configureAndPublishCustomerConversion(Session owner, long moduleId, String targetModuleCode) {
        Map<String, Object> draft = data(exchange(
                "/api/admin/module-config/modules/" + moduleId + "/draft", HttpMethod.GET, owner.token(), null));
        Map<?, ?> action = ((List<Map<?, ?>>) draft.get("actions")).stream()
                .filter(value -> "CONVERT".equals(value.get("code"))).findFirst().orElseThrow();
        ResponseEntity<Map> configured = exchange(
                "/api/admin/module-config/modules/" + moduleId + "/actions/CONVERT", HttpMethod.PUT,
                owner.token(), Map.of("location", "DETAIL_MORE", "version", action.get("version"),
                        "config", Map.of("targets", List.of(Map.of(
                                "moduleCode", targetModuleCode,
                                "fieldMappings", Map.of(
                                        "opportunity_name", "customer_name",
                                        "source_level", "level"))))));
        assertThat(configured.getStatusCode()).isEqualTo(HttpStatus.OK);
        int revision = jdbc.queryForObject("select draft_revision from un_cfg_module where id=?", Integer.class, moduleId);
        ResponseEntity<Map> published = exchange("/api/admin/module-config/modules/" + moduleId + "/publish",
                HttpMethod.POST, owner.token(), Map.of("expectedDraftRevision", revision));
        assertThat(published.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private void configureInvalidCustomerConversion(Session owner, long moduleId, String targetModuleCode) {
        Map<String, Object> draft = data(exchange(
                "/api/admin/module-config/modules/" + moduleId + "/draft", HttpMethod.GET, owner.token(), null));
        Map<?, ?> action = ((List<Map<?, ?>>) draft.get("actions")).stream()
                .filter(value -> "CONVERT".equals(value.get("code"))).findFirst().orElseThrow();
        ResponseEntity<Map> configured = exchange(
                "/api/admin/module-config/modules/" + moduleId + "/actions/CONVERT", HttpMethod.PUT,
                owner.token(), Map.of("location", "DETAIL_MORE", "version", action.get("version"),
                        "config", Map.of("targets", List.of(Map.of(
                                "moduleCode", targetModuleCode,
                                "fieldMappings", Map.of("opportunity_name", "customer_name"))))));
        assertThat(configured.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private void createField(
            Session owner, long moduleId, String code, String name, String type, boolean required, int order, Object config) {
        ResponseEntity<Map> response = exchange("/api/admin/module-config/modules/" + moduleId + "/fields",
                HttpMethod.POST, owner.token(), Map.of("code", code, "name", name, "fieldType", type,
                        "required", required, "sortOrder", order, "config", config));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private void createReferenceField(
            Session owner, long moduleId, String code, String name, long referenceModuleId, int order) {
        ResponseEntity<Map> response = exchange("/api/admin/module-config/modules/" + moduleId + "/fields",
                HttpMethod.POST, owner.token(), Map.of("code", code, "name", name, "fieldType", "REFERENCE",
                        "required", false, "referenceModuleId", referenceModuleId,
                        "sortOrder", order, "config", Map.of()));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private void publishSecondVersion(Session owner, long moduleId) {
        Map<String, Object> draft = data(exchange(
                "/api/admin/module-config/modules/" + moduleId + "/draft", HttpMethod.GET, owner.token(), null));
        Map<?, ?> field = ((List<Map<?, ?>>) draft.get("fields")).stream()
                .filter(value -> "customer_name".equals(value.get("code"))).findFirst().orElseThrow();
        exchange("/api/admin/module-config/modules/" + moduleId + "/fields/" + field.get("id"), HttpMethod.PUT,
                owner.token(), Map.of("name", "客户全称", "required", true, "sortOrder", 10,
                        "status", "ACTIVE", "config", Map.of(), "version", field.get("version")));
        int revision = jdbc.queryForObject("select draft_revision from un_cfg_module where id = ?", Integer.class, moduleId);
        ResponseEntity<Map> published = exchange("/api/admin/module-config/modules/" + moduleId + "/publish",
                HttpMethod.POST, owner.token(), Map.of("expectedDraftRevision", revision));
        assertThat(published.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private void installColleaguePermissions(Session owner, long colleagueTenantMemberId) {
        jdbc.update("insert into un_sys_role(system_id, tenant_id, code, name, status) values (?, ?, 'COLLEAGUE', '普通成员', 'ACTIVE')",
                owner.systemId(), owner.tenantId());
        long roleId = jdbc.queryForObject(
                "select id from un_sys_role where tenant_id = ? and code = 'COLLEAGUE'", Long.class, owner.tenantId());
        jdbc.update("insert into un_sys_member_role(tenant_id, tenant_member_id, role_id) values (?, ?, ?)",
                owner.tenantId(), colleagueTenantMemberId, roleId);
        jdbc.update("insert into un_sys_role_permission(system_id, tenant_id, role_id, resource_type, resource_code, action_code, data_scope_type) values (?, ?, ?, 'MODULE', 'customer', 'LIST', 'DEPARTMENT')",
                owner.systemId(), owner.tenantId(), roleId);
        // 查看范围按负责人当前部门计算；关联参与人本身不扩张数据权限。
        jdbc.update("insert into un_sys_role_permission(system_id, tenant_id, role_id, resource_type, resource_code, action_code, data_scope_type) values (?, ?, ?, 'MODULE', 'customer', 'DETAIL', 'DEPARTMENT')",
                owner.systemId(), owner.tenantId(), roleId);
        jdbc.update("insert into un_sys_role_permission(system_id, tenant_id, role_id, resource_type, resource_code, action_code, data_scope_type) values (?, ?, ?, 'MODULE', 'customer', 'UPDATE', 'SELF')",
                owner.systemId(), owner.tenantId(), roleId);
        long platformId = jdbc.queryForObject("select platform_id from un_sys_system where id=?", Long.class,
                owner.systemId());
        jdbc.update("insert into un_auth_field_policy(context_type,platform_id,system_id,tenant_id,role_id,system_role_id,resource_code,field_code,`channel`,readable,writable,mask_strategy) values ('SYSTEM',?,?,?,?,?,?,?,'PAGE',1,0,'PARTIAL')",
                platformId, owner.systemId(), owner.tenantId(), roleId, roleId, "customer", "customer_name");
        jdbc.update("insert into un_auth_field_policy(context_type,platform_id,system_id,tenant_id,role_id,system_role_id,resource_code,field_code,`channel`,readable,writable,mask_strategy) values ('SYSTEM',?,?,?,?,?,?,?,'PAGE',1,0,'FULL')",
                platformId, owner.systemId(), owner.tenantId(), roleId, roleId, "customer", "amount");
        jdbc.update("insert into un_auth_field_policy(context_type,platform_id,system_id,tenant_id,role_id,system_role_id,resource_code,field_code,`channel`,readable,writable) values ('SYSTEM',?,?,?,?,?,?,?,'PAGE',1,0)",
                platformId, owner.systemId(), owner.tenantId(), roleId, roleId, "customer", "enabled");
        jdbc.update("insert into un_auth_field_policy(context_type,platform_id,system_id,tenant_id,role_id,system_role_id,resource_code,field_code,`channel`,readable,writable,mask_strategy) values ('SYSTEM',?,?,?,?,?,?,?,'APPLICATION',1,0,'PARTIAL')",
                platformId, owner.systemId(), owner.tenantId(), roleId, roleId, "customer", "customer_name");
        jdbc.update("insert into un_auth_field_policy(context_type,platform_id,system_id,tenant_id,role_id,system_role_id,resource_code,field_code,`channel`,readable,writable) values ('SYSTEM',?,?,?,?,?,?,?,'APPLICATION',1,0)",
                platformId, owner.systemId(), owner.tenantId(), roleId, roleId, "customer", "enabled");
        jdbc.update("insert into un_auth_field_policy(context_type,platform_id,system_id,tenant_id,role_id,system_role_id,resource_code,field_code,`channel`,readable,writable) values ('SYSTEM',?,?,?,?,?,?,?,'FILE',1,0)",
                platformId, owner.systemId(), owner.tenantId(), roleId, roleId, "customer", "enabled");
    }

    private long createOtherTenant(Session owner) {
        jdbc.update("insert into un_sys_tenant(system_id, code, name, is_main, main_marker, creator_account_id, status) values (?, 'other', '其他租户', 0, null, ?, 'ACTIVE')",
                owner.systemId(), owner.accountId());
        return jdbc.queryForObject("select id from un_sys_tenant where system_id = ? and code = 'other'",
                Long.class, owner.systemId());
    }

    private Session register(String username, String systemCode, String email) {
        // 历史用例的系统代号含连字符；注册请求使用当前统一编码规则，展示名仍保留原测试语义。
        ResponseEntity<Map> response = http.postForEntity("/api/auth/register", Map.of(
                "username", username, "password", "correct-password", "displayName", username, "email", email,
                "systemName", systemCode, "systemCode", systemCode.replace('-', '_')), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> result = data(response);
        return new Session(number(result.get("accountId")), number(result.get("systemId")), number(result.get("tenantId")),
                ((Map<?, ?>) result.get("tokens")).get("accessToken").toString());
    }

    private long memberId(long systemId, long accountId) {
        return jdbc.queryForObject(
                "select id from un_sys_member where system_id = ? and account_id = ?", Long.class, systemId, accountId);
    }

    private ResponseEntity<Map> exchange(String path, HttpMethod method, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return http.exchange(path, method, new HttpEntity<>(body, headers), Map.class);
    }

    private ResponseEntity<Map> exchangeEncoded(String path, HttpMethod method, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return http.exchange(URI.create(http.getRootUri() + path), method, new HttpEntity<>(body, headers), Map.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(ResponseEntity<Map> response) {
        return (Map<String, Object>) response.getBody().get("data");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    private long number(Object value) {
        return ((Number) value).longValue();
    }

    private int count(String table, String condition) {
        return jdbc.queryForObject("select count(*) from " + table + " where " + condition, Integer.class);
    }

    private record Session(long accountId, long systemId, long tenantId, String token) {
    }
}
