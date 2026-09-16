package com.unique.unexamine.runtimedata.manage;

import java.util.List;

/** 转移前后归属及关联预览；关联记录的负责人和归属不随本记录转移。 */
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
        List<Long> toParticipantMemberIds,
        List<String> fromParticipantNames,
        List<String> toParticipantNames,
        long outgoingRelationCount,
        long incomingRelationCount) {
}
