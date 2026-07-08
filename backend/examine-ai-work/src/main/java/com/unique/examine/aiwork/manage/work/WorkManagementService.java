package com.unique.examine.aiwork.manage.work;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.aiwork.base.entity.WorkDailyReport;
import com.unique.examine.aiwork.base.entity.WorkProject;
import com.unique.examine.aiwork.base.entity.WorkTask;
import com.unique.examine.aiwork.base.entity.WorkTaskComment;
import com.unique.examine.aiwork.base.entity.WorkTaskEvent;
import com.unique.examine.aiwork.base.service.WorkDailyReportBaseService;
import com.unique.examine.aiwork.base.service.WorkProjectBaseService;
import com.unique.examine.aiwork.base.service.WorkTaskBaseService;
import com.unique.examine.aiwork.base.service.WorkTaskCommentBaseService;
import com.unique.examine.aiwork.base.service.WorkTaskEventBaseService;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.ActionPermissionVO;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.CalendarDayVO;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.CalendarItemVO;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.CommentCreateRequest;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.DailyReportAutoDraftVO;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.DailyReportAutoSourceRuleVO;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.DailyReportCreateRequest;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.DailyReportReminderVO;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.DailyReportSearchRequest;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.DailyReportSourceVO;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.DailyReportVO;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.DictItemVO;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.DictionaryBindingVO;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.FileRefVO;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.ImpactRefVO;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.KanbanCardVO;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.KanbanColumnVO;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.KanbanConfigVO;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.KanbanQueryRequest;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.KanbanResultVO;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.MemberVO;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.ProjectCreateRequest;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.ProjectSearchRequest;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.PublishCheckItemVO;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.PublishStateVO;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.RelatedObjectVO;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.StatusColorSemanticVO;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.TaskCommentVO;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.TaskCreateRequest;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.TaskEventVO;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.TaskLifecycleVO;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.TaskMutationResult;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.TaskSearchRequest;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.TaskSummaryVO;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.TaskUpdateRequest;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.WorkConfigPublishCheckResult;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.WorkConfigUpdateRequest;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.WorkConfigVO;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.WorkDashboardVO;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.WorkFieldConfigVO;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.WorkFieldRefVO;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.WorkOverview;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.WorkProjectVO;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.WorkTab;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.WorkTarget;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.WorkTaskVO;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.WorkWarningVO;
import com.unique.examine.core.api.PageRequest;
import com.unique.examine.core.api.PageResult;
import com.unique.examine.core.context.RequestContext;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.error.CommonErrorCode;
import com.unique.examine.module.base.entity.ModuleWorkConfig;
import com.unique.examine.module.base.service.ModuleWorkConfigBaseService;
import com.unique.examine.module.manage.common.ModuleSystemContextResolver;
import com.unique.examine.module.manage.common.ModuleSystemContextResolver.ModuleSystemContext;
import com.unique.examine.plat.base.entity.PlatMember;
import com.unique.examine.plat.base.service.PlatMemberBaseService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Work management service backed by persisted project, task, report, comment, and event tables.
 */
@Service
public class WorkManagementService {

    private static final String PROJECT = "PROJECT";
    private static final String PLAIN = "PLAIN";
    private static final String WORK_CONFIG = "WORK_CONFIG";
    private static final int DELETED_NO = 0;

    private final WorkProjectBaseService projectBaseService;
    private final WorkTaskBaseService taskBaseService;
    private final WorkDailyReportBaseService dailyReportBaseService;
    private final WorkTaskCommentBaseService taskCommentBaseService;
    private final WorkTaskEventBaseService taskEventBaseService;
    private final PlatMemberBaseService memberBaseService;
    private final ModuleWorkConfigBaseService workConfigBaseService;
    private final ModuleSystemContextResolver moduleSystemContextResolver;
    private final ObjectMapper objectMapper;

    public WorkManagementService(WorkProjectBaseService projectBaseService,
                                 WorkTaskBaseService taskBaseService,
                                 WorkDailyReportBaseService dailyReportBaseService,
                                 WorkTaskCommentBaseService taskCommentBaseService,
                                 WorkTaskEventBaseService taskEventBaseService,
                                 PlatMemberBaseService memberBaseService,
                                 ModuleWorkConfigBaseService workConfigBaseService,
                                 ModuleSystemContextResolver moduleSystemContextResolver,
                                 ObjectMapper objectMapper) {
        this.projectBaseService = projectBaseService;
        this.taskBaseService = taskBaseService;
        this.dailyReportBaseService = dailyReportBaseService;
        this.taskCommentBaseService = taskCommentBaseService;
        this.taskEventBaseService = taskEventBaseService;
        this.memberBaseService = memberBaseService;
        this.workConfigBaseService = workConfigBaseService;
        this.moduleSystemContextResolver = moduleSystemContextResolver;
        this.objectMapper = objectMapper;
    }

    /**
     * Return the work dashboard for the current member.
     *
     * @param systemId system id
     * @return dashboard
     */
    public WorkDashboardVO dashboard(String systemId) {
        WorkContext context = workContext(systemId);
        LocalDate today = LocalDate.now();
        boolean submittedToday = dailyReportBaseService.count(new LambdaQueryWrapper<WorkDailyReport>()
                .eq(WorkDailyReport::getSystemId, context.systemId())
                .eq(WorkDailyReport::getTenantId, context.tenantId())
                .eq(WorkDailyReport::getSubmitterId, context.memberId())
                .eq(WorkDailyReport::getReportDate, today)
                .eq(WorkDailyReport::getStatus, "SUBMITTED")) > 0;
        List<WorkTaskVO> myTasks = taskBaseService.list(taskQuery(context, null, null)
                        .eq(WorkTask::getAssigneeMemberId, context.memberId())
                        .orderByAsc(WorkTask::getDueAt)
                        .last("LIMIT 0,8"))
                .stream()
                .map(task -> toTaskVO(task, context))
                .toList();
        WorkOverview overview = new WorkOverview(
                Math.toIntExact(projectBaseService.count(projectQuery(context, null)
                        .ne(WorkProject::getStatus, "DONE"))),
                Math.toIntExact(taskBaseService.count(taskQuery(context, PROJECT, null))),
                Math.toIntExact(taskBaseService.count(taskQuery(context, PLAIN, null))),
                Math.toIntExact(taskBaseService.count(taskQuery(context, null, null)
                        .lt(WorkTask::getDueAt, LocalDateTime.now())
                        .notIn(WorkTask::getStatus, List.of("DONE", "CANCELED")))),
                submittedToday);
        return new WorkDashboardVO(stringId(context.systemId()), stringId(context.tenantId()), tabs("dashboard"),
                overview, warnings(context), calendar(context), myTasks,
                new DailyReportReminderVO(today, submittedToday ? "SUBMITTED" : "DRAFT", true, myTasks.size(),
                        submittedToday ? "Daily report submitted." : "Daily report is not submitted yet."),
                RequestContext.current().traceId());
    }

    /**
     * Search projects.
     *
     * @param systemId system id
     * @param pageRequest page request
     * @param request search request
     * @return project page
     */
    public PageResult<WorkProjectVO> searchProjects(String systemId, PageRequest pageRequest,
                                                    ProjectSearchRequest request) {
        WorkContext context = workContext(systemId);
        int pageNo = pageNo(pageRequest);
        int pageSize = pageSize(pageRequest);
        int offset = (pageNo - 1) * pageSize;
        long total = projectBaseService.count(projectQuery(context, request));
        List<WorkProjectVO> records = projectBaseService.list(projectQuery(context, request)
                        .orderByDesc(WorkProject::getUpdatedAt)
                        .last("LIMIT " + offset + "," + pageSize))
                .stream()
                .map(project -> toProjectVO(project, context))
                .toList();
        return new PageResult<>(records, pageNo, pageSize, total, offset + records.size() < total);
    }

    /**
     * Create a project.
     *
     * @param systemId system id
     * @param request create request
     * @return created project
     */
    @Transactional(rollbackFor = Exception.class)
    public WorkProjectVO createProject(String systemId, ProjectCreateRequest request) {
        WorkContext context = workContext(systemId);
        if (request == null || !StringUtils.hasText(request.projectName())) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "Project name is required.");
        }
        LocalDateTime now = LocalDateTime.now();
        WorkProject project = new WorkProject();
        project.setSystemId(context.systemId());
        project.setTenantId(context.tenantId());
        project.setProjectCode(safeText(request.projectCode(), "PRJ-" + System.currentTimeMillis()));
        project.setProjectName(request.projectName());
        project.setOwnerMemberId(parseLong(request.ownerMemberId(), context.memberId()));
        project.setStatus(safeText(request.status(), "TODO"));
        project.setProgress(Objects.isNull(request.progress()) ? 0 : request.progress());
        project.setStartDate(Objects.isNull(request.startDate()) ? LocalDate.now() : request.startDate());
        project.setEndDate(request.endDate());
        project.setCreatedAt(now);
        project.setUpdatedAt(now);
        project.setDeleted(DELETED_NO);
        projectBaseService.saveEntity(project);
        return toProjectVO(project, context);
    }

    /**
     * Search project tasks.
     *
     * @param systemId system id
     * @param pageRequest page request
     * @param request search request
     * @return task page
     */
    public PageResult<WorkTaskVO> searchProjectTasks(String systemId, PageRequest pageRequest,
                                                     TaskSearchRequest request) {
        return searchTasks(systemId, PROJECT, pageRequest, request);
    }

    /**
     * Create a project task.
     *
     * @param systemId system id
     * @param request create request
     * @return mutation result
     */
    @Transactional(rollbackFor = Exception.class)
    public TaskMutationResult createProjectTask(String systemId, TaskCreateRequest request) {
        return createTask(systemId, PROJECT, request);
    }

    /**
     * Update a project task.
     *
     * @param systemId system id
     * @param taskId task id
     * @param request update request
     * @return mutation result
     */
    @Transactional(rollbackFor = Exception.class)
    public TaskMutationResult updateProjectTask(String systemId, String taskId, TaskUpdateRequest request) {
        return updateTask(systemId, PROJECT, taskId, request);
    }

    /**
     * Search plain tasks.
     *
     * @param systemId system id
     * @param pageRequest page request
     * @param request search request
     * @return task page
     */
    public PageResult<WorkTaskVO> searchPlainTasks(String systemId, PageRequest pageRequest,
                                                   TaskSearchRequest request) {
        return searchTasks(systemId, PLAIN, pageRequest, request);
    }

    /**
     * Create a plain task.
     *
     * @param systemId system id
     * @param request create request
     * @return mutation result
     */
    @Transactional(rollbackFor = Exception.class)
    public TaskMutationResult createPlainTask(String systemId, TaskCreateRequest request) {
        return createTask(systemId, PLAIN, request);
    }

    /**
     * Update a plain task.
     *
     * @param systemId system id
     * @param taskId task id
     * @param request update request
     * @return mutation result
     */
    @Transactional(rollbackFor = Exception.class)
    public TaskMutationResult updatePlainTask(String systemId, String taskId, TaskUpdateRequest request) {
        return updateTask(systemId, PLAIN, taskId, request);
    }

    /**
     * Query task kanban.
     *
     * @param systemId system id
     * @param request query request
     * @return kanban result
     */
    public KanbanResultVO queryKanban(String systemId, KanbanQueryRequest request) {
        WorkContext context = workContext(systemId);
        String taskType = safeTaskType(request == null ? null : request.taskType());
        List<WorkTask> tasks = taskBaseService.list(taskQuery(context, taskType, null)
                .orderByAsc(WorkTask::getDueAt)
                .last("LIMIT 0,200"));
        List<KanbanColumnVO> columns = statusItems(taskType).stream()
                .map(status -> {
                    List<KanbanCardVO> cards = tasks.stream()
                            .filter(task -> status.itemCode().equals(task.getStatus()))
                            .map(task -> toKanbanCard(task, context))
                            .toList();
                    return new KanbanColumnVO(status, cards, cards.size(), false);
                })
                .toList();
        return new KanbanResultVO(stringId(context.systemId()), stringId(context.tenantId()), taskType,
                kanbanConfig(taskType), columns, request == null ? null : request.pageCursor(), null, false,
                RequestContext.current().traceId());
    }

    /**
     * Search daily reports.
     *
     * @param systemId system id
     * @param pageRequest page request
     * @param request search request
     * @return report page
     */
    public PageResult<DailyReportVO> searchDailyReports(String systemId, PageRequest pageRequest,
                                                        DailyReportSearchRequest request) {
        WorkContext context = workContext(systemId);
        int pageNo = pageNo(pageRequest);
        int pageSize = pageSize(pageRequest);
        int offset = (pageNo - 1) * pageSize;
        long total = dailyReportBaseService.count(reportQuery(context, request));
        List<DailyReportVO> records = dailyReportBaseService.list(reportQuery(context, request)
                        .orderByDesc(WorkDailyReport::getReportDate)
                        .last("LIMIT " + offset + "," + pageSize))
                .stream()
                .map(report -> toDailyReportVO(report, context))
                .toList();
        return new PageResult<>(records, pageNo, pageSize, total, offset + records.size() < total);
    }

    /**
     * Create or submit a daily report.
     *
     * @param systemId system id
     * @param request create request
     * @return report view
     */
    @Transactional(rollbackFor = Exception.class)
    public DailyReportVO createDailyReport(String systemId, DailyReportCreateRequest request) {
        WorkContext context = workContext(systemId);
        LocalDate reportDate = request == null || request.reportDate() == null ? LocalDate.now() : request.reportDate();
        String status = Boolean.TRUE.equals(request == null ? null : request.submitNow()) ? "SUBMITTED"
                : safeText(request == null ? null : request.status(), "DRAFT");
        LocalDateTime now = LocalDateTime.now();
        WorkDailyReport report = dailyReportBaseService.getOne(new LambdaQueryWrapper<WorkDailyReport>()
                .eq(WorkDailyReport::getSystemId, context.systemId())
                .eq(WorkDailyReport::getTenantId, context.tenantId())
                .eq(WorkDailyReport::getSubmitterId, context.memberId())
                .eq(WorkDailyReport::getReportDate, reportDate)
                .last("LIMIT 1"), false);
        boolean creating = Objects.isNull(report);
        if (creating) {
            report = new WorkDailyReport();
            report.setSystemId(context.systemId());
            report.setTenantId(context.tenantId());
            report.setReportDate(reportDate);
            report.setSubmitterId(context.memberId());
            report.setCreatedAt(now);
        }
        report.setStatus(status);
        report.setContent(safeText(request == null ? null : request.content(), "Daily report draft."));
        report.setSourceSummary(sourceSummaryJson(request == null ? null : request.sourceIds()));
        report.setPermissionSnapshotId("perm_work_" + context.systemId() + "_" + context.memberId());
        report.setSubmittedAt("SUBMITTED".equals(status) ? now : null);
        report.setUpdatedAt(now);
        dailyReportBaseService.saveEntity(report);
        return toDailyReportVO(report, context);
    }

    /**
     * Auto draft a daily report from current tasks.
     *
     * @param systemId system id
     * @return draft payload
     */
    public DailyReportAutoDraftVO autoDraftDailyReport(String systemId) {
        WorkContext context = workContext(systemId);
        List<DailyReportSourceVO> sources = taskBaseService.list(taskQuery(context, null, null)
                        .eq(WorkTask::getAssigneeMemberId, context.memberId())
                        .ge(WorkTask::getUpdatedAt, LocalDate.now().atStartOfDay())
                        .last("LIMIT 0,20"))
                .stream()
                .map(task -> new DailyReportSourceVO(stringId(task.getId()), task.getTaskType(), task.getTaskTitle(),
                        "Status: " + task.getStatus() + ", progress: " + task.getProgress(),
                        task.getUpdatedAt(), "CURRENT_MEMBER_AUTHORIZED_ONLY", taskTarget(task)))
                .toList();
        String content = sources.isEmpty() ? "No task activity captured today."
                : sources.stream().map(source -> "- " + source.title()).reduce((a, b) -> a + "\n" + b).orElse("");
        return new DailyReportAutoDraftVO("draft_" + LocalDate.now(), LocalDate.now(), content, sources,
                autoSourceRule(), true, "perm_work_" + context.systemId() + "_" + context.memberId(),
                RequestContext.current().traceId());
    }

    /**
     * Query work configuration.
     *
     * @param systemId system id
     * @return work config
     */
    public WorkConfigVO config(String systemId) {
        WorkContext context = workContext(systemId);
        ModuleWorkConfig entity = workConfigEntity(context);
        if (Objects.isNull(entity)) {
            return configView(context, null, defaultPublishState(), LocalDateTime.now());
        }
        return configView(context, readWorkConfig(entity), publishState(entity), entity.getUpdatedAt());
    }

    /**
     * Save work configuration.
     *
     * @param systemId system id
     * @param request update request
     * @return merged config view
     */
    public WorkConfigVO updateConfig(String systemId, WorkConfigUpdateRequest request) {
        WorkContext context = workContext(systemId);
        ModuleWorkConfig entity = workConfigEntity(context);
        WorkConfigUpdateRequest merged = mergeWorkConfig(Objects.isNull(entity) ? null : readWorkConfig(entity),
                request);
        LocalDateTime now = LocalDateTime.now();
        if (Objects.isNull(entity)) {
            entity = new ModuleWorkConfig();
            entity.setSystemId(context.systemId());
            entity.setTenantId(context.tenantId());
            entity.setConfigType(WORK_CONFIG);
            entity.setPublishStatus("DRAFT");
            entity.setPublishedVersion("work_cfg_v1");
        }
        entity.setFieldList(writeJson(merged));
        entity.setUpdatedAt(now);
        workConfigBaseService.saveEntity(entity);
        return configView(context, merged, publishState(entity), now);
    }

    /**
     * Publish-check work configuration.
     *
     * @param systemId system id
     * @return check result
     */
    public WorkConfigPublishCheckResult publishCheck(String systemId) {
        String traceId = RequestContext.current().traceId();
        return new WorkConfigPublishCheckResult(true, "work_cfg_v1",
                List.of(new PublishCheckItemVO("PROJECT_TASK_FIELDS", "Project task fields", "PASS", true,
                                "Project tasks have status, tags, progress, assignee, due time and relation fields."),
                        new PublishCheckItemVO("PLAIN_TASK_FIELDS", "Plain task fields", "PASS", true,
                                "Plain tasks have separated lifecycle and kanban fields."),
                        new PublishCheckItemVO("DAILY_REPORT_FIELDS", "Daily report fields", "PASS", true,
                                "Daily reports support manual content and auto source rules.")),
                List.of(new ImpactRefVO("WORK_TAB", "projectTask", "Project tasks"),
                        new ImpactRefVO("WORK_TAB", "plainTask", "Plain tasks"),
                        new ImpactRefVO("WORK_TAB", "dailyReport", "Daily reports")),
                traceId);
    }

    /**
     * Query task comments.
     *
     * @param systemId system id
     * @param taskId task id
     * @return comments
     */
    public List<TaskCommentVO> comments(String systemId, String taskId) {
        WorkContext context = workContext(systemId);
        WorkTask task = requireTask(context, null, taskId);
        return taskCommentBaseService.list(new LambdaQueryWrapper<WorkTaskComment>()
                        .eq(WorkTaskComment::getTaskId, task.getId())
                        .orderByAsc(WorkTaskComment::getCreatedAt))
                .stream()
                .map(this::toCommentVO)
                .toList();
    }

    /**
     * Add a task comment.
     *
     * @param systemId system id
     * @param taskId task id
     * @param request comment request
     * @return created comment
     */
    @Transactional(rollbackFor = Exception.class)
    public TaskCommentVO addComment(String systemId, String taskId, CommentCreateRequest request) {
        WorkContext context = workContext(systemId);
        WorkTask task = requireTask(context, null, taskId);
        if (request == null || !StringUtils.hasText(request.content())) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "Comment content is required.");
        }
        WorkTaskComment comment = new WorkTaskComment();
        comment.setTaskId(task.getId());
        comment.setParentId(parseLong(request.parentCommentId(), null));
        comment.setCommenterId(context.memberId());
        comment.setContent(request.content());
        comment.setCreatedAt(LocalDateTime.now());
        taskCommentBaseService.saveEntity(comment);
        writeEvent(task.getId(), "COMMENT_CREATED", null, Map.of("commentId", comment.getId()), context.memberId());
        return toCommentVO(comment);
    }

    /**
     * Query task events.
     *
     * @param systemId system id
     * @param taskId task id
     * @return events
     */
    public List<TaskEventVO> events(String systemId, String taskId) {
        WorkContext context = workContext(systemId);
        WorkTask task = requireTask(context, null, taskId);
        return taskEventBaseService.list(new LambdaQueryWrapper<WorkTaskEvent>()
                        .eq(WorkTaskEvent::getTaskId, task.getId())
                        .orderByDesc(WorkTaskEvent::getCreatedAt))
                .stream()
                .map(this::toEventVO)
                .toList();
    }

    private PageResult<WorkTaskVO> searchTasks(String systemId, String taskType, PageRequest pageRequest,
                                               TaskSearchRequest request) {
        WorkContext context = workContext(systemId);
        int pageNo = pageNo(pageRequest);
        int pageSize = pageSize(pageRequest);
        int offset = (pageNo - 1) * pageSize;
        long total = taskBaseService.count(taskQuery(context, taskType, request));
        List<WorkTaskVO> records = taskBaseService.list(taskQuery(context, taskType, request)
                        .orderByDesc(WorkTask::getUpdatedAt)
                        .last("LIMIT " + offset + "," + pageSize))
                .stream()
                .map(task -> toTaskVO(task, context))
                .toList();
        return new PageResult<>(records, pageNo, pageSize, total, offset + records.size() < total);
    }

    private TaskMutationResult createTask(String systemId, String taskType, TaskCreateRequest request) {
        WorkContext context = workContext(systemId);
        if (request == null || !StringUtils.hasText(request.title())) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "Task title is required.");
        }
        WorkTask task = new WorkTask();
        applyTaskCreate(task, context, taskType, request);
        taskBaseService.saveEntity(task);
        WorkTaskEvent event = writeEvent(task.getId(), taskType + "_TASK_CREATED", null,
                Map.of("taskId", task.getId(), "title", task.getTaskTitle()), context.memberId());
        return new TaskMutationResult(toTaskVO(task, context), toEventVO(event), RequestContext.current().traceId(),
                "aud_" + RequestContext.current().traceId());
    }

    private TaskMutationResult updateTask(String systemId, String taskType, String taskId, TaskUpdateRequest request) {
        WorkContext context = workContext(systemId);
        WorkTask task = requireTask(context, taskType, taskId);
        Map<String, Object> before = taskSnapshot(task);
        applyTaskUpdate(task, request);
        taskBaseService.updateById(task);
        WorkTaskEvent event = writeEvent(task.getId(), taskType + "_TASK_UPDATED", before, taskSnapshot(task),
                context.memberId());
        return new TaskMutationResult(toTaskVO(task, context), toEventVO(event), RequestContext.current().traceId(),
                "aud_" + RequestContext.current().traceId());
    }

    private void applyTaskCreate(WorkTask task, WorkContext context, String taskType, TaskCreateRequest request) {
        LocalDateTime now = LocalDateTime.now();
        task.setSystemId(context.systemId());
        task.setTenantId(context.tenantId());
        task.setTaskType(taskType);
        task.setProjectId(PROJECT.equals(taskType) ? parseLong(request.projectId(), null) : null);
        task.setTaskTitle(request.title());
        task.setAssigneeMemberId(parseLong(request.assigneeMemberId(), context.memberId()));
        task.setCollaborators(writeJson(safeList(request.collaborators())));
        task.setStatus(safeText(request.status(), "TODO"));
        task.setTags(writeJson(safeList(request.tagCodes())));
        task.setProgress(Objects.isNull(request.progress()) ? 0 : request.progress());
        task.setDueAt(request.dueAt());
        task.setCompletedAt(null);
        task.setRelatedObject(writeRelatedObject(request.relatedObject()));
        task.setPermissionSnapshotId("perm_work_" + context.systemId() + "_" + context.memberId());
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        task.setDeleted(DELETED_NO);
    }

    private void applyTaskUpdate(WorkTask task, TaskUpdateRequest request) {
        if (Objects.isNull(request)) {
            return;
        }
        if (StringUtils.hasText(request.title())) {
            task.setTaskTitle(request.title());
        }
        Long assigneeId = parseLong(request.assigneeMemberId(), null);
        if (Objects.nonNull(assigneeId)) {
            task.setAssigneeMemberId(assigneeId);
        }
        if (Objects.nonNull(request.collaborators())) {
            task.setCollaborators(writeJson(request.collaborators()));
        }
        if (StringUtils.hasText(request.status())) {
            task.setStatus(request.status());
        }
        if (Objects.nonNull(request.tagCodes())) {
            task.setTags(writeJson(request.tagCodes()));
        }
        if (Objects.nonNull(request.progress())) {
            task.setProgress(request.progress());
        }
        if (Objects.nonNull(request.dueAt())) {
            task.setDueAt(request.dueAt());
        }
        if (Objects.nonNull(request.completedAt())) {
            task.setCompletedAt(request.completedAt());
        }
        if (Objects.nonNull(request.relatedObject())) {
            task.setRelatedObject(writeRelatedObject(request.relatedObject()));
        }
        task.setUpdatedAt(LocalDateTime.now());
    }

    private LambdaQueryWrapper<WorkProject> projectQuery(WorkContext context, ProjectSearchRequest request) {
        LambdaQueryWrapper<WorkProject> wrapper = new LambdaQueryWrapper<WorkProject>()
                .eq(WorkProject::getSystemId, context.systemId())
                .eq(WorkProject::getTenantId, context.tenantId())
                .eq(WorkProject::getDeleted, DELETED_NO);
        if (Objects.nonNull(request)) {
            if (StringUtils.hasText(request.keyword())) {
                wrapper.and(value -> value.like(WorkProject::getProjectName, request.keyword())
                        .or().like(WorkProject::getProjectCode, request.keyword()));
            }
            if (StringUtils.hasText(request.status())) {
                wrapper.eq(WorkProject::getStatus, request.status());
            }
            Long ownerId = parseLong(request.ownerMemberId(), null);
            if (Objects.nonNull(ownerId)) {
                wrapper.eq(WorkProject::getOwnerMemberId, ownerId);
            }
        }
        return wrapper;
    }

    private LambdaQueryWrapper<WorkTask> taskQuery(WorkContext context, String taskType, TaskSearchRequest request) {
        LambdaQueryWrapper<WorkTask> wrapper = new LambdaQueryWrapper<WorkTask>()
                .eq(WorkTask::getSystemId, context.systemId())
                .eq(WorkTask::getTenantId, context.tenantId())
                .eq(WorkTask::getDeleted, DELETED_NO);
        if (StringUtils.hasText(taskType)) {
            wrapper.eq(WorkTask::getTaskType, taskType);
        }
        if (Objects.nonNull(request)) {
            if (StringUtils.hasText(request.keyword())) {
                wrapper.like(WorkTask::getTaskTitle, request.keyword());
            }
            if (StringUtils.hasText(request.status())) {
                wrapper.eq(WorkTask::getStatus, request.status());
            }
            Long assigneeId = parseLong(request.assigneeMemberId(), null);
            if (Objects.nonNull(assigneeId)) {
                wrapper.eq(WorkTask::getAssigneeMemberId, assigneeId);
            }
            Long projectId = parseLong(request.projectId(), null);
            if (Objects.nonNull(projectId)) {
                wrapper.eq(WorkTask::getProjectId, projectId);
            }
            if (Objects.nonNull(request.tagCodes()) && !request.tagCodes().isEmpty()) {
                wrapper.and(value -> {
                    boolean first = true;
                    for (String tag : request.tagCodes()) {
                        if (first) {
                            value.like(WorkTask::getTags, tag);
                            first = false;
                        } else {
                            value.or().like(WorkTask::getTags, tag);
                        }
                    }
                });
            }
        }
        return wrapper;
    }

    private LambdaQueryWrapper<WorkDailyReport> reportQuery(WorkContext context, DailyReportSearchRequest request) {
        LambdaQueryWrapper<WorkDailyReport> wrapper = new LambdaQueryWrapper<WorkDailyReport>()
                .eq(WorkDailyReport::getSystemId, context.systemId())
                .eq(WorkDailyReport::getTenantId, context.tenantId())
                .eq(WorkDailyReport::getSubmitterId, context.memberId());
        if (Objects.nonNull(request)) {
            if (Objects.nonNull(request.startDate())) {
                wrapper.ge(WorkDailyReport::getReportDate, request.startDate());
            }
            if (Objects.nonNull(request.endDate())) {
                wrapper.le(WorkDailyReport::getReportDate, request.endDate());
            }
            if (StringUtils.hasText(request.status())) {
                wrapper.eq(WorkDailyReport::getStatus, request.status());
            }
            if (StringUtils.hasText(request.keyword())) {
                wrapper.like(WorkDailyReport::getContent, request.keyword());
            }
        }
        return wrapper;
    }

    private WorkTask requireTask(WorkContext context, String taskType, String taskId) {
        Long id = parseLong(taskId, null);
        if (Objects.isNull(id)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "Task id must be numeric.");
        }
        LambdaQueryWrapper<WorkTask> wrapper = taskQuery(context, taskType, null)
                .eq(WorkTask::getId, id)
                .last("LIMIT 1");
        WorkTask task = taskBaseService.getOne(wrapper, false);
        if (Objects.isNull(task)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "Task does not exist.");
        }
        return task;
    }

    private WorkProjectVO toProjectVO(WorkProject project, WorkContext context) {
        TaskSummaryVO summary = taskSummary(project.getId(), context);
        return new WorkProjectVO(stringId(project.getId()), project.getProjectCode(), project.getProjectName(),
                member(project.getOwnerMemberId()), dict(project.getStatus()), safeInt(project.getProgress()),
                project.getStartDate(), project.getEndDate(), summary, target("WORK_PROJECT_DETAIL",
                "WorkProjectDetail", Map.of("systemId", stringId(project.getSystemId()),
                        "projectId", stringId(project.getId()))));
    }

    private WorkTaskVO toTaskVO(WorkTask task, WorkContext context) {
        WorkProject project = Objects.isNull(task.getProjectId()) ? null : projectBaseService.getById(task.getProjectId());
        List<String> tagCodes = readStringList(task.getTags());
        RelatedObjectVO relatedObject = readRelatedObject(task.getRelatedObject());
        return new WorkTaskVO(stringId(task.getId()), task.getTaskType(), task.getTaskTitle(),
                stringId(task.getProjectId()), project == null ? null : project.getProjectName(),
                member(task.getAssigneeMemberId()), members(readStringList(task.getCollaborators())),
                dict(task.getStatus()), tagCodes.stream().map(this::dict).toList(), safeInt(task.getProgress()),
                task.getDueAt(), task.getCompletedAt(), relatedObject, Map.of(),
                Math.toIntExact(taskCommentBaseService.count(new LambdaQueryWrapper<WorkTaskComment>()
                        .eq(WorkTaskComment::getTaskId, task.getId()))),
                warningLevel(task), taskTarget(task), actionPermissions(task), lifecycle(task.getTaskType()));
    }

    private KanbanCardVO toKanbanCard(WorkTask task, WorkContext context) {
        WorkTaskVO taskVO = toTaskVO(task, context);
        return new KanbanCardVO(taskVO.taskId(), taskVO.title(), taskVO.projectName(), taskVO.assignee(),
                taskVO.status(), taskVO.tags(), taskVO.progress(), taskVO.dueAt(), null, null,
                taskVO.warningLevel(), taskVO.rowDetailTarget());
    }

    private DailyReportVO toDailyReportVO(WorkDailyReport report, WorkContext context) {
        return new DailyReportVO(stringId(report.getId()), report.getReportDate(), report.getStatus(),
                report.getContent(), report.getSourceSummary(), member(report.getSubmitterId()),
                report.getSubmittedAt(), report.getPermissionSnapshotId(), target("DAILY_REPORT_DETAIL",
                "DailyReportDetail", Map.of("systemId", stringId(context.systemId()),
                        "reportId", stringId(report.getId()))));
    }

    private TaskCommentVO toCommentVO(WorkTaskComment comment) {
        return new TaskCommentVO(stringId(comment.getId()), stringId(comment.getParentId()),
                member(comment.getCommenterId()), comment.getContent(), List.of(), comment.getCreatedAt());
    }

    private TaskEventVO toEventVO(WorkTaskEvent event) {
        return new TaskEventVO(stringId(event.getId()), event.getEventType(), readMap(event.getBeforePayload()),
                readMap(event.getAfterPayload()), member(event.getOperatorMemberId()), event.getTraceId(),
                event.getAuditLogId(), event.getCreatedAt());
    }

    private WorkTaskEvent writeEvent(Long taskId, String eventType, Map<String, Object> before,
                                     Map<String, Object> after, Long memberId) {
        RequestContext requestContext = RequestContext.current();
        WorkTaskEvent event = new WorkTaskEvent();
        event.setTaskId(taskId);
        event.setEventType(eventType);
        event.setBeforePayload(writeJson(before));
        event.setAfterPayload(writeJson(after));
        event.setOperatorMemberId(memberId);
        event.setTraceId(requestContext.traceId());
        event.setAuditLogId("aud_" + requestContext.traceId());
        event.setCreatedAt(LocalDateTime.now());
        taskEventBaseService.saveEntity(event);
        return event;
    }

    private TaskSummaryVO taskSummary(Long projectId, WorkContext context) {
        LambdaQueryWrapper<WorkTask> wrapper = taskQuery(context, PROJECT, null).eq(WorkTask::getProjectId, projectId);
        List<WorkTask> tasks = taskBaseService.list(wrapper);
        int done = (int) tasks.stream().filter(task -> "DONE".equals(task.getStatus())).count();
        int overdue = (int) tasks.stream().filter(task -> task.getDueAt() != null
                && task.getDueAt().isBefore(LocalDateTime.now())
                && !"DONE".equals(task.getStatus())).count();
        int doing = (int) tasks.stream().filter(task -> "DOING".equals(task.getStatus())).count();
        return new TaskSummaryVO(tasks.size(), doing, overdue, done);
    }

    private List<WorkWarningVO> warnings(WorkContext context) {
        return taskBaseService.list(taskQuery(context, null, null)
                        .le(WorkTask::getDueAt, LocalDateTime.now().plusDays(1))
                        .notIn(WorkTask::getStatus, List.of("DONE", "CANCELED"))
                        .orderByAsc(WorkTask::getDueAt)
                        .last("LIMIT 0,5"))
                .stream()
                .map(task -> new WorkWarningVO(stringId(task.getId()), "TASK_DUE", task.getTaskTitle(),
                        warningLevel(task), task.getDueAt(), taskTarget(task)))
                .toList();
    }

    private List<CalendarDayVO> calendar(WorkContext context) {
        LocalDate firstDay = LocalDate.now().withDayOfMonth(1);
        List<WorkTask> tasks = taskBaseService.list(taskQuery(context, null, null)
                .ge(WorkTask::getDueAt, firstDay.atStartOfDay())
                .lt(WorkTask::getDueAt, firstDay.plusMonths(1).atStartOfDay()));
        Map<LocalDate, List<CalendarItemVO>> grouped = new LinkedHashMap<>();
        for (WorkTask task : tasks) {
            if (task.getDueAt() == null) {
                continue;
            }
            grouped.computeIfAbsent(task.getDueAt().toLocalDate(), key -> new ArrayList<>())
                    .add(new CalendarItemVO(stringId(task.getId()), task.getTaskType(), task.getTaskTitle(),
                            dict(task.getStatus()).color(), taskTarget(task)));
        }
        return grouped.entrySet().stream()
                .map(entry -> new CalendarDayVO(entry.getKey(), entry.getValue()))
                .toList();
    }

    private List<WorkTab> tabs(String selected) {
        return List.of(new WorkTab("dashboard", "仪表盘", "WorkDashboard", 0, "dashboard".equals(selected)),
                new WorkTab("projectTask", "项目任务", "WorkProjectTask", 0, "projectTask".equals(selected)),
                new WorkTab("plainTask", "普通任务", "WorkPlainTask", 0, "plainTask".equals(selected)),
                new WorkTab("dailyReport", "日报", "WorkDailyReport", 0, "dailyReport".equals(selected)));
    }

    private WorkConfigVO configView(WorkContext context, WorkConfigUpdateRequest request,
                                    PublishStateVO publishState, LocalDateTime updatedAt) {
        List<WorkFieldConfigVO> projectFields = request != null && request.projectTaskFields() != null
                ? request.projectTaskFields() : projectTaskFields();
        List<WorkFieldConfigVO> plainFields = request != null && request.plainTaskFields() != null
                ? request.plainTaskFields() : plainTaskFields();
        List<WorkFieldConfigVO> reportFields = request != null && request.dailyReportFields() != null
                ? request.dailyReportFields() : dailyReportFields();
        return new WorkConfigVO(stringId(context.systemId()), stringId(context.tenantId()), projectFields,
                plainFields, reportFields, request != null && request.projectTaskKanban() != null
                ? request.projectTaskKanban() : kanbanConfig(PROJECT),
                request != null && request.plainTaskKanban() != null ? request.plainTaskKanban() : kanbanConfig(PLAIN),
                request != null && request.dailyReportAutoSourceRule() != null
                        ? request.dailyReportAutoSourceRule() : autoSourceRule(),
                dictionaryBindings(), statusColors(),
                publishState, Objects.isNull(updatedAt) ? LocalDateTime.now() : updatedAt);
    }

    private ModuleWorkConfig workConfigEntity(WorkContext context) {
        return workConfigBaseService.getOne(new LambdaQueryWrapper<ModuleWorkConfig>()
                .eq(ModuleWorkConfig::getSystemId, context.systemId())
                .eq(ModuleWorkConfig::getTenantId, context.tenantId())
                .eq(ModuleWorkConfig::getConfigType, WORK_CONFIG)
                .last("LIMIT 1"), false);
    }

    private WorkConfigUpdateRequest readWorkConfig(ModuleWorkConfig entity) {
        if (Objects.isNull(entity) || !StringUtils.hasText(entity.getFieldList())) {
            return null;
        }
        try {
            return objectMapper.readValue(entity.getFieldList(), WorkConfigUpdateRequest.class);
        } catch (Exception ex) {
            return null;
        }
    }

    private WorkConfigUpdateRequest mergeWorkConfig(WorkConfigUpdateRequest existing,
                                                    WorkConfigUpdateRequest request) {
        return new WorkConfigUpdateRequest(
                request != null && request.projectTaskFields() != null
                        ? request.projectTaskFields() : existing == null ? null : existing.projectTaskFields(),
                request != null && request.plainTaskFields() != null
                        ? request.plainTaskFields() : existing == null ? null : existing.plainTaskFields(),
                request != null && request.dailyReportFields() != null
                        ? request.dailyReportFields() : existing == null ? null : existing.dailyReportFields(),
                request != null && request.projectTaskKanban() != null
                        ? request.projectTaskKanban() : existing == null ? null : existing.projectTaskKanban(),
                request != null && request.plainTaskKanban() != null
                        ? request.plainTaskKanban() : existing == null ? null : existing.plainTaskKanban(),
                request != null && request.dailyReportAutoSourceRule() != null
                        ? request.dailyReportAutoSourceRule()
                        : existing == null ? null : existing.dailyReportAutoSourceRule(),
                request != null && StringUtils.hasText(request.changeReason())
                        ? request.changeReason() : existing == null ? null : existing.changeReason());
    }

    private PublishStateVO publishState(ModuleWorkConfig entity) {
        if (Objects.isNull(entity)) {
            return defaultPublishState();
        }
        return new PublishStateVO(safeText(entity.getPublishStatus(), "DRAFT"),
                safeText(entity.getPublishedVersion(), "work_cfg_v1"), true, null);
    }

    private PublishStateVO defaultPublishState() {
        return new PublishStateVO("DRAFT", "work_cfg_v1", true, null);
    }

    private List<WorkFieldConfigVO> projectTaskFields() {
        return List.of(field("field_project_status", "status", "项目任务状态", "select", "dict_project_task_status", true),
                field("field_priority", "priority", "优先级", "select", "dict_work_priority", true),
                field("field_tags", "tags", "标签", "multiSelect", "dict_work_tag", true),
                field("field_progress", "progress", "完成度", "number", null, false),
                field("field_due_at", "dueAt", "截止时间", "datetime", null, false));
    }

    private List<WorkFieldConfigVO> plainTaskFields() {
        return List.of(field("field_plain_status", "status", "普通任务状态", "select", "dict_plain_task_status", true),
                field("field_priority", "priority", "优先级", "select", "dict_work_priority", true),
                field("field_tags", "tags", "标签", "multiSelect", "dict_work_tag", true),
                field("field_due_at", "dueAt", "截止时间", "datetime", null, false));
    }

    private List<WorkFieldConfigVO> dailyReportFields() {
        return List.of(field("field_report_date", "reportDate", "日报日期", "date", null, false),
                field("field_report_status", "status", "日报状态", "select", "dict_daily_report_status", true),
                field("field_report_content", "content", "日报内容", "textarea", null, false));
    }

    private WorkFieldConfigVO field(String id, String code, String name, String type, String dictType,
                                    boolean kanbanEligible) {
        return new WorkFieldConfigVO(id, code, name, type, false, true, true, true, dictType,
                dictType == null ? List.of() : dictItems(dictType), true, kanbanEligible, "work.field." + code);
    }

    private KanbanConfigVO kanbanConfig(String taskType) {
        String dictType = PROJECT.equals(taskType) ? "dict_project_task_status" : "dict_plain_task_status";
        return new KanbanConfigVO(taskType, new WorkFieldRefVO("field_status", "status", "状态", "select",
                dictType, true), null, null, List.of(new WorkFieldRefVO("field_tags", "tags", "标签",
                "multiSelect", "dict_work_tag", true)), "work_cfg_v1");
    }

    private List<DictionaryBindingVO> dictionaryBindings() {
        return List.of(new DictionaryBindingVO("dict_project_task_status", "项目任务状态",
                        List.of("field_project_status"), dictItems("dict_project_task_status")),
                new DictionaryBindingVO("dict_plain_task_status", "普通任务状态",
                        List.of("field_plain_status"), dictItems("dict_plain_task_status")),
                new DictionaryBindingVO("dict_work_priority", "优先级",
                        List.of("field_priority"), dictItems("dict_work_priority")),
                new DictionaryBindingVO("dict_work_tag", "任务标签",
                        List.of("field_tags"), dictItems("dict_work_tag")),
                new DictionaryBindingVO("dict_daily_report_status", "日报状态",
                        List.of("field_report_status"), dictItems("dict_daily_report_status")));
    }

    private List<DictItemVO> statusItems(String taskType) {
        return PROJECT.equals(taskType) ? dictItems("dict_project_task_status") : dictItems("dict_plain_task_status");
    }

    private List<DictItemVO> dictItems(String dictType) {
        return switch (safeText(dictType, "")) {
            case "dict_project_task_status" -> List.of(dict("TODO", "待开始", "#8A8F98", true),
                    dict("DOING", "进行中", "#1769E0", false),
                    dict("REVIEW", "验收中", "#7C3AED", false),
                    dict("DONE", "已完成", "#059669", false),
                    dict("OVERDUE", "已逾期", "#DC2626", false));
            case "dict_plain_task_status" -> List.of(dict("TODO", "待处理", "#8A8F98", true),
                    dict("DOING", "处理中", "#1769E0", false),
                    dict("DONE", "已完成", "#059669", false),
                    dict("CANCELED", "已取消", "#6B7280", false));
            case "dict_daily_report_status" -> List.of(dict("DRAFT", "草稿", "#8A8F98", true),
                    dict("SUBMITTED", "已提交", "#059669", false));
            case "dict_work_priority" -> List.of(dict("HIGH", "高", "#DC2626", false),
                    dict("MEDIUM", "中", "#D97706", true),
                    dict("LOW", "低", "#059669", false));
            case "dict_work_tag" -> List.of(dict("PROJECT", "项目", "#1769E0", false),
                    dict("FIELD", "字段", "#7C3AED", false),
                    dict("BUG", "问题", "#DC2626", false),
                    dict("DAILY", "日报", "#059669", false));
            default -> List.of();
        };
    }

    private DictItemVO dict(String code, String name, String color, boolean defaultItem) {
        return new DictItemVO(code, name, color, "circle", "status", 10, true, defaultItem);
    }

    private DictItemVO dict(String code) {
        return switch (safeText(code, "TODO")) {
            case "DOING" -> dict("DOING", "进行中", "#1769E0", false);
            case "REVIEW" -> dict("REVIEW", "验收中", "#7C3AED", false);
            case "DONE" -> dict("DONE", "已完成", "#059669", false);
            case "OVERDUE" -> dict("OVERDUE", "已逾期", "#DC2626", false);
            case "CANCELED" -> dict("CANCELED", "已取消", "#6B7280", false);
            case "HIGH" -> dict("HIGH", "高", "#DC2626", false);
            case "MEDIUM" -> dict("MEDIUM", "中", "#D97706", false);
            case "LOW" -> dict("LOW", "低", "#059669", false);
            default -> dict(code, code, "#8A8F98", "TODO".equals(code));
        };
    }

    private List<StatusColorSemanticVO> statusColors() {
        return List.of(new StatusColorSemanticVO("draft_or_pending", "#8A8F98", "Draft or pending"),
                new StatusColorSemanticVO("active_or_current", "#1769E0", "Active or current"),
                new StatusColorSemanticVO("success_or_done", "#059669", "Success or done"),
                new StatusColorSemanticVO("warning_or_review", "#D97706", "Warning or review"),
                new StatusColorSemanticVO("danger_or_disabled", "#DC2626", "Danger or disabled"));
    }

    private DailyReportAutoSourceRuleVO autoSourceRule() {
        return new DailyReportAutoSourceRuleVO(List.of("TASK", "TODO", "MESSAGE", "BUSINESS_LOG", "APPROVAL"),
                "CURRENT_MEMBER_PROJECT_AND_PLAIN_TASKS", "CURRENT_MEMBER_TODOS",
                "CURRENT_MEMBER_MESSAGES", "CURRENT_MEMBER_BUSINESS_LOGS", "CURRENT_MEMBER_APPROVALS",
                "CURRENT_MEMBER_AUTHORIZED_ONLY", true);
    }

    private WorkContext workContext(String systemId) {
        ModuleSystemContext context = moduleSystemContextResolver.resolve(systemId);
        return new WorkContext(context.systemId(), context.tenantId(), context.systemMemberId());
    }

    private MemberVO member(Long memberId) {
        if (Objects.isNull(memberId)) {
            return new MemberVO(null, "未分配", null, null);
        }
        PlatMember member = memberBaseService.getById(memberId);
        if (Objects.isNull(member)) {
            return new MemberVO(stringId(memberId), "Member " + memberId, null, null);
        }
        return new MemberVO(stringId(member.getId()), member.getMemberName(), null, null);
    }

    private List<MemberVO> members(List<String> memberIds) {
        return memberIds.stream()
                .map(memberId -> member(parseLong(memberId, null)))
                .toList();
    }

    private WorkTarget taskTarget(WorkTask task) {
        return target("WORK_TASK_DETAIL", PROJECT.equals(task.getTaskType()) ? "WorkProjectTaskDetail"
                        : "WorkPlainTaskDetail", Map.of("systemId", stringId(task.getSystemId()),
                "taskId", stringId(task.getId()), "taskType", task.getTaskType()));
    }

    private WorkTarget target(String targetType, String routeName, Map<String, String> params) {
        return new WorkTarget(targetType, routeName, params);
    }

    private RelatedObjectVO readRelatedObject(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            String objectType = text(node, "objectType");
            String objectId = text(node, "objectId");
            String objectTitle = text(node, "objectTitle");
            WorkTarget target = target("RELATED_OBJECT", "SystemRelatedObject",
                    Map.of("objectType", safeText(objectType, ""), "objectId", safeText(objectId, "")));
            return new RelatedObjectVO(objectType, objectId, objectTitle, target);
        } catch (Exception ex) {
            return null;
        }
    }

    private String writeRelatedObject(RelatedObjectVO value) {
        if (Objects.isNull(value)) {
            return null;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("objectType", value.objectType());
        payload.put("objectId", value.objectId());
        payload.put("objectTitle", value.objectTitle());
        return writeJson(payload);
    }

    private List<String> readStringList(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<List<String>>() {
            });
        } catch (Exception ex) {
            Set<String> values = new LinkedHashSet<>();
            for (String item : value.split(",")) {
                if (StringUtils.hasText(item)) {
                    values.add(item.trim());
                }
            }
            return new ArrayList<>(values);
        }
    }

    private Map<String, Object> readMap(String value) {
        if (!StringUtils.hasText(value)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String writeJson(Object value) {
        if (Objects.isNull(value)) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private Map<String, Object> taskSnapshot(WorkTask task) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("taskId", task.getId());
        map.put("title", task.getTaskTitle());
        map.put("status", task.getStatus());
        map.put("progress", task.getProgress());
        map.put("assigneeMemberId", task.getAssigneeMemberId());
        map.put("dueAt", Objects.isNull(task.getDueAt()) ? null : task.getDueAt().toString());
        return map;
    }

    private List<ActionPermissionVO> actionPermissions(WorkTask task) {
        boolean done = "DONE".equals(task.getStatus()) || "CANCELED".equals(task.getStatus());
        return List.of(new ActionPermissionVO("edit", "编辑", !done, done ? "Finished task cannot be edited." : null,
                        "workTaskMutationDrawer"),
                new ActionPermissionVO("comment", "评论", true, null, "workTaskCommentDrawer"),
                new ActionPermissionVO("delete", "删除", !done, done ? "Finished task cannot be deleted." : null,
                        "workTaskDeleteConfirm"));
    }

    private TaskLifecycleVO lifecycle(String taskType) {
        return PROJECT.equals(taskType)
                ? new TaskLifecycleVO("projectTaskLifecycle", List.of("TODO", "DOING", "REVIEW", "DONE", "OVERDUE"),
                "PLAIN")
                : new TaskLifecycleVO("plainTaskLifecycle", List.of("TODO", "DOING", "DONE", "CANCELED"),
                "PROJECT");
    }

    private String warningLevel(WorkTask task) {
        if (task.getDueAt() == null || "DONE".equals(task.getStatus())) {
            return null;
        }
        if (task.getDueAt().isBefore(LocalDateTime.now())) {
            return "HIGH";
        }
        long hours = ChronoUnit.HOURS.between(LocalDateTime.now(), task.getDueAt());
        return hours <= 24 ? "MEDIUM" : null;
    }

    private String sourceSummaryJson(List<String> sourceIds) {
        if (Objects.isNull(sourceIds) || sourceIds.isEmpty()) {
            return writeJson(Map.of("mode", "MANUAL", "sourceIds", List.of()));
        }
        return writeJson(Map.of("mode", "MANUAL_SELECTED", "sourceIds", sourceIds));
    }

    private String safeTaskType(String taskType) {
        return PROJECT.equalsIgnoreCase(safeText(taskType, PROJECT)) ? PROJECT : PLAIN;
    }

    private int pageNo(PageRequest request) {
        return request == null || request.pageNo() <= 0 ? 1 : request.pageNo();
    }

    private int pageSize(PageRequest request) {
        return request == null || request.pageSize() <= 0 ? 20 : request.pageSize();
    }

    private int safeInt(Integer value) {
        return Objects.isNull(value) ? 0 : value;
    }

    private List<String> safeList(List<String> value) {
        return Objects.isNull(value) ? List.of() : value;
    }

    private String safeText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String stringId(Long value) {
        return Objects.isNull(value) ? null : String.valueOf(value);
    }

    private Long parseLong(String value, Long fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return Objects.nonNull(value) && value.isTextual() ? value.asText() : null;
    }

    private record WorkContext(Long systemId, Long tenantId, Long memberId) {
    }
}
