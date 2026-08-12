package com.unique.examine.event.platform;

import java.time.Instant;
import java.util.List;

public final class PlatformMessageApiModels {
    private PlatformMessageApiModels() { }
    public enum Status { ALL, UNREAD, READ, ARCHIVED }
    public enum Type { ALL, AUTHORIZATION, TASK, LOG, SYSTEM_SWITCH, AGENT }
    public record Target(String type, String id, String path) { }
    public record MessageView(String id, String templateCode, String type,
                              String title, String body, Target target,
                              String status, Instant createdAt, Instant readAt,
                              Instant archivedAt, long version) { }
    public record Page(List<MessageView> items, int page, int size, long total) {
        public Page { items = List.copyOf(items); }
    }
    public record UnreadCount(long unreadCount) { }
    public record ChangedCount(long changedCount) { }
}
