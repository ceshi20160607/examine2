package com.unique.examine.module.runtime.history;

import com.unique.examine.core.id.IdService;
import com.unique.examine.module.runtime.security.RuntimeSession;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Set;

@Component
@Transactional(propagation = Propagation.MANDATORY)
public class RecordHistoryWriter {
    private final RecordHistoryRepository repository;
    private final RecordHistoryDiffCodec diffCodec;
    private final IdService ids;

    public RecordHistoryWriter(
            RecordHistoryRepository repository,
            RecordHistoryDiffCodec diffCodec,
            IdService ids
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.diffCodec = Objects.requireNonNull(diffCodec, "diffCodec");
        this.ids = Objects.requireNonNull(ids, "ids");
    }

    public String append(
            RuntimeSession session,
            long recordId,
            long recordVersion,
            String action,
            Object before,
            Object after
    ) {
        return append(session, recordId, recordVersion, action, before, after, Set.of());
    }

    public String append(
            RuntimeSession session,
            long recordId,
            long recordVersion,
            String action,
            Object before,
            Object after,
            Set<String> forceMaskedFields
    ) {
        Objects.requireNonNull(session, "session");
        return repository.append(new RecordHistoryAppend(
                ids.nextId(),
                session.systemId(),
                requiredTenant(session),
                recordId,
                recordVersion,
                action,
                session.memberId(),
                LocalDateTime.now(),
                diffCodec.changed(before, after, Set.copyOf(forceMaskedFields))));
    }

    public String appendField(
            RuntimeSession session,
            long recordId,
            long recordVersion,
            String action,
            String fieldCode,
            Object before,
            Object after
    ) {
        Objects.requireNonNull(session, "session");
        return repository.append(new RecordHistoryAppend(
                ids.nextId(),
                session.systemId(),
                requiredTenant(session),
                recordId,
                recordVersion,
                action,
                session.memberId(),
                LocalDateTime.now(),
                diffCodec.changedField(fieldCode, before, after, false)));
    }

    public String appendFlowField(
            long systemId,
            long tenantId,
            long actorMemberId,
            long recordId,
            long recordVersion,
            Instant occurredAt,
            String fieldCode,
            Object before,
            Object after
    ) {
        return repository.append(new RecordHistoryAppend(
                ids.nextId(),
                systemId,
                tenantId,
                recordId,
                recordVersion,
                "FLOW_STATUS_MAPPED",
                actorMemberId,
                LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC),
                diffCodec.changedField(fieldCode, before, after, false)));
    }

    public String appendSystem(
            long systemId,
            long tenantId,
            long recordId,
            long recordVersion,
            String action,
            Object before,
            Object after
    ) {
        return repository.append(new RecordHistoryAppend(
                ids.nextId(),
                systemId,
                tenantId,
                recordId,
                recordVersion,
                action,
                null,
                LocalDateTime.now(),
                diffCodec.changed(before, after)));
    }

    private static long requiredTenant(RuntimeSession session) {
        if (session.tenantId() == null || session.tenantId() <= 0) {
            throw new IllegalArgumentException("Record history requires a tenant");
        }
        return session.tenantId();
    }
}
