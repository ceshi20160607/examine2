package com.unique.unexamine.analytics.manage;

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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class DashboardHttpTest {
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

    @Autowired
    private MockMvc http;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void publishesContextBoundDashboardExecutesRealDataAndKeepsPublishedSnapshotsImmutable() {
        Session owner = register("dashboard_owner", "dashboard_system", "dashboard-owner@example.com");
        publishCustomerModule(owner);
        Map<String, Object> customerOne = createCustomer(owner, "C48-001", "北辰客户");
        Map<String, Object> customerTwo = createCustomer(owner, "C48-002", "星海客户");
        publishOrderModule(owner);
        createOrder(owner, "C49-ORDER-001", "北辰首单", number(customerOne.get("id")), 120);
        createOrder(owner, "C49-ORDER-002", "星海续费", number(customerTwo.get("id")), 280);

        Map<String, Object> reportSource = ok("/api/analytics/admin/data-sources", HttpMethod.POST,
                owner.token(), source("customer_order_report", "客户订单权限报表", "MODULE_REPORT",
                        reportDefinition(100), Map.of("resourceType", "MODULE", "resourceCode", "customer",
                                "actionCode", "LIST"), null));
        long reportSourceId = number(reportSource.get("id"));
        Map<String, Object> queryExplanation = map(reportSource.get("definition")).containsKey("queryExplanation")
                ? map(map(reportSource.get("definition")).get("queryExplanation")) : Map.of();
        assertThat(queryExplanation).containsEntry("mode", "MULTI_MODULE")
                .containsEntry("arbitrarySqlAllowed", false);
        assertThat((List<?>) queryExplanation.get("permissionFilters")).hasSize(4);
        assertThat(ok("/api/analytics/admin/report-metadata", HttpMethod.GET, owner.token(), null))
                .containsEntry("arbitrarySqlAllowed", false).containsEntry("maximumModules", 3);

        Map<String, Object> reportPreview = ok("/api/analytics/admin/data-sources/" + reportSourceId
                + "/report-preview", HttpMethod.GET, owner.token(), null);
        assertThat(reportPreview).containsEntry("valid", true);
        Map<String, Object> sample = map(reportPreview.get("sampleResult"));
        assertThat(number(sample.get("value"))).isEqualTo(2);
        assertThat((List<?>) sample.get("items")).hasSize(2);
        assertThat(list(sample.get("items"))).allSatisfy(item -> {
            assertThat(map(item.get("fields")).keySet())
                    .containsExactly("customer.title", "orders.amount");
            assertThat(item.get("route").toString()).contains("module=customer", "recordId=");
        });
        assertThat(list(sample.get("groups"))).singleElement().satisfies(group -> {
            assertThat(group).containsEntry("key", "ACTIVE");
            assertThat(number(group.get("value"))).isEqualTo(2);
            assertThat(number(group.get("rowCount"))).isEqualTo(2);
        });
        assertThat(map(sample.get("queryPlan"))).containsEntry("estimatedRows", 2)
                .containsEntry("arbitrarySqlAllowed", false);
        assertThat(sample.get("metricDefinition").toString()).contains("权限", "2 行授权结果");

        reportSource = ok("/api/analytics/admin/data-sources/" + reportSourceId + "/publish", HttpMethod.POST,
                owner.token(), Map.of("expectedDraftRevision", reportSource.get("draftRevision")));
        Map<String, Object> publishedReport = ok("/api/analytics/report/data-sources/" + reportSourceId,
                HttpMethod.GET, owner.token(), null);
        assertThat(number(publishedReport.get("value"))).isEqualTo(2);
        assertThat(publishedReport).containsEntry("dataSourceVersionNumber", 1).containsKey("definitionHash");
        long reportVersionId = jdbc.queryForObject(
                "select id from ana_data_source_version where data_source_id=?", Long.class, reportSourceId);
        long ownerAccountId = jdbc.queryForObject(
                "select id from plat_account where username='dashboard_owner'", Long.class);

        Map<String, Object> kpi = ok("/api/analytics/admin/kpis", HttpMethod.POST, owner.token(),
                kpi("customer_monthly", "月度有效客户", reportVersionId, 3,
                        Map.of(), Map.of("resourceType", "MODULE", "resourceCode", "customer",
                                "actionCode", "LIST"), ownerAccountId, null));
        long kpiId = number(kpi.get("id"));
        assertThat(number(kpi.get("dataSourceVersionId"))).isEqualTo(reportVersionId);
        assertThat(kpi).containsEntry("periodType", "MONTH").containsEntry("version", 0);
        Map<String, Object> kpiPreview = ok("/api/analytics/admin/kpis/" + kpiId
                + "/preview?periodKey=2026-08", HttpMethod.GET, owner.token(), null);
        assertThat(kpiPreview).containsEntry("status", "UNDER_TARGET").containsEntry("kpiVersion", 0);
        assertThat(number(kpiPreview.get("actualValue"))).isEqualTo(2);
        assertThat(number(kpiPreview.get("targetValue"))).isEqualTo(3);
        assertThat(kpiPreview.get("sourceExplanation").toString()).contains("发布版本 #" + reportVersionId);
        Response invalidPeriod = exchange("/api/analytics/admin/kpis/" + kpiId
                + "/preview?periodKey=2026-Q3", HttpMethod.GET, owner.token(), null);
        assertThat(invalidPeriod.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(invalidPeriod.body()).containsEntry("code", "KPI_PERIOD_INVALID");

        Map<String, Object> kpiResult = ok("/api/analytics/admin/kpis/" + kpiId + "/calculate",
                HttpMethod.POST, owner.token(), Map.of("periodKey", "2026-08"));
        assertThat(kpiResult).containsEntry("status", "UNDER_TARGET").containsEntry("stale", false);
        assertThat(map(kpiResult.get("explanation"))).containsEntry("kpiVersion", 0)
                .containsEntry("definitionHash", publishedReport.get("definitionHash"));
        assertThat(number(map(kpiResult.get("explanation")).get("dataSourceVersionId")))
                .isEqualTo(reportVersionId);
        assertThat(list(kpiResult.get("reminders"))).singleElement().satisfies(reminder -> {
            assertThat(number(reminder.get("recipientAccountId"))).isEqualTo(ownerAccountId);
            assertThat(reminder).containsEntry("status", "SENT").containsKey("messageId");
        });
        long reminderCount = count("ana_kpi_reminder", "kpi_result_id=" + kpiResult.get("id"));
        long messageCount = count("msg_message", "source_type='KPI_ALERT'");
        assertThat(jdbc.queryForObject("select target_route from msg_message where source_type='KPI_ALERT'",
                String.class)).isEqualTo("/systems/" + owner.systemId() + "?workspace=kpi&kpiId=" + kpiId);

        jdbc.update("update ana_data_source set source_type='MODULE_RECORDS' where id=?", reportSourceId);
        Map<String, Object> staleResult = ok("/api/analytics/admin/kpis/" + kpiId + "/calculate",
                HttpMethod.POST, owner.token(), Map.of("periodKey", "2026-08"));
        assertThat(staleResult).containsEntry("status", "STALE").containsEntry("stale", true);
        assertThat(number(staleResult.get("actualValue"))).isEqualTo(2);
        assertThat(number(staleResult.get("targetValue"))).isEqualTo(3);
        assertThat(count("ana_kpi_reminder", "kpi_result_id=" + kpiResult.get("id"))).isEqualTo(reminderCount);
        assertThat(count("msg_message", "source_type='KPI_ALERT'")).isEqualTo(messageCount);
        jdbc.update("update ana_data_source set source_type='MODULE_REPORT' where id=?", reportSourceId);
        kpiResult = ok("/api/analytics/admin/kpis/" + kpiId + "/calculate",
                HttpMethod.POST, owner.token(), Map.of("periodKey", "2026-08"));
        assertThat(kpiResult).containsEntry("status", "UNDER_TARGET").containsEntry("stale", false);
        assertThat(count("ana_kpi_reminder", "kpi_result_id=" + kpiResult.get("id"))).isEqualTo(reminderCount);

        Map<String, Object> source = ok("/api/analytics/admin/data-sources", HttpMethod.POST,
                owner.token(), source("customer_records", "客户记录", "MODULE_RECORDS",
                        Map.of("moduleCode", "customer", "limit", 5),
                        Map.of("resourceType", "MODULE", "resourceCode", "customer", "actionCode", "LIST"), null));
        long sourceId = number(source.get("id"));
        source = ok("/api/analytics/admin/data-sources/" + sourceId + "/publish", HttpMethod.POST,
                owner.token(), Map.of("expectedDraftRevision", source.get("draftRevision")));
        assertThat(source).containsEntry("status", "PUBLISHED");
        assertThat((List<?>) source.get("versions")).singleElement();

        List<Map<String, Object>> components = List.of(
                component("customer_count", "METRIC", "客户总数", sourceId, 0, Map.of(),
                        Map.of("width", 1), Map.of("moduleCode", "customer", "permission",
                                Map.of("resourceType", "MODULE", "resourceCode", "customer", "actionCode", "LIST"))),
                component("recent_customers", "LIST", "最近客户", sourceId, 10, Map.of("limit", 1),
                        Map.of("width", 2, "refreshSeconds", 60), Map.of()),
                component("customer_entry", "QUICK_ENTRY", "进入客户模块", null, 20, Map.of(),
                        Map.of("width", 1), Map.of("moduleCode", "customer", "permission",
                                Map.of("resourceType", "MODULE", "resourceCode", "customer", "actionCode", "LIST"))),
                component("customer_order_count", "CHART", "客户订单授权聚合", reportSourceId, 30, Map.of(),
                        Map.of("width", 2), Map.of("moduleCode", "customer", "permission",
                                Map.of("resourceType", "MODULE", "resourceCode", "customer", "actionCode", "LIST"))),
                component("customer_kpi", "KPI", "月度有效客户 KPI", reportSourceId, 40,
                        Map.of("kpiId", kpiId, "kpiVersion", 0), Map.of("width", 2),
                        Map.of("route", "/systems/" + owner.systemId() + "?workspace=kpi&kpiId=" + kpiId)));
        Map<String, Object> dashboard = ok("/api/analytics/admin/dashboards", HttpMethod.POST,
                owner.token(), dashboard("system_home", "客户经营工作台", "当前租户真实客户统计", components, null));
        long dashboardId = number(dashboard.get("id"));
        Map<String, Object> preview = ok("/api/analytics/admin/dashboards/" + dashboardId + "/preview",
                HttpMethod.GET, owner.token(), null);
        assertThat(preview).containsEntry("valid", true);
        List<Map<String, Object>> previewComponents = list(preview.get("components"));
        assertThat(previewComponents).extracting(item -> item.get("outcome"))
                .containsExactly("READY", "READY", "READY", "READY", "READY");
        assertThat(number(previewComponents.get(0).get("value"))).isEqualTo(2);
        assertThat((List<?>) previewComponents.get(1).get("items")).hasSize(1);
        assertThat(number(previewComponents.get(3).get("value"))).isEqualTo(2);
        assertThat(number(previewComponents.get(4).get("value"))).isEqualTo(2);
        assertThat(previewComponents.get(4).get("metricDefinition").toString())
                .contains("目标 GTE 3", "达成率", "数据源版本 #" + reportVersionId);
        assertThat(previewComponents.get(0).get("metricDefinition").toString())
                .contains("当前系统", "当前租户", "LIST 权限", "有效记录");

        Map<String, Object> publication = ok("/api/analytics/admin/dashboards/" + dashboardId + "/publish",
                HttpMethod.POST, owner.token(), Map.of("expectedDraftRevision", dashboard.get("draftRevision")));
        assertThat(publication).containsEntry("versionNumber", 1).containsKey("snapshotHash");
        long dashboardVersionId = number(publication.get("versionId"));
        Map<String, Object> currentDashboard = list(ok("/api/analytics/admin", HttpMethod.GET,
                owner.token(), null).get("dashboards")).stream()
                .filter(item -> number(item.get("id")) == dashboardId).findFirst().orElseThrow();
        long sourceVersionId = jdbc.queryForObject(
                "select id from ana_data_source_version where data_source_id=?", Long.class, sourceId);

        Map<String, Object> runtime = ok("/api/analytics/runtime", HttpMethod.GET, owner.token(), null);
        assertThat(runtime).containsEntry("configured", true).containsEntry("contextType", "SYSTEM")
                .containsEntry("versionNumber", 1).containsEntry("name", "客户经营工作台");
        List<Map<String, Object>> runtimeComponents = list(runtime.get("components"));
        assertThat(number(runtimeComponents.get(0).get("value"))).isEqualTo(2);
        assertThat(((Map<?, ?>) runtimeComponents.get(0).get("drillTarget")).get("route").toString())
                .isEqualTo("/systems/" + owner.systemId() + "?workspace=runtime&module=customer");
        assertThat(((List<Map<String, Object>>) runtimeComponents.get(1).get("items")).get(0).get("route").toString())
                .contains("/systems/" + owner.systemId(), "module=customer", "recordId=");
        assertThat(number(runtimeComponents.get(3).get("value"))).isEqualTo(2);
        assertThat(list(runtimeComponents.get(3).get("items"))).singleElement().satisfies(item -> {
            assertThat(item).containsEntry("title", "ACTIVE");
            assertThat(number(item.get("value"))).isEqualTo(2);
            assertThat(item.get("status").toString()).contains("2 行授权记录");
        });
        assertThat(runtimeComponents.get(4)).containsEntry("componentType", "KPI")
                .containsEntry("outcome", "READY").containsEntry("drillAvailable", true);
        assertThat(list(runtimeComponents.get(4).get("items"))).hasSize(2);

        Map<String, Object> reportDraft = ok("/api/analytics/admin/data-sources/" + reportSourceId,
                HttpMethod.PUT, owner.token(), source("customer_order_report", "未发布的新报表草稿",
                        "MODULE_REPORT", reportDefinition(50), Map.of("resourceType", "MODULE",
                                "resourceCode", "customer", "actionCode", "LIST"),
                        ((Number) reportSource.get("version")).intValue()));
        assertThat(reportDraft).containsEntry("status", "DRAFT");
        assertThat(number(ok("/api/analytics/report/data-sources/" + reportSourceId,
                HttpMethod.GET, owner.token(), null).get("value"))).isEqualTo(2);
        assertThat(number(list(ok("/api/analytics/runtime", HttpMethod.GET, owner.token(), null)
                .get("components")).get(3).get("value"))).isEqualTo(2);

        Response arbitrarySql = exchange("/api/analytics/admin/data-sources", HttpMethod.POST, owner.token(),
                source("forbidden_sql", "禁止任意 SQL", "MODULE_REPORT",
                        new java.util.LinkedHashMap<>(Map.of("sql", "select * from biz_record",
                                "modules", List.of(Map.of("alias", "customer", "moduleCode", "customer")),
                                "outputFields", List.of(Map.of("alias", "customer", "fieldCode", "title")),
                                "metric", Map.of("operation", "COUNT"))), Map.of(), null));
        assertThat(arbitrarySql.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(arbitrarySql.body()).containsEntry("code", "REPORT_ARBITRARY_SQL_FORBIDDEN");

        Map<String, Object> ambiguousDefinition = new java.util.LinkedHashMap<>(reportDefinition(100));
        ambiguousDefinition.put("relations", List.of());
        Response ambiguous = exchange("/api/analytics/admin/data-sources", HttpMethod.POST, owner.token(),
                source("ambiguous_relation", "歧义关系", "MODULE_REPORT", ambiguousDefinition, Map.of(), null));
        assertThat(ambiguous.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(ambiguous.body()).containsEntry("code", "REPORT_RELATION_AMBIGUOUS");

        Map<String, Object> nonIndexedDefinition = new java.util.LinkedHashMap<>(reportDefinition(100));
        nonIndexedDefinition.put("filters", List.of(Map.of("alias", "orders", "fieldCode", "notes",
                "operator", "CONTAINS", "value", "内部")));
        Response nonIndexed = exchange("/api/analytics/admin/data-sources", HttpMethod.POST, owner.token(),
                source("non_indexed_report", "不可索引报表", "MODULE_REPORT", nonIndexedDefinition, Map.of(), null));
        assertThat(nonIndexed.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(nonIndexed.body()).containsEntry("code", "REPORT_QUERY_NOT_INDEXABLE");

        Map<String, Object> unauthorizedDefinition = new java.util.LinkedHashMap<>(reportDefinition(100));
        unauthorizedDefinition.put("outputFields", List.of(
                Map.of("alias", "customer", "fieldCode", "secret_field")));
        Response unauthorized = exchange("/api/analytics/admin/data-sources", HttpMethod.POST, owner.token(),
                source("unauthorized_field", "越权字段报表", "MODULE_REPORT", unauthorizedDefinition, Map.of(), null));
        assertThat(unauthorized.status()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(unauthorized.body()).containsEntry("code", "REPORT_OUTPUT_FIELD_FORBIDDEN");

        Map<String, Object> costly = ok("/api/analytics/admin/data-sources", HttpMethod.POST, owner.token(),
                source("costly_report", "成本超限报表", "MODULE_REPORT", singleModuleDefinition(1), Map.of(), null));
        Map<String, Object> costlyPreview = ok("/api/analytics/admin/data-sources/" + costly.get("id")
                + "/report-preview", HttpMethod.GET, owner.token(), null);
        assertThat(costlyPreview).containsEntry("valid", false);
        assertThat(list(costlyPreview.get("issues"))).singleElement()
                .satisfies(issue -> assertThat(issue).containsEntry("code", "REPORT_QUERY_COST_EXCEEDED"));
        Response costlyPublish = exchange("/api/analytics/admin/data-sources/" + costly.get("id") + "/publish",
                HttpMethod.POST, owner.token(), Map.of("expectedDraftRevision", costly.get("draftRevision")));
        assertThat(costlyPublish.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(costlyPublish.body()).containsEntry("code", "REPORT_QUERY_COST_EXCEEDED");

        Map<String, Object> draftSource = ok("/api/analytics/admin/data-sources/" + sourceId, HttpMethod.PUT,
                owner.token(), source("customer_records", "客户记录新草稿", "MODULE_RECORDS",
                        Map.of("moduleCode", "customer", "limit", 20),
                        Map.of("resourceType", "MODULE", "resourceCode", "customer", "actionCode", "LIST"),
                        ((Number) source.get("version")).intValue()));
        assertThat(draftSource).containsEntry("status", "DRAFT");
        Map<String, Object> runtimeDuringSourceDraft = ok("/api/analytics/runtime", HttpMethod.GET, owner.token(), null);
        assertThat(runtimeDuringSourceDraft).containsEntry("versionNumber", 1);
        assertThat(list(runtimeDuringSourceDraft.get("components")))
                .allSatisfy(item -> assertThat(item.get("outcome")).isEqualTo("READY"));

        Map<String, Object> draftDashboard = ok("/api/analytics/admin/dashboards/" + dashboardId, HttpMethod.PUT,
                owner.token(), dashboard("system_home", "尚未发布的新标题", "草稿不能覆盖运行版",
                        components, ((Number) currentDashboard.get("version")).intValue()));
        Map<String, Object> runtimeDuringDashboardDraft = ok("/api/analytics/runtime", HttpMethod.GET, owner.token(), null);
        assertThat(runtimeDuringDashboardDraft).containsEntry("name", "客户经营工作台")
                .containsEntry("versionNumber", 1);
        assertThat(draftDashboard).containsEntry("status", "DRAFT");

        Map<String, Object> invalidSource = ok("/api/analytics/admin/data-sources", HttpMethod.POST,
                owner.token(), source("ghost_records", "失效模块数据源", "MODULE_RECORDS",
                        Map.of("moduleCode", "ghost_module", "limit", 5), Map.of(), null));
        invalidSource = ok("/api/analytics/admin/data-sources/" + invalidSource.get("id") + "/publish",
                HttpMethod.POST, owner.token(), Map.of("expectedDraftRevision", invalidSource.get("draftRevision")));
        Map<String, Object> invalidDashboard = ok("/api/analytics/admin/dashboards", HttpMethod.POST,
                owner.token(), dashboard("invalid_home", "无效源工作台", "用于验证明确失败反馈",
                        List.of(component("ghost_count", "METRIC", "不存在模块", number(invalidSource.get("id")),
                                0, Map.of(), Map.of("width", 1), Map.of())), null));
        Map<String, Object> invalidPreview = ok("/api/analytics/admin/dashboards/" + invalidDashboard.get("id")
                + "/preview", HttpMethod.GET, owner.token(), null);
        assertThat(invalidPreview).containsEntry("valid", false);
        Map<String, Object> invalidComponent = list(invalidPreview.get("components")).get(0);
        assertThat(invalidComponent).containsEntry("outcome", "ERROR").containsEntry("value", null)
                .containsKey("errorCode").containsKey("updatedAt");
        Response invalidPublish = exchange("/api/analytics/admin/dashboards/" + invalidDashboard.get("id")
                + "/publish", HttpMethod.POST, owner.token(),
                Map.of("expectedDraftRevision", invalidDashboard.get("draftRevision")));
        assertThat(invalidPublish.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(invalidPublish.body()).containsEntry("code", "DASHBOARD_PUBLICATION_INVALID");

        String platformAdmin = login("admin", "123123aa");
        Map<String, Object> platformSource = ok("/api/analytics/admin/data-sources", HttpMethod.POST,
                platformAdmin, source("authorized_systems", "已授权系统", "PLATFORM_SYSTEMS",
                        Map.of("limit", 10), Map.of(), null));
        platformSource = ok("/api/analytics/admin/data-sources/" + platformSource.get("id") + "/publish",
                HttpMethod.POST, platformAdmin,
                Map.of("expectedDraftRevision", platformSource.get("draftRevision")));
        Map<String, Object> platformDashboard = ok("/api/analytics/admin/dashboards", HttpMethod.POST,
                platformAdmin, dashboard("platform_home", "平台授权概览", "仅平台聚合与系统入口",
                        List.of(component("system_count", "METRIC", "可进入系统", number(platformSource.get("id")),
                                0, Map.of(), Map.of("width", 1), Map.of())), null));
        ok("/api/analytics/admin/dashboards/" + platformDashboard.get("id") + "/publish", HttpMethod.POST,
                platformAdmin, Map.of("expectedDraftRevision", platformDashboard.get("draftRevision")));
        Map<String, Object> platformRuntime = ok("/api/analytics/runtime", HttpMethod.GET, platformAdmin, null);
        assertThat(platformRuntime).containsEntry("contextType", "PLATFORM").containsEntry("name", "平台授权概览");
        assertThat(ok("/api/analytics/runtime", HttpMethod.GET, owner.token(), null))
                .containsEntry("contextType", "SYSTEM").containsEntry("name", "客户经营工作台");

        assertThatThrownBy(() -> jdbc.update("update ana_data_source_version set definition_hash='x' where id=?",
                sourceVersionId)).isInstanceOf(DataAccessException.class).hasMessageContaining("immutable");
        assertThatThrownBy(() -> jdbc.update("delete from ana_dashboard_version where id=?", dashboardVersionId))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("immutable");
        assertThat(count("audit_event", "event_code='DASHBOARD_DATA_SOURCE_PUBLISHED'")).isGreaterThanOrEqualTo(3);
        assertThat(count("audit_event", "event_code='DASHBOARD_PUBLISHED'")).isEqualTo(2);
        assertThat(count("audit_event", "event_code='ANALYTICS_REPORT_PREVIEWED'")).isGreaterThanOrEqualTo(1);
        assertThat(count("audit_event", "event_code='ANALYTICS_REPORT_EXECUTED'")).isGreaterThanOrEqualTo(2);
        assertThat(count("audit_event", "event_code='KPI_CALCULATED'")).isGreaterThanOrEqualTo(2);

        Map<String, Object> updatedKpi = ok("/api/analytics/admin/kpis/" + kpiId, HttpMethod.PUT,
                owner.token(), kpi("customer_monthly", "月度有效客户", reportVersionId, 3,
                        Map.of(), Map.of("accountIds", List.of(999999L)), ownerAccountId, 0));
        assertThat(updatedKpi).containsEntry("version", 1);
        Map<String, Object> visibleWithoutDrill = ok("/api/analytics/kpis/" + kpiId,
                HttpMethod.GET, owner.token(), null);
        assertThat(map(visibleWithoutDrill.get("latestResult")))
                .containsEntry("drillAvailable", false).containsEntry("status", "UNDER_TARGET");
        Map<String, Object> dashboardAfterKpiChange = ok("/api/analytics/runtime", HttpMethod.GET,
                owner.token(), null);
        assertThat(list(dashboardAfterKpiChange.get("components")).get(4))
                .containsEntry("outcome", "ERROR").containsEntry("errorCode", "DASHBOARD_KPI_VERSION_CHANGED")
                .containsEntry("value", null);
    }

    private Map<String, Object> source(String code, String name, String type, Map<String, Object> definition,
                                       Map<String, Object> permission, Integer expectedVersion) {
        return mapWithNullable("code", code, "name", name, "sourceType", type, "definition", definition,
                "permissionPolicy", permission, "expectedVersion", expectedVersion);
    }

    private Map<String, Object> dashboard(String code, String name, String description,
                                          List<Map<String, Object>> components, Integer expectedVersion) {
        return mapWithNullable("code", code, "name", name, "description", description,
                "components", components, "expectedVersion", expectedVersion);
    }

    private Map<String, Object> kpi(String code, String name, long sourceVersionId, int target,
                                    Map<String, Object> visibility, Map<String, Object> drill,
                                    long recipientAccountId, Integer expectedVersion) {
        return mapWithNullable("code", code, "name", name, "dataSourceVersionId", sourceVersionId,
                "targetValue", target, "targetOperator", "GTE", "periodType", "MONTH",
                "responsibleType", "PERSON", "responsibleIds", List.of(recipientAccountId),
                "visibilityPermission", visibility, "drillPermission", drill,
                "reminderEnabled", true, "reminderRecipientAccountIds", List.of(recipientAccountId),
                "reminderBelowPercent", 100, "status", "ACTIVE", "expectedVersion", expectedVersion);
    }

    private Map<String, Object> reportDefinition(int maxScanRows) {
        return Map.of(
                "modules", List.of(
                        Map.of("alias", "customer", "moduleCode", "customer"),
                        Map.of("alias", "orders", "moduleCode", "orders")),
                "relations", List.of(Map.of("relationType", "REFERENCE", "joinType", "INNER",
                        "leftAlias", "customer", "leftField", "id",
                        "rightAlias", "orders", "rightField", "customer_id")),
                "outputFields", List.of(
                        Map.of("alias", "customer", "fieldCode", "title", "label", "客户"),
                        Map.of("alias", "orders", "fieldCode", "amount", "label", "订单金额")),
                "metric", Map.of("operation", "COUNT"),
                "dimension", Map.of("type", "STATUS", "alias", "customer", "fieldCode", "status"),
                "filters", List.of(Map.of("alias", "customer", "fieldCode", "status",
                        "operator", "EQ", "value", "ACTIVE")),
                "sort", Map.of("alias", "orders", "fieldCode", "amount", "direction", "DESC"),
                "timeField", Map.of("alias", "customer", "fieldCode", "updatedAt"),
                "maxScanRows", maxScanRows, "limit", 20);
    }

    private Map<String, Object> singleModuleDefinition(int maxScanRows) {
        return Map.of(
                "modules", List.of(Map.of("alias", "customer", "moduleCode", "customer")),
                "relations", List.of(),
                "outputFields", List.of(Map.of("alias", "customer", "fieldCode", "title")),
                "metric", Map.of("operation", "COUNT"), "dimension", Map.of(), "filters", List.of(),
                "sort", Map.of("alias", "customer", "fieldCode", "updatedAt", "direction", "DESC"),
                "timeField", Map.of(), "maxScanRows", maxScanRows, "limit", 20);
    }

    private Map<String, Object> component(String key, String type, String title, Long sourceId, int order,
                                          Map<String, Object> query, Map<String, Object> layout,
                                          Map<String, Object> drill) {
        return mapWithNullable("componentKey", key, "componentType", type, "title", title,
                "dataSourceId", sourceId, "layout", layout, "queryParameters", query,
                "displayConfig", Map.of("refreshSeconds", 60, "accentColor", "#315efb"),
                "drillTarget", drill, "sortOrder", order);
    }

    private Map<String, Object> mapWithNullable(Object... values) {
        java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) result.put((String) values[index], values[index + 1]);
        return result;
    }

    private void publishCustomerModule(Session owner) {
        long groupId = number(ok("/api/admin/module-config/groups", HttpMethod.POST, owner.token(),
                Map.of("code", "sales", "name", "销售", "sortOrder", 10)).get("id"));
        long moduleId = number(((Map<?, ?>) ok("/api/admin/module-config/modules", HttpMethod.POST,
                owner.token(), Map.of("groupId", groupId, "code", "customer", "name", "客户")).get("module")).get("id"));
        ok("/api/admin/module-config/modules/" + moduleId + "/fields", HttpMethod.POST, owner.token(),
                Map.of("code", "customer_name", "name", "客户名称", "fieldType", "TEXT", "required", true,
                        "searchable", true, "sortOrder", 10, "config", Map.of("placeholder", "客户名称")));
        Map<String, Object> check = ok("/api/admin/module-config/modules/" + moduleId + "/publication-check",
                HttpMethod.GET, owner.token(), null);
        ok("/api/admin/module-config/modules/" + moduleId + "/publish", HttpMethod.POST, owner.token(),
                Map.of("expectedDraftRevision", check.get("draftRevision")));
    }

    private void publishOrderModule(Session owner) {
        long groupId = number(ok("/api/admin/module-config/groups", HttpMethod.POST, owner.token(),
                Map.of("code", "commerce", "name", "交易", "sortOrder", 20)).get("id"));
        long moduleId = number(((Map<?, ?>) ok("/api/admin/module-config/modules", HttpMethod.POST,
                owner.token(), Map.of("groupId", groupId, "code", "orders", "name", "订单")).get("module")).get("id"));
        ok("/api/admin/module-config/modules/" + moduleId + "/fields", HttpMethod.POST, owner.token(),
                Map.of("code", "customer_id", "name", "客户 ID", "fieldType", "NUMBER", "required", true,
                        "searchable", true, "sortOrder", 10, "config", Map.of()));
        ok("/api/admin/module-config/modules/" + moduleId + "/fields", HttpMethod.POST, owner.token(),
                Map.of("code", "amount", "name", "订单金额", "fieldType", "NUMBER", "required", true,
                        "searchable", true, "sortOrder", 20, "config", Map.of()));
        ok("/api/admin/module-config/modules/" + moduleId + "/fields", HttpMethod.POST, owner.token(),
                Map.of("code", "notes", "name", "内部备注", "fieldType", "TEXT", "required", false,
                        "searchable", false, "sortOrder", 30, "config", Map.of()));
        Map<String, Object> check = ok("/api/admin/module-config/modules/" + moduleId + "/publication-check",
                HttpMethod.GET, owner.token(), null);
        ok("/api/admin/module-config/modules/" + moduleId + "/publish", HttpMethod.POST, owner.token(),
                Map.of("expectedDraftRevision", check.get("draftRevision")));
    }

    private Map<String, Object> createCustomer(Session owner, String number, String name) {
        return ok("/api/runtime/modules/customer/records", HttpMethod.POST, owner.token(), Map.of(
                "recordNumber", number, "title", name, "status", "ACTIVE",
                "participantMemberIds", List.of(), "fields", Map.of("customer_name", name)));
    }

    private void createOrder(Session owner, String number, String title, long customerId, int amount) {
        ok("/api/runtime/modules/orders/records", HttpMethod.POST, owner.token(), Map.of(
                "recordNumber", number, "title", title, "status", "ACTIVE",
                "participantMemberIds", List.of(), "fields", Map.of(
                        "customer_id", customerId, "amount", amount, "notes", "内部")));
    }

    private Session register(String username, String systemCode, String email) {
        Map<String, Object> result = ok("/api/auth/register", HttpMethod.POST, null, Map.of(
                "username", username, "password", "correct-password", "displayName", username,
                "email", email, "systemName", systemCode, "systemCode", systemCode));
        return new Session((String) ((Map<?, ?>) result.get("tokens")).get("accessToken"),
                number(result.get("systemId")), number(result.get("tenantId")));
    }

    private String login(String username, String password) {
        return (String) ok("/api/auth/login", HttpMethod.POST, null,
                Map.of("username", username, "password", password)).get("accessToken");
    }

    private Map<String, Object> ok(String path, HttpMethod method, String token, Object body) {
        Response response = exchange(path, method, token, body);
        assertThat(response.status()).withFailMessage("%s %s failed: %s", method, path, response.body())
                .isEqualTo(HttpStatus.OK);
        return map(response.body().get("data"));
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
    private Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> list(Object value) {
        return (List<Map<String, Object>>) value;
    }

    private long number(Object value) {
        return value instanceof Number number ? number.longValue() : new java.math.BigDecimal(String.valueOf(value)).longValue();
    }

    private int count(String table, String condition) {
        return jdbc.queryForObject("select count(*) from " + table + " where " + condition, Integer.class);
    }

    private record Session(String token, long systemId, long tenantId) {
    }

    private record Response(HttpStatus status, Map<String, Object> body) {
    }
}
