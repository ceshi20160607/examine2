package com.unique.examine.module.manage.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 字典使用情况。
 */
@Data
@Builder
@Schema(description = "字典使用情况")
public class DictUsageVO {

    @Schema(description = "字典类型 ID")
    private String dictTypeId;

    @Schema(description = "字典项 ID")
    private String dictItemId;

    @Schema(description = "字段引用")
    private List<DictFieldUsageVO> fieldUsages;

    @Schema(description = "记录值使用数量")
    private Long recordUsageCount;

    @Schema(description = "启用子项数量")
    private Long enabledChildrenCount;

    @Schema(description = "是否可停用")
    private Boolean canDisable;

    @Schema(description = "是否可删除")
    private Boolean canDelete;

    @Schema(description = "阻塞原因")
    private List<String> blockingReasons;
}
