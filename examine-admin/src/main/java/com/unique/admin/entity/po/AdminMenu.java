package com.unique.admin.entity.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * 菜单权限配置表
 * </p>
 *
 * @author UNIQUE
 * @since 2023-03-25
 */
@Getter
@Setter
@TableName("un_admin_menu")
@ApiModel(value = "AdminMenu对象", description = "菜单权限配置表")
public class AdminMenu implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("菜单ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

//    @ApiModelProperty("上级菜单ID")
//    private Integer parentId;

//    @ApiModelProperty("parent_id 构建的深度")
//    private String deepth;

    @ApiModelProperty("菜单名称")
    private String menuName;

//    @ApiModelProperty("权限标识")
//    private String realm;
//
//    @ApiModelProperty("权限URL")
//    private String realmUrl;

    @ApiModelProperty("所属模块")
    private Long moduleId;

    @ApiModelProperty("菜单类型 0 列表1 详情2 添加3 编辑4 删除5 导入6 导出7 打印  10 修改状态 11 转化数据")
    private Integer menuType;

    @ApiModelProperty("修改状态/转化数据 对应的规则的json/数组")
    private String menuOption;

    @ApiModelProperty("排序（同级有效）")
    private Integer sort;

    @ApiModelProperty("状态  0 禁用 1 启用")
    private Integer status;

    @ApiModelProperty("菜单说明")
    private String remarks;


}
