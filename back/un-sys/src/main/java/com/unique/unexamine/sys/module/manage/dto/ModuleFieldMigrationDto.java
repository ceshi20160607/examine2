package com.unique.unexamine.sys.module.manage.dto;

import java.util.List;
import java.util.Map;

/** ModuleFieldMigration 的请求与返回模型。 */
public final class ModuleFieldMigrationDto {
    private ModuleFieldMigrationDto() {}

    /** 预览列出受新配置影响的真实记录和旧值，执行时会重新扫描。 */
    public record Impact(Long recordId, Long tenantId, Object before, Map<String,String> beforeDisplay, String reason) {}

public record Preview(long total, List<Impact> records) {}
}
