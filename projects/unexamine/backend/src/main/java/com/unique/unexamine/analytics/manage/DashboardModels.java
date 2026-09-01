package com.unique.unexamine.analytics.manage;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class DashboardModels {
    private DashboardModels() {
    }

    public record DataSourceRequest(
            @NotBlank @Pattern(regexp = "[a-z][a-z0-9_-]{1,99}") String code,
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Pattern(regexp = "PLATFORM_SYSTEMS|MODULE_RECORDS|MODULE_REPORT|TODO_ITEMS|MESSAGE_ITEMS|WORK_PROJECTS") String sourceType,
            @NotNull Map<String, Object> definition,
            @NotNull Map<String, Object> permissionPolicy,
            Integer expectedVersion) {
    }

    public record PublishRequest(@NotNull @Min(1) Integer expectedDraftRevision) {
    }

    public record ComponentInput(
            @NotBlank @Pattern(regexp = "[a-z][a-z0-9_-]{1,99}") String componentKey,
            @NotBlank @Pattern(regexp = "METRIC|CHART|LIST|TODO|QUICK_ENTRY|KPI|PROGRESS|RANKING") String componentType,
            @NotBlank @Size(max = 200) String title,
            Long dataSourceId,
            @NotNull Map<String, Object> layout,
            @NotNull Map<String, Object> queryParameters,
            @NotNull Map<String, Object> displayConfig,
            Map<String, Object> drillTarget,
            @NotNull @Min(0) @Max(1000) Integer sortOrder) {
    }

    public record DashboardRequest(
            @NotBlank @Pattern(regexp = "[a-z][a-z0-9_-]{1,99}") String code,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 1000) String description,
            @NotNull @Size(min = 1, max = 40) List<@Valid ComponentInput> components,
            Integer expectedVersion) {
    }

    public record DataSourceVersionView(Long id, Integer versionNumber, Integer draftRevision,
                                        String definitionHash, LocalDateTime publishedAt) {
    }

    public record DataSourceView(Long id, String contextType, String code, String name, String sourceType,
                                 Integer draftRevision, Map<String, Object> definition,
                                 Map<String, Object> permissionPolicy, String status, Integer version,
                                 LocalDateTime updatedAt, List<DataSourceVersionView> versions) {
    }

    public record ComponentView(Long id, String componentKey, String componentType, String title,
                                Long dataSourceId, Map<String, Object> layout,
                                Map<String, Object> queryParameters, Map<String, Object> displayConfig,
                                Map<String, Object> drillTarget, Integer sortOrder, Integer version) {
    }

    public record DashboardVersionView(Long id, Integer versionNumber, Integer draftRevision,
                                       String snapshotHash, LocalDateTime publishedAt, boolean current) {
    }

    public record DashboardView(Long id, String contextType, String code, String name, String description,
                                Integer draftRevision, String status, Integer version, LocalDateTime updatedAt,
                                List<ComponentView> components, List<DashboardVersionView> versions) {
    }

    public record AdminOverview(String contextType, List<DataSourceView> dataSources,
                                List<DashboardView> dashboards) {
    }

    public record Issue(String code, String message, String componentKey) {
    }

    public record RuntimeComponent(String componentKey, String componentType, String title,
                                   String outcome, Object value, List<Map<String, Object>> items,
                                   String metricDefinition, Map<String, Object> layout,
                                   Map<String, Object> displayConfig, Map<String, Object> drillTarget,
                                   boolean drillAvailable, String errorCode, String message,
                                   LocalDateTime updatedAt) {
    }

    public record Preview(Long dashboardId, Integer draftRevision, boolean valid, List<Issue> issues,
                          List<RuntimeComponent> components) {
    }

    public record PublishResult(Long dashboardId, Long versionId, Integer versionNumber,
                                Integer draftRevision, String snapshotHash, Preview preview) {
    }

    public record RuntimeDashboard(boolean configured, String contextType, Long dashboardId,
                                   Long versionId, Integer versionNumber, String name, String description,
                                   LocalDateTime publishedAt, List<RuntimeComponent> components,
                                   String message) {
    }
}
