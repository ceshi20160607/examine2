package com.unique.examine.core.api;

import java.util.List;

/**
 * Narrow, platform-owned directory projection used when a KPI calculation
 * resolves the current recipients of a department target.
 */
public interface KpiReminderRecipientFacade {
    int MAX_RECIPIENTS = 1_000;

    List<Long> activeDepartmentMemberIds(
            long systemId,
            long tenantId,
            long departmentId
    );
}
