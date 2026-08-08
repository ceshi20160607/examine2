package com.unique.examine.openapi.api;

import com.unique.examine.openapi.repository.OpenApiRepository;

import java.util.List;
import java.util.Set;

public final class OpenApiViews {
    private OpenApiViews() {
    }

    public record Application(
            String id,
            String systemId,
            String tenantId,
            String serviceMemberId,
            String appKey,
            String name,
            String status,
            Set<String> scopes,
            List<String> ipAllowlist,
            int rateLimitPerMinute,
            int credentialVersion,
            String secretRef,
            long version,
            String createdAt,
            String updatedAt
    ) {
        public Application {
            scopes = Set.copyOf(scopes);
            ipAllowlist = List.copyOf(ipAllowlist);
        }

        public static Application from(OpenApiRepository.ApplicationBundle value) {
            var application = value.application();
            return new Application(
                    Long.toString(application.id()),
                    Long.toString(application.systemId()),
                    Long.toString(application.tenantId()),
                    Long.toString(application.serviceMemberId()),
                    application.appKey(),
                    application.name(),
                    application.status().name(),
                    application.scopes(),
                    application.ipAllowlist(),
                    application.rateLimitPerMinute(),
                    value.credential().credentialVersion(),
                    value.credential().secretRef(),
                    application.version(),
                    application.createdAt().toString(),
                    application.updatedAt().toString()
            );
        }
    }

    public record ApplicationPage(
            List<Application> items,
            int page,
            int size,
            long total
    ) {
        public ApplicationPage {
            items = List.copyOf(items);
        }
    }

    public record CallLog(
            String id,
            Integer credentialVersion,
            String routeTemplate,
            String requestMethod,
            String resultCategory,
            int httpStatus,
            long latencyMs,
            String requestId,
            String traceId,
            String observedIp,
            String createdAt
    ) {
        public static CallLog from(OpenApiRepository.ApplicationCallLog value) {
            return new CallLog(
                    Long.toString(value.id()),
                    value.credentialVersion(),
                    value.routeTemplate(),
                    value.requestMethod(),
                    value.resultCategory().name(),
                    value.httpStatus(),
                    value.latencyMs(),
                    value.requestId(),
                    value.traceId(),
                    value.observedIp(),
                    value.createdAt().toString());
        }
    }

    public record CallLogPage(
            List<CallLog> items,
            int page,
            int size,
            long total
    ) {
        public CallLogPage {
            items = List.copyOf(items);
            if (page < 1 || page > 10_000 || size < 1 || size > 100
                    || total < 0 || items.size() > size || items.size() > total) {
                throw new IllegalArgumentException("OpenAPI call-log page is invalid");
            }
        }
    }
}
