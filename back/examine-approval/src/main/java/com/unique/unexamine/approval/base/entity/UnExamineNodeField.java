package com.unique.unexamine.approval.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 审批节点字段配置
 * </p>
 *
 * @author Template Base
 * @since 2026-10-19
 */
@TableName("un_examine_node_field")
public class UnExamineNodeField implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 审批节点字段配置主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属审批节点主键
     */
    @TableField("node_id")
    private Long nodeId;

    /**
     * 关联模块字段主键
     */
    @TableField("field_id")
    private Long fieldId;

    /**
     * 字段在本节点是否可见：1 可见，0 隐藏
     */
    @TableField("is_visible")
    private Byte isVisible;

    /**
     * 字段在本节点是否可编辑：1 可编辑，0 只读
     */
    @TableField("is_editable")
    private Byte isEditable;

    /**
     * 本节点编辑时是否必填：1 必填，0 非必填
     */
    @TableField("is_required")
    private Byte isRequired;

    /**
     * 本节点内字段展示顺序
     */
    @TableField("sort_num")
    private Integer sortNum;

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

    public Byte getIsVisible() {
        return isVisible;
    }

    public void setIsVisible(Byte isVisible) {
        this.isVisible = isVisible;
    }

    public Byte getIsEditable() {
        return isEditable;
    }

    public void setIsEditable(Byte isEditable) {
        this.isEditable = isEditable;
    }

    public Byte getIsRequired() {
        return isRequired;
    }

    public void setIsRequired(Byte isRequired) {
        this.isRequired = isRequired;
    }

    public Integer getSortNum() {
        return sortNum;
    }

    public void setSortNum(Integer sortNum) {
        this.sortNum = sortNum;
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
