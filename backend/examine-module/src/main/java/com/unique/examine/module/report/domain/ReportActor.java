package com.unique.examine.module.report.domain;

public record ReportActor(long systemId, long tenantId, long memberId) {
    public ReportActor {
        if (systemId <= 0 || tenantId <= 0 || memberId <= 0) {
            throw new IllegalArgumentException(
                    "Report actor scope values must be positive");
        }
    }
}
