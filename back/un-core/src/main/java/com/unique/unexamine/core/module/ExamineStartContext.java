package com.unique.unexamine.core.module;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Flow 节点发起 examine 时传递的业务快照和来源信息。 */
public record ExamineStartContext(Long systemId, Long tenantId, Long moduleId, Long recordId,
                                  Long accountId, Long examineId, Map<String, Object> values,
                                  Map<String, Object> metadata) {
    public ExamineStartContext {
        values = Collections.unmodifiableMap(new LinkedHashMap<>(values == null ? Map.of() : values));
        metadata = Collections.unmodifiableMap(new LinkedHashMap<>(metadata == null ? Map.of() : metadata));
    }
}
