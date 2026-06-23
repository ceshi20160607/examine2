package com.unique.examine.core.idempotency;

import com.unique.examine.core.common.response.ApiResponse;

/**
 * 幂等基础服务抽象。
 */
public interface IdempotencyService {

    /**
     * 检查幂等状态并尝试创建处理中锁。
     *
     * @param context 幂等上下文
     * @return 幂等判定结果
     * @param <T> 快照响应数据类型
     */
    <T> IdempotencyDecision<T> checkOrStart(IdempotencyContext context);

    /**
     * 保存成功结果快照。
     *
     * @param context 幂等上下文
     * @param response 成功响应快照
     * @param <T> 快照响应数据类型
     */
    <T> void complete(IdempotencyContext context, ApiResponse<T> response);

    /**
     * 保存失败结果快照。
     *
     * @param context 幂等上下文
     * @param response 失败响应快照
     * @param <T> 快照响应数据类型
     */
    <T> void fail(IdempotencyContext context, ApiResponse<T> response);

    /**
     * 释放处理中锁。
     *
     * @param context 幂等上下文
     */
    void release(IdempotencyContext context);
}
