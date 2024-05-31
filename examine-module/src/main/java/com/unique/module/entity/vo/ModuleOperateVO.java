package com.unique.module.entity.vo;

import java.io.Serializable;
import java.time.LocalDateTime;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * <p>
 * 模块操作功能表
 * </p>
 *
 * @author UNIQUE
 * @since 2024-05-28
 */
@Data
@ApiModel(value = "ModuleOperateVO对象", description = "模块操作功能表")
public class ModuleOperateVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("id")
    private Long id;

    @ApiModelProperty("模块id")
    private Long moduleId;

    @ApiModelProperty("功能名称")
    private String name;

    @ApiModelProperty("功能基础类型 0系统默认 1业务分配 2自定义")
    private Integer flag;

    @ApiModelProperty("创建人")
    private Long createUserId;

    @ApiModelProperty("更新人")
    private Long updateUserId;

    @ApiModelProperty("创建时间")
    private LocalDateTime createTime;

    @ApiModelProperty("更新时间")
    private LocalDateTime updateTime;

    @ApiModelProperty("企业id")
    private Long companyId;


}
