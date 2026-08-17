package com.unique.unexamine.runtimedata.manage;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.List;

public record CreateRuntimeRecordRequest(
        @NotBlank @Size(max = 500) String title,
        @Size(max = 100) String recordNumber,
        @Size(max = 64) String status,
        Long ownerMemberId,
        Long departmentId,
        @NotNull List<Long> participantMemberIds,
        @NotNull Map<String, JsonNode> fields) {
}
