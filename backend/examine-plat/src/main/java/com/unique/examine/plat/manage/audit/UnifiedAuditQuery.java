package com.unique.examine.plat.manage.audit;

public interface UnifiedAuditQuery {
    UnifiedAuditModels.Page search(UnifiedAuditModels.Scope scope, UnifiedAuditModels.Query query);
}
