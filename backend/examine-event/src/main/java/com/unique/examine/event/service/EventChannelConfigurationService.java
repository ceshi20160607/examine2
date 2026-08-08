package com.unique.examine.event.service;

import com.unique.examine.event.domain.DeliveryChannel;
import com.unique.examine.event.domain.EventDomainException;
import com.unique.examine.event.port.EventChannelConfigurationRepository;
import com.unique.examine.event.port.EventChannelTransport;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Admin orchestration for redacted channel switches and bounded connectivity checks. */
public final class EventChannelConfigurationService {
    private static final int DEFAULT_TIMEOUT_MS = 5_000;
    private static final int MAX_TIMEOUT_MS = 30_000;
    private static final Pattern SECRET_REF = Pattern.compile("env://[A-Z][A-Z0-9_]{1,126}");

    private final EventChannelConfigurationRepository repository;
    private final Map<DeliveryChannel, EventChannelTransport> transports;
    private final Clock clock;
    private final boolean allowLoopbackHttpForTesting;

    public EventChannelConfigurationService(EventChannelConfigurationRepository repository,
                                            List<EventChannelTransport> transports, Clock clock) {
        this(repository, transports, clock, false);
    }

    public EventChannelConfigurationService(EventChannelConfigurationRepository repository,
                                            List<EventChannelTransport> transports, Clock clock,
                                            boolean allowLoopbackHttpForTesting) {
        if (repository == null || transports == null || clock == null) {
            throw new IllegalArgumentException("Repository, transports and clock are required");
        }
        this.repository = repository;
        this.transports = new EnumMap<>(DeliveryChannel.class);
        for (var transport : transports) {
            if (transport != null && transport.channel() != DeliveryChannel.INBOX) {
                this.transports.putIfAbsent(transport.channel(), transport);
            }
        }
        this.clock = clock;
        this.allowLoopbackHttpForTesting = allowLoopbackHttpForTesting;
    }

    public List<ConfigurationView> list(long systemId) {
        requireId(systemId, "System id");
        var saved = new EnumMap<DeliveryChannel, EventChannelConfigurationRepository.Configuration>(
                DeliveryChannel.class);
        repository.list(systemId).forEach(value -> saved.put(value.channel(), value));
        return DeliveryChannel.STABLE_ORDER.stream()
                .map(channel -> view(channel, saved.get(channel)))
                .toList();
    }

    public ConfigurationView update(long systemId, long actorId, DeliveryChannel channel,
                                    UpdateCommand command) {
        requireId(systemId, "System id");
        requireId(actorId, "Actor id");
        requireExternal(channel);
        if (command == null || command.expectedVersion() < 0) invalid();
        var current = repository.find(systemId, channel).orElse(null);
        String endpoint = null;
        String secretRef = null;
        var timeoutMs = DEFAULT_TIMEOUT_MS;
        if (channel == DeliveryChannel.EMAIL) {
            if (text(command.endpoint()) || text(command.secretRef()) || command.timeoutMs() != null) invalid();
        } else {
            endpoint = text(command.endpoint()) ? endpoint(command.endpoint(), allowLoopbackHttpForTesting)
                    : current == null ? null : current.endpoint();
            secretRef = text(command.secretRef()) ? secretRef(command.secretRef())
                    : current == null ? null : current.secretRef();
            timeoutMs = command.timeoutMs() == null
                    ? current == null ? DEFAULT_TIMEOUT_MS : current.timeoutMs()
                    : timeout(command.timeoutMs());
            if (command.enabled() && (!text(endpoint) || !text(secretRef))) {
                throw new EventDomainException("EVENT_CHANNEL_CONFIGURATION_INVALID",
                        "Webhook endpoint and SecretRef are required before enabling the channel");
            }
        }
        var now = clock.instant();
        EventChannelConfigurationRepository.Configuration updated;
        if (current == null) {
            if (command.expectedVersion() != 0) conflict();
            updated = repository.create(systemId, channel, command.enabled(), endpoint,
                            secretRef, timeoutMs, actorId, now).orElseThrow(EventChannelConfigurationService::conflict);
        } else {
            if (current.version() != command.expectedVersion()) conflict();
            updated = repository.update(systemId, channel, command.enabled(), endpoint,
                            secretRef, timeoutMs, actorId, command.expectedVersion(), now)
                    .orElseThrow(EventChannelConfigurationService::conflict);
        }
        return view(channel, updated);
    }

    public CheckView check(long systemId, long tenantId, long actorId, DeliveryChannel channel) {
        requireId(systemId, "System id");
        requireId(tenantId, "Tenant id");
        requireId(actorId, "Actor id");
        requireExternal(channel);
        var config = repository.find(systemId, channel)
                .orElseThrow(() -> new EventDomainException("EVENT_CHANNEL_CONFIGURATION_INVALID",
                        "The channel must be configured before it can be checked"));
        if (!config.enabled()) {
            throw new EventDomainException("EVENT_CHANNEL_CONFIGURATION_INVALID",
                    "The channel must be enabled before it can be checked");
        }
        var transport = transports.get(channel);
        if (transport == null) {
            throw new EventDomainException("EVENT_CHANNEL_UNAVAILABLE",
                    "The channel transport is unavailable");
        }
        var checkId = Math.abs((systemId * 31 + actorId) ^ clock.millis());
        var result = transport.deliver(new EventChannelTransport.DeliveryCommand(
                checkId, systemId, tenantId, actorId, "EVENT_CHANNEL_CHECK",
                "channel-check-" + checkId, "通知渠道连通性检查",
                "这是一条由系统管理员主动触发的通知渠道连通性检查。",
                "SYSTEM", Long.toString(systemId), "/admin/event/channels", Map.of("check", "true")));
        var checkedAt = clock.instant();
        repository.recordCheck(systemId, channel, result.status().name(), result.traceId(),
                result.durationMillis(), checkedAt);
        return new CheckView(channel, result.status().name(), safeMessage(result), result.traceId(),
                checkedAt, result.durationMillis());
    }

    private ConfigurationView view(DeliveryChannel channel,
                                   EventChannelConfigurationRepository.Configuration value) {
        if (channel == DeliveryChannel.INBOX) {
            return new ConfigurationView(channel, true, true, true, "站内信", "站内消息中心",
                    null, null, 0, null, null, null);
        }
        var available = transports.containsKey(channel);
        if (value == null) {
            return new ConfigurationView(channel, false, available, false, displayName(channel),
                    null, null, channel == DeliveryChannel.WEBHOOK ? DEFAULT_TIMEOUT_MS : null,
                    0, null, null, null);
        }
        var configured = channel == DeliveryChannel.EMAIL
                ? available : text(value.endpoint()) && text(value.secretRef());
        return new ConfigurationView(channel, value.enabled(), available, configured,
                displayName(channel), destination(channel, value.endpoint()), mask(value.secretRef()),
                channel == DeliveryChannel.WEBHOOK ? value.timeoutMs() : null, value.version(),
                value.updatedAt(), value.lastCheckAt(), value.lastCheckStatus());
    }

    private static String endpoint(String value, boolean allowLoopbackHttpForTesting) {
        final URI endpoint;
        try {
            endpoint = URI.create(value.strip());
        } catch (IllegalArgumentException invalid) {
            throw invalidConfiguration();
        }
        var loopbackTest = allowLoopbackHttpForTesting
                && "http".equalsIgnoreCase(endpoint.getScheme())
                && ("127.0.0.1".equals(endpoint.getHost())
                || "localhost".equalsIgnoreCase(endpoint.getHost())
                || "[::1]".equals(endpoint.getHost()) || "::1".equals(endpoint.getHost()));
        if ((!"https".equalsIgnoreCase(endpoint.getScheme()) && !loopbackTest) || !text(endpoint.getHost())
                || endpoint.getUserInfo() != null || endpoint.getFragment() != null) {
            throw invalidConfiguration();
        }
        return endpoint.toASCIIString();
    }

    private static String secretRef(String value) {
        var normalized = value.strip();
        if (!SECRET_REF.matcher(normalized).matches()) invalid();
        return normalized;
    }

    private static int timeout(int value) {
        if (value < 100 || value > MAX_TIMEOUT_MS) invalid();
        return value;
    }

    private static String destination(DeliveryChannel channel, String endpoint) {
        if (channel == DeliveryChannel.EMAIL) return "系统成员邮箱（投递时脱敏解析）";
        if (!text(endpoint)) return null;
        try {
            var uri = URI.create(endpoint);
            var port = uri.getPort() < 0 ? "" : ":" + uri.getPort();
            return uri.getScheme() + "://" + uri.getHost() + port + "/…";
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private static String mask(String secretRef) {
        return text(secretRef) ? "env://********" : null;
    }

    private static String displayName(DeliveryChannel channel) {
        return channel == DeliveryChannel.EMAIL ? "电子邮件" : "签名 Webhook";
    }

    private static String safeMessage(EventChannelTransport.DeliveryResult result) {
        return switch (result.status()) {
            case SENT -> "连通性检查已成功投递";
            case TEMPORARY_FAILURE -> "连通性检查暂时失败，可稍后重试（" + safeCode(result.failureCode()) + "）";
            case PERMANENT_FAILURE -> "连通性检查失败，请检查配置（" + safeCode(result.failureCode()) + "）";
        };
    }

    private static String safeCode(String value) {
        return value == null || !value.matches("[A-Z0-9_]{1,80}") ? "CHANNEL_DELIVERY_FAILED" : value;
    }

    private static void requireExternal(DeliveryChannel channel) {
        if (channel == null || channel == DeliveryChannel.INBOX) invalid();
    }

    private static void requireId(long value, String name) {
        if (value <= 0) throw new IllegalArgumentException(name + " must be positive");
    }

    private static boolean text(String value) {
        return value != null && !value.isBlank();
    }

    private static void invalid() {
        throw invalidConfiguration();
    }

    private static EventDomainException invalidConfiguration() {
        return new EventDomainException("EVENT_CHANNEL_CONFIGURATION_INVALID",
                "Event channel configuration is invalid");
    }

    private static EventDomainException conflict() {
        return new EventDomainException("EVENT_CHANNEL_CONFIGURATION_VERSION_CONFLICT",
                "Event channel configuration changed; reload before saving again");
    }

    public record UpdateCommand(boolean enabled, String endpoint, String secretRef,
                                Integer timeoutMs, long expectedVersion) {
    }

    public record ConfigurationView(
            DeliveryChannel channel,
            boolean enabled,
            boolean available,
            boolean configured,
            String displayName,
            String maskedDestination,
            String secretRefMasked,
            Integer timeoutMs,
            long version,
            Instant updatedAt,
            Instant lastCheckAt,
            String lastCheckStatus
    ) {
    }

    public record CheckView(DeliveryChannel channel, String status, String message, String traceId,
                            Instant checkedAt, long durationMs) {
    }
}
