package com.unique.unexamine.system.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 系统成员访问申请及审批历史
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_sys_access_request")
public class SysAccessRequest {

    /**
     * 访问申请主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 目标系统标识
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 目标租户标识，未确定租户时为空
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 申请的平台账号标识
     */
    @TableField("account_id")
    private Long accountId;

    /**
     * 申请所用身份源编码
     */
    @TableField("identity_provider")
    private String identityProvider;

    /**
     * 身份源中的外部用户标识
     */
    @TableField("external_user_id")
    private String externalUserId;

    /**
     * 申请原因
     */
    @TableField("request_reason")
    private String requestReason;

    /**
     * 申请人期望的角色
     */
    @TableField("requested_role")
    private String requestedRole;

    /**
     * 申请状态，PENDING 表示未决
     */
    @TableField("`status`")
    private String status;

    /**
     * 审批系统成员标识
     */
    @TableField("decided_by_member_id")
    private Long decidedByMemberId;

    /**
     * 审批意见
     */
    @TableField("decision_comment")
    private String decisionComment;

    /**
     * 审批确认的角色标识快照
     */
    @TableField("approved_role_ids_json")
    private String approvedRoleIdsJson;

    /**
     * 审批确认的数据范围快照
     */
    @TableField("approved_data_scope_json")
    private String approvedDataScopeJson;

    /**
     * 申请链路追踪标识
     */
    @TableField("request_trace_id")
    private String requestTraceId;

    /**
     * 审批链路追踪标识
     */
    @TableField("decision_trace_id")
    private String decisionTraceId;

    /**
     * 审批时间
     */
    @TableField("decide_time")
    private LocalDateTime decideTime;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 乐观锁版本
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

    public LocalDateTime getDecideTime() {
        return decideTime;
    }

    public void setDecideTime(LocalDateTime decideTime) {
        this.decideTime = decideTime;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
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
            ", decideTime = " + decideTime +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
