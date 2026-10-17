package com.unique.unexamine.analytics.manage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.LongNode;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.moduleconfig.manage.ModulePublicationService;
import com.unique.unexamine.moduleconfig.manage.RuntimeModuleConfiguration;
import com.unique.unexamine.runtimedata.manage.RuntimeDataService;
import com.unique.unexamine.runtimedata.manage.RuntimeListQueryField;
import com.unique.unexamine.runtimedata.manage.RuntimeRecordList;
import com.unique.unexamine.runtimedata.manage.RuntimeRecordView;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;

/** 报表定义只能连接已发布的真实关联字段，避免用普通数字字段拼出伪业务关系。 */
class ReportDefinitionContractTest {
    private final ObjectMapper json = new ObjectMapper();
    private final ModulePublicationService publications = mock(ModulePublicationService.class);
    private final RuntimeDataService runtimeData = mock(RuntimeDataService.class);
    private final ReportService reports = new ReportService(null, null, null, publications,
            runtimeData, null, null, json, null);
    private final AuthenticatedContext actor = new AuthenticatedContext(null, 1L, 1L, 10L, 20L,
            30L, 40L, "tester", "测试人", "NONE", List.of(), List.of(), Map.of());

    @Test
    void acceptsDeclaredReferenceButRejectsMatchingIdsWithoutOne() {
        publishedModules();

        Map<String, Object> valid = definition("customer_id");
        assertThat(reports.prepareDefinition(actor, valid, "test")).containsKey("queryExplanation");
        assertThat(reports.prepareDefinition(actor,
                definition("SUBTABLE", "order_lines", "customer_id"), "test"))
                .containsKey("queryExplanation");

        assertThatThrownBy(() -> reports.prepareDefinition(actor, definition("id"), "test"))
                .isInstanceOfSatisfying(DomainException.class,
                        error -> assertThat(error.code()).isEqualTo("REPORT_RELATION_NOT_DECLARED"));
    }

    @Test
    void subtableJoinsOnlyChildrenPointingBackToTheAuthorizedParent() {
        publishedModules();
        RuntimeRecordView customer = record(1L, "客户甲", null);
        RuntimeRecordView matchingA = record(11L, "明细一", 1L);
        RuntimeRecordView matchingB = record(12L, "明细二", 1L);
        RuntimeRecordView other = record(13L, "其他明细", 999L);
        when(runtimeData.list(eq(actor), eq("customer"), any(), any(), any(), any(), any(), any(),
                anyInt(), anyInt(), any())).thenReturn(new RuntimeRecordList(List.of(customer), 1, 1, 200,
                queryFields("id", "title")));
        when(runtimeData.list(eq(actor), eq("orders"), any(), any(), any(), any(), any(), any(),
                anyInt(), anyInt(), any())).thenReturn(new RuntimeRecordList(
                List.of(matchingA, matchingB, other), 3, 1, 200, queryFields("customer_id")));

        ReportModels.Result result = ReflectionTestUtils.invokeMethod(reports, "executeDefinition", actor,
                definition("SUBTABLE", "order_lines", "customer_id"), null, null, null, "test");
        assertThat(result).isNotNull();
        assertThat(result.value()).isEqualTo(2L);
        assertThat(result.items()).hasSize(2);
    }

    @Test
    void rejectsRevokedOutputFieldEvenWhenTheModuleHasNoRows() {
        publishedModules();
        when(runtimeData.list(eq(actor), eq("customer"), any(), any(), any(), any(), any(), any(),
                anyInt(), anyInt(), any())).thenReturn(new RuntimeRecordList(List.of(), 0, 1, 200,
                queryFields("id")));
        Map<String, Object> single = Map.of(
                "modules", List.of(Map.of("alias", "primary", "moduleCode", "customer")),
                "relations", List.of(),
                "outputFields", List.of(Map.of("alias", "primary", "fieldCode", "nickname")),
                "metric", Map.of("operation", "COUNT"), "dimension", Map.of(),
                "filters", List.of(), "sort", Map.of(), "timeField", Map.of());

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(reports, "executeDefinition", actor,
                single, null, null, null, "test"))
                .isInstanceOfSatisfying(DomainException.class,
                        error -> assertThat(error.code()).isEqualTo("REPORT_CURRENT_FIELD_FORBIDDEN"));
    }

    private List<RuntimeListQueryField> queryFields(String... codes) {
        return java.util.Arrays.stream(codes)
                .map(code -> new RuntimeListQueryField(code, code, "TEXT", false, true, List.of(), List.of()))
                .toList();
    }

    private void publishedModules() {
        when(publications.runtime(actor, "customer", "test"))
                .thenReturn(module(101L, "客户", List.of(
                        Map.of("code", "order_lines", "name", "订单明细", "fieldType", "SUBTABLE",
                                "referenceModuleId", 102L, "searchable", false,
                                "access", Map.of("readable", true)),
                        Map.of("code", "nickname", "name", "客户别名", "fieldType", "TEXT",
                                "searchable", true, "access", Map.of("readable", true)))));
        when(publications.runtime(actor, "orders", "test"))
                .thenReturn(module(102L, "订单", List.of(Map.of(
                        "code", "customer_id", "name", "客户", "fieldType", "REFERENCE",
                        "referenceModuleId", 101L, "searchable", true,
                        "access", Map.of("readable", true)))));
    }

    private RuntimeRecordView record(Long id, String title, Long customerId) {
        RuntimeRecordView record = mock(RuntimeRecordView.class);
        when(record.id()).thenReturn(id);
        when(record.title()).thenReturn(title);
        when(record.fields()).thenReturn(customerId == null ? Map.of()
                : Map.of("customer_id", LongNode.valueOf(customerId)));
        return record;
    }

    private RuntimeModuleConfiguration module(Long id, String name, List<Map<String, Object>> fields) {
        return new RuntimeModuleConfiguration(id, id + 1000, 1, 1,
                json.valueToTree(Map.of("module", Map.of("name", name), "fields", fields)));
    }

    private Map<String, Object> definition(String relatedField) {
        return definition("REFERENCE", "id", relatedField);
    }

    private Map<String, Object> definition(String relationType, String primaryField, String relatedField) {
        return Map.of(
                "modules", List.of(Map.of("alias", "primary", "moduleCode", "customer"),
                        Map.of("alias", "related", "moduleCode", "orders")),
                "relations", List.of(Map.of("relationType", relationType, "joinType", "INNER",
                        "leftAlias", "primary", "leftField", primaryField,
                        "rightAlias", "related", "rightField", relatedField)),
                "outputFields", List.of(Map.of("alias", "primary", "fieldCode", "title")),
                "metric", Map.of("operation", "COUNT"), "dimension", Map.of(),
                "filters", List.of(), "sort", Map.of(), "timeField", Map.of(),
                "maxScanRows", 100, "limit", 20);
    }
}
