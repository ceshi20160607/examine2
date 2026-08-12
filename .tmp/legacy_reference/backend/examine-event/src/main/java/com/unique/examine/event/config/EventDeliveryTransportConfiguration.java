package com.unique.examine.event.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.OutboundHttpTransport;
import com.unique.examine.core.api.SecretResolverFacade;
import com.unique.examine.event.adapter.SignedWebhookTransport;
import com.unique.examine.event.adapter.SmtpBusinessMessageTransport;
import com.unique.examine.event.port.EventChannelTargetDirectory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({BusinessSmtpProperties.class, WebhookTransportProperties.class})
public class EventDeliveryTransportConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "examine.event.delivery.smtp", name = "enabled",
            havingValue = "true")
    SmtpBusinessMessageTransport smtpBusinessMessageTransport(
            BusinessSmtpProperties properties,
            EventChannelTargetDirectory targets,
            SecretResolverFacade secrets
    ) {
        if (!properties.isReady()) {
            throw new IllegalStateException("Business SMTP transport configuration is invalid");
        }
        return new SmtpBusinessMessageTransport(properties, targets, secrets);
    }

    @Bean
    @ConditionalOnProperty(prefix = "examine.event.delivery.webhook", name = "enabled",
            havingValue = "true")
    SignedWebhookTransport signedWebhookTransport(
            WebhookTransportProperties properties,
            EventChannelTargetDirectory targets,
            SecretResolverFacade secrets,
            OutboundHttpTransport outbound,
            ObjectMapper objectMapper
    ) {
        if (!properties.isReady()) {
            throw new IllegalStateException("Webhook transport configuration is invalid");
        }
        return new SignedWebhookTransport(
                properties, targets, secrets, outbound, objectMapper, Clock.systemUTC());
    }
}
