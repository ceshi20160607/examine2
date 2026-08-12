package com.unique.examine.flow.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.OutboundHttpTransport;
import com.unique.examine.core.api.SecretResolverFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.flow.api.FlowRequests;
import com.unique.examine.flow.domain.ApprovalCompletionStep;
import com.unique.examine.flow.service.FlowCompletionDefinitionMapper;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlowCompletionDefinitionMapperTest {
    @Test
    void preservesMaskedSecretOnReviseAndEmptyStringClearsIt()
            throws Exception {
        var mapper = mapper();
        var created = mapper.create(1, 2, List.of(webhook("env://HOOK")));

        var preserved = mapper.revise(
                1, 2, List.of(webhook("********")), created);
        var cleared = mapper.revise(
                1, 2, List.of(webhook("")), preserved);

        assertThat(created.getFirst().webhook().secretRef())
                .isEqualTo("env://HOOK");
        assertThat(preserved.getFirst().webhook().secretRef())
                .isEqualTo("env://HOOK");
        assertThat(cleared.getFirst().webhook().secretRef()).isNull();
    }

    @Test
    void rejectsMaskAsANewSecretAndMismatchedTypeConfiguration()
            throws Exception {
        var mapper = mapper();

        assertThatThrownBy(() -> mapper.create(
                1, 2, List.of(webhook("********"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("masked");
        assertThatThrownBy(() -> mapper.create(1, 2, List.of(
                new FlowRequests.CompletionStep(
                        "notify", "Notify", "WEBHOOK",
                        new FlowRequests.ExternalTaskCompletion(
                                "records", 60, 3, 1024),
                        webhook("env://HOOK").webhook()
                )
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("only webhook");
    }

    @Test
    void mapsExternalTaskBoundsIntoImmutableDomainConfiguration()
            throws Exception {
        var mapped = mapper().create(1, 2, List.of(
                new FlowRequests.CompletionStep(
                        "archive", "Archive", "EXTERNAL_TASK",
                        new FlowRequests.ExternalTaskCompletion(
                                "records.archive", 90, 4, 4096),
                        null
                )
        ));

        assertThat(mapped).containsExactly(
                ApprovalCompletionStep.externalTask(
                        "archive",
                        "Archive",
                        new ApprovalCompletionStep.ExternalTask(
                                "records.archive", 90, 4, 4096)
                )
        );
    }

    @Test
    void mapsExactSubflowTargetAndRejectsMixedConfiguration()
            throws Exception {
        var request = new FlowRequests.CompletionStep(
                "child", "Child approval", "SUBFLOW",
                null, null,
                new FlowRequests.SubflowCompletion("901", 4));

        var mapped = mapper().create(1, 2, List.of(request));

        assertThat(mapped).containsExactly(
                ApprovalCompletionStep.subflow(
                        "child", "Child approval",
                        new ApprovalCompletionStep.Subflow(901, 4)));
        assertThatThrownBy(() -> mapper().create(1, 2, List.of(
                new FlowRequests.CompletionStep(
                        "child", "Child approval", "SUBFLOW",
                        new FlowRequests.ExternalTaskCompletion(
                                "records", 60, 3, 1024),
                        null,
                        new FlowRequests.SubflowCompletion("901", 4)))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("only subflow");
    }

    @Test
    void preservesAdjacentParallelGroupAndRejectsSingleMemberGroup()
            throws Exception {
        var first = new FlowRequests.CompletionStep(
                "archive", "Archive", "EXTERNAL_TASK",
                new FlowRequests.ExternalTaskCompletion(
                        "records.archive", 60, 3, 1024),
                null, null, "post_commit");
        var second = new FlowRequests.CompletionStep(
                "index", "Index", "EXTERNAL_TASK",
                new FlowRequests.ExternalTaskCompletion(
                        "records.index", 60, 3, 1024),
                null, null, "post_commit");

        var mapped = mapper().create(1, 2, List.of(first, second));

        assertThat(mapped)
                .extracting(ApprovalCompletionStep::parallelGroup)
                .containsExactly("post_commit", "post_commit");
        assertThatThrownBy(() -> mapper().create(
                1, 2, List.of(first)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("requires 2..8 adjacent steps");
    }

    private static FlowRequests.CompletionStep webhook(String secretRef) {
        return new FlowRequests.CompletionStep(
                "notify",
                "Notify",
                "WEBHOOK",
                null,
                new FlowRequests.WebhookCompletion(
                        "https://hooks.example.test/completed",
                        secretRef,
                        5,
                        3,
                        2
                )
        );
    }

    private static FlowCompletionDefinitionMapper mapper() throws Exception {
        var publicAddress = InetAddress.getByName("93.184.216.34");
        var targets = new WebhookTargetPolicy(
                host -> new InetAddress[]{publicAddress});
        SecretResolverFacade secrets = request ->
                "env://HOOK".equals(request.reference())
                        ? Optional.of(
                                SecretResolverFacade.ResolvedSecret.utf8(
                                        "secret"))
                        : Optional.empty();
        OutboundHttpTransport transport = request ->
                new OutboundHttpTransport.Response(
                        204, new byte[0], Duration.ofMillis(1));
        var client = new WebhookDeliveryClient(
                secrets,
                transport,
                targets,
                new WebhookPayloadEncoder(new ObjectMapper()),
                Clock.systemUTC()
        );
        return new FlowCompletionDefinitionMapper(client);
    }
}
