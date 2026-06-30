package com.unique.examine.aiwork.manage.work;

import com.unique.examine.aiwork.manage.work.WorkManagementModels.CommentCreateRequest;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.DailyReportAutoDraftVO;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.DailyReportCreateRequest;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.DailyReportSearchRequest;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.DailyReportVO;
import com.unique.examine.aiwork.manage.work.HomePageConfigModels.HomePageConfigUpdateRequest;
import com.unique.examine.aiwork.manage.work.HomePageConfigModels.HomePageConfigVO;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.KanbanQueryRequest;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.KanbanResultVO;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.ProjectCreateRequest;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.ProjectSearchRequest;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.TaskCommentVO;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.TaskCreateRequest;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.TaskEventVO;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.TaskMutationResult;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.TaskSearchRequest;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.TaskUpdateRequest;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.WorkConfigPublishCheckResult;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.WorkConfigUpdateRequest;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.WorkConfigVO;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.WorkDashboardVO;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.WorkProjectVO;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.WorkTaskVO;
import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.PageRequest;
import com.unique.examine.core.api.PageResult;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工作管理 API 控制器。
 */
@RestController
public class WorkManagementController {

    private final WorkManagementService workManagementService;
    private final HomePageConfigService homePageConfigService;

    public WorkManagementController(WorkManagementService workManagementService,
                                    HomePageConfigService homePageConfigService) {
        this.workManagementService = workManagementService;
        this.homePageConfigService = homePageConfigService;
    }

    /**
     * 查询工作管理仪表盘。
     *
     * @param systemId 系统 ID
     * @return 工作仪表盘
     */
    @GetMapping("/api/v1/systems/{systemId}/work/dashboard")
    public ApiResponse<WorkDashboardVO> dashboard(@PathVariable String systemId) {
        return ApiResponse.success(workManagementService.dashboard(systemId));
    }

    /**
     * 查询项目列表。
     *
     * @param systemId 系统 ID
     * @param pageNo 页码
     * @param pageSize 每页数量
     * @param request 查询条件
     * @return 项目分页
     */
    @PostMapping("/api/v1/systems/{systemId}/work/projects/search")
    public ApiResponse<PageResult<WorkProjectVO>> searchProjects(@PathVariable String systemId,
                                                                 @RequestParam(defaultValue = "1") int pageNo,
                                                                 @RequestParam(defaultValue = "20") int pageSize,
                                                                 @RequestBody(required = false)
                                                                 ProjectSearchRequest request) {
        return ApiResponse.success(workManagementService.searchProjects(systemId,
                pageRequest(pageNo, pageSize, request == null ? null : request.keyword()), request));
    }

    @PostMapping("/api/v1/systems/{systemId}/work/projects")
    public ApiResponse<WorkProjectVO> createProject(@PathVariable String systemId,
                                                    @RequestBody(required = false)
                                                    ProjectCreateRequest request) {
        return ApiResponse.success(workManagementService.createProject(systemId, request));
    }

    /**
     * 查询项目任务列表。
     *
     * @param systemId 系统 ID
     * @param pageNo 页码
     * @param pageSize 每页数量
     * @param request 查询条件
     * @return 项目任务分页
     */
    @PostMapping("/api/v1/systems/{systemId}/work/project-tasks/search")
    public ApiResponse<PageResult<WorkTaskVO>> searchProjectTasks(@PathVariable String systemId,
                                                                  @RequestParam(defaultValue = "1") int pageNo,
                                                                  @RequestParam(defaultValue = "20") int pageSize,
                                                                  @RequestBody(required = false)
                                                                  TaskSearchRequest request) {
        return ApiResponse.success(workManagementService.searchProjectTasks(systemId,
                pageRequest(pageNo, pageSize, request == null ? null : request.keyword()), request));
    }

    /**
     * 创建项目任务。
     *
     * @param systemId 系统 ID
     * @param request 创建请求
     * @return 创建结果
     */
    @PostMapping("/api/v1/systems/{systemId}/work/project-tasks")
    public ApiResponse<TaskMutationResult> createProjectTask(@PathVariable String systemId,
                                                             @RequestBody(required = false)
                                                             TaskCreateRequest request) {
        return ApiResponse.success(workManagementService.createProjectTask(systemId, request));
    }

    /**
     * 更新项目任务。
     *
     * @param systemId 系统 ID
     * @param taskId 任务 ID
     * @param request 更新请求
     * @return 更新结果
     */
    @PatchMapping("/api/v1/systems/{systemId}/work/project-tasks/{taskId}")
    public ApiResponse<TaskMutationResult> updateProjectTask(@PathVariable String systemId,
                                                             @PathVariable String taskId,
                                                             @RequestBody(required = false)
                                                             TaskUpdateRequest request) {
        return ApiResponse.success(workManagementService.updateProjectTask(systemId, taskId, request));
    }

    /**
     * 查询普通任务列表。
     *
     * @param systemId 系统 ID
     * @param pageNo 页码
     * @param pageSize 每页数量
     * @param request 查询条件
     * @return 普通任务分页
     */
    @PostMapping("/api/v1/systems/{systemId}/work/plain-tasks/search")
    public ApiResponse<PageResult<WorkTaskVO>> searchPlainTasks(@PathVariable String systemId,
                                                                @RequestParam(defaultValue = "1") int pageNo,
                                                                @RequestParam(defaultValue = "20") int pageSize,
                                                                @RequestBody(required = false)
                                                                TaskSearchRequest request) {
        return ApiResponse.success(workManagementService.searchPlainTasks(systemId,
                pageRequest(pageNo, pageSize, request == null ? null : request.keyword()), request));
    }

    /**
     * 创建普通任务。
     *
     * @param systemId 系统 ID
     * @param request 创建请求
     * @return 创建结果
     */
    @PostMapping("/api/v1/systems/{systemId}/work/plain-tasks")
    public ApiResponse<TaskMutationResult> createPlainTask(@PathVariable String systemId,
                                                           @RequestBody(required = false)
                                                           TaskCreateRequest request) {
        return ApiResponse.success(workManagementService.createPlainTask(systemId, request));
    }

    /**
     * 更新普通任务。
     *
     * @param systemId 系统 ID
     * @param taskId 任务 ID
     * @param request 更新请求
     * @return 更新结果
     */
    @PatchMapping("/api/v1/systems/{systemId}/work/plain-tasks/{taskId}")
    public ApiResponse<TaskMutationResult> updatePlainTask(@PathVariable String systemId,
                                                           @PathVariable String taskId,
                                                           @RequestBody(required = false)
                                                           TaskUpdateRequest request) {
        return ApiResponse.success(workManagementService.updatePlainTask(systemId, taskId, request));
    }

    /**
     * 查询任务看板。
     *
     * @param systemId 系统 ID
     * @param request 看板请求
     * @return 看板结果
     */
    @PostMapping("/api/v1/systems/{systemId}/work/kanban/query")
    public ApiResponse<KanbanResultVO> queryKanban(@PathVariable String systemId,
                                                   @RequestBody(required = false)
                                                   KanbanQueryRequest request) {
        return ApiResponse.success(workManagementService.queryKanban(systemId, request));
    }

    /**
     * 查询我的日报列表。
     *
     * @param systemId 系统 ID
     * @param pageNo 页码
     * @param pageSize 每页数量
     * @param request 查询条件
     * @return 日报分页
     */
    @PostMapping("/api/v1/systems/{systemId}/work/daily-reports/search")
    public ApiResponse<PageResult<DailyReportVO>> searchDailyReports(@PathVariable String systemId,
                                                                     @RequestParam(defaultValue = "1") int pageNo,
                                                                     @RequestParam(defaultValue = "20") int pageSize,
                                                                     @RequestBody(required = false)
                                                                     DailyReportSearchRequest request) {
        return ApiResponse.success(workManagementService.searchDailyReports(systemId,
                pageRequest(pageNo, pageSize, request == null ? null : request.keyword()), request));
    }

    /**
     * 创建或提交日报。
     *
     * @param systemId 系统 ID
     * @param request 创建请求
     * @return 日报结果
     */
    @PostMapping("/api/v1/systems/{systemId}/work/daily-reports")
    public ApiResponse<DailyReportVO> createDailyReport(@PathVariable String systemId,
                                                        @RequestBody(required = false)
                                                        DailyReportCreateRequest request) {
        return ApiResponse.success(workManagementService.createDailyReport(systemId, request));
    }

    /**
     * 自动生成日报草稿。
     *
     * @param systemId 系统 ID
     * @return 自动草稿
     */
    @PostMapping("/api/v1/systems/{systemId}/work/daily-reports/auto-draft")
    public ApiResponse<DailyReportAutoDraftVO> autoDraftDailyReport(@PathVariable String systemId) {
        return ApiResponse.success(workManagementService.autoDraftDailyReport(systemId));
    }

    /**
     * 查询工作配置。
     *
     * @param systemId 系统 ID
     * @return 工作配置
     */
    @GetMapping("/api/v1/systems/{systemId}/work/config")
    public ApiResponse<WorkConfigVO> config(@PathVariable String systemId) {
        return ApiResponse.success(workManagementService.config(systemId));
    }

    /**
     * 保存工作配置。
     *
     * @param systemId 系统 ID
     * @param request 保存请求
     * @return 保存后的工作配置
     */
    @PatchMapping("/api/v1/systems/{systemId}/work/config")
    public ApiResponse<WorkConfigVO> updateConfig(@PathVariable String systemId,
                                                  @RequestBody(required = false)
                                                  WorkConfigUpdateRequest request) {
        return ApiResponse.success(workManagementService.updateConfig(systemId, request));
    }

    /**
     * 执行工作配置发布检查。
     *
     * @param systemId 系统 ID
     * @return 发布检查结果
     */
    @PostMapping("/api/v1/systems/{systemId}/work/config/publish-check")
    public ApiResponse<WorkConfigPublishCheckResult> publishCheck(@PathVariable String systemId) {
        return ApiResponse.success(workManagementService.publishCheck(systemId));
    }

    /**
     * 查询运行态首页配置。
     *
     * @param systemId 系统 ID
     * @return 首页配置
     */
    @GetMapping("/api/v1/systems/{systemId}/work/home-page")
    public ApiResponse<HomePageConfigVO> homePageConfig(@PathVariable String systemId) {
        return ApiResponse.success(homePageConfigService.config(systemId));
    }

    /**
     * 查询后台首页设计配置。
     *
     * @param systemId 系统 ID
     * @return 首页配置
     */
    @GetMapping("/api/v1/systems/{systemId}/work/home-page-config")
    public ApiResponse<HomePageConfigVO> homePageAdminConfig(@PathVariable String systemId) {
        return ApiResponse.success(homePageConfigService.config(systemId));
    }

    /**
     * 保存后台首页设计配置。
     *
     * @param systemId 系统 ID
     * @param request 保存请求
     * @return 保存后的首页配置
     */
    @PatchMapping("/api/v1/systems/{systemId}/work/home-page-config")
    public ApiResponse<HomePageConfigVO> updateHomePageConfig(@PathVariable String systemId,
                                                              @RequestBody(required = false)
                                                              HomePageConfigUpdateRequest request) {
        return ApiResponse.success(homePageConfigService.updateConfig(systemId, request));
    }

    /**
     * 执行后台首页发布检查。
     *
     * @param systemId 系统 ID
     * @return 发布检查结果
     */
    @PostMapping("/api/v1/systems/{systemId}/work/home-page-config/publish-check")
    public ApiResponse<WorkConfigPublishCheckResult> homePagePublishCheck(@PathVariable String systemId) {
        return ApiResponse.success(homePageConfigService.publishCheck(systemId));
    }

    /**
     * 查询任务评论。
     *
     * @param systemId 系统 ID
     * @param taskId 任务 ID
     * @return 评论列表
     */
    @GetMapping("/api/v1/systems/{systemId}/work/tasks/{taskId}/comments")
    public ApiResponse<List<TaskCommentVO>> comments(@PathVariable String systemId,
                                                     @PathVariable String taskId) {
        return ApiResponse.success(workManagementService.comments(systemId, taskId));
    }

    /**
     * 新增任务评论。
     *
     * @param systemId 系统 ID
     * @param taskId 任务 ID
     * @param request 评论请求
     * @return 评论结果
     */
    @PostMapping("/api/v1/systems/{systemId}/work/tasks/{taskId}/comments")
    public ApiResponse<TaskCommentVO> addComment(@PathVariable String systemId,
                                                 @PathVariable String taskId,
                                                 @RequestBody(required = false)
                                                 CommentCreateRequest request) {
        return ApiResponse.success(workManagementService.addComment(systemId, taskId, request));
    }

    /**
     * 查询任务事件。
     *
     * @param systemId 系统 ID
     * @param taskId 任务 ID
     * @return 事件列表
     */
    @GetMapping("/api/v1/systems/{systemId}/work/tasks/{taskId}/events")
    public ApiResponse<List<TaskEventVO>> events(@PathVariable String systemId,
                                                 @PathVariable String taskId) {
        return ApiResponse.success(workManagementService.events(systemId, taskId));
    }

    private PageRequest pageRequest(int pageNo, int pageSize, String keyword) {
        return new PageRequest(pageNo, pageSize, keyword, List.of(), List.of());
    }
}
