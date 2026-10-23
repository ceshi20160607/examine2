package com.unique.unexamine.work.manage.controller;

import com.unique.unexamine.work.manage.service.WorkProjectService;
import com.unique.unexamine.work.manage.service.WorkTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;

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
    public WorkProjectService.ProjectView create(
            @RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,
            @RequestParam(required=false) Long systemId,
            @Valid @RequestBody WorkProjectService.CreateRequest request) {
        return service.create(token,systemId,tenantId,request);
    }

    /** 当前作用域内本人可见的项目分页列表。 */
    @GetMapping
    @Operation(summary = "我的工作项目")
    public WorkProjectService.ProjectPage list(
            @RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,
            @RequestParam(required=false) Long systemId,
            @RequestParam(defaultValue="1") long page,
            @RequestParam(defaultValue="20") long size) {
        return service.queryPageList(token,systemId,tenantId,page,size);
    }

    /** 当前作用域内本人可见的项目详情。 */
    @GetMapping("/{projectId}")
    @Operation(summary = "工作项目详情")
    public WorkProjectService.ProjectView detail(
            @RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,
            @RequestParam(required=false) Long systemId,@PathVariable Long projectId) {
        return service.queryById(token,systemId,tenantId,projectId);
    }

    /** 查看当前项目任务进度及临期、逾期等预警数量。 */
    @GetMapping("/{projectId}/task-summary")
    @Operation(summary = "项目任务统计和预警")
    public WorkProjectService.TaskSummary taskSummary(
            @RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,
            @RequestParam(required=false) Long systemId,@PathVariable Long projectId) {
        return service.taskSummary(token,systemId,tenantId,projectId);
    }

    /** 项目负责人维护项目名称、说明和期限。 */
    @PutMapping("/{projectId}")
    @Operation(summary = "编辑工作项目")
    public WorkProjectService.ProjectView edit(
            @RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,
            @RequestParam(required=false) Long systemId,@PathVariable Long projectId,
            @Valid @RequestBody WorkProjectService.EditRequest request) {
        return service.edit(token,systemId,tenantId,projectId,request);
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
    public WorkProjectService.MemberView addMember(
            @RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,
            @RequestParam(required=false) Long systemId,@PathVariable Long projectId,
            @Valid @RequestBody WorkProjectService.MemberRequest request) {
        return service.addMember(token,systemId,tenantId,projectId,request);
    }

    /** 列出项目有效参与人。 */
    @GetMapping("/{projectId}/members")
    @Operation(summary = "项目成员列表")
    public List<WorkProjectService.MemberView> members(
            @RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,
            @RequestParam(required=false) Long systemId,@PathVariable Long projectId) {
        return service.members(token,systemId,tenantId,projectId);
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
    public WorkProjectService.GroupView createGroup(
            @RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,
            @RequestParam(required=false) Long systemId,@PathVariable Long projectId,
            @Valid @RequestBody WorkProjectService.GroupRequest request) {
        return service.createGroup(token,systemId,tenantId,projectId,request);
    }

    /** 按保存的项目内顺序读取任务组。 */
    @GetMapping("/{projectId}/groups")
    @Operation(summary = "项目任务组列表")
    public List<WorkProjectService.GroupView> groups(
            @RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,
            @RequestParam(required=false) Long systemId,@PathVariable Long projectId) {
        return service.groups(token,systemId,tenantId,projectId);
    }

    /** 项目负责人调整任务组名称和期限。 */
    @PutMapping("/{projectId}/groups/{groupId}")
    @Operation(summary = "编辑项目任务组")
    public WorkProjectService.GroupView editGroup(
            @RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,
            @RequestParam(required=false) Long systemId,@PathVariable Long projectId,
            @PathVariable Long groupId,
            @Valid @RequestBody WorkProjectService.GroupEditRequest request) {
        return service.editGroup(token,systemId,tenantId,projectId,groupId,request);
    }

    /** 项目负责人在项目或其任务组下创建任务。 */
    @PostMapping("/{projectId}/tasks")
    @Operation(summary = "创建项目任务")
    public WorkTaskService.TaskView createTask(
            @RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,
            @RequestParam(required=false) Long systemId,@PathVariable Long projectId,
            @Valid @RequestBody WorkTaskService.ProjectCreateRequest request) {
        return tasks.createProjectTask(token,systemId,tenantId,projectId,request);
    }

    /** 项目成员读取本项目任务，状态筛选与普通任务一致。 */
    @GetMapping("/{projectId}/tasks")
    @Operation(summary = "项目任务列表")
    public WorkTaskService.TaskPage tasks(
            @RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,
            @RequestParam(required=false) Long systemId,@PathVariable Long projectId,
            @RequestParam(required=false) Long groupId,
            @RequestParam(required=false) String status,
            @RequestParam(defaultValue="1") long page,@RequestParam(defaultValue="20") long size) {
        return tasks.projectTasks(token,systemId,tenantId,projectId,groupId,status,page,size);
    }
}
