package com.unique.module.entity.bo;

import com.baomidou.mybatisplus.annotation.*;
import com.unique.module.entity.po.ModuleField;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;
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
@ApiModel(value = "ModuleFieldBO对象", description = "自定义字段表")
public class ModuleFieldBO{

    @ApiModelProperty("模块id")
    private Long moduleId;

    @ApiModelProperty("控制的字段")
    private List<ModuleField> fieldList;

    @ApiModelProperty("删除的字段")
    private List<ModuleField> removeList;



}
