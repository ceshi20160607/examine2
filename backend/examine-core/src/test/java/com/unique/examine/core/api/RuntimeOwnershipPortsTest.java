package com.unique.examine.core.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimeOwnershipPortsTest {
    @Test
    void activeMemberCarriesNullablePrimaryDepartment() {
        assertThat(new RuntimeActiveMemberFacade.ActiveMember(11, 21L).primaryDepartmentId())
                .isEqualTo(21L);
        assertThat(new RuntimeActiveMemberFacade.ActiveMember(11, null).primaryDepartmentId())
                .isNull();
    }

    @Test
    void ownershipPortRejectsInvalidCrossBoundaryIds() {
        assertThatThrownBy(() -> new RuntimeActiveMemberFacade.ActiveMember(0, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RuntimeActiveMemberFacade.ActiveMember(1, 0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RuntimeRecordTeamOwnershipFacade.TeamOwnership(
                0, 1, false, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RuntimeRecordTeamOwnershipFacade.TeamOwnership(
                1, 0, false, true))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
