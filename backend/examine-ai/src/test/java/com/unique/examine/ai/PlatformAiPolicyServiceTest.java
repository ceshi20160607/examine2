package com.unique.examine.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.ai.domain.PlatformAiPolicy;
import com.unique.examine.ai.service.PlatformAiPolicyService;
import com.unique.examine.ai.service.PlatformAiProviderService;
import com.unique.examine.core.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlatformAiPolicyServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void providerAndPolicySaveCheckPublishAndExactReplayStayPlatformScoped() {
        var repository = new MemoryPlatformAiRepository();
        var ids = new SequenceIdService(1_000);
        var providers = new PlatformAiProviderService(repository, ids, CLOCK);
        var policies = new PlatformAiPolicyService(
                repository, ids, CLOCK, new ObjectMapper());
        var provider = providers.create(actor(), providerCommand(true));

        var draft = policies.save(actor(), 0, policyCommand(provider.id()));
        var check = policies.check(actor());
        var version = policies.publish(
                actor(), draft.revision(), "publish-platform-1");
        var replay = policies.publish(
                actor(), draft.revision(), "publish-platform-1");

        assertThat(check.valid()).isTrue();
        assertThat(repository.draftValue.status())
                .isEqualTo(PlatformAiPolicy.DraftStatus.PUBLISHED);
        assertThat(repository.draftValue.revision()).isEqualTo(draft.revision());
        assertThat(version).isEqualTo(replay);
        assertThat(version.settings().allowedOperations())
                .containsExactlyInAnyOrder(
                        "AUTHORIZED_SYSTEMS_QUERY", "SYSTEM_SWITCH_GUIDANCE",
                        "PLATFORM_TASK_DRAFT", "PLATFORM_OPERATIONS_QUERY");
        assertThat(repository.activePolicy()).contains(version);
        assertThat(repository.replayValues).hasSize(1);
        assertThat(repository.auditValues)
                .extracting(value -> value.eventType())
                .contains("PROVIDER_CREATED", "POLICY_SAVED",
                        "POLICY_CHECKED", "POLICY_PUBLISHED");

        var revised = policies.save(
                actor(), draft.revision(), policyCommand(provider.id()));
        assertThatThrownBy(() -> policies.publish(
                actor(), revised.revision(), "publish-platform-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(failure -> ((BusinessException) failure).code())
                .isEqualTo("PLATFORM_AI_POLICY_REPLAY_CONFLICT");
    }

    @Test
    void checkBlocksDisabledOrRevisedProviderAndSecretMustRemainAReference() {
        var repository = new MemoryPlatformAiRepository();
        var ids = new SequenceIdService(2_000);
        var providers = new PlatformAiProviderService(repository, ids, CLOCK);
        var policies = new PlatformAiPolicyService(
                repository, ids, CLOCK, new ObjectMapper());
        var provider = providers.create(actor(), providerCommand(true));
        var draft = policies.save(actor(), 0, policyCommand(provider.id()));
        providers.update(actor(), provider.id(), provider.version(),
                providerCommand(false));

        var check = policies.check(actor());

        assertThat(check.valid()).isFalse();
        assertThat(check.issues()).extracting(PlatformAiPolicy.Issue::code)
                .containsExactly("PLATFORM_AI_PROVIDER_UNAVAILABLE");
        assertThatThrownBy(() -> policies.publish(
                actor(), draft.revision(), "disabled"))
                .isInstanceOf(BusinessException.class)
                .extracting(failure -> ((BusinessException) failure).code())
                .isEqualTo("PLATFORM_AI_POLICY_CHECK_REQUIRED");
        assertThatThrownBy(() -> providers.create(actor(),
                new PlatformAiProviderService.Command(
                        "plain", "Plain", "https://api.example.test/v1",
                        "gpt-platform", "plaintext-key", 10, true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SecretRef");
    }

    @Test
    void concurrentFirstDraftCreationReturnsStableVersionConflict() {
        var repository = new MemoryPlatformAiRepository();
        var ids = new SequenceIdService(3_000);
        var provider = new PlatformAiProviderService(repository, ids, CLOCK)
                .create(actor(), providerCommand(true));
        repository.failPolicyDraftInsertAsDuplicate = true;
        var policies = new PlatformAiPolicyService(
                repository, ids, CLOCK, new ObjectMapper());

        assertThatThrownBy(() -> policies.save(
                actor(), 0, policyCommand(provider.id())))
                .isInstanceOf(BusinessException.class)
                .extracting(failure -> ((BusinessException) failure).code())
                .isEqualTo("PLATFORM_AI_POLICY_VERSION_CONFLICT");
    }

    private static PlatformAiProviderService.Command providerCommand(
            boolean enabled) {
        return new PlatformAiProviderService.Command(
                "openai", "OpenAI", "https://api.example.test/v1",
                "gpt-platform", "env://PLATFORM_AI_KEY", 10, enabled);
    }

    private static PlatformAiPolicyService.DraftCommand policyCommand(
            long providerId) {
        return new PlatformAiPolicyService.DraftCommand(
                providerId, PlatformAiPolicy.SUPPORTED_OPERATIONS,
                50, 100, 100_000, 2, true,
                PlatformAiPolicy.DataResidency.PLATFORM_METADATA_ONLY,
                "v1", true);
    }

    private static PlatformAiActor actor() {
        return new PlatformAiActor(
                7, Set.of("platform.ai.policy.manage"), 41,
                "request-1", "trace-1");
    }
}
