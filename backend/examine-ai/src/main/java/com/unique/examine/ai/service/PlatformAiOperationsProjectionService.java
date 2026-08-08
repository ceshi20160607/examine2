package com.unique.examine.ai.service;

import com.unique.examine.ai.AiSupport;
import com.unique.examine.ai.repository.PlatformAiRepository;
import com.unique.examine.core.ai.PlatformOperationsQueryFacade;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Objects;

/** Account-scoped AI-owned projections supplied to the platform owner facade. */
@Service
public final class PlatformAiOperationsProjectionService implements
        PlatformOperationsQueryFacade.AiQuotaProvider,
        PlatformOperationsQueryFacade.AgentActivityProvider {
    private final PlatformAiRepository repository;
    private final Clock clock;

    public PlatformAiOperationsProjectionService(
            PlatformAiRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public PlatformOperationsQueryFacade.AiQuotaResult current(long accountId) {
        if (accountId <= 0) {
            throw new IllegalArgumentException("accountId must be positive");
        }
        var policy = repository.activePolicy().filter(
                value -> value.settings().enabled()).orElseThrow(() ->
                AiSupport.unavailable(
                        "PLATFORM_AI_CAPABILITY_UNAVAILABLE",
                        "Platform AI policy is not published or enabled"));
        var start = clock.instant().atZone(ZoneOffset.UTC).toLocalDate()
                .atStartOfDay(ZoneOffset.UTC).toInstant();
        var end = start.plus(1, java.time.temporal.ChronoUnit.DAYS);
        var usage = repository.quotaUsage(accountId, policy.id(), start)
                .orElseGet(() -> new PlatformAiRepository.QuotaUsage(0, 0, 0, 0));
        var settings = policy.settings();
        var requests = saturatedInt(usage.requestCount());
        var running = saturatedInt(usage.runningCount());
        return new PlatformOperationsQueryFacade.AiQuotaResult(
                start, end, settings.dailyRequestQuota(), requests,
                remaining(settings.dailyRequestQuota(), requests),
                settings.dailyTokenQuota(), usage.usedTokens(),
                usage.reservedTokens(), remaining(
                settings.dailyTokenQuota(), saturatedAdd(
                        usage.usedTokens(), usage.reservedTokens())),
                settings.maxConcurrency(), running,
                remaining(settings.maxConcurrency(), running));
    }

    @Override
    public PlatformOperationsQueryFacade.AgentActivityResult recent(
            long accountId, int limit) {
        if (accountId <= 0 || limit < 1
                || limit > PlatformOperationsQueryFacade.MAX_LIMIT) {
            throw new IllegalArgumentException(
                    "Platform AI activity scope or limit is invalid");
        }
        return new PlatformOperationsQueryFacade.AgentActivityResult(
                repository.agentActivity(accountId, limit).stream().map(value ->
                        new PlatformOperationsQueryFacade.AgentActivity(
                                value.event(), value.time(),
                                value.operation() == null ? "SESSION" : value.operation(),
                                value.resultCode(), value.requestId(), value.traceId()))
                        .toList());
    }

    private static int saturatedInt(long value) {
        return (int) Math.min(Integer.MAX_VALUE, value);
    }

    private static int remaining(int limit, int used) {
        return Math.max(0, limit - Math.min(limit, used));
    }

    private static long remaining(long limit, long used) {
        return Math.max(0, limit - Math.min(limit, used));
    }

    private static long saturatedAdd(long left, long right) {
        return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
    }
}
