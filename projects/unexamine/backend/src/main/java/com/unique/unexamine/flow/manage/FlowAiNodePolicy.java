package com.unique.unexamine.flow.manage;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Flow AI 节点只允许授权模型与有限元数据模板，禁止展开任意业务字段。 */
final class FlowAiNodePolicy {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([^{}]+)\\}\\}");
    private static final Pattern VARIABLE = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,63}");

    private FlowAiNodePolicy() { }

    static Config parse(Map<String, Object> source) {
        Object grantValue = source.get("modelGrantId");
        if (!(grantValue instanceof Number number) || number.longValue() <= 0
                || number.doubleValue() != (double) number.longValue()) {
            throw new IllegalArgumentException("AI 节点必须选择当前系统授权模型");
        }
        String prompt = source.get("prompt") instanceof String text ? text.strip() : "";
        if (prompt.isBlank() || prompt.length() > 4000) {
            throw new IllegalArgumentException("AI 节点提示模板长度须为 1 至 4000 字符");
        }
        Matcher matcher = PLACEHOLDER.matcher(prompt);
        while (matcher.find()) {
            if (!"instanceTitle".equals(matcher.group(1)) && !"flowName".equals(matcher.group(1))) {
                throw new IllegalArgumentException("AI 节点只允许实例标题和流程名称占位符");
            }
        }
        String remainder = PLACEHOLDER.matcher(prompt).replaceAll("");
        if (remainder.contains("{{") || remainder.contains("}}")) {
            throw new IllegalArgumentException("AI 节点提示模板占位符格式无效");
        }
        String outputVariable = source.get("outputVariable") instanceof String text ? text.strip() : "";
        if (!outputVariable.isEmpty() && !VARIABLE.matcher(outputVariable).matches()) {
            throw new IllegalArgumentException("AI 输出变量须以英文字母开头，且只含字母、数字、下划线");
        }
        return new Config(number.longValue(), prompt, outputVariable);
    }

    /** 发布快照保留模型和授权版本，运行时仍检查当前授权是否有效。 */
    static Map<String, Object> frozen(Config config, long modelId, int modelConfigVersion,
            long grantVersionId) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("modelGrantId", config.modelGrantId());
        result.put("modelId", modelId);
        result.put("modelConfigVersion", modelConfigVersion);
        result.put("modelGrantVersionId", grantVersionId);
        result.put("prompt", config.prompt());
        if (!config.outputVariable().isEmpty()) result.put("outputVariable", config.outputVariable());
        return result;
    }

    static String render(Config config, String instanceTitle, String flowName) {
        return config.prompt().replace("{{instanceTitle}}", instanceTitle)
                .replace("{{flowName}}", flowName);
    }

    record Config(long modelGrantId, String prompt, String outputVariable) { }
}
