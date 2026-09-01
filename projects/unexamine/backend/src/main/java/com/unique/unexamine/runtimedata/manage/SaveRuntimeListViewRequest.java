package com.unique.unexamine.runtimedata.manage;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SaveRuntimeListViewRequest(
        @NotBlank @Size(max = 80) String name,
        @Size(max = 200) String search,
        @NotNull @Size(max = 10) List<@NotNull @Valid RuntimeListFilter> filters,
        @NotBlank @Size(max = 80) String sortField,
        @NotBlank @Pattern(regexp = "(?i)ASC|DESC") String sortDirection,
        @NotNull @Size(max = 100) List<@NotBlank @Size(max = 80) String> visibleFieldCodes,
        @NotNull @Size(max = 10) List<@NotBlank @Size(max = 80) String> fixedFieldCodes,
        @NotNull @Min(10) @Max(200) Integer pageSize,
        boolean defaultView,
        Integer expectedVersion) {
}
