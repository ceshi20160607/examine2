package com.unique.core.enums;

/**
 * 用户状态枚举
 *
 * @author UNIQUE
 * @date 2023/3/27
 */
public enum DataTypeEnum {
    /**
     * 审批 类型枚举
     */
    ALL(0, "全部--不使用"),
    SELF(1, "本人"),
    SELF_AND_CHILD(2, "本人及直属下属"),
    DEPT(3, "部门"),
    DEPT_AND_CHILD(4, "部门及下属部门"),
    ;

    DataTypeEnum(Integer type, String remarks) {
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

    public static DataTypeEnum parse(Integer type) {
        for (DataTypeEnum item : values()) {
            if (item.getType().equals(type)) {
                return item;
            }
        }
        return null;
    }
}
