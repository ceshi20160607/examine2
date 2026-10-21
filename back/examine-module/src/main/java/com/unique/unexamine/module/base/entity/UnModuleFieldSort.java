package com.unique.unexamine.module.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 模块字段排序
 * </p>
 *
 * @author Template Base
 * @since 2026-10-21
 */
@TableName("un_module_field_sort")
public class UnModuleFieldSort implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 个人列表列配置主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属业务模块主键
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 用户当前租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 配置该列表的平台账号主键
     */
    @TableField("account_id")
    private Long accountId;

    /**
     * 对应模块字段主键
     */
    @TableField("field_id")
    private Long fieldId;

    /**
     * 个人列表列顺序
     */
    @TableField("sort_no")
    private Integer sortNo;

    /**
     * 个人列表列宽像素
     */
    @TableField("width_px")
    private Integer widthPx;

    /**
     * 个人列表列隐藏标记
     */
    @TableField("hidden_flag")
    private Boolean hiddenFlag;

    /**
     * 列固定在左侧或右侧
     */
    @TableField("fixed_side")
    private String fixedSide;

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

    public Long getModuleId() {
        return moduleId;
    }

    public void setModuleId(Long moduleId) {
        this.moduleId = moduleId;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public Long getFieldId() {
        return fieldId;
    }

    public void setFieldId(Long fieldId) {
        this.fieldId = fieldId;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }

    public Integer getWidthPx() {
        return widthPx;
    }

    public void setWidthPx(Integer widthPx) {
        this.widthPx = widthPx;
    }

    public Boolean getHiddenFlag() {
        return hiddenFlag;
    }

    public void setHiddenFlag(Boolean hiddenFlag) {
        this.hiddenFlag = hiddenFlag;
    }

    public String getFixedSide() {
        return fixedSide;
    }

    public void setFixedSide(String fixedSide) {
        this.fixedSide = fixedSide;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
