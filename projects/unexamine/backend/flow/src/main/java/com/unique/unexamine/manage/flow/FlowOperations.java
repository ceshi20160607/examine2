package com.unique.unexamine.manage.flow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.unique.unexamine.base.entity.*;
import com.unique.unexamine.base.mapper.*;
import com.unique.unexamine.manage.module.ModuleRecordAccess;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/** 流程定义与运行共同使用的图规则、租户校验和实例状态操作。 */
@Component
public final class FlowOperations {
    private final UnFlowMapper flows;
    private final UnFlowNodeMapper nodes;
    private final UnFlowEdgeMapper edges;
    private final UnFlowExampleMapper examples;
    private final UnFlowTaskMapper tasks;
    private final UnFlowTaskCandidateMapper candidates;
    private final UnFlowEventMapper events;
    private final UnFlowBindingMapper bindings;
    private final UnFlowJobMapper jobs;
    private final UnFlowExceptionMapper exceptions;
    private final UnModuleRecordMapper records;
    private final UnSysTenantMemberMapper tenantMembers;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ModuleRecordAccess recordAccess;

    FlowOperations(UnFlowMapper flows, UnFlowNodeMapper nodes, UnFlowEdgeMapper edges,
                   UnFlowExampleMapper examples, UnFlowTaskMapper tasks,
                   UnFlowTaskCandidateMapper candidates, UnFlowEventMapper events,
                   UnFlowBindingMapper bindings, UnFlowJobMapper jobs,
                   UnFlowExceptionMapper exceptions, UnModuleRecordMapper records,
                   UnSysTenantMemberMapper tenantMembers, JdbcTemplate jdbc,
                   ObjectMapper json, ModuleRecordAccess recordAccess) {
        this.flows = flows;
        this.nodes = nodes;
        this.edges = edges;
        this.examples = examples;
        this.tasks = tasks;
        this.candidates = candidates;
        this.events = events;
        this.bindings = bindings;
        this.jobs = jobs;
        this.exceptions = exceptions;
        this.records = records;
        this.tenantMembers = tenantMembers;
        this.jdbc = jdbc;
        this.json = json;
        this.recordAccess = recordAccess;
    }

    UnFlow flowInTenant(long tenantId, long flowId) {
        UnFlow flow = flows.selectById(flowId);
        if (flow == null || !flowIdEquals(flow.getTenantId(), tenantId)) notFound("流程不存在");
        return flow;
    }

    UnFlowExample exampleInTenant(long tenantId, long exampleId) {
        UnFlowExample example = examples.selectById(exampleId);
        if (example == null || !flowIdEquals(example.getTenantId(), tenantId)) notFound("流程实例不存在");
        return example;
    }

    /** 锁定主记录使同一实例的并发操作串行，事务同时提交状态与历史。 */
    UnFlowExample lockExample(long tenantId, long exampleId) {
        jdbc.queryForList("SELECT id FROM un_flow_example WHERE id = ? AND tenant_id = ? FOR UPDATE", exampleId, tenantId);
        return exampleInTenant(tenantId, exampleId);
    }

    UnFlow lockFlow(long tenantId, long flowId) {
        jdbc.queryForList("SELECT id FROM un_flow WHERE id = ? AND tenant_id = ? FOR UPDATE", flowId, tenantId);
        return flowInTenant(tenantId, flowId);
    }

    UnFlowBinding lockBinding(long tenantId, long bindingId) {
        jdbc.queryForList("SELECT id FROM un_flow_binding WHERE id = ? AND tenant_id = ? FOR UPDATE", bindingId, tenantId);
        UnFlowBinding binding = bindings.selectById(bindingId);
        if (binding == null || !flowIdEquals(binding.getTenantId(), tenantId)) notFound("自动流程绑定不存在");
        return binding;
    }

    void requireModule(long tenantId, Long moduleId) {
        if (moduleId == null) bad("模块主键不能为空");
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM un_module module
                JOIN un_sys_tenant tenant ON tenant.system_id = module.system_id
                WHERE module.id = ? AND tenant.id = ? AND module.status = 1 AND tenant.status = 1
                """, Long.class, moduleId, tenantId);
        if (count == null || count == 0) bad("模块不属于此租户系统或已停用");
    }

    List<UnFlowNode> draftNodes(long flowId) {
        return nodes.selectList(new LambdaQueryWrapper<UnFlowNode>().eq(UnFlowNode::getFlowId, flowId).orderByAsc(UnFlowNode::getSortNum));
    }

    List<UnFlowEdge> draftEdges(long flowId) {
        return edges.selectList(new LambdaQueryWrapper<UnFlowEdge>().eq(UnFlowEdge::getFlowId, flowId).orderByAsc(UnFlowEdge::getSortNum));
    }

    /** 每个分支留一条默认线，条件按 sortNum 顺序匹配，且所有节点必须可达并最终结束。 */
    void validateGraph(List<UnFlowNode> nodeList, List<UnFlowEdge> edgeList, long tenantId) {
        if (nodeList.stream().filter(n -> "START".equals(n.getNodeType())).count() != 1
                || nodeList.stream().filter(n -> "END".equals(n.getNodeType())).count() != 1) bad("流程必须各有一个开始和结束节点");
        Map<String, UnFlowNode> byKey = new HashMap<>();
        for (UnFlowNode node : nodeList) {
            if (byKey.put(node.getNodeKey(), node) != null) bad("流程节点标识重复");
            if (!Set.of("START", "TASK", "END").contains(node.getNodeType())) bad("流程节点类型无效");
            if ("TASK".equals(node.getNodeType())) {
                if (node.getAssigneeType() == null || !Set.of("MEMBER", "STARTER", "ROLE").contains(node.getAssigneeType()))
                    bad("人工任务处理人来源无效");
                if ("MEMBER".equals(node.getAssigneeType())) requireTenantMember(tenantId, node.getAssigneeId());
                if ("ROLE".equals(node.getAssigneeType()) && roleMembers(tenantId, node.getAssigneeId()).isEmpty())
                    bad("角色不属于此租户、未启用或没有有效处理人");
            }
        }
        Map<String, List<UnFlowEdge>> outgoingByKey = new HashMap<>();
        for (UnFlowEdge edge : edgeList) {
            if (!byKey.containsKey(edge.getSourceNodeKey()) || !byKey.containsKey(edge.getTargetNodeKey())) bad("连线节点不存在");
            if ("START".equals(byKey.get(edge.getTargetNodeKey()).getNodeType())) bad("连线不能返回开始节点");
            if (hasRule(edge.getConditionRule())) rule(edge.getConditionRule());
            outgoingByKey.computeIfAbsent(edge.getSourceNodeKey(), key -> new ArrayList<>()).add(edge);
        }
        for (UnFlowNode node : nodeList) {
            List<UnFlowEdge> outgoing = outgoingByKey.getOrDefault(node.getNodeKey(), List.of());
            if ("END".equals(node.getNodeType())) {
                if (!outgoing.isEmpty()) bad("结束节点不能有连线");
                continue;
            }
            if (outgoing.isEmpty() || outgoing.stream().filter(e -> !hasRule(e.getConditionRule())).count() != 1)
                bad("每个非结束节点须有一条默认连线");
            Set<Integer> positions = new HashSet<>();
            for (UnFlowEdge edge : outgoing) if (!positions.add(edge.getSortNum())) bad("同一节点的连线顺序不能重复");
        }
        Set<String> visited = new HashSet<>();
        visit(nodeList.stream().filter(n -> "START".equals(n.getNodeType())).findFirst().orElseThrow().getNodeKey(),
                outgoingByKey, new HashSet<>(), visited);
        if (visited.size() != nodeList.size()) bad("流程存在不可达节点");
    }

    private void visit(String key, Map<String, List<UnFlowEdge>> outgoing, Set<String> active, Set<String> visited) {
        if (active.contains(key)) bad("流程不能形成循环");
        if (visited.contains(key)) return;
        active.add(key);
        for (UnFlowEdge edge : outgoing.getOrDefault(key, List.of())) visit(edge.getTargetNodeKey(), outgoing, active, visited);
        active.remove(key);
        visited.add(key);
    }

    /** 只接受字段等值或数字比较的固定 JSON 结构，禁止脚本和动态 SQL。 */
    JsonNode rule(String source) {
        try {
            JsonNode value = json.readTree(source);
            if (!value.isObject() || value.size() != 3 || !value.has("field") || !value.has("operator") || !value.has("value")
                    || !value.get("field").isTextual() || !value.get("field").asText().matches("[a-zA-Z][a-zA-Z0-9_]{0,63}")
                    || !value.get("operator").isTextual()) bad("条件只支持 field、operator、value 三个字段");
            String operator = value.get("operator").asText().toUpperCase(Locale.ROOT);
            JsonNode expected = value.get("value");
            if (!Set.of("EQ", "NE", "GT", "GTE", "LT", "LTE").contains(operator)
                    || !(expected.isTextual() || expected.isNumber())
                    || (!Set.of("EQ", "NE").contains(operator) && !expected.isNumber())) bad("条件操作符或比较值无效");
            return value;
        } catch (ResponseStatusException ex) { throw ex; }
        catch (Exception ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "条件不是有效 JSON", ex); }
    }

    boolean hasRule(String source) { return source != null && !source.isBlank(); }

    /** 条件按顺序先匹配，未命中时走唯一默认线；缺少字段值不命中任何条件。 */
    String nextNode(DraftSnapshot snapshot, String key, long tenantId, Long moduleId, Long recordId) {
        List<UnFlowEdge> outgoing = snapshot.edges().stream().filter(e -> key.equals(e.getSourceNodeKey()))
                .sorted(Comparator.comparingInt(UnFlowEdge::getSortNum)).toList();
        for (UnFlowEdge edge : outgoing) if (hasRule(edge.getConditionRule())
                && matches(rule(edge.getConditionRule()), tenantId, moduleId, recordId)) return edge.getTargetNodeKey();
        return outgoing.stream().filter(e -> !hasRule(e.getConditionRule())).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "发布快照缺少默认连线")).getTargetNodeKey();
    }

    private boolean matches(JsonNode condition, long tenantId, Long moduleId, Long recordId) {
        if (moduleId == null || recordId == null) return false;
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT value_text, value_number FROM un_module_record_data
                WHERE tenant_id = ? AND module_id = ? AND record_id = ? AND field_name = ?
                """, tenantId, moduleId, recordId, condition.get("field").asText());
        if (rows.isEmpty()) return false;
        JsonNode expected = condition.get("value");
        String operator = condition.get("operator").asText().toUpperCase(Locale.ROOT);
        Object actual = rows.getFirst().get(expected.isNumber() ? "value_number" : "value_text");
        if (actual == null) return false;
        int comparison = expected.isNumber() ? new BigDecimal(actual.toString()).compareTo(expected.decimalValue())
                : actual.toString().compareTo(expected.asText());
        return switch (operator) {
            case "EQ" -> comparison == 0;
            case "NE" -> comparison != 0;
            case "GT" -> comparison > 0;
            case "GTE" -> comparison >= 0;
            case "LT" -> comparison < 0;
            case "LTE" -> comparison <= 0;
            default -> false;
        };
    }

    /** 进入人工节点时解析真实租户成员，结束节点直接关闭实例。 */
    void enterNode(long tenantId, UnFlowExample example, DraftSnapshot snapshot, String key) {
        UnFlowNode node = snapshot.nodes().stream().filter(n -> key.equals(n.getNodeKey())).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "发布快照缺少节点"));
        if ("END".equals(node.getNodeType())) {
            example.setStatus("COMPLETED");
            example.setFinishTime(LocalDateTime.now());
            example.setCurrentNodeKey(null);
            examples.updateById(example);
            event(example.getId(), null, null, "FINISHED", null, "流程已完成");
            return;
        }
        if (!"TASK".equals(node.getNodeType())) conflict("运行节点类型无效");
        List<Long> assignees;
        if ("ROLE".equals(node.getAssigneeType())) assignees = roleMembers(tenantId, node.getAssigneeId());
        else {
            long assignee = "STARTER".equals(node.getAssigneeType()) ? example.getStarterMemberId() : node.getAssigneeId();
            requireTenantMember(tenantId, assignee);
            assignees = List.of(assignee);
        }
        if (assignees.isEmpty()) conflict("任务角色当前没有有效处理人");
        UnFlowTask task = new UnFlowTask();
        task.setExampleId(example.getId());
        task.setNodeKey(key);
        task.setRoundNo(1);
        task.setStatus("PENDING");
        tasks.insert(task);
        for (Long assignee : assignees) {
            UnFlowTaskCandidate candidate = new UnFlowTaskCandidate();
            candidate.setTaskId(task.getId());
            candidate.setMemberId(assignee);
            candidate.setStatus("PENDING");
            candidates.insert(candidate);
        }
        event(example.getId(), task.getId(), null, "TASK_CREATED", null, node.getName());
    }

    /** 角色成员同时核对角色、租户关系及系统账号的启用状态。 */
    List<Long> roleMembers(long tenantId, Long roleId) {
        if (roleId == null) return List.of();
        return jdbc.queryForList("""
                SELECT DISTINCT tm.member_id FROM un_sys_role role
                JOIN un_sys_member_role relation ON relation.role_id = role.id
                JOIN un_sys_tenant_member tm ON tm.id = relation.tenant_member_id
                JOIN un_sys_member sm ON sm.id = tm.member_id
                WHERE role.id = ? AND role.tenant_id = ? AND role.status = 1
                  AND tm.tenant_id = ? AND tm.status = 1 AND sm.status = 1
                ORDER BY tm.member_id
                """, Long.class, roleId, tenantId, tenantId);
    }

    void cancelCandidates(long taskId, LocalDateTime now) {
        for (UnFlowTaskCandidate candidate : candidates.selectList(new LambdaQueryWrapper<UnFlowTaskCandidate>()
                .eq(UnFlowTaskCandidate::getTaskId, taskId).eq(UnFlowTaskCandidate::getStatus, "PENDING"))) {
            candidate.setStatus("CANCELLED");
            candidate.setActionTime(now);
            candidates.updateById(candidate);
        }
    }

    void cancelJobs(long taskId, LocalDateTime now) {
        for (UnFlowJob job : jobs.selectList(new LambdaQueryWrapper<UnFlowJob>()
                .eq(UnFlowJob::getTaskId, taskId).eq(UnFlowJob::getStatus, "PENDING"))) {
            job.setStatus("CANCELLED");
            job.setFinishTime(now);
            jobs.updateById(job);
        }
    }

    UnFlowException recordException(long exampleId, Long taskId, String type, String detail) {
        UnFlowException incident = new UnFlowException();
        incident.setExampleId(exampleId);
        incident.setTaskId(taskId);
        incident.setExceptionType(type);
        incident.setDetail(detail == null ? "流程处理异常" : detail.substring(0, Math.min(1000, detail.length())));
        incident.setStatus("OPEN");
        exceptions.insert(incident);
        return exceptions.selectById(incident.getId());
    }

    void requireTenantMember(long tenantId, Long memberId) {
        if (memberId == null || tenantMembers.selectCount(new LambdaQueryWrapper<UnSysTenantMember>()
                .eq(UnSysTenantMember::getTenantId, tenantId).eq(UnSysTenantMember::getMemberId, memberId)
                .eq(UnSysTenantMember::getStatus, 1)) == 0) bad("处理人不是此租户的有效成员");
    }

    void requireRecord(long tenantId, long moduleId, long recordId, HttpServletRequest request) {
        UnModuleRecord record = records.selectById(recordId);
        if (record == null || !flowIdEquals(record.getTenantId(), tenantId)
                || !flowIdEquals(record.getModuleId(), moduleId)
                || (record.getRecordStatus() != null && record.getRecordStatus() == 2))
            bad("业务记录不属于此租户和模块，或已删除");
        recordAccess.requireWrite(recordAccess.scope(request, tenantId), record);
    }

    void event(long exampleId, Long taskId, Long actionId, String type, Long actor, String description) {
        UnFlowEvent event = new UnFlowEvent();
        event.setExampleId(exampleId);
        event.setTaskId(taskId);
        event.setActionId(actionId);
        event.setEventType(type);
        event.setActorMemberId(actor);
        event.setDescription(description);
        events.insert(event);
    }

    String writeJson(DraftSnapshot snapshot) {
        try { return json.writeValueAsString(snapshot); }
        catch (Exception ex) { throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "流程快照生成失败", ex); }
    }

    DraftSnapshot readJson(String value) {
        try { return json.readValue(value, DraftSnapshot.class); }
        catch (Exception ex) { throw new ResponseStatusException(HttpStatus.CONFLICT, "流程发布快照无法读取", ex); }
    }

    static boolean flowIdEquals(Long id, long expected) { return id != null && id == expected; }
    static void bad(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    static void conflict(String message) { throw new ResponseStatusException(HttpStatus.CONFLICT, message); }
    static void forbidden(String message) { throw new ResponseStatusException(HttpStatus.FORBIDDEN, message); }
    static void notFound(String message) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, message); }

    /** 发布版本及实例保存的节点、连线快照。 */
    public record DraftSnapshot(List<UnFlowNode> nodes, List<UnFlowEdge> edges) {}
}
