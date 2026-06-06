package com.unique.examine.module.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * 模块字段
 * </p>
 *
 * @author codex
 * @since 2026-06-05
 */
@Getter
@Setter
@TableName("un_module_field")
public class Field implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 模块 ID
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 字段编码
     */
    @TableField("field_code")
    private String fieldCode;

    /**
     * 字段名称
     */
    @TableField("field_name")
    private String fieldName;

    /**
     * 类型：TEXT、NUMBER、DECIMAL、DATE、DATETIME、SELECT、MULTI_SELECT、USER、DEPT、FILE
     */
    @TableField("field_type")
    private String fieldType;

    /**
     * 是否必填：0-否，1-是
     */
    @TableField("required_flag")
    private Byte requiredFlag;

    /**
     * 是否唯一：0-否，1-是
     */
    @TableField("unique_flag")
    private Byte uniqueFlag;

    /**
     * 列表是否可见：0-否，1-是
     */
    @TableField("list_visible")
    private Byte listVisible;

    /**
     * 是否可搜索：0-否，1-是
     */
    @TableField("searchable")
    private Byte searchable;

    /**
     * 是否可编辑：0-否，1-是
     */
    @TableField("editable")
    private Byte editable;

    /**
     * 默认值
     */
    @TableField("default_value")
    private String defaultValue;

    /**
     * 校验规则
     */
    @TableField("validation_json")
    private String validationJson;

    /**
     * 排序号
     */
    @TableField("sort_order")
    private Integer sortOrder;

    /**
     * 创建人账号 ID
     */
    @TableField("created_by")
    private Long createdBy;

    /**
     * 更新人账号 ID
     */
    @TableField("updated_by")
    private Long updatedBy;

    /**
     * 创建时间
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    /**
     * 逻辑删除：0-否，1-是
     */
    @TableField("deleted")
    @TableLogic
    private Byte deleted;
}
