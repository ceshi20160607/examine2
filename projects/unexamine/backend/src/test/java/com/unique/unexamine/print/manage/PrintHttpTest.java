package com.unique.unexamine.print.manage;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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

/** 打印模板发布、记录快照与 PDF 的 HTTP 集成验证。 */
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

    /** 发布版本由业务接口保持不可变；系统创建人始终拥有最高管理员权限。 */
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
        Response duplicate = exchange("/api/print/admin/templates/" + templateId + "/publish",
                HttpMethod.POST, owner.token(), Map.of("expectedDraftRevision", 1));
        assertThat(duplicate.status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.body()).containsEntry("code", "PRINT_TEMPLATE_ALREADY_PUBLISHED");

        // 模板发布不会绕过模块动作草稿；所属人补建并发布 PRINT 后运行接口才开放。
        Response withoutAction = exchange("/api/print/runtime/modules/customer/templates",
                HttpMethod.GET, owner.token(), null);
        assertThat(withoutAction.status()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(withoutAction.body()).containsEntry("code", "PRINT_ACTION_NOT_PUBLISHED");
        ok("/api/admin/module-config/modules/" + customer.moduleId() + "/actions/print",
                HttpMethod.POST, owner.token(), null);
        publishCurrentDraft(owner, customer.moduleId());
        Map<String, Object> printableRecord = ok("/api/runtime/modules/customer/records/" + recordId,
                HttpMethod.GET, owner.token(), null);
        assertThat(list(printableRecord.get("actions")).stream().map(action -> action.get("code")))
                .contains("PRINT");

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
        assertThat(jdbc.queryForObject("select status from un_print_job where id=?", String.class, jobId))
                .isEqualTo("SUCCEEDED");
        assertThat(jdbc.queryForObject("select output_file_id from un_print_job where id=?", Long.class, jobId))
                .isEqualTo(number(job.get("outputFileId")));
        String authorizationSnapshot = jdbc.queryForObject(
                "select authorization_snapshot_json from un_print_job where id=?", String.class, jobId);
        assertThat(authorizationSnapshot).contains("DETAIL", "PRINT", "PAGE", "FILE", "visibleFieldCodes");

        assertThat(jdbc.queryForObject("select count(*) from un_audit_event where event_code='PRINT_TEMPLATE_PREVIEWED' and object_id=?",
                Integer.class, String.valueOf(templateId))).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from un_audit_event where event_code='PRINT_TEMPLATE_PUBLISHED' and object_id=?",
                Integer.class, String.valueOf(versionId))).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from un_audit_event where event_code='BUSINESS_RECORD_PRINTED' and object_id=?",
                Integer.class, String.valueOf(jobId))).isEqualTo(1);

        // 停用只在模块发布后生效，并同时撤销新任务和历史 PDF 的直接读取。
        Map<String, Object> disabled = ok("/api/admin/module-config/modules/" + customer.moduleId()
                + "/actions/PRINT", HttpMethod.PUT, owner.token(), Map.of(
                "location", "DETAIL_MORE", "config", Map.of(), "status", "DISABLED", "version", 0));
        assertThat(okList("/api/print/runtime/modules/customer/templates", HttpMethod.GET,
                owner.token(), null)).isNotEmpty();
        publishCurrentDraft(owner, customer.moduleId());
        Response disabledTemplates = exchange("/api/print/runtime/modules/customer/templates",
                HttpMethod.GET, owner.token(), null);
        assertThat(disabledTemplates.status()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(disabledTemplates.body()).containsEntry("code", "PRINT_ACTION_NOT_PUBLISHED");
        Response disabledPrint = exchange("/api/print/runtime/modules/customer/records/" + recordId
                + "/templates/" + templateId, HttpMethod.POST, owner.token(), Map.of("templateVersionId", versionId));
        assertThat(disabledPrint.status()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(disabledPrint.body()).containsEntry("code", "PRINT_ACTION_NOT_PUBLISHED");
        var disabledPdf = http.perform(MockMvcRequestBuilders.get("/api/print/jobs/" + jobId + "/preview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.token())).andReturn().getResponse();
        assertThat(disabledPdf.getStatus()).isEqualTo(403);

        ok("/api/admin/module-config/modules/" + customer.moduleId() + "/actions/PRINT",
                HttpMethod.PUT, owner.token(), Map.of("location", "DETAIL_MORE", "config", Map.of(),
                        "status", "ACTIVE", "version", disabled.get("version")));
        publishCurrentDraft(owner, customer.moduleId());
        assertThat(okList("/api/print/runtime/modules/customer/templates", HttpMethod.GET,
                owner.token(), null)).isNotEmpty();
    }

    /** 旧模板过渡只增加当前发布快照中的打印动作，不发布其他尚在编辑的字段草稿。 */
    @Test
    void transitionsLegacyPublishedTemplateWithoutPublishingOtherDraftChanges() throws Exception {
        Session owner = register("legacy_print_owner", "legacy_print_system", "legacy-print@example.com");
        PublishedModule customer = publishCustomerModule(owner);
        long moduleId = customer.moduleId();
        long recordId = number(ok("/api/runtime/modules/customer/records", HttpMethod.POST, owner.token(), Map.of(
                "recordNumber", "LEGACY-PRINT-001", "title", "旧模板合同", "status", "ACTIVE",
                "participantMemberIds", List.of(), "fields", Map.of(
                        "customer_name", "旧客户", "contract_amount", 100,
                        "owner_note", "原备注", "delivery_date", "2026-09-30", "status_label", "待打印"))).get("id"));
        long templateId = number(ok("/api/print/admin/templates", HttpMethod.POST, owner.token(), Map.of(
                "moduleId", moduleId, "code", "legacy_contract", "name", "旧合同模板",
                "pageSize", "A4", "orientation", "PORTRAIT", "layout", Map.of(
                        "header", "旧模板", "footer", "", "signatureLabel", "签章",
                        "fieldCodes", List.of("customer_name", "contract_amount"),
                        "detailFieldCodes", List.of(), "rowsPerPage", 4))).get("id"));
        ok("/api/print/admin/templates/" + templateId + "/publish", HttpMethod.POST, owner.token(),
                Map.of("expectedDraftRevision", 1));
        createField(owner, moduleId, "pending_only", "待发布字段", "TEXT", false, 60);
        long oldVersionId = jdbc.queryForObject("select current_version_id from un_cfg_module_publication where module_id=?",
                Long.class, moduleId);
        String oldSnapshot = jdbc.queryForObject("select snapshot_json from un_cfg_module_version where id=?",
                String.class, oldVersionId);
        assertThat(oldSnapshot).doesNotContain("\"PRINT\"");
        Response blocked = exchange("/api/print/runtime/modules/customer/templates", HttpMethod.GET,
                owner.token(), null);
        assertThat(blocked.body()).containsEntry("code", "PRINT_ACTION_NOT_PUBLISHED");
        int publicationVersion = ((Number) okList("/api/admin/module-config/modules/" + moduleId + "/versions",
                HttpMethod.GET, owner.token(), null).stream().filter(row -> Boolean.TRUE.equals(row.get("current")))
                .findFirst().orElseThrow().get("publicationVersion")).intValue();
        Map<String, Object> transitioned = ok("/api/admin/module-config/modules/" + moduleId
                + "/legacy-print-transition?expectedPublicationVersion=" + publicationVersion,
                HttpMethod.POST, owner.token(), null);
        assertThat(transitioned).containsEntry("versionNumber", 2);
        long newVersionId = number(transitioned.get("versionId"));
        assertThat(newVersionId).isNotEqualTo(oldVersionId);
        assertThat(jdbc.queryForObject("select snapshot_json from un_cfg_module_version where id=?",
                String.class, oldVersionId)).isEqualTo(oldSnapshot);
        String newSnapshot = jdbc.queryForObject("select snapshot_json from un_cfg_module_version where id=?",
                String.class, newVersionId);
        assertThat(newSnapshot).contains("\"PRINT\"").doesNotContain("pending_only");
        assertThat(list(ok("/api/admin/module-config/modules/" + moduleId + "/draft",
                HttpMethod.GET, owner.token(), null).get("fields")).stream().map(field -> field.get("code")))
                .contains("pending_only");
        assertThat(list(map(ok("/api/admin/module-config/modules/" + moduleId + "/published",
                HttpMethod.GET, owner.token(), null).get("configuration")).get("fields"))
                .stream().map(field -> field.get("code"))).doesNotContain("pending_only");
        assertThat(okList("/api/print/runtime/modules/customer/templates", HttpMethod.GET,
                owner.token(), null)).isNotEmpty();
        Map<String, Object> job = ok("/api/print/runtime/modules/customer/records/" + recordId
                + "/templates/" + templateId, HttpMethod.POST, owner.token(), Map.of());
        assertThat(job).containsEntry("status", "SUCCEEDED");
        assertThat(jdbc.queryForObject("select count(*) from un_cfg_module_version where module_id=?",
                Integer.class, moduleId)).isEqualTo(2);
        Response repeated = exchange("/api/admin/module-config/modules/" + moduleId
                + "/legacy-print-transition?expectedPublicationVersion=" + publicationVersion,
                HttpMethod.POST, owner.token(), null);
        assertThat(repeated.status()).isEqualTo(HttpStatus.CONFLICT);

        // 用旧发布指针和仍存的打印任务模拟升级前状态，检验迁移后历史 PDF 字节保持可读。
        long oldJobId = number(job.get("id"));
        byte[] historicalPdf = http.perform(MockMvcRequestBuilders.get("/api/print/jobs/" + oldJobId + "/preview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.token()))
                .andReturn().getResponse().getContentAsByteArray();
        jdbc.update("update un_cfg_module_publication set current_version_id=?, version=version+1 where module_id=?",
                oldVersionId, moduleId);
        jdbc.update("delete from un_cfg_module_action where module_id=? and code='PRINT'", moduleId);
        int restoredPublicationVersion = jdbc.queryForObject(
                "select version from un_cfg_module_publication where module_id=?", Integer.class, moduleId);
        ok("/api/admin/module-config/modules/" + moduleId
                        + "/legacy-print-transition?expectedPublicationVersion=" + restoredPublicationVersion,
                HttpMethod.POST, owner.token(), null);
        assertThat(jdbc.queryForObject("select module_config_version_id from un_print_job where id=?",
                Long.class, oldJobId)).isEqualTo(oldVersionId);
        byte[] afterMigration = http.perform(MockMvcRequestBuilders.get("/api/print/jobs/" + oldJobId + "/preview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.token()))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(afterMigration).isEqualTo(historicalPdf);
    }

    /** 升级清单只放行原模板；新模板仍需正式动作，动作发布后停用也不能回退到兼容入口。 */
    @Test
    void limitsUpgradeCompatibilityToExistingTemplatesAndRetiresItAfterActionPublish() throws Exception {
        Session owner = register("legacy_upgrade_owner", "legacy_upgrade_system", "legacy-upgrade@example.com");
        PublishedModule customer = publishCustomerModule(owner);
        long moduleId = customer.moduleId();
        long recordId = number(ok("/api/runtime/modules/customer/records", HttpMethod.POST, owner.token(), Map.of(
                "recordNumber", "LEGACY-UPGRADE-001", "title", "旧打印订单", "status", "ACTIVE",
                "participantMemberIds", List.of(), "fields", Map.of(
                        "customer_name", "旧客户", "contract_amount", 100,
                        "delivery_date", "2026-09-30", "status_label", "待打印"))).get("id"));
        Map<String, Object> templateBody = Map.of(
                "moduleId", moduleId, "code", "legacy_upgrade", "name", "旧版订单",
                "pageSize", "A4", "orientation", "PORTRAIT", "layout", Map.of(
                        "header", "旧版订单", "footer", "", "signatureLabel", "",
                        "fieldCodes", List.of("customer_name"), "detailFieldCodes", List.of(), "rowsPerPage", 4));
        long oldTemplateId = number(ok("/api/print/admin/templates", HttpMethod.POST, owner.token(), templateBody).get("id"));
        ok("/api/print/admin/templates/" + oldTemplateId + "/publish", HttpMethod.POST, owner.token(),
                Map.of("expectedDraftRevision", 1));
        Response blocked = exchange("/api/print/runtime/modules/customer/templates", HttpMethod.GET,
                owner.token(), null);
        assertThat(blocked.body()).containsEntry("code", "PRINT_ACTION_NOT_PUBLISHED");

        // 模拟 V22 从升级前快照固定旧模板，空库新装的正常路径仍由上面的拒绝结果覆盖。
        long sourceVersionId = jdbc.queryForObject(
                "select current_version_id from un_cfg_module_publication where module_id=?", Long.class, moduleId);
        jdbc.update("""
                insert into un_print_legacy_template
                  (module_id,template_id,system_id,owner_tenant_id,source_module_version_id)
                values (?,?,?,?,?)
                """, moduleId, oldTemplateId, owner.systemId(), owner.tenantId(), sourceVersionId);
        Map<String, Object> published = ok("/api/admin/module-config/modules/" + moduleId + "/published",
                HttpMethod.GET, owner.token(), null);
        assertThat(map(published.get("configuration"))).containsEntry("legacyPrintCompatibility", true);
        assertThat(list(map(published.get("configuration")).get("actions")).stream()
                .map(action -> action.get("code"))).contains("PRINT");
        assertThat(okList("/api/print/runtime/modules/customer/templates", HttpMethod.GET,
                owner.token(), null)).extracting(item -> number(item.get("templateId"))).containsExactly(oldTemplateId);
        long oldJobId = number(ok("/api/print/runtime/modules/customer/records/" + recordId
                + "/templates/" + oldTemplateId, HttpMethod.POST, owner.token(), Map.of()).get("id"));
        byte[] oldPdf = http.perform(MockMvcRequestBuilders.get("/api/print/jobs/" + oldJobId + "/preview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.token()))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(oldPdf, 0, 4, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF");

        long newTemplateId = number(ok("/api/print/admin/templates", HttpMethod.POST, owner.token(), Map.of(
                "moduleId", moduleId, "code", "after_upgrade", "name", "升级后新模板",
                "pageSize", "A4", "orientation", "PORTRAIT", "layout", Map.of(
                        "header", "新模板", "footer", "", "signatureLabel", "",
                        "fieldCodes", List.of("customer_name"), "detailFieldCodes", List.of(), "rowsPerPage", 4))).get("id"));
        ok("/api/print/admin/templates/" + newTemplateId + "/publish", HttpMethod.POST, owner.token(),
                Map.of("expectedDraftRevision", 1));
        assertThat(okList("/api/print/runtime/modules/customer/templates", HttpMethod.GET,
                owner.token(), null)).extracting(item -> number(item.get("templateId"))).containsExactly(oldTemplateId);
        Response newDenied = exchange("/api/print/runtime/modules/customer/records/" + recordId
                + "/templates/" + newTemplateId, HttpMethod.POST, owner.token(), Map.of());
        assertThat(newDenied.body()).containsEntry("code", "PRINT_ACTION_NOT_PUBLISHED");

        int publicationVersion = jdbc.queryForObject(
                "select version from un_cfg_module_publication where module_id=?", Integer.class, moduleId);
        ok("/api/admin/module-config/modules/" + moduleId
                        + "/legacy-print-transition?expectedPublicationVersion=" + publicationVersion,
                HttpMethod.POST, owner.token(), null);
        assertThat(jdbc.queryForObject("select count(*) from un_print_legacy_template "
                + "where module_id=? and retired_time is not null", Integer.class, moduleId)).isEqualTo(1);
        assertThat(http.perform(MockMvcRequestBuilders.get("/api/print/jobs/" + oldJobId + "/preview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.token()))
                .andReturn().getResponse().getContentAsByteArray()).isEqualTo(oldPdf);
        assertThat(map(ok("/api/admin/module-config/modules/" + moduleId + "/published", HttpMethod.GET,
                owner.token(), null).get("configuration"))).doesNotContainKey("legacyPrintCompatibility");

        Map<String, Object> disabled = ok("/api/admin/module-config/modules/" + moduleId
                + "/actions/PRINT", HttpMethod.PUT, owner.token(), Map.of(
                "location", "DETAIL_MORE", "config", Map.of(), "status", "DISABLED", "version", 0));
        assertThat(disabled).containsEntry("status", "DISABLED");
        publishCurrentDraft(owner, moduleId);
        Response afterDisable = exchange("/api/print/runtime/modules/customer/templates", HttpMethod.GET,
                owner.token(), null);
        assertThat(afterDisable.body()).containsEntry("code", "PRINT_ACTION_NOT_PUBLISHED");
        assertThat(jdbc.queryForObject("select count(*) from un_print_legacy_template "
                + "where module_id=? and retired_time is null", Integer.class, moduleId)).isZero();
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
