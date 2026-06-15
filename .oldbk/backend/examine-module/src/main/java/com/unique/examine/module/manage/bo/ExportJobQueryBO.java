package com.unique.examine.module.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 导出任务查询入参。
 */
@Data
@Schema(description = "导出任务查询入参")
public class ExportJobQueryBO {

    @Schema(description = "页码，从 1 开始")
    private Long pageNo = 1L;

    @Schema(description = "每页条数")
    private Long pageSize = 20L;

    @Schema(description = "模块 ID")
    private Long moduleId;

    @Schema(description = "任务状态")
    private String status;

    @Schema(description = "关键字，匹配文件名或失败原因")
    private String keyword;
}
