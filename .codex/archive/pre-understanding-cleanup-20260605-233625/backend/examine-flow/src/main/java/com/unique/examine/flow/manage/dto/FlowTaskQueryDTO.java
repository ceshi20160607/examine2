package com.unique.examine.flow.manage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 流程任务查询 DTO。
 */
@Data
@Schema(description = "流程任务查询 DTO")
public class FlowTaskQueryDTO {

    @Schema(description = "处理人账号 ID")
    private Long assigneeId;

    @Schema(description = "任务状态")
    private String status;
}
