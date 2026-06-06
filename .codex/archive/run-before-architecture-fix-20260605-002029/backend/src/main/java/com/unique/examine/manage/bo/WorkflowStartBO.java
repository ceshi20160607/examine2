package com.unique.examine.manage.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "发起流程入参")
public class WorkflowStartBO {
    @NotNull private Long systemId;
    @NotNull private Long tenantId;
    @NotNull private Long moduleId;
    @NotNull private Long recordId;
}

