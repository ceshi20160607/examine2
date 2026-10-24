package com.unique.unexamine.work.manage.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.unexamine.core.bo.SearchBO;
import com.unique.unexamine.core.common.Result;
import com.unique.unexamine.work.manage.bo.WorkTaskSearchBO;
import com.unique.unexamine.work.manage.dto.WorkProjectDto.CreateRequest;
import com.unique.unexamine.work.manage.dto.WorkProjectDto.EditRequest;
import com.unique.unexamine.work.manage.dto.WorkProjectDto.GroupEditRequest;
import com.unique.unexamine.work.manage.dto.WorkProjectDto.GroupRequest;
import com.unique.unexamine.work.manage.dto.WorkProjectDto.GroupView;
import com.unique.unexamine.work.manage.dto.WorkProjectDto.MemberRequest;
import com.unique.unexamine.work.manage.dto.WorkProjectDto.MemberView;
import com.unique.unexamine.work.manage.dto.WorkProjectDto.ProjectView;
import com.unique.unexamine.work.manage.dto.WorkProjectDto.TaskSummary;
import com.unique.unexamine.work.manage.dto.WorkTaskDto.ProjectCreateRequest;
import com.unique.unexamine.work.manage.dto.WorkTaskDto.TaskView;
import com.unique.unexamine.work.manage.service.WorkProjectService;
import com.unique.unexamine.work.manage.service.WorkTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 工作项目 HTTP 入口；成员、作用域与任务组规则由业务 Service 执行。 */
@RestController
@RequestMapping("/api/work/projects")
@Tag(name = "工作项目")
public class WorkProjectController {
    private final WorkProjectService service;
    private final WorkTaskService tasks;

    /** 注入项目业务服务。 */
    public WorkProjectController(WorkProjectService service,WorkTaskService tasks) {
        this.service=service;this.tasks=tasks;
    }

    /** 新建平台或系统租户项目。 */
    @PostMapping
    @Operation(summary = "创建工作项目")
    public Result<ProjectView> create(
            @RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,
            @RequestParam(required=false) Long systemId,
            @Valid @RequestBody CreateRequest request) {
        return Result.ok(service.create(token,systemId,tenantId,request));
    }

    /** 当前作用域内本人可见的项目分页列表。 */
    @GetMapping
    @Operation(summary = "我的工作项目")
    public Result<Page<ProjectView>> list(
            @RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,
            @RequestParam(required=false) Long systemId,
            @ModelAttribute SearchBO search) {
        return Result.ok(service.queryPageList(token,systemId,tenantId,search));
    }

    /** 当前作用域内本人可见的项目详情。 */
    @GetMapping("/{projectId}")
    @Operation(summary = "工作项目详情")
    public Result<ProjectView> detail(
            @RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,
            @RequestParam(required=false) Long systemId,@PathVariable Long projectId) {
        return Result.ok(service.queryById(token,systemId,tenantId,projectId));
    }

    /** 查看当前项目任务进度及临期、逾期等预警数量。 */
    @GetMapping("/{projectId}/task-summary")
    @Operation(summary = "项目任务统计和预警")
    public Result<TaskSummary> taskSummary(
            @RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,
            @RequestParam(required=false) Long systemId,@PathVariable Long projectId) {
        return Result.ok(service.taskSummary(token,systemId,tenantId,projectId));
    }

    /** 项目负责人维护项目名称、说明和期限。 */
    @PutMapping("/{projectId}")
    @Operation(summary = "编辑工作项目")
    public Result<ProjectView> edit(
            @RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,
            @RequestParam(required=false) Long systemId,@PathVariable Long projectId,
            @Valid @RequestBody EditRequest request) {
        return Result.ok(service.edit(token,systemId,tenantId,projectId,request));
    }

    /** 归档项目并保留所属任务与历史。 */
    @DeleteMapping("/{projectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "归档工作项目")
    public void archive(
            @RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,
            @RequestParam(required=false) Long systemId,@PathVariable Long projectId) {
        service.archive(token,systemId,tenantId,projectId);
    }

    /** 增加项目协作或观察成员。 */
    @PostMapping("/{projectId}/members")
    @Operation(summary = "添加项目成员")
    public Result<MemberView> addMember(
            @RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,
            @RequestParam(required=false) Long systemId,@PathVariable Long projectId,
            @Valid @RequestBody MemberRequest request) {
        return Result.ok(service.addMember(token,systemId,tenantId,projectId,request));
    }

    /** 列出项目有效参与人。 */
    @GetMapping("/{projectId}/members")
    @Operation(summary = "项目成员列表")
    public Result<List<MemberView>> members(
            @RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,
            @RequestParam(required=false) Long systemId,@PathVariable Long projectId) {
        return Result.ok(service.members(token,systemId,tenantId,projectId));
    }

    /** 停用项目成员关系；待处理任务须先转交。 */
    @DeleteMapping("/{projectId}/members/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "移除项目成员")
    public void removeMember(
            @RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,
            @RequestParam(required=false) Long systemId,@PathVariable Long projectId,
            @PathVariable Long memberId) {
        service.removeMember(token,systemId,tenantId,projectId,memberId);
    }

    /** 在项目下创建任务组。 */
    @PostMapping("/{projectId}/groups")
    @Operation(summary = "创建项目任务组")
    public Result<GroupView> createGroup(
            @RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,
            @RequestParam(required=false) Long systemId,@PathVariable Long projectId,
            @Valid @RequestBody GroupRequest request) {
        return Result.ok(service.createGroup(token,systemId,tenantId,projectId,request));
    }

    /** 按保存的项目内顺序读取任务组。 */
    @GetMapping("/{projectId}/groups")
    @Operation(summary = "项目任务组列表")
    public Result<List<GroupView>> groups(
            @RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,
            @RequestParam(required=false) Long systemId,@PathVariable Long projectId) {
        return Result.ok(service.groups(token,systemId,tenantId,projectId));
    }

    /** 项目负责人调整任务组名称和期限。 */
    @PutMapping("/{projectId}/groups/{groupId}")
    @Operation(summary = "编辑项目任务组")
    public Result<GroupView> editGroup(
            @RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,
            @RequestParam(required=false) Long systemId,@PathVariable Long projectId,
            @PathVariable Long groupId,
            @Valid @RequestBody GroupEditRequest request) {
        return Result.ok(service.editGroup(token,systemId,tenantId,projectId,groupId,request));
    }

    /** 项目负责人在项目或其任务组下创建任务。 */
    @PostMapping("/{projectId}/tasks")
    @Operation(summary = "创建项目任务")
    public Result<TaskView> createTask(
            @RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,
            @RequestParam(required=false) Long systemId,@PathVariable Long projectId,
            @Valid @RequestBody ProjectCreateRequest request) {
        return Result.ok(tasks.createProjectTask(token,systemId,tenantId,projectId,request));
    }

    /** 项目成员读取本项目任务，状态筛选与普通任务一致。 */
    @GetMapping("/{projectId}/tasks")
    @Operation(summary = "项目任务列表")
    public Result<Page<TaskView>> tasks(
            @RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,
            @RequestParam(required=false) Long systemId,@PathVariable Long projectId,
            @ModelAttribute WorkTaskSearchBO search) {
        return Result.ok(tasks.projectTasks(token,systemId,tenantId,projectId,search));
    }
}
