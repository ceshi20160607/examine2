package com.unique.examine.event.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.IdempotentMemberMessageFacade;
import com.unique.examine.core.api.MemberMessageFacade;
import com.unique.examine.core.api.ResultNotificationFacade;
import com.unique.examine.core.id.IdService;
import com.unique.examine.core.job.DurableJobFacade;
import com.unique.examine.event.adapter.EventIdempotentMemberMessageAdapter;
import com.unique.examine.event.adapter.EventMemberMessageAdapter;
import com.unique.examine.event.adapter.EventResultNotificationAdapter;
import com.unique.examine.event.adapter.jdbc.JdbcInboxMessageRepository;
import com.unique.examine.event.adapter.jdbc.JdbcDeliveryPreferenceRepository;
import com.unique.examine.event.adapter.jdbc.JdbcEventChannelConfigurationRepository;
import com.unique.examine.event.adapter.jdbc.JdbcEventChannelTargetDirectory;
import com.unique.examine.event.adapter.jdbc.JdbcMessageTemplateRepository;
import com.unique.examine.event.adapter.jdbc.JdbcMessageRecipientDirectory;
import com.unique.examine.event.port.InboxMessageRepository;
import com.unique.examine.event.port.DeliveryPreferenceRepository;
import com.unique.examine.event.port.EventChannelConfigurationRepository;
import com.unique.examine.event.port.EventChannelTargetDirectory;
import com.unique.examine.event.port.EventChannelTransport;
import com.unique.examine.event.port.MessageRecipientDirectory;
import com.unique.examine.event.service.EventChannelConfigurationService;
import com.unique.examine.event.service.MessageInboxService;
import com.unique.examine.event.service.DeliveryPreferenceService;
import com.unique.examine.event.service.MessageDeliveryLogService;
import com.unique.examine.event.service.MessageTemplateService;
import com.unique.examine.event.todo.EventTodoReminderFacade;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.util.List;

@Configuration(proxyBeanMethods = false)
public class EventJdbcConfiguration {
    @Bean
    InboxMessageRepository inboxMessageRepository(JdbcTemplate jdbcTemplate, IdService idService) {
        return new JdbcInboxMessageRepository(jdbcTemplate, idService);
    }

    @Bean
    MessageRecipientDirectory messageRecipientDirectory(JdbcTemplate jdbcTemplate) {
        return new JdbcMessageRecipientDirectory(jdbcTemplate);
    }

    @Bean
    MessageInboxService messageInboxService(
            InboxMessageRepository repository,
            MessageRecipientDirectory recipients
    ) {
        return new MessageInboxService(repository, recipients, Clock.systemUTC());
    }

    @Bean
    MemberMessageFacade memberMessageFacade(MessageInboxService messages) {
        return new EventMemberMessageAdapter(messages);
    }

    @Bean
    IdempotentMemberMessageFacade idempotentMemberMessageFacade(MessageInboxService messages) {
        return new EventIdempotentMemberMessageAdapter(messages);
    }

    @Bean
    JdbcMessageTemplateRepository messageTemplateRepository(JdbcTemplate jdbcTemplate, IdService idService) {
        return new JdbcMessageTemplateRepository(jdbcTemplate, idService);
    }

    @Bean
    DeliveryPreferenceRepository deliveryPreferenceRepository(JdbcTemplate jdbcTemplate, IdService idService) {
        return new JdbcDeliveryPreferenceRepository(jdbcTemplate, idService);
    }

    @Bean
    EventChannelConfigurationRepository eventChannelConfigurationRepository(
            JdbcTemplate jdbcTemplate, IdService idService) {
        return new JdbcEventChannelConfigurationRepository(jdbcTemplate, idService);
    }

    @Bean
    EventChannelTargetDirectory eventChannelTargetDirectory(JdbcTemplate jdbcTemplate) {
        return new JdbcEventChannelTargetDirectory(jdbcTemplate);
    }

    @Bean
    MessageTemplateService messageTemplateService(
            JdbcMessageTemplateRepository repository,
            DeliveryPreferenceRepository preferences,
            MessageInboxService messages,
            ObjectMapper objectMapper,
            DurableJobFacade jobs,
            List<EventChannelTransport> transports
    ) {
        return new MessageTemplateService(repository, preferences, messages, objectMapper, jobs, transports);
    }

    @Bean
    MessageDeliveryLogService messageDeliveryLogService(JdbcMessageTemplateRepository repository) {
        return new MessageDeliveryLogService(repository);
    }

    @Bean
    EventChannelConfigurationService eventChannelConfigurationService(
            EventChannelConfigurationRepository repository,
            List<EventChannelTransport> transports,
            WebhookTransportProperties webhookProperties
    ) {
        return new EventChannelConfigurationService(repository, transports, Clock.systemUTC(),
                webhookProperties.isAllowLoopbackHttpForTesting());
    }

    @Bean
    DeliveryPreferenceService deliveryPreferenceService(
            DeliveryPreferenceRepository repository,
            MessageTemplateService templates
    ) {
        return new DeliveryPreferenceService(repository, templates, Clock.systemUTC());
    }

    @Bean
    ResultNotificationFacade resultNotificationFacade(MessageTemplateService templates) {
        return new EventResultNotificationAdapter(templates);
    }

    @Bean
    EventTodoReminderFacade eventTodoReminderFacade(MessageInboxService messages) {
        return new EventTodoReminderFacade(messages);
    }
}
