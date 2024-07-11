package com.unique.module.entity.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * 主数据基础表
 * </p>
 *
 * @author UNIQUE
 * @since 2024-05-28
 */
@Getter
@Setter
@TableName("un_module_record")
@ApiModel(value = "ModuleRecord对象", description = "主数据基础表")
public class ModuleRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ApiModelProperty("模块ID")
    private Long moduleId;

    @ApiModelProperty("主数据的名称")
    private String name;

    @ApiModelProperty("主数据的编号")
    private String num;

    @ApiModelProperty("主数据的备注")
    private String remark;

    @ApiModelProperty("动态的一个属性--类型 ")
    private Integer recordFlag;

    @ApiModelProperty("状态  0草稿 1正常 2逻辑删除 ")
    private Integer statusFlag;

    @ApiModelProperty("审核状态 1通过 2拒绝 3审核中 ")
    private Integer checkFlag;

    @ApiModelProperty("进行审批的时候关联的审批实例")
    private Long examineRecordId;

    @ApiModelProperty("审批通过时间")
    private LocalDateTime examineTime;

    @ApiModelProperty("主数据默认数字字段")
    private Integer fieldnum1;

    @ApiModelProperty("主数据默认数字字段")
    private Integer fieldnum2;

    @ApiModelProperty("主数据默认数字字段")
    private Integer fieldnum3;

    @ApiModelProperty("主数据默认数字字段")
    private Integer fieldnum4;

    @ApiModelProperty("主数据默认数字字段")
    private Integer fieldnum5;

    @ApiModelProperty("主数据默认金额字段")
    private BigDecimal fielddecimal1;

    @ApiModelProperty("主数据默认金额字段")
    private BigDecimal fielddecimal2;

    @ApiModelProperty("主数据默认金额字段")
    private BigDecimal fielddecimal3;

    @ApiModelProperty("主数据默认金额字段")
    private BigDecimal fielddecimal4;

    @ApiModelProperty("主数据默认金额字段")
    private BigDecimal fielddecimal5;

    @ApiModelProperty("主数据默认long字段")
    private Long fieldlong1;

    @ApiModelProperty("主数据默认long字段")
    private Long fieldlong2;

    @ApiModelProperty("主数据默认long字段")
    private Long fieldlong3;

    @ApiModelProperty("主数据默认long字段")
    private Long fieldlong4;

    @ApiModelProperty("主数据默认long字段")
    private Long fieldlong5;

    @ApiModelProperty("主数据默认日期字段")
    private LocalDateTime fielddate1;

    @ApiModelProperty("主数据默认日期字段")
    private LocalDateTime fielddate2;

    @ApiModelProperty("主数据默认日期字段")
    private LocalDateTime fielddate3;

    @ApiModelProperty("主数据默认日期字段")
    private LocalDateTime fielddate4;

    @ApiModelProperty("主数据默认日期字段")
    private LocalDateTime fielddate5;

    @ApiModelProperty("主数据默认文本字段")
    private String fieldtext0;

    @ApiModelProperty("主数据默认文本字段")
    private String fieldtext1;

    @ApiModelProperty("主数据默认文本字段")
    private String fieldtext2;

    @ApiModelProperty("主数据默认文本字段")
    private String fieldtext3;

    @ApiModelProperty("主数据默认文本字段")
    private String fieldtext4;

    @ApiModelProperty("主数据默认文本字段")
    private String fieldtext5;

    @ApiModelProperty("主数据默认文本字段")
    private String fieldtext6;

    @ApiModelProperty("主数据默认文本字段")
    private String fieldtext7;

    @ApiModelProperty("主数据默认文本字段")
    private String fieldtext8;

    @ApiModelProperty("主数据默认文本字段")
    private String fieldtext9;


    @ApiModelProperty("转化的时候使用的id")
    private Long oldId;

    @ApiModelProperty("转化的时候使用的模块ID")
    private Long oldModuleId;



    @ApiModelProperty("所属部门")
    private Long ownerDeptId;

    @ApiModelProperty("创建人ID")
    @TableField(fill = FieldFill.INSERT)
    private Long createUserId;

    @ApiModelProperty("负责人ID")
    private Long ownerUserId;

    @ApiModelProperty("创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @ApiModelProperty("更新时间")
    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime updateTime;


    @ApiModelProperty("企业id")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long companyId;
}
