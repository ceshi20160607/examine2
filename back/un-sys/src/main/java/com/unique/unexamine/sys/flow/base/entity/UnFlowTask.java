package com.unique.unexamine.sys.flow.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 流程节点执行轮次待办
 * </p>
 *
 * @author Template Base
 * @since 2026-10-23
 */
@TableName("un_flow_task")
public class UnFlowTask implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 流程节点待办主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属运行流程实例主键
     */
    @TableField("example_id")
    private Long exampleId;

    /**
     * 实例快照中的稳定节点编码
     */
    @TableField("node_code")
    private String nodeCode;

    /**
     * 节点退回重入或循环执行轮次
     */
    @TableField("round_no")
    private Integer roundNo;

    /**
     * Flow 节点、自动任务或等待 examine 的任务类型
     */
    @TableField("task_type")
    private String taskType;

    /**
     * 待办领取完成取消或超时状态
     */
    @TableField("status")
    private String status;

    /**
     * 本轮节点规则人员字段和业务值快照
     */
    @TableField("node_snapshot_json")
    private String nodeSnapshotJson;

    /**
     * 本轮任务到期时间
     */
    @TableField("due_time")
    private LocalDateTime dueTime;

    /**
     * 节点本轮进入时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 节点本轮完成时间
     */
    @TableField("finish_time")
    private LocalDateTime finishTime;

    /**
     * 任务更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

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

    public Long getExampleId() {
        return exampleId;
    }

    public void setExampleId(Long exampleId) {
        this.exampleId = exampleId;
    }

    public String getNodeCode() {
        return nodeCode;
    }

    public void setNodeCode(String nodeCode) {
        this.nodeCode = nodeCode;
    }

    public Integer getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(Integer roundNo) {
        this.roundNo = roundNo;
    }

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getNodeSnapshotJson() {
        return nodeSnapshotJson;
    }

    public void setNodeSnapshotJson(String nodeSnapshotJson) {
        this.nodeSnapshotJson = nodeSnapshotJson;
    }

    public LocalDateTime getDueTime() {
        return dueTime;
    }

    public void setDueTime(LocalDateTime dueTime) {
        this.dueTime = dueTime;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getFinishTime() {
        return finishTime;
    }

    public void setFinishTime(LocalDateTime finishTime) {
        this.finishTime = finishTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public LocalDateTime getDeletedTime() {
        return deletedTime;
    }

    public void setDeletedTime(LocalDateTime deletedTime) {
        this.deletedTime = deletedTime;
    }
}
