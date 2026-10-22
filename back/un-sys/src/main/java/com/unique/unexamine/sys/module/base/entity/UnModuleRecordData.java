package com.unique.unexamine.sys.module.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
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
 * @since 2026-10-22
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
     * 按字段类型保存的业务值
     */
    @TableField("value_json")
    private String valueJson;

    /**
     * 允许筛选排序的文本值索引
     */
    @TableField("index_text")
    private String indexText;

    /**
     * 允许筛选排序的数值索引
     */
    @TableField("index_decimal")
    private BigDecimal indexDecimal;

    /**
     * 允许筛选排序的时间值索引
     */
    @TableField("index_time")
    private LocalDateTime indexTime;

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

    public String getValueJson() {
        return valueJson;
    }

    public void setValueJson(String valueJson) {
        this.valueJson = valueJson;
    }

    public String getIndexText() {
        return indexText;
    }

    public void setIndexText(String indexText) {
        this.indexText = indexText;
    }

    public BigDecimal getIndexDecimal() {
        return indexDecimal;
    }

    public void setIndexDecimal(BigDecimal indexDecimal) {
        this.indexDecimal = indexDecimal;
    }

    public LocalDateTime getIndexTime() {
        return indexTime;
    }

    public void setIndexTime(LocalDateTime indexTime) {
        this.indexTime = indexTime;
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
}
