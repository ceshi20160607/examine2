package com.unique.unexamine.foundation.base.entity;

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
@TableName("core_quota_usage")
public class CoreQuotaUsage {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("quota_policy_id")
    private Long quotaPolicyId;

    @TableField("period_key")
    private String periodKey;

    @TableField("used_value")
    private Long usedValue;

    @TableField("reserved_value")
    private Long reservedValue;

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

    public Long getQuotaPolicyId() {
        return quotaPolicyId;
    }

    public void setQuotaPolicyId(Long quotaPolicyId) {
        this.quotaPolicyId = quotaPolicyId;
    }

    public String getPeriodKey() {
        return periodKey;
    }

    public void setPeriodKey(String periodKey) {
        this.periodKey = periodKey;
    }

    public Long getUsedValue() {
        return usedValue;
    }

    public void setUsedValue(Long usedValue) {
        this.usedValue = usedValue;
    }

    public Long getReservedValue() {
        return reservedValue;
    }

    public void setReservedValue(Long reservedValue) {
        this.reservedValue = reservedValue;
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
        return "CoreQuotaUsage{" +
            "id = " + id +
            ", quotaPolicyId = " + quotaPolicyId +
            ", periodKey = " + periodKey +
            ", usedValue = " + usedValue +
            ", reservedValue = " + reservedValue +
            ", updatedAt = " + updatedAt +
            ", version = " + version +
            "}";
    }
}
