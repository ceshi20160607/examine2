package com.unique.module.entity.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * 数据字段基础表
 * </p>
 *
 * @author UNIQUE
 * @since 2024-08-19
 */
@Getter
@Setter
@TableName("un_module_dict_base")
@ApiModel(value = "ModuleDictBase对象", description = "数据字段基础表")
public class ModuleDictBase implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ApiModelProperty("模块ID")
    private Long moduleId;

    @ApiModelProperty("具体数据recordID")
    private Long dictKey;

    @ApiModelProperty("名称")
    private String dictTitle;

    @ApiModelProperty("状态 1正常 0禁用")
    private Integer status;

    @ApiModelProperty("修改后是否应用所有 0不应用 1应用")
    private Integer useFlag;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @ApiModelProperty("更新时间")
    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime updateTime;

    @ApiModelProperty("企业id")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long companyId;


}
