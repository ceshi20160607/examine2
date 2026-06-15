package com.unique.examine.core.idempotency;

import com.unique.examine.core.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 幂等判定结果。
 *
 * @param <T> 快照响应数据类型
 */
@Data
@Builder
@Schema(description = "幂等判定结果")
public class IdempotencyDecision<T> {

    @Schema(description = "判定状态")
    private IdempotencyDecisionStatus status;

    @Schema(description = "是否允许继续执行业务逻辑")
    private boolean executable;

    @Schema(description = "幂等回放或冲突响应")
    private ApiResponse<T> response;

    @Schema(description = "结果快照 ID")
    private String resultSnapshotId;

    @Schema(description = "处理中重复请求建议等待秒数")
    private long retryAfterSeconds;

    /**
     * 首次请求允许执行业务。
     *
     * @param snapshotId 快照 ID
     * @return 判定结果
     * @param <T> 快照响应数据类型
     */
    public static <T> IdempotencyDecision<T> proceed(String snapshotId) {
        return IdempotencyDecision.<T>builder()
                .status(IdempotencyDecisionStatus.PROCEED)
                .executable(true)
                .resultSnapshotId(snapshotId)
                .build();
    }
}
