package com.unique.examine.flow.domain;

public enum ApprovalTaskStatus {
    PENDING,
    COMPLETED,
    ALL;

    public boolean includes(ApprovalInstance instance) {
        return switch (this) {
            case PENDING -> instance.status() == ApprovalInstance.Status.PENDING;
            case COMPLETED -> instance.status() == ApprovalInstance.Status.APPROVED
                    || instance.status() == ApprovalInstance.Status.REJECTED
                    || instance.status() == ApprovalInstance.Status.WITHDRAWN
                    || instance.status() == ApprovalInstance.Status.TERMINATED;
            case ALL -> true;
        };
    }
}
