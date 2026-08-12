package com.unique.examine.module.datasource.domain;

public record PublishedDataSource(
        ModuleDataSource root,
        DataSourceVersion version
) {
    public PublishedDataSource {
        if (root == null || version == null
                || root.id() != version.dataSourceId()
                || root.systemId() != version.systemId()
                || root.tenantId() != version.tenantId()
                || !root.code().equals(version.code())
                || root.moduleId() != version.moduleId()
                || root.activeVersionId() == null
                || root.activeVersionId() != version.id()
                || root.activeVersionNumber() == null
                || root.activeVersionNumber() != version.versionNumber()) {
            throw new IllegalArgumentException(
                    "Published data source active pointer is inconsistent");
        }
    }
}
