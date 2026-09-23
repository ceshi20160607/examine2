package com.unique.unexamine.flow.manage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.authorization.manage.ChannelFieldPolicyResolver;
import com.unique.unexamine.flow.base.entity.FlowNode;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 节点表单的安全目录、发布检查和字段身份快照；与分支条件目录独立，不限制字段家族。 */
@Service
public class FlowNodeFormService {
    private final FlowConditionService conditions;
    private final ChannelFieldPolicyResolver policies;
    private final ObjectMapper json;

    public FlowNodeFormService(FlowConditionService conditions, ChannelFieldPolicyResolver policies, ObjectMapper json) {
        this.conditions = conditions;
        this.policies = policies;
        this.json = json;
    }

    /** 目录只公开当前 PAGE/FLOW 可读字段的身份和能力，脱敏字段的值与配置均不输出。 */
    public List<FlowDesignModels.NodeFormField> fields(AuthenticatedContext actor, Long moduleId) {
        var publication = conditions.businessPublication(actor, moduleId);
        String code = publication.configuration().path("module").path("code").asText();
        List<JsonNode> active = new ArrayList<>();
        publication.configuration().path("fields").forEach(f -> {
            if ("ACTIVE".equals(f.path("status").asText())) active.add(f);
        });
        var access = policies.resolveIntersection(actor, code, "LIST", List.of("PAGE", "FLOW"),
                active.stream().map(f -> f.path("code").asText()).toList());
        return active.stream().filter(f -> access.containsKey(f.path("code").asText())
                && access.get(f.path("code").asText()).readable()).map(f -> {
            JsonNode config = read(f.path("configJson").asText("{}"));
            String type = f.path("fieldType").asText();
            return new FlowDesignModels.NodeFormField(f.path("id").longValue(), f.path("code").asText(),
                    f.path("name").asText(), type, !type.startsWith("SYSTEM_") && !config.path("readOnly").asBoolean(false),
                    optionalId(f, "dictionaryId"), optionalId(f, "referenceModuleId"));
        }).toList();
    }

    /** 发布前校验所有已声明的字段策略，不能将失效引用静默删成更宽松的缺省策略。 */
    public List<FlowDesignModels.Issue> inspect(AuthenticatedContext actor, List<FlowNode> nodes) {
        List<FlowNode> configured = nodes.stream().filter(n -> declared(read(n.getFormPolicyJson()))).toList();
        if (configured.isEmpty()) return List.of();
        try {
            Long moduleId = moduleId(nodes);
            var fields = fields(actor, moduleId);
            Set<String> codes = fields.stream().map(FlowDesignModels.NodeFormField::code).collect(Collectors.toSet());
            Set<String> editable = fields.stream().filter(FlowDesignModels.NodeFormField::editable)
                    .map(FlowDesignModels.NodeFormField::code).collect(Collectors.toSet());
            List<FlowDesignModels.Issue> issues = new ArrayList<>();
            for (FlowNode node : configured) {
                Map<String, Object> policy = json.convertValue(read(node.getFormPolicyJson()), new com.fasterxml.jackson.core.type.TypeReference<>() {});
                FlowNodeFieldPolicy.inspect(policy, codes, editable).forEach(message ->
                        issues.add(new FlowDesignModels.Issue("FLOW_NODE_FIELDS_INVALID", node.getNodeKey(), message)));
            }
            return issues;
        } catch (DomainException invalid) {
            return List.of(new FlowDesignModels.Issue("FLOW_NODE_FIELDS_INVALID", "graph", invalid.getMessage()));
        }
    }

    /** 发布冻结整个安全目录，使缺省可见也绑定当时字段；草稿或同编码新字段不改变旧实例。 */
    public Map<String, Object> snapshot(AuthenticatedContext actor, List<FlowNode> nodes) {
        if (nodes.stream().noneMatch(n -> declared(read(n.getFormPolicyJson())))) return Map.of();
        Long moduleId = moduleId(nodes);
        var publication = conditions.businessPublication(actor, moduleId);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("moduleId", moduleId);
        snapshot.put("moduleCode", publication.configuration().path("module").path("code").asText());
        snapshot.put("moduleVersionId", publication.versionId());
        snapshot.put("fields", fields(actor, moduleId));
        return snapshot;
    }

    private Long moduleId(List<FlowNode> nodes) {
        var start = nodes.stream().filter(n -> "START".equals(n.getNodeType())).findFirst().orElse(null);
        JsonNode config = start == null ? json.createObjectNode() : read(start.getConfigJson());
        if (!config.path("moduleId").isIntegralNumber() || !config.path("moduleId").canConvertToLong())
            throw invalid("节点字段策略必须先选择有效业务模块");
        return config.path("moduleId").longValue();
    }

    private boolean declared(JsonNode policy) {
        return FlowNodeFieldPolicy.KEYS.stream().anyMatch(policy::has);
    }

    private Long optionalId(JsonNode node, String key) {
        return node.path(key).isIntegralNumber() ? node.path(key).longValue() : null;
    }

    private JsonNode read(String value) {
        try {
            JsonNode result = json.readTree(value == null || value.isBlank() ? "{}" : value);
            if (!result.isObject()) throw invalid("节点字段配置必须是对象");
            return result;
        }
        catch (Exception malformed) { throw invalid("节点字段配置无法读取"); }
    }

    private DomainException invalid(String message) {
        return new DomainException("FLOW_NODE_FIELDS_INVALID", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
