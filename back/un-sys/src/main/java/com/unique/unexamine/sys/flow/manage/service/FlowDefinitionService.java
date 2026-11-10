package com.unique.unexamine.sys.flow.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.module.PublishedProcessSnapshot;
import com.unique.unexamine.core.module.ExamineStarter;
import com.unique.unexamine.core.module.TriggerConditionMatcher;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.sys.base.entity.UnSysLogOpt;
import com.unique.unexamine.sys.base.service.IUnSysLogOptService;
import com.unique.unexamine.sys.flow.base.entity.UnFlow;
import com.unique.unexamine.sys.flow.base.entity.UnFlowBinding;
import com.unique.unexamine.sys.flow.base.entity.UnFlowVersion;
import com.unique.unexamine.sys.flow.base.service.IUnFlowBindingService;
import com.unique.unexamine.sys.flow.base.service.IUnFlowService;
import com.unique.unexamine.sys.flow.base.service.IUnFlowVersionService;
import com.unique.unexamine.sys.flow.manage.dto.FlowDefinitionDto.FlowView;
import com.unique.unexamine.sys.flow.manage.dto.FlowDefinitionDto.SaveFlowRequest;
import com.unique.unexamine.sys.flow.manage.dto.FlowDefinitionDto.VersionView;
import com.unique.unexamine.sys.manage.dto.SystemManageDto.SystemContext;
import com.unique.unexamine.sys.manage.service.SystemManageService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Flow 定义管理服务。
 *
 * <p>保存会追加不可变发布版本，并让现有绑定指向新版本；运行实例已经保存自己的版本快照，
 * 因此修改或停用不会改写正在运行的 Flow。</p>
 */
@Service
public class FlowDefinitionService {
    private final SystemManageService systems;
    private final IUnFlowService flows;
    private final IUnFlowVersionService versions;
    private final IUnFlowBindingService bindings;
    private final IUnSysLogOptService logs;
    private final ObjectMapper json;
    private final ExamineStarter examineStarter;

    public FlowDefinitionService(SystemManageService systems, IUnFlowService flows,
                                 IUnFlowVersionService versions, IUnFlowBindingService bindings,
                                 IUnSysLogOptService logs, ObjectMapper json, ExamineStarter examineStarter) {
        this.systems = systems;
        this.flows = flows;
        this.versions = versions;
        this.bindings = bindings;
        this.logs = logs;
        this.json = json;
        this.examineStarter = examineStarter;
    }

    /** 查询当前系统租户的 Flow 定义，停用定义仍保留供管理员查看。 */
    public List<FlowView> list(String token, Long systemId, Long tenantId) {
        var context = systems.requireAdminContext(token, systemId, tenantId);
        return flows.list(new LambdaQueryWrapper<UnFlow>()
                        .eq(UnFlow::getSystemId, context.systemId())
                        .eq(UnFlow::getTenantId, context.tenantId())
                        .orderByAsc(UnFlow::getName).orderByAsc(UnFlow::getId))
                .stream().map(flow -> view(flow, false)).toList();
    }

    /** 查询 Flow 当前快照及所有已发布版本。 */
    public FlowView query(String token, Long systemId, Long tenantId, Long flowId) {
        var context = systems.requireAdminContext(token, systemId, tenantId);
        return view(owned(context, flowId), true);
    }

    /** 校验并发布新版本；保存成功后新绑定立即使用该版本。 */
    @Transactional(rollbackFor = Exception.class)
    public FlowView save(String token, Long systemId, Long tenantId, SaveFlowRequest request) {
        var context = systems.requireAdminContext(token, systemId, tenantId);
        if (request == null || request.code() == null || request.code().isBlank() || request.code().length() > 80
                || request.name() == null || request.name().isBlank() || request.name().length() > 120
                || request.description() != null && request.description().length() > 500)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "Flow 定义资料无效");
        validateCode(request.code());
        JsonNode snapshot = validateSnapshot(request.snapshot());
        // 发布前检查审批引用，避免把其他系统或租户的审批定义写入可触发的 Flow。
        validateExamineReferences(snapshot, context.systemId(), context.tenantId(), null);
        // 同一 Flow 的版本号由 latestVersion 串行递增；锁住定义行后再创建不可变版本。
        UnFlow flow = flows.getOne(new LambdaQueryWrapper<UnFlow>()
                .eq(UnFlow::getSystemId, context.systemId())
                .eq(UnFlow::getTenantId, context.tenantId())
                .eq(UnFlow::getCode, request.code().trim())
                .last("FOR UPDATE"), false);
        if (flow != null) {
            // 保存新版本会让现有绑定立即切换，必须按每个绑定模块核对审批引用。
            for (UnFlowBinding binding : bindings.list(new LambdaQueryWrapper<UnFlowBinding>()
                    .eq(UnFlowBinding::getFlowId, flow.getId())
                    .in(UnFlowBinding::getStatus, List.of("ACTIVE", "PUBLISHED"))))
                validateExamineReferences(snapshot, context.systemId(), context.tenantId(), binding.getModuleId());
        }
        FlowView before = flow == null ? null : view(flow, true);
        if (flow == null) {
            flow = new UnFlow();
            flow.setScopeType("SYSTEM");
            flow.setSystemId(context.systemId());
            flow.setTenantId(context.tenantId());
            flow.setCode(request.code().trim());
            flow.setLatestVersion(0);
            flow.setCreateAccountId(context.accountId());
            flow.setCreateTime(LocalDateTime.now());
        }
        flow.setName(request.name().trim());
        flow.setDescription(request.description());
        flow.setSettingsJson(writeJson(request.settings()));
        flow.setStatus("ACTIVE");
        // 新定义先取得主键，发布版本通过 flow_id 形成稳定关联。
        flow.setUpdateTime(LocalDateTime.now());
        flows.addOrUpdate(flow);

        int versionNo = (flow.getLatestVersion() == null ? 0 : flow.getLatestVersion()) + 1;
        UnFlowVersion version = new UnFlowVersion();
        version.setFlowId(flow.getId());
        version.setVersionNo(versionNo);
        version.setSnapshotJson(writeJson(snapshot));
        version.setSnapshotHash(hash(snapshot));
        version.setPublishAccountId(context.accountId());
        version.setPublishTime(LocalDateTime.now());
        versions.addOrUpdate(version);
        flow.setLatestVersion(versionNo);
        flow.setUpdateTime(LocalDateTime.now());
        flows.addOrUpdate(flow);

        // 绑定是未来触发入口；历史实例记录的 flow_version_id 不会被改写。
        bindings.update(new LambdaUpdateWrapper<UnFlowBinding>()
                .eq(UnFlowBinding::getFlowId, flow.getId())
                .eq(UnFlowBinding::getSystemId, context.systemId())
                .eq(UnFlowBinding::getTenantId, context.tenantId())
                .in(UnFlowBinding::getStatus, List.of("ACTIVE", "PUBLISHED"))
                .set(UnFlowBinding::getFlowVersionId, version.getId())
                .set(UnFlowBinding::getPublishedVersion, versionNo)
                .set(UnFlowBinding::getUpdateTime, LocalDateTime.now()));

        FlowView after = view(flow, true);
        audit(context, "FLOW_DEFINITION_SAVE", flow.getId(), before, after);
        return after;
    }

    /** 停用 Flow 与其当前租户绑定；已经建立的 Flow 实例继续按原版本执行。 */
    @Transactional(rollbackFor = Exception.class)
    public FlowView disable(String token, Long systemId, Long tenantId, Long flowId) {
        var context = systems.requireAdminContext(token, systemId, tenantId);
        UnFlow flow = owned(context, flowId);
        FlowView before = view(flow, true);
        flow.setStatus("DISABLED");
        flow.setUpdateTime(LocalDateTime.now());
        flows.addOrUpdate(flow);
        bindings.update(new LambdaUpdateWrapper<UnFlowBinding>()
                .eq(UnFlowBinding::getFlowId, flowId)
                .eq(UnFlowBinding::getSystemId, context.systemId())
                .eq(UnFlowBinding::getTenantId, context.tenantId())
                .in(UnFlowBinding::getStatus, List.of("ACTIVE", "PUBLISHED"))
                .set(UnFlowBinding::getStatus, "DISABLED")
                .set(UnFlowBinding::getUpdateTime, LocalDateTime.now()));
        FlowView after = view(flow, true);
        audit(context, "FLOW_DEFINITION_DISABLE", flowId, before, after);
        return after;
    }

    private UnFlow owned(SystemContext context, Long flowId) {
        UnFlow flow = flows.getById(flowId);
        if (flow == null || !context.systemId().equals(flow.getSystemId())
                || !context.tenantId().equals(flow.getTenantId()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "Flow 定义不存在");
        return flow;
    }

    /** 校验节点审批引用属于当前作用域及即将生效的模块绑定。 */
    private void validateExamineReferences(JsonNode snapshot, Long systemId, Long tenantId, Long moduleId) {
        for (JsonNode node : PublishedProcessSnapshot.nodes(json, snapshot.toString())) {
            Long examineId = PublishedProcessSnapshot.examineId(node);
            if (examineId == null) continue;
            examineStarter.validateReference(systemId, tenantId, moduleId, examineId);
        }
    }

    /** 校验节点编码、节点类型、起点和连线引用，避免运行时才发现流程图损坏。 */
    private JsonNode validateSnapshot(JsonNode snapshot) {
        if (snapshot == null || !snapshot.isObject())
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "Flow 快照必须是包含 nodes 的 JSON 对象");
        List<JsonNode> nodes;
        try {
            nodes = PublishedProcessSnapshot.nodes(json, snapshot.toString());
        } catch (IllegalArgumentException ex) {
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, ex.getMessage(), ex);
        }
        if (nodes.isEmpty()) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "Flow 快照至少需要一个节点");
        Set<String> codes = new HashSet<>();
        for (JsonNode node : nodes) {
            String code = PublishedProcessSnapshot.code(node);
            if (!codes.add(code)) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "Flow 节点编码重复: " + code);
            if (PublishedProcessSnapshot.type(node).isBlank())
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "Flow 节点类型不能为空: " + code);
            try {
                PublishedProcessSnapshot.accountCandidates(node);
                PublishedProcessSnapshot.externalCandidates(node);
            }
            catch (IllegalArgumentException ex) {
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "Flow 节点候选人配置无效: " + code, ex);
            }
        }
        if (PublishedProcessSnapshot.startNodes(nodes).isEmpty())
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "Flow 快照至少需要一个开始节点");
        JsonNode edges = snapshot.get("edges");
        if (edges != null) validateEdges(edges, codes);
        return snapshot;
    }

    private void validateEdges(JsonNode edges, Set<String> codes) {
        if (!edges.isArray()) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "Flow edges 必须是数组");
        for (JsonNode edge : edges) {
            String source = edge.path("sourceNodeCode").asText("");
            String target = edge.path("targetNodeCode").asText("");
            if (!codes.contains(source) || !codes.contains(target))
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "Flow 线路引用了不存在的节点");
            String condition = edge.has("condition") ? edge.get("condition").toString() : null;
            try {
                TriggerConditionMatcher.validate(json, condition);
            } catch (IllegalArgumentException ex) {
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, ex.getMessage(), ex);
            }
        }
    }

    private void validateCode(String code) {
        if (code == null || !code.trim().matches("[A-Za-z][A-Za-z0-9_]{1,79}"))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "Flow 编码只能使用字母、数字和下划线，且必须以字母开头");
    }

    private String writeJson(JsonNode value) {
        if (value == null || value.isNull()) return null;
        try { return json.writeValueAsString(value); }
        catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "Flow 配置无法保存", ex); }
    }

    private String hash(JsonNode snapshot) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(json.writeValueAsBytes(snapshot));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) result.append(String.format("%02x", value));
            return result.toString();
        } catch (Exception ex) {
            throw new BaseException(SystemCodeEnum.SYSTEM_ERROR, "Flow 快照摘要无法生成", ex);
        }
    }

    private FlowView view(UnFlow flow, boolean includeSnapshot) {
        UnFlowVersion latest = versions.getOne(new LambdaQueryWrapper<UnFlowVersion>()
                .eq(UnFlowVersion::getFlowId, flow.getId())
                .eq(UnFlowVersion::getVersionNo, flow.getLatestVersion()), false);
        JsonNode snapshot = includeSnapshot && latest != null ? readJson(latest.getSnapshotJson()) : null;
        List<VersionView> history = versions.list(new LambdaQueryWrapper<UnFlowVersion>()
                        .eq(UnFlowVersion::getFlowId, flow.getId())
                        .orderByDesc(UnFlowVersion::getVersionNo))
                .stream().map(version -> new VersionView(version.getId(), version.getVersionNo(),
                        version.getSnapshotHash(), version.getPublishAccountId(), version.getPublishTime())).toList();
        return new FlowView(flow.getId(), flow.getScopeType(), flow.getSystemId(), flow.getTenantId(),
                flow.getCode(), flow.getName(), flow.getDescription(), flow.getStatus(), flow.getLatestVersion(),
                snapshot, flow.getUpdateTime(), history);
    }

    private JsonNode readJson(String text) {
        try { return text == null ? null : json.readTree(text); }
        catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "Flow 发布快照无效", ex); }
    }

    private void audit(SystemContext context, String actionCode, Long flowId,
                       FlowView before, FlowView after) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("before", before);
        detail.put("after", after);
        UnSysLogOpt log = new UnSysLogOpt();
        log.setSystemId(context.systemId());
        log.setTenantId(context.tenantId());
        log.setAccountId(context.accountId());
        log.setActionCode(actionCode);
        log.setObjectType("FLOW_DEFINITION");
        log.setObjectId(flowId);
        try { log.setDetailJson(json.writeValueAsString(detail)); }
        catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_ERROR, "Flow 配置审计内容无法保存", ex); }
        logs.addOrUpdate(log);
    }
}
