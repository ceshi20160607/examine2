package com.unique.unexamine.runtimedata.manage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RecordTransferRequest(
        @NotNull Integer version,
        @NotNull Long ownerMemberId,
        Long departmentId,
        List<Long> participantMemberIds,
        @NotBlank @Size(max = 1000) String reason) {
}
