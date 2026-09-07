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
        Boolean archived,
        LocalDateTime archivedAt,
        Boolean deleted,
        LocalDateTime deletedAt,
        Integer version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long dataTenantId,
        String dataTenantName,
        boolean ownedByCurrentTenant,
        boolean shared,
        Long shareId,
        String shareStatus,
        List<String> sharedActions,
        LocalDateTime shareExpiresAt,
        Map<String, JsonNode> fields) {
    public RuntimeRecordView withFields(Map<String, JsonNode> visibleFields) {
        return new RuntimeRecordView(id, recordNumber, title, status, ownerMemberId, departmentId,
                createdByMemberId, updatedByMemberId, createdConfigVersionId, updatedConfigVersionId,
                participantMemberIds, archived, archivedAt, deleted, deletedAt, version, createdAt, updatedAt,
                dataTenantId, dataTenantName, ownedByCurrentTenant, shared, shareId, shareStatus, sharedActions,
                shareExpiresAt, visibleFields);
    }
}
