package com.unique.examine.core.flow;

import lombok.Builder;
import lombok.Data;

/**
 * 业务记录发起流程入参。
 */
@Data
@Builder
public class FlowRecordStartRequest {

    /** 所属系统 ID。 */
    private Long systemId;

    /** 所属租户 ID。 */
    private Long tenantId;

    /** 所属模块 ID。 */
    private Long moduleId;

    /** 业务记录 ID。 */
    private Long recordId;

    /** 触发动作，例如 RECORD_SUBMIT。 */
    private String actionCode;

    /** 发起成员 ID。 */
    private Long starterMemberId;

    /** 请求追踪 ID。 */
    private String requestId;
}
