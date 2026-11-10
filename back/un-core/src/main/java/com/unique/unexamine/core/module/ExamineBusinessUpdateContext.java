package com.unique.unexamine.core.module;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 审批终态提交运行快照字段的受控上下文。 */
public record ExamineBusinessUpdateContext(Long systemId, Long tenantId, Long moduleId, Long recordId,
                                           Long accountId, String operation, Map<String, Object> values) {
    public ExamineBusinessUpdateContext {
        values = Collections.unmodifiableMap(new LinkedHashMap<>(values == null ? Map.of() : values));
    }
}
