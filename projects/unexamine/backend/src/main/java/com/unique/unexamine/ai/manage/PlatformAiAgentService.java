package com.unique.unexamine.ai.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.ai.base.entity.AiAgent;
import com.unique.unexamine.ai.base.entity.AiAgentPublication;
import com.unique.unexamine.ai.base.entity.AiAgentTool;
import com.unique.unexamine.ai.base.entity.AiAgentVersion;
import com.unique.unexamine.ai.base.entity.AiModel;
import com.unique.unexamine.ai.base.service.AiAgentBaseService;
import com.unique.unexamine.ai.base.service.AiAgentPublicationBaseService;
import com.unique.unexamine.ai.base.service.AiAgentToolBaseService;
import com.unique.unexamine.ai.base.service.AiAgentVersionBaseService;
import com.unique.unexamine.ai.base.service.AiModelBaseService;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.authentication.manage.SecretReferenceResolver;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 平台 Agent 草稿与发布，只允许配置平台资源工具，不接触系统模块。 */
@Service
public class PlatformAiAgentService {
    private static final Set<String> PLATFORM_RESOURCES = Set.of("PLATFORM_TASK", "PLATFORM_MESSAGE",
            "PLATFORM_LOG", "PLATFORM_FLOW", "PLATFORM_SYSTEM", "PLATFORM_HEALTH", "MODEL_QUOTA");

    private final AiAgentBaseService agents;
    private final AiAgentToolBaseService tools;
    private final AiAgentVersionBaseService versions;
    private final AiAgentPublicationBaseService publications;
    private final AiModelBaseService models;
    private final SecretReferenceResolver secrets;
    private final AuditRecorder audit;
    private final ObjectMapper json;

    public PlatformAiAgentService(AiAgentBaseService agents, AiAgentToolBaseService tools,
            AiAgentVersionBaseService versions, AiAgentPublicationBaseService publications,
            AiModelBaseService models, SecretReferenceResolver secrets, AuditRecorder audit,
            ObjectMapper json) {
        this.agents = agents;
        this.tools = tools;
        this.versions = versions;
        this.publications = publications;
        this.models = models;
        this.secrets = secrets;
        this.audit = audit;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public List<AiConfigurationModels.PlatformAgentView> list(AuthenticatedContext context) {
        requirePlatform(context);
        return agents.selectList(Wrappers.<AiAgent>lambdaQuery()
                .eq(AiAgent::getContextType, "PLATFORM")
                .eq(AiAgent::getPlatformId, context.platformId())
                .orderByAsc(AiAgent::getCode)).stream().map(this::view).toList();
    }

    /** 创建平台默认 Agent 草稿，工具仅能读取所选平台资源。 */
    @Transactional
    public AiConfigurationModels.PlatformAgentView create(AuthenticatedContext context,
            AiConfigurationModels.PlatformAgentRequest input, String traceId) {
        requirePlatform(context);
        AiModel model = requireModel(context, input.modelId());
        String code = input.code().strip().toLowerCase(Locale.ROOT);
        checkCode(context, code, null);
        List<String> capabilities = capabilities(input.capabilities());
        AiAgent agent = new AiAgent();
        agent.setContextType("PLATFORM");
        agent.setPlatformId(context.platformId());
        agent.setCreatedByAccountId(context.accountId());
        agent.setCreatedByPlatformMemberAccountId(context.accountId());
        agent.setDraftRevision(1);
        agent.setVersion(0);
        bind(agent, input, code, model);
        agents.insert(agent);
        replaceTools(agent, capabilities);
        audit.record(traceId, context.accountId(), null, null, null, "AI_PLATFORM_AGENT_DRAFT_CREATED",
                "AI_AGENT", String.valueOf(agent.getId()), "SUCCESS",
                Map.of("code", code, "capabilities", capabilities));
        return view(agents.selectById(agent.getId()));
    }

    /** 编辑草稿不改变当前发布版本，直到管理员再次发布。 */
    @Transactional
    public AiConfigurationModels.PlatformAgentView saveDraft(AuthenticatedContext context, Long agentId,
            AiConfigurationModels.PlatformAgentRequest input, String traceId) {
        requirePlatform(context);
        AiAgent agent = requireOwned(context, agentId);
        if (input.expectedVersion() == null || !Objects.equals(input.expectedVersion(), agent.getVersion())) {
            throw conflict("AI_AGENT_VERSION_CONFLICT", "平台 Agent 草稿已变化，请刷新后重试");
        }
        AiModel model = requireModel(context, input.modelId());
        String code = input.code().strip().toLowerCase(Locale.ROOT);
        checkCode(context, code, agentId);
        List<String> capabilities = capabilities(input.capabilities());
        bind(agent, input, code, model);
        agent.setDraftRevision(agent.getDraftRevision() + 1);
        if (agents.updateById(agent) != 1) {
            throw conflict("AI_AGENT_VERSION_CONFLICT", "平台 Agent 草稿已变化，请刷新后重试");
        }
        replaceTools(agent, capabilities);
        audit.record(traceId, context.accountId(), null, null, null, "AI_PLATFORM_AGENT_DRAFT_SAVED",
                "AI_AGENT", String.valueOf(agentId), "SUCCESS",
                Map.of("code", code, "draftRevision", agent.getDraftRevision()));
        return view(agents.selectById(agentId));
    }

    /** 发布时重新确认模型和凭据可用，发布快照不包含密钥明文。 */
    @Transactional
    public AiConfigurationModels.PlatformAgentView publish(AuthenticatedContext context, Long agentId,
            AiConfigurationModels.PublishRequest input, String traceId) {
        requirePlatform(context);
        AiAgent agent = requireOwned(context, agentId);
        if (!Objects.equals(input.expectedDraftRevision(), agent.getDraftRevision())) {
            throw conflict("AI_AGENT_DRAFT_REVISION_CONFLICT", "平台 Agent 草稿已变化，请重新检查");
        }
        if (!versions.selectList(Wrappers.<AiAgentVersion>lambdaQuery()
                .eq(AiAgentVersion::getAgentId, agentId)
                .eq(AiAgentVersion::getDraftRevision, agent.getDraftRevision())).isEmpty()) {
            throw conflict("AI_AGENT_DRAFT_ALREADY_PUBLISHED", "当前草稿已经发布");
        }
        AiModel model = requireModel(context, agent.getModelId());
        if (!"LOCAL".equalsIgnoreCase(model.getProvider()) || !"none".equalsIgnoreCase(model.getCredentialRef())) {
            try {
                secrets.resolve(model.getCredentialRef());
            } catch (RuntimeException failure) {
                throw new DomainException("AI_MODEL_CREDENTIAL_UNAVAILABLE", "平台模型凭证不可用",
                        HttpStatus.SERVICE_UNAVAILABLE);
            }
        }
        List<String> allowed = tools(agentId).stream().map(AiAgentTool::getResourceType).sorted().toList();
        if (allowed.isEmpty()) throw conflict("AI_PLATFORM_AGENT_TOOL_REQUIRED", "平台 Agent 至少需要一项平台能力");
        int versionNumber = versions(agentId).stream().map(AiAgentVersion::getVersionNumber)
                .max(Integer::compareTo).orElse(0) + 1;
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("schemaVersion", 1);
        snapshot.put("contextType", "PLATFORM");
        snapshot.put("agentId", agentId);
        snapshot.put("code", agent.getCode());
        snapshot.put("name", agent.getName());
        snapshot.put("systemPrompt", agent.getSystemPromptText());
        snapshot.put("capabilities", allowed);
        snapshot.put("modelId", model.getId());
        snapshot.put("modelVersion", model.getVersion());
        String snapshotJson = writeJson(snapshot);
        AiAgentVersion version = new AiAgentVersion();
        version.setAgentId(agentId);
        version.setContextType("PLATFORM");
        version.setPlatformId(context.platformId());
        version.setModelId(model.getId());
        version.setModelConfigVersion(model.getVersion());
        version.setCredentialRef(model.getCredentialRef());
        version.setCredentialVersion(model.getCredentialVersion());
        version.setPolicyVersion(agent.getDraftRevision());
        version.setVersionNumber(versionNumber);
        version.setDraftRevision(agent.getDraftRevision());
        version.setSnapshotHash(sha256(snapshotJson));
        version.setSnapshotJson(snapshotJson);
        version.setPolicySnapshotJson(writeJson(Map.of("contextType", "PLATFORM",
                "capabilities", allowed, "denySystemBusinessData", true)));
        version.setPublishedByAccountId(context.accountId());
        version.setPublishedByPlatformMemberAccountId(context.accountId());
        version.setPublishTime(LocalDateTime.now());
        versions.insert(version);

        AiAgentPublication publication = publications.selectList(Wrappers.<AiAgentPublication>lambdaQuery()
                .eq(AiAgentPublication::getAgentId, agentId)).stream().findFirst().orElse(null);
        if (publication == null) {
            publication = new AiAgentPublication();
            publication.setAgentId(agentId);
            publication.setContextType("PLATFORM");
            publication.setPlatformId(context.platformId());
            publication.setCurrentVersionId(version.getId());
            publication.setUpdatedByAccountId(context.accountId());
            publication.setUpdatedByPlatformMemberAccountId(context.accountId());
            publication.setVersion(0);
            publications.insert(publication);
        } else {
            publication.setCurrentVersionId(version.getId());
            publication.setUpdatedByAccountId(context.accountId());
            publication.setUpdatedByPlatformMemberAccountId(context.accountId());
            if (publications.updateById(publication) != 1) {
                throw conflict("AI_AGENT_PUBLICATION_VERSION_CONFLICT", "平台 Agent 发布版本已变化");
            }
        }
        agent.setStatus("PUBLISHED");
        if (agents.updateById(agent) != 1) {
            throw conflict("AI_AGENT_VERSION_CONFLICT", "平台 Agent 草稿已变化，请重试");
        }
        audit.record(traceId, context.accountId(), null, null, null, "AI_PLATFORM_AGENT_PUBLISHED",
                "AI_AGENT_VERSION", String.valueOf(version.getId()), "SUCCESS",
                Map.of("agentId", agentId, "versionNumber", versionNumber, "capabilities", allowed));
        return view(agents.selectById(agentId));
    }

    private void bind(AiAgent agent, AiConfigurationModels.PlatformAgentRequest input,
            String code, AiModel model) {
        agent.setCode(code);
        agent.setName(input.name().strip());
        agent.setDescription(input.description() == null || input.description().isBlank()
                ? null : input.description().strip());
        agent.setModelId(model.getId());
        agent.setSystemPromptText(input.systemPrompt().strip());
        agent.setContextPolicyJson(writeJson(Map.of("allowedEntryContexts", List.of("PLATFORM_HOME"),
                "allowSystemBusinessData", false, "maskSensitiveData", true)));
        agent.setConfirmationPolicyJson(writeJson(Map.of("writeActionsRequireConfirmation", true)));
        agent.setFallbackPolicyJson(writeJson(Map.of("mode", "READ_ONLY",
                "userMessage", "平台智能助手暂不可用")));
        agent.setStatus("DRAFT");
    }

    private void replaceTools(AiAgent agent, List<String> capabilities) {
        tools(agent.getId()).forEach(tool -> tools.deleteById(tool.getId()));
        for (String capability : capabilities) {
            AiAgentTool tool = new AiAgentTool();
            tool.setAgentId(agent.getId());
            tool.setContextType("PLATFORM");
            tool.setPlatformId(agent.getPlatformId());
            tool.setToolType("QUERY");
            tool.setResourceType(capability);
            tool.setResourceId("*");
            tool.setActionCode("VIEW");
            tool.setFieldScopeJson("{}");
            tool.setDataScopeJson("{}");
            tool.setOutboundPolicyJson("{\"allowSystemBusinessData\":false}");
            tool.setMaskingPolicyJson("{\"maskSensitiveData\":true}");
            tool.setPolicyVersion(agent.getDraftRevision());
            tool.setRequiresConfirmation(false);
            tool.setStatus("ACTIVE");
            tool.setVersion(0);
            tools.insert(tool);
        }
    }

    private AiConfigurationModels.PlatformAgentView view(AiAgent agent) {
        Long currentVersionId = publications.selectList(Wrappers.<AiAgentPublication>lambdaQuery()
                        .eq(AiAgentPublication::getAgentId, agent.getId()))
                .stream().map(AiAgentPublication::getCurrentVersionId).findFirst().orElse(null);
        List<AiConfigurationModels.VersionView> history = versions(agent.getId()).stream()
                .map(version -> new AiConfigurationModels.VersionView(version.getId(),
                        version.getVersionNumber(), version.getDraftRevision(), version.getSnapshotHash(),
                        null, version.getPublishTime(), version.getId().equals(currentVersionId)))
                .toList();
        return new AiConfigurationModels.PlatformAgentView(agent.getId(), agent.getCode(), agent.getName(),
                agent.getDescription(), agent.getModelId(), agent.getSystemPromptText(),
                tools(agent.getId()).stream().map(AiAgentTool::getResourceType).sorted().toList(),
                agent.getDraftRevision(), agent.getStatus(), agent.getVersion(), history);
    }

    private AiAgent requireOwned(AuthenticatedContext context, Long agentId) {
        AiAgent agent = agents.selectById(agentId);
        if (agent == null || !"PLATFORM".equals(agent.getContextType())
                || !context.platformId().equals(agent.getPlatformId())) {
            throw new DomainException("AI_AGENT_NOT_FOUND", "平台 Agent 不存在", HttpStatus.NOT_FOUND);
        }
        return agent;
    }

    private AiModel requireModel(AuthenticatedContext context, Long modelId) {
        AiModel model = models.selectById(modelId);
        if (model == null || !context.platformId().equals(model.getPlatformId())
                || !"PLATFORM".equals(model.getScopeType()) || !"ACTIVE".equals(model.getStatus())) {
            throw new DomainException("AI_MODEL_NOT_FOUND", "平台可用模型不存在", HttpStatus.NOT_FOUND);
        }
        return model;
    }

    private void checkCode(AuthenticatedContext context, String code, Long excludingId) {
        boolean exists = agents.selectList(Wrappers.<AiAgent>lambdaQuery()
                        .eq(AiAgent::getContextType, "PLATFORM")
                        .eq(AiAgent::getPlatformId, context.platformId()).eq(AiAgent::getCode, code))
                .stream().anyMatch(item -> !item.getId().equals(excludingId));
        if (exists) throw conflict("AI_AGENT_CODE_CONFLICT", "平台中已存在相同 Agent 编码");
    }

    private List<String> capabilities(List<String> raw) {
        List<String> result = raw.stream().map(value -> value.strip().toUpperCase(Locale.ROOT))
                .distinct().sorted().toList();
        if (result.isEmpty() || result.stream().anyMatch(value -> !PLATFORM_RESOURCES.contains(value))) {
            throw new DomainException("AI_PLATFORM_AGENT_CAPABILITY_INVALID",
                    "平台 Agent 只能选择平台授权、日志、任务、消息、流程、健康和模型额度能力",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        return result;
    }

    private List<AiAgentTool> tools(Long agentId) {
        return tools.selectList(Wrappers.<AiAgentTool>lambdaQuery()
                .eq(AiAgentTool::getAgentId, agentId).eq(AiAgentTool::getStatus, "ACTIVE")
                .orderByAsc(AiAgentTool::getId));
    }

    private List<AiAgentVersion> versions(Long agentId) {
        return versions.selectList(Wrappers.<AiAgentVersion>lambdaQuery()
                .eq(AiAgentVersion::getAgentId, agentId).orderByDesc(AiAgentVersion::getVersionNumber));
    }

    private String writeJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Cannot serialize platform Agent", failure);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is not available", failure);
        }
    }

    private void requirePlatform(AuthenticatedContext context) {
        if (context.platformId() == null || context.systemId() != null) {
            throw conflict("PLATFORM_CONTEXT_REQUIRED", "请切换到平台上下文");
        }
    }

    private DomainException conflict(String code, String message) {
        return new DomainException(code, message, HttpStatus.CONFLICT);
    }
}
