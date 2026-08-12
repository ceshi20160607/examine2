package com.unique.examine.module.kpi.port;

import com.unique.examine.module.kpi.domain.KpiCalculation;

/** Event-owned, idempotent system-inbox delivery boundary for KPI reminders. */
@FunctionalInterface
public interface KpiReminderNotifier {
    long deliver(KpiCalculation calculation, long recipientMemberId);
}
