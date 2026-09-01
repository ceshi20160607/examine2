package com.unique.unexamine.runtimedata.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.authorization.manage.ChannelFieldPolicyResolver;
import com.unique.unexamine.authorization.manage.FieldAccessDecision;
import com.unique.unexamine.authorization.manage.PermissionChecker;
import com.unique.unexamine.foundation.base.entity.CoreSetting;
import com.unique.unexamine.foundation.base.service.CoreSettingBaseService;
import com.unique.unexamine.moduleconfig.manage.ModulePublicationService;
import com.unique.unexamine.moduleconfig.manage.RuntimeModuleConfiguration;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class RuntimeListViewService {
    private static final String CATEGORY = "RUNTIME_LIST_VIEW";
    private static final Set<String> BUILT_INS = Set.of("title", "recordNumber", "status", "ownerMemberId",
            "departmentId", "createdAt", "updatedAt", "dataTenantName");
    private final CoreSettingBaseService settingService;
    private final ModulePublicationService publicationService;
    private final PermissionChecker permissionChecker;
    private final ChannelFieldPolicyResolver fieldPolicyResolver;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;

    public RuntimeListViewService(
            CoreSettingBaseService settingService,
            ModulePublicationService publicationService,
            PermissionChecker permissionChecker,
            ChannelFieldPolicyResolver fieldPolicyResolver,
            AuditRecorder auditRecorder,
            ObjectMapper objectMapper) {
        this.settingService = settingService;
        this.publicationService = publicationService;
        this.permissionChecker = permissionChecker;
        this.fieldPolicyResolver = fieldPolicyResolver;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<RuntimeListView> list(AuthenticatedContext context, String moduleCode, String traceId) {
        Access access = requireAccess(context, moduleCode, traceId);
        String prefix = settingPrefix(context, access.moduleCode());
        return settingService.selectList(Wrappers.<CoreSetting>lambdaQuery()
                        .eq(CoreSetting::getContextType, "TENANT")
                        .eq(CoreSetting::getPlatformId, context.platformId())
                        .eq(CoreSetting::getSystemId, context.systemId())
                        .eq(CoreSetting::getTenantId, context.tenantId())
                        .eq(CoreSetting::getCategory, CATEGORY)
                        .eq(CoreSetting::getStatus, "ACTIVE")
                        .likeRight(CoreSetting::getSettingKey, prefix))
                .stream().map(setting -> view(setting, access.allowedFields()))
                .sorted(Comparator.comparing(RuntimeListView::defaultView).reversed()
                        .thenComparing(RuntimeListView::updatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    @Transactional
    public RuntimeListView save(
            AuthenticatedContext context, String moduleCode, String viewCode,
            SaveRuntimeListViewRequest request, String traceId) {
        Access access = requireAccess(context, moduleCode, traceId);
        String normalizedCode = normalizeViewCode(viewCode);
        RuntimeListView desired = normalizeRequest(request, normalizedCode, access.allowedFields());
        String key = settingPrefix(context, access.moduleCode()) + normalizedCode;
        CoreSetting setting = find(context, key);
        boolean created = setting == null || "DELETED".equals(setting.getStatus());
        if (setting == null) {
            if (request.expectedVersion() != null) {
                throw new DomainException("LIST_VIEW_VERSION_INVALID", "新视图不能携带历史版本", HttpStatus.CONFLICT);
            }
            setting = new CoreSetting();
            setting.setContextType("TENANT");
            setting.setPlatformId(context.platformId());
            setting.setSystemId(context.systemId());
            setting.setTenantId(context.tenantId());
            setting.setCategory(CATEGORY);
            setting.setSettingKey(key);
            setting.setValueType("JSON");
            setting.setSensitive(false);
            setting.setStatus("ACTIVE");
            setting.setValueJson(toJson(desired));
            settingService.insert(setting);
        } else if (created) {
            if (request.expectedVersion() != null) {
                throw new DomainException("LIST_VIEW_VERSION_INVALID", "重新创建的视图不能携带历史版本", HttpStatus.CONFLICT);
            }
            setting.setValueJson(toJson(desired));
            setting.setStatus("ACTIVE");
            if (settingService.updateById(setting) != 1) {
                throw new DomainException("LIST_VIEW_VERSION_CONFLICT", "视图已被修改，请刷新后重试", HttpStatus.CONFLICT);
            }
        } else {
            if (request.expectedVersion() == null || !request.expectedVersion().equals(setting.getVersion())) {
                throw new DomainException("LIST_VIEW_VERSION_CONFLICT", "视图已被修改，请刷新后重试", HttpStatus.CONFLICT);
            }
            setting.setValueJson(toJson(desired));
            setting.setStatus("ACTIVE");
            if (settingService.updateById(setting) != 1) {
                throw new DomainException("LIST_VIEW_VERSION_CONFLICT", "视图已被修改，请刷新后重试", HttpStatus.CONFLICT);
            }
        }
        if (request.defaultView()) {
            clearOtherDefaults(context, access, key);
        }
        CoreSetting current = settingService.selectById(setting.getId());
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "RUNTIME_LIST_VIEW_SAVED", "CORE_SETTING", current.getId().toString(), "SUCCESS",
                Map.of("moduleCode", access.moduleCode(), "viewCode", normalizedCode, "created", created,
                        "version", current.getVersion()));
        return view(current, access.allowedFields());
    }

    @Transactional
    public void delete(
            AuthenticatedContext context, String moduleCode, String viewCode,
            DeleteRuntimeListViewRequest request, String traceId) {
        Access access = requireAccess(context, moduleCode, traceId);
        String normalizedCode = normalizeViewCode(viewCode);
        CoreSetting setting = find(context, settingPrefix(context, access.moduleCode()) + normalizedCode);
        if (setting == null) {
            throw new DomainException("LIST_VIEW_NOT_FOUND", "常用视图不存在", HttpStatus.NOT_FOUND);
        }
        if (!request.expectedVersion().equals(setting.getVersion())) {
            throw new DomainException("LIST_VIEW_VERSION_CONFLICT", "视图已被修改，请刷新后重试", HttpStatus.CONFLICT);
        }
        setting.setStatus("DELETED");
        if (settingService.updateById(setting) != 1) {
            throw new DomainException("LIST_VIEW_VERSION_CONFLICT", "视图已被修改，请刷新后重试", HttpStatus.CONFLICT);
        }
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "RUNTIME_LIST_VIEW_DELETED", "CORE_SETTING", setting.getId().toString(), "SUCCESS",
                Map.of("moduleCode", access.moduleCode(), "viewCode", normalizedCode));
    }

    private void clearOtherDefaults(AuthenticatedContext context, Access access, String currentKey) {
        String prefix = settingPrefix(context, access.moduleCode());
        List<CoreSetting> others = settingService.selectList(Wrappers.<CoreSetting>lambdaQuery()
                .eq(CoreSetting::getContextType, "TENANT")
                .eq(CoreSetting::getPlatformId, context.platformId())
                .eq(CoreSetting::getSystemId, context.systemId())
                .eq(CoreSetting::getTenantId, context.tenantId())
                .eq(CoreSetting::getCategory, CATEGORY)
                .eq(CoreSetting::getStatus, "ACTIVE")
                .likeRight(CoreSetting::getSettingKey, prefix)
                .ne(CoreSetting::getSettingKey, currentKey));
        for (CoreSetting other : others) {
            RuntimeListView existing = view(other, access.allowedFields());
            if (!existing.defaultView()) continue;
            RuntimeListView changed = new RuntimeListView(null, existing.code(), existing.name(), existing.search(),
                    existing.filters(), existing.sortField(), existing.sortDirection(), existing.visibleFieldCodes(),
                    existing.fixedFieldCodes(), existing.pageSize(), false, null, null);
            other.setValueJson(toJson(changed));
            if (settingService.updateById(other) != 1) {
                throw new DomainException("LIST_VIEW_VERSION_CONFLICT", "默认视图已被修改，请刷新后重试", HttpStatus.CONFLICT);
            }
        }
    }

    private Access requireAccess(AuthenticatedContext context, String moduleCode, String traceId) {
        if (context.systemId() == null || context.tenantId() == null || context.accountId() == null) {
            throw new DomainException("SYSTEM_CONTEXT_REQUIRED", "请先进入系统", HttpStatus.BAD_REQUEST);
        }
        String normalized = moduleCode == null ? "" : moduleCode.strip().toLowerCase(Locale.ROOT);
        if (!permissionChecker.allows(context, "MODULE", normalized, "LIST")) {
            auditRecorder.recordPermissionDenied(traceId, context.accountId(), context.systemId(), context.tenantId(),
                    context.memberId(), "MODULE:" + normalized + ":LIST",
                    Map.of("resource", "MODULE:" + normalized, "action", "LIST", "channel", "PAGE"));
            throw new DomainException("PERMISSION_DENIED", "没有该模块列表权限", HttpStatus.FORBIDDEN);
        }
        RuntimeModuleConfiguration configuration = publicationService.published(context, normalized);
        List<String> fieldCodes = new ArrayList<>();
        configuration.configuration().path("fields").forEach(field -> fieldCodes.add(field.path("code").asText()));
        Map<String, FieldAccessDecision> decisions = fieldPolicyResolver.resolveIntersection(
                context, normalized, "LIST", List.of("PAGE"), fieldCodes);
        Set<String> allowed = new LinkedHashSet<>(BUILT_INS);
        decisions.forEach((code, decision) -> {
            if (decision.readable() && (decision.maskStrategy() == null || decision.maskStrategy().isBlank())) {
                allowed.add(code);
            }
        });
        return new Access(normalized, allowed);
    }

    private RuntimeListView normalizeRequest(
            SaveRuntimeListViewRequest request, String code, Set<String> allowedFields) {
        validateField(request.sortField(), allowedFields, "排序");
        List<RuntimeListFilter> filters = request.filters().stream().map(filter -> {
            validateField(filter.fieldCode(), allowedFields, "筛选");
            String operator = filter.operator() == null ? "" : filter.operator().strip().toUpperCase(Locale.ROOT);
            if (!Set.of("EQ", "NE", "CONTAINS", "GT", "GTE", "LT", "LTE", "EMPTY", "NOT_EMPTY").contains(operator)) {
                throw new DomainException("LIST_VIEW_FILTER_INVALID", "视图包含不支持的筛选运算符", HttpStatus.BAD_REQUEST);
            }
            return new RuntimeListFilter(filter.fieldCode(), operator, filter.value());
        }).toList();
        List<String> visible = request.visibleFieldCodes().stream().distinct().peek(field ->
                validateField(field, allowedFields, "显示列")).toList();
        List<String> fixed = request.fixedFieldCodes().stream().distinct().peek(field ->
                validateField(field, allowedFields, "固定列")).toList();
        if (!visible.containsAll(fixed)) {
            throw new DomainException("LIST_VIEW_FIXED_FIELD_INVALID", "固定列必须同时属于显示列", HttpStatus.BAD_REQUEST);
        }
        return new RuntimeListView(null, code, request.name().strip(),
                request.search() == null ? "" : request.search().strip(), filters, request.sortField().strip(),
                request.sortDirection().strip().toUpperCase(Locale.ROOT), visible, fixed, request.pageSize(),
                request.defaultView(), null, null);
    }

    private void validateField(String field, Set<String> allowedFields, String usage) {
        if (field == null || !allowedFields.contains(field)) {
            throw new DomainException("LIST_VIEW_FIELD_FORBIDDEN", usage + "字段不存在、不可读或已脱敏", HttpStatus.FORBIDDEN);
        }
    }

    private RuntimeListView view(CoreSetting setting, Set<String> allowedFields) {
        RuntimeListView stored;
        try {
            stored = objectMapper.readValue(setting.getValueJson(), RuntimeListView.class);
        } catch (JsonProcessingException exception) {
            throw new DomainException("LIST_VIEW_VALUE_INVALID", "常用视图配置无法读取", HttpStatus.CONFLICT);
        }
        List<RuntimeListFilter> safeFilters = stored.filters() == null ? List.of() : stored.filters().stream()
                .filter(filter -> filter != null && allowedFields.contains(filter.fieldCode())).toList();
        List<String> safeVisible = stored.visibleFieldCodes() == null ? List.of() : stored.visibleFieldCodes().stream()
                .filter(allowedFields::contains).distinct().toList();
        List<String> safeFixed = stored.fixedFieldCodes() == null ? List.of() : stored.fixedFieldCodes().stream()
                .filter(safeVisible::contains).distinct().toList();
        String safeSort = allowedFields.contains(stored.sortField()) ? stored.sortField() : "updatedAt";
        return new RuntimeListView(setting.getId(), stored.code(), stored.name(), stored.search(), safeFilters,
                safeSort, stored.sortDirection(), safeVisible, safeFixed, stored.pageSize(), stored.defaultView(),
                setting.getVersion(), setting.getUpdatedAt());
    }

    private CoreSetting find(AuthenticatedContext context, String key) {
        return settingService.selectList(Wrappers.<CoreSetting>lambdaQuery()
                        .eq(CoreSetting::getContextType, "TENANT")
                        .eq(CoreSetting::getPlatformId, context.platformId())
                        .eq(CoreSetting::getSystemId, context.systemId())
                        .eq(CoreSetting::getTenantId, context.tenantId())
                        .eq(CoreSetting::getCategory, CATEGORY)
                        .eq(CoreSetting::getSettingKey, key))
                .stream().findFirst().orElse(null);
    }

    private String settingPrefix(AuthenticatedContext context, String moduleCode) {
        return context.accountId() + ":" + moduleCode + ":";
    }

    private String normalizeViewCode(String viewCode) {
        String normalized = viewCode == null ? "" : viewCode.strip().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z][a-z0-9_-]{0,39}")) {
            throw new DomainException("LIST_VIEW_CODE_INVALID", "视图编码须以字母开头且只含小写字母、数字、下划线或横线",
                    HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String toJson(RuntimeListView view) {
        try {
            return objectMapper.writeValueAsString(view);
        } catch (JsonProcessingException exception) {
            throw new DomainException("LIST_VIEW_VALUE_INVALID", "常用视图配置无法保存", HttpStatus.BAD_REQUEST);
        }
    }

    private record Access(String moduleCode, Set<String> allowedFields) {
    }
}
