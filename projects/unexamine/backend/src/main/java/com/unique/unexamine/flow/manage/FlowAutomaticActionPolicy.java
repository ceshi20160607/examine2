package com.unique.unexamine.flow.manage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** 自动业务节点的结构化配置协议；草稿、发布快照和运行时共用同一解释规则。 */
public final class FlowAutomaticActionPolicy {
    public static final int SCHEMA_VERSION = 1;
    public static final String DATA_ACTION = "DATA_ACTION";
    public static final String UPDATE_FIELD = "UPDATE_FIELD";
    private static final Pattern VARIABLE = Pattern.compile("[A-Za-z_][A-Za-z0-9_.-]{0,99}");
    private static final Set<String> OPERATIONS = Set.of("CREATE", "UPDATE");
    private static final Set<String> TARGETS = Set.of("CURRENT_RECORD", "BUSINESS_FIELD", "NODE_RESULT");
    private static final Set<String> SOURCES = Set.of("FIXED", "VARIABLE");

    private FlowAutomaticActionPolicy() {
    }

    /** 固定值或流程变量二选一，避免用模板字符串或 JSON 表达式执行任意逻辑。 */
    public record ValueSource(String source, Object value, String variable) {
        public Object resolve(Map<String, Object> variables) {
            if ("FIXED".equals(source)) return value;
            if (!variables.containsKey(variable)) {
                throw new IllegalArgumentException("流程变量“" + variable + "”不存在");
            }
            return variables.get(variable);
        }
    }

    /** 目标字段绑定包含发布时身份，模块重新发布后不会把旧配置静默指向另一字段。 */
    public record FieldBinding(Long id, String code, String name, String fieldType,
                               Long dictionaryId, Long referenceModuleId) {
    }

    public record Assignment(String fieldCode, ValueSource value) {
    }

    public record Config(
            int schemaVersion,
            String operation,
            Long moduleId,
            String moduleCode,
            String moduleName,
            Long moduleVersionId,
            String targetRecordSource,
            String recordIdField,
            String recordNodeKey,
            ValueSource title,
            List<Assignment> assignments,
            List<FieldBinding> fieldBindings,
            boolean legacy) {

        public Map<String, Object> assignmentValues(Map<String, Object> variables) {
            Map<String, Object> values = new LinkedHashMap<>();
            for (Assignment assignment : assignments) {
                values.put(assignment.fieldCode(), assignment.value().resolve(variables));
            }
            return values;
        }

        public String resolvedTitle(Map<String, Object> variables) {
            if (title == null) return null;
            Object resolved = title.resolve(variables);
            return resolved == null ? null : String.valueOf(resolved).strip();
        }
    }

    /** 读取新结构，同时只为既有 UPDATE_FIELD 草稿保留可审计的兼容转换。 */
    public static Config parse(String nodeType, Map<String, Object> config) {
        if (UPDATE_FIELD.equals(nodeType) && !(config.get("assignments") instanceof List<?>)) {
            return legacyUpdate(config);
        }
        String operation = upper(config.get("operation"));
        if (UPDATE_FIELD.equals(nodeType)) operation = "UPDATE";
        if (!OPERATIONS.contains(operation)) throw new IllegalArgumentException("请选择新建或更新数据");
        Long moduleId = positiveLong(config.get("moduleId"));
        String target = operation.equals("CREATE") ? null
                : upper(config.getOrDefault("targetRecordSource", "CURRENT_RECORD"));
        if (target != null && !TARGETS.contains(target)) throw new IllegalArgumentException("请选择有效的目标记录来源");
        String recordIdField = text(config.get("recordIdField"));
        String recordNodeKey = text(config.get("recordNodeKey"));
        if ("BUSINESS_FIELD".equals(target) && !VARIABLE.matcher(recordIdField).matches()) {
            throw new IllegalArgumentException("请选择引用目标记录的业务字段");
        }
        if ("NODE_RESULT".equals(target) && !VARIABLE.matcher(recordNodeKey).matches()) {
            throw new IllegalArgumentException("请选择前序数据节点的结果");
        }
        ValueSource title = operation.equals("CREATE") ? valueSource(config.get("title"), "新建数据标题") : null;
        List<Assignment> assignments = assignments(config.get("assignments"));
        if (assignments.isEmpty()) throw new IllegalArgumentException("请至少配置一个目标字段");
        List<FieldBinding> bindings = fieldBindings(config.get("fieldBindings"));
        return new Config(SCHEMA_VERSION, operation, moduleId, text(config.get("moduleCode")),
                text(config.get("moduleName")), positiveLong(config.get("moduleVersionId")), target,
                recordIdField, recordNodeKey, title, assignments, bindings, false);
    }

    public static Config withModule(Config config, Long moduleId) {
        return new Config(config.schemaVersion(), config.operation(), moduleId, config.moduleCode(),
                config.moduleName(), config.moduleVersionId(), config.targetRecordSource(), config.recordIdField(),
                config.recordNodeKey(), config.title(), config.assignments(), config.fieldBindings(), config.legacy());
    }

    public static Map<String, Object> frozen(Config config, Long moduleId, String moduleCode, String moduleName,
                                              Long versionId, List<FieldBinding> bindings) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", SCHEMA_VERSION);
        result.put("operation", config.operation());
        result.put("moduleId", moduleId);
        result.put("moduleCode", moduleCode);
        result.put("moduleName", moduleName);
        result.put("moduleVersionId", versionId);
        if (config.targetRecordSource() != null) result.put("targetRecordSource", config.targetRecordSource());
        if (config.recordIdField() != null && !config.recordIdField().isBlank()) result.put("recordIdField", config.recordIdField());
        if (config.recordNodeKey() != null && !config.recordNodeKey().isBlank()) result.put("recordNodeKey", config.recordNodeKey());
        if (config.title() != null) result.put("title", valueMap(config.title()));
        result.put("assignments", config.assignments().stream().map(assignment -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("fieldCode", assignment.fieldCode());
            item.put("value", valueMap(assignment.value()));
            return item;
        }).toList());
        result.put("fieldBindings", bindings);
        return result;
    }

    private static Config legacyUpdate(Map<String, Object> config) {
        String action = upper(config.getOrDefault("businessAction", "UPDATE"));
        if (!"UPDATE".equals(action)) {
            throw new IllegalArgumentException("字段更新节点只执行标准更新动作，请重新选择字段后发布");
        }
        if (!(config.get("fieldUpdates") instanceof Map<?, ?> fields) || fields.isEmpty()) {
            throw new IllegalArgumentException("请至少配置一个目标字段");
        }
        List<Assignment> assignments = new ArrayList<>();
        for (Map.Entry<?, ?> entry : fields.entrySet()) {
            String code = text(entry.getKey());
            if (!VARIABLE.matcher(code).matches()) throw new IllegalArgumentException("字段编码无效");
            Object raw = entry.getValue();
            ValueSource source = raw instanceof String value && value.matches("^\\$\\{[A-Za-z_][A-Za-z0-9_.-]{0,99}}$")
                    ? new ValueSource("VARIABLE", null, value.substring(2, value.length() - 1))
                    : new ValueSource("FIXED", raw, null);
            assignments.add(new Assignment(code, source));
        }
        return new Config(SCHEMA_VERSION, "UPDATE", positiveLong(config.get("moduleId")), null, null, null,
                "CURRENT_RECORD", null, null, null, List.copyOf(assignments), List.of(), true);
    }

    private static List<Assignment> assignments(Object raw) {
        if (!(raw instanceof List<?> list) || list.size() > 100) {
            throw new IllegalArgumentException("字段配置数量必须在 1 到 100 之间");
        }
        List<Assignment> result = new ArrayList<>();
        Set<String> used = new LinkedHashSet<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) throw new IllegalArgumentException("字段配置格式无效");
            String code = text(map.get("fieldCode"));
            if (!VARIABLE.matcher(code).matches()) throw new IllegalArgumentException("请选择有效的目标字段");
            if (!used.add(code)) throw new IllegalArgumentException("同一目标字段只能配置一次");
            result.add(new Assignment(code, valueSource(map.get("value"), "字段“" + code + "”")));
        }
        return List.copyOf(result);
    }

    private static ValueSource valueSource(Object raw, String label) {
        if (!(raw instanceof Map<?, ?> map)) throw new IllegalArgumentException(label + "缺少取值方式");
        String source = upper(map.get("source"));
        if (!SOURCES.contains(source)) throw new IllegalArgumentException(label + "取值方式无效");
        if ("VARIABLE".equals(source)) {
            String variable = text(map.get("variable"));
            if (!VARIABLE.matcher(variable).matches()) throw new IllegalArgumentException(label + "请选择流程变量");
            return new ValueSource(source, null, variable);
        }
        if (!map.containsKey("value")) throw new IllegalArgumentException(label + "缺少固定值");
        return new ValueSource(source, map.get("value"), null);
    }

    private static List<FieldBinding> fieldBindings(Object raw) {
        if (!(raw instanceof List<?> list)) return List.of();
        List<FieldBinding> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) continue;
            result.add(new FieldBinding(positiveLong(map.get("id")), text(map.get("code")), text(map.get("name")),
                    text(map.get("fieldType")), positiveLong(map.get("dictionaryId")),
                    positiveLong(map.get("referenceModuleId"))));
        }
        return List.copyOf(result);
    }

    private static Map<String, Object> valueMap(ValueSource source) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("source", source.source());
        if ("VARIABLE".equals(source.source())) value.put("variable", source.variable());
        else value.put("value", source.value());
        return value;
    }

    private static Long positiveLong(Object value) {
        if (value instanceof Number number && number.longValue() > 0) return number.longValue();
        if (value instanceof String text) {
            try {
                long parsed = Long.parseLong(text.strip());
                return parsed > 0 ? parsed : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String upper(Object value) {
        return text(value).toUpperCase(Locale.ROOT);
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).strip();
    }
}
