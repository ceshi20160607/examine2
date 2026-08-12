package com.unique.examine.module.kpi.adapter;

import com.unique.examine.core.api.AggregateRef;
import com.unique.examine.core.api.IdempotentMemberMessageFacade;
import com.unique.examine.module.kpi.domain.KpiCalculation;
import com.unique.examine.module.kpi.port.KpiReminderNotifier;

import java.util.Objects;

/** Maps immutable KPI calculation snapshots to idempotent system inbox facts. */
public final class KpiInboxReminderNotifier implements KpiReminderNotifier {
    static final String SOURCE_TYPE = "KPI_UNMET";

    private final IdempotentMemberMessageFacade messages;

    public KpiInboxReminderNotifier(IdempotentMemberMessageFacade messages) {
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    public long deliver(
            KpiCalculation calculation,
            long recipientMemberId
    ) {
        Objects.requireNonNull(calculation, "calculation");
        var definition = calculation.definition();
        var target = calculation.target();
        var period = target.period();
        var title = bounded("KPI 提醒：" + definition.name(), 200);
        var body = bounded(
                definition.name() + "（" + target.subjectDisplayName() + "）"
                        + "在 " + period.startInclusive() + " 至 "
                        + period.endExclusive() + " 的目标值为 "
                        + target.targetValue() + "，实际值为 "
                        + calculation.actualValue() + "，当前状态为 "
                        + calculation.status().name() + "。",
                4_000);
        var path = "/systems/" + calculation.systemId()
                + "/kpis?periodType=" + definition.periodType().name()
                + "&periodStart=" + period.startInclusive();
        return messages.deliver(new IdempotentMemberMessageFacade.Command(
                "kpi-calculation:" + calculation.id() + ":recipient:"
                        + recipientMemberId,
                calculation.systemId(), calculation.tenantId(),
                recipientMemberId, SOURCE_TYPE, title, body,
                new AggregateRef("KPI_TARGET",
                        Long.toString(target.targetId())), path));
    }

    private static String bounded(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
