package com.unique.module.entity.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * 字典表
 * </p>
 *
 * @author UNIQUE
 * @since 2024-05-28
 */
@Getter
@Setter
@TableName("un_module_dict")
@ApiModel(value = "ModuleDict对象", description = "字典表")
public class ModuleDict implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ApiModelProperty("模块ID")
    private Long moduleId;

    @ApiModelProperty("组名称")
    private String groupName;

    @ApiModelProperty("字典key")
    private Integer dictKey;

    @ApiModelProperty("字典value")
    private String dictValue;

    @ApiModelProperty("是否隐藏  0不隐藏 1隐藏")
    private Integer hiddenFlag;

    @ApiModelProperty("备注")
    private String remark;

    @ApiModelProperty("创建人ID")
    @TableField(fill = FieldFill.INSERT)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long createUserId;

    @ApiModelProperty("负责人ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ownerUserId;

    @ApiModelProperty("创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @ApiModelProperty("更新时间")
    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime updateTime;


}
