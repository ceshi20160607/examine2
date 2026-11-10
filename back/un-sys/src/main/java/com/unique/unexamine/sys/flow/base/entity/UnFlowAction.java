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
 * 流程操作及幂等记录
 * </p>
 *
 * @author Template Base
 * @since 2026-10-23
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
     * 所属运行流程实例主键
     */
    @TableField("example_id")
    private Long exampleId;

    /**
     * 操作所针对的节点待办主键
     */
    @TableField("task_id")
    private Long taskId;

    /**
     * 外部操作所用受控应用主键
     */
    @TableField("application_id")
    private Long applicationId;

    /**
     * 内部实际操作平台账号主键
     */
    @TableField("actor_account_id")
    private Long actorAccountId;

    /**
     * 外部实际操作人标识
     */
    @TableField("actor_external_id")
    private String actorExternalId;

    /**
     * Flow 节点执行、继续及 examine 结果回传等操作编码
     */
    @TableField("action_code")
    private String actionCode;

    /**
     * 调用幂等请求标识
     */
    @TableField("request_id")
    private String requestId;

    /**
     * 操作完成或拒绝结果编码
     */
    @TableField("result_code")
    private String resultCode;

    /**
     * 意见附件字段修改和权限校验快照
     */
    @TableField("payload_json")
    private String payloadJson;

    /**
     * 实际操作时间
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

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public Long getActorAccountId() {
        return actorAccountId;
    }

    public void setActorAccountId(Long actorAccountId) {
        this.actorAccountId = actorAccountId;
    }

    public String getActorExternalId() {
        return actorExternalId;
    }

    public void setActorExternalId(String actorExternalId) {
        this.actorExternalId = actorExternalId;
    }

    public String getActionCode() {
        return actionCode;
    }

    public void setActionCode(String actionCode) {
        this.actionCode = actionCode;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getResultCode() {
        return resultCode;
    }

    public void setResultCode(String resultCode) {
        this.resultCode = resultCode;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
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
