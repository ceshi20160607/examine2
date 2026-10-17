package com.unique.unexamine.runtimedata.manage;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.List;

public record UpdateRuntimeRecordRequest(
        @NotBlank @Size(max = 500) String title,
        @Size(max = 100) String recordNumber,
        @Size(max = 64) String status,
        Long ownerMemberId,
        Long departmentId,
        @NotNull List<Long> participantMemberIds,
        @NotNull Map<String, JsonNode> fields,
        @NotNull @Min(0) Integer version,
        @Valid List<RuntimeRecordParticipant> participantMembers) {
    /** 旧调用方未指定角色时，服务端保留现有成员的角色。 */
    public UpdateRuntimeRecordRequest(String title, String recordNumber, String status, Long ownerMemberId,
            Long departmentId, List<Long> participantMemberIds, Map<String, JsonNode> fields, Integer version) {
        this(title, recordNumber, status, ownerMemberId, departmentId, participantMemberIds, fields, version, null);
    }
}
