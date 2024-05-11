package com.unique.examine.enums;

/**
 * 审批类型
 * @author UNIQUE
 * @date 2023/3/9
 */
public enum ExamineApplyTypeEnum {
    /**
     * 审批 类型枚举
     */
    USER(0,"用户"),
    DEPT (1, "部门"),
    ROLE (2, "角色"),
    EMAIL(3, "游戏"),
    ;

    ExamineApplyTypeEnum(Integer type, String remarks) {
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

    public static ExamineApplyTypeEnum parse(Integer type) {
        for (ExamineApplyTypeEnum item : values()) {
            if (item.getType().equals(type)) {
                return item;
            }
        }
        return null;
    }
}
