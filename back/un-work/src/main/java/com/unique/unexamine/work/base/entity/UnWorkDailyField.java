package com.unique.unexamine.work.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 工作日志字段配置
 * </p>
 *
 * @author Template Base
 * @since 2026-10-22
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
     * 平台或系统字段配置作用域
     */
    @TableField("scope_type")
    private String scopeType;

    /**
     * 系统字段所属业务系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 系统字段所属租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 作用域内稳定字段编码
     */
    @TableField("field_code")
    private String fieldCode;

    /**
     * 工作日志字段展示名称
     */
    @TableField("field_name")
    private String fieldName;

    /**
     * 复用公共固定字段类型
     */
    @TableField("field_type")
    private String fieldType;

    /**
     * 草稿校验权限布局和字典配置
     */
    @TableField("config_json")
    private String configJson;

    /**
     * 已发布字段运行配置快照
     */
    @TableField("published_config_json")
    private String publishedConfigJson;

    /**
     * 字段显示顺序
     */
    @TableField("sort_no")
    private Integer sortNo;

    /**
     * 草稿发布或停用状态
     */
    @TableField("status")
    private String status;

    /**
     * 已发布工作日志字段版本
     */
    @TableField("published_version")
    private Integer publishedVersion;

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

    public String getScopeType() {
        return scopeType;
    }

    public void setScopeType(String scopeType) {
        this.scopeType = scopeType;
    }

    public Long getSystemId() {
        return systemId;
    }

    public void setSystemId(Long systemId) {
        this.systemId = systemId;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public String getFieldCode() {
        return fieldCode;
    }

    public void setFieldCode(String fieldCode) {
        this.fieldCode = fieldCode;
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

    public String getConfigJson() {
        return configJson;
    }

    public void setConfigJson(String configJson) {
        this.configJson = configJson;
    }

    public String getPublishedConfigJson() {
        return publishedConfigJson;
    }

    public void setPublishedConfigJson(String publishedConfigJson) {
        this.publishedConfigJson = publishedConfigJson;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getPublishedVersion() {
        return publishedVersion;
    }

    public void setPublishedVersion(Integer publishedVersion) {
        this.publishedVersion = publishedVersion;
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
