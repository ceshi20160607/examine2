package com.unique.examine.module.runtime.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.runtime.api.RecordRuntimeViews;
import com.unique.examine.module.runtime.api.RuntimeViews;
import com.unique.examine.module.runtime.query.RecordSearchTokenizer;
import com.unique.examine.module.runtime.security.RuntimeSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class GlobalSearchService {
    private static final Set<String> SEARCHABLE_ORDINARY_TYPES =
            Set.of("TEXT", "TEXTAREA", "RICH_TEXT");
    private static final Set<String> FORBIDDEN_TYPES = Set.of("IDENTITY", "SECRET");

    private final GlobalSearchRuntime runtime;
    private final GlobalSearchRequestParser parser;
    private final ObjectMapper objectMapper;

    public GlobalSearchService(
            GlobalSearchRuntime runtime,
            GlobalSearchRequestParser parser,
            ObjectMapper objectMapper
    ) {
        this.runtime = runtime;
        this.parser = parser;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public GlobalSearchViews.SearchPage search(
            RuntimeSession session,
            String q,
            String page,
            String size
    ) {
        var request = parser.parse(q, page, size);
        final RuntimeViews.Navigation navigation;
        try {
            navigation = runtime.navigation(session);
        } catch (BusinessException exception) {
            return empty(request);
        }
        var modules = new ArrayList<ModuleSearch>();
        for (var group : navigation.groups()) {
            for (var module : group.modules()) {
                inspect(session, module, request.q()).ifPresent(modules::add);
            }
        }
        while (true) {
            try {
                return assemble(session, request, modules);
            } catch (ModuleUnavailable unavailable) {
                modules.removeIf(module -> module.code().equals(unavailable.moduleCode));
            }
        }
    }

    private java.util.Optional<ModuleSearch> inspect(
            RuntimeSession session,
            RuntimeViews.Module module,
            String q
    ) {
        try {
            var definition = runtime.definition(session, module.code());
            var schema = runtime.schema(session, module.code());
            if (!"READY".equals(schema.runtimeState())) {
                return java.util.Optional.empty();
            }
            var fields = safeFields(definition, schema);
            if (fields.isEmpty()) {
                return java.util.Optional.empty();
            }
            var pageSize = Math.max(1, Math.min(200, schema.queryLimits().maxSize()));
            var count = runtime.query(
                    session,
                    module.code(),
                    query(schema.schemaVersionId(), q, 1, 1, fields)).total();
            return java.util.Optional.of(new ModuleSearch(
                    module.code(), module.name(), schema.schemaVersionId(), fields, pageSize, count));
        } catch (BusinessException exception) {
            return java.util.Optional.empty();
        }
    }

    private GlobalSearchViews.SearchPage assemble(
            RuntimeSession session,
            GlobalSearchRequestParser.SearchRequest request,
            List<ModuleSearch> modules
    ) {
        var total = modules.stream().mapToLong(ModuleSearch::total).reduce(0L, Math::addExact);
        var globalOffset = (long) (request.page() - 1) * request.size();
        if (globalOffset >= total) {
            return new GlobalSearchViews.SearchPage(List.of(), request.page(), request.size(), total);
        }
        var items = new ArrayList<GlobalSearchViews.SearchItem>();
        var remainingOffset = globalOffset;
        for (var module : modules) {
            if (remainingOffset >= module.total()) {
                remainingOffset -= module.total();
                continue;
            }
            var needed = request.size() - items.size();
            if (needed <= 0) {
                break;
            }
            items.addAll(fetch(session, request.q(), module, remainingOffset, needed));
            remainingOffset = 0;
        }
        return new GlobalSearchViews.SearchPage(items, request.page(), request.size(), total);
    }

    private List<GlobalSearchViews.SearchItem> fetch(
            RuntimeSession session,
            String q,
            ModuleSearch module,
            long localOffset,
            int limit
    ) {
        var result = new ArrayList<GlobalSearchViews.SearchItem>();
        var position = localOffset;
        var tokens = RecordSearchTokenizer.queryTokens(q);
        while (result.size() < limit && position < module.total()) {
            var localPage = Math.toIntExact(position / module.pageSize()) + 1;
            final RecordRuntimeViews.RecordPage page;
            try {
                page = runtime.query(session, module.code(), query(
                        module.schemaVersionId(), q, localPage, module.pageSize(), module.fieldCodes()));
            } catch (BusinessException exception) {
                throw new ModuleUnavailable(module.code());
            }
            var withinPage = Math.toIntExact(position % module.pageSize());
            if (withinPage >= page.rows().size()) {
                break;
            }
            var available = Math.min(
                    page.rows().size() - withinPage,
                    limit - result.size());
            for (var index = 0; index < available; index++) {
                result.add(item(module, page.rows().get(withinPage + index), tokens));
            }
            position += available;
            if (available == 0) {
                break;
            }
        }
        return result;
    }

    private GlobalSearchViews.SearchItem item(
            ModuleSearch module,
            RecordRuntimeViews.RecordSummary record,
            List<String> queryTokens
    ) {
        return new GlobalSearchViews.SearchItem(
                module.code(),
                module.name(),
                record.recordId(),
                record.recordNo(),
                displayLabel(record.recordNo(), record.title()),
                record.status(),
                matchedFields(record.values(), module.fieldCodes(), queryTokens));
    }

    private static List<String> safeFields(
            RuntimeViews.Definition definition,
            RecordRuntimeViews.RecordSchema schema
    ) {
        var published = new LinkedHashMap<String, JsonNode>();
        definition.fields().forEach(field -> {
            var type = field.path("field_type").asText();
            if ("ENABLED".equals(field.path("desired_status").asText())
                    && !field.path("is_hidden").asBoolean()
                    && field.path("is_searchable").asBoolean()
                    && !"NONE".equals(field.path("index_mode").asText("NONE"))
                    && SEARCHABLE_ORDINARY_TYPES.contains(type)
                    && !FORBIDDEN_TYPES.contains(type)) {
                published.put(field.path("field_code").asText(), field);
            }
        });
        var result = new ArrayList<String>();
        for (var capability : schema.fields()) {
            var field = published.get(capability.fieldCode());
            if (field != null
                    && capability.readable()
                    && !capability.masked()
                    && capability.type().equals(field.path("field_type").asText())
                    && !FORBIDDEN_TYPES.contains(capability.type())) {
                result.add(capability.fieldCode());
            }
        }
        return List.copyOf(result);
    }

    private String query(
            String schemaVersionId,
            String q,
            int page,
            int size,
            List<String> columns
    ) {
        var root = objectMapper.createObjectNode();
        root.put("schemaVersionId", schemaVersionId);
        root.put("page", page);
        root.put("size", size);
        root.put("recordScope", "active");
        root.put("q", q);
        root.putNull("filter");
        root.putArray("sort");
        var projected = root.putArray("columns");
        columns.forEach(projected::add);
        return root.toString();
    }

    private static List<String> matchedFields(
            List<RecordRuntimeViews.FieldValue> values,
            List<String> safeFields,
            List<String> queryTokens
    ) {
        var allowed = new LinkedHashSet<>(safeFields);
        var matches = new LinkedHashSet<String>();
        for (var value : values) {
            if (!allowed.contains(value.fieldCode()) || FORBIDDEN_TYPES.contains(value.type())) {
                continue;
            }
            var projected = projectedText(value);
            if (projected == null) {
                continue;
            }
            var tokens = RecordSearchTokenizer.indexTokens(projected);
            if (tokens.containsAll(queryTokens)) {
                matches.add(value.fieldCode());
            }
        }
        return List.copyOf(matches);
    }

    private static String projectedText(RecordRuntimeViews.FieldValue value) {
        if (value.displayValue() != null && !value.displayValue().isBlank()) {
            return value.displayValue();
        }
        return value.value() instanceof String text && !text.isBlank() ? text : null;
    }

    private static String displayLabel(String recordNo, String title) {
        if (title == null || title.isBlank() || title.equals(recordNo)) {
            return recordNo;
        }
        return recordNo + " " + title;
    }

    private static GlobalSearchViews.SearchPage empty(GlobalSearchRequestParser.SearchRequest request) {
        return new GlobalSearchViews.SearchPage(List.of(), request.page(), request.size(), 0);
    }

    private record ModuleSearch(
            String code,
            String name,
            String schemaVersionId,
            List<String> fieldCodes,
            int pageSize,
            long total
    ) {
        private ModuleSearch {
            fieldCodes = List.copyOf(fieldCodes);
        }
    }

    private static final class ModuleUnavailable extends RuntimeException {
        private final String moduleCode;

        private ModuleUnavailable(String moduleCode) {
            this.moduleCode = moduleCode;
        }
    }
}
