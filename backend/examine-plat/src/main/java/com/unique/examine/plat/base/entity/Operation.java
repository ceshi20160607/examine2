package com.unique.examine.plat.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 平台中心操作权限点。 entity.
 *
 * @author examine-generator
 * @since 2026-06-06
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_plat_operation")
@Schema(name = "Operation", description = "平台中心操作权限点。")
public class Operation implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Primary key ID.")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "所属平台菜单。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long menuId;

    @Schema(description = "操作权限编码，例如 PLAT_ACCOUNT_CREATE。")
    private String code;

    @Schema(description = "操作名称。")
    private String name;

    @Schema(description = "对应 API 路径模式。")
    private String apiPattern;

    @Schema(description = "HTTP 方法。")
    private String method;

    @Schema(description = "状态：ENABLED、DISABLED。")
    private String status;

    @Schema(description = "Soft delete unique reuse marker; active rows use 0.")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long deleteToken;

    @Schema(description = "Created time.")
    private LocalDateTime createdAt;

    @Schema(description = "Updated time.")
    private LocalDateTime updatedAt;
}
