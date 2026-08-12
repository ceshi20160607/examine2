package com.unique.examine.ai.plan;

import com.unique.examine.ai.domain.AiPolicy;
import com.unique.examine.core.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiContextReadPlanParserTest {
    private final AiContextReadPlanParser parser = new AiContextReadPlanParser();

    @Test
    void parsesAllTwelveExactBoundedReadPlans() {
        var record = (AiContextReadPlanParser.RecordContextPlan) parser.parse("""
                {"operation":"RECORD_CONTEXT_SUMMARY","moduleCode":"orders",
                 "recordId":"501","outputFields":["status","amount"]}
                """, policy());
        assertThat(record.moduleCode()).isEqualTo("orders");
        assertThat(record.recordId()).isEqualTo("501");
        assertThat(record.outputFields()).containsExactly("status", "amount");
        assertThat(record.planHash()).hasSize(64);

        var task = (AiContextReadPlanParser.TaskQueryPlan) parser.parse("""
                {"operation":"WORK_TASK_QUERY","keyword":"quarter close",
                 "projectId":"701","dueFrom":"2026-08-01T00:00:00Z",
                 "dueTo":"2026-08-31T23:59:59Z","status":"OPEN",
                 "role":"PARTICIPATING","limit":7}
                """, policy());
        assertThat(task.projectId()).isEqualTo("701");
        assertThat(task.dueFrom()).isEqualTo(
                Instant.parse("2026-08-01T00:00:00Z"));
        assertThat(task.limit()).isEqualTo(7);

        var report = (AiContextReadPlanParser.DailyReportQueryPlan) parser.parse("""
                {"operation":"WORK_DAILY_REPORT_QUERY","scope":"ALL",
                 "authorMemberId":"81","dateFrom":"2026-07-29",
                 "dateTo":"2026-08-04","status":"SUBMITTED","limit":7}
                """, policy());
        assertThat(report.authorMemberId()).isEqualTo("81");
        assertThat(report.dateFrom()).isEqualTo(LocalDate.parse("2026-07-29"));
        assertThat(report.limit()).isEqualTo(7);

        var metrics = (AiContextReadPlanParser.ProjectMetricsQueryPlan) parser.parse("""
                {"operation":"WORK_PROJECT_METRICS_QUERY","projectId":"701",
                 "fromInclusive":"2026-07-29","toExclusive":"2026-08-05"}
                """, policy());
        assertThat(metrics.projectId()).isEqualTo("701");
        assertThat(metrics.fromInclusive()).isEqualTo(LocalDate.parse("2026-07-29"));
        assertThat(metrics.toExclusive()).isEqualTo(LocalDate.parse("2026-08-05"));
        assertThat(metrics.planHash()).hasSize(64);

        var todo = (AiContextReadPlanParser.TodoQueryPlan) parser.parse("""
                {"operation":"TODO_QUERY","category":"APPROVAL",
                 "state":"OPEN","time":"OVERDUE","limit":10}
                """, policy());
        assertThat(todo.category()).isEqualTo(
                AiContextReadPlanParser.TodoCategory.APPROVAL);
        assertThat(todo.state()).isEqualTo(AiContextReadPlanParser.TodoState.OPEN);
        assertThat(todo.time()).isEqualTo(AiContextReadPlanParser.TodoTime.OVERDUE);
        assertThat(todo.limit()).isEqualTo(10);

        var message = (AiContextReadPlanParser.MessageQueryPlan) parser.parse("""
                {"operation":"MESSAGE_QUERY","status":"UNREAD","limit":20}
                """, policy());
        assertThat(message.status()).isEqualTo(
                AiContextReadPlanParser.MessageStatus.UNREAD);
        assertThat(message.limit()).isEqualTo(20);

        var comments = (AiContextReadPlanParser.RecordCommentQueryPlan)
                parser.parse("""
                        {"operation":"RECORD_COMMENT_QUERY","moduleCode":"orders",
                         "recordId":"501","limit":9}
                        """, policy());
        assertThat(comments.moduleCode()).isEqualTo("orders");
        assertThat(comments.recordId()).isEqualTo("501");
        assertThat(comments.limit()).isEqualTo(9);
        assertThat(comments.planHash()).hasSize(64);

        var history = (AiContextReadPlanParser.RecordHistoryQueryPlan)
                parser.parse("""
                        {"operation":"RECORD_HISTORY_QUERY","moduleCode":"orders",
                         "recordId":"501","limit":10}
                        """, policy());
        assertThat(history.limit()).isEqualTo(10);

        var files = (AiContextReadPlanParser.RecordFileQueryPlan)
                parser.parse("""
                        {"operation":"RECORD_FILE_QUERY","moduleCode":"orders",
                         "recordId":"501","limit":20}
                        """, policy());
        assertThat(files.limit()).isEqualTo(20);

        var flowHistory = (AiContextReadPlanParser.FlowInstanceHistoryQueryPlan)
                parser.parse("""
                        {"operation":"FLOW_INSTANCE_HISTORY_QUERY",
                         "instanceId":"801","limit":20}
                        """, policy());
        assertThat(flowHistory.instanceId()).isEqualTo("801");
        assertThat(flowHistory.limit()).isEqualTo(20);
        assertThat(flowHistory.planHash()).hasSize(64);

        var statistics = (AiContextReadPlanParser.RuntimeStatisticsQueryPlan)
                parser.parse("""
                        {"operation":"RUNTIME_STATISTICS_QUERY",
                         "moduleCode":"orders","dataSourceCode":"order_metrics",
                         "aggregation":"COUNT","measureFieldCode":null,
                         "grouping":null,"trend":{"fieldCode":"event_time",
                         "grain":"DAY","startInclusive":"2026-08-01",
                         "endExclusive":"2026-08-04"}}
                        """, policy());
        assertThat(statistics.moduleCode()).isEqualTo("orders");
        assertThat(statistics.dataSourceCode()).isEqualTo("order_metrics");
        assertThat(statistics.aggregation()).isEqualTo(
                AiContextReadPlanParser.StatisticsAggregation.COUNT);
        assertThat(statistics.measureFieldCode()).isNull();
        assertThat(statistics.grouping()).isNull();
        assertThat(statistics.trend().fieldCode()).isEqualTo("event_time");
        assertThat(statistics.trend().grain()).isEqualTo(
                AiContextReadPlanParser.StatisticsGrain.DAY);
        assertThat(statistics.trend().bucketCount()).isEqualTo(3);
        assertThat(statistics.planHash()).hasSize(64);

        var runtimeReport = (AiContextReadPlanParser.RuntimeReportQueryPlan)
                parser.parse("""
                        {"operation":"RUNTIME_REPORT_QUERY",
                         "moduleCode":"orders","reportCode":"open_orders",
                         "page":10000,"size":20}
                        """, policy());
        assertThat(runtimeReport.moduleCode()).isEqualTo("orders");
        assertThat(runtimeReport.reportCode()).isEqualTo("open_orders");
        assertThat(runtimeReport.page()).isEqualTo(10_000);
        assertThat(runtimeReport.size()).isEqualTo(20);
        assertThat(runtimeReport.planHash()).hasSize(64);
    }

    @Test
    void parsesScalarGroupedAndGrainAlignedRuntimeStatistics() {
        var scalar = (AiContextReadPlanParser.RuntimeStatisticsQueryPlan)
                parser.parse("""
                        {"operation":"RUNTIME_STATISTICS_QUERY",
                         "moduleCode":"orders","dataSourceCode":"order_metrics",
                         "aggregation":"SUM","measureFieldCode":"amount",
                         "grouping":null,"trend":null}
                        """, policy());
        assertThat(scalar.measureFieldCode()).isEqualTo("amount");
        assertThat(scalar.grouping()).isNull();
        assertThat(scalar.trend()).isNull();

        var grouped = (AiContextReadPlanParser.RuntimeStatisticsQueryPlan)
                parser.parse("""
                        {"operation":"RUNTIME_STATISTICS_QUERY",
                         "moduleCode":"orders","dataSourceCode":"order_metrics",
                         "aggregation":"AVG","measureFieldCode":"amount",
                         "grouping":{"fieldCode":"status","bucketLimit":20},
                         "trend":null}
                        """, policy());
        assertThat(grouped.grouping()).isEqualTo(
                new AiContextReadPlanParser.StatisticsGrouping("status", 20));

        var weekly = (AiContextReadPlanParser.RuntimeStatisticsQueryPlan)
                parser.parse("""
                        {"operation":"RUNTIME_STATISTICS_QUERY",
                         "moduleCode":"orders","dataSourceCode":"order_metrics",
                         "aggregation":"COUNT","measureFieldCode":null,
                         "grouping":null,"trend":{"fieldCode":"event_time",
                         "grain":"WEEK","startInclusive":"2026-08-03",
                         "endExclusive":"2026-08-17"}}
                        """, policy());
        assertThat(weekly.trend().bucketCount()).isEqualTo(2);
    }

    @Test
    void rejectsUnknownDuplicateUnauthorizedAndWriteMaterialBeforeExecution() {
        rejects("""
                {"operation":"RECORD_CONTEXT_SUMMARY","moduleCode":"orders",
                 "recordId":"501","outputFields":["status"],"limit":1}
                """);
        rejects("""
                {"operation":"WORK_TASK_QUERY","keyword":null,"projectId":null,
                 "dueFrom":null,"dueTo":null,"status":"OPEN","status":"ALL",
                 "role":"PARTICIPATING","limit":1}
                """);
        rejects("""
                {"operation":"RECORD_CONTEXT_SUMMARY","moduleCode":"orders",
                 "recordId":"501","outputFields":["secret"]}
                """);
        rejects("""
                {"operation":"WORK_TASK_QUERY","keyword":"delete from task",
                 "projectId":null,"dueFrom":null,"dueTo":null,"status":"OPEN",
                 "role":"PARTICIPATING","limit":1}
                """);
        rejects("""
                {"operation":"TODO_QUERY","category":"TASK","state":"OPEN",
                 "time":"TODAY","limit":1,"memberId":"81"}
                """);
        rejects("""
                {"operation":"TODO_QUERY","category":"TASK","category":"ALL",
                 "state":"OPEN","time":"TODAY","limit":1}
                """);
        rejects("""
                {"operation":"MESSAGE_QUERY","status":"UNREAD","limit":1.0}
                """);
        rejects("""
                {"operation":"WORK_PROJECT_METRICS_QUERY","projectId":"701",
                 "fromInclusive":"2026-08-01","toExclusive":"2026-08-05",
                 "tenantId":"2"}
                """);
        rejects("""
                {"operation":"WORK_PROJECT_METRICS_QUERY","projectId":"701",
                 "projectId":"702","fromInclusive":"2026-08-01",
                 "toExclusive":"2026-08-05"}
                """);
        rejects("""
                {"operation":"WORK_PROJECT_METRICS_QUERY","projectId":"701",
                 "fromInclusive":"2026-08-01"}
                """);
        rejects("""
                {"operation":"WORK_PROJECT_METRICS_QUERY","projectId":"https://bad",
                 "fromInclusive":"2026-08-01","toExclusive":"2026-08-05"}
                """);
        rejects("""
                {"operation":"RECORD_COMMENT_QUERY","moduleCode":"orders",
                 "recordId":"501","limit":1,"page":1}
                """);
        rejects("""
                {"operation":"RECORD_HISTORY_QUERY","moduleCode":"orders",
                 "recordId":"0501","limit":1}
                """);
        rejects("""
                {"operation":"RECORD_FILE_QUERY","moduleCode":"orders",
                 "recordId":"501","limit":21}
                """);
        rejects("""
                {"operation":"RECORD_FILE_QUERY","moduleCode":"orders",
                 "recordId":"501","limit":1,"limit":2}
                """);
        rejects("""
                {"operation":"FLOW_INSTANCE_HISTORY_QUERY",
                 "instanceId":"801","limit":1,"tenantId":"2"}
                """);
        rejects("""
                {"operation":"FLOW_INSTANCE_HISTORY_QUERY",
                 "instanceId":"0801","limit":1}
                """);
        rejects("""
                {"operation":"FLOW_INSTANCE_HISTORY_QUERY",
                 "instanceId":"801","limit":21}
                """);
        rejects("""
                {"operation":"FLOW_INSTANCE_HISTORY_QUERY",
                 "instanceId":"801","instanceId":"802","limit":1}
                """);
        rejects("""
                {"operation":"RUNTIME_STATISTICS_QUERY",
                 "moduleCode":"orders","dataSourceCode":"order_metrics",
                 "aggregation":"COUNT","measureFieldCode":null,
                 "grouping":null,"trend":null,"tenantId":"2"}
                """);
        rejects("""
                {"operation":"RUNTIME_STATISTICS_QUERY",
                 "moduleCode":"orders","dataSourceCode":"order_metrics",
                 "aggregation":"COUNT","aggregation":"SUM",
                 "measureFieldCode":null,"grouping":null,"trend":null}
                """);
        rejects("""
                {"operation":"RUNTIME_REPORT_QUERY","moduleCode":"orders",
                 "reportCode":"open_orders","page":1,"size":1,
                 "outputFields":["status"]}
                """);
        rejects("""
                {"operation":"RUNTIME_REPORT_QUERY","moduleCode":"orders",
                 "reportCode":"open_orders","page":1,"page":2,"size":1}
                """);
        rejects("""
                {"operation":"RUNTIME_REPORT_QUERY","moduleCode":"orders",
                 "reportCode":null,"page":1,"size":1}
                """);
    }

    @Test
    void rejectsPolicyAndScopeOrWindowEscalation() {
        var queryOnly = policy(Set.of("RECORD_QUERY"));
        assertThatThrownBy(() -> parser.parse("""
                {"operation":"WORK_TASK_QUERY","keyword":null,"projectId":null,
                 "dueFrom":null,"dueTo":null,"status":"OPEN",
                 "role":"PARTICIPATING","limit":1}
                """, queryOnly)).isInstanceOf(BusinessException.class)
                .extracting(failure -> ((BusinessException) failure).code())
                .isEqualTo("AI_PLAN_INVALID");
        rejects("""
                {"operation":"WORK_DAILY_REPORT_QUERY","scope":"SELF",
                 "authorMemberId":"81","dateFrom":"2026-08-01",
                 "dateTo":"2026-08-04","status":"ALL","limit":1}
                """);
        rejects("""
                {"operation":"WORK_DAILY_REPORT_QUERY","scope":"SELF",
                 "authorMemberId":null,"dateFrom":"2025-01-01",
                 "dateTo":"2026-08-04","status":"ALL","limit":1}
                """);
        rejects("""
                {"operation":"WORK_TASK_QUERY","keyword":null,"projectId":null,
                 "dueFrom":"2025-01-01T00:00:00Z",
                 "dueTo":"2026-08-04T00:00:00Z","status":"OPEN",
                 "role":"PARTICIPATING","limit":1}
                """);
        rejects("""
                {"operation":"TODO_QUERY","category":"ALL","state":"ACTIVE",
                 "time":"ALL","limit":1}
                """);
        rejects("""
                {"operation":"MESSAGE_QUERY","status":"ALL","limit":21}
                """);
        rejects("""
                {"operation":"WORK_PROJECT_METRICS_QUERY","projectId":"0701",
                 "fromInclusive":"2026-08-01","toExclusive":"2026-08-05"}
                """);
        rejects("""
                {"operation":"WORK_PROJECT_METRICS_QUERY","projectId":"701",
                 "fromInclusive":"2026-08-05","toExclusive":"2026-08-05"}
                """);
        rejects("""
                {"operation":"WORK_PROJECT_METRICS_QUERY","projectId":"701",
                 "fromInclusive":"2026-08-05","toExclusive":"2026-08-04"}
                """);
        rejects("""
                {"operation":"WORK_PROJECT_METRICS_QUERY","projectId":"701",
                 "fromInclusive":"2026-08-01","toExclusive":"2026-09-02"}
                """);
        assertThatThrownBy(() -> parser.parse("""
                {"operation":"WORK_PROJECT_METRICS_QUERY","projectId":"701",
                 "fromInclusive":"2026-08-01","toExclusive":"2026-08-05"}
                """, queryOnly)).isInstanceOf(BusinessException.class)
                .extracting(failure -> ((BusinessException) failure).code())
                .isEqualTo("AI_PLAN_INVALID");
        assertThatThrownBy(() -> parser.parse("""
                {"operation":"MESSAGE_QUERY","status":"ALL","limit":1}
                """, queryOnly)).isInstanceOf(BusinessException.class)
                .extracting(failure -> ((BusinessException) failure).code())
                .isEqualTo("AI_PLAN_INVALID");
        assertThatThrownBy(() -> parser.parse("""
                {"operation":"RECORD_COMMENT_QUERY","moduleCode":"privateOrders",
                 "recordId":"501","limit":1}
                """, policy())).isInstanceOf(BusinessException.class)
                .extracting(failure -> ((BusinessException) failure).code())
                .isEqualTo("AI_PLAN_INVALID");
        assertThatThrownBy(() -> parser.parse("""
                {"operation":"RECORD_HISTORY_QUERY","moduleCode":"orders",
                 "recordId":"501","limit":1}
                """, queryOnly)).isInstanceOf(BusinessException.class)
                .extracting(failure -> ((BusinessException) failure).code())
                .isEqualTo("AI_PLAN_INVALID");
        rejects("""
                {"operation":"RUNTIME_STATISTICS_QUERY",
                 "moduleCode":"orders","dataSourceCode":"order_metrics",
                 "aggregation":"COUNT","measureFieldCode":"amount",
                 "grouping":null,"trend":null}
                """);
        rejects("""
                {"operation":"RUNTIME_STATISTICS_QUERY",
                 "moduleCode":"orders","dataSourceCode":"order_metrics",
                 "aggregation":"SUM","measureFieldCode":null,
                 "grouping":null,"trend":null}
                """);
        rejects("""
                {"operation":"RUNTIME_STATISTICS_QUERY",
                 "moduleCode":"orders","dataSourceCode":"order_metrics",
                 "aggregation":"COUNT","measureFieldCode":null,
                 "grouping":{"fieldCode":"status","bucketLimit":20},
                 "trend":{"fieldCode":"event_time","grain":"DAY",
                 "startInclusive":"2026-08-01","endExclusive":"2026-08-02"}}
                """);
        rejects("""
                {"operation":"RUNTIME_STATISTICS_QUERY",
                 "moduleCode":"orders","dataSourceCode":"order_metrics",
                 "aggregation":"COUNT","measureFieldCode":null,
                 "grouping":{"fieldCode":"secret","bucketLimit":1},
                 "trend":null}
                """);
        rejects("""
                {"operation":"RUNTIME_STATISTICS_QUERY",
                 "moduleCode":"orders","dataSourceCode":"order_metrics",
                 "aggregation":"COUNT","measureFieldCode":null,
                 "grouping":null,"trend":{"fieldCode":"event_time",
                 "grain":"WEEK","startInclusive":"2026-08-01",
                 "endExclusive":"2026-08-17"}}
                """);
        rejects("""
                {"operation":"RUNTIME_STATISTICS_QUERY",
                 "moduleCode":"orders","dataSourceCode":"order_metrics",
                 "aggregation":"COUNT","measureFieldCode":null,
                 "grouping":null,"trend":{"fieldCode":"event_time",
                 "grain":"DAY","startInclusive":"2026-08-01",
                 "endExclusive":"2026-08-22"}}
                """);
        assertThatThrownBy(() -> parser.parse("""
                {"operation":"RUNTIME_STATISTICS_QUERY",
                 "moduleCode":"orders","dataSourceCode":"order_metrics",
                 "aggregation":"COUNT","measureFieldCode":null,
                 "grouping":null,"trend":null}
                """, queryOnly)).isInstanceOf(BusinessException.class)
                .extracting(failure -> ((BusinessException) failure).code())
                .isEqualTo("AI_PLAN_INVALID");
        rejects("""
                {"operation":"RUNTIME_REPORT_QUERY","moduleCode":"orders",
                 "reportCode":"open_orders","page":0,"size":1}
                """);
        rejects("""
                {"operation":"RUNTIME_REPORT_QUERY","moduleCode":"orders",
                 "reportCode":"open_orders","page":10001,"size":1}
                """);
        rejects("""
                {"operation":"RUNTIME_REPORT_QUERY","moduleCode":"orders",
                 "reportCode":"open_orders","page":1,"size":21}
                """);
        rejects("""
                {"operation":"RUNTIME_REPORT_QUERY","moduleCode":"privateOrders",
                 "reportCode":"open_orders","page":1,"size":1}
                """);
        assertThatThrownBy(() -> parser.parse("""
                {"operation":"RUNTIME_REPORT_QUERY","moduleCode":"orders",
                 "reportCode":"open_orders","page":1,"size":1}
                """, queryOnly)).isInstanceOf(BusinessException.class)
                .extracting(failure -> ((BusinessException) failure).code())
                .isEqualTo("AI_PLAN_INVALID");
    }

    private void rejects(String value) {
        assertThatThrownBy(() -> parser.parse(value, policy()))
                .isInstanceOf(BusinessException.class)
                .extracting(failure -> ((BusinessException) failure).code())
                .isEqualTo("AI_PLAN_INVALID");
    }

    private static AiPolicy.Version policy() {
        return policy(Set.of(
                "RECORD_QUERY", "RECORD_CONTEXT_SUMMARY",
                "WORK_TASK_QUERY", "WORK_DAILY_REPORT_QUERY",
                "WORK_PROJECT_METRICS_QUERY",
                "TODO_QUERY", "MESSAGE_QUERY", "RECORD_COMMENT_QUERY",
                "RECORD_HISTORY_QUERY", "RECORD_FILE_QUERY",
                "FLOW_INSTANCE_HISTORY_QUERY",
                "RUNTIME_STATISTICS_QUERY", "RUNTIME_REPORT_QUERY"));
    }

    private static AiPolicy.Version policy(Set<String> operations) {
        return new AiPolicy.Version(
                91, 1, 2, 90, 1, 80, 3, "gpt-read",
                Set.of("orders"), Map.of("orders", Set.of(
                        "status", "amount", "event_time")),
                operations, 20, true, AiPolicy.RedactionMode.STRICT,
                "v1", "a".repeat(64),
                Instant.parse("2026-08-04T00:00:00Z"), 7);
    }
}
