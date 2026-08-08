package com.unique.examine.core.api;

public interface AuditFacade {
    void recordSecurity(AuditEvent event);
}
