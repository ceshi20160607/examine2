package com.unique.unexamine.print.manage;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class PrintHttpTest {
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
    }

    @Autowired private MockMvc http;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void designsPreviewsPublishesAndPrintsOnlyAuthorizedRecordSnapshot() throws Exception {
        Session owner = register("print_owner", "print_system", "print-owner@example.com");
        PublishedModule customer = publishCustomerModule(owner);
        Map<String, Object> record = ok("/api/runtime/modules/customer/records", HttpMethod.POST, owner.token(), Map.of(
                "recordNumber", "PRINT-001", "title", "北辰合同", "status", "ACTIVE",
                "participantMemberIds", List.of(), "fields", Map.of(
                        "customer_name", "北辰制造", "contract_amount", 128000,
                        "owner_note", "仅授权人员可见", "delivery_date", "2026-09-30",
                        "status_label", "待签章")));
        long recordId = number(record.get("id"));

        Map<String, Object> admin = ok("/api/print/admin", HttpMethod.GET, owner.token(), null);
        assertThat(list(admin.get("modules"))).singleElement().satisfies(module -> {
            assertThat(module).containsEntry("code", "customer").containsEntry("publishedVersionNumber", 1);
            assertThat(list(module.get("fields")).stream().map(field -> field.get("code")).toList())
                    .containsExactly("contract_amount", "customer_name", "delivery_date", "owner_note", "status_label");
        });

        Map<String, Object> template = ok("/api/print/admin/templates", HttpMethod.POST, owner.token(), Map.of(
                "moduleId", customer.moduleId(), "code", "customer_contract", "name", "客户合同打印单",
                "pageSize", "A4", "orientation", "PORTRAIT", "layout", Map.of(
                        "header", "北辰业务系统 · 客户合同", "footer", "系统生成文件，仅限获权人员使用",
                        "signatureLabel", "审批签章", "fieldCodes", List.of(
                                "customer_name", "contract_amount", "delivery_date", "status_label"),
                        "detailFieldCodes", List.of("owner_note"), "rowsPerPage", 4)));
        long templateId = number(template.get("id"));
        assertThat(template).containsEntry("draftRevision", 1).containsEntry("status", "DRAFT");

        Map<String, Object> preview = ok("/api/print/admin/templates/" + templateId + "/preview",
                HttpMethod.POST, owner.token(), Map.of("sampleRecordId", recordId));
        assertThat(preview).containsEntry("draftRevision", 1).containsEntry("moduleVersionNumber", 1);
        assertThat(preview.get("previewHash").toString()).hasSize(64);
        assertThat(list(preview.get("pages"))).hasSize(2);
        assertThat(list(preview.get("pages"))).extracting(page -> number(page.get("pageNumber")))
                .containsExactly(1L, 2L);
        assertThat(((List<?>) preview.get("visibleFieldCodes")).stream().map(String::valueOf).toList()).containsExactly(
                "customer_name", "contract_amount", "delivery_date", "status_label", "owner_note");
        assertThat((List<?>) preview.get("omittedFieldCodes")).isEmpty();

        Map<String, Object> publication = ok("/api/print/admin/templates/" + templateId + "/publish",
                HttpMethod.POST, owner.token(), Map.of("expectedDraftRevision", 1));
        long versionId = number(publication.get("versionId"));
        assertThat(publication).containsEntry("versionNumber", 1)
                .containsEntry("snapshotHash", preview.get("previewHash"));
        assertThatThrownBy(() -> jdbc.update("update print_template_version set version_number=2 where id=?", versionId))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("immutable");

        List<Map<String, Object>> runtimeTemplates = okList(
                "/api/print/runtime/modules/customer/templates", HttpMethod.GET, owner.token(), null);
        assertThat(runtimeTemplates).singleElement().satisfies(item -> assertThat(item)
                .containsEntry("templateId", (int) templateId).containsEntry("versionNumber", 1)
                .containsEntry("snapshotHash", preview.get("previewHash")));

        Map<String, Object> job = ok("/api/print/runtime/modules/customer/records/" + recordId
                        + "/templates/" + templateId, HttpMethod.POST, owner.token(),
                Map.of("templateVersionId", versionId));
        long jobId = number(job.get("id"));
        assertThat(job).containsEntry("status", "SUCCEEDED").containsEntry("pageCount", 2)
                .containsEntry("templateVersionNumber", 1).containsKey("outputFileId");
        assertThat(list(job.get("pages"))).hasSize(2).allSatisfy(page ->
                assertThat(list(page.get("fields"))).allSatisfy(field ->
                        assertThat(field).containsKeys("code", "name", "value", "detail")));
        assertThat(map(job.get("authorizationSnapshot"))).containsEntry("requiredActions", List.of("DETAIL", "PRINT"))
                .containsEntry("fieldChannels", List.of("PAGE", "FILE"));
        assertThat(map(job.get("recordSnapshot"))).containsEntry("recordId", (int) recordId)
                .containsEntry("recordVersion", 0).containsEntry("pageCount", 2);

        var pdf = http.perform(MockMvcRequestBuilders.get("/api/print/jobs/" + jobId + "/preview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.token()))
                .andReturn().getResponse();
        assertThat(pdf.getStatus()).isEqualTo(200);
        assertThat(pdf.getContentType()).isEqualTo(MediaType.APPLICATION_PDF_VALUE);
        assertThat(new String(pdf.getContentAsByteArray(), 0, 8, StandardCharsets.ISO_8859_1))
                .startsWith("%PDF-1.4");
        assertThat(pdf.getContentAsByteArray().length).isGreaterThan(1500);
        assertThat(jdbc.queryForObject("select status from print_job where id=?", String.class, jobId))
                .isEqualTo("SUCCEEDED");
        assertThat(jdbc.queryForObject("select output_file_id from print_job where id=?", Long.class, jobId))
                .isEqualTo(number(job.get("outputFileId")));
        String authorizationSnapshot = jdbc.queryForObject(
                "select authorization_snapshot_json from print_job where id=?", String.class, jobId);
        assertThat(authorizationSnapshot).contains("DETAIL", "PRINT", "PAGE", "FILE", "visibleFieldCodes");

        String invalidSnapshot = jdbc.queryForObject(
                "select snapshot_json from print_template_version where id=?", String.class, versionId);
        try {
            var root = objectMapper.readTree(invalidSnapshot);
            ((com.fasterxml.jackson.databind.node.ObjectNode) root.path("fields").get(4))
                    .put("code", "removed_owner_note");
            invalidSnapshot = objectMapper.writeValueAsString(root);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
        Long memberId = jdbc.queryForObject("select id from sys_member where system_id=?", Long.class, owner.systemId());
        jdbc.update("insert into print_template_version(template_id,version_number,draft_revision,snapshot_hash,snapshot_json,published_by_member_id) values (?,2,2,?,cast(? as json),?)",
                templateId, "f".repeat(64), invalidSnapshot, memberId);
        long invalidVersionId = jdbc.queryForObject(
                "select id from print_template_version where template_id=? and version_number=2", Long.class, templateId);
        jdbc.update("update print_template_publication set current_version_id=? where template_id=?",
                invalidVersionId, templateId);
        Response invalidField = exchange("/api/print/runtime/modules/customer/records/" + recordId
                        + "/templates/" + templateId, HttpMethod.POST, owner.token(),
                Map.of("templateVersionId", invalidVersionId));
        assertThat(invalidField.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(invalidField.body()).containsEntry("code", "PRINT_TEMPLATE_FIELD_INVALID");

        jdbc.update("update print_template_publication set current_version_id=? where template_id=?", versionId, templateId);
        Map<String, Object> authorization = ok("/api/admin/system/authorization", HttpMethod.GET,
                owner.token(), null);
        Map<String, Object> member = list(authorization.get("members")).getFirst();
        Map<String, Object> assignment = new java.util.LinkedHashMap<>();
        assignment.put("departmentId", null);
        assignment.put("roleIds", List.of());
        assignment.put("expectedVersion", member.get("version"));
        ok("/api/admin/system/authorization/members/" + member.get("tenantMemberId") + "/assignment",
                HttpMethod.POST, owner.token(), assignment);
        Response permissionChanged = exchange("/api/print/runtime/modules/customer/records/" + recordId
                        + "/templates/" + templateId, HttpMethod.POST, owner.token(), Map.of("templateVersionId", versionId));
        assertThat(permissionChanged.status()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(permissionChanged.body()).containsEntry("code", "PERMISSION_DENIED");

        assertThat(jdbc.queryForObject("select count(*) from audit_event where event_code='PRINT_TEMPLATE_PREVIEWED'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from audit_event where event_code='PRINT_TEMPLATE_PUBLISHED'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from audit_event where event_code='BUSINESS_RECORD_PRINTED'",
                Integer.class)).isEqualTo(1);
    }

    private PublishedModule publishCustomerModule(Session owner) {
        long groupId = number(ok("/api/admin/module-config/groups", HttpMethod.POST, owner.token(),
                Map.of("code", "sales", "name", "销售", "sortOrder", 10)).get("id"));
        long moduleId = number(map(ok("/api/admin/module-config/modules", HttpMethod.POST, owner.token(),
                Map.of("groupId", groupId, "code", "customer", "name", "客户")).get("module")).get("id"));
        createField(owner, moduleId, "customer_name", "客户名称", "TEXT", true, 10);
        createField(owner, moduleId, "contract_amount", "合同金额", "NUMBER", true, 20);
        Map<String, Object> ownerNote = createField(owner, moduleId, "owner_note", "内部备注", "TEXT", false, 30);
        createField(owner, moduleId, "delivery_date", "交付日期", "DATE", false, 40);
        createField(owner, moduleId, "status_label", "签章状态", "TEXT", false, 50);
        publishCurrentDraft(owner, moduleId);
        return new PublishedModule(moduleId, number(ownerNote.get("id")));
    }

    private Map<String, Object> createField(Session owner, long moduleId, String code, String name,
                                            String fieldType, boolean required, int sortOrder) {
        return ok("/api/admin/module-config/modules/" + moduleId + "/fields", HttpMethod.POST, owner.token(),
                Map.of("code", code, "name", name, "fieldType", fieldType, "required", required,
                        "uniqueValue", false, "searchable", false, "sortOrder", sortOrder, "config", Map.of()));
    }

    private void publishCurrentDraft(Session owner, long moduleId) {
        Map<String, Object> check = ok("/api/admin/module-config/modules/" + moduleId + "/publication-check",
                HttpMethod.GET, owner.token(), null);
        ok("/api/admin/module-config/modules/" + moduleId + "/publish", HttpMethod.POST, owner.token(),
                Map.of("expectedDraftRevision", check.get("draftRevision")));
    }

    private Session register(String username, String systemCode, String email) {
        Map<String, Object> result = ok("/api/auth/register", HttpMethod.POST, null, Map.of(
                "username", username, "password", "correct-password", "displayName", username,
                "email", email, "systemName", systemCode, "systemCode", systemCode));
        return new Session((String) map(result.get("tokens")).get("accessToken"),
                number(result.get("systemId")), number(result.get("tenantId")));
    }

    private Map<String, Object> ok(String path, HttpMethod method, String token, Object body) {
        Response response = exchange(path, method, token, body);
        assertThat(response.status()).withFailMessage("%s %s failed: %s", method, path, response.body())
                .isEqualTo(HttpStatus.OK);
        return map(response.body().get("data"));
    }

    private List<Map<String, Object>> okList(String path, HttpMethod method, String token, Object body) {
        Response response = exchange(path, method, token, body);
        assertThat(response.status()).withFailMessage("%s %s failed: %s", method, path, response.body())
                .isEqualTo(HttpStatus.OK);
        return list(response.body().get("data"));
    }

    private Response exchange(String path, HttpMethod method, String token, Object body) {
        try {
            var request = MockMvcRequestBuilders.request(method, path).contentType(MediaType.APPLICATION_JSON);
            if (token != null) request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            if (body != null) request.content(objectMapper.writeValueAsBytes(body));
            var response = http.perform(request).andReturn().getResponse();
            return new Response(HttpStatus.valueOf(response.getStatus()),
                    objectMapper.readValue(response.getContentAsByteArray(), Map.class));
        } catch (Exception exception) {
            throw new IllegalStateException("HTTP test request failed: " + method + " " + path, exception);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) { return (Map<String, Object>) value; }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> list(Object value) {
        if (value == null) return new ArrayList<>();
        return (List<Map<String, Object>>) value;
    }

    private long number(Object value) {
        return value instanceof Number number ? number.longValue()
                : new java.math.BigDecimal(String.valueOf(value)).longValue();
    }

    private record Session(String token, long systemId, long tenantId) { }
    private record PublishedModule(long moduleId, long ownerNoteFieldId) { }
    private record Response(HttpStatus status, Map<String, Object> body) { }
}
