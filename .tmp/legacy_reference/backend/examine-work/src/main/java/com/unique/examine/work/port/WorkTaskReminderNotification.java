package com.unique.examine.work.port;

import java.time.Instant;

public record WorkTaskReminderNotification(
        String deliveryKey,
        long systemId,
        long tenantId,
        long recipientMemberId,
        long taskId,
        String taskTitle,
        Instant dueAt,
        String sourceType,
        String referencePath
) {
    public static final String SOURCE_TYPE = "WORK_TASK_REMINDER";

    public WorkTaskReminderNotification {
        if (deliveryKey == null || deliveryKey.isBlank()
                || systemId <= 0 || tenantId <= 0 || recipientMemberId <= 0 || taskId <= 0) {
            throw new IllegalArgumentException("Reminder notification identity is incomplete");
        }
        if (taskTitle == null || taskTitle.isBlank() || taskTitle.length() > 500) {
            throw new IllegalArgumentException("Reminder notification title is invalid");
        }
        taskTitle = taskTitle.trim();
        if (!SOURCE_TYPE.equals(sourceType)) {
            throw new IllegalArgumentException("Reminder notification source is invalid");
        }
        var expectedPath = "/work/tasks/" + taskId;
        if (!expectedPath.equals(referencePath)) {
            throw new IllegalArgumentException("Reminder notification path is invalid");
        }
    }
}
