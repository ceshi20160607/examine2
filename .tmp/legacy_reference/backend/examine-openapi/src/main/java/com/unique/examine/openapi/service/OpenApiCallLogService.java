package com.unique.examine.openapi.service;

import com.unique.examine.core.id.IdService;
import com.unique.examine.openapi.domain.OpenApiCallLog;
import com.unique.examine.openapi.repository.OpenApiRepository;
import com.unique.examine.openapi.security.OpenApiCanonicalRequest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Objects;

public class OpenApiCallLogService {
    private final OpenApiRepository repository;
    private final IdService ids;
    private final Clock clock;

    public OpenApiCallLogService(
            OpenApiRepository repository,
            IdService ids,
            Clock clock
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Attempt value) {
        Objects.requireNonNull(value, "value");
        repository.insertCallLog(new OpenApiCallLog(
                ids.nextId(),
                value.applicationId(),
                OpenApiCanonicalRequest.sha256(
                        Objects.requireNonNullElse(value.appKey(), "")
                                .getBytes(StandardCharsets.UTF_8)),
                value.credentialVersion(),
                value.routeTemplate(),
                value.requestMethod(),
                value.resultCategory(),
                value.httpStatus(),
                value.latencyMs(),
                value.requestId(),
                value.traceId(),
                value.observedIp(),
                clock.instant()
        ));
    }

    public record Attempt(
            Long applicationId,
            String appKey,
            Integer credentialVersion,
            String routeTemplate,
            String requestMethod,
            OpenApiCallLog.ResultCategory resultCategory,
            int httpStatus,
            long latencyMs,
            String requestId,
            String traceId,
            String observedIp
    ) {
    }
}
