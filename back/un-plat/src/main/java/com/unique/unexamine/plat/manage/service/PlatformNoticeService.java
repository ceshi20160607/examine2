package com.unique.unexamine.plat.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.common.base.entity.UnCommonNotice;
import com.unique.unexamine.core.common.base.service.IUnCommonNoticeService;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.plat.base.entity.UnPlatAccount;
import com.unique.unexamine.plat.base.entity.UnPlatDepartment;
import com.unique.unexamine.plat.base.entity.UnPlatLogOpt;
import com.unique.unexamine.plat.base.entity.UnPlatMember;
import com.unique.unexamine.plat.base.entity.UnPlatMemberRole;
import com.unique.unexamine.plat.base.entity.UnPlatRole;
import com.unique.unexamine.plat.base.service.IUnPlatAccountService;
import com.unique.unexamine.plat.base.service.IUnPlatDepartmentService;
import com.unique.unexamine.plat.base.service.IUnPlatLogOptService;
import com.unique.unexamine.plat.base.service.IUnPlatMemberRoleService;
import com.unique.unexamine.plat.base.service.IUnPlatMemberService;
import com.unique.unexamine.plat.base.service.IUnPlatRoleService;
import com.unique.unexamine.plat.manage.dto.PlatformNoticeDto.NoticeSearch;
import com.unique.unexamine.plat.manage.dto.PlatformNoticeDto.NoticeView;
import com.unique.unexamine.plat.manage.dto.PlatformNoticeDto.SaveRequest;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 平台公告由管理员发布，读取时按当前有效的账号和组织关系判断可见范围。 */
@Service
public class PlatformNoticeService {
    private final AuthManageService auth;
    private final IUnCommonNoticeService notices;
    private final IUnPlatAccountService accounts;
    private final IUnPlatMemberService members;
    private final IUnPlatMemberRoleService memberRoles;
    private final IUnPlatDepartmentService departments;
    private final IUnPlatRoleService roles;
    private final IUnPlatLogOptService logs;
    private final ObjectMapper json;

    public PlatformNoticeService(AuthManageService auth, IUnCommonNoticeService notices,
            IUnPlatAccountService accounts, IUnPlatMemberService members,
            IUnPlatMemberRoleService memberRoles, IUnPlatDepartmentService departments,
            IUnPlatRoleService roles, IUnPlatLogOptService logs, ObjectMapper json) {
        this.auth = auth; this.notices = notices; this.accounts = accounts; this.members = members;
        this.memberRoles = memberRoles; this.departments = departments; this.roles = roles;
        this.logs = logs; this.json = json;
    }

    /** 按有效公告和当前成员关系筛选后分页，确保分页总数与实际可见数一致。 */
    public Page<NoticeView> list(String token, NoticeSearch search) {
        Long accountId = auth.requireAccount(token).getId();
        NoticeSearch criteria = search == null ? new NoticeSearch() : search;
        Page<NoticeView> requested = criteria.parse();
        LocalDateTime now = LocalDateTime.now();
        List<NoticeView> visible = notices.list(new LambdaQueryWrapper<UnCommonNotice>()
                .eq(UnCommonNotice::getScopeType, "PLATFORM")
                .eq(UnCommonNotice::getStatus, "PUBLISHED")
                .le(UnCommonNotice::getPublishTime, now)
                .and(w -> w.isNull(UnCommonNotice::getExpireTime).or().gt(UnCommonNotice::getExpireTime, now))
                .orderByDesc(UnCommonNotice::getPublishTime))
                .stream().filter(row -> visible(accountId, row)).map(this::view).toList();
        int from = (int) Math.min(visible.size(), (requested.getCurrent() - 1) * requested.getSize());
        int to = (int) Math.min(visible.size(), from + requested.getSize());
        return new Page<NoticeView>(requested.getCurrent(), requested.getSize(), visible.size())
                .setRecords(visible.subList(from, to));
    }

    /** 管理员分页查看所有平台公告配置。 */
    public Page<NoticeView> manage(String token, NoticeSearch search) {
        auth.requirePlatformAdmin(token);
        NoticeSearch criteria = search == null ? new NoticeSearch() : search;
        var where = new LambdaQueryWrapper<UnCommonNotice>()
                .eq(UnCommonNotice::getScopeType, "PLATFORM").orderByDesc(UnCommonNotice::getId);
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

    /** 保存为草稿；修改已发布公告须重新发布才会对账号可见。 */
    @Transactional(rollbackFor = Exception.class)
    public NoticeView save(String token, SaveRequest request) {
        Long operatorId = auth.requirePlatformAdmin(token).getId();
        if (request == null || request.title() == null || request.title().isBlank()
                || request.title().length() > 200 || request.content() == null
                || request.content().isBlank() || request.content().length() > 15000)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "公告内容无效");
        validateAudience(request.audience());
        UnCommonNotice row = request.id() == null ? new UnCommonNotice() : owned(request.id());
        boolean creating = row.getId() == null;
        if (creating) {
            row.setScopeType("PLATFORM"); row.setCreateAccountId(operatorId);
            row.setCreateTime(LocalDateTime.now());
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
        audit(operatorId, "PLATFORM_NOTICE_SAVE", row.getId());
        return view(row);
    }

    /** 发布前核对可见范围引用，避免错误的账号和组织 ID 进入运行态。 */
    @Transactional(rollbackFor = Exception.class)
    public NoticeView publish(String token, Long noticeId) {
        Long operatorId = auth.requirePlatformAdmin(token).getId();
        UnCommonNotice row = owned(noticeId);
        if (row.getExpireTime() != null && !row.getExpireTime().isAfter(LocalDateTime.now()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "公告到期时间必须晚于发布时间");
        JsonNode audience = audience(row);
        validateAudience(audience);
        for (Long id : ids(audience, "accountIds")) {
            UnPlatAccount account = accounts.getById(id);
            if (account == null || !"ACTIVE".equals(account.getStatus()))
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "公告可见账号无效");
        }
        for (Long id : ids(audience, "departmentIds")) {
            UnPlatDepartment dept = departments.getById(id);
            if (dept == null || !"ACTIVE".equals(dept.getStatus()))
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "公告可见部门无效");
        }
        for (Long id : ids(audience, "roleIds")) {
            UnPlatRole role = roles.getById(id);
            if (role == null || !"ACTIVE".equals(role.getStatus()))
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "公告可见角色无效");
        }
        row.setStatus("PUBLISHED"); row.setPublishTime(LocalDateTime.now()); row.setUpdateTime(LocalDateTime.now());
        notices.addOrUpdate(row); audit(operatorId, "PLATFORM_NOTICE_PUBLISH", row.getId());
        return view(row);
    }

    /** 停用后即从阅读列表消失，保留历史公告。 */
    @Transactional(rollbackFor = Exception.class)
    public NoticeView disable(String token, Long noticeId) {
        Long operatorId = auth.requirePlatformAdmin(token).getId();
        UnCommonNotice row = owned(noticeId);
        row.setStatus("DISABLED"); row.setUpdateTime(LocalDateTime.now());
        notices.addOrUpdate(row); audit(operatorId, "PLATFORM_NOTICE_DISABLE", row.getId());
        return view(row);
    }

    private boolean visible(Long accountId, UnCommonNotice row) {
        JsonNode audience = audience(row);
        Set<Long> accountIds = ids(audience, "accountIds");
        Set<Long> departmentIds = ids(audience, "departmentIds");
        Set<Long> roleIds = ids(audience, "roleIds");
        if (accountIds.isEmpty() && departmentIds.isEmpty() && roleIds.isEmpty()) return true;
        if (accountIds.contains(accountId)) return true;
        UnPlatMember member = members.getOne(new LambdaQueryWrapper<UnPlatMember>()
                .eq(UnPlatMember::getAccountId, accountId).eq(UnPlatMember::getStatus, "ACTIVE"), false);
        if (member == null) return false;
        if (departmentIds.contains(member.getDepartmentId()) && departments.exists(
                new LambdaQueryWrapper<UnPlatDepartment>().eq(UnPlatDepartment::getId, member.getDepartmentId())
                        .eq(UnPlatDepartment::getStatus, "ACTIVE"))) return true;
        // 角色在公告发布后可能停用，阅读时按当前有效角色判定。
        return roleIds.stream().anyMatch(roleId -> roles.exists(new LambdaQueryWrapper<UnPlatRole>()
                .eq(UnPlatRole::getId, roleId).eq(UnPlatRole::getStatus, "ACTIVE"))
                && memberRoles.exists(new LambdaQueryWrapper<UnPlatMemberRole>()
                .eq(UnPlatMemberRole::getMemberId, member.getId())
                .eq(UnPlatMemberRole::getRoleId, roleId)));
    }

    private void validateAudience(JsonNode audience) {
        if (audience == null || audience.isNull()) return;
        if (!audience.isObject() || audience.size() > 3)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "公告可见范围无效");
        audience.fieldNames().forEachRemaining(key -> {
            if (!Set.of("accountIds", "departmentIds", "roleIds").contains(key))
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "公告可见范围包含未知字段");
            ids(audience, key);
        });
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

    private UnCommonNotice owned(Long id) {
        UnCommonNotice row = notices.getById(id);
        if (row == null || !"PLATFORM".equals(row.getScopeType()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "公告不存在");
        return row;
    }

    private String status(String value) {
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("DRAFT", "PUBLISHED", "DISABLED").contains(normalized))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "公告状态无效");
        return normalized;
    }

    private NoticeView view(UnCommonNotice row) {
        return new NoticeView(row.getId(), row.getTitle(), row.getContent(), audience(row), row.getStatus(),
                row.getPublishTime(), row.getExpireTime(), row.getCreateTime());
    }

    private void audit(Long accountId, String action, Long noticeId) {
        UnPlatLogOpt log = new UnPlatLogOpt();
        log.setAccountId(accountId); log.setActionCode(action); log.setObjectType("PLATFORM_NOTICE");
        log.setObjectId(noticeId); log.setCreateTime(LocalDateTime.now());
        logs.addOrUpdate(log);
    }
}
