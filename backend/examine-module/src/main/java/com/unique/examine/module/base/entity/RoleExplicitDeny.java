package com.unique.examine.module.base.entity;

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
 * 显式禁用权限项，优先于授权并集。 entity.
 *
 * @author examine-generator
 * @since 2026-06-06
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_module_role_explicit_deny")
@Schema(name = "RoleExplicitDeny", description = "显式禁用权限项，优先于授权并集。")
public class RoleExplicitDeny implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Primary key ID.")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "所属系统 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long systemId;

    @Schema(description = "系统角色 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long roleId;

    @Schema(description = "禁用类型：MENU、OPERATION、FIELD_READ、FIELD_WRITE、DATA_SCOPE、EXPORT、OPENAPI_SCOPE。")
    private String denyType;

    @Schema(description = "禁用对象 ID；0 表示按编码禁用。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long targetId;

    @Schema(description = "禁用对象编码。")
    private String targetCode;

    @Schema(description = "禁用原因。")
    private String reason;

    @Schema(description = "Soft delete unique reuse marker; active rows use 0.")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long deleteToken;

    @Schema(description = "Created time.")
    private LocalDateTime createdAt;

    @Schema(description = "Updated time.")
    private LocalDateTime updatedAt;
}
