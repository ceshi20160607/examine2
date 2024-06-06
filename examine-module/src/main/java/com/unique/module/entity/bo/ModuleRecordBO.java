package com.unique.module.entity.bo;

import com.unique.module.entity.po.ModuleField;
import com.unique.module.entity.po.ModuleRecord;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 实例
 * </p>
 *
 * @author UNIQUE
 * @since 2024-05-28
 */
@Data
@ApiModel(value = "ModuleRecordBO对象", description = "实例数据")
public class ModuleRecordBO {

    @ApiModelProperty("模块id")
    private Long moduleId;

    @ApiModelProperty("实体数据")
    private Map<String,Object> entity;

    //todo:配置审批

}
