package com.unique.examine.module.manage.config;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Module configuration API request and response models.
 */
public final class ModuleConfigModels {

    private ModuleConfigModels() {
    }

    public record ModuleGroupSaveRequest(String name, Integer sort, List<String> visibleRoleIds,
                                         String publishStatus) {
    }

    public record ModuleGroupVO(String groupId, String systemId, String tenantId, String name, Integer sort,
                                List<String> visibleRoleIds, String publishStatus, String publishedVersion,
                                LocalDateTime updatedAt) {
    }

    public record ModuleQueryRequest(String groupId, Integer status, String publishStatus, String keyword) {
    }

    public record ModuleSaveRequest(String groupId, String moduleCode, String name, Integer status,
                                    String description) {
    }

    public record ModuleVO(String moduleId, String systemId, String tenantId, String groupId, String moduleCode,
                           String name, Integer status, String publishStatus, String currentVersion,
                           RowDetailTarget rowDetailTarget, ModuleNavigationMeta navigation,
                           LocalDateTime updatedAt) {
    }

    public record ModuleNavigationMeta(String topGroupName, String leftMenuName, String route,
                                       List<String> visibleRoleIds, boolean runtimeVisible) {
    }

    public record FieldQueryRequest(String fieldType, Integer status, String keyword) {
    }

    public record FieldSaveRequest(String fieldCode, String name, String fieldType, String storageType,
                                   Boolean required, Boolean sortable, String dictTypeId,
                                   FieldPermissionMetadata permissionMetadata, String maskRule,
                                   ImportExportRule importExportRule) {
    }

    public record FieldDefinitionVO(String fieldId, String moduleId, String fieldCode, String name,
                                    String fieldType, String storageType, List<String> filterOperators,
                                    boolean sortable, boolean required, Integer status, String dictTypeId,
                                    FieldPermissionMetadata permissionMetadata, String maskRule,
                                    ImportExportRule importExportRule, ColumnMeta columnMeta) {
    }

    public record FieldPermissionMetadata(String readablePermissionCode, String writablePermissionCode,
                                          List<String> readableRoleIds, List<String> writableRoleIds,
                                          boolean runtimeReadable, boolean runtimeWritable,
                                          String maskedWhenDenied, String permissionVersion) {
    }

    public record ImportExportRule(boolean importable, boolean exportable, boolean requiredOnImport,
                                   String duplicateKey, String desensitizeMode) {
    }

    public record ColumnMeta(Integer width, boolean visibleDefault, boolean configurable, boolean fixed,
                             String align) {
    }

    public record DictTypeQueryRequest(String dictKind, Integer status, String keyword) {
    }

    public record DictTypeSaveRequest(String dictCode, String dictName, String dictKind, Integer status) {
    }

    public record DictTypeVO(String dictTypeId, String systemId, String tenantId, String dictCode,
                             String dictName, String dictKind, Integer status, String publishedVersion,
                             List<DictItemVO> previewItems, LocalDateTime updatedAt) {
    }

    public record DictItemSaveRequest(String parentId, String itemCode, String itemName, String color,
                                      String icon, String semantic, Integer sort, Boolean defaultFlag,
                                      Boolean kanbanEnabled, Integer status) {
    }

    public record DictItemVO(String itemId, String dictTypeId, String parentId, String itemCode, String itemName,
                             String color, String icon, String semantic, Integer sort, boolean defaultFlag,
                             boolean kanbanEnabled, Integer status, String disabledReason) {
    }

    public record SceneSaveRequest(String sceneCode, String sceneName, Boolean defaultScene,
                                   List<String> visibleRoleIds, List<String> columnFieldIds,
                                   List<String> filterFieldIds, List<String> sortFieldIds) {
    }

    public record SceneSchemaVO(String sceneId, String moduleId, String sceneCode, String sceneName,
                                boolean defaultScene, List<String> visibleRoleIds,
                                DynamicListSchema listSchema, List<DetailSectionMeta> detailSections,
                                LocalDateTime updatedAt) {
    }

    public record DynamicListSchema(String moduleId, String moduleCode, String sceneId, String sceneCode,
                                    List<ColumnSchema> columns, List<FilterSchema> filters,
                                    List<SortSchema> sorters, PageMeta page, RowDetailTarget rowClickTarget,
                                    List<ActionConfigVO> batchActions, List<ActionConfigVO> toolbarActions,
                                    ImportExportConfigVO importExportConfig, EmptyStateVO emptyState,
                                    String permissionSnapshotId, List<PrintTemplateVO> printTemplates) {
    }

    public record ColumnSchema(String fieldId, String fieldCode, String label, Integer width,
                               boolean visibleDefault, boolean configurable, boolean fixed, String align,
                               String permissionMode, String maskRule) {
    }

    public record FilterSchema(String fieldId, String fieldCode, String label, String fieldType,
                               List<String> operators, boolean advanced, boolean quickFilter,
                               List<DictItemVO> options, String permissionMode) {
    }

    public record SortSchema(String fieldId, String fieldCode, String label, String defaultDirection,
                             boolean defaultSort, boolean supported) {
    }

    public record PageMeta(int defaultPageSize, List<Integer> allowedPageSizes, boolean serverSide,
                           boolean cursorSupported) {
    }

    public record RowDetailTarget(String targetType, String route, String drawerCode,
                                  boolean preserveListContext) {
    }

    public record EmptyStateVO(String title, String description, String primaryActionCode,
                               String disabledReason) {
    }

    public record DetailSectionMeta(String sectionCode, String sectionName, List<String> fieldCodes,
                                    String permissionCode, boolean visibleDefault) {
    }

    public record ActionSaveRequest(String actionCode, String actionName, String actionType, String position,
                                    SelectionRule selectionRule, String permissionCode,
                                    ResultContract resultContract, Boolean enabled) {
    }

    public record ActionConfigVO(String actionCode, String actionName, String actionType, String position,
                                 SelectionRule selectionRule, String permissionCode,
                                 ResultContract resultContract, boolean enabled, String disabledReason,
                                 boolean idempotencyRequired) {
    }

    public record SelectionRule(String selectionMode, Integer minSelected, Integer maxSelected,
                                List<String> requiredStatuses, boolean sameTenantRequired,
                                String forbiddenReason) {
    }

    public record ResultContract(String resultType, boolean returnsAuditLog, boolean returnsAsyncTask,
                                 String resultDrawer, String traceField, List<String> userVisibleStates) {
    }

    public record PermissionBindingVO(String bindingId, String targetType, String targetId,
                                      String permissionCode, List<String> roleIds, String effect,
                                      String dataScopeExpression, List<FieldPermissionMetadata> fieldPermissions,
                                      String permissionVersion) {
    }

    public record ImportExportConfigSaveRequest(Boolean importSupported, Boolean exportSupported,
                                                List<ImportTemplateMeta> importTemplates,
                                                List<ExportTemplateMeta> exportTemplates,
                                                List<FieldMappingMeta> fieldMappings,
                                                List<String> duplicateStrategies,
                                                List<String> supportedFormats) {
    }

    public record ImportExportConfigVO(String configId, String moduleId, boolean importSupported,
                                       boolean exportSupported, List<ImportTemplateMeta> importTemplates,
                                       List<ExportTemplateMeta> exportTemplates,
                                       List<FieldMappingMeta> fieldMappings, List<String> duplicateStrategies,
                                       List<String> supportedFormats, boolean precheckRequired,
                                       boolean executionOwnedByRuntime, String executionTaskOwner) {
    }

    public record ImportTemplateMeta(String templateCode, String templateName, String fileId, String version,
                                     List<String> requiredFieldCodes) {
    }

    public record ExportTemplateMeta(String templateCode, String templateName, String fileFormat,
                                     String desensitizeMode, List<String> defaultFieldCodes) {
    }

    public record FieldMappingMeta(String sourceColumn, String fieldCode, boolean required,
                                   String transformRule) {
    }

    public record PrintTemplateSaveRequest(String templateCode, String templateName, String version,
                                           Integer status, Boolean defaultTemplate, List<String> visibleRoleIds,
                                           List<String> boundFieldCodes, String previewFileId) {
    }

    public record PrintTemplateVO(String templateId, String moduleId, String templateCode, String templateName,
                                  String version, Integer status, boolean defaultTemplate,
                                  List<String> visibleRoleIds, List<String> boundFieldCodes,
                                  String previewFileId, String publishStatus) {
    }

    public record PublishRequest(String reason, String idempotencyKey) {
    }

    public record PublishCheckResultVO(boolean passed, List<PublishCheckItem> failureItems,
                                       List<PublishCheckItem> warningItems, List<ImpactRef> impactRefs,
                                       String traceId) {
    }

    public record PublishCheckItem(String itemCode, String itemName, String severity, String objectType,
                                   String objectId, String message, String fixAction) {
    }

    public record ImpactRef(String objectType, String objectId, String name, String impactType) {
    }

    public record PublishResult(String result, String targetId, String version, String traceId,
                                String auditLogId, String asyncTaskId, LocalDateTime operatedAt) {
    }
}
