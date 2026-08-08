package com.unique.examine.module.runtime.query;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public final class RecordQueryModels {
    private RecordQueryModels() { }

    public record RecordQuery(
            String schemaVersionId,
            int page,
            int size,
            String recordScope,
            String q,
            FilterNode filter,
            List<SortItem> sort,
            List<String> columns,
            String viewId,
            String canonicalJson
    ) {
        public RecordQuery {
            sort = List.copyOf(sort);
            columns = List.copyOf(columns);
        }
    }

    public sealed interface FilterNode permits Predicate, Group { }

    public record Predicate(String fieldCode, String operator, JsonNode value) implements FilterNode { }

    public record Group(String kind, List<FilterNode> children) implements FilterNode {
        public Group {
            children = List.copyOf(children);
        }
    }

    public record SortItem(String fieldCode, String direction, String nulls, String currency) { }

    public record QueryField(
            long id,
            String code,
            String type,
            boolean searchable,
            boolean filterable,
            boolean sortable,
            boolean sensitiveQueryable,
            JsonNode schema
    ) {
        public QueryField(
                long id,
                String code,
                String type,
                boolean searchable,
                boolean filterable,
                boolean sortable,
                JsonNode schema
        ) {
            this(id, code, type, searchable, filterable, sortable, false, schema);
        }
    }

    @FunctionalInterface
    public interface SensitiveHashProvider {
        List<SensitiveHash> hashes(QueryField field, JsonNode canonicalValue);
    }

    public record SensitiveHash(String hashKeyVersion, String valueHash) { }

    public record EffectiveSort(
            String expressionSql,
            String direction,
            String nulls,
            String valueType,
            List<Object> arguments
    ) {
        public EffectiveSort {
            arguments = List.copyOf(arguments);
        }
    }

    public record CompiledQuery(
            String predicateSql,
            List<Object> arguments,
            String orderSql,
            List<Object> orderArguments,
            List<EffectiveSort> effectiveSorts
    ) {
        public CompiledQuery {
            arguments = List.copyOf(arguments);
            orderArguments = List.copyOf(orderArguments);
            effectiveSorts = List.copyOf(effectiveSorts);
        }

        public CompiledQuery(
                String predicateSql,
                List<Object> arguments,
                String orderSql,
                List<Object> orderArguments
        ) {
            this(predicateSql, arguments, orderSql, orderArguments, List.of());
        }
    }
}
