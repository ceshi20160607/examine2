package com.unique.examine.event.todo;

import com.unique.examine.event.domain.EventActor;
import com.unique.examine.event.domain.EventDomainException;
import com.unique.examine.event.domain.InboxMessage;
import com.unique.examine.event.service.MessageInboxService;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Narrow Event-owned boundary for projecting actionable inbox reminders into
 * Todo. It intentionally exposes no message body, sender, or template inputs.
 */
public final class EventTodoReminderFacade {
    public static final String ACTION_SCOPE = "MARK_READ";
    public static final String WORK_TASK_REMINDER = "WORK_TASK_REMINDER";
    public static final String FLOW_INSTANCE_COPIED = "FLOW_INSTANCE_COPIED";
    private static final Set<String> SUPPORTED_TEMPLATES = Set.of(
            WORK_TASK_REMINDER,
            FLOW_INSTANCE_COPIED
    );

    private final MessageInboxService messages;

    public EventTodoReminderFacade(MessageInboxService messages) {
        if (messages == null) {
            throw new IllegalArgumentException("Message inbox service is required");
        }
        this.messages = messages;
    }

    public List<Snapshot> listUnread(Actor actor) {
        var eventActor = eventActor(actor);
        return messages.currentUnread(eventActor).stream()
                .map(message -> snapshot(actor, message))
                .flatMap(Optional::stream)
                .toList();
    }

    public Reload reload(Actor actor, long messageId, long expectedVersion) {
        requireReference(messageId, expectedVersion);
        var current = messages.findOwn(eventActor(actor), messageId);
        if (current.isEmpty()) {
            return Reload.of(Reload.Status.MISSING);
        }
        var message = current.get();
        if (!SUPPORTED_TEMPLATES.contains(message.templateCode())) {
            return Reload.of(Reload.Status.INELIGIBLE);
        }
        if (!message.unread()) {
            return Reload.of(Reload.Status.COMPLETED);
        }
        if (message.version() != expectedVersion) {
            return Reload.of(Reload.Status.STALE);
        }
        var snapshot = snapshot(actor, message);
        if (snapshot.isEmpty()) {
            return Reload.of(Reload.Status.INELIGIBLE);
        }
        return Reload.live(snapshot.get());
    }

    public ActionResult markRead(
            Actor actor,
            long messageId,
            long expectedVersion
    ) {
        requireReference(messageId, expectedVersion);
        var eventActor = eventActor(actor);
        var current = messages.findOwn(eventActor, messageId);
        if (current.isEmpty()) {
            return result(ActionResult.Code.MISSING, expectedVersion,
                    "Event reminder is no longer available");
        }
        var message = current.get();
        if (!SUPPORTED_TEMPLATES.contains(message.templateCode())) {
            return result(ActionResult.Code.INELIGIBLE, message.version(),
                    "Event reminder is not Todo eligible");
        }
        if (!message.unread()) {
            return result(ActionResult.Code.COMPLETED, message.version(),
                    "Event reminder is already completed");
        }
        if (message.version() != expectedVersion) {
            return result(ActionResult.Code.STALE, message.version(),
                    "Event reminder changed before it was marked read");
        }
        if (snapshot(actor, message).isEmpty()) {
            return result(ActionResult.Code.INELIGIBLE, message.version(),
                    "Event reminder is not Todo eligible");
        }
        try {
            var changed = messages.markRead(eventActor, messageId, expectedVersion);
            return result(ActionResult.Code.SUCCESS, changed.version(),
                    "Event reminder marked read");
        } catch (EventDomainException conflict) {
            if (!"EVENT_MESSAGE_VERSION_CONFLICT".equals(conflict.code())) {
                throw conflict;
            }
            return result(ActionResult.Code.STALE, expectedVersion,
                    "Event reminder changed concurrently");
        }
    }

    private static Optional<Snapshot> snapshot(Actor actor, InboxMessage message) {
        if (!SUPPORTED_TEMPLATES.contains(message.templateCode())) {
            return Optional.empty();
        }
        var targetPath = safeTargetPath(actor, message);
        if (targetPath == null) {
            return Optional.empty();
        }
        return Optional.of(new Snapshot(
                message.id(),
                message.version(),
                message.templateCode(),
                message.title(),
                targetPath,
                message.createdAt()
        ));
    }

    private static String safeTargetPath(Actor actor, InboxMessage message) {
        var target = message.target();
        if (target == null) {
            return null;
        }
        final long targetId;
        try {
            targetId = Long.parseLong(target.id());
            if (targetId <= 0) {
                return null;
            }
        } catch (RuntimeException invalid) {
            return null;
        }
        final String storedPath;
        final String snapshotPath;
        if (WORK_TASK_REMINDER.equals(message.templateCode())
                && "WORK_TASK".equals(target.type())) {
            storedPath = "/systems/" + actor.systemId() + "/work/tasks/" + targetId;
            snapshotPath = "/systems/" + actor.systemId() + "/tasks?taskId=" + targetId;
        } else if (FLOW_INSTANCE_COPIED.equals(message.templateCode())
                && "FLOW_INSTANCE".equals(target.type())) {
            storedPath = "/systems/" + actor.systemId() + "/flows?instanceId=" + targetId;
            snapshotPath = storedPath;
        } else {
            return null;
        }
        if (message.targetPath() == null) {
            return FLOW_INSTANCE_COPIED.equals(message.templateCode())
                    ? snapshotPath
                    : null;
        }
        return storedPath.equals(message.targetPath()) ? snapshotPath : null;
    }

    private static EventActor eventActor(Actor actor) {
        if (actor == null) {
            throw new IllegalArgumentException("Event Todo actor is required");
        }
        return new EventActor(
                actor.systemId(), actor.tenantId(), actor.memberId(), Set.of());
    }

    private static void requireReference(long messageId, long expectedVersion) {
        if (messageId <= 0 || expectedVersion <= 0) {
            throw new IllegalArgumentException("Event Todo reminder reference is invalid");
        }
    }

    private static ActionResult result(
            ActionResult.Code code,
            long sourceVersion,
            String message
    ) {
        return new ActionResult(code, sourceVersion, message);
    }

    public record Actor(long systemId, long tenantId, long memberId) {
        public Actor {
            if (systemId <= 0 || tenantId <= 0 || memberId <= 0) {
                throw new IllegalArgumentException("Event Todo actor scope is incomplete");
            }
        }
    }

    public record Snapshot(
            long messageId,
            long version,
            String templateCode,
            String title,
            String targetPath,
            Instant createdAt
    ) {
        public Snapshot {
            if (messageId <= 0 || version <= 0
                    || !SUPPORTED_TEMPLATES.contains(templateCode)
                    || title == null || title.isBlank() || title.length() > 200
                    || targetPath == null || targetPath.isBlank()
                    || targetPath.length() > 500 || createdAt == null) {
                throw new IllegalArgumentException("Event Todo snapshot is incomplete");
            }
            title = title.trim();
        }
    }

    public record Reload(Status status, Snapshot snapshot) {
        public enum Status {
            LIVE,
            STALE,
            COMPLETED,
            MISSING,
            INELIGIBLE
        }

        public Reload {
            if (status == null || status == Status.LIVE && snapshot == null
                    || status != Status.LIVE && snapshot != null) {
                throw new IllegalArgumentException("Event Todo reload result is inconsistent");
            }
        }

        public static Reload live(Snapshot snapshot) {
            return new Reload(Status.LIVE, snapshot);
        }

        public static Reload of(Status status) {
            return new Reload(status, null);
        }
    }

    public record ActionResult(Code code, long sourceVersion, String message) {
        public enum Code {
            SUCCESS,
            STALE,
            COMPLETED,
            MISSING,
            INELIGIBLE
        }

        public ActionResult {
            if (code == null || sourceVersion <= 0 || message == null
                    || message.isBlank() || message.length() > 500) {
                throw new IllegalArgumentException("Event Todo action result is invalid");
            }
            message = message.trim();
        }
    }
}
