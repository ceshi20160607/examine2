package com.unique.unexamine.moduleconfig.manage;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RollbackModuleRequest(
        @NotNull Long targetVersionId,
        @NotNull @Min(0) Integer expectedPublicationVersion) {
}
