package com.unique.unexamine.moduleconfig.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("cfg_tenant_extension_version")
public class CfgTenantExtensionVersion {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("extension_id")
    private Long extensionId;

    @TableField("version_number")
    private Integer versionNumber;

    @TableField("base_module_version_id")
    private Long baseModuleVersionId;

    @TableField("snapshot_json")
    private String snapshotJson;

    @TableField("schema_hash")
    private String schemaHash;

    @TableField("published_by_member_id")
    private Long publishedByMemberId;

    @TableField("published_at")
    private LocalDateTime publishedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getExtensionId() {
        return extensionId;
    }

    public void setExtensionId(Long extensionId) {
        this.extensionId = extensionId;
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

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    @Override
    public String toString() {
        return "CfgTenantExtensionVersion{" +
            "id = " + id +
            ", extensionId = " + extensionId +
            ", versionNumber = " + versionNumber +
            ", baseModuleVersionId = " + baseModuleVersionId +
            ", snapshotJson = " + snapshotJson +
            ", schemaHash = " + schemaHash +
            ", publishedByMemberId = " + publishedByMemberId +
            ", publishedAt = " + publishedAt +
            "}";
    }
}
