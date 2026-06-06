package com.unique.examine.core.idempotency;

/**
 * 幂等判定状态。
 */
public enum IdempotencyDecisionStatus {

    /** 首次请求，允许业务继续执行。 */
    PROCEED,

    /** 相同 key 相同 hash，返回历史快照。 */
    REPLAY,

    /** 相同 key 不同 hash，返回冲突。 */
    CONFLICT,

    /** 相同 key 相同 hash 的首个请求仍在处理中。 */
    PROCESSING
}
