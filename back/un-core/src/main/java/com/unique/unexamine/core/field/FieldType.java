package com.unique.unexamine.core.field;

import java.util.Locale;

/** 固定字段类型目录；模块、任务和其他可配置表单共用同一类型与值形态。 */
public enum FieldType {
    TEXT("文本", Kind.TEXT, ValueColumn.TEXT), MULTILINE("多行文本", Kind.TEXT, ValueColumn.TEXT),
    PHONE("手机号", Kind.TEXT, ValueColumn.VARCHAR), EMAIL("邮箱", Kind.TEXT, ValueColumn.VARCHAR),
    URL("网址", Kind.TEXT, ValueColumn.TEXT), ID_CARD("身份证号", Kind.TEXT, ValueColumn.VARCHAR),
    NUMBER("数字", Kind.NUMBER, ValueColumn.NUMBER), PERCENT("百分比", Kind.NUMBER, ValueColumn.NUMBER),
    MONEY("金额", Kind.NUMBER, ValueColumn.NUMBER), DATE("日期", Kind.DATE, ValueColumn.TIME),
    DATETIME("日期时间", Kind.DATETIME, ValueColumn.TIME), DATE_RANGE("日期范围", Kind.DATE_RANGE, ValueColumn.JSON),
    TIME("时间", Kind.TIME, ValueColumn.VARCHAR), TIME_RANGE("时间范围", Kind.TIME_RANGE, ValueColumn.JSON),
    SINGLE_SELECT("单选", Kind.OPTION, ValueColumn.NUMBER),
    MULTI_SELECT("多选", Kind.OPTIONS, ValueColumn.JSON), CASCADE("级联选择", Kind.OPTIONS, ValueColumn.JSON),
    BOOLEAN("开关", Kind.BOOLEAN, ValueColumn.NUMBER), USER("人员", Kind.REFERENCE, ValueColumn.NUMBER),
    DEPARTMENT("部门", Kind.REFERENCE, ValueColumn.NUMBER), TENANT("租户", Kind.REFERENCE, ValueColumn.NUMBER),
    ATTACHMENT("附件", Kind.REFERENCES, ValueColumn.VARCHAR), IMAGE("图片", Kind.REFERENCES, ValueColumn.VARCHAR),
    FILE_GROUP("文件组", Kind.REFERENCES, ValueColumn.VARCHAR), AUTO_NUMBER("自动编号", Kind.COMPUTED, ValueColumn.NONE),
    RELATION("关联数据", Kind.REFERENCE, ValueColumn.NUMBER), REFERENCE("引用字段", Kind.COMPUTED, ValueColumn.NONE),
    SUBTABLE("子表", Kind.ROWS, ValueColumn.JSON), ADDRESS("地址", Kind.OBJECT, ValueColumn.JSON),
    LOCATION("地理位置", Kind.OBJECT, ValueColumn.JSON), RATING("评分", Kind.NUMBER, ValueColumn.NUMBER),
    PROGRESS("进度", Kind.NUMBER, ValueColumn.NUMBER), TAG("标签", Kind.OPTIONS, ValueColumn.JSON),
    BARCODE("条码", Kind.TEXT, ValueColumn.VARCHAR), QRCODE("二维码", Kind.TEXT, ValueColumn.VARCHAR),
    SIGNATURE("签名", Kind.REFERENCE, ValueColumn.NUMBER), RICH_TEXT("富文本", Kind.TEXT, ValueColumn.TEXT),
    JSON("JSON", Kind.OBJECT, ValueColumn.JSON), SECRET("密码或密钥", Kind.TEXT, ValueColumn.TEXT),
    STATUS("状态", Kind.OPTION, ValueColumn.NUMBER), CREATOR("创建人", Kind.COMPUTED, ValueColumn.NONE),
    CREATE_TIME("创建时间", Kind.COMPUTED, ValueColumn.NONE), UPDATER("更新人", Kind.COMPUTED, ValueColumn.NONE),
    UPDATE_TIME("更新时间", Kind.COMPUTED, ValueColumn.NONE), FORMULA("公式", Kind.COMPUTED, ValueColumn.NONE),
    SUMMARY("汇总", Kind.COMPUTED, ValueColumn.NONE), CALCULATED("计算", Kind.COMPUTED, ValueColumn.NONE),
    LOOKUP("查找", Kind.COMPUTED, ValueColumn.NONE), AGGREGATE("聚合", Kind.COMPUTED, ValueColumn.NONE),
    AI("AI 填充", Kind.TEXT, ValueColumn.TEXT);

    /** 统一值形态，复杂引用的存在性与业务授权由对应业务服务检查。 */
    public enum Kind { TEXT, NUMBER, DATE, DATETIME, DATE_RANGE, TIME, TIME_RANGE,
        OPTION, OPTIONS, BOOLEAN, REFERENCE, REFERENCES, ROWS, OBJECT, COMPUTED }

    /** 业务值的物理列；计算字段不接受通用记录保存。 */
    public enum ValueColumn {
        VARCHAR("value_varchar"), TEXT("value_text"), NUMBER("value_number"),
        TIME("value_time"), JSON("value_json"), NONE(null);

        private final String sqlName;
        ValueColumn(String sqlName) { this.sqlName = sqlName; }
        /** 仅供已校验的固定字段类型拼接查询列，不能接收客户端列名。 */
        public String sqlName() { return sqlName; }
    }

    private final String label;
    private final Kind kind;
    private final ValueColumn valueColumn;

    FieldType(String label, Kind kind, ValueColumn valueColumn) {
        this.label = label; this.kind = kind; this.valueColumn = valueColumn;
    }

    /** 字段设计器显示的中文名称。 */
    public String label() { return label; }
    /** 类型规定的基本值形态。 */
    public Kind kind() { return kind; }
    /** 单值字段写入的物理列；允许多值的引用字段由配置切换到 JSON 列。 */
    public ValueColumn valueColumn() { return valueColumn; }
    /** 多选引用按 ID 数组保存，其他字段不因页面配置改变值列。 */
    public ValueColumn valueColumn(boolean multiple) {
        return multiple && kind == Kind.REFERENCE ? ValueColumn.JSON : valueColumn;
    }
    /** 服务端计算字段不能由表单、导入或应用直接写入。 */
    public boolean computed() { return kind == Kind.COMPUTED; }
    /** 敏感值不能通过通用详情或导出明文返回。 */
    public boolean sensitive() { return this == SECRET; }
    /** 选项值可以绑定统一数据字典。 */
    public boolean options() { return kind == Kind.OPTION || kind == Kind.OPTIONS; }

    /** 不允许运行时提交未知字段类型。 */
    public static FieldType parse(String value) {
        try { return valueOf(value.trim().toUpperCase(Locale.ROOT)); }
        catch (RuntimeException ex) { throw new IllegalArgumentException("未知字段类型: " + value); }
    }
}
