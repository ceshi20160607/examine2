package com.unique.examine.core.context;

import java.time.Instant;

import lombok.Builder;
import lombok.Data;

/**
 * 请求上下文，承载追踪、租户、系统和幂等基础信息。
 */
@Data
@Builder
public class RequestContext {

    /** 请求 ID 响应头和日志 MDC key。 */
    public static final String REQUEST_ID = "requestId";

    /** 链路 ID 响应头和日志 MDC key。 */
    public static final String TRACE_ID = "traceId";

    /** 前端可传入的请求 ID 请求头。 */
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    /** 网关或调用方可传入的链路 ID 请求头。 */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    /** 租户请求头。 */
    public static final String TENANT_ID_HEADER = "X-Tenant-Id";

    /** 系统请求头。 */
    public static final String SYSTEM_ID_HEADER = "X-System-Id";

    /** 系统成员请求头。 */
    public static final String MEMBER_ID_HEADER = "X-Member-Id";

    /** OpenAPI 客户端请求头。 */
    public static final String CLIENT_ID_HEADER = "X-Client-Id";

    /** 幂等键请求头。 */
    public static final String IDEMPOTENCY_KEY_HEADER = "X-Idempotency-Key";

    private String requestId;

    private String traceId;

    private String tenantId;

    private String accountId;

    private String systemId;

    private String memberId;

    private String clientId;

    private String idempotencyKey;

    private String requestHash;

    private String path;

    private String method;

    private Instant startedAt;
}
