package com.unique.module.entity.po;

import com.baomidou.mybatisplus.annotation.*;

import java.io.Serializable;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * 模块菜单功能权限配置表
 * </p>
 *
 * @author UNIQUE
 * @since 2024-07-01
 */
@Getter
@Setter
@TableName("un_module_menu")
@ApiModel(value = "ModuleMenu对象", description = "模块菜单功能权限配置表")
public class ModuleMenu implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("菜单ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ApiModelProperty("菜单名称")
    private String menuName;

    @ApiModelProperty("所属模块")
    private Long moduleId;

    @ApiModelProperty("菜单类型 0 列表1 详情2 添加3 编辑4 删除5 导入6 导出7 打印  10 修改状态 11 转化数据")
    private Integer menuType;

    @ApiModelProperty("修改状态/转化数据 对应的规则的json")
    private String menuOption;

    @ApiModelProperty("排序（同级有效）")
    private Integer sorts;

    @ApiModelProperty("状态  0 禁用 1 启用")
    private Integer status;

    @ApiModelProperty("菜单说明")
    private String remarks;


    @ApiModelProperty("企业id")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long companyId;
}
