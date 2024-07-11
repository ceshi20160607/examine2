package com.unique.module.entity.vo;

import java.io.Serializable;
import java.time.LocalDateTime;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * <p>
 * 自定义字段关联角色表
 * </p>
 *
 * @author UNIQUE
 * @since 2024-05-28
 */
@Data
@ApiModel(value = "ModuleFieldAuthVO对象", description = "自定义字段关联角色表")
public class ModuleFieldAuthVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("主键ID")
    private Long id;

    @ApiModelProperty("字段ID")
    private Long fieldId;

    @ApiModelProperty("角色id")
    private Long roleId;

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
