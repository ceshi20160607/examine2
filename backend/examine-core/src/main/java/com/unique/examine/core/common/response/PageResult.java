package com.unique.examine.core.common.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分页响应数据。
 *
 * @param <T> 记录类型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "分页响应数据")
public class PageResult<T> {

    @Schema(description = "当前页记录")
    private List<T> records;

    @Schema(description = "总记录数")
    private long total;

    @Schema(description = "当前页码，从 1 开始")
    private long pageNo;

    @Schema(description = "每页数量")
    private long pageSize;

    @Schema(description = "是否存在下一页")
    private boolean hasNext;
}
