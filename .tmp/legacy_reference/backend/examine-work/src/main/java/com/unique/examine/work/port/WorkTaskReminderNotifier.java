package com.unique.examine.work.port;

@FunctionalInterface
public interface WorkTaskReminderNotifier {
    void send(WorkTaskReminderNotification notification);
}
