package com.unique.unexamine.work.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.work.manage.service.WorkScopeService.Assignee;
import com.unique.unexamine.work.manage.service.WorkScopeService.Scope;
import com.unique.unexamine.work.base.entity.UnWorkTask;
import com.unique.unexamine.work.base.entity.UnWorkTaskHistory;
import com.unique.unexamine.work.base.entity.UnWorkTaskMember;
import com.unique.unexamine.work.base.entity.UnWorkProject;
import com.unique.unexamine.work.base.service.IUnWorkTaskHistoryService;
import com.unique.unexamine.work.base.service.IUnWorkTaskMemberService;
import com.unique.unexamine.work.base.service.IUnWorkTaskService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 普通与项目任务共用状态、成员和历史处理，项目任务额外遵守项目成员授权。 */
@Service
public class WorkTaskService {
    /** 按有效任务成员关系判定可见性，账号 ID 使用 MyBatis 占位参数。 */
    private static final String MEMBER_EXISTS_SQL="EXISTS (SELECT 1 FROM un_work_task_member tm "
            +"WHERE tm.deleted_time IS NULL AND tm.task_id = un_work_task.id AND tm.account_id = {0} AND tm.status = 'ACTIVE')";
    private final WorkScopeService workScope;
    private final WorkProjectService projects;
    private final IUnWorkTaskService tasks;
    private final IUnWorkTaskHistoryService histories;
    private final IUnWorkTaskMemberService taskMembers;
    private final ObjectMapper json;

    /** 普通任务可先只填一句标题，描述和截止时间可后续补充。 */
    public record CreateRequest(@NotBlank @Size(max=200) String title,
                                @Size(max=10000) String description, LocalDateTime dueTime) {}
    /** 项目任务可指定所属任务组及已有项目成员作为负责人。 */
    public record ProjectCreateRequest(@NotBlank @Size(max=200) String title,
            @Size(max=10000) String description,LocalDateTime dueTime,
            Long taskGroupId,Long ownerAccountId) {}
    /** 状态变更须给出目标状态；阻塞和取消原因保存在操作历史中。 */
    public record StatusRequest(@NotBlank String status, @Size(max=2000) String reason) {}
    /** 编辑普通任务的基础内容；截止时间为空表示清除。 */
    public record EditRequest(@NotBlank @Size(max=200) String title,
                              @Size(max=10000) String description, LocalDateTime dueTime) {}
    /** 指派和转交的目标必须是当前作用域内可用的账号。 */
    public record TransferRequest(@NotNull Long ownerAccountId, @Size(max=2000) String reason) {}
    /** 任务评论保存在同一操作历史中，不另建重复的评论表。 */
    public record CommentRequest(@NotBlank @Size(max=10000) String content) {}
    /** 负责人提交晚于当前截止时间的新日期及延期原因。 */
    public record DeferRequest(@NotNull LocalDateTime dueTime,
                               @NotBlank @Size(max=2000) String reason) {}
    /** 创建人按当前待处理申请的历史 ID 审批，避免处理过期申请。 */
    public record DeferDecision(@NotNull Long requestId, @NotNull Boolean approved,
                                @Size(max=2000) String comment) {}
    /** 延期申请记录提交人、原截止时间和申请时间。 */
    public record DeferView(Long requestId,Long applicantAccountId,LocalDateTime beforeDueTime,
                            LocalDateTime requestedDueTime,String reason,LocalDateTime requestTime) {}
    /** 参与人身份分为协作、关注与验收。 */
    public record MemberRequest(@NotNull Long accountId,@NotBlank String memberType) {}
    /** 任务参与人展示平台账号或所属系统的成员名称。 */
    public record MemberView(Long id,Long accountId,String displayName,String memberType) {}
    /** 逾期是截止时间计算结果，不覆盖任务本身的处理状态。 */
    public record TaskView(Long id, String scopeType, Long systemId, Long tenantId,
                           Long projectId,Long taskGroupId,String title,
                           String description, Long ownerAccountId, Long createAccountId, String status,
                           boolean overdue, BigDecimal progressPercent, LocalDateTime dueTime,
                           LocalDateTime completeTime, LocalDateTime createTime) {}
    /** 本人可见任务的分页结果，供列表、看板和日历复用同一数据源。 */
    public record TaskPage(long total, long current, long size, List<TaskView> records) {}
    /** 操作历史记录真实执行者及变更前后的状态。 */
    public record HistoryView(Long id, Long operatorAccountId, String operationCode,
                              String beforeJson, String afterJson, String content, LocalDateTime createTime) {}

    private enum Status { NOT_STARTED, IN_PROGRESS, BLOCKED, PENDING_CONFIRMATION, DONE, CANCELLED }
    private enum MemberType { COLLABORATOR, FOLLOWER, ACCEPTOR }
    /** 两种任务作用域共用同一张任务表和历史表。 */
    public WorkTaskService(WorkScopeService workScope,WorkProjectService projects,
            IUnWorkTaskService tasks, IUnWorkTaskHistoryService histories,
            IUnWorkTaskMemberService taskMembers,ObjectMapper json) {
        this.workScope=workScope; this.projects=projects; this.tasks=tasks; this.histories=histories;
        this.taskMembers=taskMembers;
        this.json=json;
    }

    /** 快速新建普通任务；项目和任务组为空，负责人默认当前账号。 */
    @Transactional(rollbackFor=Exception.class)
    public TaskView create(String token, Long systemId, Long tenantId, CreateRequest request) {
        Scope scope=scope(token,systemId,tenantId);
        UnWorkTask task=new UnWorkTask();
        task.setScopeType(scope.type()); task.setSystemId(scope.systemId()); task.setTenantId(scope.tenantId());
        task.setTitle(request.title().trim()); task.setDescription(request.description());
        task.setOwnerAccountId(scope.actorId()); task.setCreateAccountId(scope.actorId());
        task.setDueTime(request.dueTime()); task.setStatus(Status.NOT_STARTED.name());
        task.setProgressPercent(BigDecimal.ZERO);
        task.setCreateTime(LocalDateTime.now());
        tasks.addOrUpdate(task);
        history(task.getId(),scope.actorId(),"CREATE",null,Map.of("status",task.getStatus()),null);
        return view(task);
    }

    /** 项目负责人新建任务；项目、任务组、负责人在同一业务作用域内确定。 */
    @Transactional(rollbackFor=Exception.class)
    public TaskView createProjectTask(String token,Long systemId,Long tenantId,Long projectId,
                                      ProjectCreateRequest request) {
        Scope scope=scope(token,systemId,tenantId);
        UnWorkProject project=projects.visible(scope,projectId,true);
        projects.requireOwner(scope,project);
        projects.requireGroup(projectId,request.taskGroupId());
        Long ownerId=request.ownerAccountId()==null?scope.actorId():request.ownerAccountId();
        if (!ownerId.equals(scope.actorId())) {
            workScope.assignee(scope,ownerId);
            projects.requireMember(project,ownerId);
        }
        UnWorkTask task=new UnWorkTask();
        task.setScopeType(scope.type());task.setSystemId(scope.systemId());task.setTenantId(scope.tenantId());
        task.setProjectId(projectId);task.setTaskGroupId(request.taskGroupId());
        task.setTitle(request.title().trim());task.setDescription(request.description());
        task.setOwnerAccountId(ownerId);task.setCreateAccountId(scope.actorId());
        task.setDueTime(request.dueTime());task.setStatus(Status.NOT_STARTED.name());
        task.setProgressPercent(BigDecimal.ZERO);task.setCreateTime(LocalDateTime.now());
        tasks.addOrUpdate(task);
        history(task.getId(),scope.actorId(),"CREATE",null,Map.of("status",task.getStatus()),null);
        projects.refreshProgress(projectId,request.taskGroupId());
        return view(task);
    }

    /** 项目成员分页查看本项目任务，供列表、看板和日历使用同一数据。 */
    public TaskPage projectTasks(String token,Long systemId,Long tenantId,Long projectId,
                                 Long groupId,String status,long page,long size) {
        Scope scope=scope(token,systemId,tenantId);
        projects.visible(scope,projectId,false);
        if (page<1 || size<1 || size>100) throw new IllegalArgumentException("任务分页范围无效");
        var where=baseScope(scope).eq(UnWorkTask::getProjectId,projectId);
        if (groupId!=null) where.eq(UnWorkTask::getTaskGroupId,groupId);
        if (status!=null && !status.isBlank()) {
            if ("OVERDUE".equals(status)) where.lt(UnWorkTask::getDueTime,LocalDateTime.now())
                    .notIn(UnWorkTask::getStatus,Status.DONE.name(),Status.CANCELLED.name());
            else where.eq(UnWorkTask::getStatus,status(status).name());
        }
        var result=tasks.page(Page.of(page,size),where.orderByDesc(UnWorkTask::getCreateTime)
                .orderByDesc(UnWorkTask::getId));
        return new TaskPage(result.getTotal(),result.getCurrent(),result.getSize(),
                result.getRecords().stream().map(this::view).toList());
    }

    /** 创建人或负责人可编辑基础内容；只有创建人能直接调整截止时间。 */
    @Transactional(rollbackFor=Exception.class)
    public TaskView edit(String token,Long systemId,Long tenantId,Long taskId,EditRequest request) {
        Scope scope=scope(token,systemId,tenantId);
        UnWorkTask task=visible(scope,taskId,true);
        requireManager(scope,task);
        if (Set.of(Status.DONE.name(),Status.CANCELLED.name()).contains(task.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"已完成或取消任务不能编辑");
        if (!Objects.equals(task.getDueTime(),request.dueTime())
                && !scope.actorId().equals(task.getCreateAccountId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"只有任务创建人可修改截止时间");
        Map<String,Object> before=details(task);
        String title=request.title().trim();
        if (Objects.equals(task.getTitle(),title)
                && Objects.equals(task.getDescription(),request.description())
                && Objects.equals(task.getDueTime(),request.dueTime())) return view(task);
        if (!Objects.equals(task.getDueTime(),request.dueTime()))
            cancelPendingDefer(task,scope.actorId(),"创建人已直接调整截止时间");
        tasks.update(new LambdaUpdateWrapper<UnWorkTask>().eq(UnWorkTask::getId,taskId)
                .set(UnWorkTask::getTitle,title).set(UnWorkTask::getDescription,request.description())
                .set(UnWorkTask::getDueTime,request.dueTime()));
        task.setTitle(title);task.setDescription(request.description());task.setDueTime(request.dueTime());
        history(taskId,scope.actorId(),"EDIT",before,details(task),null);
        return view(task);
    }

    /** 创建人或当前负责人可指派、转交给同一作用域的有效账号。 */
    @Transactional(rollbackFor=Exception.class)
    public TaskView transfer(String token,Long systemId,Long tenantId,Long taskId,TransferRequest request) {
        Scope scope=scope(token,systemId,tenantId);
        UnWorkTask task=visible(scope,taskId,true);
        requireManager(scope,task);
        if (Set.of(Status.DONE.name(),Status.CANCELLED.name()).contains(task.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"已完成或取消任务不能转交");
        if (request.ownerAccountId().equals(task.getOwnerAccountId()))
            throw new IllegalArgumentException("目标已是当前负责人");
        workScope.assignee(scope,request.ownerAccountId());
        if (task.getProjectId()!=null)
            projects.requireMember(projects.visible(scope,task.getProjectId(),false),request.ownerAccountId());
        cancelPendingDefer(task,scope.actorId(),"负责人转交后原延期申请失效");
        Long oldOwner=task.getOwnerAccountId();
        tasks.update(new LambdaUpdateWrapper<UnWorkTask>().eq(UnWorkTask::getId,taskId)
                .set(UnWorkTask::getOwnerAccountId,request.ownerAccountId()));
        history(taskId,scope.actorId(),"TRANSFER",Map.of("ownerAccountId",oldOwner),
                Map.of("ownerAccountId",request.ownerAccountId()),request.reason());
        task.setOwnerAccountId(request.ownerAccountId());
        return view(task);
    }

    /** 只查询本人创建或负责的普通任务，平台与系统租户各自隔离。 */
    public TaskPage queryPageList(String token, Long systemId, Long tenantId, String status, long page, long size) {
        Scope scope=scope(token,systemId,tenantId);
        if (page<1 || size<1 || size>100) throw new IllegalArgumentException("任务分页范围无效");
        var where=baseScope(scope).isNull(UnWorkTask::getProjectId)
                .and(q->q.eq(UnWorkTask::getOwnerAccountId,scope.actorId())
                        .or().eq(UnWorkTask::getCreateAccountId,scope.actorId())
                        .or().apply(MEMBER_EXISTS_SQL,scope.actorId()));
        if (status!=null && !status.isBlank()) {
            if ("OVERDUE".equals(status)) where.lt(UnWorkTask::getDueTime,LocalDateTime.now())
                    .notIn(UnWorkTask::getStatus,Status.DONE.name(),Status.CANCELLED.name());
            else where.eq(UnWorkTask::getStatus,status(status).name());
        }
        var result=tasks.page(Page.of(page,size),where.orderByDesc(UnWorkTask::getCreateTime)
                .orderByDesc(UnWorkTask::getId));
        return new TaskPage(result.getTotal(),result.getCurrent(),result.getSize(),
                result.getRecords().stream().map(this::view).toList());
    }

    /** 详情仅对创建人或负责人开放，错误作用域与无权统一按不存在返回。 */
    public TaskView queryById(String token, Long systemId, Long tenantId, Long taskId) {
        return view(visible(scope(token,systemId,tenantId),taskId,false));
    }

    /** 状态流转在行锁下核对，任务与操作历史同事务提交。 */
    @Transactional(rollbackFor=Exception.class)
    public TaskView changeStatus(String token, Long systemId, Long tenantId, Long taskId, StatusRequest request) {
        Scope scope=scope(token,systemId,tenantId);
        UnWorkTask task=visible(scope,taskId,true);
        Status before=status(task.getStatus()), target=status(request.status());
        boolean acceptor=target==Status.DONE && taskMembers.exists(new LambdaQueryWrapper<UnWorkTaskMember>()
                .eq(UnWorkTaskMember::getTaskId,taskId).eq(UnWorkTaskMember::getAccountId,scope.actorId())
                .eq(UnWorkTaskMember::getMemberType,MemberType.ACCEPTOR.name())
                .eq(UnWorkTaskMember::getStatus,"ACTIVE"));
        if (before==target || !allowed(before,target,scope.actorId().equals(task.getCreateAccountId()),
                scope.actorId().equals(task.getOwnerAccountId()),acceptor))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"当前任务状态不允许该操作人执行此流转");
        if (Set.of(Status.BLOCKED,Status.CANCELLED).contains(target)
                && (request.reason()==null || request.reason().isBlank()))
            throw new IllegalArgumentException("阻塞或取消任务必须说明原因");
        if (target==Status.DONE || target==Status.CANCELLED)
            cancelPendingDefer(task,scope.actorId(),"任务已结束，原延期申请失效");
        LocalDateTime completed=target==Status.DONE?LocalDateTime.now():null;
        BigDecimal progress=target==Status.DONE?BigDecimal.valueOf(100)
                :before==Status.DONE?BigDecimal.ZERO:task.getProgressPercent();
        boolean updated=tasks.update(new LambdaUpdateWrapper<UnWorkTask>()
                .eq(UnWorkTask::getId,taskId).eq(UnWorkTask::getStatus,before.name())
                .set(UnWorkTask::getStatus,target.name()).set(UnWorkTask::getCompleteTime,completed)
                .set(UnWorkTask::getProgressPercent,progress));
        if (!updated) throw new ResponseStatusException(HttpStatus.CONFLICT,"任务状态已变化，请刷新");
        history(taskId,scope.actorId(),"STATUS_CHANGE",Map.of("status",before.name()),
                Map.of("status",target.name()),request.reason());
        task.setStatus(target.name()); task.setCompleteTime(completed); task.setProgressPercent(progress);
        if (task.getProjectId()!=null && (target==Status.DONE || before==Status.DONE
                || target==Status.CANCELLED || before==Status.CANCELLED))
            projects.refreshProgress(task.getProjectId(),task.getTaskGroupId());
        return view(task);
    }

    /** 操作历史依附可见任务，不能靠猜测任务主键读取其他租户日志。 */
    public List<HistoryView> history(String token, Long systemId, Long tenantId, Long taskId) {
        visible(scope(token,systemId,tenantId),taskId,false);
        return histories.list(new LambdaQueryWrapper<UnWorkTaskHistory>()
                .eq(UnWorkTaskHistory::getTaskId,taskId).orderByAsc(UnWorkTaskHistory::getId))
                .stream().map(row->new HistoryView(row.getId(),row.getOperatorAccountId(),
                        row.getOperationCode(),row.getBeforeJson(),row.getAfterJson(),
                        row.getContent(),row.getCreateTime())).toList();
    }

    /** 当前创建人或负责人可以留下任务评论，历史时间和操作人由服务端确定。 */
    @Transactional(rollbackFor=Exception.class)
    public HistoryView comment(String token,Long systemId,Long tenantId,Long taskId,CommentRequest request) {
        Scope scope=scope(token,systemId,tenantId);
        visible(scope,taskId,false);
        String content=request.content().trim();
        return history(taskId,scope.actorId(),"COMMENT",null,null,content);
    }

    /** 创建人或负责人添加同一作用域的协作、关注或验收成员。 */
    @Transactional(rollbackFor=Exception.class)
    public MemberView addMember(String token,Long systemId,Long tenantId,Long taskId,MemberRequest request) {
        Scope scope=scope(token,systemId,tenantId);
        UnWorkTask task=visible(scope,taskId,true);
        requireManager(scope,task);
        MemberType type=memberType(request.memberType());
        Assignee assignee=workScope.assignee(scope,request.accountId());
        var account=assignee.account();
        UnWorkTaskMember row=taskMembers.getOne(new LambdaQueryWrapper<UnWorkTaskMember>()
                .eq(UnWorkTaskMember::getTaskId,taskId)
                .eq(UnWorkTaskMember::getAccountId,request.accountId())
                .eq(UnWorkTaskMember::getMemberType,type.name()),false);
        if (row!=null && "ACTIVE".equals(row.getStatus()))
            return new MemberView(row.getId(),account.getId(),assignee.displayName(),type.name());
        if (row==null) {
            row=new UnWorkTaskMember();row.setTaskId(taskId);row.setAccountId(account.getId());
            row.setMemberType(type.name());row.setCreateTime(LocalDateTime.now());
        }
        row.setStatus("ACTIVE");taskMembers.addOrUpdate(row);
        history(taskId,scope.actorId(),"MEMBER_ADD",null,
                Map.of("accountId",account.getId(),"memberType",type.name()),null);
        return new MemberView(row.getId(),account.getId(),assignee.displayName(),type.name());
    }

    /** 参与人可见任务及成员；名称从统一平台账号读取。 */
    public List<MemberView> members(String token,Long systemId,Long tenantId,Long taskId) {
        Scope scope=scope(token,systemId,tenantId);
        visible(scope,taskId,false);
        List<UnWorkTaskMember> rows=taskMembers.list(new LambdaQueryWrapper<UnWorkTaskMember>()
                .eq(UnWorkTaskMember::getTaskId,taskId).eq(UnWorkTaskMember::getStatus,"ACTIVE")
                .orderByAsc(UnWorkTaskMember::getId));
        if (rows.isEmpty()) return List.of();
        Map<Long,String> names=workScope.displayNames(scope,rows.stream()
                .map(UnWorkTaskMember::getAccountId).distinct().toList());
        return rows.stream().map(row->new MemberView(row.getId(),row.getAccountId(),
                names.get(row.getAccountId()),row.getMemberType())).toList();
    }

    /** 停用参与关系并保留操作历史，不物理删除成员记录。 */
    @Transactional(rollbackFor=Exception.class)
    public void removeMember(String token,Long systemId,Long tenantId,Long taskId,Long memberId) {
        Scope scope=scope(token,systemId,tenantId);
        UnWorkTask task=visible(scope,taskId,true);
        requireManager(scope,task);
        UnWorkTaskMember row=taskMembers.getOne(new LambdaQueryWrapper<UnWorkTaskMember>()
                .eq(UnWorkTaskMember::getId,memberId).eq(UnWorkTaskMember::getTaskId,taskId)
                .eq(UnWorkTaskMember::getStatus,"ACTIVE"),false);
        if (row==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"任务成员不存在");
        taskMembers.update(new LambdaUpdateWrapper<UnWorkTaskMember>()
                .eq(UnWorkTaskMember::getId,memberId).set(UnWorkTaskMember::getStatus,"INACTIVE"));
        history(taskId,scope.actorId(),"MEMBER_REMOVE",
                Map.of("accountId",row.getAccountId(),"memberType",row.getMemberType()),null,null);
    }

    /** 负责人提交延期申请；一个任务同一时间只保留一条待处理申请。 */
    @Transactional(rollbackFor=Exception.class)
    public DeferView requestDefer(String token,Long systemId,Long tenantId,Long taskId,DeferRequest request) {
        Scope scope=scope(token,systemId,tenantId);
        UnWorkTask task=visible(scope,taskId,true);
        if (!scope.actorId().equals(task.getOwnerAccountId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"只有负责人可以申请延期");
        if (scope.actorId().equals(task.getCreateAccountId()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"创建人可直接编辑截止时间");
        if (Set.of(Status.DONE.name(),Status.CANCELLED.name()).contains(task.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"已完成或取消任务不能申请延期");
        if (task.getDueTime()==null || !request.dueTime().isAfter(task.getDueTime())
                || !request.dueTime().isAfter(LocalDateTime.now()))
            throw new IllegalArgumentException("申请时间必须晚于当前截止时间和现在");
        if (pendingDefer(task)!=null)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"已有待处理的延期申请");
        String reason=request.reason().trim();
        HistoryView entry=history(taskId,scope.actorId(),"DEFER_REQUEST",
                Map.of("dueTime",task.getDueTime().toString()),
                Map.of("dueTime",request.dueTime().toString()),reason);
        return new DeferView(entry.id(),scope.actorId(),task.getDueTime(),
                request.dueTime(),reason,entry.createTime());
    }

    /** 当前可见人读取待审批延期申请；处理结束后返回空列表。 */
    public List<DeferView> pendingDefer(String token,Long systemId,Long tenantId,Long taskId) {
        UnWorkTask task=visible(scope(token,systemId,tenantId),taskId,false);
        UnWorkTaskHistory pending=pendingDefer(task);
        return pending==null?List.of():List.of(deferView(pending));
    }

    /** 创建人审批延期，任务截止时间与审计记录在同一事务内更新。 */
    @Transactional(rollbackFor=Exception.class)
    public TaskView decideDefer(String token,Long systemId,Long tenantId,Long taskId,DeferDecision decision) {
        Scope scope=scope(token,systemId,tenantId);
        UnWorkTask task=visible(scope,taskId,true);
        if (!scope.actorId().equals(task.getCreateAccountId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"只有任务创建人可以处理延期申请");
        UnWorkTaskHistory pending=pendingDefer(task);
        if (pending==null || !pending.getId().equals(decision.requestId()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"延期申请已变化或不存在");
        if (!decision.approved() && (decision.comment()==null || decision.comment().isBlank()))
            throw new IllegalArgumentException("驳回延期须说明原因");
        LocalDateTime requestedDue=readDue(pending.getAfterJson());
        if (decision.approved()) {
            tasks.update(new LambdaUpdateWrapper<UnWorkTask>().eq(UnWorkTask::getId,taskId)
                    .set(UnWorkTask::getDueTime,requestedDue));
            task.setDueTime(requestedDue);
        }
        Map<String,Object> before=Map.of("requestId",pending.getId(),
                "dueTime",readDue(pending.getBeforeJson()).toString());
        Map<String,Object> after=Map.of("requestId",pending.getId(),
                "dueTime",task.getDueTime().toString());
        history(taskId,scope.actorId(),decision.approved()?"DEFER_APPROVE":"DEFER_REJECT",
                before,after,decision.comment());
        return view(task);
    }

    /** 只有创建人可以归档普通任务；归档保留任务及历史，列表和详情不再返回。 */
    @Transactional(rollbackFor=Exception.class)
    public void archive(String token,Long systemId,Long tenantId,Long taskId) {
        Scope scope=scope(token,systemId,tenantId);
        UnWorkTask task=visible(scope,taskId,true);
        if (!scope.actorId().equals(task.getCreateAccountId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"只有任务创建人可归档");
        cancelPendingDefer(task,scope.actorId(),"任务归档后原延期申请失效");
        tasks.update(new LambdaUpdateWrapper<UnWorkTask>().eq(UnWorkTask::getId,taskId)
                .set(UnWorkTask::getArchivedTime,LocalDateTime.now()));
        history(taskId,scope.actorId(),"ARCHIVE",Map.of("archived",false),
                Map.of("archived",true),null);
        if (task.getProjectId()!=null) projects.refreshProgress(task.getProjectId(),task.getTaskGroupId());
    }

    private boolean allowed(Status before,Status target,boolean creator,boolean owner,boolean acceptor) {
        if (target==Status.IN_PROGRESS) return owner && Set.of(Status.NOT_STARTED,Status.BLOCKED,
                Status.PENDING_CONFIRMATION).contains(before) || creator && before==Status.DONE;
        if (target==Status.BLOCKED) return owner && before==Status.IN_PROGRESS;
        if (target==Status.PENDING_CONFIRMATION) return owner && Set.of(Status.IN_PROGRESS,Status.BLOCKED).contains(before);
        if (target==Status.DONE) return (creator || acceptor) && before==Status.PENDING_CONFIRMATION;
        return target==Status.CANCELLED && creator && Set.of(Status.NOT_STARTED,Status.IN_PROGRESS,
                Status.BLOCKED,Status.PENDING_CONFIRMATION).contains(before);
    }

    private Scope scope(String token,Long systemId,Long tenantId) {
        return workScope.scope(token,systemId,tenantId);
    }

    private LambdaQueryWrapper<UnWorkTask> baseScope(Scope scope) {
        var where=new LambdaQueryWrapper<UnWorkTask>().eq(UnWorkTask::getScopeType,scope.type())
                .isNull(UnWorkTask::getArchivedTime);
        if (scope.systemId()==null) where.isNull(UnWorkTask::getSystemId).isNull(UnWorkTask::getTenantId);
        else where.eq(UnWorkTask::getSystemId,scope.systemId()).eq(UnWorkTask::getTenantId,scope.tenantId());
        return where;
    }

    private UnWorkTask visible(Scope scope,Long taskId,boolean lock) {
        if (taskId==null || taskId<1) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"任务不存在");
        var where=baseScope(scope).eq(UnWorkTask::getId,taskId);
        if (lock) where.last("FOR UPDATE");
        UnWorkTask task=tasks.getOne(where,false);
        if (task==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"任务不存在");
        if (task.getProjectId()!=null) projects.visible(scope,task.getProjectId(),false);
        else if (!scope.actorId().equals(task.getOwnerAccountId())
                && !scope.actorId().equals(task.getCreateAccountId())
                && !taskMembers.exists(new LambdaQueryWrapper<UnWorkTaskMember>()
                        .eq(UnWorkTaskMember::getTaskId,taskId)
                        .eq(UnWorkTaskMember::getAccountId,scope.actorId())
                        .eq(UnWorkTaskMember::getStatus,"ACTIVE")))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"任务不存在");
        return task;
    }

    private Status status(String value) {
        try { return Status.valueOf(value); }
        catch (RuntimeException ex) { throw new IllegalArgumentException("未知任务状态："+value); }
    }

    private void requireManager(Scope scope,UnWorkTask task) {
        if (!scope.actorId().equals(task.getCreateAccountId())
                && !scope.actorId().equals(task.getOwnerAccountId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"只有创建人或负责人可维护任务");
    }

    private MemberType memberType(String value) {
        try { return MemberType.valueOf(value); }
        catch (RuntimeException ex) { throw new IllegalArgumentException("未知任务成员身份："+value); }
    }

    /** 行锁由调用方持有；最新延期操作就是待处理申请时才允许审批。 */
    private UnWorkTaskHistory pendingDefer(UnWorkTask task) {
        UnWorkTaskHistory latest=histories.getOne(new LambdaQueryWrapper<UnWorkTaskHistory>()
                .eq(UnWorkTaskHistory::getTaskId,task.getId())
                .in(UnWorkTaskHistory::getOperationCode,"DEFER_REQUEST","DEFER_APPROVE",
                        "DEFER_REJECT","DEFER_CANCEL")
                .orderByDesc(UnWorkTaskHistory::getId).last("LIMIT 1"),false);
        if (latest==null || !"DEFER_REQUEST".equals(latest.getOperationCode())
                || !Objects.equals(latest.getOperatorAccountId(),task.getOwnerAccountId())
                || !Objects.equals(readDue(latest.getBeforeJson()),task.getDueTime())) return null;
        return latest;
    }

    private DeferView deferView(UnWorkTaskHistory row) {
        return new DeferView(row.getId(),row.getOperatorAccountId(),readDue(row.getBeforeJson()),
                readDue(row.getAfterJson()),row.getContent(),row.getCreateTime());
    }

    /** 负责人或截止时间发生变化时，显式关闭旧申请以免以后重新浮现。 */
    private void cancelPendingDefer(UnWorkTask task,Long actorId,String reason) {
        UnWorkTaskHistory pending=pendingDefer(task);
        if (pending!=null) history(task.getId(),actorId,"DEFER_CANCEL",
                Map.of("requestId",pending.getId()),Map.of("requestId",pending.getId()),reason);
    }

    private LocalDateTime readDue(String value) {
        try { return LocalDateTime.parse(json.readTree(value).path("dueTime").asText()); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("延期历史数据无法读取",ex); }
    }

    private Map<String,Object> details(UnWorkTask task) {
        Map<String,Object> data=new LinkedHashMap<>();
        data.put("title",task.getTitle());data.put("description",task.getDescription());
        data.put("dueTime",task.getDueTime());
        return data;
    }

    private HistoryView history(Long taskId,Long actorId,String operation,Object before,Object after,String content) {
        UnWorkTaskHistory row=new UnWorkTaskHistory(); row.setTaskId(taskId);
        row.setOperatorAccountId(actorId); row.setOperationCode(operation);
        row.setBeforeJson(before==null?null:toJson(before));
        row.setAfterJson(after==null?null:toJson(after));
        row.setContent(content); row.setCreateTime(LocalDateTime.now()); histories.addOrUpdate(row);
        return new HistoryView(row.getId(),actorId,operation,row.getBeforeJson(),
                row.getAfterJson(),content,row.getCreateTime());
    }

    private String toJson(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("任务历史无法序列化",ex); }
    }

    private TaskView view(UnWorkTask task) {
        boolean overdue=task.getDueTime()!=null && task.getDueTime().isBefore(LocalDateTime.now())
                && !Set.of(Status.DONE.name(),Status.CANCELLED.name()).contains(task.getStatus());
        return new TaskView(task.getId(),task.getScopeType(),task.getSystemId(),task.getTenantId(),
                task.getProjectId(),task.getTaskGroupId(),
                task.getTitle(),task.getDescription(),task.getOwnerAccountId(),task.getCreateAccountId(),
                task.getStatus(),overdue,task.getProgressPercent(),task.getDueTime(),
                task.getCompleteTime(),task.getCreateTime());
    }
}
