package com.unique.core.entity.user.bo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;


/**
 * @author ceshi
 * @date 2024/06/25
 */
@Data
@ApiModel(value = "SimpleMenu对象", description = "角色菜单对应关系")
public class SimpleMenu implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    @ApiModelProperty("角色ID")
    private Long roleId;

    @ApiModelProperty("菜单ID")
    private Long menuId;

    @ApiModelProperty("创建时间")
    private LocalDateTime createTime;

    @ApiModelProperty("创建人ID")
    private Long createUserId;

    @ApiModelProperty("更新时间")
    private LocalDateTime updateTime;

    @ApiModelProperty("修改人ID")
    private Long updateUserId;


}
