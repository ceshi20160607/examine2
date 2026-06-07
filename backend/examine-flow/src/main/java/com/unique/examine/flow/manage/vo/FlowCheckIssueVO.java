package com.unique.examine.flow.manage.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 流程发布检查问题。
 */
@Data
@Builder
@Schema(description = "流程发布检查问题")
public class FlowCheckIssueVO {

    @Schema(description = "问题编码")
    private String code;

    @Schema(description = "级别")
    private String level;

    @Schema(description = "对象类型")
    private String targetType;

    @Schema(description = "对象编码")
    private String targetCode;

    @Schema(description = "问题说明")
    private String message;
}
