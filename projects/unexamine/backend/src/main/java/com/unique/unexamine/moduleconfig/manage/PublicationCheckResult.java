package com.unique.unexamine.moduleconfig.manage;

import java.util.List;

public record PublicationCheckResult(
        boolean valid,
        int draftRevision,
        List<PublicationCheckIssue> issues,
        List<String> indexProjectionPlans) {
}
