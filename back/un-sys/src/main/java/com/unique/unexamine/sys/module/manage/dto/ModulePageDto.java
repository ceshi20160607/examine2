package com.unique.unexamine.sys.module.manage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** 模块页面按用途保存字段布局，字段定义仍由模块字段配置维护。 */
public final class ModulePageDto {
    private ModulePageDto() {}

    /** 稳定字段主键及该页面独有的顺序、位置和宽度。 */
    public record FieldSlot(
            @NotNull @Schema(description="模块字段主键") Long fieldId,
            @Min(0) @Schema(description="页面内顺序") int sortNo,
            @Min(0) @Schema(description="页面行号") int rowNo,
            @Min(0) @Schema(description="所在行的列号") int columnNo,
            @Min(1) @Max(100) @Schema(description="所在行的占位宽度百分比") int widthPercent) {}

    /** 页面保存即生效，列表为空表示该页面不展示任何字段。 */
    public record SaveRequest(@NotNull @Valid @Schema(description="当前页面的全部字段布局") List<FieldSlot> fields) {}

    /** 配置态回显保持页面中的字段位置和顺序。 */
    public record PageView(@Schema(description="页面主键") Long id,
                           @Schema(description="模块主键") Long moduleId,
                           @Schema(description="CREATE、EDIT、DETAIL、LIST或RELATION") String pageKind,
                           @Schema(description="来源关联字段主键，普通页面为0") Long relationFieldId,
                           @Schema(description="当前有效页面字段") List<FieldSlot> fields) {}
}
