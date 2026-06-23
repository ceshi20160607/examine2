package com.unique.examine.module.manage.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 字典类型回显。
 */
@Data
@Builder
@Schema(description = "字典类型回显")
public class DictTypeVO {

    @Schema(description = "字典类型 ID")
    private String dictTypeId;

    @Schema(description = "系统 ID")
    private String systemId;

    @Schema(description = "作用域：SYSTEM、TENANT")
    private String scopeType;

    @Schema(description = "租户 ID")
    private String tenantId;

    @Schema(description = "字典类型编码")
    private String code;

    @Schema(description = "字典类型名称")
    private String name;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "排序")
    private Integer sortOrder;

    @Schema(description = "是否内置只读")
    private Boolean systemBuiltIn;

    @Schema(description = "字典项数量")
    private Integer itemCount;

    @Schema(description = "启用字典项数量")
    private Integer enabledItemCount;

    @Schema(description = "是否被引用")
    private Boolean referenced;

    @Schema(description = "缓存版本")
    private Long cacheVersion;

    @Schema(description = "版本号，等同 cacheVersion")
    private Long version;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;

    @Schema(description = "缓存刷新信息")
    private DictCacheRefreshVO cacheRefresh;
}
