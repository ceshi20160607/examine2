package com.unique.examine.module.manage.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.unique.examine.module.manage.api.ConfigRequests;
import com.unique.examine.module.manage.api.ConfigTypes.FieldType;
import com.unique.examine.module.manage.api.ConfigTypes.IndexMode;
import com.unique.examine.module.manage.api.ConfigViews;
import com.unique.examine.module.manage.security.ConfigSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ConfigModuleCopyService {
    private final ConfigDraftService drafts;
    private final ConfigMutationSupport mutations;

    public ConfigModuleCopyService(ConfigDraftService drafts, ConfigMutationSupport mutations) {
        this.drafts = drafts;
        this.mutations = mutations;
    }

    @Transactional
    public ConfigViews.Module copy(ConfigSession session, long sourceModuleId, ConfigRequests.CopyModule request,
                                   String idempotencyKey, RequestContext context) {
        return mutations.idempotent(session.systemId() + ":module:" + sourceModuleId + ":copy",
                idempotencyKey, request, ConfigViews.Module.class,
                () -> copyNow(session, sourceModuleId, request, idempotencyKey, context));
    }

    private ConfigViews.Module copyNow(ConfigSession session, long sourceModuleId, ConfigRequests.CopyModule request,
                                       String key, RequestContext context) {
        var source = drafts.modules(session.systemId()).stream()
                .filter(module -> module.id().equals(Long.toString(sourceModuleId)))
                .findFirst().orElseThrow(ConfigErrors::notFound);
        if (!source.version().equals(request.sourceVersion())) {
            throw ConfigErrors.conflict("CONFIG_VERSION_CONFLICT", "源模块已变更，请刷新后重新复制");
        }
        if (!drafts.root(session.systemId()).draftRevision().equals(request.draftRevision())) {
            throw ConfigErrors.conflict("CONFIG_VERSION_CONFLICT", "配置草稿已变更，请刷新后重新复制");
        }

        var sourceFields = drafts.fields(session.systemId(), sourceModuleId);
        var sourcePages = drafts.pages(session.systemId(), sourceModuleId);
        var sourceActions = drafts.actions(session.systemId(), sourceModuleId);
        var sourceRules = drafts.rules(session.systemId(), sourceModuleId);
        var sourceComponents = new LinkedHashMap<String, List<ConfigViews.Component>>();
        sourcePages.forEach(page -> sourceComponents.put(page.id(),
                drafts.components(session.systemId(), sourceModuleId, Long.parseLong(page.id()))));

        var target = drafts.createModule(session, new ConfigRequests.CreateModule(
                request.groupId(), request.code(), request.name(), source.description(), source.iconKey(),
                source.sortOrder() + 1, source.status(), source.allowComments(), source.allowTeam(), revision(session)),
                innerKey(key, "module"), context);
        var targetModuleId = Long.parseLong(target.id());

        var fieldIds = new HashMap<String, String>();
        var copiedFields = new HashMap<String, ConfigViews.Field>();
        for (var field : sourceFields) {
            // Allocate every field id with a neutral draft first. Structured fields can refer to
            // fields declared later in the same module, while their exact contracts require those
            // references to be present even during creation.
            var copied = drafts.createField(session, targetModuleId, new ConfigRequests.CreateField(
                    "", "", field.code(), field.name(), FieldType.TEXT,
                    field.sortOrder(), false, field.hidden(), false, false,
                    false, field.showInList(), field.showInDetail(), IndexMode.NONE, field.status(),
                    JsonNodeFactory.instance.objectNode(),
                    null, null,
                    revision(session)),
                    innerKey(key, "field:" + field.id()), context);
            fieldIds.put(field.id(), copied.id());
            copiedFields.put(field.id(), copied);
        }

        var actionIds = new HashMap<String, String>();
        var copiedActions = new HashMap<String, ConfigViews.Action>();
        for (var action : sourceActions) {
            var copied = drafts.createAction(session, targetModuleId, new ConfigRequests.CreateAction(
                    action.code(), action.name(), action.type(), action.placement(), action.confirmMessage(),
                    action.sortOrder(), action.status(), without(action.properties(), "targetPageId"), revision(session)),
                    innerKey(key, "action:" + action.id()), context);
            actionIds.put(action.id(), copied.id());
            copiedActions.put(action.id(), copied);
        }

        var pageIds = copyPages(session, targetModuleId, sourcePages, key, context);
        for (var field : sourceFields) {
            var copied = copiedFields.get(field.id());
            var targetModuleReference = field.targetModuleId();
            if (source.id().equals(targetModuleReference)) targetModuleReference = target.id();
            drafts.updateField(session, targetModuleId, Long.parseLong(copied.id()), new ConfigRequests.UpdateField(
                    field.dictionaryId(), targetModuleReference, field.code(), field.name(), field.type(),
                    field.sortOrder(), field.required(), field.hidden(), field.readonly(), field.searchable(),
                    field.filterable(), field.showInList(), field.showInDetail(), field.indexMode(), field.status(),
                    remapFieldProperties(field.properties(), fieldIds), copiedPermissionMode(field.readPermissionMode()),
                    copiedPermissionMode(field.writePermissionMode()), copied.version(), revision(session)), context);
        }
        for (var action : sourceActions) {
            var copied = copiedActions.get(action.id());
            drafts.updateAction(session, targetModuleId, Long.parseLong(copied.id()), new ConfigRequests.UpdateAction(
                    copied.code(), copied.name(), copied.type(), copied.placement(), copied.confirmMessage(),
                    copied.sortOrder(), copied.status(), remapActionProperties(action.properties(), pageIds),
                    copied.version(), revision(session)), context);
        }
        for (var page : sourcePages) {
            copyComponents(session, targetModuleId, Long.parseLong(pageIds.get(page.id())),
                    sourceComponents.get(page.id()), fieldIds, actionIds, key, context);
        }

        for (var rule : sourceRules) {
            drafts.createRule(session, targetModuleId, new ConfigRequests.CreateRule(
                    rule.code(), rule.name(), rule.type(), rule.priority(),
                    remap(rule.condition(), fieldIds), remap(rule.effects(), fieldIds, actionIds),
                    rule.status(), revision(session)), innerKey(key, "rule:" + rule.id()), context);
        }
        return drafts.modules(session.systemId()).stream()
                .filter(module -> module.id().equals(target.id())).findFirst().orElseThrow();
    }

    private Map<String, String> copyPages(ConfigSession session, long targetModuleId,
                                          List<ConfigViews.Page> sources, String key, RequestContext context) {
        var available = new ArrayList<>(drafts.pages(session.systemId(), targetModuleId));
        var result = new HashMap<String, String>();
        for (var source : sources) {
            var target = available.stream()
                    .filter(page -> page.type() == source.type() && page.isDefault() == source.isDefault())
                    .findFirst().orElse(null);
            if (target == null) {
                target = drafts.createPage(session, targetModuleId, new ConfigRequests.CreatePage(
                        source.code(), source.name(), source.type(), source.isDefault(), source.status(),
                        source.layout(), revision(session)), innerKey(key, "page:" + source.id()), context);
            } else {
                available.remove(target);
                target = drafts.updatePage(session, targetModuleId, Long.parseLong(target.id()),
                        new ConfigRequests.UpdatePage(source.code(), source.name(), source.type(), source.isDefault(),
                                source.status(), source.layout(), target.version(), revision(session)), context);
            }
            result.put(source.id(), target.id());
        }
        return result;
    }

    private void copyComponents(ConfigSession session, long targetModuleId, long targetPageId,
                                List<ConfigViews.Component> sources, Map<String, String> fieldIds,
                                Map<String, String> actionIds, String key, RequestContext context) {
        var pending = new ArrayList<>(sources);
        var componentIds = new HashMap<String, String>();
        while (!pending.isEmpty()) {
            var copied = false;
            for (var iterator = pending.iterator(); iterator.hasNext();) {
                var source = iterator.next();
                if (source.parentComponentId() != null && !componentIds.containsKey(source.parentComponentId())) continue;
                var result = drafts.createComponent(session, targetModuleId, targetPageId,
                        new ConfigRequests.CreateComponent(
                                source.parentComponentId() == null ? null : componentIds.get(source.parentComponentId()),
                                source.fieldId() == null ? null : fieldIds.get(source.fieldId()), source.key(), source.type(),
                                source.sortOrder(), source.gridRow(), source.gridColumn(), source.gridSpan(),
                                remapProperties(source.properties(), actionIds), revision(session)),
                        innerKey(key, "component:" + source.id()), context);
                componentIds.put(source.id(), result.id());
                iterator.remove();
                copied = true;
            }
            if (!copied) throw ConfigErrors.invalid("源页面组件树包含无法解析的父组件引用");
        }
    }

    private ConfigRequests.Condition remap(ConfigRequests.Condition source, Map<String, String> fieldIds) {
        return new ConfigRequests.Condition(source.join(),
                source.fieldId() == null ? null : fieldIds.get(source.fieldId()), source.operator(), source.value(),
                source.children() == null ? List.of() : source.children().stream()
                        .map(child -> remap(child, fieldIds)).toList());
    }

    private List<ConfigRequests.Effect> remap(List<ConfigRequests.Effect> sources,
                                               Map<String, String> fieldIds, Map<String, String> actionIds) {
        return sources.stream().map(source -> {
            var targetId = source.targetId();
            if (targetId != null) targetId = fieldIds.getOrDefault(targetId, actionIds.getOrDefault(targetId, targetId));
            return new ConfigRequests.Effect(source.effect(), targetId, source.value());
        }).toList();
    }

    private JsonNode remapProperties(JsonNode source, Map<String, String> actionIds) {
        var copy = source.deepCopy();
        if (copy instanceof ObjectNode object && object.hasNonNull("actionId")) {
            var mapped = actionIds.get(object.path("actionId").asText());
            if (mapped != null) object.put("actionId", mapped);
        }
        return copy;
    }

    private JsonNode without(JsonNode source, String... keys) {
        var copy = source.deepCopy();
        if (copy instanceof ObjectNode object) object.remove(List.of(keys));
        return copy;
    }

    static com.unique.examine.module.manage.api.ConfigTypes.FieldPermissionMode copiedPermissionMode(
            com.unique.examine.module.manage.api.ConfigTypes.FieldPermissionMode source) {
        return source == null || source == com.unique.examine.module.manage.api.ConfigTypes.FieldPermissionMode.INHERIT
                ? com.unique.examine.module.manage.api.ConfigTypes.FieldPermissionMode.INHERIT
                : com.unique.examine.module.manage.api.ConfigTypes.FieldPermissionMode.STAGED;
    }

    private JsonNode remapFieldProperties(JsonNode source, Map<String, String> fieldIds) {
        var copy = source.deepCopy();
        if (!(copy instanceof ObjectNode object)) return copy;
        for (var key : List.of("displayFieldId", "valueFieldId", "sourceFieldId", "targetFieldId",
                "parentFieldId", "relationFieldId", "subtableFieldId")) {
            if (object.hasNonNull(key)) {
                var mapped = fieldIds.get(object.path(key).asText());
                if (mapped != null) object.put(key, mapped);
            }
        }
        if (object.path("sourceFieldIds").isArray()) {
            var values = object.putArray("sourceFieldIds");
            source.path("sourceFieldIds").forEach(item -> values.add(fieldIds.getOrDefault(item.asText(), item.asText())));
        }
        if (object.path("columnFieldIds").isArray()) {
            var values = object.putArray("columnFieldIds");
            source.path("columnFieldIds").forEach(item -> values.add(fieldIds.getOrDefault(item.asText(), item.asText())));
        }
        if (object.path("aggregates").isArray()) {
            object.path("aggregates").forEach(aggregate -> {
                if (aggregate instanceof ObjectNode item && item.hasNonNull("columnFieldId")) {
                    var current = item.path("columnFieldId").asText();
                    item.put("columnFieldId", fieldIds.getOrDefault(current, current));
                }
            });
        }
        if (object.path("filter").isObject()) remapConditionFields(object.path("filter"), fieldIds);
        if (object.path("expressionAst").isObject()) remapDerivedAstFields(object.path("expressionAst"), fieldIds);
        return object;
    }

    private void remapDerivedAstFields(JsonNode node, Map<String, String> fieldIds) {
        if (!(node instanceof ObjectNode object)) return;
        if (object.hasNonNull("fieldId")) {
            var current = object.path("fieldId").asText();
            object.put("fieldId", fieldIds.getOrDefault(current, current));
        }
        if (object.path("args").isArray()) {
            object.path("args").forEach(argument -> remapDerivedAstFields(argument, fieldIds));
        }
    }

    private void remapConditionFields(JsonNode node, Map<String, String> fieldIds) {
        if (!(node instanceof ObjectNode object)) return;
        if (object.hasNonNull("fieldId")) {
            var current = object.path("fieldId").asText();
            object.put("fieldId", fieldIds.getOrDefault(current, current));
        }
        if (object.path("children").isArray()) {
            object.path("children").forEach(child -> remapConditionFields(child, fieldIds));
        }
    }

    private JsonNode remapActionProperties(JsonNode source, Map<String, String> pageIds) {
        var copy = source.deepCopy();
        if (copy instanceof ObjectNode object && object.hasNonNull("targetPageId")) {
            var mapped = pageIds.get(object.path("targetPageId").asText());
            if (mapped != null) object.put("targetPageId", mapped);
        }
        return copy;
    }

    private String revision(ConfigSession session) {
        return drafts.root(session.systemId()).draftRevision();
    }

    private static String innerKey(String key, String resource) {
        return ConfigMutationSupport.sha256(key + ":copy:" + resource);
    }
}
