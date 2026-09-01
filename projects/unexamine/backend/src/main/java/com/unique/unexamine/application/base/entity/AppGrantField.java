package com.unique.unexamine.application.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("app_grant_field")
public class AppGrantField {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("grant_id")
    private Long grantId;

    @TableField("field_code")
    private String fieldCode;

    @TableField("readable")
    private Boolean readable;

    @TableField("writable")
    private Boolean writable;

    @TableField("mask_strategy")
    private String maskStrategy;

    @TableField("created_at")
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getGrantId() {
        return grantId;
    }

    public void setGrantId(Long grantId) {
        this.grantId = grantId;
    }

    public String getFieldCode() {
        return fieldCode;
    }

    public void setFieldCode(String fieldCode) {
        this.fieldCode = fieldCode;
    }

    public Boolean getReadable() {
        return readable;
    }

    public void setReadable(Boolean readable) {
        this.readable = readable;
    }

    public Boolean getWritable() {
        return writable;
    }

    public void setWritable(Boolean writable) {
        this.writable = writable;
    }

    public String getMaskStrategy() {
        return maskStrategy;
    }

    public void setMaskStrategy(String maskStrategy) {
        this.maskStrategy = maskStrategy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "AppGrantField{" +
            "id = " + id +
            ", grantId = " + grantId +
            ", fieldCode = " + fieldCode +
            ", readable = " + readable +
            ", writable = " + writable +
            ", maskStrategy = " + maskStrategy +
            ", createdAt = " + createdAt +
            "}";
    }
}
