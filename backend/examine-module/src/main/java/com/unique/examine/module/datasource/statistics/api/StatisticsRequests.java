package com.unique.examine.module.datasource.statistics.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.unique.examine.module.datasource.statistics.domain.StatisticsException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public final class StatisticsRequests {
    private StatisticsRequests() {
    }

    public record Query(
            @NotBlank String aggregation,
            String measureFieldCode,
            @Valid Grouping grouping,
            @Valid Trend trend
    ) {
        /** Reject hidden ownership/SQL fields even when global Jackson is lenient. */
        @JsonAnySetter
        public void rejectUnknown(String name, Object value) {
            throw new StatisticsException(
                    "STATISTICS_REQUEST_INVALID",
                    "Unknown public statistics request field: " + name);
        }
    }

    public record Grouping(
            @NotBlank String fieldCode,
            @NotNull Integer bucketLimit
    ) {
    }

    public record Trend(
            @NotBlank String fieldCode,
            @NotBlank String grain,
            @NotNull LocalDate startInclusive,
            @NotNull LocalDate endExclusive
    ) {
    }
}
