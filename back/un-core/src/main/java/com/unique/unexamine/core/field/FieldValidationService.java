package com.unique.unexamine.core.field;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/** 固定字段类型的共用配置与值校验；保存、导入及转化预校验调用同一入口。 */
@Service
public class FieldValidationService {
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    /** 只接受实际执行的规则，避免未支持的配置被原样保存却悄悄失效。 */
    private static final Set<String> RULES = Set.of("min", "max", "scale", "precision", "minLength", "maxLength",
            "pattern", "minItems", "maxItems", "unique", "multiple", "options", "defaultValue");

    /** 配置保存和发布前都核对规则自身，避免不可能满足的表单进入运行态。 */
    public void validateDefinition(FieldDefinition field) {
        Map<String, Object> rules = field.rules();
        if (!RULES.containsAll(rules.keySet())) fail(field, "包含未支持的字段规则");
        for (String key : List.of("min", "max", "scale", "precision")) {
            if (rules.containsKey(key) && field.type().kind() != FieldType.Kind.NUMBER) fail(field, key + "仅适用于数值类型");
        }
        for (String key : List.of("minLength", "maxLength", "pattern")) {
            if (rules.containsKey(key) && field.type().kind() != FieldType.Kind.TEXT) fail(field, key + "仅适用于文本类型");
        }
        for (String key : List.of("minItems", "maxItems")) {
            if (rules.containsKey(key) && !Set.of(FieldType.Kind.OPTIONS, FieldType.Kind.REFERENCES,
                    FieldType.Kind.ROWS, FieldType.Kind.REFERENCE).contains(field.type().kind())) fail(field, key + "仅适用于集合类型");
        }
        for (String key : List.of("minLength", "maxLength", "scale", "precision", "minItems", "maxItems")) {
            if (rules.containsKey(key) && integer(rules.get(key), key) < 0) fail(field, key + "不能小于零");
        }
        ordered(field, rules, "min", "max");
        ordered(field, rules, "minLength", "maxLength");
        ordered(field, rules, "minItems", "maxItems");
        for (String key : List.of("min", "max")) if (rules.containsKey(key)) decimal(rules.get(key));
        for (String key : List.of("unique", "multiple")) {
            if (rules.containsKey(key) && !(rules.get(key) instanceof Boolean)) fail(field, key + "必须是开关值");
        }
        if (rules.containsKey("scale") && rules.containsKey("precision")
                && integer(rules.get("scale"), "scale") != integer(rules.get("precision"), "precision")) {
            fail(field, "小数位配置不能冲突");
        }
        if (rules.containsKey("pattern")) {
            if (!(rules.get("pattern") instanceof String expression) || expression.length() > 500) {
                fail(field, "正则规则无效");
            }
            try { Pattern.compile((String) rules.get("pattern")); }
            catch (RuntimeException ex) { fail(field, "正则规则无效"); }
        }
        if (rules.containsKey("options")) {
            if (!field.type().options() || !(rules.get("options") instanceof List<?>)) fail(field, "选项规则无效");
            Set<String> ids = new HashSet<>();
            for (Object raw : (List<?>) rules.get("options")) {
                if (!(raw instanceof Map<?, ?> option) || !validOptionId(option.get("id"))
                        || !(option.get("label") instanceof String label) || label.isBlank()
                        || !ids.add(String.valueOf(option.get("id")))) fail(field, "选项必须有唯一标识和名称");
            }
        }
        if (rules.containsKey("defaultValue")) validateValue(field, rules.get("defaultValue"));
    }

    /** 校验最终业务值；不会把文本数字或错误布尔值偷偷转换后保存。 */
    public void validateValue(FieldDefinition field, Object value) {
        FieldType type = field.type();
        if (empty(value)) {
            if (field.required()) fail(field, "不能为空");
            if (value == null || value instanceof String && type.kind() == FieldType.Kind.TEXT
                    || value instanceof Collection<?> && Set.of(FieldType.Kind.OPTIONS, FieldType.Kind.REFERENCES, FieldType.Kind.ROWS).contains(type.kind())
                    || type.kind() == FieldType.Kind.OBJECT && type != FieldType.LOCATION
                        && (value instanceof Map<?, ?> || type == FieldType.JSON && value instanceof List<?>)) return;
        }
        if (type.computed()) fail(field, "由系统计算，不能直接提交");
        // 附件字段存储同批文件共享的 UUID，而不是文件主键或文件 ID 数组。
        if (Set.of(FieldType.ATTACHMENT, FieldType.IMAGE, FieldType.FILE_GROUP).contains(type)) {
            if (!FileBatchId.valid(value)) fail(field, "必须是32位UUID文件批次 ID");
            return;
        }
        try {
            switch (type.kind()) {
                case TEXT -> text(field, value);
                case NUMBER -> number(field, value);
                case BOOLEAN -> { if (!(value instanceof Boolean)) fail(field, "必须是开关值"); }
                case DATE -> LocalDate.parse(string(value));
                case DATETIME -> LocalDateTime.parse(string(value));
                case TIME -> LocalTime.parse(string(value));
                case DATE_RANGE, TIME_RANGE -> range(field, value);
                case OPTION -> option(field, value);
                case OPTIONS -> {
                    if (type == FieldType.CASCADE) cascade(field, value);
                    else options(field, value);
                }
                case REFERENCE -> {
                    if (Boolean.TRUE.equals(field.rules().get("multiple"))) references(field, value);
                    else if (!positiveId(value)) fail(field, "必须是有效关联标识");
                }
                case REFERENCES -> references(field, value);
                case ROWS -> rows(field, value);
                case OBJECT -> object(field, value);
                default -> fail(field, "字段值形态无效");
            }
        } catch (IllegalArgumentException ex) {
            if (ex.getMessage() != null && ex.getMessage().startsWith(field.label() + "：")) throw ex;
            fail(field, "值不符合" + type.label() + "类型");
        } catch (RuntimeException ex) { fail(field, "值不符合" + type.label() + "类型"); }
    }

    /** 必填判断适用于文本、集合以及空对象，数字零和 false 不是空值。 */
    public boolean empty(Object value) {
        return value == null || value instanceof String s && s.isBlank()
                || value instanceof Collection<?> c && c.isEmpty() || value instanceof Map<?, ?> m && m.isEmpty();
    }

    private void text(FieldDefinition field, Object value) {
        String text = string(value);
        int length = text.codePointCount(0, text.length());
        if (field.type().valueColumn()==FieldType.ValueColumn.VARCHAR && length>255)
            fail(field,"长度不能超过255字符");
        limit(field, length, "minLength", "maxLength", "文本长度");
        Object pattern = field.rules().get("pattern");
        if (pattern instanceof String expression && !Pattern.matches(expression, text)) fail(field, "不符合格式规则");
        switch (field.type()) {
            case EMAIL -> { if (!EMAIL.matcher(text).matches()) fail(field, "邮箱格式无效"); }
            case PHONE -> { if (!text.matches("[+0-9() -]{6,30}")) fail(field, "电话格式无效"); }
            case URL -> {
                URI uri = URI.create(text);
                if (uri.getHost() == null || !("http".equalsIgnoreCase(uri.getScheme())
                        || "https".equalsIgnoreCase(uri.getScheme()))) fail(field, "网址格式无效");
            }
            case ID_CARD -> { if (!text.matches("[0-9]{17}[0-9Xx]")) fail(field, "身份证号格式无效"); }
            default -> { }
        }
    }

    private void number(FieldDefinition field, Object value) {
        if (!(value instanceof Number)) fail(field, "必须是数值");
        BigDecimal number = decimal(value);
        if (field.rules().containsKey("min") && number.compareTo(decimal(field.rules().get("min"))) < 0) fail(field, "低于最小值");
        if (field.rules().containsKey("max") && number.compareTo(decimal(field.rules().get("max"))) > 0) fail(field, "高于最大值");
        Object scale = field.rules().getOrDefault("scale", field.rules().get("precision"));
        if (scale != null && Math.max(number.stripTrailingZeros().scale(), 0)
                > integer(scale, "scale")) fail(field, "小数位超过配置精度");
        if (field.type() == FieldType.PROGRESS && (number.signum() < 0 || number.compareTo(BigDecimal.valueOf(100)) > 0)) {
            fail(field, "进度必须在零到一百之间");
        }
    }

    private void range(FieldDefinition field, Object value) {
        List<?> values = list(value);
        if (values.size() != 2) fail(field, "范围必须包含开始和结束值");
        boolean reversed = field.type() == FieldType.DATE_RANGE
                ? LocalDate.parse(string(values.get(0))).isAfter(LocalDate.parse(string(values.get(1))))
                : LocalTime.parse(string(values.get(0))).isAfter(LocalTime.parse(string(values.get(1))));
        if (reversed) fail(field, "开始值不能晚于结束值");
    }

    private void option(FieldDefinition field, Object value) {
        if (!positiveId(value)) fail(field, "选项标识必须是字典项 ID");
        if (field.rules().get("options") instanceof List<?> options) {
            boolean found = options.stream().filter(Map.class::isInstance).map(Map.class::cast)
                    .anyMatch(option -> String.valueOf(value).equals(String.valueOf(option.get("id")))
                            && !Boolean.FALSE.equals(option.get("enabled")));
            if (!found) fail(field, "选项不存在或已停用");
        }
    }

    private void options(FieldDefinition field, Object value) {
        List<?> values = list(value);
        items(field, values);
        Set<String> selected = new HashSet<>();
        for (Object item : values) {
            option(field, item);
            if (!selected.add(String.valueOf(item))) fail(field, "不能重复选择同一选项");
        }
    }

    /** 级联使用对象承载有序路径，不能把末级 ID 或普通多选数组混作省市区值。 */
    private void cascade(FieldDefinition field, Object value) {
        if (!(value instanceof Map<?,?>)) fail(field, "必须是包含非空 path 的级联对象");
        Map<?,?> object = (Map<?,?>) value;
        if (object.size() != 1 || !(object.get("path") instanceof List<?>))
            fail(field, "必须是包含非空 path 的级联对象");
        List<?> path = (List<?>) object.get("path");
        if (path.isEmpty()) fail(field, "必须是包含非空 path 的级联对象");
        items(field, path);
        Set<String> seen = new HashSet<>();
        String parent = null;
        for (Object id : path) {
            option(field, id);
            if (!seen.add(String.valueOf(id))) fail(field, "级联路径不能重复");
            if (field.rules().get("options") instanceof List<?> options) {
                Map<?,?> option = options.stream().filter(Map.class::isInstance).map(Map.class::cast)
                        .filter(item -> String.valueOf(id).equals(String.valueOf(item.get("id"))))
                        .findFirst().orElseThrow();
                Object configuredParent = option.get("parentId");
                if (!java.util.Objects.equals(parent, configuredParent == null ? null : String.valueOf(configuredParent)))
                    fail(field, "级联值必须按完整父子路径选择");
            }
            parent = String.valueOf(id);
        }
    }

    private void references(FieldDefinition field, Object value) {
        List<?> values = list(value);
        items(field, values);
        if (values.stream().anyMatch(id -> !positiveId(id)) || values.stream().map(String::valueOf).distinct().count() != values.size()) {
            fail(field, "关联标识无效或重复");
        }
    }

    private void rows(FieldDefinition field, Object value) {
        List<?> values = list(value);
        items(field, values);
        if (values.stream().anyMatch(row -> !(row instanceof Map<?, ?>))) fail(field, "子表行必须是字段对象");
    }

    private void object(FieldDefinition field, Object value) {
        if (field.type() == FieldType.JSON) {
            if (!(value instanceof Map<?, ?>) && !(value instanceof List<?>)) fail(field, "必须是 JSON 对象或数组");
        } else if (!(value instanceof Map<?, ?>)) fail(field, "必须是结构化对象");
        if (field.type() == FieldType.LOCATION && value instanceof Map<?, ?> location) {
            BigDecimal longitude = decimal(location.get("longitude"));
            BigDecimal latitude = decimal(location.get("latitude"));
            if (longitude.abs().compareTo(BigDecimal.valueOf(180)) > 0 || latitude.abs().compareTo(BigDecimal.valueOf(90)) > 0) {
                fail(field, "经纬度范围无效");
            }
        }
    }

    private void items(FieldDefinition field, List<?> values) { limit(field, values.size(), "minItems", "maxItems", "条目数量"); }
    private void limit(FieldDefinition field, int value, String min, String max, String label) {
        if (field.rules().containsKey(min) && value < integer(field.rules().get(min), min)) fail(field, label + "不足");
        if (field.rules().containsKey(max) && value > integer(field.rules().get(max), max)) fail(field, label + "超出限制");
    }
    private void ordered(FieldDefinition field, Map<String, Object> rules, String min, String max) {
        if (rules.containsKey(min) && rules.containsKey(max) && decimal(rules.get(min)).compareTo(decimal(rules.get(max))) > 0) {
            fail(field, min + "不能大于" + max);
        }
    }
    private String string(Object value) { if (value instanceof String text) return text; throw new IllegalArgumentException(); }
    private List<?> list(Object value) { if (value instanceof List<?> values) return values; throw new IllegalArgumentException(); }
    private BigDecimal decimal(Object value) {
        if (!(value instanceof Number)) throw new IllegalArgumentException("规则必须是数值");
        return new BigDecimal(value.toString());
    }
    private int integer(Object value, String name) {
        try { return decimal(value).intValueExact(); }
        catch (RuntimeException ex) { throw new IllegalArgumentException(name + "必须是整数"); }
    }
    private boolean positiveId(Object value) {
        try { return value instanceof Number && new BigDecimal(value.toString()).longValueExact() > 0; }
        catch (RuntimeException ex) { return false; }
    }
    private boolean validOptionId(Object value) {
        return value instanceof String s && !s.isBlank() || positiveId(value);
    }
    private void fail(FieldDefinition field, String message) { throw new IllegalArgumentException(field.label() + "：" + message); }
}
