package com.unique.examine.messagelog.manage.message;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.PageRequest;
import com.unique.examine.core.api.PageResult;
import com.unique.examine.core.context.CurrentRequestHeaders;
import com.unique.examine.core.context.RequestContext;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.messagelog.base.entity.MessageMessage;
import com.unique.examine.messagelog.base.service.MessageMessageBaseService;
import com.unique.examine.messagelog.manage.message.MessageModels.MessageActionResult;
import com.unique.examine.messagelog.manage.message.MessageModels.MessageBulkActionRequest;
import com.unique.examine.messagelog.manage.message.MessageModels.MessageCardVO;
import com.unique.examine.messagelog.manage.message.MessageModels.MessageLoadMoreRequest;
import com.unique.examine.messagelog.manage.message.MessageModels.MessageLoadMoreResult;
import com.unique.examine.messagelog.manage.message.MessageModels.MessageMarkAllReadRequest;
import com.unique.examine.messagelog.manage.message.MessageModels.MessageQueryRequest;
import com.unique.examine.messagelog.manage.message.MessageModels.MessageTargetVO;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Message center service backed by persisted messages.
 */
@Service
public class MessageService {

    private static final int READ_UNREAD = 0;
    private static final int READ_READ = 1;
    private static final int ARCHIVE_ACTIVE = 0;
    private static final int ARCHIVE_ARCHIVED = 1;
    private static final String READ_STATUS_UNREAD = "unread";
    private static final String READ_STATUS_READ = "read";
    private static final String ARCHIVE_STATUS_ACTIVE = "active";
    private static final String ARCHIVE_STATUS_ARCHIVED = "archived";

    private final MessageMessageBaseService messageBaseService;
    private final ObjectMapper objectMapper;

    public MessageService(MessageMessageBaseService messageBaseService, ObjectMapper objectMapper) {
        this.messageBaseService = messageBaseService;
        this.objectMapper = objectMapper;
    }

    /**
     * Search platform messages.
     *
     * @param pageRequest page request
     * @param query query request
     * @return message page
     */
    public PageResult<MessageCardVO> platformMessages(PageRequest pageRequest, MessageQueryRequest query) {
        return page(MessageTargetPolicy.PLATFORM_SCOPE, null, pageRequest, query);
    }

    /**
     * Search system messages.
     *
     * @param systemId system id
     * @param pageRequest page request
     * @param query query request
     * @return message page
     */
    public PageResult<MessageCardVO> systemMessages(String systemId, PageRequest pageRequest,
                                                    MessageQueryRequest query) {
        Long parsedSystemId = requireSystemId(systemId);
        return page(MessageTargetPolicy.SYSTEM_SCOPE, parsedSystemId, pageRequest, query);
    }

    /**
     * Load more platform messages by cursor.
     *
     * @param request load request
     * @return loaded records and cursor state
     */
    public MessageLoadMoreResult loadMorePlatform(MessageLoadMoreRequest request) {
        return loadMore(MessageTargetPolicy.PLATFORM_SCOPE, null, request);
    }

    /**
     * Load more system messages by cursor.
     *
     * @param systemId system id
     * @param request load request
     * @return loaded records and cursor state
     */
    public MessageLoadMoreResult loadMoreSystem(String systemId, MessageLoadMoreRequest request) {
        return loadMore(MessageTargetPolicy.SYSTEM_SCOPE, requireSystemId(systemId), request);
    }

    /**
     * Mark platform messages as read.
     *
     * @param request action request
     * @return action result
     */
    public MessageActionResult markPlatformRead(MessageBulkActionRequest request) {
        int affected = updateMessages(MessageTargetPolicy.PLATFORM_SCOPE, null, tenantId(request),
                messageIds(request), READ_READ, null);
        return action(MessageTargetPolicy.PLATFORM_SCOPE, null, tenantId(request), "mark-read", affected,
                READ_STATUS_READ);
    }

    /**
     * Mark system messages as read.
     *
     * @param systemId system id
     * @param request action request
     * @return action result
     */
    public MessageActionResult markSystemRead(String systemId, MessageBulkActionRequest request) {
        Long parsedSystemId = requireSystemId(systemId);
        int affected = updateMessages(MessageTargetPolicy.SYSTEM_SCOPE, parsedSystemId, tenantId(request),
                messageIds(request), READ_READ, null);
        return action(MessageTargetPolicy.SYSTEM_SCOPE, systemId, tenantId(request), "mark-read", affected,
                READ_STATUS_READ);
    }

    /**
     * Archive platform messages.
     *
     * @param request action request
     * @return action result
     */
    public MessageActionResult archivePlatform(MessageBulkActionRequest request) {
        int affected = updateMessages(MessageTargetPolicy.PLATFORM_SCOPE, null, tenantId(request),
                messageIds(request), null, ARCHIVE_ARCHIVED);
        return action(MessageTargetPolicy.PLATFORM_SCOPE, null, tenantId(request), "archive", affected,
                ARCHIVE_STATUS_ARCHIVED);
    }

    /**
     * Archive system messages.
     *
     * @param systemId system id
     * @param request action request
     * @return action result
     */
    public MessageActionResult archiveSystem(String systemId, MessageBulkActionRequest request) {
        Long parsedSystemId = requireSystemId(systemId);
        int affected = updateMessages(MessageTargetPolicy.SYSTEM_SCOPE, parsedSystemId, tenantId(request),
                messageIds(request), null, ARCHIVE_ARCHIVED);
        return action(MessageTargetPolicy.SYSTEM_SCOPE, systemId, tenantId(request), "archive", affected,
                ARCHIVE_STATUS_ARCHIVED);
    }

    /**
     * Mark every filtered platform message as read.
     *
     * @param request action request
     * @return action result
     */
    public MessageActionResult markAllPlatformRead(MessageMarkAllReadRequest request) {
        MessageQueryRequest query = fromMarkAll(request);
        int affected = updateByQuery(MessageTargetPolicy.PLATFORM_SCOPE, null, query, READ_READ, null);
        return action(MessageTargetPolicy.PLATFORM_SCOPE, null, tenantId(request), "mark-all-read", affected,
                READ_STATUS_READ);
    }

    /**
     * Mark every filtered system message as read.
     *
     * @param systemId system id
     * @param request action request
     * @return action result
     */
    public MessageActionResult markAllSystemRead(String systemId, MessageMarkAllReadRequest request) {
        Long parsedSystemId = requireSystemId(systemId);
        MessageQueryRequest query = fromMarkAll(request);
        int affected = updateByQuery(MessageTargetPolicy.SYSTEM_SCOPE, parsedSystemId, query, READ_READ, null);
        return action(MessageTargetPolicy.SYSTEM_SCOPE, systemId, tenantId(request), "mark-all-read", affected,
                READ_STATUS_READ);
    }

    private PageResult<MessageCardVO> page(String scope, Long systemId, PageRequest pageRequest,
                                           MessageQueryRequest query) {
        int pageNo = Objects.isNull(pageRequest) || pageRequest.pageNo() <= 0 ? 1 : pageRequest.pageNo();
        int pageSize = Objects.isNull(pageRequest) || pageRequest.pageSize() <= 0 ? 20 : pageRequest.pageSize();
        int offset = (pageNo - 1) * pageSize;
        long total = messageBaseService.count(queryWrapper(scope, systemId, query, null));
        List<MessageCardVO> records = messageBaseService.list(queryWrapper(scope, systemId, query, null)
                        .orderByDesc(MessageMessage::getCreatedAt)
                        .orderByDesc(MessageMessage::getId)
                        .last("LIMIT " + offset + "," + pageSize))
                .stream()
                .map(this::toVO)
                .toList();
        return new PageResult<>(records, pageNo, pageSize, total, offset + records.size() < total);
    }

    private MessageLoadMoreResult loadMore(String scope, Long systemId, MessageLoadMoreRequest request) {
        int size = resolveSize(request);
        MessageQueryRequest query = fromLoadMore(request);
        Long cursor = parseLong(Objects.isNull(request) ? null : request.cursor());
        List<MessageCardVO> records = messageBaseService.list(queryWrapper(scope, systemId, query, cursor)
                        .orderByDesc(MessageMessage::getCreatedAt)
                        .orderByDesc(MessageMessage::getId)
                        .last("LIMIT " + (size + 1)))
                .stream()
                .map(this::toVO)
                .toList();
        boolean hasNext = records.size() > size;
        List<MessageCardVO> pageRecords = hasNext ? records.subList(0, size) : records;
        String nextCursor = hasNext && !pageRecords.isEmpty()
                ? pageRecords.get(pageRecords.size() - 1).messageId()
                : null;
        return new MessageLoadMoreResult(pageRecords, nextCursor, hasNext, "loaded", LocalDateTime.now());
    }

    private LambdaQueryWrapper<MessageMessage> queryWrapper(String scope, Long systemId, MessageQueryRequest query,
                                                            Long beforeId) {
        LambdaQueryWrapper<MessageMessage> wrapper = new LambdaQueryWrapper<MessageMessage>()
                .eq(MessageMessage::getScope, scope);
        Long accountId = CurrentRequestHeaders.currentAccountIdOrNull();
        if (Objects.nonNull(accountId)) {
            wrapper.eq(MessageMessage::getReceiverId, accountId);
        }
        if (Objects.nonNull(systemId)) {
            wrapper.eq(MessageMessage::getSystemId, systemId);
        }
        if (Objects.nonNull(query)) {
            Long querySystemId = parseLong(query.systemId());
            Long tenantId = parseLong(query.tenantId());
            if (Objects.nonNull(querySystemId)) {
                wrapper.eq(MessageMessage::getSystemId, querySystemId);
            }
            if (Objects.nonNull(tenantId)) {
                wrapper.eq(MessageMessage::getTenantId, tenantId);
            }
            if (StringUtils.hasText(query.templateCode())) {
                wrapper.eq(MessageMessage::getTemplateCode, query.templateCode());
            }
            if (StringUtils.hasText(query.type())) {
                wrapper.eq(MessageMessage::getMessageType, query.type());
            }
            Integer readStatus = parseReadStatus(query.readStatus());
            if (Objects.nonNull(readStatus)) {
                wrapper.eq(MessageMessage::getReadStatus, readStatus);
            }
            Integer archiveStatus = parseArchiveStatus(query.archiveStatus());
            if (Objects.nonNull(archiveStatus)) {
                wrapper.eq(MessageMessage::getArchiveStatus, archiveStatus);
            }
            if (StringUtils.hasText(query.keyword())) {
                wrapper.and(value -> value.like(MessageMessage::getTitle, query.keyword())
                        .or().like(MessageMessage::getContent, query.keyword()));
            }
        }
        if (Objects.nonNull(beforeId)) {
            wrapper.lt(MessageMessage::getId, beforeId);
        }
        return wrapper;
    }

    private int updateMessages(String scope, Long systemId, String tenantId, List<Long> messageIds,
                               Integer readStatus, Integer archiveStatus) {
        if (messageIds.isEmpty()) {
            return 0;
        }
        LambdaQueryWrapper<MessageMessage> countWrapper = baseActionWrapper(scope, systemId, tenantId)
                .in(MessageMessage::getId, messageIds);
        int affected = Math.toIntExact(messageBaseService.count(countWrapper));
        if (affected == 0) {
            return 0;
        }
        LambdaUpdateWrapper<MessageMessage> updateWrapper = baseUpdateWrapper(scope, systemId, tenantId)
                .in(MessageMessage::getId, messageIds);
        applyUpdateStatus(updateWrapper, readStatus, archiveStatus);
        messageBaseService.update(updateWrapper);
        return affected;
    }

    private int updateByQuery(String scope, Long systemId, MessageQueryRequest query,
                              Integer readStatus, Integer archiveStatus) {
        int affected = Math.toIntExact(messageBaseService.count(queryWrapper(scope, systemId, query, null)));
        if (affected == 0) {
            return 0;
        }
        LambdaUpdateWrapper<MessageMessage> updateWrapper = updateWrapper(scope, systemId, query);
        applyUpdateStatus(updateWrapper, readStatus, archiveStatus);
        messageBaseService.update(updateWrapper);
        return affected;
    }

    private LambdaQueryWrapper<MessageMessage> baseActionWrapper(String scope, Long systemId, String tenantId) {
        LambdaQueryWrapper<MessageMessage> wrapper = new LambdaQueryWrapper<MessageMessage>()
                .eq(MessageMessage::getScope, scope);
        Long accountId = CurrentRequestHeaders.currentAccountIdOrNull();
        if (Objects.nonNull(accountId)) {
            wrapper.eq(MessageMessage::getReceiverId, accountId);
        }
        if (Objects.nonNull(systemId)) {
            wrapper.eq(MessageMessage::getSystemId, systemId);
        }
        Long parsedTenantId = parseLong(tenantId);
        if (Objects.nonNull(parsedTenantId)) {
            wrapper.eq(MessageMessage::getTenantId, parsedTenantId);
        }
        return wrapper;
    }

    private LambdaUpdateWrapper<MessageMessage> baseUpdateWrapper(String scope, Long systemId, String tenantId) {
        LambdaUpdateWrapper<MessageMessage> wrapper = new LambdaUpdateWrapper<MessageMessage>()
                .eq(MessageMessage::getScope, scope);
        Long accountId = CurrentRequestHeaders.currentAccountIdOrNull();
        if (Objects.nonNull(accountId)) {
            wrapper.eq(MessageMessage::getReceiverId, accountId);
        }
        if (Objects.nonNull(systemId)) {
            wrapper.eq(MessageMessage::getSystemId, systemId);
        }
        Long parsedTenantId = parseLong(tenantId);
        if (Objects.nonNull(parsedTenantId)) {
            wrapper.eq(MessageMessage::getTenantId, parsedTenantId);
        }
        return wrapper;
    }

    private LambdaUpdateWrapper<MessageMessage> updateWrapper(String scope, Long systemId, MessageQueryRequest query) {
        LambdaUpdateWrapper<MessageMessage> wrapper = new LambdaUpdateWrapper<MessageMessage>()
                .eq(MessageMessage::getScope, scope);
        Long accountId = CurrentRequestHeaders.currentAccountIdOrNull();
        if (Objects.nonNull(accountId)) {
            wrapper.eq(MessageMessage::getReceiverId, accountId);
        }
        if (Objects.nonNull(systemId)) {
            wrapper.eq(MessageMessage::getSystemId, systemId);
        }
        if (Objects.nonNull(query)) {
            Long tenantId = parseLong(query.tenantId());
            if (Objects.nonNull(tenantId)) {
                wrapper.eq(MessageMessage::getTenantId, tenantId);
            }
            if (StringUtils.hasText(query.templateCode())) {
                wrapper.eq(MessageMessage::getTemplateCode, query.templateCode());
            }
            if (StringUtils.hasText(query.type())) {
                wrapper.eq(MessageMessage::getMessageType, query.type());
            }
            Integer readStatus = parseReadStatus(query.readStatus());
            if (Objects.nonNull(readStatus)) {
                wrapper.eq(MessageMessage::getReadStatus, readStatus);
            }
            Integer archiveStatus = parseArchiveStatus(query.archiveStatus());
            if (Objects.nonNull(archiveStatus)) {
                wrapper.eq(MessageMessage::getArchiveStatus, archiveStatus);
            }
            if (StringUtils.hasText(query.keyword())) {
                wrapper.and(value -> value.like(MessageMessage::getTitle, query.keyword())
                        .or().like(MessageMessage::getContent, query.keyword()));
            }
        }
        return wrapper;
    }

    private void applyUpdateStatus(LambdaUpdateWrapper<MessageMessage> wrapper, Integer readStatus,
                                   Integer archiveStatus) {
        if (Objects.nonNull(readStatus)) {
            wrapper.set(MessageMessage::getReadStatus, readStatus);
        }
        if (Objects.nonNull(archiveStatus)) {
            wrapper.set(MessageMessage::getArchiveStatus, archiveStatus);
        }
    }

    private MessageCardVO toVO(MessageMessage message) {
        return new MessageCardVO(
                String.valueOf(message.getId()),
                message.getScope(),
                stringId(message.getSystemId()),
                stringId(message.getTenantId()),
                message.getTemplateCode(),
                message.getMessageType(),
                message.getTitle(),
                message.getContent(),
                toReadStatus(message.getReadStatus()),
                toArchiveStatus(message.getArchiveStatus()),
                target(message),
                RequestContext.current().traceId(),
                message.getCreatedAt(),
                null,
                null
        );
    }

    private MessageTargetVO target(MessageMessage message) {
        JsonNode node = readPayload(message.getTargetPayload());
        String scope = text(node, "scope", message.getScope());
        String targetType = text(node, "targetType", defaultTargetType(scope));
        String targetId = text(node, "targetId", String.valueOf(message.getId()));
        String targetSystemId = text(node, "targetSystemId", stringId(message.getSystemId()));
        String targetTenantId = text(node, "targetTenantId", stringId(message.getTenantId()));
        boolean requiresSystemSwitch = bool(node, "requiresSystemSwitch",
                MessageTargetPolicy.PLATFORM_SCOPE.equals(scope) && Objects.nonNull(message.getSystemId()));
        String fallbackAction = text(node, "fallbackAction",
                requiresSystemSwitch ? "OPEN_SYSTEM_SWITCH" : "SYSTEM_MEMBER_CONTEXT_REQUIRED");
        MessageTargetPolicy.assertAllowed(scope, targetType);
        return new MessageTargetVO(scope, targetType, targetId, targetSystemId, targetTenantId,
                requiresSystemSwitch, fallbackAction, null);
    }

    private JsonNode readPayload(String payload) {
        if (!StringUtils.hasText(payload)) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(payload);
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }

    private MessageQueryRequest fromLoadMore(MessageLoadMoreRequest request) {
        if (Objects.isNull(request)) {
            return null;
        }
        return new MessageQueryRequest(request.systemId(), request.tenantId(), request.templateCode(), request.type(),
                request.readStatus(), request.archiveStatus(), request.timeRange(), request.keyword());
    }

    private MessageQueryRequest fromMarkAll(MessageMarkAllReadRequest request) {
        if (Objects.isNull(request)) {
            return new MessageQueryRequest(null, null, null, null, READ_STATUS_UNREAD, ARCHIVE_STATUS_ACTIVE,
                    null, null);
        }
        return new MessageQueryRequest(null, request.tenantId(), request.templateCode(), request.type(),
                READ_STATUS_UNREAD, ARCHIVE_STATUS_ACTIVE, request.timeRange(), request.keyword());
    }

    private MessageActionResult action(String scope, String systemId, String tenantId, String action,
                                       int affectedCount, String status) {
        RequestContext context = RequestContext.current();
        return new MessageActionResult(scope, systemId, tenantId, action, affectedCount, status,
                context.traceId(), "aud_" + context.traceId(), LocalDateTime.now());
    }

    private List<Long> messageIds(MessageBulkActionRequest request) {
        if (Objects.isNull(request) || Objects.isNull(request.messageIds())) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (String messageId : request.messageIds()) {
            Long id = parseLong(messageId);
            if (Objects.nonNull(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    private String tenantId(MessageBulkActionRequest request) {
        return Objects.isNull(request) ? null : request.tenantId();
    }

    private String tenantId(MessageMarkAllReadRequest request) {
        return Objects.isNull(request) ? null : request.tenantId();
    }

    private int resolveSize(MessageLoadMoreRequest request) {
        if (Objects.isNull(request) || Objects.isNull(request.size()) || request.size() <= 0) {
            return 20;
        }
        return Math.min(request.size(), 50);
    }

    private Long requireSystemId(String systemId) {
        Long parsed = parseLong(systemId);
        if (Objects.isNull(parsed)) {
            throw new BusinessException(MessageErrorCode.MESSAGE_CONTEXT_REQUIRED,
                    "System message APIs require a numeric system id.");
        }
        return parsed;
    }

    private Integer parseReadStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        return switch (status.trim().toLowerCase()) {
            case "0", READ_STATUS_UNREAD -> READ_UNREAD;
            case "1", READ_STATUS_READ -> READ_READ;
            default -> null;
        };
    }

    private Integer parseArchiveStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        return switch (status.trim().toLowerCase()) {
            case "0", ARCHIVE_STATUS_ACTIVE -> ARCHIVE_ACTIVE;
            case "1", ARCHIVE_STATUS_ARCHIVED -> ARCHIVE_ARCHIVED;
            default -> null;
        };
    }

    private String toReadStatus(Integer status) {
        return Objects.equals(status, READ_READ) ? READ_STATUS_READ : READ_STATUS_UNREAD;
    }

    private String toArchiveStatus(Integer status) {
        return Objects.equals(status, ARCHIVE_ARCHIVED) ? ARCHIVE_STATUS_ARCHIVED : ARCHIVE_STATUS_ACTIVE;
    }

    private String defaultTargetType(String scope) {
        return MessageTargetPolicy.PLATFORM_SCOPE.equals(scope) ? "platform_task" : "business_record";
    }

    private String stringId(Long value) {
        return Objects.isNull(value) ? null : String.valueOf(value);
    }

    private String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.get(field);
        return Objects.nonNull(value) && value.isTextual() && StringUtils.hasText(value.asText())
                ? value.asText()
                : fallback;
    }

    private boolean bool(JsonNode node, String field, boolean fallback) {
        JsonNode value = node.get(field);
        return Objects.nonNull(value) && value.isBoolean() ? value.asBoolean() : fallback;
    }

    private Long parseLong(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
