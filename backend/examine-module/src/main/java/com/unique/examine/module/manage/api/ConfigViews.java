package com.unique.examine.module.manage.api;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

import static com.unique.examine.module.manage.api.ConfigTypes.*;

public final class ConfigViews {
    private ConfigViews() {
    }

    public record CheckSummary(String id, String status, int blockerCount, int warningCount,
                               String draftRevision, String version) { }

    public record CheckIssue(String id, String severity, String code, String resourceType, String resourceId,
                             String propertyPath, String message, String suggestedAction) { }

    public record CheckReport(String id, String status, int blockerCount, int warningCount,
                              String draftRevision, String draftChecksum, long snapshotSizeBytes,
                              String expiresAt, List<CheckIssue> issues, String version) { }

    public record ConfigVersion(String id, String versionNo, String sourceType, String basedOnVersionId,
                                String rollbackTargetVersionId, String snapshotChecksum, long snapshotSizeBytes,
                                String publishedAt, String publishedBy, String reason, boolean active) { }

    public record PublishResult(ConfigVersion version, RootSummary root, long authzEpoch) { }

    public record VersionDiff(String fromVersionId, String toVersionId, JsonNode changes) { }

    public record RootSummary(String systemId, String status, String draftRevision, String activeVersionId,
                              String baseVersionId, String version, CheckSummary lastCheck) { }

    public record Group(String id, String code, String name, String description, String iconKey,
                        int sortOrder, DesiredStatus status, String version, String updatedRevision) { }

    public record Module(String id, String groupId, String code, String name, String description,
                         String iconKey, int sortOrder, DesiredStatus status, boolean allowComments,
                         boolean allowTeam, String version, String updatedRevision) { }

    public record Dictionary(String id, String code, String name, DictionaryType type, String category,
                             String description, DesiredStatus status, String version, String updatedRevision) { }

    public record DictionaryItem(String id, String dictionaryId, String parentId, String code, String label,
                                 String semanticKey, String color, String iconKey, int sortOrder, int depth,
                                 boolean isDefault, DesiredStatus status, String version, String updatedRevision,
                                 List<DictionaryItem> children) { }

    public record Field(String id, String moduleId, String dictionaryId, String targetModuleId, String code,
                        String name, FieldType type, int sortOrder, boolean required, boolean hidden,
                        boolean readonly, boolean searchable, boolean filterable, boolean showInList,
                        boolean showInDetail, IndexMode indexMode, DesiredStatus status, JsonNode properties,
                        FieldPermissionMode readPermissionMode, FieldPermissionMode writePermissionMode,
                        String version, String updatedRevision) { }

    public record Page(String id, String moduleId, String code, String name, PageType type, boolean isDefault,
                       DesiredStatus status, JsonNode layout, String version, String updatedRevision) { }

    public record Component(String id, String pageId, String parentComponentId, String fieldId, String key,
                            ComponentType type, int sortOrder, int gridRow, int gridColumn, int gridSpan,
                            JsonNode properties, String version, String updatedRevision) { }

    public record Action(String id, String moduleId, String code, String name, ActionType type,
                         ActionPlacement placement, String permissionCode, String confirmMessage, int sortOrder,
                         DesiredStatus status, JsonNode properties, String version, String updatedRevision) { }

    public record Rule(String id, String moduleId, String code, String name, RuleType type, int priority,
                       ConfigRequests.Condition condition, List<ConfigRequests.Effect> effects,
                       DesiredStatus status, String version, String updatedRevision) { }

    public record RevisionResult(String draftRevision) { }
}
