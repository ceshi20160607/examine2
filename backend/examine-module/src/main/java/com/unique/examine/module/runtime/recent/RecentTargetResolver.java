package com.unique.examine.module.runtime.recent;

import com.unique.examine.module.runtime.security.RuntimeSession;
import com.unique.examine.module.runtime.service.RecordRuntimeService;
import org.springframework.stereotype.Component;

@Component
public class RecentTargetResolver {
    private final RecordRuntimeService records;

    public RecentTargetResolver(RecordRuntimeService records) {
        this.records = records;
    }

    public ResolvedTarget resolve(RuntimeSession session, String moduleCode, long recordId) {
        var schema = records.schema(session, moduleCode);
        var detail = records.detail(session, moduleCode, recordId);
        return new ResolvedTarget(
                moduleCode,
                Long.parseLong(schema.logicalModuleId()),
                Long.parseLong(schema.schemaVersionId()),
                Long.parseLong(schema.moduleSnapshotId()),
                recordId,
                Long.parseLong(detail.schemaVersionId()),
                Long.parseLong(schema.moduleSnapshotId()),
                recordLabel(detail.recordNo(), detail.title()),
                detail.status());
    }

    private static String recordLabel(String recordNo, String title) {
        if (title == null || title.isBlank() || title.equals(recordNo)) {
            return recordNo;
        }
        return recordNo + " " + title;
    }

    public record ResolvedTarget(
            String moduleCode,
            long logicalModuleId,
            long moduleSchemaVersionId,
            long moduleSnapshotId,
            long recordId,
            long recordSchemaVersionId,
            long recordModuleSnapshotId,
            String displayLabel,
            String status
    ) {
    }
}
