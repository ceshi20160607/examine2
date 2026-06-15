package com.unique.examine.module.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 提交运行记录入参。
 */
@Data
@Schema(description = "提交运行记录入参")
public class RecordSubmitBO {

    @NotNull(message = "记录版本不能为空")
    @Schema(description = "记录乐观锁版本")
    private Integer recordVersion;

    @Schema(description = "提交说明")
    private String reason;
}
