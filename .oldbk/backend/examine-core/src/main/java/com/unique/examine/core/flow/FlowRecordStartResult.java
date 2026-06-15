package com.unique.examine.core.flow;

import lombok.Builder;
import lombok.Data;

/**
 * 业务记录发起流程结果。
 */
@Data
@Builder
public class FlowRecordStartResult {

    /** 流程实例 ID。 */
    private Long instanceId;

    /** 实例状态。 */
    private String instanceStatus;

    /** 当前节点编码。 */
    private String currentNodeKeys;
}
