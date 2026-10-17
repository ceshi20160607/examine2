package com.unique.unexamine.ai.manage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.ai.base.entity.AiModel;
import com.unique.unexamine.ai.base.entity.AiSystemModelGrant;
import com.unique.unexamine.ai.base.service.AiModelBaseService;
import com.unique.unexamine.ai.base.service.AiSystemModelGrantBaseService;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.authentication.manage.SecretReferenceResolver;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/** 平台模型授权后的实际对话调用，统一限制外发范围、日额度和并发。 */
@Service
public class AiModelGateway {
    private static final DefaultRedisScript<Long> RESERVE = new DefaultRedisScript<>("""
            local modelUsed = tonumber(redis.call('GET', KEYS[1]) or '0')
            local modelActive = tonumber(redis.call('GET', KEYS[2]) or '0')
            local grantUsed = tonumber(redis.call('GET', KEYS[3]) or '0')
            local grantActive = tonumber(redis.call('GET', KEYS[4]) or '0')
            if modelUsed + tonumber(ARGV[1]) > tonumber(ARGV[2]) then return 1 end
            if modelActive >= tonumber(ARGV[3]) then return 2 end
            if grantUsed + tonumber(ARGV[1]) > tonumber(ARGV[4]) then return 3 end
            if grantActive >= tonumber(ARGV[5]) then return 4 end
            redis.call('INCRBY', KEYS[1], ARGV[1]); redis.call('EXPIRE', KEYS[1], 172800)
            redis.call('INCR', KEYS[2]); redis.call('EXPIRE', KEYS[2], 60)
            redis.call('INCRBY', KEYS[3], ARGV[1]); redis.call('EXPIRE', KEYS[3], 172800)
            redis.call('INCR', KEYS[4]); redis.call('EXPIRE', KEYS[4], 60)
            return 0
            """, Long.class);
    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>("""
            local adjustment = tonumber(ARGV[1]) - tonumber(ARGV[2])
            redis.call('INCRBY', KEYS[1], adjustment)
            redis.call('INCRBY', KEYS[3], adjustment)
            redis.call('SET', KEYS[2], math.max(0, tonumber(redis.call('GET', KEYS[2]) or '0') - 1), 'EX', 60)
            redis.call('SET', KEYS[4], math.max(0, tonumber(redis.call('GET', KEYS[4]) or '0') - 1), 'EX', 60)
            return 1
            """, Long.class);

    private final AiModelBaseService models;
    private final AiSystemModelGrantBaseService grants;
    private final SecretReferenceResolver secrets;
    private final StringRedisTemplate redis;
    private final ObjectMapper json;

    public AiModelGateway(AiModelBaseService models, AiSystemModelGrantBaseService grants,
            SecretReferenceResolver secrets, StringRedisTemplate redis, ObjectMapper json) {
        this.models = models;
        this.grants = grants;
        this.secrets = secrets;
        this.redis = redis;
        this.json = json;
    }

    /** 调用前使用当前授权和密钥；旧 Agent 快照不能维持已撤回的使用权。 */
    public Completion complete(AuthenticatedContext context, long grantId, String systemPrompt,
            String userPrompt, boolean containsBusinessData, boolean allowExternalData) {
        AiSystemModelGrant grant = grants.selectById(grantId);
        AiModel model = grant == null ? null : models.selectById(grant.getModelId());
        if (grant == null || model == null || !"ACTIVE".equals(grant.getStatus())
                || !"ACTIVE".equals(model.getStatus()) || !context.systemId().equals(grant.getSystemId())
                || !context.platformId().equals(grant.getPlatformId())
                || !context.platformId().equals(model.getPlatformId())
                || !model.getScopeType().equals(model.getCredentialScopeType())
                || "SYSTEM".equals(model.getScopeType()) && !context.systemId().equals(model.getSystemId())) {
            throw unavailable("AI_MODEL_NOT_GRANTED", "模型授权已失效");
        }
        return invoke(model, grantId, policy(grant.getUsageLimitJson()), systemPrompt,
                userPrompt, containsBusinessData, allowExternalData);
    }

    /** 平台 Agent 使用平台自己的模型额度，不借用任何系统模型授权。 */
    public Completion completePlatform(AuthenticatedContext context, long modelId,
            String systemPrompt, String userPrompt) {
        AiModel model = models.selectById(modelId);
        if (context.systemId() != null || model == null || !"ACTIVE".equals(model.getStatus())
                || !context.platformId().equals(model.getPlatformId())
                || !"PLATFORM".equals(model.getScopeType())
                || !"PLATFORM".equals(model.getCredentialScopeType())) {
            throw unavailable("AI_PLATFORM_MODEL_UNAVAILABLE", "平台模型不可用");
        }
        return invoke(model, -modelId, policy(model.getLimitPolicyJson()), systemPrompt,
                userPrompt, false, true);
    }

    private Completion invoke(AiModel model, long capacityScopeId, JsonNode capacityPolicy,
            String systemPrompt, String userPrompt, boolean containsBusinessData,
            boolean allowExternalData) {
        URI endpoint = endpoint(model);
        boolean local = isLocal(endpoint);
        String residency = policy(model.getOutboundPolicyJson()).path("dataResidency").asText("LOCAL_ONLY");
        if (!policy(model.getMaskingPolicyJson()).path("logMasking").asBoolean(false)) {
            throw unavailable("AI_MODEL_MASKING_REQUIRED", "模型脱敏策略未启用");
        }
        if ((!local && (!"ALLOW_EXTERNAL".equals(residency)
                || containsBusinessData && !allowExternalData))
                || "LOCAL_ONLY".equals(residency) && !local) {
            throw unavailable("AI_MODEL_OUTBOUND_DENIED", "当前模型不允许外发本次业务数据");
        }
        String credential = "";
        if (!"LOCAL".equalsIgnoreCase(model.getProvider()) || !"none".equalsIgnoreCase(model.getCredentialRef())) {
            try {
                credential = secrets.resolve(model.getCredentialRef());
            } catch (RuntimeException failure) {
                throw unavailable("AI_MODEL_CREDENTIAL_UNAVAILABLE", "模型凭证不可用");
            }
        }
        String maskedUser = maskSensitive(userPrompt);
        String maskedSystem = maskSensitive(systemPrompt);
        if (maskedUser.length() > 12000 || maskedSystem.length() > 8000) {
            throw unavailable("AI_MODEL_INPUT_TOO_LARGE", "模型输入超过限制");
        }
        int reserved = maskedUser.getBytes(StandardCharsets.UTF_8).length
                + maskedSystem.getBytes(StandardCharsets.UTF_8).length + 512;
        JsonNode modelLimits = policy(model.getLimitPolicyJson());
        List<String> keys = keys(model.getId(), capacityScopeId);
        try {
            Long result = redis.execute(RESERVE, keys, Integer.toString(reserved),
                    modelLimits.path("dailyTokenLimit").asText(), modelLimits.path("concurrencyLimit").asText(),
                    capacityPolicy.path("dailyTokenLimit").asText(), capacityPolicy.path("concurrencyLimit").asText());
            if (result == null) throw new IllegalStateException("Redis reservation missing");
            if (result != 0) throw new DomainException("AI_MODEL_CAPACITY_EXCEEDED",
                    "模型额度或并发已达到上限", HttpStatus.TOO_MANY_REQUESTS);
        } catch (DomainException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw unavailable("AI_MODEL_CAPACITY_UNAVAILABLE", "模型额度状态暂时不可用");
        }

        int actual = 0;
        try {
            String payload = json.writeValueAsString(Map.of("model", model.getModelName(),
                    "messages", List.of(Map.of("role", "system", "content", maskedSystem),
                            Map.of("role", "user", "content", maskedUser)),
                    "max_tokens", 512, "stream", false));
            HttpRequest.Builder request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload));
            if (!credential.isBlank()) request.header("Authorization", "Bearer " + credential);
            // 模型网络客户端只在实际调用时创建；网络栈异常不能阻止普通系统业务启动。
            HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                    .followRedirects(HttpClient.Redirect.NEVER).build();
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || response.body().length() > 100_000) {
                throw unavailable("AI_MODEL_CALL_FAILED", "模型接口返回失败");
            }
            JsonNode body = json.readTree(response.body());
            String content = body.path("choices").path(0).path("message").path("content").asText("").strip();
            if (content.isBlank() || content.length() > 16000) {
                throw unavailable("AI_MODEL_RESPONSE_INVALID", "模型没有返回可用内容");
            }
            int promptTokens = body.path("usage").path("prompt_tokens").asInt(maskedUser.length() + maskedSystem.length());
            int completionTokens = body.path("usage").path("completion_tokens").asInt(content.length());
            actual = Math.max(1, promptTokens + completionTokens);
            return new Completion(content, promptTokens, completionTokens);
        } catch (DomainException failure) {
            throw failure;
        } catch (Exception failure) {
            throw unavailable("AI_MODEL_CALL_FAILED", "模型接口不可用");
        } finally {
            try {
                redis.execute(RELEASE, keys, Integer.toString(actual), Integer.toString(reserved));
            } catch (RuntimeException failure) {
                // 预留额度在 Redis 中保留到当日结束，故释放失败只会安全地拒绝后续调用。
            }
        }
    }

    private URI endpoint(AiModel model) {
        if (model.getEndpointUrl() == null || model.getEndpointUrl().isBlank()) {
            throw unavailable("AI_MODEL_ENDPOINT_MISSING", "模型接口地址未配置");
        }
        try {
            URI uri = URI.create(model.getEndpointUrl());
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null
                    || !("https".equalsIgnoreCase(uri.getScheme())
                    || "http".equalsIgnoreCase(uri.getScheme()) && isLocal(uri))) {
                throw new IllegalArgumentException();
            }
            return uri;
        } catch (IllegalArgumentException failure) {
            throw unavailable("AI_MODEL_ENDPOINT_INVALID", "模型接口地址无效");
        }
    }

    private boolean isLocal(URI uri) {
        return "localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost())
                || "::1".equals(uri.getHost()) || "host.docker.internal".equalsIgnoreCase(uri.getHost());
    }

    private JsonNode policy(String raw) {
        try {
            return json.readTree(raw);
        } catch (Exception failure) {
            throw unavailable("AI_MODEL_POLICY_INVALID", "模型策略无效");
        }
    }

    /** 执行日志和模型请求使用相同的基础个人信息遮蔽。 */
    String maskSensitive(String value) {
        return value.replaceAll("(?i)[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}", "[邮箱]")
                .replaceAll("(?<![0-9])1[3-9][0-9]{9}(?![0-9])", "[手机号]")
                .replaceAll("(?<![0-9])[0-9]{17}[0-9Xx](?![0-9])", "[证件号]");
    }

    private List<String> keys(long modelId, long grantId) {
        String date = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();
        return List.of("ai:model:" + modelId + ":" + date + ":tokens",
                "ai:model:" + modelId + ":active",
                "ai:grant:" + grantId + ":" + date + ":tokens",
                "ai:grant:" + grantId + ":active");
    }

    private DomainException unavailable(String code, String message) {
        return new DomainException(code, message, HttpStatus.SERVICE_UNAVAILABLE);
    }

    public record Completion(String text, int promptTokens, int completionTokens) { }
}
