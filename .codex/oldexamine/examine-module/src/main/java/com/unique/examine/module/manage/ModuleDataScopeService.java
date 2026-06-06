package com.unique.examine.module.manage;

import com.unique.examine.module.entity.po.ModuleMember;
import com.unique.examine.module.entity.po.ModuleRecord;
import com.unique.examine.module.entity.po.ModuleRole;
import com.unique.examine.module.service.IModuleDeptService;
import com.unique.examine.module.service.IModuleMemberService;
import com.unique.examine.module.service.IModuleRoleService;
import com.unique.examine.plat.entity.po.PlatSystem;
import com.unique.examine.plat.service.IPlatSystemService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 记录数据权限：按角色 data_scope 过滤 create_user_id。
 */
@Service
public class ModuleDataScopeService {

    public static final int SCOPE_SELF = 1;
    public static final int SCOPE_SELF_SUB = 2;
    public static final int SCOPE_DEPT = 3;
    public static final int SCOPE_DEPT_TREE = 4;
    public static final int SCOPE_ALL = 5;

    public record RecordScopeFilter(boolean unrestricted, List<Long> creatorPlatIds) {
        public static RecordScopeFilter all() {
            return new RecordScopeFilter(true, List.of());
        }

        public static RecordScopeFilter creators(List<Long> platIds) {
            return new RecordScopeFilter(false, platIds == null ? List.of() : List.copyOf(platIds));
        }
    }

    @Autowired
    private IPlatSystemService platSystemService;
    @Autowired
    private IModuleMemberService moduleMemberService;
    @Autowired
    private IModuleRoleService moduleRoleService;
    @Autowired
    private IModuleDeptService moduleDeptService;
    @Autowired
    private SystemModuleDeptService systemModuleDeptService;

    public boolean isSystemOwner(long systemId, Long platId) {
        if (platId == null || systemId <= 0L) {
            return false;
        }
        PlatSystem s = platSystemService.getById(systemId);
        return s != null && platId.equals(s.getOwnerPlatAccountId());
    }

    public RecordScopeFilter resolveRecordScope(long systemId, long tenantId, long appId, long platId) {
        if (isSystemOwner(systemId, platId)) {
            return RecordScopeFilter.all();
        }
        ModuleMember member = moduleMemberService.lambdaQuery()
                .eq(ModuleMember::getSystemId, systemId)
                .eq(ModuleMember::getTenantId, tenantId)
                .eq(ModuleMember::getAppId, appId)
                .eq(ModuleMember::getPlatId, platId)
                .eq(ModuleMember::getStatus, 1)
                .last("limit 1")
                .one();
        int scope = SCOPE_SELF;
        if (member != null && member.getRoleId() != null) {
            ModuleRole role = moduleRoleService.getById(member.getRoleId());
            if (role != null && role.getDataScope() != null) {
                scope = role.getDataScope();
            }
        }
        return switch (scope) {
            case SCOPE_ALL -> RecordScopeFilter.all();
            case SCOPE_DEPT -> RecordScopeFilter.creators(platIdsInSameDept(systemId, tenantId, appId, member));
            case SCOPE_DEPT_TREE -> RecordScopeFilter.creators(platIdsInDeptSubtree(systemId, tenantId, appId, member));
            case SCOPE_SELF_SUB -> RecordScopeFilter.creators(List.of(platId));
            default -> RecordScopeFilter.creators(List.of(platId));
        };
    }

    public boolean canAccessRecord(ModuleRecord record, long systemId, long tenantId, long appId, long platId) {
        if (record == null || record.getCreateUserId() == null) {
            return false;
        }
        RecordScopeFilter filter = resolveRecordScope(systemId, tenantId, appId, platId);
        if (filter.unrestricted()) {
            return true;
        }
        return filter.creatorPlatIds().contains(record.getCreateUserId());
    }

    private List<Long> platIdsInSameDept(long systemId, long tenantId, long appId, ModuleMember self) {
        if (self == null || self.getDeptId() == null || self.getDeptId() <= 0L) {
            return List.of(self == null ? -1L : self.getPlatId());
        }
        List<ModuleMember> members = moduleMemberService.lambdaQuery()
                .eq(ModuleMember::getSystemId, systemId)
                .eq(ModuleMember::getTenantId, tenantId)
                .eq(ModuleMember::getAppId, appId)
                .eq(ModuleMember::getDeptId, self.getDeptId())
                .eq(ModuleMember::getStatus, 1)
                .list();
        return collectPlatIds(members, self.getPlatId());
    }

    private List<Long> platIdsInDeptSubtree(long systemId, long tenantId, long appId, ModuleMember self) {
        if (self == null || self.getDeptId() == null || self.getDeptId() <= 0L) {
            return platIdsInSameDept(systemId, tenantId, appId, self);
        }
        List<Long> deptIds = systemModuleDeptService.listSubtreeDeptIds(appId, self.getDeptId(), self.getPlatId());
        if (deptIds.isEmpty()) {
            return List.of(self.getPlatId());
        }
        List<ModuleMember> members = moduleMemberService.lambdaQuery()
                .eq(ModuleMember::getSystemId, systemId)
                .eq(ModuleMember::getTenantId, tenantId)
                .eq(ModuleMember::getAppId, appId)
                .in(ModuleMember::getDeptId, deptIds)
                .eq(ModuleMember::getStatus, 1)
                .list();
        return collectPlatIds(members, self.getPlatId());
    }

    private static List<Long> collectPlatIds(List<ModuleMember> members, Long alwaysInclude) {
        Set<Long> set = new HashSet<>();
        if (alwaysInclude != null) {
            set.add(alwaysInclude);
        }
        if (members != null) {
            for (ModuleMember m : members) {
                if (m.getPlatId() != null) {
                    set.add(m.getPlatId());
                }
            }
        }
        return new ArrayList<>(set);
    }
}
