package com.unique.examine.module.runtime.importing;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

public final class ImportViews {
    private ImportViews() { }

    public record TemplateField(String fieldCode, String fieldName, String type, boolean required, boolean unique) { }
    public record ExcludedField(String fieldCode, String fieldName, String type, String reason) { }
    public record Template(String schemaVersionId, String moduleSnapshotId, String checksum,
                           List<TemplateField> fields, List<ExcludedField> excludedFields,
                           List<String> modes, int maxRows) { }

    public record PreviewRequest(String mode, String matchFieldCode, List<Map<String, JsonNode>> rows) {
        public PreviewRequest {
            rows = rows == null ? List.of() : List.copyOf(rows);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Row(int rowNumber, String action, String status, String errorCode, String errorMessage,
                      String targetRecordId, Long targetAfterVersion) { }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Batch(String batchId, String moduleCode, String schemaVersionId, String mode,
                        String matchFieldCode, String status, int totalRows, int newRows,
                        int updateRows, int failedRows, String previewJobId, String commitJobId,
                        String rollbackJobId, List<Row> rows) { }

    public record BatchPage(List<Batch> items, int page, int size, long total) { }
}
