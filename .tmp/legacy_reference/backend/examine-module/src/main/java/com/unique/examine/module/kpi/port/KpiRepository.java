package com.unique.examine.module.kpi.port;

import com.unique.examine.module.kpi.domain.KpiCalculation;
import com.unique.examine.module.kpi.domain.KpiDefinition;
import com.unique.examine.module.kpi.domain.KpiPeriod;
import com.unique.examine.module.kpi.domain.KpiSubjectType;
import com.unique.examine.module.kpi.domain.KpiTarget;
import com.unique.examine.module.kpi.domain.KpiVersion;

import java.util.List;
import java.util.Optional;

public interface KpiRepository {
    long nextKpiId();

    long nextVersionId();

    long nextTargetId();

    long nextCalculationId();

    Optional<KpiDefinition> findById(
            long systemId, long tenantId, long kpiId);

    Optional<KpiDefinition> findByCode(
            long systemId, long tenantId, String code);

    List<KpiDefinition> findAll(long systemId, long tenantId);

    KpiDefinition insert(KpiDefinition root);

    KpiDefinition saveDraft(
            KpiDefinition expected, KpiDefinition revised);

    KpiVersion publish(
            KpiDefinition expected,
            KpiDefinition activated,
            KpiVersion version);

    Optional<KpiVersion> findActiveVersion(
            long systemId, long tenantId, long kpiId);

    Optional<KpiVersion> findVersionById(
            long systemId,
            long tenantId,
            long kpiId,
            long versionId);

    Optional<KpiVersion> findVersion(
            long systemId,
            long tenantId,
            long kpiId,
            int versionNumber);

    List<KpiVersion> findVersions(
            long systemId, long tenantId, long kpiId);

    Optional<KpiTarget> findTargetById(
            long systemId, long tenantId, long targetId);

    Optional<KpiTarget> findTargetByBusinessKey(
            long systemId,
            long tenantId,
            long kpiVersionId,
            KpiSubjectType subjectType,
            long subjectId,
            KpiPeriod period);

    List<KpiTarget> findTargets(long systemId, long tenantId);

    KpiTarget insertTarget(KpiTarget target);

    KpiTarget saveTarget(KpiTarget expected, KpiTarget revised);

    Optional<KpiCalculation> findCalculationByCommandKey(
            long systemId, long tenantId, String commandKey);

    Optional<KpiCalculation> findLatestCalculation(
            long systemId, long tenantId, long targetId);

    List<KpiCalculation> findCalculations(
            long systemId, long tenantId, long targetId);

    KpiCalculation insertCalculation(KpiCalculation calculation);
}
