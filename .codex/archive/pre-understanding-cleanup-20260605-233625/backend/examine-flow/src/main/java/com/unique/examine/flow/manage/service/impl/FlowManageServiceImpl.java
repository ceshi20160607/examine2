package com.unique.examine.flow.manage.service.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.core.security.AuthContext;
import com.unique.examine.core.security.AuthContextHolder;
import com.unique.examine.flow.base.entity.ApprovalLog;
import com.unique.examine.flow.base.entity.Instance;
import com.unique.examine.flow.base.entity.Task;
import com.unique.examine.flow.base.entity.Template;
import com.unique.examine.flow.base.entity.TemplateVersion;
import com.unique.examine.flow.base.service.IApprovalLogService;
import com.unique.examine.flow.base.service.IInstanceService;
import com.unique.examine.flow.base.service.ITaskService;
import com.unique.examine.flow.base.service.ITemplateService;
import com.unique.examine.flow.base.service.ITemplateVersionService;
import com.unique.examine.flow.manage.bo.FlowStartBO;
import com.unique.examine.flow.manage.bo.FlowTaskHandleBO;
import com.unique.examine.flow.manage.bo.FlowTemplatePublishBO;
import com.unique.examine.flow.manage.bo.FlowTemplateSaveBO;
import com.unique.examine.flow.manage.converter.FlowManageConverter;
import com.unique.examine.flow.manage.dto.FlowTaskQueryDTO;
import com.unique.examine.flow.manage.enums.FlowManageErrorCode;
import com.unique.examine.flow.manage.service.FlowManageService;
import com.unique.examine.flow.manage.vo.FlowManageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 流程管理服务实现。
 */
@Service
@RequiredArgsConstructor
public class FlowManageServiceImpl implements FlowManageService {

    private static final String DRAFT = "DRAFT";
    private static final String PUBLISHED = "PUBLISHED";
    private static final String RUNNING = "RUNNING";
    private static final String APPROVED = "APPROVED";
    private static final String REJECTED = "REJECTED";
    private static final String TRANSFERRED = "TRANSFERRED";
    private static final String PENDING = "PENDING";
    private static final String COMPLETED = "COMPLETED";
    private static final String CANCELED = "CANCELED";
    private static final String END = "END";

    private final ITemplateService templateService;
    private final ITemplateVersionService templateVersionService;
    private final IInstanceService instanceService;
    private final ITaskService taskService;
    private final IApprovalLogService approvalLogService;

    @Override
    public List<FlowManageVO> listTemplates(Long moduleId) {
        return templateService.list(Wrappers.<Template>lambdaQuery()
                        .eq(ObjectUtil.isNotNull(moduleId), Template::getModuleId, moduleId))
                .stream().map(FlowManageConverter::fromTemplate).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowManageVO createTemplate(FlowTemplateSaveBO bo) {
        requireId(bo.getTenantId(), "tenantId");
        requireId(bo.getAppId(), "appId");
        requireId(bo.getModuleId(), "moduleId");
        requireText(bo.getTemplateCode(), "templateCode");
        requireText(bo.getTemplateName(), "templateName");
        Template template = new Template();
        template.setTenantId(bo.getTenantId());
        template.setAppId(bo.getAppId());
        template.setModuleId(bo.getModuleId());
        template.setTemplateCode(bo.getTemplateCode());
        template.setTemplateName(bo.getTemplateName());
        template.setStatus(DRAFT);
        fillAudit(template::setCreatedBy, template::setUpdatedBy);
        templateService.save(template);
        return FlowManageConverter.fromTemplate(template);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowManageVO publishTemplate(FlowTemplatePublishBO bo) {
        requireId(bo.getTemplateId(), "templateId");
        if (ObjectUtil.isNull(bo.getVersionNo())) {
            throwError(FlowManageErrorCode.PARAM_REQUIRED);
        }
        Template template = requireTemplate(bo.getTemplateId());
        TemplateVersion version = new TemplateVersion();
        version.setTemplateId(template.getId());
        version.setVersionNo(bo.getVersionNo());
        version.setStatus(PUBLISHED);
        version.setGraphJson(bo.getGraphJson());
        version.setPublishedAt(LocalDateTime.now());
        fillAudit(version::setCreatedBy, version::setUpdatedBy);
        templateVersionService.save(version);
        template.setStatus(PUBLISHED);
        template.setPublishedVersionId(version.getId());
        fillUpdatedBy(template::setUpdatedBy);
        templateService.updateById(template);
        return FlowManageConverter.fromTemplateVersion(version);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowManageVO start(FlowStartBO bo) {
        requireId(bo.getTenantId(), "tenantId");
        requireId(bo.getModuleId(), "moduleId");
        requireId(bo.getRecordId(), "recordId");
        Template template = requireTemplate(bo.getTemplateId());
        Long versionId = ObjectUtil.defaultIfNull(bo.getTemplateVersionId(), template.getPublishedVersionId());
        if (!PUBLISHED.equals(template.getStatus()) || ObjectUtil.isNull(versionId)) {
            throwError(FlowManageErrorCode.STATUS_INVALID);
        }
        Instance instance = new Instance();
        instance.setTenantId(bo.getTenantId());
        instance.setModuleId(bo.getModuleId());
        instance.setRecordId(bo.getRecordId());
        instance.setTemplateId(template.getId());
        instance.setTemplateVersionId(versionId);
        instance.setStatus(RUNNING);
        instance.setCurrentNodeKey("START");
        instance.setStartedBy(currentAccountId());
        instance.setStartedAt(LocalDateTime.now());
        fillAudit(instance::setCreatedBy, instance::setUpdatedBy);
        instanceService.save(instance);
        Task task = new Task();
        task.setInstanceId(instance.getId());
        task.setNodeKey("START");
        task.setTaskName(StrUtil.blankToDefault(bo.getTaskName(), "发起审批"));
        task.setAssigneeId(ObjectUtil.defaultIfNull(bo.getAssigneeId(), currentAccountId()));
        task.setStatus(PENDING);
        fillAudit(task::setCreatedBy, task::setUpdatedBy);
        taskService.save(task);
        return FlowManageConverter.fromInstance(instance);
    }

    @Override
    public List<FlowManageVO> listTasks(FlowTaskQueryDTO dto) {
        return taskService.list(Wrappers.<Task>lambdaQuery()
                        .eq(ObjectUtil.isNotNull(dto.getAssigneeId()), Task::getAssigneeId, dto.getAssigneeId())
                        .eq(StrUtil.isNotBlank(dto.getStatus()), Task::getStatus, dto.getStatus()))
                .stream().map(FlowManageConverter::fromTask).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowManageVO handleTask(Long taskId, FlowTaskHandleBO bo) {
        if (ObjectUtil.isNull(bo)) {
            throwError(FlowManageErrorCode.PARAM_REQUIRED);
        }
        Task task = taskService.getById(taskId);
        if (ObjectUtil.isNull(task)) {
            throwError(FlowManageErrorCode.DATA_NOT_FOUND);
        }
        if (!PENDING.equals(task.getStatus())) {
            throwError(FlowManageErrorCode.STATUS_INVALID);
        }
        Long accountId = currentAccountId();
        if (ObjectUtil.isNotNull(task.getAssigneeId()) && ObjectUtil.isNotNull(accountId)
                && !task.getAssigneeId().equals(accountId)) {
            throwError(FlowManageErrorCode.TASK_ASSIGNEE_INVALID);
        }
        FlowActionType action = requireActionType(bo.getActionType());
        task.setStatus(action.taskStatus);
        task.setHandledAt(LocalDateTime.now());
        fillUpdatedBy(task::setUpdatedBy);
        taskService.updateById(task);
        ApprovalLog log = new ApprovalLog();
        log.setInstanceId(task.getInstanceId());
        log.setTaskId(task.getId());
        log.setActionType(action.name());
        log.setOperatorId(accountId);
        log.setCommentText(bo.getCommentText());
        log.setSnapshotJson("{}");
        approvalLogService.save(log);
        if (action != FlowActionType.TRANSFER) {
            completeInstance(task.getInstanceId(), action);
        } else {
            createTransferTask(task, bo.getTransferTo());
        }
        return FlowManageConverter.fromTask(task);
    }

    /**
     * 完成或驳回流程实例。
     *
     * @param instanceId 实例 ID
     * @param action 处理动作
     */
    private void completeInstance(Long instanceId, FlowActionType action) {
        Instance instance = instanceService.getById(instanceId);
        if (ObjectUtil.isNull(instance)) {
            return;
        }
        instance.setStatus(action.instanceStatus);
        instance.setCurrentNodeKey(END);
        instance.setEndedAt(LocalDateTime.now());
        fillUpdatedBy(instance::setUpdatedBy);
        instanceService.updateById(instance);
    }

    /**
     * 创建转交任务。
     *
     * @param sourceTask 原任务
     * @param transferTo 转交账号
     */
    private void createTransferTask(Task sourceTask, Long transferTo) {
        requireId(transferTo, "transferTo");
        Task next = new Task();
        next.setInstanceId(sourceTask.getInstanceId());
        next.setNodeKey(sourceTask.getNodeKey());
        next.setTaskName(sourceTask.getTaskName());
        next.setAssigneeId(transferTo);
        next.setStatus(PENDING);
        fillAudit(next::setCreatedBy, next::setUpdatedBy);
        taskService.save(next);
    }

    /**
     * 校验并转换流程动作。
     *
     * @param action 处理动作
     * @return 流程动作
     */
    private FlowActionType requireActionType(String action) {
        if (StrUtil.isBlank(action)) {
            throwError(FlowManageErrorCode.PARAM_REQUIRED);
        }
        try {
            return FlowActionType.valueOf(action);
        } catch (IllegalArgumentException ex) {
            throwError(FlowManageErrorCode.ACTION_TYPE_INVALID);
            return FlowActionType.APPROVE;
        }
    }

    /**
     * 流程任务动作与任务、实例状态映射。
     */
    private enum FlowActionType {
        APPROVE(APPROVED, COMPLETED),
        REJECT(REJECTED, REJECTED),
        TRANSFER(TRANSFERRED, RUNNING),
        CANCEL(CANCELED, CANCELED);

        private final String taskStatus;
        private final String instanceStatus;

        FlowActionType(String taskStatus, String instanceStatus) {
            this.taskStatus = taskStatus;
            this.instanceStatus = instanceStatus;
        }
    }

    /**
     * 查询并校验模板。
     *
     * @param id 模板 ID
     * @return 模板实体
     */
    private Template requireTemplate(Long id) {
        requireId(id, "templateId");
        Template template = templateService.getById(id);
        if (ObjectUtil.isNull(template)) {
            throwError(FlowManageErrorCode.DATA_NOT_FOUND);
        }
        return template;
    }

    /**
     * 校验文本必填。
     *
     * @param value 字段值
     * @param field 字段名
     */
    private void requireText(String value, String field) {
        if (StrUtil.isBlank(value)) {
            throw new BusinessException(FlowManageErrorCode.PARAM_REQUIRED.getCode(), field + " is required");
        }
    }

    /**
     * 校验 ID 必填。
     *
     * @param value 字段值
     * @param field 字段名
     */
    private void requireId(Long value, String field) {
        if (ObjectUtil.isNull(value)) {
            throw new BusinessException(FlowManageErrorCode.PARAM_REQUIRED.getCode(), field + " is required");
        }
    }

    /**
     * 写入创建与更新人。
     *
     * @param createdSetter 创建人写入器
     * @param updatedSetter 更新人写入器
     */
    private void fillAudit(java.util.function.Consumer<Long> createdSetter, java.util.function.Consumer<Long> updatedSetter) {
        Long accountId = currentAccountId();
        createdSetter.accept(accountId);
        updatedSetter.accept(accountId);
    }

    /**
     * 写入更新人。
     *
     * @param updatedSetter 更新人写入器
     */
    private void fillUpdatedBy(java.util.function.Consumer<Long> updatedSetter) {
        updatedSetter.accept(currentAccountId());
    }

    /**
     * 获取当前账号 ID。
     *
     * @return 当前账号 ID
     */
    private Long currentAccountId() {
        AuthContext context = AuthContextHolder.get();
        return ObjectUtil.isNull(context) ? null : context.getAccountId();
    }

    /**
     * 抛出流程异常。
     *
     * @param errorCode 错误码
     */
    private void throwError(FlowManageErrorCode errorCode) {
        throw new BusinessException(errorCode.getCode(), errorCode.getMessage());
    }
}
