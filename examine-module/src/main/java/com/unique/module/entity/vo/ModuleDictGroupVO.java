package com.unique.module.entity.vo;

import java.io.Serializable;
import java.time.LocalDateTime;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * <p>
 * 数据字典组表
 * </p>
 *
 * @author UNIQUE
 * @since 2024-08-19
 */
@Data
@ApiModel(value = "ModuleDictGroupVO对象", description = "数据字典组表")
public class ModuleDictGroupVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    @ApiModelProperty("模块ID")
    private Long moduleId;

    @ApiModelProperty("名称")
    private String name;

    @ApiModelProperty("状态 1正常 0禁用")
    private Integer status;

    private LocalDateTime createTime;

    @ApiModelProperty("更新时间")
    private LocalDateTime updateTime;

    @ApiModelProperty("企业id")
    private Long companyId;


}
