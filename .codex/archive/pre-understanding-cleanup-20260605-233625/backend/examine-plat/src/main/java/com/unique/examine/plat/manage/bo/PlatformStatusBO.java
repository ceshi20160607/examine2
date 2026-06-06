package com.unique.examine.plat.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 平台资源状态变更入参。
 */
@Data
@Schema(description = "平台资源状态变更入参")
public class PlatformStatusBO {

    @Schema(description = "状态：ENABLED、DISABLED、LOCKED、EXPIRED")
    private String status;
}
