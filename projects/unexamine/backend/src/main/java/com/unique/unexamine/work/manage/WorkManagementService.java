package com.unique.unexamine.work.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.authorization.manage.PermissionChecker;
import com.unique.unexamine.platform.base.entity.PlatformAccount;
import com.unique.unexamine.platform.base.service.PlatformAccountBaseService;
import com.unique.unexamine.shared.manage.web.DomainException;
import com.unique.unexamine.system.base.entity.SystemMember;
import com.unique.unexamine.system.base.entity.SystemTenantMember;
import com.unique.unexamine.system.base.service.SystemMemberBaseService;
import com.unique.unexamine.system.base.service.SystemTenantMemberBaseService;
import com.unique.unexamine.work.base.entity.WorkProject;
import com.unique.unexamine.work.base.entity.WorkProjectMember;
import com.unique.unexamine.work.base.entity.WorkTask;
import com.unique.unexamine.work.base.entity.WorkTaskGroup;
import com.unique.unexamine.work.base.entity.WorkTaskHistory;
import com.unique.unexamine.work.base.entity.WorkTaskMember;
import com.unique.unexamine.work.base.service.WorkProjectBaseService;
import com.unique.unexamine.work.base.service.WorkProjectMemberBaseService;
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

@Service
public class WorkManagementService {
    private static final Set<String> PROJECT_ROLES = Set.of("OWNER", "MANAGER", "MEMBER", "OBSERVER");
    private static final Set<String> PROJECT_STATUSES = Set.of("PLANNED", "ACTIVE", "PAUSED", "COMPLETED", "CANCELLED");
    private static final Set<String> TASK_STATUSES = Set.of("BACKLOG", "TODO", "IN_PROGRESS", "BLOCKED", "COMPLETED", "CANCELLED");
    private static final Map<String, Set<String>> TASK_TRANSITIONS = Map.of(
            "BACKLOG", Set.of("TODO", "CANCELLED"),
            "TODO", Set.of("IN_PROGRESS", "CANCELLED"),
            "IN_PROGRESS", Set.of("BLOCKED", "COMPLETED", "CANCELLED"),
            "BLOCKED", Set.of("IN_PROGRESS", "CANCELLED"),
            "COMPLETED", Set.of("IN_PROGRESS"),
            "CANCELLED", Set.of("TODO"));

    private final WorkProjectBaseService projectService;
    private final WorkProjectMemberBaseService projectMemberService;
    private final WorkTaskGroupBaseService taskGroupService;
    private final WorkTaskBaseService taskService;
    private final WorkTaskMemberBaseService taskMemberService;
    private final WorkTaskHistoryBaseService taskHistoryService;
    private final PlatformAccountBaseService accountService;
    private final SystemMemberBaseService systemMemberService;
    private final SystemTenantMemberBaseService tenantMemberService;
    private final PermissionChecker permissionChecker;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;

    public WorkManagementService(
            WorkProjectBaseService projectService,
            WorkProjectMemberBaseService projectMemberService,
            WorkTaskGroupBaseService taskGroupService,
            WorkTaskBaseService taskService,
            WorkTaskMemberBaseService taskMemberService,
            WorkTaskHistoryBaseService taskHistoryService,
            PlatformAccountBaseService accountService,
            SystemMemberBaseService systemMemberService,
            SystemTenantMemberBaseService tenantMemberService,
            PermissionChecker permissionChecker,
            AuditRecorder auditRecorder,
            ObjectMapper objectMapper) {
        this.projectService = projectService;
        this.projectMemberService = projectMemberService;
        this.taskGroupService = taskGroupService;
        this.taskService = taskService;
        this.taskMemberService = taskMemberService;
        this.taskHistoryService = taskHistoryService;
        this.accountService = accountService;
        this.systemMemberService = systemMemberService;
        this.tenantMemberService = tenantMemberService;
        this.permissionChecker = permissionChecker;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<WorkManagementModels.ProjectView> projects(AuthenticatedContext context) {
        requireAction(context, "VIEW");
        return scopedProjects(context).stream()
                .filter(project -> canViewProject(context, project))
                .sorted(Comparator.comparing(WorkProject::getUpdatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(project -> view(project, false)).toList();
    }

    @Transactional
    public WorkManagementModels.ProjectView createProject(
            AuthenticatedContext context, WorkManagementModels.CreateProjectRequest input, String traceId) {
        requireAction(context, "CREATE_PROJECT");
        requireValidDates(input.startDate(), input.dueDate());
        String code = normalizeCode(input.code());
        if (scopedProjects(context).stream().anyMatch(project -> code.equals(project.getCode()))) {
            throw conflict("WORK_PROJECT_CODE_CONFLICT", "当前范围已存在相同项目编码");
        }
        requireAssignable(context, context.accountId());
        LinkedHashMap<Long, String> members = new LinkedHashMap<>();
        members.put(context.accountId(), "OWNER");
        if (input.members() != null) {
            for (WorkManagementModels.ProjectMemberInput member : input.members()) {
                String role = member.projectRole().strip().toUpperCase(Locale.ROOT);
                if (!PROJECT_ROLES.contains(role) || "OWNER".equals(role) && !Objects.equals(member.accountId(), context.accountId())) {
                    throw invalid("PROJECT_ROLE_INVALID", "项目成员角色无效");
                }
                requireAssignable(context, member.accountId());
                members.put(member.accountId(), role);
            }
        }
        WorkProject project = new WorkProject();
        bindContext(project, context);
        project.setCode(code);
        project.setName(input.name().strip());
        project.setDescription(blankToNull(input.description()));
        project.setOwnerAccountId(context.accountId());
        project.setStartDate(input.startDate());
        project.setDueDate(input.dueDate());
        project.setProgressPercent(BigDecimal.ZERO);
        project.setStatus("PLANNED");
        project.setVersion(0);
        projectService.insert(project);
        members.forEach((accountId, role) -> insertProjectMember(project.getId(), accountId, role));
        audit(context, traceId, "WORK_PROJECT_CREATED", "WORK_PROJECT", project.getId(),
                Map.of("code", code, "memberCount", members.size()));
        return view(project, true);
    }

    @Transactional(readOnly = true)
    public WorkManagementModels.ProjectView project(AuthenticatedContext context, Long projectId) {
        requireAction(context, "VIEW");
        WorkProject project = requireOwned(context, projectId);
        if (!canViewProject(context, project)) {
            throw forbidden("PROJECT_ROLE_DENIED", "你不是该项目的有效成员");
        }
        return view(project, true);
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
        project.setStartDate(input.startDate());
        project.setDueDate(input.dueDate());
        project.setStatus(status);
        project.setProgressPercent(input.progressPercent());
        if (projectService.updateById(project) != 1) {
            throw conflict("WORK_PROJECT_VERSION_CONFLICT", "项目已被其他人修改，请刷新后重试");
        }
        audit(context, traceId, "WORK_PROJECT_UPDATED", "WORK_PROJECT", projectId,
                Map.of("status", status, "progressPercent", input.progressPercent()));
        return view(requireOwned(context, projectId), true);
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
        group.setVersion(0);
        taskGroupService.insert(group);
        audit(context, traceId, "WORK_TASK_GROUP_CREATED", "WORK_TASK_GROUP", group.getId(),
                Map.of("projectId", projectId, "sortOrder", group.getSortOrder()));
        return view(project, true);
    }

    @Transactional
    public WorkManagementModels.TaskView createTask(
            AuthenticatedContext context, Long projectId, WorkManagementModels.CreateTaskRequest input,
            String traceId) {
        requireAction(context, "CREATE_TASK");
        WorkProject project = requireOwned(context, projectId);
        requireProjectRole(context, projectId, Set.of("OWNER", "MANAGER", "MEMBER"));
        requireAssignable(context, input.ownerAccountId());
        requireProjectMember(projectId, input.ownerAccountId());
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
        List<Long> collaborators = normalizeCollaborators(context, projectId, input.collaboratorAccountIds(),
                input.ownerAccountId());
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
        task.setOwnerAccountId(input.ownerAccountId());
        task.setStartAt(input.startAt());
        task.setDueAt(input.dueAt());
        task.setCustomValuesJson(nullableJson(input.customValues()));
        task.setCreatedByAccountId(context.accountId());
        task.setVersion(0);
        taskService.insert(task);
        replaceCollaborators(task.getId(), collaborators);
        insertHistory(task, "CREATED", null, snapshot(task), null, context.accountId());
        audit(context, traceId, "WORK_PROJECT_TASK_CREATED", "WORK_TASK", task.getId(),
                Map.of("projectId", projectId, "ownerAccountId", task.getOwnerAccountId(),
                        "collaboratorCount", collaborators.size()));
        return view(task, true);
    }

    @Transactional(readOnly = true)
    public List<WorkManagementModels.TaskView> ordinaryTasks(AuthenticatedContext context) {
        requireAction(context, "VIEW");
        return scopedOrdinaryTasks(context).stream()
                .filter(task -> taskVisibleTo(context, task))
                .sorted(Comparator.comparing(WorkTask::getUpdatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(task -> view(task, false)).toList();
    }

    @Transactional
    public WorkManagementModels.TaskView createOrdinaryTask(
            AuthenticatedContext context, WorkManagementModels.CreateTaskRequest input, String traceId) {
        requireAction(context, "CREATE_TASK");
        requireAssignable(context, input.ownerAccountId());
        if (input.taskGroupId() != null || input.parentTaskId() != null) {
            throw invalid("ORDINARY_TASK_RELATION_INVALID", "普通任务不能绑定项目任务组或父任务");
        }
        requireValidTimes(input.startAt(), input.dueAt());
        List<Long> collaborators = normalizeCollaborators(context, null, input.collaboratorAccountIds(),
                input.ownerAccountId());
        WorkTask task = new WorkTask();
        bindContext(task, context);
        task.setProjectId(null);
        task.setTaskGroupId(null);
        task.setParentTaskId(null);
        task.setTitle(input.title().strip());
        task.setDescription(blankToNull(input.description()));
        task.setTaskType("ORDINARY");
        task.setPriority(normalizePriority(input.priority()));
        task.setStatus("TODO");
        task.setProgressPercent(BigDecimal.ZERO);
        task.setOwnerAccountId(input.ownerAccountId());
        task.setStartAt(input.startAt());
        task.setDueAt(input.dueAt());
        task.setCustomValuesJson(nullableJson(input.customValues()));
        task.setCreatedByAccountId(context.accountId());
        task.setVersion(0);
        taskService.insert(task);
        replaceCollaborators(task.getId(), collaborators);
        insertHistory(task, "CREATED", null, snapshot(task), null, context.accountId());
        audit(context, traceId, "WORK_ORDINARY_TASK_CREATED", "WORK_TASK", task.getId(),
                Map.of("ownerAccountId", task.getOwnerAccountId(), "collaboratorCount", collaborators.size()));
        return view(task, true);
    }

    @Transactional(readOnly = true)
    public WorkManagementModels.TaskView task(AuthenticatedContext context, Long taskId) {
        requireAction(context, "VIEW");
        WorkTask task = requireOwnedTask(context, taskId);
        requireTaskVisible(context, task);
        return view(task, true);
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
        requireAssignable(context, input.ownerAccountId());
        if (task.getProjectId() != null) {
            requireProjectMember(task.getProjectId(), input.ownerAccountId());
        }
        requireValidTimes(input.startAt(), input.dueAt());
        String nextStatus = input.status().strip().toUpperCase(Locale.ROOT);
        validateTransition(task.getStatus(), nextStatus);
        String before = toJson(snapshot(task));
        List<Long> collaborators = normalizeCollaborators(context, task.getProjectId(),
                input.collaboratorAccountIds(), input.ownerAccountId());
        task.setStatus(nextStatus);
        task.setOwnerAccountId(input.ownerAccountId());
        task.setPriority(normalizePriority(input.priority()));
        task.setProgressPercent(input.progressPercent());
        task.setStartAt(input.startAt());
        task.setDueAt(input.dueAt());
        task.setCompletedAt("COMPLETED".equals(nextStatus) ? LocalDateTime.now() : null);
        task.setCustomValuesJson(nullableJson(input.customValues()));
        if (taskService.updateById(task) != 1) {
            throw conflict("WORK_TASK_VERSION_CONFLICT", "任务已被其他人修改，请刷新后重试");
        }
        replaceCollaborators(taskId, collaborators);
        WorkTask updated = requireOwnedTask(context, taskId);
        String action = Objects.equals(taskStatus(before), nextStatus) ? "UPDATED" : "STATUS_CHANGED";
        insertHistory(updated, action, before, snapshot(updated), blankToNull(input.comment()), context.accountId());
        audit(context, traceId, "WORK_TASK_UPDATED", "WORK_TASK", taskId,
                Map.of("status", nextStatus, "ownerAccountId", input.ownerAccountId(), "action", action));
        return view(updated, true);
    }

    private WorkManagementModels.ProjectView view(WorkProject project, boolean includeDetail) {
        List<WorkManagementModels.MemberView> members = includeDetail ? projectMembers(project.getId()).stream()
                .map(member -> new WorkManagementModels.MemberView(member.getId(), member.getAccountId(),
                        member.getProjectRole(), member.getStatus())).toList() : List.of();
        List<WorkManagementModels.TaskGroupView> groups = includeDetail ? taskGroups(project.getId()).stream()
                .map(group -> new WorkManagementModels.TaskGroupView(group.getId(), group.getName(),
                        group.getSortOrder(), group.getStatus(), group.getVersion())).toList() : List.of();
        List<WorkManagementModels.TaskView> tasks = includeDetail ? projectTasks(project.getId()).stream()
                .map(task -> view(task, false)).toList() : List.of();
        return new WorkManagementModels.ProjectView(project.getId(), project.getContextType(), project.getPlatformId(),
                project.getSystemId(), project.getTenantId(), project.getCode(), project.getName(), project.getDescription(),
                project.getOwnerAccountId(), project.getStartDate(), project.getDueDate(), project.getProgressPercent(),
                project.getStatus(), project.getVersion(), members, groups, tasks);
    }

    private WorkManagementModels.TaskView view(WorkTask task, boolean includeHistory) {
        List<Long> collaborators = taskMembers(task.getId()).stream().map(WorkTaskMember::getAccountId).toList();
        List<WorkManagementModels.HistoryView> history = includeHistory ? histories(task.getId()).stream()
                .map(item -> new WorkManagementModels.HistoryView(item.getId(), item.getActionCode(),
                        fromJson(item.getBeforeJson()), fromJson(item.getAfterJson()), item.getCommentText(),
                        item.getChangedByAccountId(), item.getChangedAt())).toList() : List.of();
        return new WorkManagementModels.TaskView(task.getId(), task.getContextType(), task.getPlatformId(),
                task.getSystemId(), task.getTenantId(), task.getProjectId(), task.getTaskGroupId(), task.getParentTaskId(),
                task.getTitle(), task.getDescription(), task.getTaskType(), task.getPriority(), task.getStatus(),
                task.getProgressPercent(), task.getOwnerAccountId(), task.getStartAt(), task.getDueAt(),
                task.getCompletedAt(), fromJson(task.getCustomValuesJson()), task.getVersion(), collaborators, history);
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
                || taskMembers(task.getId()).stream().anyMatch(member -> Objects.equals(member.getAccountId(), context.accountId()));
        if (!projectManager && !participant) {
            throw forbidden("WORK_TASK_MODIFICATION_DENIED", "你不是该任务的负责人或协作成员");
        }
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

    private void requireAssignable(AuthenticatedContext context, Long accountId) {
        PlatformAccount account = accountService.selectById(accountId);
        if (account == null || !"ACTIVE".equals(account.getStatus())) {
            throw invalid("TASK_ASSIGNEE_INVALID", "负责人账号不存在或不可用");
        }
        if (context.systemId() == null) {
            return;
        }
        SystemMember member = systemMemberService.selectList(Wrappers.<SystemMember>lambdaQuery()
                .eq(SystemMember::getSystemId, context.systemId()).eq(SystemMember::getAccountId, accountId)
                .eq(SystemMember::getStatus, "ACTIVE")).stream().findFirst().orElse(null);
        if (member == null || tenantMemberService.selectList(Wrappers.<SystemTenantMember>lambdaQuery()
                .eq(SystemTenantMember::getSystemId, context.systemId())
                .eq(SystemTenantMember::getTenantId, context.tenantId())
                .eq(SystemTenantMember::getSystemMemberId, member.getId())
                .eq(SystemTenantMember::getStatus, "ACTIVE")).isEmpty()) {
            throw invalid("TASK_ASSIGNEE_CONTEXT_INVALID", "负责人不属于当前系统租户");
        }
    }

    private List<Long> normalizeCollaborators(
            AuthenticatedContext context, Long projectId, List<Long> accountIds, Long ownerAccountId) {
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        if (accountIds != null) {
            for (Long accountId : accountIds) {
                if (accountId == null || Objects.equals(accountId, ownerAccountId)) {
                    continue;
                }
                requireAssignable(context, accountId);
                if (projectId != null) {
                    requireProjectMember(projectId, accountId);
                }
                result.add(accountId);
            }
        }
        return List.copyOf(result);
    }

    private void replaceCollaborators(Long taskId, List<Long> accountIds) {
        for (WorkTaskMember member : taskMembers(taskId)) {
            taskMemberService.deleteById(member.getId());
        }
        for (Long accountId : accountIds) {
            WorkTaskMember member = new WorkTaskMember();
            member.setTaskId(taskId);
            member.setAccountId(accountId);
            member.setMemberType("COLLABORATOR");
            taskMemberService.insert(member);
        }
    }

    private void insertProjectMember(Long projectId, Long accountId, String role) {
        WorkProjectMember member = new WorkProjectMember();
        member.setProjectId(projectId);
        member.setAccountId(accountId);
        member.setProjectRole(role);
        member.setStatus("ACTIVE");
        projectMemberService.insert(member);
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
        result.put("ownerAccountId", task.getOwnerAccountId());
        result.put("startAt", task.getStartAt());
        result.put("dueAt", task.getDueAt());
        result.put("completedAt", task.getCompletedAt());
        result.put("customValues", fromJson(task.getCustomValuesJson()));
        result.put("version", task.getVersion());
        return result;
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
                .isNull(WorkTask::getProjectId);
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
        if (task == null || !owned(context, task)) {
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
                .eq(WorkTask::getProjectId, projectId).orderByDesc(WorkTask::getUpdatedAt));
    }

    private List<WorkTaskMember> taskMembers(Long taskId) {
        return taskMemberService.selectList(Wrappers.<WorkTaskMember>lambdaQuery()
                .eq(WorkTaskMember::getTaskId, taskId).orderByAsc(WorkTaskMember::getId));
    }

    private List<WorkTaskHistory> histories(Long taskId) {
        return taskHistoryService.selectList(Wrappers.<WorkTaskHistory>lambdaQuery()
                .eq(WorkTaskHistory::getTaskId, taskId).orderByDesc(WorkTaskHistory::getChangedAt));
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

    private String nullableJson(Map<String, Object> value) {
        return value == null || value.isEmpty() ? null : toJson(value);
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
}
