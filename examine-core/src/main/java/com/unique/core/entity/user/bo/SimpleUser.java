package com.unique.core.entity.user.bo;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 用户表
 * </p>
 *
 * @author UNIQUE
 * @since 2023-03-25
 */
@Data
@ApiModel(value = "AdminUser对象", description = "用户表")
public class SimpleUser implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("主键")
    private Long id;

    @ApiModelProperty("模块ID")
    private Long moduleId;

    @ApiModelProperty("用户名")
    private String username;

    @ApiModelProperty("头像")
    private String img;

    @ApiModelProperty("真实姓名")
    private String realname;

    @ApiModelProperty("员工编号")
    private String num;

    @ApiModelProperty("手机号")
    private String mobile;

    @ApiModelProperty("邮箱")
    private String email;

    @ApiModelProperty("0 未选择 1 男 2 女 ")
    private Integer sex;

    @ApiModelProperty("部门")
    private Long deptId;

    @ApiModelProperty("岗位")
    private String post;

    @ApiModelProperty("状态,0未激活,1正常,2禁用")
    private Integer status;

    @ApiModelProperty("直属上级ID")
    private Long parentId;

    @ApiModelProperty("parent_id 构建的深度")
    private String deepth;

    @ApiModelProperty("最后登录时间")
    private LocalDateTime lastLoginTime;

    @ApiModelProperty("最后登录IP 注意兼容IPV6")
    private String lastLoginIp;

    @ApiModelProperty("创建时间")
    @JsonFormat
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


}
