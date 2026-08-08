package com.unique.examine.flow.service;

import com.unique.examine.core.api.RuntimeActiveMemberFacade;
import com.unique.examine.core.api.RuntimeApproverDirectoryFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.runtime.RuntimeRecordMemberFieldFacade;
import com.unique.examine.flow.domain.ApprovalApproverSource;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

final class ApprovalApproverSourceResolver {
    private final RuntimeApproverDirectoryFacade directory;
    private final RuntimeRecordMemberFieldFacade recordMemberFields;
    private final RuntimeActiveMemberFacade activeMembers;

    ApprovalApproverSourceResolver(RuntimeApproverDirectoryFacade directory) {
        this(
                directory, unsupportedRecordMemberFields(),
                unsupportedActiveMembers());
    }

    ApprovalApproverSourceResolver(
            RuntimeApproverDirectoryFacade directory,
            RuntimeActiveMemberFacade activeMembers
    ) {
        this(directory, unsupportedRecordMemberFields(), activeMembers);
    }

    ApprovalApproverSourceResolver(
            RuntimeApproverDirectoryFacade directory,
            RuntimeRecordMemberFieldFacade recordMemberFields
    ) {
        this(directory, recordMemberFields, unsupportedActiveMembers());
    }

    ApprovalApproverSourceResolver(
            RuntimeApproverDirectoryFacade directory,
            RuntimeRecordMemberFieldFacade recordMemberFields,
            RuntimeActiveMemberFacade activeMembers
    ) {
        this.directory = Objects.requireNonNull(directory, "directory");
        this.recordMemberFields = Objects.requireNonNull(
                recordMemberFields, "recordMemberFields");
        this.activeMembers = Objects.requireNonNull(activeMembers, "activeMembers");
    }

    Resolution resolve(
            long systemId,
            long tenantId,
            ApprovalApproverSource source,
            List<Long> fixedMemberIds
    ) {
        return resolve(systemId, tenantId, source, fixedMemberIds, null, null);
    }

    Resolution resolve(
            long systemId,
            long tenantId,
            ApprovalApproverSource source,
            List<Long> fixedMemberIds,
            Long requesterMemberId
    ) {
        return resolve(
                systemId, tenantId, source, fixedMemberIds,
                requesterMemberId, null);
    }

    Resolution resolve(
            long systemId,
            long tenantId,
            ApprovalApproverSource source,
            List<Long> fixedMemberIds,
            Long requesterMemberId,
            RecordContext recordContext
    ) {
        Objects.requireNonNull(source, "source");
        return switch (source.kind()) {
            case FIXED -> new Resolution(true, List.copyOf(fixedMemberIds));
            case ROLE -> from(directory.resolveRoleMembers(
                    systemId, tenantId, source.sourceId()));
            case DEPARTMENT -> from(directory.resolveDepartmentMembers(
                    systemId, tenantId, source.sourceId()));
            case DEPARTMENT_LEADER -> from(directory.resolveDepartmentLeader(
                    systemId, tenantId, source.sourceId()));
            case REQUESTER -> resolveRequester(
                    systemId, tenantId, requesterMemberId);
            case REQUESTER_MANAGER -> requesterMemberId == null || requesterMemberId <= 0
                    ? new Resolution(false, List.of())
                    : from(directory.resolveRequesterManager(
                            systemId, tenantId, requesterMemberId));
            case REQUESTER_DEPARTMENT_LEADER ->
                    resolveRequesterDepartmentLeader(
                            systemId, tenantId, requesterMemberId);
            case RECORD_MEMBER_FIELD -> resolveRecordMemberField(
                    systemId, tenantId, source, recordContext);
            case PREVIOUS_HANDLER -> new Resolution(false, List.of());
        };
    }

    List<Long> requireMembers(
            long systemId,
            long tenantId,
            ApprovalApproverSource source,
            List<Long> fixedMemberIds
    ) {
        return requireMembers(
                systemId, tenantId, source, fixedMemberIds, null, null);
    }

    List<Long> requireMembers(
            long systemId,
            long tenantId,
            ApprovalApproverSource source,
            List<Long> fixedMemberIds,
            Long requesterMemberId
    ) {
        return requireMembers(
                systemId, tenantId, source, fixedMemberIds,
                requesterMemberId, null);
    }

    List<Long> requireMembers(
            long systemId,
            long tenantId,
            ApprovalApproverSource source,
            List<Long> fixedMemberIds,
            Long requesterMemberId,
            RecordContext recordContext
    ) {
        var resolution = resolve(
                systemId, tenantId, source, fixedMemberIds,
                requesterMemberId, recordContext);
        var sourceDescription = description(source, requesterMemberId);
        if (!resolution.sourceActive()) {
            throw new BusinessException(
                    "FLOW_APPROVER_SOURCE_INACTIVE",
                    sourceDescription
                            + " is missing or inactive in the current system and tenant",
                    HttpStatus.UNPROCESSABLE_ENTITY
            );
        }
        if (resolution.memberIds().isEmpty()) {
            throw new BusinessException(
                    "FLOW_APPROVER_SOURCE_EMPTY",
                    sourceDescription
                            + " has no active members in the current system and tenant",
                    HttpStatus.UNPROCESSABLE_ENTITY
            );
        }
        if (resolution.memberIds().size() > 10) {
            throw new BusinessException(
                    "FLOW_APPROVER_SOURCE_TOO_LARGE",
                    sourceDescription
                            + " has more than 10 active members",
                    HttpStatus.UNPROCESSABLE_ENTITY
            );
        }
        return resolution.memberIds();
    }

    private static String description(
            ApprovalApproverSource source,
            Long requesterMemberId
    ) {
        return switch (source.kind()) {
            case FIXED -> "FIXED approver source";
            case REQUESTER -> "REQUESTER approver source for requester "
                    + (requesterMemberId == null ? "<missing>" : requesterMemberId);
            case REQUESTER_MANAGER -> "REQUESTER_MANAGER approver source for requester "
                    + (requesterMemberId == null ? "<missing>" : requesterMemberId);
            case REQUESTER_DEPARTMENT_LEADER ->
                    "REQUESTER_DEPARTMENT_LEADER approver source for requester "
                            + (requesterMemberId == null
                                    ? "<missing>"
                                    : requesterMemberId);
            case ROLE, DEPARTMENT, DEPARTMENT_LEADER ->
                    source.kind() + " approver source " + source.sourceId();
            case RECORD_MEMBER_FIELD -> "RECORD_MEMBER_FIELD approver source "
                    + source.moduleCode() + "/" + source.sourceId();
            case PREVIOUS_HANDLER -> "PREVIOUS_HANDLER approver source";
        };
    }

    private Resolution resolveRequester(
            long systemId,
            long tenantId,
            Long requesterMemberId
    ) {
        if (requesterMemberId == null || requesterMemberId <= 0) {
            return new Resolution(false, List.of());
        }
        return activeMembers.lockActiveMember(
                        systemId, tenantId, requesterMemberId)
                .map(member -> new Resolution(true, List.of(member.memberId())))
                .orElseGet(() -> new Resolution(false, List.of()));
    }

    private Resolution resolveRequesterDepartmentLeader(
            long systemId,
            long tenantId,
            Long requesterMemberId
    ) {
        if (requesterMemberId == null || requesterMemberId <= 0) {
            return new Resolution(false, List.of());
        }
        return activeMembers.lockActiveMember(
                        systemId, tenantId, requesterMemberId)
                .filter(member -> member.primaryDepartmentId() != null)
                .map(member -> from(directory.resolveDepartmentLeader(
                        systemId, tenantId, member.primaryDepartmentId())))
                .orElseGet(() -> new Resolution(false, List.of()));
    }

    boolean publishedSourceActive(long systemId, ApprovalApproverSource source) {
        if (source.kind() != ApprovalApproverSource.Kind.RECORD_MEMBER_FIELD) {
            throw new IllegalArgumentException("Record member field source is required");
        }
        return recordMemberFields.publishedEligibleFields(
                        systemId, source.moduleCode())
                .stream()
                .flatMap(catalog -> catalog.fields().stream())
                .anyMatch(field -> field.fieldId() == source.sourceId());
    }

    Optional<RuntimeRecordMemberFieldFacade.PublishedFieldCatalog> catalog(
            long systemId,
            String moduleCode
    ) {
        return recordMemberFields.publishedEligibleFields(systemId, moduleCode);
    }

    private Resolution resolveRecordMemberField(
            long systemId,
            long tenantId,
            ApprovalApproverSource source,
            RecordContext context
    ) {
        if (context == null || !source.moduleCode().equals(context.moduleCode())) {
            return new Resolution(false, List.of());
        }
        RuntimeRecordMemberFieldFacade.Resolution resolved =
                context.currentRecordId() == null
                        ? recordMemberFields.resolveSnapshot(
                                new RuntimeRecordMemberFieldFacade.SnapshotRequest(
                                        systemId,
                                        tenantId,
                                        source.moduleCode(),
                                        source.sourceId(),
                                        context.valuesJson()
                                ))
                        : recordMemberFields.resolveCurrent(
                                new RuntimeRecordMemberFieldFacade.CurrentRecordRequest(
                                        systemId,
                                        tenantId,
                                        source.moduleCode(),
                                        context.currentRecordId(),
                                        source.sourceId()
                                ));
        return switch (resolved.status()) {
            case RESOLVED -> new Resolution(true, List.of(resolved.memberId()));
            case SOURCE_EMPTY -> new Resolution(true, List.of());
            case SOURCE_MISSING, SOURCE_INVALID, MEMBER_INACTIVE ->
                    new Resolution(false, List.of());
        };
    }

    private static Resolution from(RuntimeApproverDirectoryFacade.Resolution value) {
        return new Resolution(value.sourceActive(), value.memberIds());
    }

    record Resolution(boolean sourceActive, List<Long> memberIds) {
        Resolution {
            memberIds = List.copyOf(memberIds);
        }
    }

    record RecordContext(
            String moduleCode,
            Long currentRecordId,
            Map<String, String> valuesJson
    ) {
        RecordContext {
            if (moduleCode == null
                    || !moduleCode.matches("^[A-Za-z][A-Za-z0-9_]{0,63}$")) {
                throw new IllegalArgumentException("Record context moduleCode is invalid");
            }
            if (currentRecordId != null && currentRecordId <= 0) {
                throw new IllegalArgumentException("Current record ID must be positive");
            }
            valuesJson = valuesJson == null ? Map.of() : Map.copyOf(valuesJson);
        }

        static RecordContext current(String moduleCode, long recordId) {
            return new RecordContext(moduleCode, recordId, Map.of());
        }

        static RecordContext snapshot(String moduleCode, Map<String, String> valuesJson) {
            return new RecordContext(moduleCode, null, valuesJson);
        }
    }

    private static RuntimeRecordMemberFieldFacade unsupportedRecordMemberFields() {
        return new RuntimeRecordMemberFieldFacade() {
            @Override
            public Optional<PublishedFieldCatalog> publishedEligibleFields(
                    long systemId,
                    String moduleCode
            ) {
                return Optional.empty();
            }

            @Override
            public RuntimeRecordMemberFieldFacade.Resolution resolveCurrent(
                    CurrentRecordRequest request
            ) {
                return RuntimeRecordMemberFieldFacade.Resolution.sourceMissing();
            }

            @Override
            public RuntimeRecordMemberFieldFacade.Resolution resolveSnapshot(
                    SnapshotRequest request
            ) {
                return RuntimeRecordMemberFieldFacade.Resolution.sourceMissing();
            }
        };
    }

    private static RuntimeActiveMemberFacade unsupportedActiveMembers() {
        return (systemId, tenantId, memberId) -> Optional.empty();
    }
}
