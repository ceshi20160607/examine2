package com.unique.examine.module.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 页面 schema 保存入参。
 */
@Data
@Schema(description = "页面 schema 保存入参")
public class PageSchemaSaveBO {

    @NotNull(message = "页面 schema 不能为空")
    @Schema(description = "列表列、筛选、排序、表单分区或详情区块配置")
    private Object schema;

    @Schema(description = "草稿版本号，更新时传入用于并发检查")
    private Integer draftVersion;
}
