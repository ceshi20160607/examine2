package com.unique.examine.app.manage.support;

import java.util.List;

/**
 * OpenAPI 常量。
 */
public final class OpenApiConstants {

    /** 固定签名算法。 */
    public static final String SIGN_ALGORITHM = "HMAC-SHA256";

    /** 固定参与签名 header。 */
    public static final List<String> SIGNED_HEADER_NAMES = List.of("x-openapi-accesskey",
            "x-openapi-body-sha256", "x-openapi-nonce", "x-openapi-timestamp", "x-request-id");

    /** 固定 signed headers 字符串。 */
    public static final String SIGNED_HEADERS = String.join(";", SIGNED_HEADER_NAMES);

    /** 请求时间戳允许偏差秒数。 */
    public static final long TIMESTAMP_WINDOW_SECONDS = 300L;

    /** nonce TTL 秒数。 */
    public static final long NONCE_TTL_SECONDS = 600L;

    private OpenApiConstants() {
    }
}
