package com.unique.examine.upload.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 附件引用创建入参。
 */
@Data
@Schema(description = "附件引用创建入参")
public class AttachmentCreateBO {

    @Schema(description = "文件 ID")
    private Long fileId;

    @Schema(description = "业务类型：MODULE_RECORD、FLOW_TASK、CONFIG")
    private String bizType;

    @Schema(description = "业务对象 ID")
    private Long bizId;

    @Schema(description = "关联字段编码")
    private String fieldCode;
}
