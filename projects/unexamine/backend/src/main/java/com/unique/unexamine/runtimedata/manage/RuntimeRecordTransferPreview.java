package com.unique.unexamine.runtimedata.manage;

import java.util.List;

public record RuntimeRecordTransferPreview(
        Long recordId,
        Integer version,
        Long fromOwnerMemberId,
        String fromOwnerName,
        Long toOwnerMemberId,
        String toOwnerName,
        Long fromDepartmentId,
        String fromDepartmentName,
        Long toDepartmentId,
        String toDepartmentName,
        List<Long> fromParticipantMemberIds,
        List<Long> toParticipantMemberIds) {
}
