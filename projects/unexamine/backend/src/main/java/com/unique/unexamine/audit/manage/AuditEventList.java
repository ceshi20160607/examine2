package com.unique.unexamine.audit.manage;

import com.unique.unexamine.audit.base.entity.AuditEvent;

import java.util.List;

public record AuditEventList(
        List<AuditEvent> events,
        long total,
        int page,
        int pageSize) {
}
