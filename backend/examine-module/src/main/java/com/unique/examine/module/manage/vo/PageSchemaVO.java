package com.unique.examine.module.manage.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 页面 schema 回显。
 */
@Data
@Builder
@Schema(description = "页面 schema 回显")
public class PageSchemaVO {

    @Schema(description = "schema ID")
    private String schemaId;

    @Schema(description = "模块 ID")
    private String moduleId;

    @Schema(description = "页面类型：LIST、FORM、DETAIL")
    private String pageType;

    @Schema(description = "schema 编码")
    private String schemaCode;

    @Schema(description = "schema 名称")
    private String schemaName;

    @Schema(description = "schema JSON")
    private String schema;

    @Schema(description = "草稿版本")
    private Integer draftVersion;

    @Schema(description = "已发布版本 ID")
    private String publishedVersion;

    @Schema(description = "schema 状态")
    private String status;

    @Schema(description = "乐观锁版本")
    private Integer version;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
