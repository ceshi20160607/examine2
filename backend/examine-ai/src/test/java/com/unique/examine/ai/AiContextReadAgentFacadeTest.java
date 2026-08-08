package com.unique.examine.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.ai.domain.AiConversation;
import com.unique.examine.ai.domain.AiPolicy;
import com.unique.examine.ai.domain.AiProvider;
import com.unique.examine.ai.plan.AiContextReadPlanParser;
import com.unique.examine.ai.plan.AiRecordQueryPlanParser;
import com.unique.examine.ai.provider.AiProviderClient;
import com.unique.examine.core.ai.AiRecordContextFacade;
import com.unique.examine.core.ai.AiRecordQueryFacade;
import com.unique.examine.core.ai.AiRuntimeReportReadFacade;
import com.unique.examine.core.ai.AiRuntimeStatisticsReadFacade;
import com.unique.examine.core.ai.AiMessageReadFacade;
import com.unique.examine.core.ai.AiTodoReadFacade;
import com.unique.examine.core.ai.AiWorkQueryFacade;
import com.unique.examine.core.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AiContextReadAgentFacadeTest {
    private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");

    @Test
    void serializesRuntimeReportNullDisplayValueExplicitly() throws Exception {
        var json = new ObjectMapper()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .writeValueAsString(new AiAgentFacade.ContextRuntimeReportValue(
                        "status", null));

        assertThat(json).isEqualTo("{\"fieldCode\":\"status\",\"displayValue\":null}");
    }

    @Test
    void routesSixTypedReadsThroughOwnersAndPersistsOnlyRedactedEvidence() {
        var repository = repository();
        var provider = new ScriptedProvider(List.of(
                completion("""
                        {"operation":"RECORD_CONTEXT_SUMMARY","moduleCode":"orders",
                         "recordId":"501","outputFields":["status","amount"]}
                        """),
                completion("Authorized record context."),
                completion("""
                        {"operation":"WORK_TASK_QUERY","keyword":null,
                         "projectId":null,"dueFrom":"2026-08-01T00:00:00Z",
                         "dueTo":"2026-08-31T23:59:59Z","status":"OPEN",
                         "role":"PARTICIPATING","limit":5}
                        """),
                completion("Authorized task context."),
                completion("""
                        {"operation":"WORK_DAILY_REPORT_QUERY","scope":"SELF",
                         "authorMemberId":null,"dateFrom":"2026-07-29",
                         "dateTo":"2026-08-04","status":"ALL","limit":7}
                        """),
                completion("Authorized report context."),
                completion("""
                        {"operation":"WORK_PROJECT_METRICS_QUERY","projectId":"701",
                         "fromInclusive":"2026-07-29","toExclusive":"2026-08-05"}
                        """),
                completion("Authorized project metrics context."),
                completion("""
                        {"operation":"TODO_QUERY","category":"APPROVAL",
                         "state":"OPEN","time":"OVERDUE","limit":5}
                        """),
                completion("Secret todo title at /systems/1/private/todo."),
                completion("""
                        {"operation":"MESSAGE_QUERY","status":"UNREAD","limit":5}
                        """),
                completion("Secret message title and private message body.")));
        var recordRequests = new ArrayList<AiRecordContextFacade.Request>();
        AiRecordContextFacade records = request -> {
            recordRequests.add(request);
            return new AiRecordContextFacade.Result("orders",
                    new AiRecordContextFacade.Record(
                            "501", "ORD-501", 4, "ACTIVE", "Secret order title",
                            List.of(
                                    new AiRecordContextFacade.DisplayValue(
                                            "status", "OPEN"),
                                    new AiRecordContextFacade.DisplayValue(
                                            "amount", "123.45"))));
        };
        var work = new WorkOwner();
        var todoRequests = new ArrayList<AiTodoReadFacade.Request>();
        AiTodoReadFacade todos = request -> {
            todoRequests.add(request);
            return new AiTodoReadFacade.Result(
                    AiTodoReadFacade.Category.APPROVAL,
                    AiTodoReadFacade.State.OPEN,
                    AiTodoReadFacade.Time.OVERDUE, 1,
                    new AiTodoReadFacade.Counts(4, 1, 3, 2, 1),
                    List.of(new AiTodoReadFacade.Item(
                            "901", AiTodoReadFacade.Category.APPROVAL,
                            AiTodoReadFacade.SourceType.FLOW_APPROVAL, "301",
                            "Secret todo title", 90, NOW.plusSeconds(3_600),
                            "/systems/1/private/todo",
                            List.of(AiTodoReadFacade.Action.APPROVE,
                                    AiTodoReadFacade.Action.REJECT),
                            AiTodoReadFacade.State.OPEN, 3)));
        };
        var messageRequests = new ArrayList<AiMessageReadFacade.Request>();
        AiMessageReadFacade messages = request -> {
            messageRequests.add(request);
            return new AiMessageReadFacade.Result(
                    AiMessageReadFacade.Status.UNREAD, 3, 3,
                    List.of(new AiMessageReadFacade.Message(
                            "902", "WORK_REMINDER", "Secret message title",
                            "Private message body",
                            new AiMessageReadFacade.Target("WORK_TASK", "601"),
                            "/systems/1/private/message",
                            "UNREAD", NOW.minusSeconds(60), null, null, 2)));
        };
        AiRecordQueryFacade ordinaryQuery = request -> {
            throw new AssertionError("RECORD_QUERY must not execute for context reads");
        };
        var facade = new AiAgentFacade(
                repository, provider, new AiRecordQueryPlanParser(),
                new AiContextReadPlanParser(), ordinaryQuery, records, work,
                todos, messages,
                new SequenceIdService(1000), Clock.fixed(NOW, ZoneOffset.UTC),
                new ObjectMapper());
        var actor = actor();
        var session = facade.createSession(
                actor, new AiAgentFacade.CreateSession("Context questions"));

        var record = facade.submit(actor, session.id(),
                new AiAgentFacade.SubmitMessage("Summarize record"));
        var task = facade.submit(actor, session.id(),
                new AiAgentFacade.SubmitMessage("Show due tasks"));
        var report = facade.submit(actor, session.id(),
                new AiAgentFacade.SubmitMessage("Show seven-day reports"));
        var metrics = facade.submit(actor, session.id(),
                new AiAgentFacade.SubmitMessage("Show project progress"));
        var todo = facade.submit(actor, session.id(),
                new AiAgentFacade.SubmitMessage("Show overdue approvals"));
        var message = facade.submit(actor, session.id(),
                new AiAgentFacade.SubmitMessage("Show unread messages"));

        assertThat(record.contextResult().operation())
                .isEqualTo("RECORD_CONTEXT_SUMMARY");
        assertThat(record.contextResult().record().recordVersion()).isEqualTo(4);
        assertThat(record.contextResult().record().values())
                .containsEntry("amount", "123.45");
        assertThat(record.contextResult().tasks()).isEmpty();
        assertThat(task.contextResult().operation()).isEqualTo("WORK_TASK_QUERY");
        assertThat(task.contextResult().tasks()).singleElement().satisfies(value -> {
            assertThat(value.taskId()).isEqualTo("601");
            assertThat(value.description()).isEqualTo("Secret task description");
        });
        assertThat(report.contextResult().operation())
                .isEqualTo("WORK_DAILY_REPORT_QUERY");
        assertThat(report.contextResult().reports()).singleElement().satisfies(value ->
                assertThat(value.completedWork()).isEqualTo("Secret completed work"));
        assertThat(metrics.contextResult().operation())
                .isEqualTo("WORK_PROJECT_METRICS_QUERY");
        assertThat(metrics.contextResult().workMetrics()).satisfies(value -> {
            assertThat(value.projectId()).isEqualTo("701");
            assertThat(value.title()).isEqualTo("Secret project metrics title");
            assertThat(value.visibility()).isEqualTo("PARTICIPATING");
            assertThat(value.total()).isEqualTo(2);
            assertThat(value.open()).isEqualTo(1);
            assertThat(value.completed()).isEqualTo(1);
            assertThat(value.overdueOpen().count()).isEqualTo(1);
            assertThat(value.daily()).hasSize(2);
            assertThat(value.topAssignees()).singleElement().satisfies(assignee -> {
                assertThat(assignee.assigneeMemberId()).isEqualTo("7");
                assertThat(assignee.openCount()).isEqualTo(1);
            });
        });
        assertThat(metrics.contextResult().record()).isNull();
        assertThat(metrics.contextResult().tasks()).isEmpty();
        assertThat(metrics.contextResult().reports()).isEmpty();
        assertThat(metrics.contextResult().todos()).isNull();
        assertThat(metrics.contextResult().messages()).isNull();
        assertThat(todo.contextResult().operation()).isEqualTo("TODO_QUERY");
        assertThat(todo.contextResult().todos()).satisfies(value -> {
            assertThat(value.category()).isEqualTo("APPROVAL");
            assertThat(value.state()).isEqualTo("OPEN");
            assertThat(value.time()).isEqualTo("OVERDUE");
            assertThat(value.total()).isEqualTo(1);
            assertThat(value.counts().approval()).isEqualTo(3);
            assertThat(value.items()).singleElement().satisfies(item -> {
                assertThat(item.title()).isEqualTo("Secret todo title");
                assertThat(item.routeHint()).isEqualTo("/systems/1/private/todo");
                assertThat(item.actions()).containsExactly("APPROVE", "REJECT");
            });
        });
        assertThat(todo.contextResult().messages()).isNull();
        assertThat(message.contextResult().operation()).isEqualTo("MESSAGE_QUERY");
        assertThat(message.contextResult().messages()).satisfies(value -> {
            assertThat(value.status()).isEqualTo("UNREAD");
            assertThat(value.unreadCount()).isEqualTo(3);
            assertThat(value.total()).isEqualTo(3);
            assertThat(value.items()).singleElement().satisfies(item -> {
                assertThat(item.title()).isEqualTo("Secret message title");
                assertThat(item.body()).isEqualTo("Private message body");
                assertThat(item.target().type()).isEqualTo("WORK_TASK");
                assertThat(item.targetPath())
                        .isEqualTo("/systems/1/private/message");
            });
        });
        assertThat(message.contextResult().todos()).isNull();
        assertThat(recordRequests).singleElement().satisfies(request -> {
            assertThat(request.systemId()).isEqualTo(actor.systemId());
            assertThat(request.tenantId()).isEqualTo(actor.tenantId());
            assertThat(request.memberId()).isEqualTo(actor.memberId());
            assertThat(request.effectivePermissions())
                    .isEqualTo(actor.effectivePermissions());
            assertThat(request.effectivePermissions())
                    .isEqualTo(actor.effectivePermissions());
            assertThat(request.outboundFieldCodes())
                    .containsExactly("status", "amount");
        });
        assertThat(work.taskRequests).singleElement().satisfies(request -> {
            assertThat(request.role()).isEqualTo(
                    AiWorkQueryFacade.TaskRole.PARTICIPATING);
            assertThat(request.limit()).isEqualTo(5);
        });
        assertThat(work.reportRequests).singleElement().satisfies(request -> {
            assertThat(request.scope()).isEqualTo(AiWorkQueryFacade.ReportScope.SELF);
            assertThat(request.authorMemberId()).isNull();
        });
        assertThat(work.metricsRequests).singleElement().satisfies(request -> {
            assertThat(request.systemId()).isEqualTo(actor.systemId());
            assertThat(request.tenantId()).isEqualTo(actor.tenantId());
            assertThat(request.memberId()).isEqualTo(actor.memberId());
            assertThat(request.effectivePermissions())
                    .isEqualTo(actor.effectivePermissions());
            assertThat(request.projectId()).isEqualTo("701");
            assertThat(request.fromInclusive())
                    .isEqualTo(LocalDate.parse("2026-07-29"));
            assertThat(request.toExclusive())
                    .isEqualTo(LocalDate.parse("2026-08-05"));
        });
        assertThat(todoRequests).singleElement().satisfies(request -> {
            assertThat(request.systemId()).isEqualTo(actor.systemId());
            assertThat(request.tenantId()).isEqualTo(actor.tenantId());
            assertThat(request.memberId()).isEqualTo(actor.memberId());
            assertThat(request.category())
                    .isEqualTo(AiTodoReadFacade.Category.APPROVAL);
            assertThat(request.state()).isEqualTo(AiTodoReadFacade.State.OPEN);
            assertThat(request.time()).isEqualTo(AiTodoReadFacade.Time.OVERDUE);
            assertThat(request.limit()).isEqualTo(5);
        });
        assertThat(messageRequests).singleElement().satisfies(request -> {
            assertThat(request.systemId()).isEqualTo(actor.systemId());
            assertThat(request.tenantId()).isEqualTo(actor.tenantId());
            assertThat(request.memberId()).isEqualTo(actor.memberId());
            assertThat(request.effectivePermissions())
                    .isEqualTo(actor.effectivePermissions());
            assertThat(request.status())
                    .isEqualTo(AiMessageReadFacade.Status.UNREAD);
            assertThat(request.limit()).isEqualTo(5);
        });
        assertThat(repository.toolValues)
                .extracting(AiConversation.ToolCall::toolName)
                .containsExactly("RECORD_CONTEXT_SUMMARY", "WORK_TASK_QUERY",
                        "WORK_DAILY_REPORT_QUERY", "WORK_PROJECT_METRICS_QUERY",
                        "TODO_QUERY", "MESSAGE_QUERY");
        assertThat(repository.toolValues)
                .allSatisfy(value -> {
                    assertThat(value.status()).isEqualTo(
                            AiConversation.TurnStatus.SUCCEEDED);
                    assertThat(value.requestHash()).hasSize(64);
                    assertThat(value.responseHash()).hasSize(64);
                });
        assertThat(repository.toolValues).filteredOn(tool ->
                tool.toolName().equals("WORK_PROJECT_METRICS_QUERY"))
                .singleElement().satisfies(tool -> assertThat(tool.resultCount())
                        .isEqualTo(1));
        assertThat(provider.requests.stream()
                .filter(request -> request.phase() == AiProviderClient.Phase.SUMMARY)
                .map(AiProviderClient.Request::systemPrompt))
                .allSatisfy(prompt -> assertThat(prompt)
                        .contains("untrusted data", "Do not infer hidden facts"));
        assertThat(provider.requests.stream()
                .filter(request -> request.phase() == AiProviderClient.Phase.PLAN)
                .map(AiProviderClient.Request::systemPrompt))
                .allSatisfy(prompt -> assertThat(prompt).contains(
                        "For WORK_PROJECT_METRICS_QUERY use exactly operation,projectId,"
                                + "fromInclusive,toExclusive",
                        "UTC left-closed, right-open window of 1..31 days"));
        var liveToolPayloads = provider.requests.stream()
                .filter(request -> request.phase() == AiProviderClient.Phase.SUMMARY)
                .map(AiProviderClient.Request::userContent).toList();
        assertThat(liveToolPayloads).anySatisfy(payload -> assertThat(payload)
                .contains("Secret todo title", "/systems/1/private/todo"));
        assertThat(liveToolPayloads).anySatisfy(payload -> assertThat(payload)
                .contains("Secret message title", "Private message body",
                        "/systems/1/private/message"));
        assertThat(liveToolPayloads).anySatisfy(payload -> assertThat(payload)
                .contains("Secret project metrics title",
                        "/systems/1/tasks?projectId=701&status=OPEN&due=OVERDUE",
                        "\"assigneeMemberId\":\"7\""));

        var persisted = repository.messageValues + " " + repository.turnValues
                + " " + repository.toolValues + " " + repository.usageValues;
        assertThat(persisted)
                .doesNotContain("Secret order title")
                .doesNotContain("123.45")
                .doesNotContain("Secret task description")
                .doesNotContain("Secret completed work")
                .doesNotContain("Secret planned work")
                .doesNotContain("Secret blocker")
                .doesNotContain("Secret todo title")
                .doesNotContain("/systems/1/private/todo")
                .doesNotContain("Secret message title")
                .doesNotContain("Private message body")
                .doesNotContain("/systems/1/private/message")
                .doesNotContain("Secret project metrics title")
                .doesNotContain("/systems/1/tasks?projectId=701");
    }

    @Test
    void routesScalarGroupedAndTrendStatisticsWithSafeMutuallyExclusiveResults() {
        var repository = repository();
        var provider = new ScriptedProvider(List.of(
                completion("""
                        {"operation":"RUNTIME_STATISTICS_QUERY",
                         "moduleCode":"orders","dataSourceCode":"order_metrics",
                         "aggregation":"SUM","measureFieldCode":"amount",
                         "grouping":null,"trend":null}
                        """),
                completion("Authorized scalar statistics."),
                completion("""
                        {"operation":"RUNTIME_STATISTICS_QUERY",
                         "moduleCode":"orders","dataSourceCode":"order_metrics",
                         "aggregation":"COUNT","measureFieldCode":null,
                         "grouping":{"fieldCode":"status","bucketLimit":2},
                         "trend":null}
                        """),
                completion("Authorized grouped statistics."),
                completion("""
                        {"operation":"RUNTIME_STATISTICS_QUERY",
                         "moduleCode":"orders","dataSourceCode":"order_metrics",
                         "aggregation":"SUM","measureFieldCode":"amount",
                         "grouping":null,"trend":{"fieldCode":"event_time",
                         "grain":"DAY","startInclusive":"2026-08-01",
                         "endExclusive":"2026-08-04"}}
                        """),
                completion("Authorized trend statistics.")));
        var ownerRequests = new ArrayList<AiRuntimeStatisticsReadFacade.Request>();
        AiRuntimeStatisticsReadFacade owner = request -> {
            ownerRequests.add(request);
            if (request.grouping() != null) {
                return new AiRuntimeStatisticsReadFacade.Result(
                        "order_metrics", "orders", 4,
                        AiRuntimeStatisticsReadFacade.Aggregation.COUNT,
                        null, "3", 3, 2, 3, true,
                        new AiRuntimeStatisticsReadFacade.GroupingResult(
                                "status", List.of(
                                new AiRuntimeStatisticsReadFacade.GroupBucket(
                                        "OPEN", false, "2", 2),
                                new AiRuntimeStatisticsReadFacade.GroupBucket(
                                        null, true, "1", 1))),
                        null);
            }
            if (request.trend() != null) {
                return new AiRuntimeStatisticsReadFacade.Result(
                        "order_metrics", "orders", 4,
                        AiRuntimeStatisticsReadFacade.Aggregation.SUM,
                        "amount", "5", 2, 3, 3, false, null,
                        new AiRuntimeStatisticsReadFacade.TrendResult(
                                "event_time", AiRuntimeStatisticsReadFacade.Grain.DAY,
                                LocalDate.parse("2026-08-01"),
                                LocalDate.parse("2026-08-04"), List.of(
                                new AiRuntimeStatisticsReadFacade.TrendBucket(
                                        LocalDate.parse("2026-08-01"),
                                        LocalDate.parse("2026-08-02"), "0", 1, false),
                                new AiRuntimeStatisticsReadFacade.TrendBucket(
                                        LocalDate.parse("2026-08-02"),
                                        LocalDate.parse("2026-08-03"), null, 0, true),
                                new AiRuntimeStatisticsReadFacade.TrendBucket(
                                        LocalDate.parse("2026-08-03"),
                                        LocalDate.parse("2026-08-04"), "5", 1, false))));
            }
            return new AiRuntimeStatisticsReadFacade.Result(
                    "order_metrics", "orders", 4,
                    AiRuntimeStatisticsReadFacade.Aggregation.SUM,
                    "amount", "123.45", 3, 0, 0, false, null, null);
        };
        var facade = new AiAgentFacade(
                repository, provider, new AiRecordQueryPlanParser(),
                new AiContextReadPlanParser(), request -> {
                    throw new AssertionError("ordinary query must not execute");
                }, owner, new SequenceIdService(1400),
                Clock.fixed(NOW, ZoneOffset.UTC), new ObjectMapper());
        var actor = actor();
        var session = facade.createSession(
                actor, new AiAgentFacade.CreateSession("Runtime statistics"));

        var scalar = facade.submit(actor, session.id(),
                new AiAgentFacade.SubmitMessage("Show scalar statistics"));
        var grouped = facade.submit(actor, session.id(),
                new AiAgentFacade.SubmitMessage("Show grouped statistics"));
        var trend = facade.submit(actor, session.id(),
                new AiAgentFacade.SubmitMessage("Show trend statistics"));

        assertThat(scalar.contextResult().runtimeStatistics()).satisfies(value -> {
            assertThat(value.value()).isEqualTo("123.45");
            assertThat(value.bucketCount()).isZero();
            assertThat(value.grouping()).isNull();
            assertThat(value.trend()).isNull();
        });
        assertThat(scalar.contextResult().record()).isNull();
        assertThat(scalar.contextResult().tasks()).isEmpty();
        assertThat(scalar.contextResult().recordFiles()).isNull();
        assertThat(grouped.contextResult().runtimeStatistics()).satisfies(value -> {
            assertThat(value.truncated()).isTrue();
            assertThat(value.totalBucketCount()).isEqualTo(3);
            assertThat(value.grouping().buckets()).hasSize(2);
            assertThat(value.grouping().buckets().get(1).nullBucket()).isTrue();
            assertThat(value.grouping().buckets().get(1).label()).isNull();
            assertThat(value.trend()).isNull();
        });
        assertThat(trend.contextResult().runtimeStatistics().trend().buckets())
                .satisfiesExactly(
                        value -> {
                            assertThat(value.value()).isEqualTo("0");
                            assertThat(value.empty()).isFalse();
                        },
                        value -> {
                            assertThat(value.value()).isNull();
                            assertThat(value.empty()).isTrue();
                        },
                        value -> assertThat(value.value()).isEqualTo("5"));
        assertThat(ownerRequests).hasSize(3).allSatisfy(request -> {
            assertThat(request.systemId()).isEqualTo(actor.systemId());
            assertThat(request.tenantId()).isEqualTo(actor.tenantId());
            assertThat(request.memberId()).isEqualTo(actor.memberId());
            assertThat(request.effectivePermissions())
                    .isEqualTo(actor.effectivePermissions());
            assertThat(request.allowedModuleCodes()).containsExactly("orders");
            assertThat(request.outboundFields().get("orders"))
                    .containsExactlyInAnyOrder("status", "amount", "event_time");
            assertThat(request.maxRows()).isEqualTo(10);
        });
        assertThat(repository.toolValues)
                .extracting(AiConversation.ToolCall::resultCount)
                .containsExactly(1, 3, 4);
        assertThat(repository.toolValues).allSatisfy(tool -> {
            assertThat(tool.toolName()).isEqualTo("RUNTIME_STATISTICS_QUERY");
            assertThat(tool.status()).isEqualTo(AiConversation.TurnStatus.SUCCEEDED);
            assertThat(tool.requestHash()).hasSize(64);
            assertThat(tool.responseHash()).hasSize(64);
            assertThat(tool.resultCode()).isEqualTo("OK");
        });
        assertThat(provider.requests.stream()
                .filter(request -> request.phase() == AiProviderClient.Phase.SUMMARY)
                .map(AiProviderClient.Request::userContent))
                .anySatisfy(payload -> assertThat(payload)
                        .contains("\"value\":\"123.45\"")
                        .doesNotContain("queryId", "dataSourceVersionId", "schemaVersionId"))
                .anySatisfy(payload -> assertThat(payload)
                        .contains("\"label\":\"OPEN\"", "\"nullBucket\":true")
                        .doesNotContain("\"key\""))
                .anySatisfy(payload -> assertThat(payload)
                        .contains("\"value\":\"0\"", "\"value\":null"));
        var persisted = repository.messageValues + " " + repository.turnValues
                + " " + repository.toolValues + " " + repository.usageValues;
        assertThat(persisted)
                .doesNotContain("123.45", "OPEN", "order_metrics", "event_time")
                .doesNotContain("Authorized scalar statistics",
                        "Authorized grouped statistics", "Authorized trend statistics");
    }

    @Test
    void recordsRuntimeStatisticsOwnerDenialWithoutCallingSummaryProvider() {
        var repository = repository();
        var provider = new ScriptedProvider(List.of(completion("""
                {"operation":"RUNTIME_STATISTICS_QUERY",
                 "moduleCode":"orders","dataSourceCode":"hidden_metrics",
                 "aggregation":"COUNT","measureFieldCode":null,
                 "grouping":null,"trend":null}
                """)));
        var denied = new BusinessException(
                "DATA_SOURCE_NOT_FOUND", "source is hidden", HttpStatus.NOT_FOUND);
        AiRuntimeStatisticsReadFacade statisticsOwner = request -> {
            throw denied;
        };
        var facade = new AiAgentFacade(
                repository, provider, new AiRecordQueryPlanParser(),
                new AiContextReadPlanParser(), request -> {
                    throw new AssertionError("ordinary query must not execute");
                }, statisticsOwner, new SequenceIdService(1450),
                Clock.fixed(NOW, ZoneOffset.UTC),
                new ObjectMapper());
        var session = facade.createSession(
                actor(), new AiAgentFacade.CreateSession("Hidden statistics"));

        var result = facade.submit(actor(), session.id(),
                new AiAgentFacade.SubmitMessage("Show hidden statistics"));

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorCode()).isEqualTo("DATA_SOURCE_NOT_FOUND");
        assertThat(result.contextResult()).isNull();
        assertThat(provider.requests).hasSize(1);
        assertThat(repository.toolValues).singleElement().satisfies(tool -> {
            assertThat(tool.toolName()).isEqualTo("RUNTIME_STATISTICS_QUERY");
            assertThat(tool.status()).isEqualTo(AiConversation.TurnStatus.FAILED);
            assertThat(tool.requestHash()).hasSize(64);
            assertThat(tool.responseHash()).isNull();
            assertThat(tool.resultCount()).isZero();
            assertThat(tool.resultCode()).isEqualTo("DATA_SOURCE_NOT_FOUND");
        });
    }

    @Test
    void routesRuntimeReportPagesWithOrderedDisplayOnlySafeResults() {
        var repository = repository();
        var provider = new ScriptedProvider(List.of(
                completion("""
                        {"operation":"RUNTIME_REPORT_QUERY",
                         "moduleCode":"orders","reportCode":"open_orders",
                         "page":1,"size":2}
                        """),
                completion("Authorized first runtime report page."),
                completion("""
                        {"operation":"RUNTIME_REPORT_QUERY",
                         "moduleCode":"orders","reportCode":"open_orders",
                         "page":2,"size":2}
                        """),
                completion("Authorized second runtime report page.")));
        var ownerRequests = new ArrayList<AiRuntimeReportReadFacade.Request>();
        AiRuntimeReportReadFacade owner = request -> {
            ownerRequests.add(request);
            var fields = List.of(
                    new AiRuntimeReportReadFacade.Field(
                            "amount", "Secret amount", "NUMBER"),
                    new AiRuntimeReportReadFacade.Field(
                            "status", "Secret status", "STATUS"));
            var rows = request.page() == 1 ? List.of(
                    new AiRuntimeReportReadFacade.Row(List.of(
                            new AiRuntimeReportReadFacade.Value(
                                    "amount", "12345678901234567890.123456789"),
                            new AiRuntimeReportReadFacade.Value(
                                    "status", "MASKED-VALUE"))),
                    new AiRuntimeReportReadFacade.Row(List.of(
                            new AiRuntimeReportReadFacade.Value("amount", null),
                            new AiRuntimeReportReadFacade.Value(
                                    "status", "OPEN"))))
                    : List.of(new AiRuntimeReportReadFacade.Row(List.of(
                    new AiRuntimeReportReadFacade.Value("amount", "0"),
                    new AiRuntimeReportReadFacade.Value("status", "CLOSED"))));
            return new AiRuntimeReportReadFacade.Result(
                    "open_orders", "Secret runtime report", 2,
                    "order_source", 4, "orders", request.page(),
                    request.size(), 3, rows.size(), request.page() == 1,
                    "/systems/1/reports/open_orders", fields, rows);
        };
        var facade = new AiAgentFacade(
                repository, provider, new AiRecordQueryPlanParser(),
                new AiContextReadPlanParser(), request -> {
                    throw new AssertionError("ordinary query must not execute");
                }, owner, new SequenceIdService(1470),
                Clock.fixed(NOW, ZoneOffset.UTC), new ObjectMapper());
        var actor = actor();
        var session = facade.createSession(
                actor, new AiAgentFacade.CreateSession("Runtime reports"));

        var first = facade.submit(actor, session.id(),
                new AiAgentFacade.SubmitMessage("Show first report page"));
        var second = facade.submit(actor, session.id(),
                new AiAgentFacade.SubmitMessage("Show second report page"));

        assertThat(first.contextResult().runtimeReport()).satisfies(value -> {
            assertThat(value.reportCode()).isEqualTo("open_orders");
            assertThat(value.reportName()).isEqualTo("Secret runtime report");
            assertThat(value.reportVersionNumber()).isEqualTo(2);
            assertThat(value.dataSourceCode()).isEqualTo("order_source");
            assertThat(value.dataSourceVersionNumber()).isEqualTo(4);
            assertThat(value.moduleCode()).isEqualTo("orders");
            assertThat(value.page()).isOne();
            assertThat(value.size()).isEqualTo(2);
            assertThat(value.total()).isEqualTo(3);
            assertThat(value.returnedRows()).isEqualTo(2);
            assertThat(value.hasMore()).isTrue();
            assertThat(value.route()).isEqualTo(
                    "/systems/1/reports/open_orders");
            assertThat(value.fields())
                    .extracting(AiAgentFacade.ContextRuntimeReportField::fieldCode)
                    .containsExactly("amount", "status");
            assertThat(value.rows().getFirst().values())
                    .extracting(AiAgentFacade.ContextRuntimeReportValue::displayValue)
                    .containsExactly(
                            "12345678901234567890.123456789", "MASKED-VALUE");
            assertThat(value.rows().get(1).values().getFirst().displayValue())
                    .isNull();
        });
        assertThat(first.contextResult().runtimeStatistics()).isNull();
        assertThat(first.contextResult().record()).isNull();
        assertThat(first.contextResult().tasks()).isEmpty();
        assertThat(second.contextResult().runtimeReport()).satisfies(value -> {
            assertThat(value.page()).isEqualTo(2);
            assertThat(value.returnedRows()).isOne();
            assertThat(value.hasMore()).isFalse();
            assertThat(value.rows().getFirst().values())
                    .extracting(AiAgentFacade.ContextRuntimeReportValue::displayValue)
                    .containsExactly("0", "CLOSED");
        });
        assertThat(ownerRequests).hasSize(2).allSatisfy(request -> {
            assertThat(request.systemId()).isEqualTo(actor.systemId());
            assertThat(request.tenantId()).isEqualTo(actor.tenantId());
            assertThat(request.memberId()).isEqualTo(actor.memberId());
            assertThat(request.effectivePermissions())
                    .isEqualTo(actor.effectivePermissions());
            assertThat(request.allowedModuleCodes()).containsExactly("orders");
            assertThat(request.outboundFields().get("orders"))
                    .containsExactlyInAnyOrder("status", "amount", "event_time");
            assertThat(request.maxRows()).isEqualTo(10);
            assertThat(request.moduleCode()).isEqualTo("orders");
            assertThat(request.reportCode()).isEqualTo("open_orders");
        });
        assertThat(ownerRequests).extracting(AiRuntimeReportReadFacade.Request::page)
                .containsExactly(1, 2);
        assertThat(repository.toolValues)
                .extracting(AiConversation.ToolCall::resultCount)
                .containsExactly(2, 1);
        assertThat(repository.toolValues).allSatisfy(tool -> {
            assertThat(tool.toolName()).isEqualTo("RUNTIME_REPORT_QUERY");
            assertThat(tool.status()).isEqualTo(AiConversation.TurnStatus.SUCCEEDED);
            assertThat(tool.requestHash()).hasSize(64);
            assertThat(tool.responseHash()).hasSize(64);
            assertThat(tool.resultCode()).isEqualTo("OK");
        });
        assertThat(provider.requests.stream()
                .filter(request -> request.phase() == AiProviderClient.Phase.SUMMARY)
                .map(AiProviderClient.Request::userContent))
                .anySatisfy(payload -> assertThat(payload)
                        .contains("\"displayValue\":\"12345678901234567890.123456789\"",
                                "\"displayValue\":\"MASKED-VALUE\"",
                                "\"displayValue\":null")
                        .doesNotContain("queryHash", "recordId", "recordNo",
                                "title", "rawValue", "schemaVersionId"));
        assertThat(provider.requests.stream()
                .filter(request -> request.phase() == AiProviderClient.Phase.PLAN)
                .map(AiProviderClient.Request::systemPrompt))
                .allSatisfy(prompt -> assertThat(prompt)
                        .contains("For RUNTIME_REPORT_QUERY use exactly operation,moduleCode,"
                                + "reportCode,page,size",
                                "page is an integer from 1 to 10000"));
        var persisted = repository.messageValues + " " + repository.turnValues
                + " " + repository.toolValues + " " + repository.usageValues;
        assertThat(persisted)
                .doesNotContain("Secret runtime report", "Secret amount",
                        "12345678901234567890.123456789", "MASKED-VALUE",
                        "order_source", "open_orders")
                .doesNotContain("Authorized first runtime report page",
                        "Authorized second runtime report page");
    }

    @Test
    void recordsRuntimeReportOwnerDenialWithoutCallingSummaryProvider() {
        var repository = repository();
        var provider = new ScriptedProvider(List.of(completion("""
                {"operation":"RUNTIME_REPORT_QUERY","moduleCode":"orders",
                 "reportCode":"hidden_report","page":1,"size":3}
                """)));
        AiRuntimeReportReadFacade owner = request -> {
            throw new BusinessException(
                    "REPORT_NOT_FOUND", "report is hidden", HttpStatus.NOT_FOUND);
        };
        var facade = new AiAgentFacade(
                repository, provider, new AiRecordQueryPlanParser(),
                new AiContextReadPlanParser(), request -> {
                    throw new AssertionError("ordinary query must not execute");
                }, owner, new SequenceIdService(1490),
                Clock.fixed(NOW, ZoneOffset.UTC), new ObjectMapper());
        var session = facade.createSession(
                actor(), new AiAgentFacade.CreateSession("Hidden report"));

        var result = facade.submit(actor(), session.id(),
                new AiAgentFacade.SubmitMessage("Show hidden report"));

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorCode()).isEqualTo("REPORT_NOT_FOUND");
        assertThat(result.contextResult()).isNull();
        assertThat(provider.requests).hasSize(1);
        assertThat(repository.toolValues).singleElement().satisfies(tool -> {
            assertThat(tool.toolName()).isEqualTo("RUNTIME_REPORT_QUERY");
            assertThat(tool.status()).isEqualTo(AiConversation.TurnStatus.FAILED);
            assertThat(tool.requestHash()).hasSize(64);
            assertThat(tool.responseHash()).isNull();
            assertThat(tool.resultCount()).isZero();
            assertThat(tool.resultCode()).isEqualTo("REPORT_NOT_FOUND");
        });
    }

    @Test
    void recordsProjectMetricsOwnerDenialWithoutCallingSummaryProvider() {
        var repository = repository();
        var provider = new ScriptedProvider(List.of(completion("""
                {"operation":"WORK_PROJECT_METRICS_QUERY","projectId":"701",
                 "fromInclusive":"2026-08-01","toExclusive":"2026-08-05"}
                """)));
        var denied = new BusinessException(
                "WORK_PROJECT_NOT_FOUND", "project is hidden", HttpStatus.NOT_FOUND);
        var facade = new AiAgentFacade(
                repository, provider, new AiRecordQueryPlanParser(),
                new AiContextReadPlanParser(), request -> {
                    throw new AssertionError("ordinary query must not execute");
                }, request -> {
                    throw new AssertionError("record context must not execute");
                }, new WorkOwner(denied), request -> {
                    throw new AssertionError("Todo query must not execute");
                }, request -> {
                    throw new AssertionError("message query must not execute");
                }, new SequenceIdService(1500), Clock.fixed(NOW, ZoneOffset.UTC),
                new ObjectMapper());
        var session = facade.createSession(
                actor(), new AiAgentFacade.CreateSession("Hidden project"));

        var result = facade.submit(actor(), session.id(),
                new AiAgentFacade.SubmitMessage("Show hidden project metrics"));

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorCode()).isEqualTo("WORK_PROJECT_NOT_FOUND");
        assertThat(result.contextResult()).isNull();
        assertThat(provider.requests).hasSize(1);
        assertThat(repository.toolValues).singleElement().satisfies(tool -> {
            assertThat(tool.toolName()).isEqualTo("WORK_PROJECT_METRICS_QUERY");
            assertThat(tool.status()).isEqualTo(AiConversation.TurnStatus.FAILED);
            assertThat(tool.responseHash()).isNull();
            assertThat(tool.resultCount()).isZero();
        });
    }

    @Test
    void recordsOwnerDenialAsFailedToolWithoutCallingSummaryProvider() {
        var repository = repository();
        var provider = new ScriptedProvider(List.of(completion("""
                {"operation":"MESSAGE_QUERY","status":"UNREAD","limit":5}
                """)));
        AiRecordQueryFacade ordinaryQuery = request -> {
            throw new AssertionError("ordinary query must not execute");
        };
        AiRecordContextFacade records = request -> {
            throw new AssertionError("record context must not execute");
        };
        AiTodoReadFacade todos = request -> {
            throw new AssertionError("Todo query must not execute");
        };
        AiMessageReadFacade messages = request -> {
            throw new BusinessException(
                    "AI_MESSAGE_PERMISSION_DENIED",
                    "message access was revoked", HttpStatus.FORBIDDEN);
        };
        var facade = new AiAgentFacade(
                repository, provider, new AiRecordQueryPlanParser(),
                new AiContextReadPlanParser(), ordinaryQuery, records,
                new WorkOwner(), todos, messages, new SequenceIdService(2000),
                Clock.fixed(NOW, ZoneOffset.UTC), new ObjectMapper());
        var session = facade.createSession(
                actor(), new AiAgentFacade.CreateSession("Denied messages"));

        var result = facade.submit(actor(), session.id(),
                new AiAgentFacade.SubmitMessage("Show unread messages"));

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorCode()).isEqualTo("AI_MESSAGE_PERMISSION_DENIED");
        assertThat(result.contextResult()).isNull();
        assertThat(provider.requests).hasSize(1);
        assertThat(repository.toolValues).singleElement().satisfies(tool -> {
            assertThat(tool.toolName()).isEqualTo("MESSAGE_QUERY");
            assertThat(tool.status()).isEqualTo(AiConversation.TurnStatus.FAILED);
            assertThat(tool.responseHash()).isNull();
            assertThat(tool.resultCount()).isZero();
            assertThat(tool.resultCode()).isEqualTo(
                    "AI_MESSAGE_PERMISSION_DENIED");
        });
    }

    private static MemoryAiRepository repository() {
        var repository = new MemoryAiRepository();
        repository.insertProvider(new AiProvider(
                80, 1, 2, "openai", "OpenAI", "https://api.example.test/v1",
                "gpt-read", "vault://tenant/openai", 10, true, 3,
                NOW, 7, NOW, 7));
        repository.versions.put(91L, new AiPolicy.Version(
                91, 1, 2, 90, 1, 80, 3, "gpt-read", Set.of("orders"),
                Map.of("orders", Set.of("status", "amount", "event_time")),
                Set.of("RECORD_QUERY", "RECORD_CONTEXT_SUMMARY",
                        "WORK_TASK_QUERY", "WORK_DAILY_REPORT_QUERY",
                        "WORK_PROJECT_METRICS_QUERY",
                        "TODO_QUERY", "MESSAGE_QUERY",
                        "RUNTIME_STATISTICS_QUERY", "RUNTIME_REPORT_QUERY"),
                10, true, AiPolicy.RedactionMode.STRICT, "v83",
                "b".repeat(64), NOW, 7));
        repository.insertPolicyDraft(new AiPolicy.Draft(
                90, 1, 2, 1, AiPolicy.DraftStatus.PUBLISHED, 80, 3,
                Set.of("orders"), Map.of("orders", Set.of(
                        "status", "amount", "event_time")),
                Set.of("RECORD_QUERY", "RECORD_CONTEXT_SUMMARY",
                        "WORK_TASK_QUERY", "WORK_DAILY_REPORT_QUERY",
                        "WORK_PROJECT_METRICS_QUERY",
                        "TODO_QUERY", "MESSAGE_QUERY",
                        "RUNTIME_STATISTICS_QUERY", "RUNTIME_REPORT_QUERY"),
                Map.of(), 10, AiPolicy.ConfirmationMode.REQUIRED, 600, true,
                AiPolicy.RedactionMode.STRICT, "v83", "a".repeat(64),
                91L, NOW, 7));
        return repository;
    }

    private static AiActor actor() {
        return new AiActor(
                5, 1, 2, 7,
                Set.of("system.runtime.access", "ai.agent.use",
                        "module.orders.view", "work.task.access",
                        "work.report.access", "event.message.access"),
                41, "request-83", "trace-83");
    }

    private static AiProviderClient.Completion completion(String content) {
        return new AiProviderClient.Completion(
                content, 3, 2, 9, AiSupport.sha256(content));
    }

    private static final class ScriptedProvider implements AiProviderClient {
        private final List<Completion> values;
        private final List<Request> requests = new ArrayList<>();
        private int index;

        private ScriptedProvider(List<Completion> values) {
            this.values = List.copyOf(values);
        }

        @Override
        public Completion complete(AiProvider provider, Request request) {
            requests.add(request);
            return values.get(index++);
        }
    }

    private static final class WorkOwner implements AiWorkQueryFacade {
        private final List<TaskRequest> taskRequests = new ArrayList<>();
        private final List<DailyReportRequest> reportRequests = new ArrayList<>();
        private final List<ProjectMetricsRequest> metricsRequests = new ArrayList<>();
        private final RuntimeException metricsFailure;

        private WorkOwner() {
            this(null);
        }

        private WorkOwner(RuntimeException metricsFailure) {
            this.metricsFailure = metricsFailure;
        }

        @Override
        public TaskResult taskQuery(TaskRequest request) {
            taskRequests.add(request);
            return new TaskResult(1, List.of(new Task(
                    "601", 3, "Close quarter", "Secret task description",
                    "OPEN", "701", "7", "7",
                    NOW.plusSeconds(86_400), NOW.plusSeconds(3_600),
                    NOW.minusSeconds(3_600), NOW)));
        }

        @Override
        public DailyReportResult dailyReportQuery(DailyReportRequest request) {
            reportRequests.add(request);
            return new DailyReportResult(1, List.of(new DailyReport(
                    "801", 2, "7", LocalDate.parse("2026-08-03"),
                    "Secret completed work", "Secret planned work",
                    "Secret blocker", "SUBMITTED", NOW.minusSeconds(3_600),
                    NOW, NOW)));
        }

        @Override
        public ProjectMetricsResult projectMetrics(ProjectMetricsRequest request) {
            metricsRequests.add(request);
            if (metricsFailure != null) throw metricsFailure;
            return new ProjectMetricsResult(
                    "701", "Secret project metrics title", "ACTIVE", NOW,
                    LocalDate.parse("2026-07-29"),
                    LocalDate.parse("2026-08-05"),
                    ProjectVisibility.PARTICIPATING, 2, 1, 1,
                    new Metric(1,
                            "/systems/1/tasks?projectId=701&status=OPEN&due=OVERDUE"),
                    new Metric(1,
                            "/systems/1/tasks?projectId=701&status=OPEN&dueFrom=2026-07-29"),
                    new Metric(1,
                            "/systems/1/tasks?projectId=701&status=COMPLETED&updatedFrom=2026-07-29"),
                    List.of(
                            new DailyMetric(
                                    LocalDate.parse("2026-07-29"), 1, 0,
                                    "/systems/1/tasks?projectId=701&createdFrom=2026-07-29",
                                    "/systems/1/tasks?projectId=701&status=COMPLETED&updatedFrom=2026-07-29"),
                            new DailyMetric(
                                    LocalDate.parse("2026-07-30"), 0, 1,
                                    "/systems/1/tasks?projectId=701&createdFrom=2026-07-30",
                                    "/systems/1/tasks?projectId=701&status=COMPLETED&updatedFrom=2026-07-30")),
                    List.of(new AssigneeOpen(
                            "7", 1,
                            "/systems/1/tasks?projectId=701&status=OPEN&assigneeMemberId=7")));
        }
    }
}
