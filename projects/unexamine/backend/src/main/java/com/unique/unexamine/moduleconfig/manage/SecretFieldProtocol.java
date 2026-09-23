package com.unique.unexamine.moduleconfig.manage;

import com.fasterxml.jackson.databind.JsonNode;

/** 密码/密钥字段约束；值不裁剪空格，配置、默认值和索引不能旁路敏感存储。 */
public final class SecretFieldProtocol {
    private SecretFieldProtocol() { }

    /** 默认值引用尚未贯通前禁止把密码写入公开发布配置；长度使用公共文字配置。 */
    public static void validateConfiguration(String type, JsonNode config) {
        if (!"SECRET".equals(type)) return;
        TextFieldProtocol.validateConfiguration("TEXT", config);
        if (config.hasNonNull("defaultValue")) throw new IllegalArgumentException("密码/密钥不能作为明文默认配置保存，请在业务表单中填写");
        if (config.has("sensitive") && !config.path("sensitive").asBoolean()) {
            throw new IllegalArgumentException("密码/密钥必须启用敏感保护");
        }
        if (config.path("maxLength").asInt(4096) > 4096 || config.path("minLength").asInt(0) > 4096) {
            throw new IllegalArgumentException("密码/密钥长度最多4096个字符");
        }
    }

    /** 不回显错误输入，也不把有意义的首尾空格规范化掉。 */
    public static JsonNode normalize(JsonNode config, JsonNode value) {
        validateConfiguration("SECRET", config);
        if (!value.isTextual()) throw new IllegalArgumentException("密码/密钥必须是文本");
        JsonNode normalized = TextFieldProtocol.normalize("TEXT", config, value);
        int length = value.textValue().codePointCount(0, value.textValue().length());
        if (length < config.path("minLength").asInt(0) || length > config.path("maxLength").asInt(4096)) {
            throw new IllegalArgumentException("密码/密钥长度不符合字段要求");
        }
        return normalized;
    }
}
