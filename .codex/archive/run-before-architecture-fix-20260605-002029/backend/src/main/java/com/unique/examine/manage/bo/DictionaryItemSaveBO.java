package com.unique.examine.manage.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "字典项保存入参")
public class DictionaryItemSaveBO {
    @NotNull private Long dictId;
    @NotBlank private String itemLabel;
    @NotBlank private String itemValue;
    @Schema(description = "排序号") private Integer sortOrder;
    @Schema(description = "状态：ENABLED/DISABLED") private String status;
}

