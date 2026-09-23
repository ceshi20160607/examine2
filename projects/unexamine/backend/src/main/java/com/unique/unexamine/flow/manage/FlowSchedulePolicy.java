package com.unique.unexamine.flow.manage;

import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.http.HttpStatus;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.Set;

/** 周期触发的结构化协议和下一次时间计算；普通用户不接触 cron、JSON 或时区代码。 */
public final class FlowSchedulePolicy {
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final Set<String> MODES = Set.of("INTERVAL", "DAILY", "WEEKLY");
    private static final Set<String> UNITS = Set.of("MINUTE", "HOUR", "DAY");
    private static final Set<String> MISFIRES = Set.of("FIRE_ONCE", "SKIP");

    private FlowSchedulePolicy() { }

    public static FlowBindingModels.ScheduleConfig normalize(FlowBindingModels.ScheduleConfig input) {
        if (input == null) throw invalid("请选择周期方式并填写执行时间");
        String mode = upper(input.scheduleMode());
        String misfire = upper(input.misfirePolicy() == null ? "FIRE_ONCE" : input.misfirePolicy());
        if (!MODES.contains(mode)) throw invalid("周期方式无效");
        if (!MISFIRES.contains(misfire)) throw invalid("错过执行策略无效");
        Integer value = null;
        String unit = null;
        String dailyTime = null;
        Integer weekDay = null;
        if ("INTERVAL".equals(mode)) {
            value = input.intervalValue();
            unit = upper(input.intervalUnit());
            if (value == null || value <= 0 || !UNITS.contains(unit)) throw invalid("间隔必须是正整数并选择分钟、小时或天");
            if (duration(value, unit).compareTo(Duration.ofDays(365)) > 0) throw invalid("周期间隔不能超过一年");
        } else {
            dailyTime = normalizedTime(input.dailyTime());
            if ("WEEKLY".equals(mode)) {
                weekDay = input.weekDay();
                if (weekDay == null || weekDay < 1 || weekDay > 7) throw invalid("每周执行必须选择星期一到星期日");
            }
        }
        return new FlowBindingModels.ScheduleConfig(1, mode, value, unit, dailyTime, weekDay,
                misfire, BUSINESS_ZONE.getId());
    }

    /** 返回严格晚于锚点的下一次计划时间。 */
    public static LocalDateTime nextAfter(FlowBindingModels.ScheduleConfig schedule, LocalDateTime anchor) {
        FlowBindingModels.ScheduleConfig value = normalize(schedule);
        if ("INTERVAL".equals(value.scheduleMode())) {
            return anchor.plus(duration(value.intervalValue(), value.intervalUnit()));
        }
        LocalTime time = LocalTime.parse(value.dailyTime(), TIME);
        if ("DAILY".equals(value.scheduleMode())) {
            LocalDateTime candidate = anchor.toLocalDate().atTime(time);
            return candidate.isAfter(anchor) ? candidate : candidate.plusDays(1);
        }
        DayOfWeek day = DayOfWeek.of(value.weekDay());
        LocalDateTime candidate = anchor.toLocalDate().with(TemporalAdjusters.nextOrSame(day)).atTime(time);
        return candidate.isAfter(anchor) ? candidate : candidate.plusWeeks(1);
    }

    private static Duration duration(int value, String unit) {
        return switch (unit) {
            case "MINUTE" -> Duration.ofMinutes(value);
            case "HOUR" -> Duration.ofHours(value);
            case "DAY" -> Duration.ofDays(value);
            default -> throw invalid("周期单位无效");
        };
    }

    private static String normalizedTime(String value) {
        if (value == null || value.isBlank()) throw invalid("请填写每日执行时间");
        try {
            return LocalTime.parse(value.strip(), TIME).format(TIME);
        } catch (DateTimeParseException exception) {
            throw invalid("每日执行时间必须是有效的小时和分钟");
        }
    }

    private static String upper(String value) {
        return value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
    }

    private static DomainException invalid(String message) {
        return new DomainException("FLOW_SCHEDULE_CONFIG_INVALID", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
