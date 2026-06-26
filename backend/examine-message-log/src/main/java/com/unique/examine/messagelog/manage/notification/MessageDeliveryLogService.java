package com.unique.examine.messagelog.manage.notification;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.unique.examine.core.api.PageRequest;
import com.unique.examine.core.api.PageResult;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.messagelog.base.entity.MessageDeliveryLog;
import com.unique.examine.messagelog.base.entity.MessageMessage;
import com.unique.examine.messagelog.base.service.MessageDeliveryLogBaseService;
import com.unique.examine.messagelog.base.service.MessageMessageBaseService;
import com.unique.examine.messagelog.manage.message.MessageErrorCode;
import com.unique.examine.messagelog.manage.message.MessageTargetPolicy;
import com.unique.examine.messagelog.manage.notification.NotificationModels.MessageDeliveryLogQueryRequest;
import com.unique.examine.messagelog.manage.notification.NotificationModels.MessageDeliveryLogVO;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Message delivery log query service backed by persisted delivery logs.
 */
@Service
public class MessageDeliveryLogService {

    private static final int ARCHIVE_ACTIVE = 0;
    private static final int ARCHIVE_ARCHIVED = 1;

    private final MessageDeliveryLogBaseService deliveryLogBaseService;
    private final MessageMessageBaseService messageBaseService;

    public MessageDeliveryLogService(MessageDeliveryLogBaseService deliveryLogBaseService,
                                     MessageMessageBaseService messageBaseService) {
        this.deliveryLogBaseService = deliveryLogBaseService;
        this.messageBaseService = messageBaseService;
    }

    /**
     * Search message delivery logs.
     *
     * @param systemId system id
     * @param pageRequest page request
     * @param query query request
     * @return delivery log page
     */
    public PageResult<MessageDeliveryLogVO> deliveryLogs(String systemId, PageRequest pageRequest,
                                                         MessageDeliveryLogQueryRequest query) {
        Long resolvedSystemId = requireSystemId(systemId);
        List<MessageDeliveryLog> candidateLogs = deliveryLogBaseService.list(logQuery(query)
                .orderByDesc(MessageDeliveryLog::getCreatedAt)
                .orderByDesc(MessageDeliveryLog::getId)
                .last("LIMIT 1000"));
        Map<Long, MessageMessage> messages = messages(candidateLogs);
        List<MessageDeliveryLogVO> matched = candidateLogs.stream()
                .map(log -> toVO(log, messages.get(log.getMessageId())))
                .filter(log -> matches(resolvedSystemId, query, log))
                .toList();
        return page(matched, pageRequest);
    }

    private LambdaQueryWrapper<MessageDeliveryLog> logQuery(MessageDeliveryLogQueryRequest query) {
        LambdaQueryWrapper<MessageDeliveryLog> wrapper = new LambdaQueryWrapper<>();
        if (Objects.nonNull(query)) {
            Long messageId = parseLong(query.messageId());
            if (Objects.nonNull(messageId)) {
                wrapper.eq(MessageDeliveryLog::getMessageId, messageId);
            }
            if (MessageTargetPolicy.hasText(query.channel())) {
                wrapper.eq(MessageDeliveryLog::getChannel, query.channel());
            }
            if (MessageTargetPolicy.hasText(query.status())) {
                wrapper.eq(MessageDeliveryLog::getStatus, query.status());
            }
            Integer archiveStatus = parseArchiveStatus(query.archiveStatus());
            if (Objects.nonNull(archiveStatus)) {
                wrapper.eq(MessageDeliveryLog::getArchiveStatus, archiveStatus);
            }
            if (MessageTargetPolicy.hasText(query.traceId())) {
                wrapper.eq(MessageDeliveryLog::getTraceId, query.traceId());
            }
        }
        return wrapper;
    }

    private Map<Long, MessageMessage> messages(List<MessageDeliveryLog> logs) {
        List<Long> ids = logs.stream()
                .map(MessageDeliveryLog::getMessageId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return messageBaseService.list(new LambdaQueryWrapper<MessageMessage>().in(MessageMessage::getId, ids))
                .stream()
                .collect(Collectors.toMap(MessageMessage::getId, Function.identity(), (first, second) -> first));
    }

    private MessageDeliveryLogVO toVO(MessageDeliveryLog log, MessageMessage message) {
        return new MessageDeliveryLogVO(String.valueOf(log.getId()),
                Objects.isNull(message) || Objects.isNull(message.getSystemId()) ? null : String.valueOf(message.getSystemId()),
                Objects.isNull(message) || Objects.isNull(message.getTenantId()) ? null : String.valueOf(message.getTenantId()),
                Objects.isNull(message) ? null : message.getTemplateCode(),
                String.valueOf(log.getMessageId()), log.getChannel(), log.getStatus(), log.getFailureReason(),
                log.getRetryCount(), log.getReadReceipt(), Objects.equals(log.getDoNotDisturb(), 1),
                toArchiveStatus(log.getArchiveStatus()), log.getTraceId(), log.getCreatedAt());
    }

    private boolean matches(Long systemId, MessageDeliveryLogQueryRequest query, MessageDeliveryLogVO log) {
        return Objects.equals(String.valueOf(systemId), log.systemId())
                && (Objects.isNull(query)
                || matchesValue(query.tenantId(), log.tenantId())
                && matchesValue(query.templateCode(), log.templateCode())
                && matchesValue(query.messageId(), log.messageId())
                && matchesValue(query.channel(), log.channel())
                && matchesValue(query.status(), log.status())
                && matchesValue(query.archiveStatus(), log.archiveStatus())
                && matchesValue(query.traceId(), log.traceId())
                && matchesKeyword(query.keyword(), log));
    }

    private PageResult<MessageDeliveryLogVO> page(List<MessageDeliveryLogVO> records, PageRequest pageRequest) {
        int pageNo = Objects.isNull(pageRequest) || pageRequest.pageNo() <= 0 ? 1 : pageRequest.pageNo();
        int pageSize = Objects.isNull(pageRequest) || pageRequest.pageSize() <= 0 ? 20 : pageRequest.pageSize();
        int from = Math.min((pageNo - 1) * pageSize, records.size());
        int to = Math.min(from + pageSize, records.size());
        return new PageResult<>(records.subList(from, to), pageNo, pageSize, records.size(), to < records.size());
    }

    private boolean matchesValue(String expected, String actual) {
        return !MessageTargetPolicy.hasText(expected) || expected.equals(actual);
    }

    private boolean matchesKeyword(String keyword, MessageDeliveryLogVO log) {
        return !MessageTargetPolicy.hasText(keyword)
                || log.messageId().contains(keyword)
                || Objects.nonNull(log.failureReason()) && log.failureReason().contains(keyword);
    }

    private Long requireSystemId(String systemId) {
        Long parsed = parseLong(systemId);
        if (Objects.isNull(parsed)) {
            throw new BusinessException(MessageErrorCode.MESSAGE_CONTEXT_REQUIRED, "投递日志必须归属系统");
        }
        return parsed;
    }

    private Integer parseArchiveStatus(String status) {
        if (!MessageTargetPolicy.hasText(status)) {
            return null;
        }
        return switch (status.trim().toLowerCase()) {
            case "0", "active" -> ARCHIVE_ACTIVE;
            case "1", "archived" -> ARCHIVE_ARCHIVED;
            default -> null;
        };
    }

    private String toArchiveStatus(Integer status) {
        return Objects.equals(status, ARCHIVE_ARCHIVED) ? "archived" : "active";
    }

    private Long parseLong(String value) {
        if (!MessageTargetPolicy.hasText(value)) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
