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
import java.util.Set;

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

    /**
     * 平台态抄送聚合：不要求已进入系统，直接聚合所有 system/tenant 下属于我的抄送任务。
     * actor.status：1=未读/有效 2=已读/失效（当前按该口径实现）。
     */
    public List<FlowTask> listMyCcTasksAcrossSystems(Integer limit, Integer onlyUnread, Long systemId, Long tenantId) {
        return listMyCcTasksAcrossSystems(limit, onlyUnread, systemId, tenantId, null);
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

    /**
     * 平台态抄送标记已读：不要求已进入系统。
     */
    public void markMyCcReadAcrossSystems(Long taskId) {
        Long platId = AuthContextHolder.getPlatId();
        if (platId == null) {
            throw new BusinessException(401, "未登录");
        }
        if (taskId == null) {
            throw new BusinessException(400, "taskId 不能为空");
        }

        boolean ok = flowTaskActorService.lambdaUpdate()
                .eq(FlowTaskActor::getTaskId, taskId)
                .eq(FlowTaskActor::getActorPlatId, platId)
                .eq(FlowTaskActor::getActorRole, "cc")
                .eq(FlowTaskActor::getStatus, 1)
                .set(FlowTaskActor::getStatus, 2)
                .set(FlowTaskActor::getUpdateUserId, platId)
                .update();
        if (!ok) {
            FlowTaskActor exist = flowTaskActorService.lambdaQuery()
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

    /**
     * 平台态待办聚合：不要求已进入系统，直接聚合所有 system/tenant 下属于我的待办（status=1）。
     * 说明：MVP 仅按处理人/候选人匹配，不做“我是否有该 system 权限”的二次校验（后续对接 AuthContext）。
     */
    public List<FlowTask> listMyPendingTasksAcrossSystems(Integer limit) {
        return listMyPendingTasksAcrossSystems(limit, null, null);
    }

    /**
     * 平台态待办聚合（可选按 systemId/tenantId 过滤）。
     */
    public List<FlowTask> listMyPendingTasksAcrossSystems(Integer limit, Long systemId, Long tenantId) {
        return listMyPendingTasksAcrossSystems(limit, systemId, tenantId, null);
    }

    /**
     * 平台态待办聚合（可选按 systemId/tenantId 过滤；可选 systemAllowList 白名单过滤）。
     */
    public List<FlowTask> listMyPendingTasksAcrossSystems(Integer limit, Long systemId, Long tenantId, Set<Long> systemAllowList) {
        Long platId = AuthContextHolder.getPlatId();
        if (platId == null) {
            throw new BusinessException(401, "未登录");
        }

        int lim = (limit == null || limit <= 0) ? 50 : Math.min(limit, 200);
        String likeToken = "\"" + platId + "\"";

        var q = flowTaskService.lambdaQuery()
                .eq(FlowTask::getStatus, 1)
                .and(w -> w.eq(FlowTask::getAssigneePlatId, platId)
                        .or()
                        .like(FlowTask::getCandidateJson, likeToken))
                .orderByDesc(FlowTask::getCreateTime);
        if (systemId != null && systemId > 0) {
            q.eq(FlowTask::getSystemId, systemId);
        }
        if (systemAllowList != null) {
            if (systemAllowList.isEmpty()) {
                return List.of();
            }
            q.in(FlowTask::getSystemId, systemAllowList);
        }
        if (tenantId != null && tenantId >= 0) {
            q.eq(FlowTask::getTenantId, tenantId);
        }
        List<FlowTask> candidates = q.last("limit " + lim).list();

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

    public List<FlowTask> listMyCcTasksAcrossSystems(Integer limit, Integer onlyUnread, Long systemId, Long tenantId, Set<Long> systemAllowList) {
        Long platId = AuthContextHolder.getPlatId();
        if (platId == null) {
            throw new BusinessException(401, "未登录");
        }

        int lim = (limit == null || limit <= 0) ? 50 : Math.min(limit, 200);
        boolean unreadOnly = onlyUnread != null && onlyUnread == 1;

        var aq = flowTaskActorService.lambdaQuery()
                .eq(FlowTaskActor::getActorPlatId, platId)
                .eq(FlowTaskActor::getActorRole, "cc");
        if (systemId != null && systemId > 0) {
            aq.eq(FlowTaskActor::getSystemId, systemId);
        }
        if (systemAllowList != null) {
            if (systemAllowList.isEmpty()) {
                return List.of();
            }
            aq.in(FlowTaskActor::getSystemId, systemAllowList);
        }
        if (tenantId != null && tenantId >= 0) {
            aq.eq(FlowTaskActor::getTenantId, tenantId);
        }
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

