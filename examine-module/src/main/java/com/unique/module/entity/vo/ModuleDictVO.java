package com.unique.module.entity.vo;

import java.io.Serializable;
import java.time.LocalDateTime;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * <p>
 * 数据字典组具体数据表
 * </p>
 *
 * @author UNIQUE
 * @since 2024-08-19
 */
@Data
@ApiModel(value = "ModuleDictVO对象", description = "数据字典组具体数据表")
public class ModuleDictVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    @ApiModelProperty("模块ID")
    private Long moduleId;

    @ApiModelProperty("字典组ID")
    private Long groupId;

    @ApiModelProperty("父级id 0表示顶层的系统")
    private Long parentId;

    @ApiModelProperty("列的深度")
    private String depthDepth;

    @ApiModelProperty("具体数据dictID")
    private Long dictId;

    @ApiModelProperty("具体数据recordID")
    private Long dictKey;

    @ApiModelProperty("名称")
    private String dictTitle;

    @ApiModelProperty("排序")
    private Integer sortNum;

    @ApiModelProperty("状态 1正常 0禁用")
    private Integer status;

    private LocalDateTime createTime;

    @ApiModelProperty("更新时间")
    private LocalDateTime updateTime;

    @ApiModelProperty("企业id")
    private Long companyId;


}
