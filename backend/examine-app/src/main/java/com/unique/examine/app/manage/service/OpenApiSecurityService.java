package com.unique.examine.app.manage.service;

import com.unique.examine.app.manage.vo.OpenApiRequestContext;
import jakarta.servlet.http.HttpServletRequest;

/**
 * OpenAPI 签名与安全校验服务。
 */
public interface OpenApiSecurityService {

    /**
     * 校验 AK/SK、timestamp、nonce、body hash、签名、scope、限流和幂等。
     *
     * @param request 原始 HTTP 请求
     * @param rawBody 原始请求体
     * @param apiId API ID
     * @param scopeCode 需要的 scope
     * @param moduleCode 模块编码
     * @param idempotent 是否需要幂等键
     * @return 已验签上下文
     */
    OpenApiRequestContext verify(HttpServletRequest request, String rawBody, String apiId, String scopeCode,
            String moduleCode, boolean idempotent);

    /**
     * 标记 OpenAPI 调用成功。
     *
     * @param context 请求上下文
     * @param bizType 业务类型
     * @param bizId 业务 ID
     */
    void markSuccess(OpenApiRequestContext context, String bizType, String bizId);

    /**
     * 标记 OpenAPI 调用失败。
     *
     * @param context 请求上下文
     * @param errorCode 错误码
     */
    void markFailure(OpenApiRequestContext context, String errorCode);
}
