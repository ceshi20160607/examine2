package com.unique.unexamine.work.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.core.bo.SearchBO;
import com.unique.unexamine.work.base.entity.UnWorkProject;
import com.unique.unexamine.work.base.entity.UnWorkProjectMember;
import com.unique.unexamine.work.base.entity.UnWorkTask;
import com.unique.unexamine.work.base.entity.UnWorkTaskGroup;
import com.unique.unexamine.work.base.service.IUnWorkProjectMemberService;
import com.unique.unexamine.work.base.service.IUnWorkProjectService;
import com.unique.unexamine.work.base.service.IUnWorkTaskGroupService;
import com.unique.unexamine.work.base.service.IUnWorkTaskService;
import com.unique.unexamine.work.manage.dto.WorkProjectDto.CreateRequest;
import com.unique.unexamine.work.manage.dto.WorkProjectDto.EditRequest;
import com.unique.unexamine.work.manage.dto.WorkProjectDto.GroupEditRequest;
import com.unique.unexamine.work.manage.dto.WorkProjectDto.GroupRequest;
import com.unique.unexamine.work.manage.dto.WorkProjectDto.GroupView;
import com.unique.unexamine.work.manage.dto.WorkProjectDto.MemberRequest;
import com.unique.unexamine.work.manage.dto.WorkProjectDto.MemberView;
import com.unique.unexamine.work.manage.dto.WorkProjectDto.ProjectView;
import com.unique.unexamine.work.manage.dto.WorkProjectDto.TaskSummary;
import com.unique.unexamine.work.manage.service.WorkScopeService.Scope;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 平台或系统租户内的项目、参与人和任务组业务入口。 */
@Service
public class WorkProjectService {
    /** 有效项目成员可见项目；账号 ID 由 MyBatis 占位参数绑定。 */
    private static final String MEMBER_EXISTS_SQL="EXISTS (SELECT 1 FROM un_work_project_member pm "
            +"WHERE pm.deleted_time IS NULL AND pm.project_id = un_work_project.id AND pm.account_id = {0} AND pm.status = 'ACTIVE')";
    private final WorkScopeService scopes;
    private final IUnWorkProjectService projects;
    private final IUnWorkProjectMemberService members;
    private final IUnWorkTaskGroupService groups;
    private final IUnWorkTaskService tasks;

    /** 注入作用域校验及已生成基础服务。 */
    public WorkProjectService(WorkScopeService scopes,IUnWorkProjectService projects,
            IUnWorkProjectMemberService members,IUnWorkTaskGroupService groups,IUnWorkTaskService tasks) {
        this.scopes=scopes;this.projects=projects;this.members=members;this.groups=groups;this.tasks=tasks;
    }

    /** 创建项目时同步建立负责人成员关系。 */
    @Transactional(rollbackFor=Exception.class)
    public ProjectView create(String token,Long systemId,Long tenantId,CreateRequest request) {
        validateProjectName(request == null ? null : request.name());
        if (request == null || request.code() == null || request.code().isBlank() || request.code().length() > 80)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"项目编号无效");
        Scope scope=scopes.scope(token,systemId,tenantId);
        if (request.startTime()!=null && request.dueTime()!=null
                && request.dueTime().isBefore(request.startTime()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"项目截止时间不能早于开始时间");
        String code=request.code().trim(),name=request.name().trim();
        UnWorkProject row=new UnWorkProject();
        row.setScopeType(scope.type());row.setSystemId(scope.systemId());row.setTenantId(scope.tenantId());
        row.setCode(code);row.setName(name);row.setDescription(request.description());
        row.setOwnerAccountId(scope.actorId());row.setCreateAccountId(scope.actorId());
        row.setStatus("ACTIVE");row.setProgressPercent(BigDecimal.ZERO);
        row.setStartTime(request.startTime());row.setDueTime(request.dueTime());
        row.setCreateTime(LocalDateTime.now());
        try { projects.addOrUpdate(row); }
        catch (DuplicateKeyException ex) {
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"当前作用域项目编号已存在",ex);
        }
        UnWorkProjectMember owner=new UnWorkProjectMember();owner.setProjectId(row.getId());
        owner.setAccountId(scope.actorId());owner.setMemberType("OWNER");owner.setStatus("ACTIVE");
        owner.setJoinTime(LocalDateTime.now());members.addOrUpdate(owner);
        return view(row);
    }

    /** 只返回当前账号拥有或参与的本作用域项目。 */
    public Page<ProjectView> queryPageList(String token,Long systemId,Long tenantId,SearchBO search) {
        Scope scope=scopes.scope(token,systemId,tenantId);
        SearchBO criteria=search==null?new SearchBO():search;
        var where=baseScope(scope).ne(UnWorkProject::getStatus,"ARCHIVED")
                .and(q->q.eq(UnWorkProject::getOwnerAccountId,scope.actorId())
                        .or().eq(UnWorkProject::getCreateAccountId,scope.actorId())
                        .or().apply(MEMBER_EXISTS_SQL,scope.actorId()));
        if (criteria.getKeyword()!=null) {
            if (criteria.getKeyword().length()>100)
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"搜索文本过长");
            String keyword=criteria.getKeyword().trim();
            if (!keyword.isBlank()) where.and(q->q.like(UnWorkProject::getCode,keyword)
                    .or().like(UnWorkProject::getName,keyword)
                    .or().like(UnWorkProject::getDescription,keyword));
        }
        var result=projects.page(criteria.parse(),where.orderByDesc(UnWorkProject::getId));
        return new Page<ProjectView>(result.getCurrent(), result.getSize(), result.getTotal()).setRecords(result.getRecords().stream().map(this::view).toList());
    }

    /** 项目详情按作用域和有效参与关系授权。 */
    public ProjectView queryById(String token,Long systemId,Long tenantId,Long projectId) {
        return view(visible(scopes.scope(token,systemId,tenantId),projectId,false));
    }

    /** 项目成员查看任务数量和截止预警，使用与任务列表相同的项目授权。 */
    public TaskSummary taskSummary(String token,Long systemId,Long tenantId,Long projectId) {
        UnWorkProject project=visible(scopes.scope(token,systemId,tenantId),projectId,false);
        LocalDateTime now=LocalDateTime.now();
        var active=new LambdaQueryWrapper<UnWorkTask>().eq(UnWorkTask::getProjectId,projectId)
                .isNull(UnWorkTask::getArchivedTime).ne(UnWorkTask::getStatus,"CANCELLED");
        long total=tasks.count(active);
        long done=taskCount(projectId,null,"DONE");
        long blocked=tasks.count(new LambdaQueryWrapper<UnWorkTask>()
                .eq(UnWorkTask::getProjectId,projectId).isNull(UnWorkTask::getArchivedTime)
                .eq(UnWorkTask::getStatus,"BLOCKED"));
        long overdue=tasks.count(new LambdaQueryWrapper<UnWorkTask>()
                .eq(UnWorkTask::getProjectId,projectId).isNull(UnWorkTask::getArchivedTime)
                .notIn(UnWorkTask::getStatus,"DONE","CANCELLED")
                .lt(UnWorkTask::getDueTime,now));
        long dueSoon=tasks.count(new LambdaQueryWrapper<UnWorkTask>()
                .eq(UnWorkTask::getProjectId,projectId).isNull(UnWorkTask::getArchivedTime)
                .notIn(UnWorkTask::getStatus,"DONE","CANCELLED")
                .ge(UnWorkTask::getDueTime,now).le(UnWorkTask::getDueTime,now.plusDays(1)));
        long unassigned=tasks.count(new LambdaQueryWrapper<UnWorkTask>()
                .eq(UnWorkTask::getProjectId,projectId).isNull(UnWorkTask::getArchivedTime)
                .ne(UnWorkTask::getStatus,"CANCELLED").isNull(UnWorkTask::getOwnerAccountId));
        return new TaskSummary(projectId,project.getProgressPercent(),total,done,
                blocked,overdue,dueSoon,unassigned);
    }

    /** 负责人更新项目基础资料，项目编号及已有任务关系不变。 */
    @Transactional(rollbackFor=Exception.class)
    public ProjectView edit(String token,Long systemId,Long tenantId,Long projectId,EditRequest request) {
        validateProjectName(request == null ? null : request.name());
        Scope scope=scopes.scope(token,systemId,tenantId);
        UnWorkProject row=visible(scope,projectId,true);
        requireOwner(scope,row);
        if (request.startTime()!=null && request.dueTime()!=null
                && request.dueTime().isBefore(request.startTime()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"项目截止时间不能早于开始时间");
        String name=request.name().trim();
        projects.update(new LambdaUpdateWrapper<UnWorkProject>().eq(UnWorkProject::getId,projectId)
                .set(UnWorkProject::getName,name)
                .set(UnWorkProject::getDescription,request.description())
                .set(UnWorkProject::getStartTime,request.startTime())
                .set(UnWorkProject::getDueTime,request.dueTime()));
        row.setName(name);row.setDescription(request.description());
        row.setStartTime(request.startTime());row.setDueTime(request.dueTime());
        return view(row);
    }

    /** 项目归档后不再出现在成员列表，所属任务与历史仍保留。 */
    @Transactional(rollbackFor=Exception.class)
    public void archive(String token,Long systemId,Long tenantId,Long projectId) {
        Scope scope=scopes.scope(token,systemId,tenantId);
        UnWorkProject row=visible(scope,projectId,true);
        requireOwner(scope,row);
        projects.update(new LambdaUpdateWrapper<UnWorkProject>().eq(UnWorkProject::getId,projectId)
                .set(UnWorkProject::getStatus,"ARCHIVED"));
    }

    /** 项目负责人添加同作用域协作或观察成员。 */
    @Transactional(rollbackFor=Exception.class)
    public MemberView addMember(String token,Long systemId,Long tenantId,Long projectId,MemberRequest request) {
        if (request == null || request.accountId() == null || request.accountId() < 1
                || request.memberType() == null || request.memberType().isBlank())
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"项目成员参数无效");
        Scope scope=scopes.scope(token,systemId,tenantId);
        UnWorkProject project=visible(scope,projectId,true);
        requireOwner(scope,project);
        String type=memberType(request.memberType());
        var target=scopes.assignee(scope,request.accountId());
        UnWorkProjectMember row=members.getOne(new LambdaQueryWrapper<UnWorkProjectMember>()
                .eq(UnWorkProjectMember::getProjectId,projectId)
                .eq(UnWorkProjectMember::getAccountId,request.accountId())
                .eq(UnWorkProjectMember::getMemberType,type),false);
        if (row==null) {
            row=new UnWorkProjectMember();row.setProjectId(projectId);
            row.setAccountId(request.accountId());row.setMemberType(type);
            row.setJoinTime(LocalDateTime.now());
        }
        if (!"ACTIVE".equals(row.getStatus())) {
            row.setStatus("ACTIVE");members.addOrUpdate(row);
        }
        return new MemberView(row.getId(),request.accountId(),target.displayName(),type);
    }

    /** 项目可见者一次读取有效成员及当前名称。 */
    public List<MemberView> members(String token,Long systemId,Long tenantId,Long projectId) {
        Scope scope=scopes.scope(token,systemId,tenantId);
        visible(scope,projectId,false);
        List<UnWorkProjectMember> rows=members.list(new LambdaQueryWrapper<UnWorkProjectMember>()
                .eq(UnWorkProjectMember::getProjectId,projectId)
                .eq(UnWorkProjectMember::getStatus,"ACTIVE").orderByAsc(UnWorkProjectMember::getId));
        if (rows.isEmpty()) return List.of();
        Map<Long,String> names=scopes.displayNames(scope,rows.stream()
                .map(UnWorkProjectMember::getAccountId).distinct().toList());
        return rows.stream().map(row->new MemberView(row.getId(),row.getAccountId(),
                names.get(row.getAccountId()),row.getMemberType())).toList();
    }

    /** 项目负责人停用成员关系；最后一项成员关系有未结束任务时先转交任务。 */
    @Transactional(rollbackFor=Exception.class)
    public void removeMember(String token,Long systemId,Long tenantId,Long projectId,Long memberId) {
        Scope scope=scopes.scope(token,systemId,tenantId);
        UnWorkProject project=visible(scope,projectId,true);
        requireOwner(scope,project);
        UnWorkProjectMember member=members.getOne(new LambdaQueryWrapper<UnWorkProjectMember>()
                .eq(UnWorkProjectMember::getId,memberId)
                .eq(UnWorkProjectMember::getProjectId,projectId)
                .eq(UnWorkProjectMember::getStatus,"ACTIVE"),false);
        if (member==null) throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND,"项目成员不存在");
        if ("OWNER".equals(member.getMemberType()))
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"项目负责人关系不能移除");
        boolean last=members.count(new LambdaQueryWrapper<UnWorkProjectMember>()
                .eq(UnWorkProjectMember::getProjectId,projectId)
                .eq(UnWorkProjectMember::getAccountId,member.getAccountId())
                .eq(UnWorkProjectMember::getStatus,"ACTIVE")
                .ne(UnWorkProjectMember::getId,memberId))==0;
        if (last && tasks.exists(new LambdaQueryWrapper<UnWorkTask>()
                .eq(UnWorkTask::getProjectId,projectId)
                .eq(UnWorkTask::getOwnerAccountId,member.getAccountId())
                .isNull(UnWorkTask::getArchivedTime)
                .notIn(UnWorkTask::getStatus,"DONE","CANCELLED")))
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"该成员仍负责未结束任务，请先转交");
        members.update(new LambdaUpdateWrapper<UnWorkProjectMember>()
                .eq(UnWorkProjectMember::getId,memberId).set(UnWorkProjectMember::getStatus,"INACTIVE"));
    }

    /** 项目负责人创建任务组，顺序取当前项目中下一个位置。 */
    @Transactional(rollbackFor=Exception.class)
    public GroupView createGroup(String token,Long systemId,Long tenantId,Long projectId,GroupRequest request) {
        validateGroupName(request == null ? null : request.name());
        if (request != null && request.sortNo() != null && request.sortNo() < 1)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"任务组顺序无效");
        Scope scope=scopes.scope(token,systemId,tenantId);
        UnWorkProject project=visible(scope,projectId,true);
        requireOwner(scope,project);
        Long ownerId=request.ownerAccountId()==null?scope.actorId():request.ownerAccountId();
        if (!ownerId.equals(scope.actorId())) {
            scopes.assignee(scope,ownerId);
            requireMember(project,ownerId);
        }
        UnWorkTaskGroup last=request.sortNo()==null?groups.getOne(new LambdaQueryWrapper<UnWorkTaskGroup>()
                .eq(UnWorkTaskGroup::getProjectId,projectId)
                .orderByDesc(UnWorkTaskGroup::getSortNo).last("LIMIT 1"),false):null;
        UnWorkTaskGroup row=new UnWorkTaskGroup();row.setProjectId(projectId);
        row.setName(request.name().trim());row.setOwnerAccountId(ownerId);
        row.setStatus("ACTIVE");row.setProgressPercent(BigDecimal.ZERO);
        row.setSortNo(request.sortNo()!=null?request.sortNo():last==null?1:last.getSortNo()+1);
        row.setDueTime(request.dueTime());
        row.setCreateTime(LocalDateTime.now());groups.addOrUpdate(row);
        return groupView(row);
    }

    /** 项目可见者读取组顺序，供任务看板阶段使用。 */
    public List<GroupView> groups(String token,Long systemId,Long tenantId,Long projectId) {
        visible(scopes.scope(token,systemId,tenantId),projectId,false);
        return groups.list(new LambdaQueryWrapper<UnWorkTaskGroup>()
                .eq(UnWorkTaskGroup::getProjectId,projectId)
                .orderByAsc(UnWorkTaskGroup::getSortNo).orderByAsc(UnWorkTaskGroup::getId))
                .stream().map(this::groupView).toList();
    }

    /** 项目负责人修改所属任务组资料；不触碰组内任务。 */
    @Transactional(rollbackFor=Exception.class)
    public GroupView editGroup(String token,Long systemId,Long tenantId,Long projectId,
                               Long groupId,GroupEditRequest request) {
        validateGroupName(request == null ? null : request.name());
        if (request == null || request.status() == null || request.status().isBlank()
                || request.sortNo() == null || request.sortNo() < 1)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"任务组参数无效");
        Scope scope=scopes.scope(token,systemId,tenantId);
        UnWorkProject project=visible(scope,projectId,true);
        requireOwner(scope,project);
        UnWorkTaskGroup group=groups.getOne(new LambdaQueryWrapper<UnWorkTaskGroup>()
                .eq(UnWorkTaskGroup::getId,groupId).eq(UnWorkTaskGroup::getProjectId,projectId),false);
        if (group==null) throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND,"项目任务组不存在");
        if (!Set.of("ACTIVE","PAUSED","DONE").contains(request.status()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"未知任务组状态");
        if (request.ownerAccountId()!=null) {
            scopes.assignee(scope,request.ownerAccountId());
            requireMember(project,request.ownerAccountId());
        }
        String name=request.name().trim();
        groups.update(new LambdaUpdateWrapper<UnWorkTaskGroup>().eq(UnWorkTaskGroup::getId,groupId)
                .set(UnWorkTaskGroup::getName,name)
                .set(UnWorkTaskGroup::getOwnerAccountId,request.ownerAccountId())
                .set(UnWorkTaskGroup::getStatus,request.status())
                .set(UnWorkTaskGroup::getSortNo,request.sortNo())
                .set(UnWorkTaskGroup::getDueTime,request.dueTime()));
        group.setName(name);group.setOwnerAccountId(request.ownerAccountId());
        group.setStatus(request.status());group.setSortNo(request.sortNo());
        group.setDueTime(request.dueTime());
        return groupView(group);
    }

    private LambdaQueryWrapper<UnWorkProject> baseScope(Scope scope) {
        var where=new LambdaQueryWrapper<UnWorkProject>().eq(UnWorkProject::getScopeType,scope.type());
        if (scope.systemId()==null) where.isNull(UnWorkProject::getSystemId).isNull(UnWorkProject::getTenantId);
        else where.eq(UnWorkProject::getSystemId,scope.systemId()).eq(UnWorkProject::getTenantId,scope.tenantId());
        return where;
    }

    /** 本人任务列表、看板和日历在分页前限定有效且有权查看的项目。 */
    List<Long> visibleProjectIds(Scope scope) {
        return projects.list(baseScope(scope).ne(UnWorkProject::getStatus,"ARCHIVED")
                .and(q->q.eq(UnWorkProject::getOwnerAccountId,scope.actorId())
                        .or().eq(UnWorkProject::getCreateAccountId,scope.actorId())
                        .or().apply(MEMBER_EXISTS_SQL,scope.actorId())))
                .stream().map(UnWorkProject::getId).toList();
    }

    UnWorkProject visible(Scope scope,Long projectId,boolean lock) {
        if (projectId==null || projectId<1) throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND,"项目不存在");
        var where=baseScope(scope).eq(UnWorkProject::getId,projectId)
                .ne(UnWorkProject::getStatus,"ARCHIVED")
                .and(q->q.eq(UnWorkProject::getOwnerAccountId,scope.actorId())
                        .or().eq(UnWorkProject::getCreateAccountId,scope.actorId())
                        .or().apply(MEMBER_EXISTS_SQL,scope.actorId()));
        if (lock) where.last("FOR UPDATE");
        UnWorkProject row=projects.getOne(where,false);
        if (row==null) throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND,"项目不存在");
        return row;
    }

    void requireOwner(Scope scope,UnWorkProject project) {
        if (!scope.actorId().equals(project.getOwnerAccountId()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH,"只有项目负责人可维护项目");
    }

    /** 任务归属只接受本项目仍有效的任务组。 */
    void requireGroup(Long projectId,Long groupId) {
        if (groupId!=null && !groups.exists(new LambdaQueryWrapper<UnWorkTaskGroup>()
                .eq(UnWorkTaskGroup::getId,groupId).eq(UnWorkTaskGroup::getProjectId,projectId)
                .eq(UnWorkTaskGroup::getStatus,"ACTIVE")))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND,"项目任务组不存在");
    }

    /** 项目任务负责人须已加入项目，避免派发后无法参与项目。 */
    void requireMember(UnWorkProject project,Long accountId) {
        if (accountId.equals(project.getOwnerAccountId())) return;
        if (!members.exists(new LambdaQueryWrapper<UnWorkProjectMember>()
                .eq(UnWorkProjectMember::getProjectId,project.getId())
                .eq(UnWorkProjectMember::getAccountId,accountId)
                .eq(UnWorkProjectMember::getStatus,"ACTIVE")))
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"任务负责人不是项目成员");
    }

    /** 按未归档且未取消的任务计算项目及所属任务组完成比例。 */
    void refreshProgress(Long projectId,Long groupId) {
        long total=taskCount(projectId,null,null),done=taskCount(projectId,null,"DONE");
        projects.update(new LambdaUpdateWrapper<UnWorkProject>().eq(UnWorkProject::getId,projectId)
                .set(UnWorkProject::getProgressPercent,percentage(done,total)));
        if (groupId!=null) {
            long groupTotal=taskCount(projectId,groupId,null);
            long groupDone=taskCount(projectId,groupId,"DONE");
            groups.update(new LambdaUpdateWrapper<UnWorkTaskGroup>().eq(UnWorkTaskGroup::getId,groupId)
                    .eq(UnWorkTaskGroup::getProjectId,projectId)
                    .set(UnWorkTaskGroup::getProgressPercent,percentage(groupDone,groupTotal)));
        }
    }

    private long taskCount(Long projectId,Long groupId,String status) {
        var where=new LambdaQueryWrapper<UnWorkTask>().eq(UnWorkTask::getProjectId,projectId)
                .isNull(UnWorkTask::getArchivedTime).ne(UnWorkTask::getStatus,"CANCELLED");
        if (groupId!=null) where.eq(UnWorkTask::getTaskGroupId,groupId);
        if (status!=null) where.eq(UnWorkTask::getStatus,status);
        return tasks.count(where);
    }

    private BigDecimal percentage(long done,long total) {
        return total==0?BigDecimal.ZERO:BigDecimal.valueOf(done*100)
                .divide(BigDecimal.valueOf(total),2,RoundingMode.HALF_UP);
    }

    private String memberType(String value) {
        if ("COLLABORATOR".equals(value) || "OBSERVER".equals(value)) return value;
        throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"未知项目成员身份："+value);
    }

    /** 项目服务被内部模块直接调用时也执行名称边界校验。 */
    private void validateProjectName(String name) {
        if (name == null || name.isBlank() || name.length() > 160)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"项目名称无效");
    }

    private void validateGroupName(String name) {
        if (name == null || name.isBlank() || name.length() > 120)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"任务组名称无效");
    }

    private ProjectView view(UnWorkProject row) {
        return new ProjectView(row.getId(),row.getScopeType(),row.getSystemId(),row.getTenantId(),
                row.getCode(),row.getName(),row.getDescription(),row.getOwnerAccountId(),
                row.getCreateAccountId(),row.getStatus(),row.getProgressPercent(),
                row.getStartTime(),row.getDueTime(),row.getCreateTime());
    }

    private GroupView groupView(UnWorkTaskGroup row) {
        return new GroupView(row.getId(),row.getProjectId(),row.getName(),row.getOwnerAccountId(),
                row.getStatus(),row.getProgressPercent(),row.getSortNo(),row.getDueTime());
    }
}
