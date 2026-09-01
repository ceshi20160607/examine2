package com.unique.unexamine.moduleconfig.manage;

import com.fasterxml.jackson.databind.JsonNode;
import com.unique.unexamine.moduleconfig.base.entity.CfgDictionary;
import com.unique.unexamine.moduleconfig.base.entity.CfgDictionaryItem;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class DictionaryModels {
    private DictionaryModels() {
    }

    public record CreateDictionaryRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{1,99}") String code,
            @NotBlank @Size(max = 200) String name,
            @NotNull Boolean hierarchical) {
    }

    public record CreateItemRequest(
            Long parentId,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_.-]{0,99}") String code,
            @NotBlank @Size(max = 200) String label,
            @Size(max = 32) String color,
            @Min(0) Integer sortOrder) {
    }

    public record UpdateItemRequest(
            Long parentId,
            @NotBlank @Size(max = 200) String label,
            @Size(max = 32) String color,
            @Min(0) Integer sortOrder,
            @NotBlank @Pattern(regexp = "ACTIVE|DISABLED") String status,
            @NotNull @Min(0) Integer version) {
    }

    public record DictionaryDraft(
            CfgDictionary dictionary,
            List<CfgDictionaryItem> items,
            Long currentVersionId,
            Integer currentVersionNumber,
            Integer publicationVersion) {
    }

    public record PublicationIssue(String path, String code, String message) {
    }

    public record PublicationCheck(boolean valid, int draftRevision, List<PublicationIssue> issues) {
    }

    public record PublishDictionaryRequest(@NotNull @Min(0) Integer expectedDraftRevision) {
    }

    public record PublishedDictionary(
            Long dictionaryId,
            Long versionId,
            int versionNumber,
            int publicationVersion,
            JsonNode snapshot) {
    }

    public record VersionSummary(
            Long versionId,
            int versionNumber,
            int draftRevision,
            LocalDateTime publishedAt,
            boolean current,
            int publicationVersion) {
    }

    public record RuntimeDictionary(
            Long dictionaryId,
            String code,
            String name,
            boolean hierarchical,
            Long versionId,
            int versionNumber,
            int publicationVersion,
            List<JsonNode> items) {
    }
}
