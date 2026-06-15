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
 * 系统内部门树。 entity.
 *
 * @author examine-generator
 * @since generated
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_module_dept")
@Schema(name = "Dept", description = "系统内部门树。")
public class Dept implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Primary key ID.")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "所属系统 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long systemId;

    @Schema(description = "所属租户；0 表示系统级共享部门。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tenantId;

    @Schema(description = "父部门 ID，0 表示根部门。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentId;

    @Schema(description = "部门编码，同系统同父级唯一。")
    private String code;

    @Schema(description = "部门名称。")
    private String name;

    @Schema(description = "部门状态：ENABLED、DISABLED。")
    private String status;

    @Schema(description = "排序。")
    private Integer sortOrder;

    @Schema(description = "层级。")
    private Integer depthLevel;

    @Schema(description = "部门路径。")
    private String depthPath;

    @Schema(description = "Soft delete unique reuse marker; active rows use 0.")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long deleteToken;

    @Schema(description = "Created time.")
    private LocalDateTime createdAt;

    @Schema(description = "Updated time.")
    private LocalDateTime updatedAt;
}
