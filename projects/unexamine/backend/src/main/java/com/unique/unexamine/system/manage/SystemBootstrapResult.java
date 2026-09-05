package com.unique.unexamine.system.manage;

import com.unique.unexamine.system.base.entity.SystemDefinition;
import com.unique.unexamine.system.base.entity.SystemDepartment;
import com.unique.unexamine.system.base.entity.SystemMember;
import com.unique.unexamine.system.base.entity.SystemRole;
import com.unique.unexamine.system.base.entity.SystemTenant;
import com.unique.unexamine.system.base.entity.SystemTenantMember;

public record SystemBootstrapResult(
        SystemDefinition system,
        SystemTenant tenant,
        SystemDepartment rootDepartment,
        SystemMember member,
        SystemTenantMember tenantMember,
        SystemRole administratorRole) {
}
