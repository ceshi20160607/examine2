package com.unique.unexamine.application.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 应用外部平台连接
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_app_provider_connection")
public class AppProviderConnection {

    /**
     * 外部平台连接主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属受控应用主键
     */
    @TableField("application_id")
    private Long applicationId;

    /**
     * 平台编码：WECOM企业微信、FEISHU飞书、DINGTALK钉钉、WECHAT微信
     */
    @TableField("provider_code")
    private String providerCode;

    /**
     * 平台产品形态编码
     */
    @TableField("product_code")
    private String productCode;

    /**
     * 不可猜测的公开回调连接标识
     */
    @TableField("connection_key")
    private String connectionKey;

    /**
     * 连接显示名称
     */
    @TableField("display_name")
    private String displayName;

    /**
     * 平台租户标识，例如企业微信CorpID
     */
    @TableField("tenant_key")
    private String tenantKey;

    /**
     * 平台应用标识，例如企业微信AgentID
     */
    @TableField("application_key")
    private String applicationKey;

    /**
     * 连接状态：DRAFT草稿、ACTIVE启用、DISABLED停用
     */
    @TableField("`status`")
    private String status;

    /**
     * 诊断状态：NOT_RUN未执行、LOCAL_PASSED本地协议通过、FAILED失败、EXTERNAL_PASSED外部联通通过
     */
    @TableField("diagnostic_status")
    private String diagnosticStatus;

    /**
     * 不含敏感材料的最近诊断摘要
     */
    @TableField("diagnostic_summary")
    private String diagnosticSummary;

    /**
     * 最近诊断时间
     */
    @TableField("diagnostic_time")
    private LocalDateTime diagnosticTime;

    /**
     * 创建人平台账号主键
     */
    @TableField("created_by_account_id")
    private Long createdByAccountId;

    /**
     * 创建时间
     */
    @TableField("created_time")
    private LocalDateTime createdTime;

    /**
     * 更新时间
     */
    @TableField("updated_time")
    private LocalDateTime updatedTime;

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

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public String getProviderCode() {
        return providerCode;
    }

    public void setProviderCode(String providerCode) {
        this.providerCode = providerCode;
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public String getConnectionKey() {
        return connectionKey;
    }

    public void setConnectionKey(String connectionKey) {
        this.connectionKey = connectionKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getTenantKey() {
        return tenantKey;
    }

    public void setTenantKey(String tenantKey) {
        this.tenantKey = tenantKey;
    }

    public String getApplicationKey() {
        return applicationKey;
    }

    public void setApplicationKey(String applicationKey) {
        this.applicationKey = applicationKey;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDiagnosticStatus() {
        return diagnosticStatus;
    }

    public void setDiagnosticStatus(String diagnosticStatus) {
        this.diagnosticStatus = diagnosticStatus;
    }

    public String getDiagnosticSummary() {
        return diagnosticSummary;
    }

    public void setDiagnosticSummary(String diagnosticSummary) {
        this.diagnosticSummary = diagnosticSummary;
    }

    public LocalDateTime getDiagnosticTime() {
        return diagnosticTime;
    }

    public void setDiagnosticTime(LocalDateTime diagnosticTime) {
        this.diagnosticTime = diagnosticTime;
    }

    public Long getCreatedByAccountId() {
        return createdByAccountId;
    }

    public void setCreatedByAccountId(Long createdByAccountId) {
        this.createdByAccountId = createdByAccountId;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(LocalDateTime createdTime) {
        this.createdTime = createdTime;
    }

    public LocalDateTime getUpdatedTime() {
        return updatedTime;
    }

    public void setUpdatedTime(LocalDateTime updatedTime) {
        this.updatedTime = updatedTime;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "AppProviderConnection{" +
            "id = " + id +
            ", applicationId = " + applicationId +
            ", providerCode = " + providerCode +
            ", productCode = " + productCode +
            ", connectionKey = " + connectionKey +
            ", displayName = " + displayName +
            ", tenantKey = " + tenantKey +
            ", applicationKey = " + applicationKey +
            ", status = " + status +
            ", diagnosticStatus = " + diagnosticStatus +
            ", diagnosticSummary = " + diagnosticSummary +
            ", diagnosticTime = " + diagnosticTime +
            ", createdByAccountId = " + createdByAccountId +
            ", createdTime = " + createdTime +
            ", updatedTime = " + updatedTime +
            ", version = " + version +
            "}";
    }
}
