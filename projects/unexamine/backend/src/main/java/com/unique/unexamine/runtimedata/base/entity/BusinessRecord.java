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
@TableName("un_module_record")
public class BusinessRecord {

    /**
     * 通用业务记录主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 记录所属业务系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 记录所属租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 记录所属业务模块主键
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 记录创建时生效的模块发布版本主键
     */
    @TableField("module_version_id")
    private Long moduleVersionId;

    /**
     * 记录当前负责人系统成员主键
     */
    @TableField("owner_member_id")
    private Long ownerMemberId;

    /**
     * 业务记录在列表与详情中显示的标题
     */
    @TableField("title")
    private String title;

    /**
     * 业务记录在租户和模块内唯一的稳定编号
     */
    @TableField("record_no")
    private String recordNo;

    /**
     * 记录业务状态
     */
    @TableField("record_status")
    private String recordStatus;

    /**
     * 创建记录的平台账号主键
     */
    @TableField("created_by_account_id")
    private Long createdByAccountId;

    /**
     * 最近修改记录的平台账号主键
     */
    @TableField("updated_by_account_id")
    private Long updatedByAccountId;

    /**
     * 记录创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 记录更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 记录乐观锁版本
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

    public Long getModuleVersionId() {
        return moduleVersionId;
    }

    public void setModuleVersionId(Long moduleVersionId) {
        this.moduleVersionId = moduleVersionId;
    }

    public Long getOwnerMemberId() {
        return ownerMemberId;
    }

    public void setOwnerMemberId(Long ownerMemberId) {
        this.ownerMemberId = ownerMemberId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getRecordNo() {
        return recordNo;
    }

    public void setRecordNo(String recordNo) {
        this.recordNo = recordNo;
    }

    public String getRecordStatus() {
        return recordStatus;
    }

    public void setRecordStatus(String recordStatus) {
        this.recordStatus = recordStatus;
    }

    public Long getCreatedByAccountId() {
        return createdByAccountId;
    }

    public void setCreatedByAccountId(Long createdByAccountId) {
        this.createdByAccountId = createdByAccountId;
    }

    public Long getUpdatedByAccountId() {
        return updatedByAccountId;
    }

    public void setUpdatedByAccountId(Long updatedByAccountId) {
        this.updatedByAccountId = updatedByAccountId;
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
            ", moduleVersionId = " + moduleVersionId +
            ", ownerMemberId = " + ownerMemberId +
            ", title = " + title +
            ", recordNo = " + recordNo +
            ", recordStatus = " + recordStatus +
            ", createdByAccountId = " + createdByAccountId +
            ", updatedByAccountId = " + updatedByAccountId +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
