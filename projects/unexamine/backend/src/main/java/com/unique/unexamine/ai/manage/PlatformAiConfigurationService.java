package com.unique.unexamine.ai.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.ai.base.entity.AiModel;
import com.unique.unexamine.ai.base.entity.AiSystemModelGrant;
import com.unique.unexamine.ai.base.service.AiModelBaseService;
import com.unique.unexamine.ai.base.service.AiSystemModelGrantBaseService;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.authentication.manage.SecretReferenceResolver;
import com.unique.unexamine.shared.manage.web.DomainException;
import com.unique.unexamine.system.base.entity.SystemDefinition;
import com.unique.unexamine.system.base.service.SystemDefinitionBaseService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class PlatformAiConfigurationService {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() { };
    private static final Set<String> CAPABILITIES = Set.of("CHAT", "VISION", "EMBEDDING", "TOOL_CALLING");

    private final AiModelBaseService modelService;
    private final AiSystemModelGrantBaseService grantService;
    private final SystemDefinitionBaseService systemService;
    private final SecretReferenceResolver secretResolver;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;

    public PlatformAiConfigurationService(
            AiModelBaseService modelService,
            AiSystemModelGrantBaseService grantService,
            SystemDefinitionBaseService systemService,
            SecretReferenceResolver secretResolver,
            AuditRecorder auditRecorder,
            ObjectMapper objectMapper) {
        this.modelService = modelService;
        this.grantService = grantService;
        this.systemService = systemService;
        this.secretResolver = secretResolver;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public AiConfigurationModels.PlatformOverview overview(AuthenticatedContext context) {
        requirePlatform(context);
        List<AiConfigurationModels.ModelView> models = modelService.selectList(
                        Wrappers.<AiModel>lambdaQuery().eq(AiModel::getPlatformId, context.platformId())
                                .orderByAsc(AiModel::getCode))
                .stream().map(model -> viewModel(model, true)).toList();
        List<AiConfigurationModels.SystemOption> systems = systems(context.platformId()).stream()
                .map(system -> new AiConfigurationModels.SystemOption(system.getId(), system.getCode(), system.getName()))
                .toList();
        return new AiConfigurationModels.PlatformOverview(models, systems);
    }

    @Transactional
    public AiConfigurationModels.ModelView createModel(
            AuthenticatedContext context, AiConfigurationModels.ModelRequest input, String traceId) {
        requirePlatform(context);
        String code = input.code().strip().toLowerCase(Locale.ROOT);
        if (!modelService.selectList(Wrappers.<AiModel>lambdaQuery()
                .eq(AiModel::getPlatformId, context.platformId()).eq(AiModel::getCode, code)).isEmpty()) {
            throw conflict("AI_MODEL_CODE_CONFLICT", "平台中已存在相同模型编码");
        }
        ValidatedModel validated = validate(input);
        AiModel model = new AiModel();
        model.setPlatformId(context.platformId());
        bind(model, input, validated);
        model.setVersion(0);
        modelService.insert(model);
        auditRecorder.record(traceId, context.accountId(), null, null, null,
                "AI_MODEL_CONFIGURED", "AI_MODEL", String.valueOf(model.getId()), "SUCCESS", Map.of(
                        "modelCode", code,
                        "provider", model.getProvider(),
                        "credentialReferenceType", credentialReferenceType(model.getCredentialRef()),
                        "capabilities", validated.capabilities()));
        return viewModel(model, true);
    }

    @Transactional
    public AiConfigurationModels.ModelView updateModel(
            AuthenticatedContext context, Long modelId, AiConfigurationModels.ModelRequest input, String traceId) {
        requirePlatform(context);
        AiModel model = requireModel(context.platformId(), modelId);
        if (input.expectedVersion() == null || !Objects.equals(input.expectedVersion(), model.getVersion())) {
            throw conflict("AI_MODEL_VERSION_CONFLICT", "模型配置已变化，请刷新后重试");
        }
        String code = input.code().strip().toLowerCase(Locale.ROOT);
        boolean codeExists = modelService.selectList(Wrappers.<AiModel>lambdaQuery()
                        .eq(AiModel::getPlatformId, context.platformId()).eq(AiModel::getCode, code))
                .stream().anyMatch(item -> !item.getId().equals(modelId));
        if (codeExists) throw conflict("AI_MODEL_CODE_CONFLICT", "平台中已存在相同模型编码");
        ValidatedModel validated = validate(input);
        bind(model, input, validated);
        if (modelService.updateById(model) != 1) {
            throw conflict("AI_MODEL_VERSION_CONFLICT", "模型配置已变化，请刷新后重试");
        }
        auditRecorder.record(traceId, context.accountId(), null, null, null,
                "AI_MODEL_CONFIGURED", "AI_MODEL", String.valueOf(modelId), "SUCCESS", Map.of(
                        "modelCode", code,
                        "provider", model.getProvider(),
                        "credentialReferenceType", credentialReferenceType(model.getCredentialRef()),
                        "version", model.getVersion()));
        return viewModel(requireModel(context.platformId(), modelId), true);
    }

    @Transactional
    public AiConfigurationModels.GrantView grant(
            AuthenticatedContext context,
            Long modelId,
            Long systemId,
            AiConfigurationModels.GrantRequest input,
            String traceId) {
        requirePlatform(context);
        AiModel model = requireModel(context.platformId(), modelId);
        if (!"ACTIVE".equals(model.getStatus())) {
            throw conflict("AI_MODEL_INACTIVE", "停用模型不能授权给系统");
        }
        ensureCredentialAvailable(model.getCredentialRef());
        SystemDefinition system = systemService.selectById(systemId);
        if (system == null || !context.platformId().equals(system.getPlatformId())
                || Boolean.TRUE.equals(system.getDeleted()) || !"ACTIVE".equals(system.getStatus())) {
            throw new DomainException("AI_GRANT_SYSTEM_NOT_FOUND", "授权目标系统不存在或不可用", HttpStatus.NOT_FOUND);
        }
        Map<String, Object> modelLimits = readMap(model.getLimitPolicyJson());
        long modelDaily = number(modelLimits.get("dailyTokenLimit"));
        int modelConcurrency = Math.toIntExact(number(modelLimits.get("concurrencyLimit")));
        if (input.dailyTokenLimit() > modelDaily || input.concurrencyLimit() > modelConcurrency) {
            throw new DomainException("AI_GRANT_LIMIT_EXCEEDED", "系统授权额度不能超过平台模型上限",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        AiSystemModelGrant grant = grantService.selectList(Wrappers.<AiSystemModelGrant>lambdaQuery()
                        .eq(AiSystemModelGrant::getModelId, modelId).eq(AiSystemModelGrant::getSystemId, systemId))
                .stream().findFirst().orElse(null);
        Map<String, Object> limits = Map.of(
                "dailyTokenLimit", input.dailyTokenLimit(),
                "concurrencyLimit", input.concurrencyLimit());
        if (grant == null) {
            grant = new AiSystemModelGrant();
            grant.setModelId(modelId);
            grant.setSystemId(systemId);
            grant.setUsageLimitJson(writeJson(limits));
            grant.setStatus("ACTIVE");
            grant.setGrantedByAccountId(context.accountId());
            grant.setGrantedAt(LocalDateTime.now());
            grant.setVersion(0);
            grantService.insert(grant);
        } else {
            if (input.expectedVersion() != null && !Objects.equals(input.expectedVersion(), grant.getVersion())) {
                throw conflict("AI_MODEL_GRANT_VERSION_CONFLICT", "模型授权已变化，请刷新后重试");
            }
            grant.setUsageLimitJson(writeJson(limits));
            grant.setStatus("ACTIVE");
            grant.setGrantedByAccountId(context.accountId());
            grant.setGrantedAt(LocalDateTime.now());
            grant.setRevokedAt(null);
            if (grantService.updateById(grant) != 1) {
                throw conflict("AI_MODEL_GRANT_VERSION_CONFLICT", "模型授权已变化，请刷新后重试");
            }
        }
        auditRecorder.record(traceId, context.accountId(), null, null, null,
                "AI_MODEL_GRANTED", "AI_MODEL_GRANT", String.valueOf(grant.getId()), "SUCCESS", Map.of(
                        "modelId", modelId, "modelCode", model.getCode(), "systemId", systemId,
                        "usageLimit", limits));
        return viewGrant(grant, system);
    }

    AiConfigurationModels.ModelView viewModel(AiModel model, boolean includeGrants) {
        Map<Long, SystemDefinition> systems = systems(model.getPlatformId()).stream()
                .collect(java.util.stream.Collectors.toMap(SystemDefinition::getId, item -> item));
        List<AiConfigurationModels.GrantView> grants = includeGrants
                ? grantService.selectList(Wrappers.<AiSystemModelGrant>lambdaQuery()
                        .eq(AiSystemModelGrant::getModelId, model.getId()).orderByAsc(AiSystemModelGrant::getSystemId))
                .stream().map(grant -> viewGrant(grant, systems.get(grant.getSystemId()))).toList()
                : List.of();
        return new AiConfigurationModels.ModelView(
                model.getId(), null, model.getCode(), model.getName(), model.getProvider(), model.getModelName(),
                model.getEndpointUrl(), credentialReferenceType(model.getCredentialRef()),
                credentialAvailable(model.getCredentialRef()), readList(model.getCapabilitiesJson()),
                readMap(model.getLimitPolicyJson()), model.getStatus(), model.getVersion(), grants);
    }

    private AiConfigurationModels.GrantView viewGrant(AiSystemModelGrant grant, SystemDefinition system) {
        return new AiConfigurationModels.GrantView(grant.getId(), grant.getModelId(), grant.getSystemId(),
                system == null ? null : system.getCode(), system == null ? null : system.getName(),
                readMap(grant.getUsageLimitJson()), grant.getStatus(), grant.getVersion(), grant.getGrantedAt());
    }

    private void bind(AiModel model, AiConfigurationModels.ModelRequest input, ValidatedModel validated) {
        model.setCode(input.code().strip().toLowerCase(Locale.ROOT));
        model.setName(input.name().strip());
        model.setProvider(input.provider().strip().toUpperCase(Locale.ROOT));
        model.setModelName(input.modelName().strip());
        model.setEndpointUrl(blankToNull(input.endpointUrl()));
        model.setCredentialRef(input.credentialRef().strip());
        model.setCapabilitiesJson(writeJson(validated.capabilities()));
        model.setLimitPolicyJson(writeJson(validated.limitPolicy()));
        model.setStatus("ACTIVE");
    }

    private ValidatedModel validate(AiConfigurationModels.ModelRequest input) {
        List<String> capabilities = input.capabilities().stream()
                .map(value -> value.strip().toUpperCase(Locale.ROOT)).distinct().sorted().toList();
        if (capabilities.stream().anyMatch(value -> !CAPABILITIES.contains(value)) || !capabilities.contains("CHAT")) {
            throw new DomainException("AI_MODEL_CAPABILITY_INVALID", "模型能力必须包含 CHAT，且只能使用受支持的能力",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        validateEndpoint(input.provider(), input.endpointUrl());
        ensureCredentialAvailable(input.credentialRef());
        if (!Boolean.TRUE.equals(input.logMasking())) {
            throw new DomainException("AI_MODEL_LOG_MASKING_REQUIRED", "模型日志必须启用敏感信息遮蔽",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        Map<String, Object> limitPolicy = new LinkedHashMap<>();
        limitPolicy.put("dailyTokenLimit", input.dailyTokenLimit());
        limitPolicy.put("concurrencyLimit", input.concurrencyLimit());
        limitPolicy.put("logMasking", true);
        limitPolicy.put("dataResidency", input.dataResidency());
        return new ValidatedModel(capabilities, limitPolicy);
    }

    private void validateEndpoint(String provider, String endpointUrl) {
        String normalizedProvider = provider.strip().toUpperCase(Locale.ROOT);
        String endpoint = blankToNull(endpointUrl);
        if (endpoint == null && !"LOCAL".equals(normalizedProvider)) {
            throw new DomainException("AI_MODEL_ENDPOINT_REQUIRED", "外部模型必须配置 HTTPS 接口地址",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (endpoint == null) return;
        try {
            URI uri = URI.create(endpoint);
            boolean local = "LOCAL".equals(normalizedProvider)
                    || "localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost());
            if (uri.getHost() == null || (!local && !"https".equalsIgnoreCase(uri.getScheme()))) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new DomainException("AI_MODEL_ENDPOINT_INVALID", "外部模型地址必须是有效 HTTPS 地址，本地模型可使用本机地址",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    void ensureCredentialAvailable(String reference) {
        try {
            String resolved = secretResolver.resolve(reference == null ? null : reference.strip());
            if (resolved == null || resolved.isBlank()) throw new IllegalStateException();
        } catch (Exception exception) {
            throw new DomainException("AI_MODEL_CREDENTIAL_UNAVAILABLE", "模型凭证引用当前不可用",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    boolean credentialAvailable(String reference) {
        try {
            ensureCredentialAvailable(reference);
            return true;
        } catch (DomainException exception) {
            return false;
        }
    }

    private AiModel requireModel(Long platformId, Long modelId) {
        AiModel model = modelService.selectById(modelId);
        if (model == null || !platformId.equals(model.getPlatformId())) {
            throw new DomainException("AI_MODEL_NOT_FOUND", "模型不存在", HttpStatus.NOT_FOUND);
        }
        return model;
    }

    private List<SystemDefinition> systems(Long platformId) {
        return systemService.selectList(Wrappers.<SystemDefinition>lambdaQuery()
                        .eq(SystemDefinition::getPlatformId, platformId)
                        .eq(SystemDefinition::getStatus, "ACTIVE")
                        .eq(SystemDefinition::getDeleted, false))
                .stream().sorted(Comparator.comparing(SystemDefinition::getName)).toList();
    }

    private void requirePlatform(AuthenticatedContext context) {
        if (context.systemId() != null || context.platformId() == null) {
            throw conflict("PLATFORM_CONTEXT_REQUIRED", "请切换到平台上下文");
        }
    }

    private String credentialReferenceType(String reference) {
        int separator = reference == null ? -1 : reference.indexOf(':');
        return separator > 0 ? reference.substring(0, separator).toUpperCase(Locale.ROOT) : "UNKNOWN";
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot read AI model policy", exception);
        }
    }

    private List<String> readList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, STRING_LIST_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot read AI model capabilities", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot write AI model configuration", exception);
        }
    }

    private long number(Object value) {
        return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private DomainException conflict(String code, String message) {
        return new DomainException(code, message, HttpStatus.CONFLICT);
    }

    private record ValidatedModel(List<String> capabilities, Map<String, Object> limitPolicy) {
    }
}
