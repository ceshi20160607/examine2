package com.unique.examine.plat.manage.service;

import com.unique.examine.core.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrganizationReportingPolicyTest {
    @Test
    void acceptsAnActiveDepartmentMemberAsLeader() {
        assertThatCode(() -> OrganizationReportingPolicy.requireDepartmentLeaderEligible(
                true,
                true,
                true
        )).doesNotThrowAnyException();
    }

    @Test
    void rejectsInactiveCrossTenantOrNonDepartmentLeader() {
        assertInvalid(() -> OrganizationReportingPolicy.requireDepartmentLeaderEligible(
                false,
                true,
                true
        ));
        assertInvalid(() -> OrganizationReportingPolicy.requireDepartmentLeaderEligible(
                true,
                false,
                true
        ));
        assertInvalid(() -> OrganizationReportingPolicy.requireDepartmentLeaderEligible(
                true,
                true,
                false
        ));
    }

    @Test
    void rejectsInactiveSubjectOrCrossTenantManager() {
        assertInvalid(() -> OrganizationReportingPolicy.requireManagerEligible(false, true));
        assertInvalid(() -> OrganizationReportingPolicy.requireManagerEligible(true, false));
    }

    @Test
    void detectsSelfAndCompleteReportingCycles() {
        assertInvalid(() -> OrganizationReportingPolicy.requireAcyclic(10, 10, ignored -> null));

        var directCycle = Map.of(20L, 10L);
        assertInvalid(() -> OrganizationReportingPolicy.requireAcyclic(
                10,
                20,
                directCycle::get
        ));

        var deepCycle = Map.of(20L, 30L, 30L, 40L, 40L, 10L);
        assertInvalid(() -> OrganizationReportingPolicy.requireAcyclic(
                10,
                20,
                deepCycle::get
        ));

        var corruptExistingCycle = Map.of(20L, 30L, 30L, 20L);
        assertInvalid(() -> OrganizationReportingPolicy.requireAcyclic(
                10,
                20,
                corruptExistingCycle::get
        ));
    }

    @Test
    void acceptsAnAcyclicReportingChain() {
        var chain = Map.of(20L, 30L, 30L, 40L);
        assertThatCode(() -> OrganizationReportingPolicy.requireAcyclic(
                10,
                20,
                chain::get
        )).doesNotThrowAnyException();
    }

    private static void assertInvalid(Runnable invocation) {
        assertThatThrownBy(invocation::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.code())
                                .isEqualTo("STATE_TRANSITION_INVALID"));
    }
}
