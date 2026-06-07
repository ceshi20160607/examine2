package com.unique.examine.plat.manage.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 初始化对象返回项。
 */
@Data
@Builder
@Schema(description = "初始化对象返回项")
public class InitializedObjectVO {

    @Schema(description = "对象类型")
    private String objectType;

    @Schema(description = "对象编码")
    private String code;

    @Schema(description = "对象 ID")
    private String id;

    @Schema(description = "对象状态")
    private String status;
}
