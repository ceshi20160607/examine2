package com.unique.examine.module.runtime.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Map;

@Component
public class RecordDraftExpiryWorker {
    private final JdbcTemplate jdbc;
    private final RecordMutationSupport mutations;
    private final int batchSize;

    public RecordDraftExpiryWorker(
            JdbcTemplate jdbc,
            RecordMutationSupport mutations,
            @Value("${examine.runtime.draft-expiry.batch-size:100}") int batchSize
    ) {
        this.jdbc = jdbc;
        this.mutations = mutations;
        this.batchSize = Math.max(1, Math.min(batchSize, 500));
    }

    @Scheduled(
            initialDelayString = "${examine.runtime.draft-expiry.initial-delay-ms:60000}",
            fixedDelayString = "${examine.runtime.draft-expiry.fixed-delay-ms:60000}"
    )
    @Transactional
    public int expireDueDrafts() {
        var now = LocalDateTime.now();
        var due = jdbc.query("SELECT system_id,tenant_id,record_id,schema_version_id,module_snapshot_id,version "
                        + "FROM un_module_record WHERE status='DRAFT' AND draft_expires_at<=? "
                        + "ORDER BY draft_expires_at,record_id LIMIT ? FOR UPDATE SKIP LOCKED",
                (result, row) -> expiryRecord(result), now, batchSize);
        var expired = 0;
        for (var record : due) {
            var updated = jdbc.update("UPDATE un_module_record SET status='EXPIRED',draft_expires_at=NULL,"
                            + "updated_at=?,version=version+1 WHERE system_id=? AND tenant_id=? AND record_id=? "
                            + "AND schema_version_id=? AND module_snapshot_id=? AND status='DRAFT' AND version=? "
                            + "AND draft_expires_at<=?",
                    now, record.systemId(), record.tenantId(), record.recordId(), record.schemaVersionId(),
                    record.moduleSnapshotId(), record.version(), now);
            if (updated != 1) {
                continue;
            }
            jdbc.update("UPDATE un_module_record_index SET record_status='EXPIRED',updated_at=? WHERE system_id=? "
                            + "AND tenant_id=? AND record_id=? AND schema_version_id=? AND module_snapshot_id=?",
                    now, record.systemId(), record.tenantId(), record.recordId(), record.schemaVersionId(),
                    record.moduleSnapshotId());
            jdbc.update("UPDATE un_module_record_search SET record_status='EXPIRED' WHERE system_id=? "
                            + "AND tenant_id=? AND record_id=? AND schema_version_id=? AND module_snapshot_id=?",
                    record.systemId(), record.tenantId(), record.recordId(), record.schemaVersionId(),
                    record.moduleSnapshotId());
            var nextVersion = record.version() + 1;
            var requestId = "expiry-worker-" + record.recordId();
            var traceId = "expiry-" + record.recordId() + "-v" + nextVersion;
            mutations.systemChanged(
                    record.systemId(), record.tenantId(), record.recordId(), nextVersion,
                    "RECORD_DRAFT_EXPIRED",
                    Map.of("status", "DRAFT", "version", record.version()),
                    Map.of("status", "EXPIRED", "version", nextVersion),
                    requestId, traceId
            );
            expired++;
        }
        return expired;
    }

    private static ExpiryRecord expiryRecord(ResultSet result) throws SQLException {
        return new ExpiryRecord(
                result.getLong("system_id"), result.getLong("tenant_id"), result.getLong("record_id"),
                result.getLong("schema_version_id"), result.getLong("module_snapshot_id"),
                result.getLong("version")
        );
    }

    private record ExpiryRecord(
            long systemId,
            long tenantId,
            long recordId,
            long schemaVersionId,
            long moduleSnapshotId,
            long version
    ) { }
}
