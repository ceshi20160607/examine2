package com.unique.examine.module.dashboard.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.time.LocalDate;

public final class DashboardRequests {
    private DashboardRequests() {
    }

    public record Create(
            @NotBlank @Size(max = 64) String code,
            @NotNull String placement,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 2_000) String description
    ) {
    }

    public record ScopedCreate(
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 2_000) String description
    ) {
    }

    public record SaveDraft(
            @NotNull Long expectedVersion,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 2_000) String description,
            @NotNull @Size(max = 20) List<@Valid Widget> widgets
    ) {
    }

    public record Publish(@NotNull Long expectedVersion) {
    }

    public record Widget(
            @NotBlank @Size(max = 64) String code,
            @NotNull String type,
            @NotBlank @Size(max = 200) String title,
            String dataSourceId,
            String kpiId,
            Integer rowLimit,
            @Valid Statistic statistics,
            @Valid Behavior behavior,
            @NotNull @Valid Grid grid
    ) {
        public Widget(
                String code, String type, String title,
                String dataSourceId, String kpiId, Integer rowLimit,
                Statistic statistics, Grid grid
        ) {
            this(code, type, title, dataSourceId, kpiId, rowLimit,
                    statistics, null, grid);
        }
    }

    public record Behavior(
            Integer refreshSeconds,
            @Size(max = 300) String clickThrough,
            @Size(max = 32) String styleVariant
    ) {
    }

    public record Statistic(
            @NotNull String aggregation,
            String measureFieldCode,
            @Valid Grouping grouping,
            @Valid Trend trend
    ) {
    }

    public record Grouping(
            @NotBlank String fieldCode,
            @NotNull Integer bucketLimit
    ) {
    }

    public record Trend(
            @NotBlank String fieldCode,
            @NotNull String grain,
            @NotNull LocalDate startInclusive,
            @NotNull LocalDate endExclusive
    ) {
    }

    public record Grid(
            @NotNull Integer x,
            @NotNull Integer y,
            @NotNull Integer width,
            @NotNull Integer height
    ) {
    }
}
