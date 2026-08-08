package com.unique.examine.module.runtime.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.unique.examine.module.runtime.api.RecordRuntimeViews;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Compiles and evaluates the page/rule portion of one immutable module-config snapshot.
 *
 * <p>This class deliberately has no database or draft dependency. Callers must pass the
 * active published snapshot (or one exact historical version), which keeps restart readback
 * and version pinning identical to record-schema resolution.</p>
 */
public final class PublishedPageRuleRuntime {

    public RecordRuntimeViews.PublishedRuntimeContract compile(
            String schemaVersionId,
            JsonNode snapshot,
            String moduleId,
            Map<String, String> readableFieldCodesById
    ) {
        var pages = compilePages(snapshot, moduleId, readableFieldCodesById);
        var rules = compileRules(snapshot, moduleId, readableFieldCodesById);
        return new RecordRuntimeViews.PublishedRuntimeContract(schemaVersionId, pages, rules);
    }

    public RecordRuntimeViews.RuntimeRuleDecision evaluate(
            RecordRuntimeViews.PublishedRuntimeContract contract,
            Map<String, JsonNode> values,
            Set<String> baseRequired,
            Set<String> baseReadonly
    ) {
        var hidden = new LinkedHashSet<String>();
        var required = new LinkedHashSet<>(baseRequired == null ? Set.of() : baseRequired);
        var readonly = new LinkedHashSet<>(baseReadonly == null ? Set.of() : baseReadonly);
        var disabledActions = new LinkedHashSet<String>();
        var applied = new HashSet<String>();
        var deleteAllowed = true;
        var approvalRequired = false;
        var deleteResolved = false;
        var approvalResolved = false;

        for (var rule : contract.rules()) {
            if (!matches(rule.condition(), values == null ? Map.of() : values)) {
                continue;
            }
            for (var effect : rule.effects()) {
                var key = effect.effect() + ":" + Objects.toString(effect.targetCode(), "");
                if (!applied.add(key)) {
                    continue;
                }
                switch (effect.effect()) {
                    case "VISIBLE" -> setMembership(hidden, effect.targetCode(), !effect.value());
                    case "REQUIRED" -> setMembership(required, effect.targetCode(), effect.value());
                    case "READ_ONLY" -> setMembership(readonly, effect.targetCode(), effect.value());
                    case "ACTION_ENABLED" -> {
                        if (!effect.value()) {
                            if (effect.targetCode() != null) disabledActions.add(effect.targetCode());
                            if (effect.targetAction() != null) disabledActions.add(effect.targetAction());
                        }
                    }
                    case "DELETE_ALLOWED" -> {
                        if (!deleteResolved) {
                            deleteAllowed = effect.value();
                            deleteResolved = true;
                        }
                    }
                    case "APPROVAL_REQUIRED" -> {
                        if (!approvalResolved) {
                            approvalRequired = effect.value();
                            approvalResolved = true;
                        }
                    }
                    default -> {
                        // Publication validation owns the enum boundary. Unknown historical values fail closed.
                    }
                }
            }
        }
        return new RecordRuntimeViews.RuntimeRuleDecision(
                hidden, required, readonly, disabledActions, deleteAllowed, approvalRequired);
    }

    private List<RecordRuntimeViews.PublishedPage> compilePages(
            JsonNode snapshot,
            String moduleId,
            Map<String, String> readableFieldCodesById
    ) {
        var fieldsById = new HashMap<String, JsonNode>();
        snapshot.path("fields").forEach(field -> fieldsById.put(field.path("id").asText(), field));
        var componentsByPage = new HashMap<String, List<JsonNode>>();
        snapshot.path("components").forEach(component -> componentsByPage
                .computeIfAbsent(component.path("page_id").asText(), ignored -> new ArrayList<>())
                .add(component));

        var selected = new LinkedHashMap<String, JsonNode>();
        sorted(snapshot.path("pages"), "page_code").stream()
                .filter(page -> moduleId.equals(page.path("module_id").asText()))
                .filter(PublishedPageRuleRuntime::enabled)
                .forEach(page -> {
                    var type = page.path("page_type").asText();
                    var existing = selected.get(type);
                    if (existing == null || flag(page, "is_default") && !flag(existing, "is_default")) {
                        selected.put(type, page);
                    }
                });

        var result = new ArrayList<RecordRuntimeViews.PublishedPage>();
        selected.values().stream()
                .sorted(Comparator.comparing(page -> page.path("page_type").asText()))
                .forEach(page -> result.add(page(page, componentsByPage.getOrDefault(
                        page.path("id").asText(), List.of()), fieldsById, readableFieldCodesById)));
        return List.copyOf(result);
    }

    private RecordRuntimeViews.PublishedPage page(
            JsonNode page,
            List<JsonNode> rawComponents,
            Map<String, JsonNode> fieldsById,
            Map<String, String> readableFieldCodesById
    ) {
        var layout = page.path("layout_json");
        var components = new ArrayList<>(rawComponents);
        components.sort(Comparator.comparingInt((JsonNode component) -> component.path("sort_order").asInt())
                .thenComparing(component -> component.path("id").asText()));
        var sectionCodes = new HashMap<String, String>();
        var hiddenSectionIds = new HashSet<String>();
        var sections = new ArrayList<RecordRuntimeViews.PageSection>();
        for (var component : components) {
            var type = component.path("component_type").asText();
            if (!Set.of("SECTION", "TAB").contains(type)) continue;
            var properties = component.path("property_json");
            if (properties.has("visible") && !properties.path("visible").asBoolean()) {
                hiddenSectionIds.add(component.path("id").asText());
                continue;
            }
            var code = component.path("component_key").asText();
            sectionCodes.put(component.path("id").asText(), code);
            sections.add(new RecordRuntimeViews.PageSection(
                    code,
                    properties.path("title").asText(properties.path("label").asText(code)),
                    component.path("sort_order").asInt(),
                    bounded(properties.path("columns").asInt(layout.path("columns").asInt(24)), 1, 24),
                    properties.path("collapsible").asBoolean(false),
                    properties.path("collapsed").asBoolean(false)));
        }

        var placements = new ArrayList<RecordRuntimeViews.PageFieldPlacement>();
        for (var component : components) {
            if (!"FIELD".equals(component.path("component_type").asText())) continue;
            var properties = component.path("property_json");
            if (properties.has("visible") && !properties.path("visible").asBoolean()) continue;
            if (hiddenSectionIds.contains(component.path("parent_component_id").asText())) continue;
            var fieldId = component.path("field_id").asText();
            var fieldCode = readableFieldCodesById.get(fieldId);
            if (fieldCode == null) continue;
            var field = fieldsById.get(fieldId);
            if (field == null || !enabled(field)) continue;
            var variant = properties.path("variant").asText("").toUpperCase(Locale.ROOT);
            var fixed = switch (variant) {
                case "FIXED_LEFT" -> "LEFT";
                case "FIXED_RIGHT" -> "RIGHT";
                default -> "NONE";
            };
            var width = bounded(field.path("property_json").path("width").asInt(160), 40, 1200);
            placements.add(new RecordRuntimeViews.PageFieldPlacement(
                    fieldCode,
                    sectionCodes.get(component.path("parent_component_id").asText()),
                    component.path("sort_order").asInt(),
                    component.path("grid_row").asInt(),
                    component.path("grid_column").asInt(),
                    bounded(component.path("grid_span").asInt(24), 1, 24),
                    width,
                    fixed));
        }

        // Existing published modules may predate explicit FIELD components. Keep their
        // declared field flags as the deterministic fallback while still pinning the page version.
        if (placements.isEmpty()) {
            var type = page.path("page_type").asText();
            var order = 0;
            for (var field : sorted(fieldsById.values(), "sort_order")) {
                var fieldId = field.path("id").asText();
                var fieldCode = readableFieldCodesById.get(fieldId);
                if (fieldCode == null || !enabled(field)) continue;
                var declared = switch (type) {
                    case "LIST" -> flag(field, "show_in_list");
                    case "DETAIL" -> flag(field, "show_in_detail");
                    default -> true;
                };
                if (!declared) continue;
                placements.add(new RecordRuntimeViews.PageFieldPlacement(
                        fieldCode, null, order++, order, 0,
                        "FORM".equals(type) || "DETAIL".equals(type) ? 12 : 24,
                        bounded(field.path("property_json").path("width").asInt(160), 40, 1200), "NONE"));
            }
        }
        placements.sort(Comparator.comparingInt(RecordRuntimeViews.PageFieldPlacement::sortOrder)
                .thenComparing(RecordRuntimeViews.PageFieldPlacement::fieldCode));
        sections.sort(Comparator.comparingInt(RecordRuntimeViews.PageSection::sortOrder)
                .thenComparing(RecordRuntimeViews.PageSection::sectionCode));
        return new RecordRuntimeViews.PublishedPage(
                page.path("id").asText(), page.path("page_code").asText(), page.path("page_type").asText(),
                layout.path("density").asText("DEFAULT"), bounded(layout.path("columns").asInt(24), 1, 24),
                bounded(layout.path("gap").asInt(16), 0, 64), layout.path("labelPosition").asText("TOP"),
                layout.path("stickyActions").asBoolean(false), bounded(layout.path("pageSize").asInt(50), 1, 500),
                sections, placements);
    }

    private List<RecordRuntimeViews.PublishedRule> compileRules(
            JsonNode snapshot,
            String moduleId,
            Map<String, String> readableFieldCodesById
    ) {
        var actions = new HashMap<String, JsonNode>();
        snapshot.path("actions").forEach(action -> actions.put(action.path("id").asText(), action));
        var result = new ArrayList<RecordRuntimeViews.PublishedRule>();
        for (var rule : sorted(snapshot.path("rules"), "priority")) {
            if (!moduleId.equals(rule.path("module_id").asText()) || !enabled(rule)) continue;
            var condition = condition(rule.path("condition_json"), readableFieldCodesById);
            if (condition == null) continue;
            var effects = new ArrayList<RecordRuntimeViews.PublishedEffect>();
            for (var effect : rule.path("effect_json")) {
                var targetId = effect.path("targetId").asText(null);
                var action = targetId == null ? null : actions.get(targetId);
                var targetCode = targetId == null ? null : readableFieldCodesById.get(targetId);
                if (targetCode == null && action != null) targetCode = action.path("action_code").asText(null);
                effects.add(new RecordRuntimeViews.PublishedEffect(
                        effect.path("effect").asText(), targetCode,
                        action == null ? null : action.path("action_type").asText(null),
                        effect.path("value").asBoolean(false)));
            }
            if (!effects.isEmpty()) {
                result.add(new RecordRuntimeViews.PublishedRule(
                        rule.path("rule_code").asText(), rule.path("rule_type").asText(),
                        rule.path("priority").asInt(), condition, effects));
            }
        }
        result.sort(Comparator.comparingInt(RecordRuntimeViews.PublishedRule::priority)
                .thenComparing(RecordRuntimeViews.PublishedRule::ruleCode));
        return List.copyOf(result);
    }

    private RecordRuntimeViews.PublishedCondition condition(
            JsonNode node,
            Map<String, String> fieldCodesById
    ) {
        if (node.hasNonNull("join")) {
            var children = new ArrayList<RecordRuntimeViews.PublishedCondition>();
            node.path("children").forEach(child -> {
                var compiled = condition(child, fieldCodesById);
                if (compiled != null) children.add(compiled);
            });
            if (children.size() != node.path("children").size() || children.isEmpty()) return null;
            return new RecordRuntimeViews.PublishedCondition(
                    node.path("join").asText(), null, null, null, children);
        }
        var fieldCode = fieldCodesById.get(node.path("fieldId").asText());
        if (fieldCode == null) return null;
        return new RecordRuntimeViews.PublishedCondition(
                null, fieldCode, node.path("operator").asText(),
                node.has("value") ? node.get("value") : null, List.of());
    }

    private boolean matches(RecordRuntimeViews.PublishedCondition condition, Map<String, JsonNode> values) {
        if (condition == null) return false;
        if (condition.join() != null) {
            return "AND".equals(condition.join())
                    ? condition.children().stream().allMatch(child -> matches(child, values))
                    : condition.children().stream().anyMatch(child -> matches(child, values));
        }
        var actual = values.getOrDefault(condition.fieldCode(), NullNode.getInstance());
        var expected = condition.value() == null ? NullNode.getInstance() : condition.value();
        return switch (condition.operator()) {
            case "EMPTY" -> empty(actual);
            case "NOT_EMPTY" -> !empty(actual);
            case "EQ" -> compare(actual, expected) == 0;
            case "NE" -> compare(actual, expected) != 0;
            case "GT" -> compare(actual, expected) > 0;
            case "GTE" -> compare(actual, expected) >= 0;
            case "LT" -> compare(actual, expected) < 0;
            case "LTE" -> compare(actual, expected) <= 0;
            case "IN" -> expected.isArray() && contains(expected, actual);
            case "NOT_IN" -> expected.isArray() && !contains(expected, actual);
            case "CONTAINS" -> contains(actual, expected);
            case "BETWEEN" -> expected.isArray() && expected.size() == 2
                    && compare(actual, expected.get(0)) >= 0 && compare(actual, expected.get(1)) <= 0;
            default -> false;
        };
    }

    private static boolean contains(JsonNode container, JsonNode candidate) {
        if (container.isArray()) {
            for (var item : container) if (compare(item, candidate) == 0) return true;
            return false;
        }
        return container.isTextual() && candidate.isTextual()
                && container.asText().contains(candidate.asText());
    }

    private static int compare(JsonNode left, JsonNode right) {
        if (empty(left) && empty(right)) return 0;
        if (left.isNumber() && right.isNumber()) {
            return left.decimalValue().compareTo(right.decimalValue());
        }
        if (left.isBoolean() && right.isBoolean()) {
            return Boolean.compare(left.asBoolean(), right.asBoolean());
        }
        var leftText = scalarText(left);
        var rightText = scalarText(right);
        try {
            return new BigDecimal(leftText).compareTo(new BigDecimal(rightText));
        } catch (NumberFormatException ignored) {
            return leftText.compareTo(rightText);
        }
    }

    private static String scalarText(JsonNode node) {
        return node == null || node.isNull() || node.isMissingNode() ? "" : node.isTextual() ? node.asText() : node.toString();
    }

    private static boolean empty(JsonNode node) {
        return node == null || node.isNull() || node.isMissingNode()
                || node.isTextual() && node.asText().isBlank()
                || node.isArray() && node.isEmpty()
                || node.isObject() && node.isEmpty();
    }

    private static void setMembership(Set<String> target, String value, boolean present) {
        if (value == null) return;
        if (present) target.add(value); else target.remove(value);
    }

    private static boolean enabled(JsonNode node) {
        return "ENABLED".equals(node.path("desired_status").asText());
    }

    private static boolean flag(JsonNode node, String key) {
        var value = node.path(key);
        return value.isBoolean() ? value.asBoolean() : value.asInt() != 0;
    }

    private static int bounded(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static List<JsonNode> sorted(JsonNode array, String orderKey) {
        var result = new ArrayList<JsonNode>();
        array.forEach(result::add);
        result.sort(Comparator.comparingInt((JsonNode node) -> node.path(orderKey).asInt())
                .thenComparing(node -> node.path("id").asText()));
        return result;
    }

    private static List<JsonNode> sorted(Iterable<JsonNode> values, String orderKey) {
        var result = new ArrayList<JsonNode>();
        values.forEach(result::add);
        result.sort(Comparator.comparingInt((JsonNode node) -> node.path(orderKey).asInt())
                .thenComparing(node -> node.path("id").asText()));
        return result;
    }
}
