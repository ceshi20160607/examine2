package com.unique.unexamine.flow.manage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.authorization.manage.PermissionChecker;
import com.unique.unexamine.flow.base.entity.FlowEdge;
import com.unique.unexamine.flow.base.entity.FlowNode;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModule;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleBaseService;
import com.unique.unexamine.moduleconfig.manage.ModulePublicationService;
import com.unique.unexamine.moduleconfig.manage.PublishedConditionCatalog;
import com.unique.unexamine.moduleconfig.manage.RuntimeModuleConfiguration;
import com.unique.unexamine.moduleconfig.manage.StructuredConditionProtocol;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** 审批设计、模拟和实例选路共用条件协议，实例只使用自身发布的字段类型快照。 */
@Service
public class FlowConditionService {
    private static final Pattern LEGACY = Pattern.compile("^([A-Za-z_][A-Za-z0-9_.-]*)\\s*(==|!=|>=|<=|>|<)\\s*(.+)$");
    private final ConfiguredModuleBaseService modules;
    private final ModulePublicationService publications;
    private final PublishedConditionCatalog catalog;
    private final StructuredConditionProtocol protocol;
    private final ObjectMapper json;
    private final PermissionChecker permissions;

    public FlowConditionService(ConfiguredModuleBaseService modules, ModulePublicationService publications,
            PublishedConditionCatalog catalog, StructuredConditionProtocol protocol, ObjectMapper json, PermissionChecker permissions) {
        this.modules = modules;
        this.publications = publications;
        this.catalog = catalog;
        this.protocol = protocol;
        this.json = FlowJsonStorage.mapper(json);
        this.permissions = permissions;
    }

    /** 设计者的字段目录以当前租户有效发布配置及 PAGE/FLOW 可读交集为准。 */
    public List<FlowBindingModels.ConditionField> fields(AuthenticatedContext actor, Long moduleId) {
        var module = module(actor, moduleId);
        var publication = publication(actor, module);
        return catalog.readableFields(actor, module.getCode(), publication, "LIST", List.of("PAGE", "FLOW")).stream()
                .map(field -> new FlowBindingModels.ConditionField(field.code(), field.name(), field.fieldType(), field.config().toString())).toList();
    }

    /** 节点表单复用相同模块归属及有效发布解析，避免另建一套来源判断。 */
    public RuntimeModuleConfiguration businessPublication(AuthenticatedContext actor, Long moduleId) {
        return publication(actor, module(actor, moduleId));
    }

    /** 模块引用位于开始节点配置，沿用现有可扩展存储，不为每个流程新增物理表或 Java 业务类。 */
    public Map<String, Object> context(AuthenticatedContext actor, List<FlowNode> nodes) {
        var start = nodes.stream().filter(node -> "START".equals(node.getNodeType())).findFirst().orElse(null);
        if (start == null) return Map.of();
        JsonNode config = read(start.getConfigJson());
        if (!config.hasNonNull("moduleId")) return Map.of();
        if (!config.path("moduleId").isIntegralNumber() || !config.path("moduleId").canConvertToLong())
            throw invalid("请选择有效的业务模块");
        var module = module(actor, config.path("moduleId").longValue());
        var publication = publication(actor, module);
        var fields = catalog.readableFields(actor, module.getCode(), publication, "LIST", List.of("PAGE", "FLOW"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("moduleId", module.getId());
        result.put("moduleCode", module.getCode());
        result.put("moduleVersionId", publication.versionId());
        result.put("fields", fields);
        List<Map<String, Object>> identities = new ArrayList<>();
        for (JsonNode field : publication.configuration().path("fields")) {
            if (fields.stream().noneMatch(visible -> visible.code().equals(field.path("code").asText()))) continue;
            Map<String, Object> identity = new LinkedHashMap<>();
            identity.put("code", field.path("code").asText());
            identity.put("id", field.path("id").asLong());
            identity.put("fieldType", field.path("fieldType").asText());
            identity.put("dictionaryId", field.path("dictionaryId").isNull() ? null : field.path("dictionaryId").asLong());
            identities.add(identity);
        }
        result.put("fieldBindings", identities);
        return result;
    }

    /** 新动作发起前重查来源字段身份与当前选项；在途实例仍只读取旧快照，不调用本方法。 */
    public void validateCurrent(AuthenticatedContext actor, Long moduleId, Map<String, Object> snapshot) {
        JsonNode definition = json.valueToTree(snapshot);
        JsonNode profile = definition.path("conditionContext");
        if (!profile.hasNonNull("moduleId")) return;
        if (profile.path("moduleId").asLong() != moduleId)
            throw invalid("流程业务模块与动作模块不一致，请重新选择流程");
        var module = modules.selectById(moduleId);
        if (module == null || !Objects.equals(actor.systemId(), module.getSystemId()) || !"ACTIVE".equals(module.getStatus()))
            throw invalid("流程业务模块不存在或已停用");
        var publication = publication(actor, module);
        var currentFields = catalog.fields(actor, publication);
        List<JsonNode> conditions = new ArrayList<>();
        definition.path("edges").forEach(edge -> conditions.add(edge.path("config").path("condition")));
        definition.path("nodes").forEach(node -> {
            JsonNode repeat = node.path("config").path("repeatApproval");
            if (repeat.path("enabled").asBoolean(false)) conditions.add(repeat.path("condition"));
        });
        for (JsonNode condition : conditions) {
            if (condition.isMissingNode()) continue;
            Set<String> referenced = new HashSet<>();
            collectFields(condition, referenced);
            for (JsonNode frozen : profile.path("fieldBindings")) {
                if (!referenced.contains(frozen.path("code").asText())) continue;
                JsonNode current = null;
                for (JsonNode field : publication.configuration().path("fields"))
                    if (field.path("code").asText().equals(frozen.path("code").asText())) current = field;
                if (current == null || current.path("id").asLong() != frozen.path("id").asLong()
                        || !current.path("fieldType").equals(frozen.path("fieldType"))
                        || !Objects.equals(current.path("dictionaryId").isNull() ? null : current.path("dictionaryId").asLong(),
                                          frozen.path("dictionaryId").isNull() ? null : frozen.path("dictionaryId").asLong()))
                    throw invalid("流程条件字段身份或类型已改变，请重新检查并发布流程");
            }
            protocol.normalize(condition, currentFields, "FLOW_CONDITION_INVALID");
        }
    }

    private void collectFields(JsonNode condition, Set<String> fields) {
        if (condition.has("conditions")) condition.path("conditions").forEach(child -> collectFields(child, fields));
        else fields.add(condition.path("field").asText());
    }

    /** 发布只冻结分支真正引用的字段，避免把无关内部字段目录带入流程版本响应。 */
    public Map<String, Object> snapshotContext(AuthenticatedContext actor, List<FlowNode> nodes, List<FlowEdge> edges) {
        Map<String, Object> context = new LinkedHashMap<>(context(actor, nodes));
        if (context.isEmpty()) return context;
        Set<String> referenced = new HashSet<>();
        for (FlowEdge edge : edges) {
            JsonNode condition = read(edge.getConfigJson()).path("condition");
            if (!condition.isMissingNode()) collectFields(condition, referenced);
        }
        for (FlowNode node : nodes) {
            JsonNode repeat = read(node.getConfigJson()).path("repeatApproval");
            if (repeat.path("enabled").asBoolean(false) && repeat.has("condition"))
                collectFields(repeat.path("condition"), referenced);
        }
        context.put("fields", profileFields(context).stream().filter(field -> referenced.contains(field.code())).toList());
        List<JsonNode> identities = new ArrayList<>();
        for (JsonNode identity : json.valueToTree(context.get("fieldBindings")))
            if (referenced.contains(identity.path("code").asText())) identities.add(identity);
        context.put("fieldBindings", identities);
        return context;
    }

    /** 保存草稿可暂不连通，但发布前必须校验模块和每份结构化条件，不能静默退回无条件边。 */
    public List<FlowDesignModels.Issue> inspect(AuthenticatedContext actor, List<FlowNode> nodes, List<FlowEdge> edges) {
        List<FlowDesignModels.Issue> issues = new ArrayList<>();
        Map<String, Object> context;
        try { context = context(actor, nodes); }
        catch (DomainException error) {
            issues.add(new FlowDesignModels.Issue("FLOW_BUSINESS_MODULE_INVALID", "graph", error.getMessage()));
            return issues;
        }
        for (var edge : edges) {
            JsonNode condition = read(edge.getConfigJson()).path("condition");
            if (condition.isMissingNode()) continue;
            try {
                if (context.isEmpty()) throw invalid("结构化分支条件必须选择已发布的业务模块");
                if (edge.getConditionExpression() != null && !edge.getConditionExpression().isBlank())
                    throw invalid("同一连线不能同时配置新条件和旧表达式");
                protocol.normalize(condition, profileFields(context), "FLOW_CONDITION_INVALID");
            } catch (DomainException error) {
                issues.add(new FlowDesignModels.Issue("FLOW_CONDITION_INVALID", "edge:" + edge.getEdgeKey(), error.getMessage()));
            }
        }
        // 后续自动审批与分支条件共用类型目录和冻结字段身份，不另建表达式解释器。
        for (FlowNode node : nodes) {
            JsonNode repeat = read(node.getConfigJson()).path("repeatApproval");
            if (!repeat.path("enabled").asBoolean(false) || !repeat.has("condition")) continue;
            try {
                JsonNode condition = repeat.path("condition");
                if (context.isEmpty()) throw invalid("附加自动审批条件必须选择已发布的业务模块");
                if (!condition.path("conditions").isArray() || condition.path("conditions").isEmpty())
                    throw invalid("附加自动审批条件不能为空，请配置条件或关闭附加限制");
                protocol.normalize(condition, profileFields(context), "FLOW_REPEAT_APPROVAL_CONDITION_INVALID");
            } catch (DomainException error) {
                issues.add(new FlowDesignModels.Issue("FLOW_REPEAT_APPROVAL_CONDITION_INVALID",
                        node.getNodeKey(), error.getMessage()));
            }
        }
        return issues;
    }

    /** 设计模拟和已发布实例使用相同入口；旧快照的表达式只读取兼容，不改写历史定义。 */
    public boolean matches(Map<String, Object> context, JsonNode edgeConfig, String legacyExpression, Map<String, Object> variables) {
        JsonNode condition = edgeConfig.path("condition");
        if (condition.isMissingNode()) return matchesLegacy(legacyExpression, variables);
        if (context.isEmpty()) throw invalid("流程缺少业务字段类型快照");
        return protocol.matches(condition, json.valueToTree(variables), profileFields(context));
    }

    /** 从实例不可变快照还原字段类型与安全选项，不重新读取最新模板。 */
    private List<StructuredConditionProtocol.Field> profileFields(Map<String, Object> context) {
        return json.convertValue(context.getOrDefault("fields", List.of()), new TypeReference<>() { });
    }

    private ConfiguredModule module(AuthenticatedContext actor, Long id) {
        var module = modules.selectById(id);
        if (actor.systemId() == null || module == null || !Objects.equals(actor.systemId(), module.getSystemId())
                || !"ACTIVE".equals(module.getStatus())) throw invalid("业务模块不在当前系统或已停用");
        if (!permissions.allows(actor, "MODULE", module.getCode(), "LIST"))
            throw new DomainException("PERMISSION_DENIED", "没有业务模块列表权限", HttpStatus.FORBIDDEN);
        return module;
    }

    private RuntimeModuleConfiguration publication(AuthenticatedContext actor, ConfiguredModule module) {
        var publication = publications.published(actor, module.getCode());
        if (!Objects.equals(module.getId(), publication.moduleId())) throw invalid("业务模块不是当前租户有效发布配置");
        return publication;
    }

    private JsonNode read(String value) {
        try { return json.readTree(value == null ? "{}" : value); }
        catch (Exception malformed) { throw invalid("流程条件配置无法读取"); }
    }

    private boolean matchesLegacy(String expression, Map<String, Object> variables) {
        // 旧实例沿用既有运行表达式语义；默认路径由空表达式表示，不把历史无效脚本改成放行。
        if (expression == null || expression.isBlank()) return true;
        var matcher = LEGACY.matcher(expression.strip());
        if (!matcher.matches()) return false;
        Object actual = variables.get(matcher.group(1));
        String operator = matcher.group(2);
        String text = matcher.group(3).strip();
        Object expected;
        if ("null".equalsIgnoreCase(text)) expected = null;
        else if ("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text)) expected = Boolean.valueOf(text);
        else if ((text.startsWith("\"") && text.endsWith("\"")) || (text.startsWith("'") && text.endsWith("'"))) expected = text.substring(1, text.length() - 1);
        else { try { expected = new BigDecimal(text); } catch (NumberFormatException literal) { expected = text; } }
        if (actual == null) return "==".equals(operator) && expected == null;
        int compared;
        if (actual instanceof Number || expected instanceof Number) {
            try { compared = new BigDecimal(String.valueOf(actual)).compareTo(new BigDecimal(String.valueOf(expected))); }
            catch (NumberFormatException incompatible) { return false; }
        } else compared = String.valueOf(actual).compareTo(String.valueOf(expected));
        return switch (operator) {
            case "==" -> compared == 0;
            case "!=" -> compared != 0;
            case ">" -> compared > 0;
            case ">=" -> compared >= 0;
            case "<" -> compared < 0;
            case "<=" -> compared <= 0;
            default -> false;
        };
    }

    private DomainException invalid(String message) {
        return new DomainException("FLOW_CONDITION_INVALID", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
