package com.unique.examine.module.entity.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * <p>
 * 模型字段定义
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-14
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_module_field")
@Schema(name = "ModuleField对象", description = "模型字段定义")
public class ModuleField implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "字段ID")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "systemId")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long systemId;

    @Schema(description = "tenantId")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tenantId;

    @Schema(description = "un_module_app.id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long appId;

    @Schema(description = "un_module_model.id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long modelId;

    @Schema(description = "字段编码（同 model 内唯一）")
    private String fieldCode;

    @Schema(description = "字段名称")
    private String fieldName;

    @Schema(description = "字段类型：string|number|date|datetime|bool|enum|ref|json 等")
    private String fieldType;

    @Schema(description = "是否必填：1=必填 0=可空")
    private Integer requiredFlag;

    @Schema(description = "是否唯一：1=唯一 0=否")
    private Integer uniqueFlag;

    @Schema(description = "是否隐藏（字段级配置默认）：1=隐藏 0=显示")
    private Integer hiddenFlag;

    @Schema(description = "输入提示/placeholder（可选）")
    private String tips;

    @Schema(description = "最大长度（string 等，可选）")
    private Integer maxLength;

    @Schema(description = "最小长度（string 等，可选）")
    private Integer minLength;

    @Schema(description = "校验类型（phone|email|idCard|url 等，可选）")
    private String validateType;

    @Schema(description = "日期格式（如 yyyymmdd / yyyy-mm-dd / yyyy-mm-dd HH:mm:ss）")
    private String dateFormat;

    @Schema(description = "数据字典编码（引用 un_module_dict.dict_code，可选）")
    private String dictCode;

    @Schema(description = "是否多选：1=多选 0=单选（enum/dict 等）")
    private Integer multiFlag;

    @Schema(description = "默认值（字符串表示，可选）")
    private String defaultValue;

    @Schema(description = "排序号")
    private Integer sortNo;

    @Schema(description = "状态：1=启用 2=停用")
    private Integer status;

    @Schema(description = "创建人 platId")
    @TableField(fill = FieldFill.INSERT)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long createUserId;

    @Schema(description = "更新人 platId")
    @TableField(fill = FieldFill.UPDATE)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long updateUserId;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime updateTime;


}
