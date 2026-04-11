package com.unique.examine.module.entity.po;

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
 * 无代码应用
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-10
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_module_app")
@Schema(name = "ModuleApp对象", description = "无代码应用")
public class ModuleApp implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "应用ID")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "systemId")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long systemId;

    @Schema(description = "tenantId；无多租户时固定 0")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tenantId;

    @Schema(description = "应用编码（同 system 内唯一）")
    private String appCode;

    @Schema(description = "应用名称")
    private String appName;

    @Schema(description = "图标 URL")
    private String iconUrl;

    @Schema(description = "状态：1=启用 2=停用")
    private Integer status;

    @Schema(description = "是否已发布：0=否 1=是（MVP 可选）")
    private Integer publishedFlag;

    @Schema(description = "备注")
    private String remark;

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
