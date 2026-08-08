package com.unique.examine.flow.domain;

/**
 * Execution policy for one resolved approval route.
 */
public enum ApprovalMode {
    /**
     * Members decide one at a time in configured order.
     */
    SEQUENTIAL,

    /**
     * Every member receives a task; the first approval succeeds and only an
     * all-member rejection fails.
     */
    ANY,

    /**
     * Every member receives a task; all approvals are required and the first
     * rejection fails.
     */
    ALL,

    /**
     * Every member receives a task; a definition-owned count or percentage
     * rule is resolved into an immutable required-approval count at start.
     */
    QUORUM
}
