package com.unique.examine.module.manage.enums;

import com.unique.examine.core.error.ErrorCode;
import com.unique.examine.core.error.ErrorCodeNamespace;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 系统管理与 RBAC 错误码。
 */
@Getter
@RequiredArgsConstructor
public enum SystemManageErrorCode implements ErrorCode {

    /** 系统不存在或已删除。 */
    SYSTEM_NOT_FOUND("SYS_SYSTEM_NOT_FOUND", "系统不存在", HttpStatus.NOT_FOUND, false),

    /** 系统已停用。 */
    SYSTEM_DISABLED("SYS_SYSTEM_DISABLED", "系统已停用", HttpStatus.FORBIDDEN, false),

    /** 租户不存在。 */
    TENANT_NOT_FOUND("SYS_TENANT_NOT_FOUND", "租户不存在", HttpStatus.NOT_FOUND, false),

    /** 租户已停用。 */
    TENANT_DISABLED("SYS_TENANT_DISABLED", "租户已停用", HttpStatus.FORBIDDEN, false),

    /** 成员账号入参缺失。 */
    MEMBER_ACCOUNT_REQUIRED("SYS_MEMBER_ACCOUNT_REQUIRED", "成员账号不能为空", HttpStatus.BAD_REQUEST, false),

    /** 成员已存在。 */
    MEMBER_DUPLICATED("SYS_MEMBER_DUPLICATED", "成员已存在", HttpStatus.CONFLICT, false),

    /** 成员不存在。 */
    MEMBER_NOT_FOUND("SYS_MEMBER_NOT_FOUND", "成员不存在", HttpStatus.NOT_FOUND, false),

    /** 成员已停用。 */
    MEMBER_DISABLED("SYS_MEMBER_DISABLED", "成员已停用", HttpStatus.FORBIDDEN, false),

    /** 部门不存在。 */
    DEPT_NOT_FOUND("SYS_DEPT_NOT_FOUND", "部门不存在", HttpStatus.NOT_FOUND, false),

    /** 部门下存在成员。 */
    DEPT_HAS_MEMBER("SYS_DEPT_HAS_MEMBER", "部门下存在成员，不能删除", HttpStatus.CONFLICT, false),

    /** 角色不存在。 */
    ROLE_NOT_FOUND("SYS_ROLE_NOT_FOUND", "系统角色不存在", HttpStatus.NOT_FOUND, false),

    /** 角色已停用。 */
    ROLE_DISABLED("SYS_ROLE_DISABLED", "系统角色已停用", HttpStatus.FORBIDDEN, false),

    /** 角色编码重复。 */
    ROLE_CODE_DUPLICATED("SYS_ROLE_CODE_DUPLICATED", "系统角色编码已存在", HttpStatus.CONFLICT, false),

    /** 保护角色不允许当前操作。 */
    ROLE_PROTECTED("SYS_ROLE_PROTECTED", "保护角色不允许当前操作", HttpStatus.FORBIDDEN, false),

    /** 权限对象不存在。 */
    PERMISSION_TARGET_NOT_FOUND("SYS_PERMISSION_TARGET_NOT_FOUND", "权限对象不存在", HttpStatus.NOT_FOUND, false),

    /** 状态入参不合法。 */
    STATUS_INVALID("SYS_STATUS_INVALID", "状态不合法", HttpStatus.BAD_REQUEST, false);

    private final String code;

    private final String message;

    private final HttpStatus httpStatus;

    private final boolean retryable;

    @Override
    public ErrorCodeNamespace getNamespace() {
        return ErrorCodeNamespace.SYS;
    }
}
