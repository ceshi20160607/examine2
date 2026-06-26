package com.unique.examine.messagelog.manage.notification;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.PageRequest;
import com.unique.examine.core.api.PageResult;
import com.unique.examine.core.context.RequestContext;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.error.CommonErrorCode;
import com.unique.examine.messagelog.base.entity.MessageNotificationTemplate;
import com.unique.examine.messagelog.base.service.MessageNotificationTemplateBaseService;
import com.unique.examine.messagelog.manage.message.MessageErrorCode;
import com.unique.examine.messagelog.manage.message.MessageTargetPolicy;
import com.unique.examine.messagelog.manage.notification.NotificationModels.NotificationTargetRuleVO;
import com.unique.examine.messagelog.manage.notification.NotificationModels.NotificationTemplateDeleteResult;
import com.unique.examine.messagelog.manage.notification.NotificationModels.NotificationTemplateQueryRequest;
import com.unique.examine.messagelog.manage.notification.NotificationModels.NotificationTemplateSaveRequest;
import com.unique.examine.messagelog.manage.notification.NotificationModels.NotificationTemplateVO;
import com.unique.examine.messagelog.manage.notification.NotificationModels.PublishCheckItem;
import com.unique.examine.messagelog.manage.notification.NotificationModels.PublishCheckResultVO;
import com.unique.examine.messagelog.manage.notification.NotificationModels.QuietPolicyVO;
import com.unique.examine.messagelog.manage.notification.NotificationModels.RetryPolicyVO;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Notification template configuration service backed by persisted template records.
 */
@Service
public class NotificationTemplateService {

    private static final int STATUS_DRAFT = 0;
    private static final int STATUS_ENABLED = 1;
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private final MessageNotificationTemplateBaseService templateBaseService;
    private final ObjectMapper objectMapper;

    public NotificationTemplateService(MessageNotificationTemplateBaseService templateBaseService,
                                       ObjectMapper objectMapper) {
        this.templateBaseService = templateBaseService;
        this.objectMapper = objectMapper;
    }

    /**
     * Search notification templates.
     *
     * @param systemId system id
     * @param pageRequest page request
     * @param query query request
     * @return template page
     */
    public PageResult<NotificationTemplateVO> templates(String systemId, PageRequest pageRequest,
                                                        NotificationTemplateQueryRequest query) {
        Long resolvedSystemId = requireSystemId(systemId);
        int pageNo = Objects.isNull(pageRequest) || pageRequest.pageNo() <= 0 ? 1 : pageRequest.pageNo();
        int pageSize = Objects.isNull(pageRequest) || pageRequest.pageSize() <= 0 ? 20 : pageRequest.pageSize();
        int offset = (pageNo - 1) * pageSize;
        LambdaQueryWrapper<MessageNotificationTemplate> wrapper = queryWrapper(resolvedSystemId, query);
        long total = templateBaseService.count(wrapper);
        List<NotificationTemplateVO> records = templateBaseService.list(queryWrapper(resolvedSystemId, query)
                        .orderByDesc(MessageNotificationTemplate::getCreatedAt)
                        .last("LIMIT " + offset + "," + pageSize))
                .stream()
                .map(this::toVO)
                .toList();
        return new PageResult<>(records, pageNo, pageSize, total, offset + records.size() < total);
    }

    /**
     * Create a notification template.
     *
     * @param systemId system id
     * @param request save request
     * @return created template
     */
    public NotificationTemplateVO create(String systemId, NotificationTemplateSaveRequest request) {
        Long resolvedSystemId = requireSystemId(systemId);
        validateSaveRequest(request);
        String scope = scope(request.scope());
        if (templateBaseService.count(new LambdaQueryWrapper<MessageNotificationTemplate>()
                .eq(MessageNotificationTemplate::getScope, scope)
                .eq(MessageNotificationTemplate::getSystemId, resolvedSystemId)
                .eq(MessageNotificationTemplate::getTemplateCode, request.templateCode())) > 0) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "模板编码已存在");
        }
        MessageNotificationTemplate entity = new MessageNotificationTemplate();
        entity.setScope(scope);
        entity.setSystemId(resolvedSystemId);
        entity.setTemplateCode(request.templateCode());
        fillTemplate(entity, request, resolvedSystemId);
        entity.setCreatedAt(LocalDateTime.now());
        templateBaseService.saveEntity(entity);
        return toVO(entity);
    }

    /**
     * Return template detail.
     *
     * @param systemId system id
     * @param templateCode template code
     * @return template detail
     */
    public NotificationTemplateVO detail(String systemId, String templateCode) {
        Long resolvedSystemId = requireSystemId(systemId);
        return toVO(requireTemplate(resolvedSystemId, templateCode));
    }

    /**
     * Update a notification template.
     *
     * @param systemId system id
     * @param templateCode template code
     * @param request save request
     * @return updated template
     */
    public NotificationTemplateVO update(String systemId, String templateCode, NotificationTemplateSaveRequest request) {
        Long resolvedSystemId = requireSystemId(systemId);
        validateSaveRequest(request);
        MessageNotificationTemplate entity = requireTemplate(resolvedSystemId, templateCode);
        fillTemplate(entity, request, resolvedSystemId);
        templateBaseService.updateById(entity);
        return toVO(entity);
    }

    /**
     * Delete a notification template.
     *
     * @param systemId system id
     * @param templateCode template code
     * @return delete result
     */
    public NotificationTemplateDeleteResult delete(String systemId, String templateCode) {
        Long resolvedSystemId = requireSystemId(systemId);
        MessageNotificationTemplate entity = requireTemplate(resolvedSystemId, templateCode);
        templateBaseService.removeById(entity.getId());
        RequestContext context = RequestContext.current();
        return new NotificationTemplateDeleteResult(templateCode, systemId, "deleted",
                "aud_" + context.traceId(), LocalDateTime.now());
    }

    /**
     * Check whether a template can be published.
     *
     * @param systemId system id
     * @param templateCode template code
     * @return publish check result
     */
    public PublishCheckResultVO publishCheck(String systemId, String templateCode) {
        NotificationTemplateVO template = detail(systemId, templateCode);
        List<PublishCheckItem> failures = publishFailures(template);
        List<PublishCheckItem> warnings = publishWarnings(template);
        RequestContext context = RequestContext.current();
        return new PublishCheckResultVO(failures.isEmpty(), failures, warnings,
                List.of("message_template:" + templateCode, "message_delivery_log"),
                context.traceId(), "aud_" + context.traceId(), LocalDateTime.now());
    }

    private LambdaQueryWrapper<MessageNotificationTemplate> queryWrapper(Long systemId,
                                                                         NotificationTemplateQueryRequest query) {
        LambdaQueryWrapper<MessageNotificationTemplate> wrapper = new LambdaQueryWrapper<MessageNotificationTemplate>()
                .eq(MessageNotificationTemplate::getSystemId, systemId);
        if (Objects.nonNull(query)) {
            if (MessageTargetPolicy.hasText(query.scope())) {
                wrapper.eq(MessageNotificationTemplate::getScope, scope(query.scope()));
            }
            if (MessageTargetPolicy.hasText(query.templateType())) {
                wrapper.eq(MessageNotificationTemplate::getTemplateType, query.templateType());
            }
            if (Objects.nonNull(query.status())) {
                wrapper.eq(MessageNotificationTemplate::getStatus, query.status());
            }
            if (MessageTargetPolicy.hasText(query.keyword())) {
                wrapper.and(value -> value.like(MessageNotificationTemplate::getTemplateCode, query.keyword())
                        .or().like(MessageNotificationTemplate::getTemplateType, query.keyword()));
            }
        }
        return wrapper;
    }

    private void fillTemplate(MessageNotificationTemplate entity, NotificationTemplateSaveRequest request,
                              Long systemId) {
        NotificationTargetRuleVO targetRule = normalizeTargetRule(String.valueOf(systemId), request.scope(),
                request.targetRule());
        entity.setScope(scope(request.scope()));
        entity.setTemplateType(request.templateType());
        entity.setVariables(toJson(Objects.isNull(request.variables()) ? List.of() : request.variables()));
        entity.setChannels(toJson(request.channels()));
        entity.setTargetRule(toJson(targetRule));
        entity.setDedupeKey(request.dedupeKey());
        entity.setReadReceiptRequired(Boolean.TRUE.equals(request.readReceiptRequired()) ? 1 : 0);
        entity.setQuietPolicy(toJson(Objects.isNull(request.quietPolicy()) ? quietPolicy() : request.quietPolicy()));
        entity.setRetryPolicy(toJson(Objects.isNull(request.retryPolicy()) ? retryPolicy() : request.retryPolicy()));
        entity.setStatus(Objects.isNull(request.status()) ? STATUS_DRAFT : request.status());
    }

    private MessageNotificationTemplate requireTemplate(Long systemId, String templateCode) {
        MessageNotificationTemplate template = templateBaseService.getOne(
                new LambdaQueryWrapper<MessageNotificationTemplate>()
                        .eq(MessageNotificationTemplate::getSystemId, systemId)
                        .eq(MessageNotificationTemplate::getTemplateCode, templateCode)
                        .last("LIMIT 1"), false);
        if (Objects.isNull(template)) {
            throw new BusinessException(MessageErrorCode.MESSAGE_REQUIRED_FIELD, "通知模板不存在");
        }
        return template;
    }

    private NotificationTemplateVO toVO(MessageNotificationTemplate entity) {
        RequestContext context = RequestContext.current();
        return new NotificationTemplateVO(String.valueOf(entity.getId()),
                Objects.isNull(entity.getSystemId()) ? null : String.valueOf(entity.getSystemId()),
                entity.getTemplateCode(), entity.getScope(), entity.getTemplateType(),
                readJson(entity.getVariables(), STRING_LIST_TYPE, List.of()),
                readJson(entity.getChannels(), STRING_LIST_TYPE, List.of()),
                readJson(entity.getTargetRule(), NotificationTargetRuleVO.class,
                        systemTarget(String.valueOf(entity.getSystemId()), "business_record")),
                entity.getDedupeKey(), Objects.equals(entity.getReadReceiptRequired(), 1),
                readJson(entity.getQuietPolicy(), QuietPolicyVO.class, quietPolicy()),
                readJson(entity.getRetryPolicy(), RetryPolicyVO.class, retryPolicy()),
                entity.getStatus(), "aud_" + context.traceId(), entity.getCreatedAt());
    }

    private NotificationTargetRuleVO systemTarget(String systemId, String targetType) {
        MessageTargetPolicy.assertAllowed(MessageTargetPolicy.SYSTEM_SCOPE, targetType);
        return new NotificationTargetRuleVO(MessageTargetPolicy.SYSTEM_SCOPE, targetType, null, systemId,
                null, false, "SYSTEM_MEMBER_CONTEXT_REQUIRED");
    }

    private QuietPolicyVO quietPolicy() {
        return new QuietPolicyVO(true, "22:00-08:00", List.of("approval", "failure"));
    }

    private RetryPolicyVO retryPolicy() {
        return new RetryPolicyVO(3, 300, true);
    }

    private NotificationTargetRuleVO normalizeTargetRule(String systemId, String scope,
                                                         NotificationTargetRuleVO targetRule) {
        if (Objects.isNull(targetRule)) {
            throw new BusinessException(MessageErrorCode.MESSAGE_REQUIRED_FIELD, "通知模板跳转目标不能为空");
        }
        String normalizedScope = MessageTargetPolicy.hasText(targetRule.scope())
                ? MessageTargetPolicy.normalizeScope(targetRule.scope())
                : scope(scope);
        MessageTargetPolicy.assertAllowed(normalizedScope, targetRule.targetType());
        String targetSystemId = MessageTargetPolicy.hasText(targetRule.targetSystemId())
                ? targetRule.targetSystemId()
                : systemId;
        return new NotificationTargetRuleVO(normalizedScope, targetRule.targetType(), targetRule.targetId(),
                targetSystemId, targetRule.targetTenantId(), Boolean.TRUE.equals(targetRule.requiresSystemSwitch()),
                targetRule.fallbackAction());
    }

    private void validateSaveRequest(NotificationTemplateSaveRequest request) {
        if (Objects.isNull(request)) {
            throw new BusinessException(MessageErrorCode.MESSAGE_REQUIRED_FIELD, "通知模板请求不能为空");
        }
        if (!MessageTargetPolicy.hasText(request.templateCode())) {
            throw new BusinessException(MessageErrorCode.MESSAGE_REQUIRED_FIELD, "模板编码不能为空");
        }
        if (!MessageTargetPolicy.hasText(request.templateType())) {
            throw new BusinessException(MessageErrorCode.MESSAGE_REQUIRED_FIELD, "模板类型不能为空");
        }
        if (Objects.isNull(request.channels()) || request.channels().isEmpty()) {
            throw new BusinessException(MessageErrorCode.MESSAGE_REQUIRED_FIELD, "通知渠道不能为空");
        }
    }

    private List<PublishCheckItem> publishFailures(NotificationTemplateVO template) {
        try {
            MessageTargetPolicy.assertAllowed(template.targetRule().scope(), template.targetRule().targetType());
            return List.of();
        } catch (BusinessException exception) {
            return List.of(new PublishCheckItem("target_rule", "error", exception.getMessage(),
                    exception.getDisabledReason()));
        }
    }

    private List<PublishCheckItem> publishWarnings(NotificationTemplateVO template) {
        if (MessageTargetPolicy.PLATFORM_SCOPE.equals(template.scope())
                && Boolean.FALSE.equals(template.targetRule().requiresSystemSwitch())
                && "system_switch".equals(template.targetRule().targetType())) {
            return List.of(new PublishCheckItem("system_switch", "warning",
                    "平台消息跳转系统前需要建立 SystemSwitchContext",
                    "请开启 requiresSystemSwitch 并返回系统切换引导"));
        }
        return List.of();
    }

    private String scope(String scope) {
        return MessageTargetPolicy.normalizeScope(scope);
    }

    private Long requireSystemId(String systemId) {
        if (!MessageTargetPolicy.hasText(systemId)) {
            throw new BusinessException(MessageErrorCode.MESSAGE_CONTEXT_REQUIRED, "通知模板必须归属系统");
        }
        try {
            return Long.valueOf(systemId);
        } catch (NumberFormatException ex) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "系统ID格式不正确");
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(CommonErrorCode.OPS_INTERNAL_ERROR, "通知模板序列化失败");
        }
    }

    private <T> T readJson(String value, Class<T> type, T fallback) {
        if (!MessageTargetPolicy.hasText(value)) {
            return fallback;
        }
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException ex) {
            return fallback;
        }
    }

    private <T> T readJson(String value, TypeReference<T> type, T fallback) {
        if (!MessageTargetPolicy.hasText(value)) {
            return fallback;
        }
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException ex) {
            return fallback;
        }
    }
}
