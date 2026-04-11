package com.unique.examine.plat.entity.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
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
 * <p>
 * 自建系统/应用
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-10
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_plat_system")
@Schema(name = "PlatSystem对象", description = "自建系统/应用")
public class PlatSystem implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "systemId；0=平台占位")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "系统名称")
    private String name;

    @Schema(description = "图标 URL")
    private String iconUrl;

    @Schema(description = "是否启用多租户：0=否 1=是")
    private Integer multiTenantEnabled;

    @Schema(description = "默认 tenantId")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long defaultTenantId;

    @Schema(description = "状态：1=启用 2=停用")
    private Integer status;

    @Schema(description = "创建/所有者 platId")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ownerPlatAccountId;

    @Schema(description = "创建人 platId")
    @TableField(fill = FieldFill.INSERT)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long createUserId;

    @Schema(description = "更新人 platId")
    @TableField(fill = FieldFill.UPDATE)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long updateUserId;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime updateTime;


}
