package com.unique.module.entity.vo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * <p>
 * 模块菜单功能权限配置表
 * </p>
 *
 * @author UNIQUE
 * @since 2024-07-01
 */
@Data
@ApiModel(value = "ModuleMenuVO对象", description = "模块菜单功能权限配置表")
public class ModuleMenuVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("菜单ID")
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
    private Long companyId;

}
