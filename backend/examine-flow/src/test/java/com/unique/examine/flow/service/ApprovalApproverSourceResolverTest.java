package com.unique.examine.flow.service;

import com.unique.examine.core.api.RuntimeApproverDirectoryFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.runtime.RuntimeRecordMemberFieldFacade;
import com.unique.examine.flow.domain.ApprovalApproverSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class ApprovalApproverSourceResolverTest {

    @Test
    void resolvesLeaderByDepartmentAndManagerByRequesterContext() {
        var calls = new java.util.ArrayList<String>();
        var resolver = new ApprovalApproverSourceResolver(
                new RuntimeApproverDirectoryFacade() {
                    @Override
                    public Resolution resolveRoleMembers(
                            long systemId,
                            long tenantId,
                            long roleId
                    ) {
                        return Resolution.missing();
                    }

                    @Override
                    public Resolution resolveDepartmentMembers(
                            long systemId,
                            long tenantId,
                            long departmentId
                    ) {
                        return Resolution.missing();
                    }

                    @Override
                    public Resolution resolveDepartmentLeader(
                            long systemId,
                            long tenantId,
                            long departmentId
                    ) {
                        calls.add("leader:" + systemId + ":" + tenantId + ":" + departmentId);
                        return Resolution.active(List.of(30L));
                    }

                    @Override
                    public Resolution resolveRequesterManager(
                            long systemId,
                            long tenantId,
                            long requesterMemberId
                    ) {
                        calls.add("manager:" + systemId + ":" + tenantId
                                + ":" + requesterMemberId);
                        return Resolution.active(List.of(40L));
                    }
                }
        );

        assertThat(resolver.requireMembers(
                99, 1, ApprovalApproverSource.departmentLeader(7), List.of(), 10L
        )).containsExactly(30L);
        assertThat(resolver.requireMembers(
                99, 1, ApprovalApproverSource.requesterManager(), List.of(), 20L
        )).containsExactly(40L);
        assertThat(calls).containsExactly(
                "leader:99:1:7",
                "manager:99:1:20"
        );
    }

    @Test
    void missingRequesterContextAndEmptyOrganizationRelationshipsUseStableErrors() {
        var resolver = new ApprovalApproverSourceResolver(
                new RuntimeApproverDirectoryFacade() {
                    @Override
                    public Resolution resolveRoleMembers(
                            long systemId,
                            long tenantId,
                            long roleId
                    ) {
                        return Resolution.missing();
                    }

                    @Override
                    public Resolution resolveDepartmentMembers(
                            long systemId,
                            long tenantId,
                            long departmentId
                    ) {
                        return Resolution.missing();
                    }

                    @Override
                    public Resolution resolveDepartmentLeader(
                            long systemId,
                            long tenantId,
                            long departmentId
                    ) {
                        return Resolution.active(List.of());
                    }

                    @Override
                    public Resolution resolveRequesterManager(
                            long systemId,
                            long tenantId,
                            long requesterMemberId
                    ) {
                        return Resolution.active(List.of());
                    }
                }
        );

        var noContext = catchThrowableOfType(
                () -> resolver.requireMembers(
                        99,
                        1,
                        ApprovalApproverSource.requesterManager(),
                        List.of()
                ),
                BusinessException.class
        );
        var noLeader = catchThrowableOfType(
                () -> resolver.requireMembers(
                        99,
                        1,
                        ApprovalApproverSource.departmentLeader(7),
                        List.of(),
                        10L
                ),
                BusinessException.class
        );
        var noManager = catchThrowableOfType(
                () -> resolver.requireMembers(
                        99,
                        1,
                        ApprovalApproverSource.requesterManager(),
                        List.of(),
                        10L
                ),
                BusinessException.class
        );

        assertThat(noContext.code()).isEqualTo("FLOW_APPROVER_SOURCE_INACTIVE");
        assertThat(noLeader.code()).isEqualTo("FLOW_APPROVER_SOURCE_EMPTY");
        assertThat(noManager.code()).isEqualTo("FLOW_APPROVER_SOURCE_EMPTY");
    }

    @Test
    void resolvesCurrentAndSnapshotRecordMembersWithoutMixingContexts() {
        var calls = new java.util.ArrayList<String>();
        var resolver = new ApprovalApproverSourceResolver(
                missingDirectory(),
                new RuntimeRecordMemberFieldFacade() {
                    @Override
                    public Optional<PublishedFieldCatalog> publishedEligibleFields(
                            long systemId,
                            String moduleCode
                    ) {
                        return Optional.of(new PublishedFieldCatalog(
                                systemId, 1, 2, 3, moduleCode,
                                List.of(new EligibleField(42, "owner", "Owner"))
                        ));
                    }

                    @Override
                    public Resolution resolveCurrent(CurrentRecordRequest request) {
                        calls.add("current:" + request.moduleCode() + ":"
                                + request.recordId() + ":" + request.fieldId());
                        return Resolution.resolved(701);
                    }

                    @Override
                    public Resolution resolveSnapshot(SnapshotRequest request) {
                        calls.add("snapshot:" + request.moduleCode() + ":"
                                + request.valuesJson().get("owner"));
                        return Resolution.resolved(702);
                    }
                }
        );
        var source = ApprovalApproverSource.recordMemberField("work_order", 42);

        assertThat(resolver.requireMembers(
                11, 22, source, List.of(), 10L,
                ApprovalApproverSourceResolver.RecordContext.current(
                        "work_order", 99)
        )).containsExactly(701L);
        assertThat(resolver.requireMembers(
                11, 22, source, List.of(), 10L,
                ApprovalApproverSourceResolver.RecordContext.snapshot(
                        "work_order", Map.of("owner", "702"))
        )).containsExactly(702L);
        assertThat(calls).containsExactly(
                "current:work_order:99:42",
                "snapshot:work_order:702"
        );
    }

    @Test
    void recordMemberEmptyAndInvalidUseStableExistingErrors() {
        var source = ApprovalApproverSource.recordMemberField("work_order", 42);
        var empty = resolverFor(RuntimeRecordMemberFieldFacade.Resolution.sourceEmpty());
        var invalid = resolverFor(RuntimeRecordMemberFieldFacade.Resolution.sourceInvalid());
        var context = ApprovalApproverSourceResolver.RecordContext.snapshot(
                "work_order", Map.of());

        var emptyFailure = catchThrowableOfType(
                () -> empty.requireMembers(
                        11, 22, source, List.of(), 10L, context),
                BusinessException.class
        );
        var invalidFailure = catchThrowableOfType(
                () -> invalid.requireMembers(
                        11, 22, source, List.of(), 10L, context),
                BusinessException.class
        );

        assertThat(emptyFailure.code()).isEqualTo("FLOW_APPROVER_SOURCE_EMPTY");
        assertThat(invalidFailure.code()).isEqualTo("FLOW_APPROVER_SOURCE_INACTIVE");
    }

    private static ApprovalApproverSourceResolver resolverFor(
            RuntimeRecordMemberFieldFacade.Resolution result
    ) {
        return new ApprovalApproverSourceResolver(
                missingDirectory(),
                new RuntimeRecordMemberFieldFacade() {
                    @Override
                    public Optional<PublishedFieldCatalog> publishedEligibleFields(
                            long systemId,
                            String moduleCode
                    ) {
                        return Optional.empty();
                    }

                    @Override
                    public Resolution resolveCurrent(CurrentRecordRequest request) {
                        return result;
                    }

                    @Override
                    public Resolution resolveSnapshot(SnapshotRequest request) {
                        return result;
                    }
                }
        );
    }

    private static RuntimeApproverDirectoryFacade missingDirectory() {
        return new RuntimeApproverDirectoryFacade() {
            @Override
            public Resolution resolveRoleMembers(
                    long systemId,
                    long tenantId,
                    long roleId
            ) {
                return Resolution.missing();
            }

            @Override
            public Resolution resolveDepartmentMembers(
                    long systemId,
                    long tenantId,
                    long departmentId
            ) {
                return Resolution.missing();
            }
        };
    }
}
