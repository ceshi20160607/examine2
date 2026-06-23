package com.unique.examine.module.manage.vo;

import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 字典项回显。
 */
@Data
@Builder
@Schema(description = "字典项回显")
public class DictItemVO {

    @Schema(description = "字典项 ID")
    private String dictItemId;

    @Schema(description = "字典类型 ID")
    private String dictTypeId;

    @Schema(description = "父字典项 ID")
    private String parentId;

    @Schema(description = "字典项编码")
    private String code;

    @Schema(description = "展示文本")
    private String label;

    @Schema(description = "业务值")
    private String value;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "排序")
    private Integer sortOrder;

    @Schema(description = "层级")
    private Integer depthLevel;

    @Schema(description = "路径")
    private String depthPath;

    @Schema(description = "是否叶子节点")
    private Boolean leaf;

    @Schema(description = "是否内置只读")
    private Boolean systemBuiltIn;

    @Schema(description = "是否被记录引用")
    private Boolean referenced;

    @Schema(description = "缓存版本")
    private Long cacheVersion;

    @Schema(description = "版本号，等同 cacheVersion")
    private Long version;

    @Schema(description = "子项，仅树模式返回")
    private List<DictItemVO> children;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;

    @Schema(description = "缓存刷新信息")
    private DictCacheRefreshVO cacheRefresh;
}
