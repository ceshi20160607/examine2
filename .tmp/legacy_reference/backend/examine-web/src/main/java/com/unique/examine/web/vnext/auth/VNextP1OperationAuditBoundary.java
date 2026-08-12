package com.unique.examine.web.vnext.auth;

import com.unique.examine.core.api.OperationAudit;
import com.unique.examine.core.api.OperationAuditFacade;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * P1 provisions only {@code un_audit_security}; its authentication use cases
 * already record LOGIN, REFRESH, and LOGOUT security outcomes in their owner
 * transactions. This phase boundary prevents frozen legacy operation auditing
 * from reaching the deferred {@code un_audit_operation} table. Replace it when
 * the operation-audit schema is delivered in a later phase.
 */
@Component
@Profile("vnext")
@Primary
public class VNextP1OperationAuditBoundary implements OperationAuditFacade {
    @Override
    public void recordSuccess(OperationAudit audit) {
        // Intentionally unavailable until the operation-audit schema is delivered.
    }

    @Override
    public void recordDenied(OperationAudit audit) {
        // Authentication denial is already recorded in un_audit_security.
    }

    @Override
    public void recordFailed(OperationAudit audit) {
        // Authentication failure is already recorded in un_audit_security.
    }
}
