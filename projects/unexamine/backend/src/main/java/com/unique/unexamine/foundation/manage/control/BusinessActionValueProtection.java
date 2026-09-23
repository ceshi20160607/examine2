package com.unique.unexamine.foundation.manage.control;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.unique.unexamine.shared.manage.security.RuntimeSecretCipher;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

/** 原动作字段值的存储保护；身份、记录和流程关联保留可检索，敏感业务值只供锁定的续跑读取。 */
@Component
public class BusinessActionValueProtection {
    private final ObjectMapper json;
    private final RuntimeSecretCipher cipher;

    public BusinessActionValueProtection(ObjectMapper json, RuntimeSecretCipher cipher) {
        this.json = BusinessAction.storageMapper(json);
        this.cipher = cipher;
    }

    /** 来源有效发布快照确定敏感字段，不接受客户端自报字段清单。 */
    public Set<String> secretFields(JsonNode configuration) {
        Set<String> fields = new LinkedHashSet<>();
        configuration.path("fields").forEach(field -> {
            if ("SECRET".equals(field.path("fieldType").asText())) fields.add(field.path("code").asText());
        });
        return fields;
    }

    /** 不修改正在执行的请求对象；仅加密待持久化副本，路径也参与认证。 */
    public ObjectNode protect(ObjectNode event, Set<String> fields) {
        ObjectNode protectedEvent = event.deepCopy();
        if (fields.isEmpty()) return protectedEvent;
        protectedEvent.set("secretFieldCodes", json.valueToTree(fields));
        JsonNode action = protectedEvent.path("action");
        transformValues(action.path("payload"), fields, binding(action), false);
        return protectedEvent;
    }

    /** 只有内部审批执行调用；查看进度、通知和页面不需要解密原提交内容。 */
    public ObjectNode restoreForExecution(ObjectNode event) {
        ObjectNode restored = event.deepCopy();
        Set<String> fields = new LinkedHashSet<>();
        restored.path("secretFieldCodes").forEach(field -> fields.add(field.asText()));
        JsonNode action = restored.path("action");
        transformValues(action.path("payload"), fields, binding(action), true);
        return restored;
    }

    /** 流程上下文只消费保护后的意图，实际写入值仍由原动作意图安全续跑。 */
    public BusinessAction.Input flowInput(ObjectNode event) {
        ObjectNode action = ((ObjectNode) event.path("action")).deepCopy();
        Set<String> fields = new LinkedHashSet<>();
        event.path("secretFieldCodes").forEach(field -> fields.add(field.asText()));
        for (String container : Set.of("fields", "values")) redact(action.path("payload").path(container), fields);
        return json.convertValue(action, BusinessAction.Input.class);
    }

    private void transformValues(JsonNode payload, Set<String> fields, String binding, boolean restore) {
        // 仅处理类型化请求的业务值容器，避免合法字段名与请求结构的字段名发生碰撞。
        for (String container : Set.of("fields", "values")) {
            JsonNode node = payload.path(container);
            if (!node.isObject()) continue;
            for (String code : fields) {
                JsonNode value = node.get(code);
                if (value == null || value.isNull()) continue;
                if (!value.isTextual()) throw new IllegalArgumentException("敏感业务值必须是文本");
                String authenticatedPath = binding + ":payload/" + container + "/" + code;
                ((ObjectNode) node).put(code, restore
                        ? cipher.decrypt(value.textValue(), authenticatedPath)
                        : cipher.encrypt(value.textValue(), authenticatedPath));
            }
        }
    }

    private void redact(JsonNode node, Set<String> fields) {
        if (node.isObject()) fields.forEach(code -> {
            if (node.hasNonNull(code)) ((ObjectNode) node).put(code, "***");
        });
    }

    private String binding(JsonNode action) {
        return "action:" + action.path("actor").path("systemId").asText() + ":"
                + action.path("dataTenantId").asText() + ":" + action.path("moduleId").asText() + ":"
                + action.path("configVersionId").asText() + ":" + action.path("actionCode").asText() + ":"
                + action.path("recordId").asText() + ":" + action.path("actor").path("accountId").asText() + ":"
                + action.path("traceId").asText();
    }
}
