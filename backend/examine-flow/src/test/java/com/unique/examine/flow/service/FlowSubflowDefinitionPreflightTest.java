package com.unique.examine.flow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.OutboundHttpTransport;
import com.unique.examine.core.api.SecretResolverFacade;
import com.unique.examine.core.id.IdService;
import com.unique.examine.flow.domain.ApprovalCompletionStep;
import com.unique.examine.flow.domain.ApprovalMode;
import com.unique.examine.flow.repository.memory.InMemoryApprovalRepository;
import com.unique.examine.flow.transport.WebhookDeliveryClient;
import com.unique.examine.flow.transport.WebhookPayloadEncoder;
import com.unique.examine.flow.transport.WebhookTargetPolicy;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class FlowSubflowDefinitionPreflightTest {
    private static final Instant NOW =
            Instant.parse("2026-07-31T00:00:00Z");

    @Test
    void reportsUnavailableExactTargetAndBoundedIndirectCycle()
            throws Exception {
        var repository = new InMemoryApprovalRepository();
        var sequence = new AtomicLong(500);
        var ids = new IdService() {
            @Override
            public long nextId() {
                return sequence.incrementAndGet();
            }
        };
        var workflow = new ApprovalWorkflowService(
                repository, ids, Clock.fixed(NOW, ZoneOffset.UTC), 1, 2);
        var rootDraft = workflow.createDraft("Root", 10);
        var rootVersion = workflow.publish(rootDraft.id());
        var childStep = ApprovalCompletionStep.subflow(
                "root", "Root v1",
                new ApprovalCompletionStep.Subflow(
                        rootDraft.id(), rootVersion.version()));
        var childDraft = workflow.createDraft(
                "Child", List.of(20L), null, null, null,
                ApprovalMode.SEQUENTIAL, null, null, null, null, null, null,
                null, null, List.of(childStep));
        var childVersion = workflow.publish(childDraft.id());

        var mapper = mapper();
        mapper.configureServices((systemId, tenantId) -> workflow);
        var cycle = mapper.preflight(
                1, 2, rootDraft.id(), List.of(
                        ApprovalCompletionStep.subflow(
                                "child", "Child v1",
                                new ApprovalCompletionStep.Subflow(
                                        childDraft.id(),
                                        childVersion.version()))));
        var missing = mapper.preflight(
                1, 2, rootDraft.id(), List.of(
                        ApprovalCompletionStep.subflow(
                                "missing", "Missing",
                                new ApprovalCompletionStep.Subflow(9999, 1))));

        assertThat(cycle).singleElement().satisfies(issue -> {
            assertThat(issue.code()).isEqualTo("SUBFLOW_CYCLE");
            assertThat(issue.path())
                    .isEqualTo("/completionSteps/0/subflow");
            assertThat(issue.message()).contains("->");
        });
        assertThat(missing).singleElement().satisfies(issue ->
                assertThat(issue.code())
                        .isEqualTo("SUBFLOW_TARGET_UNAVAILABLE"));
    }

    private static FlowCompletionDefinitionMapper mapper() throws Exception {
        var targets = new WebhookTargetPolicy();
        SecretResolverFacade secrets = request -> Optional.empty();
        OutboundHttpTransport transport = request ->
                new OutboundHttpTransport.Response(
                        204, new byte[0], Duration.ofMillis(1));
        return new FlowCompletionDefinitionMapper(new WebhookDeliveryClient(
                secrets,
                transport,
                targets,
                new WebhookPayloadEncoder(new ObjectMapper()),
                Clock.systemUTC()
        ));
    }
}
