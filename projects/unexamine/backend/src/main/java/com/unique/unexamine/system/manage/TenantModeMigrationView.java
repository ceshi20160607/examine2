package com.unique.unexamine.system.manage;

import java.time.LocalDateTime;

public record TenantModeMigrationView(
        Long id,
        String fromMode,
        String toMode,
        String status,
        Long jobId,
        Long requestedByMemberId,
        String impactSnapshotJson,
        String rollbackSnapshotJson,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        Integer version) {
}
