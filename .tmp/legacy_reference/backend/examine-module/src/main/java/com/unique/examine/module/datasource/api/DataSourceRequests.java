package com.unique.examine.module.datasource.api;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class DataSourceRequests {
    private DataSourceRequests() {
    }

    public record Create(
            @NotBlank @Size(max = 64) String code,
            @NotBlank String moduleId,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 2_000) String description,
            @Size(max = 32) String sourceKind,
            @Valid HttpJsonConnection httpJsonConnection,
            @Valid JdbcTableConnection jdbcTableConnection,
            @Valid MultiModuleJoin multiModuleJoin
    ) {
        /** Compatibility constructor for Native/HTTP/JDBC callers. */
        public Create(
                String code,
                String moduleId,
                String name,
                String description,
                String sourceKind,
                HttpJsonConnection httpJsonConnection,
                JdbcTableConnection jdbcTableConnection
        ) {
            this(code, moduleId, name, description, sourceKind,
                    httpJsonConnection, jdbcTableConnection, null);
        }

        /** Compatibility constructor for Native/HTTP callers. */
        public Create(
                String code,
                String moduleId,
                String name,
                String description,
                String sourceKind,
                HttpJsonConnection httpJsonConnection
        ) {
            this(code, moduleId, name, description, sourceKind,
                    httpJsonConnection, null, null);
        }
    }

    public record SaveDraft(
            @NotNull Long expectedVersion,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 2_000) String description,
            @NotNull @Size(max = 50) List<@Valid OutputField> outputFields,
            @NotNull @Size(max = 20) List<@Valid FixedFilter> fixedFilters,
            @Valid DefaultSort defaultSort,
            @Size(max = 64) String defaultTimeFieldCode,
            @Size(max = 32) String sourceKind,
            @Valid HttpJsonConnection httpJsonConnection,
            @Size(max = 50)
            List<@Valid HttpJsonFieldProjection> httpFieldProjections,
            @Valid JdbcTableConnection jdbcTableConnection,
            @Size(max = 50)
            List<@Valid JdbcTableFieldProjection> jdbcFieldProjections,
            @Valid MultiModuleJoin multiModuleJoin
    ) {
        /** Compatibility constructor for Native/HTTP/JDBC callers. */
        public SaveDraft(
                Long expectedVersion,
                String name,
                String description,
                List<OutputField> outputFields,
                List<FixedFilter> fixedFilters,
                DefaultSort defaultSort,
                String defaultTimeFieldCode,
                String sourceKind,
                HttpJsonConnection httpJsonConnection,
                List<HttpJsonFieldProjection> httpFieldProjections,
                JdbcTableConnection jdbcTableConnection,
                List<JdbcTableFieldProjection> jdbcFieldProjections
        ) {
            this(expectedVersion, name, description, outputFields,
                    fixedFilters, defaultSort, defaultTimeFieldCode,
                    sourceKind, httpJsonConnection, httpFieldProjections,
                    jdbcTableConnection, jdbcFieldProjections, null);
        }

        /** Compatibility constructor for Native/HTTP callers. */
        public SaveDraft(
                Long expectedVersion,
                String name,
                String description,
                List<OutputField> outputFields,
                List<FixedFilter> fixedFilters,
                DefaultSort defaultSort,
                String defaultTimeFieldCode,
                String sourceKind,
                HttpJsonConnection httpJsonConnection,
                List<HttpJsonFieldProjection> httpFieldProjections
        ) {
            this(expectedVersion, name, description, outputFields,
                    fixedFilters, defaultSort, defaultTimeFieldCode,
                    sourceKind, httpJsonConnection, httpFieldProjections,
                    null, List.of(), null);
        }
    }

    public record Publish(@NotNull Long expectedVersion) {
    }

    public record CheckConnection(@NotNull Long expectedVersion) {
    }

    public record DiscoverSchema(@NotNull Long expectedVersion) {
    }

    public record PreviewRows(@NotNull Long expectedVersion) {
    }

    public record HttpJsonConnection(
            @NotBlank @Size(max = 1024) String endpoint,
            @Size(max = 512) String authSecretRef,
            @NotNull @Min(1) @Max(10) Integer timeoutSeconds
    ) {
    }

    public record HttpJsonFieldProjection(
            @NotBlank @Size(max = 128) String sourceField,
            @NotBlank @Size(max = 64) String fieldCode,
            @NotNull String sourceType
    ) {
    }

    public record JdbcTableConnection(
            @NotBlank @Size(max = 253) String host,
            @NotNull @Min(1) @Max(65_535) Integer port,
            @NotBlank @Size(max = 64) String databaseName,
            @NotBlank @Size(max = 64) String tableName,
            @Size(max = 512) String usernameSecretRef,
            @Size(max = 512) String passwordSecretRef,
            @NotNull @Min(1) @Max(10) Integer connectTimeoutSeconds,
            @NotNull @Min(1) @Max(10) Integer queryTimeoutSeconds
    ) {
    }

    public record JdbcTableFieldProjection(
            @NotBlank @Size(max = 64) String sourceColumn,
            @NotBlank @Size(max = 64) String fieldCode,
            @NotNull String sourceType
    ) {
    }

    public record MultiModuleJoin(
            @NotNull @Size(min = 2, max = 8)
            List<@Valid JoinInput> inputs,
            @NotNull @Size(min = 1, max = 7)
            List<@Valid JoinEdge> edges,
            @NotNull @Size(min = 1, max = 50)
            List<@Valid JoinProjection> projections,
            @NotBlank String failureMode,
            @NotNull @Min(1) @Max(10) Integer timeoutSeconds,
            @NotNull @Min(1) @Max(100) Integer rowLimit
    ) {
    }

    public record JoinInput(
            @NotBlank @Size(max = 20) String alias,
            @NotBlank String dataSourceId,
            @NotBlank String dataSourceVersionId
    ) {
    }

    public record JoinEdge(
            @NotBlank @Size(max = 20) String leftAlias,
            @NotBlank @Size(max = 64) String leftFieldCode,
            @NotBlank @Size(max = 20) String rightAlias,
            @NotBlank @Size(max = 64) String rightFieldCode,
            @NotBlank String joinType,
            @NotBlank String cardinality
    ) {
    }

    public record JoinProjection(
            @NotBlank @Size(max = 20) String sourceAlias,
            @NotBlank @Size(max = 64) String sourceFieldCode,
            @NotBlank @Size(max = 64) String fieldCode
    ) {
    }

    public record OutputField(@NotBlank @Size(max = 64) String fieldCode) {
    }

    public record FixedFilter(
            @NotBlank @Size(max = 64) String fieldCode,
            @NotBlank @Size(max = 64) String operator,
            JsonNode canonicalValue
    ) {
    }

    public record DefaultSort(
            @NotBlank @Size(max = 64) String fieldCode,
            @NotNull String direction
    ) {
    }
}
