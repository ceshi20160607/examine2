package com.unique.unexamine.foundation.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 租户业务编号规则及当前序号
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_core_sequence")
public class CoreSequence {

    /**
     * 租户业务编号序列主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 编号序列所属系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 编号序列所属租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 租户范围内的编号序列编码
     */
    @TableField("sequence_code")
    private String sequenceCode;

    /**
     * 生成业务编号的格式模板
     */
    @TableField("pattern")
    private String pattern;

    /**
     * 计数器重置周期类型
     */
    @TableField("reset_period")
    private String resetPeriod;

    /**
     * 当前计数周期标识
     */
    @TableField("current_period_key")
    private String currentPeriodKey;

    /**
     * 当前周期已分配的最大序号
     */
    @TableField("current_value")
    private Long currentValue;

    /**
     * 每次分配增加的序号步长
     */
    @TableField("step_value")
    private Integer stepValue;

    /**
     * 编号序列启停状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 编号序列创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 编号序列更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 编号序列乐观锁版本
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

    public String getSequenceCode() {
        return sequenceCode;
    }

    public void setSequenceCode(String sequenceCode) {
        this.sequenceCode = sequenceCode;
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public String getResetPeriod() {
        return resetPeriod;
    }

    public void setResetPeriod(String resetPeriod) {
        this.resetPeriod = resetPeriod;
    }

    public String getCurrentPeriodKey() {
        return currentPeriodKey;
    }

    public void setCurrentPeriodKey(String currentPeriodKey) {
        this.currentPeriodKey = currentPeriodKey;
    }

    public Long getCurrentValue() {
        return currentValue;
    }

    public void setCurrentValue(Long currentValue) {
        this.currentValue = currentValue;
    }

    public Integer getStepValue() {
        return stepValue;
    }

    public void setStepValue(Integer stepValue) {
        this.stepValue = stepValue;
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
        return "CoreSequence{" +
            "id = " + id +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", sequenceCode = " + sequenceCode +
            ", pattern = " + pattern +
            ", resetPeriod = " + resetPeriod +
            ", currentPeriodKey = " + currentPeriodKey +
            ", currentValue = " + currentValue +
            ", stepValue = " + stepValue +
            ", status = " + status +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
