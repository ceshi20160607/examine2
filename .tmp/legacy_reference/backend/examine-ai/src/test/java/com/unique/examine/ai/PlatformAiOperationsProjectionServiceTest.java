package com.unique.examine.ai;

import com.unique.examine.ai.domain.PlatformAiConversation;
import com.unique.examine.ai.domain.PlatformAiPolicy;
import com.unique.examine.ai.service.PlatformAiOperationsProjectionService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformAiOperationsProjectionServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-04T05:00:00Z");

    @Test
    void projectsCurrentAccountQuotaWithSaturatedRemainingCounts() {
        var repository = repository();
        var quota = new MemoryPlatformAiRepository.Quota();
        quota.requests = 7;
        quota.usedTokens = 20_000;
        quota.reservedTokens = 10_000;
        quota.running = 2;
        repository.quotas.put("7:91:2026-08-04T00:00:00Z", quota);
        var service = service(repository);

        var result = service.current(7);

        assertThat(result.requestLimit()).isEqualTo(100);
        assertThat(result.requestCount()).isEqualTo(7);
        assertThat(result.remainingRequests()).isEqualTo(93);
        assertThat(result.tokenLimit()).isEqualTo(100_000);
        assertThat(result.usedTokens()).isEqualTo(20_000);
        assertThat(result.reservedTokens()).isEqualTo(10_000);
        assertThat(result.remainingTokens()).isEqualTo(70_000);
        assertThat(result.runningCount()).isEqualTo(2);
        assertThat(result.remainingConcurrency()).isEqualTo(2);
    }

    @Test
    void activityIsAccountScopedBoundedNewestFirstAndMetadataOnly() {
        var repository = repository();
        repository.turnValues.put(41L, turn(7, 41, "PLATFORM_OPERATIONS_QUERY"));
        repository.auditValues.add(audit(
                51, 7, 41, "TURN_SUCCEEDED", "OK", NOW.plusSeconds(2)));
        repository.auditValues.add(audit(
                52, 8, 42, "TURN_FAILED", "PRIVATE_FAILURE", NOW.plusSeconds(3)));
        repository.auditValues.add(audit(
                53, 7, 31, "SESSION_CREATED", "OK", NOW.plusSeconds(1)));

        var result = service(repository).recent(7, 1);

        assertThat(result.activities()).hasSize(1);
        assertThat(result.activities().getFirst().event())
                .isEqualTo("TURN_SUCCEEDED");
        assertThat(result.activities().getFirst().operation())
                .isEqualTo("PLATFORM_OPERATIONS_QUERY");
        assertThat(result.activities()).extracting(value -> value.resultCode())
                .doesNotContain("PRIVATE_FAILURE");
    }

    private static PlatformAiOperationsProjectionService service(
            MemoryPlatformAiRepository repository) {
        return new PlatformAiOperationsProjectionService(
                repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static MemoryPlatformAiRepository repository() {
        var repository = new MemoryPlatformAiRepository();
        var settings = new PlatformAiPolicy.Settings(
                PlatformAiPolicy.SUPPORTED_OPERATIONS, 50, 100, 100_000, 4,
                true, PlatformAiPolicy.DataResidency.PLATFORM_METADATA_ONLY,
                "v1", true);
        repository.versionValues.put(91L, new PlatformAiPolicy.Version(
                91, 90, 1, 80, 3, "gpt-platform", settings,
                "b".repeat(64), NOW, 7));
        repository.draftValue = new PlatformAiPolicy.Draft(
                90, 1, PlatformAiPolicy.DraftStatus.PUBLISHED, 80, 3,
                settings, "a".repeat(64), 91L, NOW, 7);
        return repository;
    }

    private static PlatformAiConversation.Turn turn(
            long accountId, long id, String operation) {
        return new PlatformAiConversation.Turn(
                id, PlatformAiConversation.Scope.PLATFORM, accountId, 31, 91,
                80, 3, 41, operation,
                PlatformAiConversation.TurnStatus.SUCCEEDED, "request",
                AiSupport.sha256("request"), AiSupport.sha256("plan"),
                "response", AiSupport.sha256("response"), 0, "OK", false,
                10_000, 2, "request-1", "trace-1", NOW, NOW.plusSeconds(1));
    }

    private static PlatformAiConversation.AuditEvent audit(
            long id, long accountId, long aggregateId, String event,
            String resultCode, Instant at) {
        return new PlatformAiConversation.AuditEvent(
                id, PlatformAiConversation.Scope.PLATFORM, accountId,
                "TURN_SUCCEEDED".equals(event) || "TURN_FAILED".equals(event)
                        ? "TURN" : "SESSION",
                aggregateId, event, resultCode, "request-1", "trace-1",
                AiSupport.sha256(id + ":" + event), at);
    }
}
