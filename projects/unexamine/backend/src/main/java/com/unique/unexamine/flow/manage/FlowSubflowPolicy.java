package com.unique.unexamine.flow.manage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 子流程节点的结构化配置协议。草稿只保存目标流程和变量映射，发布时再补入不可变的目标版本信息。
 */
final class FlowSubflowPolicy {
    static final String FLOW_ID = "flowId";
    static final String FLOW_VERSION_ID = "flowVersionId";
    static final String DEFINITION_HASH = "definitionHash";
    static final String FLOW_NAME = "flowName";
    static final String VERSION_NUMBER = "versionNumber";
    static final String INHERIT_ALL_VARIABLES = "inheritAllVariables";
    static final String INPUT_MAPPINGS = "inputMappings";
    static final String OUTPUT_MAPPINGS = "outputMappings";
    private static final int MAX_MAPPINGS = 100;

    private FlowSubflowPolicy() {
    }

    record Mapping(String sourceKey, String targetKey) {
        Map<String, Object> snapshot() {
            return Map.of("sourceKey", sourceKey, "targetKey", targetKey);
        }
    }

    record Config(Long flowId, boolean inheritAllVariables, List<Mapping> inputMappings,
                  List<Mapping> outputMappings) {
        Map<String, Object> draftSnapshot() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put(FLOW_ID, flowId);
            result.put(INHERIT_ALL_VARIABLES, inheritAllVariables);
            result.put(INPUT_MAPPINGS, inputMappings.stream().map(Mapping::snapshot).toList());
            result.put(OUTPUT_MAPPINGS, outputMappings.stream().map(Mapping::snapshot).toList());
            return result;
        }
    }

    static Config parse(Map<String, Object> config) {
        Map<String, Object> source = config == null ? Map.of() : config;
        Long flowId = positiveLong(source.get(FLOW_ID));
        boolean inherit = !Boolean.FALSE.equals(source.get(INHERIT_ALL_VARIABLES));
        List<Mapping> inputs = mappings(source.get(INPUT_MAPPINGS), "传入");
        List<Mapping> outputs = mappings(source.get(OUTPUT_MAPPINGS), "返回");
        return new Config(flowId, inherit, inputs, outputs);
    }

    /** 发布快照内固定目标版本，运行时绝不重新读取目标流程的当前发布指针。 */
    static Map<String, Object> frozen(Config config, Long versionId, String definitionHash,
                                      String flowName, Integer versionNumber) {
        Map<String, Object> result = new LinkedHashMap<>(config.draftSnapshot());
        result.put(FLOW_VERSION_ID, Objects.requireNonNull(versionId));
        result.put(DEFINITION_HASH, Objects.requireNonNull(definitionHash));
        result.put(FLOW_NAME, Objects.requireNonNullElse(flowName, "子流程"));
        result.put(VERSION_NUMBER, Objects.requireNonNullElse(versionNumber, 0));
        return result;
    }

    /** 子流程输入默认继承父流程变量；显式映射可重命名或在关闭继承后形成白名单。 */
    static Map<String, Object> childVariables(Config config, Map<String, Object> parentVariables) {
        Map<String, Object> parent = parentVariables == null ? Map.of() : parentVariables;
        Map<String, Object> child = config.inheritAllVariables()
                ? new LinkedHashMap<>(parent) : new LinkedHashMap<>();
        for (Mapping mapping : config.inputMappings()) {
            if (!parent.containsKey(mapping.sourceKey())) {
                throw new IllegalArgumentException("传入变量不存在：" + mapping.sourceKey());
            }
            child.put(mapping.targetKey(), parent.get(mapping.sourceKey()));
        }
        return child;
    }

    /** 返回变量只按显式映射写回，避免子流程内部变量无意覆盖父流程状态。 */
    static Map<String, Object> parentOutputs(Config config, Map<String, Object> childVariables) {
        Map<String, Object> child = childVariables == null ? Map.of() : childVariables;
        Map<String, Object> outputs = new LinkedHashMap<>();
        for (Mapping mapping : config.outputMappings()) {
            if (!child.containsKey(mapping.sourceKey())) {
                throw new IllegalArgumentException("返回变量不存在：" + mapping.sourceKey());
            }
            outputs.put(mapping.targetKey(), child.get(mapping.sourceKey()));
        }
        return outputs;
    }

    private static List<Mapping> mappings(Object value, String label) {
        if (value == null) return List.of();
        if (!(value instanceof List<?> values) || values.size() > MAX_MAPPINGS) {
            throw new IllegalArgumentException(label + "变量映射必须是至多 " + MAX_MAPPINGS + " 项的列表");
        }
        List<Mapping> result = new ArrayList<>();
        Set<String> targets = new LinkedHashSet<>();
        for (Object item : values) {
            if (!(item instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException(label + "变量映射格式无效");
            }
            String source = variableKey(map.get("sourceKey"));
            String target = variableKey(map.get("targetKey"));
            if (!targets.add(target)) {
                throw new IllegalArgumentException(label + "变量目标重复：" + target);
            }
            result.add(new Mapping(source, target));
        }
        return List.copyOf(result);
    }

    private static String variableKey(Object value) {
        String key = value == null ? "" : String.valueOf(value).strip();
        if (key.isEmpty() || key.length() > 100 || key.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("变量名不能为空、不能含空格且不能超过 100 个字符");
        }
        return key;
    }

    private static Long positiveLong(Object value) {
        if (value instanceof Number number && number.longValue() > 0) return number.longValue();
        try {
            long parsed = value == null ? 0 : Long.parseLong(String.valueOf(value));
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
