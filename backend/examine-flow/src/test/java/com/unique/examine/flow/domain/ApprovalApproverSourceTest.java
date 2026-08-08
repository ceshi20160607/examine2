package com.unique.examine.flow.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ApprovalApproverSourceTest {

    @Test
    void organizationSourcesEnforceTheirFrozenSourceIdShapes() {
        assertThat(ApprovalApproverSource.departmentLeader(7))
                .isEqualTo(new ApprovalApproverSource(
                        ApprovalApproverSource.Kind.DEPARTMENT_LEADER,
                        7L
                ));
        assertThat(ApprovalApproverSource.requesterManager())
                .isEqualTo(new ApprovalApproverSource(
                        ApprovalApproverSource.Kind.REQUESTER_MANAGER,
                        null
                ));
        assertThat(ApprovalApproverSource.Kind.DEPARTMENT_LEADER.requiresSourceId())
                .isTrue();
        assertThat(ApprovalApproverSource.Kind.REQUESTER_MANAGER.requiresSourceId())
                .isFalse();

        assertThatIllegalArgumentException().isThrownBy(() ->
                new ApprovalApproverSource(
                        ApprovalApproverSource.Kind.DEPARTMENT_LEADER,
                        null
                ));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new ApprovalApproverSource(
                        ApprovalApproverSource.Kind.REQUESTER_MANAGER,
                        10L
                ));
    }

    @Test
    void recordMemberFieldRequiresModuleAndFieldWhileLegacyKindsRejectModule() {
        assertThat(ApprovalApproverSource.recordMemberField("work_order", 42))
                .isEqualTo(new ApprovalApproverSource(
                        ApprovalApproverSource.Kind.RECORD_MEMBER_FIELD,
                        42L,
                        "work_order"
                ));
        assertThat(ApprovalApproverSource.Kind.RECORD_MEMBER_FIELD.requiresSourceId())
                .isTrue();
        assertThat(ApprovalApproverSource.Kind.RECORD_MEMBER_FIELD.requiresModuleCode())
                .isTrue();

        assertThatIllegalArgumentException().isThrownBy(() ->
                new ApprovalApproverSource(
                        ApprovalApproverSource.Kind.RECORD_MEMBER_FIELD,
                        42L,
                        null
                ));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new ApprovalApproverSource(
                        ApprovalApproverSource.Kind.RECORD_MEMBER_FIELD,
                        0L,
                        "work_order"
                ));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new ApprovalApproverSource(
                        ApprovalApproverSource.Kind.ROLE,
                        7L,
                        "work_order"
                ));
    }
}
