package com.unique.unexamine.work.manage;

import com.unique.unexamine.sys.base.entity.UnSysTenantMember;
import com.unique.unexamine.sys.base.mapper.UnSysTenantMemberMapper;
import com.unique.unexamine.work.base.entity.UnWorkProject;
import com.unique.unexamine.work.base.entity.UnWorkProjectMember;
import com.unique.unexamine.work.base.entity.UnWorkTaskGroup;
import com.unique.unexamine.work.base.mapper.UnWorkProjectMapper;
import com.unique.unexamine.work.base.mapper.UnWorkProjectMemberMapper;
import com.unique.unexamine.work.base.mapper.UnWorkTaskGroupMapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.unexamine.sys.manage.SystemSessionAccess;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** 工作项目、参与成员与任务组的业务入口。 */
@RestController
@RequestMapping("/api/tenants/{tenantId}/work/projects")
public class WorkProjectController {
    private final SystemSessionAccess access;
    private final UnWorkProjectMapper projects;
    private final UnWorkProjectMemberMapper participants;
    private final UnWorkTaskGroupMapper groups;
    private final UnSysTenantMemberMapper tenantMembers;

    public WorkProjectController(SystemSessionAccess access, UnWorkProjectMapper projects,
                                 UnWorkProjectMemberMapper participants, UnWorkTaskGroupMapper groups,
                                 UnSysTenantMemberMapper tenantMembers) {
        this.access = access;
        this.projects = projects;
        this.participants = participants;
        this.groups = groups;
        this.tenantMembers = tenantMembers;
    }

    /** 只返回当前成员负责或参与的项目；分页和排序在数据库执行。 */
    @GetMapping
    public ProjectPage list(@PathVariable long tenantId,
                            @RequestParam(defaultValue = "1") long page,
                            @RequestParam(defaultValue = "20") long size,
                            @RequestParam(defaultValue = "createTime") String sortField,
                            @RequestParam(defaultValue = "desc") String sortOrder,
                            HttpServletRequest request) {
        UnSysTenantMember actor = access.tenantMember(request, tenantId);
        if (page < 1 || size < 1 || size > 100 || !List.of("createTime", "projectName", "projectCode").contains(sortField)
                || !List.of("asc", "desc").contains(sortOrder.toLowerCase())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分页或排序参数无效");
        }
        List<Long> joinedIds = participants.selectList(new LambdaQueryWrapper<UnWorkProjectMember>()
                        .eq(UnWorkProjectMember::getMemberId, actor.getMemberId()))
                .stream().map(UnWorkProjectMember::getProjectId).toList();
        LambdaQueryWrapper<UnWorkProject> query = new LambdaQueryWrapper<UnWorkProject>()
                .eq(UnWorkProject::getTenantId, tenantId);
        if (joinedIds.isEmpty()) {
            query.eq(UnWorkProject::getOwnerMemberId, actor.getMemberId());
        } else {
            query.and(w -> w.eq(UnWorkProject::getOwnerMemberId, actor.getMemberId())
                    .or().in(UnWorkProject::getId, joinedIds));
        }
        boolean asc = "asc".equalsIgnoreCase(sortOrder);
        switch (sortField) {
            case "projectName" -> query.orderBy(true, asc, UnWorkProject::getProjectName);
            case "projectCode" -> query.orderBy(true, asc, UnWorkProject::getProjectCode);
            default -> query.orderBy(true, asc, UnWorkProject::getCreateTime);
        }
        query.orderByAsc(UnWorkProject::getId);
        Page<UnWorkProject> result = projects.selectPage(new Page<>(page, size), query);
        return new ProjectPage(result.getRecords().stream().map(this::view).toList(), result.getTotal(), page, size);
    }

    /** 创建项目时把负责人加入参与人表，避免项目建立后只有负责人 ID 没有成员关系。 */
    @PostMapping
    @Transactional
    public ProjectView create(@PathVariable long tenantId, @Valid @RequestBody ProjectInput input,
                              HttpServletRequest request) {
        UnSysTenantMember actor = access.tenantAdmin(request, tenantId);
        long ownerId = input.ownerMemberId() == null ? actor.getMemberId() : input.ownerMemberId();
        requireTenantMember(tenantId, ownerId);
        UnWorkProject project = new UnWorkProject();
        project.setTenantId(tenantId);
        project.setProjectCode(input.projectCode().trim());
        project.setProjectName(input.projectName().trim());
        project.setDescription(input.description());
        project.setOwnerMemberId(ownerId);
        project.setStatus("active");
        projects.insert(project);
        UnWorkProjectMember owner = new UnWorkProjectMember();
        owner.setProjectId(project.getId());
        owner.setMemberId(ownerId);
        owner.setRoleCode("owner");
        participants.insert(owner);
        return view(project);
    }

    @GetMapping("/{projectId}")
    public ProjectView detail(@PathVariable long tenantId, @PathVariable long projectId,
                              HttpServletRequest request) {
        return view(requireProject(tenantId, projectId, request));
    }

    /** 项目负责人和租户管理员可以修改项目基础信息及归档状态。 */
    @PutMapping("/{projectId}")
    public ProjectView update(@PathVariable long tenantId, @PathVariable long projectId,
                              @Valid @RequestBody ProjectUpdate input, HttpServletRequest request) {
        UnWorkProject project = requireManagedProject(tenantId, projectId, request);
        if (!List.of("active", "archived").contains(input.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "项目状态无效");
        }
        project.setProjectName(input.projectName().trim());
        project.setDescription(input.description());
        project.setStatus(input.status());
        projects.updateById(project);
        return view(project);
    }

    /** 加入的成员必须属于项目租户，不能通过成员 ID 引入其他系统人员。 */
    @PostMapping("/{projectId}/members")
    public MemberView addMember(@PathVariable long tenantId, @PathVariable long projectId,
                                @Valid @RequestBody MemberInput input, HttpServletRequest request) {
        requireManagedProject(tenantId, projectId, request);
        requireTenantMember(tenantId, input.memberId());
        if (participants.selectCount(new LambdaQueryWrapper<UnWorkProjectMember>()
                .eq(UnWorkProjectMember::getProjectId, projectId)
                .eq(UnWorkProjectMember::getMemberId, input.memberId())) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "成员已在项目中");
        }
        UnWorkProjectMember participant = new UnWorkProjectMember();
        participant.setProjectId(projectId);
        participant.setMemberId(input.memberId());
        participant.setRoleCode("member");
        participants.insert(participant);
        return new MemberView(participant.getId(), input.memberId(), participant.getRoleCode());
    }

    @GetMapping("/{projectId}/members")
    public List<MemberView> members(@PathVariable long tenantId, @PathVariable long projectId,
                                    HttpServletRequest request) {
        requireProject(tenantId, projectId, request);
        return participants.selectList(new LambdaQueryWrapper<UnWorkProjectMember>()
                        .eq(UnWorkProjectMember::getProjectId, projectId)
                        .orderByAsc(UnWorkProjectMember::getId))
                .stream().map(item -> new MemberView(item.getId(), item.getMemberId(), item.getRoleCode())).toList();
    }

    /** 项目负责人不能从参与人表移除，否则后续管理入口会失去稳定归属。 */
    @DeleteMapping("/{projectId}/members/{relationId}")
    public void removeMember(@PathVariable long tenantId, @PathVariable long projectId,
                             @PathVariable long relationId, HttpServletRequest request) {
        UnWorkProject project = requireManagedProject(tenantId, projectId, request);
        UnWorkProjectMember participant = participants.selectById(relationId);
        if (participant == null || !participant.getProjectId().equals(projectId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "项目参与关系不存在");
        }
        if (participant.getMemberId().equals(project.getOwnerMemberId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "不能移除项目负责人");
        }
        participants.deleteById(relationId);
    }

    @PostMapping("/{projectId}/groups")
    public GroupView addGroup(@PathVariable long tenantId, @PathVariable long projectId,
                              @Valid @RequestBody GroupInput input, HttpServletRequest request) {
        requireManagedProject(tenantId, projectId, request);
        UnWorkTaskGroup group = new UnWorkTaskGroup();
        group.setProjectId(projectId);
        group.setGroupName(input.groupName().trim());
        group.setSortNo(input.sortNo() == null ? 0 : input.sortNo());
        groups.insert(group);
        return groupView(group);
    }

    @GetMapping("/{projectId}/groups")
    public List<GroupView> groups(@PathVariable long tenantId, @PathVariable long projectId,
                                  HttpServletRequest request) {
        requireProject(tenantId, projectId, request);
        return groups.selectList(new LambdaQueryWrapper<UnWorkTaskGroup>()
                        .eq(UnWorkTaskGroup::getProjectId, projectId)
                        .orderByAsc(UnWorkTaskGroup::getSortNo, UnWorkTaskGroup::getId))
                .stream().map(this::groupView).toList();
    }

    @PutMapping("/{projectId}/groups/{groupId}")
    public GroupView updateGroup(@PathVariable long tenantId, @PathVariable long projectId,
                                 @PathVariable long groupId, @Valid @RequestBody GroupInput input,
                                 HttpServletRequest request) {
        requireManagedProject(tenantId, projectId, request);
        UnWorkTaskGroup group = groups.selectById(groupId);
        if (group == null || !group.getProjectId().equals(projectId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "任务组不存在");
        }
        group.setGroupName(input.groupName().trim());
        group.setSortNo(input.sortNo() == null ? 0 : input.sortNo());
        groups.updateById(group);
        return groupView(group);
    }

    /** 工作域其他入口也使用此检查，确保项目和租户归属及参与范围一致。 */
    public UnWorkProject requireProject(long tenantId, long projectId, HttpServletRequest request) {
        UnSysTenantMember actor = access.tenantMember(request, tenantId);
        UnWorkProject project = projects.selectById(projectId);
        if (project == null || !project.getTenantId().equals(tenantId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "项目不存在");
        }
        boolean joined = participants.selectCount(new LambdaQueryWrapper<UnWorkProjectMember>()
                .eq(UnWorkProjectMember::getProjectId, projectId)
                .eq(UnWorkProjectMember::getMemberId, actor.getMemberId())) > 0;
        if (!project.getOwnerMemberId().equals(actor.getMemberId()) && !joined) {
            access.tenantAdmin(request, tenantId);
        }
        return project;
    }

    public UnWorkProject requireManagedProject(long tenantId, long projectId, HttpServletRequest request) {
        UnSysTenantMember actor = access.tenantMember(request, tenantId);
        UnWorkProject project = projects.selectById(projectId);
        if (project == null || !project.getTenantId().equals(tenantId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "项目不存在");
        }
        if (!project.getOwnerMemberId().equals(actor.getMemberId())) {
            access.tenantAdmin(request, tenantId);
        }
        return project;
    }

    private void requireTenantMember(long tenantId, long systemMemberId) {
        Long count = tenantMembers.selectCount(new LambdaQueryWrapper<UnSysTenantMember>()
                .eq(UnSysTenantMember::getTenantId, tenantId)
                .eq(UnSysTenantMember::getMemberId, systemMemberId)
                .eq(UnSysTenantMember::getStatus, 1));
        if (count == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "负责人或参与人不是有效租户成员");
        }
    }

    private ProjectView view(UnWorkProject project) {
        return new ProjectView(project.getId(), project.getProjectCode(), project.getProjectName(),
                project.getDescription(), project.getOwnerMemberId(), project.getStatus());
    }

    private GroupView groupView(UnWorkTaskGroup group) {
        return new GroupView(group.getId(), group.getGroupName(), group.getSortNo());
    }

    /** 项目编码创建后保持稳定，变更展示名称不影响既有任务归属。 */
    public record ProjectInput(@NotBlank @Size(max = 64) String projectCode,
                               @NotBlank @Size(max = 120) String projectName,
                               @Size(max = 2000) String description, Long ownerMemberId) { }
    public record ProjectUpdate(@NotBlank @Size(max = 120) String projectName,
                                @Size(max = 2000) String description, @NotBlank String status) { }
    public record ProjectView(Long id, String projectCode, String projectName, String description,
                              Long ownerMemberId, String status) { }
    public record ProjectPage(List<ProjectView> records, long total, long page, long size) { }
    public record MemberInput(long memberId) { }
    public record MemberView(Long id, Long memberId, String roleCode) { }
    public record GroupInput(@NotBlank @Size(max = 100) String groupName, Integer sortNo) { }
    public record GroupView(Long id, String groupName, Integer sortNo) { }
}
