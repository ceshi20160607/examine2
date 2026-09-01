package com.unique.unexamine.system.manage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record DecideAccessRequest(
        @NotBlank @Pattern(regexp = "APPROVE|REJECT") String decision,
        List<Long> roleIds,
        @Size(max = 1000) String comment,
        @NotNull Integer version) {
}
