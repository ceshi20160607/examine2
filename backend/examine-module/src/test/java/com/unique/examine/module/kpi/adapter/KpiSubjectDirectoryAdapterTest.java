package com.unique.examine.module.kpi.adapter;

import com.unique.examine.core.api.KpiSubjectDirectoryFacade;
import com.unique.examine.module.kpi.domain.KpiSubjectType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KpiSubjectDirectoryAdapterTest {
    @Test
    void mapsActiveRoleSnapshotAndHidesMissingSubjects() {
        var directory = new FakeDirectory();
        var adapter = new KpiSubjectDirectoryAdapter(directory);

        var role = adapter.resolve(
                1, 2, KpiSubjectType.ROLE, 3).orElseThrow();
        assertThat(role.displayName()).isEqualTo("Operators");
        assertThat(role.activeMemberIds()).containsExactly(10L, 30L);

        directory.active = false;
        assertThat(adapter.resolve(
                1, 2, KpiSubjectType.ROLE, 3)).isEmpty();
    }

    @Test
    void mapsCurrentDepartmentAndRoleMemberships() {
        var adapter = new KpiSubjectDirectoryAdapter(new FakeDirectory());

        var membership = adapter.currentMembership(1, 2, 9);

        assertThat(membership.memberActive()).isTrue();
        assertThat(membership.departmentIds()).containsExactly(4L, 8L);
        assertThat(membership.roleIds()).containsExactly(5L, 7L);
    }

    private static final class FakeDirectory
            implements KpiSubjectDirectoryFacade {
        private boolean active = true;

        @Override
        public SubjectResolution resolve(
                long systemId,
                long tenantId,
                SubjectType subjectType,
                long subjectId
        ) {
            return active ? SubjectResolution.active(
                    subjectType, subjectId, "Operators",
                    List.of(30L, 10L, 30L))
                    : SubjectResolution.missing(subjectType, subjectId);
        }

        @Override
        public CurrentMembership currentMembership(
                long systemId,
                long tenantId,
                long memberId
        ) {
            return CurrentMembership.active(
                    List.of(8L, 4L), List.of(7L, 5L));
        }
    }
}
