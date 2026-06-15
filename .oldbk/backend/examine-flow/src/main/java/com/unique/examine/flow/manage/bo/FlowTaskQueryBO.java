package com.unique.examine.flow.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 流程任务查询入参。
 */
@Data
@Schema(description = "流程任务查询入参")
public class FlowTaskQueryBO {

    @Schema(description = "页码")
    private Long pageNo = 1L;

    @Schema(description = "每页条数")
    private Long pageSize = 20L;

    @Schema(description = "关键字")
    private String keyword;

    @Schema(description = "任务状态")
    private String status;
}
