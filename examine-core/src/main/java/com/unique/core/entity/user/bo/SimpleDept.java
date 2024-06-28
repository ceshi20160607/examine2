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
 * @author ceshi
 * @date 2024/06/25
 */
@Data
@ApiModel(value = "SimpleDept对象", description = "部门表")
public class SimpleDept implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("主键")
    private Long id;

    @ApiModelProperty("默认0 部门  1公司")
    private Integer companyFlag;

    @ApiModelProperty("父级ID 顶级部门为0")
    private Long parentId;

    @ApiModelProperty("parent_id 构建的深度")
    private String deepth;

    @ApiModelProperty("部门名称")
    private String name;

    @ApiModelProperty("排序 越大越靠后")
    private Integer num;

    @ApiModelProperty("数据权限 1、本人，2、本人及下属，3、本部门，4、本部门及下属部门，5、全部")
    private Integer dataType;

    @ApiModelProperty("部门备注")
    private String remark;

    @ApiModelProperty("部门负责人")
    private Long ownerUserId;

    @ApiModelProperty("创建时间")
    private LocalDateTime createTime;

    @ApiModelProperty("创建人ID")
    private Long createUserId;

    @ApiModelProperty("更新时间")
    private LocalDateTime updateTime;

    @ApiModelProperty("修改人ID")
    private Long updateUserId;


}
