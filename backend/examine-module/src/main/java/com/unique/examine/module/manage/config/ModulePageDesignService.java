package com.unique.examine.module.manage.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.context.RequestContext;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.error.CommonErrorCode;
import com.unique.examine.module.base.entity.ModuleDefinition;
import com.unique.examine.module.base.entity.ModuleFieldDefinition;
import com.unique.examine.module.base.entity.ModulePublishVersion;
import com.unique.examine.module.base.entity.ModuleWorkConfig;
import com.unique.examine.module.base.service.ModuleDefinitionBaseService;
import com.unique.examine.module.base.service.ModuleFieldDefinitionBaseService;
import com.unique.examine.module.base.service.ModulePublishVersionBaseService;
import com.unique.examine.module.base.service.ModuleWorkConfigBaseService;
import com.unique.examine.module.manage.common.ModuleSystemContextResolver;
import com.unique.examine.module.manage.common.ModuleSystemContextResolver.ModuleSystemContext;
import com.unique.examine.module.manage.config.ModuleConfigModels.ImpactRef;
import com.unique.examine.module.manage.config.ModuleConfigModels.PublishCheckItem;
import com.unique.examine.module.manage.config.ModuleConfigModels.PublishCheckResultVO;
import com.unique.examine.module.manage.config.ModuleConfigModels.PublishRequest;
import com.unique.examine.module.manage.config.ModuleConfigModels.PublishResult;
import com.unique.examine.module.manage.config.ModulePageDesignModels.PageComponentConfig;
import com.unique.examine.module.manage.config.ModulePageDesignModels.PageDesignerSaveRequest;
import com.unique.examine.module.manage.config.ModulePageDesignModels.PageDesignerVO;
import com.unique.examine.module.manage.config.ModulePageDesignModels.PageSchemaFieldVO;
import com.unique.examine.module.manage.config.ModulePageDesignModels.PageSchemaVO;
import com.unique.examine.plat.manage.permission.PermissionModels.EffectivePermissionSnapshot;
import com.unique.examine.plat.manage.permission.PermissionService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * System module page designer backed by persisted module configuration.
 */
@Service
public class ModulePageDesignService {

    private static final int ENABLED = 1;
    private static final int DELETED_NO = 0;
    private static final String DRAFT = "DRAFT";
    private static final String PUBLISHED = "PUBLISHED";
    private static final String PAGE_CONFIG_PREFIX = "PAGE:";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ModuleSystemContextResolver contextResolver;
    private final ModuleDefinitionBaseService moduleBaseService;
    private final ModuleFieldDefinitionBaseService fieldBaseService;
    private final ModuleWorkConfigBaseService workConfigBaseService;
    private final ModulePublishVersionBaseService publishVersionBaseService;
    private final PermissionService permissionService;
    private final ObjectMapper objectMapper;

    public ModulePageDesignService(ModuleSystemContextResolver contextResolver,
                                   ModuleDefinitionBaseService moduleBaseService,
                                   ModuleFieldDefinitionBaseService fieldBaseService,
                                   ModuleWorkConfigBaseService workConfigBaseService,
                                   ModulePublishVersionBaseService publishVersionBaseService,
                                   PermissionService permissionService,
                                   ObjectMapper objectMapper) {
        this.contextResolver = contextResolver;
        this.moduleBaseService = moduleBaseService;
        this.fieldBaseService = fieldBaseService;
        this.workConfigBaseService = workConfigBaseService;
        this.publishVersionBaseService = publishVersionBaseService;
        this.permissionService = permissionService;
        this.objectMapper = objectMapper;
    }

    public List<PageDesignerVO> pages(String systemId, String moduleId) {
        ModuleSystemContext context = contextResolver.resolve(systemId);
        ModuleDefinition module = requireModule(context, moduleId);
        return workConfigBaseService.list(new LambdaQueryWrapper<ModuleWorkConfig>()
                        .eq(ModuleWorkConfig::getSystemId, context.systemId())
                        .eq(ModuleWorkConfig::getTenantId, context.tenantId())
                        .likeRight(ModuleWorkConfig::getConfigType, configPrefix(module.getId()))
                        .orderByDesc(ModuleWorkConfig::getUpdatedAt)
                        .orderByAsc(ModuleWorkConfig::getId))
                .stream()
                .map(config -> toVO(context, module, config, false))
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public PageDesignerVO savePage(String systemId, String moduleId, PageDesignerSaveRequest request) {
        ModuleSystemContext context = contextResolver.resolve(systemId);
        ModuleDefinition module = requireModule(context, moduleId);
        requireText(request == null ? null : request.pageCode(), "Page code is required.");
        requireText(request.pageName(), "Page name is required.");
        List<PageComponentConfig> components = request.components() == null ? List.of() : request.components();
        ModuleWorkConfig config = findConfig(context, module.getId(), request.pageCode());
        if (Objects.isNull(config)) {
            config = new ModuleWorkConfig();
            config.setSystemId(context.systemId());
            config.setTenantId(context.tenantId());
            config.setConfigType(configType(module.getId(), request.pageCode()));
            config.setPublishedVersion("page_draft");
        }
        config.setFieldList(toJson(pagePayload(request, components)));
        config.setPublishStatus(DRAFT);
        config.setUpdatedAt(LocalDateTime.now());
        saveOrUpdate(config);
        return toVO(context, module, config, false);
    }

    public PublishCheckResultVO publishCheck(String systemId, String moduleId, String pageCode) {
        ModuleSystemContext context = contextResolver.resolve(systemId);
        ModuleDefinition module = requireModule(context, moduleId);
        ModuleWorkConfig config = requirePageConfig(context, module.getId(), pageCode);
        Map<String, Object> payload = readPayload(config.getFieldList());
        List<PageComponentConfig> components = components(payload);
        List<PublishCheckItem> failures = pageFailures(module, payload, components);
        List<PublishCheckItem> warnings = pageWarnings(module, components);
        return new PublishCheckResultVO(failures.isEmpty(), failures, warnings,
                List.of(new ImpactRef("RUNTIME_PAGE", String.valueOf(config.getId()), pageName(payload),
                                "REFRESH_PAGE_SCHEMA"),
                        new ImpactRef("RUNTIME_MODULE", String.valueOf(module.getId()), module.getModuleName(),
                                "REFRESH_NAVIGATION")),
                RequestContext.current().traceId());
    }

    @Transactional(rollbackFor = Exception.class)
    public PublishResult publishPage(String systemId, String moduleId, String pageCode, PublishRequest request) {
        ModuleSystemContext context = contextResolver.resolve(systemId);
        ModuleDefinition module = requireModule(context, moduleId);
        ModuleWorkConfig config = requirePageConfig(context, module.getId(), pageCode);
        PublishCheckResultVO checkResult = publishCheck(systemId, moduleId, pageCode);
        if (!checkResult.passed()) {
            String message = checkResult.failureItems().stream()
                    .findFirst()
                    .map(PublishCheckItem::message)
                    .orElse("Page publish check failed.");
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, message);
        }
        String version = "PAGE_v" + System.currentTimeMillis();
        config.setCardFields(config.getFieldList());
        config.setPublishStatus(PUBLISHED);
        config.setPublishedVersion(version);
        config.setUpdatedAt(LocalDateTime.now());
        workConfigBaseService.updateById(config);
        savePublishVersion(context, config, version);
        RequestContext requestContext = RequestContext.current();
        return new PublishResult("PUBLISHED_MODULE_PAGE", String.valueOf(config.getId()), version,
                requestContext.traceId(), "aud_" + requestContext.traceId(), null, LocalDateTime.now());
    }

    public PageDesignerVO runtimePage(String systemId, String moduleId, String pageCode) {
        ModuleSystemContext context = contextResolver.resolve(systemId);
        ModuleDefinition module = requireModule(context, moduleId);
        ModuleWorkConfig config = requirePageConfig(context, module.getId(), pageCode);
        if (!PUBLISHED.equals(config.getPublishStatus()) || !StringUtils.hasText(config.getCardFields())) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "Page is not published.");
        }
        return toVO(context, module, config, true);
    }

    public PageSchemaVO pageSchema(String systemId, String moduleId, String pageCode, String snapshot) {
        ModuleSystemContext context = contextResolver.resolve(systemId);
        ModuleDefinition module = requireModule(context, moduleId);
        ModuleWorkConfig config = requirePageConfig(context, module.getId(), pageCode);
        boolean publishedSnapshot = "published".equalsIgnoreCase(snapshot);
        return toSchemaVO(context, module, config, publishedSnapshot);
    }

    public PageSchemaVO runtimePageSchema(String systemId, String moduleId, String pageCode) {
        ModuleSystemContext context = contextResolver.resolve(systemId);
        ModuleDefinition module = requireModule(context, moduleId);
        ModuleWorkConfig config = requirePageConfig(context, module.getId(), pageCode);
        return toSchemaVO(context, module, config, true);
    }

    private List<PublishCheckItem> pageFailures(ModuleDefinition module, Map<String, Object> payload,
                                                List<PageComponentConfig> components) {
        if (components.isEmpty()) {
            return List.of(new PublishCheckItem("PAGE_COMPONENT_EMPTY", "Page components", "ERROR",
                    "MODULE_PAGE", String.valueOf(module.getId()), "Page must contain at least one component.",
                    "Add list, form, detail, chart, or shortcut components."));
        }
        Set<String> fieldCodes = fieldCodes(module.getId());
        List<String> invalidFields = components.stream()
                .map(PageComponentConfig::boundFieldCode)
                .filter(StringUtils::hasText)
                .filter(fieldCode -> !fieldCodes.contains(fieldCode))
                .distinct()
                .toList();
        if (!invalidFields.isEmpty()) {
            return List.of(new PublishCheckItem("PAGE_BOUND_FIELD_MISSING", "Bound fields", "ERROR",
                    "MODULE_PAGE", String.valueOf(module.getId()),
                    "Page references missing fields: " + String.join(",", invalidFields),
                    "Remove missing field bindings or create the fields before publishing."));
        }
        if (!StringUtils.hasText(String.valueOf(payload.getOrDefault("route", "")))) {
            return List.of(new PublishCheckItem("PAGE_ROUTE_EMPTY", "Runtime route", "ERROR",
                    "MODULE_PAGE", String.valueOf(module.getId()), "Page route is required.",
                    "Set a runtime route before publishing."));
        }
        return List.of();
    }

    private List<PublishCheckItem> pageWarnings(ModuleDefinition module, List<PageComponentConfig> components) {
        boolean hasPreviewComponent = components.stream()
                .map(PageComponentConfig::componentType)
                .filter(StringUtils::hasText)
                .anyMatch(type -> Set.of("LIST", "FORM", "DETAIL", "CHART", "SHORTCUT").contains(type));
        if (!hasPreviewComponent) {
            return List.of(new PublishCheckItem("PAGE_PREVIEW_WEAK", "Preview", "WARNING",
                    "MODULE_PAGE", String.valueOf(module.getId()),
                    "Page has no standard visible preview component.", "Add a list, form, detail, chart, or shortcut."));
        }
        return List.of();
    }

    private Map<String, Object> pagePayload(PageDesignerSaveRequest request, List<PageComponentConfig> components) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("pageCode", request.pageCode());
        payload.put("pageName", request.pageName());
        payload.put("pageType", safeText(request.pageType(), "MODULE_LIST"));
        payload.put("route", safeText(request.route(), "/modules/{moduleId}"));
        payload.put("layoutMode", safeText(request.layoutMode(), "left-list-right-detail"));
        payload.put("components", components);
        payload.put("visibleRoleIds", request.visibleRoleIds() == null ? List.of() : request.visibleRoleIds());
        payload.put("changeReason", safeText(request.changeReason(), "page designer save"));
        return payload;
    }

    private PageDesignerVO toVO(ModuleSystemContext context, ModuleDefinition module, ModuleWorkConfig config,
                                boolean publishedSnapshot) {
        Map<String, Object> payload = readPayload(publishedSnapshot ? config.getCardFields() : config.getFieldList());
        return new PageDesignerVO(String.valueOf(config.getId()), String.valueOf(config.getSystemId()),
                String.valueOf(config.getTenantId()), String.valueOf(module.getId()), module.getModuleCode(),
                pageCode(payload, config), pageName(payload), String.valueOf(payload.getOrDefault("pageType", "MODULE_LIST")),
                String.valueOf(payload.getOrDefault("route", "/modules/" + module.getId())),
                String.valueOf(payload.getOrDefault("layoutMode", "left-list-right-detail")), components(payload),
                stringList(payload.get("visibleRoleIds")), config.getPublishStatus(), config.getPublishedVersion(),
                context.permissionSnapshotId(), RequestContext.current().traceId(), config.getUpdatedAt());
    }

    private PageSchemaVO toSchemaVO(ModuleSystemContext context, ModuleDefinition module, ModuleWorkConfig config,
                                    boolean publishedSnapshot) {
        String payloadJson = publishedSnapshot ? config.getCardFields() : config.getFieldList();
        if (publishedSnapshot && (!PUBLISHED.equals(config.getPublishStatus()) || !StringUtils.hasText(payloadJson))) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "Page is not published.");
        }
        Map<String, Object> payload = readPayload(payloadJson);
        EffectivePermissionSnapshot permission = permissionService.effective(String.valueOf(context.systemId()));
        List<PageSchemaFieldVO> fields = schemaFields(module, permission);
        Set<String> visibleFieldCodes = fields.stream().map(PageSchemaFieldVO::fieldCode).collect(java.util.stream.Collectors.toSet());
        List<PageComponentConfig> visibleComponents = components(payload).stream()
                .filter(component -> !Boolean.FALSE.equals(component.visible()))
                .filter(component -> !StringUtils.hasText(component.boundFieldCode())
                        || visibleFieldCodes.contains(component.boundFieldCode()))
                .sorted(Comparator.comparing(component -> component.sort() == null ? Integer.MAX_VALUE : component.sort()))
                .toList();
        String schemaVersion = publishedSnapshot
                ? safeText(config.getPublishedVersion(), "PAGE_UNVERSIONED")
                : "PAGE_DRAFT_" + shortHash(safeText(config.getFieldList(), ""));
        return new PageSchemaVO(String.valueOf(config.getId()), String.valueOf(config.getSystemId()),
                String.valueOf(config.getTenantId()), String.valueOf(module.getId()), module.getModuleCode(),
                pageCode(payload, config), pageName(payload), String.valueOf(payload.getOrDefault("pageType", "MODULE_LIST")),
                String.valueOf(payload.getOrDefault("route", "/modules/" + module.getId())),
                String.valueOf(payload.getOrDefault("layoutMode", "left-list-right-detail")), config.getPublishStatus(),
                publishedSnapshot ? "PUBLISHED" : "DRAFT", schemaVersion, visibleComponents, fields,
                permission.snapshotId(), permission.permissionVersion(), RequestContext.current().traceId(),
                config.getUpdatedAt());
    }

    private List<PageSchemaFieldVO> schemaFields(ModuleDefinition module, EffectivePermissionSnapshot permission) {
        return fieldBaseService.list(new LambdaQueryWrapper<ModuleFieldDefinition>()
                        .eq(ModuleFieldDefinition::getModuleId, module.getId())
                        .eq(ModuleFieldDefinition::getDeleted, DELETED_NO)
                        .eq(ModuleFieldDefinition::getStatus, ENABLED)
                        .orderByAsc(ModuleFieldDefinition::getSortOrder)
                        .orderByAsc(ModuleFieldDefinition::getId))
                .stream()
                .map(field -> schemaField(module, permission, field))
                .filter(Objects::nonNull)
                .toList();
    }

    private PageSchemaFieldVO schemaField(ModuleDefinition module, EffectivePermissionSnapshot permission,
                                          ModuleFieldDefinition field) {
        String permissionMode = fieldPermissionMode(permission, module.getId(), field.getId(), field.getFieldCode());
        if ("HIDDEN".equalsIgnoreCase(permissionMode)) {
            return null;
        }
        boolean writable = "WRITABLE".equalsIgnoreCase(permissionMode);
        return new PageSchemaFieldVO(String.valueOf(field.getId()), field.getFieldCode(), field.getFieldName(),
                safeText(field.getFieldType(), "TEXT"), safeText(field.getStorageType(), "VARCHAR"),
                Objects.equals(field.getRequired(), ENABLED), Objects.equals(field.getSortable(), ENABLED),
                permissionMode, writable, !writable, true,
                fieldMaskRule(permissionMode, field.getMaskRule()), readPayload(field.getValidationRule()),
                writable ? null : "当前角色可以查看该字段，但没有写入权限。", field.getSortOrder());
    }

    private ModuleDefinition requireModule(ModuleSystemContext context, String moduleId) {
        Long id = contextResolver.parseRequiredId(moduleId, "Invalid module id.");
        ModuleDefinition module = moduleBaseService.getOne(new LambdaQueryWrapper<ModuleDefinition>()
                .eq(ModuleDefinition::getId, id)
                .eq(ModuleDefinition::getSystemId, context.systemId())
                .eq(ModuleDefinition::getTenantId, context.tenantId())
                .eq(ModuleDefinition::getDeleted, DELETED_NO)
                .last("LIMIT 1"), false);
        if (Objects.isNull(module)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "Module does not exist.");
        }
        if (!Objects.equals(module.getStatus(), ENABLED)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "Module is disabled.");
        }
        return module;
    }

    private ModuleWorkConfig requirePageConfig(ModuleSystemContext context, Long moduleId, String pageCode) {
        ModuleWorkConfig config = findConfig(context, moduleId, pageCode);
        if (Objects.isNull(config)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "Page design does not exist.");
        }
        return config;
    }

    private ModuleWorkConfig findConfig(ModuleSystemContext context, Long moduleId, String pageCode) {
        return workConfigBaseService.getOne(new LambdaQueryWrapper<ModuleWorkConfig>()
                .eq(ModuleWorkConfig::getSystemId, context.systemId())
                .eq(ModuleWorkConfig::getTenantId, context.tenantId())
                .eq(ModuleWorkConfig::getConfigType, configType(moduleId, pageCode))
                .last("LIMIT 1"), false);
    }

    private Set<String> fieldCodes(Long moduleId) {
        return new HashSet<>(fieldBaseService.list(new LambdaQueryWrapper<ModuleFieldDefinition>()
                        .eq(ModuleFieldDefinition::getModuleId, moduleId)
                        .eq(ModuleFieldDefinition::getDeleted, DELETED_NO)
                        .eq(ModuleFieldDefinition::getStatus, ENABLED))
                .stream()
                .map(ModuleFieldDefinition::getFieldCode)
                .toList());
    }

    private void savePublishVersion(ModuleSystemContext context, ModuleWorkConfig config, String version) {
        ModulePublishVersion publishVersion = new ModulePublishVersion();
        publishVersion.setSystemId(context.systemId());
        publishVersion.setTenantId(context.tenantId());
        publishVersion.setObjectType("MODULE_PAGE");
        publishVersion.setObjectId(config.getId());
        publishVersion.setVersionNo(version);
        publishVersion.setPublishStatus(PUBLISHED);
        publishVersion.setImpactRefs(toJson(List.of("RUNTIME_PAGE", "MODULE_NAVIGATION", "PERMISSION_SNAPSHOT")));
        publishVersion.setFailureItems(toJson(List.of()));
        publishVersion.setTraceId(RequestContext.current().traceId());
        publishVersion.setCreatedAt(LocalDateTime.now());
        publishVersionBaseService.saveEntity(publishVersion);
    }

    private void saveOrUpdate(ModuleWorkConfig config) {
        if (Objects.isNull(config.getId())) {
            workConfigBaseService.saveEntity(config);
        } else {
            workConfigBaseService.updateById(config);
        }
    }

    private String configPrefix(Long moduleId) {
        return PAGE_CONFIG_PREFIX + moduleId + ":";
    }

    private String configType(Long moduleId, String pageCode) {
        return configPrefix(moduleId) + shortHash(normalizeCode(pageCode));
    }

    private String pageCode(Map<String, Object> payload, ModuleWorkConfig config) {
        Object value = payload.get("pageCode");
        if (value != null && StringUtils.hasText(String.valueOf(value))) {
            return String.valueOf(value);
        }
        String configType = config.getConfigType();
        return configType.substring(configType.lastIndexOf(':') + 1);
    }

    private String pageName(Map<String, Object> payload) {
        return String.valueOf(payload.getOrDefault("pageName", "Module page"));
    }

    private List<PageComponentConfig> components(Map<String, Object> payload) {
        Object raw = payload.get("components");
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .map(item -> objectMapper.convertValue(item, PageComponentConfig.class))
                .toList();
    }

    private List<String> stringList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .toList();
    }

    private String fieldPermissionMode(EffectivePermissionSnapshot permission, Object moduleId,
                                       Object fieldId, String fieldCode) {
        Map<String, String> fields = permission.field();
        return safeText(firstFieldPermission(fields, moduleId, fieldId, fieldCode), "READABLE")
                .toUpperCase(Locale.ROOT);
    }

    private String firstFieldPermission(Map<String, String> fields, Object moduleId, Object fieldId,
                                        String fieldCode) {
        List<String> keys = List.of("*", String.valueOf(moduleId) + "." + fieldId,
                String.valueOf(moduleId) + "." + fieldCode, String.valueOf(fieldId), fieldCode);
        for (String key : keys) {
            if (StringUtils.hasText(fields.get(key))) {
                return fields.get(key);
            }
        }
        return null;
    }

    private String fieldMaskRule(String permissionMode, String configuredMaskRule) {
        if ("MASKED".equalsIgnoreCase(permissionMode)) {
            return safeText(readJsonString(configuredMaskRule, "MASKED"), "MASKED");
        }
        return readJsonString(configuredMaskRule, "NONE");
    }

    private String readJsonString(String json, String fallback) {
        if (!StringUtils.hasText(json)) {
            return fallback;
        }
        try {
            Object value = objectMapper.readValue(json, Object.class);
            return value == null ? fallback : String.valueOf(value);
        } catch (Exception ex) {
            return fallback;
        }
    }

    private Map<String, Object> readPayload(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BusinessException(CommonErrorCode.OPS_INTERNAL_ERROR, "JSON serialization failed.");
        }
    }

    private void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, message);
        }
    }

    private String normalizeCode(String value) {
        String normalized = safeText(value, "page").trim().toLowerCase().replaceAll("[^a-z0-9_\\-]", "_");
        return StringUtils.hasText(normalized) ? normalized : "page";
    }

    private String shortHash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 12);
        } catch (Exception ex) {
            throw new BusinessException(CommonErrorCode.OPS_INTERNAL_ERROR, "Page code hash failed.");
        }
    }

    private String safeText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
