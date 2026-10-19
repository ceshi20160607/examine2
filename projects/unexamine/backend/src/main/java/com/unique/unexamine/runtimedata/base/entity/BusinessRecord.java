package com.unique.unexamine.runtimedata.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 用户自定义模块通用业务主记录
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_biz_record")
public class BusinessRecord {

    /**
     * 通用业务记录标识
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 记录所属业务系统标识
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 记录原始所属租户标识
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 记录所属业务模块标识
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 创建或最近验证时使用的模块发布版本号
     */
    @TableField("module_version_no")
    private Integer moduleVersionNo;

    /**
     * 模块租户内唯一业务编号
     */
    @TableField("record_no")
    private String recordNo;

    /**
     * 业务记录标题
     */
    @TableField("title")
    private String title;

    /**
     * 业务状态编码
     */
    @TableField("business_status")
    private String businessStatus;

    /**
     * 当前负责人系统成员标识
     */
    @TableField("owner_member_id")
    private Long ownerMemberId;

    /**
     * 当前负责部门标识，组织启用后使用
     */
    @TableField("owner_department_id")
    private Long ownerDepartmentId;

    /**
     * 相关人员系统成员标识的 JSON 文本
     */
    @TableField("related_member_ids_json")
    private String relatedMemberIdsJson;

    /**
     * 创建人系统成员标识
     */
    @TableField("created_by_member_id")
    private Long createdByMemberId;

    /**
     * 最近修改人系统成员标识
     */
    @TableField("updated_by_member_id")
    private Long updatedByMemberId;

    /**
     * 是否归档：1是，0否
     */
    @TableField("archived_flag")
    private Boolean archivedFlag;

    /**
     * 是否逻辑删除：1是，0否
     */
    @TableField("deleted_flag")
    private Boolean deletedFlag;

    /**
     * 乐观锁版本号
     */
    @TableField("version_no")
    private Long versionNo;

    /**
     * 业务记录创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 业务记录修改时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

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

    public Integer getModuleVersionNo() {
        return moduleVersionNo;
    }

    public void setModuleVersionNo(Integer moduleVersionNo) {
        this.moduleVersionNo = moduleVersionNo;
    }

    public String getRecordNo() {
        return recordNo;
    }

    public void setRecordNo(String recordNo) {
        this.recordNo = recordNo;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBusinessStatus() {
        return businessStatus;
    }

    public void setBusinessStatus(String businessStatus) {
        this.businessStatus = businessStatus;
    }

    public Long getOwnerMemberId() {
        return ownerMemberId;
    }

    public void setOwnerMemberId(Long ownerMemberId) {
        this.ownerMemberId = ownerMemberId;
    }

    public Long getOwnerDepartmentId() {
        return ownerDepartmentId;
    }

    public void setOwnerDepartmentId(Long ownerDepartmentId) {
        this.ownerDepartmentId = ownerDepartmentId;
    }

    public String getRelatedMemberIdsJson() {
        return relatedMemberIdsJson;
    }

    public void setRelatedMemberIdsJson(String relatedMemberIdsJson) {
        this.relatedMemberIdsJson = relatedMemberIdsJson;
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

    public Boolean getArchivedFlag() {
        return archivedFlag;
    }

    public void setArchivedFlag(Boolean archivedFlag) {
        this.archivedFlag = archivedFlag;
    }

    public Boolean getDeletedFlag() {
        return deletedFlag;
    }

    public void setDeletedFlag(Boolean deletedFlag) {
        this.deletedFlag = deletedFlag;
    }

    public Long getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(Long versionNo) {
        this.versionNo = versionNo;
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
        return "BusinessRecord{" +
            "id = " + id +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", moduleId = " + moduleId +
            ", moduleVersionNo = " + moduleVersionNo +
            ", recordNo = " + recordNo +
            ", title = " + title +
            ", businessStatus = " + businessStatus +
            ", ownerMemberId = " + ownerMemberId +
            ", ownerDepartmentId = " + ownerDepartmentId +
            ", relatedMemberIdsJson = " + relatedMemberIdsJson +
            ", createdByMemberId = " + createdByMemberId +
            ", updatedByMemberId = " + updatedByMemberId +
            ", archivedFlag = " + archivedFlag +
            ", deletedFlag = " + deletedFlag +
            ", versionNo = " + versionNo +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            "}";
    }
}
