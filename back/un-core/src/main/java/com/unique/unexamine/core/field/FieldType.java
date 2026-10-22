package com.unique.unexamine.core.field;

import java.util.Locale;

/** 固定字段类型目录；模块、任务和其他可配置表单共用同一类型与值形态。 */
public enum FieldType {
    TEXT("文本", Kind.TEXT), MULTILINE("多行文本", Kind.TEXT), PHONE("手机号", Kind.TEXT),
    EMAIL("邮箱", Kind.TEXT), URL("网址", Kind.TEXT), ID_CARD("身份证号", Kind.TEXT),
    NUMBER("数字", Kind.NUMBER), PERCENT("百分比", Kind.NUMBER), MONEY("金额", Kind.NUMBER),
    DATE("日期", Kind.DATE), DATETIME("日期时间", Kind.DATETIME), DATE_RANGE("日期范围", Kind.DATE_RANGE),
    TIME("时间", Kind.TIME), TIME_RANGE("时间范围", Kind.TIME_RANGE),
    SINGLE_SELECT("单选", Kind.OPTION), MULTI_SELECT("多选", Kind.OPTIONS), CASCADE("级联选择", Kind.OPTIONS),
    BOOLEAN("开关", Kind.BOOLEAN), USER("人员", Kind.REFERENCE), DEPARTMENT("部门", Kind.REFERENCE),
    TENANT("租户", Kind.REFERENCE), ATTACHMENT("附件", Kind.REFERENCES), IMAGE("图片", Kind.REFERENCES),
    FILE_GROUP("文件组", Kind.REFERENCES), AUTO_NUMBER("自动编号", Kind.COMPUTED),
    RELATION("关联数据", Kind.REFERENCE), REFERENCE("引用字段", Kind.COMPUTED), SUBTABLE("子表", Kind.ROWS),
    ADDRESS("地址", Kind.OBJECT), LOCATION("地理位置", Kind.OBJECT), RATING("评分", Kind.NUMBER),
    PROGRESS("进度", Kind.NUMBER), TAG("标签", Kind.OPTIONS), BARCODE("条码", Kind.TEXT),
    QRCODE("二维码", Kind.TEXT), SIGNATURE("签名", Kind.REFERENCE), RICH_TEXT("富文本", Kind.TEXT),
    JSON("JSON", Kind.OBJECT), SECRET("密码或密钥", Kind.TEXT), STATUS("状态", Kind.OPTION),
    CREATOR("创建人", Kind.COMPUTED), CREATE_TIME("创建时间", Kind.COMPUTED),
    UPDATER("更新人", Kind.COMPUTED), UPDATE_TIME("更新时间", Kind.COMPUTED),
    FORMULA("公式", Kind.COMPUTED), SUMMARY("汇总", Kind.COMPUTED), CALCULATED("计算", Kind.COMPUTED),
    LOOKUP("查找", Kind.COMPUTED), AGGREGATE("聚合", Kind.COMPUTED), AI("AI 填充", Kind.TEXT);

    /** 统一值形态，复杂引用的存在性与业务授权由对应业务服务检查。 */
    public enum Kind { TEXT, NUMBER, DATE, DATETIME, DATE_RANGE, TIME, TIME_RANGE,
        OPTION, OPTIONS, BOOLEAN, REFERENCE, REFERENCES, ROWS, OBJECT, COMPUTED }

    private final String label;
    private final Kind kind;

    FieldType(String label, Kind kind) { this.label = label; this.kind = kind; }

    /** 字段设计器显示的中文名称。 */
    public String label() { return label; }
    /** 类型规定的基本值形态。 */
    public Kind kind() { return kind; }
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
