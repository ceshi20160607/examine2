package com.unique.unexamine.runtimedata.manage;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;

public record RuntimeRecordView(
        Long id,
        String recordNumber,
        String title,
        String status,
        Long ownerMemberId,
        Long departmentId,
        Long createdByMemberId,
        Long updatedByMemberId,
        Long createdConfigVersionId,
        Long updatedConfigVersionId,
        List<Long> participantMemberIds,
        Integer version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Map<String, JsonNode> fields) {
}
