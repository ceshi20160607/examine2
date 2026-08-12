package com.unique.examine.module.datasource.domain;

/**
 * A specific immutable publication of a data source.
 *
 * <p>Unlike {@link PublishedDataSource}, this value intentionally does not
 * require the version to be the root's current active version. Consumers that
 * pin configuration at their own publish boundary can therefore keep reading
 * the exact historical source version they validated.</p>
 */
public record DataSourcePublication(
        ModuleDataSource root,
        DataSourceVersion version
) {
    public DataSourcePublication {
        if (root == null || version == null
                || root.id() != version.dataSourceId()
                || root.systemId() != version.systemId()
                || root.tenantId() != version.tenantId()
                || !root.code().equals(version.code())
                || root.moduleId() != version.moduleId()) {
            throw new IllegalArgumentException(
                    "Data source publication identity is inconsistent");
        }
    }
}
