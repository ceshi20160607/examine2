package com.unique.unexamine.flow.manage;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 抄送、通知和消息节点共用的站内投递配置。
 * 用户只配置业务文案和当前流程入口，通道、目标地址与幂等键均由运行时生成。
 */
public final class FlowMessageNodePolicy {
    public static final String CC = "CC";
    public static final String NOTIFICATION = "NOTIFICATION";
    public static final String MESSAGE = "MESSAGE";
    public static final String FLOW_INSTANCE = "FLOW_INSTANCE";
    private static final Set<String> NODE_TYPES = Set.of(CC, NOTIFICATION, MESSAGE);
    private static final Set<String> STRUCTURED_KEYS = Set.of(
            "schemaVersion", "messageKind", "subject", "content", "appendInstanceTitle", "destinationType");

    private FlowMessageNodePolicy() { }

    public record Config(
            String nodeType,
            String subject,
            String content,
            boolean appendInstanceTitle,
            String destinationType,
            boolean legacy) { }

    public static boolean supports(String nodeType) {
        return nodeType != null && NODE_TYPES.contains(nodeType.strip().toUpperCase(Locale.ROOT));
    }

    /** 读取并校验结构化配置；旧通知草稿允许进入发布检查，发布快照会升级为版本 1。 */
    public static Config parse(String nodeType, Map<String, Object> config) {
        String normalizedType = nodeType == null ? "" : nodeType.strip().toUpperCase(Locale.ROOT);
        if (!NODE_TYPES.contains(normalizedType)) throw new IllegalArgumentException("当前节点不是站内消息节点");
        Map<String, Object> source = config == null ? Map.of() : config;
        boolean legacy = !source.containsKey("schemaVersion");
        if (!legacy) {
            int schemaVersion = integer(source.get("schemaVersion"));
            if (schemaVersion != 1) throw new IllegalArgumentException("消息节点配置版本无效，请重新配置后发布");
            if (source.keySet().stream().anyMatch(key -> !STRUCTURED_KEYS.contains(key))) {
                throw new IllegalArgumentException("消息节点包含不受支持的内部配置，请在页面重新配置");
            }
        }
        String configuredKind = text(source.get("messageKind"));
        if (!configuredKind.isBlank() && !normalizedType.equals(configuredKind.toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("消息节点类型与冻结配置不一致，请重新发布");
        }
        String subject = text(source.get("subject"));
        String content = text(source.get("content"));
        if (subject.isBlank()) throw new IllegalArgumentException(kindLabel(normalizedType) + "标题不能为空");
        if (subject.length() > 200) throw new IllegalArgumentException(kindLabel(normalizedType) + "标题不能超过 200 个字符");
        if (content.isBlank()) throw new IllegalArgumentException(kindLabel(normalizedType) + "内容不能为空");
        if (content.length() > 2000) throw new IllegalArgumentException(kindLabel(normalizedType) + "内容不能超过 2000 个字符");
        Object appendValue = source.get("appendInstanceTitle");
        if (appendValue != null && !(appendValue instanceof Boolean)) {
            throw new IllegalArgumentException("是否附带流程标题必须使用开关配置");
        }
        String destinationType = text(source.get("destinationType"));
        if (destinationType.isBlank()) destinationType = FLOW_INSTANCE;
        if (!FLOW_INSTANCE.equals(destinationType)) {
            throw new IllegalArgumentException("站内消息只能打开当前流程运行页");
        }
        return new Config(normalizedType, subject, content, Boolean.TRUE.equals(appendValue), destinationType, legacy);
    }

    /** 发布时只冻结用户可理解的业务配置，不接受自定义通道、账号或跳转地址。 */
    public static Map<String, Object> frozen(Config config) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", 1);
        result.put("messageKind", config.nodeType());
        result.put("subject", config.subject());
        result.put("content", config.content());
        result.put("appendInstanceTitle", config.appendInstanceTitle());
        result.put("destinationType", FLOW_INSTANCE);
        return result;
    }

    public static String kindLabel(String nodeType) {
        return switch (nodeType == null ? "" : nodeType.strip().toUpperCase(Locale.ROOT)) {
            case CC -> "抄送";
            case MESSAGE -> "消息";
            default -> "通知";
        };
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).strip();
    }

    private static int integer(Object value) {
        if (value instanceof Number number && number.doubleValue() == Math.rint(number.doubleValue())) {
            return number.intValue();
        }
        try { return Integer.parseInt(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return -1; }
    }
}
