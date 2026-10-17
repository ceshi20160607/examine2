package com.unique.unexamine.runtimedata.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 通用业务记录的自定义字段原始值
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_module_record_data")
public class RecordData {

    /**
     * 记录字段值主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属业务主记录主键
     */
    @TableField("record_id")
    private Long recordId;

    /**
     * 对应模块字段主键
     */
    @TableField("field_id")
    private Long fieldId;

    /**
     * 保留原始类型和内容的字段值
     */
    @TableField("value_json")
    private String valueJson;

    /**
     * 字段值创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 字段值更新时间
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

    @Override
    public String toString() {
        return "RecordData{" +
            "id = " + id +
            ", recordId = " + recordId +
            ", fieldId = " + fieldId +
            ", valueJson = " + valueJson +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            "}";
    }
}
