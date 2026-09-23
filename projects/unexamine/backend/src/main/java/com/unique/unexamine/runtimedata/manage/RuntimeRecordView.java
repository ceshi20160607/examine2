package com.unique.unexamine.runtimedata.manage;

import com.fasterxml.jackson.databind.JsonNode;
import com.unique.unexamine.authorization.manage.SystemPeopleDirectoryModels;

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
        SystemPeopleDirectoryModels.Labels organizationLabels,
        Map<String, RuntimeFieldPresentation> fieldPresentation,
        List<RuntimeRecordAction> actions,
        Map<String, JsonNode> fields) {
    /** 重放时重新裁剪字段、名称和展示文本，不能沿用旧权限下的描述。 */
    public RuntimeRecordView withFields(Map<String, JsonNode> visibleFields, SystemPeopleDirectoryModels.Labels visibleLabels,
                                        Map<String, RuntimeFieldPresentation> visiblePresentation,
                                        List<RuntimeRecordAction> visibleActions) {
        return new RuntimeRecordView(id, recordNumber, title, status, ownerMemberId, departmentId,
                createdByMemberId, updatedByMemberId, createdConfigVersionId, updatedConfigVersionId,
                participantMemberIds, archived, archivedAt, deleted, deletedAt, version, createdAt, updatedAt,
                dataTenantId, dataTenantName, ownedByCurrentTenant, shared, shareId, shareStatus, sharedActions,
                shareExpiresAt, visibleLabels, visiblePresentation, visibleActions, visibleFields);
    }
}
