package com.unique.unexamine.examine.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 审批节点字段配置
 * </p>
 *
 * @author Template Base
 * @since 2026-10-23
 */
@TableName("un_examine_node_field")
public class UnExamineNodeField implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 审批节点字段权限主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属审批草稿节点主键
     */
    @TableField("node_id")
    private Long nodeId;

    /**
     * 关联模块字段稳定主键
     */
    @TableField("field_id")
    private Long fieldId;

    /**
     * 节点处理人能否读取该字段
     */
    @TableField("readable_flag")
    private Boolean readableFlag;

    /**
     * 节点处理人能否写入该字段
     */
    @TableField("writable_flag")
    private Boolean writableFlag;

    /**
     * 节点提交时是否必填
     */
    @TableField("required_flag")
    private Boolean requiredFlag;

    /**
     * 配置时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 逻辑删除时间，空表示未删除
     */
    @TableLogic
    @TableField("deleted_time")
    private LocalDateTime deletedTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getNodeId() {
        return nodeId;
    }

    public void setNodeId(Long nodeId) {
        this.nodeId = nodeId;
    }

    public Long getFieldId() {
        return fieldId;
    }

    public void setFieldId(Long fieldId) {
        this.fieldId = fieldId;
    }

    public Boolean getReadableFlag() {
        return readableFlag;
    }

    public void setReadableFlag(Boolean readableFlag) {
        this.readableFlag = readableFlag;
    }

    public Boolean getWritableFlag() {
        return writableFlag;
    }

    public void setWritableFlag(Boolean writableFlag) {
        this.writableFlag = writableFlag;
    }

    public Boolean getRequiredFlag() {
        return requiredFlag;
    }

    public void setRequiredFlag(Boolean requiredFlag) {
        this.requiredFlag = requiredFlag;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getDeletedTime() {
        return deletedTime;
    }

    public void setDeletedTime(LocalDateTime deletedTime) {
        this.deletedTime = deletedTime;
    }
}
