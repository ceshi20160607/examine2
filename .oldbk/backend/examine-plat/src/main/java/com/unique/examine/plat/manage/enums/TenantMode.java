package com.unique.examine.plat.manage.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 租户模式。
 */
@Getter
@RequiredArgsConstructor
public enum TenantMode {

    /** 单租户。 */
    SINGLE("SINGLE"),

    /** 多租户。 */
    MULTI("MULTI");

    private final String code;

    /**
     * 判断租户模式是否有效。
     *
     * @param code 租户模式编码
     * @return true 表示有效
     */
    public static boolean isValid(String code) {
        for (TenantMode mode : values()) {
            if (mode.code.equals(code)) {
                return true;
            }
        }
        return false;
    }
}
