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
 * 平台角色与菜单授权关联。 entity.
 *
 * @author examine-generator
 * @since generated
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_plat_role_menu")
@Schema(name = "RoleMenu", description = "平台角色与菜单授权关联。")
public class RoleMenu implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Primary key ID.")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "平台角色 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long roleId;

    @Schema(description = "平台菜单 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long menuId;

    @Schema(description = "Soft delete unique reuse marker; active rows use 0.")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long deleteToken;

    @Schema(description = "Created time.")
    private LocalDateTime createdAt;

    @Schema(description = "Updated time.")
    private LocalDateTime updatedAt;
}
