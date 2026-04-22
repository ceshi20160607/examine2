package com.unique.examine.flow.manage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.core.security.AuthContextHolder;
import com.unique.examine.flow.entity.po.FlowLogAction;
import com.unique.examine.flow.entity.po.FlowLogTrace;
import com.unique.examine.flow.entity.po.FlowRecord;
import com.unique.examine.flow.entity.po.FlowRecordLine;
import com.unique.examine.flow.entity.po.FlowRecordVar;
import com.unique.examine.flow.entity.po.FlowTask;
import com.unique.examine.flow.entity.po.FlowTaskActor;
import com.unique.examine.flow.entity.po.FlowTemp;
import com.unique.examine.flow.entity.po.FlowTempVer;
import com.unique.examine.flow.service.IFlowLogActionService;
import com.unique.examine.flow.service.IFlowLogTraceService;
import com.unique.examine.flow.service.IFlowRecordLineService;
import com.unique.examine.flow.service.IFlowRecordService;
import com.unique.examine.flow.service.IFlowRecordVarService;
import com.unique.examine.flow.service.IFlowTaskActorService;
import com.unique.examine.flow.service.IFlowTaskService;
import com.unique.examine.flow.service.IFlowTempService;
import com.unique.examine.flow.service.IFlowTempVerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 流程引擎（系统态）：基于 {@code un_flow_record.graph_json} 推进；变量在 {@code un_flow_record_var}。
 * <p>
 * HTTP 路径仍使用 {@code instanceId} 命名，语义为 {@code un_flow_record.id}（兼容历史调用方）。
 * </p>
 */
@Service
public class FlowEngineService {

    @Autowired
    private IFlowTempService flowTempService;
    @Autowired
    private IFlowTempVerService flowTempVerService;
    @Autowired
    private IFlowRecordService flowRecordService;
    @Autowired
    private IFlowRecordVarService flowRecordVarService;
    @Autowired
    private IFlowTaskService flowTaskService;
    @Autowired
    private IFlowTaskActorService flowTaskActorService;
    @Autowired
    private IFlowLogActionService flowLogActionService;
    @Autowired
    private IFlowLogTraceService flowLogTraceService;
    @Autowired
    private IFlowRecordLineService flowRecordLineService;
    @Autowired
    private FlowRecordGraphSyncService flowRecordGraphSyncService;
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * @param instanceId 实为 {@code FlowRecord.id}
     */
    public record StartResult(Long instanceId, Long taskId, String nodeId, String nodeName) {}

    /**
     * @param instanceId 实为 {@code FlowRecord.id}
     */
    public record TaskActionResult(Long instanceId,
                                   Long currentTaskId,
                                   Integer currentTaskStatus,
                                   Integer instanceStatus,
                                   String nextNodeId,
                                   String nextNodeName,
                                   Long nextTaskId,
                                   Integer nextTaskStatus,
                                   String note) {}

    private record ChildStartResult(Long childRecordId, Long childTaskId) {}

    /**
     * 兼容旧请求字段名 {@code defCode}：与 {@code un_flow_temp.temp_code} 一致。
     */
    @Transactional(rollbackFor = Exception.class)
    public StartResult startByDefCode(String defCode, String bizType, String bizId, String title, Map<String, Object> vars) {
        return startByTempCode(defCode, bizType, bizId, title, vars);
    }

    @Transactional(rollbackFor = Exception.class)
    public StartResult startByTempCode(String tempCode, String bizType, String bizId, String title, Map<String, Object> vars) {
        Long platId = AuthContextHolder.getPlatId();
        if (platId == null) {
            throw new BusinessException(401, "未登录");
        }
        long systemId = AuthContextHolder.getSystemIdOrDefault();
        if (systemId == 0L) {
            throw new BusinessException(403, "请先进入自建系统");
        }
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (tempCode == null || tempCode.isBlank()) {
            throw new BusinessException("tempCode 不能为空");
        }
        if (bizType == null || bizType.isBlank()) {
            throw new BusinessException("bizType 不能为空");
        }
        if (bizId == null || bizId.isBlank()) {
            throw new BusinessException("bizId 不能为空");
        }

        FlowTemp temp = flowTempService.lambdaQuery()
                .eq(FlowTemp::getSystemId, systemId)
                .eq(FlowTemp::getTenantId, tenantId)
                .eq(FlowTemp::getTempCode, tempCode.trim())
                .eq(FlowTemp::getStatus, 1)
                .last("limit 1")
                .one();
        if (temp == null) {
            throw new BusinessException(404, "流程模板不存在或已停用");
        }
        if (temp.getLatestVerNo() == null || temp.getLatestVerNo() <= 0) {
            throw new BusinessException(400, "流程尚未发布");
        }

        FlowTempVer ver = flowTempVerService.lambdaQuery()
                .eq(FlowTempVer::getSystemId, systemId)
                .eq(FlowTempVer::getTenantId, tenantId)
                .eq(FlowTempVer::getTempId, temp.getId())
                .eq(FlowTempVer::getVerNo, temp.getLatestVerNo())
                .eq(FlowTempVer::getPublishStatus, 2)
                .last("limit 1")
                .one();
        if (ver == null) {
            throw new BusinessException(400, "未找到已发布版本");
        }
        if (ver.getGraphJson() == null || ver.getGraphJson().isBlank()) {
            throw new BusinessException(400, "发布版本缺少 graphJson");
        }

        NodeInfo firstApprove = resolveFirstApproveNode(ver.getGraphJson());

        LocalDateTime now = LocalDateTime.now();
        FlowRecord rec = new FlowRecord();
        rec.setSystemId(systemId);
        rec.setTenantId(tenantId);
        rec.setRootRecordId(null);
        rec.setParentRecordId(null);
        rec.setParentNodeKey(null);
        rec.setParentTaskId(null);
        rec.setTempId(temp.getId());
        rec.setTempVerNo(ver.getVerNo());
        rec.setGraphJson(ver.getGraphJson());
        rec.setFormJson(ver.getFormJson());
        rec.setBizType(bizType.trim());
        rec.setBizId(bizId.trim());
        rec.setTitle(title == null ? null : title.trim());
        rec.setStarterPlatId(platId);
        rec.setStatus(1);
        rec.setCurrentNodeKey(firstApprove.nodeKey());
        rec.setStartTime(now);
        rec.setCreateUserId(platId);
        rec.setUpdateUserId(platId);
        flowRecordService.save(rec);

        rec.setRootRecordId(rec.getId());
        rec.setUpdateUserId(platId);
        flowRecordService.updateById(rec);

        saveVars(systemId, tenantId, rec.getId(), platId, vars);

        flowRecordGraphSyncService.syncFromGraphJson(rec, platId, ver.getId());

        FlowTask task = createApproveTasksForNode(rec, firstApprove.nodeKey(), firstApprove.nodeName(), platId, systemId, tenantId, now, null);

        FlowLogAction startLog = new FlowLogAction();
        startLog.setSystemId(systemId);
        startLog.setTenantId(tenantId);
        startLog.setRecordId(rec.getId());
        startLog.setTaskId(task.getId());
        startLog.setNodeKey(task.getNodeKey());
        startLog.setAction("start");
        startLog.setActorPlatId(platId);
        startLog.setActionTime(now);
        startLog.setCreateUserId(platId);
        startLog.setUpdateUserId(platId);
        flowLogActionService.save(startLog);

        return new StartResult(rec.getId(), task.getId(), task.getNodeKey(), task.getNodeName());
    }

    @Transactional(rollbackFor = Exception.class)
    public TaskActionResult approve(Long instanceId, Long taskId, String commentText) {
        return finishTaskAndAdvance(instanceId, taskId, "approve", 2, commentText);
    }

    @Transactional(rollbackFor = Exception.class)
    public TaskActionResult reject(Long instanceId, Long taskId, String commentText) {
        return finishTaskAndEnd(instanceId, taskId, "reject", 3, commentText);
    }

    private record NodeInfo(String nodeKey, String nodeName) {}

    private NodeInfo resolveFirstApproveNode(String graphJson) {
        try {
            JsonNode root = objectMapper.readTree(graphJson);
            JsonNode nodes = root.get("nodes");
            if (nodes != null && nodes.isArray()) {
                for (JsonNode n : nodes) {
                    String type = n.path("type").asText();
                    if ("approve".equalsIgnoreCase(type)) {
                        String id = n.path("id").asText(null);
                        if (id == null || id.isBlank()) {
                            continue;
                        }
                        String name = n.path("name").asText(null);
                        return new NodeInfo(id, name);
                    }
                }
            }
        } catch (Exception ignore) {
            // fall through
        }
        throw new BusinessException(400, "graphJson 未包含可用的 approve 节点（MVP 规则）");
    }

    private void saveVars(long systemId, long tenantId, long recordId, long platId, Map<String, Object> vars) {
        if (vars == null || vars.isEmpty()) {
            return;
        }
        List<FlowRecordVar> list = new ArrayList<>();
        for (Map.Entry<String, Object> e : vars.entrySet()) {
            String key = e.getKey();
            if (key == null || key.isBlank()) {
                continue;
            }
            Object v = e.getValue();
            String varKey = key.trim();
            if (varKey.isEmpty()) {
                continue;
            }

            JsonNode node = (v == null) ? NullNode.getInstance() : objectMapper.valueToTree(v);
            String type = resolveVarType(node);
            String value = node.isNull() ? null : (node.isTextual() ? node.asText() : node.toString());

            FlowRecordVar po = new FlowRecordVar();
            po.setSystemId(systemId);
            po.setTenantId(tenantId);
            po.setRecordId(recordId);
            po.setVarKey(varKey);
            po.setVarType(type);
            po.setVarValue(value);
            po.setCreateUserId(platId);
            po.setUpdateUserId(platId);
            list.add(po);
        }
        if (!list.isEmpty()) {
            flowRecordVarService.saveBatch(list);
        }
    }

    private static String resolveVarType(JsonNode node) {
        if (node == null || node.isNull()) {
            return "string";
        }
        if (node.isNumber()) {
            return "number";
        }
        if (node.isBoolean()) {
            return "bool";
        }
        if (node.isTextual()) {
            return "string";
        }
        return "json";
    }

    private TaskActionResult finishTaskAndEnd(Long instanceId, Long taskId, String action, int taskStatus, String commentText) {
        FinishContext ctx = loadAndValidate(instanceId, taskId);
        Long platId = ctx.platId();
        long systemId = ctx.systemId();
        long tenantId = ctx.tenantId();
        FlowRecord rec = ctx.rec();
        FlowTask task = ctx.task();

        LocalDateTime now = LocalDateTime.now();
        task.setStatus(taskStatus);
        task.setFinishTime(now);
        if (task.getClaimTime() == null) {
            task.setClaimTime(now);
        }
        task.setUpdateUserId(platId);
        flowTaskService.updateById(task);

        cancelPendingTasksSameNode(instanceId, task.getNodeKey(), taskId, platId, now);

        rec.setStatus(2);
        rec.setCurrentNodeKey(null);
        rec.setEndTime(now);
        rec.setUpdateUserId(platId);
        flowRecordService.updateById(rec);

        saveActionLog(systemId, tenantId, instanceId, taskId, task.getNodeKey(), action, platId, commentText, now, null);

        return new TaskActionResult(instanceId, taskId, task.getStatus(), rec.getStatus(),
                null, null, null, null, null);
    }

    private TaskActionResult finishTaskAndAdvance(Long instanceId, Long taskId, String action, int taskStatus, String commentText) {
        FinishContext ctx = loadAndValidate(instanceId, taskId);
        Long platId = ctx.platId();
        long systemId = ctx.systemId();
        long tenantId = ctx.tenantId();
        FlowRecord rec = ctx.rec();
        FlowTask task = ctx.task();

        LocalDateTime now = LocalDateTime.now();
        task.setStatus(taskStatus);
        task.setFinishTime(now);
        if (task.getClaimTime() == null) {
            task.setClaimTime(now);
        }
        task.setUpdateUserId(platId);
        flowTaskService.updateById(task);

        saveActionLog(systemId, tenantId, instanceId, taskId, task.getNodeKey(), action, platId, commentText, now, null);

        if (hasPendingSameNodeApproveTasks(instanceId, task.getNodeKey())) {
            rec.setCurrentNodeKey(task.getNodeKey());
            rec.setUpdateUserId(platId);
            flowRecordService.updateById(rec);
            return new TaskActionResult(instanceId, taskId, task.getStatus(), rec.getStatus(),
                    task.getNodeKey(), task.getNodeName(), null, null, "countersign_wait");
        }

        AdvanceResult advance;
        try {
            advance = advanceFromNode(rec, task.getNodeKey());
        } catch (Exception ex) {
            return handleExceptionPolicy(rec, task, ex, now);
        }
        advance = consumeCcChain(rec, advance, platId, systemId, tenantId, now);

        if (advance.end()) {
            rec.setStatus(2);
            rec.setCurrentNodeKey(null);
            rec.setEndTime(now);
            rec.setUpdateUserId(platId);
            flowRecordService.updateById(rec);
            if (rec.getParentRecordId() != null) {
                resumeParentAfterSubflowChildCompleted(rec, platId, now);
            }
            return new TaskActionResult(instanceId, taskId, task.getStatus(), rec.getStatus(),
                    advance.nextNodeKey(), advance.nextNodeName(), null, null, "ended");
        }

        if (isSubflowNode(rec.getGraphJson(), advance.nextNodeKey())) {
            return enterSubflowChild(rec, task, advance, instanceId, platId, systemId, tenantId, now);
        }

        FlowTask nextTask = createApproveTasksForNode(rec, advance.nextNodeKey(), advance.nextNodeName(), platId, systemId, tenantId, now, null);

        rec.setCurrentNodeKey(nextTask.getNodeKey());
        rec.setUpdateUserId(platId);
        flowRecordService.updateById(rec);

        return new TaskActionResult(instanceId, taskId, task.getStatus(), rec.getStatus(),
                nextTask.getNodeKey(), nextTask.getNodeName(), nextTask.getId(), nextTask.getStatus(), "advanced");
    }

    private record FinishContext(Long platId, long systemId, long tenantId, FlowRecord rec, FlowTask task) {}

    private FinishContext loadAndValidate(Long instanceId, Long taskId) {
        Long platId = AuthContextHolder.getPlatId();
        if (platId == null) {
            throw new BusinessException(401, "未登录");
        }
        long systemId = AuthContextHolder.getSystemIdOrDefault();
        if (systemId == 0L) {
            throw new BusinessException(403, "请先进入自建系统");
        }
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (instanceId == null || taskId == null) {
            throw new BusinessException(400, "instanceId/taskId 不能为空");
        }

        FlowRecord rec = flowRecordService.lambdaQuery()
                .eq(FlowRecord::getSystemId, systemId)
                .eq(FlowRecord::getTenantId, tenantId)
                .eq(FlowRecord::getId, instanceId)
                .last("limit 1")
                .one();
        if (rec == null) {
            throw new BusinessException(404, "实例不存在");
        }
        if (rec.getStatus() == null || rec.getStatus() != 1) {
            throw new BusinessException(400, "实例非运行中，无法办理");
        }

        FlowTask task = flowTaskService.lambdaQuery()
                .eq(FlowTask::getSystemId, systemId)
                .eq(FlowTask::getTenantId, tenantId)
                .eq(FlowTask::getId, taskId)
                .eq(FlowTask::getRecordId, instanceId)
                .last("limit 1")
                .one();
        if (task == null) {
            throw new BusinessException(404, "任务不存在");
        }
        if (task.getStatus() == null || task.getStatus() != 1) {
            throw new BusinessException(400, "任务非待处理状态");
        }
        if (!canActOnTask(task, platId)) {
            throw new BusinessException(403, "无权办理该任务");
        }
        return new FinishContext(platId, systemId, tenantId, rec, task);
    }

    /**
     * 单人待办：assignee 匹配；或签待办：assignee 或 candidateJson 数组中的 platId 可办理。
     */
    private boolean canActOnTask(FlowTask task, Long platId) {
        if (platId == null) {
            return false;
        }
        // 领取并发保护：一旦被领取，只允许领取人办理
        if (task.getClaimTime() != null) {
            return task.getAssigneePlatId() != null && task.getAssigneePlatId().equals(platId);
        }
        if (task.getAssigneePlatId() != null && task.getAssigneePlatId().equals(platId)) {
            return true;
        }
        String cj = task.getCandidateJson();
        if (cj == null || cj.isBlank()) {
            return false;
        }
        try {
            JsonNode root = objectMapper.readTree(cj);
            if (root.isArray()) {
                for (JsonNode n : root) {
                    if (n.isNumber() && platId.equals(n.asLong())) {
                        return true;
                    }
                    if (n.isTextual()) {
                        try {
                            if (platId.equals(Long.parseLong(n.asText().trim()))) {
                                return true;
                            }
                        } catch (NumberFormatException ignore) {
                            // skip
                        }
                    }
                }
            }
        } catch (Exception ignore) {
            // fall through
        }
        return false;
    }

    @Transactional(rollbackFor = Exception.class)
    public void claimTask(Long instanceId, Long taskId) {
        Long platId = AuthContextHolder.getPlatId();
        if (platId == null) {
            throw new BusinessException(401, "未登录");
        }
        long systemId = AuthContextHolder.getSystemIdOrDefault();
        if (systemId == 0L) {
            throw new BusinessException(403, "请先进入自建系统");
        }
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (instanceId == null || taskId == null) {
            throw new BusinessException(400, "instanceId/taskId 不能为空");
        }

        FlowTask task = flowTaskService.lambdaQuery()
                .eq(FlowTask::getSystemId, systemId)
                .eq(FlowTask::getTenantId, tenantId)
                .eq(FlowTask::getId, taskId)
                .eq(FlowTask::getRecordId, instanceId)
                .last("limit 1")
                .one();
        if (task == null) {
            throw new BusinessException(404, "任务不存在");
        }
        if (task.getStatus() == null || task.getStatus() != 1) {
            throw new BusinessException(400, "任务非待处理状态");
        }
        if (task.getClaimTime() != null) {
            if (task.getAssigneePlatId() != null && task.getAssigneePlatId().equals(platId)) {
                return; // idempotent
            }
            throw new BusinessException(409, "任务已被领取");
        }
        if (!canActOnTask(task, platId)) {
            throw new BusinessException(403, "无权领取该任务");
        }

        LocalDateTime now = LocalDateTime.now();
        boolean ok = flowTaskService.lambdaUpdate()
                .eq(FlowTask::getSystemId, systemId)
                .eq(FlowTask::getTenantId, tenantId)
                .eq(FlowTask::getId, taskId)
                .eq(FlowTask::getRecordId, instanceId)
                .eq(FlowTask::getStatus, 1)
                .isNull(FlowTask::getClaimTime)
                .set(FlowTask::getAssigneePlatId, platId)
                .set(FlowTask::getClaimTime, now)
                .set(FlowTask::getUpdateUserId, platId)
                .set(FlowTask::getUpdateTime, now)
                .update();
        if (!ok) {
            throw new BusinessException(409, "领取失败（可能已被他人领取）");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void unclaimTask(Long instanceId, Long taskId) {
        Long platId = AuthContextHolder.getPlatId();
        if (platId == null) {
            throw new BusinessException(401, "未登录");
        }
        long systemId = AuthContextHolder.getSystemIdOrDefault();
        if (systemId == 0L) {
            throw new BusinessException(403, "请先进入自建系统");
        }
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (instanceId == null || taskId == null) {
            throw new BusinessException(400, "instanceId/taskId 不能为空");
        }

        FlowTask task = flowTaskService.lambdaQuery()
                .eq(FlowTask::getSystemId, systemId)
                .eq(FlowTask::getTenantId, tenantId)
                .eq(FlowTask::getId, taskId)
                .eq(FlowTask::getRecordId, instanceId)
                .last("limit 1")
                .one();
        if (task == null) {
            throw new BusinessException(404, "任务不存在");
        }
        if (task.getStatus() == null || task.getStatus() != 1) {
            throw new BusinessException(400, "任务非待处理状态");
        }
        if (task.getClaimTime() == null) {
            return; // idempotent
        }
        if (task.getAssigneePlatId() == null || !task.getAssigneePlatId().equals(platId)) {
            throw new BusinessException(403, "仅领取人可取消领取");
        }

        LocalDateTime now = LocalDateTime.now();
        boolean hasCandidates = task.getCandidateJson() != null && !task.getCandidateJson().isBlank();
        var u = flowTaskService.lambdaUpdate()
                .eq(FlowTask::getSystemId, systemId)
                .eq(FlowTask::getTenantId, tenantId)
                .eq(FlowTask::getId, taskId)
                .eq(FlowTask::getRecordId, instanceId)
                .eq(FlowTask::getStatus, 1)
                .set(FlowTask::getClaimTime, null)
                .set(FlowTask::getUpdateUserId, platId)
                .set(FlowTask::getUpdateTime, now);
        if (hasCandidates) {
            u.set(FlowTask::getAssigneePlatId, null);
        }
        boolean ok = u.update();
        if (!ok) {
            throw new BusinessException(409, "取消领取失败");
        }
    }

    private void saveActionLog(long systemId,
                               long tenantId,
                               long recordId,
                               Long taskId,
                               String nodeKey,
                               String action,
                               long actorPlatId,
                               String commentText,
                               LocalDateTime now,
                               String extraJson) {
        FlowLogAction log = new FlowLogAction();
        log.setSystemId(systemId);
        log.setTenantId(tenantId);
        log.setRecordId(recordId);
        log.setTaskId(taskId);
        log.setNodeKey(nodeKey);
        log.setAction(action);
        log.setActorPlatId(actorPlatId);
        log.setCommentText(commentText == null ? null : commentText.trim());
        log.setExtraJson(extraJson);
        log.setActionTime(now);
        log.setCreateUserId(actorPlatId);
        log.setUpdateUserId(actorPlatId);
        flowLogActionService.save(log);
    }

    private record AdvanceResult(boolean end, String nextNodeKey, String nextNodeName) {}

    private AdvanceResult advanceFromNode(FlowRecord rec, String currentNodeKey) {
        Long lineCnt = flowRecordLineService.lambdaQuery()
                .eq(FlowRecordLine::getRecordId, rec.getId())
                .count();
        if (lineCnt != null && lineCnt > 0) {
            return advanceFromRelationalLines(rec, currentNodeKey);
        }
        return advanceFromGraphJsonEdges(rec, currentNodeKey);
    }

    private AdvanceResult advanceFromRelationalLines(FlowRecord rec, String currentNodeKey) {
        if (rec.getGraphJson() == null || rec.getGraphJson().isBlank()) {
            throw new BusinessException(400, "实例缺少 graphJson");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(rec.getGraphJson());
        } catch (Exception e) {
            throw new BusinessException(400, "graphJson 解析失败");
        }
        JsonNode nodes = root.path("nodes");
        if (!nodes.isArray()) {
            throw new BusinessException(400, "graphJson 缺少 nodes");
        }
        Map<String, JsonNode> nodeById = new HashMap<>();
        for (JsonNode n : nodes) {
            String id = n.path("id").asText(null);
            if (id != null && !id.isBlank()) {
                nodeById.put(id, n);
            }
        }
        if (!nodeById.containsKey(currentNodeKey)) {
            throw new BusinessException(400, "当前节点不存在于 graphJson");
        }
        Map<String, JsonNode> vars = loadVarsAsJsonNodes(rec.getSystemId(), rec.getTenantId(), rec.getId());
        List<FlowRecordLine> lines = flowRecordLineService.lambdaQuery()
                .eq(FlowRecordLine::getRecordId, rec.getId())
                .eq(FlowRecordLine::getFromNodeKey, currentNodeKey)
                .eq(FlowRecordLine::getStatus, 1)
                .list();
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException(400, "当前节点无可用出边");
        }
        lines.sort(Comparator
                .comparingInt((FlowRecordLine l) -> l.getPriority() == null ? Integer.MAX_VALUE : l.getPriority()));

        String nextId = null;
        for (FlowRecordLine line : lines) {
            String cond = null;
            if (line.getRemark() != null && !line.getRemark().isBlank()) {
                try {
                    JsonNode rj = objectMapper.readTree(line.getRemark());
                    cond = rj.path("cond").asText(null);
                } catch (Exception ignore) {
                    cond = null;
                }
            }
            if (cond == null || cond.isBlank()) {
                nextId = line.getToNodeKey();
                break;
            }
            if (evalCond(cond, vars)) {
                nextId = line.getToNodeKey();
                break;
            }
        }
        if (nextId == null) {
            throw new BusinessException(400, "未命中任何分支");
        }
        JsonNode nextNode = nodeById.get(nextId);
        if (nextNode == null) {
            throw new BusinessException(400, "下一节点不存在于 graphJson");
        }
        String nextType = nextNode.path("type").asText("");
        String nextName = nextNode.path("name").asText(null);
        if ("end".equalsIgnoreCase(nextType)) {
            return new AdvanceResult(true, nextId, nextName);
        }
        if ("approve".equalsIgnoreCase(nextType) || "subflow".equalsIgnoreCase(nextType)
                || "cc".equalsIgnoreCase(nextType)) {
            return new AdvanceResult(false, nextId, nextName);
        }
        throw new BusinessException(400, "下一节点类型暂不支持：" + nextType);
    }

    private AdvanceResult advanceFromGraphJsonEdges(FlowRecord rec, String currentNodeKey) {
        if (rec.getGraphJson() == null || rec.getGraphJson().isBlank()) {
            throw new BusinessException(400, "实例缺少 graphJson");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(rec.getGraphJson());
        } catch (Exception e) {
            throw new BusinessException(400, "graphJson 解析失败");
        }
        JsonNode nodes = root.path("nodes");
        JsonNode edges = root.path("edges");
        if (!nodes.isArray() || !edges.isArray()) {
            throw new BusinessException(400, "graphJson 缺少 nodes/edges");
        }

        Map<String, JsonNode> nodeById = new HashMap<>();
        for (JsonNode n : nodes) {
            String id = n.path("id").asText(null);
            if (id != null && !id.isBlank()) {
                nodeById.put(id, n);
            }
        }
        if (!nodeById.containsKey(currentNodeKey)) {
            throw new BusinessException(400, "当前节点不存在于 graphJson");
        }

        Map<String, JsonNode> vars = loadVarsAsJsonNodes(rec.getSystemId(), rec.getTenantId(), rec.getId());
        List<EdgeCandidate> outgoing = new ArrayList<>();
        int idx = 0;
        for (JsonNode e : edges) {
            String from = e.path("from").asText(null);
            String to = e.path("to").asText(null);
            if (!Objects.equals(from, currentNodeKey) || to == null || to.isBlank()) {
                idx++;
                continue;
            }
            Integer priority = e.has("priority") && e.get("priority").canConvertToInt() ? e.get("priority").asInt() : null;
            String cond = e.has("cond") ? e.path("cond").asText(null) : null;
            outgoing.add(new EdgeCandidate(to, priority, idx, cond));
            idx++;
        }
        if (outgoing.isEmpty()) {
            throw new BusinessException(400, "当前节点无可用出边");
        }

        outgoing.sort(Comparator
                .comparingInt((EdgeCandidate c) -> c.priority() == null ? Integer.MAX_VALUE : c.priority())
                .thenComparingInt(EdgeCandidate::index));

        String nextId = null;
        for (EdgeCandidate c : outgoing) {
            if (c.cond() == null || c.cond().isBlank()) {
                nextId = c.to();
                break;
            }
            if (evalCond(c.cond(), vars)) {
                nextId = c.to();
                break;
            }
        }
        if (nextId == null) {
            throw new BusinessException(400, "未命中任何分支");
        }
        JsonNode nextNode = nodeById.get(nextId);
        if (nextNode == null) {
            throw new BusinessException(400, "下一节点不存在于 graphJson");
        }
        String nextType = nextNode.path("type").asText("");
        String nextName = nextNode.path("name").asText(null);
        if ("end".equalsIgnoreCase(nextType)) {
            return new AdvanceResult(true, nextId, nextName);
        }
        if ("approve".equalsIgnoreCase(nextType) || "subflow".equalsIgnoreCase(nextType)
                || "cc".equalsIgnoreCase(nextType)) {
            return new AdvanceResult(false, nextId, nextName);
        }
        throw new BusinessException(400, "下一节点类型暂不支持：" + nextType);
    }

    private record EdgeCandidate(String to, Integer priority, int index, String cond) {}

    private Map<String, JsonNode> loadVarsAsJsonNodes(long systemId, long tenantId, long recordId) {
        List<FlowRecordVar> list = flowRecordVarService.lambdaQuery()
                .eq(FlowRecordVar::getSystemId, systemId)
                .eq(FlowRecordVar::getTenantId, tenantId)
                .eq(FlowRecordVar::getRecordId, recordId)
                .list();
        Map<String, JsonNode> map = new HashMap<>();
        for (FlowRecordVar v : list) {
            if (v.getVarKey() == null || v.getVarKey().isBlank()) {
                continue;
            }
            String key = v.getVarKey().trim();
            if (key.isEmpty()) {
                continue;
            }
            JsonNode node;
            if (v.getVarValue() == null) {
                node = NullNode.getInstance();
            } else {
                node = parseStoredVarValue(v.getVarType(), v.getVarValue());
            }
            map.put(key, node);
        }
        return map;
    }

    private JsonNode parseStoredVarValue(String varType, String varValue) {
        String t = varType == null ? "string" : varType.trim().toLowerCase(Locale.ROOT);
        try {
            return switch (t) {
                case "number", "bool", "json" -> objectMapper.readTree(varValue);
                default -> objectMapper.getNodeFactory().textNode(varValue);
            };
        } catch (Exception e) {
            return objectMapper.getNodeFactory().textNode(varValue);
        }
    }

    private static final Pattern COND_PATTERN = Pattern.compile("^\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\((.*)\\)\\s*$");

    private boolean evalCond(String cond, Map<String, JsonNode> vars) {
        Matcher m = COND_PATTERN.matcher(cond == null ? "" : cond);
        if (!m.matches()) {
            throw new BusinessException(400, "cond 语法不支持：" + cond);
        }
        String fn = m.group(1).toLowerCase(Locale.ROOT);
        String argsRaw = m.group(2);
        List<String> args = splitArgs(argsRaw);
        return switch (fn) {
            case "exists" -> {
                if (args.size() != 1) {
                    throw new BusinessException(400, "exists 参数错误：" + cond);
                }
                JsonNode v = vars.get(args.get(0));
                yield v != null && !v.isNull() && !(v.isTextual() && v.asText().isBlank());
            }
            case "eq", "ne", "gt", "ge", "lt", "le", "in" -> evalBinary(fn, cond, args, vars);
            default -> throw new BusinessException(400, "cond 函数不支持：" + fn);
        };
    }

    private boolean evalBinary(String fn, String cond, List<String> args, Map<String, JsonNode> vars) {
        if (args.size() != 2) {
            throw new BusinessException(400, "cond 参数错误：" + cond);
        }
        String varName = args.get(0);
        JsonNode left = vars.get(varName);
        JsonNode right = parseLiteral(args.get(1));
        if ("in".equals(fn)) {
            if (!(right instanceof ArrayNode arr)) {
                throw new BusinessException(400, "in 右值必须是数组：" + cond);
            }
            if (left == null || left.isNull()) {
                return false;
            }
            String lv = left.isTextual() ? left.asText() : left.toString();
            for (JsonNode it : arr) {
                if (it != null && it.isTextual() && Objects.equals(lv, it.asText())) {
                    return true;
                }
            }
            return false;
        }
        if ("eq".equals(fn)) {
            return jsonLooseEquals(left, right);
        }
        if ("ne".equals(fn)) {
            return !jsonLooseEquals(left, right);
        }

        BigDecimal ln = toBigDecimal(left);
        BigDecimal rn = toBigDecimal(right);
        if (ln == null || rn == null) {
            throw new BusinessException(400, "数值比较需要 number：" + cond);
        }
        int cmp = ln.compareTo(rn);
        return switch (fn) {
            case "gt" -> cmp > 0;
            case "ge" -> cmp >= 0;
            case "lt" -> cmp < 0;
            case "le" -> cmp <= 0;
            default -> throw new BusinessException(400, "cond 不支持：" + fn);
        };
    }

    private JsonNode parseLiteral(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty()) {
            return NullNode.getInstance();
        }
        try {
            return objectMapper.readTree(s);
        } catch (Exception e) {
            throw new BusinessException(400, "literal 解析失败：" + raw);
        }
    }

    private static BigDecimal toBigDecimal(JsonNode n) {
        if (n == null || n.isNull()) {
            return null;
        }
        if (n.isNumber()) {
            return n.decimalValue();
        }
        if (n.isTextual()) {
            try {
                return new BigDecimal(n.asText().trim());
            } catch (Exception ignore) {
                return null;
            }
        }
        return null;
    }

    private static boolean jsonLooseEquals(JsonNode a, JsonNode b) {
        if (a == null || a.isNull()) {
            return b == null || b.isNull();
        }
        if (b == null || b.isNull()) {
            return false;
        }
        if (a.isNumber() && b.isNumber()) {
            return a.decimalValue().compareTo(b.decimalValue()) == 0;
        }
        if (a.isBoolean() && b.isBoolean()) {
            return a.asBoolean() == b.asBoolean();
        }
        if (a.isTextual() && b.isTextual()) {
            return Objects.equals(a.asText(), b.asText());
        }
        return Objects.equals(a.toString(), b.toString());
    }

    private static List<String> splitArgs(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return out;
        }
        int depth = 0;
        boolean inQuote = false;
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '"' && (i == 0 || raw.charAt(i - 1) != '\\')) {
                inQuote = !inQuote;
                cur.append(c);
                continue;
            }
            if (!inQuote) {
                if (c == '[' || c == '{' || c == '(') {
                    depth++;
                } else if (c == ']' || c == '}' || c == ')') {
                    depth = Math.max(0, depth - 1);
                } else if (c == ',' && depth == 0) {
                    out.add(cur.toString().trim());
                    cur.setLength(0);
                    continue;
                }
            }
            cur.append(c);
        }
        String last = cur.toString().trim();
        if (!last.isEmpty()) {
            out.add(last);
        }
        if (!out.isEmpty()) {
            out.set(0, out.get(0).trim());
        }
        return out;
    }

    private record ExceptionPolicy(String mode, Long adminPlatId, String endReason) {}

    private TaskActionResult handleExceptionPolicy(FlowRecord rec, FlowTask task, Exception ex, LocalDateTime now) {
        Long platId = AuthContextHolder.getPlatId();
        long systemId = AuthContextHolder.getSystemIdOrDefault();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        ExceptionPolicy policy = resolveExceptionPolicy(rec, task.getNodeKey());

        if ("end_instance".equalsIgnoreCase(policy.mode())) {
            rec.setStatus(2);
            rec.setCurrentNodeKey(null);
            rec.setEndTime(now);
            rec.setUpdateUserId(platId);
            flowRecordService.updateById(rec);

            String extra = safeJson(Map.of("reason", policy.endReason(), "error", ex.getMessage()));
            saveActionLog(systemId, tenantId, rec.getId(), task.getId(), task.getNodeKey(), "terminate", platId, "exception_policy:end_instance", now, extra);

            return new TaskActionResult(rec.getId(), task.getId(), task.getStatus(), rec.getStatus(),
                    null, null, null, null, "terminated_by_policy");
        }

        Long admin = policy.adminPlatId() == null ? rec.getStarterPlatId() : policy.adminPlatId();
        if (admin == null) {
            throw new BusinessException(400, "异常兜底未配置 admin_plat_id，且无法使用发起人兜底");
        }

        // approve 已将当前 task 置为终态（2/3）；兜底仅新建管理员待办，不回写已完成任务状态
        if (task.getStatus() != null && task.getStatus() == 1) {
            task.setStatus(4);
            task.setFinishTime(now);
            if (task.getClaimTime() == null) {
                task.setClaimTime(now);
            }
            task.setUpdateUserId(platId);
            flowTaskService.updateById(task);
        }

        FlowTask adminTask = new FlowTask();
        adminTask.setSystemId(systemId);
        adminTask.setTenantId(tenantId);
        adminTask.setRecordId(rec.getId());
        adminTask.setNodeKey(task.getNodeKey());
        adminTask.setNodeName(task.getNodeName());
        adminTask.setTaskType("approve");
        adminTask.setAssigneePlatId(admin);
        adminTask.setCandidateJson(null);
        adminTask.setStatus(1);
        adminTask.setCreateUserId(platId);
        adminTask.setUpdateUserId(platId);
        flowTaskService.save(adminTask);

        rec.setCurrentNodeKey(task.getNodeKey());
        rec.setUpdateUserId(platId);
        flowRecordService.updateById(rec);

        String extra = safeJson(Map.of("mode", "fallback_admin", "admin_plat_id", admin, "error", ex.getMessage()));
        saveActionLog(systemId, tenantId, rec.getId(), adminTask.getId(), task.getNodeKey(), "transfer", platId, "exception_policy:fallback_admin", now, extra);

        return new TaskActionResult(rec.getId(), task.getId(), task.getStatus(), rec.getStatus(),
                task.getNodeKey(), task.getNodeName(), adminTask.getId(), adminTask.getStatus(), "fallback_admin");
    }

    private ExceptionPolicy resolveExceptionPolicy(FlowRecord rec, String currentNodeKey) {
        try {
            JsonNode root = objectMapper.readTree(rec.getGraphJson());
            JsonNode nodePolicy = null;
            JsonNode nodes = root.path("nodes");
            if (nodes.isArray()) {
                for (JsonNode n : nodes) {
                    if (Objects.equals(currentNodeKey, n.path("id").asText(null))) {
                        nodePolicy = n.path("config").path("exception_policy");
                        break;
                    }
                }
            }
            if (nodePolicy != null && nodePolicy.isObject() && nodePolicy.has("mode")) {
                return parsePolicy(nodePolicy, rec.getStarterPlatId());
            }
            JsonNode globalPolicy = root.path("config").path("exception_policy");
            if (globalPolicy != null && globalPolicy.isObject() && globalPolicy.has("mode")) {
                return parsePolicy(globalPolicy, rec.getStarterPlatId());
            }
        } catch (Exception ignore) {
            // fall through
        }
        return new ExceptionPolicy("fallback_admin", rec.getStarterPlatId(), "graph_invalid");
    }

    private ExceptionPolicy parsePolicy(JsonNode policy, Long starterPlatId) {
        String mode = policy.path("mode").asText("fallback_admin");
        Long admin = policy.hasNonNull("admin_plat_id") ? policy.get("admin_plat_id").asLong() : starterPlatId;
        String reason = policy.path("end_reason").asText(null);
        return new ExceptionPolicy(mode, admin, reason);
    }

    private String safeJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return null;
        }
    }

    private JsonNode findNodeByKey(String graphJson, String nodeKey) {
        if (graphJson == null || graphJson.isBlank() || nodeKey == null || nodeKey.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(graphJson);
            JsonNode nodes = root.path("nodes");
            if (!nodes.isArray()) {
                return null;
            }
            for (JsonNode n : nodes) {
                if (Objects.equals(nodeKey, n.path("id").asText(null))) {
                    return n;
                }
            }
        } catch (Exception ignore) {
            // fall through
        }
        return null;
    }

    private boolean isSubflowNode(String graphJson, String nodeKey) {
        JsonNode n = findNodeByKey(graphJson, nodeKey);
        if (n == null) {
            return false;
        }
        return "subflow".equalsIgnoreCase(n.path("type").asText(""));
    }

    private boolean isCcNode(String graphJson, String nodeKey) {
        JsonNode n = findNodeByKey(graphJson, nodeKey);
        if (n == null) {
            return false;
        }
        return "cc".equalsIgnoreCase(n.path("type").asText(""));
    }

    /**
     * 抄送节点不阻塞：依次发出抄送记录并立即推进，直到下一节点非 cc。
     */
    private AdvanceResult consumeCcChain(FlowRecord rec,
                                       AdvanceResult advance,
                                       Long platId,
                                       long systemId,
                                       long tenantId,
                                       LocalDateTime now) {
        int guard = 0;
        while (isCcNode(rec.getGraphJson(), advance.nextNodeKey())) {
            if (++guard > 64) {
                throw new BusinessException(400, "抄送节点链过长或可能存在环");
            }
            emitCcNode(rec, advance, platId, systemId, tenantId, now);
            advance = advanceFromNode(rec, advance.nextNodeKey());
            if (advance.end()) {
                return advance;
            }
        }
        return advance;
    }

    private void emitCcNode(FlowRecord rec,
                            AdvanceResult advance,
                            Long platId,
                            long systemId,
                            long tenantId,
                            LocalDateTime now) {
        JsonNode subNode = findNodeByKey(rec.getGraphJson(), advance.nextNodeKey());
        if (subNode == null) {
            throw new BusinessException(400, "抄送节点不存在");
        }
        JsonNode cfg = subNode.path("config");
        List<Long> platIds = parseCcPlatIds(cfg);
        if (platIds.isEmpty()) {
            if (rec.getStarterPlatId() != null) {
                platIds = new ArrayList<>();
                platIds.add(rec.getStarterPlatId());
            } else {
                throw new BusinessException(400, "抄送节点缺少 config.plat_ids 且无发起人");
            }
        }

        FlowTask ccTask = new FlowTask();
        ccTask.setSystemId(systemId);
        ccTask.setTenantId(tenantId);
        ccTask.setRecordId(rec.getId());
        ccTask.setNodeKey(advance.nextNodeKey());
        ccTask.setNodeName(advance.nextNodeName());
        ccTask.setTaskType("cc");
        ccTask.setAssigneePlatId(platIds.get(0));
        ccTask.setCandidateJson(null);
        ccTask.setStatus(2);
        ccTask.setClaimTime(now);
        ccTask.setFinishTime(now);
        ccTask.setCreateUserId(platId);
        ccTask.setUpdateUserId(platId);
        flowTaskService.save(ccTask);

        for (Long pid : platIds) {
            FlowTaskActor a = new FlowTaskActor();
            a.setSystemId(systemId);
            a.setTenantId(tenantId);
            a.setTaskId(ccTask.getId());
            a.setActorPlatId(pid);
            a.setActorRole("cc");
            a.setStatus(1);
            a.setCreateUserId(platId);
            a.setUpdateUserId(platId);
            flowTaskActorService.save(a);
        }

        String extra = safeJson(Map.of("platIds", platIds));
        saveActionLog(systemId, tenantId, rec.getId(), ccTask.getId(), advance.nextNodeKey(),
                "cc", platId, null, now, extra);
        saveTrace(systemId, tenantId, rec.getId(), "cc", advance.nextNodeKey(), advance.nextNodeKey(),
                extra, platId, now);
    }

    private static List<Long> parseCcPlatIds(JsonNode cfg) {
        LinkedHashSet<Long> set = new LinkedHashSet<>();
        JsonNode arr = cfg.path("plat_ids");
        if (arr.isArray()) {
            for (JsonNode x : arr) {
                if (x.isNumber()) {
                    set.add(x.asLong());
                } else if (x.isTextual()) {
                    String s = x.asText().trim();
                    if (!s.isEmpty()) {
                        try {
                            set.add(Long.parseLong(s));
                        } catch (NumberFormatException ignore) {
                            // skip invalid
                        }
                    }
                }
            }
        }
        return new ArrayList<>(set);
    }

    private void cancelPendingTasksSameNode(Long recordId, String nodeKey, Long completedTaskId, Long platId, LocalDateTime now) {
        if (recordId == null || nodeKey == null || nodeKey.isBlank() || completedTaskId == null) {
            return;
        }
        flowTaskService.lambdaUpdate()
                .eq(FlowTask::getRecordId, recordId)
                .eq(FlowTask::getNodeKey, nodeKey)
                .eq(FlowTask::getStatus, 1)
                .ne(FlowTask::getId, completedTaskId)
                .set(FlowTask::getStatus, 5)
                .set(FlowTask::getFinishTime, now)
                .set(FlowTask::getUpdateUserId, platId)
                .update();
    }

    private boolean hasPendingSameNodeApproveTasks(Long recordId, String nodeKey) {
        if (recordId == null || nodeKey == null || nodeKey.isBlank()) {
            return false;
        }
        Long c = flowTaskService.lambdaQuery()
                .eq(FlowTask::getRecordId, recordId)
                .eq(FlowTask::getNodeKey, nodeKey)
                .eq(FlowTask::getStatus, 1)
                .count();
        return c != null && c > 0;
    }

    /**
     * 创建审批待办：默认单人；
     * {@code sign_mode=all} 会签（每人一条待办，全同意后才推进）；
     * {@code sign_mode=any} 或签（单条待办，candidateJson 存候选人，任一人可办）。
     *
     * @param assigneeOverrideSingle 非会签/或签多人时覆盖主处理人（如子流程首任务由父侧办理人触发）
     */
    private FlowTask createApproveTasksForNode(FlowRecord rec,
                                             String nodeKey,
                                             String nodeName,
                                             Long platId,
                                             long systemId,
                                             long tenantId,
                                             LocalDateTime now,
                                             Long assigneeOverrideSingle) {
        JsonNode n = findNodeByKey(rec.getGraphJson(), nodeKey);
        if (n == null) {
            throw new BusinessException(400, "审批节点不存在");
        }
        String name = (nodeName != null && !nodeName.isBlank()) ? nodeName : n.path("name").asText(null);
        JsonNode cfg = n.path("config");
        String mode = cfg.path("sign_mode").asText("single").trim();
        List<Long> platIds = parseCcPlatIds(cfg);

        if ("all".equalsIgnoreCase(mode)) {
            if (platIds.size() < 2) {
                throw new BusinessException(400, "会签(all)需要 config.plat_ids 至少 2 个处理人");
            }
            FlowTask first = null;
            for (Long pid : platIds) {
                FlowTask t = new FlowTask();
                t.setSystemId(systemId);
                t.setTenantId(tenantId);
                t.setRecordId(rec.getId());
                t.setNodeKey(nodeKey);
                t.setNodeName(name);
                t.setTaskType("approve");
                t.setAssigneePlatId(pid);
                t.setCandidateJson(null);
                t.setStatus(1);
                t.setCreateUserId(platId);
                t.setUpdateUserId(platId);
                flowTaskService.save(t);
                if (first == null) {
                    first = t;
                }
            }
            return first;
        }

        if ("any".equalsIgnoreCase(mode)) {
            if (platIds.size() < 2) {
                throw new BusinessException(400, "或签(any)需要 config.plat_ids 至少 2 个处理人");
            }
            List<Long> candidates = new ArrayList<>(platIds);
            if (assigneeOverrideSingle != null && !candidates.contains(assigneeOverrideSingle)) {
                candidates.add(0, assigneeOverrideSingle);
            }
            Long assignee = assigneeOverrideSingle != null ? assigneeOverrideSingle : candidates.get(0);
            FlowTask t = new FlowTask();
            t.setSystemId(systemId);
            t.setTenantId(tenantId);
            t.setRecordId(rec.getId());
            t.setNodeKey(nodeKey);
            t.setNodeName(name);
            t.setTaskType("approve");
            t.setAssigneePlatId(assignee);
            try {
                t.setCandidateJson(objectMapper.writeValueAsString(candidates));
            } catch (Exception e) {
                throw new BusinessException(400, "或签候选列表序列化失败");
            }
            t.setStatus(1);
            t.setCreateUserId(platId);
            t.setUpdateUserId(platId);
            flowTaskService.save(t);
            return t;
        }

        if (platIds.size() > 1) {
            throw new BusinessException(400, "多个处理人请设置 sign_mode=all（会签）或 sign_mode=any（或签）");
        }

        Long assignee;
        if (platIds.size() == 1) {
            assignee = platIds.get(0);
        } else if (assigneeOverrideSingle != null) {
            assignee = assigneeOverrideSingle;
        } else {
            assignee = rec.getStarterPlatId();
        }

        FlowTask t = new FlowTask();
        t.setSystemId(systemId);
        t.setTenantId(tenantId);
        t.setRecordId(rec.getId());
        t.setNodeKey(nodeKey);
        t.setNodeName(name);
        t.setTaskType("approve");
        t.setAssigneePlatId(assignee);
        t.setCandidateJson(null);
        t.setStatus(1);
        t.setCreateUserId(platId);
        t.setUpdateUserId(platId);
        flowTaskService.save(t);
        return t;
    }

    private void copyRecordVarsFromParent(long parentRecordId,
                                          long childRecordId,
                                          long systemId,
                                          long tenantId,
                                          long platId) {
        List<FlowRecordVar> list = flowRecordVarService.lambdaQuery()
                .eq(FlowRecordVar::getSystemId, systemId)
                .eq(FlowRecordVar::getTenantId, tenantId)
                .eq(FlowRecordVar::getRecordId, parentRecordId)
                .list();
        if (list == null || list.isEmpty()) {
            return;
        }
        List<FlowRecordVar> batch = new ArrayList<>();
        for (FlowRecordVar v : list) {
            if (v.getVarKey() == null || v.getVarKey().isBlank()) {
                continue;
            }
            FlowRecordVar c = new FlowRecordVar();
            c.setSystemId(systemId);
            c.setTenantId(tenantId);
            c.setRecordId(childRecordId);
            c.setVarKey(v.getVarKey());
            c.setVarType(v.getVarType());
            c.setVarValue(v.getVarValue());
            c.setCreateUserId(platId);
            c.setUpdateUserId(platId);
            batch.add(c);
        }
        if (!batch.isEmpty()) {
            flowRecordVarService.saveBatch(batch);
        }
    }

    private ChildStartResult startSubflowChildCore(FlowRecord parentRec,
                                                   FlowTask completedParentTask,
                                                   AdvanceResult advance,
                                                   Long platId,
                                                   long systemId,
                                                   long tenantId,
                                                   LocalDateTime now) {
        JsonNode subNode = findNodeByKey(parentRec.getGraphJson(), advance.nextNodeKey());
        if (subNode == null) {
            throw new BusinessException(400, "subflow 节点不存在");
        }
        JsonNode cfg = subNode.path("config");
        String subCode = cfg.path("sub_temp_code").asText(null);
        if (subCode == null || subCode.isBlank()) {
            throw new BusinessException(400, "subflow 节点缺少 config.sub_temp_code");
        }
        boolean copyVars = cfg.path("copy_vars").asBoolean(false);

        FlowTemp temp = flowTempService.lambdaQuery()
                .eq(FlowTemp::getSystemId, systemId)
                .eq(FlowTemp::getTenantId, tenantId)
                .eq(FlowTemp::getTempCode, subCode.trim())
                .eq(FlowTemp::getStatus, 1)
                .last("limit 1")
                .one();
        if (temp == null) {
            throw new BusinessException(404, "子流程模板不存在或已停用: " + subCode.trim());
        }
        if (temp.getLatestVerNo() == null || temp.getLatestVerNo() <= 0) {
            throw new BusinessException(400, "子流程尚未发布");
        }
        FlowTempVer ver = flowTempVerService.lambdaQuery()
                .eq(FlowTempVer::getSystemId, systemId)
                .eq(FlowTempVer::getTenantId, tenantId)
                .eq(FlowTempVer::getTempId, temp.getId())
                .eq(FlowTempVer::getVerNo, temp.getLatestVerNo())
                .eq(FlowTempVer::getPublishStatus, 2)
                .last("limit 1")
                .one();
        if (ver == null) {
            throw new BusinessException(400, "未找到子流程已发布版本");
        }
        if (ver.getGraphJson() == null || ver.getGraphJson().isBlank()) {
            throw new BusinessException(400, "子流程发布版本缺少 graphJson");
        }

        NodeInfo firstApprove = resolveFirstApproveNode(ver.getGraphJson());
        Long rootId = parentRec.getRootRecordId() != null ? parentRec.getRootRecordId() : parentRec.getId();
        String childBizId = parentRec.getBizId() + ":sub:" + parentRec.getId() + ":" + advance.nextNodeKey() + ":" + System.nanoTime();

        FlowRecord child = new FlowRecord();
        child.setSystemId(systemId);
        child.setTenantId(tenantId);
        child.setRootRecordId(rootId);
        child.setParentRecordId(parentRec.getId());
        child.setParentNodeKey(advance.nextNodeKey());
        child.setParentTaskId(completedParentTask != null ? completedParentTask.getId() : null);
        child.setTempId(temp.getId());
        child.setTempVerNo(ver.getVerNo());
        child.setGraphJson(ver.getGraphJson());
        child.setFormJson(ver.getFormJson());
        child.setBizType(parentRec.getBizType());
        child.setBizId(childBizId);
        String pt = parentRec.getTitle();
        child.setTitle(pt == null || pt.isBlank() ? "[子流程]" : pt.trim() + " [子流程]");
        child.setStarterPlatId(parentRec.getStarterPlatId());
        child.setStatus(1);
        child.setCurrentNodeKey(firstApprove.nodeKey());
        child.setStartTime(now);
        child.setCreateUserId(platId);
        child.setUpdateUserId(platId);
        flowRecordService.save(child);

        if (copyVars) {
            copyRecordVarsFromParent(parentRec.getId(), child.getId(), systemId, tenantId, platId);
        }

        flowRecordGraphSyncService.syncFromGraphJson(child, platId, ver.getId());

        Long assigneePlatId;
        if (completedParentTask != null && completedParentTask.getAssigneePlatId() != null) {
            assigneePlatId = completedParentTask.getAssigneePlatId();
        } else {
            assigneePlatId = platId;
        }
        if (assigneePlatId == null) {
            assigneePlatId = parentRec.getStarterPlatId();
        }

        FlowTask childTask = createApproveTasksForNode(child, firstApprove.nodeKey(), firstApprove.nodeName(), platId, systemId, tenantId, now, assigneePlatId);

        FlowLogAction startLog = new FlowLogAction();
        startLog.setSystemId(systemId);
        startLog.setTenantId(tenantId);
        startLog.setRecordId(child.getId());
        startLog.setTaskId(childTask.getId());
        startLog.setNodeKey(childTask.getNodeKey());
        startLog.setAction("start");
        startLog.setActorPlatId(platId);
        startLog.setActionTime(now);
        startLog.setCreateUserId(platId);
        startLog.setUpdateUserId(platId);
        flowLogActionService.save(startLog);

        parentRec.setCurrentNodeKey(advance.nextNodeKey());
        parentRec.setUpdateUserId(platId);
        flowRecordService.updateById(parentRec);

        String extra = safeJson(Map.of(
                "childRecordId", child.getId(),
                "childTaskId", childTask.getId(),
                "subTempCode", subCode.trim()));
        saveActionLog(systemId, tenantId, parentRec.getId(),
                completedParentTask != null ? completedParentTask.getId() : null,
                advance.nextNodeKey(), "subflow_start", platId, null, now, extra);
        saveTrace(systemId, tenantId, parentRec.getId(), "branch",
                completedParentTask != null ? completedParentTask.getNodeKey() : advance.nextNodeKey(),
                advance.nextNodeKey(),
                safeJson(Map.of("event", "subflow_start", "childRecordId", child.getId(), "childTaskId", childTask.getId())),
                platId, now);

        return new ChildStartResult(child.getId(), childTask.getId());
    }

    private TaskActionResult enterSubflowChild(FlowRecord parentRec,
                                               FlowTask completedParentTask,
                                               AdvanceResult advance,
                                               Long parentInstanceId,
                                               Long platId,
                                               long systemId,
                                               long tenantId,
                                               LocalDateTime now) {
        ChildStartResult cs = startSubflowChildCore(parentRec, completedParentTask, advance, platId, systemId, tenantId, now);
        return new TaskActionResult(parentInstanceId, completedParentTask.getId(), completedParentTask.getStatus(),
                parentRec.getStatus(), advance.nextNodeKey(), advance.nextNodeName(), cs.childTaskId(), 1, "subflow_started");
    }

    private void resumeParentAfterSubflowChildCompleted(FlowRecord completedChild, long platId, LocalDateTime now) {
        Long parentId = completedChild.getParentRecordId();
        String subflowNodeKey = completedChild.getParentNodeKey();
        if (parentId == null || subflowNodeKey == null || subflowNodeKey.isBlank()) {
            return;
        }
        FlowRecord parent = flowRecordService.getById(parentId);
        if (parent == null || parent.getStatus() == null || parent.getStatus() != 1) {
            return;
        }
        long systemId = parent.getSystemId();
        long tenantId = parent.getTenantId();

        // 子流程结束后：按 subflow 节点配置回写变量到父实例
        tryCopyVarsFromChildToParent(parent, completedChild, subflowNodeKey, platId, now);

        AdvanceResult advance = advanceFromNode(parent, subflowNodeKey);
        advance = consumeCcChain(parent, advance, platId, systemId, tenantId, now);
        if (advance.end()) {
            parent.setStatus(2);
            parent.setCurrentNodeKey(null);
            parent.setEndTime(now);
            parent.setUpdateUserId(platId);
            flowRecordService.updateById(parent);
            saveTrace(systemId, tenantId, parent.getId(), "end", subflowNodeKey, null,
                    safeJson(Map.of("source", "subflow_child")), platId, now);
            if (parent.getParentRecordId() != null) {
                resumeParentAfterSubflowChildCompleted(parent, platId, now);
            }
            return;
        }
        if (isSubflowNode(parent.getGraphJson(), advance.nextNodeKey())) {
            startSubflowChildCore(parent, null, advance, platId, systemId, tenantId, now);
            return;
        }

        FlowTask nextTask = createApproveTasksForNode(parent, advance.nextNodeKey(), advance.nextNodeName(), platId, systemId, tenantId, now, null);

        parent.setCurrentNodeKey(nextTask.getNodeKey());
        parent.setUpdateUserId(platId);
        flowRecordService.updateById(parent);

        saveTrace(systemId, tenantId, parent.getId(), "branch", subflowNodeKey, nextTask.getNodeKey(),
                safeJson(Map.of("source", "subflow_child_resume")), platId, now);
    }

    private void tryCopyVarsFromChildToParent(FlowRecord parent,
                                              FlowRecord child,
                                              String subflowNodeKey,
                                              long platId,
                                              LocalDateTime now) {
        if (parent == null || child == null) {
            return;
        }
        JsonNode subNode = findNodeByKey(parent.getGraphJson(), subflowNodeKey);
        if (subNode == null) {
            return;
        }
        JsonNode cfg = subNode.path("config");
        JsonNode outVars = cfg.path("out_vars");      // array of child keys
        JsonNode outVarMap = cfg.path("out_var_map"); // object: childKey -> parentKey
        boolean hasOutVars = outVars != null && outVars.isArray() && outVars.size() > 0;
        boolean hasOutMap = outVarMap != null && outVarMap.isObject() && outVarMap.size() > 0;
        if (!hasOutVars && !hasOutMap) {
            return;
        }

        long systemId = parent.getSystemId();
        long tenantId = parent.getTenantId();
        long parentRecordId = parent.getId();
        long childRecordId = child.getId();

        Map<String, String> mapping = new HashMap<>();
        if (hasOutVars) {
            for (JsonNode n : outVars) {
                if (n != null && n.isTextual()) {
                    String k = n.asText().trim();
                    if (!k.isEmpty()) {
                        mapping.put(k, k);
                    }
                }
            }
        }
        if (hasOutMap) {
            outVarMap.fields().forEachRemaining(e -> {
                String ck = e.getKey() == null ? null : e.getKey().trim();
                String pk = e.getValue() == null ? null : e.getValue().asText(null);
                if (ck != null && !ck.isEmpty() && pk != null && !pk.trim().isEmpty()) {
                    mapping.put(ck, pk.trim());
                }
            });
        }
        if (mapping.isEmpty()) {
            return;
        }

        List<FlowRecordVar> childVars = flowRecordVarService.lambdaQuery()
                .eq(FlowRecordVar::getSystemId, systemId)
                .eq(FlowRecordVar::getTenantId, tenantId)
                .eq(FlowRecordVar::getRecordId, childRecordId)
                .in(FlowRecordVar::getVarKey, mapping.keySet())
                .list();
        if (childVars == null || childVars.isEmpty()) {
            return;
        }

        int copied = 0;
        for (FlowRecordVar cv : childVars) {
            if (cv.getVarKey() == null || cv.getVarKey().isBlank()) {
                continue;
            }
            String parentKey = mapping.get(cv.getVarKey());
            if (parentKey == null || parentKey.isBlank()) {
                continue;
            }
            FlowRecordVar exist = flowRecordVarService.lambdaQuery()
                    .eq(FlowRecordVar::getSystemId, systemId)
                    .eq(FlowRecordVar::getTenantId, tenantId)
                    .eq(FlowRecordVar::getRecordId, parentRecordId)
                    .eq(FlowRecordVar::getVarKey, parentKey)
                    .last("limit 1")
                    .one();
            if (exist == null) {
                FlowRecordVar pv = new FlowRecordVar();
                pv.setSystemId(systemId);
                pv.setTenantId(tenantId);
                pv.setRecordId(parentRecordId);
                pv.setVarKey(parentKey);
                pv.setVarType(cv.getVarType());
                pv.setVarValue(cv.getVarValue());
                pv.setCreateUserId(platId);
                pv.setUpdateUserId(platId);
                flowRecordVarService.save(pv);
            } else {
                exist.setVarType(cv.getVarType());
                exist.setVarValue(cv.getVarValue());
                exist.setUpdateUserId(platId);
                flowRecordVarService.updateById(exist);
            }
            copied++;
        }

        if (copied > 0) {
            saveTrace(systemId, tenantId, parentRecordId, "subflow_vars",
                    subflowNodeKey, subflowNodeKey,
                    safeJson(Map.of("childRecordId", childRecordId, "copied", copied)),
                    platId, now);
        }
    }

    private void saveTrace(long systemId, long tenantId, long recordId, String eventType,
                           String fromNodeKey, String toNodeKey, String detailJson, long platId, LocalDateTime now) {
        FlowLogTrace t = new FlowLogTrace();
        t.setSystemId(systemId);
        t.setTenantId(tenantId);
        t.setRecordId(recordId);
        t.setEventType(eventType);
        t.setFromNodeKey(fromNodeKey);
        t.setToNodeKey(toNodeKey);
        t.setDetailJson(detailJson);
        t.setEventTime(now);
        t.setCreateUserId(platId);
        t.setUpdateUserId(platId);
        flowLogTraceService.save(t);
    }

    /**
     * 撤回：仅发起人；运行中实例置为已撤回，待办置为取消。
     */
    @Transactional(rollbackFor = Exception.class)
    public void withdrawFlow(Long recordId) {
        Long platId = AuthContextHolder.getPlatId();
        if (platId == null) {
            throw new BusinessException(401, "未登录");
        }
        long systemId = AuthContextHolder.getSystemIdOrDefault();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (recordId == null) {
            throw new BusinessException(400, "recordId 不能为空");
        }
        FlowRecord rec = flowRecordService.lambdaQuery()
                .eq(FlowRecord::getSystemId, systemId)
                .eq(FlowRecord::getTenantId, tenantId)
                .eq(FlowRecord::getId, recordId)
                .last("limit 1")
                .one();
        if (rec == null) {
            throw new BusinessException(404, "实例不存在");
        }
        if (rec.getStatus() == null || rec.getStatus() != 1) {
            throw new BusinessException(400, "仅运行中实例可撤回");
        }
        if (rec.getStarterPlatId() == null || !rec.getStarterPlatId().equals(platId)) {
            throw new BusinessException(403, "仅发起人可撤回");
        }
        LocalDateTime now = LocalDateTime.now();
        rec.setStatus(3);
        rec.setCurrentNodeKey(null);
        rec.setEndTime(now);
        rec.setUpdateUserId(platId);
        flowRecordService.updateById(rec);

        flowTaskService.lambdaUpdate()
                .eq(FlowTask::getRecordId, recordId)
                .eq(FlowTask::getStatus, 1)
                .set(FlowTask::getStatus, 5)
                .set(FlowTask::getFinishTime, now)
                .set(FlowTask::getUpdateUserId, platId)
                .update();

        saveActionLog(systemId, tenantId, recordId, null, null, "withdraw", platId, null, now, null);
        saveTrace(systemId, tenantId, recordId, "end", null, null, "{\"action\":\"withdraw\"}", platId, now);
    }

    /**
     * 终止：发起人；运行中实例置为已终止，待办取消。
     */
    @Transactional(rollbackFor = Exception.class)
    public void terminateFlow(Long recordId, String reason) {
        Long platId = AuthContextHolder.getPlatId();
        if (platId == null) {
            throw new BusinessException(401, "未登录");
        }
        long systemId = AuthContextHolder.getSystemIdOrDefault();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (recordId == null) {
            throw new BusinessException(400, "recordId 不能为空");
        }
        FlowRecord rec = flowRecordService.lambdaQuery()
                .eq(FlowRecord::getSystemId, systemId)
                .eq(FlowRecord::getTenantId, tenantId)
                .eq(FlowRecord::getId, recordId)
                .last("limit 1")
                .one();
        if (rec == null) {
            throw new BusinessException(404, "实例不存在");
        }
        if (rec.getStatus() == null || rec.getStatus() != 1) {
            throw new BusinessException(400, "仅运行中实例可终止");
        }
        if (rec.getStarterPlatId() == null || !rec.getStarterPlatId().equals(platId)) {
            throw new BusinessException(403, "仅发起人可终止");
        }
        LocalDateTime now = LocalDateTime.now();
        rec.setStatus(4);
        rec.setCurrentNodeKey(null);
        rec.setEndTime(now);
        rec.setUpdateUserId(platId);
        flowRecordService.updateById(rec);

        flowTaskService.lambdaUpdate()
                .eq(FlowTask::getRecordId, recordId)
                .eq(FlowTask::getStatus, 1)
                .set(FlowTask::getStatus, 5)
                .set(FlowTask::getFinishTime, now)
                .set(FlowTask::getUpdateUserId, platId)
                .update();

        String r = reason == null ? null : reason.trim();
        saveActionLog(systemId, tenantId, recordId, null, null, "terminate", platId, r, now, null);
        Map<String, Object> termDetail = new HashMap<>();
        termDetail.put("action", "terminate");
        termDetail.put("reason", r);
        saveTrace(systemId, tenantId, recordId, "end", null, null, safeJson(termDetail), platId, now);
    }

    /**
     * 转交当前待办给指定处理人。
     */
    @Transactional(rollbackFor = Exception.class)
    public void transferTask(Long recordId, Long taskId, Long toPlatId) {
        Long platId = AuthContextHolder.getPlatId();
        if (platId == null) {
            throw new BusinessException(401, "未登录");
        }
        long systemId = AuthContextHolder.getSystemIdOrDefault();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (recordId == null || taskId == null || toPlatId == null) {
            throw new BusinessException(400, "recordId/taskId/toPlatId 不能为空");
        }
        FlowRecord rec = flowRecordService.lambdaQuery()
                .eq(FlowRecord::getSystemId, systemId)
                .eq(FlowRecord::getTenantId, tenantId)
                .eq(FlowRecord::getId, recordId)
                .last("limit 1")
                .one();
        if (rec == null) {
            throw new BusinessException(404, "实例不存在");
        }
        if (rec.getStatus() == null || rec.getStatus() != 1) {
            throw new BusinessException(400, "实例非运行中");
        }
        FlowTask task = flowTaskService.lambdaQuery()
                .eq(FlowTask::getSystemId, systemId)
                .eq(FlowTask::getTenantId, tenantId)
                .eq(FlowTask::getId, taskId)
                .eq(FlowTask::getRecordId, recordId)
                .last("limit 1")
                .one();
        if (task == null) {
            throw new BusinessException(404, "任务不存在");
        }
        if (task.getStatus() == null || task.getStatus() != 1) {
            throw new BusinessException(400, "任务非待处理状态");
        }
        if (!canActOnTask(task, platId)) {
            throw new BusinessException(403, "无权转交该任务");
        }
        LocalDateTime now = LocalDateTime.now();
        task.setAssigneePlatId(toPlatId);
        task.setCandidateJson(null);
        task.setUpdateUserId(platId);
        flowTaskService.updateById(task);

        saveActionLog(systemId, tenantId, recordId, taskId, task.getNodeKey(), "transfer", platId,
                "toPlatId=" + toPlatId, now, null);
        saveTrace(systemId, tenantId, recordId, "branch", task.getNodeKey(), task.getNodeKey(),
                safeJson(Map.of("action", "transfer", "toPlatId", toPlatId)), platId, now);
    }
}
