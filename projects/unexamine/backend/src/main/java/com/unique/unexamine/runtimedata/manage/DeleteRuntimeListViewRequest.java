package com.unique.unexamine.runtimedata.manage;

import jakarta.validation.constraints.NotNull;

public record DeleteRuntimeListViewRequest(@NotNull Integer expectedVersion) {
}
