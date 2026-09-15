package com.unique.unexamine.moduleconfig.manage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.TextNode;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;

/** Safe rich markup and recursive, typed JSON values for the shared module runtime. */
public final class StructuredContentFieldProtocol {
    private static final Set<String> JSON_TYPES = Set.of("STRING", "NUMBER", "BOOLEAN", "OBJECT", "ARRAY", "NULL");
    private static final Set<String> TAGS = Set.of("p", "div", "br", "b", "strong", "i", "em", "u", "s",
            "ul", "ol", "li", "blockquote", "h1", "h2", "h3", "h4", "h5", "h6", "a");
    private static final Set<String> BLOCKED = Set.of("script", "style", "iframe", "object", "applet", "svg", "math");
    private static final int MAX_DEPTH = 8;
    private static final int MAX_ENTRIES = 1000;

    private StructuredContentFieldProtocol() { }

    public static JsonNode defaultSchema() {
        return JsonNodeFactory.instance.objectNode().put("type", "OBJECT").put("allowExtraProperties", true)
                .set("properties", JsonNodeFactory.instance.arrayNode());
    }

    public static void validateConfiguration(String type, JsonNode config) {
        if ("RICH_TEXT".equals(type)) {
            TextFieldProtocol.validateConfiguration("TEXT", config);
            if (!Set.of("BASIC", "FULL").contains(config.path("richTextMode").asText("FULL"))) {
                throw new IllegalArgumentException("富文本工具栏仅支持基础或完整模式");
            }
        } else if ("JSON".equals(type)) {
            validateSchema(config.has("jsonSchema") ? config.get("jsonSchema") : defaultSchema(), 0, new int[]{0});
        } else return;
        JsonNode value = config.get("defaultValue");
        if (value != null && !value.isNull()) normalize(type, config, value);
    }

    public static JsonNode normalize(String type, JsonNode config, JsonNode value) {
        if ("RICH_TEXT".equals(type)) {
            if (!value.isTextual()) throw new IllegalArgumentException("富文本必须是文本内容");
            if (value.textValue().length() > 100_000) throw new IllegalArgumentException("富文本内容过长");
            Document safe = cleanMarkup(value.textValue());
            String plain = safe.body().text();
            TextFieldProtocol.normalize("TEXT", config, TextNode.valueOf(plain));
            return TextNode.valueOf(plain.isBlank() ? "" : safe.body().html());
        }
        if (!"JSON".equals(type)) throw new IllegalArgumentException("不是结构化内容字段");
        if (value.toString().length() > 100_000) throw new IllegalArgumentException("结构化内容过长");
        JsonNode schema = config.has("jsonSchema") ? config.get("jsonSchema") : defaultSchema();
        validateSchema(schema, 0, new int[]{0});
        validateValue(schema, value, "内容", 0, new int[]{0});
        return value.deepCopy();
    }

    private static void validateSchema(JsonNode schema, int depth, int[] entries) {
        bound(depth, entries);
        if (schema == null || !schema.isObject() || !JSON_TYPES.contains(schema.path("type").asText())) {
            throw new IllegalArgumentException("结构化内容必须选择有效的数据类型");
        }
        String type = schema.path("type").asText();
        if ("STRING".equals(type)) TextFieldProtocol.validateConfiguration("TEXT", schema);
        if ("NUMBER".equals(type)) {
            for (String key : Set.of("minimum", "maximum")) {
                if (schema.has(key) && !schema.get(key).isNumber()) throw new IllegalArgumentException("数值范围必须是数字");
            }
            if (schema.has("minimum") && schema.has("maximum")
                    && schema.get("minimum").decimalValue().compareTo(schema.get("maximum").decimalValue()) > 0) {
                throw new IllegalArgumentException("最小值不能大于最大值");
            }
        }
        if ("ARRAY".equals(type)) {
            if (schema.has("maxItems") && (!schema.get("maxItems").isIntegralNumber()
                    || schema.get("maxItems").asInt() < 1 || schema.get("maxItems").asInt() > MAX_ENTRIES)) {
                throw new IllegalArgumentException("数组最大行数必须在 1 到 1000 之间");
            }
            validateSchema(schema.get("items"), depth + 1, entries);
        }
        if ("OBJECT".equals(type)) {
            JsonNode properties = schema.path("properties");
            if (!properties.isArray()) throw new IllegalArgumentException("对象属性必须通过属性列表配置");
            Set<String> keys = new HashSet<>();
            for (JsonNode property : properties) {
                String key = property.path("key").asText("").strip();
                if (!property.isObject() || key.isEmpty() || key.length() > 64 || reservedKey(key) || !keys.add(key)) {
                    throw new IllegalArgumentException("属性名称必须填写且不能重复或使用保留名称");
                }
                if (property.has("required") && !property.get("required").isBoolean()) {
                    throw new IllegalArgumentException("属性必填设置必须是开关");
                }
                validateSchema(property.get("schema"), depth + 1, entries);
            }
            if (schema.has("allowExtraProperties") && !schema.get("allowExtraProperties").isBoolean()) {
                throw new IllegalArgumentException("允许额外属性必须是开关");
            }
        }
    }

    private static void validateValue(JsonNode schema, JsonNode value, String path, int depth, int[] entries) {
        bound(depth, entries);
        String type = schema.path("type").asText();
        boolean matches = switch (type) {
            case "STRING" -> value.isTextual();
            case "NUMBER" -> value.isNumber();
            case "BOOLEAN" -> value.isBoolean();
            case "OBJECT" -> value.isObject();
            case "ARRAY" -> value.isArray();
            case "NULL" -> value.isNull();
            default -> false;
        };
        if (!matches) throw new IllegalArgumentException(path + "的数据类型不正确");
        if ("STRING".equals(type)) {
            try { TextFieldProtocol.normalize("TEXT", schema, value); }
            catch (IllegalArgumentException exception) { throw new IllegalArgumentException(path + "：" + exception.getMessage()); }
        } else if ("NUMBER".equals(type)) {
            if (schema.has("minimum") && value.decimalValue().compareTo(schema.get("minimum").decimalValue()) < 0)
                throw new IllegalArgumentException(path + "不能小于 " + schema.get("minimum"));
            if (schema.has("maximum") && value.decimalValue().compareTo(schema.get("maximum").decimalValue()) > 0)
                throw new IllegalArgumentException(path + "不能大于 " + schema.get("maximum"));
        } else if ("ARRAY".equals(type)) {
            if (value.size() > schema.path("maxItems").asInt(MAX_ENTRIES)) throw new IllegalArgumentException(path + "超过最大行数");
            for (int index = 0; index < value.size(); index++) {
                validateValue(schema.get("items"), value.get(index), path + "第 " + (index + 1) + " 项", depth + 1, entries);
            }
        } else if ("OBJECT".equals(type)) {
            Set<String> configured = new HashSet<>();
            for (JsonNode property : schema.path("properties")) {
                String key = property.path("key").asText().strip();
                configured.add(key);
                JsonNode submitted = value.get(key);
                String label = property.path("label").asText(key);
                if (submitted == null || (submitted.isNull() && !"NULL".equals(property.path("schema").path("type").asText()))
                        || (submitted.isTextual() && submitted.textValue().isBlank() && property.path("required").asBoolean(false))) {
                    if (property.path("required").asBoolean(false)) throw new IllegalArgumentException(path + "·" + label + "必须填写");
                } else validateValue(property.get("schema"), submitted, path + "·" + label, depth + 1, entries);
            }
            value.fields().forEachRemaining(item -> {
                if (reservedKey(item.getKey())) throw new IllegalArgumentException(path + "包含保留属性名称");
                if (!configured.contains(item.getKey())) {
                    if (!schema.path("allowExtraProperties").asBoolean(false)) {
                        throw new IllegalArgumentException(path + "包含未配置属性：" + item.getKey());
                    }
                    validateUntyped(item.getValue(), depth + 1, entries);
                }
            });
        }
    }

    private static void validateUntyped(JsonNode value, int depth, int[] entries) {
        bound(depth, entries);
        if (value.isContainerNode()) {
            value.fields().forEachRemaining(item -> {
                if (reservedKey(item.getKey())) throw new IllegalArgumentException("内容包含保留属性名称");
            });
            value.forEach(item -> validateUntyped(item, depth + 1, entries));
        }
    }

    private static boolean reservedKey(String key) { return Set.of("__proto__", "constructor", "prototype").contains(key); }
    public static String plainText(String markup) { return cleanMarkup(markup).body().text(); }
    private static Document cleanMarkup(String markup) {
        Document parsed = Jsoup.parseBodyFragment(markup);
        parsed.select(String.join(",", BLOCKED)).remove();
        Safelist allowed = Safelist.none().addTags(TAGS.toArray(String[]::new))
                .addAttributes("a", "href").addProtocols("a", "href", "http", "https")
                .addEnforcedAttribute("a", "rel", "noopener noreferrer");
        Document cleaned = new Cleaner(allowed).clean(parsed);
        cleaned.select("a[href]").forEach(link -> {
            try { if (new URI(link.attr("href")).getHost() == null) link.removeAttr("href"); }
            catch (Exception ignored) { link.removeAttr("href"); }
        });
        cleaned.outputSettings().prettyPrint(false);
        return cleaned;
    }
    private static void bound(int depth, int[] entries) {
        if (depth > MAX_DEPTH || ++entries[0] > MAX_ENTRIES) throw new IllegalArgumentException("结构化内容最多 8 层、1000 个项目");
    }

}
