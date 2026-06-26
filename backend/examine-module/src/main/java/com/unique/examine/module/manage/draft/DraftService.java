package com.unique.examine.module.manage.draft;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.context.RequestContext;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.error.CommonErrorCode;
import com.unique.examine.module.base.entity.ModuleDynamicDraft;
import com.unique.examine.module.base.service.ModuleDynamicDraftBaseService;
import com.unique.examine.module.manage.common.ModuleSystemContextResolver;
import com.unique.examine.module.manage.common.ModuleSystemContextResolver.ModuleSystemContext;
import com.unique.examine.module.manage.draft.DraftModels.DraftSaveRequest;
import com.unique.examine.module.manage.draft.DraftModels.DraftVO;
import com.unique.examine.module.manage.draft.DraftModels.DraftValidationIssue;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 表单草稿持久化服务。
 */
@Service
public class DraftService {

    private final ModuleSystemContextResolver contextResolver;
    private final ModuleDynamicDraftBaseService draftBaseService;
    private final ObjectMapper objectMapper;

    public DraftService(ModuleSystemContextResolver contextResolver,
                        ModuleDynamicDraftBaseService draftBaseService,
                        ObjectMapper objectMapper) {
        this.contextResolver = contextResolver;
        this.draftBaseService = draftBaseService;
        this.objectMapper = objectMapper;
    }

    /**
     * 保存草稿并写入动态草稿表，后续重启后仍可恢复。
     *
     * @param systemId system id
     * @param moduleId module id
     * @param request draft save request
     * @param idempotencyKey idempotency key
     * @return saved draft view
     */
    @Transactional(rollbackFor = Exception.class)
    public DraftVO save(String systemId, String moduleId, DraftSaveRequest request, String idempotencyKey) {
        ModuleSystemContext context = contextResolver.resolve(systemId);
        Long parsedModuleId = contextResolver.parseRequiredId(moduleId, "模块ID格式不正确");
        RequestContext requestContext = RequestContext.current();
        String draftNo = safeText(Objects.isNull(request) ? null : request.draftId(),
                "draft_" + shortTrace(requestContext.traceId()));
        DraftPayload payload = new DraftPayload(Objects.isNull(request) ? null : request.recordId(),
                fieldValues(request), childRows(request), attachmentIds(request), validationIssues(request),
                context.permissionVersion(), safeText(idempotencyKey, "idem_draft_" + shortTrace(requestContext.traceId())),
                requestContext.traceId(), auditLogId(requestContext));

        ModuleDynamicDraft draft = draftByNo(draftNo);
        if (Objects.nonNull(draft)) {
            ensureOwnedByCurrentContext(draft, context, parsedModuleId);
        } else {
            draft = new ModuleDynamicDraft();
            draft.setSystemId(context.systemId());
            draft.setTenantId(context.tenantId());
            draft.setModuleId(parsedModuleId);
            draft.setDraftNo(draftNo);
            draft.setCreatedBy(context.systemMemberId());
        }
        draft.setDraftPayload(toJson(payload));
        draft.setUpdatedAt(LocalDateTime.now());
        draftBaseService.saveEntity(draft);
        return toVO(draft, payload);
    }

    /**
     * 获取草稿详情；未命中时返回可恢复的空草稿视图，但不写入数据库。
     *
     * @param systemId system id
     * @param moduleId module id
     * @param draftId draft no
     * @return draft view
     */
    public DraftVO get(String systemId, String moduleId, String draftId) {
        ModuleSystemContext context = contextResolver.resolve(systemId);
        Long parsedModuleId = contextResolver.parseRequiredId(moduleId, "模块ID格式不正确");
        ModuleDynamicDraft draft = draftByNo(draftId);
        if (Objects.isNull(draft)) {
            return emptyDraft(context, parsedModuleId, draftId);
        }
        ensureOwnedByCurrentContext(draft, context, parsedModuleId);
        return toVO(draft, readPayload(draft.getDraftPayload(), context));
    }

    private ModuleDynamicDraft draftByNo(String draftNo) {
        if (!StringUtils.hasText(draftNo)) {
            return null;
        }
        return draftBaseService.getOne(new LambdaQueryWrapper<ModuleDynamicDraft>()
                .eq(ModuleDynamicDraft::getDraftNo, draftNo)
                .last("LIMIT 1"), false);
    }

    private void ensureOwnedByCurrentContext(ModuleDynamicDraft draft, ModuleSystemContext context, Long moduleId) {
        if (!Objects.equals(draft.getSystemId(), context.systemId())
                || !Objects.equals(draft.getTenantId(), context.tenantId())
                || !Objects.equals(draft.getModuleId(), moduleId)
                || !Objects.equals(draft.getCreatedBy(), context.systemMemberId())) {
            throw new BusinessException(CommonErrorCode.PERMISSION_DENIED, "草稿不属于当前系统成员上下文");
        }
    }

    private DraftVO toVO(ModuleDynamicDraft draft, DraftPayload payload) {
        return new DraftVO(draft.getDraftNo(), String.valueOf(draft.getSystemId()),
                String.valueOf(draft.getTenantId()), String.valueOf(draft.getModuleId()),
                payload.recordId(), safeMap(payload.fieldValues()), safeChildRows(payload.childRows()),
                safeList(payload.attachmentIds()), safeList(payload.validationIssues()),
                payload.permissionSnapshotVersion(), payload.idempotencyKey(), payload.traceId(),
                payload.auditLogId(), String.valueOf(draft.getCreatedBy()), draft.getUpdatedAt());
    }

    private DraftVO emptyDraft(ModuleSystemContext context, Long moduleId, String draftId) {
        RequestContext requestContext = RequestContext.current();
        return new DraftVO(draftId, String.valueOf(context.systemId()), String.valueOf(context.tenantId()),
                String.valueOf(moduleId), null, Map.of("recordStatus", "DRAFT"), Map.of(), List.of(),
                List.of(new DraftValidationIssue("draftId", "草稿不存在或已过期，请重新保存后再恢复。", true)),
                context.permissionVersion(), null, requestContext.traceId(), auditLogId(requestContext),
                String.valueOf(context.systemMemberId()), LocalDateTime.now());
    }

    private Map<String, Object> fieldValues(DraftSaveRequest request) {
        return Objects.isNull(request) || Objects.isNull(request.fieldValues())
                ? Map.of("status", "DRAFT") : request.fieldValues();
    }

    private Map<String, List<Map<String, Object>>> childRows(DraftSaveRequest request) {
        return Objects.isNull(request) || Objects.isNull(request.childRows()) ? Map.of() : request.childRows();
    }

    private List<String> attachmentIds(DraftSaveRequest request) {
        return Objects.isNull(request) || Objects.isNull(request.attachmentIds()) ? List.of() : request.attachmentIds();
    }

    private List<DraftValidationIssue> validationIssues(DraftSaveRequest request) {
        if (Objects.isNull(request) || Objects.isNull(request.fieldValues())
                || request.fieldValues().isEmpty()) {
            return List.of(new DraftValidationIssue("fieldValues", "业务字段为空，提交前需要补充必填字段。", true));
        }
        return List.of();
    }

    private DraftPayload readPayload(String payload, ModuleSystemContext context) {
        if (!StringUtils.hasText(payload)) {
            RequestContext requestContext = RequestContext.current();
            return new DraftPayload(null, Map.of("recordStatus", "DRAFT"), Map.of(), List.of(), List.of(),
                    context.permissionVersion(), null, requestContext.traceId(), auditLogId(requestContext));
        }
        try {
            return objectMapper.readValue(payload, DraftPayload.class);
        } catch (Exception ex) {
            throw new BusinessException(CommonErrorCode.OPS_INTERNAL_ERROR, "草稿内容解析失败");
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BusinessException(CommonErrorCode.OPS_INTERNAL_ERROR, "草稿内容序列化失败");
        }
    }

    private String auditLogId(RequestContext context) {
        return StringUtils.hasText(context.auditLogId()) ? context.auditLogId() : "aud_" + context.traceId();
    }

    private String shortTrace(String traceId) {
        String value = safeText(traceId, "trace");
        return value.length() <= 8 ? value : value.substring(0, 8);
    }

    private String safeText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private Map<String, Object> safeMap(Map<String, Object> value) {
        return Objects.isNull(value) ? Map.of() : value;
    }

    private Map<String, List<Map<String, Object>>> safeChildRows(Map<String, List<Map<String, Object>>> value) {
        return Objects.isNull(value) ? Map.of() : value;
    }

    private <T> List<T> safeList(List<T> value) {
        return Objects.isNull(value) ? List.of() : value;
    }

    private record DraftPayload(String recordId, Map<String, Object> fieldValues,
                                Map<String, List<Map<String, Object>>> childRows, List<String> attachmentIds,
                                List<DraftValidationIssue> validationIssues, String permissionSnapshotVersion,
                                String idempotencyKey, String traceId, String auditLogId) {
    }
}
