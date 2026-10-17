package com.unique.unexamine.application.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 应用授权内的字段读写与脱敏策略
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_app_grant_field")
public class AppGrantField {

    /**
     * 授权字段主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属授权主键
     */
    @TableField("grant_id")
    private Long grantId;

    /**
     * 目标模块字段编码
     */
    @TableField("field_code")
    private String fieldCode;

    /**
     * 是否允许返回字段值
     */
    @TableField("readable")
    private Boolean readable;

    /**
     * 是否允许写入字段值
     */
    @TableField("writable")
    private Boolean writable;

    /**
     * 字段返回时的脱敏方式
     */
    @TableField("mask_strategy")
    private String maskStrategy;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

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

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
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
            ", createTime = " + createTime +
            "}";
    }
}
