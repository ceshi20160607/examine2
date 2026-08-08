package com.unique.examine.module.kpi.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public final class KpiRequests {
    private KpiRequests() {
    }

    public record Create(
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 2_000) String description,
            @NotBlank String dataSourceId,
            @NotBlank String subjectType,
            @NotBlank String periodType,
            @NotBlank String aggregation,
            @Size(max = 64) String measureFieldCode,
            @NotBlank @Size(max = 64) String timeFieldCode,
            @NotBlank String direction,
            @NotBlank @Size(max = 1_000) String warningThreshold
    ) {
    }

    public record SaveDraft(
            @NotNull Long expectedVersion,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 2_000) String description,
            @NotBlank String dataSourceId,
            @NotBlank String subjectType,
            @NotBlank String periodType,
            @NotBlank String aggregation,
            @Size(max = 64) String measureFieldCode,
            @NotBlank @Size(max = 64) String timeFieldCode,
            @NotBlank String direction,
            @NotBlank @Size(max = 1_000) String warningThreshold
    ) {
    }

    public record Publish(@NotNull Long expectedVersion) {
    }

    public record CreateTarget(
            @NotBlank @Pattern(regexp = "^[1-9][0-9]{0,18}$")
            String subjectId,
            @NotNull LocalDate periodStart,
            @NotBlank @Size(max = 1_000) String targetValue
    ) {
    }

    public record UpdateTarget(
            @NotBlank @Size(max = 1_000) String targetValue,
            @NotNull Long expectedVersion
    ) {
    }
}
