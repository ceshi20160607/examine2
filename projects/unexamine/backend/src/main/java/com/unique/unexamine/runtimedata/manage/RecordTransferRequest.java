package com.unique.unexamine.runtimedata.manage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 成员使用系统成员标识；目标必须是数据所属租户内的有效成员/部门。 */
public record RecordTransferRequest(
        @NotNull @Min(0) Integer version,
        @NotNull Long ownerMemberId,
        Long departmentId,
        @Size(max = 100) List<@NotNull Long> participantMemberIds,
        @NotBlank @Size(max = 1000) String reason) {
}
