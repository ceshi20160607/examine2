package com.unique.examine.plat.manage.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 平台账号状态。
 */
@Getter
@RequiredArgsConstructor
public enum AccountStatus {

    /** 正常，可登录。 */
    NORMAL("NORMAL", "正常"),

    /** 已停用，不允许登录。 */
    DISABLED("DISABLED", "停用"),

    /** 已锁定，达到锁定截止时间前不允许登录。 */
    LOCKED("LOCKED", "锁定");

    private final String code;

    private final String description;
}
