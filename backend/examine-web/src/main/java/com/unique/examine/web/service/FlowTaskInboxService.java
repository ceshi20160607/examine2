package com.unique.examine.web.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.core.security.AuthContextHolder;
import com.unique.examine.flow.entity.po.FlowTaskActor;
import com.unique.examine.flow.service.IFlowTaskActorService;
import com.unique.examine.flow.entity.po.FlowTask;
import com.unique.examine.flow.service.IFlowTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class FlowTaskInboxService {

    @Autowired
    private IFlowTaskService flowTaskService;
    @Autowired
    private IFlowTaskActorService flowTaskActorService;
    @Autowired
    private ObjectMapper objectMapper;

    public List<FlowTask> listMyCcTasks(Integer limit, Integer onlyUnread) {
        Long platId = AuthContextHolder.getPlatId();
        if (platId == null) {
            throw new BusinessException(401, "未登录");
        }
        long systemId = AuthContextHolder.getSystemIdOrDefault();
        if (systemId == 0L) {
            throw new BusinessException(403, "请先进入自建系统");
        }
        long tenantId = AuthContextHolder.getTenantIdOrDefault();

        int lim = (limit == null || limit <= 0) ? 50 : Math.min(limit, 200);
        boolean unreadOnly = onlyUnread != null && onlyUnread == 1;

        var aq = flowTaskActorService.lambdaQuery()
                .eq(FlowTaskActor::getSystemId, systemId)
                .eq(FlowTaskActor::getTenantId, tenantId)
                .eq(FlowTaskActor::getActorPlatId, platId)
                .eq(FlowTaskActor::getActorRole, "cc");
        if (unreadOnly) {
            aq.eq(FlowTaskActor::getStatus, 1);
        }
        List<FlowTaskActor> actors = aq.orderByDesc(FlowTaskActor::getId)
                .last("limit " + lim)
                .list();
        if (actors == null || actors.isEmpty()) {
            return List.of();
        }

        List<Long> taskIds = new ArrayList<>();
        for (FlowTaskActor a : actors) {
            if (a.getTaskId() != null) {
                taskIds.add(a.getTaskId());
            }
        }
        if (taskIds.isEmpty()) {
            return List.of();
        }

        List<FlowTask> tasks = flowTaskService.lambdaQuery()
                .eq(FlowTask::getSystemId, systemId)
                .eq(FlowTask::getTenantId, tenantId)
                .in(FlowTask::getId, taskIds)
                .orderByDesc(FlowTask::getCreateTime)
                .list();
        if (tasks == null || tasks.isEmpty()) {
            return List.of();
        }
        Map<Long, FlowTask> map = new HashMap<>();
        for (FlowTask t : tasks) {
            map.put(t.getId(), t);
        }
        List<FlowTask> out = new ArrayList<>();
        for (Long tid : taskIds) {
            FlowTask t = map.get(tid);
            if (t != null) {
                out.add(t);
            }
        }
        return out;
    }

    public void markMyCcRead(Long taskId) {
        Long platId = AuthContextHolder.getPlatId();
        if (platId == null) {
            throw new BusinessException(401, "未登录");
        }
        long systemId = AuthContextHolder.getSystemIdOrDefault();
        if (systemId == 0L) {
            throw new BusinessException(403, "请先进入自建系统");
        }
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (taskId == null) {
            throw new BusinessException(400, "taskId 不能为空");
        }
        boolean ok = flowTaskActorService.lambdaUpdate()
                .eq(FlowTaskActor::getSystemId, systemId)
                .eq(FlowTaskActor::getTenantId, tenantId)
                .eq(FlowTaskActor::getTaskId, taskId)
                .eq(FlowTaskActor::getActorPlatId, platId)
                .eq(FlowTaskActor::getActorRole, "cc")
                .eq(FlowTaskActor::getStatus, 1)
                .set(FlowTaskActor::getStatus, 2)
                .set(FlowTaskActor::getUpdateUserId, platId)
                .update();
        if (!ok) {
            // idempotent: allow when already read or not exists
            FlowTaskActor exist = flowTaskActorService.lambdaQuery()
                    .eq(FlowTaskActor::getSystemId, systemId)
                    .eq(FlowTaskActor::getTenantId, tenantId)
                    .eq(FlowTaskActor::getTaskId, taskId)
                    .eq(FlowTaskActor::getActorPlatId, platId)
                    .eq(FlowTaskActor::getActorRole, "cc")
                    .last("limit 1")
                    .one();
            if (exist == null) {
                throw new BusinessException(404, "抄送不存在");
            }
        }
    }

    public List<FlowTask> listMyPendingTasks(Integer limit) {
        Long platId = AuthContextHolder.getPlatId();
        if (platId == null) {
            throw new BusinessException(401, "未登录");
        }
        long systemId = AuthContextHolder.getSystemIdOrDefault();
        if (systemId == 0L) {
            throw new BusinessException(403, "请先进入自建系统");
        }
        long tenantId = AuthContextHolder.getTenantIdOrDefault();

        int lim = (limit == null || limit <= 0) ? 50 : Math.min(limit, 200);
        String likeToken = "\"" + platId + "\"";

        List<FlowTask> candidates = flowTaskService.lambdaQuery()
                .eq(FlowTask::getSystemId, systemId)
                .eq(FlowTask::getTenantId, tenantId)
                .eq(FlowTask::getStatus, 1)
                .and(w -> w.eq(FlowTask::getAssigneePlatId, platId)
                        .or()
                        .like(FlowTask::getCandidateJson, likeToken))
                .orderByDesc(FlowTask::getCreateTime)
                .last("limit " + lim)
                .list();

        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        List<FlowTask> out = new ArrayList<>();
        for (FlowTask t : candidates) {
            if (canActOnTask(t, platId)) {
                out.add(t);
            }
        }
        return out;
    }

    public List<FlowTask> listMyPendingTasksForRecord(Long recordId, Integer limit) {
        Long platId = AuthContextHolder.getPlatId();
        if (platId == null) {
            throw new BusinessException(401, "未登录");
        }
        long systemId = AuthContextHolder.getSystemIdOrDefault();
        if (systemId == 0L) {
            throw new BusinessException(403, "请先进入自建系统");
        }
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (recordId == null) {
            throw new BusinessException(400, "recordId 不能为空");
        }

        int lim = (limit == null || limit <= 0) ? 50 : Math.min(limit, 200);
        String likeToken = "\"" + platId + "\"";
        List<FlowTask> candidates = flowTaskService.lambdaQuery()
                .eq(FlowTask::getSystemId, systemId)
                .eq(FlowTask::getTenantId, tenantId)
                .eq(FlowTask::getRecordId, recordId)
                .eq(FlowTask::getStatus, 1)
                .and(w -> w.eq(FlowTask::getAssigneePlatId, platId)
                        .or()
                        .like(FlowTask::getCandidateJson, likeToken))
                .orderByDesc(FlowTask::getCreateTime)
                .last("limit " + lim)
                .list();
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<FlowTask> out = new ArrayList<>();
        for (FlowTask t : candidates) {
            if (canActOnTask(t, platId)) {
                out.add(t);
            }
        }
        return out;
    }

    private boolean canActOnTask(FlowTask task, Long platId) {
        if (platId == null) {
            return false;
        }
        if (task.getAssigneePlatId() != null && Objects.equals(task.getAssigneePlatId(), platId)) {
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
                    if (n.isNumber() && Objects.equals(platId, n.asLong())) {
                        return true;
                    }
                    if (n.isTextual()) {
                        try {
                            if (Objects.equals(platId, Long.parseLong(n.asText().trim()))) {
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
}

