package com.unique.unexamine.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 流程操作及幂等记录
 * </p>
 *
 * @author Template Base
 * @since 2026-10-19
 */
@TableName("un_flow_action")
public class UnFlowAction implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 流程操作主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 所属流程实例主键
     */
    @TableField("example_id")
    private Long exampleId;

    /**
     * 操作关联的待办任务主键
     */
    @TableField("task_id")
    private Long taskId;

    /**
     * 操作成员主键
     */
    @TableField("actor_member_id")
    private Long actorMemberId;

    /**
     * 操作类型，如 APPROVE、REJECT、WITHDRAW
     */
    @TableField("action_type")
    private String actionType;

    /**
     * 客户端提交的幂等请求标识
     */
    @TableField("request_key")
    private String requestKey;

    /**
     * 操作结果：SUCCESS 成功，FAILED 失败
     */
    @TableField("result_status")
    private String resultStatus;

    /**
     * 操作意见或失败原因
     */
    @TableField("remark")
    private String remark;

    /**
     * 操作发生时间
     */
    @TableField("action_time")
    private LocalDateTime actionTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getExampleId() {
        return exampleId;
    }

    public void setExampleId(Long exampleId) {
        this.exampleId = exampleId;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public Long getActorMemberId() {
        return actorMemberId;
    }

    public void setActorMemberId(Long actorMemberId) {
        this.actorMemberId = actorMemberId;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public String getRequestKey() {
        return requestKey;
    }

    public void setRequestKey(String requestKey) {
        this.requestKey = requestKey;
    }

    public String getResultStatus() {
        return resultStatus;
    }

    public void setResultStatus(String resultStatus) {
        this.resultStatus = resultStatus;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public LocalDateTime getActionTime() {
        return actionTime;
    }

    public void setActionTime(LocalDateTime actionTime) {
        this.actionTime = actionTime;
    }
}
