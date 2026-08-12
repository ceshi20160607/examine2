package com.unique.examine.module.kpi.adapter;

import com.unique.examine.core.api.KpiSubjectDirectoryFacade;
import com.unique.examine.module.kpi.domain.KpiSubjectType;
import com.unique.examine.module.kpi.port.KpiSubjectDirectory;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

/** Maps the platform-owned facade into the KPI-owned directory port. */
@Component
public final class KpiSubjectDirectoryAdapter
        implements KpiSubjectDirectory {
    private final KpiSubjectDirectoryFacade directory;

    public KpiSubjectDirectoryAdapter(KpiSubjectDirectoryFacade directory) {
        this.directory = Objects.requireNonNull(directory, "directory");
    }

    @Override
    public Optional<SubjectResolution> resolve(
            long systemId,
            long tenantId,
            KpiSubjectType subjectType,
            long subjectId
    ) {
        Objects.requireNonNull(subjectType, "subjectType");
        var resolved = directory.resolve(
                systemId, tenantId,
                KpiSubjectDirectoryFacade.SubjectType.valueOf(
                        subjectType.name()),
                subjectId);
        if (!resolved.active()) {
            return Optional.empty();
        }
        return Optional.of(new SubjectResolution(
                subjectType, resolved.subjectId(), true,
                resolved.displayName(), resolved.activeMemberIds()));
    }

    @Override
    public CurrentMembership currentMembership(
            long systemId,
            long tenantId,
            long memberId
    ) {
        var resolved = directory.currentMembership(
                systemId, tenantId, memberId);
        return new CurrentMembership(
                resolved.memberActive(), resolved.departmentIds(),
                resolved.roleIds());
    }
}
