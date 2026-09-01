package com.unique.unexamine.print.manage;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class PrintModels {
    private PrintModels() {
    }

    public record Layout(
            @Size(max = 200) String header,
            @Size(max = 500) String footer,
            @Size(max = 200) String signatureLabel,
            @NotEmpty @Size(max = 40) List<@Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{0,99}") String> fieldCodes,
            @Size(max = 20) List<@Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{0,99}") String> detailFieldCodes,
            @NotNull @Min(4) @Max(24) Integer rowsPerPage) {
    }

    public record SaveTemplateRequest(
            @NotNull Long moduleId,
            @NotBlank @Pattern(regexp = "[a-z][a-z0-9_]{1,99}") String code,
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Pattern(regexp = "A4|A5") String pageSize,
            @NotBlank @Pattern(regexp = "PORTRAIT|LANDSCAPE") String orientation,
            @NotNull @Valid Layout layout,
            Integer expectedVersion) {
    }

    public record PreviewRequest(@NotNull Long sampleRecordId) {
    }

    public record PublishRequest(@NotNull Integer expectedDraftRevision) {
    }

    public record PrintRequest(Long templateVersionId) {
    }

    public record FieldOption(Long id, String code, String name, String fieldType) {
    }

    public record ModuleOption(Long id, String code, String name, Long publishedVersionId,
                               Integer publishedVersionNumber, List<FieldOption> fields) {
    }

    public record VersionView(Long id, Integer versionNumber, Integer draftRevision,
                              String snapshotHash, LocalDateTime publishedAt, boolean current) {
    }

    public record TemplateView(Long id, Long moduleId, String moduleCode, String moduleName,
                               String code, String name, String pageSize, String orientation,
                               Integer draftRevision, Layout layout, String status, Integer version,
                               Long currentVersionId, List<VersionView> versions,
                               LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    public record AdminOverview(List<ModuleOption> modules, List<TemplateView> templates) {
    }

    public record PageField(String code, String name, String value, boolean detail) {
    }

    public record PreviewPage(Integer pageNumber, Integer pageCount, String header,
                              String recordNumber, String recordTitle, List<PageField> fields,
                              String signatureLabel, String footer) {
    }

    public record Preview(Long templateId, Integer draftRevision, String previewHash,
                          Long moduleVersionId, Integer moduleVersionNumber,
                          Long sampleRecordId, List<String> visibleFieldCodes,
                          List<String> omittedFieldCodes, List<PreviewPage> pages,
                          LocalDateTime renderedAt) {
    }

    public record Publication(Long templateId, Long versionId, Integer versionNumber,
                              Integer draftRevision, String snapshotHash, Long moduleVersionId,
                              Integer moduleVersionNumber, LocalDateTime publishedAt) {
    }

    public record RuntimeTemplate(Long templateId, String code, String name, String pageSize,
                                  String orientation, Long versionId, Integer versionNumber,
                                  String snapshotHash, LocalDateTime publishedAt) {
    }

    public record PrintJobView(Long id, Long recordId, Long templateVersionId,
                               Integer templateVersionNumber, String templateName,
                               String status, Long outputFileId, String outputFileName,
                               String previewPath, String downloadPath, Integer pageCount,
                               List<PreviewPage> pages,
                               Map<String, Object> authorizationSnapshot,
                               Map<String, Object> recordSnapshot, String errorMessage,
                               LocalDateTime createdAt, LocalDateTime finishedAt) {
    }
}
