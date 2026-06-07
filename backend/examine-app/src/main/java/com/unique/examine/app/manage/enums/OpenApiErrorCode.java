package com.unique.examine.app.manage.enums;

import com.unique.examine.core.error.ErrorCode;
import com.unique.examine.core.error.ErrorCodeNamespace;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * OpenAPI 错误码。
 */
@Getter
@RequiredArgsConstructor
public enum OpenApiErrorCode implements ErrorCode {

    /** accessKey 不存在或无有效凭证。 */
    CLIENT_NOT_FOUND("OPENAPI_CLIENT_NOT_FOUND", "OpenAPI 客户端不存在", HttpStatus.UNAUTHORIZED, false),

    /** 客户端未启用、已停用或已过期。 */
    CLIENT_DISABLED("OPENAPI_CLIENT_DISABLED", "OpenAPI 客户端不可用", HttpStatus.FORBIDDEN, false),

    /** 来源 IP 未命中白名单。 */
    IP_DENIED("OPENAPI_IP_DENIED", "来源 IP 不在白名单内", HttpStatus.FORBIDDEN, false),

    /** timestamp 超出允许窗口。 */
    TIMESTAMP_EXPIRED("OPENAPI_TIMESTAMP_EXPIRED", "OpenAPI timestamp 已过期", HttpStatus.UNAUTHORIZED, false),

    /** nonce 重放。 */
    NONCE_REPLAY("OPENAPI_NONCE_REPLAY", "OpenAPI nonce 重放", HttpStatus.CONFLICT, false),

    /** body hash 与请求体不一致。 */
    BODY_HASH_MISMATCH("OPENAPI_BODY_HASH_MISMATCH", "OpenAPI body hash 不匹配", HttpStatus.UNAUTHORIZED, false),

    /** canonical request 或 HMAC 签名不匹配。 */
    SIGNATURE_INVALID("OPENAPI_SIGNATURE_INVALID", "OpenAPI 签名不匹配", HttpStatus.UNAUTHORIZED, false),

    /** scope 未授权。 */
    SCOPE_DENIED("OPENAPI_SCOPE_DENIED", "OpenAPI scope 未授权", HttpStatus.FORBIDDEN, false),

    /** 命中限流策略。 */
    RATE_LIMITED("OPENAPI_RATE_LIMITED", "OpenAPI 调用触发限流", HttpStatus.TOO_MANY_REQUESTS, true),

    /** 幂等键请求摘要不一致。 */
    IDEMPOTENCY_CONFLICT("OPENAPI_IDEMPOTENCY_CONFLICT", "OpenAPI 幂等键请求内容不一致", HttpStatus.CONFLICT, false),

    /** 幂等键对应请求仍在处理中。 */
    IDEMPOTENCY_PROCESSING("OPENAPI_IDEMPOTENCY_PROCESSING", "OpenAPI 幂等请求处理中", HttpStatus.LOCKED, true);

    private final String code;

    private final String message;

    private final HttpStatus httpStatus;

    private final boolean retryable;

    @Override
    public ErrorCodeNamespace getNamespace() {
        return ErrorCodeNamespace.OPENAPI;
    }
}
