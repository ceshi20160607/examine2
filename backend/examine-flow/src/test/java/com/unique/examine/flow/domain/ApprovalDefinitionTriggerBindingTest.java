package com.unique.examine.flow.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ApprovalDefinitionTriggerBindingTest {
    private static final Instant NOW = Instant.parse("2026-07-27T13:00:00Z");

    @Test
    void publishSnapshotsTheTriggerWhileDraftRevisionMayChangeOrRemoveIt() {
        var trigger = new TriggerBinding(
                "purchase_order",
                TriggerBinding.Event.RECORD_ACTIVATED,
                200,
                false,
                List.of(
                        new TriggerCondition("amount", TriggerCondition.Operator.GTE, "100"),
                        new TriggerCondition("remark", TriggerCondition.Operator.NOT_EMPTY, null)
                )
        );
        var draft = new ApprovalDefinitionDraft(
                101L,
                "Purchase approval",
                List.of(20L),
                1,
                NOW,
                trigger
        );
        var published = ApprovalDefinitionVersion.publish(draft, 1, NOW.plusSeconds(1));
        var changed = draft.revise(
                "Purchase approval changed",
                List.of(30L),
                new TriggerBinding(
                        "purchase_order",
                        TriggerBinding.Event.RECORD_ACTIVATED,
                        -10,
                        true
                ),
                NOW.plusSeconds(2)
        );
        var removed = changed.revise(
                "Manual purchase approval",
                List.of(30L),
                null,
                NOW.plusSeconds(3)
        );

        assertThat(published.triggerBinding()).isEqualTo(trigger);
        assertThat(published.triggerBinding().conditions()).containsExactlyElementsOf(trigger.conditions());
        assertThat(published.approverIds()).containsExactly(20L);
        assertThat(changed.triggerBinding().priority()).isEqualTo(-10);
        assertThat(removed.triggerBinding()).isNull();
        assertThat(published.triggerBinding()).isEqualTo(trigger);
    }

    @Test
    void triggerRequiresCanonicalModuleBoundedPriorityAndAtMostTenValidConditions() {
        assertThatIllegalArgumentException().isThrownBy(() -> new TriggerBinding(
                "bad-code",
                TriggerBinding.Event.RECORD_ACTIVATED,
                0,
                true
        ));
        assertThatIllegalArgumentException().isThrownBy(() -> new TriggerBinding(
                "purchase_order",
                TriggerBinding.Event.RECORD_ACTIVATED,
                1001,
                true
        ));
        assertThat(new TriggerBinding(
                "purchase_order",
                TriggerBinding.Event.RECORD_ACTIVATED,
                0,
                false
        ).exclusive()).isFalse();
        assertThatIllegalArgumentException().isThrownBy(() -> new TriggerBinding(
                "purchase_order",
                TriggerBinding.Event.RECORD_ACTIVATED,
                0,
                false,
                java.util.Collections.nCopies(
                        11,
                        new TriggerCondition("amount", TriggerCondition.Operator.EMPTY, null)
                )
        ));
        assertThatIllegalArgumentException().isThrownBy(() -> new TriggerCondition(
                "amount",
                TriggerCondition.Operator.EQ,
                null
        ));
        assertThatIllegalArgumentException().isThrownBy(() -> new TriggerCondition(
                "amount",
                TriggerCondition.Operator.EMPTY,
                "0"
        ));
    }

    @Test
    void supportsTheFrozenRecordEventsButOnlyActivationTriggersMayMapTerminalStatus() {
        assertThat(TriggerBinding.Event.values()).containsExactly(
                TriggerBinding.Event.RECORD_ACTIVATED,
                TriggerBinding.Event.RECORD_CREATED,
                TriggerBinding.Event.RECORD_UPDATED,
                TriggerBinding.Event.RECORD_DELETED,
                TriggerBinding.Event.RECORD_STATUS_CHANGED,
                TriggerBinding.Event.IMPORT_COMPLETED,
                TriggerBinding.Event.PERIODIC
        );
        var mapping = new RecordStatusMapping(
                "approval_status",
                "101",
                "102",
                "103",
                "104"
        );
        assertThatIllegalArgumentException().isThrownBy(() -> new ApprovalDefinitionDraft(
                102L,
                "Created record approval",
                List.of(20L),
                1,
                NOW,
                new TriggerBinding(
                        "purchase_order",
                        TriggerBinding.Event.RECORD_CREATED,
                        100,
                        false
                ),
                mapping
        ));
        assertThat(new ApprovalDefinitionDraft(
                103L,
                "Manual approval",
                List.of(20L),
                1,
                NOW,
                null,
                mapping
        ).recordStatusMapping()).isEqualTo(mapping);
    }

    @Test
    void periodicTriggerFreezesScheduleAndRejectsRecordFanoutFields() {
        var schedule = new PeriodicSchedule(NOW.plusSeconds(60), 15, 88L);
        var trigger = TriggerBinding.periodic(schedule);
        var published = ApprovalDefinitionVersion.publish(
                new ApprovalDefinitionDraft(
                        104L,
                        "Periodic approval",
                        List.of(20L),
                        1,
                        NOW,
                        trigger
                ),
                1,
                NOW.plusSeconds(1)
        );

        assertThat(published.triggerBinding().periodicSchedule()).isEqualTo(schedule);
        assertThat(published.triggerBinding().moduleCode()).isNull();
        assertThat(published.triggerBinding().conditions()).isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> new TriggerBinding(
                "purchase_order",
                TriggerBinding.Event.PERIODIC,
                0,
                true,
                List.of(),
                schedule
        ));
        assertThatIllegalArgumentException().isThrownBy(() -> new PeriodicSchedule(
                NOW,
                0,
                88L
        ));
    }
}
