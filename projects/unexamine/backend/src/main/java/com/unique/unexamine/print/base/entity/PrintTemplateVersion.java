package com.unique.unexamine.print.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 不可变打印模板发布版本与模块配置依赖
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_print_template_version")
public class PrintTemplateVersion {

    /**
     * 不可变打印模板版本主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 模板配置所属租户主键，用于校验发布人
     */
    @TableField("owner_tenant_id")
    private Long ownerTenantId;

    /**
     * 模板绑定的业务模块主键
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 来源模板草稿主键
     */
    @TableField("template_id")
    private Long templateId;

    /**
     * 发布时固定的模块配置版本
     */
    @TableField("module_config_version_id")
    private Long moduleConfigVersionId;

    /**
     * 同一模板内递增的发布版本号
     */
    @TableField("version_number")
    private Integer versionNumber;

    /**
     * 发布所依据的草稿修订号
     */
    @TableField("draft_revision")
    private Integer draftRevision;

    /**
     * 模板与字段引用快照摘要
     */
    @TableField("snapshot_hash")
    private String snapshotHash;

    /**
     * 页眉、页脚、明细和签章等不可变布局快照
     */
    @TableField("snapshot_json")
    private String snapshotJson;

    /**
     * 发布模板的系统成员主键
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

    public Long getTemplateId() {
        return templateId;
    }

    public void setTemplateId(Long templateId) {
        this.templateId = templateId;
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
        return "PrintTemplateVersion{" +
            "id = " + id +
            ", ownerTenantId = " + ownerTenantId +
            ", moduleId = " + moduleId +
            ", templateId = " + templateId +
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
