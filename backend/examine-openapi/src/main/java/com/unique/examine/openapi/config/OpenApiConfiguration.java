package com.unique.examine.openapi.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.IdempotencyFacade;
import com.unique.examine.core.api.OpenApiPrincipalFacade;
import com.unique.examine.core.api.PlatformOpenApiPrincipalFacade;
import com.unique.examine.core.api.OperationAuditFacade;
import com.unique.examine.core.api.OutboundHttpTransport;
import com.unique.examine.core.id.IdService;
import com.unique.examine.core.job.DurableJobFacade;
import com.unique.examine.openapi.repository.OpenApiCallbackRepository;
import com.unique.examine.openapi.repository.OpenApiRepository;
import com.unique.examine.openapi.repository.jdbc.JdbcOpenApiCallbackRepository;
import com.unique.examine.openapi.repository.jdbc.JdbcOpenApiRepository;
import com.unique.examine.openapi.repository.PlatformOpenApiRepository;
import com.unique.examine.openapi.repository.jdbc.JdbcPlatformOpenApiRepository;
import com.unique.examine.openapi.secret.DefaultSecretRefResolver;
import com.unique.examine.openapi.secret.SecretRefResolver;
import com.unique.examine.openapi.security.OpenApiAuthenticator;
import com.unique.examine.openapi.security.PlatformOpenApiAuthenticator;
import com.unique.examine.openapi.security.OpenApiCallbackTargetPolicy;
import com.unique.examine.openapi.service.OpenApiCallbackDeliveryService;
import com.unique.examine.openapi.service.OpenApiCallbackRetryWorker;
import com.unique.examine.openapi.service.OpenApiCallbackService;
import com.unique.examine.openapi.service.OpenApiCallbackUseCase;
import com.unique.examine.openapi.service.OpenApiApplicationService;
import com.unique.examine.openapi.service.OpenApiApplicationUseCase;
import com.unique.examine.openapi.service.OpenApiCallLogService;
import com.unique.examine.openapi.service.PlatformOpenApiCallLogService;
import com.unique.examine.openapi.service.PlatformOpenApiApplicationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;

@Configuration
public class OpenApiConfiguration {
    @Bean
    OpenApiRepository openApiRepository(JdbcTemplate jdbc, ObjectMapper json) {
        return new JdbcOpenApiRepository(jdbc, json);
    }

    @Bean
    PlatformOpenApiRepository platformOpenApiRepository(JdbcTemplate jdbc, ObjectMapper json) {
        return new JdbcPlatformOpenApiRepository(jdbc, json);
    }

    @Bean
    PlatformOpenApiAuthenticator platformOpenApiAuthenticator(
            PlatformOpenApiRepository repository, SecretRefResolver secrets,
            PlatformOpenApiPrincipalFacade principals) {
        return new PlatformOpenApiAuthenticator(repository, secrets, principals, Clock.systemUTC());
    }

    @Bean
    PlatformOpenApiCallLogService platformOpenApiCallLogService(
            PlatformOpenApiRepository repository, IdService ids) {
        return new PlatformOpenApiCallLogService(repository, ids, Clock.systemUTC());
    }

    @Bean
    PlatformOpenApiApplicationService platformOpenApiApplicationService(
            PlatformOpenApiRepository repository, PlatformOpenApiPrincipalFacade principals,
            SecretRefResolver secrets, IdService ids) {
        return new PlatformOpenApiApplicationService(repository, principals, secrets, ids,
                Clock.systemUTC());
    }

    @Bean
    OpenApiCallbackRepository openApiCallbackRepository(JdbcTemplate jdbc, ObjectMapper json) {
        return new JdbcOpenApiCallbackRepository(jdbc, json);
    }

    @Bean
    OpenApiCallbackTargetPolicy openApiCallbackTargetPolicy() {
        return new OpenApiCallbackTargetPolicy();
    }

    @Bean
    SecretRefResolver openApiSecretRefResolver(
            @Value("${examine.openapi.secret-file-roots:}") String configuredRoots
    ) {
        List<Path> roots = Arrays.stream(configuredRoots.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(Path::of)
                .map(Path::toAbsolutePath)
                .map(Path::normalize)
                .toList();
        return new DefaultSecretRefResolver(roots);
    }

    @Bean
    OpenApiAuthenticator openApiAuthenticator(
            OpenApiRepository repository,
            SecretRefResolver secrets,
            OpenApiPrincipalFacade principals
    ) {
        return new OpenApiAuthenticator(repository, secrets, principals, Clock.systemUTC());
    }

    @Bean
    OpenApiCallLogService openApiCallLogService(
            OpenApiRepository repository,
            IdService ids
    ) {
        return new OpenApiCallLogService(repository, ids, Clock.systemUTC());
    }

    @Bean
    OpenApiApplicationUseCase openApiApplicationUseCase(
            OpenApiRepository repository,
            OpenApiPrincipalFacade principals,
            SecretRefResolver secrets,
            IdempotencyFacade idempotency,
            OperationAuditFacade audits,
            IdService ids,
            ObjectMapper json
    ) {
        return new OpenApiApplicationService(
                repository,
                principals,
                secrets,
                idempotency,
                audits,
                ids,
                json,
                Clock.systemUTC()
        );
    }

    @Bean
    OpenApiCallbackUseCase openApiCallbackUseCase(
            OpenApiCallbackRepository callbacks,
            OpenApiRepository applications,
            SecretRefResolver secrets,
            OpenApiCallbackTargetPolicy targets,
            OperationAuditFacade audits,
            IdService ids
    ) {
        return new OpenApiCallbackService(callbacks, applications, secrets, targets,
                audits, ids, Clock.systemUTC());
    }

    @Bean
    OpenApiCallbackDeliveryService openApiCallbackDeliveryService(
            OpenApiCallbackRepository callbacks,
            OpenApiRepository applications,
            DurableJobFacade jobs,
            SecretRefResolver secrets,
            OutboundHttpTransport transport,
            OpenApiCallbackTargetPolicy targets,
            IdService ids,
            ObjectMapper json
    ) {
        return new OpenApiCallbackDeliveryService(callbacks, applications, jobs, secrets,
                transport, targets, ids, json, Clock.systemUTC());
    }

    @Bean
    OpenApiCallbackRetryWorker openApiCallbackRetryWorker(
            DurableJobFacade jobs,
            OpenApiCallbackDeliveryService deliveries
    ) {
        return new OpenApiCallbackRetryWorker(jobs, deliveries);
    }
}
