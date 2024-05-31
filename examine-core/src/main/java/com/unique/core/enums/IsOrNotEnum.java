package com.unique.core.enums;

public enum IsOrNotEnum {

    /**
     * 行为
     */
    ZERO(0, "否"),
    ONE(1, "是")
    ;

    private int type;
    private String name;

    IsOrNotEnum(int type, String name) {
        this.type = type;
        this.name = name;
    }

    public int getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public static IsOrNotEnum parse(int type) {
        for (IsOrNotEnum Enum : IsOrNotEnum.values()) {
            if (Enum.getType() == type) {
                return Enum;
            }
        }
        return ZERO;
    }
}
