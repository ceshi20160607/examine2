package com.unique.unexamine.foundation.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 平台或系统租户的配额上限策略
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_core_quota_policy")
public class CoreQuotaPolicy {

    /**
     * 配额策略主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 配额归属：平台或系统租户
     */
    @TableField("context_type")
    private String contextType;

    /**
     * 平台范围主键，系统租户配额为空
     */
    @TableField("platform_id")
    private Long platformId;

    /**
     * 系统范围主键，平台配额为空
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 系统租户主键，平台配额为空
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 归属范围内的配额编码
     */
    @TableField("quota_code")
    private String quotaCode;

    /**
     * 统计周期类型
     */
    @TableField("period_type")
    private String periodType;

    /**
     * 周期内的硬性上限
     */
    @TableField("hard_limit")
    private Long hardLimit;

    /**
     * 触发预警的已用阈值
     */
    @TableField("warning_threshold")
    private Long warningThreshold;

    /**
     * 策略启停状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 创建时间，后续统一更名为 create_time
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间，后续统一更名为 update_time
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

    public String getContextType() {
        return contextType;
    }

    public void setContextType(String contextType) {
        this.contextType = contextType;
    }

    public Long getPlatformId() {
        return platformId;
    }

    public void setPlatformId(Long platformId) {
        this.platformId = platformId;
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

    public String getQuotaCode() {
        return quotaCode;
    }

    public void setQuotaCode(String quotaCode) {
        this.quotaCode = quotaCode;
    }

    public String getPeriodType() {
        return periodType;
    }

    public void setPeriodType(String periodType) {
        this.periodType = periodType;
    }

    public Long getHardLimit() {
        return hardLimit;
    }

    public void setHardLimit(Long hardLimit) {
        this.hardLimit = hardLimit;
    }

    public Long getWarningThreshold() {
        return warningThreshold;
    }

    public void setWarningThreshold(Long warningThreshold) {
        this.warningThreshold = warningThreshold;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
        return "CoreQuotaPolicy{" +
            "id = " + id +
            ", contextType = " + contextType +
            ", platformId = " + platformId +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", quotaCode = " + quotaCode +
            ", periodType = " + periodType +
            ", hardLimit = " + hardLimit +
            ", warningThreshold = " + warningThreshold +
            ", status = " + status +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
