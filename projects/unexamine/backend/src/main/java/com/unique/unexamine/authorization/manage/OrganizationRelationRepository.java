package com.unique.unexamine.authorization.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.unique.unexamine.system.base.entity.SystemMemberReportingLine;
import com.unique.unexamine.system.base.entity.SystemTenantMember;
import com.unique.unexamine.system.base.service.SystemMemberReportingLineBaseService;
import com.unique.unexamine.system.base.service.SystemTenantMemberBaseService;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** 租户成员汇报关系的业务查询；普通持久化统一交给生成的 Base Service。 */
@Repository
public class OrganizationRelationRepository {
    private final SystemMemberReportingLineBaseService relationService;
    private final SystemTenantMemberBaseService tenantMemberService;

    public OrganizationRelationRepository(SystemMemberReportingLineBaseService relationService,
                                          SystemTenantMemberBaseService tenantMemberService) {
        this.relationService = relationService;
        this.tenantMemberService = tenantMemberService;
    }

    public Map<Long, MemberRelation> byTenant(Long tenantId) {
        Map<Long, MemberRelation> result = new LinkedHashMap<>();
        relationService.selectList(Wrappers.<SystemMemberReportingLine>lambdaQuery()
                        .eq(SystemMemberReportingLine::getTenantId, tenantId)
                        .orderByAsc(SystemMemberReportingLine::getTenantMemberId))
                .forEach(row -> result.put(row.getTenantMemberId(), new MemberRelation(row.getTenantMemberId(),
                        row.getManagerTenantMemberId(), row.getPositionTitle(), row.getVersion())));
        return result;
    }

    /** 调用方先验证成员、直属上级的租户归属和汇报环，再在同一事务保存。 */
    public void upsert(Long systemId, Long tenantId, Long tenantMemberId,
                       Long managerTenantMemberId, String positionTitle) {
        SystemMemberReportingLine relation = relationService.selectOne(
                Wrappers.<SystemMemberReportingLine>lambdaQuery()
                        .eq(SystemMemberReportingLine::getTenantId, tenantId)
                        .eq(SystemMemberReportingLine::getTenantMemberId, tenantMemberId));
        if (relation == null) {
            relation = new SystemMemberReportingLine();
            relation.setSystemId(systemId);
            relation.setTenantId(tenantId);
            relation.setTenantMemberId(tenantMemberId);
            relation.setManagerTenantMemberId(managerTenantMemberId);
            relation.setPositionTitle(emptyToNull(positionTitle));
            relationService.insert(relation);
            return;
        }
        relation.setManagerTenantMemberId(managerTenantMemberId);
        relation.setPositionTitle(emptyToNull(positionTitle));
        if (relationService.updateById(relation) != 1) {
            throw new DomainException("ORGANIZATION_RELATION_CONFLICT", "成员汇报关系已变化，请刷新后重试",
                    HttpStatus.CONFLICT);
        }
    }

    /** 沿直属上级关系查找下级，仅返回当前租户有效成员的系统成员标识。 */
    public List<Long> subordinateSystemMemberIds(Long tenantId, Long managerTenantMemberId) {
        Map<Long, MemberRelation> relations = byTenant(tenantId);
        LinkedHashSet<Long> tenantMemberIds = new LinkedHashSet<>();
        tenantMemberIds.add(managerTenantMemberId);
        boolean changed;
        do {
            changed = false;
            for (MemberRelation relation : relations.values()) {
                if (relation.managerTenantMemberId() != null
                        && tenantMemberIds.contains(relation.managerTenantMemberId())
                        && tenantMemberIds.add(relation.tenantMemberId())) {
                    changed = true;
                }
            }
        } while (changed);
        return tenantMemberService.selectList(Wrappers.<SystemTenantMember>lambdaQuery()
                        .eq(SystemTenantMember::getTenantId, tenantId)
                        .eq(SystemTenantMember::getStatus, "ACTIVE")
                        .in(SystemTenantMember::getId, tenantMemberIds)
                        .orderByAsc(SystemTenantMember::getId))
                .stream().map(SystemTenantMember::getSystemMemberId).toList();
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    public record MemberRelation(Long tenantMemberId, Long managerTenantMemberId,
                                 String positionTitle, Integer version) {
    }
}
