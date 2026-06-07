package com.unique.examine.app.manage.bo;

import java.util.List;

import com.unique.examine.module.manage.bo.RecordFieldValueBO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

/**
 * OpenAPI 记录创建入参。
 */
@Data
@Schema(description = "OpenAPI 记录创建入参")
public class OpenApiRecordSaveBO {

    @NotBlank(message = "模块编码不能为空")
    @Schema(description = "模块编码")
    private String moduleCode;

    @Valid
    @NotEmpty(message = "字段值不能为空")
    @Schema(description = "动态字段值")
    private List<RecordFieldValueBO> values;

    @Schema(description = "保存备注")
    private String remark;
}
