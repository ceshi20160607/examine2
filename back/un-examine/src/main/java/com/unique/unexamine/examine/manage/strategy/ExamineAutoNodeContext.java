package com.unique.unexamine.examine.manage.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 自动审批节点执行上下文；节点只读取当前审批运行快照的业务值和发布配置。 */
public record ExamineAutoNodeContext(Long systemId, Long tenantId, Long moduleId, Long recordId,
                                     Long accountId, JsonNode node, Map<String, Object> values) {
    public ExamineAutoNodeContext {
        values = Collections.unmodifiableMap(new LinkedHashMap<>(values == null ? Map.of() : values));
    }
}
