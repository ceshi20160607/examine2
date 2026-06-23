package com.unique.examine.plat.manage.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 启停状态。
 */
@Getter
@RequiredArgsConstructor
public enum EnableStatus {

    /** 启用。 */
    ENABLED("ENABLED"),

    /** 停用。 */
    DISABLED("DISABLED");

    private final String code;

    /**
     * 判断状态编码是否有效。
     *
     * @param code 状态编码
     * @return true 表示有效
     */
    public static boolean isValid(String code) {
        for (EnableStatus status : values()) {
            if (status.code.equals(code)) {
                return true;
            }
        }
        return false;
    }
}
