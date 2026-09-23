package com.unique.unexamine.moduleconfig.manage;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

/** 业务日期时间按北京时间存储和比较，数据库精度为毫秒，文本字段不使用此协议。 */
public final class DateTimeFieldProtocol {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private DateTimeFieldProtocol() { }

    /** 本地输入表示北京时间；带偏移输入表示同一时刻，不能直接去掉偏移导致八小时差。 */
    public static String normalize(String value) {
        try {
            LocalDateTime time;
            try { time = OffsetDateTime.parse(value).atZoneSameInstant(BUSINESS_ZONE).toLocalDateTime(); }
            catch (DateTimeParseException local) { time = LocalDateTime.parse(value); }
            if (time.getNano() % 1_000_000 != 0) throw new IllegalArgumentException("日期时间精度最多为毫秒");
            return time.toString();
        } catch (DateTimeParseException malformed) {
            throw new IllegalArgumentException("日期时间格式不正确");
        }
    }
}
