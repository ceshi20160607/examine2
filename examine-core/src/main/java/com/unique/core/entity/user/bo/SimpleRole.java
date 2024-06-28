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

    @ApiModelProperty("名称")
    private String roleName;

    @ApiModelProperty("0 超管 1自定义 ")
    private Integer roleType;

    @ApiModelProperty("备注")
    private String remark;

    @ApiModelProperty(" 0 禁用 1 启用")
    private Integer status;

    @ApiModelProperty("数据权限 0默认全部 1本人，2本人及下属 3本部门 4本部门及下属部门 ")
    private Integer dataType;

    @ApiModelProperty("0 隐藏 1 不隐藏")
    private Integer hidden;

    @ApiModelProperty("0默认系统级别  1 审批级别")
    private Integer moduleType;

    @ApiModelProperty("创建时间")
    private LocalDateTime createTime;

    @ApiModelProperty("创建人ID")
    private Long createUserId;

    @ApiModelProperty("更新时间")
    private LocalDateTime updateTime;

    @ApiModelProperty("修改人ID")
    private Long updateUserId;



}
