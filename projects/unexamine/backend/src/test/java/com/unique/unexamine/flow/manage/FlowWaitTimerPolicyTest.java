package com.unique.unexamine.flow.manage;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FlowWaitTimerPolicyTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 20, 9, 0);

    @Test
    void freezesStructuredDurationAndAbsoluteTimer() {
        var wait = FlowWaitTimerPolicy.parse("WAIT", Map.of(
                "schemaVersion", 1, "durationValue", 2, "durationUnit", "HOUR"), NOW, true);
        assertEquals(120, wait.durationMinutes());
        assertEquals(Map.of("schemaVersion", 1, "waitMode", "DURATION", "durationValue", 2,
                "durationUnit", "HOUR", "durationMinutes", 120), FlowWaitTimerPolicy.frozen(wait));

        var timer = FlowWaitTimerPolicy.parse("TIMER", Map.of(
                "schemaVersion", 1, "resumeTime", "2026-09-21T10:30:00"), NOW, true);
        assertEquals(LocalDateTime.of(2026, 9, 21, 10, 30), timer.dueTime(NOW));
        assertEquals("ABSOLUTE", FlowWaitTimerPolicy.frozen(timer).get("waitMode"));
    }

    @Test
    void rejectsFractionalNonPositiveTooLongAndPastValues() {
        assertThrows(IllegalArgumentException.class, () -> FlowWaitTimerPolicy.parse("WAIT", Map.of(
                "schemaVersion", 1, "durationValue", 1.5, "durationUnit", "HOUR"), NOW, true));
        assertThrows(IllegalArgumentException.class, () -> FlowWaitTimerPolicy.parse("WAIT", Map.of(
                "schemaVersion", 1, "durationValue", 0, "durationUnit", "MINUTE"), NOW, true));
        assertThrows(IllegalArgumentException.class, () -> FlowWaitTimerPolicy.parse("WAIT", Map.of(
                "schemaVersion", 1, "durationValue", 366, "durationUnit", "DAY"), NOW, true));
        assertThrows(IllegalArgumentException.class, () -> FlowWaitTimerPolicy.parse("TIMER", Map.of(
                "schemaVersion", 1, "resumeTime", "2026-09-20T08:59:59"), NOW, true));
    }

    @Test
    void readsLegacyIsoDurationButDoesNotSilentlyAcceptMalformedValues() {
        var legacy = FlowWaitTimerPolicy.parse("WAIT_TIMER", Map.of("duration", "PT2H"), NOW, true);
        assertEquals(120, legacy.durationMinutes());
        assertEquals("MINUTE", legacy.durationUnit());
        assertThrows(IllegalArgumentException.class,
                () -> FlowWaitTimerPolicy.parse("WAIT_TIMER", Map.of("duration", "two hours"), NOW, true));
    }
}
