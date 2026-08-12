package com.unique.examine.core.api;

/**
 * Operation audit transaction boundaries. Successful mutations must already be in
 * their owner transaction; denied and failed attempts are persisted independently.
 */
public interface OperationAuditFacade {
    void recordSuccess(OperationAudit audit);

    void recordDenied(OperationAudit audit);

    void recordFailed(OperationAudit audit);
}
