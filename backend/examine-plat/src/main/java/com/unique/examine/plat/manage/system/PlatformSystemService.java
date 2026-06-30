package com.unique.examine.plat.manage.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.unique.examine.core.api.PageRequest;
import com.unique.examine.core.api.PageResult;
import com.unique.examine.core.context.RequestContext;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.error.CommonErrorCode;
import com.unique.examine.plat.base.entity.PlatAccount;
import com.unique.examine.plat.base.entity.PlatAccountMemberBinding;
import com.unique.examine.plat.base.entity.PlatDepartment;
import com.unique.examine.plat.base.entity.PlatMember;
import com.unique.examine.plat.base.entity.PlatRole;
import com.unique.examine.plat.base.entity.PlatRoleMember;
import com.unique.examine.plat.base.entity.PlatSystem;
import com.unique.examine.plat.base.entity.PlatTenant;
import com.unique.examine.plat.base.service.PlatAccountMemberBindingBaseService;
import com.unique.examine.plat.base.service.PlatDepartmentBaseService;
import com.unique.examine.plat.base.service.PlatMemberBaseService;
import com.unique.examine.plat.base.service.PlatRoleBaseService;
import com.unique.examine.plat.base.service.PlatRoleMemberBaseService;
import com.unique.examine.plat.base.service.PlatSystemBaseService;
import com.unique.examine.plat.base.service.PlatTenantBaseService;
import com.unique.examine.plat.manage.common.CurrentAccountProvider;
import com.unique.examine.plat.manage.system.SystemModels.LifecycleResult;
import com.unique.examine.plat.manage.system.SystemModels.PlatformHealthVO;
import com.unique.examine.plat.manage.system.SystemModels.SystemCreateRequest;
import com.unique.examine.plat.manage.system.SystemModels.SystemLifecycleRequest;
import com.unique.examine.plat.manage.system.SystemModels.SystemQueryRequest;
import com.unique.examine.plat.manage.system.SystemModels.SystemUpdateRequest;
import com.unique.examine.plat.manage.system.SystemModels.SystemVO;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Platform system lifecycle service backed by persisted platform system tables.
 */
@Service
public class PlatformSystemService {

    private static final int ENABLED = 1;
    private static final int DISABLED = 0;
    private static final int DELETED_NO = 0;
    private static final int DELETED_YES = 1;
    private static final String DEFAULT_TENANT_CODE = "default";
    private static final String DEFAULT_TENANT_NAME = "默认租户";
    private static final String DEFAULT_DEPARTMENT_CODE = "default_department";
    private static final String DEFAULT_DEPARTMENT_NAME = "默认部门";
    private static final String SCOPE_SYSTEM = "SYSTEM";
    private static final String SYSTEM_SUPER_ADMIN = "SYSTEM_SUPER_ADMIN";

    private final CurrentAccountProvider currentAccountProvider;
    private final PlatSystemBaseService systemBaseService;
    private final PlatTenantBaseService tenantBaseService;
    private final PlatDepartmentBaseService departmentBaseService;
    private final PlatMemberBaseService memberBaseService;
    private final PlatRoleBaseService roleBaseService;
    private final PlatRoleMemberBaseService roleMemberBaseService;
    private final PlatAccountMemberBindingBaseService bindingBaseService;

    public PlatformSystemService(CurrentAccountProvider currentAccountProvider,
                                 PlatSystemBaseService systemBaseService,
                                 PlatTenantBaseService tenantBaseService,
                                 PlatDepartmentBaseService departmentBaseService,
                                 PlatMemberBaseService memberBaseService,
                                 PlatRoleBaseService roleBaseService,
                                 PlatRoleMemberBaseService roleMemberBaseService,
                                 PlatAccountMemberBindingBaseService bindingBaseService) {
        this.currentAccountProvider = currentAccountProvider;
        this.systemBaseService = systemBaseService;
        this.tenantBaseService = tenantBaseService;
        this.departmentBaseService = departmentBaseService;
        this.memberBaseService = memberBaseService;
        this.roleBaseService = roleBaseService;
        this.roleMemberBaseService = roleMemberBaseService;
        this.bindingBaseService = bindingBaseService;
    }

    /**
     * Search platform systems.
     *
     * @param pageRequest page request
     * @param query filter request
     * @return system page
     */
    public PageResult<SystemVO> search(PageRequest pageRequest, SystemQueryRequest query) {
        int pageNo = pageRequest == null || pageRequest.pageNo() <= 0 ? 1 : pageRequest.pageNo();
        int pageSize = pageRequest == null || pageRequest.pageSize() <= 0 ? 20 : pageRequest.pageSize();
        int offset = (pageNo - 1) * pageSize;
        LambdaQueryWrapper<PlatSystem> wrapper = queryWrapper(query, false);
        long total = systemBaseService.count(wrapper);
        List<SystemVO> records = systemBaseService.list(queryWrapper(query, false)
                        .orderByDesc(PlatSystem::getCreatedAt)
                        .last("LIMIT " + offset + "," + pageSize))
                .stream()
                .map(this::toVO)
                .toList();
        return new PageResult<>(records, pageNo, pageSize, total, offset + records.size() < total);
    }

    /**
     * Create a system lifecycle record and bootstrap owner as system super admin.
     *
     * @param request create request
     * @return created system
     */
    @Transactional(rollbackFor = Exception.class)
    public SystemVO create(SystemCreateRequest request) {
        requireText(request.systemName(), "系统名称不能为空");
        String systemCode = normalizedCode(request.systemCode(), request.systemName());
        if (systemBaseService.count(new LambdaQueryWrapper<PlatSystem>()
                .eq(PlatSystem::getSystemCode, systemCode)
                .eq(PlatSystem::getDeleted, DELETED_NO)) > 0) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "系统编码已存在");
        }
        PlatAccount owner = currentAccountProvider.currentAccount();
        LocalDateTime now = LocalDateTime.now();
        PlatSystem system = new PlatSystem();
        system.setSystemCode(systemCode);
        system.setSystemName(request.systemName());
        system.setTenantMode(Objects.isNull(request.tenantMode()) ? 1 : request.tenantMode());
        system.setOwnerAccountId(owner.getId());
        system.setStatus(ENABLED);
        system.setCreatedAt(now);
        system.setUpdatedAt(now);
        system.setDeleted(DELETED_NO);
        systemBaseService.saveEntity(system);

        PlatTenant tenant = createDefaultTenant(system.getId(), now);
        PlatDepartment department = createDefaultDepartment(system.getId(), tenant.getId(), now);
        PlatMember member = createOwnerMember(owner, system.getId(), tenant.getId(), department.getId(), now);
        PlatRole role = createSystemSuperAdminRole(system.getId(), tenant.getId(), now);
        createRoleMember(role.getId(), owner.getId(), member.getId(), system.getId(), tenant.getId(), now);
        createBinding(owner.getId(), system.getId(), tenant.getId(), member.getId(), now);
        return toVO(system);
    }

    /**
     * Return one system detail.
     *
     * @param systemId system id
     * @return system detail
     */
    public SystemVO detail(String systemId) {
        return toVO(requireSystem(systemId));
    }

    /**
     * Update a system.
     *
     * @param systemId system id
     * @param request update request
     * @return updated system
     */
    public SystemVO update(String systemId, SystemUpdateRequest request) {
        PlatSystem system = requireSystem(systemId);
        if (StringUtils.hasText(request.systemName())) {
            system.setSystemName(request.systemName());
        }
        if (Objects.nonNull(request.tenantMode())) {
            system.setTenantMode(request.tenantMode());
        }
        system.setDisabledReason(request.disabledReason());
        system.setUpdatedAt(LocalDateTime.now());
        systemBaseService.updateById(system);
        return toVO(system);
    }

    /**
     * Apply lifecycle action.
     *
     * @param systemId system id
     * @param action lifecycle action
     * @param request lifecycle request
     * @return lifecycle result
     */
    public LifecycleResult lifecycle(String systemId, String action, SystemLifecycleRequest request) {
        PlatSystem system = requireSystem(systemId);
        String normalizedAction = action == null ? "" : action.toLowerCase();
        switch (normalizedAction) {
            case "enable" -> {
                system.setStatus(ENABLED);
                system.setDisabledReason(null);
            }
            case "disable" -> {
                system.setStatus(DISABLED);
                system.setDisabledReason(request == null ? null : request.reason());
            }
            case "delete" -> {
                system.setDeleted(DELETED_YES);
                system.setDisabledReason(request == null ? null : request.reason());
            }
            case "restore" -> {
                system.setDeleted(DELETED_NO);
                system.setStatus(ENABLED);
                system.setDisabledReason(null);
            }
            default -> throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "不支持的生命周期动作");
        }
        system.setUpdatedAt(LocalDateTime.now());
        systemBaseService.updateById(system);
        RequestContext context = RequestContext.current();
        return new LifecycleResult(normalizedAction.toUpperCase(), String.valueOf(system.getId()), context.traceId(),
                "aud_" + context.traceId(), null, LocalDateTime.now());
    }

    /**
     * Return platform health summary.
     *
     * @return health summary
     */
    public PlatformHealthVO health() {
        long enabledSystems = systemBaseService.count(new LambdaQueryWrapper<PlatSystem>()
                .eq(PlatSystem::getDeleted, DELETED_NO)
                .eq(PlatSystem::getStatus, ENABLED));
        List<String> warnings = enabledSystems == 0 ? List.of("NO_ENABLED_SYSTEM") : List.of();
        return new PlatformHealthVO("UP", List.of("database-schema", "identity-binding", "system-lifecycle"),
                warnings, RequestContext.current().traceId(), LocalDateTime.now());
    }

    private LambdaQueryWrapper<PlatSystem> queryWrapper(SystemQueryRequest query, boolean includeDeleted) {
        LambdaQueryWrapper<PlatSystem> wrapper = new LambdaQueryWrapper<>();
        if (!includeDeleted) {
            wrapper.eq(PlatSystem::getDeleted, DELETED_NO);
        }
        if (Objects.nonNull(query)) {
            Integer status = parseStatus(query.status());
            if (Objects.nonNull(status)) {
                wrapper.eq(PlatSystem::getStatus, status);
            }
            if (Objects.nonNull(query.tenantMode())) {
                wrapper.eq(PlatSystem::getTenantMode, query.tenantMode());
            }
            if (StringUtils.hasText(query.owner())) {
                Long ownerId = parseLong(query.owner());
                if (Objects.nonNull(ownerId)) {
                    wrapper.eq(PlatSystem::getOwnerAccountId, ownerId);
                }
            }
            if (StringUtils.hasText(query.keyword())) {
                wrapper.and(value -> value.like(PlatSystem::getSystemName, query.keyword())
                        .or().like(PlatSystem::getSystemCode, query.keyword()));
            }
        }
        return wrapper;
    }

    private PlatSystem requireSystem(String systemId) {
        Long id = parseLong(systemId);
        if (Objects.isNull(id)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "系统ID格式不正确");
        }
        PlatSystem system = systemBaseService.getOne(new LambdaQueryWrapper<PlatSystem>()
                .eq(PlatSystem::getId, id)
                .last("LIMIT 1"), false);
        if (Objects.isNull(system)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "系统不存在");
        }
        return system;
    }

    private SystemVO toVO(PlatSystem system) {
        return new SystemVO(String.valueOf(system.getId()), system.getSystemCode(), system.getSystemName(),
                system.getTenantMode(), String.valueOf(system.getOwnerAccountId()), system.getStatus(),
                system.getDisabledReason(), system.getCreatedAt(), system.getUpdatedAt());
    }

    private PlatTenant createDefaultTenant(Long systemId, LocalDateTime now) {
        PlatTenant tenant = new PlatTenant();
        tenant.setSystemId(systemId);
        tenant.setTenantCode(DEFAULT_TENANT_CODE);
        tenant.setTenantName(DEFAULT_TENANT_NAME);
        tenant.setStatus(ENABLED);
        tenant.setCreatedAt(now);
        tenant.setUpdatedAt(now);
        tenant.setDeleted(DELETED_NO);
        tenantBaseService.saveEntity(tenant);
        return tenant;
    }

    private PlatDepartment createDefaultDepartment(Long systemId, Long tenantId, LocalDateTime now) {
        PlatDepartment department = new PlatDepartment();
        department.setSystemId(systemId);
        department.setTenantId(tenantId);
        department.setParentId(null);
        department.setDeptCode(DEFAULT_DEPARTMENT_CODE);
        department.setDeptName(DEFAULT_DEPARTMENT_NAME);
        department.setSortOrder(10);
        department.setStatus(ENABLED);
        department.setCreatedAt(now);
        department.setUpdatedAt(now);
        department.setDeleted(DELETED_NO);
        departmentBaseService.saveEntity(department);
        return department;
    }

    private PlatMember createOwnerMember(PlatAccount owner, Long systemId, Long tenantId, Long departmentId,
                                         LocalDateTime now) {
        PlatMember member = new PlatMember();
        member.setSystemId(systemId);
        member.setTenantId(tenantId);
        member.setDeptId(departmentId);
        member.setMemberName(owner.getAccountName());
        member.setEmployeeNo("SA" + owner.getId());
        member.setMobile(owner.getMobile());
        member.setEmail(owner.getEmail());
        member.setStatus(ENABLED);
        member.setCreatedAt(now);
        member.setUpdatedAt(now);
        member.setDeleted(DELETED_NO);
        memberBaseService.saveEntity(member);
        return member;
    }

    private PlatRole createSystemSuperAdminRole(Long systemId, Long tenantId, LocalDateTime now) {
        PlatRole role = new PlatRole();
        role.setScope(SCOPE_SYSTEM);
        role.setSystemId(systemId);
        role.setTenantId(tenantId);
        role.setRoleCode(SYSTEM_SUPER_ADMIN);
        role.setRoleName("系统超级管理员");
        role.setRoleType(SYSTEM_SUPER_ADMIN);
        role.setBuiltin(ENABLED);
        role.setStatus(ENABLED);
        role.setDescription("创建系统时自动生成，拥有系统内全部权限");
        role.setCreatedAt(now);
        role.setUpdatedAt(now);
        role.setDeleted(DELETED_NO);
        roleBaseService.saveEntity(role);
        return role;
    }

    private void createRoleMember(Long roleId, Long accountId, Long memberId, Long systemId, Long tenantId,
                                  LocalDateTime now) {
        PlatRoleMember roleMember = new PlatRoleMember();
        roleMember.setRoleId(roleId);
        roleMember.setAccountId(accountId);
        roleMember.setSystemMemberId(memberId);
        roleMember.setSystemId(systemId);
        roleMember.setTenantId(tenantId);
        roleMember.setCreatedAt(now);
        roleMemberBaseService.saveEntity(roleMember);
    }

    private void createBinding(Long accountId, Long systemId, Long tenantId, Long memberId, LocalDateTime now) {
        PlatAccountMemberBinding binding = new PlatAccountMemberBinding();
        binding.setAccountId(accountId);
        binding.setSystemId(systemId);
        binding.setTenantId(tenantId);
        binding.setSystemMemberId(memberId);
        binding.setBindingStatus(ENABLED);
        binding.setCreatedAt(now);
        binding.setUpdatedAt(now);
        bindingBaseService.saveEntity(binding);
    }

    private void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, message);
        }
    }

    private String normalizedCode(String value, String fallback) {
        String candidate = StringUtils.hasText(value) ? value : fallback;
        return candidate.trim().toLowerCase().replaceAll("[^a-z0-9_\\-]", "_");
    }

    private Integer parseStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        return switch (status.toUpperCase()) {
            case "1", "ENABLED", "ENABLE", "UP" -> ENABLED;
            case "0", "DISABLED", "DISABLE", "DOWN" -> DISABLED;
            default -> null;
        };
    }

    private Long parseLong(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
