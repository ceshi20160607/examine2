package com.unique.examine.todo.port;

import com.unique.examine.todo.domain.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TodoRepository {
    long nextItemId();
    long nextActionLogId();

    Optional<TodoItem> findByIdentity(TodoIdentity identity);
    Optional<TodoItem> findById(long systemId, long tenantId,
                                long recipientMemberId, long itemId);
    List<TodoItem> findOpen(long systemId, long tenantId,
                            long recipientMemberId, TodoItem.SourceType sourceType);
    TodoPage findPage(long systemId, long tenantId, long recipientMemberId,
                      TodoQuery query);
    TodoCounts counts(long systemId, long tenantId, long recipientMemberId,
                      Instant todayStart, Instant tomorrowStart);
    TodoItem save(TodoItem item);

    TodoActionReservation reserveAction(TodoActionLog proposed);
    TodoActionLog completeAction(TodoActionLog completed);
    Optional<TodoActionLog> findAction(long systemId, long tenantId,
                                       long actorMemberId, String callerIdempotencyKey);
}
