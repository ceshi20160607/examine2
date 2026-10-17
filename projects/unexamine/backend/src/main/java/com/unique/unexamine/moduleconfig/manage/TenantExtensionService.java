package com.unique.unexamine.moduleconfig.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.unique.unexamine.application.base.entity.AppDefinition;
import com.unique.unexamine.application.base.entity.AppGrant;
import com.unique.unexamine.application.base.entity.AppGrantField;
import com.unique.unexamine.application.base.service.AppDefinitionBaseService;
import com.unique.unexamine.application.base.service.AppGrantBaseService;
import com.unique.unexamine.application.base.service.AppGrantFieldBaseService;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.moduleconfig.base.entity.CfgTenantExtension;
import com.unique.unexamine.moduleconfig.base.entity.CfgTenantExtensionVersion;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModule;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModuleField;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModulePublication;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModuleVersion;
import com.unique.unexamine.moduleconfig.base.service.CfgTenantExtensionBaseService;
import com.unique.unexamine.moduleconfig.base.service.CfgTenantExtensionVersionBaseService;
import com.unique.unexamine.moduleconfig.base.mapper.CfgTenantExtensionMapper;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleBaseService;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleFieldBaseService;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModuleAction;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleActionBaseService;
import com.unique.unexamine.moduleconfig.manage.TenantExtensionModels.ExtensionActionInput;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModulePublicationBaseService;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleVersionBaseService;
import com.unique.unexamine.moduleconfig.manage.TenantExtensionModels.ApplicationBindingInput;
import com.unique.unexamine.moduleconfig.manage.TenantExtensionModels.ApplicationGrantOption;
import com.unique.unexamine.moduleconfig.manage.TenantExtensionModels.ExtensionFieldInput;
import com.unique.unexamine.moduleconfig.manage.TenantExtensionModels.PageOverrideInput;
import com.unique.unexamine.moduleconfig.manage.TenantExtensionModels.RollbackTenantExtensionRequest;
import com.unique.unexamine.moduleconfig.manage.TenantExtensionModels.SaveTenantExtensionRequest;
import com.unique.unexamine.moduleconfig.manage.TenantExtensionModels.TenantExtensionCheck;
import com.unique.unexamine.moduleconfig.manage.TenantExtensionModels.TenantExtensionIssue;
import com.unique.unexamine.moduleconfig.manage.TenantExtensionModels.TenantExtensionModuleView;
import com.unique.unexamine.moduleconfig.manage.TenantExtensionModels.TenantExtensionVersionView;
import com.unique.unexamine.shared.manage.web.DomainException;
import com.unique.unexamine.system.base.entity.SystemTenant;
import com.unique.unexamine.system.base.service.SystemTenantBaseService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TenantExtensionService {
    private final SystemTenantBaseService tenantService;
    private final ConfiguredModuleBaseService moduleService;
    private final ConfiguredModuleFieldBaseService fieldService;
    private final ConfiguredModuleActionBaseService actionService;
    private final ConfiguredModulePublicationBaseService publicationService;
    private final ConfiguredModuleVersionBaseService moduleVersionService;
    private final CfgTenantExtensionBaseService extensionService;
    private final CfgTenantExtensionMapper extensionMapper;
    private final CfgTenantExtensionVersionBaseService extensionVersionService;
    private final AppDefinitionBaseService applicationService;
    private final AppGrantBaseService grantService;
    private final AppGrantFieldBaseService grantFieldService;
    private final TenantExtensionRuntimeService runtimeService;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;
    private final ModuleConfigurationService configurationService;
    private final DictionaryConfigurationService dictionaryConfigurationService;
    private final ModuleIdentifierIntegrity identifierIntegrity;

    public TenantExtensionService(
            SystemTenantBaseService tenantService,
            ConfiguredModuleBaseService moduleService,
            ConfiguredModuleFieldBaseService fieldService,
            ConfiguredModuleActionBaseService actionService,
            ConfiguredModulePublicationBaseService publicationService,
            ConfiguredModuleVersionBaseService moduleVersionService,
            CfgTenantExtensionBaseService extensionService,
            CfgTenantExtensionMapper extensionMapper,
            CfgTenantExtensionVersionBaseService extensionVersionService,
            AppDefinitionBaseService applicationService,
            AppGrantBaseService grantService,
            AppGrantFieldBaseService grantFieldService,
            TenantExtensionRuntimeService runtimeService,
            AuditRecorder auditRecorder,
            ObjectMapper objectMapper,
            ModuleConfigurationService configurationService,
            DictionaryConfigurationService dictionaryConfigurationService,
            ModuleIdentifierIntegrity identifierIntegrity) {
        this.tenantService = tenantService;
        this.moduleService = moduleService;
        this.fieldService = fieldService;
        this.actionService = actionService;
        this.publicationService = publicationService;
        this.moduleVersionService = moduleVersionService;
        this.extensionService = extensionService;
        this.extensionMapper = extensionMapper;
        this.extensionVersionService = extensionVersionService;
        this.applicationService = applicationService;
        this.grantService = grantService;
        this.grantFieldService = grantFieldService;
        this.runtimeService = runtimeService;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
        this.configurationService = configurationService;
        this.dictionaryConfigurationService = dictionaryConfigurationService;
        this.identifierIntegrity = identifierIntegrity;
    }

    @Transactional(readOnly = true)
    public List<TenantExtensionModuleView> list(AuthenticatedContext context) {
        ExtensionContext scope = requireExtensionContext(context);
        return moduleService.selectList(Wrappers.<ConfiguredModule>lambdaQuery()
                        .eq(ConfiguredModule::getSystemId, context.systemId())
                        .eq(ConfiguredModule::getOwnerTenantId, scope.mainTenant().getId())
                        .eq(ConfiguredModule::getStatus, "ACTIVE")
                        .orderByAsc(ConfiguredModule::getGroupId, ConfiguredModule::getId)).stream()
                .filter(module -> publication(module) != null)
                .map(module -> view(context, scope, module))
                .toList();
    }

    @Transactional(readOnly = true)
    public TenantExtensionModuleView get(AuthenticatedContext context, Long moduleId) {
        ExtensionContext scope = requireExtensionContext(context);
        return view(context, scope, requireBaseModule(context, scope, moduleId));
    }

    @Transactional
    public TenantExtensionModuleView save(
            AuthenticatedContext context, Long moduleId, SaveTenantExtensionRequest request, String traceId) {
        // 扩展字段与主模块字段共用租户配额；在任何配置读取前锁定系统，避免并发快照漏计。
        configurationService.lockConfigurationQuotaScope(context);
        ExtensionContext scope = requireExtensionContext(context);
        ConfiguredModule module = requireBaseModule(context, scope, moduleId);
        BaseConfiguration base = baseConfiguration(module);
        CfgTenantExtension extension = findExtension(context, moduleId);
        if (extension == null && request.expectedVersion() != 0) conflict("租户扩展草稿已经变化");
        if (extension != null && !request.expectedVersion().equals(extension.getVersion())) conflict("租户扩展草稿已经变化");

        ObjectNode document = extensionDocument(context, module, base.configuration(), extension, request);
        if (extension == null) {
            extension = new CfgTenantExtension();
            extension.setSystemId(context.systemId());
            extension.setTenantId(context.tenantId());
            extension.setBaseModuleId(moduleId);
            extension.setDraftRevision(1);
            extension.setExtensionJson(write(document));
            extension.setStatus("DRAFT");
            extension.setCreatedByMemberId(context.memberId());
            extension.setUpdatedByMemberId(context.memberId());
            extension.setVersion(0);
            extensionService.insert(extension);
        } else {
            extension.setDraftRevision(extension.getDraftRevision() + 1);
            extension.setExtensionJson(write(document));
            extension.setStatus("DRAFT");
            extension.setUpdatedByMemberId(context.memberId());
            if (extensionService.updateById(extension) == 0) conflict("租户扩展草稿已经变化");
        }
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "TENANT_EXTENSION_DRAFT_SAVED", "TENANT_EXTENSION", extension.getId().toString(), "SUCCESS",
                Map.of("baseModuleId", moduleId, "draftRevision", extension.getDraftRevision(),
                        "fieldCount", request.fields().size(), "bindingCount", request.applicationBindings().size()));
        return view(context, scope, module);
    }

    @Transactional(readOnly = true)
    public TenantExtensionCheck check(AuthenticatedContext context, Long moduleId) {
        ExtensionContext scope = requireExtensionContext(context);
        ConfiguredModule module = requireBaseModule(context, scope, moduleId);
        CfgTenantExtension extension = requireExtension(context, moduleId);
        BaseConfiguration base = baseConfiguration(module);
        JsonNode document = parse(extension.getExtensionJson());
        List<TenantExtensionIssue> issues = validateDocument(context, module, base.configuration(), document);
        return new TenantExtensionCheck(issues.isEmpty(), extension.getDraftRevision(), base.version().getId(), issues,
                runtimeService.merge(decorateBase(base.configuration().deepCopy(), scope.mainTenant()), document));
    }

    @Transactional
    public TenantExtensionModuleView publish(
            AuthenticatedContext context, Long moduleId, Integer expectedDraftRevision, String traceId) {
        ExtensionContext scope = requireExtensionContext(context);
        ConfiguredModule module = requireBaseModule(context, scope, moduleId);
        CfgTenantExtension extension = requireExtension(context, moduleId);
        if (!expectedDraftRevision.equals(extension.getDraftRevision())) conflict("租户扩展草稿已经变化，请重新检查");
        identifierIntegrity.lockSystem(context.systemId());
        identifierIntegrity.requireUnambiguous(module);
        BaseConfiguration base = baseConfiguration(module);
        JsonNode document = parse(extension.getExtensionJson());
        List<TenantExtensionIssue> issues = validateDocument(context, module, base.configuration(), document);
        if (!issues.isEmpty()) {
            throw new DomainException("TENANT_EXTENSION_CHECK_FAILED",
                    issues.stream().map(TenantExtensionIssue::message).collect(Collectors.joining("；")),
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        int next = extensionVersionService.selectList(Wrappers.<CfgTenantExtensionVersion>lambdaQuery()
                        .eq(CfgTenantExtensionVersion::getExtensionId, extension.getId())).stream()
                .map(CfgTenantExtensionVersion::getVersionNumber).max(Integer::compareTo).orElse(0) + 1;
        ObjectNode snapshot = objectMapper.createObjectNode();
        snapshot.put("schemaVersion", 1);
        snapshot.put("source", "TENANT");
        snapshot.put("sourceTenantId", context.tenantId());
        snapshot.put("baseModuleId", moduleId);
        snapshot.put("baseModuleVersionId", base.version().getId());
        snapshot.put("versionNumber", next);
        snapshot.set("extension", document.deepCopy());
        snapshot.set("mergedConfiguration", runtimeService.merge(
                decorateBase(base.configuration().deepCopy(), scope.mainTenant()), document));

        CfgTenantExtensionVersion version = new CfgTenantExtensionVersion();
        // 发布版本保留系统与基础模块归属，避免生成的 Base 插入遗漏非空业务键。
        version.setSystemId(context.systemId());
        version.setExtensionId(extension.getId());
        version.setBaseModuleId(moduleId);
        version.setVersionNumber(next);
        version.setBaseModuleVersionId(base.version().getId());
        version.setSnapshotJson(write(snapshot));
        version.setSchemaHash(sha256(version.getSnapshotJson()));
        version.setPublishedByMemberId(context.memberId());
        version.setPublishTime(LocalDateTime.now());
        extensionVersionService.insert(version);
        extension.setCurrentVersionId(version.getId());
        extension.setStatus("PUBLISHED");
        extension.setUpdatedByMemberId(context.memberId());
        if (extensionService.updateById(extension) == 0) conflict("租户扩展发布指针已经变化");
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "TENANT_EXTENSION_PUBLISHED", "TENANT_EXTENSION_VERSION", version.getId().toString(), "SUCCESS",
                Map.of("baseModuleId", moduleId, "versionNumber", next, "baseModuleVersionId", base.version().getId()));
        return view(context, scope, module);
    }

    @Transactional
    public TenantExtensionModuleView remove(AuthenticatedContext context, Long moduleId, Integer expectedVersion, String traceId) {
        ExtensionContext scope = requireExtensionContext(context);
        ConfiguredModule module = requireBaseModule(context, scope, moduleId);
        CfgTenantExtension extension = requireExtension(context, moduleId);
        if (!expectedVersion.equals(extension.getVersion())) conflict("租户扩展已经变化");
        int nextVersion = extension.getVersion() + 1;
        if (extensionMapper.update(null, Wrappers.<CfgTenantExtension>lambdaUpdate()
                .eq(CfgTenantExtension::getId, extension.getId())
                .eq(CfgTenantExtension::getVersion, extension.getVersion())
                .set(CfgTenantExtension::getCurrentVersionId, null)
                .set(CfgTenantExtension::getStatus, "DELETED")
                .set(CfgTenantExtension::getUpdatedByMemberId, context.memberId())
                .set(CfgTenantExtension::getVersion, nextVersion)) == 0) conflict("租户扩展已经变化");
        extension.setCurrentVersionId(null);
        extension.setStatus("DELETED");
        extension.setUpdatedByMemberId(context.memberId());
        extension.setVersion(nextVersion);
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "TENANT_EXTENSION_REMOVED", "TENANT_EXTENSION", extension.getId().toString(), "SUCCESS",
                Map.of("baseModuleId", moduleId, "fallback", "MAIN_TENANT_CONFIGURATION"));
        return view(context, scope, module);
    }

    @Transactional
    public TenantExtensionModuleView rollback(
            AuthenticatedContext context, Long moduleId, RollbackTenantExtensionRequest request, String traceId) {
        ExtensionContext scope = requireExtensionContext(context);
        ConfiguredModule module = requireBaseModule(context, scope, moduleId);
        CfgTenantExtension extension = requireExtension(context, moduleId);
        if (!request.expectedVersion().equals(extension.getVersion())) conflict("租户扩展发布指针已经变化");
        CfgTenantExtensionVersion target = extensionVersionService.selectById(request.targetVersionId());
        if (target == null || !extension.getId().equals(target.getExtensionId())) notFound("租户扩展版本不存在");
        identifierIntegrity.lockSystem(context.systemId());
        BaseConfiguration base = baseConfiguration(module);
        JsonNode snapshot = parse(target.getSnapshotJson());
        JsonNode document = snapshot.path("extension");
        List<TenantExtensionIssue> issues = validateDocument(context, module, base.configuration(), document);
        List<PublicationCheckIssue> compatibility = runtimeService.checkDocument(
                context.tenantId(), base.configuration(), document);
        if (!issues.isEmpty() || !compatibility.isEmpty()) {
            List<String> messages = new ArrayList<>();
            issues.forEach(issue -> messages.add(issue.message()));
            compatibility.forEach(issue -> messages.add(issue.message()));
            throw new DomainException("TENANT_EXTENSION_CHECK_FAILED", String.join("；", messages),
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        extension.setCurrentVersionId(target.getId());
        extension.setStatus("PUBLISHED");
        extension.setUpdatedByMemberId(context.memberId());
        if (extensionService.updateById(extension) == 0) conflict("租户扩展发布指针已经变化");
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "TENANT_EXTENSION_ROLLED_BACK", "TENANT_EXTENSION_VERSION", target.getId().toString(), "SUCCESS",
                Map.of("baseModuleId", moduleId, "versionNumber", target.getVersionNumber()));
        return view(context, scope, module);
    }

    private TenantExtensionModuleView view(AuthenticatedContext context, ExtensionContext scope, ConfiguredModule module) {
        BaseConfiguration base = baseConfiguration(module);
        CfgTenantExtension extension = findExtension(context, module.getId());
        JsonNode draft = extension == null ? emptyDocument(context) : parse(extension.getExtensionJson());
        ObjectNode preview = decorateBase(base.configuration().deepCopy(), scope.mainTenant());
        if (extension != null && !"DELETED".equals(extension.getStatus())) preview = runtimeService.merge(preview, draft);
        List<TenantExtensionVersionView> versions = extension == null ? List.of()
                : extensionVersionService.selectList(Wrappers.<CfgTenantExtensionVersion>lambdaQuery()
                        .eq(CfgTenantExtensionVersion::getExtensionId, extension.getId())
                        .orderByDesc(CfgTenantExtensionVersion::getVersionNumber)).stream()
                .map(version -> new TenantExtensionVersionView(version.getId(), version.getVersionNumber(),
                        version.getBaseModuleVersionId(), version.getPublishTime(),
                        version.getId().equals(extension.getCurrentVersionId())))
                .toList();
        return new TenantExtensionModuleView(module.getId(), module.getCode(), module.getName(),
                scope.mainTenant().getId(), scope.mainTenant().getName(), base.version().getId(),
                base.version().getVersionNumber(), extension, draft, preview,
                grantOptions(context, module), versions);
    }

    private ObjectNode extensionDocument(
            AuthenticatedContext context, ConfiguredModule module, ObjectNode base,
            CfgTenantExtension existing, SaveTenantExtensionRequest request) {
        JsonNode old = existing == null ? emptyDocument(context) : parse(existing.getExtensionJson());
        Map<String, JsonNode> previousByCode = new LinkedHashMap<>();
        old.path("fields").forEach(field -> previousByCode.put(field.path("code").asText(), field));
        Set<String> baseCodes = new HashSet<>();
        base.path("fields").forEach(field -> baseCodes.add(field.path("code").asText()));
        Set<String> submitted = new HashSet<>();
        ArrayNode fields = objectMapper.createArrayNode();
        for (ExtensionFieldInput input : request.fields()) {
            String code = input.code().strip().toLowerCase(Locale.ROOT);
            if (!submitted.add(code) || baseCodes.contains(code)) invalid("扩展字段编码与主配置或本租户扩展冲突：" + code);
            if (!ModuleConfigurationService.FIELD_TYPES.contains(input.fieldType()) || input.fieldType().startsWith("SYSTEM_")) {
                invalid("租户扩展不能创建该字段类型：" + input.fieldType());
            }
            JsonNode previous = previousByCode.get(code);
            ConfiguredModuleField physical;
            if (previous != null) {
                if (!previous.path("fieldType").asText().equals(input.fieldType())) invalid("租户扩展不能修改已有字段类型：" + code);
                physical = requireExtensionField(context, module.getId(), previous.path("id").asLong());
                if (input.fieldId() != null && !input.fieldId().equals(physical.getId())) invalid("扩展字段标识不能修改：" + code);
            } else {
                if (input.fieldId() != null) invalid("新扩展字段不能指定已有字段标识");
                physical = new ConfiguredModuleField();
                physical.setSystemId(context.systemId());
                physical.setOwnerTenantId(context.tenantId());
                physical.setModuleId(module.getId());
                physical.setCode("__t" + context.tenantId() + "_" + code);
                physical.setFieldType(input.fieldType());
                physical.setUniqueValue(false);
                physical.setSearchable(false);
                physical.setStatus("ACTIVE");
                physical.setVersion(0);
            }
            validateFieldBinding(context, input.fieldType(), input.dictionaryId(), input.referenceModuleId(), input.config());
            physical.setDictionaryId(input.dictionaryId());
            physical.setReferenceModuleId(input.referenceModuleId());
            physical.setName(input.name().strip());
            physical.setRequired(input.required());
            physical.setSortOrder(input.sortOrder());
            physical.setConfigJson(write(input.config()));
            if (physical.getId() == null) {
                configurationService.requireFieldQuotaCapacity(context);
                fieldService.insert(physical);
            }
            else if (fieldService.updateById(physical) == 0) conflict("扩展字段已经变化");
            ObjectNode node = objectMapper.valueToTree(physical);
            node.put("code", code);
            node.put("source", "TENANT");
            node.put("sourceTenantId", context.tenantId());
            node.put("mandatory", false);
            fields.add(node);
        }
        Set<String> allCodes = new HashSet<>(baseCodes);
        submitted.forEach(allCodes::add);
        Set<String> requiredBaseCodes = new HashSet<>();
        base.path("fields").forEach(field -> { if (field.path("required").asBoolean(false)) requiredBaseCodes.add(field.path("code").asText()); });
        ArrayNode pages = objectMapper.createArrayNode();
        Set<String> pageTypes = new HashSet<>();
        for (PageOverrideInput page : request.pages()) {
            if (!pageTypes.add(page.pageType())) invalid("同一页面只能配置一个租户覆盖：" + page.pageType());
            Set<String> pageCodes = new HashSet<>(page.fieldCodes());
            if (!allCodes.containsAll(pageCodes)) invalid("租户页面引用不存在字段：" + page.pageType());
            if ("FORM".equals(page.pageType()) && !pageCodes.containsAll(requiredBaseCodes)) {
                invalid("租户页面不能隐藏主租户强制字段：" + String.join(",", requiredBaseCodes));
            }
            ObjectNode node = objectMapper.createObjectNode();
            node.put("pageType", page.pageType());
            node.set("fieldCodes", objectMapper.valueToTree(page.fieldCodes()));
            node.put("source", "TENANT");
            pages.add(node);
        }
        ArrayNode bindings = validateBindings(context, module, request.applicationBindings(), allCodes, true);
        ObjectNode document = objectMapper.createObjectNode();
        document.put("schemaVersion", 1);
        ObjectNode source = document.putObject("source");
        source.put("type", "TENANT");
        source.put("tenantId", context.tenantId());
        source.put("baseModuleId", module.getId());
        document.set("fields", fields);
        document.set("pages", pages);
        document.set("actions", extensionActions(context, module, base, fields, old, request.actions()));
        document.set("applicationBindings", bindings);
        return document;
    }

    /** 动作仍使用公共物理身份，内部编码隔离租户；发布快照恢复用户看到的稳定逻辑编码。 */
    private ArrayNode extensionActions(AuthenticatedContext context, ConfiguredModule module, JsonNode base,
                                       ArrayNode extensionFields, JsonNode old, List<ExtensionActionInput> submitted) {
        List<ExtensionActionInput> inputs = submitted;
        if (inputs == null) {
            inputs = new ArrayList<>(); // 旧客户端未传动作时保留现有动作，同时重新校验字段引用。
            for (JsonNode action : old.path("actions")) {
                List<String> codes = new ArrayList<>();
                parse(action.path("configJson").asText("{}")).path("parameterFieldCodes").forEach(code -> codes.add(code.asText()));
                inputs.add(new ExtensionActionInput(action.path("id").asLong(), action.path("code").asText(),
                        action.path("name").asText(), action.path("location").asText(), codes, action.path("sortOrder").asInt()));
            }
        }
        if (inputs.size() > 20) invalid("每个租户模块最多配置20个新增动作");
        List<JsonNode> fields = new ArrayList<>();
        base.path("fields").forEach(fields::add);
        extensionFields.forEach(fields::add);
        Set<String> codes = new HashSet<>();
        base.path("actions").forEach(action -> codes.add(action.path("code").asText().toUpperCase(Locale.ROOT)));
        Map<String, JsonNode> previous = new LinkedHashMap<>();
        old.path("actions").forEach(action -> previous.put(action.path("code").asText(), action));
        ArrayNode result = objectMapper.createArrayNode();
        for (ExtensionActionInput input : inputs) {
            String code = input.code().strip().toUpperCase(Locale.ROOT);
            if (!codes.add(code)) invalid("新增动作与主配置或本租户动作编码冲突：" + code);
            ObjectNode config = objectMapper.createObjectNode();
            config.put("operation", "UPDATE_FIELDS");
            config.set("parameterFieldCodes", objectMapper.valueToTree(input.parameterFieldCodes()));
            configurationService.validateCustomActionConfig(config, fields);
            JsonNode prior = previous.get(code);
            String physicalCode = "__t" + context.tenantId() + "_" + code;
            ConfiguredModuleAction physical;
            if (prior != null) {
                physical = actionService.selectById(prior.path("id").asLong());
                if (input.actionId() != null && !input.actionId().equals(prior.path("id").asLong())) invalid("动作身份不能修改：" + code);
            } else {
                if (input.actionId() != null) invalid("新动作不能指定已有动作身份");
                // 删除后重新加入同一编码时复用原物理身份，保留历史版本及 Flow 引用。
                physical = actionService.selectList(Wrappers.<ConfiguredModuleAction>lambdaQuery()
                        .eq(ConfiguredModuleAction::getModuleId, module.getId())
                        .eq(ConfiguredModuleAction::getOwnerTenantId, context.tenantId())
                        .eq(ConfiguredModuleAction::getCode, physicalCode)).stream().findFirst().orElse(null);
                if (physical == null) {
                    physical = new ConfiguredModuleAction();
                    physical.setSystemId(context.systemId());
                    physical.setOwnerTenantId(context.tenantId());
                    physical.setModuleId(module.getId());
                    physical.setCode(physicalCode);
                    physical.setActionType("CUSTOM");
                    physical.setVersion(0);
                }
            }
            if (physical == null || !context.systemId().equals(physical.getSystemId())
                    || !context.tenantId().equals(physical.getOwnerTenantId()) || !module.getId().equals(physical.getModuleId())
                    || !physicalCode.equals(physical.getCode()) || !"CUSTOM".equals(physical.getActionType())) invalid("动作来源无效：" + code);
            physical.setName(input.name().strip());
            physical.setLocation(input.location());
            physical.setSortOrder(input.sortOrder());
            physical.setStatus("ACTIVE");
            physical.setConfigJson(write(config));
            if (physical.getId() == null) actionService.insert(physical);
            else if (actionService.updateById(physical) == 0) conflict("扩展动作已经变化");
            ObjectNode action = objectMapper.valueToTree(physical);
            action.put("code", code);
            action.put("source", "TENANT");
            action.put("sourceTenantId", context.tenantId());
            action.put("mandatory", false);
            result.add(action);
        }
        return result;
    }

    /** 发布时检查动作物理归属和当前有效参数，不能用旧快照跳过来源及字段变化。 */
    private void validateActions(AuthenticatedContext context, ConfiguredModule module, JsonNode base,
                                 JsonNode document, List<TenantExtensionIssue> issues) {
        List<JsonNode> fields = new ArrayList<>();
        base.path("fields").forEach(fields::add);
        document.path("fields").forEach(fields::add);
        Set<String> codes = new HashSet<>();
        base.path("actions").forEach(action -> codes.add(action.path("code").asText().toUpperCase(Locale.ROOT)));
        for (JsonNode action : document.path("actions")) {
            String code = action.path("code").asText();
            ConfiguredModuleAction physical = actionService.selectById(action.path("id").asLong());
            if (!codes.add(code) || physical == null || !context.systemId().equals(physical.getSystemId())
                    || !context.tenantId().equals(physical.getOwnerTenantId()) || !module.getId().equals(physical.getModuleId())
                    || !("__t" + context.tenantId() + "_" + code).equals(physical.getCode())
                    || !"CUSTOM".equals(physical.getActionType()) || !"ACTIVE".equals(physical.getStatus())) {
                issues.add(new TenantExtensionIssue("actions." + code, "TENANT_ACTION_SOURCE_INVALID", "扩展动作身份、编码或来源无效"));
            }
            try { configurationService.validateCustomActionConfig(parse(action.path("configJson").asText("{}")), fields); }
            catch (DomainException exception) { issues.add(new TenantExtensionIssue("actions." + code, exception.code(), exception.getMessage())); }
        }
    }

    private List<TenantExtensionIssue> validateDocument(
            AuthenticatedContext context, ConfiguredModule module, ObjectNode base, JsonNode document) {
        List<TenantExtensionIssue> issues = new ArrayList<>();
        Set<String> baseCodes = new HashSet<>();
        Set<String> required = new HashSet<>();
        base.path("fields").forEach(field -> {
            baseCodes.add(field.path("code").asText());
            if (field.path("required").asBoolean(false)) required.add(field.path("code").asText());
        });
        Set<String> allCodes = new HashSet<>(baseCodes);
        document.path("fields").forEach(field -> {
            String code = field.path("code").asText();
            if (baseCodes.contains(code) || !allCodes.add(code)) {
                issues.add(new TenantExtensionIssue("fields." + code, "TENANT_FIELD_CODE_CONFLICT", "扩展字段稳定编码发生冲突"));
            }
            ConfiguredModuleField physical = fieldService.selectById(field.path("id").asLong());
            if (physical == null || !context.systemId().equals(physical.getSystemId())
                    || !context.tenantId().equals(physical.getOwnerTenantId()) || !"ACTIVE".equals(physical.getStatus())
                    || !module.getId().equals(physical.getModuleId()) || !physical.getFieldType().equals(field.path("fieldType").asText())) {
                issues.add(new TenantExtensionIssue("fields." + code, "TENANT_FIELD_SOURCE_INVALID", "扩展字段来源或基础语义无效"));
            }
            try {
                validateFieldBinding(context, field.path("fieldType").asText(),
                        nullableId(field, "dictionaryId"), nullableId(field, "referenceModuleId"),
                        parse(field.path("configJson").asText("{}")));
            } catch (DomainException exception) {
                issues.add(new TenantExtensionIssue("fields." + code, exception.code(), exception.getMessage()));
            }
        });
        document.path("pages").forEach(page -> {
            Set<String> pageCodes = new HashSet<>();
            page.path("fieldCodes").forEach(code -> pageCodes.add(code.asText()));
            if (!allCodes.containsAll(pageCodes)) issues.add(new TenantExtensionIssue(
                    "pages." + page.path("pageType").asText(), "TENANT_PAGE_FIELD_INVALID", "页面引用不存在字段"));
            if ("FORM".equals(page.path("pageType").asText()) && !pageCodes.containsAll(required)) {
                issues.add(new TenantExtensionIssue("pages.FORM", "MAIN_MANDATORY_FIELD_HIDDEN", "租户页面不能隐藏主租户强制字段"));
            }
        });
        validateActions(context, module, base, document, issues);
        try { validateBindings(context, module, readBindings(document), allCodes, false); }
        catch (DomainException exception) { issues.add(new TenantExtensionIssue("applicationBindings", exception.code(), exception.getMessage())); }
        return issues;
    }

    /** 草稿和发布重复核对当前有效来源，不能使用其他租户的字典或未发布关联模块。 */
    private void validateFieldBinding(AuthenticatedContext context, String fieldType, Long dictionaryId,
                                      Long referenceModuleId, JsonNode config) {
        configurationService.validateFieldDefinition(context, fieldType, dictionaryId, referenceModuleId, config, null);
        if (dictionaryId != null) dictionaryConfigurationService.validateExtensionBinding(context, dictionaryId);
        if (referenceModuleId != null) {
            ConfiguredModule target = moduleService.selectById(referenceModuleId);
            if (!"ACTIVE".equals(target.getStatus()) || publication(target) == null) {
                invalid("租户扩展只能绑定已发布的关联模块");
            }
            identifierIntegrity.requireUnambiguous(target);
        }
    }

    private Long nullableId(JsonNode field, String property) {
        return field.path(property).isNumber() ? field.path(property).longValue() : null;
    }

    private ArrayNode validateBindings(
            AuthenticatedContext context, ConfiguredModule module, List<ApplicationBindingInput> inputs,
            Set<String> knownFields, boolean serialize) {
        ArrayNode output = objectMapper.createArrayNode();
        Set<Long> usedGrants = new HashSet<>();
        Map<Long, AppDefinition> applications = applicationService.selectList(Wrappers.<AppDefinition>lambdaQuery()
                        .eq(AppDefinition::getContextType, "SYSTEM")
                        .eq(AppDefinition::getOwnerSystemId, context.systemId()))
                .stream().collect(Collectors.toMap(AppDefinition::getId, Function.identity()));
        for (ApplicationBindingInput input : inputs) {
            AppDefinition application = applications.get(input.applicationId());
            if (application == null || !(application.getOwnerTenantId() == null || context.tenantId().equals(application.getOwnerTenantId()))) {
                invalidBinding("应用不属于当前系统租户");
            }
            ObjectNode binding = objectMapper.createObjectNode();
            binding.put("applicationId", application.getId());
            binding.put("applicationCode", application.getCode());
            binding.put("applicationName", application.getName());
            ArrayNode grantIds = binding.putArray("grantIds");
            ArrayNode actions = binding.putArray("actionCodes");
            ArrayNode fields = binding.putArray("fieldCodes");
            Set<String> bindingFields = new HashSet<>();
            for (Long grantId : input.grantIds()) {
                if (!usedGrants.add(grantId)) invalidBinding("同一授权不能重复绑定");
                AppGrant grant = grantService.selectById(grantId);
                if (grant == null || !application.getId().equals(grant.getApplicationId())
                        || !"ACTIVE".equals(grant.getStatus()) || !context.systemId().equals(grant.getTargetSystemId())
                        || !context.tenantId().equals(grant.getTargetTenantId())
                        || !"MODULE".equals(grant.getResourceType()) || !module.getCode().equals(grant.getResourceId())) {
                    invalidBinding("模块应用绑定只能引用当前租户已有的有效模块授权");
                }
                grantIds.add(grantId);
                actions.add(grant.getActionCode());
                grantFieldService.selectList(Wrappers.<AppGrantField>lambdaQuery()
                                .eq(AppGrantField::getGrantId, grantId)).stream()
                        .map(AppGrantField::getFieldCode).filter(knownFields::contains).forEach(bindingFields::add);
            }
            bindingFields.stream().sorted().forEach(fields::add);
            binding.put("source", "TENANT");
            output.add(binding);
        }
        return output;
    }

    private List<ApplicationBindingInput> readBindings(JsonNode document) {
        List<ApplicationBindingInput> bindings = new ArrayList<>();
        document.path("applicationBindings").forEach(binding -> {
            List<Long> grants = new ArrayList<>();
            binding.path("grantIds").forEach(id -> grants.add(id.asLong()));
            bindings.add(new ApplicationBindingInput(binding.path("applicationId").asLong(), grants));
        });
        return bindings;
    }

    private List<ApplicationGrantOption> grantOptions(AuthenticatedContext context, ConfiguredModule module) {
        Map<Long, AppDefinition> applications = applicationService.selectList(Wrappers.<AppDefinition>lambdaQuery()
                        .eq(AppDefinition::getContextType, "SYSTEM")
                        .eq(AppDefinition::getOwnerSystemId, context.systemId()))
                .stream().filter(app -> app.getOwnerTenantId() == null || context.tenantId().equals(app.getOwnerTenantId()))
                .collect(Collectors.toMap(AppDefinition::getId, Function.identity()));
        return grantService.selectList(Wrappers.<AppGrant>lambdaQuery()
                        .eq(AppGrant::getTargetSystemId, context.systemId())
                        .eq(AppGrant::getTargetTenantId, context.tenantId())
                        .eq(AppGrant::getResourceType, "MODULE")
                        .eq(AppGrant::getResourceId, module.getCode())
                        .eq(AppGrant::getStatus, "ACTIVE")).stream()
                .filter(grant -> applications.containsKey(grant.getApplicationId()))
                .map(grant -> {
                    AppDefinition app = applications.get(grant.getApplicationId());
                    List<String> fields = grantFieldService.selectList(Wrappers.<AppGrantField>lambdaQuery()
                                    .eq(AppGrantField::getGrantId, grant.getId())).stream()
                            .map(AppGrantField::getFieldCode).sorted().toList();
                    return new ApplicationGrantOption(app.getId(), app.getCode(), app.getName(), grant.getId(),
                            grant.getActionCode(), fields);
                }).sorted(Comparator.comparing(ApplicationGrantOption::applicationName)
                        .thenComparing(ApplicationGrantOption::actionCode)).toList();
    }

    private ObjectNode decorateBase(ObjectNode base, SystemTenant mainTenant) {
        return runtimeService.decorateBase(base, mainTenant.getId());
    }

    private BaseConfiguration baseConfiguration(ConfiguredModule module) {
        ConfiguredModulePublication publication = publication(module);
        if (publication == null) notFound("主租户模块尚未发布");
        ConfiguredModuleVersion version = moduleVersionService.selectById(publication.getCurrentVersionId());
        if (version == null || !module.getId().equals(version.getModuleId())) notFound("主租户模块发布版本无效");
        JsonNode configuration = parse(version.getSnapshotJson());
        if (!configuration.isObject()) throw new IllegalStateException("Base module snapshot must be an object");
        return new BaseConfiguration(version, (ObjectNode) configuration);
    }

    private ConfiguredModulePublication publication(ConfiguredModule module) {
        return publicationService.selectList(Wrappers.<ConfiguredModulePublication>lambdaQuery()
                        .eq(ConfiguredModulePublication::getSystemId, module.getSystemId())
                        .eq(ConfiguredModulePublication::getOwnerTenantId, module.getOwnerTenantId())
                        .eq(ConfiguredModulePublication::getModuleId, module.getId()))
                .stream().findFirst().orElse(null);
    }

    private ExtensionContext requireExtensionContext(AuthenticatedContext context) {
        if (context.systemId() == null || context.tenantId() == null || context.memberId() == null) {
            throw new DomainException("SYSTEM_CONTEXT_REQUIRED", "请先进入系统租户", HttpStatus.CONFLICT);
        }
        SystemTenant current = tenantService.selectById(context.tenantId());
        SystemTenant main = tenantService.selectList(Wrappers.<SystemTenant>lambdaQuery()
                        .eq(SystemTenant::getSystemId, context.systemId()).eq(SystemTenant::getMain, true)
                        .eq(SystemTenant::getStatus, "ACTIVE"))
                .stream().findFirst().orElseThrow(() -> new DomainException(
                        "MAIN_TENANT_NOT_FOUND", "系统缺少有效主租户", HttpStatus.CONFLICT));
        if (current == null || !context.systemId().equals(current.getSystemId())) notFound("当前租户不存在");
        if (Boolean.TRUE.equals(current.getMain())) {
            throw new DomainException("TENANT_EXTENSION_NOT_AVAILABLE", "主租户直接维护基础配置，不创建租户扩展", HttpStatus.CONFLICT);
        }
        return new ExtensionContext(current, main);
    }

    private ConfiguredModule requireBaseModule(AuthenticatedContext context, ExtensionContext scope, Long moduleId) {
        ConfiguredModule module = moduleService.selectById(moduleId);
        if (module == null || "DELETED".equals(module.getStatus()) || !context.systemId().equals(module.getSystemId())
                || !scope.mainTenant().getId().equals(module.getOwnerTenantId())) notFound("主租户基础模块不存在");
        return module;
    }

    private CfgTenantExtension findExtension(AuthenticatedContext context, Long moduleId) {
        return extensionService.selectList(Wrappers.<CfgTenantExtension>lambdaQuery()
                        .eq(CfgTenantExtension::getSystemId, context.systemId())
                        .eq(CfgTenantExtension::getTenantId, context.tenantId())
                        .eq(CfgTenantExtension::getBaseModuleId, moduleId))
                .stream().findFirst().orElse(null);
    }

    private CfgTenantExtension requireExtension(AuthenticatedContext context, Long moduleId) {
        CfgTenantExtension extension = findExtension(context, moduleId);
        if (extension == null) notFound("租户扩展不存在");
        return extension;
    }

    private ConfiguredModuleField requireExtensionField(AuthenticatedContext context, Long moduleId, Long fieldId) {
        ConfiguredModuleField field = fieldService.selectById(fieldId);
        if (field == null || !context.systemId().equals(field.getSystemId())
                || !context.tenantId().equals(field.getOwnerTenantId()) || !moduleId.equals(field.getModuleId())) {
            notFound("租户扩展字段不存在");
        }
        return field;
    }

    private ObjectNode emptyDocument(AuthenticatedContext context) {
        ObjectNode document = objectMapper.createObjectNode();
        document.put("schemaVersion", 1);
        ObjectNode source = document.putObject("source");
        source.put("type", "TENANT");
        source.put("tenantId", context.tenantId());
        document.putArray("fields");
        document.putArray("pages");
        document.putArray("actions");
        document.putArray("applicationBindings");
        return document;
    }

    private JsonNode parse(String value) {
        try { return objectMapper.readTree(value); }
        catch (Exception exception) { throw new IllegalStateException("Cannot parse tenant extension JSON", exception); }
    }

    private String write(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("Cannot serialize tenant extension JSON", exception); }
    }

    private String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 unavailable", exception); }
    }

    private void invalid(String message) { throw new DomainException("TENANT_EXTENSION_INVALID", message, HttpStatus.UNPROCESSABLE_ENTITY); }
    private void invalidBinding(String message) { throw new DomainException("APPLICATION_BINDING_SCOPE_INVALID", message, HttpStatus.UNPROCESSABLE_ENTITY); }
    private void conflict(String message) { throw new DomainException("TENANT_EXTENSION_VERSION_CONFLICT", message, HttpStatus.CONFLICT); }
    private void notFound(String message) { throw new DomainException("CONFIGURATION_NOT_FOUND", message, HttpStatus.NOT_FOUND); }

    private record ExtensionContext(SystemTenant currentTenant, SystemTenant mainTenant) {}
    private record BaseConfiguration(ConfiguredModuleVersion version, ObjectNode configuration) {}
}
