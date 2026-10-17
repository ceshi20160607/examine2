package com.unique.unexamine.runtimedata.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 自定义模块的通用业务主记录
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_biz_record")
public class BusinessRecord {

    /**
     * 本表主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 业务数据所属系统
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 业务数据归属租户
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 业务记录所属模块主键
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 创建业务记录时固定的已发布模块版本
     */
    @TableField("created_config_version_id")
    private Long createdConfigVersionId;

    /**
     * 最近修改业务记录时固定的已发布模块版本
     */
    @TableField("updated_config_version_id")
    private Long updatedConfigVersionId;

    /**
     * 模块内可展示的业务编号
     */
    @TableField("record_number")
    private String recordNumber;

    /**
     * 业务记录标题
     */
    @TableField("title")
    private String title;

    /**
     * 当前业务状态或处理状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 当前业务记录负责人主键，缺失时由系统当前所属人补齐
     */
    @TableField("owner_member_id")
    private Long ownerMemberId;

    /**
     * 业务记录所属部门主键
     */
    @TableField("department_id")
    private Long departmentId;

    /**
     * 创建业务数据的系统成员主键
     */
    @TableField("created_by_member_id")
    private Long createdByMemberId;

    /**
     * 最近修改业务记录的系统成员主键
     */
    @TableField("updated_by_member_id")
    private Long updatedByMemberId;

    /**
     * 业务记录归档标记
     */
    @TableField("archived")
    private Boolean archived;

    /**
     * 业务记录归档时间
     */
    @TableField("archive_time")
    private LocalDateTime archiveTime;

    /**
     * 逻辑删除标记
     */
    @TableField("deleted")
    private Boolean deleted;

    /**
     * 逻辑删除时间
     */
    @TableField("delete_time")
    private LocalDateTime deleteTime;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 最近修改时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 乐观锁版本
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

    public Long getCreatedConfigVersionId() {
        return createdConfigVersionId;
    }

    public void setCreatedConfigVersionId(Long createdConfigVersionId) {
        this.createdConfigVersionId = createdConfigVersionId;
    }

    public Long getUpdatedConfigVersionId() {
        return updatedConfigVersionId;
    }

    public void setUpdatedConfigVersionId(Long updatedConfigVersionId) {
        this.updatedConfigVersionId = updatedConfigVersionId;
    }

    public String getRecordNumber() {
        return recordNumber;
    }

    public void setRecordNumber(String recordNumber) {
        this.recordNumber = recordNumber;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getOwnerMemberId() {
        return ownerMemberId;
    }

    public void setOwnerMemberId(Long ownerMemberId) {
        this.ownerMemberId = ownerMemberId;
    }

    public Long getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Long departmentId) {
        this.departmentId = departmentId;
    }

    public Long getCreatedByMemberId() {
        return createdByMemberId;
    }

    public void setCreatedByMemberId(Long createdByMemberId) {
        this.createdByMemberId = createdByMemberId;
    }

    public Long getUpdatedByMemberId() {
        return updatedByMemberId;
    }

    public void setUpdatedByMemberId(Long updatedByMemberId) {
        this.updatedByMemberId = updatedByMemberId;
    }

    public Boolean getArchived() {
        return archived;
    }

    public void setArchived(Boolean archived) {
        this.archived = archived;
    }

    public LocalDateTime getArchiveTime() {
        return archiveTime;
    }

    public void setArchiveTime(LocalDateTime archiveTime) {
        this.archiveTime = archiveTime;
    }

    public Boolean getDeleted() {
        return deleted;
    }

    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
    }

    public LocalDateTime getDeleteTime() {
        return deleteTime;
    }

    public void setDeleteTime(LocalDateTime deleteTime) {
        this.deleteTime = deleteTime;
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

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "BusinessRecord{" +
            "id = " + id +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", moduleId = " + moduleId +
            ", createdConfigVersionId = " + createdConfigVersionId +
            ", updatedConfigVersionId = " + updatedConfigVersionId +
            ", recordNumber = " + recordNumber +
            ", title = " + title +
            ", status = " + status +
            ", ownerMemberId = " + ownerMemberId +
            ", departmentId = " + departmentId +
            ", createdByMemberId = " + createdByMemberId +
            ", updatedByMemberId = " + updatedByMemberId +
            ", archived = " + archived +
            ", archiveTime = " + archiveTime +
            ", deleted = " + deleted +
            ", deleteTime = " + deleteTime +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
