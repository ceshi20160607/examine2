package com.unique.unexamine.core.field;

import java.util.UUID;
import java.util.regex.Pattern;

/** 同一附件字段内多份文件共享的独立标识，不使用任何文件主键代替。 */
public final class FileBatchId {
    private static final Pattern FORMAT = Pattern.compile("[0-9a-f]{32}");

    private FileBatchId() {}

    /** 创建小写、无连字符的32位 UUID。 */
    public static String create() { return UUID.randomUUID().toString().replace("-", ""); }

    /** 字段值、上传续传和业务关联使用同一格式约束。 */
    public static boolean valid(Object raw) {
        return raw instanceof String value && FORMAT.matcher(value).matches();
    }

    /** 拒绝数字文件主键及非规范批次标识。 */
    public static String require(Object raw) {
        if (!valid(raw)) throw new IllegalArgumentException("附件批次 ID 必须是32位小写UUID");
        return (String) raw;
    }
}
