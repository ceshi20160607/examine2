package com.unique.unexamine.sys.module.manage.enums;

import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;

/** 记录团队只提供查看和编辑两档权限，负责人仍由业务主记录维护。 */
public enum TeamAccessLevel {
    VIEW, EDIT;

    /** 拒绝旧身份、全部权限及其他未配置的档位。 */
    public static TeamAccessLevel parse(String value) {
        if (value == null) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "团队权限不能为空");
        try { return valueOf(value); }
        catch (IllegalArgumentException ex) {
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "团队权限只能是 VIEW 或 EDIT", ex);
        }
    }
}
