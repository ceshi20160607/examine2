package com.unique.module.entity.bo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * <p>
 * 自定义字段表
 * </p>
 *
 * @author UNIQUE
 * @since 2024-05-28
 */
@Data
@ApiModel(value = "ModuleFieldUserBO对象", description = "自定义字段表")
public class ModuleFieldUserBO {

    @ApiModelProperty("模块id")
    private Long moduleId;

    @ApiModelProperty("排序的字段")
    private List<Long> sortIds;

    @ApiModelProperty("隐藏的字段")
    private List<Long> hiddenIds;



}
