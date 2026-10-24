package com.unique.unexamine.core.field;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    /** 只接受实际执行的规则，避免未支持的配置被原样保存却悄悄失效。 */
    private static final Set<String> RULES = Set.of("min", "max", "scale", "precision", "minLength", "maxLength",
            "pattern", "minItems", "maxItems", "unique", "multiple", "options", "defaultValue",
            "displayStyle", "hint", "placeholder", "round", "unitDictCode", "currencyCode", "dictCode",
            "optionDisplayRules",
            "relationModuleId", "relationConfig", "autoNumberConfig", "formulaConfig");

    /** 配置保存和发布前都核对规则自身，避免不可能满足的表单进入运行态。 */
    public void validateDefinition(FieldDefinition field) {
        Map<String, Object> rules = field.rules();
        if (!RULES.containsAll(rules.keySet())) fail(field, "包含未支持的字段规则");
        if (rules.containsKey("displayStyle") && !(rules.get("displayStyle") instanceof String))
            fail(field,"展示样式无效");
        String style=rules.get("displayStyle") instanceof String value?value:field.type().defaultStyle();
        Set<String> styles=switch (field.type()) {
            case TEXT -> Set.of("SINGLE_LINE","MULTI_LINE");
            case RICH_TEXT -> Set.of("MARKDOWN");
            case NUMBER -> Set.of("NUMBER","MONEY","PERCENT","RATING","PROGRESS");
            case DATE_TIME -> Set.of("DATE","DATE_TIME");
            case SELECT -> Set.of("DROPDOWN","RADIO","SWITCH","CHECKBOX","TAGS");
            case CASCADE -> Set.of("CASCADE","REGION");
            case ATTACHMENT -> Set.of("FILE","IMAGE","FILE_GROUP");
            case USER, DEPARTMENT, RELATION -> Set.of("PICKER","LIST");
            default -> Set.of("DEFAULT");
        };
        if (!styles.contains(style)) fail(field,"展示样式与字段类型不匹配");
        for (String key:List.of("hint","placeholder")) if (rules.containsKey(key)
                && (!(rules.get(key) instanceof String value) || value.length()>("hint".equals(key)?500:200)))
            fail(field,key+"内容无效或过长");
        for (String key : List.of("min", "max", "scale", "precision")) {
            if (rules.containsKey(key) && field.type().kind() != FieldType.Kind.NUMBER) fail(field, key + "仅适用于数值类型");
        }
        for (String key : List.of("minLength", "maxLength", "pattern")) {
            if (rules.containsKey(key) && field.type().kind() != FieldType.Kind.TEXT) fail(field, key + "仅适用于文本类型");
        }
        for (String key : List.of("minItems", "maxItems")) {
            if (rules.containsKey(key) && !Set.of(FieldType.Kind.OPTIONS,
                    FieldType.Kind.REFERENCE).contains(field.type().kind())
                    && field.type()!=FieldType.SELECT) fail(field, key + "仅适用于多值类型");
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
        if (rules.containsKey("round") && (field.type()!=FieldType.NUMBER || !(rules.get("round") instanceof Boolean)))
            fail(field,"四舍五入配置只适用于数字");
        if (Boolean.TRUE.equals(rules.get("round")) && !rules.containsKey("scale"))
            fail(field,"启用四舍五入须配置小数位数");
        if (rules.containsKey("unitDictCode") && (field.type()!=FieldType.NUMBER
                || !(rules.get("unitDictCode") instanceof String value) || !value.matches("[A-Za-z0-9_]{1,80}")))
            fail(field,"单位必须引用有效字典编码");
        if (rules.containsKey("currencyCode") && (field.type()!=FieldType.NUMBER || !"MONEY".equals(style)
                || !(rules.get("currencyCode") instanceof String currency) || !currency.matches("[A-Z]{3}")))
            fail(field,"货币代码须为金额样式的三位大写代码");
        if ("MONEY".equals(style) && !rules.containsKey("currencyCode")) fail(field,"金额必须配置货币代码");
        if (rules.containsKey("dictCode") && (!field.type().options()
                || !(rules.get("dictCode") instanceof String value) || !value.matches("[A-Za-z0-9_]{1,80}")))
            fail(field,"选项字段必须使用有效字典编码");
        if (rules.containsKey("relationModuleId") != (field.type()==FieldType.RELATION)
                || rules.containsKey("relationConfig") != (field.type()==FieldType.RELATION))
            fail(field,"关联模块及显示字段须成组配置");
        if (field.type()==FieldType.RELATION) {
            if (!positiveId(rules.get("relationModuleId")) || !(rules.get("relationConfig") instanceof Map<?,?> config)
                    || !(config.get("displayFieldKeys") instanceof List<?> keys) || keys.isEmpty()
                    || keys.stream().anyMatch(key->!(key instanceof String value) || !value.matches("[a-z][a-zA-Z0-9_]{0,79}"))
                    || keys.stream().map(String::valueOf).distinct().count()!=keys.size())
                fail(field,"关联目标与可显示字段无效");
        }
        if (rules.containsKey("multiple") && !Set.of(FieldType.SELECT,FieldType.USER,FieldType.DEPARTMENT,FieldType.RELATION).contains(field.type()))
            fail(field,"多选仅适用于选项、人员、部门和关联模块");
        if (field.type()==FieldType.SELECT && (rules.containsKey("minItems") || rules.containsKey("maxItems"))
                && !Boolean.TRUE.equals(rules.get("multiple")))
            fail(field,"选项数量限制只适用于多选");
        if (rules.containsKey("minItems") || rules.containsKey("maxItems")) {
            if (field.type().kind()==FieldType.Kind.REFERENCE && !Boolean.TRUE.equals(rules.get("multiple")))
                fail(field,"选取数量限制只适用于多选引用");
        }
        if (rules.containsKey("precision") && (integer(rules.get("precision"),"precision")<1
                || integer(rules.get("precision"),"precision")>65)) fail(field,"总位数须在1到65之间");
        if (rules.containsKey("scale") && integer(rules.get("scale"),"scale")>30)
            fail(field,"小数位不能超过30位");
        if (rules.containsKey("precision") && rules.containsKey("scale")
                && integer(rules.get("scale"),"scale")>integer(rules.get("precision"),"precision"))
            fail(field,"小数位不能超过总位数");
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
        if (rules.containsKey("optionDisplayRules") && (!field.type().options()
                || !(rules.get("optionDisplayRules") instanceof List<?>)))
            fail(field,"选项联动显示规则无效");
        if (field.type()==FieldType.SELECT && "SWITCH".equals(style)) {
            if (Boolean.TRUE.equals(rules.get("multiple"))) fail(field,"开关样式不能启用多选");
            if (!(rules.get("options") instanceof List<?> options)
                    || options.stream().filter(item->item instanceof Map<?,?> option && !Boolean.FALSE.equals(option.get("enabled"))).count()!=2)
                fail(field,"开关样式必须恰好绑定两个启用的字典项");
        }
        if (rules.containsKey("defaultValue")) validateDefault(field,rules.get("defaultValue"));
    }

    /** 校验最终业务值；不会把文本数字或错误布尔值偷偷转换后保存。 */
    public void validateValue(FieldDefinition field, Object value) {
        FieldType type = field.type();
        if (empty(value)) {
            if (field.required()) fail(field, "不能为空");
            if (value == null || value instanceof String && type.kind() == FieldType.Kind.TEXT
                    || value instanceof Collection<?> && (type.kind()==FieldType.Kind.OPTIONS
                    || type==FieldType.SELECT && Boolean.TRUE.equals(field.rules().get("multiple")))) return;
        }
        if (type.computed()) fail(field, "由系统计算，不能直接提交");
        // 附件字段存储同批文件共享的 UUID，而不是文件主键或文件 ID 数组。
        if (type==FieldType.ATTACHMENT) {
            if (!FileBatchId.valid(value)) fail(field, "必须是32位UUID文件批次 ID");
            return;
        }
        try {
            switch (type.kind()) {
                case TEXT -> text(field, value);
                case NUMBER -> number(field, value);
                case DATE_TIME -> {
                    if ("DATE".equals(field.rules().get("displayStyle"))) LocalDate.parse(string(value));
                    else LocalDateTime.parse(string(value));
                }
                case OPTION -> {
                    if (type==FieldType.SELECT && Boolean.TRUE.equals(field.rules().get("multiple"))) options(field, value);
                    else option(field, value);
                }
                case OPTIONS -> {
                    if (type == FieldType.CASCADE) cascade(field, value);
                    else options(field, value);
                }
                case REFERENCE -> {
                    if (Boolean.TRUE.equals(field.rules().get("multiple"))) references(field, value);
                    else if (!positiveId(value)) fail(field, "必须是有效关联标识");
                }
                case REFERENCES -> references(field, value);
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
    }

    private void number(FieldDefinition field, Object value) {
        if (!(value instanceof Number)) fail(field, "必须是数值");
        BigDecimal number = decimal(value);
        if (field.rules().containsKey("min") && number.compareTo(decimal(field.rules().get("min"))) < 0) fail(field, "低于最小值");
        if (field.rules().containsKey("max") && number.compareTo(decimal(field.rules().get("max"))) > 0) fail(field, "高于最大值");
        if (field.rules().containsKey("scale") && Math.max(number.stripTrailingZeros().scale(),0)
                > integer(field.rules().get("scale"),"scale")) fail(field,"小数位超过配置位数");
        BigDecimal normalized=number.stripTrailingZeros();
        int digits=Math.max(normalized.precision()-normalized.scale(),0)+Math.max(normalized.scale(),0);
        if (field.rules().containsKey("precision") && digits>integer(field.rules().get("precision"),"precision"))
            fail(field,"总位数超过配置位数");
    }

    private void option(FieldDefinition field, Object value) {
        if (field.type()==FieldType.SELECT) {
            if (!(value instanceof String text) || text.isBlank()) fail(field,"选项值必须是非空字符串");
        } else if (!positiveId(value)) fail(field, "选项标识必须是字典项 ID");
        if (field.rules().get("options") instanceof List<?> options) {
            boolean found = options.stream().filter(Map.class::isInstance).map(Map.class::cast)
                    .anyMatch(option -> String.valueOf(value).equals(String.valueOf(
                            field.type()==FieldType.SELECT?option.get("value"):option.get("id")))
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

    private void items(FieldDefinition field, List<?> values) { limit(field, values.size(), "minItems", "maxItems", "条目数量"); }

    /** 默认值按文本保存，但仍校验它能被当前字段解释。 */
    private void validateDefault(FieldDefinition field,Object value) {
        if (!(value instanceof String) || ((String)value).length()>500) fail(field,"默认值必须是500字符以内的文本");
        String text=(String)value;
        if (text.isBlank()) return;
        try {
            switch (field.type()) {
                case NUMBER -> new BigDecimal(text);
                case DATE_TIME -> { if ("DATE".equals(field.rules().get("displayStyle"))) LocalDate.parse(text); else LocalDateTime.parse(text); }
                case SELECT -> { if (text.isBlank()) fail(field,"默认选项值不能为空"); }
                default -> { }
            }
        } catch (RuntimeException ex) { fail(field,"默认值不能按当前字段类型解释"); }
    }
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
