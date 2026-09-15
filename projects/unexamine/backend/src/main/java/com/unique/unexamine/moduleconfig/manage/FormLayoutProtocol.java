package com.unique.unexamine.moduleconfig.manage;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Shared validation for form drafts and publication; layouts without sections remain compatible. */
final class FormLayoutProtocol {
    record Problem(String path, String message) {}

    static List<Problem> validate(JsonNode layout, Set<String> activeCodes) {
        List<Problem> problems = new ArrayList<>();
        if (!layout.isObject()) return List.of(new Problem("", "表单配置必须是对象"));
        JsonNode codes = layout.path("fieldCodes");
        List<String> visible = new ArrayList<>();
        if (codes.isMissingNode()) visible.addAll(activeCodes);
        else if (!codes.isArray()) problems.add(new Problem("fieldCodes", "表单字段必须是数组"));
        else {
            for (JsonNode code : codes) {
                if (!code.isTextual() || !activeCodes.contains(code.asText()) || visible.contains(code.asText()))
                    problems.add(new Problem("fieldCodes", "字段不存在、已停用或重复：" + code.asText()));
                visible.add(code.asText());
            }
        }
        JsonNode components = layout.path("components");
        List<String> placed = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        boolean explicitSections = false;
        if (!components.isMissingNode() && !components.isArray()) problems.add(new Problem("components", "表单分组必须是数组"));
        if (components.isArray()) {
            for (JsonNode section : components) {
                if (!"SECTION".equals(section.path("type").asText())) continue;
                String id = section.path("id").asText();
                String path = "components." + id;
                if (id.isBlank() || !ids.add(id)) problems.add(new Problem(path, "分组编码不能为空或重复"));
                JsonNode columns = section.path("columns");
                if (!columns.isMissingNode() && !inRange(columns)) problems.add(new Problem(path + ".columns", "每行列数必须为 1 到 3 的整数"));
                JsonNode fields = section.path("fieldCodes");
                if (fields.isMissingNode()) continue;
                explicitSections = true;
                if (!fields.isArray()) problems.add(new Problem(path + ".fieldCodes", "分组字段必须是数组"));
                else for (JsonNode code : fields) {
                    if (!code.isTextual() || !visible.contains(code.asText()) || placed.contains(code.asText()))
                        problems.add(new Problem(path + ".fieldCodes", "分组包含未选择或重复的字段：" + code.asText()));
                    placed.add(code.asText());
                }
            }
        }
        if (explicitSections && !codes.isMissingNode() && !placed.equals(visible))
            problems.add(new Problem("components", "分组字段与表单字段的顺序和范围不一致"));
        JsonNode spans = layout.path("fieldSpans");
        if (!spans.isMissingNode() && !spans.isObject()) problems.add(new Problem("fieldSpans", "字段宽度必须按字段配置"));
        else if (spans.isObject()) spans.fields().forEachRemaining(entry -> {
            if (!visible.contains(entry.getKey()) || !inRange(entry.getValue()))
                problems.add(new Problem("fieldSpans." + entry.getKey(), "字段宽度必须为 1 到 3 的整数，且字段在表单中"));
        });
        return problems;
    }

    private static boolean inRange(JsonNode value) { return value.isIntegralNumber() && value.canConvertToInt() && value.asInt() >= 1 && value.asInt() <= 3; }
}
