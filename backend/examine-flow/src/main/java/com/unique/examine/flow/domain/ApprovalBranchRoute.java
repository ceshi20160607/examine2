package com.unique.examine.flow.domain;

import java.util.List;

/**
 * The immutable execution inputs shared by parallel and inclusive branches.
 */
public interface ApprovalBranchRoute {
    String code();

    String name();

    List<Long> approverIds();

    ApprovalMode approvalMode();

    default List<ApprovalStage> approvalStages() {
        return null;
    }
}
