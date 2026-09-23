package com.unique.unexamine.flow.manage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 节点字段策略只限制已有权限：未配置查看为全部，显式空查看为全部隐藏，未配置编辑为不可编辑。 */
public final class FlowNodeFieldPolicy {
    public static final List<String> KEYS = List.of("visible", "editable", "required", "hidden");

    private FlowNodeFieldPolicy() {}

    /** 发布拒绝错误集合和字段引用；保存草稿保留原配置供用户明确修正。 */
    public static List<String> inspect(Map<String, Object> policy, Set<String> fields, Set<String> editableFields) {
        List<String> issues = new ArrayList<>();
        for (String key : KEYS) {
            if (!policy.containsKey(key)) continue;
            Object value = policy.get(key);
            if (!(value instanceof List<?> values) || values.stream().anyMatch(v -> !(v instanceof String code) || code.isBlank())) {
                issues.add("节点字段策略必须是有效的字段列表：" + key);
                continue;
            }
            if (values.stream().distinct().count() != values.size()) issues.add("节点字段策略存在重复字段：" + key);
            if (values.stream().anyMatch(v -> !fields.contains(v))) issues.add("节点字段策略包含已失效或无权读取的字段：" + key);
        }
        if (!wellFormed(policy)) return issues;
        for (Object value : list(policy, "editable")) {
            String code = (String) value;
            if (!allowsRead(policy, code)) issues.add("可编辑字段必须可见且不能隐藏：" + code);
            if (!editableFields.contains(code)) issues.add("系统或只读字段不能配置编辑：" + code);
        }
        for (Object value : list(policy, "required")) {
            if (!list(policy, "editable").contains(value)) issues.add("必填字段必须同时配置可编辑：" + value);
        }
        return issues;
    }

    /** 旧配置兼容缺省；任何已知集合结构错误都拒绝读取，不能被错误类型扩大可见范围。 */
    public static boolean allowsRead(Map<String, Object> policy, String code) {
        return wellFormed(policy) && (!policy.containsKey("visible") || list(policy, "visible").contains(code))
                && !list(policy, "hidden").contains(code);
    }

    /** 配置允许编辑也不是权限授予，实际提交仍须重检当前处理人的业务和渠道权限。 */
    public static boolean allowsEdit(Map<String, Object> policy, String code) {
        return allowsRead(policy, code) && list(policy, "editable").contains(code);
    }

    /** 新发布的节点字段绑定防止同编码重建或类型/字典/关联来源变化；旧未绑定实例沿用原兼容策略。 */
    public static boolean matchesBinding(Map<String, Object> policy, Long id, String code, String type,
                                         Long dictionaryId, Long referenceModuleId) {
        if (!policy.containsKey("fieldBindings")) return true;
        if (!(policy.get("fieldBindings") instanceof List<?> bindings)) return false;
        return bindings.stream().filter(v -> v instanceof Map<?, ?>).map(v -> (Map<?, ?>) v)
                .anyMatch(b -> Objects.equals(code, b.get("code")) && Objects.equals(id, number(b.get("id")))
                        && Objects.equals(type, b.get("fieldType"))
                        && validReference(b.get("dictionaryId")) && validReference(b.get("referenceModuleId"))
                        && Objects.equals(dictionaryId, number(b.get("dictionaryId")))
                        && Objects.equals(referenceModuleId, number(b.get("referenceModuleId"))));
    }

    private static boolean wellFormed(Map<String, Object> policy) {
        return KEYS.stream().allMatch(key -> !policy.containsKey(key) || policy.get(key) instanceof List<?> values
                && values.stream().allMatch(v -> v instanceof String code && !code.isBlank()));
    }

    private static List<?> list(Map<String, Object> policy, String key) {
        return policy.get(key) instanceof List<?> values ? values : List.of();
    }

    private static Long number(Object value) {
        if (!(value instanceof Number)) return null;
        try { return new java.math.BigDecimal(value.toString()).longValueExact(); }
        catch (ArithmeticException | NumberFormatException malformed) { return null; }
    }

    private static boolean validReference(Object value) {
        return value == null || number(value) != null;
    }
}
