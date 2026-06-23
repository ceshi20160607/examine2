package com.unique.examine.module.manage.bo;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 运行记录查询入参。
 */
@Data
@Schema(description = "运行记录查询入参")
public class RecordQueryBO {

    @Schema(description = "页码，从 1 开始")
    private Long pageNo = 1L;

    @Schema(description = "每页条数")
    private Long pageSize = 20L;

    @Schema(description = "关键字，匹配记录标题或编号")
    private String keyword;

    @Schema(description = "动态字段过滤条件，MVP 先透传记录，后续按字段索引扩展")
    private List<JsonNode> filters;

    @Schema(description = "排序规则，MVP 先按更新时间倒序")
    private List<JsonNode> sorter;
}
