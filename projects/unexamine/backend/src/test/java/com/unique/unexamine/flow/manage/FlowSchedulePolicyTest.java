package com.unique.unexamine.flow.manage;

import com.unique.unexamine.shared.manage.web.DomainException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FlowSchedulePolicyTest {

    @Test
    void normalizesStructuredIntervalAndComputesNextSlot() {
        FlowBindingModels.ScheduleConfig schedule = FlowSchedulePolicy.normalize(new FlowBindingModels.ScheduleConfig(
                null, " interval ", 3, "hour", null, null, "fire_once", null));

        assertEquals(1, schedule.schemaVersion());
        assertEquals("Asia/Shanghai", schedule.timezone());
        assertEquals("INTERVAL", schedule.scheduleMode());
        assertEquals(LocalDateTime.of(2026, 9, 20, 13, 0), FlowSchedulePolicy.nextAfter(
                schedule, LocalDateTime.of(2026, 9, 20, 10, 0)));
    }

    @Test
    void dailyAndWeeklyAlwaysReturnAPlanAfterTheAnchor() {
        FlowBindingModels.ScheduleConfig daily = new FlowBindingModels.ScheduleConfig(
                null, "DAILY", null, null, "09:30", null, "SKIP", null);
        FlowBindingModels.ScheduleConfig weekly = new FlowBindingModels.ScheduleConfig(
                null, "WEEKLY", null, null, "08:00", 1, "FIRE_ONCE", null);

        assertEquals(LocalDateTime.of(2026, 9, 21, 9, 30), FlowSchedulePolicy.nextAfter(
                daily, LocalDateTime.of(2026, 9, 20, 10, 0)));
        assertEquals(LocalDateTime.of(2026, 9, 21, 8, 0), FlowSchedulePolicy.nextAfter(
                weekly, LocalDateTime.of(2026, 9, 20, 10, 0)));
    }

    @Test
    void rejectsInvalidOrUnboundedSchedules() {
        assertThrows(DomainException.class, () -> FlowSchedulePolicy.normalize(new FlowBindingModels.ScheduleConfig(
                null, "INTERVAL", 366, "DAY", null, null, "FIRE_ONCE", null)));
        assertThrows(DomainException.class, () -> FlowSchedulePolicy.normalize(new FlowBindingModels.ScheduleConfig(
                null, "WEEKLY", null, null, "25:00", 8, "SKIP", null)));
    }
}
