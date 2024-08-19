package com.unique.module.entity.vo;

import java.io.Serializable;
import java.time.LocalDateTime;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * <p>
 * 数据字段基础表
 * </p>
 *
 * @author UNIQUE
 * @since 2024-08-19
 */
@Data
@ApiModel(value = "ModuleDictBaseVO对象", description = "数据字段基础表")
public class ModuleDictBaseVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    @ApiModelProperty("模块ID")
    private Long moduleId;

    @ApiModelProperty("具体数据recordID")
    private Long dictKey;

    @ApiModelProperty("名称")
    private String dictTitle;

    @ApiModelProperty("状态 1正常 0禁用")
    private Integer status;

    @ApiModelProperty("修改后是否应用所有 0不应用 1应用")
    private Integer useFlag;

    private LocalDateTime createTime;

    @ApiModelProperty("更新时间")
    private LocalDateTime updateTime;

    @ApiModelProperty("企业id")
    private Long companyId;


}
