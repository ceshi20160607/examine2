package com.unique.unexamine.core.event;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 本地业务扩展点收到的事件上下文。
 *
 * <p>事件只在当前后端进程内同步执行，不承诺跨进程投递；上下文中的 values
 * 是当前动作已经通过校验的值快照，监听器不得直接修改业务记录。</p>
 */
public record BusinessEventContext(
        String eventCode,
        Long systemId,
        Long tenantId,
        Long moduleId,
        Long recordId,
        Long accountId,
        Long actionMenuId,
        Map<String, Object> values,
        Map<String, Object> metadata) {

    public BusinessEventContext {
        values = immutable(values);
        metadata = immutable(metadata);
    }

    private static Map<String, Object> immutable(Map<String, Object> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source == null ? Map.of() : source));
    }
}
