package com.unique.unexamine.system.manage;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 系统所属人转让请求；版本用于阻止并发覆盖。 */
public record TransferSystemOwnerRequest(
        @NotNull @Positive Long accountId,
        @NotNull Integer expectedVersion) {
}
