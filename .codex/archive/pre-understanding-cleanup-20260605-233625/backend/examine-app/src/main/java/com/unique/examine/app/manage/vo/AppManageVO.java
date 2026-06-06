package com.unique.examine.app.manage.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 应用与 OpenAPI 统一出参。
 */
@Data
@Schema(description = "应用与 OpenAPI 统一出参")
public class AppManageVO {

    @Schema(description = "主键 ID")
    private Long id;

    @Schema(description = "租户 ID")
    private Long tenantId;

    @Schema(description = "系统 ID")
    private Long systemId;

    @Schema(description = "客户端 ID")
    private Long clientId;

    @Schema(description = "应用 ID")
    private Long appId;

    @Schema(description = "模块 ID")
    private Long moduleId;

    @Schema(description = "编码")
    private String code;

    @Schema(description = "名称")
    private String name;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "类型或动作集合")
    private String type;

    @Schema(description = "密钥版本或版本号")
    private Integer versionNo;

    @Schema(description = "访问 key、IP 或请求 ID")
    private String value;

    @Schema(description = "配置快照、请求摘要或错误摘要")
    private String detail;

    @Schema(description = "过期时间")
    private LocalDateTime expiredAt;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
