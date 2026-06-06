package com.unique.examine.core.error;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * COMMON 命名空间错误码。
 */
@Getter
@RequiredArgsConstructor
public enum CommonErrorCode implements ErrorCode {

    /** 成功响应。 */
    OK("COMMON_OK", "success", HttpStatus.OK, false),

    /** 通用参数错误。 */
    PARAM_INVALID("COMMON_PARAM_INVALID", "参数不合法", HttpStatus.BAD_REQUEST, false),

    /** 请求体格式错误。 */
    REQUEST_BODY_INVALID("COMMON_REQUEST_BODY_INVALID", "请求体格式不合法", HttpStatus.BAD_REQUEST, false),

    /** 认证信息缺失或无效。 */
    UNAUTHORIZED("COMMON_UNAUTHORIZED", "请先登录", HttpStatus.UNAUTHORIZED, false),

    /** 访问被拒绝。 */
    FORBIDDEN("COMMON_FORBIDDEN", "无访问权限", HttpStatus.FORBIDDEN, false),

    /** 资源不存在。 */
    NOT_FOUND("COMMON_NOT_FOUND", "资源不存在", HttpStatus.NOT_FOUND, false),

    /** 状态冲突或并发冲突。 */
    CONFLICT("COMMON_CONFLICT", "当前状态不允许操作", HttpStatus.CONFLICT, false),

    /** 幂等键对应的请求内容不一致。 */
    IDEMPOTENCY_CONFLICT("COMMON_IDEMPOTENCY_CONFLICT", "幂等请求内容不一致", HttpStatus.CONFLICT, false),

    /** 相同幂等键请求仍在处理中。 */
    IDEMPOTENCY_PROCESSING("COMMON_IDEMPOTENCY_PROCESSING", "相同幂等请求正在处理中", HttpStatus.LOCKED, true),

    /** 系统内部错误。 */
    INTERNAL_ERROR("COMMON_INTERNAL_ERROR", "系统异常，请稍后重试", HttpStatus.INTERNAL_SERVER_ERROR, true);

    private final String code;

    private final String message;

    private final HttpStatus httpStatus;

    private final boolean retryable;

    @Override
    public ErrorCodeNamespace getNamespace() {
        return ErrorCodeNamespace.COMMON;
    }
}
