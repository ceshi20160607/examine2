package com.unique.examine.module.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * 模块业务记录字段值
 * </p>
 *
 * @author codex
 * @since 2026-06-05
 */
@Getter
@Setter
@TableName("un_module_record_value")
public class RecordValue implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 记录 ID
     */
    @TableField("record_id")
    private Long recordId;

    /**
     * 模块 ID，查询冗余
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 字段 ID
     */
    @TableField("field_id")
    private Long fieldId;

    /**
     * 字段编码，查询冗余
     */
    @TableField("field_code")
    private String fieldCode;

    /**
     * 文本值
     */
    @TableField("value_text")
    private String valueText;

    /**
     * 数值
     */
    @TableField("value_number")
    private BigDecimal valueNumber;

    /**
     * 日期时间值
     */
    @TableField("value_datetime")
    private LocalDateTime valueDatetime;

    /**
     * 复杂值
     */
    @TableField("value_json")
    private String valueJson;

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
}
