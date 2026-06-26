package com.unique.examine.plat.manage.role;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.unique.examine.core.api.PageRequest;
import com.unique.examine.core.api.PageResult;
import com.unique.examine.core.context.RequestContext;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.error.CommonErrorCode;
import com.unique.examine.plat.base.entity.PlatRole;
import com.unique.examine.plat.base.entity.PlatRoleMember;
import com.unique.examine.plat.base.service.PlatRoleBaseService;
import com.unique.examine.plat.base.service.PlatRoleMemberBaseService;
import com.unique.examine.plat.manage.common.SystemMemberContextResolver;
import com.unique.examine.plat.manage.common.SystemMemberContextResolver.SystemMemberContext;
import com.unique.examine.plat.manage.role.RoleModels.RoleMemberAssignRequest;
import com.unique.examine.plat.manage.role.RoleModels.RoleMemberAssignResult;
import com.unique.examine.plat.manage.role.RoleModels.RoleQueryRequest;
import com.unique.examine.plat.manage.role.RoleModels.RoleSaveRequest;
import com.unique.examine.plat.manage.role.RoleModels.RoleVO;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Role management service backed by persisted role and role-member tables.
 */
@Service
public class RoleService {

    private static final int ENABLED = 1;
    private static final int DELETED_NO = 0;
    private static final String SCOPE_PLATFORM = "PLATFORM";
    private static final String SCOPE_SYSTEM = "SYSTEM";

    private final SystemMemberContextResolver contextResolver;
    private final PlatRoleBaseService roleBaseService;
    private final PlatRoleMemberBaseService roleMemberBaseService;

    public RoleService(SystemMemberContextResolver contextResolver,
                       PlatRoleBaseService roleBaseService,
                       PlatRoleMemberBaseService roleMemberBaseService) {
        this.contextResolver = contextResolver;
        this.roleBaseService = roleBaseService;
        this.roleMemberBaseService = roleMemberBaseService;
    }

    /**
     * Search platform roles.
     *
     * @param pageRequest page request
     * @param query query request
     * @return role page
     */
    public PageResult<RoleVO> platformRoles(PageRequest pageRequest, RoleQueryRequest query) {
        return search(SCOPE_PLATFORM, null, null, pageRequest, query);
    }

    /**
     * Search system roles.
     *
     * @param systemId system id
     * @param pageRequest page request
     * @param query query request
     * @return role page
     */
    public PageResult<RoleVO> systemRoles(String systemId, PageRequest pageRequest, RoleQueryRequest query) {
        SystemMemberContext context = contextResolver.resolve(systemId);
        return search(SCOPE_SYSTEM, context.systemId(), context.tenantId(), pageRequest, query);
    }

    /**
     * Save a platform role.
     *
     * @param request role request
     * @return role
     */
    @Transactional(rollbackFor = Exception.class)
    public RoleVO createPlatformRole(RoleSaveRequest request) {
        return saveRole(SCOPE_PLATFORM, null, null, null, request);
    }

    /**
     * Update a platform role.
     *
     * @param roleId role id
     * @param request role request
     * @return role
     */
    @Transactional(rollbackFor = Exception.class)
    public RoleVO updatePlatformRole(String roleId, RoleSaveRequest request) {
        return saveRole(SCOPE_PLATFORM, null, null, roleId, request);
    }

    /**
     * Save a system role.
     *
     * @param systemId system id
     * @param request role request
     * @return role
     */
    @Transactional(rollbackFor = Exception.class)
    public RoleVO createSystemRole(String systemId, RoleSaveRequest request) {
        SystemMemberContext context = contextResolver.resolve(systemId);
        return saveRole(SCOPE_SYSTEM, context.systemId(), context.tenantId(), null, request);
    }

    /**
     * Update a system role.
     *
     * @param systemId system id
     * @param roleId role id
     * @param request role request
     * @return role
     */
    @Transactional(rollbackFor = Exception.class)
    public RoleVO updateSystemRole(String systemId, String roleId, RoleSaveRequest request) {
        SystemMemberContext context = contextResolver.resolve(systemId);
        return saveRole(SCOPE_SYSTEM, context.systemId(), context.tenantId(), roleId, request);
    }

    /**
     * Assign members to a role.
     *
     * @param systemId system id
     * @param roleId role id
     * @param request assign request
     * @return assign result
     */
    @Transactional(rollbackFor = Exception.class)
    public RoleMemberAssignResult assignMembers(String systemId, String roleId, RoleMemberAssignRequest request) {
        SystemMemberContext context = contextResolver.resolve(systemId);
        PlatRole role = requireRole(SCOPE_SYSTEM, context.systemId(), context.tenantId(), roleId);
        int assigned = 0;
        for (String systemMemberId : safeList(request == null ? null : request.systemMemberIds())) {
            Long memberId = contextResolver.parseRequiredId(systemMemberId, "成员ID格式不正确");
            if (createRoleMemberIfAbsent(role.getId(), null, memberId, context.systemId(), context.tenantId())) {
                assigned++;
            }
        }
        for (String accountId : safeList(request == null ? null : request.accountIds())) {
            Long id = contextResolver.parseRequiredId(accountId, "账号ID格式不正确");
            if (createRoleMemberIfAbsent(role.getId(), id, null, context.systemId(), context.tenantId())) {
                assigned++;
            }
        }
        RequestContext requestContext = RequestContext.current();
        return new RoleMemberAssignResult(String.valueOf(role.getId()), assigned, requestContext.traceId(),
                "aud_" + requestContext.traceId());
    }

    private PageResult<RoleVO> search(String scope, Long systemId, Long tenantId, PageRequest pageRequest,
                                      RoleQueryRequest query) {
        int pageNo = pageRequest == null || pageRequest.pageNo() <= 0 ? 1 : pageRequest.pageNo();
        int pageSize = pageRequest == null || pageRequest.pageSize() <= 0 ? 20 : pageRequest.pageSize();
        int offset = (pageNo - 1) * pageSize;
        LambdaQueryWrapper<PlatRole> wrapper = roleQuery(scope, systemId, tenantId, query);
        long total = roleBaseService.count(wrapper);
        List<RoleVO> records = roleBaseService.list(roleQuery(scope, systemId, tenantId, query)
                        .orderByDesc(PlatRole::getBuiltin)
                        .orderByDesc(PlatRole::getUpdatedAt)
                        .last("LIMIT " + offset + "," + pageSize))
                .stream()
                .map(this::toVO)
                .toList();
        return new PageResult<>(records, pageNo, pageSize, total, offset + records.size() < total);
    }

    private RoleVO saveRole(String scope, Long systemId, Long tenantId, String roleId, RoleSaveRequest request) {
        requireText(request == null ? null : request.roleCode(), "角色编码不能为空");
        requireText(request.roleName(), "角色名称不能为空");
        PlatRole role = StringUtils.hasText(roleId) ? requireRole(scope, systemId, tenantId, roleId) : new PlatRole();
        if (Objects.isNull(role.getId())) {
            role.setScope(scope);
            role.setSystemId(systemId);
            role.setTenantId(tenantId);
            role.setRoleCode(request.roleCode());
            role.setBuiltin(0);
            role.setCreatedAt(LocalDateTime.now());
            role.setDeleted(DELETED_NO);
            if (roleBaseService.count(new LambdaQueryWrapper<PlatRole>()
                    .eq(PlatRole::getScope, scope)
                    .eq(Objects.nonNull(systemId), PlatRole::getSystemId, systemId)
                    .eq(Objects.nonNull(tenantId), PlatRole::getTenantId, tenantId)
                    .eq(PlatRole::getRoleCode, request.roleCode())
                    .eq(PlatRole::getDeleted, DELETED_NO)) > 0) {
                throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "角色编码已存在");
            }
        } else if (Objects.equals(role.getBuiltin(), 1)
                && (!Objects.equals(role.getRoleCode(), request.roleCode())
                || !Objects.equals(role.getRoleType(), request.roleType()))) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "内置角色不允许修改编码和类型");
        }
        role.setRoleName(request.roleName());
        role.setRoleType(safeText(request.roleType(), "CUSTOM"));
        role.setStatus(Objects.isNull(request.status()) ? ENABLED : request.status());
        role.setDescription(request.description());
        role.setUpdatedAt(LocalDateTime.now());
        if (Objects.isNull(role.getId())) {
            roleBaseService.saveEntity(role);
        } else {
            roleBaseService.updateById(role);
        }
        return toVO(role);
    }

    private PlatRole requireRole(String scope, Long systemId, Long tenantId, String roleId) {
        Long id = contextResolver.parseRequiredId(roleId, "角色ID格式不正确");
        PlatRole role = roleBaseService.getOne(new LambdaQueryWrapper<PlatRole>()
                .eq(PlatRole::getId, id)
                .eq(PlatRole::getScope, scope)
                .eq(Objects.nonNull(systemId), PlatRole::getSystemId, systemId)
                .eq(Objects.nonNull(tenantId), PlatRole::getTenantId, tenantId)
                .eq(PlatRole::getDeleted, DELETED_NO)
                .last("LIMIT 1"), false);
        if (Objects.isNull(role)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "角色不存在");
        }
        return role;
    }

    private LambdaQueryWrapper<PlatRole> roleQuery(String scope, Long systemId, Long tenantId, RoleQueryRequest query) {
        LambdaQueryWrapper<PlatRole> wrapper = new LambdaQueryWrapper<PlatRole>()
                .eq(PlatRole::getScope, scope)
                .eq(Objects.nonNull(systemId), PlatRole::getSystemId, systemId)
                .eq(Objects.nonNull(tenantId), PlatRole::getTenantId, tenantId)
                .eq(PlatRole::getDeleted, DELETED_NO);
        if (Objects.nonNull(query)) {
            if (StringUtils.hasText(query.roleType())) {
                wrapper.eq(PlatRole::getRoleType, query.roleType());
            }
            if (Objects.nonNull(query.status())) {
                wrapper.eq(PlatRole::getStatus, query.status());
            }
            if (StringUtils.hasText(query.keyword())) {
                wrapper.and(value -> value.like(PlatRole::getRoleName, query.keyword())
                        .or().like(PlatRole::getRoleCode, query.keyword()));
            }
        }
        return wrapper;
    }

    private boolean createRoleMemberIfAbsent(Long roleId, Long accountId, Long systemMemberId, Long systemId,
                                             Long tenantId) {
        LambdaQueryWrapper<PlatRoleMember> wrapper = new LambdaQueryWrapper<PlatRoleMember>()
                .eq(PlatRoleMember::getRoleId, roleId);
        if (Objects.nonNull(accountId)) {
            wrapper.eq(PlatRoleMember::getAccountId, accountId);
        } else {
            wrapper.isNull(PlatRoleMember::getAccountId);
        }
        if (Objects.nonNull(systemMemberId)) {
            wrapper.eq(PlatRoleMember::getSystemMemberId, systemMemberId);
        } else {
            wrapper.isNull(PlatRoleMember::getSystemMemberId);
        }
        if (roleMemberBaseService.count(wrapper) > 0) {
            return false;
        }
        PlatRoleMember roleMember = new PlatRoleMember();
        roleMember.setRoleId(roleId);
        roleMember.setAccountId(accountId);
        roleMember.setSystemMemberId(systemMemberId);
        roleMember.setSystemId(systemId);
        roleMember.setTenantId(tenantId);
        roleMember.setCreatedAt(LocalDateTime.now());
        roleMemberBaseService.saveEntity(roleMember);
        return true;
    }

    private RoleVO toVO(PlatRole role) {
        return new RoleVO(String.valueOf(role.getId()), role.getScope(),
                Objects.isNull(role.getSystemId()) ? null : String.valueOf(role.getSystemId()),
                Objects.isNull(role.getTenantId()) ? null : String.valueOf(role.getTenantId()),
                role.getRoleCode(), role.getRoleName(), role.getRoleType(), Objects.equals(role.getBuiltin(), 1),
                role.getStatus(), role.getDescription(), role.getUpdatedAt());
    }

    private List<String> safeList(List<String> value) {
        return Objects.isNull(value) ? List.of() : value;
    }

    private String safeText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, message);
        }
    }
}
