package com.unique.unexamine.examine.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.module.ExamineDefinitionAccess;
import com.unique.unexamine.core.module.ExamineDefinitionAudit;
import com.unique.unexamine.core.module.PublishedProcessSnapshot;
import com.unique.unexamine.core.module.TriggerConditionMatcher;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.examine.base.entity.UnExamine;
import com.unique.unexamine.examine.base.entity.UnExamineGroup;
import com.unique.unexamine.examine.base.service.IUnExamineGroupService;
import com.unique.unexamine.examine.base.service.IUnExamineService;
import com.unique.unexamine.examine.manage.dto.ExamineDefinitionDto.DefinitionView;
import com.unique.unexamine.examine.manage.dto.ExamineDefinitionDto.SaveDefinitionRequest;
import com.unique.unexamine.examine.manage.strategy.ExamineNodeStrategyResolver;
import com.unique.unexamine.examine.manage.strategy.ApiNodeConfigValidator;
import com.unique.unexamine.examine.manage.strategy.ApiNodeSecretProtector;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 审批定义管理服务；保存即校验、生成新发布版本并启用，运行实例继续使用自己的快照。 */
@Service
public class ExamineDefinitionService {
    private final ExamineDefinitionAccess access;
    private final ExamineDefinitionAudit audit;
    private final IUnExamineService examines;
    private final IUnExamineGroupService groups;
    private final ObjectMapper json;
    private final ExamineNodeStrategyResolver strategies;
    private final ApiNodeConfigValidator apiValidator;
    private final ApiNodeSecretProtector secrets;

    public ExamineDefinitionService(ExamineDefinitionAccess access, ExamineDefinitionAudit audit,
                                    IUnExamineService examines, IUnExamineGroupService groups,
                                    ObjectMapper json, ExamineNodeStrategyResolver strategies,
                                    ApiNodeConfigValidator apiValidator, ApiNodeSecretProtector secrets) {
        this.access = access;
        this.audit = audit;
        this.examines = examines;
        this.groups = groups;
        this.json = json;
        this.strategies = strategies;
        this.apiValidator = apiValidator;
        this.secrets = secrets;
    }

    /** 查询当前系统租户下的审批定义；停用定义仍返回，供管理端恢复或查看。 */
    public List<DefinitionView> list(String token, Long systemId, Long tenantId) {
        ExamineDefinitionAccess.Context context = access.requireAdmin(token, systemId, tenantId);
        return examines.list(new LambdaQueryWrapper<UnExamine>()
                        .eq(UnExamine::getSystemId, context.systemId())
                        .eq(UnExamine::getTenantId, context.tenantId())
                        .orderByAsc(UnExamine::getSortNo).orderByAsc(UnExamine::getId))
                .stream().map(this::view).toList();
    }

    /** 查询单个审批定义及当前发布快照。 */
    public DefinitionView query(String token, Long systemId, Long tenantId, Long examineId) {
        ExamineDefinitionAccess.Context context = access.requireAdmin(token, systemId, tenantId);
        return view(owned(context, examineId));
    }

    /** 保存审批定义并立即启用新版本；运行中的实例不读取本次新快照。 */
    @Transactional(rollbackFor = Exception.class)
    public DefinitionView save(String token, Long systemId, Long tenantId, SaveDefinitionRequest request) {
        ExamineDefinitionAccess.Context context = access.requireAdmin(token, systemId, tenantId);
        if (request == null || request.code() == null || request.code().isBlank() || request.code().length() > 80
                || request.name() == null || request.name().isBlank() || request.name().length() > 120
                || request.description() != null && request.description().length() > 500
                || request.icon() != null && request.icon().length() > 255
                || request.groupId() != null && request.groupId() < 1
                || request.moduleId() != null && request.moduleId() < 1
                || request.sortNo() != null && request.sortNo() < 0)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "审批定义资料无效");
        validateCode(request.code());
        access.validateModule(context, request.moduleId());
        validateGroup(context, request.groupId());
        // 审批发布版本号由当前定义行递增；锁行后再保存新快照，避免并发保存覆盖版本。
        UnExamine examine = examines.getOne(new LambdaQueryWrapper<UnExamine>()
                .eq(UnExamine::getSystemId, context.systemId())
                .eq(UnExamine::getTenantId, context.tenantId())
                .eq(UnExamine::getCode, request.code().trim())
                .last("FOR UPDATE"), false);
        JsonNode previous = examine == null ? null : readSnapshot(examine);
        JsonNode snapshot = validateSnapshot(secrets.restore(request.snapshot(), previous));
        access.validateFieldKeys(context, request.moduleId(), snapshotFieldKeys(context, request.moduleId(), snapshot));
        DefinitionView before = examine == null ? null : view(examine);
        if (examine == null) {
            examine = new UnExamine();
            examine.setSystemId(context.systemId());
            examine.setTenantId(context.tenantId());
            examine.setCode(request.code().trim());
            examine.setPublishedVersion(0);
            examine.setCreateAccountId(context.accountId());
            examine.setCreateTime(LocalDateTime.now());
        }
        examine.setGroupId(request.groupId());
        examine.setModuleId(request.moduleId());
        examine.setName(request.name().trim());
        examine.setIcon(blankToNull(request.icon()));
        examine.setDescription(request.description());
        examine.setSortNo(request.sortNo() == null ? 0 : request.sortNo());
        examine.setStatus("ACTIVE");
        examine.setPublishedVersion((examine.getPublishedVersion() == null ? 0 : examine.getPublishedVersion()) + 1);
        try { examine.setPublishedSnapshotJson(json.writeValueAsString(snapshot)); }
        catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "审批快照无法保存", ex); }
        examine.setUpdateTime(LocalDateTime.now());
        examines.addOrUpdate(examine);
        DefinitionView after = view(examine);
        audit.record(context, "EXAMINE_DEFINITION_SAVE", examine.getId(), detail(before, after));
        return after;
    }

    /** 停用审批定义；已有审批实例保存了发布快照，不受停用影响。 */
    @Transactional(rollbackFor = Exception.class)
    public DefinitionView disable(String token, Long systemId, Long tenantId, Long examineId) {
        ExamineDefinitionAccess.Context context = access.requireAdmin(token, systemId, tenantId);
        UnExamine examine = owned(context, examineId);
        DefinitionView before = view(examine);
        examine.setStatus("DISABLED");
        examine.setUpdateTime(LocalDateTime.now());
        examines.addOrUpdate(examine);
        DefinitionView after = view(examine);
        audit.record(context, "EXAMINE_DEFINITION_DISABLE", examine.getId(), detail(before, after));
        return after;
    }

    private UnExamine owned(ExamineDefinitionAccess.Context context, Long examineId) {
        UnExamine examine = examines.getById(examineId);
        if (examine == null || !context.systemId().equals(examine.getSystemId())
                || !context.tenantId().equals(examine.getTenantId()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "审批定义不存在");
        return examine;
    }

    /** 分组引用必须属于当前系统租户且仍启用。 */
    private void validateGroup(ExamineDefinitionAccess.Context context, Long groupId) {
        if (groupId == null) return;
        UnExamineGroup group = groups.queryById(groupId);
        if (group == null || !context.systemId().equals(group.getSystemId())
                || !context.tenantId().equals(group.getTenantId()) || !"ACTIVE".equals(group.getStatus()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "审批分组不存在或已停用");
    }

    /** 校验设计器提交的节点、分支和节点字段配置，避免发布后运行时才发现图不完整。 */
    private JsonNode validateSnapshot(JsonNode snapshot) {
        if (snapshot == null || !snapshot.isObject())
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "审批快照必须是包含 nodes 和 edges 的 JSON 对象");
        ExamineEmptyApproverPolicy.validateDefinition(snapshot);
        List<JsonNode> nodes;
        try { nodes = PublishedProcessSnapshot.nodes(json, snapshot.toString()); }
        catch (RuntimeException ex) { throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "审批快照节点配置无效", ex); }
        if (nodes.isEmpty()) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "审批快照至少需要一个节点");
        if (PublishedProcessSnapshot.startNodes(nodes).isEmpty())
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "审批快照至少需要一个开始节点");
        Set<String> codes = new HashSet<>();
        for (JsonNode node : nodes) {
            String code = PublishedProcessSnapshot.code(node);
            if (!codes.add(code)) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "审批节点编码重复: " + code);
            if (PublishedProcessSnapshot.type(node).isBlank()) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "审批节点类型不能为空: " + code);
            try {
                strategies.resolve(PublishedProcessSnapshot.type(node));
                if (strategies.resolve(PublishedProcessSnapshot.type(node)) instanceof com.unique.unexamine.examine.manage.strategy.ApiNodeStrategy)
                    apiValidator.validate(node, code);
                // 外部审批候选人必须在保存配置时确定应用命名空间和外部标识，不能等到实例启动才失败。
                PublishedProcessSnapshot.accountCandidates(node);
                PublishedProcessSnapshot.externalCandidates(node);
                ExamineEmptyApproverPolicy.validateNode(node, code);
                validateFieldKeys(node, code);
            } catch (BaseException ex) {
                throw ex;
            } catch (RuntimeException ex) {
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "审批节点配置无效: " + code, ex);
            }
        }
        // 审批运行仅读取顶层线路；发布时必须使用同一位置，避免实例启动后无法推进。
        JsonNode edges = snapshot.get("edges");
        if (edges == null || !edges.isArray() || edges.isEmpty())
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "审批快照必须包含非空的顶层 edges 数组");
        validateEdges(edges, codes);
        return snapshot;
    }

    /** 汇总审批节点读写及 API 返回映射的字段 key，统一交给模块边界校验。 */
    private Set<String> snapshotFieldKeys(ExamineDefinitionAccess.Context context, Long moduleId, JsonNode snapshot) {
        Set<String> keys = new HashSet<>();
        for (JsonNode node : PublishedProcessSnapshot.nodes(json, snapshot.toString())) {
            for (String name : List.of("readableFieldKeys", "writableFieldKeys", "editableFieldKeys")) {
                JsonNode values = node.get(name);
                if (values == null) values = node.path("config").get(name);
                if (values != null && values.isArray()) values.forEach(value -> keys.add(value.asText()));
            }
            if (strategies.resolve(PublishedProcessSnapshot.type(node)) instanceof com.unique.unexamine.examine.manage.strategy.ApiNodeStrategy)
                keys.addAll(apiValidator.mappedFieldKeys(node));
        }
        JsonNode edges = PublishedProcessSnapshot.edges(json, snapshot.toString());
        if (edges != null && edges.isArray()) edges.forEach(edge -> {
            String condition = edge.has("condition") ? edge.get("condition").toString() : null;
            keys.addAll(TriggerConditionMatcher.fieldKeys(json, condition));
            access.validateConditionValues(context, moduleId, condition);
        });
        return keys;
    }

    private void validateFieldKeys(JsonNode node, String code) {
        for (String key : List.of("readableFieldKeys", "writableFieldKeys", "editableFieldKeys")) {
            JsonNode values = node.get(key);
            if (values == null) values = node.path("config").get(key);
            if (values != null && !values.isArray()) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "节点 " + code + " 的 " + key + " 必须是数组");
            if (values != null) values.forEach(item -> {
                if (!item.isTextual() || item.asText().isBlank()) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "节点 " + code + " 的字段 key 无效");
            });
        }
    }

    private void validateEdges(JsonNode edges, Set<String> codes) {
        if (!edges.isArray()) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "审批 edges 必须是数组");
        for (JsonNode edge : edges) {
            if (List.of("source_code", "source", "sourceNodeId", "source_node_id",
                    "target_code", "target", "targetNodeId", "target_node_id",
                    "conditionJson", "condition_json", "priority_no", "exclusive_flag")
                    .stream().anyMatch(edge::has))
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "审批线路必须使用当前字段编码");
            String source = edge.path("sourceNodeCode").asText("");
            String target = edge.path("targetNodeCode").asText("");
            if (!codes.contains(source) || !codes.contains(target))
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "审批线路引用了不存在的节点");
            String condition = edge.has("condition") ? edge.get("condition").toString() : null;
            try { TriggerConditionMatcher.validate(json, condition); }
            catch (IllegalArgumentException ex) { throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, ex.getMessage(), ex); }
        }
    }

    private void validateCode(String code) {
        if (code == null || !code.trim().matches("[A-Za-z][A-Za-z0-9_]{1,79}"))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "审批编码只能使用字母、数字和下划线，且必须以字母开头");
    }

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    /** 审计记录保存前后的定义快照，便于追溯节点和线路配置变化。 */
    private String detail(DefinitionView before, DefinitionView after) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("before", before);
        detail.put("after", after);
        try {
            return json.writeValueAsString(detail);
        } catch (Exception ex) {
            throw new BaseException(SystemCodeEnum.SYSTEM_ERROR, "审批定义审计内容无法保存", ex);
        }
    }

    private DefinitionView view(UnExamine examine) {
        JsonNode snapshot = secrets.mask(readSnapshot(examine));
        return new DefinitionView(examine.getId(), examine.getSystemId(), examine.getTenantId(), examine.getGroupId(),
                examine.getModuleId(), examine.getCode(), examine.getName(), examine.getIcon(), examine.getDescription(),
                examine.getSortNo(), examine.getStatus(), examine.getPublishedVersion(), snapshot, examine.getUpdateTime());
    }

    private JsonNode readSnapshot(UnExamine examine) {
        if (examine.getPublishedSnapshotJson() == null) return null;
        try { return json.readTree(examine.getPublishedSnapshotJson()); }
        catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "审批发布快照无效", ex); }
    }
}
