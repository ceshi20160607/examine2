package com.unique.unexamine.flow.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 父子流程实例关系
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_flow_subflow_link")
public class FlowSubflowLink {

    /**
     * 子流程关系主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 父流程实例ID
     */
    @TableField("parent_instance_id")
    private Long parentInstanceId;

    /**
     * 启动子流程的父节点执行轮次ID
     */
    @TableField("parent_execution_id")
    private Long parentExecutionId;

    /**
     * 子流程实例ID
     */
    @TableField("child_instance_id")
    private Long childInstanceId;

    /**
     * 父流程节点编码快照
     */
    @TableField("parent_node_key")
    private String parentNodeKey;

    /**
     * 子流程关系状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 关系创建时间
     */
    @TableField("created_time")
    private LocalDateTime createdTime;

    /**
     * 子流程结束并处理父流程的时间
     */
    @TableField("completed_time")
    private LocalDateTime completedTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getParentInstanceId() {
        return parentInstanceId;
    }

    public void setParentInstanceId(Long parentInstanceId) {
        this.parentInstanceId = parentInstanceId;
    }

    public Long getParentExecutionId() {
        return parentExecutionId;
    }

    public void setParentExecutionId(Long parentExecutionId) {
        this.parentExecutionId = parentExecutionId;
    }

    public Long getChildInstanceId() {
        return childInstanceId;
    }

    public void setChildInstanceId(Long childInstanceId) {
        this.childInstanceId = childInstanceId;
    }

    public String getParentNodeKey() {
        return parentNodeKey;
    }

    public void setParentNodeKey(String parentNodeKey) {
        this.parentNodeKey = parentNodeKey;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(LocalDateTime createdTime) {
        this.createdTime = createdTime;
    }

    public LocalDateTime getCompletedTime() {
        return completedTime;
    }

    public void setCompletedTime(LocalDateTime completedTime) {
        this.completedTime = completedTime;
    }

    @Override
    public String toString() {
        return "FlowSubflowLink{" +
            "id = " + id +
            ", parentInstanceId = " + parentInstanceId +
            ", parentExecutionId = " + parentExecutionId +
            ", childInstanceId = " + childInstanceId +
            ", parentNodeKey = " + parentNodeKey +
            ", status = " + status +
            ", createdTime = " + createdTime +
            ", completedTime = " + completedTime +
            "}";
    }
}
