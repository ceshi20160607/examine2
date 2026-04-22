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
 * 导出模板（字段集合与格式）
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-14
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_module_export_tpl")
@Schema(name = "ModuleExportTpl对象", description = "导出模板（字段集合与格式）")
public class ModuleExportTpl implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "导出模板ID")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "systemId")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long systemId;

    @Schema(description = "tenantId")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tenantId;

    @Schema(description = "un_module_app.id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long appId;

    @Schema(description = "un_module_model.id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long modelId;

    @Schema(description = "un_module_menu.id；为空表示模型级模板")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long menuId;

    @Schema(description = "模板编码（同 scope 内唯一）")
    private String tplCode;

    @Schema(description = "模板名称")
    private String tplName;

    @Schema(description = "文件类型：xlsx|csv")
    private String fileType;

    @Schema(description = "状态：1=启用 2=停用")
    private Integer status;

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
