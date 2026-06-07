package com.unique.examine.module.manage.vo;

import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 字典缓存刷新信息。
 */
@Data
@Builder
@Schema(description = "字典缓存刷新信息")
public class DictCacheRefreshVO {

    @Schema(description = "字典类型 ID")
    private String dictTypeId;

    @Schema(description = "缓存版本")
    private Long cacheVersion;

    @Schema(description = "刷新方式")
    private String refreshMode;

    @Schema(description = "刷新时间")
    private LocalDateTime refreshedAt;

    @Schema(description = "影响的缓存 key")
    private List<String> affectedKeys;
}
