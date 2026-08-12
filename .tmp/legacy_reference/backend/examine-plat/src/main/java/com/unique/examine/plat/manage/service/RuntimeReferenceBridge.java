package com.unique.examine.plat.manage.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.unique.examine.core.api.RuntimeReferenceFacade;
import com.unique.examine.plat.base.entity.Department;
import com.unique.examine.plat.base.entity.Member;
import com.unique.examine.plat.base.entity.MemberDepartment;
import com.unique.examine.plat.base.entity.MemberTenant;
import com.unique.examine.plat.base.mapper.PlatDepartmentMapper;
import com.unique.examine.plat.base.mapper.PlatMemberDepartmentMapper;
import com.unique.examine.plat.base.mapper.PlatMemberMapper;
import com.unique.examine.plat.base.mapper.PlatMemberTenantMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class RuntimeReferenceBridge implements RuntimeReferenceFacade {
    private final PlatMemberMapper memberMapper;
    private final PlatMemberTenantMapper memberTenantMapper;
    private final PlatDepartmentMapper departmentMapper;
    private final PlatMemberDepartmentMapper memberDepartmentMapper;

    public RuntimeReferenceBridge(
            PlatMemberMapper memberMapper,
            PlatMemberTenantMapper memberTenantMapper,
            PlatDepartmentMapper departmentMapper,
            PlatMemberDepartmentMapper memberDepartmentMapper
    ) {
        this.memberMapper = memberMapper;
        this.memberTenantMapper = memberTenantMapper;
        this.departmentMapper = departmentMapper;
        this.memberDepartmentMapper = memberDepartmentMapper;
    }

    @Override
    public ReferenceCatalog resolve(long systemId, long tenantId, long memberId) {
        var now = LocalDateTime.now();
        var tenantMemberIds = memberTenantMapper.selectList(Wrappers.<MemberTenant>lambdaQuery()
                        .eq(MemberTenant::getSystemId, systemId)
                        .eq(MemberTenant::getTenantId, tenantId)
                        .eq(MemberTenant::getStatus, "ACTIVE")
                        .and(query -> query.isNull(MemberTenant::getExpiresAt)
                                .or().gt(MemberTenant::getExpiresAt, now)))
                .stream().map(MemberTenant::getMemberId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        tenantMemberIds.add(memberId);
        var members = memberMapper.selectList(Wrappers.<Member>lambdaQuery()
                        .eq(Member::getSystemId, systemId)
                        .eq(Member::getStatus, "ACTIVE")
                        .in(Member::getId, tenantMemberIds))
                .stream().sorted(Comparator.comparing(Member::getDisplayName).thenComparing(Member::getId))
                .map(member -> new ReferenceOption(Long.toString(member.getId()), member.getDisplayName()))
                .toList();

        var departments = departmentMapper.selectList(Wrappers.<Department>lambdaQuery()
                        .eq(Department::getScopeType, "SYSTEM")
                        .eq(Department::getScopeKey, systemId)
                        .eq(Department::getSystemId, systemId)
                        .eq(Department::getStatus, "ACTIVE")
                        .and(query -> query.isNull(Department::getTenantId)
                                .or().eq(Department::getTenantId, tenantId)))
                .stream().sorted(Comparator.comparing(Department::getSortOrder)
                        .thenComparing(Department::getName).thenComparing(Department::getId))
                .map(department -> new ReferenceOption(Long.toString(department.getId()), department.getName()))
                .toList();

        var primaryDepartmentId = memberDepartmentMapper.selectList(Wrappers.<MemberDepartment>lambdaQuery()
                        .eq(MemberDepartment::getScopeType, "SYSTEM")
                        .eq(MemberDepartment::getScopeKey, systemId)
                        .eq(MemberDepartment::getSystemId, systemId)
                        .eq(MemberDepartment::getMemberId, memberId)
                        .eq(MemberDepartment::getIsPrimary, true)
                        .and(query -> query.isNull(MemberDepartment::getTenantId)
                                .or().eq(MemberDepartment::getTenantId, tenantId)))
                .stream().map(MemberDepartment::getDepartmentId).sorted().findFirst().orElse(null);
        return new ReferenceCatalog(primaryDepartmentId, members, departments);
    }
}
