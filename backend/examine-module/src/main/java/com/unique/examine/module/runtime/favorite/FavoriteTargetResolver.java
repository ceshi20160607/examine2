package com.unique.examine.module.runtime.favorite;

import com.unique.examine.module.runtime.security.RuntimeSession;
import com.unique.examine.module.runtime.service.RecordRuntimeService;
import org.springframework.stereotype.Component;

@Component
public class FavoriteTargetResolver {
    private final RecordRuntimeService records;

    public FavoriteTargetResolver(RecordRuntimeService records) {
        this.records = records;
    }

    public ResolvedTarget resolve(RuntimeSession session, String type, String moduleCode, Long recordId) {
        var schema = records.schema(session, moduleCode);
        if ("MODULE".equals(type)) {
            return new ResolvedTarget(
                    type,
                    moduleCode,
                    Long.parseLong(schema.logicalModuleId()),
                    Long.parseLong(schema.schemaVersionId()),
                    Long.parseLong(schema.moduleSnapshotId()),
                    null,
                    null,
                    null,
                    moduleCode,
                    null);
        }
        var detail = records.detail(session, moduleCode, recordId);
        return new ResolvedTarget(
                type,
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
            String type,
            String moduleCode,
            long logicalModuleId,
            long moduleSchemaVersionId,
            long moduleSnapshotId,
            Long recordId,
            Long recordSchemaVersionId,
            Long recordModuleSnapshotId,
            String displayLabel,
            String status
    ) {
    }
}
