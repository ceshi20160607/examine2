package com.unique.examine.manage.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "状态更新入参")
public class StatusUpdateBO {
    @NotBlank
    @Schema(description = "目标状态")
    private String status;
}

