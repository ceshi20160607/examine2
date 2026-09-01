package com.unique.unexamine.foundation.manage.configuration;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.foundation.base.entity.CoreSetting;
import com.unique.unexamine.foundation.base.service.CoreSettingBaseService;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ContextSettingManagementService {
    private static final Map<String, String> PLATFORM_CATEGORIES = categories(
            "PLATFORM_INFO", "平台信息",
            "ORGANIZATION", "组织架构",
            "AUTHORIZATION", "角色权限",
            "DICTIONARY", "数据字典",
            "SYSTEM", "系统配置",
            "BUSINESS_PARAMETER", "其他业务参数配置",
            "FLOW", "Flow 配置",
            "APPLICATION", "应用配置",
            "AI", "AI 配置",
            "AUDIT", "审计日志",
            "OPERATIONS", "运维配置");
    private static final Map<String, String> SYSTEM_CATEGORIES = categories(
            "SYSTEM_INFO", "系统信息",
            "ORGANIZATION", "组织架构",
            "AUTHORIZATION", "角色权限",
            "DICTIONARY", "数据字典",
            "TEMPLATE", "模板配置（模块信息、模块字段、模块动作、模块 Flow、模块应用）",
            "BUSINESS_PARAMETER", "其他业务参数配置",
            "FLOW", "Flow 配置",
            "APPLICATION", "应用配置",
            "AI", "AI 配置",
            "AUDIT", "审计日志",
            "OPERATIONS", "运维配置");

    private final CoreSettingBaseService settingService;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;

    public ContextSettingManagementService(
            CoreSettingBaseService settingService,
            AuditRecorder auditRecorder,
            ObjectMapper objectMapper) {
        this.settingService = settingService;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
    }

    public List<ContextSettingCategory> platformCatalog() {
        return catalog(PLATFORM_CATEGORIES);
    }

    public List<ContextSettingCategory> systemCatalog() {
        return catalog(SYSTEM_CATEGORIES);
    }

    @Transactional(readOnly = true)
    public List<ContextSettingView> listPlatform(AuthenticatedContext context) {
        requirePlatformContext(context);
        return settingService.selectList(Wrappers.<CoreSetting>lambdaQuery()
                        .eq(CoreSetting::getContextType, "PLATFORM")
                        .eq(CoreSetting::getPlatformId, context.platformId())
                        .isNull(CoreSetting::getSystemId)
                        .isNull(CoreSetting::getTenantId))
                .stream().sorted(settingOrder()).map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public List<ContextSettingView> listSystem(AuthenticatedContext context) {
        requireSystemContext(context);
        return settingService.selectList(Wrappers.<CoreSetting>lambdaQuery()
                        .eq(CoreSetting::getContextType, "SYSTEM")
                        .eq(CoreSetting::getPlatformId, context.platformId())
                        .eq(CoreSetting::getSystemId, context.systemId())
                        .isNull(CoreSetting::getTenantId))
                .stream().sorted(settingOrder()).map(this::view).toList();
    }

    @Transactional
    public ContextSettingView savePlatform(
            AuthenticatedContext context,
            SaveContextSettingRequest request,
            String traceId) {
        requirePlatformContext(context);
        return save(context, "PLATFORM", PLATFORM_CATEGORIES.keySet(), request, traceId);
    }

    @Transactional
    public ContextSettingView saveSystem(
            AuthenticatedContext context,
            SaveContextSettingRequest request,
            String traceId) {
        requireSystemContext(context);
        return save(context, "SYSTEM", SYSTEM_CATEGORIES.keySet(), request, traceId);
    }

    private ContextSettingView save(
            AuthenticatedContext context,
            String contextType,
            Set<String> allowedCategories,
            SaveContextSettingRequest request,
            String traceId) {
        String category = request.category().strip().toUpperCase(Locale.ROOT);
        if (!allowedCategories.contains(category)) {
            throw new DomainException("SETTING_CATEGORY_NOT_ALLOWED", "该配置类别不属于当前后台", HttpStatus.BAD_REQUEST);
        }
        String key = request.settingKey().strip().toLowerCase(Locale.ROOT);
        List<CoreSetting> matches = find(context, contextType, category, key);
        CoreSetting setting;
        boolean created = matches.isEmpty();
        if (created) {
            if (request.expectedVersion() != null) {
                throw new DomainException("SETTING_VERSION_INVALID", "新配置不能指定历史版本", HttpStatus.CONFLICT);
            }
            setting = new CoreSetting();
            setting.setContextType(contextType);
            setting.setPlatformId(context.platformId());
            setting.setSystemId("SYSTEM".equals(contextType) ? context.systemId() : null);
            setting.setTenantId(null);
            setting.setCategory(category);
            setting.setSettingKey(key);
            setting.setValueType(valueType(request.value()));
            setting.setValueJson(toJson(request.value()));
            setting.setSensitive(request.sensitive());
            setting.setStatus("ACTIVE");
            settingService.insert(setting);
        } else {
            setting = matches.getFirst();
            if (request.expectedVersion() == null || !request.expectedVersion().equals(setting.getVersion())) {
                throw new DomainException("CONCURRENT_MODIFICATION", "配置已被其他操作修改，请刷新后重试", HttpStatus.CONFLICT);
            }
            setting.setValueType(valueType(request.value()));
            setting.setValueJson(toJson(request.value()));
            setting.setSensitive(request.sensitive());
            setting.setStatus("ACTIVE");
            if (settingService.updateById(setting) != 1) {
                throw new DomainException("CONCURRENT_MODIFICATION", "配置已被其他操作修改，请刷新后重试", HttpStatus.CONFLICT);
            }
        }
        CoreSetting current = settingService.selectById(setting.getId());
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "PLATFORM".equals(contextType) ? "PLATFORM_SETTING_SAVED" : "SYSTEM_SETTING_SAVED",
                "CORE_SETTING", current.getId().toString(), "SUCCESS",
                Map.of("category", category, "settingKey", key, "version", current.getVersion(),
                        "created", created, "sensitive", request.sensitive()));
        return view(current);
    }

    private List<CoreSetting> find(
            AuthenticatedContext context,
            String contextType,
            String category,
            String key) {
        var query = Wrappers.<CoreSetting>lambdaQuery()
                .eq(CoreSetting::getContextType, contextType)
                .eq(CoreSetting::getPlatformId, context.platformId())
                .eq(CoreSetting::getCategory, category)
                .eq(CoreSetting::getSettingKey, key)
                .isNull(CoreSetting::getTenantId);
        if ("SYSTEM".equals(contextType)) {
            query.eq(CoreSetting::getSystemId, context.systemId());
        } else {
            query.isNull(CoreSetting::getSystemId);
        }
        return settingService.selectList(query);
    }

    private void requirePlatformContext(AuthenticatedContext context) {
        if (context.platformId() == null || context.systemId() != null || context.tenantId() != null) {
            throw new DomainException("PLATFORM_CONTEXT_REQUIRED", "请切换到平台后台", HttpStatus.BAD_REQUEST);
        }
    }

    private void requireSystemContext(AuthenticatedContext context) {
        if (context.platformId() == null || context.systemId() == null || context.tenantId() == null
                || context.memberId() == null || context.tenantMemberId() == null) {
            throw new DomainException("SYSTEM_CONTEXT_REQUIRED", "请先进入系统后台", HttpStatus.BAD_REQUEST);
        }
    }

    private ContextSettingView view(CoreSetting setting) {
        return new ContextSettingView(setting.getId(), setting.getContextType(), setting.getPlatformId(),
                setting.getSystemId(), setting.getCategory(), setting.getSettingKey(), setting.getValueType(),
                readValue(setting.getValueJson()), Boolean.TRUE.equals(setting.getSensitive()), setting.getStatus(),
                setting.getVersion(), setting.getUpdatedAt());
    }

    private Comparator<CoreSetting> settingOrder() {
        return Comparator.comparing(CoreSetting::getCategory).thenComparing(CoreSetting::getSettingKey);
    }

    private List<ContextSettingCategory> catalog(Map<String, String> categories) {
        return categories.entrySet().stream().map(entry -> new ContextSettingCategory(entry.getKey(), entry.getValue())).toList();
    }

    private String valueType(Object value) {
        if (value instanceof String) return "STRING";
        if (value instanceof Boolean) return "BOOLEAN";
        if (value instanceof Number) return "NUMBER";
        return "JSON";
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new DomainException("SETTING_VALUE_INVALID", "配置值无法保存", HttpStatus.BAD_REQUEST);
        }
    }

    private Object readValue(String json) {
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (JsonProcessingException exception) {
            throw new DomainException("SETTING_VALUE_INVALID", "配置值无法读取", HttpStatus.CONFLICT);
        }
    }

    private static Map<String, String> categories(String... values) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            result.put(values[index], values[index + 1]);
        }
        return Collections.unmodifiableMap(result);
    }
}
