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
 * 系统内角色，含系统超级管理员。 entity.
 *
 * @author examine-generator
 * @since generated
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_module_role")
@Schema(name = "Role", description = "系统内角色，含系统超级管理员。")
public class Role implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Primary key ID.")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "所属系统 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long systemId;

    @Schema(description = "租户级角色归属；0 表示系统级角色。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tenantId;

    @Schema(description = "角色编码，同系统同租户唯一。")
    private String code;

    @Schema(description = "角色名称。")
    private String name;

    @Schema(description = "角色说明。")
    private String description;

    @Schema(description = "角色状态：ENABLED、DISABLED。")
    private String status;

    @Schema(description = "是否保护角色，如 SYS_SUPER_ADMIN。")
    private Byte protectedFlag;

    @Schema(description = "排序。")
    private Integer sortOrder;

    @Schema(description = "Soft delete unique reuse marker; active rows use 0.")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long deleteToken;

    @Schema(description = "Created time.")
    private LocalDateTime createdAt;

    @Schema(description = "Updated time.")
    private LocalDateTime updatedAt;
}
