package com.unique.examine.core.idempotency;

/**
 * 幂等记录状态。
 */
public enum IdempotencyRecordStatus {

    /** 首个请求正在处理。 */
    PROCESSING,

    /** 请求已完成，可按快照回放。 */
    COMPLETED,

    /** 请求处理失败，可按调用方策略决定是否保存失败快照。 */
    FAILED
}
