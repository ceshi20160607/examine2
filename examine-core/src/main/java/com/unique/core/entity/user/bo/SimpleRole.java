package com.unique.core.entity.user.bo;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;


/**用户角色对应关系
 * @author ceshi
 * @date 2024/06/25
 */
@Data
@ApiModel(value = "SimpleRole对象", description = "用户角色关系")
public class SimpleRole implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    @ApiModelProperty("模块ID")
    private Long moduleId;

    @ApiModelProperty("用户ID")
    private Long userId;

    @ApiModelProperty("用户ID-部门")
    private Long deptId;

    @ApiModelProperty("角色ID")
    private Long roleId;

    @ApiModelProperty("角色名称")
    private String roleName;

    @ApiModelProperty("是否超管 0非超管  1超管")
    private Integer adminFlag;

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



}
