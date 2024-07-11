package com.unique.module.entity.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * 自定义字段表
 * </p>
 *
 * @author UNIQUE
 * @since 2024-05-28
 */
@Getter
@Setter
@TableName("un_module_field")
@ApiModel(value = "ModuleField对象", description = "自定义字段表")
public class ModuleField implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("主键ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ApiModelProperty("模块id")
    private Long moduleId;

    @ApiModelProperty("自定义字段英文标识")
    private String fieldName;

    @ApiModelProperty("字段名称")
    private String name;

    @ApiModelProperty("字段类型 1 单行文本 2 多行文本 3 单选 4日期 5 数字 6 小数 7 手机  8 文件 9 多选 10 人员 11 附件 12 部门 13 日期时间 14 邮箱 15客户 16 商机 17 联系人 18 地图 19 产品类型 20 合同 21 回款计划")
    private Integer type;

    @ApiModelProperty("字段说明")
    private String remark;

    @ApiModelProperty("输入提示")
    private String inputTips;

    @ApiModelProperty("最大长度")
    private Integer maxLength;

    @ApiModelProperty("默认值")
    private String defaultValue;

    @ApiModelProperty("具体的数值")
    @TableField(exist = false)
    private String value;

    @ApiModelProperty("唯一 0不唯一 1唯一")
    private Integer unionFlag;

    @ApiModelProperty("必填 0不必填 1必填")
    private Integer mustFlag;

    @ApiModelProperty("隐藏 0不隐藏 1隐藏")
    private Integer hiddenFlag;

    @ApiModelProperty("删除 0不删除 1删除")
    private Integer deleteFlag;

    @ApiModelProperty("新建 0不新建 1新建")
    private Integer addFlag;

    @ApiModelProperty("列表 0不列表 1列表")
    private Integer indexFlag;

    @ApiModelProperty("详情 0不详情 1详情")
    private Integer detailFlag;

    @ApiModelProperty("排序 从小到大")
    private Integer sorting;

    @ApiModelProperty("字段来源  0.自定义 1.原始固定 ")
    private Integer fieldType;

    @ApiModelProperty("字典id")
    private Long dictId;

    @ApiModelProperty("json类型的数据，如果是下来可以配置显示隐藏字段")
    private String optionData;

    @ApiModelProperty("父级id")
    private Long parentId;

    @ApiModelProperty("列的深度")
    private String depthDepth;

    @ApiModelProperty("转化modelid")
    private Long transferModelId;

    @ApiModelProperty("转化字段名称")
    private String transferFieldName;

    @ApiModelProperty("样式百分比%")
    private Integer stylePercent;

    @ApiModelProperty("精度，允许的最大小数位")
    private Integer precisions;

    @ApiModelProperty("限制的最大数值")
    private String maxNumRestrict;

    @ApiModelProperty("限制的最小数值")
    private String minNumRestrict;

    @ApiModelProperty("存储的坐标位置x轴")
    private Integer axisx;

    @ApiModelProperty("存储的坐标位置y轴")
    private Integer axisy;

    @ApiModelProperty("创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @ApiModelProperty("创建人ID")
    @TableField(fill = FieldFill.INSERT)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long createUserId;

    @ApiModelProperty("更新时间")
    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime updateTime;

    @ApiModelProperty("修改人ID")
    @TableField(fill = FieldFill.UPDATE)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long updateUserId;


    @ApiModelProperty("企业id")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long companyId;
}
