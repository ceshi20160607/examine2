package com.unique.unexamine.foundation.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 配额周期内的已用和预留计数
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_core_quota_usage")
public class CoreQuotaUsage {

    /**
     * 周期配额用量主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属配额策略主键
     */
    @TableField("quota_policy_id")
    private Long quotaPolicyId;

    /**
     * 配额统计周期标识
     */
    @TableField("period_key")
    private String periodKey;

    /**
     * 当前周期已正式占用的配额量
     */
    @TableField("used_value")
    private Long usedValue;

    /**
     * 当前周期已预留但尚未正式占用的配额量
     */
    @TableField("reserved_value")
    private Long reservedValue;

    /**
     * 配额用量最后更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 配额用量乐观锁版本
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
        return "CoreQuotaUsage{" +
            "id = " + id +
            ", quotaPolicyId = " + quotaPolicyId +
            ", periodKey = " + periodKey +
            ", usedValue = " + usedValue +
            ", reservedValue = " + reservedValue +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
