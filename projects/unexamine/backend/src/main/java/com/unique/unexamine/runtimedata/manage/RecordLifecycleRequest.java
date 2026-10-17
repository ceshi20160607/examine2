package com.unique.unexamine.runtimedata.manage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RecordLifecycleRequest(
        @NotBlank @Size(max = 1000) String reason,
        @NotNull Integer version,
        Long replacementRecordId) {
    /** 无引用记录沿用原有的原因和版本参数。 */
    public RecordLifecycleRequest(String reason, Integer version) {
        this(reason, version, null);
    }
}
