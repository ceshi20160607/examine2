package com.unique.unexamine.sys.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.common.base.entity.UnCommonNotice;
import com.unique.unexamine.core.common.base.service.IUnCommonNoticeService;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.sys.base.entity.UnSysDepartment;
import com.unique.unexamine.sys.base.entity.UnSysLogOpt;
import com.unique.unexamine.sys.base.entity.UnSysMember;
import com.unique.unexamine.sys.base.entity.UnSysMemberRole;
import com.unique.unexamine.sys.base.entity.UnSysRole;
import com.unique.unexamine.sys.base.entity.UnSysTenantMember;
import com.unique.unexamine.sys.base.service.IUnSysDepartmentService;
import com.unique.unexamine.sys.base.service.IUnSysLogOptService;
import com.unique.unexamine.sys.base.service.IUnSysMemberRoleService;
import com.unique.unexamine.sys.base.service.IUnSysMemberService;
import com.unique.unexamine.sys.base.service.IUnSysRoleService;
import com.unique.unexamine.sys.base.service.IUnSysTenantMemberService;
import com.unique.unexamine.sys.manage.dto.SystemManageDto.SystemContext;
import com.unique.unexamine.sys.manage.dto.SystemNoticeDto.NoticeView;
import com.unique.unexamine.sys.manage.dto.SystemNoticeDto.NoticeSearch;
import com.unique.unexamine.sys.manage.dto.SystemNoticeDto.SaveRequest;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 系统公告按租户发布，并在读取时核对成员当前的角色和部门。 */
@Service
public class SystemNoticeService {
    private final SystemManageService systems;
    private final IUnCommonNoticeService notices;
    private final IUnSysMemberService members;
    private final IUnSysTenantMemberService tenantMembers;
    private final IUnSysMemberRoleService memberRoles;
    private final IUnSysDepartmentService departments;
    private final IUnSysRoleService roles;
    private final IUnSysLogOptService logs;
    private final ObjectMapper json;

    public SystemNoticeService(SystemManageService systems, IUnCommonNoticeService notices,
            IUnSysMemberService members, IUnSysTenantMemberService tenantMembers,
            IUnSysMemberRoleService memberRoles, IUnSysDepartmentService departments,
            IUnSysRoleService roles, IUnSysLogOptService logs, ObjectMapper json) {
        this.systems = systems; this.notices = notices; this.members = members;
        this.tenantMembers = tenantMembers; this.memberRoles = memberRoles;
        this.departments = departments; this.roles = roles; this.logs = logs; this.json = json;
    }

    /** 只返回当前租户内已发布、未过期且命中当前成员范围的公告。 */
    public Page<NoticeView> list(String token, Long systemId, Long tenantId, NoticeSearch search) {
        SystemContext context = systems.context(token, systemId, tenantId);
        NoticeSearch criteria = search == null ? new NoticeSearch() : search;
        Page<NoticeView> requested = criteria.parse();
        LocalDateTime now = LocalDateTime.now();
        List<NoticeView> visible = notices.list(new LambdaQueryWrapper<UnCommonNotice>()
                .eq(UnCommonNotice::getScopeType, "SYSTEM")
                .eq(UnCommonNotice::getSystemId, context.systemId())
                .eq(UnCommonNotice::getTenantId, context.tenantId())
                .eq(UnCommonNotice::getStatus, "PUBLISHED")
                .le(UnCommonNotice::getPublishTime, now)
                .and(w -> w.isNull(UnCommonNotice::getExpireTime).or().gt(UnCommonNotice::getExpireTime, now))
                .orderByDesc(UnCommonNotice::getPublishTime))
                .stream().filter(row -> visible(context, row)).map(this::view).toList();
        int from = (int) Math.min(visible.size(), (requested.getCurrent() - 1) * requested.getSize());
        int to = (int) Math.min(visible.size(), from + requested.getSize());
        return new Page<NoticeView>(requested.getCurrent(), requested.getSize(), visible.size())
                .setRecords(visible.subList(from, to));
    }

    /** 管理员分页查看本租户的公告配置。 */
    public Page<NoticeView> manage(String token, Long systemId, Long tenantId, NoticeSearch search) {
        SystemContext context = systems.requireAdminContext(token, systemId, tenantId);
        NoticeSearch criteria = search == null ? new NoticeSearch() : search;
        var where = new LambdaQueryWrapper<UnCommonNotice>()
                .eq(UnCommonNotice::getScopeType, "SYSTEM")
                .eq(UnCommonNotice::getSystemId, context.systemId())
                .eq(UnCommonNotice::getTenantId, context.tenantId())
                .orderByDesc(UnCommonNotice::getId);
        if (criteria.getStatus() != null && !criteria.getStatus().isBlank())
            where.eq(UnCommonNotice::getStatus, status(criteria.getStatus()));
        if (criteria.getKeyword() != null && !criteria.getKeyword().isBlank()) {
            if (criteria.getKeyword().length() > 100)
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "公告搜索内容过长");
            where.like(UnCommonNotice::getTitle, criteria.getKeyword().trim());
        }
        Page<UnCommonNotice> page = notices.page(criteria.parse(), where);
        return new Page<NoticeView>(page.getCurrent(), page.getSize(), page.getTotal())
                .setRecords(page.getRecords().stream().map(this::view).toList());
    }

    /** 保存为草稿，修改后须重新发布才对成员可见。 */
    @Transactional(rollbackFor = Exception.class)
    public NoticeView save(String token, Long systemId, Long tenantId, SaveRequest request) {
        SystemContext context = systems.requireAdminContext(token, systemId, tenantId);
        if (request == null || request.title() == null || request.title().isBlank()
                || request.title().length() > 200 || request.content() == null
                || request.content().isBlank() || request.content().length() > 15000)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "公告内容无效");
        validateAudienceShape(request.audience());
        UnCommonNotice row = request.id() == null ? new UnCommonNotice() : owned(context, request.id());
        boolean creating = row.getId() == null;
        if (creating) {
            row.setScopeType("SYSTEM"); row.setSystemId(context.systemId()); row.setTenantId(context.tenantId());
            row.setCreateAccountId(context.accountId()); row.setCreateTime(LocalDateTime.now());
        }
        row.setTitle(request.title().trim()); row.setContent(request.content());
        row.setAudienceJson(request.audience() == null || request.audience().isNull()
                ? null : request.audience().toString());
        row.setExpireTime(request.expireTime()); row.setStatus("DRAFT"); row.setPublishTime(null);
        row.setUpdateTime(LocalDateTime.now());
        if (creating) notices.addOrUpdate(row);
        else notices.update(new LambdaUpdateWrapper<UnCommonNotice>()
                .eq(UnCommonNotice::getId, row.getId())
                .set(UnCommonNotice::getTitle, row.getTitle())
                .set(UnCommonNotice::getContent, row.getContent())
                .set(UnCommonNotice::getAudienceJson, row.getAudienceJson())
                .set(UnCommonNotice::getExpireTime, row.getExpireTime())
                .set(UnCommonNotice::getStatus, row.getStatus())
                .set(UnCommonNotice::getPublishTime, null)
                .set(UnCommonNotice::getUpdateTime, row.getUpdateTime()));
        audit(context, "NOTICE_SAVE", row.getId());
        return view(row);
    }

    /** 发布前检查范围引用属于当前租户，避免错误配置成为不可见公告。 */
    @Transactional(rollbackFor = Exception.class)
    public NoticeView publish(String token, Long systemId, Long tenantId, Long noticeId) {
        SystemContext context = systems.requireAdminContext(token, systemId, tenantId);
        UnCommonNotice row = owned(context, noticeId);
        if (row.getExpireTime() != null && !row.getExpireTime().isAfter(LocalDateTime.now()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "公告到期时间必须晚于发布时间");
        JsonNode audience = audience(row);
        validateAudienceShape(audience);
        for (Long accountId : ids(audience, "accountIds")) {
            UnSysMember member = members.getOne(new LambdaQueryWrapper<UnSysMember>()
                    .eq(UnSysMember::getSystemId, systemId).eq(UnSysMember::getAccountId, accountId)
                    .eq(UnSysMember::getStatus, "ACTIVE"), false);
            if (member == null || tenantMembers.getOne(new LambdaQueryWrapper<UnSysTenantMember>()
                    .eq(UnSysTenantMember::getSystemId, systemId)
                    .eq(UnSysTenantMember::getTenantId, context.tenantId())
                    .eq(UnSysTenantMember::getMemberId, member.getId())
                    .eq(UnSysTenantMember::getStatus, "ACTIVE"), false) == null)
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "公告可见账号不属于当前租户");
        }
        for (Long id : ids(audience, "departmentIds")) {
            UnSysDepartment dept = departments.getById(id);
            if (dept == null || !context.systemId().equals(dept.getSystemId())
                    || !context.tenantId().equals(dept.getTenantId()) || !"ACTIVE".equals(dept.getStatus()))
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "公告可见部门无效");
        }
        for (Long id : ids(audience, "roleIds")) {
            UnSysRole role = roles.getById(id);
            if (role == null || !context.systemId().equals(role.getSystemId())
                    || !context.tenantId().equals(role.getTenantId()) || !"ACTIVE".equals(role.getStatus()))
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "公告可见角色无效");
        }
        row.setStatus("PUBLISHED"); row.setPublishTime(LocalDateTime.now()); row.setUpdateTime(LocalDateTime.now());
        notices.addOrUpdate(row); audit(context, "NOTICE_PUBLISH", row.getId());
        return view(row);
    }

    /** 停用仅影响后续读取，历史公告保持原样。 */
    @Transactional(rollbackFor = Exception.class)
    public NoticeView disable(String token, Long systemId, Long tenantId, Long noticeId) {
        SystemContext context = systems.requireAdminContext(token, systemId, tenantId);
        UnCommonNotice row = owned(context, noticeId);
        row.setStatus("DISABLED"); row.setUpdateTime(LocalDateTime.now());
        notices.addOrUpdate(row); audit(context, "NOTICE_DISABLE", row.getId());
        return view(row);
    }

    private boolean visible(SystemContext context, UnCommonNotice row) {
        JsonNode audience = audience(row);
        Set<Long> accounts = ids(audience, "accountIds");
        Set<Long> depts = ids(audience, "departmentIds");
        Set<Long> roleIds = ids(audience, "roleIds");
        if (accounts.isEmpty() && depts.isEmpty() && roleIds.isEmpty()) return true;
        if (accounts.contains(context.accountId())) return true;
        UnSysMember member = members.getOne(new LambdaQueryWrapper<UnSysMember>()
                .eq(UnSysMember::getSystemId, context.systemId())
                .eq(UnSysMember::getAccountId, context.accountId())
                .eq(UnSysMember::getStatus, "ACTIVE"), false);
        if (member == null) return false;
        UnSysTenantMember tenantMember = tenantMembers.getOne(new LambdaQueryWrapper<UnSysTenantMember>()
                .eq(UnSysTenantMember::getSystemId, context.systemId())
                .eq(UnSysTenantMember::getTenantId, context.tenantId())
                .eq(UnSysTenantMember::getMemberId, member.getId())
                .eq(UnSysTenantMember::getStatus, "ACTIVE"), false);
        if (tenantMember == null) return false;
        if (depts.contains(tenantMember.getDepartmentId()) && departments.exists(
                new LambdaQueryWrapper<UnSysDepartment>()
                        .eq(UnSysDepartment::getId, tenantMember.getDepartmentId())
                        .eq(UnSysDepartment::getStatus, "ACTIVE"))) return true;
        // 发布后组织关系仍可能失效，读取时只按当前有效角色判定。
        return roleIds.stream().anyMatch(roleId -> roles.exists(new LambdaQueryWrapper<UnSysRole>()
                .eq(UnSysRole::getId, roleId).eq(UnSysRole::getStatus, "ACTIVE"))
                && memberRoles.exists(new LambdaQueryWrapper<UnSysMemberRole>()
                .eq(UnSysMemberRole::getTenantMemberId, tenantMember.getId())
                .eq(UnSysMemberRole::getRoleId, roleId)));
    }

    private void validateAudienceShape(JsonNode audience) {
        if (audience == null || audience.isNull()) return;
        if (!audience.isObject() || audience.size() > 3)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "公告可见范围无效");
        audience.fieldNames().forEachRemaining(key -> {
            if (!Set.of("accountIds", "departmentIds", "roleIds").contains(key))
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "公告可见范围包含未知字段");
            ids(audience, key);
        });
    }

    private String status(String value) {
        String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
        if (!Set.of("DRAFT", "PUBLISHED", "DISABLED").contains(normalized))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "公告状态无效");
        return normalized;
    }

    private Set<Long> ids(JsonNode audience, String key) {
        if (audience == null || audience.isNull() || !audience.has(key)) return Set.of();
        JsonNode values = audience.get(key);
        if (!values.isArray() || values.size() > 500)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "公告可见范围必须是至多 500 个 ID");
        Set<Long> result = new HashSet<>();
        values.forEach(value -> {
            if (!value.canConvertToLong() || value.asLong() < 1 || !result.add(value.asLong()))
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "公告可见范围主键无效或重复");
        });
        return result;
    }

    private JsonNode audience(UnCommonNotice row) {
        if (row.getAudienceJson() == null) return null;
        try { return json.readTree(row.getAudienceJson()); }
        catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "公告可见范围无法读取", ex); }
    }

    private UnCommonNotice owned(SystemContext context, Long noticeId) {
        UnCommonNotice row = notices.getById(noticeId);
        if (row == null || !"SYSTEM".equals(row.getScopeType())
                || !context.systemId().equals(row.getSystemId())
                || !context.tenantId().equals(row.getTenantId()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "公告不存在");
        return row;
    }

    private NoticeView view(UnCommonNotice row) {
        return new NoticeView(row.getId(), row.getSystemId(), row.getTenantId(), row.getTitle(),
                row.getContent(), audience(row), row.getStatus(), row.getPublishTime(),
                row.getExpireTime(), row.getCreateTime());
    }

    private void audit(SystemContext context, String action, Long noticeId) {
        UnSysLogOpt log = new UnSysLogOpt();
        log.setSystemId(context.systemId()); log.setTenantId(context.tenantId());
        log.setAccountId(context.accountId()); log.setActionCode(action);
        log.setObjectType("NOTICE"); log.setObjectId(noticeId);
        log.setDetailJson("{\"noticeId\":" + noticeId + "}");
        logs.addOrUpdate(log);
    }
}
