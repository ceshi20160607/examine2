package com.unique.unexamine.system.manage;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RunIdentityMappingPreflightRequest(
        @NotEmpty @Size(max = 100) List<@Valid IdentityMappingSample> samples) {
}
