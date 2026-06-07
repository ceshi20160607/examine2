package com.unique.examine.module.manage.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * 导出任务详情。
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@Schema(description = "导出任务详情")
public class ExportJobDetailVO extends ExportJobListItemVO {

    @Schema(description = "选中记录 ID 快照")
    private String selectedRecordIdsJson;

    @Schema(description = "筛选条件快照")
    private String filterSnapshotJson;

    @Schema(description = "排序规则快照")
    private String sorterSnapshotJson;

    @Schema(description = "字段快照")
    private String fieldSnapshotJson;

    @Schema(description = "权限快照")
    private String permissionSnapshotJson;

    @Schema(description = "数据范围快照")
    private String dataScopeSnapshotJson;
}
