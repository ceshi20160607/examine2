package com.unique.examine.manage.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "进入系统上下文入参")
public class ContextEnterBO {
    @NotNull
    @Schema(description = "系统ID")
    private Long systemId;

    @Schema(description = "租户ID；传入时必须与系统归属租户一致")
    private Long tenantId;
}

