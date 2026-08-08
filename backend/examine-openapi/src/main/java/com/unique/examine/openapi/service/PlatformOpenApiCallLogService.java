package com.unique.examine.openapi.service;

import com.unique.examine.core.id.IdService;
import com.unique.examine.openapi.domain.OpenApiCallLog;
import com.unique.examine.openapi.repository.PlatformOpenApiRepository;
import com.unique.examine.openapi.security.OpenApiCanonicalRequest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;import java.time.Clock;

public class PlatformOpenApiCallLogService {
    private final PlatformOpenApiRepository repository;private final IdService ids;private final Clock clock;
    public PlatformOpenApiCallLogService(PlatformOpenApiRepository repository,IdService ids,Clock clock){this.repository=repository;this.ids=ids;this.clock=clock;}
    @Transactional(propagation= Propagation.REQUIRES_NEW) public void record(OpenApiCallLogService.Attempt a){repository.insertCallLog(new OpenApiCallLog(ids.nextId(),a.applicationId(),OpenApiCanonicalRequest.sha256((a.appKey()==null?"":a.appKey()).getBytes(StandardCharsets.UTF_8)),a.credentialVersion(),a.routeTemplate(),a.requestMethod(),a.resultCategory(),a.httpStatus(),a.latencyMs(),a.requestId(),a.traceId(),a.observedIp(),clock.instant()));}
}
