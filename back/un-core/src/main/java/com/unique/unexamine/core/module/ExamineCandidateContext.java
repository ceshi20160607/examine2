package com.unique.unexamine.core.module;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 审批人解析上下文；解析器只读取发起时的业务快照和节点配置。 */
public record ExamineCandidateContext(Long systemId, Long tenantId, Long moduleId, Long recordId,
                                      Long initiatorAccountId, Map<String, Object> values) {
    public ExamineCandidateContext {
        values = Collections.unmodifiableMap(new LinkedHashMap<>(values == null ? Map.of() : values));
    }
}
