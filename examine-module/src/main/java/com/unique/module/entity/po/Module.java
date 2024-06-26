package com.unique.module.entity.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
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
 * 模块表
 * </p>
 *
 * @author UNIQUE
 * @since 2024-05-28
 */
@Getter
@Setter
@TableName("un_module")
@ApiModel(value = "Module对象", description = "模块表")
public class Module implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("分组id")
    private Long id;

    @ApiModelProperty("分组名称")
    private String name;

    @ApiModelProperty("流程的排序")
    private Integer sortNum;

    @ApiModelProperty("父级id 0表示顶层的系统")
    private Long parentId;

    @ApiModelProperty("列的深度")
    private String depthDepth;

    @ApiModelProperty("类型标识 0分组结构 1数据模块")
    private Integer typeFlag;

    @ApiModelProperty("是否隐藏 0隐藏 1不隐藏")
    private Integer hiddenFlag;

    @ApiModelProperty("模块的根id")
    private Long rootId;

    @ApiModelProperty("创建人")
    @TableField(fill = FieldFill.INSERT)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long createUserId;

    @ApiModelProperty("更新人")
    @TableField(fill = FieldFill.UPDATE)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long updateUserId;

    @ApiModelProperty("创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @ApiModelProperty("更新时间")
    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime updateTime;

    @ApiModelProperty("企业id")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long companyId;


}
