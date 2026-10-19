package com.unique.unexamine.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 工作任务
 * </p>
 *
 * @author Template Base
 * @since 2026-10-19
 */
@TableName("un_work_task")
public class UnWorkTask implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 工作任务主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属工作项目主键
     */
    @TableField("project_id")
    private Long projectId;

    /**
     * 所属任务组主键
     */
    @TableField("group_id")
    private Long groupId;

    /**
     * 任务标题
     */
    @TableField("title")
    private String title;

    /**
     * 任务说明
     */
    @TableField("description")
    private String description;

    /**
     * 任务负责人系统成员主键
     */
    @TableField("owner_member_id")
    private Long ownerMemberId;

    /**
     * 任务状态编码
     */
    @TableField("status")
    private String status;

    /**
     * 任务优先级，数字越小越优先
     */
    @TableField("priority")
    private Byte priority;

    /**
     * 任务计划完成时间
     */
    @TableField("due_time")
    private LocalDateTime dueTime;

    /**
     * 已配置任务字段的值
     */
    @TableField("field_data")
    private String fieldData;

    /**
     * 并发编辑版本号
     */
    @TableField("version")
    private Integer version;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getOwnerMemberId() {
        return ownerMemberId;
    }

    public void setOwnerMemberId(Long ownerMemberId) {
        this.ownerMemberId = ownerMemberId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Byte getPriority() {
        return priority;
    }

    public void setPriority(Byte priority) {
        this.priority = priority;
    }

    public LocalDateTime getDueTime() {
        return dueTime;
    }

    public void setDueTime(LocalDateTime dueTime) {
        this.dueTime = dueTime;
    }

    public String getFieldData() {
        return fieldData;
    }

    public void setFieldData(String fieldData) {
        this.fieldData = fieldData;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
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
}
