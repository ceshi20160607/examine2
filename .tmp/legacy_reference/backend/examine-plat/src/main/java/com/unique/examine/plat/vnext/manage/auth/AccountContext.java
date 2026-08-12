package com.unique.examine.plat.vnext.manage.auth;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record AccountContext(
        AccountSummary account,
        SessionContext context,
        List<Object> systems,
        List<Object> tenants,
        String firstSystemId
) {
    public AccountContext {
        systems = List.copyOf(systems);
        tenants = List.copyOf(tenants);
    }
}
