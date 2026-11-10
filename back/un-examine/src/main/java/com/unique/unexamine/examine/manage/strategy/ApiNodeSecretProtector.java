package com.unique.unexamine.examine.manage.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.unique.unexamine.core.module.PublishedProcessSnapshot;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 审批 API 节点的敏感配置保护器。
 *
 * <p>审批定义查询可以返回流程配置，但不能把 token、密码和认证请求头回传给前端。
 * 编辑时前端提交脱敏占位值或省略该值，服务端继续沿用数据库中的密钥；只有提交新的非占位值时才替换原密钥。</p>
 */
@Component
public class ApiNodeSecretProtector {
    private static final String MASK = "******";
    private final ObjectMapper json;

    public ApiNodeSecretProtector(ObjectMapper json) {
        this.json = json;
    }

    /** 返回可交给管理接口和审计日志的脱敏快照副本。 */
    public JsonNode mask(JsonNode snapshot) {
        if (snapshot == null) return null;
        JsonNode copy = snapshot.deepCopy();
        try {
            for (JsonNode node : PublishedProcessSnapshot.nodes(json, copy.toString())) {
                maskConfig(config(node));
            }
        } catch (RuntimeException ex) {
            // 历史脏快照也不能因为格式错误而把认证值原样返回，退化为按敏感键递归脱敏。
            maskFallback(copy);
        }
        return copy;
    }

    /** 将编辑请求中的脱敏或缺失密钥恢复为已保存值，避免编辑普通节点时意外清空认证。 */
    public JsonNode restore(JsonNode submitted, JsonNode stored) {
        if (submitted == null || stored == null) return submitted;
        JsonNode copy = submitted.deepCopy();
        Map<String, JsonNode> previous = new HashMap<>();
        try {
            for (JsonNode node : PublishedProcessSnapshot.nodes(json, stored.toString())) {
                previous.put(PublishedProcessSnapshot.code(node), node);
            }
            for (JsonNode node : PublishedProcessSnapshot.nodes(json, copy.toString())) {
                JsonNode old = previous.get(PublishedProcessSnapshot.code(node));
                if (old != null) restoreConfig(config(node), config(old));
            }
        } catch (RuntimeException ex) {
            // 结构校验会在调用方返回明确的配置错误；这里不吞掉或改写未确认的请求内容。
        }
        return copy;
    }

    private void maskFallback(JsonNode node) {
        if (node == null) return;
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            object.fieldNames().forEachRemaining(name -> {
                JsonNode value = object.get(name);
                if (sensitiveKey(name) && value != null && value.isValueNode()
                        && !value.isNull() && !value.asText().isBlank()) object.put(name, MASK);
                else maskFallback(value);
            });
        } else if (node.isArray()) node.forEach(this::maskFallback);
    }

    private ObjectNode config(JsonNode node) {
        if (node != null && node.path("config").isObject()) return (ObjectNode) node.path("config");
        return node instanceof ObjectNode object ? object : json.createObjectNode();
    }

    private void maskConfig(ObjectNode config) {
        if (config == null) return;
        maskValue(config, "token");
        maskValue(config, "password");
        JsonNode auth = config.get("auth");
        if (auth instanceof ObjectNode authObject) {
            maskValue(authObject, "token");
            maskValue(authObject, "password");
        }
        JsonNode headers = config.get("headers");
        if (headers instanceof ObjectNode headerObject) {
            headerObject.fieldNames().forEachRemaining(name -> {
                if (sensitiveHeader(name)) maskValue(headerObject, name);
            });
        }
    }

    private void restoreConfig(ObjectNode current, ObjectNode old) {
        if (current == null || old == null) return;
        restoreValue(current, old, "token");
        restoreValue(current, old, "password");
        JsonNode currentAuth = current.get("auth");
        JsonNode oldAuth = old.get("auth");
        if (currentAuth instanceof ObjectNode currentObject && oldAuth instanceof ObjectNode oldObject) {
            restoreValue(currentObject, oldObject, "token");
            restoreValue(currentObject, oldObject, "password");
        }
        JsonNode currentHeaders = current.get("headers");
        JsonNode oldHeaders = old.get("headers");
        if (currentHeaders instanceof ObjectNode currentObject && oldHeaders instanceof ObjectNode oldObject) {
            oldObject.fieldNames().forEachRemaining(name -> {
                if (sensitiveHeader(name)) restoreValue(currentObject, oldObject, name);
            });
        }
    }

    private void maskValue(ObjectNode object, String key) {
        JsonNode value = object.get(key);
        if (value != null && !value.isNull() && !value.asText().isBlank()) object.put(key, MASK);
    }

    private void restoreValue(ObjectNode current, ObjectNode old, String key) {
        JsonNode value = current.get(key);
        if (value == null || value.isNull() || value.asText().isBlank() || MASK.equals(value.asText())) {
            JsonNode previous = old.get(key);
            if (previous != null && !previous.isNull() && !previous.asText().isBlank())
                current.set(key, previous.deepCopy());
        }
    }

    private boolean sensitiveHeader(String name) {
        String key = name == null ? "" : name.toLowerCase(Locale.ROOT).replace("-", "");
        return key.equals("authorization") || key.equals("apikey") || key.equals("xapikey")
                || key.contains("token") || key.contains("secret") || key.contains("password");
    }

    private boolean sensitiveKey(String name) {
        String key = name == null ? "" : name.toLowerCase(Locale.ROOT).replace("-", "");
        return key.equals("authorization") || key.equals("apikey") || key.equals("xapikey")
                || key.contains("token") || key.contains("secret") || key.contains("password");
    }
}
