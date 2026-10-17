package com.unique.unexamine.moduleconfig.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 业务模块草稿与当前发布指针
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_module")
public class BusinessModule {

    /**
     * 业务模块主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 模块所属业务系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 模块所在模块组主键
     */
    @TableField("group_id")
    private Long groupId;

    /**
     * 系统内业务模块编码
     */
    @TableField("`code`")
    private String code;

    /**
     * 业务模块显示名称
     */
    @TableField("`name`")
    private String name;

    /**
     * 模块业务用途说明
     */
    @TableField("`description`")
    private String description;

    /**
     * 模块草稿、发布或停用状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 当前草稿修订号
     */
    @TableField("draft_revision")
    private Integer draftRevision;

    /**
     * 当前生效的发布快照主键
     */
    @TableField("published_version_id")
    private Long publishedVersionId;

    /**
     * 模块创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 模块更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 模块乐观锁版本
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

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getDraftRevision() {
        return draftRevision;
    }

    public void setDraftRevision(Integer draftRevision) {
        this.draftRevision = draftRevision;
    }

    public Long getPublishedVersionId() {
        return publishedVersionId;
    }

    public void setPublishedVersionId(Long publishedVersionId) {
        this.publishedVersionId = publishedVersionId;
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
        return "BusinessModule{" +
            "id = " + id +
            ", systemId = " + systemId +
            ", groupId = " + groupId +
            ", code = " + code +
            ", name = " + name +
            ", description = " + description +
            ", status = " + status +
            ", draftRevision = " + draftRevision +
            ", publishedVersionId = " + publishedVersionId +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
