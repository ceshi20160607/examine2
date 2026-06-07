package com.unique.examine.module.manage.bo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

/**
 * 新增运行记录入参。
 */
@Data
@Schema(description = "新增运行记录入参")
public class RecordSaveBO {

    @Valid
    @NotEmpty(message = "字段值不能为空")
    @Schema(description = "动态字段值")
    private List<RecordFieldValueBO> values;

    @Schema(description = "保存备注")
    private String remark;
}
