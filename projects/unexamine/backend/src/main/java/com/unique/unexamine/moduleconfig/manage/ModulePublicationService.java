package com.unique.unexamine.moduleconfig.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.unique.unexamine.moduleconfig.base.entity.CfgDictionary;
import com.unique.unexamine.moduleconfig.base.entity.CfgDictionaryPublication;
import com.unique.unexamine.moduleconfig.base.entity.CfgModuleMenu;
import com.unique.unexamine.moduleconfig.base.entity.CfgModuleRule;
import com.unique.unexamine.moduleconfig.base.entity.CfgQueryIndex;
import com.unique.unexamine.moduleconfig.base.entity.CfgQueryIndexField;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModule;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModuleAction;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModuleField;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModuleGroup;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModulePage;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModulePublication;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModuleVersion;
import com.unique.unexamine.moduleconfig.base.service.CfgDictionaryBaseService;
import com.unique.unexamine.moduleconfig.base.service.CfgDictionaryPublicationBaseService;
import com.unique.unexamine.moduleconfig.base.service.CfgModuleMenuBaseService;
import com.unique.unexamine.moduleconfig.base.service.CfgModuleRuleBaseService;
import com.unique.unexamine.moduleconfig.base.service.CfgQueryIndexBaseService;
import com.unique.unexamine.moduleconfig.base.service.CfgQueryIndexFieldBaseService;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleActionBaseService;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleBaseService;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleFieldBaseService;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleGroupBaseService;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModulePageBaseService;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModulePublicationBaseService;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleVersionBaseService;
import com.unique.unexamine.moduleconfig.manage.repository.ModulePublicationCheckRepository;
import com.unique.unexamine.analytics.manage.LegacyModuleDashboardCompatibility;
import com.unique.unexamine.print.base.entity.PrintTemplate;
import com.unique.unexamine.print.base.entity.PrintTemplatePublication;
import com.unique.unexamine.print.base.service.PrintTemplateBaseService;
import com.unique.unexamine.print.base.service.PrintTemplatePublicationBaseService;
import com.unique.unexamine.print.manage.LegacyPrintCompatibility;
import com.unique.unexamine.foundation.base.entity.CoreSequence;
import com.unique.unexamine.foundation.base.service.CoreSequenceBaseService;
import com.unique.unexamine.system.base.entity.SystemTenant;
import com.unique.unexamine.system.base.service.SystemTenantBaseService;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.authorization.manage.PermissionChecker;
import com.unique.unexamine.authorization.manage.ChannelFieldPolicyResolver;
import com.unique.unexamine.authorization.manage.FieldAccessDecision;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.http.HttpStatus;
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
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 校验并发布模块配置快照，阻断无效引用及会破坏现有运行数据的字段变更。 */
@Service
public class ModulePublicationService {
    private static final Set<String> REQUIRED_PAGES = Set.of("LIST", "FORM", "DETAIL");
    private static final Set<String> REQUIRED_ACTIONS = Set.of("LIST", "DETAIL", "CREATE", "UPDATE");
    /** 发布范围必须能由运行态保存并读回；尚未实现的配置类型保留在草稿中继续开发。 */
    private static final Set<String> SAVABLE_FIELD_TYPES = Set.of(
            "TEXT", "MULTILINE_TEXT", "PHONE", "EMAIL", "URL", "ID_CARD", "BARCODE", "QRCODE", "NUMBER", "PERCENT", "MONEY",
            "RATING", "PROGRESS", "ADDRESS", "LOCATION",
            "DATE", "DATETIME", "DATE_RANGE", "TIME", "TIME_RANGE", "SINGLE_SELECT", "MULTI_SELECT", "CASCADE",
            "BOOLEAN", "MEMBER", "DEPARTMENT", "ATTACHMENT", "IMAGE", "FILE", "FILE_GROUP",
            "AUTO_NUMBER", "REFERENCE", "SUBTABLE", "TAG", "RICH_TEXT", "JSON", "SECRET", "STATUS",
            "SYSTEM_CREATED_BY", "SYSTEM_CREATED_AT", "SYSTEM_UPDATED_BY", "SYSTEM_UPDATED_AT");

    private final ConfiguredModuleBaseService moduleService;
    private final ConfiguredModuleFieldBaseService fieldService;
    private final ConfiguredModuleGroupBaseService groupService;
    private final ConfiguredModulePageBaseService pageService;
    private final CfgModuleMenuBaseService menuService;
    private final CfgDictionaryBaseService dictionaryService;
    private final CfgDictionaryPublicationBaseService dictionaryPublicationService;
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
    private final ModulePublicationCheckRepository publicationChecks;
    private final StructuredRuleEvaluator ruleEvaluator;
    private final SystemTenantBaseService tenantService;
    private final TenantExtensionRuntimeService tenantExtensionRuntimeService;
    private final ModuleIdentifierIntegrity identifierIntegrity;
    private final ModuleConfigurationService configurationService;
    private final CoreSequenceBaseService sequenceService;
    private final PrintTemplateBaseService printTemplateService;
    private final PrintTemplatePublicationBaseService printTemplatePublicationService;
    private final LegacyPrintCompatibility legacyPrintCompatibility;
    private final LegacyModuleDashboardCompatibility legacyModuleDashboardCompatibility;

    public ModulePublicationService(
            ConfiguredModuleBaseService moduleService,
            ConfiguredModuleFieldBaseService fieldService,
            ConfiguredModuleGroupBaseService groupService,
            ConfiguredModulePageBaseService pageService,
            CfgModuleMenuBaseService menuService,
            CfgDictionaryBaseService dictionaryService,
            CfgDictionaryPublicationBaseService dictionaryPublicationService,
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
            ModulePublicationCheckRepository publicationChecks,
            StructuredRuleEvaluator ruleEvaluator,
            SystemTenantBaseService tenantService,
            TenantExtensionRuntimeService tenantExtensionRuntimeService,
            ModuleIdentifierIntegrity identifierIntegrity,
            ModuleConfigurationService configurationService,
            CoreSequenceBaseService sequenceService,
            PrintTemplateBaseService printTemplateService,
            PrintTemplatePublicationBaseService printTemplatePublicationService,
            LegacyPrintCompatibility legacyPrintCompatibility,
            LegacyModuleDashboardCompatibility legacyModuleDashboardCompatibility) {
        this.moduleService = moduleService;
        this.fieldService = fieldService;
        this.groupService = groupService;
        this.pageService = pageService;
        this.menuService = menuService;
        this.dictionaryService = dictionaryService;
        this.dictionaryPublicationService = dictionaryPublicationService;
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
        this.publicationChecks = publicationChecks;
        this.ruleEvaluator = ruleEvaluator;
        this.tenantService = tenantService;
        this.tenantExtensionRuntimeService = tenantExtensionRuntimeService;
        this.identifierIntegrity = identifierIntegrity;
        this.configurationService = configurationService;
        this.sequenceService = sequenceService;
        this.printTemplateService = printTemplateService;
        this.printTemplatePublicationService = printTemplatePublicationService;
        this.legacyPrintCompatibility = legacyPrintCompatibility;
        this.legacyModuleDashboardCompatibility = legacyModuleDashboardCompatibility;
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
        identifierIntegrity.lockSystem(context.systemId());
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
        version.setPublishTime(LocalDateTime.now());
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
        if (parts.actions().stream().anyMatch(action -> "PRINT".equals(action.getCode()))) {
            legacyPrintCompatibility.retire(context.systemId(), context.tenantId(), moduleId);
        }
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "MODULE_PUBLISHED", "MODULE_VERSION", version.getId().toString(), "SUCCESS",
                Map.of("moduleId", moduleId, "versionNumber", nextVersion, "draftRevision", module.getDraftRevision()));
        return new PublishedModuleResult(moduleId, version.getId(), nextVersion, module.getDraftRevision(), version.getPublishTime());
    }

    /**
     * 旧版打印模板曾独立发布。只把已生效模块快照加上 PRINT 形成新版本，
     * 保留已有发布版本、打印任务及其他未发布草稿，避免恢复打印时意外发布草稿修改。
     */
    @Transactional
    public PublishedModuleResult transitionLegacyPrintAction(
            AuthenticatedContext context, Long moduleId, Integer expectedPublicationVersion, String traceId) {
        if (!permissionChecker.allows(context, "CONFIG", "MODULE", "MANAGE")
                || !permissionChecker.allows(context, "CONFIG", "MODULE", "PUBLISH")) {
            throw new DomainException("PERMISSION_DENIED", "没有管理和发布模块的权限", HttpStatus.FORBIDDEN);
        }
        identifierIntegrity.lockSystem(context.systemId());
        ConfiguredModule module = requireOwnedModule(context, moduleId);
        if (!"ACTIVE".equals(module.getStatus())) {
            throw new DomainException("LEGACY_PRINT_TRANSITION_UNAVAILABLE", "只有运行中的模块可以恢复旧打印模板",
                    HttpStatus.CONFLICT);
        }
        ConfiguredModulePublication publication = publicationService.selectOne(
                Wrappers.<ConfiguredModulePublication>lambdaQuery().eq(ConfiguredModulePublication::getModuleId, moduleId));
        if (publication == null || !expectedPublicationVersion.equals(publication.getVersion())) {
            throw conflict("模块发布版本已变化，请刷新后重试");
        }
        ConfiguredModuleVersion previous = versionService.selectById(publication.getCurrentVersionId());
        if (previous == null || !moduleId.equals(previous.getModuleId())) {
            throw new DomainException("PUBLISHED_SNAPSHOT_INVALID", "当前模块发布快照无效", HttpStatus.CONFLICT);
        }
        List<Long> templateIds = printTemplateService.selectList(Wrappers.<PrintTemplate>lambdaQuery()
                        .eq(PrintTemplate::getSystemId, context.systemId())
                        .eq(PrintTemplate::getOwnerTenantId, context.tenantId())
                        .eq(PrintTemplate::getModuleId, moduleId))
                .stream().map(PrintTemplate::getId).toList();
        if (templateIds.isEmpty() || printTemplatePublicationService.selectList(
                Wrappers.<PrintTemplatePublication>lambdaQuery()
                        .in(PrintTemplatePublication::getTemplateId, templateIds)).isEmpty()) {
            throw new DomainException("LEGACY_PRINT_TEMPLATE_NOT_FOUND", "当前模块没有已发布打印模板",
                    HttpStatus.CONFLICT);
        }
        if (!actionService.selectList(Wrappers.<ConfiguredModuleAction>lambdaQuery()
                .eq(ConfiguredModuleAction::getModuleId, moduleId)
                .eq(ConfiguredModuleAction::getCode, "PRINT")).isEmpty()) {
            throw new DomainException("LEGACY_PRINT_ACTION_DRAFT_EXISTS", "打印动作已在草稿中，请检查并正常发布",
                    HttpStatus.CONFLICT);
        }
        try {
            JsonNode parsed = objectMapper.readTree(previous.getSnapshotJson());
            if (!(parsed instanceof ObjectNode source) || !(source.path("actions") instanceof ArrayNode oldActions)) {
                throw new DomainException("PUBLISHED_SNAPSHOT_INVALID", "当前模块发布快照缺少动作配置", HttpStatus.CONFLICT);
            }
            for (JsonNode action : oldActions) {
                if ("PRINT".equals(action.path("code").asText())) {
                    throw new DomainException("LEGACY_PRINT_TRANSITION_UNAVAILABLE", "当前发布版本已有打印动作",
                            HttpStatus.CONFLICT);
                }
            }
            ConfiguredModuleAction action = configurationService.createPrintAction(context, moduleId, traceId);
            int nextVersion = versionService.selectList(Wrappers.<ConfiguredModuleVersion>lambdaQuery()
                            .eq(ConfiguredModuleVersion::getModuleId, moduleId))
                    .stream().map(ConfiguredModuleVersion::getVersionNumber).max(Integer::compareTo).orElse(0) + 1;
            ObjectNode migrated = source.deepCopy();
            migrated.put("versionNumber", nextVersion);
            migrated.put("printTransitionSourceVersionId", previous.getId());
            ((ArrayNode) migrated.path("actions")).add(objectMapper.valueToTree(action));
            List<PublicationCheckIssue> extensionIssues = tenantExtensionRuntimeService.checkPublishedExtensions(
                    context.systemId(), moduleId, migrated);
            if (!extensionIssues.isEmpty()) {
                throw new DomainException("TENANT_EXTENSION_CHECK_FAILED",
                        extensionIssues.stream().map(PublicationCheckIssue::message)
                                .collect(java.util.stream.Collectors.joining("；")), HttpStatus.UNPROCESSABLE_ENTITY);
            }
            String snapshotJson = objectMapper.writeValueAsString(migrated);
            ConfiguredModuleVersion version = new ConfiguredModuleVersion();
            version.setSystemId(context.systemId());
            version.setOwnerTenantId(context.tenantId());
            version.setModuleId(moduleId);
            version.setVersionNumber(nextVersion);
            version.setDraftRevision(previous.getDraftRevision());
            version.setSchemaHash(sha256(snapshotJson));
            version.setSnapshotJson(snapshotJson);
            version.setChangeSummary("恢复旧打印模板：在原发布快照中启用 PRINT，未发布草稿保持独立");
            version.setPublishedByMemberId(context.memberId());
            version.setPublishTime(LocalDateTime.now());
            versionService.insert(version);
            publication.setCurrentVersionId(version.getId());
            publication.setUpdatedByMemberId(context.memberId());
            if (publicationService.updateById(publication) == 0) throw conflict("模块发布版本已变化，请刷新后重试");
            legacyPrintCompatibility.retire(context.systemId(), context.tenantId(), moduleId);
            auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(),
                    context.memberId(), "MODULE_LEGACY_PRINT_TRANSITIONED", "MODULE_VERSION", version.getId().toString(),
                    "SUCCESS", Map.of("moduleId", moduleId, "sourceVersionId", previous.getId(),
                            "versionNumber", nextVersion, "actionId", action.getId()));
            return new PublishedModuleResult(moduleId, version.getId(), nextVersion,
                    previous.getDraftRevision(), version.getPublishTime());
        } catch (JsonProcessingException exception) {
            throw new DomainException("PUBLISHED_SNAPSHOT_INVALID", "当前模块发布快照无法解析", HttpStatus.CONFLICT);
        }
    }

    /** 只发布模块仪表盘入口的草稿状态；字段、页面及其他动作仍保留在原草稿中。 */
    @Transactional
    public PublishedModuleResult publishDashboardAction(
            AuthenticatedContext context, Long moduleId, Integer expectedPublicationVersion,
            Integer expectedActionVersion, String traceId) {
        if (!permissionChecker.allows(context, "CONFIG", "MODULE", "MANAGE")
                || !permissionChecker.allows(context, "CONFIG", "MODULE", "PUBLISH")) {
            throw new DomainException("PERMISSION_DENIED", "没有管理和发布模块的权限", HttpStatus.FORBIDDEN);
        }
        identifierIntegrity.lockSystem(context.systemId());
        ConfiguredModule module = requireOwnedModule(context, moduleId);
        if (!"ACTIVE".equals(module.getStatus())) {
            throw new DomainException("MODULE_DASHBOARD_ACTION_UNAVAILABLE", "请先发布模块", HttpStatus.CONFLICT);
        }
        ConfiguredModulePublication publication = publicationService.selectOne(
                Wrappers.<ConfiguredModulePublication>lambdaQuery().eq(ConfiguredModulePublication::getModuleId, moduleId));
        if (publication == null || !expectedPublicationVersion.equals(publication.getVersion())) {
            throw conflict("模块发布版本已变化，请刷新后重试");
        }
        ConfiguredModuleVersion previous = versionService.selectById(publication.getCurrentVersionId());
        if (previous == null || !moduleId.equals(previous.getModuleId())) {
            throw new DomainException("PUBLISHED_SNAPSHOT_INVALID", "当前模块发布快照无效", HttpStatus.CONFLICT);
        }
        ConfiguredModuleAction dashboardAction = actionService.selectOne(Wrappers.<ConfiguredModuleAction>lambdaQuery()
                .eq(ConfiguredModuleAction::getModuleId, moduleId)
                .eq(ConfiguredModuleAction::getOwnerTenantId, module.getOwnerTenantId())
                .eq(ConfiguredModuleAction::getCode, "DASHBOARD"));
        if (dashboardAction == null || !expectedActionVersion.equals(dashboardAction.getVersion())) {
            throw conflict("仪表盘入口草稿已变化，请刷新后重试");
        }
        if (!"MODULE_ENTRY".equals(dashboardAction.getLocation())) {
            throw new DomainException("MODULE_DASHBOARD_LOCATION_INVALID", "仪表盘入口只能放在模块入口",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        try {
            JsonNode parsed = objectMapper.readTree(previous.getSnapshotJson());
            if (!(parsed instanceof ObjectNode source) || !(source.path("actions") instanceof ArrayNode oldActions)) {
                throw new DomainException("PUBLISHED_SNAPSHOT_INVALID", "当前模块发布快照缺少动作配置", HttpStatus.CONFLICT);
            }
            JsonNode newAction = objectMapper.valueToTree(dashboardAction);
            ArrayNode updatedActions = objectMapper.createArrayNode();
            boolean changed = true;
            for (JsonNode action : oldActions) {
                if ("DASHBOARD".equals(action.path("code").asText())) {
                    if (action.equals(newAction)) changed = false;
                } else updatedActions.add(action.deepCopy());
            }
            if (!changed) throw conflict("仪表盘入口草稿与当前发布版本相同");
            updatedActions.add(newAction);
            int nextVersion = versionService.selectList(Wrappers.<ConfiguredModuleVersion>lambdaQuery()
                            .eq(ConfiguredModuleVersion::getModuleId, moduleId))
                    .stream().map(ConfiguredModuleVersion::getVersionNumber).max(Integer::compareTo).orElse(0) + 1;
            ObjectNode next = source.deepCopy();
            next.put("versionNumber", nextVersion);
            next.set("actions", updatedActions);
            List<PublicationCheckIssue> extensionIssues = tenantExtensionRuntimeService.checkPublishedExtensions(
                    context.systemId(), moduleId, next);
            if (!extensionIssues.isEmpty()) {
                throw new DomainException("TENANT_EXTENSION_CHECK_FAILED",
                        extensionIssues.stream().map(PublicationCheckIssue::message)
                                .collect(java.util.stream.Collectors.joining("；")), HttpStatus.UNPROCESSABLE_ENTITY);
            }
            String snapshotJson = objectMapper.writeValueAsString(next);
            ConfiguredModuleVersion version = new ConfiguredModuleVersion();
            version.setSystemId(context.systemId());
            version.setOwnerTenantId(context.tenantId());
            version.setModuleId(moduleId);
            version.setVersionNumber(nextVersion);
            version.setDraftRevision(previous.getDraftRevision());
            version.setSchemaHash(sha256(snapshotJson));
            version.setSnapshotJson(snapshotJson);
            version.setChangeSummary("仅发布模块仪表盘入口；其他草稿保持独立");
            version.setPublishedByMemberId(context.memberId());
            version.setPublishTime(LocalDateTime.now());
            versionService.insert(version);
            publication.setCurrentVersionId(version.getId());
            publication.setUpdatedByMemberId(context.memberId());
            if (publicationService.updateById(publication) == 0) throw conflict("模块发布版本已变化，请刷新后重试");
            auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(),
                    context.memberId(), "MODULE_DASHBOARD_ACTION_PUBLISHED", "MODULE_VERSION",
                    version.getId().toString(), "SUCCESS", Map.of("moduleId", moduleId,
                            "sourceVersionId", previous.getId(), "actionStatus", dashboardAction.getStatus()));
            return new PublishedModuleResult(moduleId, version.getId(), nextVersion,
                    previous.getDraftRevision(), version.getPublishTime());
        } catch (JsonProcessingException exception) {
            throw new DomainException("PUBLISHED_SNAPSHOT_INVALID", "当前模块发布快照无法解析", HttpStatus.CONFLICT);
        }
    }

    @Transactional
    public RuntimeModuleConfiguration rollback(
            AuthenticatedContext context,
            Long moduleId,
            RollbackModuleRequest request,
            String traceId) {
        identifierIntegrity.lockSystem(context.systemId());
        identifierIntegrity.requireUnambiguous(requireOwnedModule(context, moduleId));
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
        try {
            List<PublicationCheckIssue> tenantIssues = tenantExtensionRuntimeService.checkPublishedExtensions(
                    context.systemId(), moduleId, objectMapper.readTree(target.getSnapshotJson()));
            if (!tenantIssues.isEmpty()) {
                throw new DomainException("TENANT_EXTENSION_CHECK_FAILED",
                        tenantIssues.stream().map(PublicationCheckIssue::message)
                                .collect(java.util.stream.Collectors.joining("；")), HttpStatus.UNPROCESSABLE_ENTITY);
            }
        } catch (JsonProcessingException exception) {
            throw new DomainException("PUBLISHED_SNAPSHOT_INVALID", "目标发布快照无法解析，不能回滚",
                    HttpStatus.UNPROCESSABLE_ENTITY);
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
        rollbackVersion.setPublishTime(LocalDateTime.now());
        versionService.insert(rollbackVersion);

        publication.setCurrentVersionId(rollbackVersion.getId());
        publication.setUpdatedByMemberId(context.memberId());
        if (publicationService.updateById(publication) == 0) {
            throw conflict("当前发布指针已被其他操作修改");
        }
        if (hasPrintAction(rollbackSnapshot)) {
            legacyPrintCompatibility.retire(context.systemId(), context.tenantId(), moduleId);
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
                        version.getPublishTime(),
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
        return guardRuntimeConfiguration(context, moduleCode, configuration, "LIST", true);
    }

    /** 调用方先校验具体记录；以实际操作者裁剪来源快照，不取得来源租户的角色权限。 */
    public RuntimeModuleConfiguration recordEditConfiguration(
            AuthenticatedContext actor, String moduleCode, RuntimeModuleConfiguration sourceConfiguration) {
        return guardRuntimeConfiguration(actor, moduleCode, sourceConfiguration, "UPDATE", false);
    }

    /** 模块入口与记录编辑共用字段策略；编辑只消费 UPDATE，不混入 CREATE 的字段写权限。 */
    private RuntimeModuleConfiguration guardRuntimeConfiguration(
            AuthenticatedContext context, String moduleCode, RuntimeModuleConfiguration configuration,
            String readAction, boolean includeCreate) {
        ObjectNode visible = configuration.configuration().deepCopy();
        ArrayNode actions = objectMapper.createArrayNode();
        configuration.configuration().path("actions").forEach(action -> {
            String actionCode = action.path("code").asText();
            if (permissionChecker.allows(context, "MODULE", moduleCode, actionCode)
                    || "DASHBOARD".equals(actionCode)
                    && action.path("legacyDashboardCompatibility").asBoolean(false)
                    && permissionChecker.allows(context, "MODULE", moduleCode, "LIST")) {
                actions.add(action.deepCopy());
            }
        });
        visible.set("actions", actions);
        List<String> fieldCodes = new ArrayList<>();
        configuration.configuration().path("fields").forEach(field -> fieldCodes.add(field.path("code").asText()));
        Map<String, FieldAccessDecision> readAccess = fieldPolicyResolver.resolve(
                context, moduleCode, readAction, "PAGE", fieldCodes);
        Map<String, FieldAccessDecision> createAccess = includeCreate && permissionChecker.allows(context, "MODULE", moduleCode, "CREATE")
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
            if (!includeCreate && (read == null || !read.readable()) && !writable) return;
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

    // 只读目录的不可访问结果可由审批投影转换为“业务不可查看”，不能把调用方正常幂等回执标为回滚。
    // 若调用方不处理领域异常，调用方自己的事务仍按原规则回滚；本方法不执行任何业务写入。
    @Transactional(readOnly = true, noRollbackFor = DomainException.class)
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
        if (module == null || "DELETED".equals(module.getStatus()) || !context.systemId().equals(module.getSystemId())) {
            throw new DomainException("CONFIGURATION_NOT_FOUND", "运行模块不存在", HttpStatus.NOT_FOUND);
        }
        identifierIntegrity.requireUnambiguous(module);
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
                    withLegacyDashboardAction(context, module, withLegacyPrintAction(context, module, effective)));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot read published module snapshot", exception);
        }
    }

    /**
     * 运行态仅为升级前仍受兼容保护的模板补出打印动作；不修改任何历史发布快照或草稿。
     * 兼容动作仍经过现有 MODULE:PRINT、记录范围和字段权限检查。
     */
    private JsonNode withLegacyPrintAction(AuthenticatedContext context, ConfiguredModule module, JsonNode effective) {
        if (!(effective instanceof ObjectNode source) || !(source.path("actions") instanceof ArrayNode actions)
                || !module.getOwnerTenantId().equals(context.tenantId())) return effective;
        for (JsonNode action : actions) {
            if ("PRINT".equals(action.path("code").asText())) return effective;
        }
        Long templateId = legacyPrintCompatibility.activeTemplateId(
                context.systemId(), module.getOwnerTenantId(), module.getId());
        if (templateId == null) return effective;
        ObjectNode projected = source.deepCopy();
        ObjectNode print = objectMapper.createObjectNode();
        print.put("id", 9_000_000_000_000L + templateId);
        print.put("moduleId", module.getId());
        print.put("code", "PRINT");
        print.put("name", "打印");
        print.put("actionType", "BUILTIN");
        print.put("location", "DETAIL_MORE");
        print.put("status", "ACTIVE");
        print.put("configJson", "{}");
        print.put("sortOrder", 999);
        print.put("version", 0);
        ((ArrayNode) projected.path("actions")).add(print);
        projected.put("legacyPrintCompatibility", true);
        return projected;
    }

    /** 旧首页只在 V23 固定清单内投影入口，不写回历史版本或混入升级后的新页面。 */
    private JsonNode withLegacyDashboardAction(AuthenticatedContext context, ConfiguredModule module, JsonNode effective) {
        if (!(effective instanceof ObjectNode source) || !(source.path("actions") instanceof ArrayNode actions)
                || !legacyModuleDashboardCompatibility.hasPublishedDashboard(
                        context.systemId(), context.tenantId(), module.getId())) return effective;
        ObjectNode projected = source.deepCopy();
        ArrayNode projectedActions = (ArrayNode) projected.path("actions");
        for (int index = 0; index < projectedActions.size(); index++) {
            JsonNode action = projectedActions.get(index);
            if (!"DASHBOARD".equals(action.path("code").asText())) continue;
            if ("ACTIVE".equals(action.path("status").asText()) && action instanceof ObjectNode existing) {
                existing.put("legacyDashboardCompatibility", true);
            }
            return projected;
        }
        ObjectNode dashboard = objectMapper.createObjectNode();
        dashboard.put("id", 8_000_000_000_000L + module.getId());
        dashboard.put("moduleId", module.getId());
        dashboard.put("code", "DASHBOARD");
        dashboard.put("name", "模块首页");
        dashboard.put("actionType", "BUILTIN");
        dashboard.put("location", "MODULE_ENTRY");
        dashboard.put("status", "ACTIVE");
        dashboard.put("configJson", "{}");
        dashboard.put("sortOrder", 998);
        dashboard.put("version", 0);
        dashboard.put("legacyDashboardCompatibility", true);
        dashboard.put("legacyDashboardSynthetic", true);
        projectedActions.add(dashboard);
        return projected;
    }

    /** 正式打印动作一经发布即终止旧兼容，停用或回滚不能重新放开兼容入口。 */
    private boolean hasPrintAction(String snapshotJson) {
        try {
            for (JsonNode action : objectMapper.readTree(snapshotJson).path("actions")) {
                if ("PRINT".equals(action.path("code").asText())) return true;
            }
            return false;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot inspect published module snapshot", exception);
        }
    }

    private ConfiguredModule requireOwnedModule(AuthenticatedContext context, Long moduleId) {
        if (context.systemId() == null || context.tenantId() == null || context.memberId() == null) {
            throw new DomainException("SYSTEM_CONTEXT_REQUIRED", "请先进入系统", HttpStatus.CONFLICT);
        }
        ConfiguredModule module = moduleService.selectById(moduleId);
        if (module == null || "DELETED".equals(module.getStatus()) || !context.systemId().equals(module.getSystemId()) || !context.tenantId().equals(module.getOwnerTenantId())) {
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
                        .eq(ConfiguredModuleAction::getOwnerTenantId, module.getOwnerTenantId())
                        .orderByAsc(ConfiguredModuleAction::getSortOrder, ConfiguredModuleAction::getId)));
    }

    private List<PublicationCheckIssue> validate(ConfiguredModule module, SnapshotParts parts) {
        List<PublicationCheckIssue> issues = new ArrayList<>();
        if (identifierIntegrity.hasActiveConflict(module)) {
            issues.add(new PublicationCheckIssue("module.code", "MODULE_CODE_CONFLICT",
                    "模块标识存在历史冲突，请先处理重复模块；不能发布替换其他租户配置"));
        }
        if (parts.fields().isEmpty()) {
            issues.add(new PublicationCheckIssue("fields", "ACTIVE_FIELD_REQUIRED", "至少配置一个有效字段"));
        }
        for (ConfiguredModuleField field : parts.fields()) {
            String path = "fields." + field.getCode();
            if (!ModuleConfigurationService.FIELD_TYPES.contains(field.getFieldType())) {
                issues.add(new PublicationCheckIssue(path + ".fieldType", "FIELD_TYPE_UNSUPPORTED",
                        "当前阶段不支持该字段类型"));
            } else if (!SAVABLE_FIELD_TYPES.contains(field.getFieldType())) {
                issues.add(new PublicationCheckIssue(path + ".fieldType", "FIELD_RUNTIME_NOT_READY",
                        "该字段尚未支持业务保存，暂不能发布；字段草稿可继续编辑"));
            }
            if (Set.of("SINGLE_SELECT", "MULTI_SELECT", "STATUS", "TAG").contains(field.getFieldType())
                    && field.getDictionaryId() == null) {
                validateOptions(field, path, issues);
            }
            if ("CASCADE".equals(field.getFieldType()) && field.getReferenceModuleId() != null) {
                // 目标模块层级选项尚未接通运行态保存与权限校验，不能发布后让普通用户在表单中失败。
                issues.add(new PublicationCheckIssue(path + ".config", "MODULE_CASCADE_RUNTIME_UNAVAILABLE",
                        "模块级联的运行态选项、保存和展示尚未就绪，当前不能发布该字段"));
            }
            try {
                JsonNode config = objectMapper.readTree(field.getConfigJson() == null ? "{}" : field.getConfigJson());
                TextFieldProtocol.validateConfiguration(field.getFieldType(), config);
                StructuredContentFieldProtocol.validateConfiguration(field.getFieldType(), config);
                SecretFieldProtocol.validateConfiguration(field.getFieldType(), config);
                if ("AUTO_NUMBER".equals(field.getFieldType())) {
                    validateAutoNumberSequence(module, field, config, path, issues);
                }
                if ("SECRET".equals(field.getFieldType()) && (Boolean.TRUE.equals(field.getSearchable())
                        || Boolean.TRUE.equals(field.getUniqueValue()))) {
                    throw new IllegalArgumentException("密码/密钥不能用于搜索或唯一性索引");
                }
            } catch (Exception exception) {
                issues.add(new PublicationCheckIssue(path + ".config", "FIELD_CONFIG_INVALID", exception.getMessage()));
            }
            if (field.getDictionaryId() != null) {
                if (!hasPublishedDictionary(field.getDictionaryId(), module)) {
                    issues.add(new PublicationCheckIssue(path + ".dictionaryId", "DICTIONARY_NOT_PUBLISHED",
                            "绑定字典尚未发布，运行态不能引用草稿"));
                }
            }
        }
        validateSubtables(parts, issues);
        Set<String> pages = parts.pages().stream().map(ConfiguredModulePage::getPageType).collect(java.util.stream.Collectors.toSet());
        REQUIRED_PAGES.stream().sorted().filter(type -> !pages.contains(type)).forEach(type ->
                issues.add(new PublicationCheckIssue("pages." + type, "PAGE_REQUIRED", "缺少" + type + "页面")));
        validatePageLayouts(parts, issues);
        validateMenus(module, parts, issues);
        validateRulesAndIndexes(parts, issues);
        Set<String> actions = parts.actions().stream().map(ConfiguredModuleAction::getCode).collect(java.util.stream.Collectors.toSet());
        REQUIRED_ACTIONS.stream().sorted().filter(code -> !actions.contains(code)).forEach(code ->
                issues.add(new PublicationCheckIssue("actions." + code, "ACTION_REQUIRED", "缺少" + code + "动作")));
        validateCustomActions(parts, issues);
        validateConversionActions(module, parts, issues);
        validatePublishedCompatibility(module, parts, issues);
        // 主配置切版会立即影响所有次租户；候选字段、动作和页面必须能继续叠加其已发布扩展。
        ObjectNode candidate = objectMapper.createObjectNode();
        candidate.set("fields", objectMapper.valueToTree(parts.fields()));
        candidate.set("actions", objectMapper.valueToTree(parts.actions()));
        candidate.set("pages", objectMapper.valueToTree(parts.pages()));
        issues.addAll(tenantExtensionRuntimeService.checkPublishedExtensions(module.getSystemId(), module.getId(), candidate));
        return issues;
    }

    /** 自动编号依赖当前模块所属租户的启用序列；子表列暂不支持领取编号。 */
    private void validateAutoNumberSequence(ConfiguredModule module, ConfiguredModuleField field,
                                            JsonNode config, String path, List<PublicationCheckIssue> issues) {
        if (field.getParentFieldId() != null) {
            issues.add(new PublicationCheckIssue(path + ".fieldType", "SUBTABLE_AUTO_NUMBER_UNSUPPORTED",
                    "子表列暂不支持自动编号，当前子表保存链路不会为每行领取编号"));
            return;
        }
        String sequenceCode = config.path("sequenceCode").asText().strip().toLowerCase(Locale.ROOT);
        if (sequenceCode.isBlank()) {
            issues.add(new PublicationCheckIssue(path + ".config.sequenceCode", "AUTO_NUMBER_SEQUENCE_REQUIRED",
                    "自动编号字段必须配置编号规则编码"));
            return;
        }
        CoreSequence sequence = sequenceService.selectOne(Wrappers.<CoreSequence>lambdaQuery()
                .eq(CoreSequence::getSystemId, module.getSystemId())
                .eq(CoreSequence::getTenantId, module.getOwnerTenantId())
                .eq(CoreSequence::getSequenceCode, sequenceCode));
        if (sequence == null) {
            issues.add(new PublicationCheckIssue(path + ".config.sequenceCode", "AUTO_NUMBER_SEQUENCE_NOT_FOUND",
                    "编号规则不存在或不属于当前模块租户"));
        } else if (!"ACTIVE".equals(sequence.getStatus())) {
            issues.add(new PublicationCheckIssue(path + ".config.sequenceCode", "AUTO_NUMBER_SEQUENCE_INACTIVE",
                    "编号规则未启用，不能发布自动编号字段"));
        }
    }

    /** 发布快照必须包含可执行的子表结构，避免运行时收到孤立列或空子表定义。 */
    private void validateSubtables(SnapshotParts parts, List<PublicationCheckIssue> issues) {
        Map<Long, ConfiguredModuleField> active = parts.fields().stream()
                .collect(java.util.stream.Collectors.toMap(ConfiguredModuleField::getId, field -> field));
        Map<Long, Integer> childCounts = new java.util.HashMap<>();
        for (ConfiguredModuleField field : parts.fields()) {
            Long parentId = field.getParentFieldId();
            if (parentId == null) continue;
            String path = "fields." + field.getCode() + ".parentFieldId";
            ConfiguredModuleField parent = active.get(parentId);
            if (parent == null || !"SUBTABLE".equals(parent.getFieldType())
                    || parent.getParentFieldId() != null || "SUBTABLE".equals(field.getFieldType())) {
                issues.add(new PublicationCheckIssue(path, "SUBTABLE_PARENT_INVALID",
                        "子表列必须属于当前模块已启用的子表字段，且子表不能嵌套"));
            } else {
                childCounts.merge(parentId, 1, Integer::sum);
            }
            if (Boolean.TRUE.equals(field.getUniqueValue())) {
                issues.add(new PublicationCheckIssue("fields." + field.getCode() + ".uniqueValue",
                        "SUBTABLE_UNIQUE_UNSUPPORTED", "子表列不能使用主记录唯一索引"));
            }
        }
        for (ConfiguredModuleField field : parts.fields()) {
            if (!"SUBTABLE".equals(field.getFieldType())) continue;
            String path = "fields." + field.getCode();
            if (field.getParentFieldId() != null || field.getReferenceModuleId() != null) {
                issues.add(new PublicationCheckIssue(path, "SUBTABLE_DEFINITION_INVALID",
                        "子表容器必须位于主记录，列字段由当前模块定义"));
            }
            if (childCounts.getOrDefault(field.getId(), 0) == 0) {
                issues.add(new PublicationCheckIssue(path, "SUBTABLE_COLUMN_REQUIRED", "子表至少需要一个有效列字段"));
            }
            try {
                JsonNode config = objectMapper.readTree(field.getConfigJson() == null ? "{}" : field.getConfigJson());
                int min = config.path("minRows").asInt(0);
                int max = config.path("maxRows").asInt(Integer.MAX_VALUE);
                if (min < 0 || max < min) {
                    issues.add(new PublicationCheckIssue(path + ".config", "SUBTABLE_ROW_LIMIT_INVALID",
                            "子表最大行数不能小于最小行数，且行数不能为负"));
                }
            } catch (JsonProcessingException exception) {
                issues.add(new PublicationCheckIssue(path + ".config", "SUBTABLE_CONFIG_INVALID", "子表配置格式不正确"));
            }
        }
    }

    /** 发布前复核动作效果和字段引用；仅在草稿保存时合法不足以保证当前发布仍可运行。 */
    private void validateCustomActions(SnapshotParts parts, List<PublicationCheckIssue> issues) {
        List<JsonNode> fields = parts.fields().stream().map(field -> (JsonNode) objectMapper.valueToTree(field)).toList();
        for (ConfiguredModuleAction action : parts.actions()) {
            if (!"CUSTOM".equals(action.getActionType()) || !"ACTIVE".equals(action.getStatus())) continue;
            try {
                JsonNode config = objectMapper.readTree(action.getConfigJson() == null ? "{}" : action.getConfigJson());
                if (config == null || !config.isObject()) {
                    throw new DomainException("CUSTOM_ACTION_CONFIG_INVALID", "动作配置结构无效",
                            HttpStatus.UNPROCESSABLE_ENTITY);
                }
                configurationService.validateCustomActionConfig(config, fields);
            } catch (JsonProcessingException | DomainException exception) {
                issues.add(new PublicationCheckIssue("actions." + action.getCode() + ".config",
                        "CUSTOM_ACTION_CONFIG_INVALID", exception.getMessage()));
            }
        }
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
                    int values = "SUBTABLE".equals(publishedField.path("fieldType").asText())
                            ? publicationChecks.countChildRows(module.getId(), fieldId)
                            : publishedField.hasNonNull("parentFieldId")
                            ? publicationChecks.countChildFieldValues(module.getId(), fieldId)
                            : publicationChecks.countFieldValues(module.getId(), fieldId);
                    if (values > 0) {
                        issues.add(new PublicationCheckIssue("fields." + code,
                                "FIELD_REMOVAL_REQUIRES_MIGRATION",
                                "字段已有 " + values + " 条运行数据，停用前必须完成迁移并保留历史快照"));
                    }
                    continue;
                }
                if (!publishedField.path("required").asBoolean(false) && Boolean.TRUE.equals(draftField.getRequired())) {
                    // 多选空数组表示未选择，不能因JSON列非NULL而绕过必填变更迁移检查；普通JSON字段仍按原协议。
                    int missing = "SUBTABLE".equals(draftField.getFieldType())
                            ? publicationChecks.countRecordsMissingChildRows(module.getId(), draftField.getId())
                            : draftField.getParentFieldId() == null
                            ? publicationChecks.countRecordsMissingField(
                            draftField.getId(), module.getId(), draftField.getFieldType())
                            : publicationChecks.countChildRowsMissingField(draftField.getId(),
                            draftField.getParentFieldId(), module.getId(), draftField.getFieldType());
                    if (missing > 0) {
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
                .filter(field -> field.getParentFieldId() == null)
                .collect(java.util.stream.Collectors.toMap(ConfiguredModuleField::getCode, field -> field));
        for (ConfiguredModulePage page : parts.pages()) {
            String path = "pages." + page.getPageType() + ".layout.fieldCodes";
            try {
                JsonNode layout = objectMapper.readTree(page.getLayoutJson());
                if ("FORM".equals(page.getPageType())) {
                    FormLayoutProtocol.validate(layout, activeFields.keySet()).forEach(problem -> issues.add(
                            new PublicationCheckIssue("pages.FORM.layout." + problem.path(), "FORM_LAYOUT_INVALID", problem.message())));
                }
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

    /** 字典及其发布记录都必须属于当前模块的系统和配置租户，避免跨租户引用。 */
    private boolean hasPublishedDictionary(Long dictionaryId, ConfiguredModule module) {
        if (dictionaryService.selectList(Wrappers.<CfgDictionary>lambdaQuery()
                .eq(CfgDictionary::getId, dictionaryId)
                .eq(CfgDictionary::getSystemId, module.getSystemId())
                .eq(CfgDictionary::getOwnerTenantId, module.getOwnerTenantId())).isEmpty()) {
            return false;
        }
        return !dictionaryPublicationService.selectList(Wrappers.<CfgDictionaryPublication>lambdaQuery()
                .eq(CfgDictionaryPublication::getDictionaryId, dictionaryId)
                .eq(CfgDictionaryPublication::getSystemId, module.getSystemId())
                .eq(CfgDictionaryPublication::getOwnerTenantId, module.getOwnerTenantId())).isEmpty();
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
                if (menuService.selectList(Wrappers.<CfgModuleMenu>lambdaQuery()
                        .eq(CfgModuleMenu::getId, menu.getParentId())
                        .eq(CfgModuleMenu::getSystemId, module.getSystemId())
                        .eq(CfgModuleMenu::getOwnerTenantId, module.getOwnerTenantId())
                        .eq(CfgModuleMenu::getStatus, "ACTIVE")).isEmpty()) {
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
                } else if ("SECRET".equals(fieldsById.get(indexField.getFieldId()).getFieldType())) {
                    issues.add(new PublicationCheckIssue("indexes." + index.getCode(), "SECRET_INDEX_FORBIDDEN",
                            "密码/密钥不能写入查询索引"));
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
            return index.getCode() + ": un_biz_record_index(" + fields + ") -> SHA-256 -> "
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
