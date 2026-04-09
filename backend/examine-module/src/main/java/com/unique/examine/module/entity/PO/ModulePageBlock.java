package com.unique.examine.module.entity.PO;

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
 * 页面区块
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-09
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_module_page_block")
@Schema(name = "ModulePageBlock对象", description = "页面区块")
public class ModulePageBlock implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "区块ID")
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

    @Schema(description = "un_module_page.id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long pageId;

    @Schema(description = "区块类型：form|table|chart|text|custom")
    private String blockType;

    @Schema(description = "排序号")
    private Integer sortNo;

    @Schema(description = "区块配置 JSON")
    private String configJson;

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
