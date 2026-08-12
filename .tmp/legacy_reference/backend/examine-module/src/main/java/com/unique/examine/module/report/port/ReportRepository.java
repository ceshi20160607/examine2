package com.unique.examine.module.report.port;

import com.unique.examine.module.report.domain.ReportDefinition;
import com.unique.examine.module.report.domain.ReportVersion;

import java.util.List;
import java.util.Optional;

public interface ReportRepository {
    long nextReportId();

    long nextVersionId();

    Optional<ReportDefinition> findById(
            long systemId, long tenantId, long reportId);

    Optional<ReportDefinition> findByCode(
            long systemId, long tenantId, String code);

    List<ReportDefinition> findAll(long systemId, long tenantId);

    ReportDefinition insert(ReportDefinition root);

    ReportDefinition saveDraft(
            ReportDefinition expected, ReportDefinition revised);

    ReportVersion publish(
            ReportDefinition expected,
            ReportDefinition activated,
            ReportVersion version);

    Optional<ReportVersion> findActiveVersion(
            long systemId, long tenantId, long reportId);

    Optional<ReportVersion> findVersion(
            long systemId,
            long tenantId,
            long reportId,
            int versionNumber);

    Optional<ReportVersion> findVersionById(
            long systemId,
            long tenantId,
            long reportId,
            long versionId);

    List<ReportVersion> findVersions(
            long systemId, long tenantId, long reportId);
}
