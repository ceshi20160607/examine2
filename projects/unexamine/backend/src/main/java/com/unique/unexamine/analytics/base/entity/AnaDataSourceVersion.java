package com.unique.unexamine.analytics.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 不可变数据源发布版本
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_ana_data_source_version")
public class AnaDataSourceVersion {

    /**
     * 数据源发布版本主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属数据源主键
     */
    @TableField("data_source_id")
    private Long dataSourceId;

    /**
     * 发布时固定的归属层级
     */
    @TableField("scope_type")
    private String scopeType;

    /**
     * 发布时固定的归属主体主键
     */
    @TableField("scope_owner_id")
    private Long scopeOwnerId;

    /**
     * 递增发布版本号
     */
    @TableField("version_number")
    private Integer versionNumber;

    /**
     * 发布时采用的草稿修订号
     */
    @TableField("draft_revision")
    private Integer draftRevision;

    /**
     * 数据源发布快照的 SHA-256 摘要
     */
    @TableField("definition_hash")
    private String definitionHash;

    /**
     * 不可变的数据源定义、输出字段与权限口径快照
     */
    @TableField("snapshot_json")
    private String snapshotJson;

    /**
     * 发布者账号主键
     */
    @TableField("published_by_account_id")
    private Long publishedByAccountId;

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

    public Long getDataSourceId() {
        return dataSourceId;
    }

    public void setDataSourceId(Long dataSourceId) {
        this.dataSourceId = dataSourceId;
    }

    public String getScopeType() {
        return scopeType;
    }

    public void setScopeType(String scopeType) {
        this.scopeType = scopeType;
    }

    public Long getScopeOwnerId() {
        return scopeOwnerId;
    }

    public void setScopeOwnerId(Long scopeOwnerId) {
        this.scopeOwnerId = scopeOwnerId;
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

    public String getDefinitionHash() {
        return definitionHash;
    }

    public void setDefinitionHash(String definitionHash) {
        this.definitionHash = definitionHash;
    }

    public String getSnapshotJson() {
        return snapshotJson;
    }

    public void setSnapshotJson(String snapshotJson) {
        this.snapshotJson = snapshotJson;
    }

    public Long getPublishedByAccountId() {
        return publishedByAccountId;
    }

    public void setPublishedByAccountId(Long publishedByAccountId) {
        this.publishedByAccountId = publishedByAccountId;
    }

    public LocalDateTime getPublishTime() {
        return publishTime;
    }

    public void setPublishTime(LocalDateTime publishTime) {
        this.publishTime = publishTime;
    }

    @Override
    public String toString() {
        return "AnaDataSourceVersion{" +
            "id = " + id +
            ", dataSourceId = " + dataSourceId +
            ", scopeType = " + scopeType +
            ", scopeOwnerId = " + scopeOwnerId +
            ", versionNumber = " + versionNumber +
            ", draftRevision = " + draftRevision +
            ", definitionHash = " + definitionHash +
            ", snapshotJson = " + snapshotJson +
            ", publishedByAccountId = " + publishedByAccountId +
            ", publishTime = " + publishTime +
            "}";
    }
}
