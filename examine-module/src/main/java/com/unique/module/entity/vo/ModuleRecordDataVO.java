package com.unique.module.entity.vo;

import java.io.Serializable;
import java.time.LocalDateTime;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * <p>
 * 主数据自定义字段存值表
 * </p>
 *
 * @author UNIQUE
 * @since 2024-05-28
 */
@Data
@ApiModel(value = "ModuleRecordDataVO对象", description = "主数据自定义字段存值表")
public class ModuleRecordDataVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    @ApiModelProperty("模块ID")
    private Long moduleId;

    @ApiModelProperty("具体数据recordID")
    private Long recordId;

    @ApiModelProperty("字段ID")
    private Long fieldId;

    @ApiModelProperty("字段名称")
    private String name;

    @ApiModelProperty("值")
    private String value;

    @ApiModelProperty("老值")
    private String oldValue;

    private LocalDateTime createTime;

    @ApiModelProperty("企业id")
    private Long companyId;

}
