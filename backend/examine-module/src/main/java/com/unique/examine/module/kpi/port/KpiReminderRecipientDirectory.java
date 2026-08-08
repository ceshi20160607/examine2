package com.unique.examine.module.kpi.port;

import java.util.List;

/** KPI-owned port for resolving current active department recipients. */
public interface KpiReminderRecipientDirectory {
    int MAX_RECIPIENTS = 1_000;

    List<Long> activeDepartmentMemberIds(
            long systemId,
            long tenantId,
            long departmentId
    );
}
