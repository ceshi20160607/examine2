package com.unique.examine.plat.manage.vo;

import java.util.List;
import java.util.Set;

public record SessionContextVo(
        String type,
        AccountSummaryVo account,
        String systemId,
        String systemName,
        String tenantId,
        String tenantName,
        String memberId,
        String permissionVersion,
        Set<String> permissions,
        List<String> shells
) {
    public SessionContextVo {
        permissions = Set.copyOf(permissions);
        shells = List.copyOf(shells);
    }
}
