package com.unique.unexamine.examine.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 审批实例节点执行轮次
 * </p>
 *
 * @author Template Base
 * @since 2026-10-22
 */
@TableName("un_examine_example_node")
public class UnExamineExampleNode implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 审批实例节点主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属审批实例主键
     */
    @TableField("example_id")
    private Long exampleId;

    /**
     * 发布快照中的稳定节点编码
     */
    @TableField("node_code")
    private String nodeCode;

    /**
     * 退回重审时的节点执行轮次
     */
    @TableField("round_no")
    private Integer roundNo;

    /**
     * 本轮节点审批条件或抄送类型
     */
    @TableField("node_type")
    private String nodeType;

    /**
     * 本轮节点人员字段条件和审批方式快照
     */
    @TableField("node_snapshot_json")
    private String nodeSnapshotJson;

    /**
     * 待办通过拒绝跳过或取消状态
     */
    @TableField("status")
    private String status;

    /**
     * 自动处理或跳过节点的可解释原因
     */
    @TableField("auto_reason")
    private String autoReason;

    /**
     * 节点本轮开始时间
     */
    @TableField("start_time")
    private LocalDateTime startTime;

    /**
     * 节点本轮完成时间
     */
    @TableField("finish_time")
    private LocalDateTime finishTime;

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

    public String getNodeType() {
        return nodeType;
    }

    public void setNodeType(String nodeType) {
        this.nodeType = nodeType;
    }

    public String getNodeSnapshotJson() {
        return nodeSnapshotJson;
    }

    public void setNodeSnapshotJson(String nodeSnapshotJson) {
        this.nodeSnapshotJson = nodeSnapshotJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getAutoReason() {
        return autoReason;
    }

    public void setAutoReason(String autoReason) {
        this.autoReason = autoReason;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getFinishTime() {
        return finishTime;
    }

    public void setFinishTime(LocalDateTime finishTime) {
        this.finishTime = finishTime;
    }
}
