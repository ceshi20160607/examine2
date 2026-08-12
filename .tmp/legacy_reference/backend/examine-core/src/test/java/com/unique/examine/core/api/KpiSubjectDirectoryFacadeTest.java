package com.unique.examine.core.api;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KpiSubjectDirectoryFacadeTest {
    @Test
    void roleAndMembershipSnapshotsAreCanonical() {
        var role = KpiSubjectDirectoryFacade.SubjectResolution.active(
                KpiSubjectDirectoryFacade.SubjectType.ROLE,
                9, "  Sales  ", List.of(30L, 10L, 30L, 20L));
        var membership = KpiSubjectDirectoryFacade.CurrentMembership.active(
                List.of(8L, 3L, 8L), List.of(11L, 4L, 11L));

        assertThat(role.displayName()).isEqualTo("Sales");
        assertThat(role.activeMemberIds()).containsExactly(10L, 20L, 30L);
        assertThat(membership.departmentIds()).containsExactly(3L, 8L);
        assertThat(membership.roleIds()).containsExactly(4L, 11L);
    }

    @Test
    void missingSubjectsAndMembersNeverLeakNamesOrMemberships() {
        var missing = new KpiSubjectDirectoryFacade.SubjectResolution(
                KpiSubjectDirectoryFacade.SubjectType.ROLE,
                9, false, "hidden", List.of(10L));

        assertThat(missing.displayName()).isNull();
        assertThat(missing.activeMemberIds()).isEmpty();
        assertThatThrownBy(() -> new KpiSubjectDirectoryFacade.CurrentMembership(
                false, List.of(1L), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void onlyRoleSubjectsCanExposeExpandedMembers() {
        assertThatThrownBy(() ->
                KpiSubjectDirectoryFacade.SubjectResolution.active(
                        KpiSubjectDirectoryFacade.SubjectType.MEMBER,
                        7, "Ada", List.of(7L)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
