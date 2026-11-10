package com.unique.unexamine.core.module;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** examine 结束后通知 Flow 等后续业务的只读上下文。 */
public record ExamineCompletionContext(Long systemId, Long tenantId, Long moduleId, Long recordId,
                                       Long accountId, Long examineId, Long exampleId,
                                       String status, Map<String, Object> values,
                                       Map<String, Object> metadata) {
    public ExamineCompletionContext {
        values = Collections.unmodifiableMap(new LinkedHashMap<>(values == null ? Map.of() : values));
        metadata = Collections.unmodifiableMap(new LinkedHashMap<>(metadata == null ? Map.of() : metadata));
    }
}
