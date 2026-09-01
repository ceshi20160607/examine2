package com.unique.unexamine.analytics.manage;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class ReportModels {
    private ReportModels() {
    }

    public record FieldMetadata(String code, String name, String fieldType, boolean searchable,
                                boolean readable, boolean indexed, boolean builtIn) {
    }

    public record ModuleMetadata(Long moduleId, String moduleCode, String moduleName,
                                 Long versionId, Integer versionNumber, List<FieldMetadata> fields) {
    }

    public record Metadata(List<ModuleMetadata> modules, List<String> metricOperations,
                           List<String> dimensionTypes, List<String> relationTypes,
                           int maximumModules, int maximumScanRows, boolean arbitrarySqlAllowed) {
    }

    public record Issue(String code, String message, String path) {
    }

    public record QueryPlan(String mode, List<Map<String, Object>> modules,
                            List<Map<String, Object>> relations, List<String> outputFields,
                            Map<String, Object> metric, Map<String, Object> dimension,
                            List<Map<String, Object>> fixedFilters, Map<String, Object> sort,
                            Map<String, Object> timeField, long estimatedRows, int maximumScanRows,
                            List<String> steps, List<String> permissionFilters,
                            boolean arbitrarySqlAllowed) {
    }

    public record Group(Object key, String label, Object value, long rowCount) {
    }

    public record Result(String outcome, Object value, List<Group> groups,
                         List<Map<String, Object>> items, String metricDefinition,
                         List<String> sourceFields, List<String> permissionFilters,
                         QueryPlan queryPlan, Long dataSourceVersionId,
                         Integer dataSourceVersionNumber, String definitionHash,
                         LocalDateTime updatedAt) {
    }

    public record Preview(Long dataSourceId, Integer draftRevision, boolean valid,
                          List<Issue> issues, QueryPlan queryPlan, Result sampleResult) {
    }
}
