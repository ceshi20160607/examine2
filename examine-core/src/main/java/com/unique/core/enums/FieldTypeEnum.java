package com.unique.core.enums;

/**
 * 用户状态枚举
 *
 * @author UNIQUE
 * @date 2023/3/27
 */
public enum FieldTypeEnum {
    /**
     * 审批 类型枚举
     */
    EXTEND(0, "扩展非默认主表字段"),
    BASE(1, "系统基础字段"),
    MAIN(2, "扩展默认在主表字段"),
    ;

    FieldTypeEnum(Integer type, String remarks) {
        this.type = type;
        this.remarks = remarks;
    }

    private final Integer type;
    private final String remarks;


    public Integer getType() {
        return type;
    }

    public String getRemarks() {
        return remarks;
    }

    public static FieldTypeEnum parse(Integer type) {
        for (FieldTypeEnum item : values()) {
            if (item.getType().equals(type)) {
                return item;
            }
        }
        return null;
    }
}
