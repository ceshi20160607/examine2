package com.unique.unexamine.runtimedata.manage;

import java.util.List;

/** 当前列表可查询字段的最小描述，不包含来源表单配置、默认值或完整字典。 */
public record RuntimeListQueryField(
        String code, String name, String fieldType, boolean searchable, boolean sortable,
        List<String> operators, List<Option> options) {
    /** 选项只取当前已授权记录中的可见值和经过权限裁剪的名称。 */
    public record Option(String value, String label) {
    }

    /** 与公共类型控件一致；身份、关联和层级只比较选定值，不按内部编号大小比较。 */
    public static List<String> operatorsFor(String type) {
        return switch (type) {
            case "SECRET" -> List.of();
            case "JSON" -> List.of("EMPTY", "NOT_EMPTY");
            case "BOOLEAN", "SINGLE_SELECT", "STATUS", "MEMBER", "DEPARTMENT", "REFERENCE", "CASCADE" ->
                    List.of("EQ", "NE", "EMPTY", "NOT_EMPTY");
            case "NUMBER", "MONEY", "DATE", "DATETIME" ->
                    List.of("EQ", "NE", "GT", "GTE", "LT", "LTE", "EMPTY", "NOT_EMPTY");
            default -> List.of("EQ", "NE", "CONTAINS", "EMPTY", "NOT_EMPTY");
        };
    }
}
