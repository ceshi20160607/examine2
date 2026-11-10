package com.unique.unexamine.core.module;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 模块记录完成保存后的统一业务上下文。
 *
 * <p>上下文只携带已经通过字段校验并写入数据库的值，后续 Flow、审批等处理器不能修改
 * 记录值。处理器若执行失败，调用方所在事务会回滚记录和已创建的运行实例。</p>
 */
public record ModuleRecordProcessContext(Long systemId, Long tenantId, Long moduleId, Long recordId,
                                         Long accountId, String eventCode, Long actionMenuId,
                                         String operation, Map<String, Object> values) {

    public ModuleRecordProcessContext {
        values = Collections.unmodifiableMap(new LinkedHashMap<>(values == null ? Map.of() : values));
    }
}
