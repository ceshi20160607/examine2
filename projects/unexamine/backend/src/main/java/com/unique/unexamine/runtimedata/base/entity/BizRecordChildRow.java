package com.unique.unexamine.runtimedata.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 通用业务记录的子表明细行
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_biz_record_child_row")
public class BizRecordChildRow {

    /**
     * 通用子表行主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 主记录所属系统
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 主记录的数据归属租户
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 主记录所属模块
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 主业务记录主键
     */
    @TableField("record_id")
    private Long recordId;

    /**
     * 定义此子表的主模块字段主键
     */
    @TableField("child_field_id")
    private Long childFieldId;

    /**
     * 同一记录和子表内稳定的行标识
     */
    @TableField("row_key")
    private String rowKey;

    /**
     * 子表行显示顺序
     */
    @TableField("sort_order")
    private Integer sortOrder;

    /**
     * 行逻辑删除标记
     */
    @TableField("deleted")
    private Boolean deleted;

    /**
     * 行创建时间
     */
    @TableField("created_time")
    private LocalDateTime createdTime;

    /**
     * 行最近修改时间
     */
    @TableField("updated_time")
    private LocalDateTime updatedTime;

    /**
     * 行乐观锁版本
     */
    @Version
    @TableField("version")
    private Integer version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Long getModuleId() {
        return moduleId;
    }

    public void setModuleId(Long moduleId) {
        this.moduleId = moduleId;
    }

    public Long getRecordId() {
        return recordId;
    }

    public void setRecordId(Long recordId) {
        this.recordId = recordId;
    }

    public Long getChildFieldId() {
        return childFieldId;
    }

    public void setChildFieldId(Long childFieldId) {
        this.childFieldId = childFieldId;
    }

    public String getRowKey() {
        return rowKey;
    }

    public void setRowKey(String rowKey) {
        this.rowKey = rowKey;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Boolean getDeleted() {
        return deleted;
    }

    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(LocalDateTime createdTime) {
        this.createdTime = createdTime;
    }

    public LocalDateTime getUpdatedTime() {
        return updatedTime;
    }

    public void setUpdatedTime(LocalDateTime updatedTime) {
        this.updatedTime = updatedTime;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "BizRecordChildRow{" +
            "id = " + id +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", moduleId = " + moduleId +
            ", recordId = " + recordId +
            ", childFieldId = " + childFieldId +
            ", rowKey = " + rowKey +
            ", sortOrder = " + sortOrder +
            ", deleted = " + deleted +
            ", createdTime = " + createdTime +
            ", updatedTime = " + updatedTime +
            ", version = " + version +
            "}";
    }
}
