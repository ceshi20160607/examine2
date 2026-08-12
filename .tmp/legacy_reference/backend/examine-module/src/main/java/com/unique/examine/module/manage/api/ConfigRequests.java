package com.unique.examine.module.manage.api;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

import static com.unique.examine.module.manage.api.ConfigTypes.*;

public final class ConfigRequests {
    private ConfigRequests() {
    }

    public record CreateGroup(
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 128) String name,
            @Size(max = 500) String description,
            @Size(max = 64) String iconKey,
            @NotNull @Min(0) Integer sortOrder,
            @NotNull DesiredStatus status,
            @NotNull String draftRevision
    ) { }

    public record UpdateGroup(
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 128) String name,
            @Size(max = 500) String description,
            @Size(max = 64) String iconKey,
            @NotNull @Min(0) Integer sortOrder,
            @NotNull DesiredStatus status,
            @NotNull String version,
            @NotNull String draftRevision
    ) { }

    public record CreateModule(
            @NotBlank String groupId,
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 128) String name,
            @Size(max = 500) String description,
            @Size(max = 64) String iconKey,
            @NotNull @Min(0) Integer sortOrder,
            @NotNull DesiredStatus status,
            @NotNull Boolean allowComments,
            @NotNull Boolean allowTeam,
            @NotNull String draftRevision
    ) { }

    public record UpdateModule(
            @NotBlank String groupId,
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 128) String name,
            @Size(max = 500) String description,
            @Size(max = 64) String iconKey,
            @NotNull @Min(0) Integer sortOrder,
            @NotNull DesiredStatus status,
            @NotNull Boolean allowComments,
            @NotNull Boolean allowTeam,
            @NotNull String version,
            @NotNull String draftRevision
    ) { }

    public record CopyModule(
            @NotBlank String groupId,
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 128) String name,
            @NotNull String sourceVersion,
            @NotNull String draftRevision
    ) { }

    public record SortItem(@NotBlank String id, @NotNull String version, @NotNull @Min(0) Integer sortOrder) { }

    public record SortResources(@NotNull @Size(min = 1, max = 500) List<@Valid SortItem> items,
                                @NotNull String draftRevision) { }

    public record DeleteResource(@NotNull String version, @NotNull String draftRevision) { }

    public record RunCheck(@NotNull String draftRevision) { }

    public record PublishConfig(
            @NotBlank String checkId,
            @NotNull String draftRevision,
            @NotNull String configRootVersion,
            @NotBlank @Size(max = 500) String reason
    ) { }

    public record RollbackConfig(
            @NotNull String configRootVersion,
            @NotBlank @Size(max = 500) String reason
    ) { }

    /** Restores one immutable publication into a new editable draft. */
    public record RestoreConfig(
            @NotNull String expectedVersion,
            @NotBlank @Size(max = 500) String reason
    ) { }

    public record CreateDictionary(
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 128) String name,
            @NotNull DictionaryType type,
            @Size(max = 64) String category,
            @Size(max = 500) String description,
            @NotNull DesiredStatus status,
            @NotNull String draftRevision
    ) { }

    public record UpdateDictionary(
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 128) String name,
            @NotNull DictionaryType type,
            @Size(max = 64) String category,
            @Size(max = 500) String description,
            @NotNull DesiredStatus status,
            @NotNull String version,
            @NotNull String draftRevision
    ) { }

    public record CreateDictionaryItem(
            String parentId,
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 128) String label,
            @Size(max = 32) String semanticKey,
            @Size(max = 32) String color,
            @Size(max = 64) String iconKey,
            @NotNull @Min(0) Integer sortOrder,
            @NotNull Boolean isDefault,
            @NotNull DesiredStatus status,
            @NotNull String draftRevision
    ) { }

    public record UpdateDictionaryItem(
            String parentId,
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 128) String label,
            @Size(max = 32) String semanticKey,
            @Size(max = 32) String color,
            @Size(max = 64) String iconKey,
            @NotNull @Min(0) Integer sortOrder,
            @NotNull Boolean isDefault,
            @NotNull DesiredStatus status,
            @NotNull String version,
            @NotNull String draftRevision
    ) { }

    public record CreateField(
            String dictionaryId,
            String targetModuleId,
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 128) String name,
            @NotNull FieldType type,
            @NotNull @Min(0) Integer sortOrder,
            @NotNull Boolean required,
            @NotNull Boolean hidden,
            @NotNull Boolean readonly,
            @NotNull Boolean searchable,
            @NotNull Boolean filterable,
            @NotNull Boolean showInList,
            @NotNull Boolean showInDetail,
            @NotNull IndexMode indexMode,
            @NotNull DesiredStatus status,
            @NotNull JsonNode properties,
            FieldPermissionMode readPermissionMode,
            FieldPermissionMode writePermissionMode,
            @NotNull String draftRevision
    ) { }

    public record UpdateField(
            String dictionaryId,
            String targetModuleId,
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 128) String name,
            @NotNull FieldType type,
            @NotNull @Min(0) Integer sortOrder,
            @NotNull Boolean required,
            @NotNull Boolean hidden,
            @NotNull Boolean readonly,
            @NotNull Boolean searchable,
            @NotNull Boolean filterable,
            @NotNull Boolean showInList,
            @NotNull Boolean showInDetail,
            @NotNull IndexMode indexMode,
            @NotNull DesiredStatus status,
            @NotNull JsonNode properties,
            FieldPermissionMode readPermissionMode,
            FieldPermissionMode writePermissionMode,
            @NotNull String version,
            @NotNull String draftRevision
    ) { }

    public record CreatePage(
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 128) String name,
            @NotNull PageType type,
            @NotNull Boolean isDefault,
            @NotNull DesiredStatus status,
            @NotNull JsonNode layout,
            @NotNull String draftRevision
    ) { }

    public record UpdatePage(
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 128) String name,
            @NotNull PageType type,
            @NotNull Boolean isDefault,
            @NotNull DesiredStatus status,
            @NotNull JsonNode layout,
            @NotNull String version,
            @NotNull String draftRevision
    ) { }

    public record CreateComponent(
            String parentComponentId,
            String fieldId,
            @NotBlank @Size(max = 64) String key,
            @NotNull ComponentType type,
            @NotNull @Min(0) Integer sortOrder,
            @NotNull @Min(0) Integer gridRow,
            @NotNull @Min(0) @Max(23) Integer gridColumn,
            @NotNull @Min(1) @Max(24) Integer gridSpan,
            @NotNull JsonNode properties,
            @NotNull String draftRevision
    ) { }

    public record UpdateComponent(
            String parentComponentId,
            String fieldId,
            @NotBlank @Size(max = 64) String key,
            @NotNull ComponentType type,
            @NotNull @Min(0) Integer sortOrder,
            @NotNull @Min(0) Integer gridRow,
            @NotNull @Min(0) @Max(23) Integer gridColumn,
            @NotNull @Min(1) @Max(24) Integer gridSpan,
            @NotNull JsonNode properties,
            @NotNull String version,
            @NotNull String draftRevision
    ) { }

    public record CreateAction(
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 128) String name,
            @NotNull ActionType type,
            @NotNull ActionPlacement placement,
            @Size(max = 300) String confirmMessage,
            @NotNull @Min(0) Integer sortOrder,
            @NotNull DesiredStatus status,
            @NotNull JsonNode properties,
            @NotNull String draftRevision
    ) { }

    public record UpdateAction(
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 128) String name,
            @NotNull ActionType type,
            @NotNull ActionPlacement placement,
            @Size(max = 300) String confirmMessage,
            @NotNull @Min(0) Integer sortOrder,
            @NotNull DesiredStatus status,
            @NotNull JsonNode properties,
            @NotNull String version,
            @NotNull String draftRevision
    ) { }

    public record Condition(
            ConditionJoin join,
            String fieldId,
            ConditionOperator operator,
            JsonNode value,
            List<@Valid Condition> children
    ) { }

    public record Effect(@NotNull RuleEffect effect, String targetId, JsonNode value) { }

    public record CreateRule(
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 128) String name,
            @NotNull RuleType type,
            @NotNull @Min(0) @Max(10000) Integer priority,
            @NotNull @Valid Condition condition,
            @NotNull @Size(min = 1, max = 20) List<@Valid Effect> effects,
            @NotNull DesiredStatus status,
            @NotNull String draftRevision
    ) { }

    public record UpdateRule(
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 128) String name,
            @NotNull RuleType type,
            @NotNull @Min(0) @Max(10000) Integer priority,
            @NotNull @Valid Condition condition,
            @NotNull @Size(min = 1, max = 20) List<@Valid Effect> effects,
            @NotNull DesiredStatus status,
            @NotNull String version,
            @NotNull String draftRevision
    ) { }
}
