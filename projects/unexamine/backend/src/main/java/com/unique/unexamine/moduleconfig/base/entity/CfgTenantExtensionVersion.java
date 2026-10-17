package com.unique.unexamine.moduleconfig.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 不可变的租户扩展发布版本
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_cfg_tenant_extension_version")
public class CfgTenantExtensionVersion {

    /**
     * 主键标识
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属系统标识
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 所属租户扩展标识
     */
    @TableField("extension_id")
    private Long extensionId;

    /**
     * 扩展的基础模块标识
     */
    @TableField("base_module_id")
    private Long baseModuleId;

    /**
     * 发布版本序号
     */
    @TableField("version_number")
    private Integer versionNumber;

    /**
     * 扩展所基于的模块发布版本标识
     */
    @TableField("base_module_version_id")
    private Long baseModuleVersionId;

    /**
     * 发布时冻结的完整配置快照
     */
    @TableField("snapshot_json")
    private String snapshotJson;

    /**
     * 发布配置内容摘要
     */
    @TableField("schema_hash")
    private String schemaHash;

    /**
     * 发布系统成员标识
     */
    @TableField("published_by_member_id")
    private Long publishedByMemberId;

    /**
     * 发布时间
     */
    @TableField("publish_time")
    private LocalDateTime publishTime;

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

    public Long getExtensionId() {
        return extensionId;
    }

    public void setExtensionId(Long extensionId) {
        this.extensionId = extensionId;
    }

    public Long getBaseModuleId() {
        return baseModuleId;
    }

    public void setBaseModuleId(Long baseModuleId) {
        this.baseModuleId = baseModuleId;
    }

    public Integer getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(Integer versionNumber) {
        this.versionNumber = versionNumber;
    }

    public Long getBaseModuleVersionId() {
        return baseModuleVersionId;
    }

    public void setBaseModuleVersionId(Long baseModuleVersionId) {
        this.baseModuleVersionId = baseModuleVersionId;
    }

    public String getSnapshotJson() {
        return snapshotJson;
    }

    public void setSnapshotJson(String snapshotJson) {
        this.snapshotJson = snapshotJson;
    }

    public String getSchemaHash() {
        return schemaHash;
    }

    public void setSchemaHash(String schemaHash) {
        this.schemaHash = schemaHash;
    }

    public Long getPublishedByMemberId() {
        return publishedByMemberId;
    }

    public void setPublishedByMemberId(Long publishedByMemberId) {
        this.publishedByMemberId = publishedByMemberId;
    }

    public LocalDateTime getPublishTime() {
        return publishTime;
    }

    public void setPublishTime(LocalDateTime publishTime) {
        this.publishTime = publishTime;
    }

    @Override
    public String toString() {
        return "CfgTenantExtensionVersion{" +
            "id = " + id +
            ", systemId = " + systemId +
            ", extensionId = " + extensionId +
            ", baseModuleId = " + baseModuleId +
            ", versionNumber = " + versionNumber +
            ", baseModuleVersionId = " + baseModuleVersionId +
            ", snapshotJson = " + snapshotJson +
            ", schemaHash = " + schemaHash +
            ", publishedByMemberId = " + publishedByMemberId +
            ", publishTime = " + publishTime +
            "}";
    }
}
