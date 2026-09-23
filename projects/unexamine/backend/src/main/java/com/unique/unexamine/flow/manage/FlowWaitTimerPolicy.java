package com.unique.unexamine.flow.manage;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 等待和定时器节点的统一配置协议。
 * 新配置只接受页面生成的数字、单位或日期时间；旧 WAIT_TIMER 的 ISO 时长仅用于兼容已有草稿和版本。
 */
final class FlowWaitTimerPolicy {
    static final String WAIT = "WAIT";
    static final String TIMER = "TIMER";
    static final String LEGACY = "WAIT_TIMER";
    static final String DURATION = "DURATION";
    static final String ABSOLUTE = "ABSOLUTE";
    static final int MAX_DURATION_MINUTES = 365 * 24 * 60;
    private static final Set<String> TYPES = Set.of(WAIT, TIMER, LEGACY);
    private static final Set<String> UNITS = Set.of("MINUTE", "HOUR", "DAY");

    private FlowWaitTimerPolicy() {
    }

    record Config(String mode, Integer durationValue, String durationUnit, Integer durationMinutes,
                  LocalDateTime resumeTime, boolean legacy) {
        LocalDateTime dueTime(LocalDateTime now) {
            if (DURATION.equals(mode)) return now.plusMinutes(durationMinutes);
            if (resumeTime == null || !resumeTime.isAfter(now)) {
                throw new IllegalArgumentException("定时器恢复时间必须晚于当前时间");
            }
            return resumeTime;
        }
    }

    static boolean supports(String nodeType) {
        return TYPES.contains(normalize(nodeType));
    }

    /** 发布校验使用当前时间拒绝已经过期的绝对定时器。 */
    static Config parse(String nodeType, Map<String, Object> source) {
        return parse(nodeType, source, LocalDateTime.now(), true);
    }

    /** 运行期读取冻结配置，绝对时间是否过期由 dueTime 明确判断，不能回退成一分钟。 */
    static Config readFrozen(String nodeType, Map<String, Object> source) {
        return parse(nodeType, source, LocalDateTime.now(), false);
    }

    static Config parse(String nodeType, Map<String, Object> source, LocalDateTime now, boolean requireFuture) {
        String type = normalize(nodeType);
        if (!TYPES.contains(type)) throw new IllegalArgumentException("节点不是等待或定时器类型");
        Map<String, Object> config = source == null ? Map.of() : source;
        boolean legacy = LEGACY.equals(type) || !config.containsKey("schemaVersion");
        String configuredMode = text(config.get("waitMode"));
        String mode = WAIT.equals(type) ? DURATION : TIMER.equals(type) ? ABSOLUTE
                : configuredMode.isBlank() ? legacyMode(config) : configuredMode.toUpperCase(Locale.ROOT);
        if (!Set.of(DURATION, ABSOLUTE).contains(mode)) {
            throw new IllegalArgumentException("请选择等待时长或指定恢复时间");
        }
        if (WAIT.equals(type) && !DURATION.equals(mode)) throw new IllegalArgumentException("等待节点只能配置等待时长");
        if (TIMER.equals(type) && !ABSOLUTE.equals(mode)) throw new IllegalArgumentException("定时器节点只能配置恢复时间");
        if (DURATION.equals(mode)) return duration(config, legacy);
        LocalDateTime resumeTime = dateTime(config.get("resumeTime"));
        if (resumeTime == null && legacy) resumeTime = dateTime(config.get("resumeAt"));
        if (resumeTime == null) throw new IllegalArgumentException("请选择定时器恢复时间");
        if (requireFuture && !resumeTime.isAfter(now)) throw new IllegalArgumentException("定时器恢复时间必须晚于当前时间");
        return new Config(ABSOLUTE, null, null, null, resumeTime, legacy);
    }

    static Map<String, Object> frozen(Config config) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", 1);
        result.put("waitMode", config.mode());
        if (DURATION.equals(config.mode())) {
            result.put("durationValue", config.durationValue());
            result.put("durationUnit", config.durationUnit());
            result.put("durationMinutes", config.durationMinutes());
        } else {
            result.put("resumeTime", config.resumeTime().toString());
        }
        return result;
    }

    private static Config duration(Map<String, Object> config, boolean legacy) {
        Integer value = integer(config.get("durationValue"));
        String unit = text(config.get("durationUnit")).toUpperCase(Locale.ROOT);
        Integer minutes;
        if (value != null || !unit.isBlank()) {
            if (value == null || value <= 0 || !UNITS.contains(unit)) {
                throw new IllegalArgumentException("请填写正整数等待时长并选择分钟、小时或天");
            }
            long multiplier = "DAY".equals(unit) ? 24L * 60L : "HOUR".equals(unit) ? 60L : 1L;
            long computed = value.longValue() * multiplier;
            if (computed > MAX_DURATION_MINUTES) throw new IllegalArgumentException("等待时长不能超过一年");
            minutes = (int) computed;
        } else {
            minutes = integer(config.get("durationMinutes"));
            if (minutes == null && legacy) minutes = legacyMinutes(config.get("duration"));
            if (minutes == null || minutes <= 0) throw new IllegalArgumentException("请配置等待时长");
            if (minutes > MAX_DURATION_MINUTES) throw new IllegalArgumentException("等待时长不能超过一年");
            value = minutes;
            unit = "MINUTE";
        }
        return new Config(DURATION, value, unit, minutes, null, legacy);
    }

    private static Integer legacyMinutes(Object value) {
        Integer numeric = integer(value);
        if (numeric != null) return numeric;
        String text = text(value);
        if (text.isBlank()) return null;
        try {
            long seconds = Duration.parse(text).getSeconds();
            if (seconds <= 0 || seconds % 60 != 0 || seconds / 60 > Integer.MAX_VALUE) return null;
            return (int) (seconds / 60);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static String legacyMode(Map<String, Object> config) {
        return config.get("resumeAt") != null || config.get("resumeTime") != null ? ABSOLUTE : DURATION;
    }

    private static LocalDateTime dateTime(Object value) {
        String text = text(value);
        if (text.isBlank()) return null;
        try {
            return LocalDateTime.parse(text);
        } catch (DateTimeParseException ignored) {
            throw new IllegalArgumentException("恢复时间格式无效，请重新选择日期和时间");
        }
    }

    private static Integer integer(Object value) {
        if (value instanceof Byte || value instanceof Short || value instanceof Integer) return ((Number) value).intValue();
        if (value instanceof Long number && number >= Integer.MIN_VALUE && number <= Integer.MAX_VALUE) return number.intValue();
        if (value instanceof java.math.BigInteger number && number.bitLength() < 31) return number.intValue();
        if (value instanceof java.math.BigDecimal number && number.scale() <= 0) {
            try { return number.intValueExact(); } catch (ArithmeticException ignored) { return null; }
        }
        return null;
    }

    private static String text(Object value) {
        return value instanceof String text ? text.strip() : "";
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
    }
}
