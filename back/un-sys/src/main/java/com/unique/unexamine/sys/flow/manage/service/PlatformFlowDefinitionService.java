package com.unique.unexamine.sys.flow.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.plat.base.entity.UnPlatLogOpt;
import com.unique.unexamine.plat.base.service.IUnPlatLogOptService;
import com.unique.unexamine.plat.manage.service.AuthManageService;
import com.unique.unexamine.sys.flow.base.entity.UnFlow;
import com.unique.unexamine.sys.flow.base.entity.UnFlowVersion;
import com.unique.unexamine.sys.flow.base.service.IUnFlowService;
import com.unique.unexamine.sys.flow.base.service.IUnFlowVersionService;
import com.unique.unexamine.sys.flow.manage.dto.FlowDefinitionDto.FlowView;
import com.unique.unexamine.sys.flow.manage.dto.FlowDefinitionDto.SaveFlowRequest;
import com.unique.unexamine.sys.flow.manage.dto.FlowDefinitionDto.VersionView;
import com.unique.unexamine.core.module.PublishedProcessSnapshot;
import com.unique.unexamine.core.module.TriggerConditionMatcher;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
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

/** 平台 Flow 定义管理；平台作用域没有系统租户，但版本快照规则与系统 Flow 一致。 */
@Service
public class PlatformFlowDefinitionService {
    private final AuthManageService auth;
    private final IUnFlowService flows;
    private final IUnFlowVersionService versions;
    private final IUnPlatLogOptService logs;
    private final ObjectMapper json;

    public PlatformFlowDefinitionService(AuthManageService auth, IUnFlowService flows,
            IUnFlowVersionService versions, IUnPlatLogOptService logs, ObjectMapper json) {
        this.auth = auth;
        this.flows = flows;
        this.versions = versions;
        this.logs = logs;
        this.json = json;
    }

    /** 查询平台管理员可维护的 Flow，停用定义仍保留。 */
    public List<FlowView> list(String token) {
        auth.requirePlatformAdmin(token);
        return flows.list(new LambdaQueryWrapper<UnFlow>().eq(UnFlow::getScopeType, "PLATFORM")
                        .isNull(UnFlow::getSystemId).isNull(UnFlow::getTenantId)
                        .orderByAsc(UnFlow::getName).orderByAsc(UnFlow::getId))
                .stream().map(flow -> view(flow, false)).toList();
    }

    /** 查询平台 Flow 当前快照及版本历史。 */
    public FlowView query(String token, Long flowId) {
        auth.requirePlatformAdmin(token);
        return view(owned(flowId), true);
    }

    /** 保存平台 Flow 并立即启用新的不可变发布版本。 */
    @Transactional(rollbackFor = Exception.class)
    public FlowView save(String token, SaveFlowRequest request) {
        Long accountId = auth.requirePlatformAdmin(token).getId();
        if (request == null || request.code() == null || request.code().isBlank() || request.code().length() > 80
                || request.name() == null || request.name().isBlank() || request.name().length() > 120
                || request.description() != null && request.description().length() > 500)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "平台 Flow 定义资料无效");
        validateCode(request.code());
        JsonNode snapshot = validateSnapshot(request.snapshot());
        // 平台 Flow 同样按定义行串行递增版本，避免两个管理员生成重复 versionNo。
        UnFlow flow = flows.getOne(new LambdaQueryWrapper<UnFlow>().eq(UnFlow::getScopeType, "PLATFORM")
                .isNull(UnFlow::getSystemId).isNull(UnFlow::getTenantId)
                .eq(UnFlow::getCode, request.code().trim())
                .last("FOR UPDATE"), false);
        FlowView before = flow == null ? null : view(flow, true);
        if (flow == null) {
            flow = new UnFlow();
            flow.setScopeType("PLATFORM");
            flow.setCode(request.code().trim());
            flow.setLatestVersion(0);
            flow.setCreateAccountId(accountId);
            flow.setCreateTime(LocalDateTime.now());
        }
        flow.setSystemId(null);
        flow.setTenantId(null);
        flow.setName(request.name().trim());
        flow.setDescription(request.description());
        flow.setSettingsJson(writeJson(request.settings()));
        flow.setStatus("ACTIVE");
        flow.setUpdateTime(LocalDateTime.now());
        flows.addOrUpdate(flow);
        int versionNo = (flow.getLatestVersion() == null ? 0 : flow.getLatestVersion()) + 1;
        UnFlowVersion version = new UnFlowVersion();
        version.setFlowId(flow.getId());
        version.setVersionNo(versionNo);
        version.setSnapshotJson(writeJson(snapshot));
        version.setSnapshotHash(hash(snapshot));
        version.setPublishAccountId(accountId);
        version.setPublishTime(LocalDateTime.now());
        versions.addOrUpdate(version);
        flow.setLatestVersion(versionNo);
        flow.setUpdateTime(LocalDateTime.now());
        flows.addOrUpdate(flow);
        audit("PLATFORM_FLOW_SAVE", flow.getId(), before, view(flow, true), accountId);
        return view(flow, true);
    }

    /** 停用平台 Flow；历史运行实例不会被改写。 */
    @Transactional(rollbackFor = Exception.class)
    public FlowView disable(String token, Long flowId) {
        Long accountId = auth.requirePlatformAdmin(token).getId();
        UnFlow flow = owned(flowId);
        FlowView before = view(flow, true);
        flow.setStatus("DISABLED");
        flow.setUpdateTime(LocalDateTime.now());
        flows.addOrUpdate(flow);
        FlowView after = view(flow, true);
        audit("PLATFORM_FLOW_DISABLE", flowId, before, after, accountId);
        return after;
    }

    private UnFlow owned(Long flowId) {
        UnFlow flow = flows.getById(flowId);
        if (flow == null || !"PLATFORM".equals(flow.getScopeType())
                || flow.getSystemId() != null || flow.getTenantId() != null)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "平台 Flow 不存在");
        return flow;
    }

    private JsonNode validateSnapshot(JsonNode snapshot) {
        if (snapshot == null || !snapshot.isObject())
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "Flow 快照必须是包含 nodes 的 JSON 对象");
        List<JsonNode> nodes;
        try { nodes = PublishedProcessSnapshot.nodes(json, snapshot.toString()); }
        catch (RuntimeException ex) { throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "Flow 快照节点配置无效", ex); }
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
        JsonNode edges = snapshot.isObject() ? snapshot.get("edges") : null;
        if (edges != null) {
            if (!edges.isArray()) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "Flow edges 必须是数组");
            for (JsonNode edge : edges) {
                String source = edge.path("sourceNodeCode").asText("");
                String target = edge.path("targetNodeCode").asText("");
                if (!codes.contains(source) || !codes.contains(target))
                    throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "Flow 线路引用了不存在的节点");
                String condition = edge.has("condition") ? edge.get("condition").toString() : null;
                try { TriggerConditionMatcher.validate(json, condition); }
                catch (IllegalArgumentException ex) { throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, ex.getMessage(), ex); }
            }
        }
        return snapshot;
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
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(snapshot));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) result.append(String.format("%02x", value));
            return result.toString();
        } catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_ERROR, "Flow 快照摘要无法生成", ex); }
    }

    private FlowView view(UnFlow flow, boolean includeSnapshot) {
        UnFlowVersion latest = versions.getOne(new LambdaQueryWrapper<UnFlowVersion>()
                .eq(UnFlowVersion::getFlowId, flow.getId())
                .eq(UnFlowVersion::getVersionNo, flow.getLatestVersion()), false);
        JsonNode snapshot = includeSnapshot && latest != null ? readJson(latest.getSnapshotJson()) : null;
        List<VersionView> history = versions.list(new LambdaQueryWrapper<UnFlowVersion>()
                        .eq(UnFlowVersion::getFlowId, flow.getId()).orderByDesc(UnFlowVersion::getVersionNo))
                .stream().map(version -> new VersionView(version.getId(), version.getVersionNo(),
                        version.getSnapshotHash(), version.getPublishAccountId(), version.getPublishTime())).toList();
        return new FlowView(flow.getId(), flow.getScopeType(), flow.getSystemId(), flow.getTenantId(),
                flow.getCode(), flow.getName(), flow.getDescription(), flow.getStatus(), flow.getLatestVersion(),
                snapshot, flow.getUpdateTime(), history);
    }

    private JsonNode readJson(String text) {
        try { return text == null ? null : json.readTree(text); }
        catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "Flow 发布快照无效", ex); }
    }

    private void audit(String code, Long objectId, FlowView before, FlowView after, Long accountId) {
        UnPlatLogOpt log = new UnPlatLogOpt();
        log.setAccountId(accountId);
        log.setActionCode(code);
        log.setObjectType("FLOW_DEFINITION");
        log.setObjectId(objectId);
        try {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("before", before); detail.put("after", after);
            log.setDetailJson(json.writeValueAsString(detail));
        } catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_ERROR, "平台 Flow 审计内容无法保存", ex); }
        logs.addOrUpdate(log);
    }
}
