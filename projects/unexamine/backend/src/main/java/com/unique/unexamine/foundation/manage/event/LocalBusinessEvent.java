package com.unique.unexamine.foundation.manage.event;

import java.time.LocalDateTime;

/**
 * 本地扩展点接收的已提交业务事实。只传业务标识与执行人引用，不传字段值、文件内容或凭据。
 */
public record LocalBusinessEvent(
        Type type,
        String contextType,
        Long systemId,
        Long tenantId,
        String sourceType,
        String sourceId,
        String moduleCode,
        Long actorAccountId,
        Long actorMemberId,
        LocalDateTime occurredTime) {

    /** 产品需求 14.15 规定的九个扩展时点。 */
    public enum Type {
        RECORD_CREATED, RECORD_UPDATED, RECORD_DELETED,
        FLOW_STARTED, FLOW_APPROVED, FLOW_REJECTED,
        FILE_UPLOADED, IMPORT_COMPLETED, EXPORT_COMPLETED
    }
}
