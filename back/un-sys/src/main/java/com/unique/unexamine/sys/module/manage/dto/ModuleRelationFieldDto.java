package com.unique.unexamine.sys.module.manage.dto;

import java.util.Map;

/** 关联字段选择弹窗的 HTTP 返回模型。 */
public final class ModuleRelationFieldDto {
    private ModuleRelationFieldDto() {
    }

    /** 目标记录主键及按来源字段的关联页面配置筛选后的摘要字段。 */
    public record Option(Long id, Map<String, Object> fields) {
    }
}
