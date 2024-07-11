package com.unique.module.entity.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * 用户模块的数据权限
 * </p>
 *
 * @author UNIQUE
 * @since 2024-07-01
 */
@Getter
@Setter
@TableName("un_module_data")
@ApiModel(value = "ModuleData对象", description = "用户模块的数据权限")
public class ModuleData implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    @ApiModelProperty("用户id")
    private Long userId;

    @ApiModelProperty("数据权限 1、本人，2、本人及下属，3、本部门，4、本部门及下属部门，5、全部")
    private Integer dataType;

    @ApiModelProperty("创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @ApiModelProperty("创建人ID")
    @TableField(fill = FieldFill.INSERT)
    private Long createUserId;

    @ApiModelProperty("更新时间")
    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime updateTime;

    @ApiModelProperty("修改人ID")
    @TableField(fill = FieldFill.UPDATE)
    private Long updateUserId;

    @ApiModelProperty("企业id")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long companyId;

}
