package com.unique.examine.app.manage.bo;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * OpenAPI 记录查询入参。
 */
@Data
@Schema(description = "OpenAPI 记录查询入参")
public class OpenApiRecordQueryBO {

    @NotBlank(message = "模块编码不能为空")
    @Schema(description = "模块编码")
    private String moduleCode;

    @Schema(description = "页码，从 1 开始")
    private Long pageNo = 1L;

    @Schema(description = "每页条数")
    private Long pageSize = 20L;

    @Schema(description = "关键字")
    private String keyword;

    @Schema(description = "动态字段过滤条件")
    private List<JsonNode> filters;

    @Schema(description = "排序规则")
    private List<JsonNode> sorter;
}
