package com.unique.unexamine.app.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 应用调用幂等与审计记录
 * </p>
 *
 * @author Template Base
 * @since 2026-10-23
 */
@TableName("un_app_call")
public class UnAppCall implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 应用调用审计主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 实际调用的受控应用主键
     */
    @TableField("application_id")
    private Long applicationId;

    /**
     * 通过验证的应用密钥版本
     */
    @TableField("credential_version")
    private Integer credentialVersion;

    /**
     * 目标业务系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 目标业务租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 调用模块流程或文件资源类型
     */
    @TableField("resource_type")
    private String resourceType;

    /**
     * 调用资源主键
     */
    @TableField("resource_id")
    private Long resourceId;

    /**
     * 本次执行的独立授权动作
     */
    @TableField("action_code")
    private String actionCode;

    /**
     * 调用方提交的稳定幂等请求标识
     */
    @TableField("request_id")
    private String requestId;

    /**
     * 请求内容摘要，用于拒绝同键异参
     */
    @TableField("request_hash")
    private String requestHash;

    /**
     * 成功拒绝或失败结果编码
     */
    @TableField("result_code")
    private String resultCode;

    /**
     * 幂等重试需要的非敏感结果摘要
     */
    @TableField("response_json")
    private String responseJson;

    /**
     * 实际生效资源字段及数据范围快照
     */
    @TableField("permission_json")
    private String permissionJson;

    /**
     * 接口处理耗时毫秒
     */
    @TableField("duration_ms")
    private Integer durationMs;

    /**
     * 调用方网络地址
     */
    @TableField("caller_ip")
    private String callerIp;

    /**
     * 调用发生时间
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

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public Integer getCredentialVersion() {
        return credentialVersion;
    }

    public void setCredentialVersion(Integer credentialVersion) {
        this.credentialVersion = credentialVersion;
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

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public Long getResourceId() {
        return resourceId;
    }

    public void setResourceId(Long resourceId) {
        this.resourceId = resourceId;
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

    public String getRequestHash() {
        return requestHash;
    }

    public void setRequestHash(String requestHash) {
        this.requestHash = requestHash;
    }

    public String getResultCode() {
        return resultCode;
    }

    public void setResultCode(String resultCode) {
        this.resultCode = resultCode;
    }

    public String getResponseJson() {
        return responseJson;
    }

    public void setResponseJson(String responseJson) {
        this.responseJson = responseJson;
    }

    public String getPermissionJson() {
        return permissionJson;
    }

    public void setPermissionJson(String permissionJson) {
        this.permissionJson = permissionJson;
    }

    public Integer getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Integer durationMs) {
        this.durationMs = durationMs;
    }

    public String getCallerIp() {
        return callerIp;
    }

    public void setCallerIp(String callerIp) {
        this.callerIp = callerIp;
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
