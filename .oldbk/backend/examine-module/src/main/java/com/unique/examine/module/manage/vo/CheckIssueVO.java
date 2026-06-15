package com.unique.examine.module.manage.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 发布检查问题。
 */
@Data
@Builder
@Schema(description = "发布检查问题")
public class CheckIssueVO {

    @Schema(description = "问题编码")
    private String code;

    @Schema(description = "问题级别：ERROR、WARN")
    private String level;

    @Schema(description = "配置对象类型：MODULE、FIELD、PAGE、MENU、ACTION")
    private String targetType;

    @Schema(description = "配置对象 ID")
    private String targetId;

    @Schema(description = "配置对象编码")
    private String targetCode;

    @Schema(description = "问题描述")
    private String message;
}
