package com.unique.examine.manage.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "分页结果")
public class PageResult<T> {
    @Schema(description = "当前页")
    private long pageNo;
    @Schema(description = "每页条数")
    private long pageSize;
    @Schema(description = "总条数")
    private long total;
    @Schema(description = "列表数据")
    private List<T> records;
}
