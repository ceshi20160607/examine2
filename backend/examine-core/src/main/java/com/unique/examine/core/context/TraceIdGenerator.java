package com.unique.examine.core.context;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import com.unique.examine.core.common.response.ResponseClock;

/**
 * requestId/traceId 生成器。
 */
public final class TraceIdGenerator {

    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private TraceIdGenerator() {
    }

    /**
     * 生成 requestId。
     *
     * @return requestId
     */
    public static String newRequestId() {
        return "req_" + datePart() + "_" + randomPart();
    }

    /**
     * 生成 traceId。
     *
     * @return traceId
     */
    public static String newTraceId() {
        return "trc_" + datePart() + "_" + randomPart();
    }

    private static String datePart() {
        return LocalDate.now(ResponseClock.DEFAULT_ZONE).format(DATE);
    }

    private static String randomPart() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
