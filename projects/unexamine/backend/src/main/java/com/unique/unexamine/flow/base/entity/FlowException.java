package com.unique.unexamine.flow.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("flow_exception")
public class FlowException {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("instance_id")
    private Long instanceId;

    @TableField("node_key")
    private String nodeKey;

    @TableField("exception_type")
    private String exceptionType;

    @TableField("error_code")
    private String errorCode;

    @TableField("error_message")
    private String errorMessage;

    @TableField("policy_action")
    private String policyAction;

    @TableField("`status`")
    private String status;

    @TableField("resolved_by_account_id")
    private Long resolvedByAccountId;

    @TableField("resolved_by_tenant_member_id")
    private Long resolvedByTenantMemberId;

    @TableField("resolution_comment")
    private String resolutionComment;

    @TableField("occurred_at")
    private LocalDateTime occurredAt;

    @TableField("resolved_at")
    private LocalDateTime resolvedAt;

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

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(LocalDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
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
            ", occurredAt = " + occurredAt +
            ", resolvedAt = " + resolvedAt +
            ", version = " + version +
            "}";
    }
}
