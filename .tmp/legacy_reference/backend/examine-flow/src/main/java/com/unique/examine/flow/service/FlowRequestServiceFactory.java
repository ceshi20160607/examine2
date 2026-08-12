package com.unique.examine.flow.service;

public interface FlowRequestServiceFactory {
    ApprovalWorkflowService forTenant(long systemId, long tenantId);
}
