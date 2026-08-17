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
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModulePage;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModulePublication;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModuleVersion;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleActionBaseService;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleBaseService;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleFieldBaseService;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModulePageBaseService;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModulePublicationBaseService;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleVersionBaseService;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.authorization.manage.PermissionChecker;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.LocalDateTime;

@Service
public class ModulePublicationService {
    private static final Set<String> REQUIRED_PAGES = Set.of("LIST", "FORM", "DETAIL");
    private static final Set<String> REQUIRED_ACTIONS = Set.of("LIST", "DETAIL", "CREATE", "UPDATE");
    private static final Set<String> FIRST_STAGE_FIELD_TYPES = Set.of(
            "TEXT", "MULTILINE_TEXT", "NUMBER", "MONEY", "DATE", "DATETIME", "SINGLE_SELECT",
            "BOOLEAN", "MEMBER", "DEPARTMENT", "STATUS");

    private final ConfiguredModuleBaseService moduleService;
    private final ConfiguredModuleFieldBaseService fieldService;
    private final ConfiguredModulePageBaseService pageService;
    private final ConfiguredModuleActionBaseService actionService;
    private final ConfiguredModuleVersionBaseService versionService;
    private final ConfiguredModulePublicationBaseService publicationService;
    private final PermissionChecker permissionChecker;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;

    public ModulePublicationService(
            ConfiguredModuleBaseService moduleService,
            ConfiguredModuleFieldBaseService fieldService,
            ConfiguredModulePageBaseService pageService,
            ConfiguredModuleActionBaseService actionService,
            ConfiguredModuleVersionBaseService versionService,
            ConfiguredModulePublicationBaseService publicationService,
            PermissionChecker permissionChecker,
            AuditRecorder auditRecorder,
            ObjectMapper objectMapper) {
        this.moduleService = moduleService;
        this.fieldService = fieldService;
        this.pageService = pageService;
        this.actionService = actionService;
        this.versionService = versionService;
        this.publicationService = publicationService;
        this.permissionChecker = permissionChecker;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public PublicationCheckResult check(AuthenticatedContext context, Long moduleId) {
        ConfiguredModule module = requireOwnedModule(context, moduleId);
        SnapshotParts parts = parts(moduleId);
        List<PublicationCheckIssue> issues = validate(parts);
        return new PublicationCheckResult(issues.isEmpty(), module.getDraftRevision(), issues);
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
        SnapshotParts parts = parts(moduleId);
        List<PublicationCheckIssue> issues = validate(parts);
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
        version.setSnapshotJson(snapshot(module, parts, nextVersion));
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
        publication.setCurrentVersionId(target.getId());
        publication.setUpdatedByMemberId(context.memberId());
        if (publicationService.updateById(publication) == 0) {
            throw conflict("当前发布指针已被其他操作修改");
        }
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "MODULE_PUBLICATION_ROLLED_BACK", "MODULE_VERSION", target.getId().toString(), "SUCCESS",
                Map.of("moduleId", moduleId, "versionNumber", target.getVersionNumber()));
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
                        version.getId(), version.getVersionNumber(), version.getDraftRevision(), version.getPublishedAt(),
                        version.getId().equals(currentVersionId), publicationVersion))
                .toList();
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
                .stream().findFirst().orElseThrow(() -> new DomainException(
                        "CONFIGURATION_NOT_FOUND", "运行模块不存在或尚未发布", HttpStatus.NOT_FOUND));
        return runtimeByModuleId(context, module.getId(), true);
    }

    private RuntimeModuleConfiguration runtimeByModuleId(AuthenticatedContext context, Long moduleId, boolean enforceContext) {
        ConfiguredModulePublication publication = publicationService.selectList(
                        Wrappers.<ConfiguredModulePublication>lambdaQuery()
                                .eq(ConfiguredModulePublication::getSystemId, context.systemId())
                                .eq(ConfiguredModulePublication::getOwnerTenantId, context.tenantId())
                                .eq(ConfiguredModulePublication::getModuleId, moduleId))
                .stream().findFirst().orElseThrow(() -> new DomainException(
                        "CONFIGURATION_NOT_FOUND", "模块尚未发布", HttpStatus.NOT_FOUND));
        ConfiguredModuleVersion version = versionService.selectById(publication.getCurrentVersionId());
        if (version == null || (enforceContext && (!context.systemId().equals(version.getSystemId())
                || !context.tenantId().equals(version.getOwnerTenantId())))) {
            throw new DomainException("CONFIGURATION_NOT_FOUND", "当前发布版本无效", HttpStatus.NOT_FOUND);
        }
        try {
            return new RuntimeModuleConfiguration(moduleId, version.getId(), version.getVersionNumber(), publication.getVersion(),
                    objectMapper.readTree(version.getSnapshotJson()));
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

    private SnapshotParts parts(Long moduleId) {
        return new SnapshotParts(
                fieldService.selectList(Wrappers.<ConfiguredModuleField>lambdaQuery()
                        .eq(ConfiguredModuleField::getModuleId, moduleId).eq(ConfiguredModuleField::getStatus, "ACTIVE")
                        .orderByAsc(ConfiguredModuleField::getSortOrder, ConfiguredModuleField::getId)),
                pageService.selectList(Wrappers.<ConfiguredModulePage>lambdaQuery()
                        .eq(ConfiguredModulePage::getModuleId, moduleId).eq(ConfiguredModulePage::getStatus, "ACTIVE")
                        .orderByAsc(ConfiguredModulePage::getId)),
                actionService.selectList(Wrappers.<ConfiguredModuleAction>lambdaQuery()
                        .eq(ConfiguredModuleAction::getModuleId, moduleId).eq(ConfiguredModuleAction::getStatus, "ACTIVE")
                        .orderByAsc(ConfiguredModuleAction::getSortOrder, ConfiguredModuleAction::getId)));
    }

    private List<PublicationCheckIssue> validate(SnapshotParts parts) {
        List<PublicationCheckIssue> issues = new ArrayList<>();
        if (parts.fields().isEmpty()) {
            issues.add(new PublicationCheckIssue("fields", "ACTIVE_FIELD_REQUIRED", "至少配置一个有效字段"));
        }
        for (ConfiguredModuleField field : parts.fields()) {
            String path = "fields." + field.getCode();
            if (!FIRST_STAGE_FIELD_TYPES.contains(field.getFieldType())) {
                issues.add(new PublicationCheckIssue(path + ".fieldType", "FIELD_TYPE_UNSUPPORTED",
                        "当前阶段不支持该字段类型"));
            }
            if (Set.of("SINGLE_SELECT", "STATUS").contains(field.getFieldType())) {
                validateOptions(field, path, issues);
            }
        }
        Set<String> pages = parts.pages().stream().map(ConfiguredModulePage::getPageType).collect(java.util.stream.Collectors.toSet());
        REQUIRED_PAGES.stream().sorted().filter(type -> !pages.contains(type)).forEach(type ->
                issues.add(new PublicationCheckIssue("pages." + type, "PAGE_REQUIRED", "缺少" + type + "页面")));
        validatePageLayouts(parts, issues);
        Set<String> actions = parts.actions().stream().map(ConfiguredModuleAction::getCode).collect(java.util.stream.Collectors.toSet());
        REQUIRED_ACTIONS.stream().sorted().filter(code -> !actions.contains(code)).forEach(code ->
                issues.add(new PublicationCheckIssue("actions." + code, "ACTION_REQUIRED", "缺少" + code + "动作")));
        return issues;
    }

    private void validatePageLayouts(SnapshotParts parts, List<PublicationCheckIssue> issues) {
        Map<String, ConfiguredModuleField> activeFields = parts.fields().stream()
                .collect(java.util.stream.Collectors.toMap(ConfiguredModuleField::getCode, field -> field));
        for (ConfiguredModulePage page : parts.pages()) {
            String path = "pages." + page.getPageType() + ".layout.fieldCodes";
            try {
                JsonNode fieldCodes = objectMapper.readTree(page.getLayoutJson()).path("fieldCodes");
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
        root.put("schemaVersion", 1);
        root.put("versionNumber", versionNumber);
        root.put("draftRevision", module.getDraftRevision());
        root.set("module", objectMapper.valueToTree(module));
        root.set("fields", objectMapper.valueToTree(parts.fields()));
        root.set("pages", objectMapper.valueToTree(parts.pages()));
        root.set("actions", objectMapper.valueToTree(parts.actions()));
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize published module snapshot", exception);
        }
    }

    private DomainException conflict(String message) {
        return new DomainException("PUBLICATION_VERSION_CONFLICT", message, HttpStatus.CONFLICT);
    }

    private record SnapshotParts(
            List<ConfiguredModuleField> fields,
            List<ConfiguredModulePage> pages,
            List<ConfiguredModuleAction> actions) {
    }
}
