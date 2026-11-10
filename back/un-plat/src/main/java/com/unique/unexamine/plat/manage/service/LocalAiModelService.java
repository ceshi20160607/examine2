package com.unique.unexamine.plat.manage.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.unique.unexamine.core.common.base.entity.UnCommonAi;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/** 平台与系统 Agent 共用的本地模型调用；统一执行月额度、并发和本机地址限制。 */
@Service
public class LocalAiModelService {
    private final StringRedisTemplate redis;
    private final ObjectMapper json;

    public LocalAiModelService(StringRedisTemplate redis, ObjectMapper json) {
        this.redis = redis; this.json = json;
    }

    /** 计数在调用前原子占用，失败调用也计入模型额度；并发位在结束后释放。 */
    public JsonNode complete(UnCommonAi model, String prompt, String message) {
        if (model == null || !"LOCAL".equalsIgnoreCase(model.getProviderCode()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH, "业务内容只能交给本地模型");
        JsonNode config;
        try { config = json.readTree(model.getPublishedConfigJson()); }
        catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "平台模型发布快照无效", ex); }
        if (config == null || !config.isObject())
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "平台模型发布快照无效");
        String activeKey = claim(model, config);
        try { return request(config, prompt, message); }
        finally { redis.opsForValue().decrement(activeKey); }
    }

    /** 运行额度直接来自与调用脚本相同的 Redis 计数。 */
    public Usage usage(UnCommonAi model) {
        JsonNode config;
        try { config = json.readTree(model.getPublishedConfigJson()); }
        catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "平台模型发布快照无效", ex); }
        if (config == null || !config.isObject())
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "平台模型发布快照无效");
        String month = YearMonth.now(ZoneId.of("Asia/Shanghai")).toString();
        String used = redis.opsForValue().get("ai:model:" + model.getId() + ":" + month + ":calls");
        String active = redis.opsForValue().get("ai:model:" + model.getId() + ":active");
        try {
            return new Usage(month, config.path("monthlyQuota").asInt(),
                    config.path("maxConcurrency").asInt(), used == null ? 0 : Long.parseLong(used),
                    active == null ? 0 : Long.parseLong(active));
        } catch (NumberFormatException ex) {
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "平台模型额度计数无效", ex);
        }
    }

    /** 月额度与当前并发不包含业务内容。 */
    public record Usage(String month, int monthlyQuota, int maxConcurrency, long usedCalls, long activeCalls) {}

    /** Redis 脚本同时判断平台月额度与当前并发，避免多个服务实例超量调用。 */
    private String claim(UnCommonAi model, JsonNode config) {
        int quota = config.path("monthlyQuota").asInt();
        int concurrency = config.path("maxConcurrency").asInt();
        if (quota < 1 || concurrency < 1)
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "平台模型额度配置无效");
        String prefix = "ai:model:" + model.getId() + ":" + YearMonth.now(ZoneId.of("Asia/Shanghai"));
        String activeKey = "ai:model:" + model.getId() + ":active";
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(
                "local used=tonumber(redis.call('GET',KEYS[1]) or '0'); "
                + "local active=tonumber(redis.call('GET',KEYS[2]) or '0'); "
                + "if used>=tonumber(ARGV[1]) then return 1 end; "
                + "if active>=tonumber(ARGV[2]) then return 2 end; "
                + "redis.call('INCR',KEYS[1]); redis.call('EXPIRE',KEYS[1],3456000); "
                + "redis.call('INCR',KEYS[2]); redis.call('EXPIRE',KEYS[2],60); return 0",
                Long.class);
        Long result = redis.execute(script, List.of(prefix + ":calls", activeKey),
                String.valueOf(quota), String.valueOf(concurrency));
        if (result == null) throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "平台模型额度暂时不可用");
        if (result == 1) throw new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH, "平台模型本月额度已用完");
        if (result == 2) throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "平台模型当前并发已满");
        if (result != 0) throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "平台模型额度状态无效");
        return activeKey;
    }

    /** 本地 chat completions 请求不走代理或重定向，并限制响应体大小。 */
    private JsonNode request(JsonNode config, String prompt, String message) {
        try {
            URI uri = URI.create(config.path("apiUrl").asText());
            if (uri.getScheme() == null || !Set.of("http", "https").contains(uri.getScheme().toLowerCase(Locale.ROOT))
                    || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null
                    || !Set.of("localhost", "127.0.0.1", "::1", "[::1]")
                            .contains(uri.getHost().toLowerCase(Locale.ROOT)))
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH, "本地模型地址无效");
            ObjectNode body = json.createObjectNode();
            body.put("model", config.path("model").asText()); body.put("temperature", 0);
            body.putObject("response_format").put("type", "json_object");
            var messages = body.putArray("messages");
            messages.addObject().put("role", "system").put("content", prompt);
            messages.addObject().put("role", "user").put("content", message);
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection(Proxy.NO_PROXY);
            String responseBody;
            try {
                connection.setRequestMethod("POST"); connection.setConnectTimeout(5000);
                connection.setReadTimeout(30000); connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(bytes.length);
                try (var output = connection.getOutputStream()) { output.write(bytes); }
                if (connection.getResponseCode() != 200)
                    throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "本地模型调用失败");
                try (var input = connection.getInputStream()) {
                    byte[] response = input.readNBytes(16001);
                    if (response.length > 16000)
                        throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "本地模型响应过长");
                    responseBody = new String(response, StandardCharsets.UTF_8);
                }
            } finally { connection.disconnect(); }
            JsonNode root = json.readTree(responseBody);
            return json.readTree(root.path("choices").path(0).path("message").path("content").asText());
        } catch (BaseException ex) { throw ex; }
        catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "本地模型暂时不可用", ex); }
    }
}
