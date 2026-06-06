package com.unique.examine.manage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.examine.base.entity.BusinessRecord;
import com.unique.examine.base.entity.SystemMember;
import com.unique.examine.base.entity.WorkflowInstance;
import com.unique.examine.base.entity.WorkflowTask;
import com.unique.examine.base.entity.WorkflowTemplate;
import com.unique.examine.base.entity.WorkflowVersion;
import com.unique.examine.base.service.IBusinessRecordService;
import com.unique.examine.base.service.ISystemMemberService;
import com.unique.examine.base.service.IWorkflowInstanceService;
import com.unique.examine.base.service.IWorkflowTaskService;
import com.unique.examine.base.service.IWorkflowTemplateService;
import com.unique.examine.base.service.IWorkflowVersionService;
import com.unique.examine.manage.bo.WorkflowStartBO;
import com.unique.examine.manage.bo.WorkflowTaskActionBO;
import com.unique.examine.manage.bo.WorkflowTemplateSaveBO;
import com.unique.examine.manage.bo.WorkflowVersionSaveBO;
import com.unique.examine.manage.converter.EntityMapConverter;
import com.unique.examine.manage.enums.ErrorCode;
import com.unique.examine.manage.enums.StatusEnums;
import com.unique.examine.manage.exception.BusinessException;
import com.unique.examine.manage.security.SecurityContext;
import com.unique.examine.manage.service.PermissionService;
import com.unique.examine.manage.service.WorkflowManageService;
import com.unique.examine.manage.vo.PageResult;
import com.unique.examine.manage.vo.SimpleVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class WorkflowManageServiceImpl implements WorkflowManageService {
    private final IWorkflowTemplateService templateService;
    private final IWorkflowVersionService versionService;
    private final IWorkflowInstanceService instanceService;
    private final IWorkflowTaskService taskService;
    private final IBusinessRecordService recordService;
    private final ISystemMemberService systemMemberService;
    private final PermissionService permissionService;
    private final EntityMapConverter converter;

    @Override
    public PageResult<SimpleVO> templates(long pageNo, long pageSize, Long systemId, Long tenantId, Long moduleId) {
        permissionService.requireAction(systemId, tenantId, "workflow:view");
        IPage<WorkflowTemplate> page = templateService.page(Page.of(pageNo, pageSize), Wrappers.<WorkflowTemplate>lambdaQuery()
                .eq(WorkflowTemplate::getSystemId, systemId)
                .eq(WorkflowTemplate::getTenantId, tenantId)
                .eq(moduleId != null, WorkflowTemplate::getModuleId, moduleId)
                .orderByDesc(WorkflowTemplate::getUpdatedAt));
        return new PageResult<>(pageNo, pageSize, page.getTotal(), page.getRecords().stream().map(converter::toSimple).toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SimpleVO saveTemplate(WorkflowTemplateSaveBO bo) {
        permissionService.requireAction(bo.getSystemId(), bo.getTenantId(), "workflow:save");
        WorkflowTemplate template = new WorkflowTemplate();
        template.setSystemId(bo.getSystemId());
        template.setTenantId(bo.getTenantId());
        template.setModuleId(bo.getModuleId());
        template.setTemplateName(bo.getTemplateName());
        template.setStatus(bo.getStatus() == null ? StatusEnums.DRAFT : bo.getStatus());
        templateService.save(template);
        return converter.toSimple(template);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SimpleVO saveVersion(WorkflowVersionSaveBO bo) {
        WorkflowTemplate template = templateService.getById(bo.getTemplateId());
        if (template == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "流程模板不存在");
        }
        if (bo.getSystemId() != null && !bo.getSystemId().equals(template.getSystemId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "流程版本系统上下文与模板归属不一致");
        }
        if (bo.getTenantId() != null && !bo.getTenantId().equals(template.getTenantId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "流程版本租户上下文与模板归属不一致");
        }
        permissionService.requireAction(template.getSystemId(), template.getTenantId(), "workflow:save");
        WorkflowVersion version = new WorkflowVersion();
        version.setSystemId(template.getSystemId());
        version.setTenantId(template.getTenantId());
        version.setTemplateId(bo.getTemplateId());
        version.setVersionNo(parseVersionNo(bo.getVersionNo(), bo.getTemplateId()));
        version.setNodeJson(bo.getNodeJson());
        version.setEdgeJson(bo.getEdgeJson());
        version.setConditionJson(bo.getConditionJson());
        version.setSettingJson(bo.getSettingJson());
        version.setStatus(StatusEnums.DRAFT);
        versionService.save(version);
        return converter.toSimple(version);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SimpleVO publishVersion(Long versionId) {
        WorkflowVersion version = versionService.getById(versionId);
        if (version == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "流程版本不存在");
        }
        permissionService.requireAction(version.getSystemId(), version.getTenantId(), "workflow:publish");
        if (version.getNodeJson() == null || version.getNodeJson().isBlank()
                || version.getEdgeJson() == null || version.getEdgeJson().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "流程节点和连线不能为空");
        }
        version.setStatus(StatusEnums.PUBLISHED);
        version.setPublishedAt(LocalDateTime.now());
        versionService.updateById(version);
        WorkflowTemplate template = templateService.getById(version.getTemplateId());
        template.setCurrentVersionId(version.getId());
        template.setStatus(StatusEnums.PUBLISHED);
        templateService.updateById(template);
        return converter.toSimple(version);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SimpleVO start(WorkflowStartBO bo) {
        permissionService.requireAction(bo.getSystemId(), bo.getTenantId(), "workflow:start");
        BusinessRecord record = recordService.getById(bo.getRecordId());
        if (record == null || Integer.valueOf(1).equals(record.getIsDeleted())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "业务记录不存在");
        }
        WorkflowTemplate template = templateService.getOne(Wrappers.<WorkflowTemplate>lambdaQuery()
                .eq(WorkflowTemplate::getSystemId, bo.getSystemId())
                .eq(WorkflowTemplate::getTenantId, bo.getTenantId())
                .eq(WorkflowTemplate::getModuleId, bo.getModuleId())
                .eq(WorkflowTemplate::getStatus, StatusEnums.PUBLISHED), false);
        if (template == null || template.getCurrentVersionId() == null) {
            throw new BusinessException(ErrorCode.CONFIG_NOT_PUBLISHED, "模块未绑定已发布流程");
        }
        WorkflowInstance instance = new WorkflowInstance();
        instance.setSystemId(bo.getSystemId());
        instance.setTenantId(bo.getTenantId());
        instance.setModuleId(bo.getModuleId());
        instance.setRecordId(bo.getRecordId());
        instance.setTemplateId(template.getId());
        instance.setVersionId(template.getCurrentVersionId());
        instance.setStartedBy(SecurityContext.currentUser().getAccountId());
        instance.setBusinessSnapshot(record.getConfigSnapshot());
        instance.setStatus(StatusEnums.RUNNING);
        instanceService.save(instance);

        WorkflowTask task = new WorkflowTask();
        task.setSystemId(bo.getSystemId());
        task.setTenantId(bo.getTenantId());
        task.setInstanceId(instance.getId());
        task.setTaskStatus(StatusEnums.PENDING);
        task.setCandidateJson("[]");
        taskService.save(task);

        record.setProcessStatus(StatusEnums.RUNNING);
        record.setRecordStatus(StatusEnums.SUBMITTED);
        recordService.updateById(record);
        return converter.toSimple(instance);
    }

    @Override
    public PageResult<SimpleVO> tasks(long pageNo, long pageSize, Long assigneeId, String status) {
        Long operator = SecurityContext.currentUser().getAccountId();
        Long systemId = SecurityContext.currentUser().getSystemId();
        Long tenantId = SecurityContext.currentUser().getTenantId();
        if (systemId == null || tenantId == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "请先进入系统上下文");
        }
        permissionService.requireScope(systemId, tenantId);
        if (assigneeId != null && !assigneeId.equals(operator)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能查询当前账号的流程任务");
        }
        LambdaQueryWrapper<WorkflowTask> wrapper = Wrappers.<WorkflowTask>lambdaQuery()
                .eq(WorkflowTask::getSystemId, systemId)
                .eq(WorkflowTask::getTenantId, tenantId)
                .eq(status != null && !status.isBlank(), WorkflowTask::getTaskStatus, status)
                .and(q -> q.eq(WorkflowTask::getAssigneeId, operator)
                        .or().like(WorkflowTask::getCandidateJson, String.valueOf(operator)))
                .orderByDesc(WorkflowTask::getUpdatedAt);
        IPage<WorkflowTask> page = taskService.page(Page.of(pageNo, pageSize), wrapper);
        return new PageResult<>(pageNo, pageSize, page.getTotal(), page.getRecords().stream().map(converter::toSimple).toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SimpleVO handleTask(Long taskId, WorkflowTaskActionBO bo) {
        WorkflowTask task = taskService.getById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "流程任务不存在");
        }
        if (!StatusEnums.PENDING.equals(task.getTaskStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATUS, "任务已处理");
        }
        WorkflowInstance instance = instanceService.getById(task.getInstanceId());
        if (instance == null || !task.getSystemId().equals(instance.getSystemId())
                || !task.getTenantId().equals(instance.getTenantId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "流程实例不存在或归属不一致");
        }
        permissionService.requireAction(task.getSystemId(), task.getTenantId(), "workflow:handle");
        Long operator = SecurityContext.currentUser().getAccountId();
        if (!canHandleTask(task, operator)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前账号不是任务处理人或候选人");
        }
        if ("TRANSFER".equals(bo.getAction()) && bo.getTransferTo() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "转交目标不能为空");
        }
        if (bo.getTransferTo() != null && !isEnabledMember(task.getSystemId(), task.getTenantId(), bo.getTransferTo())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "转交目标不是当前系统启用成员");
        }
        task.setAssigneeId(operator);
        task.setComment(bo.getComment());
        task.setCompletedAt(LocalDateTime.now());
        switch (bo.getAction()) {
            case "APPROVE" -> {
                task.setTaskStatus(StatusEnums.APPROVED);
                instance.setStatus(StatusEnums.APPROVED);
            }
            case "REJECT" -> {
                task.setTaskStatus(StatusEnums.REJECTED);
                instance.setStatus(StatusEnums.REJECTED);
            }
            case "TRANSFER" -> {
                task.setTaskStatus("TRANSFERRED");
                task.setAssigneeId(bo.getTransferTo());
            }
            case "TERMINATE" -> {
                task.setTaskStatus("TERMINATED");
                instance.setStatus("TERMINATED");
            }
            default -> throw new BusinessException(ErrorCode.PARAM_ERROR, "不支持的流程动作");
        }
        taskService.updateById(task);
        if (!StatusEnums.RUNNING.equals(instance.getStatus())) {
            instanceService.updateById(instance);
            BusinessRecord record = recordService.getById(instance.getRecordId());
            if (record != null) {
                record.setProcessStatus(instance.getStatus());
                recordService.updateById(record);
            }
        }
        return converter.toSimple(task);
    }

    private int parseVersionNo(String versionNo, Long templateId) {
        if (versionNo == null || versionNo.isBlank()) {
            return versionService.list(Wrappers.<WorkflowVersion>lambdaQuery()
                            .eq(WorkflowVersion::getTemplateId, templateId))
                    .stream().map(WorkflowVersion::getVersionNo).max(Integer::compareTo).orElse(0) + 1;
        }
        try {
            return Integer.parseInt(versionNo.trim());
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "流程版本号必须是整数，或留空由后端生成");
        }
    }

    private boolean canHandleTask(WorkflowTask task, Long operator) {
        return operator.equals(task.getAssigneeId())
                || (task.getAssigneeId() == null && task.getCandidateJson() != null
                && task.getCandidateJson().contains(String.valueOf(operator)));
    }

    private boolean isEnabledMember(Long systemId, Long tenantId, Long accountId) {
        return systemMemberService.count(Wrappers.<SystemMember>lambdaQuery()
                .eq(SystemMember::getSystemId, systemId)
                .eq(SystemMember::getTenantId, tenantId)
                .eq(SystemMember::getAccountId, accountId)
                .eq(SystemMember::getStatus, StatusEnums.ENABLED)) > 0;
    }
}
