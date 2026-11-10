package com.unique.unexamine.examine.manage.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * API 审批节点的发布期配置校验，保证运行时请求、认证和字段映射具备明确结构。
 * 敏感认证值仍由现有配置入口保存，校验器只检查形状，不记录或输出密钥。
 */
@Component
public class ApiNodeConfigValidator {
    private static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS");
    private static final Set<String> AUTH_TYPES = Set.of("NONE", "TOKEN", "API_KEY", "BASIC");
    private static final Pattern FIELD_KEY = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,127}");

    /** 校验节点配置；错误必须在流程保存时返回，不能延迟到实例运行。 */
    public void validate(JsonNode node, String nodeCode) {
        JsonNode config = node != null && node.path("config").isObject() ? node.path("config") : node;
        if (config == null || !config.isObject()) throw error(nodeCode, "配置必须是 JSON 对象");

        String url = text(config, "url");
        if (url.isBlank()) throw error(nodeCode, "缺少 url");
        URI uri;
        try { uri = URI.create(url); }
        catch (IllegalArgumentException ex) { throw error(nodeCode, "url 无效"); }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!Set.of("http", "https").contains(scheme) || uri.getHost() == null)
            throw error(nodeCode, "url 只允许带主机的 http 或 https 地址");
        if (uri.getUserInfo() != null || uri.getFragment() != null)
            throw error(nodeCode, "url 不能携带用户凭据或片段");

        String method = text(config, "method", "POST").toUpperCase(Locale.ROOT);
        if (!METHODS.contains(method)) throw error(nodeCode, "method 不支持: " + method);
        if (config.has("timeoutMs") && (!config.get("timeoutMs").canConvertToLong()
                || config.get("timeoutMs").asLong() < 100 || config.get("timeoutMs").asLong() > 30000))
            throw error(nodeCode, "timeoutMs 必须在 100 至 30000 之间");

        validateHeaders(config.get("headers"), nodeCode);
        validateAuth(config, nodeCode);
        validateMappings(config.has("fieldMappings") ? config.get("fieldMappings") : config.get("responseMappings"), nodeCode);
    }

    /** 取得 API 返回映射到内部业务字段的 key。 */
    public Set<String> mappedFieldKeys(JsonNode node) {
        JsonNode config = node != null && node.path("config").isObject() ? node.path("config") : node;
        JsonNode mappings = config == null ? null
                : config.has("fieldMappings") ? config.get("fieldMappings") : config.get("responseMappings");
        Set<String> keys = new HashSet<>();
        if (mappings == null) return keys;
        if (mappings.isObject()) mappings.fieldNames().forEachRemaining(keys::add);
        if (mappings.isArray()) for (JsonNode item : mappings) {
            String key = text(item, "fieldKey", "field_key");
            if (!key.isBlank()) keys.add(key);
        }
        return keys;
    }

    private void validateHeaders(JsonNode headers, String nodeCode) {
        if (headers == null || headers.isMissingNode() || headers.isNull()) return;
        if (!headers.isObject()) throw error(nodeCode, "headers 必须是对象");
        headers.fieldNames().forEachRemaining(name -> {
            if (name.isBlank() || name.indexOf('\r') >= 0 || name.indexOf('\n') >= 0)
                throw error(nodeCode, "headers 包含无效名称");
            JsonNode value = headers.get(name);
            if (value == null || !value.isValueNode() || value.isContainerNode())
                throw error(nodeCode, "headers 的值必须是文本或数字");
            String text = value.asText();
            if (text.indexOf('\r') >= 0 || text.indexOf('\n') >= 0)
                throw error(nodeCode, "headers 的值不能包含换行");
        });
    }

    private void validateAuth(JsonNode config, String nodeCode) {
        JsonNode auth = config.path("auth");
        String type = text(auth, "type", text(config, "authType", "TOKEN")).toUpperCase(Locale.ROOT);
        if (!AUTH_TYPES.contains(type)) throw error(nodeCode, "认证类型不支持: " + type);
        if (!"NONE".equals(type)) {
            String header = text(auth, "header", text(config, "authHeader", "Authorization"));
            if (header.indexOf('\r') >= 0 || header.indexOf('\n') >= 0 || header.isBlank())
                throw error(nodeCode, "认证 header 无效");
        }
        if ("BASIC".equals(type) && text(auth, "username").isBlank())
            throw error(nodeCode, "Basic 认证缺少 username");
        // TOKEN/API_KEY 没有凭据时只能发出未认证请求，保存阶段直接拒绝，避免实例运行到一半才失败。
        if (Set.of("TOKEN", "API_KEY").contains(type)
                && text(auth, "token", text(config, "token", "")).isBlank())
            throw error(nodeCode, "认证类型 " + type + " 缺少 token");
    }

    private void validateMappings(JsonNode mappings, String nodeCode) {
        if (mappings == null || mappings.isMissingNode() || mappings.isNull()) return;
        Set<String> keys = new HashSet<>();
        if (mappings.isObject()) {
            mappings.fields().forEachRemaining(item -> {
                if (!FIELD_KEY.matcher(item.getKey()).matches() || !item.getValue().isTextual()
                        || item.getValue().asText().isBlank()) throw error(nodeCode, "API 返回映射无效");
                if (!keys.add(item.getKey())) throw error(nodeCode, "API 返回映射字段重复: " + item.getKey());
            });
            return;
        }
        if (!mappings.isArray()) throw error(nodeCode, "fieldMappings 必须是对象或数组");
        for (JsonNode item : mappings) {
            String fieldKey = text(item, "fieldKey", "field_key");
            String path = text(item, "responsePath", "response_path");
            if (!FIELD_KEY.matcher(fieldKey).matches() || path.isBlank() || !keys.add(fieldKey))
                throw error(nodeCode, "API 返回映射无效或字段重复: " + fieldKey);
        }
    }

    private String text(JsonNode node, String key) { return text(node, key, ""); }

    private String text(JsonNode node, String key, String fallback) {
        if (node == null || !node.isObject() || !node.has(key) || node.get(key).isNull()) return fallback;
        return node.get(key).asText(fallback).trim();
    }

    private IllegalArgumentException error(String nodeCode, String message) {
        return new IllegalArgumentException("API 节点 " + nodeCode + " " + message);
    }
}
