package com.unique.examine.core.idempotency;

import java.time.Instant;

import com.unique.examine.core.common.response.ApiResponse;
import lombok.Builder;
import lombok.Data;

/**
 * 幂等记录。
 *
 * @param <T> 快照响应数据类型
 */
@Data
@Builder
public class IdempotencyRecord<T> {

    private String scope;

    private String idempotencyKey;

    private String requestHash;

    private String resultSnapshotId;

    private IdempotencyRecordStatus status;

    private ApiResponse<T> resultSnapshot;

    private Instant expiresAt;
}
