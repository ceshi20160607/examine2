package com.unique.examine.plat.manage.audit;

public interface OperationsHealthQuery {
    OperationsHealthModels.Summary inspect(UnifiedAuditModels.Scope scope);
}
