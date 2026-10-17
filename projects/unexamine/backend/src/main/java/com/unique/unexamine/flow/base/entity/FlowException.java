package com.unique.unexamine.flow.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 流程异常与处置
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_flow_exception")
public class FlowException {

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
     * 流程节点稳定编码
     */
    @TableField("node_key")
    private String nodeKey;

    /**
     * 流程异常类型
     */
    @TableField("exception_type")
    private String exceptionType;

    /**
     * 失败错误编码
     */
    @TableField("error_code")
    private String errorCode;

    /**
     * 失败原因说明
     */
    @TableField("error_message")
    private String errorMessage;

    /**
     * 异常策略选择的处置动作
     */
    @TableField("policy_action")
    private String policyAction;

    /**
     * 当前业务状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 异常处置人平台账号ID
     */
    @TableField("resolved_by_account_id")
    private Long resolvedByAccountId;

    /**
     * 异常处置人在运行租户的成员ID
     */
    @TableField("resolved_by_tenant_member_id")
    private Long resolvedByTenantMemberId;

    /**
     * 异常处置说明
     */
    @TableField("resolution_comment")
    private String resolutionComment;

    /**
     * 事件或异常发生时间
     */
    @TableField("occur_time")
    private LocalDateTime occurTime;

    /**
     * 绑定解析时间
     */
    @TableField("resolve_time")
    private LocalDateTime resolveTime;

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

    public String getNodeKey() {
        return nodeKey;
    }

    public void setNodeKey(String nodeKey) {
        this.nodeKey = nodeKey;
    }

    public String getExceptionType() {
        return exceptionType;
    }

    public void setExceptionType(String exceptionType) {
        this.exceptionType = exceptionType;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getPolicyAction() {
        return policyAction;
    }

    public void setPolicyAction(String policyAction) {
        this.policyAction = policyAction;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getResolvedByAccountId() {
        return resolvedByAccountId;
    }

    public void setResolvedByAccountId(Long resolvedByAccountId) {
        this.resolvedByAccountId = resolvedByAccountId;
    }

    public Long getResolvedByTenantMemberId() {
        return resolvedByTenantMemberId;
    }

    public void setResolvedByTenantMemberId(Long resolvedByTenantMemberId) {
        this.resolvedByTenantMemberId = resolvedByTenantMemberId;
    }

    public String getResolutionComment() {
        return resolutionComment;
    }

    public void setResolutionComment(String resolutionComment) {
        this.resolutionComment = resolutionComment;
    }

    public LocalDateTime getOccurTime() {
        return occurTime;
    }

    public void setOccurTime(LocalDateTime occurTime) {
        this.occurTime = occurTime;
    }

    public LocalDateTime getResolveTime() {
        return resolveTime;
    }

    public void setResolveTime(LocalDateTime resolveTime) {
        this.resolveTime = resolveTime;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "FlowException{" +
            "id = " + id +
            ", instanceId = " + instanceId +
            ", nodeKey = " + nodeKey +
            ", exceptionType = " + exceptionType +
            ", errorCode = " + errorCode +
            ", errorMessage = " + errorMessage +
            ", policyAction = " + policyAction +
            ", status = " + status +
            ", resolvedByAccountId = " + resolvedByAccountId +
            ", resolvedByTenantMemberId = " + resolvedByTenantMemberId +
            ", resolutionComment = " + resolutionComment +
            ", occurTime = " + occurTime +
            ", resolveTime = " + resolveTime +
            ", version = " + version +
            "}";
    }
}
