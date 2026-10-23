package com.unique.unexamine.sys.module.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <p>
 * 模块通用业务扩展记录
 * </p>
 *
 * @author Template Base
 * @since 2026-10-23
 */
@TableName("un_module_record_data")
public class UnModuleRecordData implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 业务字段值主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 通用业务主记录主键
     */
    @TableField("record_id")
    private Long recordId;

    /**
     * 保存时对应的模块字段主键
     */
    @TableField("field_id")
    private Long fieldId;

    /**
     * 保存时的字段编码快照
     */
    @TableField("field_code")
    private String fieldCode;

    /**
     * 短字符串及附件批次UUID业务值
     */
    @TableField("value_varchar")
    private String valueVarchar;

    /**
     * 多行文本及其他长文本业务值
     */
    @TableField("value_text")
    private String valueText;

    /**
     * 数值标识和布尔等数值业务值
     */
    @TableField("value_number")
    private BigDecimal valueNumber;

    /**
     * 日期和日期时间业务值
     */
    @TableField("value_time")
    private LocalDateTime valueTime;

    /**
     * 多选级联和结构化字段业务值
     */
    @TableField("value_json")
    private String valueJson;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 逻辑删除时间，空表示未删除
     */
    @TableLogic
    @TableField("deleted_time")
    private LocalDateTime deletedTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRecordId() {
        return recordId;
    }

    public void setRecordId(Long recordId) {
        this.recordId = recordId;
    }

    public Long getFieldId() {
        return fieldId;
    }

    public void setFieldId(Long fieldId) {
        this.fieldId = fieldId;
    }

    public String getFieldCode() {
        return fieldCode;
    }

    public void setFieldCode(String fieldCode) {
        this.fieldCode = fieldCode;
    }

    public String getValueVarchar() {
        return valueVarchar;
    }

    public void setValueVarchar(String valueVarchar) {
        this.valueVarchar = valueVarchar;
    }

    public String getValueText() {
        return valueText;
    }

    public void setValueText(String valueText) {
        this.valueText = valueText;
    }

    public BigDecimal getValueNumber() {
        return valueNumber;
    }

    public void setValueNumber(BigDecimal valueNumber) {
        this.valueNumber = valueNumber;
    }

    public LocalDateTime getValueTime() {
        return valueTime;
    }

    public void setValueTime(LocalDateTime valueTime) {
        this.valueTime = valueTime;
    }

    public String getValueJson() {
        return valueJson;
    }

    public void setValueJson(String valueJson) {
        this.valueJson = valueJson;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public LocalDateTime getDeletedTime() {
        return deletedTime;
    }

    public void setDeletedTime(LocalDateTime deletedTime) {
        this.deletedTime = deletedTime;
    }
}
