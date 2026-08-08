package com.unique.examine.flow.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.DefaultSecretResolverFacade;
import com.unique.examine.core.api.OutboundHttpTransport;
import com.unique.examine.core.api.SecretResolverFacade;
import com.unique.examine.flow.transport.HardenedOutboundHttpTransport;
import com.unique.examine.flow.transport.WebhookDeliveryClient;
import com.unique.examine.flow.transport.WebhookPayloadEncoder;
import com.unique.examine.flow.transport.WebhookTargetPolicy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.nio.file.Path;
import java.util.Arrays;

@Configuration
public class FlowWebhookConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public SecretResolverFacade flowSecretResolverFacade(
            @Value("${examine.flow.webhook.secret-file-roots:"
                    + "${examine.openapi.secret-file-roots:}}")
            String configuredRoots
    ) {
        var roots = Arrays.stream(configuredRoots.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(Path::of)
                .map(Path::toAbsolutePath)
                .map(Path::normalize)
                .toList();
        return new DefaultSecretResolverFacade(roots);
    }

    @Bean
    public WebhookTargetPolicy webhookTargetPolicy() {
        return new WebhookTargetPolicy();
    }

    @Bean
    @ConditionalOnMissingBean
    public OutboundHttpTransport outboundHttpTransport(
            WebhookTargetPolicy targets
    ) {
        return new HardenedOutboundHttpTransport(targets);
    }

    @Bean
    public WebhookPayloadEncoder webhookPayloadEncoder(ObjectMapper json) {
        return new WebhookPayloadEncoder(json);
    }

    @Bean
    public WebhookDeliveryClient webhookDeliveryClient(
            SecretResolverFacade secrets,
            OutboundHttpTransport transport,
            WebhookTargetPolicy targets,
            WebhookPayloadEncoder payloads
    ) {
        return new WebhookDeliveryClient(
                secrets, transport, targets, payloads, Clock.systemUTC());
    }
}
