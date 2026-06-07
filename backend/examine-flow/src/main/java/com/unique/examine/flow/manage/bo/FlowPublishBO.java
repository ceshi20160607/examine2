package com.unique.examine.flow.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 流程发布入参。
 */
@Data
@Schema(description = "流程发布入参")
public class FlowPublishBO {

    @Schema(description = "模板版本号")
    private Integer templateVersion;

    @Schema(description = "发布说明")
    private String publishComment;
}
