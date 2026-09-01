package com.unique.unexamine.runtimedata.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("biz_record_state_history")
public class BizRecordStateHistory {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("record_id")
    private Long recordId;

    @TableField("from_status")
    private String fromStatus;

    @TableField("to_status")
    private String toStatus;

    @TableField("reason")
    private String reason;

    @TableField("source_type")
    private String sourceType;

    @TableField("source_id")
    private String sourceId;

    @TableField("changed_by_member_id")
    private Long changedByMemberId;

    @TableField("changed_at")
    private LocalDateTime changedAt;

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

    public LocalDateTime getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(LocalDateTime changedAt) {
        this.changedAt = changedAt;
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
            ", changedAt = " + changedAt +
            "}";
    }
}
