package com.unique.unexamine.moduleconfig.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModule;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModuleAction;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModuleField;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModuleGroup;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModulePage;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModulePublication;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModuleVersion;
import com.unique.unexamine.moduleconfig.base.entity.CfgModuleMenu;
import com.unique.unexamine.moduleconfig.base.entity.CfgModuleRule;
import com.unique.unexamine.moduleconfig.base.entity.CfgQueryIndex;
import com.unique.unexamine.moduleconfig.base.entity.CfgQueryIndexField;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleActionBaseService;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleBaseService;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleFieldBaseService;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleGroupBaseService;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModulePageBaseService;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModulePublicationBaseService;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleVersionBaseService;
import com.unique.unexamine.moduleconfig.base.service.CfgModuleMenuBaseService;
import com.unique.unexamine.moduleconfig.base.service.CfgModuleRuleBaseService;
import com.unique.unexamine.moduleconfig.base.service.CfgQueryIndexBaseService;
import com.unique.unexamine.moduleconfig.base.service.CfgQueryIndexFieldBaseService;
import com.unique.unexamine.system.base.entity.SystemTenant;
import com.unique.unexamine.system.base.service.SystemTenantBaseService;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.authorization.manage.PermissionChecker;
import com.unique.unexamine.authorization.manage.ChannelFieldPolicyResolver;
import com.unique.unexamine.authorization.manage.FieldAccessDecision;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ModulePublicationService {
    private static final Set<String> REQUIRED_PAGES = Set.of("LIST", "FORM", "DETAIL");
    private static final Set<String> REQUIRED_ACTIONS = Set.of("LIST", "DETAIL", "CREATE", "UPDATE");

    private final ConfiguredModuleBaseService moduleService;
    private final ConfiguredModuleFieldBaseService fieldService;
    private final ConfiguredModuleGroupBaseService groupService;
    private final ConfiguredModulePageBaseService pageService;
    private final CfgModuleMenuBaseService menuService;
    private final CfgModuleRuleBaseService ruleService;
    private final CfgQueryIndexBaseService indexService;
    private final CfgQueryIndexFieldBaseService indexFieldService;
    private final ConfiguredModuleActionBaseService actionService;
    private final ConfiguredModuleVersionBaseService versionService;
    private final ConfiguredModulePublicationBaseService publicationService;
    private final PermissionChecker permissionChecker;
    private final ChannelFieldPolicyResolver fieldPolicyResolver;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbc;
    private final StructuredRuleEvaluator ruleEvaluator;
    private final SystemTenantBaseService tenantService;
    private final TenantExtensionRuntimeService tenantExtensionRuntimeService;

    public ModulePublicationService(
            ConfiguredModuleBaseService moduleService,
            ConfiguredModuleFieldBaseService fieldService,
            ConfiguredModuleGroupBaseService groupService,
            ConfiguredModulePageBaseService pageService,
            CfgModuleMenuBaseService menuService,
            CfgModuleRuleBaseService ruleService,
            CfgQueryIndexBaseService indexService,
            CfgQueryIndexFieldBaseService indexFieldService,
            ConfiguredModuleActionBaseService actionService,
            ConfiguredModuleVersionBaseService versionService,
            ConfiguredModulePublicationBaseService publicationService,
            PermissionChecker permissionChecker,
            ChannelFieldPolicyResolver fieldPolicyResolver,
            AuditRecorder auditRecorder,
            ObjectMapper objectMapper,
            JdbcTemplate jdbc,
            StructuredRuleEvaluator ruleEvaluator,
            SystemTenantBaseService tenantService,
            TenantExtensionRuntimeService tenantExtensionRuntimeService) {
        this.moduleService = moduleService;
        this.fieldService = fieldService;
        this.groupService = groupService;
        this.pageService = pageService;
        this.menuService = menuService;
        this.ruleService = ruleService;
        this.indexService = indexService;
        this.indexFieldService = indexFieldService;
        this.actionService = actionService;
        this.versionService = versionService;
        this.publicationService = publicationService;
        this.permissionChecker = permissionChecker;
        this.fieldPolicyResolver = fieldPolicyResolver;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
        this.jdbc = jdbc;
        this.ruleEvaluator = ruleEvaluator;
        this.tenantService = tenantService;
        this.tenantExtensionRuntimeService = tenantExtensionRuntimeService;
    }

    @Transactional(readOnly = true)
    public PublicationCheckResult check(AuthenticatedContext context, Long moduleId) {
        ConfiguredModule module = requireOwnedModule(context, moduleId);
        SnapshotParts parts = parts(module);
        List<PublicationCheckIssue> issues = validate(module, parts);
        return new PublicationCheckResult(issues.isEmpty(), module.getDraftRevision(), issues, projectionPlans(parts));
    }

    @Transactional
    public PublishedModuleResult publish(
            AuthenticatedContext context,
            Long moduleId,
            PublishModuleRequest request,
            String traceId) {
        ConfiguredModule module = requireOwnedModule(context, moduleId);
        if (!request.expectedDraftRevision().equals(module.getDraftRevision())) {
            throw conflict("草稿已经变化，请重新执行发布检查");
        }
        SnapshotParts parts = parts(module);
        List<PublicationCheckIssue> issues = validate(module, parts);
        if (!issues.isEmpty()) {
            auditRecorder.recordFailure(traceId, context.accountId(), "MODULE_PUBLISH", "PUBLICATION_CHECK_FAILED",
                    Map.of("moduleId", moduleId, "issues", issues));
            throw new DomainException("PUBLICATION_CHECK_FAILED", "发布检查未通过", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (!versionService.selectList(Wrappers.<ConfiguredModuleVersion>lambdaQuery()
                .eq(ConfiguredModuleVersion::getModuleId, moduleId)
                .eq(ConfiguredModuleVersion::getDraftRevision, module.getDraftRevision())).isEmpty()) {
            throw conflict("当前草稿修订已经发布");
        }

        int nextVersion = versionService.selectList(Wrappers.<ConfiguredModuleVersion>lambdaQuery()
                        .eq(ConfiguredModuleVersion::getModuleId, moduleId))
                .stream().map(ConfiguredModuleVersion::getVersionNumber).max(Integer::compareTo).orElse(0) + 1;
        ConfiguredModuleVersion version = new ConfiguredModuleVersion();
        version.setSystemId(context.systemId());
        version.setOwnerTenantId(context.tenantId());
        version.setModuleId(moduleId);
        version.setVersionNumber(nextVersion);
        version.setDraftRevision(module.getDraftRevision());
        String snapshotJson = snapshot(module, parts, nextVersion);
        version.setSchemaHash(sha256(snapshotJson));
        version.setSnapshotJson(snapshotJson);
        version.setChangeSummary("发布草稿修订 " + module.getDraftRevision());
        version.setPublishedByMemberId(context.memberId());
        version.setPublishedAt(LocalDateTime.now());
        versionService.insert(version);

        ConfiguredModulePublication publication = publicationService.selectList(
                        Wrappers.<ConfiguredModulePublication>lambdaQuery().eq(ConfiguredModulePublication::getModuleId, moduleId))
                .stream().findFirst().orElse(null);
        if (publication == null) {
            publication = new ConfiguredModulePublication();
            publication.setSystemId(context.systemId());
            publication.setOwnerTenantId(context.tenantId());
            publication.setModuleId(moduleId);
            publication.setCurrentVersionId(version.getId());
            publication.setUpdatedByMemberId(context.memberId());
            publication.setVersion(0);
            publicationService.insert(publication);
        } else {
            publication.setCurrentVersionId(version.getId());
            publication.setUpdatedByMemberId(context.memberId());
            if (publicationService.updateById(publication) == 0) {
                throw conflict("当前发布版本已被其他操作修改");
            }
        }
        module.setStatus("ACTIVE");
        if (moduleService.updateById(module) == 0) {
            throw conflict("模块草稿已被其他操作修改");
        }
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "MODULE_PUBLISHED", "MODULE_VERSION", version.getId().toString(), "SUCCESS",
                Map.of("moduleId", moduleId, "versionNumber", nextVersion, "draftRevision", module.getDraftRevision()));
        return new PublishedModuleResult(moduleId, version.getId(), nextVersion, module.getDraftRevision(), version.getPublishedAt());
    }

    @Transactional
    public RuntimeModuleConfiguration rollback(
            AuthenticatedContext context,
            Long moduleId,
            RollbackModuleRequest request,
            String traceId) {
        requireOwnedModule(context, moduleId);
        ConfiguredModuleVersion target = versionService.selectById(request.targetVersionId());
        if (target == null || !moduleId.equals(target.getModuleId()) || !context.systemId().equals(target.getSystemId())
                || !context.tenantId().equals(target.getOwnerTenantId())) {
            throw new DomainException("CONFIGURATION_NOT_FOUND", "目标发布版本不存在", HttpStatus.NOT_FOUND);
        }
        ConfiguredModulePublication publication = publicationService.selectList(
                        Wrappers.<ConfiguredModulePublication>lambdaQuery().eq(ConfiguredModulePublication::getModuleId, moduleId))
                .stream().findFirst().orElseThrow(() -> new DomainException(
                        "CONFIGURATION_NOT_FOUND", "模块尚未发布", HttpStatus.NOT_FOUND));
        if (!request.expectedPublicationVersion().equals(publication.getVersion())) {
            throw conflict("当前发布指针已被其他操作修改");
        }
        if (target.getId().equals(publication.getCurrentVersionId())) {
            throw conflict("目标版本已经是当前运行版本");
        }

        int nextVersion = versionService.selectList(Wrappers.<ConfiguredModuleVersion>lambdaQuery()
                        .eq(ConfiguredModuleVersion::getModuleId, moduleId))
                .stream().map(ConfiguredModuleVersion::getVersionNumber).max(Integer::compareTo).orElse(0) + 1;
        String rollbackSnapshot = rollbackSnapshot(target, nextVersion);
        ConfiguredModuleVersion rollbackVersion = new ConfiguredModuleVersion();
        rollbackVersion.setSystemId(context.systemId());
        rollbackVersion.setOwnerTenantId(context.tenantId());
        rollbackVersion.setModuleId(moduleId);
        rollbackVersion.setVersionNumber(nextVersion);
        rollbackVersion.setDraftRevision(target.getDraftRevision());
        rollbackVersion.setSchemaHash(sha256(rollbackSnapshot));
        rollbackVersion.setSnapshotJson(rollbackSnapshot);
        rollbackVersion.setChangeSummary("回滚自发布版本 v" + target.getVersionNumber());
        rollbackVersion.setPublishedByMemberId(context.memberId());
        rollbackVersion.setPublishedAt(LocalDateTime.now());
        versionService.insert(rollbackVersion);

        publication.setCurrentVersionId(rollbackVersion.getId());
        publication.setUpdatedByMemberId(context.memberId());
        if (publicationService.updateById(publication) == 0) {
            throw conflict("当前发布指针已被其他操作修改");
        }
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "MODULE_PUBLICATION_ROLLED_BACK", "MODULE_VERSION", rollbackVersion.getId().toString(), "SUCCESS",
                Map.of("moduleId", moduleId, "versionNumber", nextVersion,
                        "sourceVersionId", target.getId(), "sourceVersionNumber", target.getVersionNumber()));
        return runtimeByModuleId(context, moduleId, false);
    }

    @Transactional(readOnly = true)
    public List<PublishedVersionSummary> versions(AuthenticatedContext context, Long moduleId) {
        requireOwnedModule(context, moduleId);
        ConfiguredModulePublication publication = publicationService.selectList(
                        Wrappers.<ConfiguredModulePublication>lambdaQuery().eq(ConfiguredModulePublication::getModuleId, moduleId))
                .stream().findFirst().orElse(null);
        Long currentVersionId = publication == null ? null : publication.getCurrentVersionId();
        Integer publicationVersion = publication == null ? null : publication.getVersion();
        return versionService.selectList(Wrappers.<ConfiguredModuleVersion>lambdaQuery()
                        .eq(ConfiguredModuleVersion::getModuleId, moduleId)
                        .orderByDesc(ConfiguredModuleVersion::getVersionNumber))
                .stream().map(version -> new PublishedVersionSummary(
                        version.getId(), version.getVersionNumber(), version.getDraftRevision(), version.getChangeSummary(),
                        version.getPublishedAt(),
                        version.getId().equals(currentVersionId), publicationVersion))
                .toList();
    }

    private String rollbackSnapshot(ConfiguredModuleVersion target, int versionNumber) {
        try {
            JsonNode parsed = objectMapper.readTree(target.getSnapshotJson());
            if (!(parsed instanceof ObjectNode root)) {
                throw new IllegalStateException("Published module snapshot must be an object");
            }
            ObjectNode copied = root.deepCopy();
            copied.put("versionNumber", versionNumber);
            copied.put("rollbackSourceVersionId", target.getId());
            copied.put("rollbackSourceVersionNumber", target.getVersionNumber());
            return objectMapper.writeValueAsString(copied);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot create rollback module snapshot", exception);
        }
    }

    @Transactional(readOnly = true)
    public RuntimeModuleConfiguration runtime(AuthenticatedContext context, String moduleCode, String traceId) {
        RuntimeModuleConfiguration configuration = published(context, moduleCode);
        if (!permissionChecker.allows(context, "MODULE", moduleCode, "LIST")) {
            auditRecorder.recordPermissionDenied(traceId, context.accountId(), context.systemId(), context.tenantId(),
                    context.memberId(), "MODULE:" + moduleCode + ":LIST",
                    Map.of("roleIds", context.roleIds(), "permissions", context.permissions(),
                            "dataScopes", context.dataScopes(), "entry", "RUNTIME_MODULE_CONFIGURATION"));
            throw new DomainException("PERMISSION_DENIED", "没有查看该模块的权限", HttpStatus.FORBIDDEN);
        }
        ObjectNode visible = configuration.configuration().deepCopy();
        ArrayNode actions = objectMapper.createArrayNode();
        configuration.configuration().path("actions").forEach(action -> {
            String actionCode = action.path("code").asText();
            if (permissionChecker.allows(context, "MODULE", moduleCode, actionCode)) {
                actions.add(action.deepCopy());
            }
        });
        visible.set("actions", actions);
        List<String> fieldCodes = new ArrayList<>();
        configuration.configuration().path("fields").forEach(field -> fieldCodes.add(field.path("code").asText()));
        Map<String, FieldAccessDecision> readAccess = fieldPolicyResolver.resolve(
                context, moduleCode, "LIST", "PAGE", fieldCodes);
        Map<String, FieldAccessDecision> createAccess = permissionChecker.allows(context, "MODULE", moduleCode, "CREATE")
                ? fieldPolicyResolver.resolve(context, moduleCode, "CREATE", "PAGE", fieldCodes) : Map.of();
        Map<String, FieldAccessDecision> updateAccess = permissionChecker.allows(context, "MODULE", moduleCode, "UPDATE")
                ? fieldPolicyResolver.resolve(context, moduleCode, "UPDATE", "PAGE", fieldCodes) : Map.of();
        ArrayNode fields = objectMapper.createArrayNode();
        configuration.configuration().path("fields").forEach(field -> {
            ObjectNode guarded = field.deepCopy();
            String code = field.path("code").asText();
            FieldAccessDecision read = readAccess.get(code);
            boolean writable = (createAccess.containsKey(code) && createAccess.get(code).writable())
                    || (updateAccess.containsKey(code) && updateAccess.get(code).writable());
            ObjectNode access = objectMapper.createObjectNode();
            access.put("channel", "PAGE");
            access.put("readable", read != null && read.readable());
            access.put("writable", writable);
            if (read != null && read.maskStrategy() != null) access.put("maskStrategy", read.maskStrategy());
            access.put("reason", read == null ? "字段默认拒绝" : read.reason());
            guarded.set("access", access);
            fields.add(guarded);
        });
        visible.set("fields", fields);
        return new RuntimeModuleConfiguration(configuration.moduleId(), configuration.versionId(),
                configuration.versionNumber(), configuration.publicationVersion(), visible);
    }

    @Transactional(readOnly = true)
    public RuntimeModuleConfiguration published(AuthenticatedContext context, String moduleCode) {
        if (context.systemId() == null || context.tenantId() == null) {
            throw new DomainException("SYSTEM_CONTEXT_REQUIRED", "请先进入系统", HttpStatus.CONFLICT);
        }
        ConfiguredModule module = moduleService.selectList(Wrappers.<ConfiguredModule>lambdaQuery()
                        .eq(ConfiguredModule::getSystemId, context.systemId())
                        .eq(ConfiguredModule::getOwnerTenantId, context.tenantId())
                        .eq(ConfiguredModule::getCode, moduleCode)
                        .eq(ConfiguredModule::getStatus, "ACTIVE"))
                .stream().findFirst().orElse(null);
        if (module == null) {
            Long mainTenantId = mainTenantId(context.systemId());
            module = moduleService.selectList(Wrappers.<ConfiguredModule>lambdaQuery()
                            .eq(ConfiguredModule::getSystemId, context.systemId())
                            .eq(ConfiguredModule::getOwnerTenantId, mainTenantId)
                            .eq(ConfiguredModule::getCode, moduleCode)
                            .eq(ConfiguredModule::getStatus, "ACTIVE"))
                    .stream().findFirst().orElseThrow(() -> new DomainException(
                            "CONFIGURATION_NOT_FOUND", "运行模块不存在或尚未发布", HttpStatus.NOT_FOUND));
        }
        return runtimeByModuleId(context, module.getId(), true);
    }

    private RuntimeModuleConfiguration runtimeByModuleId(AuthenticatedContext context, Long moduleId, boolean enforceContext) {
        ConfiguredModule module = moduleService.selectById(moduleId);
        if (module == null || !context.systemId().equals(module.getSystemId())) {
            throw new DomainException("CONFIGURATION_NOT_FOUND", "运行模块不存在", HttpStatus.NOT_FOUND);
        }
        ConfiguredModulePublication publication = publicationService.selectList(
                        Wrappers.<ConfiguredModulePublication>lambdaQuery()
                                .eq(ConfiguredModulePublication::getSystemId, context.systemId())
                                .eq(ConfiguredModulePublication::getOwnerTenantId, module.getOwnerTenantId())
                                .eq(ConfiguredModulePublication::getModuleId, moduleId))
                .stream().findFirst().orElseThrow(() -> new DomainException(
                        "CONFIGURATION_NOT_FOUND", "模块尚未发布", HttpStatus.NOT_FOUND));
        ConfiguredModuleVersion version = versionService.selectById(publication.getCurrentVersionId());
        if (version == null || (enforceContext && (!context.systemId().equals(version.getSystemId())
                || !module.getOwnerTenantId().equals(version.getOwnerTenantId())))) {
            throw new DomainException("CONFIGURATION_NOT_FOUND", "当前发布版本无效", HttpStatus.NOT_FOUND);
        }
        try {
            JsonNode base = objectMapper.readTree(version.getSnapshotJson());
            JsonNode effective = tenantExtensionRuntimeService.mergePublished(
                    context, moduleId, module.getOwnerTenantId(), base);
            return new RuntimeModuleConfiguration(moduleId, version.getId(), version.getVersionNumber(), publication.getVersion(),
                    effective);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot read published module snapshot", exception);
        }
    }

    private ConfiguredModule requireOwnedModule(AuthenticatedContext context, Long moduleId) {
        if (context.systemId() == null || context.tenantId() == null || context.memberId() == null) {
            throw new DomainException("SYSTEM_CONTEXT_REQUIRED", "请先进入系统", HttpStatus.CONFLICT);
        }
        ConfiguredModule module = moduleService.selectById(moduleId);
        if (module == null || !context.systemId().equals(module.getSystemId()) || !context.tenantId().equals(module.getOwnerTenantId())) {
            throw new DomainException("CONFIGURATION_NOT_FOUND", "模块不存在", HttpStatus.NOT_FOUND);
        }
        return module;
    }

    private Long mainTenantId(Long systemId) {
        return tenantService.selectList(Wrappers.<SystemTenant>lambdaQuery()
                        .eq(SystemTenant::getSystemId, systemId)
                        .eq(SystemTenant::getMain, true)
                        .eq(SystemTenant::getStatus, "ACTIVE"))
                .stream().map(SystemTenant::getId).findFirst().orElseThrow(() -> new DomainException(
                        "MAIN_TENANT_NOT_FOUND", "系统缺少有效主租户", HttpStatus.CONFLICT));
    }

    private SnapshotParts parts(ConfiguredModule module) {
        Long moduleId = module.getId();
        List<CfgQueryIndex> indexes = indexService.selectList(Wrappers.<CfgQueryIndex>lambdaQuery()
                .eq(CfgQueryIndex::getModuleId, moduleId).eq(CfgQueryIndex::getStatus, "ACTIVE")
                .orderByAsc(CfgQueryIndex::getId));
        List<Long> indexIds = indexes.stream().map(CfgQueryIndex::getId).toList();
        List<CfgQueryIndexField> indexFields = indexIds.isEmpty() ? List.of()
                : indexFieldService.selectList(Wrappers.<CfgQueryIndexField>lambdaQuery()
                .in(CfgQueryIndexField::getQueryIndexId, indexIds)
                .orderByAsc(CfgQueryIndexField::getQueryIndexId, CfgQueryIndexField::getSortOrder));
        return new SnapshotParts(
                fieldService.selectList(Wrappers.<ConfiguredModuleField>lambdaQuery()
                        .eq(ConfiguredModuleField::getModuleId, moduleId)
                        .eq(ConfiguredModuleField::getOwnerTenantId, module.getOwnerTenantId())
                        .eq(ConfiguredModuleField::getStatus, "ACTIVE")
                        .orderByAsc(ConfiguredModuleField::getSortOrder, ConfiguredModuleField::getId)),
                pageService.selectList(Wrappers.<ConfiguredModulePage>lambdaQuery()
                        .eq(ConfiguredModulePage::getModuleId, moduleId).eq(ConfiguredModulePage::getStatus, "ACTIVE")
                        .orderByAsc(ConfiguredModulePage::getId)),
                menuService.selectList(Wrappers.<CfgModuleMenu>lambdaQuery()
                        .eq(CfgModuleMenu::getModuleId, moduleId).eq(CfgModuleMenu::getStatus, "ACTIVE")
                        .orderByAsc(CfgModuleMenu::getSortOrder, CfgModuleMenu::getId)),
                ruleService.selectList(Wrappers.<CfgModuleRule>lambdaQuery()
                        .eq(CfgModuleRule::getModuleId, moduleId).eq(CfgModuleRule::getStatus, "ACTIVE")
                        .orderByAsc(CfgModuleRule::getSortOrder, CfgModuleRule::getId)),
                indexes,
                indexFields,
                actionService.selectList(Wrappers.<ConfiguredModuleAction>lambdaQuery()
                        .eq(ConfiguredModuleAction::getModuleId, moduleId).eq(ConfiguredModuleAction::getStatus, "ACTIVE")
                        .orderByAsc(ConfiguredModuleAction::getSortOrder, ConfiguredModuleAction::getId)));
    }

    private List<PublicationCheckIssue> validate(ConfiguredModule module, SnapshotParts parts) {
        List<PublicationCheckIssue> issues = new ArrayList<>();
        if (parts.fields().isEmpty()) {
            issues.add(new PublicationCheckIssue("fields", "ACTIVE_FIELD_REQUIRED", "至少配置一个有效字段"));
        }
        for (ConfiguredModuleField field : parts.fields()) {
            String path = "fields." + field.getCode();
            if (!ModuleConfigurationService.FIELD_TYPES.contains(field.getFieldType())) {
                issues.add(new PublicationCheckIssue(path + ".fieldType", "FIELD_TYPE_UNSUPPORTED",
                        "当前阶段不支持该字段类型"));
            }
            if (Set.of("SINGLE_SELECT", "MULTI_SELECT", "STATUS", "TAG").contains(field.getFieldType())
                    && field.getDictionaryId() == null) {
                validateOptions(field, path, issues);
            }
            if (field.getDictionaryId() != null) {
                Integer publications = jdbc.queryForObject(
                        "select count(*) from cfg_dictionary_publication p join cfg_dictionary d on d.id=p.dictionary_id "
                                + "where d.id=? and d.system_id=? and d.owner_tenant_id=?",
                        Integer.class, field.getDictionaryId(), module.getSystemId(), module.getOwnerTenantId());
                if (publications == null || publications == 0) {
                    issues.add(new PublicationCheckIssue(path + ".dictionaryId", "DICTIONARY_NOT_PUBLISHED",
                            "绑定字典尚未发布，运行态不能引用草稿"));
                }
            }
        }
        Set<String> pages = parts.pages().stream().map(ConfiguredModulePage::getPageType).collect(java.util.stream.Collectors.toSet());
        REQUIRED_PAGES.stream().sorted().filter(type -> !pages.contains(type)).forEach(type ->
                issues.add(new PublicationCheckIssue("pages." + type, "PAGE_REQUIRED", "缺少" + type + "页面")));
        validatePageLayouts(parts, issues);
        validateMenus(module, parts, issues);
        validateRulesAndIndexes(parts, issues);
        Set<String> actions = parts.actions().stream().map(ConfiguredModuleAction::getCode).collect(java.util.stream.Collectors.toSet());
        REQUIRED_ACTIONS.stream().sorted().filter(code -> !actions.contains(code)).forEach(code ->
                issues.add(new PublicationCheckIssue("actions." + code, "ACTION_REQUIRED", "缺少" + code + "动作")));
        validateConversionActions(module, parts, issues);
        validatePublishedCompatibility(module, parts, issues);
        return issues;
    }

    private void validateConversionActions(
            ConfiguredModule sourceModule, SnapshotParts parts, List<PublicationCheckIssue> issues) {
        ConfiguredModuleAction action = parts.actions().stream()
                .filter(candidate -> "CONVERT".equals(candidate.getCode()) && "ACTIVE".equals(candidate.getStatus()))
                .findFirst().orElse(null);
        if (action == null) return;
        JsonNode config;
        try {
            config = objectMapper.readTree(action.getConfigJson() == null ? "{}" : action.getConfigJson());
        } catch (Exception exception) {
            issues.add(new PublicationCheckIssue("actions.CONVERT.config", "CONVERSION_CONFIG_INVALID",
                    "转化动作配置不是有效 JSON"));
            return;
        }
        List<JsonNode> targets = new ArrayList<>();
        if (config.path("targets").isArray()) config.path("targets").forEach(targets::add);
        else if (config.path("targetModuleCode").isTextual()) targets.add(config);
        if (targets.isEmpty()) return;

        Set<String> sourceFields = parts.fields().stream().filter(field -> "ACTIVE".equals(field.getStatus()))
                .map(ConfiguredModuleField::getCode).collect(java.util.stream.Collectors.toSet());
        Set<String> seenTargets = new java.util.HashSet<>();
        for (int index = 0; index < targets.size(); index++) {
            JsonNode target = targets.get(index);
            String path = "actions.CONVERT.targets[" + index + "]";
            String targetCode = target.path("moduleCode").asText(target.path("targetModuleCode").asText());
            if (targetCode.isBlank()) {
                issues.add(new PublicationCheckIssue(path + ".moduleCode", "CONVERSION_TARGET_REQUIRED",
                        "转化目标模块不能为空"));
                continue;
            }
            if (!seenTargets.add(targetCode)) {
                issues.add(new PublicationCheckIssue(path + ".moduleCode", "CONVERSION_TARGET_DUPLICATE",
                        "同一目标模块只能配置一次"));
                continue;
            }
            ConfiguredModule targetModule = moduleService.selectList(Wrappers.<ConfiguredModule>lambdaQuery()
                            .eq(ConfiguredModule::getSystemId, sourceModule.getSystemId())
                            .eq(ConfiguredModule::getCode, targetCode))
                    .stream().findFirst().orElse(null);
            if (targetModule == null || targetModule.getId().equals(sourceModule.getId())) {
                issues.add(new PublicationCheckIssue(path + ".moduleCode", "CONVERSION_TARGET_INVALID",
                        "目标模块不存在、跨系统或与来源模块相同"));
                continue;
            }
            ConfiguredModulePublication publication = publicationService.selectList(
                            Wrappers.<ConfiguredModulePublication>lambdaQuery()
                                    .eq(ConfiguredModulePublication::getModuleId, targetModule.getId()))
                    .stream().findFirst().orElse(null);
            if (publication == null || publication.getCurrentVersionId() == null) {
                issues.add(new PublicationCheckIssue(path + ".moduleCode", "CONVERSION_TARGET_NOT_PUBLISHED",
                        "目标模块尚未发布"));
                continue;
            }
            ConfiguredModuleVersion version = versionService.selectById(publication.getCurrentVersionId());
            JsonNode targetSnapshot;
            try {
                targetSnapshot = objectMapper.readTree(version.getSnapshotJson());
            } catch (Exception exception) {
                issues.add(new PublicationCheckIssue(path, "CONVERSION_TARGET_SNAPSHOT_INVALID",
                        "目标模块发布快照不可读"));
                continue;
            }
            Map<String, JsonNode> targetFields = new java.util.LinkedHashMap<>();
            targetSnapshot.path("fields").forEach(field -> {
                if ("ACTIVE".equals(field.path("status").asText())) targetFields.put(field.path("code").asText(), field);
            });
            JsonNode mappings = target.path("fieldMappings");
            if (!mappings.isObject()) {
                issues.add(new PublicationCheckIssue(path + ".fieldMappings", "CONVERSION_MAPPING_REQUIRED",
                        "转化目标必须配置字段映射"));
                continue;
            }
            targetFields.forEach((fieldCode, field) -> {
                if (field.path("required").asBoolean(false) && !mappings.path(fieldCode).isTextual()) {
                    issues.add(new PublicationCheckIssue(path + ".fieldMappings." + fieldCode,
                            "CONVERSION_REQUIRED_FIELD_UNMAPPED", "目标必填字段 " + fieldCode + " 未配置来源"));
                }
            });
            mappings.fields().forEachRemaining(mapping -> {
                if (!targetFields.containsKey(mapping.getKey())) {
                    issues.add(new PublicationCheckIssue(path + ".fieldMappings." + mapping.getKey(),
                            "CONVERSION_TARGET_FIELD_MISSING", "目标字段不存在或已停用"));
                }
                if (!mapping.getValue().isTextual() || !sourceFields.contains(mapping.getValue().asText())) {
                    issues.add(new PublicationCheckIssue(path + ".fieldMappings." + mapping.getKey(),
                            "CONVERSION_SOURCE_FIELD_MISSING", "来源字段不存在或已停用"));
                }
            });
        }
    }

    private void validatePublishedCompatibility(ConfiguredModule module, SnapshotParts parts,
                                                 List<PublicationCheckIssue> issues) {
        ConfiguredModulePublication publication = publicationService.selectList(
                        Wrappers.<ConfiguredModulePublication>lambdaQuery()
                                .eq(ConfiguredModulePublication::getModuleId, module.getId()))
                .stream().findFirst().orElse(null);
        if (publication == null) return;
        ConfiguredModuleVersion current = versionService.selectById(publication.getCurrentVersionId());
        if (current == null || current.getSnapshotJson() == null) return;
        Map<String, ConfiguredModuleField> active = parts.fields().stream()
                .collect(java.util.stream.Collectors.toMap(ConfiguredModuleField::getCode, field -> field));
        try {
            for (JsonNode publishedField : objectMapper.readTree(current.getSnapshotJson()).path("fields")) {
                String code = publishedField.path("code").asText();
                long fieldId = publishedField.path("id").asLong();
                ConfiguredModuleField draftField = active.get(code);
                if (draftField == null) {
                    Integer values = jdbc.queryForObject("select count(*) from biz_record_value v "
                                    + "join biz_record r on r.id=v.record_id where r.module_id=? and r.deleted=0 and v.field_id=?",
                            Integer.class, module.getId(), fieldId);
                    if (values != null && values > 0) {
                        issues.add(new PublicationCheckIssue("fields." + code,
                                "FIELD_REMOVAL_REQUIRES_MIGRATION",
                                "字段已有 " + values + " 条运行数据，停用前必须完成迁移并保留历史快照"));
                    }
                    continue;
                }
                if (!publishedField.path("required").asBoolean(false) && Boolean.TRUE.equals(draftField.getRequired())) {
                    Integer missing = jdbc.queryForObject("select count(*) from biz_record r left join biz_record_value v "
                                    + "on v.record_id=r.id and v.field_id=? where r.module_id=? and r.deleted=0 and "
                                    + "(v.id is null or (v.value_text is null and v.value_number is null and v.value_date is null "
                                    + "and v.value_datetime is null and v.value_boolean is null and v.value_reference_id is null "
                                    + "and v.value_file_id is null and v.value_json is null))",
                            Integer.class, draftField.getId(), module.getId());
                    if (missing != null && missing > 0) {
                        issues.add(new PublicationCheckIssue("fields." + code + ".required",
                                "REQUIRED_FIELD_DATA_MIGRATION_REQUIRED",
                                "仍有 " + missing + " 条运行数据缺少必填值，完成迁移前不能发布"));
                    }
                }
            }
        } catch (JsonProcessingException exception) {
            issues.add(new PublicationCheckIssue("publishedVersion", "PUBLISHED_SNAPSHOT_INVALID",
                    "当前发布快照无法解析，禁止覆盖并需要运维修复"));
        }
    }

    private void validatePageLayouts(SnapshotParts parts, List<PublicationCheckIssue> issues) {
        Map<String, ConfiguredModuleField> activeFields = parts.fields().stream()
                .collect(java.util.stream.Collectors.toMap(ConfiguredModuleField::getCode, field -> field));
        for (ConfiguredModulePage page : parts.pages()) {
            String path = "pages." + page.getPageType() + ".layout.fieldCodes";
            try {
                JsonNode layout = objectMapper.readTree(page.getLayoutJson());
                int schemaVersion = layout.path("schemaVersion").asInt(1);
                if (schemaVersion != 1) {
                    issues.add(new PublicationCheckIssue("pages." + page.getPageType() + ".layout.schemaVersion",
                            "PAGE_SCHEMA_VERSION_UNSUPPORTED", "当前只支持页面 Schema v1"));
                }
                if (!REQUIRED_PAGES.contains(page.getPageType())) {
                    JsonNode specialComponents = layout.path("components");
                    boolean dedicated = specialComponents.isArray() && !specialComponents.isEmpty();
                    if (dedicated) {
                        for (JsonNode component : specialComponents) {
                            String type = component.path("type").asText().strip().toUpperCase(java.util.Locale.ROOT);
                            if (type.isEmpty() || "SECTION".equals(type) || "FORM".equals(type) || "TABLE".equals(type)) {
                                dedicated = false;
                                break;
                            }
                        }
                    }
                    if (!dedicated) {
                        issues.add(new PublicationCheckIssue("pages." + page.getPageType() + ".layout.components",
                                "SPECIAL_PAGE_DESIGN_REQUIRED",
                                "特殊页面必须配置专属组件、状态和失败反馈，不能使用通用表单、表格或空白占位"));
                    }
                }
                JsonNode fieldCodes = layout.path("fieldCodes");
                if (fieldCodes.isMissingNode()) {
                    continue;
                }
                if (!fieldCodes.isArray()) {
                    issues.add(new PublicationCheckIssue(path, "PAGE_FIELD_CODES_INVALID", "页面字段必须是数组"));
                    continue;
                }
                Set<String> visible = new java.util.HashSet<>();
                for (JsonNode fieldCode : fieldCodes) {
                    String code = fieldCode.asText().strip();
                    if (!activeFields.containsKey(code)) {
                        issues.add(new PublicationCheckIssue(path, "PAGE_FIELD_NOT_ACTIVE", "页面包含不存在或已停用的字段：" + code));
                    } else if (!visible.add(code)) {
                        issues.add(new PublicationCheckIssue(path, "PAGE_FIELD_DUPLICATED", "页面字段不能重复：" + code));
                    }
                }
                JsonNode components = layout.path("components");
                if (!components.isMissingNode() && !components.isArray()) {
                    issues.add(new PublicationCheckIssue("pages." + page.getPageType() + ".layout.components",
                            "PAGE_COMPONENTS_INVALID", "页面组件必须是数组"));
                } else if (components.isArray()) {
                    Set<String> componentIds = new java.util.HashSet<>();
                    for (JsonNode component : components) {
                        String componentId = component.path("id").asText().strip();
                        if (componentId.isEmpty() || !componentIds.add(componentId)) {
                            issues.add(new PublicationCheckIssue("pages." + page.getPageType() + ".layout.components",
                                    "PAGE_COMPONENT_ID_INVALID", "页面组件必须包含不重复的稳定编码"));
                        }
                        JsonNode componentFields = component.path("fieldCodes");
                        if (!componentFields.isMissingNode() && !componentFields.isArray()) {
                            issues.add(new PublicationCheckIssue("pages." + page.getPageType() + ".layout.components." + componentId,
                                    "PAGE_COMPONENT_FIELDS_INVALID", "组件字段必须是数组"));
                        } else if (componentFields.isArray()) {
                            for (JsonNode fieldCode : componentFields) {
                                String code = fieldCode.asText().strip();
                                if (!activeFields.containsKey(code)) {
                                    issues.add(new PublicationCheckIssue("pages." + page.getPageType() + ".layout.components." + componentId,
                                            "PAGE_FIELD_NOT_ACTIVE", "组件包含不存在或已停用的字段：" + code));
                                }
                            }
                        }
                    }
                }
                if ("FORM".equals(page.getPageType())) {
                    activeFields.values().stream().filter(ConfiguredModuleField::getRequired)
                            .map(ConfiguredModuleField::getCode).sorted().filter(code -> !visible.contains(code))
                            .forEach(code -> issues.add(new PublicationCheckIssue(path,
                                    "REQUIRED_FIELD_NOT_IN_FORM", "表单页面不能隐藏必填字段：" + code)));
                }
            } catch (Exception exception) {
                issues.add(new PublicationCheckIssue("pages." + page.getPageType() + ".layout",
                        "PAGE_LAYOUT_INVALID", "页面配置格式不正确"));
            }
        }
    }

    private void validateMenus(ConfiguredModule module, SnapshotParts parts, List<PublicationCheckIssue> issues) {
        if (parts.menus().isEmpty()) {
            issues.add(new PublicationCheckIssue("menus", "ACTIVE_MENU_REQUIRED", "模块至少需要一个有效运行菜单"));
            return;
        }
        for (CfgModuleMenu menu : parts.menus()) {
            String path = "menus." + menu.getCode();
            if (!("/runtime/" + module.getCode()).equals(menu.getRoutePath())) {
                issues.add(new PublicationCheckIssue(path + ".routePath", "MENU_ROUTE_INVALID",
                        "运行菜单必须绑定当前模块发布入口 /runtime/" + module.getCode()));
            }
            if (menu.getParentId() != null) {
                Integer parents = jdbc.queryForObject(
                        "select count(*) from cfg_module_menu where id=? and system_id=? and owner_tenant_id=? and status='ACTIVE'",
                        Integer.class, menu.getParentId(), module.getSystemId(), module.getOwnerTenantId());
                if (parents == null || parents == 0) {
                    issues.add(new PublicationCheckIssue(path + ".parentId", "MENU_PARENT_INVALID",
                            "上级菜单不存在、已停用或不在当前系统租户"));
                }
                Set<Long> chain = new java.util.HashSet<>();
                boolean cycle = false;
                Long cursorId = menu.getId();
                while (cursorId != null) {
                    if (!chain.add(cursorId)) {
                        cycle = true;
                        break;
                    }
                    CfgModuleMenu cursor = cursorId.equals(menu.getId()) ? menu : menuService.selectById(cursorId);
                    cursorId = cursor == null ? null : cursor.getParentId();
                }
                if (cycle) {
                    issues.add(new PublicationCheckIssue(path + ".parentId", "MENU_HIERARCHY_CYCLE", "菜单层级不能形成循环"));
                }
            }
        }
    }

    private void validateRulesAndIndexes(SnapshotParts parts, List<PublicationCheckIssue> issues) {
        Map<Long, ConfiguredModuleField> fieldsById = parts.fields().stream()
                .collect(java.util.stream.Collectors.toMap(ConfiguredModuleField::getId, field -> field));
        Set<String> fieldCodes = parts.fields().stream().map(ConfiguredModuleField::getCode)
                .collect(java.util.stream.Collectors.toSet());
        for (CfgModuleRule rule : parts.rules()) {
            if (!"EXECUTED".equals(rule.getTestStatus())) {
                issues.add(new PublicationCheckIssue("rules." + rule.getCode(), "RULE_TEST_REQUIRED",
                        "规则定义变更后必须使用样例数据重新预演"));
            }
            try {
                List<String> ruleIssues = ruleEvaluator.inspect(objectMapper.readTree(rule.getExpressionText()), fieldCodes);
                ruleIssues.forEach(message -> issues.add(new PublicationCheckIssue("rules." + rule.getCode(),
                        "RULE_DEFINITION_INVALID", message)));
            } catch (Exception exception) {
                issues.add(new PublicationCheckIssue("rules." + rule.getCode(), "RULE_DEFINITION_INVALID",
                        "规则定义无法解析"));
            }
        }
        Map<Long, List<CfgQueryIndexField>> indexFields = parts.indexFields().stream()
                .collect(java.util.stream.Collectors.groupingBy(CfgQueryIndexField::getQueryIndexId));
        for (CfgQueryIndex index : parts.indexes()) {
            List<CfgQueryIndexField> configuredFields = indexFields.getOrDefault(index.getId(), List.of());
            if (configuredFields.isEmpty()) {
                issues.add(new PublicationCheckIssue("indexes." + index.getCode(), "QUERY_INDEX_FIELD_REQUIRED",
                        "查询索引至少需要一个有效字段"));
                continue;
            }
            for (CfgQueryIndexField indexField : configuredFields) {
                if (!fieldsById.containsKey(indexField.getFieldId())) {
                    issues.add(new PublicationCheckIssue("indexes." + index.getCode(), "QUERY_INDEX_FIELD_NOT_ACTIVE",
                            "查询索引引用不存在或已停用字段"));
                }
            }
        }
    }

    private List<String> projectionPlans(SnapshotParts parts) {
        Map<Long, String> fieldCodes = parts.fields().stream()
                .collect(java.util.stream.Collectors.toMap(ConfiguredModuleField::getId, ConfiguredModuleField::getCode));
        Map<Long, List<CfgQueryIndexField>> indexFields = parts.indexFields().stream()
                .collect(java.util.stream.Collectors.groupingBy(CfgQueryIndexField::getQueryIndexId));
        return parts.indexes().stream().map(index -> {
            String fields = indexFields.getOrDefault(index.getId(), List.of()).stream()
                    .map(field -> fieldCodes.getOrDefault(field.getFieldId(), "<missing>"))
                    .collect(java.util.stream.Collectors.joining(", "));
            return index.getCode() + ": biz_record_index(" + fields + ") -> SHA-256 -> "
                    + (Boolean.TRUE.equals(index.getUniqueIndex()) ? "租户模块唯一约束" : "查询投影");
        }).toList();
    }

    private void validateOptions(
            ConfiguredModuleField field,
            String path,
            List<PublicationCheckIssue> issues) {
        try {
            JsonNode options = objectMapper.readTree(field.getConfigJson()).path("options");
            if (!options.isArray() || options.isEmpty()) {
                issues.add(new PublicationCheckIssue(path + ".config.options", "ACTIVE_OPTION_REQUIRED",
                        "选项字段至少需要一个选项"));
                return;
            }
            Set<String> values = new java.util.HashSet<>();
            boolean activeFound = false;
            for (JsonNode option : options) {
                String value = option.path("value").asText(option.path("code").asText()).strip();
                if (value.isEmpty()) {
                    issues.add(new PublicationCheckIssue(path + ".config.options", "OPTION_VALUE_REQUIRED",
                            "选项值不能为空"));
                } else if (!values.add(value)) {
                    issues.add(new PublicationCheckIssue(path + ".config.options", "OPTION_VALUE_DUPLICATED",
                            "选项值不能重复"));
                }
                if ("ACTIVE".equals(option.path("status").asText("ACTIVE"))
                        && !option.path("disabled").asBoolean(false)) {
                    activeFound = true;
                }
            }
            if (!activeFound) {
                issues.add(new PublicationCheckIssue(path + ".config.options", "ACTIVE_OPTION_REQUIRED",
                        "选项字段至少需要一个有效选项"));
            }
        } catch (Exception exception) {
            issues.add(new PublicationCheckIssue(path + ".config", "FIELD_CONFIG_INVALID", "字段配置格式不正确"));
        }
    }

    private String snapshot(ConfiguredModule module, SnapshotParts parts, int versionNumber) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("schemaVersion", 3);
        root.put("versionNumber", versionNumber);
        root.put("draftRevision", module.getDraftRevision());
        root.set("module", objectMapper.valueToTree(module));
        ConfiguredModuleGroup group = groupService.selectById(module.getGroupId());
        root.set("group", objectMapper.valueToTree(group));
        root.set("fields", objectMapper.valueToTree(parts.fields()));
        root.set("pages", objectMapper.valueToTree(parts.pages()));
        root.set("menus", objectMapper.valueToTree(parts.menus()));
        root.set("rules", objectMapper.valueToTree(parts.rules()));
        root.set("queryIndexes", objectMapper.valueToTree(parts.indexes()));
        root.set("queryIndexFields", objectMapper.valueToTree(parts.indexFields()));
        root.set("actions", objectMapper.valueToTree(parts.actions()));
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize published module snapshot", exception);
        }
    }

    private String sha256(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private DomainException conflict(String message) {
        return new DomainException("PUBLICATION_VERSION_CONFLICT", message, HttpStatus.CONFLICT);
    }

    private record SnapshotParts(
            List<ConfiguredModuleField> fields,
            List<ConfiguredModulePage> pages,
            List<CfgModuleMenu> menus,
            List<CfgModuleRule> rules,
            List<CfgQueryIndex> indexes,
            List<CfgQueryIndexField> indexFields,
            List<ConfiguredModuleAction> actions) {
    }
}
