package com.unique.unexamine.ai.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.ai.base.entity.AiAgent;
import com.unique.unexamine.ai.base.entity.AiAgentPublication;
import com.unique.unexamine.ai.base.entity.AiAgentTool;
import com.unique.unexamine.ai.base.entity.AiAgentVersion;
import com.unique.unexamine.ai.base.entity.AiModel;
import com.unique.unexamine.ai.base.entity.AiSystemModelGrant;
import com.unique.unexamine.ai.base.service.AiAgentBaseService;
import com.unique.unexamine.ai.base.service.AiAgentPublicationBaseService;
import com.unique.unexamine.ai.base.service.AiAgentToolBaseService;
import com.unique.unexamine.ai.base.service.AiAgentVersionBaseService;
import com.unique.unexamine.ai.base.service.AiModelBaseService;
import com.unique.unexamine.ai.base.service.AiSystemModelGrantBaseService;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.authorization.manage.ChannelFieldPolicyResolver;
import com.unique.unexamine.authorization.manage.DataScopeExpression;
import com.unique.unexamine.authorization.manage.FieldAccessDecision;
import com.unique.unexamine.authorization.manage.PermissionChecker;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModule;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModulePublication;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModuleVersion;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleBaseService;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModulePublicationBaseService;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleVersionBaseService;
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
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SystemAiConfigurationService {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() { };
    private static final Set<String> WRITE_ACTIONS = Set.of("CREATE", "UPDATE", "DELETE", "CONVERT", "IMPORT", "EXECUTE");

    private final AiAgentBaseService agentService;
    private final AiAgentToolBaseService toolService;
    private final AiAgentVersionBaseService agentVersionService;
    private final AiAgentPublicationBaseService agentPublicationService;
    private final AiModelBaseService modelService;
    private final AiSystemModelGrantBaseService grantService;
    private final ConfiguredModuleBaseService moduleService;
    private final ConfiguredModulePublicationBaseService modulePublicationService;
    private final ConfiguredModuleVersionBaseService moduleVersionService;
    private final SystemTenantBaseService tenantService;
    private final PermissionChecker permissionChecker;
    private final ChannelFieldPolicyResolver fieldPolicyResolver;
    private final PlatformAiConfigurationService platformService;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;

    public SystemAiConfigurationService(
            AiAgentBaseService agentService,
            AiAgentToolBaseService toolService,
            AiAgentVersionBaseService agentVersionService,
            AiAgentPublicationBaseService agentPublicationService,
            AiModelBaseService modelService,
            AiSystemModelGrantBaseService grantService,
            ConfiguredModuleBaseService moduleService,
            ConfiguredModulePublicationBaseService modulePublicationService,
            ConfiguredModuleVersionBaseService moduleVersionService,
            SystemTenantBaseService tenantService,
            PermissionChecker permissionChecker,
            ChannelFieldPolicyResolver fieldPolicyResolver,
            PlatformAiConfigurationService platformService,
            AuditRecorder auditRecorder,
            ObjectMapper objectMapper) {
        this.agentService = agentService;
        this.toolService = toolService;
        this.agentVersionService = agentVersionService;
        this.agentPublicationService = agentPublicationService;
        this.modelService = modelService;
        this.grantService = grantService;
        this.moduleService = moduleService;
        this.modulePublicationService = modulePublicationService;
        this.moduleVersionService = moduleVersionService;
        this.tenantService = tenantService;
        this.permissionChecker = permissionChecker;
        this.fieldPolicyResolver = fieldPolicyResolver;
        this.platformService = platformService;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public AiConfigurationModels.SystemOverview overview(AuthenticatedContext context) {
        requireSystem(context);
        List<AiConfigurationModels.ModelView> available = activeGrants(context).stream()
                .map(grant -> Map.entry(grant, modelService.selectById(grant.getModelId())))
                .filter(entry -> entry.getValue() != null)
                .filter(entry -> context.platformId().equals(entry.getValue().getPlatformId())
                        && "ACTIVE".equals(entry.getValue().getStatus()))
                .map(entry -> withGrant(platformService.viewModel(entry.getValue(), false), entry.getKey().getId()))
                .sorted(Comparator.comparing(AiConfigurationModels.ModelView::name)).toList();
        List<AiConfigurationModels.AgentView> agents = agentService.selectList(Wrappers.<AiAgent>lambdaQuery()
                        .eq(AiAgent::getSystemId, context.systemId())
                        .eq(AiAgent::getOwnerTenantId, context.tenantId())
                        .orderByAsc(AiAgent::getCode))
                .stream().map(this::view).toList();
        return new AiConfigurationModels.SystemOverview(available, moduleOptions(context), agents);
    }

    @Transactional
    public AiConfigurationModels.AgentView createAgent(
            AuthenticatedContext context, AiConfigurationModels.AgentDraftRequest input, String traceId) {
        requireSystem(context);
        requireActiveGrant(context, input.modelGrantId());
        String code = normalizeCode(input.code());
        if (!agentService.selectList(Wrappers.<AiAgent>lambdaQuery()
                .eq(AiAgent::getOwnerTenantId, context.tenantId()).eq(AiAgent::getCode, code)).isEmpty()) {
            throw conflict("AI_AGENT_CODE_CONFLICT", "当前租户已存在相同 Agent 编码");
        }
        validateUniqueTools(input.tools());
        AiAgent agent = new AiAgent();
        agent.setSystemId(context.systemId());
        agent.setOwnerTenantId(context.tenantId());
        agent.setCreatedByMemberId(context.memberId());
        agent.setDraftRevision(1);
        agent.setVersion(0);
        bind(agent, input);
        agentService.insert(agent);
        replaceTools(agent.getId(), input.tools());
        audit(context, traceId, "AI_AGENT_DRAFT_CREATED", agent.getId(), Map.of(
                "agentCode", code, "draftRevision", 1, "toolCount", input.tools().size()));
        return view(agentService.selectById(agent.getId()));
    }

    @Transactional
    public AiConfigurationModels.AgentView saveDraft(
            AuthenticatedContext context,
            Long agentId,
            AiConfigurationModels.AgentDraftRequest input,
            String traceId) {
        requireSystem(context);
        AiAgent agent = requireOwned(context, agentId);
        if (input.expectedVersion() == null || !Objects.equals(input.expectedVersion(), agent.getVersion())) {
            throw conflict("AI_AGENT_VERSION_CONFLICT", "Agent 草稿已变化，请刷新后重试");
        }
        requireActiveGrant(context, input.modelGrantId());
        String code = normalizeCode(input.code());
        boolean codeExists = agentService.selectList(Wrappers.<AiAgent>lambdaQuery()
                        .eq(AiAgent::getOwnerTenantId, context.tenantId()).eq(AiAgent::getCode, code))
                .stream().anyMatch(item -> !agentId.equals(item.getId()));
        if (codeExists) throw conflict("AI_AGENT_CODE_CONFLICT", "当前租户已存在相同 Agent 编码");
        validateUniqueTools(input.tools());
        replaceTools(agentId, input.tools());
        bind(agent, input);
        agent.setDraftRevision(agent.getDraftRevision() + 1);
        if (agentService.updateById(agent) != 1) {
            throw conflict("AI_AGENT_VERSION_CONFLICT", "Agent 草稿已变化，请刷新后重试");
        }
        audit(context, traceId, "AI_AGENT_DRAFT_SAVED", agentId, Map.of(
                "agentCode", code, "draftRevision", agent.getDraftRevision(), "toolCount", input.tools().size()));
        return view(agentService.selectById(agentId));
    }

    @Transactional(readOnly = true)
    public AiConfigurationModels.AgentPreview preview(AuthenticatedContext context, Long agentId) {
        requireSystem(context);
        return buildPreview(context, requireOwned(context, agentId));
    }

    @Transactional
    public AiConfigurationModels.PublishResult publish(
            AuthenticatedContext context,
            Long agentId,
            AiConfigurationModels.PublishRequest input,
            String traceId) {
        requireSystem(context);
        AiAgent agent = requireOwned(context, agentId);
        if (!Objects.equals(agent.getDraftRevision(), input.expectedDraftRevision())) {
            throw conflict("AI_AGENT_DRAFT_REVISION_CONFLICT", "Agent 草稿修订号已变化，请重新预览后发布");
        }
        if (!agentVersionService.selectList(Wrappers.<AiAgentVersion>lambdaQuery()
                .eq(AiAgentVersion::getAgentId, agentId)
                .eq(AiAgentVersion::getDraftRevision, agent.getDraftRevision())).isEmpty()) {
            throw conflict("AI_AGENT_DRAFT_ALREADY_PUBLISHED", "当前 Agent 草稿已经发布");
        }
        AiConfigurationModels.AgentPreview preview = buildPreview(context, agent);
        if (!preview.valid()) {
            auditRecorder.recordFailure(
                    traceId,
                    context.accountId(),
                    context.systemId(),
                    context.tenantId(),
                    context.memberId(),
                    "AI_AGENT_PUBLISH_BLOCKED",
                    "AI_AGENT",
                    agentId.toString(),
                    "AI_AGENT_PUBLICATION_INVALID",
                    Map.of("draftRevision", agent.getDraftRevision(), "issues", preview.issues()));
            throw new DomainException("AI_AGENT_PUBLICATION_INVALID", "Agent 发布检查未通过",
                    HttpStatus.UNPROCESSABLE_ENTITY, Map.of("issues", preview.issues()));
        }
        int versionNumber = versions(agentId).stream().map(AiAgentVersion::getVersionNumber)
                .max(Integer::compareTo).orElse(0) + 1;
        String snapshotJson = writeJson(snapshot(agent, preview, versionNumber));
        AiAgentVersion version = new AiAgentVersion();
        version.setAgentId(agentId);
        version.setVersionNumber(versionNumber);
        version.setDraftRevision(agent.getDraftRevision());
        version.setSnapshotHash(sha256(snapshotJson));
        version.setSnapshotJson(snapshotJson);
        version.setPublishedByMemberId(context.memberId());
        version.setPublishedAt(LocalDateTime.now());
        agentVersionService.insert(version);

        AiAgentPublication publication = agentPublicationService.selectList(
                        Wrappers.<AiAgentPublication>lambdaQuery().eq(AiAgentPublication::getAgentId, agentId))
                .stream().findFirst().orElse(null);
        if (publication == null) {
            publication = new AiAgentPublication();
            publication.setAgentId(agentId);
            publication.setCurrentVersionId(version.getId());
            publication.setUpdatedByMemberId(context.memberId());
            publication.setVersion(0);
            agentPublicationService.insert(publication);
        } else {
            publication.setCurrentVersionId(version.getId());
            publication.setUpdatedByMemberId(context.memberId());
            if (agentPublicationService.updateById(publication) != 1) {
                throw conflict("AI_AGENT_PUBLICATION_VERSION_CONFLICT", "Agent 当前发布版本已变化，请重试");
            }
        }
        agent.setStatus("PUBLISHED");
        if (agentService.updateById(agent) != 1) {
            throw conflict("AI_AGENT_VERSION_CONFLICT", "Agent 草稿已变化，请重试");
        }
        auditRecorder.recordWithPermissionSnapshot(traceId, context.accountId(), context.systemId(), context.tenantId(),
                context.memberId(), "AI_AGENT_PUBLISHED", "AI_AGENT_VERSION", String.valueOf(version.getId()),
                "SUCCESS", preview.permissionSnapshot(), Map.of(
                        "agentId", agentId, "agentCode", agent.getCode(), "versionNumber", versionNumber,
                        "draftRevision", agent.getDraftRevision(), "snapshotHash", version.getSnapshotHash()));
        return new AiConfigurationModels.PublishResult(agentId, version.getId(), versionNumber,
                agent.getDraftRevision(), version.getSnapshotHash(), preview);
    }

    private AiConfigurationModels.AgentPreview buildPreview(AuthenticatedContext context, AiAgent agent) {
        List<AiConfigurationModels.Issue> issues = new ArrayList<>();
        AiSystemModelGrant grant = grantService.selectById(agent.getModelGrantId());
        AiModel model = grant == null ? null : modelService.selectById(grant.getModelId());
        if (grant == null || !context.systemId().equals(grant.getSystemId()) || !"ACTIVE".equals(grant.getStatus())) {
            issues.add(issue("AI_MODEL_NOT_GRANTED", "Agent 选择的模型未授权给当前系统", null));
        }
        if (model == null || !context.platformId().equals(model.getPlatformId()) || !"ACTIVE".equals(model.getStatus())) {
            issues.add(issue("AI_MODEL_INACTIVE", "Agent 选择的模型不存在或已停用", null));
        } else if (!platformService.credentialAvailable(model.getCredentialRef())) {
            issues.add(issue("AI_MODEL_CREDENTIAL_UNAVAILABLE", "模型凭证引用当前不可用", null));
        }
        Map<String, Object> contextPolicy = readMap(agent.getContextPolicyJson());
        Map<String, Object> confirmationPolicy = readMap(agent.getConfirmationPolicyJson());
        Map<String, Object> fallbackPolicy = readMap(agent.getFallbackPolicyJson());
        if (!Boolean.TRUE.equals(contextPolicy.get("maskSensitiveData"))) {
            issues.add(issue("AI_CONTEXT_MASKING_REQUIRED", "Agent 上下文必须启用敏感字段脱敏", null));
        }
        if (!Set.of("DISABLED", "READ_ONLY", "TEMPLATE_QUERY").contains(string(fallbackPolicy.get("mode")))) {
            issues.add(issue("AI_FALLBACK_POLICY_INVALID", "降级策略必须明确为停用、只读或模板查询", null));
        }

        List<AiAgentTool> tools = tools(agent.getId());
        if (tools.isEmpty() || tools.size() > 20) {
            issues.add(issue("AI_TOOL_COUNT_INVALID", "Agent 必须配置 1 至 20 个工具", null));
        }
        List<AiConfigurationModels.ToolPreview> toolPreviews = new ArrayList<>();
        for (int index = 0; index < tools.size(); index++) {
            toolPreviews.add(previewTool(context, tools.get(index), index, confirmationPolicy));
        }
        toolPreviews.forEach(tool -> issues.addAll(tool.issues()));
        Map<String, Object> permissionSnapshot = permissionSnapshot(context);
        AiConfigurationModels.ModelView finalModel = model == null ? null : platformService.viewModel(model, false);
        return new AiConfigurationModels.AgentPreview(agent.getId(), agent.getDraftRevision(), issues.isEmpty(),
                List.copyOf(issues), finalModel, List.copyOf(toolPreviews), permissionSnapshot, confirmationPolicy);
    }

    private AiConfigurationModels.ModelView withGrant(AiConfigurationModels.ModelView model, Long grantId) {
        return new AiConfigurationModels.ModelView(model.id(), grantId, model.code(), model.name(), model.provider(),
                model.modelName(), model.endpointUrl(), model.credentialReferenceType(), model.credentialAvailable(),
                model.capabilities(), model.limitPolicy(), model.status(), model.version(), model.grants());
    }

    private AiConfigurationModels.ToolPreview previewTool(
            AuthenticatedContext context,
            AiAgentTool tool,
            int index,
            Map<String, Object> confirmationPolicy) {
        List<AiConfigurationModels.Issue> issues = new ArrayList<>();
        List<String> fieldCodes = fieldCodes(tool);
        String requestedScope = requestedScope(tool);
        Map<String, Object> fieldAuthorization = new LinkedHashMap<>();
        Map<String, Object> dataAuthorization = new LinkedHashMap<>();
        String resourceId = tool.getResourceId();
        String actionCode = tool.getActionCode();
        if (containsWildcard(resourceId) || containsWildcard(actionCode)
                || fieldCodes.stream().anyMatch(this::containsWildcard)) {
            issues.add(issue("AI_TOOL_SCOPE_EXCESSIVE", "工具范围必须绑定到明确模块、动作和字段，不能使用通配符", index));
        }
        PublishedModule published = publishedModule(context, resourceId);
        if (published == null) {
            issues.add(issue("AI_TOOL_MODULE_NOT_PUBLISHED", "工具绑定模块不存在或尚未发布", index));
        } else {
            if (!published.actions().contains(actionCode)) {
                issues.add(issue("AI_TOOL_ACTION_NOT_PUBLISHED", "工具动作不在模块当前发布版本中", index));
            }
            List<String> unknownFields = fieldCodes.stream().filter(field -> !published.fields().contains(field)).toList();
            if (!unknownFields.isEmpty()) {
                issues.add(issue("AI_TOOL_FIELD_NOT_PUBLISHED", "工具包含未发布字段：" + String.join("、", unknownFields), index));
            }
        }
        boolean actionAllowed = permissionChecker.allows(context, "MODULE", resourceId, actionCode);
        if (!actionAllowed) {
            issues.add(issue("AI_TOOL_ACTION_PERMISSION_DENIED", "当前发布人没有该模块动作权限", index));
        }
        Map<String, FieldAccessDecision> decisions = fieldPolicyResolver.resolve(
                context, resourceId, actionCode, "APPLICATION", fieldCodes);
        boolean write = "WRITE".equals(tool.getToolType()) || WRITE_ACTIONS.contains(actionCode);
        decisions.forEach((field, decision) -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("readable", decision.readable());
            value.put("writable", decision.writable());
            value.put("maskStrategy", decision.maskStrategy());
            value.put("contributingRoleIds", decision.contributingRoleIds());
            value.put("reason", decision.reason());
            fieldAuthorization.put(field, value);
            if (!decision.readable() || (write && !decision.writable())) {
                issues.add(issue("AI_TOOL_FIELD_PERMISSION_DENIED", "字段 " + field + " 超出当前发布人的字段权限", index));
            }
        });

        Map<String, DataScopeExpression> matchingScopes = context.dataScopes().entrySet().stream()
                .filter(entry -> scopeKeyMatches(entry.getKey(), resourceId, actionCode))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (left, right) -> left,
                        LinkedHashMap::new));
        boolean allData = matchingScopes.values().stream()
                .anyMatch(scope -> "ALL".equals(scope.mode())
                        || scope.terms().stream().anyMatch(term -> "ALL".equals(term.type())));
        dataAuthorization.put("requestedMode", requestedScope);
        dataAuthorization.put("effectiveScopes", matchingScopes);
        dataAuthorization.put("allData", allData);
        if (matchingScopes.isEmpty()) {
            issues.add(issue("AI_TOOL_DATA_SCOPE_DENIED", "当前发布人没有该模块动作的数据范围", index));
        } else if ("ALL".equals(requestedScope) && !allData) {
            issues.add(issue("AI_TOOL_DATA_SCOPE_EXCESSIVE", "工具请求全部数据，超过当前发布人的数据范围", index));
        }
        if (write && (!Boolean.TRUE.equals(tool.getRequiresConfirmation())
                || !Boolean.TRUE.equals(confirmationPolicy.get("writeActionsRequireConfirmation")))) {
            issues.add(issue("AI_TOOL_CONFIRMATION_REQUIRED", "写入类工具必须启用逐次人工确认", index));
        }
        return new AiConfigurationModels.ToolPreview(tool.getId(), tool.getToolType(), resourceId, actionCode,
                fieldAuthorization, dataAuthorization, Boolean.TRUE.equals(tool.getRequiresConfirmation()),
                issues.isEmpty(), List.copyOf(issues));
    }

    private Map<String, Object> snapshot(
            AiAgent agent, AiConfigurationModels.AgentPreview preview, int versionNumber) {
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("schemaVersion", 1);
        snapshot.put("versionNumber", versionNumber);
        snapshot.put("draftRevision", agent.getDraftRevision());
        snapshot.put("agent", Map.of(
                "id", agent.getId(), "systemId", agent.getSystemId(), "ownerTenantId", agent.getOwnerTenantId(),
                "code", agent.getCode(), "name", agent.getName(), "description", Objects.toString(agent.getDescription(), ""),
                "systemPrompt", agent.getSystemPromptText()));
        LinkedHashMap<String, Object> modelBinding = new LinkedHashMap<>();
        modelBinding.put("modelGrantId", agent.getModelGrantId());
        if (preview.finalModel() != null) {
            modelBinding.put("modelId", preview.finalModel().id());
            modelBinding.put("modelCode", preview.finalModel().code());
            modelBinding.put("provider", preview.finalModel().provider());
            modelBinding.put("modelName", preview.finalModel().modelName());
            modelBinding.put("modelVersion", preview.finalModel().version());
            modelBinding.put("credentialReferenceType", preview.finalModel().credentialReferenceType());
        }
        snapshot.put("modelBinding", modelBinding);
        snapshot.put("contextPolicy", readMap(agent.getContextPolicyJson()));
        snapshot.put("confirmationPolicy", readMap(agent.getConfirmationPolicyJson()));
        snapshot.put("fallbackPolicy", readMap(agent.getFallbackPolicyJson()));
        snapshot.put("tools", preview.tools());
        snapshot.put("authorizationSnapshot", preview.permissionSnapshot());
        return snapshot;
    }

    private void bind(AiAgent agent, AiConfigurationModels.AgentDraftRequest input) {
        agent.setCode(normalizeCode(input.code()));
        agent.setName(input.name().strip());
        agent.setDescription(blankToNull(input.description()));
        agent.setModelGrantId(input.modelGrantId());
        agent.setSystemPromptText(input.systemPrompt().strip());
        agent.setContextPolicyJson(writeJson(normalizeContextPolicy(input.contextPolicy())));
        agent.setConfirmationPolicyJson(writeJson(normalizeConfirmationPolicy(input.confirmationPolicy())));
        agent.setFallbackPolicyJson(writeJson(normalizeFallbackPolicy(input.fallbackPolicy())));
        agent.setStatus("DRAFT");
    }

    private void replaceTools(Long agentId, List<AiConfigurationModels.ToolInput> inputs) {
        tools(agentId).forEach(tool -> toolService.deleteById(tool.getId()));
        for (AiConfigurationModels.ToolInput input : inputs) {
            AiAgentTool tool = new AiAgentTool();
            tool.setAgentId(agentId);
            tool.setToolType(input.toolType().strip().toUpperCase(Locale.ROOT));
            tool.setResourceType("MODULE");
            tool.setResourceId(input.resourceId().strip().toLowerCase(Locale.ROOT));
            tool.setActionCode(input.actionCode().strip().toUpperCase(Locale.ROOT));
            List<String> fields = input.fieldCodes().stream().map(value -> value.strip().toLowerCase(Locale.ROOT))
                    .distinct().sorted().toList();
            tool.setFieldScopeJson(writeJson(Map.of("fieldCodes", fields)));
            tool.setDataScopeJson(writeJson(Map.of("requestedMode", input.requestedDataScope().strip().toUpperCase(Locale.ROOT))));
            tool.setRequiresConfirmation(input.requiresConfirmation());
            tool.setStatus("ACTIVE");
            tool.setVersion(0);
            toolService.insert(tool);
        }
    }

    private AiConfigurationModels.AgentView view(AiAgent agent) {
        AiAgentPublication publication = agentPublicationService.selectList(
                        Wrappers.<AiAgentPublication>lambdaQuery().eq(AiAgentPublication::getAgentId, agent.getId()))
                .stream().findFirst().orElse(null);
        Long currentVersionId = publication == null ? null : publication.getCurrentVersionId();
        List<AiConfigurationModels.ToolView> toolViews = tools(agent.getId()).stream().map(tool ->
                new AiConfigurationModels.ToolView(tool.getId(), tool.getToolType(), tool.getResourceType(),
                        tool.getResourceId(), tool.getActionCode(), fieldCodes(tool), requestedScope(tool),
                        Boolean.TRUE.equals(tool.getRequiresConfirmation()), tool.getVersion())).toList();
        List<AiConfigurationModels.VersionView> versionViews = versions(agent.getId()).stream().map(version ->
                new AiConfigurationModels.VersionView(version.getId(), version.getVersionNumber(),
                        version.getDraftRevision(), version.getSnapshotHash(), version.getPublishedByMemberId(),
                        version.getPublishedAt(), version.getId().equals(currentVersionId))).toList();
        return new AiConfigurationModels.AgentView(agent.getId(), agent.getCode(), agent.getName(),
                agent.getDescription(), agent.getModelGrantId(), agent.getDraftRevision(), agent.getSystemPromptText(),
                readMap(agent.getContextPolicyJson()), readMap(agent.getConfirmationPolicyJson()),
                readMap(agent.getFallbackPolicyJson()), agent.getStatus(), agent.getVersion(), toolViews, versionViews);
    }

    private List<AiConfigurationModels.ModuleOption> moduleOptions(AuthenticatedContext context) {
        List<AiConfigurationModels.ModuleOption> result = new ArrayList<>();
        for (ConfiguredModule module : visibleModules(context)) {
            PublishedModule published = publishedModule(context, module.getCode());
            if (published == null) continue;
            List<String> actions = published.actions().stream()
                    .filter(action -> permissionChecker.allows(context, "MODULE", module.getCode(), action)).toList();
            if (!actions.isEmpty()) {
                result.add(new AiConfigurationModels.ModuleOption(module.getCode(), module.getName(), actions,
                        published.fields()));
            }
        }
        return result.stream().sorted(Comparator.comparing(AiConfigurationModels.ModuleOption::name)).toList();
    }

    private PublishedModule publishedModule(AuthenticatedContext context, String moduleCode) {
        ConfiguredModule module = visibleModules(context).stream()
                .filter(item -> item.getCode().equals(moduleCode))
                .sorted(Comparator.comparing(item -> item.getOwnerTenantId().equals(context.tenantId()) ? 0 : 1))
                .findFirst().orElse(null);
        if (module == null) return null;
        ConfiguredModulePublication publication = modulePublicationService.selectList(
                        Wrappers.<ConfiguredModulePublication>lambdaQuery()
                                .eq(ConfiguredModulePublication::getModuleId, module.getId()))
                .stream().findFirst().orElse(null);
        if (publication == null || publication.getCurrentVersionId() == null) return null;
        ConfiguredModuleVersion version = moduleVersionService.selectById(publication.getCurrentVersionId());
        if (version == null || version.getSnapshotJson() == null) return null;
        try {
            JsonNode snapshot = objectMapper.readTree(version.getSnapshotJson());
            List<String> fields = new ArrayList<>();
            snapshot.path("fields").forEach(field -> {
                if ("ACTIVE".equals(field.path("status").asText())) fields.add(field.path("code").asText());
            });
            List<String> actions = new ArrayList<>();
            snapshot.path("actions").forEach(action -> {
                if ("ACTIVE".equals(action.path("status").asText())) actions.add(action.path("code").asText());
            });
            return new PublishedModule(module.getCode(), fields.stream().distinct().sorted().toList(),
                    actions.stream().distinct().sorted().toList(), version.getId(), version.getVersionNumber());
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private List<ConfiguredModule> visibleModules(AuthenticatedContext context) {
        Long mainTenantId = tenantService.selectList(Wrappers.<SystemTenant>lambdaQuery()
                        .eq(SystemTenant::getSystemId, context.systemId())
                        .eq(SystemTenant::getMain, true).eq(SystemTenant::getStatus, "ACTIVE"))
                .stream().map(SystemTenant::getId).findFirst().orElse(context.tenantId());
        Set<Long> tenantIds = context.tenantId().equals(mainTenantId)
                ? Set.of(mainTenantId) : Set.of(context.tenantId(), mainTenantId);
        return moduleService.selectList(Wrappers.<ConfiguredModule>lambdaQuery()
                .eq(ConfiguredModule::getSystemId, context.systemId())
                .in(ConfiguredModule::getOwnerTenantId, tenantIds)
                .eq(ConfiguredModule::getStatus, "ACTIVE"));
    }

    private List<AiSystemModelGrant> activeGrants(AuthenticatedContext context) {
        return grantService.selectList(Wrappers.<AiSystemModelGrant>lambdaQuery()
                .eq(AiSystemModelGrant::getSystemId, context.systemId())
                .eq(AiSystemModelGrant::getStatus, "ACTIVE"));
    }

    private AiSystemModelGrant requireActiveGrant(AuthenticatedContext context, Long grantId) {
        AiSystemModelGrant grant = grantService.selectById(grantId);
        if (grant == null || !context.systemId().equals(grant.getSystemId()) || !"ACTIVE".equals(grant.getStatus())) {
            throw new DomainException("AI_MODEL_NOT_GRANTED", "该模型未授权给当前系统", HttpStatus.FORBIDDEN);
        }
        AiModel model = modelService.selectById(grant.getModelId());
        if (model == null || !context.platformId().equals(model.getPlatformId()) || !"ACTIVE".equals(model.getStatus())) {
            throw conflict("AI_MODEL_INACTIVE", "授权模型不存在或已停用");
        }
        return grant;
    }

    private AiAgent requireOwned(AuthenticatedContext context, Long agentId) {
        AiAgent agent = agentService.selectById(agentId);
        if (agent == null || !context.systemId().equals(agent.getSystemId())
                || !context.tenantId().equals(agent.getOwnerTenantId())) {
            throw new DomainException("AI_AGENT_NOT_FOUND", "Agent 不存在", HttpStatus.NOT_FOUND);
        }
        return agent;
    }

    private List<AiAgentTool> tools(Long agentId) {
        return toolService.selectList(Wrappers.<AiAgentTool>lambdaQuery()
                .eq(AiAgentTool::getAgentId, agentId).eq(AiAgentTool::getStatus, "ACTIVE").orderByAsc(AiAgentTool::getId));
    }

    private List<AiAgentVersion> versions(Long agentId) {
        return agentVersionService.selectList(Wrappers.<AiAgentVersion>lambdaQuery()
                .eq(AiAgentVersion::getAgentId, agentId).orderByDesc(AiAgentVersion::getVersionNumber));
    }

    private List<String> fieldCodes(AiAgentTool tool) {
        Object value = readMap(tool.getFieldScopeJson()).get("fieldCodes");
        return value instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
    }

    private String requestedScope(AiAgentTool tool) {
        return Objects.toString(readMap(tool.getDataScopeJson()).get("requestedMode"), "CURRENT");
    }

    private Map<String, Object> normalizeContextPolicy(Map<String, Object> source) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        Object entryContexts = source.get("allowedEntryContexts");
        List<String> entries = entryContexts instanceof List<?> list
                ? list.stream().map(String::valueOf).map(value -> value.strip().toUpperCase(Locale.ROOT)).distinct().toList()
                : List.of("SYSTEM_ADMIN", "MODULE_PAGE");
        result.put("allowedEntryContexts", entries);
        result.put("allowExternalData", Boolean.TRUE.equals(source.get("allowExternalData")));
        result.put("maskSensitiveData", !Boolean.FALSE.equals(source.get("maskSensitiveData")));
        return result;
    }

    private Map<String, Object> normalizeConfirmationPolicy(Map<String, Object> source) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("writeActionsRequireConfirmation", !Boolean.FALSE.equals(source.get("writeActionsRequireConfirmation")));
        result.put("batchActionsRequireConfirmation", !Boolean.FALSE.equals(source.get("batchActionsRequireConfirmation")));
        result.put("showFieldLevelDiff", !Boolean.FALSE.equals(source.get("showFieldLevelDiff")));
        return result;
    }

    private Map<String, Object> normalizeFallbackPolicy(Map<String, Object> source) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        String mode = string(source.get("mode"));
        result.put("mode", mode == null ? "READ_ONLY" : mode.toUpperCase(Locale.ROOT));
        result.put("userMessage", Objects.toString(source.get("userMessage"), "AI 暂不可用，已切换到只读模式"));
        return result;
    }

    private Map<String, Object> permissionSnapshot(AuthenticatedContext context) {
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("accountId", context.accountId());
        snapshot.put("memberId", context.memberId());
        snapshot.put("systemId", context.systemId());
        snapshot.put("tenantId", context.tenantId());
        snapshot.put("roleIds", context.roleIds());
        snapshot.put("permissions", context.permissions());
        snapshot.put("dataScopes", context.dataScopes());
        return snapshot;
    }

    private boolean scopeKeyMatches(String key, String resourceId, String actionCode) {
        String[] parts = key.split(":", 3);
        return parts.length == 3
                && ("*".equals(parts[0]) || "MODULE".equals(parts[0]))
                && ("*".equals(parts[1]) || resourceId.equals(parts[1]))
                && ("*".equals(parts[2]) || actionCode.equals(parts[2]));
    }

    private void validateUniqueTools(List<AiConfigurationModels.ToolInput> tools) {
        long unique = tools.stream().map(tool -> String.join(":",
                tool.toolType().strip().toUpperCase(Locale.ROOT), "MODULE",
                tool.resourceId().strip().toLowerCase(Locale.ROOT),
                tool.actionCode().strip().toUpperCase(Locale.ROOT))).distinct().count();
        if (unique != tools.size()) {
            throw new DomainException("AI_AGENT_TOOL_DUPLICATE", "同一 Agent 不能重复配置相同工具范围",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private boolean containsWildcard(String value) {
        return value == null || value.isBlank() || value.contains("*");
    }

    private String normalizeCode(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value).strip().toUpperCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private AiConfigurationModels.Issue issue(String code, String message, Integer toolIndex) {
        return new AiConfigurationModels.Issue(code, message, toolIndex);
    }

    private void requireSystem(AuthenticatedContext context) {
        if (context.systemId() == null || context.tenantId() == null || context.memberId() == null) {
            throw conflict("SYSTEM_CONTEXT_REQUIRED", "请先进入系统和租户");
        }
    }

    private void audit(AuthenticatedContext context, String traceId, String eventCode, Long agentId,
                       Map<String, ?> detail) {
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                eventCode, "AI_AGENT", String.valueOf(agentId), "SUCCESS", detail);
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot read Agent policy", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot write Agent policy", exception);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private DomainException conflict(String code, String message) {
        return new DomainException(code, message, HttpStatus.CONFLICT);
    }

    private record PublishedModule(
            String code, List<String> fields, List<String> actions, Long versionId, Integer versionNumber) {
    }
}
