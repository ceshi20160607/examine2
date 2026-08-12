package com.unique.examine.flow.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ApprovalDefinitionRecordStatusMappingTest {
    private static final Instant NOW = Instant.parse("2026-07-27T14:00:00Z");

    @Test
    void publishSnapshotsMappingWhileLaterDraftRevisionChangesOrRemovesIt() {
        var initial = mapping("101", "102", "103", "104");
        var changed = mapping("201", "202", "203", "204");
        var draft = new ApprovalDefinitionDraft(
                101L,
                "Purchase approval",
                List.of(20L),
                1,
                NOW,
                null,
                initial
        );

        var versionOne = ApprovalDefinitionVersion.publish(draft, 1, NOW.plusSeconds(1));
        var revised = draft.revise(
                "Purchase approval changed",
                List.of(30L),
                null,
                changed,
                NOW.plusSeconds(2)
        );
        var versionTwo = ApprovalDefinitionVersion.publish(revised, 2, NOW.plusSeconds(3));
        var removed = revised.revise(
                "Purchase approval unmapped",
                List.of(30L),
                null,
                null,
                NOW.plusSeconds(4)
        );

        assertThat(versionOne.recordStatusMapping()).isEqualTo(initial);
        assertThat(versionTwo.recordStatusMapping()).isEqualTo(changed);
        assertThat(removed.recordStatusMapping()).isNull();
        assertThat(versionOne.recordStatusMapping()).isEqualTo(initial);
    }

    @Test
    void mappingRequiresCanonicalFieldCodeAndFourCanonicalPositiveOptionIds() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new RecordStatusMapping("bad-code", "1", "2", "3", "4"));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new RecordStatusMapping("approval_status", null, "2", "3", "4"));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new RecordStatusMapping("approval_status", "01", "2", "3", "4"));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new RecordStatusMapping(
                        "approval_status",
                        "12345678901234567890",
                        "2",
                        "3",
                        "4"
                ));
    }

    private static RecordStatusMapping mapping(
            String approved,
            String rejected,
            String withdrawn,
            String terminated
    ) {
        return new RecordStatusMapping(
                "approval_status",
                approved,
                rejected,
                withdrawn,
                terminated
        );
    }
}
