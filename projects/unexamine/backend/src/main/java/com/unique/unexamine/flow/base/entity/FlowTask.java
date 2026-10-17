package com.unique.unexamine.flow.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 指定审批人的流程待办任务
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_flow_task")
public class FlowTask {

    /**
     * 审批待办任务主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属流程实例主键
     */
    @TableField("example_id")
    private Long exampleId;

    /**
     * 可办理任务的系统成员主键
     */
    @TableField("approver_member_id")
    private Long approverMemberId;

    /**
     * 发起时快照中的节点名称
     */
    @TableField("node_name")
    private String nodeName;

    /**
     * 任务待办、通过或拒绝状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 办理意见，拒绝时必填
     */
    @TableField("opinion")
    private String opinion;

    /**
     * 待办产生时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 任务办理完成时间
     */
    @TableField("finish_time")
    private LocalDateTime finishTime;

    /**
     * 任务乐观锁版本，阻止重复办理
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

    public Long getExampleId() {
        return exampleId;
    }

    public void setExampleId(Long exampleId) {
        this.exampleId = exampleId;
    }

    public Long getApproverMemberId() {
        return approverMemberId;
    }

    public void setApproverMemberId(Long approverMemberId) {
        this.approverMemberId = approverMemberId;
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getOpinion() {
        return opinion;
    }

    public void setOpinion(String opinion) {
        this.opinion = opinion;
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

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "FlowTask{" +
            "id = " + id +
            ", exampleId = " + exampleId +
            ", approverMemberId = " + approverMemberId +
            ", nodeName = " + nodeName +
            ", status = " + status +
            ", opinion = " + opinion +
            ", createTime = " + createTime +
            ", finishTime = " + finishTime +
            ", version = " + version +
            "}";
    }
}
