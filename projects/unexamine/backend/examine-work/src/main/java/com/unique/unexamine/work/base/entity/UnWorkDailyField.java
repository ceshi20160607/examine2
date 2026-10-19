package com.unique.unexamine.work.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 工作日志字段
 * </p>
 *
 * @author Template Base
 * @since 2026-10-19
 */
@TableName("un_work_daily_field")
public class UnWorkDailyField implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 工作日志字段配置主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 租户内稳定字段编码
     */
    @TableField("field_key")
    private String fieldKey;

    /**
     * 字段显示名称
     */
    @TableField("field_name")
    private String fieldName;

    /**
     * 字段值类型
     */
    @TableField("field_type")
    private String fieldType;

    /**
     * 提交日志时是否必填
     */
    @TableField("required_flag")
    private Byte requiredFlag;

    /**
     * 选项型字段的可选值
     */
    @TableField("option_data")
    private String optionData;

    /**
     * 字段显示顺序
     */
    @TableField("sort_no")
    private Integer sortNo;

    /**
     * 字段状态：1启用，0停用
     */
    @TableField("status")
    private Byte status;

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

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public String getFieldKey() {
        return fieldKey;
    }

    public void setFieldKey(String fieldKey) {
        this.fieldKey = fieldKey;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getFieldType() {
        return fieldType;
    }

    public void setFieldType(String fieldType) {
        this.fieldType = fieldType;
    }

    public Byte getRequiredFlag() {
        return requiredFlag;
    }

    public void setRequiredFlag(Byte requiredFlag) {
        this.requiredFlag = requiredFlag;
    }

    public String getOptionData() {
        return optionData;
    }

    public void setOptionData(String optionData) {
        this.optionData = optionData;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }

    public Byte getStatus() {
        return status;
    }

    public void setStatus(Byte status) {
        this.status = status;
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
