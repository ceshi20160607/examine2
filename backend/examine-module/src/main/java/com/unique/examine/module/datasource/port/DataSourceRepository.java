package com.unique.examine.module.datasource.port;

import com.unique.examine.module.datasource.domain.DataSourceVersion;
import com.unique.examine.module.datasource.domain.ModuleDataSource;

import java.util.List;
import java.util.Optional;

public interface DataSourceRepository {
    long nextDataSourceId();

    long nextVersionId();

    Optional<ModuleDataSource> findById(
            long systemId,
            long tenantId,
            long dataSourceId);

    Optional<ModuleDataSource> findByCode(
            long systemId,
            long tenantId,
            String code);

    List<ModuleDataSource> findAll(long systemId, long tenantId);

    ModuleDataSource insert(ModuleDataSource root);

    ModuleDataSource saveDraft(
            ModuleDataSource expected,
            ModuleDataSource revised);

    DataSourceVersion publish(
            ModuleDataSource expected,
            ModuleDataSource activated,
            DataSourceVersion version);

    Optional<DataSourceVersion> findActiveVersion(
            long systemId,
            long tenantId,
            long dataSourceId);

    Optional<DataSourceVersion> findVersion(
            long systemId,
            long tenantId,
            long dataSourceId,
            int versionNumber);

    Optional<DataSourceVersion> findVersionById(
            long systemId,
            long tenantId,
            long dataSourceId,
            long versionId);

    List<DataSourceVersion> findVersions(
            long systemId,
            long tenantId,
            long dataSourceId);
}
