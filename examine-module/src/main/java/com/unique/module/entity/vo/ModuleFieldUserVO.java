package com.unique.module.entity.vo;

import java.io.Serializable;
import java.time.LocalDateTime;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * <p>
 * 自定义字段关联用户表
 * </p>
 *
 * @author UNIQUE
 * @since 2024-05-28
 */
@Data
@ApiModel(value = "ModuleFieldUserVO对象", description = "自定义字段关联用户表")
public class ModuleFieldUserVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("主键ID")
    private Long id;

    @ApiModelProperty("字段ID")
    private Long fieldId;

    @ApiModelProperty("用户id")
    private Long userId;

    @ApiModelProperty("是否隐藏  0不隐藏 1隐藏")
    private Integer hiddenFlag;

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
