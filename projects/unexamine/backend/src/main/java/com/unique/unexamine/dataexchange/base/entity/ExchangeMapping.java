package com.unique.unexamine.dataexchange.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 模块数据导入导出字段映射草稿
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_exchange_mapping")
public class ExchangeMapping {

    /**
     * 模块导入导出映射草稿主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 映射配置所属租户主键
     */
    @TableField("owner_tenant_id")
    private Long ownerTenantId;

    /**
     * 映射所属模块主键
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 模块内映射编码
     */
    @TableField("`code`")
    private String code;

    /**
     * 映射名称
     */
    @TableField("`name`")
    private String name;

    /**
     * 映射类型，IMPORT 或 EXPORT
     */
    @TableField("mapping_type")
    private String mappingType;

    /**
     * 草稿修订号，发布时据此检查并发修改
     */
    @TableField("draft_revision")
    private Integer draftRevision;

    /**
     * 草稿中的外部列与模块字段映射
     */
    @TableField("column_mapping_json")
    private String columnMappingJson;

    /**
     * 草稿中的导入校验及预检策略
     */
    @TableField("validation_policy_json")
    private String validationPolicyJson;

    /**
     * 草稿中的唯一字段匹配和冲突策略
     */
    @TableField("conflict_policy_json")
    private String conflictPolicyJson;

    /**
     * 草稿、已发布或停用等配置状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 创建映射的系统成员主键
     */
    @TableField("created_by_member_id")
    private Long createdByMemberId;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 修改时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 草稿乐观锁版本
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

    public Long getOwnerTenantId() {
        return ownerTenantId;
    }

    public void setOwnerTenantId(Long ownerTenantId) {
        this.ownerTenantId = ownerTenantId;
    }

    public Long getModuleId() {
        return moduleId;
    }

    public void setModuleId(Long moduleId) {
        this.moduleId = moduleId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMappingType() {
        return mappingType;
    }

    public void setMappingType(String mappingType) {
        this.mappingType = mappingType;
    }

    public Integer getDraftRevision() {
        return draftRevision;
    }

    public void setDraftRevision(Integer draftRevision) {
        this.draftRevision = draftRevision;
    }

    public String getColumnMappingJson() {
        return columnMappingJson;
    }

    public void setColumnMappingJson(String columnMappingJson) {
        this.columnMappingJson = columnMappingJson;
    }

    public String getValidationPolicyJson() {
        return validationPolicyJson;
    }

    public void setValidationPolicyJson(String validationPolicyJson) {
        this.validationPolicyJson = validationPolicyJson;
    }

    public String getConflictPolicyJson() {
        return conflictPolicyJson;
    }

    public void setConflictPolicyJson(String conflictPolicyJson) {
        this.conflictPolicyJson = conflictPolicyJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getCreatedByMemberId() {
        return createdByMemberId;
    }

    public void setCreatedByMemberId(Long createdByMemberId) {
        this.createdByMemberId = createdByMemberId;
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
        return "ExchangeMapping{" +
            "id = " + id +
            ", systemId = " + systemId +
            ", ownerTenantId = " + ownerTenantId +
            ", moduleId = " + moduleId +
            ", code = " + code +
            ", name = " + name +
            ", mappingType = " + mappingType +
            ", draftRevision = " + draftRevision +
            ", columnMappingJson = " + columnMappingJson +
            ", validationPolicyJson = " + validationPolicyJson +
            ", conflictPolicyJson = " + conflictPolicyJson +
            ", status = " + status +
            ", createdByMemberId = " + createdByMemberId +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
