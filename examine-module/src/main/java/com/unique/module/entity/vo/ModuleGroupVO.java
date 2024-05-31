package com.unique.module.entity.vo;

import java.io.Serializable;
import java.time.LocalDateTime;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * <p>
 * 模块分组表
 * </p>
 *
 * @author UNIQUE
 * @since 2024-05-28
 */
@Data
@ApiModel(value = "ModuleGroupVO对象", description = "模块分组表")
public class ModuleGroupVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("分组id")
    private Long id;

    @ApiModelProperty("分组名称")
    private String name;

    @ApiModelProperty("流程的排序")
    private Integer sortNum;

    @ApiModelProperty("父级id")
    private Long parentId;

    @ApiModelProperty("列的深度")
    private String depthDepth;

    @ApiModelProperty("是否隐藏 0隐藏 1不隐藏")
    private Integer hiddenFlag;

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
