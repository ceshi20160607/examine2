package com.unique.examine.upload.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 文件解绑入参。
 */
@Data
@Schema(description = "文件解绑入参")
public class FileUnbindDTO {

    @Schema(description = "文件 ID")
    @NotNull(message = "文件 ID 不能为空")
    private Long fileId;

    @Schema(description = "引用业务类型")
    private String bizType;

    @Schema(description = "业务对象 ID")
    private Long bizId;

    @Schema(description = "附件字段编码")
    private String fieldCode;
}
