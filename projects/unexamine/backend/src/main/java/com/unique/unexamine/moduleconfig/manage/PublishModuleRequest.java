package com.unique.unexamine.moduleconfig.manage;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PublishModuleRequest(@NotNull @Min(1) Integer expectedDraftRevision) {
}
