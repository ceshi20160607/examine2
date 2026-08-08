package com.unique.examine.module.runtime.printing;

import java.time.LocalDateTime;
import java.util.List;

public final class PrintViews {
    private PrintViews() { }

    public record TemplateDefinition(
            String header,
            String title,
            List<String> fieldCodes,
            String footer,
            List<PositionedElement> positionedElements,
            List<CodeBlock> codeBlocks
    ) {
        public TemplateDefinition {
            fieldCodes = fieldCodes == null ? List.of() : List.copyOf(fieldCodes);
            positionedElements = positionedElements == null ? List.of() : List.copyOf(positionedElements);
            codeBlocks = codeBlocks == null ? List.of() : List.copyOf(codeBlocks);
        }
        public TemplateDefinition(String title, List<String> fieldCodes, String footer) {
            this("", title, fieldCodes, footer, List.of(), List.of());
        }
    }

    public record PositionedElement(
            String kind,
            String label,
            String fieldCode,
            double xMm,
            double yMm,
            double widthMm,
            double heightMm
    ) { }

    public record CodeBlock(String kind, String label, String fieldCode) { }

    public record CreateTemplateRequest(
            String code,
            String name,
            String status,
            String paperSize,
            String orientation,
            String title,
            List<String> fieldCodes,
            String footer,
            String header,
            List<PositionedElement> positionedElements,
            List<CodeBlock> codeBlocks
    ) {
        public CreateTemplateRequest {
            fieldCodes = fieldCodes == null ? List.of() : List.copyOf(fieldCodes);
            positionedElements = positionedElements == null ? List.of() : List.copyOf(positionedElements);
            codeBlocks = codeBlocks == null ? List.of() : List.copyOf(codeBlocks);
        }
        public CreateTemplateRequest(String code, String name, String status, String paperSize, String orientation,
                                     String title, List<String> fieldCodes, String footer) {
            this(code, name, status, paperSize, orientation, title, fieldCodes, footer, "", List.of(), List.of());
        }
    }

    public record UpdateTemplateRequest(
            String name,
            String status,
            String paperSize,
            String orientation,
            String title,
            List<String> fieldCodes,
            String footer,
            String header,
            List<PositionedElement> positionedElements,
            List<CodeBlock> codeBlocks,
            long expectedVersion
    ) {
        public UpdateTemplateRequest {
            fieldCodes = fieldCodes == null ? List.of() : List.copyOf(fieldCodes);
            positionedElements = positionedElements == null ? List.of() : List.copyOf(positionedElements);
            codeBlocks = codeBlocks == null ? List.of() : List.copyOf(codeBlocks);
        }
        public UpdateTemplateRequest(String name, String status, String paperSize, String orientation, String title,
                                     List<String> fieldCodes, String footer, long expectedVersion) {
            this(name, status, paperSize, orientation, title, fieldCodes, footer, "", List.of(), List.of(),
                    expectedVersion);
        }
    }

    public record PublishTemplateRequest(long expectedVersion) { }

    public record Template(
            String templateId,
            String moduleId,
            String moduleCode,
            String code,
            String name,
            String status,
            String paperSize,
            String orientation,
            TemplateDefinition definition,
            String publishedVersionId,
            Long publishedVersionNo,
            String publishedSchemaVersionId,
            long version,
            LocalDateTime updatedAt
    ) { }

    public record RuntimeTemplate(
            String templateId,
            String code,
            String name,
            String paperSize,
            String orientation,
            String templateVersionId,
            long templateVersionNo,
            String schemaVersionId
    ) { }

    public record PreviewRequest(String templateCode) { }

    public record Preview(
            String templateCode,
            String templateVersionId,
            long templateVersionNo,
            String recordId,
            long recordVersion,
            String html
    ) { }

    public record CreatePrintRequest(String templateCode, long expectedRecordVersion) { }

    public record Task(
            String printId,
            String recordId,
            long recordVersion,
            String recordNo,
            String templateCode,
            String templateName,
            String templateVersionId,
            long templateVersionNo,
            String schemaVersionId,
            String status,
            String jobId,
            String resultFilename,
            Long resultSize,
            String failureCode,
            String failureMessage,
            LocalDateTime createdAt,
            LocalDateTime startedAt,
            LocalDateTime finishedAt
    ) { }

    public record TaskPage(List<Task> items, int page, int size, long total) {
        public TaskPage { items = List.copyOf(items); }
    }

    public record Snapshot(
            String header,
            String title,
            String subtitle,
            String footer,
            String paperSize,
            String orientation,
            List<Line> lines,
            List<Table> tables,
            List<ResolvedPositionedElement> positionedElements,
            List<ResolvedCodeBlock> codeBlocks
    ) {
        public Snapshot {
            lines = lines == null ? List.of() : List.copyOf(lines);
            tables = tables == null ? List.of() : List.copyOf(tables);
            positionedElements = positionedElements == null ? List.of() : List.copyOf(positionedElements);
            codeBlocks = codeBlocks == null ? List.of() : List.copyOf(codeBlocks);
        }
        public Snapshot(String title, String subtitle, String footer, String paperSize, String orientation,
                        List<Line> lines) {
            this("", title, subtitle, footer, paperSize, orientation, lines, List.of(), List.of(), List.of());
        }
    }

    public record Line(String label, String fieldCode, String value) { }
    public record ResolvedPositionedElement(
            String kind, String label, String fieldCode,
            double xMm, double yMm, double widthMm, double heightMm,
            String imageDataUri, String contentSha256
    ) { }
    public record Table(String label, String fieldCode, List<String> columns, List<List<String>> rows) {
        public Table {
            columns = columns == null ? List.of() : List.copyOf(columns);
            rows = rows == null ? List.of() : rows.stream().map(List::copyOf).toList();
        }
    }
    public record ResolvedCodeBlock(String kind, String label, String value) { }
}
