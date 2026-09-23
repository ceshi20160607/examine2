package com.unique.unexamine.runtimedata.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.authorization.manage.PermissionChecker;
import com.unique.unexamine.foundation.base.entity.CoreSetting;
import com.unique.unexamine.foundation.base.service.CoreSettingBaseService;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 接收方个人视图管理；字段权限按视图自身范围解析，配置不会写入来源租户。 */
@Service
public class RuntimeListViewService {
    private static final String CATEGORY = "RUNTIME_LIST_VIEW";
    private final CoreSettingBaseService settingService;
    private final PermissionChecker permissionChecker;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;
    private final RuntimeDataService runtimeDataService;

    public RuntimeListViewService(
            CoreSettingBaseService settingService,
            PermissionChecker permissionChecker,
            AuditRecorder auditRecorder,
            ObjectMapper objectMapper, RuntimeDataService runtimeDataService) {
        this.settingService = settingService;
        this.permissionChecker = permissionChecker;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
        this.runtimeDataService = runtimeDataService;
    }

    /** 仅列出当前账户视图，并按各自记录状态、租户范围校验当前可查询字段。 */
    @Transactional(readOnly = true)
    public List<RuntimeListView> list(AuthenticatedContext context, String moduleCode, String traceId) {
        Access access = requireAccess(context, moduleCode, traceId);
        Map<String, Access> scopes = new HashMap<>();
        scopes.put("ACTIVE:ALL", access);
        String prefix = settingPrefix(context, access.moduleCode());
        return settingService.selectList(Wrappers.<CoreSetting>lambdaQuery()
                        .eq(CoreSetting::getContextType, "TENANT")
                        .eq(CoreSetting::getPlatformId, context.platformId())
                        .eq(CoreSetting::getSystemId, context.systemId())
                        .eq(CoreSetting::getTenantId, context.tenantId())
                        .eq(CoreSetting::getCategory, CATEGORY)
                        .eq(CoreSetting::getStatus, "ACTIVE")
                        .likeRight(CoreSetting::getSettingKey, prefix))
                .stream().map(setting -> {
                    RuntimeListView saved = stored(setting);
                    String lifecycle = lifecycle(saved.lifecycleState());
                    String tenant = tenantScope(saved.tenantScope());
                    Access scoped = scopes.computeIfAbsent(lifecycle + ":" + tenant,
                            ignored -> requireAccess(context, moduleCode, lifecycle, tenant, traceId));
                    return view(setting, scoped);
                })
                .sorted(Comparator.comparing(RuntimeListView::defaultView).reversed()
                        .thenComparing(RuntimeListView::updatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    /** 同一事务保存精确查询范围和默认标记；版本冲突不会覆盖其他个人视图。 */
    @Transactional
    public RuntimeListView save(
            AuthenticatedContext context, String moduleCode, String viewCode,
            SaveRuntimeListViewRequest request, String traceId) {
        Access access = requireAccess(context, moduleCode,
                lifecycle(request.lifecycleState()), tenantScope(request.tenantScope()), traceId);
        runtimeDataService.validateListQuery(access.queryFields(), request.filters(), request.sortField(), request.sortDirection());
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
        return view(current, access);
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

    /** 只改变默认标记，保留其他视图的原始条件与范围，避免失权裁剪永久丢失配置。 */
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
            RuntimeListView existing = stored(other);
            if (!existing.defaultView()) continue;
            RuntimeListView changed = new RuntimeListView(null, existing.code(), existing.name(), existing.search(),
                    existing.filters(), existing.sortField(), existing.sortDirection(), existing.visibleFieldCodes(),
                    existing.fixedFieldCodes(), existing.pageSize(), false, null, null, false,
                    lifecycle(existing.lifecycleState()), tenantScope(existing.tenantScope()));
            other.setValueJson(toJson(changed));
            if (settingService.updateById(other) != 1) {
                throw new DomainException("LIST_VIEW_VERSION_CONFLICT", "默认视图已被修改，请刷新后重试", HttpStatus.CONFLICT);
            }
        }
    }

    private Access requireAccess(AuthenticatedContext context, String moduleCode, String traceId) {
        return requireAccess(context, moduleCode, "ACTIVE", "ALL", traceId);
    }

    /** 不使用当前页面范围替代历史视图范围，尤其保留仅归档来源字段的查询能力。 */
    private Access requireAccess(AuthenticatedContext context, String moduleCode,
            String lifecycleState, String tenantScope, String traceId) {
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
        // 与实时查询共享原操作者范围、有效来源字段和脱敏限制；存储仍只属于接收方个人。
        List<RuntimeListQueryField> fields = runtimeDataService.queryFields(
                context, normalized, lifecycleState, tenantScope, traceId);
        Set<String> allowed = fields.stream().map(RuntimeListQueryField::code)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return new Access(normalized, allowed, fields);
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
                request.defaultView(), null, null, false, lifecycle(request.lifecycleState()), tenantScope(request.tenantScope()));
    }

    private void validateField(String field, Set<String> allowedFields, String usage) {
        if (field == null || !allowedFields.contains(field)) {
            throw new DomainException("LIST_VIEW_FIELD_FORBIDDEN", usage + "字段不存在、不可读或已脱敏", HttpStatus.FORBIDDEN);
        }
    }

    private RuntimeListView stored(CoreSetting setting) {
        try {
            return objectMapper.readValue(setting.getValueJson(), RuntimeListView.class);
        } catch (JsonProcessingException exception) {
            throw new DomainException("LIST_VIEW_VALUE_INVALID", "常用视图配置无法读取", HttpStatus.CONFLICT);
        }
    }

    private RuntimeListView view(CoreSetting setting, Access access) {
        RuntimeListView stored = stored(setting);
        Set<String> allowedFields = access.allowedFields();
        List<RuntimeListFilter> safeFilters = stored.filters() == null ? List.of() : stored.filters().stream()
                .filter(filter -> filter != null && allowedFields.contains(filter.fieldCode())).toList();
        List<String> safeVisible = stored.visibleFieldCodes() == null ? List.of() : stored.visibleFieldCodes().stream()
                .filter(allowedFields::contains).distinct().toList();
        List<String> safeFixed = stored.fixedFieldCodes() == null ? List.of() : stored.fixedFieldCodes().stream()
                .filter(safeVisible::contains).distinct().toList();
        String safeSort = allowedFields.contains(stored.sortField()) ? stored.sortField() : "updatedAt";
        // 失权或类型变化的历史条件不能静默变为宽查询；仅返回安全字段并明确要求重新配置。
        boolean queryInvalid = safeFilters.size() != (stored.filters() == null ? 0 : stored.filters().size())
                || !safeSort.equals(stored.sortField());
        if (!queryInvalid) {
            try {
                runtimeDataService.validateListQuery(access.queryFields(), safeFilters, safeSort, stored.sortDirection());
            } catch (DomainException invalid) {
                if (invalid.status() != HttpStatus.BAD_REQUEST && invalid.status() != HttpStatus.FORBIDDEN) throw invalid;
                queryInvalid = true;
            }
        }
        return new RuntimeListView(setting.getId(), stored.code(), stored.name(), stored.search(), safeFilters,
                safeSort, stored.sortDirection(), safeVisible, safeFixed, stored.pageSize(), stored.defaultView(),
                setting.getVersion(), setting.getUpdatedAt(), queryInvalid,
                lifecycle(stored.lifecycleState()), tenantScope(stored.tenantScope()));
    }

    /** 缺失范围仅兼容原默认值；非法已存范围不静默改成更宽查询。 */
    private String lifecycle(String value) {
        return scope(value, "ACTIVE", Set.of("ACTIVE", "ARCHIVED", "DELETED"));
    }

    private String tenantScope(String value) {
        return scope(value, "ALL", Set.of("ALL", "OWN", "SHARED"));
    }

    private String scope(String value, String fallback, Set<String> allowed) {
        if (value == null) return fallback;
        String normalized = value.strip().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new DomainException("LIST_VIEW_SCOPE_INVALID", "常用视图查询范围无效，请重新配置", HttpStatus.CONFLICT);
        }
        return normalized;
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

    private record Access(String moduleCode, Set<String> allowedFields, List<RuntimeListQueryField> queryFields) {
    }
}
