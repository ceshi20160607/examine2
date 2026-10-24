package com.unique.unexamine.core.field;

import java.util.Arrays;

/** 模块字段的稳定类型编号；细分输入与展示效果由字段配置决定。 */
public enum FieldType {
    TEXT(1, "文本", Kind.TEXT, ValueColumn.TEXT),
    RICH_TEXT(2, "富文本", Kind.TEXT, ValueColumn.TEXT),
    NUMBER(3, "数字", Kind.NUMBER, ValueColumn.NUMBER),
    DATE_TIME(4, "日期时间", Kind.DATE_TIME, ValueColumn.TIME),
    /** 单选值保存字典的稳定 value 字符串；多选由 valueColumn(boolean) 切换为 JSON。 */
    SELECT(5, "选项", Kind.OPTION, ValueColumn.VARCHAR),
    CASCADE(6, "级联选择", Kind.OPTIONS, ValueColumn.JSON),
    USER(7, "人员", Kind.REFERENCE, ValueColumn.NUMBER),
    DEPARTMENT(8, "部门", Kind.REFERENCE, ValueColumn.NUMBER),
    ATTACHMENT(9, "附件", Kind.REFERENCES, ValueColumn.VARCHAR),
    RELATION(10, "关联模块", Kind.REFERENCE, ValueColumn.NUMBER),
    AUTO_NUMBER(11, "自动编号", Kind.COMPUTED, ValueColumn.NONE),
    FORMULA(12, "公式", Kind.COMPUTED, ValueColumn.NONE);

    /** 值形态决定共用校验；关联存在性与权限仍由业务服务检查。 */
    public enum Kind { TEXT, NUMBER, DATE_TIME, OPTION, OPTIONS, REFERENCE, REFERENCES, COMPUTED }

    /** 模块扩展记录中的唯一有效值列。 */
    public enum ValueColumn {
        VARCHAR("value_varchar"), TEXT("value_text"), NUMBER("value_number"),
        TIME("value_time"), JSON("value_json"), NONE(null);

        private final String sqlName;
        ValueColumn(String sqlName) { this.sqlName = sqlName; }
        public String sqlName() { return sqlName; }
    }

    private final int code;
    private final String label;
    private final Kind kind;
    private final ValueColumn valueColumn;

    FieldType(int code, String label, Kind kind, ValueColumn valueColumn) {
        this.code=code; this.label=label; this.kind=kind; this.valueColumn=valueColumn;
    }

    public int code() { return code; }
    public String label() { return label; }
    public Kind kind() { return kind; }
    public ValueColumn valueColumn() { return valueColumn; }
    /** 选项、人员和关联模块启用多选时按稳定标识集合保存。 */
    public ValueColumn valueColumn(boolean multiple) {
        return multiple && (this==SELECT || kind==Kind.REFERENCE) ? ValueColumn.JSON : valueColumn;
    }
    public boolean computed() { return kind==Kind.COMPUTED; }
    public boolean options() { return kind==Kind.OPTION || kind==Kind.OPTIONS; }

    /** 未指定样式时使用各类型的基础展示方式。 */
    public String defaultStyle() {
        return switch (this) {
            case TEXT -> "SINGLE_LINE"; case RICH_TEXT -> "MARKDOWN"; case NUMBER -> "NUMBER";
            case DATE_TIME -> "DATE_TIME"; case SELECT -> "DROPDOWN"; case CASCADE -> "CASCADE";
            case USER, DEPARTMENT, RELATION -> "PICKER"; case ATTACHMENT -> "FILE";
            default -> "DEFAULT";
        };
    }

    /** 只接受新整数编号，旧字符串和未知编号均拒绝。 */
    public static FieldType parse(Object value) {
        if (!(value instanceof Number number) || number.doubleValue()!=number.intValue())
            throw new IllegalArgumentException("字段类型须使用有效数字编号");
        return Arrays.stream(values()).filter(type->type.code==number.intValue()).findFirst()
                .orElseThrow(()->new IllegalArgumentException("未知字段类型编号: "+value));
    }
}
