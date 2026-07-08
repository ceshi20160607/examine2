package com.unique.examine.module.manage.config;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Page designer API request and response models.
 */
public final class ModulePageDesignModels {

    private ModulePageDesignModels() {
    }

    public record PageDesignerSaveRequest(String pageCode, String pageName, String pageType, String route,
                                          String layoutMode, List<PageComponentConfig> components,
                                          List<String> visibleRoleIds, String changeReason) {
    }

    public record PageDesignerVO(String pageId, String systemId, String tenantId, String moduleId,
                                 String moduleCode, String pageCode, String pageName, String pageType,
                                 String route, String layoutMode, List<PageComponentConfig> components,
                                 List<String> visibleRoleIds, String publishStatus, String publishedVersion,
                                 String permissionSnapshotId, String traceId, LocalDateTime updatedAt) {
    }

    public record PageSchemaVO(String pageId, String systemId, String tenantId, String moduleId,
                               String moduleCode, String pageCode, String pageName, String pageType,
                               String route, String layoutMode, String publishStatus, String schemaSource,
                               String schemaVersion, List<PageComponentConfig> components,
                               List<PageSchemaFieldVO> fields, String permissionSnapshotId,
                               String permissionSnapshotVersion, String traceId, LocalDateTime updatedAt) {
    }

    public record PageSchemaFieldVO(String fieldId, String fieldCode, String name, String fieldType,
                                    String storageType, boolean required, boolean sortable,
                                    String permissionMode, boolean writable, boolean readonly,
                                    boolean visible, String maskRule, Map<String, Object> validationRules,
                                    String disabledReason, Integer sortOrder) {
    }

    public record PageComponentConfig(String componentCode, String componentType, String title,
                                      String dataSource, String boundFieldCode, Integer sort, Boolean visible,
                                      Map<String, Object> props) {
    }
}
