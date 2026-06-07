package com.unique.examine.module.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 更新运行记录入参。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "更新运行记录入参")
public class RecordUpdateBO extends RecordSaveBO {

    @NotNull(message = "记录版本不能为空")
    @Schema(description = "记录乐观锁版本")
    private Integer recordVersion;
}
