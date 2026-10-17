package com.unique.unexamine.system.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.unique.unexamine.shared.manage.web.DomainException;
import com.unique.unexamine.system.base.entity.SystemDefinition;
import com.unique.unexamine.system.base.entity.SystemDepartment;
import com.unique.unexamine.system.base.entity.SystemMember;
import com.unique.unexamine.system.base.entity.SystemRole;
import com.unique.unexamine.system.base.entity.SystemRolePermission;
import com.unique.unexamine.system.base.entity.SystemTenant;
import com.unique.unexamine.system.base.entity.SystemTenantMember;
import com.unique.unexamine.system.base.service.SystemDefinitionBaseService;
import com.unique.unexamine.system.base.service.SystemDepartmentBaseService;
import com.unique.unexamine.system.base.service.SystemMemberBaseService;
import com.unique.unexamine.system.base.service.SystemRoleBaseService;
import com.unique.unexamine.system.base.service.SystemRolePermissionBaseService;
import com.unique.unexamine.system.base.service.SystemTenantBaseService;
import com.unique.unexamine.system.base.service.SystemTenantMemberBaseService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/** 新系统统一初始化主租户、根部门和初始成员；多租户由系统后台后续启用。 */
@Service
public class SystemBootstrapService {
    private static final Pattern SYSTEM_CODE = Pattern.compile("[a-z][a-z0-9_]{1,99}");

    private final SystemDefinitionBaseService systemService;
    private final SystemTenantBaseService tenantService;
    private final SystemDepartmentBaseService departmentService;
    private final SystemMemberBaseService memberService;
    private final SystemTenantMemberBaseService tenantMemberService;
    private final SystemRoleBaseService roleService;
    private final SystemRolePermissionBaseService rolePermissionService;

    public SystemBootstrapService(
            SystemDefinitionBaseService systemService,
            SystemTenantBaseService tenantService,
            SystemDepartmentBaseService departmentService,
            SystemMemberBaseService memberService,
            SystemTenantMemberBaseService tenantMemberService,
            SystemRoleBaseService roleService,
            SystemRolePermissionBaseService rolePermissionService) {
        this.systemService = systemService;
        this.tenantService = tenantService;
        this.departmentService = departmentService;
        this.memberService = memberService;
        this.tenantMemberService = tenantMemberService;
        this.roleService = roleService;
        this.rolePermissionService = rolePermissionService;
    }

    @Transactional
    public SystemBootstrapResult bootstrap(
            Long platformId,
            Long accountId,
            String displayName,
            String systemName,
            String requestedCode) {
        String normalizedName = requireName(systemName);
        // 新系统从单租户开始；多租户仅由系统后台开关启用。
        String tenantMode = "SINGLE";
        String systemCode = resolveSystemCode(platformId, requestedCode);

        SystemDefinition system = new SystemDefinition();
        system.setPlatformId(platformId);
        system.setCode(systemCode);
        system.setName(normalizedName);
        system.setCreatorAccountId(accountId);
        system.setTenantMode(tenantMode);
        system.setStatus("ACTIVE");
        system.setDeleted(false);
        systemService.insert(system);

        SystemTenant tenant = new SystemTenant();
        tenant.setSystemId(system.getId());
        tenant.setCode("main");
        tenant.setName("默认主租户");
        tenant.setMain(true);
        tenant.setMainMarker("MAIN");
        tenant.setCreatorAccountId(accountId);
        tenant.setStatus("ACTIVE");
        tenantService.insert(tenant);

        SystemDepartment rootDepartment = initializeRootDepartment(system.getId(), tenant.getId());

        SystemMember member = new SystemMember();
        member.setSystemId(system.getId());
        member.setAccountId(accountId);
        member.setDisplayName(displayName == null || displayName.isBlank() ? "系统创建人" : displayName.strip());
        member.setStatus("ACTIVE");
        memberService.insert(member);

        SystemTenantMember tenantMember = new SystemTenantMember();
        tenantMember.setSystemId(system.getId());
        tenantMember.setTenantId(tenant.getId());
        tenantMember.setSystemMemberId(member.getId());
        tenantMember.setDepartmentId(rootDepartment.getId());
        tenantMember.setTenantAdmin(false);
        tenantMember.setStatus("ACTIVE");
        tenantMemberService.insert(tenantMember);

        SystemRole administratorRole = new SystemRole();
        administratorRole.setSystemId(system.getId());
        administratorRole.setTenantId(tenant.getId());
        administratorRole.setCode("SYSTEM_SUPER_ADMIN");
        administratorRole.setName("系统超级管理员");
        administratorRole.setDescription("系统创建时自动生成的内置管理员角色");
        administratorRole.setBuiltIn(true);
        administratorRole.setStatus("ACTIVE");
        roleService.insert(administratorRole);

        SystemRolePermission permission = new SystemRolePermission();
        permission.setSystemId(system.getId());
        permission.setTenantId(tenant.getId());
        permission.setRoleId(administratorRole.getId());
        permission.setResourceType("*");
        permission.setResourceCode("*");
        permission.setActionCode("*");
        permission.setDataScopeType("ALL");
        rolePermissionService.insert(permission);

        // 当前所属人的全权限来自系统归属，不绑定显式超管角色，转让后即可立即失去隐式权限。

        return new SystemBootstrapResult(system, tenant, rootDepartment, member, tenantMember, administratorRole);
    }

    private String requireName(String value) {
        if (value == null || value.isBlank()) {
            throw new DomainException("SYSTEM_NAME_REQUIRED", "请填写系统名称", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        return value.strip();
    }

    /** 新系统主租户和新增租户共用默认部门；由调用方的创建事务保证失败整体回滚。 */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public SystemDepartment initializeRootDepartment(Long systemId, Long tenantId) {
        SystemDepartment root = new SystemDepartment();
        root.setSystemId(systemId);
        root.setTenantId(tenantId);
        root.setParentId(null);
        root.setCode("root");
        root.setName("全公司");
        root.setPathCode("/root");
        root.setSortOrder(0);
        root.setStatus("ACTIVE");
        departmentService.insert(root);
        return root;
    }

    private String resolveSystemCode(Long platformId, String requestedCode) {
        if (requestedCode != null && !requestedCode.isBlank()) {
            String normalized = requestedCode.strip().toLowerCase(Locale.ROOT);
            if (!SYSTEM_CODE.matcher(normalized).matches()) {
                throw new DomainException("SYSTEM_CODE_INVALID", "系统标识格式无效", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            requireCodeAvailable(platformId, normalized);
            return normalized;
        }
        for (int attempt = 0; attempt < 5; attempt++) {
            String generated = "system_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
            if (codeAvailable(platformId, generated)) {
                return generated;
            }
        }
        throw new DomainException("SYSTEM_CODE_GENERATION_FAILED", "系统初始化失败，请重试", HttpStatus.CONFLICT);
    }

    private void requireCodeAvailable(Long platformId, String code) {
        if (!codeAvailable(platformId, code)) {
            throw new DomainException("SYSTEM_CODE_EXISTS", "系统标识已经存在", HttpStatus.CONFLICT);
        }
    }

    private boolean codeAvailable(Long platformId, String code) {
        return systemService.selectList(Wrappers.<SystemDefinition>lambdaQuery()
                .eq(SystemDefinition::getPlatformId, platformId)
                .eq(SystemDefinition::getCode, code)).isEmpty();
    }
}
