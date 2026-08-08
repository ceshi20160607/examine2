package com.unique.examine.event.service;

import com.unique.examine.event.domain.DeliveryChannel;
import com.unique.examine.event.domain.DeliveryPreference;
import com.unique.examine.event.domain.EventActor;
import com.unique.examine.event.domain.EventDomainException;
import com.unique.examine.event.port.DeliveryPreferenceRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

@Transactional
public class DeliveryPreferenceService {
    public static final String ACCESS = "event.message.access";
    private static final Pattern TEMPLATE_CODE = Pattern.compile("[A-Z][A-Z0-9_]{1,99}");

    private final DeliveryPreferenceRepository repository;
    private final MessageTemplateService templates;
    private final Clock clock;

    public DeliveryPreferenceService(DeliveryPreferenceRepository repository,
                                     MessageTemplateService templates,
                                     Clock clock) {
        if (repository == null || templates == null || clock == null) {
            throw new IllegalArgumentException("Delivery preference dependencies are required");
        }
        this.repository = repository;
        this.templates = templates;
        this.clock = clock;
    }

    public List<PreferenceView> list(EventActor actor) {
        requireAccess(actor);
        var catalog = templates.preferenceCatalog(actor.systemId(), actor.memberId());
        var overrides = new HashMap<String, DeliveryPreference>();
        for (var channel : DeliveryChannel.STABLE_ORDER) {
            for (var preference : repository.findAll(
                    actor.systemId(), actor.tenantId(), actor.memberId(), channel)) {
                overrides.put(key(preference.templateCode(), channel), preference);
            }
        }
        return catalog.stream()
                .flatMap(template -> template.channels().stream()
                        .map(channel -> view(template, channel,
                                overrides.get(key(template.templateCode(), channel)))))
                .toList();
    }

    public PreferenceView update(EventActor actor, String templateCode,
                                 Boolean enabled, Long expectedVersion) {
        return update(actor, templateCode, DeliveryChannel.INBOX.name(), enabled, expectedVersion);
    }

    public PreferenceView update(EventActor actor, String templateCode, String channelValue,
                                 Boolean enabled, Long expectedVersion) {
        requireAccess(actor);
        validate(templateCode, channelValue, enabled, expectedVersion);
        var channel = DeliveryChannel.parse(channelValue);
        var template = templates.requirePreferenceTemplate(
                actor.systemId(), actor.memberId(), templateCode);
        if (!template.channels().contains(channel)) {
            throw invalid("Template does not declare this delivery channel");
        }
        var current = repository.find(actor.systemId(), actor.tenantId(), actor.memberId(),
                templateCode, channel).orElse(null);
        if (current == null) {
            if (expectedVersion != 0) throw conflict();
            if (enabled) return view(template, channel, null);
            var now = Instant.now(clock);
            var created = new DeliveryPreference(repository.nextId(), actor.systemId(), actor.tenantId(),
                    actor.memberId(), templateCode, channel, false, now, now, 1);
            if (!repository.insert(created)) throw conflict();
            return view(template, channel, created);
        }
        if (current.version() != expectedVersion) throw conflict();
        if (current.enabled() == enabled) return view(template, channel, current);
        var changed = current.change(enabled, Instant.now(clock));
        if (!repository.update(changed, expectedVersion)) throw conflict();
        return view(template, channel, changed);
    }

    private static PreferenceView view(MessageTemplateService.PreferenceTemplate template,
                                       DeliveryChannel channel,
                                       DeliveryPreference preference) {
        return new PreferenceView(template.templateCode(), template.eventType(), template.name(),
                channel.name(), preference == null || preference.enabled(),
                preference == null ? 0 : preference.version(),
                preference == null ? null : preference.updatedAt());
    }

    private static void validate(String templateCode, String channel,
                                 Boolean enabled, Long expectedVersion) {
        if (templateCode == null || !TEMPLATE_CODE.matcher(templateCode).matches()
                || channel == null
                || enabled == null || expectedVersion == null || expectedVersion < 0) {
            throw invalid("Delivery preference request is invalid");
        }
    }

    private static String key(String templateCode, DeliveryChannel channel) {
        return templateCode + ':' + channel.name();
    }

    private static void requireAccess(EventActor actor) {
        if (actor == null || !actor.has(ACCESS)) {
            throw new EventDomainException(
                    "EVENT_DELIVERY_PREFERENCE_FORBIDDEN", "Message preference access is denied");
        }
    }

    private static EventDomainException invalid(String message) {
        return new EventDomainException("EVENT_DELIVERY_PREFERENCE_INVALID", message);
    }

    private static EventDomainException conflict() {
        return new EventDomainException("EVENT_DELIVERY_PREFERENCE_VERSION_CONFLICT",
                "Delivery preference version is stale");
    }

    public record PreferenceView(String templateCode, String eventType, String name, String channel,
                                 boolean enabled, long version, Instant updatedAt) {
    }
}
