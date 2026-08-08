package com.unique.examine.event.service;

import com.unique.examine.core.error.BusinessException;
import com.unique.examine.event.adapter.jdbc.JdbcMessageTemplateRepository;
import com.unique.examine.event.domain.DeliveryChannel;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Transactional(readOnly = true)
public class MessageDeliveryLogService {
    private static final Set<String> STATUSES = Set.of("PENDING", "DELIVERED", "SKIPPED", "FAILED");
    private static final Pattern TEMPLATE_CODE = Pattern.compile("[A-Z][A-Z0-9_]{1,99}");
    private final JdbcMessageTemplateRepository repository;

    public MessageDeliveryLogService(JdbcMessageTemplateRepository repository) {
        if (repository == null) throw new IllegalArgumentException("Delivery log repository is required");
        this.repository = repository;
    }

    public DeliveryLogPage list(long systemId, long tenantId, int page, int size,
                                String channel, String status, String templateCode) {
        if (systemId <= 0 || tenantId <= 0 || page < 0 || size < 1 || size > 100) throw invalid();
        var normalizedChannel = optionalChannel(channel);
        var normalizedStatus = optionalStatus(status);
        var normalizedTemplate = optionalTemplate(templateCode);
        var values = repository.deliveryLogs(systemId, tenantId, page, size,
                normalizedChannel, normalizedStatus, normalizedTemplate);
        return new DeliveryLogPage(values.items().stream().map(MessageDeliveryLogService::summary).toList(),
                values.total(), values.page(), values.size());
    }

    public DeliveryLogDetail detail(long systemId, long tenantId, long deliveryId) {
        if (systemId <= 0 || tenantId <= 0 || deliveryId <= 0) throw invalid();
        var delivery = repository.scopedDelivery(systemId, tenantId, deliveryId)
                .orElseThrow(() -> new BusinessException("EVENT_DELIVERY_LOG_NOT_FOUND",
                        "Delivery log was not found", HttpStatus.NOT_FOUND));
        var attempts = repository.deliveryAttempts(systemId, tenantId, deliveryId).stream()
                .map(MessageDeliveryLogService::attempt).toList();
        var value = summary(delivery);
        return new DeliveryLogDetail(value.deliveryId(), value.templateCode(), value.channel(), value.status(),
                value.attemptCount(), value.durationMs(), value.traceId(), value.recipientMasked(),
                value.targetType(), value.targetId(), value.createdAt(), value.completedAt(), value.retryable(),
                delivery.templateVersionId(), safePath(delivery.targetPath()), safeFailureCode(delivery.failureCode()),
                safeFailureMessage(delivery.failureCode(), delivery.failureMessage()),
                fingerprint(delivery.dedupeKey()), attempts);
    }

    private static DeliveryLogItem summary(JdbcMessageTemplateRepository.DeliveryRecord value) {
        return new DeliveryLogItem(value.id(), value.templateCode(), value.channel(), value.status(),
                value.attemptCount(), value.durationMillis(), value.traceId(),
                masked(value.maskedDestination(), value.recipientMemberId()), value.targetType(), value.targetId(),
                value.createdAt(), value.completedAt(),
                "FAILED".equals(value.status())
                        && MessageTemplateService.TEMPORARY_FAILURE.equals(value.failureCode())
                        && value.attemptCount() < 3);
    }

    private static DeliveryAttemptView attempt(JdbcMessageTemplateRepository.AttemptRecord value) {
        return new DeliveryAttemptView(value.attemptNo(), value.status(), value.durationMillis(), value.traceId(),
                safeFailureCode(value.failureCode()), safeFailureMessage(value.failureCode(), value.failureMessage()),
                value.startedAt(), value.completedAt());
    }

    private static String optionalChannel(String value) {
        if (value == null || value.isBlank()) return null;
        return DeliveryChannel.parse(value).name();
    }

    private static String optionalStatus(String value) {
        if (value == null || value.isBlank()) return null;
        if (!STATUSES.contains(value)) throw invalid();
        return value;
    }

    private static String optionalTemplate(String value) {
        if (value == null || value.isBlank()) return null;
        if (!TEMPLATE_CODE.matcher(value).matches()) throw invalid();
        return value;
    }

    private static String masked(String value, long memberId) {
        if (value != null && !value.isBlank() && value.length() <= 200
                && !value.contains("?") && !value.contains("#")
                && (!value.contains("@") || value.contains("***"))) return value;
        var id = Long.toString(memberId);
        return "member-***" + id.substring(Math.max(0, id.length() - 3));
    }

    private static String safePath(String value) {
        if (value == null) return null;
        var query = value.indexOf('?');
        var fragment = value.indexOf('#');
        var end = query < 0 ? value.length() : query;
        if (fragment >= 0) end = Math.min(end, fragment);
        return value.substring(0, Math.min(end, 500));
    }

    private static String safeFailureCode(String value) {
        return value != null && value.matches("[A-Z][A-Z0-9_]{1,63}") ? value : null;
    }

    private static String safeFailureMessage(String code, String ignoredStoredMessage) {
        if (code == null) return null;
        return switch (code) {
            case "MESSAGE_TEMPLATE_NOT_PUBLISHED" -> "Published template was not found";
            case "MESSAGE_TEMPLATE_DISABLED" -> "Published template is disabled";
            case "MESSAGE_DELIVERY_DISABLED_BY_RECIPIENT" -> "Recipient disabled this notification";
            case "MESSAGE_DELIVERY_CHANNEL_UNAVAILABLE" -> "Delivery channel is not configured";
            case "MESSAGE_DELIVERY_PERMANENT_FAILURE" -> "External delivery was rejected permanently";
            case MessageTemplateService.TEMPORARY_FAILURE -> MessageTemplateService.SAFE_TEMPORARY_MESSAGE;
            default -> "Delivery failed safely";
        };
    }

    private static String fingerprint(String value) {
        if (value == null) return null;
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static BusinessException invalid() {
        return new BusinessException("EVENT_DELIVERY_LOG_FILTER_INVALID",
                "Delivery log request is invalid", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public record DeliveryLogPage(List<DeliveryLogItem> items, long total, int page, int size) {
        public DeliveryLogPage { items = List.copyOf(items); }
    }

    public record DeliveryLogItem(long deliveryId, String templateCode, String channel, String status,
                                  int attemptCount, Long durationMs, String traceId, String recipientMasked,
                                  String targetType, String targetId, LocalDateTime createdAt,
                                  LocalDateTime completedAt, boolean retryable) { }

    public record DeliveryLogDetail(long deliveryId, String templateCode, String channel, String status,
                                    int attemptCount, Long durationMs, String traceId, String recipientMasked,
                                    String targetType, String targetId, LocalDateTime createdAt,
                                    LocalDateTime completedAt, boolean retryable, Long templateVersionId,
                                    String targetPath, String failureCode, String failureMessage,
                                    String dedupeFingerprint, List<DeliveryAttemptView> attempts) {
        public DeliveryLogDetail { attempts = List.copyOf(attempts); }
    }

    public record DeliveryAttemptView(int attemptNo, String status, Long durationMs, String traceId,
                                      String failureCode, String failureMessage,
                                      LocalDateTime startedAt, LocalDateTime completedAt) { }
}
