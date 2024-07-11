package com.unique.module.entity.bo;

import com.unique.module.entity.po.ModuleMenu;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 模块功能
 * </p>
 *
 * @author UNIQUE
 * @since 2024-05-28
 */
@Data
@ApiModel(value = "ModuleMenuBO对象", description = "模块功能")
public class ModuleMenuBO {

    @ApiModelProperty("模块id")
    private Long moduleId;

    @ApiModelProperty("实体数据")
    private List<ModuleMenu> menuList;

    //todo:配置审批

}
