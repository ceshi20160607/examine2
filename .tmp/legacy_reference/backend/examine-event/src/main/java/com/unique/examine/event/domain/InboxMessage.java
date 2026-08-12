package com.unique.examine.event.domain;

import com.unique.examine.core.api.AggregateRef;

import java.time.Instant;

public record InboxMessage(
        long id,
        long systemId,
        long tenantId,
        long senderMemberId,
        long recipientMemberId,
        String templateCode,
        String title,
        String body,
        AggregateRef target,
        String targetPath,
        Instant createdAt,
        Instant readAt,
        Instant archivedAt,
        long version
) {
    public enum Status {
        UNREAD,
        READ,
        ARCHIVED
    }

    public InboxMessage {
        if (id <= 0 || systemId <= 0 || tenantId <= 0 || senderMemberId <= 0 || recipientMemberId <= 0) {
            throw new IllegalArgumentException("Message identity and scope values must be positive");
        }
        templateCode = required(templateCode, "template code", 100);
        title = required(title, "title", 200);
        body = required(body, "body", 4000);
        if (targetPath != null && (targetPath.isBlank() || targetPath.length() > 500
                || !targetPath.startsWith("/systems/" + systemId + "/"))) {
            throw new IllegalArgumentException("Message target path must stay inside its system");
        }
        if (createdAt == null || version <= 0) {
            throw new IllegalArgumentException("Message state is incomplete");
        }
        if (archivedAt != null && readAt == null) {
            throw new IllegalArgumentException("An archived message must also be read");
        }
    }

    public boolean unread() {
        return readAt == null && archivedAt == null;
    }

    public Status status() {
        if (archivedAt != null) {
            return Status.ARCHIVED;
        }
        return readAt == null ? Status.UNREAD : Status.READ;
    }

    public InboxMessage markRead(Instant now) {
        if (readAt != null) {
            return this;
        }
        return new InboxMessage(id, systemId, tenantId, senderMemberId, recipientMemberId,
                templateCode, title, body, target, targetPath, createdAt, now, archivedAt, version + 1);
    }

    public InboxMessage archive(Instant now) {
        if (archivedAt != null) {
            return this;
        }
        return new InboxMessage(id, systemId, tenantId, senderMemberId, recipientMemberId,
                templateCode, title, body, target, targetPath, createdAt, readAt == null ? now : readAt, now,
                version + 1);
    }

    private static String required(String value, String name, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException("Message " + name + " must contain 1 to " + maxLength + " characters");
        }
        return value.trim();
    }
}
