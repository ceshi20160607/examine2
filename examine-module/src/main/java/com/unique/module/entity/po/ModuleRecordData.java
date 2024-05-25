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
 * 主数据自定义字段存值表
 * </p>
 *
 * @author UNIQUE
 * @since 2024-05-25
 */
@Getter
@Setter
@TableName("un_module_record_data")
@ApiModel(value = "ModuleRecordData对象", description = "主数据自定义字段存值表")
public class ModuleRecordData implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ApiModelProperty("模块ID")
    private Long moduleId;

    @ApiModelProperty("具体数据recordID")
    private Long recordId;

    @ApiModelProperty("字段ID")
    private Long fieldId;

    @ApiModelProperty("字段名称")
    private String name;

    @ApiModelProperty("值")
    private String value;

    @ApiModelProperty("老值")
    private String oldValue;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;


}
