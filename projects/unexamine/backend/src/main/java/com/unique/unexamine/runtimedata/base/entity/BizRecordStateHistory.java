package com.unique.unexamine.runtimedata.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 业务记录状态变更历史
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_biz_record_state_history")
public class BizRecordStateHistory {

    /**
     * 本表主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 业务主记录主键
     */
    @TableField("record_id")
    private Long recordId;

    /**
     * 变更前业务状态
     */
    @TableField("from_status")
    private String fromStatus;

    /**
     * 变更后的业务状态
     */
    @TableField("to_status")
    private String toStatus;

    /**
     * 业务变更原因
     */
    @TableField("reason")
    private String reason;

    /**
     * 发起状态变更的来源类型
     */
    @TableField("source_type")
    private String sourceType;

    /**
     * 发起状态变更的来源对象标识
     */
    @TableField("source_id")
    private String sourceId;

    /**
     * 执行变更的系统成员主键
     */
    @TableField("changed_by_member_id")
    private Long changedByMemberId;

    /**
     * 业务变更发生时间
     */
    @TableField("change_time")
    private LocalDateTime changeTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRecordId() {
        return recordId;
    }

    public void setRecordId(Long recordId) {
        this.recordId = recordId;
    }

    public String getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(String fromStatus) {
        this.fromStatus = fromStatus;
    }

    public String getToStatus() {
        return toStatus;
    }

    public void setToStatus(String toStatus) {
        this.toStatus = toStatus;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public Long getChangedByMemberId() {
        return changedByMemberId;
    }

    public void setChangedByMemberId(Long changedByMemberId) {
        this.changedByMemberId = changedByMemberId;
    }

    public LocalDateTime getChangeTime() {
        return changeTime;
    }

    public void setChangeTime(LocalDateTime changeTime) {
        this.changeTime = changeTime;
    }

    @Override
    public String toString() {
        return "BizRecordStateHistory{" +
            "id = " + id +
            ", recordId = " + recordId +
            ", fromStatus = " + fromStatus +
            ", toStatus = " + toStatus +
            ", reason = " + reason +
            ", sourceType = " + sourceType +
            ", sourceId = " + sourceId +
            ", changedByMemberId = " + changedByMemberId +
            ", changeTime = " + changeTime +
            "}";
    }
}
