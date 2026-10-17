package com.unique.unexamine.notification.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 平台消息模板草稿
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_plat_msg_template")
public class PlatMsgTemplate {

    /**
     * 平台消息模板主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 模板所属平台主键
     */
    @TableField("platform_id")
    private Long platformId;

    /**
     * 平台范围内的模板编码
     */
    @TableField("`code`")
    private String code;

    /**
     * 模板显示名称
     */
    @TableField("`name`")
    private String name;

    /**
     * 通知发送渠道
     */
    @TableField("`channel`")
    private String channel;

    /**
     * 当前草稿修订号
     */
    @TableField("draft_revision")
    private Integer draftRevision;

    /**
     * 消息主题模板
     */
    @TableField("subject_template")
    private String subjectTemplate;

    /**
     * 消息正文模板
     */
    @TableField("content_template")
    private String contentTemplate;

    /**
     * 模板变量定义
     */
    @TableField("variable_schema_json")
    private String variableSchemaJson;

    /**
     * 模板草稿或发布状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 创建模板的平台成员账号
     */
    @TableField("created_by_account_id")
    private Long createdByAccountId;

    /**
     * 模板创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 模板更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 乐观锁版本号
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

    public Long getPlatformId() {
        return platformId;
    }

    public void setPlatformId(Long platformId) {
        this.platformId = platformId;
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

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public Integer getDraftRevision() {
        return draftRevision;
    }

    public void setDraftRevision(Integer draftRevision) {
        this.draftRevision = draftRevision;
    }

    public String getSubjectTemplate() {
        return subjectTemplate;
    }

    public void setSubjectTemplate(String subjectTemplate) {
        this.subjectTemplate = subjectTemplate;
    }

    public String getContentTemplate() {
        return contentTemplate;
    }

    public void setContentTemplate(String contentTemplate) {
        this.contentTemplate = contentTemplate;
    }

    public String getVariableSchemaJson() {
        return variableSchemaJson;
    }

    public void setVariableSchemaJson(String variableSchemaJson) {
        this.variableSchemaJson = variableSchemaJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getCreatedByAccountId() {
        return createdByAccountId;
    }

    public void setCreatedByAccountId(Long createdByAccountId) {
        this.createdByAccountId = createdByAccountId;
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
        return "PlatMsgTemplate{" +
            "id = " + id +
            ", platformId = " + platformId +
            ", code = " + code +
            ", name = " + name +
            ", channel = " + channel +
            ", draftRevision = " + draftRevision +
            ", subjectTemplate = " + subjectTemplate +
            ", contentTemplate = " + contentTemplate +
            ", variableSchemaJson = " + variableSchemaJson +
            ", status = " + status +
            ", createdByAccountId = " + createdByAccountId +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
