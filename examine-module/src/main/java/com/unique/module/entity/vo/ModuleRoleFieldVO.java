package com.unique.module.entity.vo;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * <p>
 * 模块的角色对应字段权限
 * </p>
 *
 * @author UNIQUE
 * @since 2024-07-02
 */
@Data
@ApiModel(value = "ModuleRoleFieldVO对象", description = "模块的角色对应字段权限")
public class ModuleRoleFieldVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("主键ID")
    private Long id;

    @ApiModelProperty("角色id")
    private Long roleId;

    @ApiModelProperty("所属模块")
    private Long moduleId;

    @ApiModelProperty("字段ID")
    private Long fieldId;

    @ApiModelProperty("授权类型   0不能查看   1只能看 2可以编辑")
    private Integer authType;

    @ApiModelProperty("创建时间")
    private LocalDateTime createTime;

    @ApiModelProperty("创建人ID")
    private Long createUserId;

    @ApiModelProperty("更新时间")
    private LocalDateTime updateTime;

    @ApiModelProperty("修改人ID")
    private Long updateUserId;

    @ApiModelProperty("企业id")
    private Long companyId;

}
