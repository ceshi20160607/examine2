package com.unique.examine.module.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 模块发布入参。
 */
@Data
@Schema(description = "模块发布入参")
public class PublishRequestBO {

    @Schema(description = "发布说明")
    private String publishRemark;

    @NotNull(message = "模块版本号不能为空")
    @Schema(description = "模块乐观锁版本")
    private Integer moduleVersion;
}
