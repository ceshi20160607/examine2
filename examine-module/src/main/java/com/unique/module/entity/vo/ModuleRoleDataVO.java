package com.unique.module.entity.vo;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * <p>
 * 模块的角色对应数据权限
 * </p>
 *
 * @author UNIQUE
 * @since 2024-07-02
 */
@Data
@ApiModel(value = "ModuleRoleDataVO对象", description = "模块的角色对应数据权限")
public class ModuleRoleDataVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    @ApiModelProperty("角色ID")
    private Long roleId;

    @ApiModelProperty("所属模块")
    private Long moduleId;

    @ApiModelProperty("数据权限 1、本人，2、本人及下属，3、本部门，4、本部门及下属部门，5、全部")
    private Integer dataType;

    @ApiModelProperty("创建时间")
    private LocalDateTime createTime;

    @ApiModelProperty("创建人ID")
    private Long createUserId;

    @ApiModelProperty("更新时间")
    private LocalDateTime updateTime;

    @ApiModelProperty("修改人ID")
    private Long updateUserId;

    @ApiModelProperty("企业id")
    private Long companyId;

}
