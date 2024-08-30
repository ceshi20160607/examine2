package com.unique.core.entity.base.vo;

import com.unique.core.entity.user.bo.SimpleDept;
import com.unique.core.entity.user.bo.SimpleMenu;
import com.unique.core.entity.user.bo.SimpleUser;
import com.unique.core.entity.user.bo.SimpleRole;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Set;


/**
 * 权限对象
 * @author ceshi
 * @date 2024/06/25
 */
@Data
@ApiModel(value = "AuthVO对象", description = "权限对象")
public class AuthVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("是否超管")
    private Boolean adminFlag;

    @ApiModelProperty("主键")
    private SimpleUser simpleUser;

    @ApiModelProperty("主键")
    private SimpleDept simpleDept;

    @ApiModelProperty("主键")
    private List<SimpleRole> userRoles;

    @ApiModelProperty("主键")
    private List<SimpleMenu> roleMenus;

    //------------------------数据权限-------------------------
    @ApiModelProperty("数据权限")
    private List<Long> dataDeptIds;
    @ApiModelProperty("数据权限")
    private Set<Long> dataUserIds;

    @ApiModelProperty("数据权限")
    private List<SimpleDept> dataSimpleDeptIds;
    @ApiModelProperty("数据权限")
    private List<SimpleUser> dataSimpleUserIds;
    //------------------------数据权限-------------------------


}
