package com.unique.examine.web.service;

import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.core.security.AuthContextHolder;
import com.unique.examine.flow.manage.FlowEngineService;
import com.unique.examine.flow.entity.po.FlowRecord;
import com.unique.examine.flow.entity.po.FlowTask;
import com.unique.examine.flow.service.IFlowRecordService;
import com.unique.examine.flow.service.IFlowTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class FlowBizActionService {

    @Autowired
    private IFlowRecordService flowRecordService;
    @Autowired
    private IFlowTaskService flowTaskService;
    @Autowired
    private FlowTaskInboxService flowTaskInboxService;
    @Autowired
    private FlowEngineService flowEngineService;

    public FlowEngineService.TaskActionResult approveByBiz(String bizType, String bizId, String commentText) {
        FlowTask t = resolveSingleActionablePendingTask(bizType, bizId);
        return flowEngineService.approve(t.getRecordId(), t.getId(), commentText);
    }

    public FlowEngineService.TaskActionResult approveByBiz(String bizType, String bizId, Long taskId, String commentText) {
        FlowTask t = resolveActionablePendingTaskById(bizType, bizId, taskId);
        return flowEngineService.approve(t.getRecordId(), t.getId(), commentText);
    }

    public FlowEngineService.TaskActionResult rejectByBiz(String bizType, String bizId, String commentText) {
        FlowTask t = resolveSingleActionablePendingTask(bizType, bizId);
        return flowEngineService.reject(t.getRecordId(), t.getId(), commentText);
    }

    public FlowEngineService.TaskActionResult rejectByBiz(String bizType, String bizId, Long taskId, String commentText) {
        FlowTask t = resolveActionablePendingTaskById(bizType, bizId, taskId);
        return flowEngineService.reject(t.getRecordId(), t.getId(), commentText);
    }

    public List<FlowTask> listActionablePendingTasksByBiz(String bizType, String bizId) {
        FlowRecord rec = resolveLatestRunningRecord(bizType, bizId);
        return flowTaskInboxService.listMyPendingTasksForRecord(rec.getId(), 200);
    }

    private FlowTask resolveActionablePendingTaskById(String bizType, String bizId, Long taskId) {
        if (taskId == null) {
            throw new BusinessException(400, "taskId 不能为空");
        }
        FlowRecord rec = resolveLatestRunningRecord(bizType, bizId);
        Long platId = AuthContextHolder.getPlatId();
        if (platId == null) {
            throw new BusinessException(401, "未登录");
        }

        FlowTask t = flowTaskService.lambdaQuery()
                .eq(FlowTask::getSystemId, rec.getSystemId())
                .eq(FlowTask::getTenantId, rec.getTenantId())
                .eq(FlowTask::getId, taskId)
                .last("limit 1")
                .one();
        if (t == null || t.getRecordId() == null || !Objects.equals(t.getRecordId(), rec.getId())) {
            throw new BusinessException(404, "待办不存在");
        }
        if (t.getStatus() == null || t.getStatus() != 1) {
            throw new BusinessException(400, "待办非待处理");
        }

        List<FlowTask> actionable = flowTaskInboxService.listMyPendingTasksForRecord(rec.getId(), 200);
        boolean ok = false;
        for (FlowTask a : actionable) {
            if (a.getId() != null && Objects.equals(a.getId(), taskId)) {
                ok = true;
                break;
            }
        }
        if (!ok) {
            throw new BusinessException(403, "无权办理该待办");
        }
        return t;
    }

    private FlowTask resolveSingleActionablePendingTask(String bizType, String bizId) {
        FlowRecord rec = resolveLatestRunningRecord(bizType, bizId);

        List<FlowTask> tasks = flowTaskInboxService.listMyPendingTasksForRecord(rec.getId(), 50);
        if (tasks.isEmpty()) {
            throw new BusinessException(404, "未找到可办理的待办");
        }
        if (tasks.size() > 1) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("recordId", rec.getId());
            detail.put("taskCount", tasks.size());
            detail.put("tasks", tasks.stream().map(t -> Map.of(
                    "taskId", t.getId(),
                    "nodeKey", t.getNodeKey(),
                    "nodeName", t.getNodeName(),
                    "taskType", t.getTaskType()
            )).toList());
            throw new BusinessException(409, "存在多个可办理待办，请明确 taskId: " + detail);
        }
        return tasks.get(0);
    }

    private FlowRecord resolveLatestRunningRecord(String bizType, String bizId) {
        if (bizType == null || bizType.isBlank() || bizId == null || bizId.isBlank()) {
            throw new BusinessException(400, "bizType/bizId 不能为空");
        }
        Long platId = AuthContextHolder.getPlatId();
        if (platId == null) {
            throw new BusinessException(401, "未登录");
        }
        long systemId = AuthContextHolder.getSystemIdOrDefault();
        if (systemId == 0L) {
            throw new BusinessException(403, "请先进入自建系统");
        }
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        FlowRecord rec = flowRecordService.lambdaQuery()
                .eq(FlowRecord::getSystemId, systemId)
                .eq(FlowRecord::getTenantId, tenantId)
                .eq(FlowRecord::getBizType, bizType)
                .eq(FlowRecord::getBizId, bizId)
                .orderByDesc(FlowRecord::getId)
                .last("limit 1")
                .one();
        if (rec == null) {
            throw new BusinessException(404, "实例不存在");
        }
        if (rec.getStatus() == null || rec.getStatus() != 1) {
            throw new BusinessException(400, "实例非运行中");
        }
        return rec;
    }
}

