package com.unique.unexamine.system.base.entity;

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
@TableName("sys_access_request")
public class SysAccessRequest {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("system_id")
    private Long systemId;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("account_id")
    private Long accountId;

    @TableField("identity_provider")
    private String identityProvider;

    @TableField("external_user_id")
    private String externalUserId;

    @TableField("request_reason")
    private String requestReason;

    @TableField("requested_role")
    private String requestedRole;

    @TableField("`status`")
    private String status;

    @TableField("decided_by_member_id")
    private Long decidedByMemberId;

    @TableField("decision_comment")
    private String decisionComment;

    @TableField("approved_role_ids_json")
    private String approvedRoleIdsJson;

    @TableField("approved_data_scope_json")
    private String approvedDataScopeJson;

    @TableField("request_trace_id")
    private String requestTraceId;

    @TableField("decision_trace_id")
    private String decisionTraceId;

    @TableField("decided_at")
    private LocalDateTime decidedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @Version
    @TableField("version")
    private Integer version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSystemId() {
        return systemId;
    }

    public void setSystemId(Long systemId) {
        this.systemId = systemId;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public String getIdentityProvider() {
        return identityProvider;
    }

    public void setIdentityProvider(String identityProvider) {
        this.identityProvider = identityProvider;
    }

    public String getExternalUserId() {
        return externalUserId;
    }

    public void setExternalUserId(String externalUserId) {
        this.externalUserId = externalUserId;
    }

    public String getRequestReason() {
        return requestReason;
    }

    public void setRequestReason(String requestReason) {
        this.requestReason = requestReason;
    }

    public String getRequestedRole() {
        return requestedRole;
    }

    public void setRequestedRole(String requestedRole) {
        this.requestedRole = requestedRole;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getDecidedByMemberId() {
        return decidedByMemberId;
    }

    public void setDecidedByMemberId(Long decidedByMemberId) {
        this.decidedByMemberId = decidedByMemberId;
    }

    public String getDecisionComment() {
        return decisionComment;
    }

    public void setDecisionComment(String decisionComment) {
        this.decisionComment = decisionComment;
    }

    public String getApprovedRoleIdsJson() {
        return approvedRoleIdsJson;
    }

    public void setApprovedRoleIdsJson(String approvedRoleIdsJson) {
        this.approvedRoleIdsJson = approvedRoleIdsJson;
    }

    public String getApprovedDataScopeJson() {
        return approvedDataScopeJson;
    }

    public void setApprovedDataScopeJson(String approvedDataScopeJson) {
        this.approvedDataScopeJson = approvedDataScopeJson;
    }

    public String getRequestTraceId() {
        return requestTraceId;
    }

    public void setRequestTraceId(String requestTraceId) {
        this.requestTraceId = requestTraceId;
    }

    public String getDecisionTraceId() {
        return decisionTraceId;
    }

    public void setDecisionTraceId(String decisionTraceId) {
        this.decisionTraceId = decisionTraceId;
    }

    public LocalDateTime getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(LocalDateTime decidedAt) {
        this.decidedAt = decidedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "SysAccessRequest{" +
            "id = " + id +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", accountId = " + accountId +
            ", identityProvider = " + identityProvider +
            ", externalUserId = " + externalUserId +
            ", requestReason = " + requestReason +
            ", requestedRole = " + requestedRole +
            ", status = " + status +
            ", decidedByMemberId = " + decidedByMemberId +
            ", decisionComment = " + decisionComment +
            ", approvedRoleIdsJson = " + approvedRoleIdsJson +
            ", approvedDataScopeJson = " + approvedDataScopeJson +
            ", requestTraceId = " + requestTraceId +
            ", decisionTraceId = " + decisionTraceId +
            ", decidedAt = " + decidedAt +
            ", createdAt = " + createdAt +
            ", updatedAt = " + updatedAt +
            ", version = " + version +
            "}";
    }
}
