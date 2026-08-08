package com.unique.examine.module.kpi.adapter;

import com.unique.examine.core.api.KpiReminderRecipientFacade;
import com.unique.examine.module.kpi.port.KpiReminderRecipientDirectory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/** Maps the platform recipient facade into the KPI-owned port. */
@Component
public final class KpiReminderRecipientDirectoryAdapter
        implements KpiReminderRecipientDirectory {
    private final KpiReminderRecipientFacade recipients;

    public KpiReminderRecipientDirectoryAdapter(
            KpiReminderRecipientFacade recipients
    ) {
        this.recipients = Objects.requireNonNull(recipients, "recipients");
    }

    @Override
    public List<Long> activeDepartmentMemberIds(
            long systemId,
            long tenantId,
            long departmentId
    ) {
        var resolved = recipients.activeDepartmentMemberIds(
                systemId, tenantId, departmentId);
        if (resolved == null || resolved.isEmpty()) {
            return List.of();
        }
        return resolved.stream()
                .filter(memberId -> memberId != null && memberId > 0)
                .distinct()
                .sorted()
                .limit(MAX_RECIPIENTS)
                .toList();
    }
}
