package com.unique.unexamine.examine.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.module.PublishedProcessSnapshot;
import com.unique.unexamine.core.module.TriggerConditionMatcher;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.examine.base.entity.UnExamine;
import com.unique.unexamine.examine.base.service.IUnExamineService;
import com.unique.unexamine.examine.manage.dto.ExamineDefinitionDto.DefinitionView;
import com.unique.unexamine.examine.manage.dto.ExamineDefinitionDto.SaveDefinitionRequest;
import com.unique.unexamine.examine.manage.strategy.ExamineNodeStrategyResolver;
import com.unique.unexamine.examine.manage.strategy.ApiNodeConfigValidator;
import com.unique.unexamine.examine.manage.strategy.ApiNodeSecretProtector;
import com.unique.unexamine.plat.base.entity.UnPlatLogOpt;
import com.unique.unexamine.plat.base.service.IUnPlatLogOptService;
import com.unique.unexamine.plat.manage.service.AuthManageService;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 平台内审批定义管理；平台审批不绑定业务模块，运行实例可由平台受控入口发起。 */
@Service
public class PlatformExamineDefinitionService {
    private final AuthManageService auth;
    private final IUnExamineService examines;
    private final IUnPlatLogOptService logs;
    private final ObjectMapper json;
    private final ExamineNodeStrategyResolver strategies;
    private final ApiNodeConfigValidator apiValidator;
    private final ApiNodeSecretProtector secrets;

    public PlatformExamineDefinitionService(AuthManageService auth, IUnExamineService examines,
            IUnPlatLogOptService logs, ObjectMapper json, ExamineNodeStrategyResolver strategies,
            ApiNodeConfigValidator apiValidator, ApiNodeSecretProtector secrets) {
        this.auth = auth;
        this.examines = examines;
        this.logs = logs;
        this.json = json;
        this.strategies = strategies;
        this.apiValidator = apiValidator;
        this.secrets = secrets;
    }

    /** 查询平台审批定义。 */
    public List<DefinitionView> list(String token) {
        auth.requirePlatformAdmin(token);
        return examines.list(new LambdaQueryWrapper<UnExamine>().isNull(UnExamine::getSystemId)
                        .isNull(UnExamine::getTenantId).orderByAsc(UnExamine::getSortNo)
                        .orderByAsc(UnExamine::getId))
                .stream().map(this::view).toList();
    }

    /** 查询平台审批当前发布快照。 */
    public DefinitionView query(String token, Long examineId) {
        auth.requirePlatformAdmin(token);
        return view(owned(examineId));
    }

    /** 保存并立即启用新的平台审批快照。 */
    @Transactional(rollbackFor = Exception.class)
    public DefinitionView save(String token, SaveDefinitionRequest request) {
        Long accountId = auth.requirePlatformAdmin(token).getId();
        if (request == null || request.code() == null || request.code().isBlank() || request.code().length() > 80
                || request.name() == null || request.name().isBlank() || request.name().length() > 120
                || request.description() != null && request.description().length() > 500
                || request.icon() != null && request.icon().length() > 255
                || request.sortNo() != null && request.sortNo() < 0)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "平台审批定义资料无效");
        validateCode(request.code());
        // 平台审批发布版本号也必须在定义行锁内递增，保证并发保存的快照顺序稳定。
        UnExamine examine = examines.getOne(new LambdaQueryWrapper<UnExamine>().isNull(UnExamine::getSystemId)
                .isNull(UnExamine::getTenantId).eq(UnExamine::getCode, request.code().trim())
                .last("FOR UPDATE"), false);
        JsonNode previous = examine == null ? null : readSnapshot(examine);
        JsonNode snapshot = validateSnapshot(secrets.restore(request.snapshot(), previous));
        DefinitionView before = examine == null ? null : view(examine);
        if (examine == null) {
            examine = new UnExamine();
            examine.setSystemId(null);
            examine.setTenantId(null);
            examine.setCode(request.code().trim());
            examine.setPublishedVersion(0);
            examine.setCreateAccountId(accountId);
            examine.setCreateTime(LocalDateTime.now());
        }
        examine.setGroupId(null);
        examine.setModuleId(null);
        examine.setName(request.name().trim());
        examine.setIcon(request.icon() == null || request.icon().isBlank() ? null : request.icon().trim());
        examine.setDescription(request.description());
        examine.setSortNo(request.sortNo() == null ? 0 : request.sortNo());
        examine.setStatus("ACTIVE");
        examine.setPublishedVersion((examine.getPublishedVersion() == null ? 0 : examine.getPublishedVersion()) + 1);
        try { examine.setPublishedSnapshotJson(json.writeValueAsString(snapshot)); }
        catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "审批快照无法保存", ex); }
        examine.setUpdateTime(LocalDateTime.now());
        examines.addOrUpdate(examine);
        DefinitionView after = view(examine);
        audit("PLATFORM_EXAMINE_SAVE", examine.getId(), before, after, accountId);
        return after;
    }

    /** 停用平台审批定义；已运行实例仍使用发起时快照。 */
    @Transactional(rollbackFor = Exception.class)
    public DefinitionView disable(String token, Long examineId) {
        Long accountId = auth.requirePlatformAdmin(token).getId();
        UnExamine examine = owned(examineId);
        DefinitionView before = view(examine);
        examine.setStatus("DISABLED");
        examine.setUpdateTime(LocalDateTime.now());
        examines.addOrUpdate(examine);
        DefinitionView after = view(examine);
        audit("PLATFORM_EXAMINE_DISABLE", examineId, before, after, accountId);
        return after;
    }

    private UnExamine owned(Long id) {
        UnExamine examine = examines.getById(id);
        if (examine == null || examine.getSystemId() != null || examine.getTenantId() != null)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "平台审批定义不存在");
        return examine;
    }

    private JsonNode validateSnapshot(JsonNode snapshot) {
        if (snapshot == null || !snapshot.isObject())
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "审批快照必须是包含 nodes 和 edges 的 JSON 对象");
        ExamineEmptyApproverPolicy.validateDefinition(snapshot);
        List<JsonNode> nodes;
        try { nodes = PublishedProcessSnapshot.nodes(json, snapshot.toString()); }
        catch (RuntimeException ex) { throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "审批快照节点配置无效", ex); }
        if (nodes.isEmpty()) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "审批快照至少需要一个节点");
        Set<String> codes = new HashSet<>();
        for (JsonNode node : nodes) {
            String code = PublishedProcessSnapshot.code(node);
            if (!codes.add(code)) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "审批节点编码重复: " + code);
            if (PublishedProcessSnapshot.type(node).isBlank())
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "审批节点类型不能为空: " + code);
            try {
                strategies.resolve(PublishedProcessSnapshot.type(node));
                if (strategies.resolve(PublishedProcessSnapshot.type(node)) instanceof com.unique.unexamine.examine.manage.strategy.ApiNodeStrategy)
                    apiValidator.validate(node, code);
                PublishedProcessSnapshot.accountCandidates(node);
                PublishedProcessSnapshot.externalCandidates(node);
                ExamineEmptyApproverPolicy.validateNode(node, code, true);
                validateFieldKeys(node, code);
            } catch (BaseException ex) {
                throw ex;
            } catch (RuntimeException ex) {
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "审批节点配置无效: " + code, ex);
            }
        }
        if (PublishedProcessSnapshot.startNodes(nodes).isEmpty())
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "审批快照至少需要一个开始节点");
        // 平台审批与模块审批共用运行解析规则，线路只能保存为顶层 edges。
        JsonNode edges = snapshot.get("edges");
        if (edges == null || !edges.isArray() || edges.isEmpty())
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "审批快照必须包含非空的顶层 edges 数组");
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
        return snapshot;
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

    private void validateCode(String code) {
        if (code == null || !code.trim().matches("[A-Za-z][A-Za-z0-9_]{1,79}"))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "审批编码只能使用字母、数字和下划线，且必须以字母开头");
    }

    private DefinitionView view(UnExamine examine) {
        JsonNode snapshot = secrets.mask(readSnapshot(examine));
        return new DefinitionView(examine.getId(), examine.getSystemId(), examine.getTenantId(), null, null,
                examine.getCode(), examine.getName(), examine.getIcon(), examine.getDescription(),
                examine.getSortNo(), examine.getStatus(), examine.getPublishedVersion(), snapshot,
                examine.getUpdateTime());
    }

    private JsonNode readSnapshot(UnExamine examine) {
        if (examine.getPublishedSnapshotJson() == null) return null;
        try { return json.readTree(examine.getPublishedSnapshotJson()); }
        catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "审批发布快照无效", ex); }
    }

    private void audit(String action, Long objectId, DefinitionView before, DefinitionView after, Long accountId) {
        UnPlatLogOpt log = new UnPlatLogOpt();
        log.setAccountId(accountId); log.setActionCode(action); log.setObjectType("EXAMINE_DEFINITION");
        log.setObjectId(objectId);
        try {
            Map<String, Object> detail = new LinkedHashMap<>(); detail.put("before", before); detail.put("after", after);
            log.setDetailJson(json.writeValueAsString(detail));
        } catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_ERROR, "平台审批审计内容无法保存", ex); }
        logs.addOrUpdate(log);
    }
}
