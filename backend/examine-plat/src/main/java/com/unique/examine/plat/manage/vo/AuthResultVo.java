package com.unique.examine.plat.manage.vo;

import java.util.List;

public record AuthResultVo(
        AccountSummaryVo account,
        SessionContextVo context,
        List<SystemSummaryVo> systems,
        List<TenantSummaryVo> tenants,
        String firstSystemId
) {
    public AuthResultVo {
        systems = List.copyOf(systems);
        tenants = List.copyOf(tenants);
    }

    public AuthResultVo withFirstSystemId(String systemId) {
        return new AuthResultVo(account, context, systems, tenants, systemId);
    }
}
