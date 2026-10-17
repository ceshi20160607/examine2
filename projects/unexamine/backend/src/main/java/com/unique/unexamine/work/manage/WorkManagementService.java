package com.unique.unexamine.work.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.authorization.manage.PermissionChecker;
import com.unique.unexamine.notification.manage.MessageService;
import com.unique.unexamine.notification.manage.RuntimeTargetAccessReader;
import com.unique.unexamine.runtimedata.manage.RuntimeDataService;
import com.unique.unexamine.shared.manage.web.DomainException;
import com.unique.unexamine.work.base.entity.WorkProject;
import com.unique.unexamine.work.base.entity.WorkProjectMember;
import com.unique.unexamine.work.base.entity.WorkLog;
import com.unique.unexamine.work.base.entity.WorkLogTaskLink;
import com.unique.unexamine.work.base.entity.WorkTask;
import com.unique.unexamine.work.base.entity.WorkTaskGroup;
import com.unique.unexamine.work.base.entity.WorkTaskHistory;
import com.unique.unexamine.work.base.entity.WorkTaskMember;
import com.unique.unexamine.work.base.service.WorkProjectBaseService;
import com.unique.unexamine.work.base.service.WorkProjectMemberBaseService;
import com.unique.unexamine.work.base.service.WorkLogBaseService;
import com.unique.unexamine.work.base.service.WorkLogTaskLinkBaseService;
import com.unique.unexamine.work.base.service.WorkTaskBaseService;
import com.unique.unexamine.work.base.service.WorkTaskGroupBaseService;
import com.unique.unexamine.work.base.service.WorkTaskHistoryBaseService;
import com.unique.unexamine.work.base.service.WorkTaskMemberBaseService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class WorkManagementService {
    private static final Set<String> PROJECT_ROLES = Set.of("OWNER", "MANAGER", "MEMBER", "OBSERVER");
    private static final Set<String> PROJECT_STATUSES = Set.of("PLANNED", "ACTIVE", "PAUSED", "COMPLETED", "CANCELLED");
    private static final Set<String> TASK_STATUSES = Set.of(
            "BACKLOG", "TODO", "IN_PROGRESS", "BLOCKED", "WAITING_CONFIRMATION",
            "COMPLETED", "CANCELLED", "ARCHIVED");
    private static final Map<String, Set<String>> TASK_TRANSITIONS = Map.of(
            "BACKLOG", Set.of("TODO", "IN_PROGRESS", "CANCELLED"),
            "TODO", Set.of("IN_PROGRESS", "CANCELLED"),
            "IN_PROGRESS", Set.of("BLOCKED", "CANCELLED"),
            "BLOCKED", Set.of("IN_PROGRESS", "CANCELLED"),
            "WAITING_CONFIRMATION", Set.of(),
            "COMPLETED", Set.of(),
            "CANCELLED", Set.of(),
            "ARCHIVED", Set.of());

    private final WorkProjectBaseService projectService;
    private final WorkProjectMemberBaseService projectMemberService;
    private final WorkTaskGroupBaseService taskGroupService;
    private final WorkTaskBaseService taskService;
    private final WorkTaskMemberBaseService taskMemberService;
    private final WorkTaskHistoryBaseService taskHistoryService;
    private final WorkLogBaseService workLogService;
    private final WorkLogTaskLinkBaseService workLogTaskLinkService;
    private final WorkParticipantResolver participantResolver;
    private final RuntimeDataService runtimeDataService;
    /** 任务可继续访问时，关联业务的拒绝读取需与任务外层事务隔离。 */
    private final RuntimeTargetAccessReader runtimeTargetAccessReader;
    private final PermissionChecker permissionChecker;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;
    private final MessageService messageService;
    private final WorkConfigurationService workConfigurationService;

    public WorkManagementService(
            WorkProjectBaseService projectService,
            WorkProjectMemberBaseService projectMemberService,
            WorkTaskGroupBaseService taskGroupService,
            WorkTaskBaseService taskService,
            WorkTaskMemberBaseService taskMemberService,
            WorkTaskHistoryBaseService taskHistoryService,
            WorkLogBaseService workLogService,
            WorkLogTaskLinkBaseService workLogTaskLinkService,
            WorkParticipantResolver participantResolver,
            RuntimeDataService runtimeDataService,
            RuntimeTargetAccessReader runtimeTargetAccessReader,
            PermissionChecker permissionChecker,
            AuditRecorder auditRecorder,
            ObjectMapper objectMapper,
            MessageService messageService,
            WorkConfigurationService workConfigurationService) {
        this.projectService = projectService;
        this.projectMemberService = projectMemberService;
        this.taskGroupService = taskGroupService;
        this.taskService = taskService;
        this.taskMemberService = taskMemberService;
        this.taskHistoryService = taskHistoryService;
        this.workLogService = workLogService;
        this.workLogTaskLinkService = workLogTaskLinkService;
        this.participantResolver = participantResolver;
        this.runtimeDataService = runtimeDataService;
        this.runtimeTargetAccessReader = runtimeTargetAccessReader;
        this.permissionChecker = permissionChecker;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
        this.messageService = messageService;
        this.workConfigurationService = workConfigurationService;
    }

    @Transactional(readOnly = true)
    public List<WorkManagementModels.ProjectView> projects(AuthenticatedContext context) {
        requireAction(context, "VIEW");
        return scopedProjects(context).stream()
                .filter(project -> canViewProject(context, project))
                .sorted(Comparator.comparing(WorkProject::getUpdateTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(project -> view(context, project, false)).toList();
    }

    @Transactional
    public WorkManagementModels.ProjectView createProject(
            AuthenticatedContext context, WorkManagementModels.CreateProjectRequest input, String traceId) {
        requireAction(context, "CREATE_PROJECT");
        requireValidDates(input.startDate(), input.dueDate());
        String code = normalizeCode(input.code() == null || input.code().isBlank()
                ? "project_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12)
                : input.code());
        if (scopedProjects(context).stream().anyMatch(project -> code.equals(project.getCode()))) {
            throw conflict("WORK_PROJECT_CODE_CONFLICT", "当前范围已存在相同项目编码");
        }
        BusinessReference business = validateBusinessReference(context, input.businessType(), input.businessId(),
                input.businessTitle(), traceId);
        WorkParticipantResolver.ResolvedPerson owner = participantResolver.current(context);
        LinkedHashMap<Long, MemberAssignment> members = new LinkedHashMap<>();
        members.put(owner.accountId(), new MemberAssignment(owner, "OWNER"));
        if (input.members() != null) {
            for (WorkManagementModels.ProjectMemberInput member : input.members()) {
                WorkParticipantResolver.ResolvedPerson selected = participantResolver.require(
                        context, member.tenantMemberId(), member.compatibilityAccountId());
                String role = member.projectRole().strip().toUpperCase(Locale.ROOT);
                if (!PROJECT_ROLES.contains(role)
                        || "OWNER".equals(role) && !Objects.equals(selected.accountId(), context.accountId())) {
                    throw invalid("PROJECT_ROLE_INVALID", "项目成员角色无效");
                }
                members.put(selected.accountId(), new MemberAssignment(selected, role));
            }
        }
        WorkProject project = new WorkProject();
        bindContext(project, context);
        project.setCode(code);
        project.setName(input.name().strip());
        project.setDescription(blankToNull(input.description()));
        project.setCustomerName(blankToNull(input.customerName()));
        project.setInternalDepartmentName(blankToNull(input.internalDepartmentName()));
        project.setStage(blankToNull(input.stage()));
        project.setLabelsJson(nullableListJson(normalizeLabels(input.labels())));
        project.setBusinessType(business.type());
        project.setBusinessId(business.id());
        project.setBusinessTitle(business.title());
        project.setOwnerAccountId(context.accountId());
        project.setOwnerTenantMemberId(owner.tenantMemberId());
        project.setStartDate(input.startDate());
        project.setDueDate(input.dueDate());
        project.setProgressPercent(BigDecimal.ZERO);
        project.setStatus("PLANNED");
        project.setVersion(0);
        projectService.insert(project);
        members.values().forEach(member -> insertProjectMember(project.getId(), member.person(), member.role()));
        audit(context, traceId, "WORK_PROJECT_CREATED", "WORK_PROJECT", project.getId(),
                Map.of("code", code, "memberCount", members.size()));
        return view(context, project, true);
    }

    @Transactional(readOnly = true)
    public WorkManagementModels.ProjectView project(AuthenticatedContext context, Long projectId) {
        requireAction(context, "VIEW");
        WorkProject project = requireOwned(context, projectId);
        if (!canViewProject(context, project)) {
            throw forbidden("PROJECT_ROLE_DENIED", "你不是该项目的有效成员");
        }
        return view(context, project, true);
    }

    @Transactional
    public WorkManagementModels.ProjectView updateProject(
            AuthenticatedContext context, Long projectId, WorkManagementModels.UpdateProjectRequest input,
            String traceId) {
        requireAction(context, "MANAGE_PROJECT");
        WorkProject project = requireOwned(context, projectId);
        requireProjectRole(context, projectId, Set.of("OWNER", "MANAGER"));
        if (!Objects.equals(project.getVersion(), input.expectedVersion())) {
            throw conflict("WORK_PROJECT_VERSION_CONFLICT", "项目已被其他人修改，请刷新后重试");
        }
        requireValidDates(input.startDate(), input.dueDate());
        String status = input.status().strip().toUpperCase(Locale.ROOT);
        if (!PROJECT_STATUSES.contains(status)) {
            throw invalid("PROJECT_STATUS_INVALID", "项目状态无效");
        }
        project.setName(input.name().strip());
        project.setDescription(blankToNull(input.description()));
        if (input.customerName() != null) project.setCustomerName(blankToNull(input.customerName()));
        if (input.internalDepartmentName() != null) {
            project.setInternalDepartmentName(blankToNull(input.internalDepartmentName()));
        }
        if (input.stage() != null) project.setStage(blankToNull(input.stage()));
        if (input.labels() != null) project.setLabelsJson(nullableListJson(normalizeLabels(input.labels())));
        if (input.businessType() != null || input.businessId() != null || input.businessTitle() != null) {
            BusinessReference business = validateBusinessReference(context, input.businessType(), input.businessId(),
                    input.businessTitle(), traceId);
            project.setBusinessType(business.type());
            project.setBusinessId(business.id());
            project.setBusinessTitle(business.title());
        }
        project.setStartDate(input.startDate());
        project.setDueDate(input.dueDate());
        project.setStatus(status);
        project.setProgressPercent(input.progressPercent());
        if (projectService.updateById(project) != 1) {
            throw conflict("WORK_PROJECT_VERSION_CONFLICT", "项目已被其他人修改，请刷新后重试");
        }
        if (input.members() != null) {
            replaceProjectMembers(context, project, input.members());
        }
        audit(context, traceId, "WORK_PROJECT_UPDATED", "WORK_PROJECT", projectId,
                Map.of("status", status, "progressPercent", input.progressPercent()));
        return view(context, requireOwned(context, projectId), true);
    }

    @Transactional
    public WorkManagementModels.ProjectView createTaskGroup(
            AuthenticatedContext context, Long projectId, WorkManagementModels.CreateTaskGroupRequest input,
            String traceId) {
        requireAction(context, "MANAGE_PROJECT");
        WorkProject project = requireOwned(context, projectId);
        requireProjectRole(context, projectId, Set.of("OWNER", "MANAGER"));
        if (taskGroups(projectId).stream().anyMatch(group -> Objects.equals(group.getSortOrder(), input.sortOrder()))) {
            throw conflict("TASK_GROUP_ORDER_CONFLICT", "同一项目内任务组排序值不能重复");
        }
        WorkTaskGroup group = new WorkTaskGroup();
        group.setProjectId(projectId);
        group.setName(input.name().strip());
        group.setSortOrder(input.sortOrder());
        group.setStatus("ACTIVE");
        if (input.ownerTenantMemberId() != null || input.compatibilityOwnerAccountId() != null) {
            WorkParticipantResolver.ResolvedPerson owner = participantResolver.require(
                    context, input.ownerTenantMemberId(), input.compatibilityOwnerAccountId());
            requireProjectMember(projectId, owner.accountId());
            group.setOwnerAccountId(owner.accountId());
            group.setOwnerTenantMemberId(owner.tenantMemberId());
        }
        group.setDueTime(input.dueTime());
        group.setProgressPercent(input.progressPercent() == null ? BigDecimal.ZERO : input.progressPercent());
        group.setVersion(0);
        taskGroupService.insert(group);
        audit(context, traceId, "WORK_TASK_GROUP_CREATED", "WORK_TASK_GROUP", group.getId(),
                Map.of("projectId", projectId, "sortOrder", group.getSortOrder()));
        return view(context, project, true);
    }

    @Transactional
    public WorkManagementModels.ProjectView updateTaskGroup(
            AuthenticatedContext context, Long projectId, Long groupId,
            WorkManagementModels.UpdateTaskGroupRequest input, String traceId) {
        requireAction(context, "MANAGE_PROJECT");
        WorkProject project = requireOwned(context, projectId);
        requireProjectRole(context, projectId, Set.of("OWNER", "MANAGER"));
        WorkTaskGroup group = taskGroupService.selectById(groupId);
        if (group == null || !Objects.equals(group.getProjectId(), projectId)) {
            throw new DomainException("TASK_GROUP_NOT_FOUND", "任务组不存在或不属于当前项目", HttpStatus.NOT_FOUND);
        }
        if (!Objects.equals(group.getVersion(), input.expectedVersion())) {
            throw conflict("TASK_GROUP_VERSION_CONFLICT", "任务组已被其他人修改，请刷新后重试");
        }
        if (taskGroups(projectId).stream().anyMatch(item -> !Objects.equals(item.getId(), groupId)
                && Objects.equals(item.getSortOrder(), input.sortOrder()))) {
            throw conflict("TASK_GROUP_ORDER_CONFLICT", "同一项目内任务组排序值不能重复");
        }
        String status = input.status().strip().toUpperCase(Locale.ROOT);
        if (!Set.of("ACTIVE", "COMPLETED", "CANCELLED").contains(status)) {
            throw invalid("TASK_GROUP_STATUS_INVALID", "任务组状态无效");
        }
        group.setName(input.name().strip());
        group.setSortOrder(input.sortOrder());
        group.setStatus(status);
        group.setDueTime(input.dueTime());
        group.setProgressPercent(input.progressPercent());
        if (input.ownerTenantMemberId() == null && input.compatibilityOwnerAccountId() == null) {
            group.setOwnerAccountId(null);
            group.setOwnerTenantMemberId(null);
        } else {
            WorkParticipantResolver.ResolvedPerson owner = participantResolver.require(
                    context, input.ownerTenantMemberId(), input.compatibilityOwnerAccountId());
            requireProjectMember(projectId, owner.accountId());
            group.setOwnerAccountId(owner.accountId());
            group.setOwnerTenantMemberId(owner.tenantMemberId());
        }
        if (taskGroupService.updateById(group) != 1) {
            throw conflict("TASK_GROUP_VERSION_CONFLICT", "任务组已被其他人修改，请刷新后重试");
        }
        audit(context, traceId, "WORK_TASK_GROUP_UPDATED", "WORK_TASK_GROUP", groupId,
                Map.of("projectId", projectId, "status", status));
        return view(context, project, true);
    }

    @Transactional
    public WorkManagementModels.TaskView createTask(
            AuthenticatedContext context, Long projectId, WorkManagementModels.CreateTaskRequest input,
            String traceId) {
        requireAction(context, "CREATE_TASK");
        WorkProject project = requireOwned(context, projectId);
        requireProjectRole(context, projectId, Set.of("OWNER", "MANAGER", "MEMBER"));
        WorkParticipantResolver.ResolvedPerson owner = participantResolver.require(
                context, input.ownerTenantMemberId(), input.compatibilityOwnerAccountId());
        requireProjectMember(projectId, owner.accountId());
        WorkTaskGroup group = null;
        if (input.taskGroupId() != null) {
            group = taskGroupService.selectById(input.taskGroupId());
            if (group == null || !Objects.equals(group.getProjectId(), projectId) || !"ACTIVE".equals(group.getStatus())) {
                throw invalid("TASK_GROUP_INVALID", "任务组不存在、已停用或不属于当前项目");
            }
        }
        WorkTask parent = null;
        if (input.parentTaskId() != null) {
            parent = taskService.selectById(input.parentTaskId());
            if (parent == null || !Objects.equals(parent.getProjectId(), projectId) || !owned(context, parent)) {
                throw invalid("PARENT_TASK_INVALID", "父任务不存在或不属于当前项目");
            }
        }
        requireValidTimes(input.startAt(), input.dueAt());
        String priority = normalizePriority(input.priority());
        List<WorkParticipantResolver.ResolvedPerson> collaborators = normalizeCollaborators(context, projectId,
                input.collaboratorTenantMemberIds(), input.compatibilityCollaboratorAccountIds(), owner);
        List<WorkParticipantResolver.ResolvedPerson> followers = normalizeParticipants(
                context, projectId, input.followerTenantMemberIds(), owner, collaborators);
        List<WorkParticipantResolver.ResolvedPerson> acceptors = normalizeParticipants(
                context, projectId, input.acceptanceTenantMemberIds(), owner, List.of());
        BusinessReference business = validateBusinessReference(context, input.businessType(), input.businessId(),
                input.businessTitle(), traceId);
        WorkTask task = new WorkTask();
        bindContext(task, context);
        task.setProjectId(projectId);
        task.setTaskGroupId(group == null ? null : group.getId());
        task.setParentTaskId(parent == null ? null : parent.getId());
        task.setTitle(input.title().strip());
        task.setDescription(blankToNull(input.description()));
        task.setTaskType("PROJECT");
        task.setPriority(priority);
        task.setStatus("TODO");
        task.setProgressPercent(BigDecimal.ZERO);
        task.setEstimatedHours(input.estimatedHours());
        task.setLabelsJson(nullableListJson(normalizeLabels(input.labels())));
        task.setChecklistJson(nullableListJson(normalizeChecklist(input.checklist())));
        task.setOwnerAccountId(owner.accountId());
        task.setOwnerTenantMemberId(owner.tenantMemberId());
        task.setStartTime(input.startAt());
        task.setDueTime(input.dueAt());
        task.setCustomValuesJson(nullableJson(
                workConfigurationService.validateConfiguredValues(context, "TASK", input.configuredValues())));
        task.setBusinessType(business.type());
        task.setBusinessId(business.id());
        task.setBusinessTitle(business.title());
        task.setCreatedByAccountId(context.accountId());
        task.setVersion(0);
        taskService.insert(task);
        replaceTaskMembers(task.getId(), collaborators, followers, acceptors);
        insertHistory(task, "CREATED", null, snapshot(task), null, context.accountId());
        audit(context, traceId, "WORK_PROJECT_TASK_CREATED", "WORK_TASK", task.getId(),
                Map.of("projectId", projectId, "owner", owner.displayName(),
                        "collaboratorCount", collaborators.size(), "followerCount", followers.size(),
                        "acceptorCount", acceptors.size()));
        projectTaskEvent(context, task, "CREATED", traceId);
        return view(context, task, true);
    }

    @Transactional(readOnly = true)
    public List<WorkManagementModels.TaskView> ordinaryTasks(AuthenticatedContext context) {
        requireAction(context, "VIEW");
        return scopedOrdinaryTasks(context).stream()
                .filter(task -> taskVisibleTo(context, task))
                .sorted(Comparator.comparing(WorkTask::getUpdateTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(task -> view(context, task, false)).toList();
    }

    @Transactional
    public WorkManagementModels.TaskView createOrdinaryTask(
            AuthenticatedContext context, WorkManagementModels.CreateTaskRequest input, String traceId) {
        requireAction(context, "CREATE_TASK");
        WorkParticipantResolver.ResolvedPerson owner = participantResolver.require(
                context, input.ownerTenantMemberId(), input.compatibilityOwnerAccountId());
        if (input.taskGroupId() != null) {
            throw invalid("ORDINARY_TASK_RELATION_INVALID", "普通任务不能绑定项目任务组");
        }
        WorkTask parent = null;
        if (input.parentTaskId() != null) {
            parent = requireOwnedTask(context, input.parentTaskId());
            if (parent.getProjectId() != null || parent.getDeleteTime() != null) {
                throw invalid("PARENT_TASK_INVALID", "父任务不存在或不是当前范围的普通任务");
            }
            requireTaskModification(context, parent);
        }
        requireValidTimes(input.startAt(), input.dueAt());
        List<WorkParticipantResolver.ResolvedPerson> collaborators = normalizeCollaborators(context, null,
                input.collaboratorTenantMemberIds(), input.compatibilityCollaboratorAccountIds(), owner);
        List<WorkParticipantResolver.ResolvedPerson> followers = normalizeParticipants(
                context, null, input.followerTenantMemberIds(), owner, collaborators);
        List<WorkParticipantResolver.ResolvedPerson> acceptors = normalizeParticipants(
                context, null, input.acceptanceTenantMemberIds(), owner, List.of());
        BusinessReference business = validateBusinessReference(context, input.businessType(), input.businessId(),
                input.businessTitle(), traceId);
        WorkTask task = new WorkTask();
        bindContext(task, context);
        task.setProjectId(null);
        task.setTaskGroupId(null);
        task.setParentTaskId(parent == null ? null : parent.getId());
        task.setTitle(input.title().strip());
        task.setDescription(blankToNull(input.description()));
        task.setTaskType("ORDINARY");
        task.setPriority(normalizePriority(input.priority()));
        task.setStatus("TODO");
        task.setProgressPercent(BigDecimal.ZERO);
        task.setEstimatedHours(input.estimatedHours());
        task.setLabelsJson(nullableListJson(normalizeLabels(input.labels())));
        task.setChecklistJson(nullableListJson(normalizeChecklist(input.checklist())));
        task.setOwnerAccountId(owner.accountId());
        task.setOwnerTenantMemberId(owner.tenantMemberId());
        task.setStartTime(input.startAt());
        task.setDueTime(input.dueAt());
        task.setCustomValuesJson(nullableJson(
                workConfigurationService.validateConfiguredValues(context, "TASK", input.configuredValues())));
        task.setBusinessType(business.type());
        task.setBusinessId(business.id());
        task.setBusinessTitle(business.title());
        task.setCreatedByAccountId(context.accountId());
        task.setVersion(0);
        taskService.insert(task);
        replaceTaskMembers(task.getId(), collaborators, followers, acceptors);
        insertHistory(task, "CREATED", null, snapshot(task), null, context.accountId());
        audit(context, traceId, "WORK_ORDINARY_TASK_CREATED", "WORK_TASK", task.getId(),
                Map.of("owner", owner.displayName(), "collaboratorCount", collaborators.size(),
                        "followerCount", followers.size(), "acceptorCount", acceptors.size()));
        projectTaskEvent(context, task, "CREATED", traceId);
        return view(context, task, true);
    }

    @Transactional(readOnly = true)
    public WorkManagementModels.TaskView task(AuthenticatedContext context, Long taskId) {
        requireAction(context, "VIEW");
        WorkTask task = requireOwnedTask(context, taskId);
        requireTaskVisible(context, task);
        return view(context, task, true);
    }

    @Transactional
    public WorkManagementModels.TaskView updateTask(
            AuthenticatedContext context, Long taskId, WorkManagementModels.UpdateTaskRequest input,
            String traceId) {
        requireAction(context, "UPDATE_TASK");
        WorkTask task = requireOwnedTask(context, taskId);
        requireTaskModification(context, task);
        if (!Objects.equals(task.getVersion(), input.expectedVersion())) {
            throw conflict("WORK_TASK_VERSION_CONFLICT", "任务已被其他人修改，请刷新后重试");
        }
        WorkParticipantResolver.ResolvedPerson owner = participantResolver.require(
                context, input.ownerTenantMemberId(), input.compatibilityOwnerAccountId());
        if (task.getProjectId() != null) {
            requireProjectMember(task.getProjectId(), owner.accountId());
        }
        if (!Objects.equals(owner.accountId(), task.getOwnerAccountId())) {
            throw invalid("WORK_TASK_TRANSFER_ACTION_REQUIRED", "更换负责人请使用转派操作");
        }
        requireValidTimes(input.startAt(), input.dueAt());
        String nextStatus = input.status().strip().toUpperCase(Locale.ROOT);
        validateTransition(task.getStatus(), nextStatus);
        String before = toJson(snapshot(task));
        List<WorkTaskMember> currentMembers = taskMembers(taskId);
        List<WorkParticipantResolver.ResolvedPerson> collaborators =
                input.collaboratorTenantMemberIds() == null && input.compatibilityCollaboratorAccountIds() == null
                        ? resolvedOfType(context, currentMembers, "COLLABORATOR")
                        : normalizeCollaborators(context, task.getProjectId(), input.collaboratorTenantMemberIds(),
                        input.compatibilityCollaboratorAccountIds(), owner);
        List<WorkParticipantResolver.ResolvedPerson> followers = input.followerTenantMemberIds() == null
                ? resolvedOfType(context, currentMembers, "FOLLOWER")
                : normalizeParticipants(context, task.getProjectId(), input.followerTenantMemberIds(), owner, collaborators);
        List<WorkParticipantResolver.ResolvedPerson> acceptors = input.acceptanceTenantMemberIds() == null
                ? resolvedOfType(context, currentMembers, "ACCEPTOR")
                : normalizeParticipants(context, task.getProjectId(), input.acceptanceTenantMemberIds(), owner, List.of());
        BusinessReference business = validateBusinessReference(context, input.businessType(), input.businessId(),
                input.businessTitle(), traceId);
        task.setStatus(nextStatus);
        if (input.title() != null) task.setTitle(input.title().strip());
        if (input.description() != null) task.setDescription(blankToNull(input.description()));
        task.setPriority(normalizePriority(input.priority()));
        task.setProgressPercent(input.progressPercent());
        if (input.estimatedHours() != null) task.setEstimatedHours(input.estimatedHours());
        if (input.actualHours() != null) task.setActualHours(input.actualHours());
        if (input.labels() != null) task.setLabelsJson(nullableListJson(normalizeLabels(input.labels())));
        if (input.checklist() != null) task.setChecklistJson(nullableListJson(normalizeChecklist(input.checklist())));
        task.setStartTime(input.startAt());
        task.setDueTime(input.dueAt());
        task.setCustomValuesJson(nullableJson(
                workConfigurationService.validateConfiguredValues(context, "TASK", input.configuredValues())));
        task.setBusinessType(business.type());
        task.setBusinessId(business.id());
        task.setBusinessTitle(business.title());
        if (taskService.updateById(task) != 1) {
            throw conflict("WORK_TASK_VERSION_CONFLICT", "任务已被其他人修改，请刷新后重试");
        }
        replaceTaskMembers(taskId, collaborators, followers, acceptors);
        WorkTask updated = requireOwnedTask(context, taskId);
        String action = Objects.equals(taskStatus(before), nextStatus) ? "UPDATED" : "STATUS_CHANGED";
        insertHistory(updated, action, before, snapshot(updated), blankToNull(input.comment()), context.accountId());
        audit(context, traceId, "WORK_TASK_UPDATED", "WORK_TASK", taskId,
                Map.of("status", nextStatus, "owner", owner.displayName(), "action", action));
        if ("STATUS_CHANGED".equals(action)) projectTaskEvent(context, updated, "STATUS_CHANGED", traceId);
        return view(context, updated, true);
    }

    /** 转派只改变负责人，原参与人和过程历史均保留。 */
    @Transactional
    public WorkManagementModels.TaskView transferTask(
            AuthenticatedContext context, Long taskId, WorkManagementModels.TransferTaskRequest input,
            String traceId) {
        requireAction(context, "UPDATE_TASK");
        WorkTask task = requireOwnedTask(context, taskId);
        requireTaskAdministration(context, task);
        requireVersion(task, input.expectedVersion());
        requireTaskActive(task);
        WorkParticipantResolver.ResolvedPerson owner = participantResolver.require(
                context, input.ownerTenantMemberId(), input.compatibilityOwnerAccountId());
        if (task.getProjectId() != null) requireProjectMember(task.getProjectId(), owner.accountId());
        if (Objects.equals(task.getOwnerAccountId(), owner.accountId())) {
            throw invalid("WORK_TASK_OWNER_UNCHANGED", "新负责人不能与当前负责人相同");
        }
        String before = toJson(snapshot(task));
        task.setOwnerAccountId(owner.accountId());
        task.setOwnerTenantMemberId(owner.tenantMemberId());
        updateTaskOrConflict(task);
        WorkTask updated = requireOwnedTask(context, taskId);
        insertHistory(updated, "TRANSFERRED", before, snapshot(updated), blankToNull(input.comment()), context.accountId());
        audit(context, traceId, "WORK_TASK_TRANSFERRED", "WORK_TASK", taskId,
                Map.of("owner", owner.displayName()));
        projectTaskEvent(context, updated, "TRANSFERRED", traceId);
        return view(context, updated, true);
    }

    /** 延期申请在当前权限范围内直接生效，并用不可变历史保留申请原因。 */
    @Transactional
    public WorkManagementModels.TaskView delayTask(
            AuthenticatedContext context, Long taskId, WorkManagementModels.DelayTaskRequest input,
            String traceId) {
        requireAction(context, "UPDATE_TASK");
        WorkTask task = requireOwnedTask(context, taskId);
        requireTaskModification(context, task);
        requireVersion(task, input.expectedVersion());
        requireTaskActive(task);
        requireValidTimes(task.getStartTime(), input.dueTime());
        if (task.getDueTime() != null && !input.dueTime().isAfter(task.getDueTime())) {
            throw invalid("WORK_TASK_DELAY_TIME_INVALID", "延期后的截止时间必须晚于当前截止时间");
        }
        String before = toJson(snapshot(task));
        task.setDueTime(input.dueTime());
        updateTaskOrConflict(task);
        WorkTask updated = requireOwnedTask(context, taskId);
        insertHistory(updated, "DELAY_REQUESTED", before, snapshot(updated), input.reason().strip(), context.accountId());
        audit(context, traceId, "WORK_TASK_DELAYED", "WORK_TASK", taskId,
                Map.of("dueTime", input.dueTime().toString()));
        projectTaskEvent(context, updated, "DELAYED", traceId);
        return view(context, updated, true);
    }

    /** 完成任务先进入待验收，不能用普通编辑绕过验收。 */
    @Transactional
    public WorkManagementModels.TaskView submitCompletion(
            AuthenticatedContext context, Long taskId, WorkManagementModels.SubmitCompletionRequest input,
            String traceId) {
        requireAction(context, "UPDATE_TASK");
        WorkTask task = requireOwnedTask(context, taskId);
        requireTaskModification(context, task);
        requireVersion(task, input.expectedVersion());
        if (!Set.of("TODO", "IN_PROGRESS", "BLOCKED").contains(task.getStatus())) {
            throw invalid("WORK_TASK_COMPLETION_STATE_INVALID", "当前状态不能提交完成");
        }
        String before = toJson(snapshot(task));
        task.setStatus("WAITING_CONFIRMATION");
        task.setProgressPercent(new BigDecimal("100"));
        task.setActualHours(input.actualHours());
        task.setCompletionNote(input.completionNote().strip());
        task.setCompletionSubmittedTime(LocalDateTime.now());
        task.setCompletionAcceptedTime(null);
        task.setCompleteTime(null);
        updateTaskOrConflict(task);
        WorkTask updated = requireOwnedTask(context, taskId);
        insertHistory(updated, "COMPLETION_SUBMITTED", before, snapshot(updated),
                input.completionNote().strip(), context.accountId());
        audit(context, traceId, "WORK_TASK_COMPLETION_SUBMITTED", "WORK_TASK", taskId,
                Map.of("status", updated.getStatus()));
        projectTaskEvent(context, updated, "COMPLETION_SUBMITTED", traceId);
        return view(context, updated, true);
    }

    /** 验收人、任务创建人或项目管理者可以确认完成。 */
    @Transactional
    public WorkManagementModels.TaskView acceptCompletion(
            AuthenticatedContext context, Long taskId, WorkManagementModels.TaskReasonRequest input,
            String traceId) {
        requireAction(context, "UPDATE_TASK");
        WorkTask task = requireOwnedTask(context, taskId);
        requireTaskAcceptance(context, task);
        requireVersion(task, input.expectedVersion());
        if (!"WAITING_CONFIRMATION".equals(task.getStatus())) {
            throw invalid("WORK_TASK_ACCEPTANCE_STATE_INVALID", "只有待验收任务可以确认完成");
        }
        String before = toJson(snapshot(task));
        LocalDateTime now = LocalDateTime.now();
        task.setStatus("COMPLETED");
        task.setProgressPercent(new BigDecimal("100"));
        task.setCompleteTime(now);
        task.setCompletionAcceptedTime(now);
        updateTaskOrConflict(task);
        WorkTask updated = requireOwnedTask(context, taskId);
        insertHistory(updated, "COMPLETION_ACCEPTED", before, snapshot(updated), input.comment().strip(), context.accountId());
        audit(context, traceId, "WORK_TASK_COMPLETION_ACCEPTED", "WORK_TASK", taskId,
                Map.of("status", updated.getStatus()));
        projectTaskEvent(context, updated, "COMPLETION_ACCEPTED", traceId);
        return view(context, updated, true);
    }

    /** 已完成、待验收或已取消任务可以由管理者重新打开。 */
    @Transactional
    public WorkManagementModels.TaskView reopenTask(
            AuthenticatedContext context, Long taskId, WorkManagementModels.TaskReasonRequest input,
            String traceId) {
        requireAction(context, "UPDATE_TASK");
        WorkTask task = requireOwnedTask(context, taskId);
        requireTaskAdministration(context, task);
        requireVersion(task, input.expectedVersion());
        if (!Set.of("WAITING_CONFIRMATION", "COMPLETED", "CANCELLED").contains(task.getStatus())) {
            throw invalid("WORK_TASK_REOPEN_STATE_INVALID", "当前状态不能重新打开");
        }
        String before = toJson(snapshot(task));
        task.setStatus("IN_PROGRESS");
        if (task.getProgressPercent() != null && task.getProgressPercent().compareTo(new BigDecimal("100")) >= 0) {
            task.setProgressPercent(new BigDecimal("90"));
        }
        task.setCompleteTime(null);
        task.setCompletionSubmittedTime(null);
        task.setCompletionAcceptedTime(null);
        task.setArchiveTime(null);
        updateTaskOrConflict(task);
        WorkTask updated = requireOwnedTask(context, taskId);
        insertHistory(updated, "REOPENED", before, snapshot(updated), input.comment().strip(), context.accountId());
        audit(context, traceId, "WORK_TASK_REOPENED", "WORK_TASK", taskId,
                Map.of("status", updated.getStatus()));
        projectTaskEvent(context, updated, "REOPENED", traceId);
        return view(context, updated, true);
    }

    /** 归档仅接受已完成或已取消任务，归档后保持可查。 */
    @Transactional
    public WorkManagementModels.TaskView archiveTask(
            AuthenticatedContext context, Long taskId, WorkManagementModels.TaskReasonRequest input,
            String traceId) {
        requireAction(context, "UPDATE_TASK");
        WorkTask task = requireOwnedTask(context, taskId);
        requireTaskAdministration(context, task);
        requireVersion(task, input.expectedVersion());
        if (!Set.of("COMPLETED", "CANCELLED").contains(task.getStatus())) {
            throw invalid("WORK_TASK_ARCHIVE_STATE_INVALID", "只有已完成或已取消任务可以归档");
        }
        String before = toJson(snapshot(task));
        task.setStatus("ARCHIVED");
        task.setArchiveTime(LocalDateTime.now());
        updateTaskOrConflict(task);
        WorkTask updated = requireOwnedTask(context, taskId);
        insertHistory(updated, "ARCHIVED", before, snapshot(updated), input.comment().strip(), context.accountId());
        audit(context, traceId, "WORK_TASK_ARCHIVED", "WORK_TASK", taskId, Map.of());
        return view(context, updated, true);
    }

    /** 软删除隐藏任务但保留审计、评论和业务关联。 */
    @Transactional
    public void deleteTask(
            AuthenticatedContext context, Long taskId, WorkManagementModels.TaskReasonRequest input,
            String traceId) {
        requireAction(context, "UPDATE_TASK");
        WorkTask task = requireOwnedTask(context, taskId);
        requireTaskAdministration(context, task);
        requireVersion(task, input.expectedVersion());
        boolean hasChildren = taskService.selectList(Wrappers.<WorkTask>lambdaQuery()
                        .eq(WorkTask::getParentTaskId, taskId).isNull(WorkTask::getDeleteTime))
                .stream().findAny().isPresent();
        if (hasChildren) throw conflict("WORK_TASK_HAS_SUBTASKS", "请先处理子任务后再删除");
        String before = toJson(snapshot(task));
        task.setStatus("ARCHIVED");
        task.setArchiveTime(task.getArchiveTime() == null ? LocalDateTime.now() : task.getArchiveTime());
        task.setDeleteTime(LocalDateTime.now());
        updateTaskOrConflict(task);
        insertHistory(task, "DELETED", before, snapshot(task), input.comment().strip(), context.accountId());
        audit(context, traceId, "WORK_TASK_DELETED", "WORK_TASK", taskId, Map.of("softDelete", true));
    }

    @Transactional
    public WorkManagementModels.TaskView addComment(
            AuthenticatedContext context, Long taskId, WorkManagementModels.AddCommentRequest input,
            String traceId) {
        requireAction(context, "VIEW");
        WorkTask task = requireOwnedTask(context, taskId);
        requireTaskVisible(context, task);
        insertHistory(task, "COMMENTED", null, snapshot(task), input.comment().strip(), context.accountId());
        audit(context, traceId, "WORK_TASK_COMMENTED", "WORK_TASK", taskId, Map.of());
        return view(context, task, true);
    }

    @Transactional
    public WorkManagementModels.TaskView recordProgress(
            AuthenticatedContext context, Long taskId, WorkManagementModels.RecordProgressRequest input,
            String traceId) {
        requireAction(context, "UPDATE_TASK");
        WorkTask task = requireOwnedTask(context, taskId);
        requireTaskModification(context, task);
        requireVersion(task, input.expectedVersion());
        requireTaskActive(task);
        String before = toJson(snapshot(task));
        task.setProgressPercent(input.progressPercent());
        task.setActualHours(input.actualHours());
        if (Set.of("BACKLOG", "TODO").contains(task.getStatus())
                && input.progressPercent().compareTo(BigDecimal.ZERO) > 0) {
            task.setStatus("IN_PROGRESS");
        }
        updateTaskOrConflict(task);
        WorkTask updated = requireOwnedTask(context, taskId);
        insertHistory(updated, "PROGRESS_RECORDED", before, snapshot(updated),
                blankToNull(input.comment()), context.accountId());
        audit(context, traceId, "WORK_TASK_PROGRESS_RECORDED", "WORK_TASK", taskId,
                Map.of("progressPercent", input.progressPercent()));
        return view(context, updated, true);
    }

    private void projectTaskEvent(
            AuthenticatedContext context, WorkTask task, String eventType, String traceId) {
        String statusName = taskStatusName(task.getStatus());
        String eventKey = "task-" + task.getId() + "-" + eventType.toLowerCase(Locale.ROOT)
                + "-v" + task.getVersion();
        String subject = "CREATED".equals(eventType)
                ? "新任务：" + task.getTitle() : "任务已" + statusName + "：" + task.getTitle();
        // 消息会送给任务参与人；参与任务不等于获准读取关联业务记录。
        String content = "负责人和协作成员可在任务工作区查看；当前状态：" + statusName;
        messageService.notifyWorkTaskEvent(context, task, eventKey, subject, content, taskRecipientAccountIds(task));
        if (task.getBusinessType() == null || task.getBusinessId() == null) return;
        Long recordId;
        try { recordId = Long.valueOf(task.getBusinessId()); }
        catch (NumberFormatException ignored) { return; }
        LinkedHashMap<String, Object> detail = new LinkedHashMap<>();
        detail.put("taskId", task.getId());
        detail.put("taskTitle", task.getTitle());
        detail.put("taskStatus", task.getStatus());
        detail.put("taskType", task.getTaskType());
        detail.put("ownerTenantMemberId", task.getOwnerTenantMemberId());
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "CREATED".equals(eventType) ? "BUSINESS_RECORD_TASK_CREATED" : "BUSINESS_RECORD_TASK_UPDATED",
                "BUSINESS_RECORD", recordId.toString(), "SUCCESS",
                detail);
    }

    private List<Long> taskRecipientAccountIds(WorkTask task) {
        LinkedHashSet<Long> recipients = new LinkedHashSet<>();
        if (task.getOwnerAccountId() != null) recipients.add(task.getOwnerAccountId());
        taskMembers(task.getId()).stream().map(WorkTaskMember::getAccountId)
                .filter(Objects::nonNull).forEach(recipients::add);
        if (task.getCreatedByAccountId() != null) recipients.add(task.getCreatedByAccountId());
        return List.copyOf(recipients);
    }

    private String taskStatusName(String status) {
        return switch (status) {
            case "BACKLOG" -> "待规划";
            case "TODO" -> "待开始";
            case "IN_PROGRESS" -> "开始";
            case "BLOCKED" -> "阻塞";
            case "WAITING_CONFIRMATION" -> "待验收";
            case "COMPLETED" -> "完成";
            case "CANCELLED" -> "取消";
            case "ARCHIVED" -> "归档";
            default -> status;
        };
    }

    private WorkManagementModels.ProjectView view(
            AuthenticatedContext context, WorkProject project, boolean includeDetail) {
        List<WorkManagementModels.MemberView> members = includeDetail ? projectMembers(project.getId()).stream()
                .map(member -> new WorkManagementModels.MemberView(member.getId(), personView(context,
                                participantResolver.find(context, member.getTenantMemberId(), member.getAccountId())),
                        member.getProjectRole(), member.getStatus())).toList() : List.of();
        List<WorkManagementModels.TaskGroupView> groups = includeDetail ? taskGroups(project.getId()).stream()
                .map(group -> new WorkManagementModels.TaskGroupView(group.getId(), group.getName(),
                        group.getSortOrder(), group.getStatus(), nullablePersonView(context,
                                participantResolver.find(context, group.getOwnerTenantMemberId(), group.getOwnerAccountId())),
                        group.getDueTime(), group.getProgressPercent(), group.getVersion())).toList() : List.of();
        List<WorkManagementModels.TaskView> tasks = includeDetail ? projectTasks(project.getId()).stream()
                .map(task -> view(context, task, false)).toList() : List.of();
        WorkParticipantResolver.ResolvedPerson owner = participantResolver.find(
                context, project.getOwnerTenantMemberId(), project.getOwnerAccountId());
        return new WorkManagementModels.ProjectView(project.getId(), project.getContextType(), project.getPlatformId(),
                project.getSystemId(), project.getTenantId(), project.getCode(), project.getName(), project.getDescription(),
                project.getCustomerName(), project.getInternalDepartmentName(), project.getStage(),
                stringList(project.getLabelsJson()), project.getBusinessType(), project.getBusinessId(),
                project.getBusinessTitle(), personView(context, owner), project.getStartDate(), project.getDueDate(), project.getProgressPercent(),
                project.getStatus(), project.getVersion(), members, groups, tasks);
    }

    private WorkManagementModels.TaskView view(
            AuthenticatedContext context, WorkTask task, boolean includeHistory) {
        // 任务参与资格不扩大关联记录的数据权限；每次展示都按当前身份重查记录。
        String visibleBusinessTitle = null;
        boolean businessVisible = false;
        if (task.getBusinessType() != null && task.getBusinessType().startsWith("MODULE:")
                && task.getBusinessId() != null) {
            try {
                var record = runtimeTargetAccessReader.detail(context, task.getBusinessType().substring(7),
                        Long.valueOf(task.getBusinessId()), "work-task-view");
                visibleBusinessTitle = record.title();
                businessVisible = true;
            } catch (DomainException | NumberFormatException ignored) {
                // 记录已删除、权限被撤销或上下文不符时只展示任务自身内容。
            }
        }
        List<WorkTaskMember> taskMembers = taskMembers(task.getId());
        List<WorkManagementModels.PersonView> collaborators = peopleOfType(context, taskMembers, "COLLABORATOR");
        List<WorkManagementModels.PersonView> followers = peopleOfType(context, taskMembers, "FOLLOWER");
        List<WorkManagementModels.PersonView> acceptors = peopleOfType(context, taskMembers, "ACCEPTOR");
        boolean showBusiness = businessVisible;
        List<WorkManagementModels.HistoryView> history = includeHistory ? histories(task.getId()).stream()
                .map(item -> new WorkManagementModels.HistoryView(item.getId(), item.getActionCode(),
                        businessSnapshot(fromJson(item.getBeforeJson()), showBusiness),
                        businessSnapshot(fromJson(item.getAfterJson()), showBusiness), item.getCommentText(),
                        displayName(context, null, item.getChangedByAccountId()), item.getChangeTime())).toList() : List.of();
        WorkParticipantResolver.ResolvedPerson owner = participantResolver.find(
                context, task.getOwnerTenantMemberId(), task.getOwnerAccountId());
        List<WorkManagementModels.LinkedLogView> linkedLogs = includeHistory
                ? linkedLogs(context, task.getId()) : List.of();
        List<WorkManagementModels.TaskView> subtasks = includeHistory
                ? childTasks(task.getId()).stream().map(child -> view(context, child, false)).toList()
                : List.of();
        boolean overdue = isOverdue(task);
        return new WorkManagementModels.TaskView(task.getId(), task.getContextType(), task.getPlatformId(),
                task.getSystemId(), task.getTenantId(), task.getProjectId(), task.getTaskGroupId(), task.getParentTaskId(),
                task.getTitle(), task.getDescription(), task.getTaskType(), task.getPriority(), task.getStatus(),
                overdue ? "已逾期" : taskStatusName(task.getStatus()), overdue, task.getProgressPercent(),
                task.getEstimatedHours(), task.getActualHours(), stringList(task.getLabelsJson()),
                checklist(task.getChecklistJson()), personView(context, owner), task.getStartTime(), task.getDueTime(),
                task.getCompleteTime(), task.getCompletionNote(), task.getCompletionSubmittedTime(),
                task.getCompletionAcceptedTime(), task.getArchiveTime(),
                businessVisible ? task.getBusinessType() : null, businessVisible ? task.getBusinessId() : null,
                visibleBusinessTitle, fromJson(task.getCustomValuesJson()), task.getVersion(), collaborators,
                followers, acceptors, history, linkedLogs, subtasks, taskActions(context, task, taskMembers));
    }

    /** 历史快照同样不能绕过当前业务记录权限泄露关联标识和标题。 */
    private Map<String, Object> businessSnapshot(Map<String, Object> snapshot, boolean businessVisible) {
        if (businessVisible || snapshot.isEmpty()) return snapshot;
        Map<String, Object> visible = new LinkedHashMap<>(snapshot);
        visible.remove("businessType");
        visible.remove("businessId");
        visible.remove("businessTitle");
        return visible;
    }

    private List<WorkManagementModels.LinkedLogView> linkedLogs(
            AuthenticatedContext context, Long taskId) {
        List<Long> logIds = workLogTaskLinkService.selectList(Wrappers.<WorkLogTaskLink>lambdaQuery()
                        .eq(WorkLogTaskLink::getTaskId, taskId)
                        .orderByDesc(WorkLogTaskLink::getId))
                .stream().map(WorkLogTaskLink::getWorkLogId).toList();
        if (logIds.isEmpty()) return List.of();
        return workLogService.selectList(Wrappers.<WorkLog>lambdaQuery()
                        .in(WorkLog::getId, logIds)
                        .orderByDesc(WorkLog::getWorkDate, WorkLog::getUpdateTime))
                .stream()
                .filter(log -> Objects.equals(log.getPlatformId(), context.platformId()))
                .filter(log -> Objects.equals(log.getSystemId(), context.systemId()))
                .filter(log -> Objects.equals(log.getTenantId(), context.systemId() == null ? null : context.tenantId()))
                .map(log -> new WorkManagementModels.LinkedLogView(
                        log.getId(), log.getWorkDate(), log.getTitle(), log.getContentText(),
                        log.getDurationMinutes(), log.getStatus(),
                        displayName(context, log.getAuthorTenantMemberId(), log.getAuthorAccountId()),
                        log.getUpdateTime()))
                .toList();
    }

    private void validateTransition(String current, String next) {
        if (!TASK_STATUSES.contains(next)) {
            throw invalid("WORK_TASK_STATUS_INVALID", "任务状态无效");
        }
        if (Objects.equals(current, next)) {
            return;
        }
        if (!TASK_TRANSITIONS.getOrDefault(current, Set.of()).contains(next)) {
            throw invalid("WORK_TASK_TRANSITION_INVALID", "任务不能从 " + current + " 直接变更为 " + next);
        }
    }

    private void requireTaskModification(AuthenticatedContext context, WorkTask task) {
        String role = task.getProjectId() == null ? null : projectRole(task.getProjectId(), context.accountId());
        boolean projectManager = role != null && Set.of("OWNER", "MANAGER").contains(role);
        boolean participant = Objects.equals(task.getOwnerAccountId(), context.accountId())
                || taskMembers(task.getId()).stream().anyMatch(member -> "COLLABORATOR".equals(member.getMemberType())
                && Objects.equals(member.getAccountId(), context.accountId()));
        if (!projectManager && !participant) {
            throw forbidden("WORK_TASK_MODIFICATION_DENIED", "你不是该任务的负责人或协作成员");
        }
    }

    private void requireTaskAdministration(AuthenticatedContext context, WorkTask task) {
        String role = task.getProjectId() == null ? null : projectRole(task.getProjectId(), context.accountId());
        boolean allowed = Objects.equals(task.getCreatedByAccountId(), context.accountId())
                || Objects.equals(task.getOwnerAccountId(), context.accountId())
                || role != null && Set.of("OWNER", "MANAGER").contains(role);
        if (!allowed) throw forbidden("WORK_TASK_ADMINISTRATION_DENIED", "当前成员不能管理该任务的负责人或生命周期");
    }

    private void requireTaskAcceptance(AuthenticatedContext context, WorkTask task) {
        String role = task.getProjectId() == null ? null : projectRole(task.getProjectId(), context.accountId());
        boolean allowed = Objects.equals(task.getCreatedByAccountId(), context.accountId())
                || role != null && Set.of("OWNER", "MANAGER").contains(role)
                || taskMembers(task.getId()).stream().anyMatch(member -> "ACCEPTOR".equals(member.getMemberType())
                && Objects.equals(member.getAccountId(), context.accountId()));
        if (!allowed) throw forbidden("WORK_TASK_ACCEPTANCE_DENIED", "你不是该任务的验收人");
    }

    private void requireTaskVisible(AuthenticatedContext context, WorkTask task) {
        if (task.getProjectId() == null) {
            if (!taskVisibleTo(context, task)) {
                throw forbidden("WORK_TASK_VIEW_DENIED", "你不是该任务的负责人或协作成员");
            }
            return;
        }
        if (!canViewProject(context, requireOwned(context, task.getProjectId()))) {
            throw forbidden("PROJECT_ROLE_DENIED", "你不是该任务所属项目的有效成员");
        }
    }

    private void requireProjectRole(AuthenticatedContext context, Long projectId, Set<String> allowed) {
        String role = projectRole(projectId, context.accountId());
        if (!allowed.contains(role)) {
            throw forbidden("PROJECT_ROLE_DENIED", "当前项目角色不允许执行该操作");
        }
    }

    private String projectRole(Long projectId, Long accountId) {
        return projectMembers(projectId).stream()
                .filter(member -> Objects.equals(member.getAccountId(), accountId) && "ACTIVE".equals(member.getStatus()))
                .map(WorkProjectMember::getProjectRole).findFirst().orElse(null);
    }

    private void requireProjectMember(Long projectId, Long accountId) {
        if (projectRole(projectId, accountId) == null) {
            throw invalid("TASK_ASSIGNEE_NOT_PROJECT_MEMBER", "负责人必须是当前项目的有效成员");
        }
    }

    private boolean canViewProject(AuthenticatedContext context, WorkProject project) {
        return projectRole(project.getId(), context.accountId()) != null;
    }

    private List<WorkParticipantResolver.ResolvedPerson> normalizeCollaborators(
            AuthenticatedContext context, Long projectId, List<Long> tenantMemberIds,
            List<Long> compatibilityAccountIds, WorkParticipantResolver.ResolvedPerson owner) {
        LinkedHashMap<Long, WorkParticipantResolver.ResolvedPerson> result = new LinkedHashMap<>();
        if (tenantMemberIds != null) {
            for (Long tenantMemberId : tenantMemberIds) {
                if (tenantMemberId == null) continue;
                WorkParticipantResolver.ResolvedPerson person = participantResolver.require(context, tenantMemberId, null);
                addCollaborator(projectId, owner, result, person);
            }
        }
        if (compatibilityAccountIds != null) {
            for (Long accountId : compatibilityAccountIds) {
                if (accountId == null) continue;
                WorkParticipantResolver.ResolvedPerson person = participantResolver.require(context, null, accountId);
                addCollaborator(projectId, owner, result, person);
            }
        }
        return List.copyOf(result.values());
    }

    private List<WorkParticipantResolver.ResolvedPerson> normalizeParticipants(
            AuthenticatedContext context, Long projectId, List<Long> tenantMemberIds,
            WorkParticipantResolver.ResolvedPerson owner,
            List<WorkParticipantResolver.ResolvedPerson> excluded) {
        if (tenantMemberIds == null) return List.of();
        Set<Long> excludedAccounts = new HashSet<>();
        excludedAccounts.add(owner.accountId());
        excluded.stream().map(WorkParticipantResolver.ResolvedPerson::accountId).forEach(excludedAccounts::add);
        LinkedHashMap<Long, WorkParticipantResolver.ResolvedPerson> result = new LinkedHashMap<>();
        for (Long tenantMemberId : tenantMemberIds) {
            if (tenantMemberId == null) continue;
            WorkParticipantResolver.ResolvedPerson person = participantResolver.require(context, tenantMemberId, null);
            if (projectId != null) requireProjectMember(projectId, person.accountId());
            if (!excludedAccounts.contains(person.accountId())) result.put(person.accountId(), person);
        }
        return List.copyOf(result.values());
    }

    private void addCollaborator(
            Long projectId, WorkParticipantResolver.ResolvedPerson owner,
            Map<Long, WorkParticipantResolver.ResolvedPerson> result,
            WorkParticipantResolver.ResolvedPerson person) {
        if (Objects.equals(person.accountId(), owner.accountId())) return;
        if (projectId != null) requireProjectMember(projectId, person.accountId());
        result.put(person.accountId(), person);
    }

    private void replaceTaskMembers(
            Long taskId, List<WorkParticipantResolver.ResolvedPerson> collaborators,
            List<WorkParticipantResolver.ResolvedPerson> followers,
            List<WorkParticipantResolver.ResolvedPerson> acceptors) {
        for (WorkTaskMember member : taskMembers(taskId)) {
            taskMemberService.deleteById(member.getId());
        }
        insertTaskMembers(taskId, collaborators, "COLLABORATOR");
        insertTaskMembers(taskId, followers, "FOLLOWER");
        insertTaskMembers(taskId, acceptors, "ACCEPTOR");
    }

    private void insertTaskMembers(
            Long taskId, List<WorkParticipantResolver.ResolvedPerson> people, String memberType) {
        for (WorkParticipantResolver.ResolvedPerson person : people) {
            WorkTaskMember member = new WorkTaskMember();
            member.setTaskId(taskId);
            member.setAccountId(person.accountId());
            member.setTenantMemberId(person.tenantMemberId());
            member.setMemberType(memberType);
            taskMemberService.insert(member);
        }
    }

    private void insertProjectMember(
            Long projectId, WorkParticipantResolver.ResolvedPerson person, String role) {
        WorkProjectMember member = new WorkProjectMember();
        member.setProjectId(projectId);
        member.setAccountId(person.accountId());
        member.setTenantMemberId(person.tenantMemberId());
        member.setProjectRole(role);
        member.setStatus("ACTIVE");
        projectMemberService.insert(member);
    }

    private void replaceProjectMembers(
            AuthenticatedContext context, WorkProject project,
            List<WorkManagementModels.ProjectMemberInput> inputs) {
        WorkParticipantResolver.ResolvedPerson owner = participantResolver.find(
                context, project.getOwnerTenantMemberId(), project.getOwnerAccountId());
        LinkedHashMap<Long, MemberAssignment> desired = new LinkedHashMap<>();
        desired.put(project.getOwnerAccountId(), new MemberAssignment(owner, "OWNER"));
        for (WorkManagementModels.ProjectMemberInput input : inputs) {
            WorkParticipantResolver.ResolvedPerson person = participantResolver.require(
                    context, input.tenantMemberId(), input.compatibilityAccountId());
            String role = input.projectRole().strip().toUpperCase(Locale.ROOT);
            if (!PROJECT_ROLES.contains(role) || "OWNER".equals(role)
                    && !Objects.equals(person.accountId(), project.getOwnerAccountId())) {
                throw invalid("PROJECT_ROLE_INVALID", "项目成员角色无效");
            }
            desired.put(person.accountId(), new MemberAssignment(person, role));
        }
        for (WorkProjectMember member : projectMembers(project.getId())) {
            projectMemberService.deleteById(member.getId());
        }
        desired.values().forEach(member -> insertProjectMember(project.getId(), member.person(), member.role()));
    }

    private void insertHistory(WorkTask task, String action, String before, Map<String, Object> after,
                               String comment, Long actorAccountId) {
        WorkTaskHistory history = new WorkTaskHistory();
        history.setTaskId(task.getId());
        history.setActionCode(action);
        history.setBeforeJson(before);
        history.setAfterJson(toJson(after));
        history.setCommentText(comment);
        history.setChangedByAccountId(actorAccountId);
        taskHistoryService.insert(history);
    }

    private Map<String, Object> snapshot(WorkTask task) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", task.getId());
        result.put("projectId", task.getProjectId());
        result.put("taskGroupId", task.getTaskGroupId());
        result.put("parentTaskId", task.getParentTaskId());
        result.put("title", task.getTitle());
        result.put("status", task.getStatus());
        result.put("priority", task.getPriority());
        result.put("progressPercent", task.getProgressPercent());
        result.put("estimatedHours", task.getEstimatedHours());
        result.put("actualHours", task.getActualHours());
        result.put("labels", stringList(task.getLabelsJson()));
        result.put("checklist", checklist(task.getChecklistJson()));
        result.put("ownerTenantMemberId", task.getOwnerTenantMemberId());
        result.put("startAt", task.getStartTime());
        result.put("dueAt", task.getDueTime());
        result.put("completedAt", task.getCompleteTime());
        result.put("completionNote", task.getCompletionNote());
        result.put("completionSubmittedTime", task.getCompletionSubmittedTime());
        result.put("completionAcceptedTime", task.getCompletionAcceptedTime());
        result.put("archiveTime", task.getArchiveTime());
        result.put("businessType", task.getBusinessType());
        result.put("businessId", task.getBusinessId());
        result.put("businessTitle", task.getBusinessTitle());
        result.put("configuredValues", fromJson(task.getCustomValuesJson()));
        result.put("version", task.getVersion());
        return result;
    }

    private BusinessReference validateBusinessReference(
            AuthenticatedContext context, String businessType, String businessId,
            String suppliedTitle, String traceId) {
        String type = blankToNull(businessType);
        String id = blankToNull(businessId);
        if (type == null && id == null) return new BusinessReference(null, null, null);
        if (type == null || id == null) {
            throw invalid("WORK_BUSINESS_REFERENCE_INCOMPLETE", "关联业务对象必须同时选择模块和记录");
        }
        String moduleCode = type.startsWith("MODULE:") ? type.substring(7) : type;
        Long recordId;
        try {
            recordId = Long.valueOf(id);
        } catch (NumberFormatException exception) {
            throw invalid("WORK_BUSINESS_REFERENCE_INVALID", "关联业务记录标识无效");
        }
        var record = runtimeDataService.detail(context, moduleCode, recordId, traceId);
        String title = record.title() == null || record.title().isBlank()
                ? blankToNull(suppliedTitle) : record.title();
        return new BusinessReference("MODULE:" + moduleCode, String.valueOf(record.id()), title);
    }

    private WorkManagementModels.PersonView personView(
            AuthenticatedContext context, WorkParticipantResolver.ResolvedPerson person) {
        if (person == null) return new WorkManagementModels.PersonView(null, "已失效成员", null, null);
        return new WorkManagementModels.PersonView(person.tenantMemberId(), person.displayName(),
                person.departmentName(), person.positionTitle());
    }

    private String displayName(AuthenticatedContext context, Long tenantMemberId, Long accountId) {
        WorkParticipantResolver.ResolvedPerson person = participantResolver.find(context, tenantMemberId, accountId);
        return person == null ? "已失效成员" : person.displayName();
    }

    private String taskStatus(String json) {
        return String.valueOf(fromJson(json).get("status"));
    }

    private List<WorkProject> scopedProjects(AuthenticatedContext context) {
        var query = Wrappers.<WorkProject>lambdaQuery()
                .eq(WorkProject::getContextType, context.systemId() == null ? "PLATFORM" : "SYSTEM")
                .eq(WorkProject::getPlatformId, context.platformId());
        if (context.systemId() == null) {
            query.isNull(WorkProject::getSystemId).isNull(WorkProject::getTenantId);
        } else {
            query.eq(WorkProject::getSystemId, context.systemId()).eq(WorkProject::getTenantId, context.tenantId());
        }
        return projectService.selectList(query);
    }

    private List<WorkTask> scopedOrdinaryTasks(AuthenticatedContext context) {
        var query = Wrappers.<WorkTask>lambdaQuery()
                .eq(WorkTask::getContextType, context.systemId() == null ? "PLATFORM" : "SYSTEM")
                .eq(WorkTask::getPlatformId, context.platformId())
                .isNull(WorkTask::getProjectId)
                .isNull(WorkTask::getDeleteTime);
        if (context.systemId() == null) {
            query.isNull(WorkTask::getSystemId).isNull(WorkTask::getTenantId);
        } else {
            query.eq(WorkTask::getSystemId, context.systemId()).eq(WorkTask::getTenantId, context.tenantId());
        }
        return taskService.selectList(query);
    }

    private boolean taskVisibleTo(AuthenticatedContext context, WorkTask task) {
        return Objects.equals(task.getOwnerAccountId(), context.accountId())
                || taskMembers(task.getId()).stream()
                .anyMatch(member -> Objects.equals(member.getAccountId(), context.accountId()));
    }

    private WorkProject requireOwned(AuthenticatedContext context, Long id) {
        WorkProject project = projectService.selectById(id);
        if (project == null || !owned(context, project)) {
            throw new DomainException("WORK_PROJECT_NOT_FOUND", "项目不存在或不在当前范围", HttpStatus.NOT_FOUND);
        }
        return project;
    }

    private WorkTask requireOwnedTask(AuthenticatedContext context, Long id) {
        WorkTask task = taskService.selectById(id);
        if (task == null || task.getDeleteTime() != null || !owned(context, task)) {
            throw new DomainException("WORK_TASK_NOT_FOUND", "任务不存在或不在当前范围", HttpStatus.NOT_FOUND);
        }
        return task;
    }

    private boolean owned(AuthenticatedContext context, WorkProject project) {
        if (!Objects.equals(project.getPlatformId(), context.platformId())) {
            return false;
        }
        return context.systemId() == null
                ? "PLATFORM".equals(project.getContextType()) && project.getSystemId() == null && project.getTenantId() == null
                : "SYSTEM".equals(project.getContextType()) && Objects.equals(project.getSystemId(), context.systemId())
                && Objects.equals(project.getTenantId(), context.tenantId());
    }

    private boolean owned(AuthenticatedContext context, WorkTask task) {
        if (!Objects.equals(task.getPlatformId(), context.platformId())) {
            return false;
        }
        return context.systemId() == null
                ? "PLATFORM".equals(task.getContextType()) && task.getSystemId() == null && task.getTenantId() == null
                : "SYSTEM".equals(task.getContextType()) && Objects.equals(task.getSystemId(), context.systemId())
                && Objects.equals(task.getTenantId(), context.tenantId());
    }

    private void bindContext(WorkProject project, AuthenticatedContext context) {
        project.setContextType(context.systemId() == null ? "PLATFORM" : "SYSTEM");
        project.setPlatformId(context.platformId());
        project.setSystemId(context.systemId());
        project.setTenantId(context.systemId() == null ? null : context.tenantId());
    }

    private void bindContext(WorkTask task, AuthenticatedContext context) {
        task.setContextType(context.systemId() == null ? "PLATFORM" : "SYSTEM");
        task.setPlatformId(context.platformId());
        task.setSystemId(context.systemId());
        task.setTenantId(context.systemId() == null ? null : context.tenantId());
    }

    private List<WorkProjectMember> projectMembers(Long projectId) {
        return projectMemberService.selectList(Wrappers.<WorkProjectMember>lambdaQuery()
                .eq(WorkProjectMember::getProjectId, projectId).orderByAsc(WorkProjectMember::getId));
    }

    private List<WorkTaskGroup> taskGroups(Long projectId) {
        return taskGroupService.selectList(Wrappers.<WorkTaskGroup>lambdaQuery()
                .eq(WorkTaskGroup::getProjectId, projectId).orderByAsc(WorkTaskGroup::getSortOrder));
    }

    private List<WorkTask> projectTasks(Long projectId) {
        return taskService.selectList(Wrappers.<WorkTask>lambdaQuery()
                .eq(WorkTask::getProjectId, projectId).isNull(WorkTask::getDeleteTime)
                .orderByDesc(WorkTask::getUpdateTime));
    }

    private List<WorkTask> childTasks(Long taskId) {
        return taskService.selectList(Wrappers.<WorkTask>lambdaQuery()
                .eq(WorkTask::getParentTaskId, taskId).isNull(WorkTask::getDeleteTime)
                .orderByAsc(WorkTask::getId));
    }

    private List<WorkTaskMember> taskMembers(Long taskId) {
        return taskMemberService.selectList(Wrappers.<WorkTaskMember>lambdaQuery()
                .eq(WorkTaskMember::getTaskId, taskId).orderByAsc(WorkTaskMember::getId));
    }

    private List<WorkTaskHistory> histories(Long taskId) {
        return taskHistoryService.selectList(Wrappers.<WorkTaskHistory>lambdaQuery()
                .eq(WorkTaskHistory::getTaskId, taskId).orderByDesc(WorkTaskHistory::getChangeTime));
    }

    private void requireAction(AuthenticatedContext context, String action) {
        if (context == null || context.platformId() == null) {
            throw new DomainException("AUTHENTICATION_REQUIRED", "请先登录", HttpStatus.UNAUTHORIZED);
        }
        String resourceCode = context.systemId() == null ? "PLATFORM" : "SYSTEM";
        if (!permissionChecker.allows(context, "WORK", resourceCode, action)
                && !permissionChecker.allows(context, "WORK", "*", action)) {
            throw forbidden("PERMISSION_DENIED", "没有工作管理的 " + action + " 权限");
        }
    }

    private void requireValidDates(java.time.LocalDate start, java.time.LocalDate due) {
        if (start != null && due != null && due.isBefore(start)) {
            throw invalid("PROJECT_DATE_INVALID", "项目截止日期不能早于开始日期");
        }
    }

    private void requireValidTimes(LocalDateTime start, LocalDateTime due) {
        if (start != null && due != null && due.isBefore(start)) {
            throw invalid("TASK_DATE_INVALID", "任务截止时间不能早于开始时间");
        }
    }

    private void requireVersion(WorkTask task, Integer expectedVersion) {
        if (!Objects.equals(task.getVersion(), expectedVersion)) {
            throw conflict("WORK_TASK_VERSION_CONFLICT", "任务已被其他人修改，请刷新后重试");
        }
    }

    private void updateTaskOrConflict(WorkTask task) {
        if (taskService.updateById(task) != 1) {
            throw conflict("WORK_TASK_VERSION_CONFLICT", "任务已被其他人修改，请刷新后重试");
        }
    }

    private void requireTaskActive(WorkTask task) {
        if (Set.of("WAITING_CONFIRMATION", "COMPLETED", "CANCELLED", "ARCHIVED").contains(task.getStatus())) {
            throw invalid("WORK_TASK_NOT_ACTIVE", "当前任务状态不能执行该操作");
        }
    }

    private List<WorkManagementModels.PersonView> peopleOfType(
            AuthenticatedContext context, List<WorkTaskMember> members, String memberType) {
        return members.stream().filter(member -> memberType.equals(member.getMemberType()))
                .map(member -> participantResolver.find(context, member.getTenantMemberId(), member.getAccountId()))
                .filter(Objects::nonNull).map(person -> personView(context, person)).toList();
    }

    private List<WorkParticipantResolver.ResolvedPerson> resolvedOfType(
            AuthenticatedContext context, List<WorkTaskMember> members, String memberType) {
        return members.stream().filter(member -> memberType.equals(member.getMemberType()))
                .map(member -> participantResolver.find(context, member.getTenantMemberId(), member.getAccountId()))
                .filter(Objects::nonNull).toList();
    }

    private WorkManagementModels.PersonView nullablePersonView(
            AuthenticatedContext context, WorkParticipantResolver.ResolvedPerson person) {
        return person == null ? null : personView(context, person);
    }

    private boolean isOverdue(WorkTask task) {
        return task.getDueTime() != null && task.getDueTime().isBefore(LocalDateTime.now())
                && !Set.of("COMPLETED", "CANCELLED", "ARCHIVED").contains(task.getStatus());
    }

    private WorkManagementModels.TaskActionsView taskActions(
            AuthenticatedContext context, WorkTask task, List<WorkTaskMember> members) {
        String projectRole = task.getProjectId() == null ? null : projectRole(task.getProjectId(), context.accountId());
        boolean manager = projectRole != null && Set.of("OWNER", "MANAGER").contains(projectRole);
        boolean owner = Objects.equals(task.getOwnerAccountId(), context.accountId());
        boolean creator = Objects.equals(task.getCreatedByAccountId(), context.accountId());
        boolean collaborator = members.stream().anyMatch(member -> "COLLABORATOR".equals(member.getMemberType())
                && Objects.equals(member.getAccountId(), context.accountId()));
        boolean acceptor = members.stream().anyMatch(member -> "ACCEPTOR".equals(member.getMemberType())
                && Objects.equals(member.getAccountId(), context.accountId()));
        boolean active = !Set.of("WAITING_CONFIRMATION", "COMPLETED", "CANCELLED", "ARCHIVED")
                .contains(task.getStatus());
        boolean administrator = manager || creator || owner;
        return new WorkManagementModels.TaskActionsView(
                active && (manager || owner || collaborator),
                active && administrator,
                active && (manager || owner || collaborator),
                Set.of("TODO", "IN_PROGRESS", "BLOCKED").contains(task.getStatus())
                        && (manager || owner || collaborator),
                "WAITING_CONFIRMATION".equals(task.getStatus()) && (manager || creator || acceptor),
                Set.of("WAITING_CONFIRMATION", "COMPLETED", "CANCELLED").contains(task.getStatus()) && administrator,
                Set.of("COMPLETED", "CANCELLED").contains(task.getStatus()) && administrator,
                administrator,
                true);
    }

    private String normalizeCode(String code) {
        String normalized = code.strip().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z][a-z0-9_-]{1,99}")) {
            throw invalid("WORK_PROJECT_CODE_INVALID", "项目编码需以字母开头，只能包含小写字母、数字、下划线和横线");
        }
        return normalized;
    }

    private String normalizePriority(String priority) {
        String normalized = priority.strip().toUpperCase(Locale.ROOT);
        if (!Set.of("LOW", "NORMAL", "HIGH", "URGENT").contains(normalized)) {
            throw invalid("TASK_PRIORITY_INVALID", "任务优先级无效");
        }
        return normalized;
    }

    private void audit(AuthenticatedContext context, String traceId, String eventCode,
                       String objectType, Object objectId, Map<String, ?> detail) {
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                eventCode, objectType, String.valueOf(objectId), "SUCCESS", detail);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private List<String> normalizeLabels(List<String> labels) {
        if (labels == null) return List.of();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String label : labels) {
            String normalized = blankToNull(label);
            if (normalized != null) result.add(normalized);
            if (result.size() > 20) throw invalid("WORK_LABEL_LIMIT_EXCEEDED", "标签最多 20 个");
        }
        return List.copyOf(result);
    }

    private List<WorkManagementModels.ChecklistItemView> normalizeChecklist(
            List<WorkManagementModels.ChecklistItemInput> items) {
        if (items == null) return List.of();
        if (items.size() > 100) throw invalid("WORK_CHECKLIST_LIMIT_EXCEEDED", "检查清单最多 100 项");
        List<WorkManagementModels.ChecklistItemView> result = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (WorkManagementModels.ChecklistItemInput item : items) {
            String id = blankToNull(item.id());
            if (id == null) id = UUID.randomUUID().toString();
            if (!ids.add(id)) throw invalid("WORK_CHECKLIST_ID_DUPLICATED", "检查清单存在重复项");
            result.add(new WorkManagementModels.ChecklistItemView(id, item.text().strip(), item.completed()));
        }
        return List.copyOf(result);
    }

    private String nullableJson(Map<String, Object> value) {
        return value == null || value.isEmpty() ? null : toJson(value);
    }

    private String nullableListJson(List<?> value) {
        return value == null || value.isEmpty() ? null : toJson(value);
    }

    private List<String> stringList(String value) {
        if (value == null || value.isBlank()) return List.of();
        try {
            return objectMapper.readValue(value, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot deserialize work label data", exception);
        }
    }

    private List<WorkManagementModels.ChecklistItemView> checklist(String value) {
        if (value == null || value.isBlank()) return List.of();
        try {
            return objectMapper.readValue(value, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot deserialize work checklist data", exception);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize work data", exception);
        }
    }

    private Map<String, Object> fromJson(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot deserialize work data", exception);
        }
    }

    private DomainException invalid(String code, String message) {
        return new DomainException(code, message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private DomainException conflict(String code, String message) {
        return new DomainException(code, message, HttpStatus.CONFLICT);
    }

    private DomainException forbidden(String code, String message) {
        return new DomainException(code, message, HttpStatus.FORBIDDEN);
    }

    private record MemberAssignment(WorkParticipantResolver.ResolvedPerson person, String role) {
    }

    private record BusinessReference(String type, String id, String title) {
    }
}
