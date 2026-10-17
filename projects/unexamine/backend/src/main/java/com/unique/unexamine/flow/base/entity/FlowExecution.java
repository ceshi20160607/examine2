package com.unique.unexamine.flow.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 流程节点执行轮次
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_flow_execution")
public class FlowExecution {

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属流程实例ID
     */
    @TableField("instance_id")
    private Long instanceId;

    /**
     * 父节点执行轮次ID
     */
    @TableField("parent_execution_id")
    private Long parentExecutionId;

    /**
     * 流程节点稳定编码
     */
    @TableField("node_key")
    private String nodeKey;

    /**
     * 节点类型
     */
    @TableField("node_type")
    private String nodeType;

    /**
     * 当前业务状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 节点进入时间
     */
    @TableField("enter_time")
    private LocalDateTime enterTime;

    /**
     * 节点离开时间
     */
    @TableField("leave_time")
    private LocalDateTime leaveTime;

    /**
     * 节点执行结果编码
     */
    @TableField("result_code")
    private String resultCode;

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

    public Long getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(Long instanceId) {
        this.instanceId = instanceId;
    }

    public Long getParentExecutionId() {
        return parentExecutionId;
    }

    public void setParentExecutionId(Long parentExecutionId) {
        this.parentExecutionId = parentExecutionId;
    }

    public String getNodeKey() {
        return nodeKey;
    }

    public void setNodeKey(String nodeKey) {
        this.nodeKey = nodeKey;
    }

    public String getNodeType() {
        return nodeType;
    }

    public void setNodeType(String nodeType) {
        this.nodeType = nodeType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getEnterTime() {
        return enterTime;
    }

    public void setEnterTime(LocalDateTime enterTime) {
        this.enterTime = enterTime;
    }

    public LocalDateTime getLeaveTime() {
        return leaveTime;
    }

    public void setLeaveTime(LocalDateTime leaveTime) {
        this.leaveTime = leaveTime;
    }

    public String getResultCode() {
        return resultCode;
    }

    public void setResultCode(String resultCode) {
        this.resultCode = resultCode;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "FlowExecution{" +
            "id = " + id +
            ", instanceId = " + instanceId +
            ", parentExecutionId = " + parentExecutionId +
            ", nodeKey = " + nodeKey +
            ", nodeType = " + nodeType +
            ", status = " + status +
            ", enterTime = " + enterTime +
            ", leaveTime = " + leaveTime +
            ", resultCode = " + resultCode +
            ", version = " + version +
            "}";
    }
}
