package com.unique.unexamine.runtimedata.manage;

import java.time.LocalDateTime;
import java.util.List;

public record RuntimeRecordTimelineEntry(
        Long eventId,
        String eventCode,
        String label,
        Long actorAccountId,
        Long actorMemberId,
        String actorDisplayName,
        String summary,
        String targetType,
        String targetId,
        LocalDateTime occurredAt,
        List<RuntimeRecordTimelineChange> changes) {
}
