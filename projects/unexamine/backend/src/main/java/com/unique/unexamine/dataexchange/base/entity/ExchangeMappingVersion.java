package com.unique.unexamine.dataexchange.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 不可变的模块导入导出映射发布版本
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_exchange_mapping_version")
public class ExchangeMappingVersion {

    /**
     * 映射不可变发布版本主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 映射所属租户主键，用于校验发布人
     */
    @TableField("owner_tenant_id")
    private Long ownerTenantId;

    /**
     * 映射所属模块主键
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 来源映射草稿主键
     */
    @TableField("mapping_id")
    private Long mappingId;

    /**
     * 发布时引用的不可变模块配置版本
     */
    @TableField("module_config_version_id")
    private Long moduleConfigVersionId;

    /**
     * 同一映射内递增的发布版本号
     */
    @TableField("version_number")
    private Integer versionNumber;

    /**
     * 发布所依据的草稿修订号
     */
    @TableField("draft_revision")
    private Integer draftRevision;

    /**
     * 映射快照摘要
     */
    @TableField("snapshot_hash")
    private String snapshotHash;

    /**
     * 字段映射、校验和冲突策略的不可变快照
     */
    @TableField("snapshot_json")
    private String snapshotJson;

    /**
     * 发布映射的系统成员主键
     */
    @TableField("published_by_member_id")
    private Long publishedByMemberId;

    /**
     * 版本发布时间
     */
    @TableField("publish_time")
    private LocalDateTime publishTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Long getMappingId() {
        return mappingId;
    }

    public void setMappingId(Long mappingId) {
        this.mappingId = mappingId;
    }

    public Long getModuleConfigVersionId() {
        return moduleConfigVersionId;
    }

    public void setModuleConfigVersionId(Long moduleConfigVersionId) {
        this.moduleConfigVersionId = moduleConfigVersionId;
    }

    public Integer getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(Integer versionNumber) {
        this.versionNumber = versionNumber;
    }

    public Integer getDraftRevision() {
        return draftRevision;
    }

    public void setDraftRevision(Integer draftRevision) {
        this.draftRevision = draftRevision;
    }

    public String getSnapshotHash() {
        return snapshotHash;
    }

    public void setSnapshotHash(String snapshotHash) {
        this.snapshotHash = snapshotHash;
    }

    public String getSnapshotJson() {
        return snapshotJson;
    }

    public void setSnapshotJson(String snapshotJson) {
        this.snapshotJson = snapshotJson;
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
        return "ExchangeMappingVersion{" +
            "id = " + id +
            ", ownerTenantId = " + ownerTenantId +
            ", moduleId = " + moduleId +
            ", mappingId = " + mappingId +
            ", moduleConfigVersionId = " + moduleConfigVersionId +
            ", versionNumber = " + versionNumber +
            ", draftRevision = " + draftRevision +
            ", snapshotHash = " + snapshotHash +
            ", snapshotJson = " + snapshotJson +
            ", publishedByMemberId = " + publishedByMemberId +
            ", publishTime = " + publishTime +
            "}";
    }
}
