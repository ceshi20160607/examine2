package com.unique.examine.core.common.response;

import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 响应时间工具。
 */
public final class ResponseClock {

    public static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");

    private ResponseClock() {
    }

    /**
     * 返回统一响应时间。
     *
     * @return 当前时间
     */
    public static OffsetDateTime now() {
        return OffsetDateTime.now(DEFAULT_ZONE);
    }
}
