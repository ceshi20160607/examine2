package com.unique.examine.flow.manage.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.unique.examine.core.common.response.PageResult;
import com.unique.examine.core.context.RequestContext;
import com.unique.examine.core.context.RequestContextHolder;
import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.core.flow.FlowRecordRuntimeService;
import com.unique.examine.core.flow.FlowRecordStartRequest;
import com.unique.examine.core.flow.FlowRecordStartResult;
import com.unique.examine.core.permission.PermissionService;
import com.unique.examine.flow.base.entity.ActionLog;
import com.unique.examine.flow.base.entity.Binding;
import com.unique.examine.flow.base.entity.Cc;
import com.unique.examine.flow.base.entity.Instance;
import com.unique.examine.flow.base.entity.Task;
import com.unique.examine.flow.base.entity.TaskActor;
import com.unique.examine.flow.base.entity.Template;
import com.unique.examine.flow.base.entity.TemplateCondition;
import com.unique.examine.flow.base.entity.TemplateLine;
import com.unique.examine.flow.base.entity.TemplateNode;
import com.unique.examine.flow.base.entity.TemplateVersion;
import com.unique.examine.flow.base.service.IActionLogService;
import com.unique.examine.flow.base.service.IBindingService;
import com.unique.examine.flow.base.service.ICcService;
import com.unique.examine.flow.base.service.IInstanceService;
import com.unique.examine.flow.base.service.ITaskActorService;
import com.unique.examine.flow.base.service.ITaskService;
import com.unique.examine.flow.base.service.ITemplateConditionService;
import com.unique.examine.flow.base.service.ITemplateLineService;
import com.unique.examine.flow.base.service.ITemplateNodeService;
import com.unique.examine.flow.base.service.ITemplateService;
import com.unique.examine.flow.base.service.ITemplateVersionService;
import com.unique.examine.flow.manage.bo.FlowActionBO;
import com.unique.examine.flow.manage.bo.FlowBindingSaveBO;
import com.unique.examine.flow.manage.bo.FlowClaimBO;
import com.unique.examine.flow.manage.bo.FlowPublishBO;
import com.unique.examine.flow.manage.bo.FlowTaskQueryBO;
import com.unique.examine.flow.manage.bo.FlowTemplateConditionBO;
import com.unique.examine.flow.manage.bo.FlowTemplateGraphBO;
import com.unique.examine.flow.manage.bo.FlowTemplateLineBO;
import com.unique.examine.flow.manage.bo.FlowTemplateNodeBO;
import com.unique.examine.flow.manage.bo.FlowTemplateSaveBO;
import com.unique.examine.flow.manage.bo.FlowTemplateStatusBO;
import com.unique.examine.flow.manage.bo.FlowWithdrawBO;
import com.unique.examine.flow.manage.enums.FlowErrorCode;
import com.unique.examine.flow.manage.service.FlowManageService;
import com.unique.examine.flow.manage.vo.FlowActionResultVO;
import com.unique.examine.flow.manage.vo.FlowBindingVO;
import com.unique.examine.flow.manage.vo.FlowCcItemVO;
import com.unique.examine.flow.manage.vo.FlowCheckIssueVO;
import com.unique.examine.flow.manage.vo.FlowDiagramVO;
import com.unique.examine.flow.manage.vo.FlowHistoryItemVO;
import com.unique.examine.flow.manage.vo.FlowInstanceVO;
import com.unique.examine.flow.manage.vo.FlowPublishCheckResultVO;
import com.unique.examine.flow.manage.vo.FlowTaskDetailVO;
import com.unique.examine.flow.manage.vo.FlowTaskListItemVO;
import com.unique.examine.flow.manage.vo.FlowTemplateGraphVO;
import com.unique.examine.flow.manage.vo.FlowTemplateVO;
import com.unique.examine.module.base.entity.Model;
import com.unique.examine.module.base.entity.Record;
import com.unique.examine.module.base.service.IModelService;
import com.unique.examine.module.base.service.IRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * 流程管理服务实现。
 */
@Service
@RequiredArgsConstructor
public class FlowManageServiceImpl implements FlowManageService, FlowRecordRuntimeService {

    private static final byte NO = 0;

    private static final byte YES = 1;

    private static final String DRAFT = "DRAFT";

    private static final String PUBLISHED = "PUBLISHED";

    private static final String DISABLED = "DISABLED";

    private static final String ENABLED = "ENABLED";

    private static final String IN_APPROVAL = "IN_APPROVAL";

    private static final String APPROVED = "APPROVED";

    private static final String REJECTED = "REJECTED";

    private static final String WITHDRAWN = "WITHDRAWN";

    private static final String TERMINATED = "TERMINATED";

    private static final String PENDING = "PENDING";

    private static final String DONE = "DONE";

    private static final String CANCELED = "CANCELED";

    private static final String TRANSFERRED = "TRANSFERRED";

    private static final String RETURNED = "RETURNED";

    private static final String ACTIVE = "ACTIVE";

    private static final String INACTIVE = "INACTIVE";

    private static final String RECORD_SUBMIT = "RECORD_SUBMIT";

    private final ITemplateService templateService;

    private final ITemplateVersionService templateVersionService;

    private final ITemplateNodeService templateNodeService;

    private final ITemplateLineService templateLineService;

    private final ITemplateConditionService templateConditionService;

    private final IBindingService bindingService;

    private final ICcService ccService;

    private final IInstanceService instanceService;

    private final ITaskService taskService;

    private final ITaskActorService taskActorService;

    private final IActionLogService actionLogService;

    private final IModelService modelService;

    private final IRecordService recordService;

    private final PermissionService permissionService;

    private final ObjectMapper objectMapper;

    /**
     * 查询流程模板。
     */
    @Override
    public List<FlowTemplateVO> listTemplates(Long systemId, String keyword, String status) {
        permissionService.requireOperation("FLOW_TEMPLATE_VIEW");
        return templateService.lambdaQuery()
                .eq(Template::getSystemId, systemId)
                .eq(Template::getDeleted, NO)
                .list()
                .stream()
                .filter(template -> !StringUtils.hasText(keyword) || contains(template.getCode(), keyword)
                        || contains(template.getName(), keyword))
                .filter(template -> !StringUtils.hasText(status) || Objects.equals(template.getStatus(), status))
                .sorted(Comparator.comparing(Template::getUpdatedAt, Comparator.nullsLast(LocalDateTime::compareTo))
                        .reversed())
                .map(this::toTemplateVO)
                .toList();
    }

    /**
     * 创建流程模板。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowTemplateVO createTemplate(Long systemId, FlowTemplateSaveBO saveBO) {
        permissionService.requireOperation("FLOW_TEMPLATE_CREATE");
        Template duplicated = templateService.lambdaQuery()
                .eq(Template::getSystemId, systemId)
                .eq(Template::getCode, saveBO.getCode())
                .eq(Template::getDeleted, NO)
                .one();
        if (Objects.nonNull(duplicated)) {
            throw new BusinessException(FlowErrorCode.TEMPLATE_CHECK_FAILED, "流程模板编码已存在");
        }
        LocalDateTime now = LocalDateTime.now();
        Template template = new Template()
                .setSystemId(systemId)
                .setTenantId(currentTenantId())
                .setCode(saveBO.getCode())
                .setName(saveBO.getName())
                .setDescription(saveBO.getDescription())
                .setStatus(DRAFT)
                .setVersionNo(1)
                .setDeleted(NO)
                .setCreatedAt(now)
                .setUpdatedAt(now);
        templateService.save(template);
        return toTemplateVO(template);
    }

    /**
     * 查询流程模板详情。
     */
    @Override
    public FlowTemplateVO templateDetail(Long systemId, Long templateId) {
        permissionService.requireOperation("FLOW_TEMPLATE_VIEW");
        return toTemplateVO(activeTemplate(systemId, templateId));
    }

    /**
     * 保存流程图草稿。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowTemplateGraphVO saveGraph(Long systemId, Long templateId, FlowTemplateGraphBO graphBO) {
        permissionService.requireOperation("FLOW_TEMPLATE_EDIT");
        Template template = activeTemplate(systemId, templateId);
        TemplateVersion draft = draftVersion(systemId, templateId)
                .orElseGet(() -> new TemplateVersion()
                        .setSystemId(systemId)
                        .setTenantId(template.getTenantId())
                        .setTemplateId(templateId)
                        .setVersionNo(nextVersionNo(systemId, templateId))
                        .setStatus(DRAFT)
                        .setCreatedAt(LocalDateTime.now()));
        LocalDateTime now = LocalDateTime.now();
        draft.setGraphSnapshotJson(writeJson(graphBO))
                // 当前表结构要求发布人/发布时间非空，草稿版本先记录最后编辑人，正式发布时再写正式发布版本。
                .setPublishedBy(currentMemberId())
                .setPublishedAt(now)
                .setUpdatedAt(now);
        if (Objects.isNull(draft.getId())) {
            templateVersionService.save(draft);
        } else {
            templateVersionService.updateById(draft);
        }
        template.setStatus(DRAFT)
                .setVersionNo(nextTemplateVersion(template.getVersionNo()))
                .setUpdatedAt(LocalDateTime.now());
        templateService.updateById(template);
        return FlowTemplateGraphVO.builder()
                .templateId(toId(templateId))
                .templateVersionId(toId(draft.getId()))
                .graph(readJson(draft.getGraphSnapshotJson()))
                .draft(true)
                .build();
    }

    /**
     * 查询流程图。
     */
    @Override
    public FlowTemplateGraphVO templateGraph(Long systemId, Long templateId) {
        permissionService.requireOperation("FLOW_TEMPLATE_VIEW");
        Template template = activeTemplate(systemId, templateId);
        TemplateVersion version = draftVersion(systemId, templateId)
                .orElseGet(() -> Objects.isNull(template.getCurrentVersionId()) ? null
                        : templateVersionService.getById(template.getCurrentVersionId()));
        return FlowTemplateGraphVO.builder()
                .templateId(toId(templateId))
                .templateVersionId(Objects.isNull(version) ? null : toId(version.getId()))
                .graph(Objects.isNull(version) ? emptyGraph() : readJson(version.getGraphSnapshotJson()))
                .draft(Objects.nonNull(version) && DRAFT.equals(version.getStatus()))
                .build();
    }

    /**
     * 发布检查。
     */
    @Override
    public FlowPublishCheckResultVO publishCheck(Long systemId, Long templateId) {
        permissionService.requireOperation("FLOW_TEMPLATE_PUBLISH");
        activeTemplate(systemId, templateId);
        TemplateVersion version = draftVersion(systemId, templateId)
                .orElseThrow(() -> new BusinessException(FlowErrorCode.TEMPLATE_CHECK_FAILED, "流程图草稿不存在"));
        List<FlowCheckIssueVO> issues = checkGraph(readJson(version.getGraphSnapshotJson()));
        return FlowPublishCheckResultVO.builder()
                .passed(issues.isEmpty())
                .templateId(toId(templateId))
                .nextVersionNo(nextVersionNo(systemId, templateId))
                .issues(issues)
                .requestId(currentRequestId())
                .checkedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 发布流程模板。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowTemplateVO publish(Long systemId, Long templateId, FlowPublishBO publishBO) {
        permissionService.requireOperation("FLOW_TEMPLATE_PUBLISH");
        Template template = activeTemplate(systemId, templateId);
        TemplateVersion draft = draftVersion(systemId, templateId)
                .orElseThrow(() -> new BusinessException(FlowErrorCode.TEMPLATE_CHECK_FAILED, "流程图草稿不存在"));
        FlowPublishCheckResultVO check = publishCheck(systemId, templateId);
        if (!Boolean.TRUE.equals(check.getPassed())) {
            throw new BusinessException(FlowErrorCode.TEMPLATE_CHECK_FAILED, "流程发布检查失败");
        }
        TemplateVersion published = new TemplateVersion()
                .setSystemId(systemId)
                .setTenantId(template.getTenantId())
                .setTemplateId(templateId)
                .setVersionNo(nextVersionNo(systemId, templateId))
                .setStatus(PUBLISHED)
                .setPublishComment(publishBO == null ? null : publishBO.getPublishComment())
                .setGraphSnapshotJson(draft.getGraphSnapshotJson())
                .setCheckResultJson(writeJson(check))
                .setPublishedBy(currentMemberId())
                .setPublishedAt(LocalDateTime.now())
                .setCreatedAt(LocalDateTime.now())
                .setUpdatedAt(LocalDateTime.now());
        templateVersionService.save(published);
        persistPublishedGraph(systemId, published.getId(), readJson(published.getGraphSnapshotJson()));
        draft.setStatus("DISCARDED").setUpdatedAt(LocalDateTime.now());
        templateVersionService.updateById(draft);
        template.setStatus(PUBLISHED)
                .setCurrentVersionId(published.getId())
                .setVersionNo(nextTemplateVersion(template.getVersionNo()))
                .setUpdatedAt(LocalDateTime.now());
        templateService.updateById(template);
        return toTemplateVO(template);
    }

    /**
     * 绑定模块与流程。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowBindingVO bindModule(Long systemId, Long moduleId, FlowBindingSaveBO saveBO) {
        permissionService.requireOperation("FLOW_BINDING_EDIT");
        Model model = modelService.lambdaQuery()
                .eq(Model::getSystemId, systemId)
                .eq(Model::getModuleId, moduleId)
                .eq(Model::getDeleteMarker, "0")
                .one();
        if (Objects.isNull(model)) {
            throw new BusinessException(FlowErrorCode.BINDING_MISSING, "模块不存在");
        }
        TemplateVersion version = publishedVersion(systemId, saveBO.getTemplateVersionId());
        Binding binding = bindingService.lambdaQuery()
                .eq(Binding::getSystemId, systemId)
                .eq(Binding::getModuleId, moduleId)
                .eq(Binding::getActionCode, defaultText(saveBO.getActionCode(), RECORD_SUBMIT))
                .eq(Binding::getDeleted, NO)
                .one();
        LocalDateTime now = LocalDateTime.now();
        if (Objects.isNull(binding)) {
            binding = new Binding()
                    .setSystemId(systemId)
                    .setTenantId(model.getTenantId())
                    .setModuleId(moduleId)
                    .setActionCode(defaultText(saveBO.getActionCode(), RECORD_SUBMIT))
                    .setCreatedAt(now)
                    .setDeleted(NO);
        }
        binding.setTemplateId(version.getTemplateId())
                .setTemplateVersionId(version.getId())
                .setStatus(defaultText(saveBO.getStatus(), ENABLED))
                .setVersionNo(nextTemplateVersion(binding.getVersionNo()))
                .setUpdatedAt(now);
        if (Objects.isNull(binding.getId())) {
            bindingService.save(binding);
        } else {
            bindingService.updateById(binding);
        }
        model.setFlowBindingId(binding.getId()).setUpdatedAt(now);
        modelService.updateById(model);
        return toBindingVO(binding);
    }

    /**
     * 查询待办任务。
     */
    @Override
    public PageResult<FlowTaskListItemVO> todoTasks(Long systemId, FlowTaskQueryBO queryBO) {
        permissionService.requireOperation("FLOW_TASK_HANDLE");
        Long memberId = currentMemberId();
        List<Long> taskIds = taskActorService.lambdaQuery()
                .eq(TaskActor::getSystemId, systemId)
                .eq(TaskActor::getActorMemberId, memberId)
                .eq(TaskActor::getStatus, ACTIVE)
                .list()
                .stream()
                .map(TaskActor::getTaskId)
                .distinct()
                .toList();
        if (taskIds.isEmpty()) {
            return page(List.of(), queryBO);
        }
        List<Task> tasks = taskService.lambdaQuery()
                .eq(Task::getSystemId, systemId)
                .in(Task::getId, taskIds)
                .list()
                .stream()
                .filter(task -> PENDING.equals(task.getStatus()))
                .filter(task -> !StringUtils.hasText(queryBO.getKeyword()) || contains(task.getTaskName(), queryBO.getKeyword()))
                .sorted(Comparator.comparing(Task::getUpdatedAt, Comparator.nullsLast(LocalDateTime::compareTo)).reversed())
                .toList();
        return page(tasks.stream().map(this::toTaskListItemVO).toList(), queryBO);
    }

    /**
     * 查询当前成员的流程抄送。
     */
    @Override
    public PageResult<FlowCcItemVO> ccTasks(Long systemId, FlowTaskQueryBO queryBO) {
        permissionService.requireOperation("FLOW_INSTANCE_VIEW");
        Long memberId = currentMemberId();
        Map<Long, Instance> instanceMap = instanceService.lambdaQuery()
                .eq(Instance::getSystemId, systemId)
                .list()
                .stream()
                .collect(Collectors.toMap(Instance::getId, Function.identity(), (left, right) -> left));
        List<FlowCcItemVO> ccItems = ccService.lambdaQuery()
                .eq(Cc::getSystemId, systemId)
                .eq(Cc::getCcMemberId, memberId)
                .list()
                .stream()
                .filter(cc -> instanceMap.containsKey(cc.getInstanceId()))
                .sorted(Comparator.comparing(Cc::getUpdatedAt, Comparator.nullsLast(LocalDateTime::compareTo))
                        .reversed())
                .map(cc -> toCcItemVO(cc, instanceMap.get(cc.getInstanceId())))
                .toList();
        return page(ccItems, queryBO);
    }

    /**
     * 查询当前成员发起的流程实例。
     */
    @Override
    public PageResult<FlowInstanceVO> startedInstances(Long systemId, FlowTaskQueryBO queryBO) {
        permissionService.requireOperation("FLOW_INSTANCE_VIEW");
        Long memberId = currentMemberId();
        List<FlowInstanceVO> instances = instanceService.lambdaQuery()
                .eq(Instance::getSystemId, systemId)
                .eq(Instance::getStarterMemberId, memberId)
                .list()
                .stream()
                .filter(instance -> !StringUtils.hasText(queryBO.getStatus())
                        || Objects.equals(instance.getStatus(), queryBO.getStatus()))
                .sorted(Comparator.comparing(Instance::getUpdatedAt, Comparator.nullsLast(LocalDateTime::compareTo))
                        .reversed())
                .map(this::toInstanceVO)
                .toList();
        return page(instances, queryBO);
    }

    /**
     * 查询任务详情。
     */
    @Override
    public FlowTaskDetailVO taskDetail(Long systemId, Long taskId) {
        permissionService.requireOperation("FLOW_TASK_HANDLE");
        Task task = activeTask(systemId, taskId);
        ensureTaskActor(task);
        Instance instance = activeInstance(systemId, task.getInstanceId());
        return FlowTaskDetailVO.builder()
                .taskId(toId(task.getId()))
                .taskVersion(task.getTaskVersion())
                .instanceId(toId(instance.getId()))
                .recordId(toId(instance.getRecordId()))
                .moduleId(toId(instance.getModuleId()))
                .nodeId(task.getNodeKey())
                .nodeName(task.getTaskName())
                .recordSummary(recordSummary(instance))
                .formSchema(NullNode.getInstance())
                .values(NullNode.getInstance())
                .history(instanceHistory(systemId, instance.getId()))
                .diagram(instanceDiagram(systemId, instance.getId()).getGraph())
                .availableActions(List.of("APPROVE", "REJECT", "TRANSFER", "RETURN", "TERMINATE"))
                .build();
    }

    /**
     * 处理任务。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowActionResultVO handleTask(Long systemId, Long taskId, FlowActionBO actionBO) {
        permissionService.requireOperation("FLOW_TASK_HANDLE");
        Task task = activeTask(systemId, taskId);
        ensureTaskActor(task);
        ensureTaskVersion(task, actionBO.getTaskVersion());
        String action = actionBO.getAction();
        if (Set.of("REJECT", "RETURN", "TERMINATE").contains(action) && !StringUtils.hasText(actionBO.getComment())) {
            throw new BusinessException(FlowErrorCode.ACTION_REASON_REQUIRED);
        }
        if ("TRANSFER".equals(action) && Objects.isNull(actionBO.getTargetMemberId())) {
            throw new BusinessException(FlowErrorCode.ACTION_REASON_REQUIRED, "转交目标成员不能为空");
        }
        Instance instance = activeInstance(systemId, task.getInstanceId());
        FlowActionResultVO result = switch (action) {
            case "APPROVE" -> approveTask(instance, task, actionBO);
            case "REJECT" -> finishTask(instance, task, actionBO, REJECTED, REJECTED);
            case "TERMINATE" -> finishTask(instance, task, actionBO, TERMINATED, REJECTED);
            case "RETURN" -> returnTask(instance, task, actionBO);
            case "TRANSFER" -> transferTask(instance, task, actionBO);
            default -> throw new BusinessException(FlowErrorCode.INSTANCE_STATUS_CONFLICT, "不支持的流程动作");
        };
        saveActionLog(instance, task, action, actionBO.getComment(), task.getNodeKey(), result.getCurrentNode(),
                result.getInstanceStatus());
        return result;
    }

    /**
     * 撤回实例。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowActionResultVO withdraw(Long systemId, Long instanceId, FlowWithdrawBO withdrawBO) {
        permissionService.requireOperation("FLOW_TASK_HANDLE");
        Instance instance = activeInstance(systemId, instanceId);
        if (!IN_APPROVAL.equals(instance.getStatus()) || !Objects.equals(instance.getStarterMemberId(), currentMemberId())) {
            throw new BusinessException(FlowErrorCode.INSTANCE_STATUS_CONFLICT);
        }
        cancelPendingTasks(instance.getId());
        updateInstanceAndRecord(instance, WITHDRAWN, WITHDRAWN, NO, List.of());
        saveActionLog(instance, null, "WITHDRAW", withdrawBO.getReason(), instance.getCurrentNodeKeys(), null, WITHDRAWN);
        return FlowActionResultVO.builder()
                .instanceId(toId(instance.getId()))
                .instanceStatus(WITHDRAWN)
                .taskStatus(CANCELED)
                .businessRecordStatus(WITHDRAWN)
                .nextNodes(List.of())
                .build();
    }

    @Override
    public FlowInstanceVO instanceDetail(Long systemId, Long instanceId) {
        permissionService.requireOperation("FLOW_INSTANCE_VIEW");
        return toInstanceVO(activeInstance(systemId, instanceId));
    }

    @Override
    public FlowDiagramVO instanceDiagram(Long systemId, Long instanceId) {
        permissionService.requireOperation("FLOW_INSTANCE_VIEW");
        Instance instance = activeInstance(systemId, instanceId);
        TemplateVersion version = templateVersionService.getById(instance.getTemplateVersionId());
        return FlowDiagramVO.builder()
                .instanceId(toId(instanceId))
                .templateVersionId(toId(instance.getTemplateVersionId()))
                .currentNodeKeys(instance.getCurrentNodeKeys())
                .graph(Objects.isNull(version) ? emptyGraph() : readJson(version.getGraphSnapshotJson()))
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowActionResultVO claim(Long systemId, Long taskId, FlowClaimBO claimBO) {
        permissionService.requireOperation("FLOW_TASK_HANDLE");
        Task task = activeTask(systemId, taskId);
        ensureTaskActor(task);
        ensureTaskVersion(task, claimBO.getTaskVersion());
        task.setClaimMemberId(currentMemberId())
                .setClaimedAt(LocalDateTime.now())
                .setTaskVersion(nextTemplateVersion(task.getTaskVersion()))
                .setUpdatedAt(LocalDateTime.now());
        taskService.updateById(task);
        Instance instance = activeInstance(systemId, task.getInstanceId());
        saveActionLog(instance, task, "CLAIM", null, task.getNodeKey(), task.getNodeKey(), task.getStatus());
        return actionResult(instance, task, task.getStatus(), List.of(task.getNodeKey()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowActionResultVO unclaim(Long systemId, Long taskId, FlowClaimBO claimBO) {
        permissionService.requireOperation("FLOW_TASK_HANDLE");
        Task task = activeTask(systemId, taskId);
        ensureTaskActor(task);
        ensureTaskVersion(task, claimBO.getTaskVersion());
        task.setClaimMemberId(null)
                .setClaimedAt(null)
                .setTaskVersion(nextTemplateVersion(task.getTaskVersion()))
                .setUpdatedAt(LocalDateTime.now());
        taskService.updateById(task);
        Instance instance = activeInstance(systemId, task.getInstanceId());
        saveActionLog(instance, task, "UNCLAIM", null, task.getNodeKey(), task.getNodeKey(), task.getStatus());
        return actionResult(instance, task, task.getStatus(), List.of(task.getNodeKey()));
    }

    @Override
    public PageResult<FlowInstanceVO> listInstances(Long systemId, FlowTaskQueryBO queryBO) {
        permissionService.requireOperation("FLOW_INSTANCE_VIEW");
        List<FlowInstanceVO> instances = instanceService.lambdaQuery()
                .eq(Instance::getSystemId, systemId)
                .list()
                .stream()
                .filter(instance -> !StringUtils.hasText(queryBO.getStatus()) || Objects.equals(instance.getStatus(), queryBO.getStatus()))
                .sorted(Comparator.comparing(Instance::getUpdatedAt, Comparator.nullsLast(LocalDateTime::compareTo)).reversed())
                .map(this::toInstanceVO)
                .toList();
        return page(instances, queryBO);
    }

    @Override
    public List<FlowHistoryItemVO> instanceHistory(Long systemId, Long instanceId) {
        permissionService.requireOperation("FLOW_INSTANCE_VIEW");
        activeInstance(systemId, instanceId);
        return actionLogService.lambdaQuery()
                .eq(ActionLog::getSystemId, systemId)
                .eq(ActionLog::getInstanceId, instanceId)
                .orderByAsc(ActionLog::getCreatedAt)
                .list()
                .stream()
                .map(this::toHistoryVO)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowTemplateVO changeTemplateStatus(Long systemId, Long templateId, FlowTemplateStatusBO statusBO) {
        permissionService.requireOperation("FLOW_TEMPLATE_EDIT");
        Template template = activeTemplate(systemId, templateId);
        if (statusBO.getVersionNo() != null && !Objects.equals(statusBO.getVersionNo(), template.getVersionNo())) {
            throw new BusinessException(FlowErrorCode.INSTANCE_STATUS_CONFLICT);
        }
        template.setStatus(statusBO.getTargetStatus())
                .setVersionNo(nextTemplateVersion(template.getVersionNo()))
                .setUpdatedAt(LocalDateTime.now());
        templateService.updateById(template);
        return toTemplateVO(template);
    }

    /**
     * 运行态记录提交时创建流程实例。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowRecordStartResult startForRecord(FlowRecordStartRequest request) {
        Binding binding = bindingService.lambdaQuery()
                .eq(Binding::getSystemId, request.getSystemId())
                .eq(Binding::getModuleId, request.getModuleId())
                .eq(Binding::getActionCode, defaultText(request.getActionCode(), RECORD_SUBMIT))
                .eq(Binding::getStatus, ENABLED)
                .eq(Binding::getDeleted, NO)
                .one();
        if (Objects.isNull(binding)) {
            throw new BusinessException(FlowErrorCode.BINDING_MISSING);
        }
        TemplateVersion version = publishedVersion(request.getSystemId(), binding.getTemplateVersionId());
        Graph graph = graph(version);
        String firstNode = firstApprovalNode(graph);
        LocalDateTime now = LocalDateTime.now();
        Instance instance = new Instance()
                .setSystemId(request.getSystemId())
                .setTenantId(request.getTenantId())
                .setModuleId(request.getModuleId())
                .setRecordId(request.getRecordId())
                .setTemplateId(binding.getTemplateId())
                .setTemplateVersionId(binding.getTemplateVersionId())
                .setStatus(Objects.isNull(firstNode) ? APPROVED : IN_APPROVAL)
                .setStarterMemberId(request.getStarterMemberId())
                .setCurrentNodeKeys(firstNode)
                .setStartedAt(now)
                .setFinishedAt(Objects.isNull(firstNode) ? now : null)
                .setRequestId(request.getRequestId())
                .setVersionNo(1)
                .setCreatedAt(now)
                .setUpdatedAt(now);
        instanceService.save(instance);
        if (StringUtils.hasText(firstNode)) {
            createTask(instance, graph.node(firstNode), request.getStarterMemberId());
        }
        return FlowRecordStartResult.builder()
                .instanceId(instance.getId())
                .instanceStatus(instance.getStatus())
                .currentNodeKeys(instance.getCurrentNodeKeys())
                .build();
    }

    private FlowActionResultVO approveTask(Instance instance, Task task, FlowActionBO actionBO) {
        Graph graph = graph(templateVersionService.getById(instance.getTemplateVersionId()));
        task.setStatus(DONE)
                .setHandlerMemberId(currentMemberId())
                .setHandledAt(LocalDateTime.now())
                .setTaskVersion(nextTemplateVersion(task.getTaskVersion()))
                .setIdempotencyKey(actionBO.getIdempotencyKey())
                .setRequestId(currentRequestId())
                .setUpdatedAt(LocalDateTime.now());
        taskService.updateById(task);
        deactivateActors(task.getId());
        String nextNodeKey = nextApprovalOrEnd(graph, task.getNodeKey());
        if (!StringUtils.hasText(nextNodeKey) || "END".equals(graph.node(nextNodeKey).nodeType())) {
            updateInstanceAndRecord(instance, APPROVED, APPROVED, NO, List.of());
            return actionResult(instance, task, DONE, List.of());
        }
        Task nextTask = createTask(instance, graph.node(nextNodeKey), instance.getStarterMemberId());
        updateInstanceAndRecord(instance, IN_APPROVAL, IN_APPROVAL, YES, List.of(nextNodeKey));
        return actionResult(instance, nextTask, PENDING, List.of(nextNodeKey));
    }

    private FlowActionResultVO returnTask(Instance instance, Task task, FlowActionBO actionBO) {
        Graph graph = graph(templateVersionService.getById(instance.getTemplateVersionId()));
        task.setStatus(RETURNED)
                .setHandlerMemberId(currentMemberId())
                .setHandledAt(LocalDateTime.now())
                .setTaskVersion(nextTemplateVersion(task.getTaskVersion()))
                .setUpdatedAt(LocalDateTime.now());
        taskService.updateById(task);
        deactivateActors(task.getId());
        String firstNode = firstApprovalNode(graph);
        Task nextTask = createTask(instance, graph.node(firstNode), instance.getStarterMemberId());
        updateInstanceAndRecord(instance, IN_APPROVAL, IN_APPROVAL, YES, List.of(firstNode));
        return actionResult(instance, nextTask, PENDING, List.of(firstNode));
    }

    private FlowActionResultVO transferTask(Instance instance, Task task, FlowActionBO actionBO) {
        deactivateActors(task.getId());
        taskActorService.save(new TaskActor()
                .setSystemId(task.getSystemId())
                .setTaskId(task.getId())
                .setActorMemberId(actionBO.getTargetMemberId())
                .setActorType("TRANSFER_TARGET")
                .setSourceType("MEMBER")
                .setStatus(ACTIVE)
                .setCreatedAt(LocalDateTime.now())
                .setUpdatedAt(LocalDateTime.now()));
        task.setClaimMemberId(actionBO.getTargetMemberId())
                .setStatus(PENDING)
                .setTaskVersion(nextTemplateVersion(task.getTaskVersion()))
                .setUpdatedAt(LocalDateTime.now());
        taskService.updateById(task);
        return actionResult(instance, task, TRANSFERRED, List.of(task.getNodeKey()));
    }

    private FlowActionResultVO finishTask(Instance instance, Task task, FlowActionBO actionBO, String instanceStatus,
            String recordStatus) {
        task.setStatus(DONE)
                .setHandlerMemberId(currentMemberId())
                .setHandledAt(LocalDateTime.now())
                .setTaskVersion(nextTemplateVersion(task.getTaskVersion()))
                .setUpdatedAt(LocalDateTime.now());
        taskService.updateById(task);
        deactivateActors(task.getId());
        cancelPendingTasks(instance.getId());
        updateInstanceAndRecord(instance, instanceStatus, recordStatus, NO, List.of());
        return actionResult(instance, task, DONE, List.of());
    }

    private Task createTask(Instance instance, Node node, Long starterMemberId) {
        Task task = new Task()
                .setSystemId(instance.getSystemId())
                .setTenantId(instance.getTenantId())
                .setInstanceId(instance.getId())
                .setNodeKey(node.nodeKey())
                .setTaskName(node.nodeName())
                .setStatus(PENDING)
                .setTaskVersion(1)
                .setCreatedAt(LocalDateTime.now())
                .setUpdatedAt(LocalDateTime.now());
        taskService.save(task);
        List<Long> actors = actorMemberIds(node, starterMemberId);
        actors.forEach(actor -> taskActorService.save(new TaskActor()
                .setSystemId(instance.getSystemId())
                .setTaskId(task.getId())
                .setActorMemberId(actor)
                .setActorType("CANDIDATE")
                .setSourceType(defaultText(node.actorStrategy(), "MEMBER"))
                .setStatus(ACTIVE)
                .setCreatedAt(LocalDateTime.now())
                .setUpdatedAt(LocalDateTime.now())));
        return task;
    }

    private void updateInstanceAndRecord(Instance instance, String instanceStatus, String recordStatus, byte lockedFlag,
            List<String> currentNodes) {
        instance.setStatus(instanceStatus)
                .setCurrentNodeKeys(currentNodes.isEmpty() ? null : String.join(",", currentNodes))
                .setFinishedAt(Set.of(APPROVED, REJECTED, WITHDRAWN, TERMINATED).contains(instanceStatus)
                        ? LocalDateTime.now() : null)
                .setVersionNo(nextTemplateVersion(instance.getVersionNo()))
                .setUpdatedAt(LocalDateTime.now());
        instanceService.updateById(instance);
        Record record = recordService.getById(instance.getRecordId());
        if (Objects.nonNull(record)) {
            record.setRecordStatus(recordStatus)
                    .setFlowStatus(instanceStatus)
                    .setLockedFlag(lockedFlag)
                    .setRecordVersion(nextTemplateVersion(record.getRecordVersion()))
                    .setUpdatedAt(LocalDateTime.now());
            recordService.updateById(record);
        }
    }

    private void persistPublishedGraph(Long systemId, Long versionId, JsonNode graph) {
        List<TemplateNode> nodes = nodes(graph).stream()
                .map(node -> new TemplateNode()
                        .setSystemId(systemId)
                        .setTemplateVersionId(versionId)
                        .setNodeKey(node.nodeKey())
                        .setNodeName(node.nodeName())
                        .setNodeType(node.nodeType())
                        .setActorStrategy(node.actorStrategy())
                        .setActorConfigJson(writeJson(node.actorConfig()))
                        .setApprovalRequired(Boolean.TRUE.equals(node.approvalRequired()) ? YES : NO)
                        .setSortOrder(node.sortOrder())
                        .setCreatedAt(LocalDateTime.now())
                        .setUpdatedAt(LocalDateTime.now()))
                .toList();
        if (!nodes.isEmpty()) {
            templateNodeService.saveBatch(nodes);
        }
        for (FlowTemplateLineBO lineBO : lines(graph)) {
            TemplateLine line = new TemplateLine()
                    .setSystemId(systemId)
                    .setTemplateVersionId(versionId)
                    .setLineKey(lineBO.getLineKey())
                    .setFromNodeKey(lineBO.getFromNodeKey())
                    .setToNodeKey(lineBO.getToNodeKey())
                    .setConditionMode(defaultText(lineBO.getConditionMode(), "ALWAYS"))
                    .setSortOrder(lineBO.getSortOrder())
                    .setCreatedAt(LocalDateTime.now())
                    .setUpdatedAt(LocalDateTime.now());
            templateLineService.save(line);
            if (!CollectionUtils.isEmpty(lineBO.getConditions())) {
                List<TemplateCondition> conditions = lineBO.getConditions().stream()
                        .map(condition -> condition(systemId, line.getId(), condition))
                        .toList();
                templateConditionService.saveBatch(conditions);
            }
        }
    }

    private TemplateCondition condition(Long systemId, Long lineId, FlowTemplateConditionBO conditionBO) {
        return new TemplateCondition()
                .setSystemId(systemId)
                .setLineId(lineId)
                .setFieldCode(conditionBO.getFieldCode())
                .setOperator(conditionBO.getOperator())
                .setCompareValueJson(writeJson(conditionBO.getCompareValue()))
                .setExpressionJson(writeJson(conditionBO.getExpression()))
                .setSortOrder(conditionBO.getSortOrder())
                .setCreatedAt(LocalDateTime.now())
                .setUpdatedAt(LocalDateTime.now());
    }

    private List<FlowCheckIssueVO> checkGraph(JsonNode graph) {
        List<Node> nodes = nodes(graph);
        List<FlowTemplateLineBO> lines = lines(graph);
        List<FlowCheckIssueVO> issues = new ArrayList<>();
        if (nodes.stream().noneMatch(node -> "START".equals(node.nodeType()))) {
            issues.add(issue("FLOW_START_MISSING", "NODE", null, "流程缺少开始节点"));
        }
        if (nodes.stream().noneMatch(node -> "END".equals(node.nodeType()))) {
            issues.add(issue("FLOW_END_MISSING", "NODE", null, "流程缺少结束节点"));
        }
        if (nodes.stream().noneMatch(node -> "APPROVAL".equals(node.nodeType()))) {
            issues.add(issue("FLOW_APPROVAL_MISSING", "NODE", null, "流程至少需要一个审批节点"));
        }
        if (!hasStartToEndPath(nodes, lines)) {
            issues.add(issue("FLOW_PATH_INVALID", "LINE", null, "流程缺少从开始到结束的可达路径"));
        }
        return issues;
    }

    private boolean hasStartToEndPath(List<Node> nodes, List<FlowTemplateLineBO> lines) {
        String start = nodes.stream().filter(node -> "START".equals(node.nodeType())).map(Node::nodeKey).findFirst().orElse(null);
        if (!StringUtils.hasText(start)) {
            return false;
        }
        Set<String> endNodes = nodes.stream().filter(node -> "END".equals(node.nodeType())).map(Node::nodeKey)
                .collect(Collectors.toSet());
        Map<String, List<String>> adjacency = lines.stream()
                .collect(Collectors.groupingBy(FlowTemplateLineBO::getFromNodeKey,
                        Collectors.mapping(FlowTemplateLineBO::getToNodeKey, Collectors.toList())));
        ArrayDeque<String> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            if (endNodes.contains(current)) {
                return true;
            }
            adjacency.getOrDefault(current, List.of()).forEach(queue::addLast);
        }
        return false;
    }

    private Graph graph(TemplateVersion version) {
        return new Graph(nodes(readJson(version.getGraphSnapshotJson())), lines(readJson(version.getGraphSnapshotJson())));
    }

    private String firstApprovalNode(Graph graph) {
        Node start = graph.nodes().stream().filter(node -> "START".equals(node.nodeType())).findFirst().orElse(null);
        if (Objects.isNull(start)) {
            return null;
        }
        return nextApprovalOrEnd(graph, start.nodeKey());
    }

    private String nextApprovalOrEnd(Graph graph, String nodeKey) {
        String current = nodeKey;
        Set<String> visited = new HashSet<>();
        while (StringUtils.hasText(current) && visited.add(current)) {
            String lookupNodeKey = current;
            String next = graph.lines().stream()
                    .filter(line -> Objects.equals(line.getFromNodeKey(), lookupNodeKey))
                    .sorted(Comparator.comparing(FlowTemplateLineBO::getSortOrder, Comparator.nullsLast(Integer::compareTo)))
                    .map(FlowTemplateLineBO::getToNodeKey)
                    .findFirst()
                    .orElse(null);
            if (!StringUtils.hasText(next)) {
                return null;
            }
            Node node = graph.node(next);
            if (Objects.isNull(node) || "APPROVAL".equals(node.nodeType()) || "END".equals(node.nodeType())) {
                return next;
            }
            current = next;
        }
        return null;
    }

    private List<Long> actorMemberIds(Node node, Long starterMemberId) {
        JsonNode config = node.actorConfig();
        List<Long> memberIds = new ArrayList<>();
        if (config != null && config.has("memberIds") && config.get("memberIds").isArray()) {
            config.get("memberIds").forEach(item -> {
                if (item.asLong() > 0) {
                    memberIds.add(item.asLong());
                }
            });
        }
        if (memberIds.isEmpty() && Objects.nonNull(starterMemberId)) {
            memberIds.add(starterMemberId);
        }
        return memberIds;
    }

    private void ensureTaskActor(Task task) {
        Long memberId = currentMemberId();
        boolean allowed = Objects.equals(task.getClaimMemberId(), memberId) || taskActorService.lambdaQuery()
                .eq(TaskActor::getSystemId, task.getSystemId())
                .eq(TaskActor::getTaskId, task.getId())
                .eq(TaskActor::getActorMemberId, memberId)
                .eq(TaskActor::getStatus, ACTIVE)
                .count() > 0;
        if (!allowed) {
            throw new BusinessException(FlowErrorCode.TASK_ACTOR_INVALID);
        }
    }

    private void ensureTaskVersion(Task task, Integer taskVersion) {
        if (!PENDING.equals(task.getStatus())) {
            throw new BusinessException(FlowErrorCode.TASK_ALREADY_HANDLED);
        }
        if (!Objects.equals(task.getTaskVersion(), taskVersion)) {
            throw new BusinessException(FlowErrorCode.TASK_ALREADY_HANDLED);
        }
    }

    private void cancelPendingTasks(Long instanceId) {
        taskService.lambdaUpdate()
                .eq(Task::getInstanceId, instanceId)
                .eq(Task::getStatus, PENDING)
                .set(Task::getStatus, CANCELED)
                .set(Task::getUpdatedAt, LocalDateTime.now())
                .update();
    }

    private void deactivateActors(Long taskId) {
        taskActorService.lambdaUpdate()
                .eq(TaskActor::getTaskId, taskId)
                .set(TaskActor::getStatus, INACTIVE)
                .set(TaskActor::getUpdatedAt, LocalDateTime.now())
                .update();
    }

    private void saveActionLog(Instance instance, Task task, String action, String comment, String fromNode, String toNode,
            String resultStatus) {
        actionLogService.save(new ActionLog()
                .setSystemId(instance.getSystemId())
                .setTenantId(instance.getTenantId())
                .setInstanceId(instance.getId())
                .setTaskId(Objects.isNull(task) ? null : task.getId())
                .setAction(action)
                .setOperatorMemberId(currentMemberId())
                .setComment(comment)
                .setFromNodeKey(fromNode)
                .setToNodeKey(toNode)
                .setResultStatus(resultStatus)
                .setRequestId(currentRequestId())
                .setCreatedAt(LocalDateTime.now())
                .setUpdatedAt(LocalDateTime.now()));
    }

    private Template activeTemplate(Long systemId, Long templateId) {
        Template template = templateService.lambdaQuery()
                .eq(Template::getSystemId, systemId)
                .eq(Template::getId, templateId)
                .eq(Template::getDeleted, NO)
                .one();
        if (Objects.isNull(template)) {
            throw new BusinessException(FlowErrorCode.TEMPLATE_NOT_FOUND);
        }
        return template;
    }

    private Instance activeInstance(Long systemId, Long instanceId) {
        Instance instance = instanceService.lambdaQuery()
                .eq(Instance::getSystemId, systemId)
                .eq(Instance::getId, instanceId)
                .one();
        if (Objects.isNull(instance)) {
            throw new BusinessException(FlowErrorCode.INSTANCE_NOT_FOUND);
        }
        return instance;
    }

    private Task activeTask(Long systemId, Long taskId) {
        Task task = taskService.lambdaQuery()
                .eq(Task::getSystemId, systemId)
                .eq(Task::getId, taskId)
                .one();
        if (Objects.isNull(task)) {
            throw new BusinessException(FlowErrorCode.TASK_NOT_FOUND);
        }
        return task;
    }

    private TemplateVersion publishedVersion(Long systemId, Long versionId) {
        TemplateVersion version = templateVersionService.lambdaQuery()
                .eq(TemplateVersion::getSystemId, systemId)
                .eq(TemplateVersion::getId, versionId)
                .eq(TemplateVersion::getStatus, PUBLISHED)
                .one();
        if (Objects.isNull(version)) {
            throw new BusinessException(FlowErrorCode.TEMPLATE_NOT_PUBLISHED);
        }
        return version;
    }

    private Optional<TemplateVersion> draftVersion(Long systemId, Long templateId) {
        return Optional.ofNullable(templateVersionService.lambdaQuery()
                .eq(TemplateVersion::getSystemId, systemId)
                .eq(TemplateVersion::getTemplateId, templateId)
                .eq(TemplateVersion::getStatus, DRAFT)
                .orderByDesc(TemplateVersion::getUpdatedAt)
                .last("limit 1")
                .one());
    }

    private Integer nextVersionNo(Long systemId, Long templateId) {
        TemplateVersion latest = templateVersionService.lambdaQuery()
                .eq(TemplateVersion::getSystemId, systemId)
                .eq(TemplateVersion::getTemplateId, templateId)
                .orderByDesc(TemplateVersion::getVersionNo)
                .last("limit 1")
                .one();
        return Objects.isNull(latest) ? 1 : latest.getVersionNo() + 1;
    }

    private List<Node> nodes(JsonNode graph) {
        JsonNode nodes = graph.path("nodes");
        if (!nodes.isArray()) {
            return List.of();
        }
        List<Node> result = new ArrayList<>();
        nodes.forEach(item -> result.add(new Node(item.path("nodeKey").asText(), item.path("nodeName").asText(),
                item.path("nodeType").asText(), item.path("actorStrategy").asText(null), item.path("actorConfig"),
                item.path("approvalRequired").asBoolean(false), item.path("sortOrder").asInt(0))));
        return result;
    }

    private List<FlowTemplateLineBO> lines(JsonNode graph) {
        JsonNode lines = graph.path("lines");
        if (!lines.isArray()) {
            return List.of();
        }
        List<FlowTemplateLineBO> result = new ArrayList<>();
        lines.forEach(item -> {
            FlowTemplateLineBO line = new FlowTemplateLineBO();
            line.setLineKey(item.path("lineKey").asText());
            line.setFromNodeKey(item.path("fromNodeKey").asText());
            line.setToNodeKey(item.path("toNodeKey").asText());
            line.setConditionMode(item.path("conditionMode").asText("ALWAYS"));
            line.setSortOrder(item.path("sortOrder").asInt(0));
            line.setConditions(List.of());
            result.add(line);
        });
        return result;
    }

    private JsonNode emptyGraph() {
        ObjectNode graph = objectMapper.createObjectNode();
        graph.set("nodes", objectMapper.createArrayNode());
        graph.set("lines", objectMapper.createArrayNode());
        return graph;
    }

    private JsonNode recordSummary(Instance instance) {
        Record record = recordService.getById(instance.getRecordId());
        ObjectNode summary = objectMapper.createObjectNode();
        if (Objects.nonNull(record)) {
            summary.put("recordId", toId(record.getRecordId()));
            summary.put("title", record.getTitle());
            summary.put("recordNo", record.getRecordNo());
            summary.put("recordStatus", record.getRecordStatus());
        }
        return summary;
    }

    private String recordTitle(Instance instance) {
        Record record = recordService.getById(instance.getRecordId());
        if (Objects.isNull(record)) {
            return toId(instance.getRecordId());
        }
        return StringUtils.hasText(record.getTitle()) ? record.getTitle() : record.getRecordNo();
    }

    private String currentNodeName(Instance instance) {
        if (!StringUtils.hasText(instance.getCurrentNodeKeys())) {
            return null;
        }
        return templateNodeService.lambdaQuery()
                .eq(TemplateNode::getSystemId, instance.getSystemId())
                .eq(TemplateNode::getTemplateVersionId, instance.getTemplateVersionId())
                .eq(TemplateNode::getNodeKey, instance.getCurrentNodeKeys())
                .oneOpt()
                .map(TemplateNode::getNodeName)
                .orElse(instance.getCurrentNodeKeys());
    }

    private FlowActionResultVO actionResult(Instance instance, Task task, String taskStatus, List<String> nextNodes) {
        return FlowActionResultVO.builder()
                .instanceId(toId(instance.getId()))
                .taskId(Objects.isNull(task) ? null : toId(task.getId()))
                .instanceStatus(instance.getStatus())
                .taskStatus(taskStatus)
                .currentNode(instance.getCurrentNodeKeys())
                .nextNodes(nextNodes)
                .businessRecordStatus(recordStatus(instance.getRecordId()))
                .build();
    }

    private String recordStatus(Long recordId) {
        Record record = recordService.getById(recordId);
        return Objects.isNull(record) ? null : record.getRecordStatus();
    }

    private FlowTaskListItemVO toTaskListItemVO(Task task) {
        Instance instance = instanceService.getById(task.getInstanceId());
        return FlowTaskListItemVO.builder()
                .taskId(toId(task.getId()))
                .instanceId(toId(task.getInstanceId()))
                .recordId(Objects.isNull(instance) ? null : toId(instance.getRecordId()))
                .moduleId(Objects.isNull(instance) ? null : toId(instance.getModuleId()))
                .nodeKey(task.getNodeKey())
                .taskName(task.getTaskName())
                .taskStatus(task.getStatus())
                .taskVersion(task.getTaskVersion())
                .recordSummary(Objects.isNull(instance) ? NullNode.getInstance() : recordSummary(instance))
                .dueAt(task.getDueAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }

    private FlowTemplateVO toTemplateVO(Template template) {
        return FlowTemplateVO.builder()
                .templateId(toId(template.getId()))
                .code(template.getCode())
                .name(template.getName())
                .status(template.getStatus())
                .currentVersionId(toId(template.getCurrentVersionId()))
                .description(template.getDescription())
                .versionNo(template.getVersionNo())
                .updatedAt(template.getUpdatedAt())
                .build();
    }

    private FlowBindingVO toBindingVO(Binding binding) {
        return FlowBindingVO.builder()
                .bindingId(toId(binding.getId()))
                .moduleId(toId(binding.getModuleId()))
                .actionCode(binding.getActionCode())
                .templateId(toId(binding.getTemplateId()))
                .templateVersionId(toId(binding.getTemplateVersionId()))
                .status(binding.getStatus())
                .versionNo(binding.getVersionNo())
                .build();
    }

    private FlowCcItemVO toCcItemVO(Cc cc, Instance instance) {
        return FlowCcItemVO.builder()
                .ccId(toId(cc.getId()))
                .instanceId(toId(cc.getInstanceId()))
                .moduleId(toId(instance.getModuleId()))
                .recordId(toId(instance.getRecordId()))
                .recordTitle(recordTitle(instance))
                .nodeName(currentNodeName(instance))
                .read("READ".equals(cc.getReadStatus()))
                .createdAt(cc.getCreatedAt())
                .build();
    }

    private FlowInstanceVO toInstanceVO(Instance instance) {
        return FlowInstanceVO.builder()
                .instanceId(toId(instance.getId()))
                .moduleId(toId(instance.getModuleId()))
                .recordId(toId(instance.getRecordId()))
                .templateId(toId(instance.getTemplateId()))
                .templateVersionId(toId(instance.getTemplateVersionId()))
                .status(instance.getStatus())
                .starterMemberId(toId(instance.getStarterMemberId()))
                .currentNodeKeys(instance.getCurrentNodeKeys())
                .versionNo(instance.getVersionNo())
                .startedAt(instance.getStartedAt())
                .finishedAt(instance.getFinishedAt())
                .build();
    }

    private FlowHistoryItemVO toHistoryVO(ActionLog log) {
        return FlowHistoryItemVO.builder()
                .logId(toId(log.getId()))
                .taskId(toId(log.getTaskId()))
                .action(log.getAction())
                .operatorMemberId(toId(log.getOperatorMemberId()))
                .comment(log.getComment())
                .fromNodeKey(log.getFromNodeKey())
                .toNodeKey(log.getToNodeKey())
                .resultStatus(log.getResultStatus())
                .requestId(log.getRequestId())
                .createdAt(log.getCreatedAt())
                .build();
    }

    private <T> PageResult<T> page(List<T> records, FlowTaskQueryBO queryBO) {
        long pageNo = Optional.ofNullable(queryBO.getPageNo()).orElse(1L);
        long pageSize = Optional.ofNullable(queryBO.getPageSize()).orElse(20L);
        int from = Math.toIntExact(Math.min((pageNo - 1) * pageSize, records.size()));
        int to = Math.toIntExact(Math.min(from + pageSize, records.size()));
        return PageResult.<T>builder()
                .records(records.subList(from, to))
                .total(records.size())
                .pageNo(pageNo)
                .pageSize(pageSize)
                .hasNext(to < records.size())
                .build();
    }

    private FlowCheckIssueVO issue(String code, String targetType, String targetCode, String message) {
        return FlowCheckIssueVO.builder()
                .code(code)
                .level("ERROR")
                .targetType(targetType)
                .targetCode(targetCode)
                .message(message)
                .build();
    }

    private JsonNode readJson(String json) {
        if (!StringUtils.hasText(json)) {
            return NullNode.getInstance();
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException ex) {
            return NullNode.getInstance();
        }
    }

    private String writeJson(Object value) {
        if (Objects.isNull(value)) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(FlowErrorCode.TEMPLATE_CHECK_FAILED);
        }
    }

    private Long currentTenantId() {
        RequestContext context = RequestContextHolder.get();
        return context != null && StringUtils.hasText(context.getTenantId()) ? Long.valueOf(context.getTenantId()) : null;
    }

    private Long currentMemberId() {
        RequestContext context = RequestContextHolder.get();
        return context != null && StringUtils.hasText(context.getMemberId()) ? Long.valueOf(context.getMemberId()) : null;
    }

    private String currentRequestId() {
        RequestContext context = RequestContextHolder.get();
        return Objects.isNull(context) ? null : context.getRequestId();
    }

    private boolean contains(String source, String keyword) {
        return StringUtils.hasText(source) && source.contains(keyword);
    }

    private String defaultText(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private Integer nextTemplateVersion(Integer version) {
        return Objects.isNull(version) ? 1 : version + 1;
    }

    private String toId(Long id) {
        return Objects.isNull(id) ? null : id.toString();
    }

    private record Node(String nodeKey, String nodeName, String nodeType, String actorStrategy, JsonNode actorConfig,
            Boolean approvalRequired, Integer sortOrder) {
    }

    private record Graph(List<Node> nodes, List<FlowTemplateLineBO> lines) {

        private Node node(String nodeKey) {
            return nodes.stream().filter(node -> Objects.equals(node.nodeKey(), nodeKey)).findFirst().orElse(null);
        }
    }
}
