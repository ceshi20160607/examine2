package com.unique.unexamine.runtimedata.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 业务记录评论与同记录回复
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_biz_record_comment")
public class BizRecordComment {

    /**
     * 业务评论主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 业务记录所属系统
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 业务记录的数据归属租户
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 业务记录所属模块
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 被评论的业务记录主键
     */
    @TableField("record_id")
    private Long recordId;

    /**
     * 同一记录内被回复的评论主键
     */
    @TableField("parent_comment_id")
    private Long parentCommentId;

    /**
     * 评论正文
     */
    @TableField("body")
    private String body;

    /**
     * 发表评论的系统成员主键
     */
    @TableField("created_by_member_id")
    private Long createdByMemberId;

    /**
     * 评论发表时间
     */
    @TableField("created_time")
    private LocalDateTime createdTime;

    /**
     * 评论最近修改时间
     */
    @TableField("updated_time")
    private LocalDateTime updatedTime;

    /**
     * 评论逻辑删除标记
     */
    @TableField("deleted")
    private Boolean deleted;

    /**
     * 评论逻辑删除时间
     */
    @TableField("deleted_time")
    private LocalDateTime deletedTime;

    /**
     * 评论乐观锁版本
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

    public Long getRecordId() {
        return recordId;
    }

    public void setRecordId(Long recordId) {
        this.recordId = recordId;
    }

    public Long getParentCommentId() {
        return parentCommentId;
    }

    public void setParentCommentId(Long parentCommentId) {
        this.parentCommentId = parentCommentId;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public Long getCreatedByMemberId() {
        return createdByMemberId;
    }

    public void setCreatedByMemberId(Long createdByMemberId) {
        this.createdByMemberId = createdByMemberId;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(LocalDateTime createdTime) {
        this.createdTime = createdTime;
    }

    public LocalDateTime getUpdatedTime() {
        return updatedTime;
    }

    public void setUpdatedTime(LocalDateTime updatedTime) {
        this.updatedTime = updatedTime;
    }

    public Boolean getDeleted() {
        return deleted;
    }

    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
    }

    public LocalDateTime getDeletedTime() {
        return deletedTime;
    }

    public void setDeletedTime(LocalDateTime deletedTime) {
        this.deletedTime = deletedTime;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "BizRecordComment{" +
            "id = " + id +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", moduleId = " + moduleId +
            ", recordId = " + recordId +
            ", parentCommentId = " + parentCommentId +
            ", body = " + body +
            ", createdByMemberId = " + createdByMemberId +
            ", createdTime = " + createdTime +
            ", updatedTime = " + updatedTime +
            ", deleted = " + deleted +
            ", deletedTime = " + deletedTime +
            ", version = " + version +
            "}";
    }
}
